import { beforeEach, describe, expect, it, vi } from 'vitest';

const fetchMock = vi.fn();
const localStorageMock = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn()
};

if (typeof globalThis.window === 'undefined') {
  globalThis.window = globalThis;
}
globalThis.fetch = fetchMock;
globalThis.localStorage = localStorageMock;
globalThis.window.localStorage = localStorageMock;

vi.mock('../session', () => ({
  clearStoredSession: vi.fn(),
  getStoredToken: () => 'token-123'
}));

import {
  copyDriveItems,
  deleteWorkspace,
  getDriveSortPreference,
  getWorkspaceItemsQuery,
  getWorkspaceSortPreference,
  updateWorkspace,
  updateWorkspaceSortPreference,
  updateDriveSortPreference
} from '../api/cloudnext';

describe('cloudnext workspace API', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    localStorageMock.getItem.mockReset();
    localStorageMock.setItem.mockReset();
    localStorageMock.removeItem.mockReset();
  });

  it('patches a workspace name with auth headers', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ id: 'ws-1', name: 'New Name' })
    });

    await updateWorkspace('ws-1', { name: 'New Name' });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/workspaces/ws-1');
    expect(options.method).toBe('PATCH');
    expect(options.body).toBe(JSON.stringify({ name: 'New Name' }));
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });

  it('deletes a workspace with auth headers', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => ''
    });

    await deleteWorkspace('ws-1');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/workspaces/ws-1');
    expect(options.method).toBe('DELETE');
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });

  it('gets workspace sort preference with auth headers', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ sortMode: 'TIME' })
    });

    await getWorkspaceSortPreference();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/workspaces/sort-preference');
    expect(options.method).toBeUndefined();
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });

  it('updates workspace sort preference with auth headers', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ sortMode: 'NAME' })
    });

    await updateWorkspaceSortPreference('NAME');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/workspaces/sort-preference');
    expect(options.method).toBe('PUT');
    expect(options.body).toBe(JSON.stringify({ sortMode: 'NAME' }));
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });

  it('queries paged workspace directory items', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => JSON.stringify({ items: [] }) });
    await getWorkspaceItemsQuery('ws-1', { parentId: 'folder-1', page: 2, size: 50, sortBy: 'name', sortOrder: 'asc' });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/workspaces/ws-1/items/query?parentId=folder-1&page=2&size=50&sortBy=name&sortOrder=asc');
  });

  it('persists a directory sort preference', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => JSON.stringify({ context: 'my-files', sortBy: 'name', sortOrder: 'asc' }) });
    await getDriveSortPreference('my-files');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/drive/preferences/sort?context=my-files');
    await updateDriveSortPreference({ context: 'my-files', sortBy: 'name', sortOrder: 'asc' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/drive/preferences/sort');
    expect(fetchMock.mock.calls[1][1].method).toBe('PUT');
  });

  it('posts a cross-scope copy request', async () => {
    fetchMock.mockResolvedValue({ ok: true, text: async () => JSON.stringify({ totalSucceeded: 1 }) });
    const payload = { itemIds: ['a'], targetScopeType: 'WORKSPACE', targetScopeId: 'ws-1', targetParentId: '', conflictStrategy: 'keep_both' };
    await copyDriveItems(payload);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/drive/items/copy');
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify(payload));
  });
});
