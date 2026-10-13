import { describe, expect, it } from 'vitest';

import { isRenderableRecentItem } from '../composables/useHomePageData';

describe('useHomePageData.isRenderableRecentItem', () => {
  it('hides structural empty-name folders from the home recent list', () => {
    expect(isRenderableRecentItem({
      id: 'workspace-root',
      itemType: 'folder',
      name: ''
    })).toBe(false);

    expect(isRenderableRecentItem({
      id: 'blank-folder',
      itemType: 'folder',
      name: '   '
    })).toBe(false);
  });

  it('keeps normal files and named folders renderable', () => {
    expect(isRenderableRecentItem({
      id: 'file-1',
      itemType: 'file',
      name: ''
    })).toBe(true);

    expect(isRenderableRecentItem({
      id: 'folder-1',
      itemType: 'folder',
      name: 'docs'
    })).toBe(true);
  });
});
