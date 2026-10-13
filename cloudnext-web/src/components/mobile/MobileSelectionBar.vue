<template>
  <Teleport to="body">
    <transition name="m-bar-slide">
      <div v-if="visible" class="m-sel-bar" :style="barStyle">
        <div class="m-sel-bar-info">
          <button class="m-sel-bar-cancel m-tap" type="button" @click="$emit('cancel')">取消</button>
          <span>已选 {{ count }} 项</span>
        </div>
        <div class="m-sel-bar-actions">
          <button
            v-for="action in actions"
            :key="action.key"
            class="m-sel-bar-action m-tap"
            :class="{ 'is-danger': action.danger }"
            type="button"
            :disabled="action.disabled"
            @click="$emit('action', action.key)"
          >
            <span class="m-sel-bar-icon" v-html="action.icon"></span>
            <span class="m-sel-bar-label">{{ action.label }}</span>
          </button>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  count: { type: Number, default: 0 },
  actions: { type: Array, default: () => [] },
  bottomOffset: { type: Number, default: 0 }
});

defineEmits(['cancel', 'action']);

const barStyle = computed(() => ({
  bottom: `calc(${props.bottomOffset}px + var(--m-safe-bottom))`
}));
</script>

<style scoped>
.m-sel-bar {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 35;
  background: #fff;
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.1);
  padding: var(--m-sp-2) var(--m-sp-3) calc(var(--m-sp-2) + var(--m-safe-bottom));
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.m-sel-bar-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--m-sp-3);
  padding: var(--m-sp-1) var(--m-sp-1) var(--m-sp-2);
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
}

.m-sel-bar-cancel {
  background: transparent;
  color: var(--bg-primary);
  font-size: var(--m-fs-body);
  padding: 0;
}

.m-sel-bar-actions {
  display: grid;
  grid-auto-columns: 1fr;
  grid-auto-flow: column;
  gap: var(--m-sp-1);
}

.m-sel-bar-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  height: 52px;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--m-fs-meta);
  border-radius: var(--radius-md);
}

.m-sel-bar-action:active {
  background: var(--bg-nav-hover);
}

.m-sel-bar-action:disabled {
  opacity: 0.4;
}

.m-sel-bar-action.is-danger {
  color: #b42318;
}

.m-sel-bar-action.is-danger:active {
  background: var(--bg-danger-soft);
}

.m-sel-bar-icon {
  display: inline-flex;
  height: 22px;
}

.m-bar-slide-enter-active,
.m-bar-slide-leave-active {
  transition: transform 0.22s ease, opacity 0.22s ease;
}

.m-bar-slide-enter-from,
.m-bar-slide-leave-to {
  transform: translateY(100%);
  opacity: 0;
}
</style>
