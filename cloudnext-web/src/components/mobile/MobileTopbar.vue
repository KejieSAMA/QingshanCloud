<template>
  <header class="m-topbar">
    <button class="m-topbar-icon m-tap" type="button" aria-label="菜单" @click="$emit('open-drawer')">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <line x1="4" y1="7" x2="20" y2="7" />
        <line x1="4" y1="12" x2="20" y2="12" />
        <line x1="4" y1="17" x2="20" y2="17" />
      </svg>
    </button>
    <h1 class="m-topbar-title">{{ title }}</h1>
    <button
      v-if="showSearch"
      class="m-topbar-icon m-tap"
      type="button"
      aria-label="搜索"
      @click="$emit('search')"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="11" cy="11" r="7" />
        <line x1="16.5" y1="16.5" x2="21" y2="21" />
      </svg>
    </button>
    <span v-else class="m-topbar-icon m-topbar-spacer" aria-hidden="true"></span>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, default: '云盘' },
  showSearch: { type: Boolean, default: true }
});
defineEmits(['open-drawer', 'search']);
</script>

<style scoped>
.m-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: calc(var(--m-topbar-h) + var(--m-safe-top));
  padding-top: var(--m-safe-top);
  z-index: 30;
  display: grid;
  grid-template-columns: var(--m-touch-h) 1fr var(--m-touch-h);
  align-items: center;
  padding-left: var(--m-sp-1);
  padding-right: var(--m-sp-1);
  background: #fff;
  box-shadow: var(--m-shadow-bar);
}

.m-topbar-title {
  margin: 0;
  text-align: center;
  font-size: var(--m-fs-title);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-topbar-icon {
  width: var(--m-touch-h);
  height: var(--m-touch-h);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-primary);
  border-radius: var(--radius-md);
}

.m-topbar-icon:active {
  background: var(--bg-nav-hover);
}

.m-topbar-spacer {
  pointer-events: none;
}
</style>
