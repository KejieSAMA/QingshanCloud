<template>
  <section class="cloudnext-page" :class="{ 'm-page-flush': true }">


      <div class="m-link-bar">
        <span class="m-link-bar-summary">{{ summaryText }}</span>
        <button class="m-link-bar-refresh m-tap" type="button" :disabled="loading" @click="refresh">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>
      <p v-if="errorText" class="m-trash-msg m-trash-msg-error">{{ errorText }}</p>
      <p v-if="successText" class="m-trash-msg m-trash-msg-ok">{{ successText }}</p>
      <div v-if="!links.length && !loading" class="m-link-empty">
        暂无外链。点击右下角 + 为文件生成第一个分享链接。
      </div>
      <div v-else class="m-link-list">
        <MobileLinkCard
          v-for="link in mobileEnrichedLinks"
          :key="link.id"
          :link="link"
          :disabled="isLinkBusy(link)"
          @copy="onCopy(link)"
          @more="onMobileMore(link)"
        />
      </div>
      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />


    <LinkCreateDialog
      v-if="createDialogOpen"
      :open="createDialogOpen"
      @close="onCreateDialogClose"
      @created="onLinkCreated"
    />

    <LinkUpdateDialog
      v-if="updateDialogOpen && editingLink"
      :open="updateDialogOpen"
      :link="editingLink"
      @close="onUpdateDialogClose"
      @updated="onLinkUpdated"
    />

    <LinkAccessLogsDialog
      :open="accessLogsDialogOpen"
      :link="accessLogsLink"
      @close="onCloseAccessLogs"
    />
  </section>
</template>
<script setup>

import { computed, ref, onMounted, onUnmounted } from 'vue';

import LinkCreateDialog from '../../components/drive/LinkCreateDialog.vue';
import LinkUpdateDialog from '../../components/drive/LinkUpdateDialog.vue';
import LinkAccessLogsDialog from '../../components/drive/LinkAccessLogsDialog.vue';
import { useLinksPageState } from '../../composables/drive/useLinksPageState';
import { formatTime } from '../../utils/formatters';
import MobileLinkCard from '../../components/mobile/MobileLinkCard.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const {
  links,
  loading,
  errorText,
  successText,
  summaryText,
  isLinkBusy,
  refresh,
  onCopy,
  onLinkCreated: onLinkCreatedFromState,
  onLinkUpdated: onLinkUpdatedFromState,
  onRevoke,
  cancelSuccessTimer
} = useLinksPageState();

const createDialogOpen = ref(false);
const updateDialogOpen = ref(false);
const editingLink = ref(null);

const formatExpires = (expiresAt) => {
  if (!expiresAt) return '永久有效';
  const formatted = formatTime(expiresAt);
  return formatted || '永久有效';
};

const parseExpiresAt = (expiresAt) => {
  if (!expiresAt) return null;
  const date = expiresAt instanceof Date ? expiresAt : new Date(expiresAt);
  if (Number.isNaN(date.getTime())) return null;
  return date;
};

const isExpired = (link) => {
  const date = parseExpiresAt(link?.expiresAt);
  if (!date) return false;
  return date.getTime() < Date.now();
};

const isExhausted = (link) => {
  const limit = Number(link?.downloadLimit ?? -1);
  const count = Number(link?.downloadCount ?? 0);
  return Number.isFinite(limit) && limit >= 0 && count >= limit;
};

const openCreateDialog = () => {
  createDialogOpen.value = true;
};

const onCreateDialogClose = () => {
  createDialogOpen.value = false;
};

const onLinkCreated = (link) => {
  createDialogOpen.value = false;
  onLinkCreatedFromState(link);
};

const onEdit = (link) => {
  if (!link || link.revoked || isLinkBusy(link)) return;
  editingLink.value = link;
  updateDialogOpen.value = true;
};

const onUpdateDialogClose = () => {
  updateDialogOpen.value = false;
  editingLink.value = null;
};

const onLinkUpdated = (updated) => {
  onLinkUpdatedFromState(updated);
  updateDialogOpen.value = false;
  editingLink.value = null;
};

// === 移动端逻辑 ===

const mobileSheetOpen = ref(false);
const mobileSheetLink = ref(null);
const mobileSheetTitle = computed(() => mobileSheetLink.value?.fileName || '');

const mobileSheetActions = computed(() => {
  const link = mobileSheetLink.value;
  if (!link) {
    return [];
  }
  if (link.revoked) {
    return [{ key: 'access-logs', label: '访问统计' }];
  }
  return [
    { key: 'copy', label: '复制链接' },
    { key: 'edit', label: '修改' },
    { key: 'access-logs', label: '访问统计' },
    { key: 'revoke', label: '失效链接', danger: true }
  ];
});

const accessLogsDialogOpen = ref(false);
const accessLogsLink = ref(null);

const onShowAccessLogs = (link) => {
  accessLogsLink.value = link;
  accessLogsDialogOpen.value = true;
};

const onCloseAccessLogs = () => {
  accessLogsDialogOpen.value = false;
};

const mobileEnrichedLinks = computed(() =>
  links.value.map((link) => ({
    ...link,
    expiresLabel: formatExpires(link.expiresAt),
    isExpired: isExpired(link),
    isExhausted: isExhausted(link)
  }))
);

const onMobileMore = (link) => {
  mobileSheetLink.value = link;
  mobileSheetOpen.value = true;
};

const onMobileSheetSelect = (key) => {
  const link = mobileSheetLink.value;
  mobileSheetOpen.value = false;
  if (!link) {
    return;
  }
  switch (key) {
    case 'copy':
      onCopy(link);
      break;
    case 'edit':
      onEdit(link);
      break;
    case 'access-logs':
      onShowAccessLogs(link);
      break;
    case 'revoke':
      void onRevoke(link);
      break;
  }
};

const onMobileFabAction = (event) => {

  if (event?.detail?.key === 'create-link') {
    openCreateDialog();
  }
};

onMounted(() => {
  void refresh();
  window.addEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});

onUnmounted(() => {
  cancelSuccessTimer();
  window.removeEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});
</script>
<style scoped>

.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-link-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.m-link-bar-summary {
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-link-bar-refresh {
  height: 32px;
  padding: 0 var(--m-sp-3);
  background: var(--bg-panel-soft);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  font-size: var(--m-fs-meta);
}

.m-link-bar-refresh:disabled {
  opacity: 0.5;
}

.m-link-empty {
  padding: 64px var(--m-sp-4);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--m-fs-body);
}

.m-link-list {
  background: #fff;
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

</style>
