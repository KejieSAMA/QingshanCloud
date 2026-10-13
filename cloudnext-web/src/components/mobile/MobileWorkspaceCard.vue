<template>
  <div class="m-ws-card m-tap" @click="$emit('click')">
    <div class="m-ws-card-icon">
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
      </svg>
    </div>
    <div class="m-ws-card-body">
      <span class="m-ws-card-name">{{ item.name }}</span>
      <span class="m-ws-card-meta">{{ metaText }}</span>
    </div>
    <span v-if="roleLabel" class="m-ws-card-role">{{ roleLabel }}</span>
    <button
      class="m-ws-card-more m-tap"
      type="button"
      aria-label="更多操作"
      @click.stop="$emit('more')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <circle cx="12" cy="12" r="1" />
        <circle cx="19" cy="12" r="1" />
        <circle cx="5" cy="12" r="1" />
      </svg>
    </button>
    <svg class="m-ws-card-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <polyline points="9 18 15 12 9 6" />
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  item: { type: Object, required: true },
  roleLabel: { type: String, default: '' }
});

defineEmits(['click', 'more']);

const metaText = computed(() => {
  const parts = [];
  if (props.item.memberCount != null) {
    parts.push(`${props.item.memberCount} 人`);
  }
  if (props.item.creatorName) {
    parts.push(`创建人 ${props.item.creatorName}`);
  }
  if (props.item.updatedLabel) {
    parts.push(props.item.updatedLabel);
  }
  return parts.join(' · ');
});
</script>

<style scoped>
.m-ws-card {
  display: grid;
  grid-template-columns: 40px 1fr auto 32px auto;
  align-items: center;
  gap: var(--m-sp-3);
  min-height: 64px;
  padding: var(--m-sp-2) var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.m-ws-card:active {
  background: var(--bg-nav-hover);
}

.m-ws-card-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--m-rad-card);
  background: rgba(245, 101, 39, 0.12);
  color: #ea580c;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.m-ws-card-body {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.m-ws-card-name {
  font-size: var(--m-fs-body);
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-ws-card-meta {
  font-size: var(--m-fs-meta);
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-ws-card-role {
  height: 22px;
  padding: 0 10px;
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.m-ws-card-arrow {
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.m-ws-card-more {
  width: 32px;
  height: 32px;
  border-radius: 16px;
  background: transparent;
  color: var(--text-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.m-ws-card-more:active {
  background: var(--bg-nav-hover);
}
</style>
