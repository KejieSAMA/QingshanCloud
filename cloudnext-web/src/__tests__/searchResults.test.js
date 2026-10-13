import { describe, it, expect } from 'vitest';
import { highlightSearchName, searchResultLocation, searchDateBoundary } from '../composables/drive/searchResults';

describe('search result navigation and presentation', () => {
  it('routes folders and files to their actual workspace', () => {
    const item = { id: 'child', resultType: 'folder', source: 'workspace', workspaceId: 'ws', locationParentId: 'parent' };
    expect(searchResultLocation(item, true)).toEqual({ name: 'workspace-detail', params: { id: 'ws' }, query: { parentId: 'child' } });
    expect(searchResultLocation(item).query.parentId).toBe('parent');
  });
  it('retains the share root for nested results without exposing a private parent', () => {
    const item = { id: 'file', resultType: 'file', source: 'shared', shareId: 'share', locationParentId: 'allowed' };
    expect(searchResultLocation(item)).toEqual({ name: 'shared', query: { tab: 'incoming', shareId: 'share', parentId: 'allowed' } });
    expect(searchResultLocation({ ...item, locationParentId: '' })).toEqual({ name: 'shared', query: { tab: 'incoming' } });
  });
  it('preserves literal markup and handles repeated overlapping matches as text', () => {
    const name = '<img onerror=x>合同合同.PDF';
    const parts = highlightSearchName(name, '合同 合同合同 pdf');
    expect(parts.map(p => p.text).join('')).toBe(name);
    expect(parts.filter(p => p.match).map(p => p.text)).toEqual(['合同合同', 'PDF']);
  });
  it('uses local inclusive days with an exclusive following-day boundary', () => {
    const start = new Date(searchDateBoundary('2026-09-09'));
    const end = new Date(searchDateBoundary('2026-09-09', true));
    expect(start.getHours()).toBe(0); expect(end.getDate()).toBe(10);
    expect(searchDateBoundary('')).toBe('');
  });
});
