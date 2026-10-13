<template>
  <div v-if="open" class="copy-dialog-mask" @click.self="requestClose">
    <section class="copy-dialog" role="dialog" aria-modal="true" aria-labelledby="copy-dialog-title">
      <header class="copy-dialog-header">
        <div>
          <h2 id="copy-dialog-title">复制到</h2>
          <p>已选择 {{ items.length }} 项 · 副本不会继承共享和外链</p>
        </div>
        <button class="copy-close" type="button" aria-label="关闭" :disabled="submitting" @click="requestClose">×</button>
      </header>

      <div class="copy-scope-list">
        <button :class="{ active: targetType === 'PERSONAL' }" type="button" @click="selectPersonal">
          <AppIcon name="file" :size="16" /> 我的文件
        </button>
        <button
          v-for="workspace in writableWorkspaces"
          :key="workspace.id"
          :class="{ active: targetType === 'WORKSPACE' && targetScopeId === workspace.id }"
          type="button"
          @click="selectWorkspace(workspace)"
        >
          <AppIcon name="workspace" :size="16" /> {{ workspace.name }}
        </button>
      </div>

      <div class="copy-location-bar">
        <button type="button" :disabled="!path.length" @click="goBack">
          <AppIcon name="chevron-left" :size="15" /> 上一级
        </button>
        <span>{{ locationText }}</span>
      </div>

      <div class="copy-folder-list" :class="{ loading }">
        <button v-for="folder in folders" :key="folder.id" type="button" @click="browseInto(folder)">
          <FileTypeIcon kind="folder" :size="28" />
          <span>{{ folder.name }}</span>
          <AppIcon name="chevron-right" :size="16" />
        </button>
        <p v-if="!loading && !folders.length">当前目录下没有子文件夹，可直接复制到这里。</p>
        <p v-if="loading">正在读取目录…</p>
      </div>

      <label class="copy-conflict">
        <span>同名项目</span>
        <select v-model="conflictStrategy">
          <option value="keep_both">保留两份（推荐）</option>
          <option value="abort">遇同名停止</option>
          <option value="replace">覆盖旧项目</option>
        </select>
      </label>

      <p v-if="errorMessage" class="copy-error">{{ errorMessage }}</p>
      <footer>
        <button class="copy-secondary" type="button" :disabled="submitting" @click="requestClose">取消</button>
        <button class="copy-primary" type="button" :disabled="submitting || loading || !items.length" @click="confirmCopy">
          {{ submitting ? '正在复制…' : `复制到此处` }}
        </button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  copyDriveItems,
  getMyDriveItemsQuery,
  getWorkspaceItemsQuery,
  getWorkspaces
} from '../../api/cloudnext';
import AppIcon from '../icons/AppIcon.vue';
import FileTypeIcon from './FileTypeIcon.vue';

const props = defineProps({
  open: { type: Boolean, default: false },
  items: { type: Array, default: () => [] }
});
const emit = defineEmits(['close', 'copied']);

const workspaces = ref([]);
const targetType = ref('PERSONAL');
const targetScopeId = ref('');
const currentParentId = ref('');
const folders = ref([]);
const path = ref([]);
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const conflictStrategy = ref('keep_both');
let folderRequestId = 0;

const writableWorkspaceRoles = new Set(['MEMBER', 'ADMIN', 'OWNER']);
const writableWorkspaces = computed(() => workspaces.value.filter((workspace) =>
  String(workspace.status || '').toUpperCase() === 'ACTIVE'
  && writableWorkspaceRoles.has(String(workspace.myRole || '').toUpperCase())));
const locationText = computed(() => {
  const root = targetType.value === 'PERSONAL'
    ? '我的文件'
    : (writableWorkspaces.value.find((item) => item.id === targetScopeId.value)?.name || '协作空间');
  return [root, ...path.value.map((item) => item.name)].join(' / ');
});

const loadFolders = async () => {
  const requestId = ++folderRequestId;
  const requestedType = targetType.value;
  const requestedScopeId = targetScopeId.value;
  const requestedParentId = currentParentId.value;
  loading.value = true;
  errorMessage.value = '';
  try {
    const loaded = [];
    let pageNumber = 1;
    let totalPages = 1;
    do {
      const params = { parentId: requestedParentId, page: pageNumber, size: 100, itemType: 'folder', sortBy: 'name', sortOrder: 'asc' };
      const response = requestedType === 'PERSONAL'
        ? await getMyDriveItemsQuery(params)
        : await getWorkspaceItemsQuery(requestedScopeId, params);
      if (requestId !== folderRequestId) return;
      loaded.push(...(Array.isArray(response?.items) ? response.items : []));
      totalPages = Number(response?.totalPages || 0);
      pageNumber += 1;
    } while (pageNumber <= totalPages);
    if (requestId === folderRequestId) folders.value = loaded;
  } catch (error) {
    if (requestId === folderRequestId) {
      folders.value = [];
      errorMessage.value = error?.message || '目标目录读取失败';
    }
  } finally {
    if (requestId === folderRequestId) loading.value = false;
  }
};

const requestClose = () => {
  if (!submitting.value) emit('close');
};
const onKeydown = (event) => {
  if (props.open && event.key === 'Escape') requestClose();
};

const resetLocation = () => {
  currentParentId.value = '';
  path.value = [];
};
const selectPersonal = () => {
  targetType.value = 'PERSONAL';
  targetScopeId.value = '';
  resetLocation();
  void loadFolders();
};
const selectWorkspace = (workspace) => {
  targetType.value = 'WORKSPACE';
  targetScopeId.value = workspace.id;
  resetLocation();
  void loadFolders();
};
const browseInto = (folder) => {
  currentParentId.value = folder.id;
  path.value.push({ id: folder.id, name: folder.name });
  void loadFolders();
};
const goBack = () => {
  path.value.pop();
  currentParentId.value = path.value[path.value.length - 1]?.id || '';
  void loadFolders();
};

const confirmCopy = async () => {
  submitting.value = true;
  errorMessage.value = '';
  try {
    const response = await copyDriveItems({
      itemIds: props.items.map((item) => item.id),
      targetScopeType: targetType.value,
      targetScopeId: targetScopeId.value,
      targetParentId: currentParentId.value,
      conflictStrategy: conflictStrategy.value
    });
    if (Number(response?.totalFailed || 0) > 0) {
      const details = Object.values(response.failedItems || {}).join('；');
      errorMessage.value = `已复制 ${response.totalSucceeded || 0} 项，${response.totalFailed} 项失败${details ? `：${details}` : ''}`;
      return;
    }
    emit('copied', response);
  } catch (error) {
    errorMessage.value = error?.message || '复制失败';
  } finally {
    submitting.value = false;
  }
};

watch(() => props.open, async (open) => {
  if (!open) {
    folderRequestId += 1;
    return;
  }
  errorMessage.value = '';
  conflictStrategy.value = 'keep_both';
  targetType.value = 'PERSONAL';
  targetScopeId.value = '';
  resetLocation();
  try {
    const response = await getWorkspaces();
    workspaces.value = Array.isArray(response) ? response : [];
  } catch (error) {
    workspaces.value = [];
  }
  if (!props.open) return;
  await loadFolders();
});

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));
</script>

<style scoped>
.copy-dialog-mask { position: fixed; inset: 0; z-index: 120; display: grid; place-items: center; padding: 20px; background: rgba(15, 23, 42, .4); backdrop-filter: blur(2px); }
.copy-dialog { width: min(720px, 100%); max-height: min(720px, calc(100vh - 40px)); display: grid; grid-template-columns: 190px minmax(0, 1fr); grid-template-rows: auto auto minmax(180px, 1fr) auto auto auto; overflow: hidden; border: 1px solid var(--border-color); border-radius: 18px; background: var(--bg-panel); box-shadow: 0 24px 70px rgba(15, 23, 42, .22); }
.copy-dialog-header { grid-column: 1 / -1; display: flex; align-items: flex-start; justify-content: space-between; padding: 20px 22px 16px; border-bottom: 1px solid var(--border-color); }
.copy-dialog-header h2 { margin: 0; font-size: 20px; }
.copy-dialog-header p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }
.copy-close { width: 32px; height: 32px; border-radius: 9px; color: var(--text-secondary); font-size: 24px; line-height: 1; }
.copy-close:hover { background: var(--bg-panel-soft); color: var(--text-primary); }
.copy-scope-list { grid-row: 2 / 7; display: flex; flex-direction: column; gap: 4px; min-height: 0; padding: 12px; overflow-y: auto; border-right: 1px solid var(--border-color); background: var(--bg-panel-muted); }
.copy-scope-list button { display: flex; align-items: center; gap: 8px; min-height: 38px; padding: 0 10px; border-radius: 9px; color: var(--text-secondary); text-align: left; }
.copy-scope-list button.active { background: rgba(47, 107, 255, .1); color: var(--primary-color); font-weight: 600; }
.copy-location-bar { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid var(--border-color); }
.copy-location-bar button { display: inline-flex; align-items: center; gap: 4px; color: var(--text-secondary); }
.copy-location-bar span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: var(--text-primary); }
.copy-folder-list { min-height: 0; padding: 8px 12px; overflow-y: auto; }
.copy-folder-list button { width: 100%; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; min-height: 48px; padding: 6px 8px; border-radius: 10px; text-align: left; }
.copy-folder-list button:hover { background: var(--bg-panel-soft); }
.copy-folder-list p { margin: 32px 8px; color: var(--text-tertiary); text-align: center; font-size: 13px; }
.copy-conflict { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; border-top: 1px solid var(--border-color); color: var(--text-secondary); font-size: 13px; }
.copy-conflict select { min-width: 170px; height: 36px; padding: 0 10px; border: 1px solid var(--border-color); border-radius: 9px; background: var(--bg-panel); }
.copy-error { margin: 0; padding: 0 16px 10px; color: var(--danger-color, #dc2626); font-size: 13px; }
.copy-dialog footer { display: flex; justify-content: flex-end; gap: 10px; padding: 14px 16px 16px; border-top: 1px solid var(--border-color); }
.copy-dialog footer button { min-height: 38px; padding: 0 16px; border-radius: 10px; }
.copy-secondary { border: 1px solid var(--border-color); color: var(--text-secondary); }
.copy-primary { background: var(--primary-color); color: #fff; font-weight: 600; }
@media (max-width: 640px) {
  .copy-dialog-mask { padding: 0; align-items: end; }
  .copy-dialog { width: 100%; max-height: 88vh; grid-template-columns: 1fr; grid-template-rows: auto auto auto minmax(180px, 1fr) auto auto auto; border-radius: 18px 18px 0 0; }
  .copy-scope-list { grid-row: auto; flex-direction: row; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--border-color); }
  .copy-scope-list button { flex: 0 0 auto; }
}
</style>
