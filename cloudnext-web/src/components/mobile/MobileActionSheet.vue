<template>
  <Teleport to="body">
    <transition name="m-sheet-fade">
      <div v-if="open" class="m-sheet-mask" @click="close"></div>
    </transition>
    <div class="m-sheet" :class="{ 'is-open': open }" role="dialog" aria-modal="true">
      <div class="m-sheet-handle"></div>
      <div v-if="title" class="m-sheet-title">{{ title }}</div>
      <div class="m-sheet-actions">
        <button
          v-for="action in actions"
          :key="action.key"
          class="m-sheet-action m-tap"
          :class="{ 'is-danger': action.danger }"
          type="button"
          @click="onClick(action)"
        >
          <span>{{ action.label }}</span>
        </button>
      </div>
      <button class="m-sheet-cancel m-tap" type="button" @click="close">取消</button>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  actions: { type: Array, default: () => [] }
});

const emit = defineEmits(['update:open', 'select']);

const close = () => emit('update:open', false);
const onClick = (action) => {
  emit('select', action.key);
};
</script>

<style scoped>
.m-sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  z-index: 50;
}

.m-sheet-fade-enter-active,
.m-sheet-fade-leave-active {
  transition: opacity 0.18s ease;
}

.m-sheet-fade-enter-from,
.m-sheet-fade-leave-to {
  opacity: 0;
}

.m-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 51;
  background: #fff;
  border-radius: 16px 16px 0 0;
  box-shadow: var(--m-shadow-sheet);
  padding: var(--m-sp-2) var(--m-sp-3) calc(var(--m-sp-3) + var(--m-safe-bottom));
  transform: translateY(100%);
  transition: transform 0.22s ease;
  visibility: hidden;
}

.m-sheet.is-open {
  transform: translateY(0);
  visibility: visible;
}

.m-sheet-handle {
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: rgba(15, 23, 42, 0.16);
  margin: 0 auto var(--m-sp-2);
}

.m-sheet-title {
  text-align: center;
  font-size: var(--m-fs-meta);
  color: var(--text-secondary);
  padding-bottom: var(--m-sp-2);
}

.m-sheet-actions {
  display: grid;
  gap: 2px;
}

.m-sheet-action {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--m-tap-h);
  padding: 0 var(--m-sp-4);
  font-size: var(--m-fs-body);
  background: transparent;
  color: var(--text-primary);
  border-radius: var(--radius-md);
}

.m-sheet-action:active {
  background: var(--bg-nav-hover);
}

.m-sheet-action.is-danger {
  color: #b42318;
}

.m-sheet-action.is-danger:active {
  background: var(--bg-danger-soft);
}

.m-sheet-cancel {
  margin-top: var(--m-sp-3);
  width: 100%;
  height: var(--m-tap-h);
  background: var(--bg-panel-soft);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: var(--m-fs-body);
  font-weight: 500;
}

.m-sheet-cancel:active {
  background: var(--bg-nav-hover);
}
</style>
