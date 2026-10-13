import fs from 'node:fs';
import vm from 'node:vm';
import { parse as parseSfc } from '@vue/compiler-sfc';
import { parse } from '@babel/parser';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const read = relative => fs.readFileSync(new URL(relative, import.meta.url), 'utf8');
function boundNames(pattern) {
  if (!pattern) return [];
  if (pattern.type === 'Identifier') return [pattern.name];
  if (pattern.type === 'ObjectPattern') return pattern.properties.flatMap(p => boundNames(p.value || p.argument));
  if (pattern.type === 'ArrayPattern') return pattern.elements.flatMap(boundNames);
  return [];
}

function browser() {
  const listeners = new Map(), storage = new Map();
  const surface = {
    addEventListener: vi.fn((type, handler) => {
      if (typeof handler !== 'function') throw new TypeError('Listener must be a function');
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(handler);
    }),
    removeEventListener: vi.fn((type, handler) => listeners.get(type)?.delete(handler)),
    dispatchEvent: vi.fn(event => { for (const fn of [...(listeners.get(event.type) || [])]) fn(event); }),
    setTimeout: (...args) => setTimeout(...args),
    clearTimeout: vi.fn(id => clearTimeout(id)),
    sessionStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    }
  };
  return { surface, listeners, count: () => [...listeners.values()].reduce((sum, group) => sum + group.size, 0) };
}

// Execute the actual page lifecycle/watch/FAB callbacks without rendering a UI.
// Business services and data-loading functions are explicit spies: no real API is reachable.
function pageHarness(name, env, router = { push: vi.fn() }) {
  const source = parseSfc(read('../mobile/pages/' + name + '.vue')).descriptor.scriptSetup.content;
  const ast = parse(source, { sourceType: 'module' }), bindings = {}, mounted = [], unmounted = [], watchers = [];
  const fragments = [], realHandlers = new Set(['onMobileFabAction', 'onMobileFileClick', 'onMobileItemClick']);
  for (const node of ast.program.body) {
    if (node.type === 'ImportDeclaration') for (const specifier of node.specifiers) bindings[specifier.local.name] = vi.fn();
    if (node.type === 'FunctionDeclaration') bindings[node.id.name] = vi.fn();
    if (node.type === 'VariableDeclaration') {
      for (const declaration of node.declarations) {
        for (const bound of boundNames(declaration.id)) bindings[bound] = vi.fn();
        if (declaration.id.type === 'Identifier' && (realHandlers.has(declaration.id.name) || /Timer$/.test(declaration.id.name))) {
          fragments.push(node.kind + ' ' + source.slice(declaration.start, declaration.end) + ';');
        }
      }
    }
    if (node.type === 'ExpressionStatement' && node.expression.type === 'CallExpression'
      && ['onMounted', 'onUnmounted', 'watch'].includes(node.expression.callee.name)) fragments.push(source.slice(node.start, node.end));
  }
  Object.assign(bindings, {
    window: env.surface, sessionStorage: env.surface.sessionStorage, router,
    CustomEvent: class { constructor(type, options = {}) { this.type = type; this.detail = options.detail; } },
    setTimeout: env.surface.setTimeout, clearTimeout: env.surface.clearTimeout,
    CLOUDNEXT_DRIVE_REFRESH_EVENT: 'cloudnext:drive-refresh',
    CLOUDNEXT_OPEN_UPLOAD_MODAL_EVENT: 'cloudnext:open-upload-modal',
    selectedCount: { value: 0 }, currentParentId: { value: '' }, myPersonKey: { value: 'test-person' },
    onMounted: callback => mounted.push(callback),
    onUnmounted: callback => unmounted.push(callback),
    watch: (value, callback) => watchers.push({ value, callback }),
    fetch: () => { throw new Error('Real HTTP is forbidden in lifecycle tests'); }
  });
  const exported = [...realHandlers].filter(key => Object.hasOwn(bindings, key));
  vm.runInNewContext(fragments.join('\n') + '\nglobalThis.handlers = {' + exported.join(',') + '};', bindings, { filename: name + '.vue' });
  return {
    bindings, watchers, handlers: bindings.handlers,
    mount: async () => { for (const fn of mounted) await fn(); },
    unmount: async () => { for (const fn of unmounted) await fn(); },
    directoryChanged: () => {
      for (const watcher of watchers) if (watcher.value === bindings.currentParentId) watcher.callback();
    }
  };
}

beforeEach(() => vi.useFakeTimers());
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

describe('mobile page lifecycle regression', () => {
  it('registers and removes every file-page listener with no obsolete document click listener', async () => {
    const env = browser(), page = pageHarness('MyFilesPage', env);
    await page.mount();
    expect(env.count()).toBe(3);
    expect(env.listeners.has('click')).toBe(false);
    expect(page.bindings.loadAdminConfig).toHaveBeenCalledOnce();
    for (const [type, handler] of env.surface.addEventListener.mock.calls) {
      expect(typeof handler).toBe('function');
      expect(type).not.toBe('click');
    }
    page.directoryChanged();
    for (const method of ['clearSelection', 'closeUploadDialog', 'closeShareDialog', 'closeRenameDialog', 'closeMoveDialog', 'closeBatchRenameDialog']) {
      expect(page.bindings[method]).toHaveBeenCalledOnce();
    }
    await page.unmount();
    expect(env.count()).toBe(0);
    expect(env.surface.removeEventListener.mock.calls).toEqual(env.surface.addEventListener.mock.calls);
  });

  it('keeps refresh, upload, file and folder actions working', async () => {
    const env = browser(), page = pageHarness('MyFilesPage', env);
    await page.mount();
    env.surface.dispatchEvent({ type: 'cloudnext:drive-refresh' });
    env.surface.dispatchEvent({ type: 'cloudnext:open-upload-modal' });
    env.surface.dispatchEvent({ type: 'cloudnext:mobile-fab-action', detail: { key: 'upload' } });
    page.handlers.onMobileItemClick({ id: 'folder', itemType: 'folder' });
    page.handlers.onMobileItemClick({ id: 'file', itemType: 'file' });
    expect(page.bindings.onDriveRefresh).toHaveBeenCalledOnce();
    expect(page.bindings.onOpenUploadModal).toHaveBeenCalledOnce();
    expect(page.bindings.openUploadDialog).toHaveBeenCalledOnce();
    expect(page.bindings.onOpenFolder).toHaveBeenCalledOnce();
    expect(page.bindings.onPreview).toHaveBeenCalledOnce();
    await page.unmount();
  });

  it('replays the home FAB action once after entering files', async () => {
    const env = browser(), router = { push: vi.fn() }, home = pageHarness('HomePage', env, router);
    await home.mount();
    env.surface.dispatchEvent({ type: 'cloudnext:mobile-fab-action', detail: { key: 'upload' } });
    expect(router.push).toHaveBeenCalledWith({ name: 'my-files' });
    await home.unmount();
    const files = pageHarness('MyFilesPage', env);
    await files.mount();
    expect(env.surface.sessionStorage.getItem('cloudnext-pending-fab')).toBeNull();
    await vi.advanceTimersByTimeAsync(30);
    expect(files.bindings.openUploadDialog).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(100);
    expect(files.bindings.openUploadDialog).toHaveBeenCalledOnce();
    await files.unmount();
  });

  it('cancels the pending FAB replay on fast navigation away', async () => {
    const env = browser();
    env.surface.sessionStorage.setItem('cloudnext-pending-fab', 'folder');
    const files = pageHarness('MyFilesPage', env);
    await files.mount();
    expect(vi.getTimerCount()).toBe(1);
    await files.unmount();
    expect(vi.getTimerCount()).toBe(0);
    const home = pageHarness('HomePage', env);
    await home.mount();
    await vi.advanceTimersByTimeAsync(100);
    expect(files.bindings.onCreateFolder).not.toHaveBeenCalled();
    expect(env.surface.dispatchEvent).not.toHaveBeenCalled();
    await home.unmount();
  });

  it('cancels delayed share searches when leaving files', async () => {
    const env = browser(), page = pageHarness('MyFilesPage', env);
    await page.mount();
    page.bindings.shareDialogOpen.value = true;
    page.bindings.shareDialogMode.value = 'add';
    page.bindings.shareKeyword.value = '测试';
    const searchWatch = page.watchers.find(watcher => Array.isArray(watcher.value) && watcher.value[0] === page.bindings.shareKeyword);
    searchWatch.callback();
    expect(vi.getTimerCount()).toBe(1);
    await page.unmount();
    await vi.advanceTimersByTimeAsync(250);
    expect(page.bindings.runShareSearch).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('opens a direct file-directory URL and cleans up without requiring the home page first', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/my-files', name: 'my-files', component: {} }]
    });
    await router.push('/my-files?parentId=folder');
    const env = browser(), page = pageHarness('MyFilesPage', env, router);
    page.bindings.currentParentId.value = router.currentRoute.value.query.parentId;
    await page.mount();
    page.directoryChanged();
    expect(router.currentRoute.value.fullPath).toBe('/my-files?parentId=folder');
    await page.unmount();
    expect(env.count()).toBe(0);
  });

  it('switches home → files → folder → home → shared → workspaces without a reload', async () => {
    const env = browser();
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'home', component: {} }, { path: '/my-files', name: 'my-files', component: {} },
        { path: '/shared', name: 'shared', component: {} }, { path: '/workspaces', name: 'workspaces', component: {} }
      ]
    });
    const pages = { home:'HomePage', 'my-files':'MyFilesPage', shared:'SharedFilesPage', workspaces:'WorkspacesPage' };
    let current, name;
    for (const url of ['/', '/my-files', '/my-files?parentId=folder', '/', '/shared', '/workspaces', '/my-files', '/']) {
      await router.push(url);
      const nextName = router.currentRoute.value.name;
      if (name !== nextName) {
        if (current) { await current.unmount(); expect(env.count()).toBe(0); }
        current = pageHarness(pages[nextName], env, router);
        await current.mount(); name = nextName;
      } else current.directoryChanged();
      expect(router.currentRoute.value.fullPath).toBe(url);
    }
    await current.unmount();
    expect(env.count()).toBe(0);
  });

  it.each(['HomePage', 'MyFilesPage'])('cold-starts and leaves %s cleanly', async name => {
    const env = browser(), page = pageHarness(name, env);
    await page.mount();
    if (name === 'MyFilesPage') page.directoryChanged();
    await page.unmount();
    expect(env.count()).toBe(0);
  });
});
