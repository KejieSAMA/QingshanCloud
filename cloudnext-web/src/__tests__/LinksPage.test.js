import { describe, expect, it, vi } from 'vitest';

// `useLinksPageState` calls `setTimeout` / `clearTimeout` through a thin
// adapter that prefers `window.setTimeout` when present. Vitest's default
// `node` environment exposes timers on `globalThis` but not on `window`. We
// simply alias `window` to `globalThis` (same trick used by
// `useDriveDirectoryState.workspace.test.js`) so the SUT runs without
// needing jsdom/happy-dom.
if (typeof globalThis.window === 'undefined') {
  globalThis.window = globalThis;
}

// `vi.mock` calls are hoisted above the imports below by vitest. The page
// composable resolves `listMyLinks` / `revokeLink` from the real module path
// in production; in the tests we drive them through explicit `deps` instead
// of relying on a module-level mock. The mock here is just a safety net so
// `import { listMyLinks } from '../api/cloudnext'` inside the composable
// never hits a real network call if a future refactor changes how the
// defaults are resolved.
vi.mock('../api/cloudnext', () => ({
  listMyLinks: vi.fn(),
  revokeLink: vi.fn(),
  updateLink: vi.fn(),
  createLink: vi.fn()
}));

import { useLinksPageState } from '../composables/drive/useLinksPageState';

/**
 * Drain the microtask queue several times so awaited promises resolve.
 * The composable awaits `listMyLinks()` / `revokeLink()` / clipboard writes,
 * each of which only resolves on the next microtask tick.
 */
async function flushMicrotasks(times = 5) {
  for (let i = 0; i < times; i += 1) {
    // eslint-disable-next-line no-await-in-loop
    await Promise.resolve();
  }
}

/**
 * Build a single representative link record. Keeping the shape close to the
 * server's `LinkProjection` lets the assertions reflect real wire data.
 */
function makeLink(overrides = {}) {
  return {
    id: 'L1',
    token: 't1',
    shareUrl: '/api/public/links/t1',
    fileName: 'a.txt',
    permission: 'preview',
    requirePassword: false,
    downloadLimit: -1,
    downloadCount: 0,
    expiresAt: '',
    revoked: false,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    ...overrides
  };
}

/** A `setTimeout` stub that records the schedule but never auto-fires. */
function makeManualTimers() {
  const calls = [];
  const setTimeoutFn = (cb, ms) => {
    calls.push({ cb, ms });
    return calls.length; // return a dummy handle
  };
  const clearTimeoutFn = vi.fn();
  return { setTimeoutFn, clearTimeoutFn, calls };
}

describe('LinksPage useLinksPageState', () => {
  it('refresh: 加载态翻转后落到列表态 (Requirement 9.2)', async () => {
    let resolveList;
    const listMyLinks = vi.fn(
      () => new Promise((resolve) => { resolveList = resolve; })
    );

    const state = useLinksPageState({ listMyLinks, revokeLink: vi.fn() });
    expect(state.loading.value).toBe(false);

    const refreshPromise = state.refresh();
    expect(state.loading.value).toBe(true);
    expect(state.links.value).toEqual([]);

    resolveList([makeLink()]);
    await refreshPromise;

    expect(state.loading.value).toBe(false);
    expect(state.links.value).toHaveLength(1);
    expect(state.links.value[0].fileName).toBe('a.txt');
    expect(state.errorText.value).toBe('');
    expect(state.summaryText.value).toBe('共 1 条外链');
  });

  it('refresh: 空态保持空数组并提供空态摘要 (Requirement 9.2)', async () => {
    const listMyLinks = vi.fn().mockResolvedValue([]);
    const state = useLinksPageState({ listMyLinks, revokeLink: vi.fn() });

    await state.refresh();

    expect(state.links.value).toEqual([]);
    expect(state.errorText.value).toBe('');
    expect(state.summaryText.value).toBe('共 0 条外链');
    // The template falls back to the empty-state copy whenever
    // `links.length === 0 && !loading`.
    expect(state.loading.value).toBe(false);
  });

  it('refresh: 错误态保留旧数据并写入 errorText (Requirement 9.7)', async () => {
    const listMyLinks = vi
      .fn()
      .mockResolvedValueOnce([makeLink()])
      .mockRejectedValueOnce(new Error('boom'));

    const state = useLinksPageState({ listMyLinks, revokeLink: vi.fn() });

    await state.refresh();
    expect(state.links.value).toHaveLength(1);

    await state.refresh();

    expect(state.errorText.value).toBe('boom');
    // Critical: the prior row is still present — the failed refresh did not
    // clear `links`. This is what Requirement 9.7 (错误时保留旧数据) means.
    expect(state.links.value).toHaveLength(1);
    expect(state.links.value[0].id).toBe('L1');
    expect(state.loading.value).toBe(false);
  });

  it('onCopy 成功路径：clipboard 写入并设置 successText (Requirement 9.4)', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    const { setTimeoutFn, clearTimeoutFn } = makeManualTimers();

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([makeLink()]),
      revokeLink: vi.fn(),
      clipboard: { writeText },
      execCommandFallback: vi.fn().mockReturnValue(false),
      setTimeoutFn,
      clearTimeoutFn
    });

    await state.refresh();
    await state.onCopy(state.links.value[0]);

    expect(writeText).toHaveBeenCalledWith('/api/public/links/t1');
    expect(state.successText.value).toBe('外链已复制到剪贴板');
    expect(state.errorText.value).toBe('');
  });

  it('onCopy 失败路径：clipboard reject + execCommand false 时回退提示 (Requirement 9.4)', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'));
    const execCommandFallback = vi.fn().mockReturnValue(false);
    const { setTimeoutFn, clearTimeoutFn } = makeManualTimers();

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([makeLink()]),
      revokeLink: vi.fn(),
      clipboard: { writeText },
      execCommandFallback,
      setTimeoutFn,
      clearTimeoutFn
    });

    await state.refresh();
    await state.onCopy(state.links.value[0]);

    expect(writeText).toHaveBeenCalled();
    expect(execCommandFallback).toHaveBeenCalledWith('/api/public/links/t1');
    expect(state.errorText.value).toBe('复制失败，请手动复制');
    expect(state.successText.value).toBe('');
  });

  it('onRevoke: 二次确认取消时不调用 revokeLink (Requirement 9.5)', async () => {
    const revokeLink = vi.fn();
    const confirm = vi.fn().mockReturnValue(false);

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([makeLink()]),
      revokeLink,
      confirm
    });

    await state.refresh();
    await state.onRevoke(state.links.value[0]);

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(revokeLink).not.toHaveBeenCalled();
    expect(state.links.value[0].revoked).toBe(false);
    expect(state.successText.value).toBe('');
    expect(state.errorText.value).toBe('');
  });

  it('onRevoke: 确认后用服务端响应替换该行 (Requirement 9.5, 9.6)', async () => {
    const original = makeLink();
    const serverProjection = { ...original, revoked: true, updatedAt: '2025-02-02T00:00:00Z' };
    const revokeLink = vi.fn().mockResolvedValue(serverProjection);
    const confirm = vi.fn().mockReturnValue(true);
    const { setTimeoutFn, clearTimeoutFn } = makeManualTimers();

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([original]),
      revokeLink,
      confirm,
      setTimeoutFn,
      clearTimeoutFn
    });

    await state.refresh();
    await state.onRevoke(state.links.value[0]);

    expect(revokeLink).toHaveBeenCalledWith('L1');
    expect(state.links.value).toHaveLength(1);
    // The row is the *server's* projection, not a locally-flipped clone.
    expect(state.links.value[0]).toBe(serverProjection);
    expect(state.links.value[0].revoked).toBe(true);
    expect(state.links.value[0].updatedAt).toBe('2025-02-02T00:00:00Z');
    expect(state.successText.value).toBe('外链已失效');
    expect(state.errorText.value).toBe('');
  });

  it('onRevoke: 失败时保留行 + 不写 successText (Requirement 9.7)', async () => {
    const original = makeLink();
    const revokeLink = vi.fn().mockRejectedValue(new Error('boom'));
    const confirm = vi.fn().mockReturnValue(true);

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([original]),
      revokeLink,
      confirm
    });

    await state.refresh();
    await state.onRevoke(state.links.value[0]);

    expect(revokeLink).toHaveBeenCalledWith('L1');
    expect(state.links.value).toHaveLength(1);
    expect(state.links.value[0].revoked).toBe(false);
    expect(state.errorText.value).toBe('boom');
    // Crucial: revoke failure must not leak a "外链已失效" success toast.
    expect(state.successText.value).toBe('');
  });

  it('onLinkUpdated: 用服务端响应替换该行字段 (Requirement 9.6)', async () => {
    const original = makeLink({
      permission: 'preview',
      downloadLimit: -1,
      downloadCount: 0
    });
    const serverProjection = {
      ...original,
      permission: 'download',
      downloadLimit: 5,
      downloadCount: 0,
      updatedAt: '2025-03-03T00:00:00Z'
    };
    const { setTimeoutFn, clearTimeoutFn } = makeManualTimers();

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([original]),
      revokeLink: vi.fn(),
      setTimeoutFn,
      clearTimeoutFn
    });

    await state.refresh();
    state.onLinkUpdated(serverProjection);
    await flushMicrotasks();

    // The row is the server projection itself — i.e. the page never tries to
    // build the new row out of the user-submitted form values.
    expect(state.links.value[0]).toBe(serverProjection);
    expect(state.links.value[0].permission).toBe('download');
    expect(state.links.value[0].downloadLimit).toBe(5);
    expect(state.successText.value).toBe('外链已更新');
    expect(state.errorText.value).toBe('');
  });

  it('错误时不回显成功：失败的 revoke 不会复用上一次的 successText (Requirement 9.7)', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    const original = makeLink();
    const revokeLink = vi.fn().mockRejectedValue(new Error('revoke failed'));
    const confirm = vi.fn().mockReturnValue(true);
    const { setTimeoutFn, clearTimeoutFn } = makeManualTimers();

    const state = useLinksPageState({
      listMyLinks: vi.fn().mockResolvedValue([original]),
      revokeLink,
      confirm,
      clipboard: { writeText },
      execCommandFallback: vi.fn().mockReturnValue(false),
      setTimeoutFn,
      clearTimeoutFn
    });

    await state.refresh();

    // Step 1: a successful copy populates `successText`.
    await state.onCopy(state.links.value[0]);
    expect(state.successText.value).toBe('外链已复制到剪贴板');

    // Step 2: a failing revoke must NOT overwrite that success message with a
    // bogus "外链已失效", AND must populate `errorText` instead. This is the
    // contract Requirement 9.7 calls out as "错误时不回显成功".
    await state.onRevoke(state.links.value[0]);

    expect(state.errorText.value).toBe('revoke failed');
    // The "外链已失效" success copy is the one we explicitly forbid here.
    expect(state.successText.value).not.toBe('外链已失效');
    // The earlier copy success can still be visible (it's on a 2s timer that
    // hasn't fired in this fake-timer setup), but the failed action did not
    // *write* a new success message.
    expect(state.successText.value).toBe('外链已复制到剪贴板');
  });
});
