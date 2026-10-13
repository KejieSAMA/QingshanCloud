<template>
  <div
    class="m-file-card m-tap"
    :class="{ 'is-selected': selected }"
    @click="onClick"
    @touchstart.passive="onTouchStart"
    @touchmove.passive="onTouchMove"
    @touchend.passive="onTouchEnd"
    @touchcancel="onTouchCancel"
    @contextmenu.prevent
  >
    <span class="m-file-card-icon">
      <FileTypeIcon
        :kind="item.itemType === 'folder' ? 'folder' : (item.fileType || '')"
        :from-name="item.name"
        :content-type="item.contentType"
        :size="40"
      />
    </span>
    <div class="m-file-card-body">
      <span class="m-file-card-name">{{ item.name }}</span>
      <span class="m-file-card-meta">{{ metaText }}</span>
    </div>
    <button
      v-if="selecting"
      class="m-file-card-radio m-tap"
      :class="{ 'is-on': selected }"
      type="button"
      :aria-label="selected ? '取消选择' : '选择'"
      @click.stop="$emit('toggle')"
    >
      <svg v-if="selected" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    </button>
    <button
      v-else
      class="m-file-card-action m-tap"
      type="button"
      aria-label="更多操作"
      @click.stop="$emit('more')"
    >
      <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <circle cx="6" cy="12" r="1.6" />
        <circle cx="12" cy="12" r="1.6" />
        <circle cx="18" cy="12" r="1.6" />
      </svg>
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount } from 'vue';
import FileTypeIcon from '../drive/FileTypeIcon.vue';

const props = defineProps({
  item: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  selecting: { type: Boolean, default: false }
});

const emit = defineEmits(['click', 'long-press', 'more', 'toggle']);

const LONG_PRESS_MS = 480;
const MOVE_TOLERANCE = 10;

let pressTimer = null;
let startX = 0;
let startY = 0;
let longPressed = false;

const clearTimer = () => {
  if (pressTimer) {
    clearTimeout(pressTimer);
    pressTimer = null;
  }
};

const onTouchStart = (event) => {
  if (!event.touches?.length) {
    return;
  }
  startX = event.touches[0].clientX;
  startY = event.touches[0].clientY;
  longPressed = false;
  clearTimer();
  pressTimer = window.setTimeout(() => {
    longPressed = true;
    pressTimer = null;
    emit('long-press');
  }, LONG_PRESS_MS);
};

const onTouchMove = (event) => {
  if (!pressTimer || !event.touches?.length) {
    return;
  }
  const dx = Math.abs(event.touches[0].clientX - startX);
  const dy = Math.abs(event.touches[0].clientY - startY);
  if (dx > MOVE_TOLERANCE || dy > MOVE_TOLERANCE) {
    clearTimer();
  }
};

const onTouchEnd = () => {
  clearTimer();
};

const onTouchCancel = () => {
  clearTimer();
};

const onClick = (event) => {
  if (longPressed) {
    longPressed = false;
    event?.preventDefault?.();
    return;
  }
  emit('click');
};

onBeforeUnmount(() => {
  clearTimer();
});

const metaText = computed(() => {
  const parts = [];
  if (props.item.itemType === 'folder') {
    if (props.item.childCount != null) {
      parts.push(`${props.item.childCount} 项`);
    }
  } else if (props.item.sizeLabel) {
    parts.push(props.item.sizeLabel);
  }
  if (props.item.modifiedLabel) {
    parts.push(props.item.modifiedLabel);
  } else if (props.item.deletedLabel) {
    parts.push(props.item.deletedLabel);
  }
  return parts.join(' · ');
});
</script>

<style scoped>
.m-file-card {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  align-items: center;
  gap: var(--m-sp-3);
  min-height: 60px;
  padding: var(--m-sp-2) var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
}

.m-file-card:active {
  background: var(--bg-nav-hover);
}

.m-file-card.is-selected {
  background: var(--bg-primary-soft);
}

.m-file-card-icon {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.m-file-card-body {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.m-file-card-name {
  font-size: var(--m-fs-body);
  color: var(--text-primary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-file-card-meta {
  font-size: var(--m-fs-meta);
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-file-card-action,
.m-file-card-radio {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: transparent;
  color: var(--text-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.m-file-card-action:active {
  background: rgba(15, 23, 42, 0.06);
}

.m-file-card-radio {
  border: 1.5px solid var(--border-strong);
  color: #fff;
}

.m-file-card-radio.is-on {
  background: var(--bg-primary);
  border-color: var(--bg-primary);
}
</style>
