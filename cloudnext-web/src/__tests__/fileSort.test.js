import { describe, expect, it } from 'vitest';
import { sortDirectoryItems } from '../composables/drive/fileSort';

describe('workspace directory sorting', () => {
  const items = [
    { id: '10', name: '10-档案.pdf', itemType: 'file', sizeBytes: 100, updatedAt: '2026-01-01T00:00:00Z' },
    { id: '2', name: '2-档案.pdf', itemType: 'file', sizeBytes: 2048, updatedAt: '2026-02-01T00:00:00Z' },
    { id: 'folder', name: '30-文件夹', itemType: 'folder' }
  ];
  it('sorts numbered names naturally, keeps folders first and preserves the input', () => {
    expect(sortDirectoryItems(items, 'name', 'asc').map(x => x.id)).toEqual(['folder', '2', '10']);
    expect(sortDirectoryItems(items, 'name', 'desc').map(x => x.id)).toEqual(['folder', '10', '2']);
    expect(items.map(x => x.id)).toEqual(['10', '2', 'folder']);
  });
  it('compares actual bytes and timestamps in both directions', () => {
    for (const key of ['sizeBytes', 'updatedAt']) {
      expect(sortDirectoryItems(items, key, 'asc').map(x => x.id)).toEqual(['folder', '10', '2']);
      expect(sortDirectoryItems(items, key, 'desc').map(x => x.id)).toEqual(['folder', '2', '10']);
    }
  });
});
