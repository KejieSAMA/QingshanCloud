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

import { searchFiles } from '../api/cloudnext';

describe('cloudnext search API', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    localStorageMock.getItem.mockReset();
    localStorageMock.setItem.mockReset();
    localStorageMock.removeItem.mockReset();
  });

  it('serializes query params and attaches the auth header', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ items: [], page: 1, size: 20, total: 0 })
    });

    await searchFiles({
      scope: 'workspace',
      keyword: 'plan',
      itemType: 'file',
      owner: 'person-a',
      updatedFrom: '2026-05-01T00:00:00Z',
      updatedTo: '2026-05-31T23:59:59Z',
      page: 2,
      size: 50
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe(
      '/api/search/files?scope=workspace&keyword=plan&itemType=file&owner=person-a'
        + '&updatedFrom=2026-05-01T00%3A00%3A00Z&updatedTo=2026-05-31T23%3A59%3A59Z&page=2&size=50'
    );
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });
});
