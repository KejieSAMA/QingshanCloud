<template>
  <RouterView v-if="route.meta.public" />
  <MobileShell v-else-if="isMobile" :key="'mobile-' + ui.accountRevision"><RouterView /></MobileShell>
  <DesktopShell v-else :key="'desktop-' + ui.accountRevision" />
  <div v-if="!route.meta.public" class="desktop-overlays" :data-primary-color="ui.effectivePrimaryColor"><DialogHost /><PreviewHost /></div>
</template>
<script setup>
import { defineAsyncComponent, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import { useBreakpoint } from './composables/useBreakpoint';
import { ui, applyPrefs } from './next/state';
const DesktopShell = defineAsyncComponent(() => import('./next/App.vue'));
const MobileShell = defineAsyncComponent(() => import('./components/mobile/MobileShell.vue'));
const DialogHost = defineAsyncComponent(() => import('./next/components/DialogHost.vue'));
const PreviewHost = defineAsyncComponent(() => import('./next/components/PreviewHost.vue'));
const route = useRoute(), { isMobile } = useBreakpoint();
watch([() => route.meta.public, () => route.meta.standalone, isMobile], () => {
  document.documentElement.dataset.experience = route.meta.standalone ? 'preview' : isMobile.value ? 'mobile' : 'desktop';
  ui.popup = null; ui.detail = null;
  applyPrefs(route.meta.public ? { ...ui.prefs, primaryColor: 'blue' } : ui.prefs);
}, { immediate: true, flush: 'sync' });
</script>
