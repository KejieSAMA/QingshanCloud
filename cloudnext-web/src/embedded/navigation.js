import { isNavigationFailure } from 'vue-router';
import { withDriveContext } from '../driveContext.js';

export const NAVIGATION_VERSION = 1;
const routes = Object.freeze({ home: '/', files: '/my-files', shared: '/shared', workspaces: '/workspaces' });
export function cloudTabForRoute(route) {
  const path = route.path || new URL(route.fullPath, 'https://cloud.invalid').pathname;
  if (path === '/') return 'home';
  if (path === '/my-files') return 'files';
  if (path === '/shared') return 'shared';
  if (path === '/workspaces' || path.startsWith('/workspaces/')) return 'workspaces';
  return null;
}

// Serialize router work, keeping only the latest intent. A final router guard
// prevents a cancelled command from committing after an asynchronous guard.
export function createEmbeddedNavigation({ router, sessionKey, blocked, result }) {
  let active = null, queued = null, running = false, disposed = false;
  const seen = new Set();
  const valid = task => !disposed && !task.cancelled && Boolean(task.owner) && sessionKey() === task.owner;
  const removeGuard = router.beforeResolve(to => {
    if (active && to.path === active.path && (!valid(active) || blocked())) return false;
  });
  async function pump() {
    if (running || disposed) return;
    running = true;
    try {
      while (queued && !disposed) {
        const task = queued; queued = null; active = task;
        try {
          if (!valid(task) || blocked()) { result(task.requestId, { ok: false, cancelled: true }); continue; }
          // Restoring an already open section must keep its folder and filters.
          const failure = cloudTabForRoute(router.currentRoute.value) === task.tab
            ? undefined : await router.push(task.target);
          if (!valid(task) || isNavigationFailure(failure)) result(task.requestId, { ok: false, cancelled: true });
          else result(task.requestId, { ok: cloudTabForRoute(router.currentRoute.value) === task.tab });
        } catch (error) {
          result(task.requestId, valid(task) ? { ok: false, message: error?.message || '云盘导航失败，请重试。' } : { ok: false, cancelled: true });
        } finally { if (active === task) active = null; }
      }
    } finally { running = false; }
  }
  return {
    enqueue(data) {
      if (disposed || typeof data.requestId !== 'string' || !/^[a-f0-9]{32,64}$/.test(data.requestId) || typeof data.tab !== 'string' || !Object.hasOwn(routes, data.tab) || seen.has(data.requestId)) return;
      seen.add(data.requestId); if (seen.size > 64) seen.delete(seen.values().next().value);
      if (active) active.cancelled = true;
      if (queued) result(queued.requestId, { ok: false, cancelled: true });
      queued = { requestId: data.requestId, tab: data.tab, owner: sessionKey(), path: routes[data.tab], target: withDriveContext(routes[data.tab]), cancelled: false };
      void pump();
    },
    cancel(requestId) {
      if (active?.requestId === requestId) active.cancelled = true;
      if (queued?.requestId === requestId) { const task = queued; queued = null; result(task.requestId, { ok: false, cancelled: true }); }
    },
    reset() {
      if (active) active.cancelled = true;
      queued = null; seen.clear();
    },
    dispose() { this.reset(); disposed = true; removeGuard(); },
  };
}
