import { computed, onUnmounted, ref, watch } from 'vue';
import {
  getDrivePath,
  getDriveSortPreference,
  getMyDriveItemsQuery,
  getWorkspaceItemsQuery,
  updateDriveSortPreference,
  isAbortError
} from '../../api/cloudnext';
import { formatFileSize, formatTime } from '../../utils/formatters';
import { appendBreadcrumb, canReuseBreadcrumbs, cloneBreadcrumbs, sliceBreadcrumbsTo } from './navigation';
import { createRequestTracker } from './requestTracker';

export function useDriveDirectoryState({
  route,
  router,
  onRouteChangeStart = () => {},
  scope = { type: 'personal' }
} = {}) {
  const isWorkspaceScope = scope && scope.type === 'workspace';
  const sortContext = isWorkspaceScope ? 'workspace-directory' : 'my-files';
  const activeFilter = ref('all');
  const sortBy = ref('updatedAt');
  const sortOrder = ref('desc');
  const page = ref(1);
  const pageSize = ref(50);
  const loading = ref(false);
  const items = ref([]);
  const total = ref(0);
  const totalPages = ref(0);
  const breadcrumbs = ref([]);

  const currentParentId = computed(() => (typeof route.query.parentId === 'string' ? route.query.parentId : ''));
  const displayItems = computed(() =>
    items.value.map((item) => ({
      ...item,
      ownerAvatar: item.ownerName?.slice(0, 1) || 'U',
      modifiedLabel: formatTime(item.updatedAt),
      sizeLabel: item.itemType === 'folder' ? '--' : formatFileSize(item.sizeBytes)
    }))
  );

  const requestTracker = createRequestTracker();
  const breadcrumbHint = ref(null);
  let loadTimer = null;
  let initialized = false;

  const buildQuery = () => ({
    parentId: currentParentId.value,
    page: page.value,
    size: pageSize.value,
    sortBy: sortBy.value,
    sortOrder: sortOrder.value,
    itemType: activeFilter.value === 'all' ? '' : activeFilter.value
  });

  const resolveHintedBreadcrumbs = () => {
    const hint = breadcrumbHint.value;
    if (!hint || hint.parentId !== currentParentId.value) {
      return null;
    }
    return canReuseBreadcrumbs(hint.breadcrumbs, currentParentId.value) ? cloneBreadcrumbs(hint.breadcrumbs) : null;
  };

  const refreshCurrentDirectory = async ({ preferredBreadcrumbs = null } = {}) => {
    const reusableBreadcrumbs = canReuseBreadcrumbs(preferredBreadcrumbs, currentParentId.value)
      ? cloneBreadcrumbs(preferredBreadcrumbs)
      : null;
    const { requestId, signal } = requestTracker.begin();
    loading.value = true;
    try {
      if (isWorkspaceScope) {
        const itemsResponse = await getWorkspaceItemsQuery(scope.id, buildQuery(), { signal });
        if (!requestTracker.isCurrent(requestId)) {
          return;
        }
        items.value = Array.isArray(itemsResponse?.items) ? itemsResponse.items : [];
        total.value = Number(itemsResponse?.total || 0);
        totalPages.value = Number(itemsResponse?.totalPages || 0);
        page.value = Number(itemsResponse?.page || page.value);
        pageSize.value = Number(itemsResponse?.size || pageSize.value);
        breadcrumbs.value = reusableBreadcrumbs || [];
      } else {
        const [drivePage, path] = await Promise.all([
          getMyDriveItemsQuery(buildQuery(), { signal }),
          reusableBreadcrumbs !== null
            ? Promise.resolve(reusableBreadcrumbs)
            : currentParentId.value
              ? getDrivePath(currentParentId.value, { signal })
              : Promise.resolve([])
        ]);
        if (!requestTracker.isCurrent(requestId)) {
          return;
        }
        items.value = Array.isArray(drivePage?.items) ? drivePage.items : [];
        total.value = Number(drivePage?.total || 0);
        totalPages.value = Number(drivePage?.totalPages || 0);
        page.value = Number(drivePage?.page || page.value);
        pageSize.value = Number(drivePage?.size || pageSize.value);
        breadcrumbs.value = Array.isArray(path) ? path : [];
      }
    } catch (error) {
      if (isAbortError(error) || !requestTracker.isCurrent(requestId)) {
        return;
      }
      items.value = [];
      if (reusableBreadcrumbs !== null || !currentParentId.value) {
        breadcrumbs.value = reusableBreadcrumbs || [];
      }
      throw error;
    } finally {
      if (requestTracker.isCurrent(requestId)) {
        loading.value = false;
      }
    }
  };

  const scheduleLoad = (resetPage = false) => {
    if (resetPage) {
      page.value = 1;
    }
    window.clearTimeout(loadTimer);
    loadTimer = window.setTimeout(() => {
      void refreshCurrentDirectory({ preferredBreadcrumbs: breadcrumbs.value }).catch(() => {
        items.value = [];
      });
    }, 220);
  };

  const goToFolder = async (parentId, nextBreadcrumbs = null) => {
    const normalizedParentId = parentId || '';
    breadcrumbHint.value = {
      parentId: normalizedParentId,
      breadcrumbs: cloneBreadcrumbs(nextBreadcrumbs)
    };
    if (isWorkspaceScope) {
      await router.push({
        name: 'workspace-detail',
        params: { id: scope.id },
        query: normalizedParentId ? { parentId: normalizedParentId } : {}
      });
    } else {
      await router.push({
        name: 'my-files',
        query: normalizedParentId ? { parentId: normalizedParentId } : {}
      });
    }
  };

  const goToRoot = async () => {
    await goToFolder('', []);
  };

  const onOpenFolder = async (item) => {
    if (item.itemType !== 'folder') {
      return;
    }
    await goToFolder(item.id, appendBreadcrumb(breadcrumbs.value, item));
  };

  const goToBreadcrumb = async (parentId) => {
    await goToFolder(parentId, sliceBreadcrumbsTo(breadcrumbs.value, parentId));
  };

  const applyFilter = (value) => {
    activeFilter.value = value;
  };

  const changePage = (nextPage) => {
    page.value = Math.max(1, nextPage);
    scheduleLoad(false);
  };

  watch(
    currentParentId,
    () => {
      const preferredBreadcrumbs = resolveHintedBreadcrumbs();
      breadcrumbHint.value = null;
      page.value = 1;
      onRouteChangeStart();
      void refreshCurrentDirectory({ preferredBreadcrumbs }).catch(() => {
        items.value = [];
        breadcrumbs.value = preferredBreadcrumbs || [];
      });
    },
    { immediate: false }
  );

  watch([activeFilter, pageSize], () => {
    scheduleLoad(true);
  });

  watch([sortBy, sortOrder], () => {
    if (initialized) {
      scheduleLoad(true);
    }
  });

  const setSort = (key, order) => {
    sortBy.value = key;
    sortOrder.value = order === 'asc' ? 'asc' : 'desc';
    void updateDriveSortPreference({
      context: sortContext,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value
    }).catch(() => {});
  };
  const toggleSort = (key) => setSort(
    key,
    sortBy.value === key ? (sortOrder.value === 'asc' ? 'desc' : 'asc') : (key === 'name' ? 'asc' : 'desc')
  );

  const initialize = async () => {
    try {
      const preference = await getDriveSortPreference(sortContext);
      if (preference?.sortBy) sortBy.value = preference.sortBy;
      if (preference?.sortOrder) sortOrder.value = preference.sortOrder;
    } catch (error) {
      // Directory data remains available when preference loading fails.
    }
    initialized = true;
    await refreshCurrentDirectory({ preferredBreadcrumbs: resolveHintedBreadcrumbs() });
  };

  void initialize().catch(() => {
    items.value = [];
  });

  onUnmounted(() => {
    window.clearTimeout(loadTimer);
    requestTracker.cancel();
  });

  return {
    sortBy,
    sortOrder,
    toggleSort,
    setSort,
    activeFilter,
    page,
    pageSize,
    loading,
    items,
    total,
    totalPages,
    breadcrumbs,
    currentParentId,
    displayItems,
    refreshCurrentDirectory,
    applyFilter,
    changePage,
    goToFolder: goToBreadcrumb,
    goToRoot,
    onOpenFolder
  };
}
