<template>
  <div class="m-shell">
    <MobileTopbar
      :title="routeTitle"
      :show-search="showSearch"
      @open-drawer="drawerOpen = true"
      @search="router.push({ name: 'search' })"
    />
    <main class="m-shell-main m-scroll">
      <slot />
    </main>
    <MobileFAB v-if="showFab" />
    <MobileBottomTabs />
    <MobileDrawer :open="drawerOpen" @close="drawerOpen = false" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MobileTopbar from './MobileTopbar.vue';
import MobileBottomTabs from './MobileBottomTabs.vue';
import MobileDrawer from './MobileDrawer.vue';
import MobileFAB from './MobileFAB.vue';

const route = useRoute();
const router = useRouter();
const drawerOpen = ref(false);

const titleMap = {
  search: '搜索',
  recent: '最近打开',
  favorites: '我的收藏',
  home: '云盘',
  'my-files': '我的文件',
  shared: '共享文件',
  workspaces: '协作空间',
  'workspace-detail': '协作空间',
  links: '外链管理',
  trash: '回收站',
  admin: '系统管理'
};

const routeTitle = computed(() => titleMap[route.name] || '云盘');
const showFab = computed(() =>
  ['home', 'my-files', 'shared', 'workspaces', 'workspace-detail', 'links'].includes(route.name)
);
const showSearch = computed(() =>
  ['home', 'my-files', 'shared', 'workspaces', 'workspace-detail', 'links', 'trash'].includes(route.name)
);
</script>

<style scoped>
.m-shell {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-page);
  padding-top: calc(var(--m-topbar-h) + var(--m-safe-top));
  padding-bottom: calc(var(--m-tabbar-h) + var(--m-safe-bottom));
}

.m-shell-main {
  position: relative;
  min-height: calc(100dvh - var(--m-topbar-h) - var(--m-tabbar-h) - var(--m-safe-top) - var(--m-safe-bottom));
}
</style>
