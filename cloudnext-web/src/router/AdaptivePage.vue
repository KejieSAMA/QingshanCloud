<template><component :is="page" :key="String(route.name) + String(route.params.id || '')" @upload="$emit('upload')" /></template>
<script setup>
import { computed, defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
import { useBreakpoint } from '../composables/useBreakpoint';
defineEmits(['upload']);
const route = useRoute(), { isMobile } = useBreakpoint();
const desktopFiles = defineAsyncComponent(() => import('../next/pages/FilesPage.vue'));
const desktop = {
  workspaces: defineAsyncComponent(() => import('../next/pages/WorkspacesPage.vue')),
  links: defineAsyncComponent(() => import('../next/pages/LinksPage.vue')),
  admin: defineAsyncComponent(() => import('../next/pages/AdminPage.vue'))
};
const mobile = {
  home: defineAsyncComponent(() => import('../mobile/pages/HomePage.vue')),
  'my-files': defineAsyncComponent(() => import('../mobile/pages/MyFilesPage.vue')),
  shared: defineAsyncComponent(() => import('../mobile/pages/SharedFilesPage.vue')),
  workspaces: defineAsyncComponent(() => import('../mobile/pages/WorkspacesPage.vue')),
  'workspace-detail': defineAsyncComponent(() => import('../mobile/pages/WorkspaceDetailPage.vue')),
  links: defineAsyncComponent(() => import('../mobile/pages/LinksPage.vue')),
  trash: defineAsyncComponent(() => import('../mobile/pages/TrashPage.vue')),
  search: defineAsyncComponent(() => import('../mobile/pages/SearchPage.vue')),
  admin: defineAsyncComponent(() => import('../mobile/pages/AdminPage.vue')),
  recent: defineAsyncComponent(() => import('../mobile/pages/SavedItemsPage.vue')),
  favorites: defineAsyncComponent(() => import('../mobile/pages/SavedItemsPage.vue'))
};
const page = computed(() => isMobile.value ? mobile[route.name] : (desktop[route.name] || desktopFiles));
</script>
