export function itemLocation(item, folder = false) {
 if (item.resultType === 'workspace' || item.itemType === 'workspace') return { path: '/workspaces/' + item.workspaceId };
 const parentId = folder && item.itemType === 'folder' ? item.id : (item.locationParentId ?? item.parentId ?? '');
 if (item.source === 'workspace' || item.workspaceId) return { path: '/workspaces/' + item.workspaceId, query: parentId ? { parentId } : {} };
 if (item.source === 'shared') return { path: '/shared', query: { tab: 'incoming', ...(parentId ? { shareId: item.shareId, parentId } : {}) } };
 return { path: '/my-files', query: parentId ? { parentId } : {} };
}

// A workspace's persisted root DriveItem deliberately has an empty name.
// Display the authorized workspace name instead; never treat the root as a folder.
export function directoryBreadcrumbs(path = [], workspace = null) {
 const nodes = Array.isArray(path) ? path : [];
 const folders = nodes.filter(node => node?.id && (!workspace || node.id !== workspace.rootItemId))
  .map(node => ({ ...node, parentId: node.id }));
 if (!workspace) return folders;
 return [{ id: workspace.rootItemId || 'workspace:' + workspace.id, name: workspace.name || '空间根目录', parentId: '' }, ...folders];
}

export function breadcrumbLocation(route, crumb) {
 const query = { ...route.query, page: 1 };
 if (crumb.parentId) query.parentId = crumb.parentId;
 else delete query.parentId;
 return { path: route.path, query };
}
