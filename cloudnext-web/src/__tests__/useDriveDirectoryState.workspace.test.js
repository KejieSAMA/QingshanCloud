import { describe, expect, it, vi } from 'vitest';
import { reactive, nextTick } from 'vue';

// The composable references `window.setTimeout` / `window.clearTimeout`
// (because it's designed for the browser). In vitest's default node
// environment there is no `window`. We don't want to pull in jsdom for this
// task, so we just point `window` at `globalThis` — Node already exposes
// `setTimeout` / `clearTimeout` on the global object.
if (typeof globalThis.window === 'undefined') {
  globalThis.window = globalThis;
}

// IMPORTANT: vi.mock must be hoisted above the imports of the modules that
// pull from `../api/cloudnext`. Vitest hoists `vi.mock` automatically, so we
// can keep the imports below.
vi.mock('../api/cloudnext', () => {
  return {
    getDrivePath: vi.fn(),
    getMyDriveItemsQuery: vi.fn(),
    getWorkspaceItemsQuery: vi.fn(),
    getDriveSortPreference: vi.fn().mockResolvedValue({ sortBy: 'updatedAt', sortOrder: 'desc' }),
    updateDriveSortPreference: vi.fn().mockResolvedValue({}),
    isAbortError: () => false
  };
});

import { useDriveDirectoryState } from '../composables/drive/useDriveDirectoryState';
import { getMyDriveItemsQuery, getWorkspaceItemsQuery } from '../api/cloudnext';

/**
 * The composable's `watch(currentParentId, ..., { immediate: true })` invokes
 * `refreshCurrentDirectory` synchronously, but the awaited promises inside it
 * resolve on the microtask queue. Drain a few ticks so we observe the side
 * effects.
 *
 * Validates: Requirements 5.2, 5.3, 14.2 (workspace scope reuses the same
 * directory state composable but goes through the workspace endpoint and
 * navigates back to the workspace-detail route).
 */
async function flushMicrotasks(times = 5) {
  for (let i = 0; i < times; i += 1) {
    // eslint-disable-next-line no-await-in-loop
    await Promise.resolve();
  }
}

describe('useDriveDirectoryState scope=workspace', () => {
  it('passes personal sorting to the server and returns to page one', async () => {
    vi.useFakeTimers();
    try {
      getMyDriveItemsQuery.mockReset().mockImplementation(async (query) => ({ items: [], total: 120, totalPages: 3, page: query.page, size: 50 }));
      const directory = useDriveDirectoryState({ route: reactive({ query: {} }), router: { push: vi.fn() } });
      await flushMicrotasks();
      directory.page.value = 3;
      directory.toggleSort('name');
      await nextTick();
      await vi.advanceTimersByTimeAsync(250);
      expect(getMyDriveItemsQuery).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, sortBy: 'name', sortOrder: 'asc' }), expect.any(Object));
      directory.toggleSort('name');
      await nextTick();
      await vi.advanceTimersByTimeAsync(250);
      expect(getMyDriveItemsQuery).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, sortBy: 'name', sortOrder: 'desc' }), expect.any(Object));
    } finally {
      vi.useRealTimers();
    }
  });
  it('uses the workspace endpoint and skips the personal path lookup', async () => {
    getMyDriveItemsQuery.mockReset();
    getWorkspaceItemsQuery
      .mockReset()
      .mockResolvedValue({ items: [{ id: 'item-1', name: 'a.txt', itemType: 'file' }], total: 1, totalPages: 1, page: 1, size: 50 });

    const route = reactive({ query: {} });
    const router = { push: vi.fn() };

    const directory = useDriveDirectoryState({
      route,
      router,
      scope: { type: 'workspace', id: 'ws-1' }
    });

    await flushMicrotasks();

    expect(getWorkspaceItemsQuery).toHaveBeenCalledWith('ws-1', expect.objectContaining({ parentId: '' }), expect.any(Object));
    expect(getMyDriveItemsQuery).not.toHaveBeenCalled();
    expect(directory.items.value).toHaveLength(1);
    expect(directory.items.value[0].id).toBe('item-1');
  });

  it('navigates to the workspace-detail route on goToFolder', async () => {
    getMyDriveItemsQuery.mockReset();
    getWorkspaceItemsQuery.mockReset().mockResolvedValue({ items: [], total: 0, totalPages: 0, page: 1, size: 50 });

    const route = reactive({ query: {} });
    const router = { push: vi.fn().mockResolvedValue() };

    const directory = useDriveDirectoryState({
      route,
      router,
      scope: { type: 'workspace', id: 'ws-2' }
    });

    await flushMicrotasks();

    await directory.onOpenFolder({
      id: 'folder-99',
      name: 'docs',
      itemType: 'folder'
    });

    expect(router.push).toHaveBeenCalledWith({
      name: 'workspace-detail',
      params: { id: 'ws-2' },
      query: { parentId: 'folder-99' }
    });
  });

  it('falls back to the personal endpoint when scope.type is omitted', async () => {
    getMyDriveItemsQuery.mockReset().mockResolvedValue({
      items: [],
      total: 0,
      totalPages: 0,
      page: 1,
      size: 50
    });
    getWorkspaceItemsQuery.mockReset();

    const route = reactive({ query: {} });
    const router = { push: vi.fn() };

    useDriveDirectoryState({ route, router });

    await flushMicrotasks();

    expect(getMyDriveItemsQuery).toHaveBeenCalled();
    expect(getWorkspaceItemsQuery).not.toHaveBeenCalled();
  });
});
