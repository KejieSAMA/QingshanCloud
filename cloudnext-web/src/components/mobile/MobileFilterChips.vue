<template>
  <div class="m-chips">
    <div class="m-chip-filters" role="tablist" aria-label="文件筛选">
    <button
      v-for="chip in chips"
      :key="chip.key"
      type="button"
      class="m-chip m-tap"
      :class="{ 'is-active': modelValue === chip.key }"
      role="tab"
      :aria-selected="modelValue === chip.key ? 'true' : 'false'"
      @click="$emit('update:modelValue', chip.key)"
    >
      {{ chip.label }}
    </button>
    </div>
    <div v-if="sortBy" ref="sortRoot" class="m-chip-sort" @keydown.esc.stop="closeSort(true)"
      @focusout="onSortFocusOut">
      <button ref="sortTrigger" class="m-chip m-sort-trigger" type="button"
        :class="{ 'is-active': sortOpen }" :aria-expanded="sortOpen"
        aria-label="选择文件排序方式" @click="sortOpen = !sortOpen">
        <span>{{ effectiveSortLabels[sortBy] || '排序' }} {{ sortOrder === 'asc' ? '↑' : '↓' }}</span>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
          <path :d="sortOpen ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'" />
        </svg>
      </button>
      <div v-if="sortOpen" class="m-sort-menu" role="group" aria-label="文件排序">
        <button v-for="(label, key) in effectiveSortLabels" :key="key" type="button"
          class="m-sort-option" :class="{ 'is-selected': sortBy === key }"
          :aria-pressed="sortBy === key" @click="selectSort(key, sortOrder)">
          <span>{{ label }}</span><span v-if="sortBy === key" aria-hidden="true">✓</span>
        </button>
        <div class="m-sort-direction" role="group" aria-label="排序方向">
          <button type="button" :class="{ 'is-selected': sortOrder === 'asc' }"
            :aria-pressed="sortOrder === 'asc'" @click="selectSort(sortBy, 'asc')">升序 ↑</button>
          <button type="button" :class="{ 'is-selected': sortOrder === 'desc' }"
            :aria-pressed="sortOrder === 'desc'" @click="selectSort(sortBy, 'desc')">降序 ↓</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
const props = defineProps({
  chips: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' },
  sortBy: { type: String, default: '' },
  sortOrder: { type: String, default: 'desc' },
  sortOptions: { type: Object, default: () => ({}) }
});
const emit = defineEmits(['update:modelValue', 'update:sortBy', 'update:sortOrder', 'sort-change']);
const sortLabels = { name: '名称', updatedAt: '修改时间', createdAt: '创建时间', sizeBytes: '大小' };
const effectiveSortLabels = computed(() => Object.keys(props.sortOptions || {}).length ? props.sortOptions : sortLabels);
const sortOpen = ref(false);
const sortRoot = ref(null);
const sortTrigger = ref(null);
const closeSort = (restoreFocus = false) => {
  sortOpen.value = false;
  if (restoreFocus) sortTrigger.value?.focus();
};
const selectSort = (key, order) => {
  emit('update:sortBy', key);
  emit('update:sortOrder', order);
  emit('sort-change', { sortBy: key, sortOrder: order });
  closeSort(true);
};
const onOutsidePointer = (event) => {
  if (!sortRoot.value?.contains(event.target)) closeSort();
};
const onSortFocusOut = (event) => {
  if (!sortRoot.value?.contains(event.relatedTarget)) closeSort();
};
onMounted(() => document.addEventListener('pointerdown', onOutsidePointer));
onUnmounted(() => document.removeEventListener('pointerdown', onOutsidePointer));
</script>

<style scoped>
.m-chips {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  padding: var(--m-sp-2) var(--m-sp-3);
  flex-wrap: wrap;
  overflow: visible;
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  scrollbar-width: none;
}

.m-chip-filters {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  flex-shrink: 0;
}

.m-chip-sort {
  position: relative;
  margin-left: auto;
  flex-shrink: 0;
}

.m-sort-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.m-chip-sort button:focus-visible {
  outline: 2px solid var(--bg-primary);
  outline-offset: 1px;
}

.m-sort-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 60;
  width: 168px;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.m-sort-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 38px;
  padding: 0 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: left;
}

.m-sort-direction {
  display: flex;
  gap: 4px;
  margin-top: 5px;
  padding-top: 6px;
  border-top: 1px solid var(--border-color);
}

.m-sort-direction button {
  flex: 1;
  min-height: 34px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12px;
}

.m-sort-menu .is-selected,
.m-sort-menu button:hover {
  color: var(--bg-primary);
  background: var(--bg-primary-soft);
}

.m-chips::-webkit-scrollbar {
  display: none;
}

.m-chip {
  height: 32px;
  min-height: 32px;
  padding: 0 14px;
  font-size: var(--m-fs-meta);
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
  border-radius: var(--m-rad-chip);
  white-space: nowrap;
  flex-shrink: 0;
  border: 1px solid transparent;
}

.m-chip.is-active {
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
  border-color: rgba(47, 107, 255, 0.2);
  font-weight: 500;
}

.m-chip:active {
  transform: scale(0.96);
}
</style>
