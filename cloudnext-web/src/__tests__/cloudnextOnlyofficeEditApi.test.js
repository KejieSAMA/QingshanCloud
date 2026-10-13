import { beforeEach, describe, expect, it, vi } from 'vitest';

const fetchMock = vi.fn();
const openMock = vi.fn();

if (typeof globalThis.window === 'undefined') {
  globalThis.window = globalThis;
}

globalThis.fetch = fetchMock;
globalThis.window.open = openMock;

vi.mock('../session', () => ({
  clearStoredSession: vi.fn(),
  getStoredToken: () => 'token-123'
}));

import { editDriveItem, getDriveItemEdit } from '../api/cloudnext';

describe('cloudnext onlyoffice edit API', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    openMock.mockReset();
  });

  it('requests an edit ticket with the existing fetchJson auth style', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ supported: true, editPageUrl: '/edit/office?ticket=t1' })
    });

    await getDriveItemEdit('file-1');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/drive/items/file-1/edit');
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-CloudNext-Token': 'token-123'
    });
  });

  it('opens the returned edit page in a new window', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      text: async () => JSON.stringify({ supported: true, editPageUrl: '/edit/office?ticket=t1' })
    });
    openMock.mockReturnValue({});

    await editDriveItem({ id: 'file-1' });

    expect(openMock).toHaveBeenCalledWith('/edit/office?ticket=t1', '_blank', 'noopener');
  });
});
