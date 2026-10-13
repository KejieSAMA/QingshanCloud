<template>
  <nav class="m-tabs" role="navigation" aria-label="底部导航">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.to"
      :to="tab.to"
      class="m-tab m-tap"
      :class="{ 'is-active': isActive(tab) }"
    >
      <span class="m-tab-icon" v-html="tab.icon"></span>
      <span class="m-tab-label">{{ tab.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { RouterLink, useRoute } from 'vue-router';

const route = useRoute();

const tabs = [
  {
    to: '/',
    name: 'home',
    label: '首页',
    icon:
      '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11l9-8 9 8v10a2 2 0 0 1-2 2h-4v-7h-6v7H5a2 2 0 0 1-2-2V11z"/></svg>'
  },
  {
    to: '/my-files',
    name: 'my-files',
    label: '文件',
    icon:
      '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z"/></svg>'
  },
  {
    to: '/shared',
    name: 'shared',
    label: '共享',
    icon:
      '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>'
  },
  {
    to: '/workspaces',
    name: 'workspaces',
    label: '协作',
    icon:
      '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>'
  }
];

const isActive = (tab) => {
  if (tab.name === 'workspaces') {
    return route.name === 'workspaces' || route.name === 'workspace-detail';
  }
  return route.name === tab.name;
};
</script>

<style scoped>
.m-tabs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: calc(var(--m-tabbar-h) + var(--m-safe-bottom));
  padding-bottom: var(--m-safe-bottom);
  z-index: 30;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: #fff;
  box-shadow: 0 -1px 0 rgba(15, 23, 42, 0.06);
}

.m-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--text-tertiary);
  text-decoration: none;
  font-size: var(--m-fs-meta);
}

.m-tab-icon {
  display: inline-flex;
}

.m-tab.is-active {
  color: var(--bg-primary);
}

.m-tab:active {
  background: var(--bg-nav-hover);
}
</style>
