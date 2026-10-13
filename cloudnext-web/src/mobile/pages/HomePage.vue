<template>
  <!-- Preserved mobile home. Desktop home lives in next/pages. -->
  <section class="homepage" :class="{ 'm-page-flush': true }">


      <div class="m-home-welcome">
        <h1 class="m-home-greeting">{{ greeting }}</h1>
        <p class="m-home-date">{{ dateString }}</p>
      </div>

      <nav class="m-home-shortcuts" aria-label="快捷入口">
        <RouterLink to="/my-files" class="m-shortcut m-tap">
          <span class="m-shortcut-icon m-shortcut-icon-files">
            <AppIcon name="file" :size="22" />
          </span>
          <span class="m-shortcut-label">我的文件</span>
        </RouterLink>
        <RouterLink to="/shared" class="m-shortcut m-tap">
          <span class="m-shortcut-icon m-shortcut-icon-shared">
            <AppIcon name="share" :size="22" />
          </span>
          <span class="m-shortcut-label">共享文件</span>
        </RouterLink>
        <RouterLink to="/workspaces" class="m-shortcut m-tap">
          <span class="m-shortcut-icon m-shortcut-icon-workspaces">
            <AppIcon name="workspace" :size="22" />
          </span>
          <span class="m-shortcut-label">协作空间</span>
        </RouterLink>
        <RouterLink to="/trash" class="m-shortcut m-tap">
          <span class="m-shortcut-icon m-shortcut-icon-trash">
            <AppIcon name="trash" :size="22" />
          </span>
          <span class="m-shortcut-label">回收站</span>
        </RouterLink>
      </nav>

      <div class="m-home-section">
        <h2 class="m-home-section-title">最近访问</h2>
        <MobileFilterChips v-model="activeFilter" :chips="HOME_FILTER_TABS" />
        <p v-if="driveError" class="m-home-error">{{ driveError }}</p>
        <MobileFileList
          :items="filteredRecentFiles"
          empty-text="暂无最近访问的文件"
          @item-click="onMobileFileClick"
          @item-more="onMobileFileMore"
        />
      </div>

      <MobileActionSheet
        v-model:open="mobileSheetOpen"
        :title="mobileSheetTitle"
        :actions="mobileSheetActions"
        @select="onMobileSheetSelect"
      />

  </section>
</template>
<script setup>

import { computed, onMounted, onUnmounted, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import AppIcon from '../../components/icons/AppIcon.vue';

import { useHomePageData, HOME_FILTER_TABS } from '../../composables/useHomePageData';
import MobileFilterChips from '../../components/mobile/MobileFilterChips.vue';
import MobileFileList from '../../components/mobile/MobileFileList.vue';
import MobileActionSheet from '../../components/mobile/MobileActionSheet.vue';

const router = useRouter();

const {
  service,
  sync,
  stats,
  driveError,
  activeFilter,
  greeting,
  dateString,
  filteredRecentFiles,
  onPreview,
  onEdit,
  onDownload,
  onDelete
} = useHomePageData();

// 快速入口 → 转发到 /my-files 让它接管 FAB 创建对话框

// === 移动端逻辑 ===
const mobileSheetOpen = ref(false);
const mobileSheetItem = ref(null);
const mobileSheetTitle = computed(() => mobileSheetItem.value?.name || '');
const mobileSheetActions = computed(() => {
  const item = mobileSheetItem.value;
  if (!item) {
    return [];
  }
  if (item.itemType === 'folder') {
    return [{ key: 'preview', label: '打开' }];
  }
  return [
    { key: 'preview', label: '预览' },
    { key: 'edit', label: '在线编辑' },
    { key: 'download', label: '下载' },
    { key: 'delete', label: '删除', danger: true }
  ];
});

const onMobileFileClick = (item) => {
  void onPreview(item);
};

const onMobileFileMore = (item) => {
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
    case 'preview':
      void onPreview(item);
      break;
    case 'edit':
      void onEdit(item);
      break;
    case 'download':
      void onDownload(item);
      break;
    case 'delete':
      void onDelete(item);
      break;
  }
};

const onMobileFabAction = (event) => {
  const key = event?.detail?.key;
  if (!key) {
    return;
  }
  try {
    sessionStorage.setItem('cloudnext-pending-fab', key);
  } catch (e) {
    /* ignore */
  }
  void router.push({ name: 'my-files' });
};

onMounted(() => {

    window.addEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});

onUnmounted(() => {

    window.removeEventListener('cloudnext:mobile-fab-action', onMobileFabAction);
});
</script>
<style scoped>

.homepage {
  display: grid;
  gap: 16px;
}
.m-page-flush {
  padding: 0 !important;
  gap: 0 !important;
  background: var(--bg-page);
}

.m-home-welcome {
  padding: var(--m-sp-4) var(--m-sp-4) var(--m-sp-3);
  background: linear-gradient(135deg, #f8fbff 0%, #eef4ff 100%);
}

.m-home-greeting {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.m-home-date {
  margin: 4px 0 0;
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
}

.m-home-shortcuts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--m-sp-2);
  padding: var(--m-sp-3);
  background: var(--bg-page);
}

.m-shortcut {
  display: flex;
  align-items: center;
  gap: var(--m-sp-3);
  height: 64px;
  padding: 0 var(--m-sp-3);
  background: #fff;
  border-radius: var(--m-rad-card);
  text-decoration: none;
  color: var(--text-primary);
  font-size: var(--m-fs-body);
}

.m-shortcut:active {
  background: var(--bg-nav-hover);
}

.m-shortcut-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--m-rad-card);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.m-shortcut-icon-files {
  background: rgba(47, 107, 255, 0.1);
  color: #2f6bff;
}

.m-shortcut-icon-shared {
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
}

.m-shortcut-icon-workspaces {
  background: rgba(245, 101, 39, 0.12);
  color: #ea580c;
}

.m-shortcut-icon-trash {
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
}

.m-shortcut-label {
  font-weight: 500;
}

.m-home-section {
  background: #fff;
  margin-top: var(--m-sp-2);
}

.m-home-section-title {
  margin: 0;
  padding: var(--m-sp-3) var(--m-sp-3) var(--m-sp-1);
  font-size: var(--m-fs-section);
  font-weight: 600;
  color: var(--text-primary);
}

.m-home-error {
  margin: 0;
  padding: 0 var(--m-sp-3) var(--m-sp-2);
  color: #b42318;
  font-size: var(--m-fs-meta);
}

</style>
