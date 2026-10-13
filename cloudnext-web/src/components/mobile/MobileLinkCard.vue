<template>
  <div class="m-link-card">
    <div class="m-link-card-head">
      <span class="m-link-card-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
      </span>
      <span class="m-link-card-name">{{ link.fileName || '(已删除)' }}</span>
      <span class="m-link-card-status" :class="statusClass">{{ statusText }}</span>
    </div>
    <div class="m-link-card-meta">
      <span class="m-link-tag" :class="{ 'is-primary': link.permission === 'download' }">{{ permissionText }}</span>
      <span v-if="link.requirePassword" class="m-link-tag">密码保护</span>
      <span class="m-link-meta-text">下载 {{ usageText }}</span>
      <span class="m-link-meta-text">{{ expiresLabel }}</span>
    </div>
    <div class="m-link-card-actions">
      <button class="m-link-copy m-tap" type="button" :disabled="disabled" @click="onClickCopy">
        {{ copied ? '已复制' : '复制链接' }}
      </button>
      <button
        v-if="!link.revoked"
        class="m-link-more m-tap"
        type="button"
        aria-label="更多"
        :disabled="disabled"
        @click="$emit('more')"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="6" cy="12" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="18" cy="12" r="1.6" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue';

const props = defineProps({
  link: { type: Object, required: true },
  disabled: { type: Boolean, default: false }
});

const emit = defineEmits(['copy', 'more']);

const copied = ref(false);
let timer = null;

const statusText = computed(() => {
  if (props.link.revoked) return '已失效';
  if (props.link.isExpired) return '已过期';
  if (props.link.isExhausted) return '已用完';
  return '有效';
});

const statusClass = computed(() => {
  if (props.link.revoked) return 'is-danger';
  if (props.link.isExpired || props.link.isExhausted) return 'is-muted';
  return 'is-active';
});

const permissionText = computed(() => (props.link.permission === 'download' ? '可下载' : '仅预览'));

const usageText = computed(() => {
  const count = Number(props.link.downloadCount ?? 0);
  const limit = Number(props.link.downloadLimit ?? -1);
  if (!Number.isFinite(limit) || limit < 0) {
    return `${count} 次`;
  }
  return `${count}/${limit} 次`;
});

const expiresLabel = computed(() => props.link.expiresLabel || '永久有效');

const onClickCopy = () => {
  if (props.disabled) return;
  emit('copy');
  copied.value = true;
  if (timer) {
    clearTimeout(timer);
  }
  timer = setTimeout(() => {
    copied.value = false;
  }, 1800);
};

onBeforeUnmount(() => {
  if (timer) {
    clearTimeout(timer);
  }
});
</script>

<style scoped>
.m-link-card {
  padding: var(--m-sp-3);
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
  display: grid;
  gap: var(--m-sp-2);
}

.m-link-card-head {
  display: grid;
  grid-template-columns: 32px 1fr auto;
  align-items: center;
  gap: var(--m-sp-2);
}

.m-link-card-icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: rgba(34, 197, 94, 0.12);
  color: #16a34a;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.m-link-card-name {
  font-size: var(--m-fs-body);
  color: var(--text-primary);
  font-weight: 500;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.m-link-card-status {
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.m-link-card-status.is-active {
  background: rgba(31, 122, 58, 0.1);
  color: #1f7a3a;
}

.m-link-card-status.is-muted {
  background: rgba(148, 163, 184, 0.16);
  color: var(--text-tertiary);
}

.m-link-card-status.is-danger {
  background: rgba(220, 38, 38, 0.1);
  color: #b42318;
}

.m-link-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  font-size: var(--m-fs-meta);
  color: var(--text-tertiary);
  padding-left: calc(32px + var(--m-sp-2));
}

.m-link-tag {
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.m-link-tag.is-primary {
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
  border-color: rgba(47, 107, 255, 0.18);
}

.m-link-meta-text {
  display: inline-flex;
  align-items: center;
}

.m-link-card-actions {
  display: flex;
  align-items: center;
  gap: var(--m-sp-2);
  padding-left: calc(32px + var(--m-sp-2));
}

.m-link-copy {
  flex: 1;
  height: 36px;
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
  border-radius: var(--radius-md);
  font-size: var(--m-fs-meta);
  font-weight: 500;
}

.m-link-copy:active:not(:disabled) {
  background: rgba(47, 107, 255, 0.18);
}

.m-link-copy:disabled {
  opacity: 0.5;
}

.m-link-more {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: transparent;
  color: var(--text-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.m-link-more:active:not(:disabled) {
  background: rgba(15, 23, 42, 0.06);
}
</style>
