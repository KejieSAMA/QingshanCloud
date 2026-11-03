import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const A = 'a'.repeat(64), B = 'b'.repeat(64);
const session = (id, entryId, token) => ({ token, personKey: 'same-person', personName: '测试用户', drive: { id, entryId, name: entryId } });
let data, store;
beforeEach(() => {
  vi.resetModules(); data = new Map();
  store = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  vi.stubGlobal('window', { localStorage: store, location: { search: '?driveId=' + A + '&entryId=cloud_a' }, dispatchEvent: vi.fn() });
  vi.stubGlobal('CustomEvent', class { constructor(type, options) { this.type = type; this.detail = options?.detail; } });
});
afterEach(() => vi.unstubAllGlobals());

describe('per-drive browser state', () => {
  it('never uses the legacy/default token for an exclusive entry', async () => {
    store.setItem('cloudnext.token', 'default-token');
    const api = await import('../session.js');
    expect(api.getStoredToken()).toBe('');
    api.setStoredSession(session(A, 'cloud_a', 'a-token'));
    expect(api.getStoredToken()).toBe('a-token');
    expect(store.getItem('cloudnext.token')).toBe('default-token');
  });
  it('keeps A, B and the default login independent, including logout', async () => {
    const api = await import('../session.js'), context = await import('../driveContext.js');
    store.setItem('cloudnext.token', 'default-token');
    api.setStoredSession(session(A, 'cloud_a', 'a-token'));
    context.setDriveRouteContext({ driveId: B, entryId: 'cloud_b' });
    expect(api.getStoredSession()).toBeNull();
    api.setStoredSession(session(B, 'cloud_b', 'b-token'));
    api.clearStoredSession();
    context.setDriveRouteContext({ driveId: A, entryId: 'cloud_a' });
    expect(api.getStoredToken()).toBe('a-token');
    expect(store.getItem('cloudnext.token')).toBe('default-token');
  });
  it('rejects a session from another drive before writing storage', async () => {
    const api = await import('../session.js');
    expect(() => api.setStoredSession(session(B, 'cloud_b', 'b-token'))).toThrow('不一致');
    expect(data.size).toBe(0);
    store.setItem('cloudnext.session.' + A, JSON.stringify(session(B, 'cloud_b', 'b-token')));
    expect(api.getStoredSession()).toBeNull();
  });
  it('preserves the drive through login and preview return paths', async () => {
    const context = await import('../driveContext.js');
    const path = new URL(context.withDriveContext('/preview/office?ticket=preview'), 'https://test.invalid');
    expect(path.searchParams.get('driveId')).toBe(A);
    expect(path.searchParams.get('entryId')).toBe('cloud_a');
    expect(path.searchParams.get('ticket')).toBe('preview');
    const absolute = new URL(context.withDriveContext('https://configured-web.test/preview/office?ticket=preview', true));
    expect(absolute.origin).toBe('https://configured-web.test');
    expect(absolute.pathname).toBe('/preview/office');
    expect(absolute.searchParams.get('driveId')).toBe(A);
  });
  it('checks the drive again after asynchronously reading an error body', async () => {
    const api = await import('../session.js'), context = await import('../driveContext.js');
    api.setStoredSession(session(A, 'cloud_a', 'a-token'));
    let finishBody, bodyStarted;
    const started = new Promise(resolve => { bodyStarted = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 401, text: () => { bodyStarted(); return new Promise(resolve => { finishBody = resolve; }); } })));
    const http = await import('../api/cloudnext.js'), pending = http.fetchJson('/api/drive/me/items');
    await started;
    context.setDriveRouteContext({ driveId: B, entryId: 'cloud_b' });
    api.setStoredSession(session(B, 'cloud_b', 'b-token'));
    finishBody(JSON.stringify({ message: 'expired' }));
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(api.getStoredToken()).toBe('b-token');
  });
  it('ignores an old drive 401 without clearing the new drive session', async () => {
    const api = await import('../session.js'), context = await import('../driveContext.js');
    api.setStoredSession(session(A, 'cloud_a', 'a-token'));
    let finish;
    vi.stubGlobal('fetch', vi.fn(() => new Promise(resolve => { finish = resolve; })));
    const http = await import('../api/cloudnext.js');
    const pending = http.fetchJson('/api/drive/me/items');
    context.setDriveRouteContext({ driveId: B, entryId: 'cloud_b' });
    api.setStoredSession(session(B, 'cloud_b', 'b-token'));
    finish(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }));
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
    expect(api.getStoredToken()).toBe('b-token');
  });
  it('scopes refresh and token events to the current drive', async () => {
    const api = await import('../session.js');
    api.setStoredSession(session(A, 'cloud_a', 'a-token'));
    api.emitDriveRefreshSignal();
    expect(store.getItem('cloudnext.drive.refresh')).toBeNull();
    expect(store.getItem('cloudnext.drive.refresh.' + A)).toBeTruthy();
    expect(api.isCurrentSessionStorageEvent({ key: 'cloudnext.token.' + B })).toBe(false);
    expect(api.isCurrentSessionStorageEvent({ key: 'cloudnext.token.' + A })).toBe(true);
  });
  it('recovers entry context from its own stored session for a drive-only bookmark', async () => {
    window.location.search = '?driveId=' + A;
    store.setItem('cloudnext.session.' + A, JSON.stringify(session(A, 'cloud_a', 'a-token')));
    store.setItem('cloudnext.token.' + A, 'a-token');
    const context = await import('../driveContext.js');
    expect(context.getActiveEntryId()).toBe('cloud_a');
    expect(context.getDriveStorageScope()).toBe(A);
  });
});
