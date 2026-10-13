<template>
  <section class="cloudnext-page" :class="{ 'm-page-flush': true }">


      <div class="m-ws-search">
        <label class="m-ws-search-wrap">
          <svg class="m-ws-search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <circle cx="11" cy="11" r="7" />
            <line x1="16.5" y1="16.5" x2="21" y2="21" />
          </svg>
          <input v-model.trim="keyword" type="search" placeholder="搜索协作空间" />
        </label>
        <div class="m-ws-sort" role="group" aria-label="协作空间排序方式">
          <button
            v-for="option in sortOptions"
            :key="option.value"
            class="m-ws-sort-button"
            :class="{ active: sortMode === option.value }"
            type="button"
            :disabled="sortSaving"
            @click="onChangeSortMode(option.value)"
          >
            {{ option.mobileLabel }}
          </button>
        </div>
      </div>
      <div v-if="loading" class="m-ws-state">加载中…</div>
      <div v-else-if="errorText" class="m-ws-state m-ws-state-error">
        <strong>加载失败</strong>
        <p>{{ errorText }}</p>
      </div>
      <div v-else-if="!filteredItems.length" class="m-ws-state">
        <strong>{{ keyword ? '未找到匹配的协作空间' : '暂未加入任何协作空间' }}</strong>
        <p>{{ keyword ? '换个关键字试试。' : '点击右下角 + 创建你的第一个空间。' }}</p>
      </div>
      <div v-else class="m-ws-list">
        <MobileWorkspaceCard
          v-for="item in filteredItems"
          :key="item.id"
          :item="item"
          :role-label="formatRoleLabel(item.myRole)"
          @click="enterWorkspace(item)"
          @more="onMobileWorkspaceMore(item)"
        />
      </div>
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />


    <div v-if="createDialogOpen" class="dialog-mask" @click.self="onCloseCreate">
      <div
        class="dialog-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="ws-create-title"
      >
        <div class="dialog-head">
          <h2 id="ws-create-title">创建协作空间</h2>
          <p>请输入空间名称，最长 100 字符。</p>
        </div>
        <label class="dialog-field">
          <span>空间名称</span>
          <input
            v-model.trim="createName"
            type="text"
            maxlength="100"
            @keydown.enter.prevent="onConfirmCreate"
          />
        </label>
        <div v-if="createError" class="dialog-error">{{ createError }}</div>
        <div class="dialog-actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="creating"
            @click="onCloseCreate"
          >
            取消
          </button>
          <button
            class="primary-button"
            type="button"
            :disabled="creating || !createName"
            @click="onConfirmCreate"
          >
            {{ creating ? '创建中…' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
<script setup>

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  createWorkspace,
  deleteWorkspace,
  getWorkspaces,
  getWorkspaceSortPreference,
  updateWorkspace,
  updateWorkspaceSortPreference
} from '../../api/cloudnext';
import {
  canDeleteWorkspace as roleCanDeleteWorkspace,
  canRenameWorkspace as roleCanRenameWorkspace,
  formatRoleLabel
} from '../../composables/drive/workspacePermissions';
import { formatTime } from '../../utils/formatters';
import MobileWorkspaceCard from '../../components/mobile/MobileWorkspaceCard.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const creating = ref(false);
const createDialogOpen = ref(false);
const createName = ref('');
const createError = ref('');
const items = ref([]);
const errorText = ref('');
const workspaceActionBusy = ref(false);
const sortMode = ref('TIME');
const sortSaving = ref(false);
const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const sortOptions = [
  { value: 'TIME', label: '创建时间', mobileLabel: '时间' },
  { value: 'NAME', label: '名称', mobileLabel: '名称' }
];

const normalizeSortMode = (value) => (value === 'NAME' ? 'NAME' : 'TIME');

const compareByName = (left, right) => {
  const byName = String(left?.name || '').localeCompare(String(right?.name || ''));
  if (byName !== 0) {
    return byName;
  }
  return String(left?.id || '').localeCompare(String(right?.id || ''));
};

const compareByTime = (left, right) => {
  const leftTime = left?.createdAt || left?.updatedAt || '';
  const rightTime = right?.createdAt || right?.updatedAt || '';
  if (leftTime !== rightTime) {
    return rightTime.localeCompare(leftTime);
  }
  return compareByName(left, right);
};

const filteredItems = computed(() => {
  const list = [...items.value]
    .sort(sortMode.value === 'NAME' ? compareByName : compareByTime)
    .map((item) => ({
      ...item,
      creatorAvatar: (item.creatorName || '?').slice(0, 1),
      createdLabel: item.createdAt ? formatTime(item.createdAt) : (item.updatedAt ? formatTime(item.updatedAt) : '--'),
      updatedLabel: item.createdAt ? formatTime(item.createdAt) : (item.updatedAt ? formatTime(item.updatedAt) : '')
    }));
  if (!keyword.value) {
    return list;
  }
  const k = keyword.value.toLowerCase();
  return list.filter(
    (it) =>
      (it.name || '').toLowerCase().includes(k) ||
      (it.creatorName || '').toLowerCase().includes(k)
  );
});

const reload = async () => {
  loading.value = true;
  errorText.value = '';
  try {
    const list = await getWorkspaces();
    items.value = Array.isArray(list) ? list : [];
  } catch (error) {
    errorText.value = error?.message || '加载失败';
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const enterWorkspace = (item) => {
  router.push({ name: 'workspace-detail', params: { id: item.id } });
};

const loadInitialData = async () => {
  loading.value = true;
  errorText.value = '';
  try {
    const [preference, list] = await Promise.all([
      getWorkspaceSortPreference().catch(() => ({ sortMode: 'TIME' })),
      getWorkspaces()
    ]);
    sortMode.value = normalizeSortMode(preference?.sortMode);
    items.value = Array.isArray(list) ? list : [];
  } catch (error) {
    errorText.value = error?.message || '加载失败';
    items.value = [];
    sortMode.value = 'TIME';
  } finally {
    loading.value = false;
  }
};

const onChangeSortMode = async (nextMode) => {
  const normalized = normalizeSortMode(nextMode);
  if (sortMode.value === normalized) {
    return;
  }
  const previous = sortMode.value;
  sortMode.value = normalized;
  sortSaving.value = true;
  errorText.value = '';
  try {
    const saved = await updateWorkspaceSortPreference(normalized);
    sortMode.value = normalizeSortMode(saved?.sortMode);
  } catch (error) {
    sortMode.value = previous;
    errorText.value = error?.message || '排序偏好保存失败';
  } finally {
    sortSaving.value = false;
  }
};

const canRenameWorkspaceItem = (item) => roleCanRenameWorkspace(item?.myRole);
const canDeleteWorkspaceItem = (item) => roleCanDeleteWorkspace(item?.myRole);

const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');
const mobileSheetActions = computed(() => {
  const item = mobileSheetItem.value;
  if (!item) {
    return [];
  }
  const actions = [{ key: 'enter', label: '进入' }];
  if (canRenameWorkspaceItem(item)) {
    actions.push({ key: 'rename', label: '改名' });
  }
  if (canDeleteWorkspaceItem(item)) {
    actions.push({ key: 'delete', label: '删除', danger: true });
  }
  return actions;
});

const onRenameWorkspace = async (item) => {
  if (!canRenameWorkspaceItem(item)) {
    errorText.value = '当前角色无改名权限';
    return;
  }
  const raw = window.prompt('请输入新的协作空间名称', item?.name || '');
  const name = (raw || '').trim();
  if (!name || name === item.name) {
    return;
  }
  if (name.length > 100) {
    errorText.value = '空间名称不能超过 100 字符。';
    return;
  }
  workspaceActionBusy.value = true;
  errorText.value = '';
  try {
    const updated = await updateWorkspace(item.id, { name });
    items.value = items.value.map((current) =>
      current.id === item.id ? { ...current, ...(updated || {}), name } : current
    );
  } catch (error) {
    errorText.value = error?.message || '改名失败';
  } finally {
    workspaceActionBusy.value = false;
  }
};

const onDeleteWorkspace = async (item) => {
  if (!canDeleteWorkspaceItem(item)) {
    errorText.value = '当前角色无删除权限';
    return;
  }
  if (!window.confirm(`确认删除协作空间「${item.name}」?删除后列表中将不再显示。`)) {
    return;
  }
  workspaceActionBusy.value = true;
  errorText.value = '';
  try {
    await deleteWorkspace(item.id);
    items.value = items.value.filter((current) => current.id !== item.id);
  } catch (error) {
    errorText.value = error?.message || '删除失败';
  } finally {
    workspaceActionBusy.value = false;
  }
};

const onMobileWorkspaceMore = (item) => {
  mobileSheetItem.value = item;
  mobileSheetOpen.value = true;
};

const onMobileSheetSelect = (key) => {
  const item = mobileSheetItem.value;
  mobileSheetOpen.value = false;
  if (!item) {
    return;
  }
  if (key === 'enter') {
    enterWorkspace(item);
  } else if (key === 'rename') {
    void onRenameWorkspace(item);
  } else if (key === 'delete') {
    void onDeleteWorkspace(item);
  }
};

const onOpenCreate = () => {
  createName.value = '';
  createError.value = '';
  createDialogOpen.value = true;
};

const onCloseCreate = () => {
  if (creating.value) return;
  createDialogOpen.value = false;
};

const onConfirmCreate = async () => {
  const name = createName.value.trim();
  if (!name) {
    createError.value = '空间名称不能为空。';
    return;
  }
  if (name.length > 100) {
    createError.value = '空间名称不能超过 100 字符。';
    return;
  }
  creating.value = true;
  createError.value = '';
  try {
    const detail = await createWorkspace({ name });
    createDialogOpen.value = false;
    if (detail?.id) {
      router.push({ name: 'workspace-detail', params: { id: detail.id } });
    } else {
      await reload();
    }
  } catch (error) {
    createError.value = error?.message || '创建失败';
  } finally {
    creating.value = false;
  }
};

const onMobileFabAction = (event) => {

  if (event?.detail?.key === 'create-workspace') {
    onOpenCreate();
  }
};

onMounted(() => {
  void loadInitialData();
  window.addEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});

onUnmounted(() => {
  window.removeEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});
</script>
<style scoped>

.m-ws-sort {
  display: inline-flex;
  align-items: center;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
}

.m-ws-sort-button {
  border: none;
  border-radius: calc(var(--radius-md) - 3px);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 600;
}

.m-ws-sort-button.active {
  background: var(--bg-panel);
  color: var(--text-primary);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.m-ws-sort-button:disabled {
  cursor: wait;
  opacity: 0.72;
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

.dialog-field input {
  width: 100%;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 14px;
  color: var(--text-primary);
  background: var(--bg-panel);
}

.dialog-error {
  color: var(--danger-color, #d33);
  font-size: 13px;
  margin: 0;
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-ws-search {
  display: grid;
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.m-ws-search-wrap {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  height: 38px;
  padding: 0 var(--m-sp-3);
  background: var(--bg-panel-soft);
  border-radius: 19px;
}

.m-ws-search-icon {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.m-ws-search-wrap input {
  flex: 1;
  height: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: var(--m-fs-body);
}

.m-ws-sort {
  justify-self: flex-start;
}

.m-ws-sort-button {
  height: 28px;
  padding: 0 10px;
  font-size: var(--m-fs-meta);
}

.m-ws-state {
  padding: 64px var(--m-sp-4);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--m-fs-body);
}

.m-ws-state strong {
  display: block;
  font-size: var(--m-fs-section);
  color: var(--text-primary);
  margin-bottom: var(--m-sp-1);
}

.m-ws-state p {
  margin: 0;
  font-size: var(--m-fs-meta);
}

.m-ws-state-error strong {
  color: #b42318;
}

.m-ws-list {
  background: #fff;
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
