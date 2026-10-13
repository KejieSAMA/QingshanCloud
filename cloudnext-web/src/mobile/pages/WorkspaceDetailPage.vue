<template>
  <section class="cloudnext-page drive-directory-page" :class="{ 'm-page-flush': true }">


      <div class="m-wd-breadcrumb">
        <button class="m-mp-back m-tap" type="button" @click="onMobileGoBack">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          <span>{{ mobileBackLabel }}</span>
        </button>
        <span class="m-wd-current">{{ mobileCurrentName }}</span>
        <button
          v-if="mobileWorkspaceActions.length"
          class="m-wd-more m-tap"
          type="button"
          aria-label="空间操作"
          @click="mobileWorkspaceSheetOpen = true"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
            <circle cx="12" cy="12" r="1" />
            <circle cx="19" cy="12" r="1" />
            <circle cx="5" cy="12" r="1" />
          </svg>
        </button>
      </div>
      <p v-if="errorText" class="m-trash-msg m-trash-msg-error">{{ errorText }}</p>
      <MobileFilterChips v-model="activeFilter" :chips="filters"
        :sort-by="sortBy" :sort-order="sortOrder"
        @sort-change="({ sortBy: key, sortOrder: order }) => setSort(key, order)" />
      <MobileFileList
        v-if="workspace"
        :items="filteredDisplayItems"
        :loading="loading"
        :selecting="selectedCount > 0"
        :selected-ids="selectedIds"
        empty-text="当前目录暂无文件"
        @item-click="onMobileItemClick"
        @item-long-press="onMobileItemLongPress"
        @item-toggle="onMobileItemToggle"
        @item-more="onMobileItemMore"
      />
      <div class="m-mp-pagination">
        <button class="m-mp-page-btn m-tap" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
        <span class="m-mp-page-info">第 {{ page }} / {{ totalPages || 1 }} 页 · 共 {{ total }} 项</span>
        <button class="m-mp-page-btn m-tap" type="button" :disabled="page >= totalPages || loading || totalPages === 0" @click="changePage(page + 1)">下一页</button>
      </div>
      <MobileSelectionBar
        :visible="selectedCount > 0"
        :count="selectedCount"
        :actions="mobileSelectionActions"
        :bottom-offset="56"
        @cancel="clearSelection"
        @action="onMobileSelectionAction"
      />
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />
      <MobileActionSheet
        v-model:open="mobileWorkspaceSheetOpen"
        :title="workspace?.name || '协作空间'"
        :actions="mobileWorkspaceActions"
        @select="onMobileWorkspaceSheetSelect"
      />
      <input
        ref="mobileFileInputRef"
        type="file"
        class="m-wd-hidden-upload"
        :disabled="submitting"
        @change="onUploadFile"
      />


    <WorkspaceMembersDialog
      v-if="workspace"
      :workspace-id="workspace.id"
      :my-role="workspace.myRole || ''"
      :my-person-key="myPersonKey"
      :open="membersDialogOpen"
      @close="membersDialogOpen = false"
      @changed="reloadWorkspace"
    />
    <DriveCopyDialog :open="copyDialogOpen" :items="copyDialogItems" @close="closeCopyDialog" @copied="onCopyCompleted" />
  </section>
</template>
<script setup>
import { enqueue, ui } from '../../next/state';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  createWorkspaceFolder,
  deleteWorkspace,
  deleteDriveItem,
  downloadDriveItem,
  editDriveItem,
  getCurrentSession,
  getWorkspace,
  previewDriveItem,
  updateWorkspace,
} from '../../api/cloudnext';

import DriveCopyDialog from '../../components/drive/DriveCopyDialog.vue';
import WorkspaceMembersDialog from '../../components/drive/WorkspaceMembersDialog.vue';
import { useDriveDirectoryState } from '../../composables/drive/useDriveDirectoryState';
import { useDriveSelection } from '../../composables/drive/useDriveSelection';
import {
  canDeleteWorkspace as roleCanDeleteWorkspace,
  canManageMembers as roleCanManageMembers,
  canRenameWorkspace as roleCanRenameWorkspace,
  canWriteInWorkspace
} from '../../composables/drive/workspacePermissions';
import { getSessionPersonKey, getStoredSession, getStoredToken } from '../../session';
import MobileFilterChips from '../../components/mobile/MobileFilterChips.vue';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import MobileSelectionBar from '../../components/mobile/MobileSelectionBar.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const route = useRoute();
const router = useRouter();
const workspaceId = String(route.params.id || '');

const workspace = ref(null);
const submitting = ref(false);
const errorText = ref('');
const membersDialogOpen = ref(false);
const myPersonKey = ref(getSessionPersonKey(getStoredSession()));

const copyDialogOpen = ref(false);
const copyDialogItems = ref([]);

const directory = useDriveDirectoryState({
  route,
  router,
  scope: { type: 'workspace', id: workspaceId },
  onRouteChangeStart: () => {
    errorText.value = '';
  }
});

const {
  loading,
  activeFilter,
  sortBy,
  sortOrder,
  toggleSort,
  setSort,
  displayItems,
  page,
  pageSize,
  total,
  totalPages,
  changePage,
  breadcrumbs,
  currentParentId,
  refreshCurrentDirectory,
  onOpenFolder,
  goToFolder,
  goToRoot
} = directory;

const filters = [
  { key: 'all', label: '全部' },
  { key: 'folder', label: '文件夹' },
  { key: 'file', label: '文件' }
];
const filteredDisplayItems = computed(() => displayItems.value);

const {
  selectedIds,
  selectedItems,
  selectedCount,
  selectedFileItems,
  clearSelection,
  onSelectionChange
} = useDriveSelection(filteredDisplayItems);

const canWrite = computed(() => canWriteInWorkspace(workspace.value?.myRole));
const canManageMembers = computed(() => roleCanManageMembers(workspace.value?.myRole));
const canRenameWorkspace = computed(() => roleCanRenameWorkspace(workspace.value?.myRole));
const canDeleteWorkspace = computed(() => roleCanDeleteWorkspace(workspace.value?.myRole));

const reloadWorkspace = async () => {
  if (!workspaceId) {
    errorText.value = '工作区未指定';
    return;
  }
  try {
    workspace.value = await getWorkspace(workspaceId);
  } catch (error) {
    errorText.value = error?.message || '加载工作区失败';
    workspace.value = null;
  }
};

const goToWorkspaces = () => router.push({ name: 'workspaces' });

const onRenameWorkspace = async () => {
  if (!workspace.value || !canRenameWorkspace.value) {
    errorText.value = '当前角色无改名权限';
    return;
  }
  const raw = window.prompt('请输入新的协作空间名称', workspace.value.name || '');
  const name = (raw || '').trim();
  if (!name || name === workspace.value.name) {
    return;
  }
  if (name.length > 100) {
    errorText.value = '空间名称不能超过 100 字符。';
    return;
  }
  submitting.value = true;
  errorText.value = '';
  try {
    const updated = await updateWorkspace(workspaceId, { name });
    workspace.value = { ...workspace.value, ...(updated || {}), name };
  } catch (error) {
    errorText.value = error?.message || '改名失败';
  } finally {
    submitting.value = false;
  }
};

const onDeleteWorkspace = async () => {
  if (!workspace.value || !canDeleteWorkspace.value) {
    errorText.value = '当前角色无删除权限';
    return;
  }
  if (!window.confirm(`确认删除协作空间「${workspace.value.name}」?删除后列表中将不再显示。`)) {
    return;
  }
  submitting.value = true;
  errorText.value = '';
  try {
    await deleteWorkspace(workspaceId);
    goToWorkspaces();
  } catch (error) {
    errorText.value = error?.message || '删除失败';
  } finally {
    submitting.value = false;
  }
};

const onCreateFolder = async () => {
  const raw = window.prompt('请输入文件夹名称');
  const name = (raw || '').trim();
  if (!name) return;
  submitting.value = true;
  errorText.value = '';
  try {
    await createWorkspaceFolder(workspaceId, {
      parentId: currentParentId.value || workspace.value?.rootItemId || '',
      name
    });
    await refreshCurrentDirectory();
  } catch (error) {
    errorText.value = error?.message || '新建文件夹失败';
  } finally {
    submitting.value = false;
  }
};

const onUploadFile = (event) => {
  const files = Array.from(event.target.files || []);
  event.target.value = '';
  if (!files.length || !canWrite.value) return;
  enqueue(files, { scopeType: 'WORKSPACE', scopeId: workspaceId, parentId: currentParentId.value || workspace.value?.rootItemId || '' }, 'keep_both');
};
watch(() => ui.revision, () => { void refreshCurrentDirectory(); });

const onPreview = async (item) => {
  try {
    await previewDriveItem(item);
  } catch (error) {
    errorText.value = error?.message || '预览失败';
  }
};

const onEdit = async (item) => {
  if (!canWrite.value) {
    errorText.value = '当前角色无编辑权限';
    return;
  }
  try {
    await editDriveItem(item);
  } catch (error) {
    errorText.value = error?.message || '编辑失败';
  }
};

const onDownload = async (item) => {
  if (!canWrite.value) {
    errorText.value = '当前角色无下载权限';
    return;
  }
  try {
    await downloadDriveItem(item);
  } catch (error) {
    errorText.value = error?.message || '下载失败';
  }
};

const onDelete = async (item) => {
  if (!canWrite.value) {
    errorText.value = '当前角色无删除权限';
    return;
  }
  if (!window.confirm(`确认删除 ${item.name}?将移入工作区回收站。`)) return;
  submitting.value = true;
  errorText.value = '';
  try {
    await deleteDriveItem(item.id);
    await refreshCurrentDirectory();
  } catch (error) {
    errorText.value = error?.message || '删除失败';
  } finally {
    submitting.value = false;
  }
};

const onBatchDownload = async () => {
  if (!canWrite.value) {
    errorText.value = '当前角色无下载权限';
    return;
  }
  if (!selectedFileItems.value.length) return;
  submitting.value = true;
  errorText.value = '';
  try {
    for (const item of selectedFileItems.value) {
      // serial download so the browser doesn't reject parallel saves
      // eslint-disable-next-line no-await-in-loop
      await downloadDriveItem(item);
    }
  } catch (error) {
    errorText.value = error?.message || '批量下载失败';
  } finally {
    submitting.value = false;
  }
};

const onBatchDelete = async () => {
  if (!selectedItems.value.length) return;
  if (
    !window.confirm(
      `确认删除选中的 ${selectedItems.value.length} 项?将移入工作区回收站。`
    )
  ) {
    return;
  }
  submitting.value = true;
  errorText.value = '';
  const failures = [], actionToken = getStoredToken();
  for (const item of selectedItems.value) {
    if (actionToken !== getStoredToken()) break;
    try {
      // eslint-disable-next-line no-await-in-loop
      await deleteDriveItem(item.id);
    } catch (error) {
      failures.push(`${item.name}: ${error?.message || '删除失败'}`);
    }
  }
  clearSelection();
  await refreshCurrentDirectory();
  if (failures.length) {
    errorText.value = `部分项删除失败:\n${failures.join('\n')}`;
  }
  submitting.value = false;
};

// === 移动端逻辑 ===

const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');
const mobileFileInputRef = ref(null);
const mobileWorkspaceSheetOpen = ref(false);

const mobileBackLabel = computed(() => {
  const crumbs = breadcrumbs.value || [];
  if (!currentParentId.value) {
    return '协作空间';
  }
  if (crumbs.length <= 1) {
    return workspace.value?.name || '空间根目录';
  }
  return crumbs[crumbs.length - 2]?.name || '上一级';
});

const mobileCurrentName = computed(() => {
  const crumbs = breadcrumbs.value || [];
  return crumbs[crumbs.length - 1]?.name || workspace.value?.name || '空间根目录';
});

const mobileSheetActions = computed(() => {
  const item = mobileSheetItem.value;
  if (!item) {
    return [];
  }
  if (item.itemType === 'folder') {
    const list = [{ key: 'open', label: '打开' }];
    if (canWrite.value) {
      list.push({ key: 'copy', label: '复制到' });
      list.push({ key: 'delete', label: '删除', danger: true });
    }
    return list;
  }
  const list = [{ key: 'preview', label: '预览' }];
  if (canWrite.value) {
    list.push({ key: 'edit', label: '在线编辑' });
    list.push({ key: 'download', label: '下载' });
    list.push({ key: 'copy', label: '复制到' });
    list.push({ key: 'delete', label: '删除', danger: true });
  }
  return list;
});

const M_ICON_DOWNLOAD = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>';
const M_ICON_DELETE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>';
const M_ICON_COPY = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/></svg>';

const mobileSelectionActions = computed(() => {
  if (!canWrite.value) {
    return [];
  }
  return [
    { key: 'download', label: '下载', icon: M_ICON_DOWNLOAD, disabled: !selectedFileItems.value.length },
    { key: 'copy', label: '复制到', icon: M_ICON_COPY },
    { key: 'delete', label: '删除', icon: M_ICON_DELETE, danger: true }
  ];
});

const mobileWorkspaceActions = computed(() => {
  const actions = [];
  if (canRenameWorkspace.value) {
    actions.push({ key: 'rename', label: '改名' });
  }
  if (canDeleteWorkspace.value) {
    actions.push({ key: 'delete', label: '删除空间', danger: true });
  }
  return actions;
});

const onMobileGoBack = () => {
  const crumbs = breadcrumbs.value || [];
  if (!currentParentId.value) {
    goToWorkspaces();
    return;
  }
  if (crumbs.length <= 1) {
    goToRoot();
  } else {
    goToFolder(crumbs[crumbs.length - 2].id);
  }
};

const onMobileItemClick = (item) => {
  if (selectedCount.value > 0) {
    onMobileItemToggle(item);
    return;
  }
  if (item.itemType === 'folder') {
    onOpenFolder(item);
  } else {
    void onPreview(item);
  }
};

const onMobileItemLongPress = (item) => {
  if (!canWrite.value) {
    return;
  }
  if (!selectedIds.value.includes(item.id)) {
    selectedIds.value = [...selectedIds.value, item.id];
  }
};

const openCopyDialog = (items) => {
  if (!canWrite.value) {
    errorText.value = '当前角色无复制权限';
    return;
  }
  copyDialogItems.value = Array.isArray(items) ? items.filter(Boolean) : [];
  copyDialogOpen.value = copyDialogItems.value.length > 0;
};

const closeCopyDialog = () => {
  copyDialogOpen.value = false;
  copyDialogItems.value = [];
};
const onCopyCompleted = async () => {
  closeCopyDialog();
  clearSelection();
  await refreshCurrentDirectory();
};

const onMobileItemToggle = (item) => {
  if (!canWrite.value) {
    return;
  }
  const next = new Set(selectedIds.value);
  if (next.has(item.id)) {
    next.delete(item.id);
  } else {
    next.add(item.id);
  }
  selectedIds.value = Array.from(next);
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
      onOpenFolder(item);
      break;
    case 'preview':
      void onPreview(item);
      break;
    case 'edit':
      void onEdit(item);
      break;
    case 'download':
      void onDownload(item);
      break;
    case 'copy':
      openCopyDialog([item]);
      break;
    case 'delete':
      void onDelete(item);
      break;
  }
};

const onMobileWorkspaceSheetSelect = (key) => {
  mobileWorkspaceSheetOpen.value = false;
  if (key === 'rename') {
    void onRenameWorkspace();
  } else if (key === 'delete') {
    void onDeleteWorkspace();
  }
};

const onMobileSelectionAction = (key) => {
  if (key === 'download') {
    void onBatchDownload();
  } else if (key === 'copy') {
    openCopyDialog(selectedItems.value);
  } else if (key === 'delete') {
    void onBatchDelete();
  }
};

const onMobileFabAction = (event) => {

  const key = event?.detail?.key;
  if (!key) {
    return;
  }
  if (key === 'folder') {
    if (!canWrite.value) {
      errorText.value = '当前角色无写入权限';
      return;
    }
    void onCreateFolder();
  } else if (key === 'upload') {
    if (!canWrite.value) {
      errorText.value = '当前角色无写入权限';
      return;
    }
    mobileFileInputRef.value?.click();
  } else if (key === 'members') {
    if (!canManageMembers.value) {
      errorText.value = '当前角色无成员管理权限';
      return;
    }
    membersDialogOpen.value = true;
  }
};

onMounted(async () => {
  window.addEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
  await reloadWorkspace();
  if (myPersonKey.value) {
    return;
  }
  try {
    const session = await getCurrentSession();
    myPersonKey.value = getSessionPersonKey(session);
  } catch (error) {
    // session may already be loaded by the shell; ignore
  }
});

onUnmounted(() => {
  window.removeEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});
</script>
<style scoped>

.breadcrumb-text,
.breadcrumb-current {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary);
}

.breadcrumb-text {
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.breadcrumb-text:hover,
.breadcrumb-text.active,
.breadcrumb-current {
  color: var(--text-primary);
}

.breadcrumb-current {
  font-weight: 600;
}

.breadcrumb-divider {
  display: inline-flex;
  align-items: center;
  color: var(--text-tertiary);
  font-size: 12px;
}

.drive-directory-page { height: 100%; min-height: 0; display: flex; flex-direction: column; }

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-wd-breadcrumb {
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

.m-wd-current {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-weight: 500;
  text-align: center;
}

.m-wd-more {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  background: transparent;
  color: var(--text-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.m-wd-more:active {
  background: var(--bg-nav-hover);
}

.m-trash-msg {
  margin: 0;
  padding: var(--m-sp-2) var(--m-sp-3);
  font-size: var(--m-fs-meta);
}

.m-trash-msg-error {
  background: rgba(220, 38, 38, 0.08);
  color: #b42318;
}

.m-wd-hidden-upload {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

.m-mp-pagination { display: flex; align-items: center; justify-content: space-between; gap: var(--m-sp-2); padding: var(--m-sp-3); background: #fff; border-top: 1px solid rgba(15, 23, 42, .04); font-size: var(--m-fs-meta); color: var(--text-tertiary); }
.m-mp-page-btn { height: 36px; padding: 0 var(--m-sp-3); background: var(--bg-panel-soft); color: var(--text-primary); border-radius: var(--radius-md); }
.m-mp-page-btn:disabled { opacity: .4; }
.m-mp-page-info { flex: 1; text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

</style>
