const nameOrder = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' });

// Workspace directories arrive as complete lists; sort raw values, never display labels.
export function sortDirectoryItems(items, sortBy, sortOrder) {
  const direction = sortOrder === 'asc' ? 1 : -1;
  return [...items].sort((a, b) => {
    const folders = Number(b.itemType === 'folder') - Number(a.itemType === 'folder');
    if (folders) return folders;
    let compared;
    if (sortBy === 'sizeBytes') {
      compared = Number(a.sizeBytes || 0) - Number(b.sizeBytes || 0);
    } else if (sortBy === 'name') {
      compared = nameOrder.compare(a.name || '', b.name || '');
    } else {
      compared = (Date.parse(a[sortBy]) || 0) - (Date.parse(b[sortBy]) || 0);
    }
    return compared * direction || nameOrder.compare(a.name || '', b.name || '') || String(a.id).localeCompare(String(b.id));
  });
}
