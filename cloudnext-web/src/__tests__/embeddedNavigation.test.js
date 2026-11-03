import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { createRouter, createMemoryHistory } from 'vue-router';
import { createEmbeddedNavigation, cloudTabForRoute } from '../embedded/navigation.js';

const id = n => n.toString(16).padStart(32, '0');
let router, navigation, owner, blocked, results;
const settle = () => new Promise(resolve => setImmediate(resolve));
beforeEach(async () => {
  owner = 'alice'; blocked = false; results = vi.fn();
  router = createRouter({ history: createMemoryHistory(), routes: ['/', '/my-files', '/shared', '/workspaces', '/workspaces/:id', '/trash'].map(path => ({ path, component: {} })) });
  router.onError(() => {}); await router.push('/'); await router.isReady();
  navigation = createEmbeddedNavigation({ router, sessionKey: () => owner, blocked: () => blocked, result: results });
});
afterEach(() => navigation.dispose());
it.each([['/', 'home'], ['/my-files', 'files'], ['/shared', 'shared'], ['/workspaces/p001', 'workspaces'], ['/trash', null]])('maps %s to its actual section', (path, tab) => {
  expect(cloudTabForRoute({ path })).toBe(tab);
});
it('restores the current section without resetting its directory and filters', async () => {
  await router.push('/workspaces/p001?folder=sub&sort=name'); const before = router.currentRoute.value.fullPath;
  navigation.enqueue({ requestId: id(1), tab: 'workspaces' }); await settle();
  expect(router.currentRoute.value.fullPath).toBe(before); expect(results).toHaveBeenCalledWith(id(1), { ok: true });
});
it('only accepts the four tab keys and deduplicates request IDs', async () => {
  const push = vi.spyOn(router, 'push');
  navigation.enqueue({ requestId: id(1), tab: 'https://evil.test' }); navigation.enqueue({ requestId: 'invalid', tab: 'files' });
  navigation.enqueue({ requestId: id(2), tab: 'files' }); navigation.enqueue({ requestId: id(2), tab: 'files' }); await settle();
  expect(push).toHaveBeenCalledTimes(1); expect(router.currentRoute.value.path).toBe('/my-files'); expect(results).toHaveBeenCalledTimes(1);
});
it('serializes slow guards and commits only the last requested section', async () => {
  let release; router.beforeEach(to => to.path === '/my-files' ? new Promise(resolve => { release = resolve; }) : true);
  const visited = []; router.afterEach((to, _from, failure) => { if (!failure) visited.push(to.path); });
  navigation.enqueue({ requestId: id(1), tab: 'files' }); await settle();
  navigation.enqueue({ requestId: id(2), tab: 'shared' }); release(true); await settle(); await settle();
  expect(visited).toEqual(['/shared']); expect(results).toHaveBeenCalledWith(id(1), { ok: false, cancelled: true }); expect(results).toHaveBeenCalledWith(id(2), { ok: true });
});
it('cancel and account changes prevent an asynchronous guard from committing', async () => {
  let release; router.beforeEach(to => to.path === '/my-files' ? new Promise(resolve => { release = resolve; }) : true);
  navigation.enqueue({ requestId: id(1), tab: 'files' }); await settle(); navigation.cancel(id(1)); owner = 'bob'; release(true); await settle();
  expect(router.currentRoute.value.path).toBe('/'); expect(results).toHaveBeenCalledWith(id(1), { ok: false, cancelled: true });
});
it('an existing modal keeps its editor and rejects footer navigation without closing it', async () => {
  blocked = true; navigation.enqueue({ requestId: id(1), tab: 'files' }); await settle();
  expect(router.currentRoute.value.path).toBe('/'); expect(results).toHaveBeenCalledWith(id(1), { ok: false, cancelled: true });
});
it('honors existing route guards and reports a real navigation failure once', async () => {
  router.beforeEach(to => { if (to.path === '/shared') throw Error('权限已变更'); return false; });
  navigation.enqueue({ requestId: id(1), tab: 'files' }); await settle(); expect(results).toHaveBeenCalledWith(id(1), { ok: false, cancelled: true });
  navigation.enqueue({ requestId: id(2), tab: 'shared' }); await settle();
  expect(results).toHaveBeenCalledWith(id(2), { ok: false, message: '权限已变更' }); expect(results).toHaveBeenCalledTimes(2);
});
it('reset cancels a previous session but allows a new authenticated request', async () => {
  navigation.reset(); owner = 'new-session'; navigation.enqueue({ requestId: id(1), tab: 'files' }); await settle();
  expect(router.currentRoute.value.path).toBe('/my-files'); expect(results).toHaveBeenCalledWith(id(1), { ok: true });
});
