// N+2.8 F1: HomePage 数据加载逻辑共用 composable
// 三个风格变体（A/B/C）的 HomePageStyle*.vue 共用此 composable，避免数据加载重复。
//
// 暴露：
//   - 状态：session / service / sync / stats / recentItems / incomingSharedItems / driveError
//   - 计算：greeting / dateString / normalizedRecentFiles / normalizedIncomingSharedFiles / filteredRecentFiles
//   - 交互：activeFilter / onPreview / onEdit / onDownload / onDelete
//   - 生命周期：自动 onMounted 拉取所有数据 + 监听 storage / 退出时清理

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  deleteDriveItem,
  downloadDriveItem,
  editDriveItem,
  getIncomingDriveShares,
  getRecentDriveItems,
  getStatus,
  getSyncStats,
  getSyncStatus,
  previewDriveItem
} from '../api/cloudnext';
import {
  CLOUDNEXT_DRIVE_REFRESH_EVENT,
  CLOUDNEXT_DRIVE_REFRESH_KEY,
  emitDriveRefreshSignal,
  getSessionPersonKey,
  getStoredSession
} from '../session';
import { formatFileSize, formatTime, getDateString, getGreeting } from '../utils/formatters.js';

export const isRenderableRecentItem = (item) => {
  if (!item) {
    return false;
  }
  return !(item.itemType === 'folder' && !String(item.name || '').trim());
};

export function useHomePageData() {
  const router = useRouter();
  const session = ref(getStoredSession());
  const service = ref({ phase: '--', orgSyncOutcome: '' });
  const sync = ref({ mode: '--', cron: '', lastOutcome: '', nextAction: '' });
  const stats = ref({
    units: 0,
    persons: 0,
    identities: 0,
    memberships: 0,
    groups: 0,
    roles: 0,
    capturedAt: ''
  });
  const recentItems = ref([]);
  const incomingSharedItems = ref([]);
  const driveError = ref('');
  const activeFilter = ref('all');

  const greeting = computed(() =>
    getGreeting(new Date().getHours(), session.value?.personName || getSessionPersonKey(session.value))
  );
  const dateString = computed(() => getDateString());

  const normalizedRecentFiles = computed(() =>
    recentItems.value
      .filter(isRenderableRecentItem)
      .map((item) => ({
        ...item,
        ownerAvatar: item.ownerName?.slice(0, 1) || 'U',
        modifiedLabel: formatTime(item.updatedAt),
        sizeLabel: item.itemType === 'folder' ? '--' : formatFileSize(item.sizeBytes)
      }))
  );

  const normalizedIncomingSharedFiles = computed(() =>
    incomingSharedItems.value
      .map((share) => {
        if (!share.item) {
          return null;
        }
        return {
          ...share.item,
          ownerAvatar: share.item.ownerName?.slice(0, 1) || 'U',
          allowPreview: share.allowPreview !== false,
          allowDownload: share.allowDownload !== false,
          allowEdit: String(share.effectivePermission || share.permission || '').toLowerCase() === 'edit',
          sharePermission: share.effectivePermission || share.permission,
          modifiedLabel: formatTime(share.item.updatedAt),
          sizeLabel: share.item.itemType === 'folder' ? '--' : formatFileSize(share.item.sizeBytes)
        };
      })
      .filter(Boolean)
  );

  const filteredRecentFiles = computed(() => {
    if (activeFilter.value === 'created') {
      return normalizedRecentFiles.value.filter(
        (item) => item.ownerName === (session.value?.personName || item.ownerName)
      );
    }
    if (activeFilter.value === 'shared') {
      return normalizedIncomingSharedFiles.value.filter((item) => item.itemType === 'file');
    }
    return normalizedRecentFiles.value;
  });

  const loadHomeData = async () => {
    session.value = getStoredSession();
    const [serviceStatus, syncStatus, syncStats] = await Promise.all([
      getStatus(),
      getSyncStatus(),
      getSyncStats()
    ]);
    service.value = serviceStatus || service.value;
    sync.value = syncStatus || sync.value;
    stats.value = syncStats || stats.value;
  };

  const loadRecentItems = async () => {
    driveError.value = '';
    try {
      recentItems.value = await getRecentDriveItems();
    } catch (error) {
      driveError.value = error.message || '最近文件加载失败';
    }
  };

  const loadIncomingSharedItems = async () => {
    try {
      const response = await getIncomingDriveShares({ page: 1, size: 20 });
      incomingSharedItems.value = Array.isArray(response?.items)
        ? response.items.filter((share) => share?.item)
        : [];
    } catch (error) {
      incomingSharedItems.value = [];
    }
  };

  const onPreview = async (item) => {
    if (item?.itemType === 'folder') {
      await router.push({ name: 'my-files', query: { parentId: item.id } });
      return;
    }
    try {
      await previewDriveItem(item);
      emitDriveRefreshSignal();
    } catch (error) {
      driveError.value = error.message || '预览失败';
    }
  };

  const onEdit = async (item) => {
    try {
      await editDriveItem(item);
    } catch (error) {
      driveError.value = error.message || '编辑失败';
    }
  };

  const onDownload = async (item) => {
    try {
      await downloadDriveItem(item);
      emitDriveRefreshSignal();
    } catch (error) {
      driveError.value = error.message || '下载失败';
    }
  };

  const onDelete = async (item) => {
    if (!window.confirm(`确认将"${item.name}"移入回收站吗？`)) {
      return;
    }
    try {
      await deleteDriveItem(item.id);
      emitDriveRefreshSignal();
      await loadRecentItems();
    } catch (error) {
      driveError.value = error.message || '删除失败';
    }
  };

  const onDriveRefresh = () => {
    void loadRecentItems();
    void loadIncomingSharedItems();
  };

  const onStorageChange = (event) => {
    if (event.key === CLOUDNEXT_DRIVE_REFRESH_KEY) {
      void loadRecentItems();
      void loadIncomingSharedItems();
    }
  };

  onMounted(async () => {
    window.addEventListener('storage', onStorageChange);
    window.addEventListener(CLOUDNEXT_DRIVE_REFRESH_EVENT, onDriveRefresh);
    try {
      await loadHomeData();
    } catch (error) {
      // 状态接口失败不阻塞文件加载
    }
    await loadRecentItems();
    await loadIncomingSharedItems();
  });

  onUnmounted(() => {
    window.removeEventListener('storage', onStorageChange);
    window.removeEventListener(CLOUDNEXT_DRIVE_REFRESH_EVENT, onDriveRefresh);
  });

  return {
    // 状态
    session,
    service,
    sync,
    stats,
    recentItems,
    incomingSharedItems,
    driveError,
    activeFilter,
    // 计算
    greeting,
    dateString,
    normalizedRecentFiles,
    normalizedIncomingSharedFiles,
    filteredRecentFiles,
    // 交互
    onPreview,
    onEdit,
    onDownload,
    onDelete,
    // 加载（外部可触发刷新）
    loadHomeData,
    loadRecentItems,
    loadIncomingSharedItems
  };
}

// 共用过滤 tab 常量（三个风格组件统一）
export const HOME_FILTER_TABS = [
  { key: 'all', label: '全部' },
  { key: 'created', label: '我创建的' },
  { key: 'shared', label: '共享给我的' }
];
