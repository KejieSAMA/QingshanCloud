import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const storage = new Map();
function browser(embed = true) {
  const target = new EventTarget();
  const surface = { location: { search: embed ? '?embed=1&parentOrigin=https%3A%2F%2Foa.test&channel=' + 'a'.repeat(32) + '&entryId=default' : '' }, parent: {},
    localStorage: { getItem: vi.fn(key => storage.get(key) || null), setItem: vi.fn((k,v) => storage.set(k,v)), removeItem: vi.fn(k => storage.delete(k)) },
    addEventListener: target.addEventListener.bind(target), dispatchEvent: target.dispatchEvent.bind(target) };
  vi.stubGlobal('window', surface); return surface;
}
beforeEach(() => { vi.resetModules(); storage.clear(); });
afterEach(() => vi.unstubAllGlobals());
it('embed sessions are memory-only even if third-party storage throws', async () => {
  const surface = browser(); surface.localStorage.getItem.mockImplementation(() => { throw Error('blocked'); });
  const s = await import('../session.js');
  s.setStoredSession({ token: 'private-cloud-session', personKey: 'alice', drive: { id: 'b'.repeat(64), entryId: 'default' } });
  expect(s.getStoredToken()).toBe('private-cloud-session');expect(s.getStoredSession().personKey).toBe('alice');
  expect(surface.localStorage.setItem).not.toHaveBeenCalled(); s.clearStoredSession();expect(s.getStoredToken()).toBe('');
});
it('standalone sessions keep their existing storage contract', async () => {
  const surface=browser(false);const s=await import('../session.js');s.setStoredSession({token:'standalone'});
  expect(surface.localStorage.setItem).toHaveBeenCalled();expect(s.getStoredToken()).toBe('standalone');
});
it('checks both message origin and the actual parent window', async () => {
  const surface=browser();const c=await import('../embedded/context.js');
  expect(c.trustedParent(['https://oa.test'])).toBe(true);expect(c.trustedParent(['https://evil.test'])).toBe(false);
  const event={origin:'https://oa.test',source:surface.parent,data:{protocol:'oa-cloudnext',version:1,channel:'a'.repeat(32),type:'authenticate'}};
  expect(c.validEnvelope(event,surface.parent,'https://oa.test','a'.repeat(32))).toBe(true);
  expect(c.validEnvelope({...event,source:{}},surface.parent,'https://oa.test','a'.repeat(32))).toBe(false);
  expect(c.validEnvelope({...event,origin:'https://evil.test'},surface.parent,'https://oa.test','a'.repeat(32))).toBe(false);
});
it('an expired embedded session returns to the bridge instead of the password form', async () => {
  browser();const { loginRouteFor }=await import('../authNavigation.js');expect(loginRouteFor('/my-files').name).toBe('oa-embed');
});
it('keeps the namespace and embed context on SPA navigation', async () => {
  browser();const d=await import('../driveContext.js');expect(d.withDriveContext('/workspaces')).toContain('embed=1');expect(d.withDriveContext('/workspaces')).toContain('entryId=default');
});
it('one bounded mobile shell serves every list without truncating its data', () => {
  const shell=readFileSync(new URL('../components/mobile/MobileShell.vue',import.meta.url),'utf8');
  expect(shell).toContain('height: 100%');expect(shell).toContain('min-height: 0');expect(shell).toContain('flex: 1 1 0%');expect(shell).toContain('v-if="!embedded"');
  const scope=readFileSync(new URL('../mobile/styles/mobile.css',import.meta.url),'utf8');expect(scope).toContain('[data-shell="app"]');
});
it('resizing updates the experience attribute alongside reactive layout state', async () => {
  await import('vue');
  const callbacks=[];vi.stubGlobal('window',{innerWidth:390,addEventListener:(_e,fn)=>callbacks.push(fn)});vi.stubGlobal('document',{documentElement:{dataset:{}}});
  const {useBreakpoint}=await import('../composables/useBreakpoint.js');const state=useBreakpoint();expect(state.isMobile.value).toBe(true);
  window.innerWidth=900;callbacks[0]();expect(state.isMobile.value).toBe(false);expect(document.documentElement.dataset.experience).toBe('desktop');
});
it('a native phone retains its mobile shell when rotated',async()=>{
  await import('vue');const surface=browser();surface.location.search+='&device=mobile';surface.innerWidth=390;
  const callbacks=[];surface.addEventListener=(_name,callback)=>callbacks.push(callback);
  vi.stubGlobal('document',{documentElement:{dataset:{}}});const {useBreakpoint}=await import('../composables/useBreakpoint.js');const state=useBreakpoint();
  surface.innerWidth=900;callbacks[0]();expect(state.isMobile.value).toBe(true);expect(document.documentElement.dataset.experience).toBe('mobile');
});
it('older WebViews receive the array and own-property operations used by navigation',()=>{
  const compatibility=readFileSync(new URL('../embedded/runtimeCompatibility.js',import.meta.url),'utf8');
  const result=runInNewContext("Array.prototype.at=undefined; Object.hasOwn=undefined;"+compatibility+"; [[1,2,3].at(-1),[1].at(2),Object.hasOwn({x:1},'x'),Object.hasOwn(Object.create({x:1}),'x')]");
  expect(Array.from(result)).toEqual([3,undefined,true,false]);
});
