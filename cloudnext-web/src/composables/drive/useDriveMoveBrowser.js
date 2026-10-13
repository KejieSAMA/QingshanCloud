import { computed, onUnmounted, ref } from 'vue';
import { getDrivePath, getMyDriveItemsQuery, isAbortError } from '../../api/cloudnext';
import { appendBreadcrumb, canReuseBreadcrumbs, cloneBreadcrumbs, sliceBreadcrumbsTo } from './navigation';
import { createRequestTracker } from './requestTracker';

export function useDriveMoveBrowser() {
  const moveDialogOpen = ref(false);
  const moveDialogItems = ref([]);
  const moveBrowseId = ref('');
  const moveBreadcrumbs = ref([]);
  const moveFolders = ref([]);
  const moveLoading = ref(false);
  const moveConflictStrategy = ref('abort');

  const requestTracker = createRequestTracker();

  const moveBrowseParentId = computed(() => {
    if (moveBreadcrumbs.value.length <= 1) {
      return '';
    }
    return moveBreadcrumbs.value[moveBreadcrumbs.value.length - 2]?.id || '';
  });

  const moveDialogPathText = computed(() => {
    if (!moveBreadcrumbs.value.length) {
      return '根目录';
    }
    return ['根目录', ...moveBreadcrumbs.value.map((item) => item.name)].join(' > ');
  });

  const loadMoveFolders = async ({ preferredBreadcrumbs = null } = {}) => {
    const reusableBreadcrumbs = canReuseBreadcrumbs(preferredBreadcrumbs, moveBrowseId.value)
      ? cloneBreadcrumbs(preferredBreadcrumbs)
      : null;
    const { requestId, signal } = requestTracker.begin();
    moveLoading.value = true;
    try {
      const [drivePage, path] = await Promise.all([
        getMyDriveItemsQuery(
          {
            parentId: moveBrowseId.value,
            page: 1,
            size: 100,
            itemType: 'folder',
            sortBy: 'name',
            sortOrder: 'asc'
          },
          { signal }
        ),
        reusableBreadcrumbs !== null
          ? Promise.resolve(reusableBreadcrumbs)
          : moveBrowseId.value
            ? getDrivePath(moveBrowseId.value, { signal })
            : Promise.resolve([])
      ]);
      if (!requestTracker.isCurrent(requestId)) {
        return;
      }
      moveFolders.value = Array.isArray(drivePage?.items) ? drivePage.items.filter((item) => item.itemType === 'folder') : [];
      moveBreadcrumbs.value = Array.isArray(path) ? path : [];
    } catch (error) {
      if (isAbortError(error) || !requestTracker.isCurrent(requestId)) {
        return;
      }
      moveFolders.value = [];
      if (reusableBreadcrumbs !== null || !moveBrowseId.value) {
        moveBreadcrumbs.value = reusableBreadcrumbs || [];
      }
      throw error;
    } finally {
      if (requestTracker.isCurrent(requestId)) {
        moveLoading.value = false;
      }
    }
  };

  const openMoveDialog = async (targetItems) => {
    moveDialogItems.value = Array.isArray(targetItems) ? targetItems : [];
    moveBrowseId.value = '';
    moveConflictStrategy.value = 'abort';
    moveDialogOpen.value = true;
    await loadMoveFolders({ preferredBreadcrumbs: [] });
  };

  const moveBrowseInto = async (folder) => {
    moveBrowseId.value = folder.id;
    await loadMoveFolders({ preferredBreadcrumbs: appendBreadcrumb(moveBreadcrumbs.value, folder) });
  };

  const moveBrowseToRoot = async () => {
    moveBrowseId.value = '';
    await loadMoveFolders({ preferredBreadcrumbs: [] });
  };

  const moveBrowseToParent = async () => {
    const targetParentId = moveBrowseParentId.value;
    moveBrowseId.value = targetParentId;
    await loadMoveFolders({ preferredBreadcrumbs: sliceBreadcrumbsTo(moveBreadcrumbs.value, targetParentId) });
  };

  const closeMoveDialog = () => {
    moveDialogOpen.value = false;
    moveDialogItems.value = [];
    moveBrowseId.value = '';
    moveBreadcrumbs.value = [];
    moveFolders.value = [];
    requestTracker.cancel();
  };

  onUnmounted(() => {
    requestTracker.cancel();
  });

  return {
    moveDialogOpen,
    moveDialogItems,
    moveBrowseId,
    moveBreadcrumbs,
    moveFolders,
    moveLoading,
    moveConflictStrategy,
    moveBrowseParentId,
    moveDialogPathText,
    loadMoveFolders,
    openMoveDialog,
    moveBrowseInto,
    moveBrowseToRoot,
    moveBrowseToParent,
    closeMoveDialog
  };
}
