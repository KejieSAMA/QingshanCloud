export function cloneBreadcrumbs(breadcrumbs = []) {
  return Array.isArray(breadcrumbs)
    ? breadcrumbs.map((item) => ({
        id: item?.id || '',
        name: item?.name || ''
      }))
    : [];
}

export function canReuseBreadcrumbs(breadcrumbs = [], targetId = '') {
  const normalized = cloneBreadcrumbs(breadcrumbs);
  if (!targetId) {
    return normalized.length === 0;
  }
  return normalized.at(-1)?.id === targetId;
}

export function appendBreadcrumb(breadcrumbs = [], folder) {
  const normalized = cloneBreadcrumbs(breadcrumbs);
  if (!folder?.id) {
    return normalized;
  }
  if (normalized.at(-1)?.id === folder.id) {
    return normalized;
  }
  return [
    ...normalized,
    {
      id: folder.id,
      name: folder.name || ''
    }
  ];
}

export function sliceBreadcrumbsTo(breadcrumbs = [], targetId = '') {
  if (!targetId) {
    return [];
  }
  const normalized = cloneBreadcrumbs(breadcrumbs);
  const index = normalized.findIndex((item) => item.id === targetId);
  return index >= 0 ? normalized.slice(0, index + 1) : [];
}
