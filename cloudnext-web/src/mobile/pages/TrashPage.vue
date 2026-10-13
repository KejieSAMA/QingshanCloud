<template>
  <section class="cloudnext-page drive-directory-page" :class="{ 'm-page-flush': true }">


      <div class="m-trash-bar">
        <span class="m-trash-bar-count">共 {{ total }} 项</span>
        <button
          class="m-trash-bar-clear m-tap"
          type="button"
          :disabled="submitting || loading || !total"
          @click="onClearTrash"
        >
          清空回收站
        </button>
      </div>
      <MobileFilterChips v-model="activeFilter" :chips="filters" :sort-by="sortBy" :sort-order="sortOrder"
        :sort-options="{ name: '名称', deletedAt: '删除时间', sizeBytes: '大小' }"
        @sort-change="({ sortBy: key, sortOrder: order }) => setSort(key, order)" />
      <p v-if="message" class="m-trash-msg m-trash-msg-ok">{{ message }}</p>
      <p v-if="error" class="m-trash-msg m-trash-msg-error">{{ error }}</p>
      <MobileFileList
        :items="displayItems"
        :loading="loading"
        :selecting="selectedCount > 0"
        :selected-ids="selectedIds"
        empty-text="回收站当前为空"
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
        @cancel="clearMobileSelection"
        @action="onMobileSelectionAction"
      />
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />

  </section>
</template>
<script setup>

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { clearTrashItems, getDriveSortPreference, getTrashItemsQuery, purgeTrashItems, restoreDriveItems, updateDriveSortPreference } from '../../api/cloudnext';
import { emitDriveRefreshSignal } from '../../session';
import { formatFileSize, formatTime } from '../../utils/formatters';
import MobileFilterChips from '../../components/mobile/MobileFilterChips.vue';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import MobileSelectionBar from '../../components/mobile/MobileSelectionBar.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const filters = [
  { key: 'all', label: '全部' },
  { key: 'folder', label: '文件夹' },
  { key: 'file', label: '文件' }
];

const activeFilter = ref('all');
const keyword = ref('');
const page = ref(1);
const pageSize = ref(50);
const sortBy = ref('deletedAt');
const sortOrder = ref('desc');
const items = ref([]);
const total = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const message = ref('');
const selectedIds = ref([]);
let searchTimer = null;

const displayItems = computed(() =>
  items.value.map((item) => ({
    ...item,
    description: item.parentName ? `原位置：${item.parentName}` : '根目录',
    ownerAvatar: item.ownerName?.slice(0, 1) || '用',
    deletedLabel: formatTime(item.deletedAt),
    sizeLabel: item.itemType === 'folder' ? '--' : formatFileSize(item.sizeBytes)
  }))
);

const selectedItems = computed(() => displayItems.value.filter((item) => selectedIds.value.includes(item.id)));
const selectedCount = computed(() => selectedItems.value.length);

const buildQuery = () => ({
  page: page.value,
  size: pageSize.value,
  keyword: keyword.value,
  itemType: activeFilter.value === 'all' ? '' : activeFilter.value,
  sortBy: sortBy.value,
  sortOrder: sortOrder.value
});

const loadItems = async () => {
  loading.value = true;
  error.value = '';
  try {
    const result = await getTrashItemsQuery(buildQuery());
    items.value = Array.isArray(result?.items) ? result.items : [];
    total.value = Number(result?.total || 0);
    totalPages.value = Number(result?.totalPages || 0);
    page.value = Number(result?.page || page.value);
    pageSize.value = Number(result?.size || pageSize.value);
  } catch (e) {
    error.value = e.message || '回收站加载失败';
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const scheduleLoad = (resetPage = false) => {
  if (resetPage) {
    page.value = 1;
  }
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    void loadItems();
  }, 220);
};

const changePage = (nextPage) => {
  page.value = Math.max(1, nextPage);
  scheduleLoad(false);
};

const setSort = (key, order) => {
  sortBy.value = key;
  sortOrder.value = order === 'asc' ? 'asc' : 'desc';
  void updateDriveSortPreference({ context: 'trash', sortBy: sortBy.value, sortOrder: sortOrder.value }).catch(() => {});
  scheduleLoad(true);
};

const applyBatchResult = async (result, fallbackMessage) => {
  await loadItems();
  emitDriveRefreshSignal();
  selectedIds.value = [];
  message.value = result?.message || fallbackMessage;
};

const onRestoreOne = async (item) => {
  await onBatchRestore([item]);
};

const onBatchRestore = async (targetItems = selectedItems.value) => {
  if (!targetItems.length) {
    return;
  }
  submitting.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await restoreDriveItems({ itemIds: targetItems.map((item) => item.id) });
    if (result?.totalFailed) {
      message.value = result.message || '部分项目恢复失败';
    }
    await applyBatchResult(result, '恢复完成。');
  } catch (e) {
    error.value = e.message || '恢复失败';
  } finally {
    submitting.value = false;
  }
};

const onPurgeOne = async (item) => {
  await onBatchPurge([item]);
};

const onBatchPurge = async (targetItems = selectedItems.value) => {
  if (!targetItems.length) {
    return;
  }
  if (!window.confirm(targetItems.length === 1 ? `确认彻底删除“${targetItems[0].name}”吗？` : `确认彻底删除选中的 ${targetItems.length} 项吗？`)) {
    return;
  }
  submitting.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await purgeTrashItems({ itemIds: targetItems.map((item) => item.id) });
    await applyBatchResult(result, '彻底删除完成。');
  } catch (e) {
    error.value = e.message || '彻底删除失败';
  } finally {
    submitting.value = false;
  }
};

const onClearTrash = async () => {
  if (!window.confirm('确认清空当前用户的回收站吗？该操作不可撤销。')) {
    return;
  }
  submitting.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await clearTrashItems();
    await applyBatchResult(result, '回收站已清空。');
  } catch (e) {
    error.value = e.message || '清空回收站失败';
  } finally {
    submitting.value = false;
  }
};

// === 移动端逻辑 ===

const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');

const mobileSheetActions = computed(() => [
  { key: 'restore', label: '恢复' },
  { key: 'purge', label: '彻底删除', danger: true }
]);

const M_ICON_RESTORE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>';
const M_ICON_PURGE = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>';

const mobileSelectionActions = computed(() => [
  { key: 'restore', label: '恢复', icon: M_ICON_RESTORE },
  { key: 'purge', label: '彻底删除', icon: M_ICON_PURGE, danger: true }
]);

const clearMobileSelection = () => {
  selectedIds.value = [];
};

const onMobileItemClick = (item) => {
  if (selectedCount.value > 0) {
    onMobileItemToggle(item);
    return;
  }
  onMobileItemMore(item);
};

const onMobileItemLongPress = (item) => {
  if (!selectedIds.value.includes(item.id)) {
    selectedIds.value = [...selectedIds.value, item.id];
  }
};

const onMobileItemToggle = (item) => {
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
  if (key === 'restore') {
    void onRestoreOne(item);
  } else if (key === 'purge') {
    void onPurgeOne(item);
  }
};

const onMobileSelectionAction = (key) => {
  if (key === 'restore') {
    void onBatchRestore();
  } else if (key === 'purge') {
    void onBatchPurge();
  }
};

watch([activeFilter, pageSize], () => {
  scheduleLoad(true);
});

watch(keyword, () => {
  scheduleLoad(true);
});

watch(displayItems, (nextItems) => {
  const allowedIds = new Set(nextItems.map((item) => item.id));
  selectedIds.value = selectedIds.value.filter((id) => allowedIds.has(id));
});

onMounted(async () => {
  try {
    const preference = await getDriveSortPreference('trash');
    sortBy.value = preference?.sortBy || 'deletedAt';
    sortOrder.value = preference?.sortOrder || 'desc';
  } catch (error) { }
  await loadItems();
});

onUnmounted(() => {
  window.clearTimeout(searchTimer);
});
</script>
<style scoped>

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

.toolbar-select select:focus {
  border-color: rgba(47, 107, 255, 0.6);
  box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.12);
}

.drive-directory-page { height: 100%; min-height: 0; display: flex; flex-direction: column; }

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-trash-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.m-trash-bar-count {
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
}

.m-trash-bar-clear {
  height: 32px;
  padding: 0 var(--m-sp-3);
  background: transparent;
  color: #b42318;
  border-radius: var(--radius-md);
  font-size: var(--m-fs-meta);
  font-weight: 500;
}

.m-trash-bar-clear:active:not(:disabled) {
  background: var(--bg-danger-soft);
}

.m-trash-bar-clear:disabled {
  opacity: 0.4;
}

.m-trash-msg {
  margin: 0;
  padding: var(--m-sp-2) var(--m-sp-3);
  font-size: var(--m-fs-meta);
}

.m-trash-msg-ok {
  background: rgba(34, 197, 94, 0.08);
  color: #15803d;
}

.m-trash-msg-error {
  background: rgba(220, 38, 38, 0.08);
  color: #b42318;
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

</style>
