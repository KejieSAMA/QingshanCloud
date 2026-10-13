export function searchResultLocation(item, openFolder = false) {
  if (item.resultType === 'workspace') return { name: 'workspace-detail', params: { id: item.workspaceId } };
  const parentId = openFolder && item.resultType === 'folder' ? item.id : item.locationParentId || '';
  if (item.source === 'workspace') return { name: 'workspace-detail', params: { id: item.workspaceId }, query: parentId ? { parentId } : {} };
  if (item.source === 'shared') return { name: 'shared', query: { tab: 'incoming', ...(parentId ? { shareId: item.shareId, parentId } : {}) } };
  return { name: 'my-files', query: parentId ? { parentId } : {} };
}

export function highlightSearchName(name, keyword) {
  const text = String(name || '');
  const terms = String(keyword || '').trim().split(/\s+/).filter(Boolean);
  const lower = text.toLowerCase();
  const ranges = [];
  for (const term of terms) {
    const needle = term.toLowerCase();
    let at = lower.indexOf(needle);
    while (at !== -1) {
      ranges.push([at, at + needle.length]);
      at = lower.indexOf(needle, at + needle.length);
    }
  }
  ranges.sort((a, b) => a[0] - b[0]);
  const merged = [];
  for (const range of ranges) {
    const last = merged[merged.length - 1];
    if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
    else merged.push([...range]);
  }
  const parts = []; let cursor = 0;
  for (const [start, end] of merged) {
    if (start > cursor) parts.push({ text: text.slice(cursor, start), match: false });
    parts.push({ text: text.slice(start, end), match: true }); cursor = end;
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), match: false });
  return parts;
}

export function searchDateBoundary(value, nextDay = false) {
  if (!value) return '';
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day + (nextDay ? 1 : 0)).toISOString();
}
