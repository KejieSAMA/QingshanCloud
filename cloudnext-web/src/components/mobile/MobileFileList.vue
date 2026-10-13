<template>
  <div class="m-file-list">
    <div v-if="!items.length && !loading" class="m-empty">
      <p>{{ emptyText || '暂无内容' }}</p>
    </div>
    <MobileFileCard
      v-for="item in items"
      :key="item.id"
      :item="item"
      :selected="selectedIds.includes(item.id)"
      :selecting="selecting"
      @click="$emit('item-click', item)"
      @long-press="$emit('item-long-press', item)"
      @toggle="$emit('item-toggle', item)"
      @more="$emit('item-more', item)"
    />
    <div v-if="loading" class="m-loading">
      <span class="m-loading-dot"></span>
      <span>加载中</span>
    </div>
  </div>
</template>

<script setup>
import MobileFileCard from './MobileFileCard.vue';

defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  selecting: { type: Boolean, default: false },
  selectedIds: { type: Array, default: () => [] },
  emptyText: { type: String, default: '' }
});

defineEmits(['item-click', 'item-long-press', 'item-toggle', 'item-more']);
</script>

<style scoped>
.m-file-list {
  position: relative;
  background: #fff;
}

.m-empty {
  padding: 64px var(--m-sp-4);
  text-align: center;
  color: var(--text-tertiary);
  font-size: var(--m-fs-body);
}

.m-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--m-sp-2);
  padding: var(--m-sp-4) var(--m-sp-3);
  color: var(--text-tertiary);
  font-size: var(--m-fs-meta);
}

.m-loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--bg-primary);
  animation: m-loading-pulse 0.9s ease-in-out infinite;
}

@keyframes m-loading-pulse {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.1); }
}
</style>
