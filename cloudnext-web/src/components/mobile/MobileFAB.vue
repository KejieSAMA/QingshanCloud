<template>
  <button class="m-fab m-tap" type="button" aria-label="新建" @click="sheetOpen = true">
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </svg>
  </button>
  <MobileActionSheet
    v-model:open="sheetOpen"
    title="新建"
    :actions="actions"
    @select="onSelect"
  />
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import MobileActionSheet from './MobileActionSheet.vue';

const route = useRoute();
const sheetOpen = ref(false);

const actions = computed(() => {
  switch (route.name) {
    case 'workspaces':
      return [{ key: 'create-workspace', label: '创建协作空间' }];
    case 'workspace-detail':
      return [
        { key: 'folder', label: '新建文件夹' },
        { key: 'upload', label: '上传文件' },
        { key: 'members', label: '成员管理' }
      ];
    case 'links':
      return [{ key: 'create-link', label: '创建外链' }];
    case 'my-files':
    case 'shared':
    case 'home':
    default:
      return [
        { key: 'folder', label: '新建文件夹' },
        { key: 'upload', label: '上传文件' },
        { key: 'photo', label: '上传图片' }
      ];
  }
});

const onSelect = (key) => {
  sheetOpen.value = false;
  window.dispatchEvent(new CustomEvent('cloudnext:mobile-fab-action', { detail: { key } }));
};
</script>

<style scoped>
.m-fab {
  position: fixed;
  right: 16px;
  bottom: calc(var(--m-tabbar-h) + var(--m-safe-bottom) + 16px);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--bg-primary);
  color: #fff;
  box-shadow: var(--m-shadow-fab);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  z-index: 25;
}

.m-fab:active {
  background: var(--bg-primary-hover);
  transform: scale(0.96);
}
</style>
