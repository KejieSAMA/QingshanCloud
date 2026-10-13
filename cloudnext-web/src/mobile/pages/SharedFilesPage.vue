<template>
  <section class="cloudnext-page drive-directory-page" :class="{ 'm-page-flush': true }">


      <div v-if="tab === 'incoming' && parentId" class="m-mp-breadcrumb">
        <button class="m-mp-back m-tap" type="button" @click="onMobileGoBack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          <span>上一级</span>
        </button>
        <span class="m-mp-current">{{ mobileCurrentFolderName }}</span>
      </div>
      <MobileFilterChips
        :chips="tabOptions"
        :model-value="tab"
        :sort-by="tab === 'incoming' ? incomingSortBy : outgoingSortBy"
        :sort-order="tab === 'incoming' ? incomingSortOrder : outgoingSortOrder"
        :sort-options="tab === 'incoming' ? incomingMobileSortOptions : outgoingMobileSortOptions"
        @update:modelValue="switchTab"
        @sort-change="toggleActiveSort"
      />
      <MobileFileList
        :items="tab === 'incoming' ? incomingRows : outgoingRows"
        :loading="activeLoading"
        :selecting="tab === 'incoming' && selectedIncomingIds.length > 0"
        :selected-ids="selectedIncomingIds"
        :empty-text="tab === 'incoming' ? '暂无收到的共享文件' : '暂无我发出的共享'"
        @item-click="onMobileItemClick"
        @item-long-press="onMobileItemLongPress"
        @item-toggle="onMobileItemToggle"
        @item-more="onMobileItemMore"
      />
      <div class="m-mp-pagination">
        <button class="m-mp-page-btn m-tap" type="button" :disabled="activePage <= 1 || activeLoading" @click="changePage(activePage - 1)">上一页</button>
        <span class="m-mp-page-info">第 {{ activePage }} / {{ activeTotalPages || 1 }} 页 · 共 {{ activeTotal }} 项</span>
        <button class="m-mp-page-btn m-tap" type="button" :disabled="activePage >= activeTotalPages || activeLoading || activeTotalPages === 0" @click="changePage(activePage + 1)">下一页</button>
      </div>
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />
      <MobileSelectionBar
        :visible="tab === 'incoming' && selectedIncomingIds.length > 0"
        :count="selectedIncomingIds.length"
        :actions="mobileSelectionActions"
        :bottom-offset="56"
        @cancel="selectedIncomingIds = []"
        @action="openCopyDialog(selectedIncomingRows)"
      />


    <div v-if="editDialogOpen" class="dialog-mask" @click.self="closeEditDialog">
      <div class="dialog-panel" role="dialog" aria-modal="true" aria-labelledby="edit-share-title">
        <div class="dialog-head">
          <h2 id="edit-share-title">修改共享</h2>
          <p>{{ editShareTarget?.item?.name || '共享项' }}</p>
        </div>
        <div class="share-settings-grid">
          <label class="dialog-field">
            <span>权限</span>
            <select v-model="editSharePermission">
              <option value="read">只读</option>
              <option value="download">可下载</option>
              <option value="edit">可编辑</option>
            </select>
          </label>
          <label class="dialog-field">
            <span>有效期</span>
            <select v-model="editShareExpiryMode">
              <option value="forever">长期有效</option>
              <option value="date">指定日期</option>
            </select>
          </label>
          <label v-if="editShareExpiryMode === 'date'" class="dialog-field">
            <span>到期日期</span>
            <input v-model="editShareExpiryDate" type="date" />
          </label>
        </div>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="closeEditDialog">
            取消
          </button>
          <button
            class="primary-button"
            type="button"
            :disabled="submitting || (editShareExpiryMode === 'date' && !editShareExpiryDate)"
            @click="onConfirmEditShare"
          >
            保存
          </button>
        </div>
      </div>
    </div>
    <DriveCopyDialog :open="copyDialogOpen" :items="copyDialogItems" @close="closeCopyDialog" @copied="onCopyCompleted" />
  </section>
</template>
<script setup>

import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DriveCopyDialog from '../../components/drive/DriveCopyDialog.vue';
import { downloadDriveItem, editDriveItem, getIncomingDriveSharePath, getIncomingDriveShares, getOutgoingDriveShares, getDriveSortPreference, previewDriveItem, revokeDriveShare, updateDriveShare, updateDriveSortPreference } from "../../api/cloudnext";
import { emitDriveRefreshSignal } from '../../session';
import { formatFileSize, formatTime } from '../../utils/formatters';
import MobileFilterChips from '../../components/mobile/MobileFilterChips.vue';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';
import MobileSelectionBar from '../../components/mobile/MobileSelectionBar.vue';

const route = useRoute();
const router = useRouter();

const tabOptions = [
  { key: 'incoming', label: '收到的共享' },
  { key: 'outgoing', label: '我发出的' }
];
const incomingMobileSortOptions = { name: '名称', updatedAt: '修改时间', sizeBytes: '大小', ownerName: '共享人' };
const outgoingMobileSortOptions = { name: '名称', updatedAt: '修改时间', targetName: '共享对象', permission: '权限' };

const tab = computed(() => (route.query.tab === 'outgoing' ? 'outgoing' : 'incoming'));
const shareId = computed(() => (typeof route.query.shareId === 'string' ? route.query.shareId : ''));
const parentId = computed(() => (typeof route.query.parentId === 'string' ? route.query.parentId : ''));

const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '');

watch(
  () => route.query.keyword,
  (next) => {
    const value = typeof next === 'string' ? next : '';
    if (value !== keyword.value) {
      keyword.value = value;
    }
  }
);
const submitting = ref(false);

const incomingPage = ref(1);
const incomingPageSize = ref(50);
const incomingTotal = ref(0);
const incomingTotalPages = ref(0);
const incomingLoading = ref(false);
const incomingItems = ref([]);
const incomingSortBy = ref('updatedAt');
const incomingSortOrder = ref('desc');
const selectedIncomingIds = ref([]);
const incomingBreadcrumbs = ref([]);

const outgoingPage = ref(1);
const outgoingPageSize = ref(50);
const outgoingTotal = ref(0);
const outgoingTotalPages = ref(0);
const outgoingLoading = ref(false);
const outgoingItems = ref([]);
const outgoingSortBy = ref('updatedAt');
const outgoingSortOrder = ref('desc');
const selectedOutgoingIds = ref([]);

const editDialogOpen = ref(false);
const editShareTarget = ref(null);
const editSharePermission = ref('read');
const editShareExpiryMode = ref('forever');
const editShareExpiryDate = ref('');
let loadTimer = null;
const copyDialogOpen = ref(false);
const copyDialogItems = ref([]);

const incomingRows = computed(() =>
  incomingItems.value
    .map((share) => {
      if (!share.item) {
        return null;
      }
      return {
        ...share.item,
        shareId: share.shareId || share.id,
        ownerAvatar: share.item.ownerName?.slice(0, 1) || 'U',
        permissionLabel: formatPermission(share.effectivePermission || share.permission),
        sharePermission: share.effectivePermission || share.permission,
        allowPreview: share.allowPreview !== false,
        allowDownload: share.allowDownload !== false,
        allowEdit: String(share.effectivePermission || share.permission || '').toLowerCase() === 'edit',
        modifiedLabel: formatTime(share.item.updatedAt),
        sizeLabel: share.item.itemType === 'folder' ? '--' : formatFileSize(share.item.sizeBytes)
      };
    })
    .filter(Boolean)
);

const outgoingRows = computed(() =>
  outgoingItems.value
    .map((share) => {
      if (!share.item) {
        return null;
      }
      return {
        ...share.item,
        id: share.id,
        itemId: share.item.id,
        targetName: share.targetName || '--',
        permissionLabel: formatPermission(share.permission),
        expiresLabel: formatExpiresAt(share.expiresAt),
        statusLabel: formatShareStatus(share.status),
        ownerAvatar: share.ownerName?.slice(0, 1) || 'U',
        sharePermission: share.permission,
        shareExpiresAt: share.expiresAt,
        shareStatus: share.status
      };
    })
    .filter(Boolean)
);

const selectedIncomingRows = computed(() => incomingRows.value.filter((item) => selectedIncomingIds.value.includes(item.id)));
const activePage = computed(() => (tab.value === 'incoming' ? incomingPage.value : outgoingPage.value));
const activePageSize = computed({
  get: () => (tab.value === 'incoming' ? incomingPageSize.value : outgoingPageSize.value),
  set: (value) => {
    if (tab.value === 'incoming') {
      incomingPageSize.value = value;
    } else {
      outgoingPageSize.value = value;
    }
  }
});
const activeTotal = computed(() => (tab.value === 'incoming' ? incomingTotal.value : outgoingTotal.value));
const activeTotalPages = computed(() => (tab.value === 'incoming' ? incomingTotalPages.value : outgoingTotalPages.value));
const activeLoading = computed(() => (tab.value === 'incoming' ? incomingLoading.value : outgoingLoading.value));

const normalizeIncomingItems = (items) => (Array.isArray(items) ? items : []);

const loadIncoming = async () => {
  incomingLoading.value = true;
  try {
    const page = await getIncomingDriveShares({
      page: incomingPage.value,
      size: incomingPageSize.value,
      keyword: keyword.value,
      shareId: shareId.value,
      parentId: parentId.value,
      sortBy: incomingSortBy.value,
      sortOrder: incomingSortOrder.value
    });
    incomingItems.value = normalizeIncomingItems(page?.items);
    incomingTotal.value = Number(page?.total || 0);
    incomingTotalPages.value = Number(page?.totalPages || 0);
    incomingPage.value = Number(page?.page || incomingPage.value);
    incomingPageSize.value = Number(page?.size || incomingPageSize.value);
    if (shareId.value) {
      incomingBreadcrumbs.value = await getIncomingDriveSharePath(shareId.value, parentId.value);
    } else {
      incomingBreadcrumbs.value = [];
    }
  } catch (e) {
    incomingItems.value = [];
    incomingBreadcrumbs.value = [];
  } finally {
    incomingLoading.value = false;
  }
};

const loadOutgoing = async () => {
  outgoingLoading.value = true;
  try {
    const page = await getOutgoingDriveShares({
      page: outgoingPage.value,
      size: outgoingPageSize.value,
      keyword: keyword.value,
      sortBy: outgoingSortBy.value,
      sortOrder: outgoingSortOrder.value
    });
    outgoingItems.value = Array.isArray(page?.items) ? page.items : [];
    outgoingTotal.value = Number(page?.total || 0);
    outgoingTotalPages.value = Number(page?.totalPages || 0);
    outgoingPage.value = Number(page?.page || outgoingPage.value);
    outgoingPageSize.value = Number(page?.size || outgoingPageSize.value);
    selectedOutgoingIds.value = selectedOutgoingIds.value.filter((id) =>
      outgoingItems.value.some((item) => item.id === id)
    );
  } catch (e) {
    outgoingItems.value = [];
  } finally {
    outgoingLoading.value = false;
  }
};

const scheduleLoad = (resetPage = false) => {
  window.clearTimeout(loadTimer);
  if (resetPage) {
    if (tab.value === 'incoming') {
      incomingPage.value = 1;
    } else {
      outgoingPage.value = 1;
    }
  }
  loadTimer = window.setTimeout(() => {
    void (tab.value === 'incoming' ? loadIncoming() : loadOutgoing());
  }, 220);
};

const switchTab = async (nextTab) => {
  if (nextTab === tab.value) {
    return;
  }
  keyword.value = '';
  selectedOutgoingIds.value = [];
  await router.push({ name: 'shared', query: nextTab === 'outgoing' ? { tab: 'outgoing' } : {} });
};

const goIncomingRoot = async () => {
  await router.push({ name: 'shared' });
};

const goIncomingFolder = async (folderId) => {
  await router.push({
    name: 'shared',
    query: {
      tab: 'incoming',
      shareId: shareId.value,
      ...(folderId ? { parentId: folderId } : {})
    }
  });
};

const onOpenIncomingFolder = async (item) => {
  if (item.itemType !== 'folder') {
    return;
  }
  const nextShareId = shareId.value || item.shareId;
  await router.push({
    name: 'shared',
    query: {
      tab: 'incoming',
      shareId: nextShareId,
      parentId: item.id
    }
  });
};

const onPreviewIncoming = async (item) => {
  try {
    await previewDriveItem(item);
    emitDriveRefreshSignal();
  } catch (e) {
  }
};

const onEditIncoming = async (item) => {
  try {
    await editDriveItem(item);
  } catch (e) {
  }
};

const onDownloadIncoming = async (item) => {
  try {
    await downloadDriveItem(item);
    emitDriveRefreshSignal();
  } catch (e) {
  }
};

const openCopyDialog = (items) => {
  copyDialogItems.value = Array.isArray(items) ? items.filter((item) => item?.allowDownload !== false) : [];
  copyDialogOpen.value = copyDialogItems.value.length > 0;
};

const closeCopyDialog = () => {
  copyDialogOpen.value = false;
  copyDialogItems.value = [];
};
const onCopyCompleted = () => {
  closeCopyDialog();
  selectedIncomingIds.value = [];
  emitDriveRefreshSignal();
};

const restoreActiveSort = async () => {
  const context = tab.value === 'incoming' ? 'shared-incoming' : 'shared-outgoing';
  try {
    const preference = await getDriveSortPreference(context);
    if (tab.value === 'incoming') {
      incomingSortBy.value = preference?.sortBy || 'updatedAt';
      incomingSortOrder.value = preference?.sortOrder || 'desc';
    } else {
      outgoingSortBy.value = preference?.sortBy || 'updatedAt';
      outgoingSortOrder.value = preference?.sortOrder || 'desc';
    }
  } catch (error) { }
};

const toggleActiveSort = (selection) => {
  const explicit = selection && typeof selection === 'object';
  const key = explicit ? selection.sortBy : selection;
  const sortByRef = tab.value === 'incoming' ? incomingSortBy : outgoingSortBy;
  const sortOrderRef = tab.value === 'incoming' ? incomingSortOrder : outgoingSortOrder;
  if (explicit) {
    sortByRef.value = key;
    sortOrderRef.value = selection.sortOrder === 'asc' ? 'asc' : 'desc';
  } else if (sortByRef.value === key) sortOrderRef.value = sortOrderRef.value === 'asc' ? 'desc' : 'asc';
  else {
    sortByRef.value = key;
    sortOrderRef.value = key === 'name' ? 'asc' : 'desc';
  }
  const context = tab.value === 'incoming' ? 'shared-incoming' : 'shared-outgoing';
  void updateDriveSortPreference({ context, sortBy: sortByRef.value, sortOrder: sortOrderRef.value }).catch(() => {});
  scheduleLoad(true);
};

const onStartEditShare = (item) => {
  const matched = outgoingItems.value.find((share) => share.id === item.id);
  if (!matched) {
    return;
  }
  editShareTarget.value = matched;
  editSharePermission.value = matched.permission || 'read';
  if (matched.expiresAt) {
    editShareExpiryMode.value = 'date';
    editShareExpiryDate.value = matched.expiresAt.slice(0, 10);
  } else {
    editShareExpiryMode.value = 'forever';
    editShareExpiryDate.value = '';
  }
  editDialogOpen.value = true;
};

const closeEditDialog = (force = false) => {
  if (submitting.value && !force) {
    return;
  }
  editDialogOpen.value = false;
  editShareTarget.value = null;
  editSharePermission.value = 'read';
  editShareExpiryMode.value = 'forever';
  editShareExpiryDate.value = '';
};

const onConfirmEditShare = async () => {
  if (!editShareTarget.value) {
    return;
  }
  submitting.value = true;
  try {
    await updateDriveShare(editShareTarget.value.id, {
      permission: editSharePermission.value,
      expiresAt: editShareExpiryMode.value === 'date' ? editShareExpiryDate.value : ''
    });
    await loadOutgoing();
  } catch (e) {
    submitting.value = false;
    return;
  }
  submitting.value = false;
  closeEditDialog(true);
};

const onRevokeShare = async (item) => {
  if (!window.confirm(`确认取消“${item.name}”的共享吗？`)) {
    return;
  }
  submitting.value = true;
  try {
    await revokeDriveShare(item.id);
    await loadOutgoing();
    selectedOutgoingIds.value = selectedOutgoingIds.value.filter((id) => id !== item.id);
  } catch (e) {
  } finally {
    submitting.value = false;
  }
};

const changePage = (nextPage) => {
  if (tab.value === 'incoming') {
    incomingPage.value = Math.max(1, nextPage);
    void loadIncoming();
    return;
  }
  outgoingPage.value = Math.max(1, nextPage);
  void loadOutgoing();
};

const formatPermission = (value) => {
  if (value === 'edit') {
    return '可编辑';
  }
  if (value === 'download') {
    return '可下载';
  }
  return '只读';
};

const formatExpiresAt = (value) => (value ? formatTime(value) : '长期有效');

const formatShareStatus = (value) => {
  if (value === 'expired') {
    return '已过期';
  }
  if (value === 'revoked') {
    return '已取消';
  }
  if (value === 'source_deleted') {
    return '源文件已删除';
  }
  return '生效中';
};

// === 移动端逻辑 ===

const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');

const mobileCurrentFolderName = computed(() => {
  const crumbs = incomingBreadcrumbs.value || [];
  return crumbs[crumbs.length - 1]?.name || '共享文件';
});

const mobileSheetActions = computed(() => {
  const item = mobileSheetItem.value;
  if (!item) {
    return [];
  }
  if (tab.value === 'outgoing') {
    return [
      { key: 'edit-share', label: '修改共享' },
      { key: 'revoke-share', label: '取消共享', danger: true }
    ];
  }
  if (item.itemType === 'folder') {
    return [
      { key: 'open', label: '打开' },
      ...(item.allowDownload ? [{ key: 'copy', label: '复制到' }] : [])
    ];
  }
  const actions = [{ key: 'preview', label: '预览' }];
  if (item.allowEdit) {
    actions.push({ key: 'edit', label: '在线编辑' });
  }
  if (item.allowDownload) {
    actions.push({ key: 'download', label: '下载' });
    actions.push({ key: 'copy', label: '复制到' });
  }
  return actions;
});
const MOBILE_COPY_ICON = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>';
const mobileSelectionActions = computed(() => [{ key: 'copy', label: '复制到', icon: MOBILE_COPY_ICON }]);

const onMobileGoBack = () => {
  const crumbs = incomingBreadcrumbs.value || [];
  if (crumbs.length <= 1) {
    goIncomingRoot();
  } else {
    goIncomingFolder(crumbs[crumbs.length - 2].id);
  }
};

const onMobileItemClick = (item) => {
  if (tab.value === 'incoming' && selectedIncomingIds.value.length) {
    onMobileItemToggle(item);
    return;
  }
  if (tab.value === 'incoming') {
    if (item.itemType === 'folder') {
      onOpenIncomingFolder(item);
    } else {
      void onPreviewIncoming(item);
    }
  } else {
    // outgoing 单击 = 弹出更多操作
    onMobileItemMore(item);
  }
};

const onMobileItemLongPress = (item) => {
  if (tab.value === 'incoming' && item.allowDownload !== false && !selectedIncomingIds.value.includes(item.id)) {
    selectedIncomingIds.value = [...selectedIncomingIds.value, item.id];
  }
};
const onMobileItemToggle = (item) => {
  if (tab.value !== 'incoming' || item.allowDownload === false) return;
  const next = new Set(selectedIncomingIds.value);
  if (next.has(item.id)) next.delete(item.id); else next.add(item.id);
  selectedIncomingIds.value = Array.from(next);
};

const onMobileItemMore = (item) => {
  mobileSheetItem.value = item;
  mobileSheetOpen.value = true;
};

const onMobileSheetSelect = (key) => {
  const item = mobileSheetItem.value;
  mobileSheetOpen.value = false;
  if (!item) {
    return;
  }
  switch (key) {
    case 'open':
      onOpenIncomingFolder(item);
      break;
    case 'preview':
      void onPreviewIncoming(item);
      break;
    case 'edit':
      void onEditIncoming(item);
      break;
    case 'download':
      void onDownloadIncoming(item);
      break;
    case 'copy':
      openCopyDialog([item]);
      break;
    case 'edit-share':
      onStartEditShare(item);
      break;
    case 'revoke-share':
      onRevokeShare(item);
      break;
  }
};

watch(
  () => [tab.value, shareId.value, parentId.value],
  async () => {
    await restoreActiveSort();
    selectedIncomingIds.value = [];
    if (tab.value === 'incoming') {
      selectedOutgoingIds.value = [];
      void loadIncoming();
    } else {
      void loadOutgoing();
    }
  },
  { immediate: true }
);

watch([keyword, activePageSize], () => {
  scheduleLoad(true);
});

onUnmounted(() => {
  window.clearTimeout(loadTimer);
});
</script>
<style scoped>

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-mp-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  height: 40px;
  padding: 0 var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
  font-size: var(--m-fs-body);
}

.m-mp-back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 32px;
  padding: 0 var(--m-sp-2);
  background: transparent;
  color: var(--bg-primary);
  border-radius: var(--radius-md);
}

.m-mp-back:active {
  background: var(--bg-nav-hover);
}

.m-mp-current {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-weight: 500;
}

.m-mp-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: #fff;
  border-top: 1px solid rgba(15, 23, 42, 0.04);
  font-size: var(--m-fs-meta);
  color: var(--text-tertiary);
}

.m-mp-page-btn {
  height: 36px;
  padding: 0 var(--m-sp-3);
  background: var(--bg-panel-soft);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  font-size: var(--m-fs-meta);
}

.m-mp-page-btn:active:not(:disabled) {
  background: var(--bg-nav-hover);
}

.m-mp-page-btn:disabled {
  opacity: 0.4;
}

.m-mp-page-info {
  flex: 1;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.breadcrumb-text,
.breadcrumb-current {
  display: inline-flex;
  align-items: center;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.4;
}

.breadcrumb-text {
  padding: 0;
  background: transparent;
}

.breadcrumb-text:hover,
.breadcrumb-current {
  color: var(--text-primary);
}

.breadcrumb-current {
  font-weight: 600;
}

.breadcrumb-divider {
  color: var(--text-tertiary);
  font-size: 12px;
}

.drive-directory-page { height: 100%; min-height: 0; display: flex; flex-direction: column; }

.toolbar-select {
  display: grid;
  gap: 6px;
}

.toolbar-select span {
  font-size: 12px;
  color: var(--text-secondary);
}

.toolbar-select select {
  min-width: 120px;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 12px;
  background: var(--bg-panel);
  color: var(--text-primary);
}

.toolbar-select.compact select {
  min-width: 96px;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.18);
}

.dialog-panel {
  width: min(520px, 100%);
  display: grid;
  gap: 18px;
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.dialog-head {
  display: grid;
  gap: 6px;
}

.dialog-head h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.dialog-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.dialog-field {
  display: grid;
  gap: 8px;
}

.dialog-field span {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.dialog-field input,
.dialog-field select {
  width: 100%;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 14px;
  color: var(--text-primary);
  background: var(--bg-panel);
}

.share-settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1080px) {
  .dialog-actions {
    align-items: stretch;
  }

  .dialog-actions {
    width: 100%;
  }

  .dialog-actions button {
    flex: 1;
  }

  .share-settings-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 559px) {
  .dialog-mask {
    padding: 12px;
    align-items: center;
  }

  .dialog-panel {
    width: 100%;
    max-width: 100%;
    padding: 16px;
    gap: 12px;
    max-height: 90vh;
    overflow-y: auto;
  }

  .dialog-head h2 {
    font-size: 17px;
  }

  .dialog-head p {
    font-size: 12px;
  }

  .dialog-field {
    gap: 6px;
  }

  .dialog-field input,
.dialog-field select,
.dialog-field textarea {
    height: 40px;
    font-size: 14px;
  }

  .dialog-actions {
    flex-direction: column-reverse;
    gap: 8px;
  }

  .dialog-actions > button {
    width: 100%;
    min-height: 44px;
  }
}

</style>
