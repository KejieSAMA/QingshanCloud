<template>
  <Teleport to="body">
    <div class="file-preview-backdrop">
      <section ref="panel" class="file-preview-dialog" :class="'is-' + state.kind" role="dialog" aria-modal="true" aria-label="文件预览" @keydown="keys">
        <header class="file-preview-header">
          <h2 :title="state.descriptor?.fileName || target.item?.name">{{ state.descriptor?.fileName || target.item?.name || '文件预览' }}<span v-if="target.version"> · v{{ target.version.versionNumber }}</span></h2>
          <div class="file-preview-actions">
            <button v-if="state.descriptor?.allowDownload && systemOpen" type="button" :disabled="actionBusy" @click="perform(systemOpen)">系统打开</button>
            <button v-if="state.descriptor?.allowDownload && download" type="button" :disabled="actionBusy" @click="perform(download)">下载原件</button>
            <button ref="closeButton" class="file-preview-close" type="button" aria-label="关闭文件预览" @click="$emit('close')">×</button>
          </div>
        </header>
        <div class="file-preview-stage">
          <template v-if="state.phase === 'ready'">
            <PdfPreview v-if="state.kind === 'pdf'" :ticket="state.ticket" :source-identity="state.descriptor.sourceIdentity" />
            <PanZoomSurface v-else-if="state.kind === 'image'" :width="imageSize.width" :height="imageSize.height" :reset-key="state.url" image label="图片拖动预览">
              <template #default="metrics"><div class="file-view-image-holder" :style="{ width: metrics.width + 'px', height: metrics.height + 'px' }"><img :src="state.url" :alt="state.descriptor.fileName" draggable="false" :style="imageStyle(metrics)" @load="imageLoad" @error="imageError = '图片加载失败，请重新打开预览。'" /></div></template>
            </PanZoomSurface>
            <pre v-else-if="state.kind === 'text'" class="file-preview-text">{{ state.text }}</pre>
          </template>
          <div v-else class="file-view-status" :role="state.error ? 'alert' : 'status'" aria-live="polite">
            <span>{{ statusText }}</span><button v-if="state.retryable" type="button" @click="controller.retry()">重试</button>
          </div>
          <div v-if="imageError || actionError" class="file-view-notice" role="alert">{{ imageError || actionError }}</div>
        </div>
      </section>
    </div>
  </Teleport>
</template>
<script setup>
import { ref, shallowRef, computed, watch, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { createPreviewSession } from './previewSession.js';
import { describePreview, inspectPreview, previewBlob } from './previewApi.js';
import { sessionContextKey, CLOUDNEXT_SESSION_CHANGED_EVENT } from '../session.js';
import { embedState, setEmbeddedPreviewOpen, registerEmbeddedBack } from '../embedded/runtime.js';
import PanZoomSurface from './PanZoomSurface.vue';
import './file-preview.css';
const PdfPreview = defineAsyncComponent(() => import('./PdfPreview.vue'));
const props = defineProps({ target: { type: Object, required: true }, download: Function, systemOpen: Function });
const emit = defineEmits(['close']);
const state = shallowRef({ phase: 'loading' }), panel = ref(null), closeButton = ref(null), imageSize = ref({ width: 1, height: 1 });
const imageError = ref(''), actionError = ref(''), actionBusy = ref(false);
let savedFocus, savedOverflow, unregister, mountedOwner;
const controller = createPreviewSession({ describe: describePreview, inspect: inspectPreview, blob: previewBlob,
  identity: sessionContextKey, publish: value => { state.value = value; } });
const statusText = computed(() => state.value.error || ({ queued: '预览任务正在排队', converting: '正在生成预览' }[state.value.phase] || '正在加载预览…'));
function imageStyle(metrics) { return { width: Math.max(1, imageSize.value.width * metrics.scale) + 'px', height: Math.max(1, imageSize.value.height * metrics.scale) + 'px', transform: `translate(-50%, -50%) rotate(${metrics.rotation}deg)` }; }
async function imageLoad(e) {
  const image = e.target, url = state.value.url;
  let size = { width: image.naturalWidth, height: image.naturalHeight };
  if (typeof createImageBitmap === 'function') {
    try { const content = await fetch(url); const bitmap = await createImageBitmap(await content.blob(), { imageOrientation: 'from-image' });
      size = { width: bitmap.width, height: bitmap.height }; bitmap.close(); } catch { /* Older WebViews use intrinsic image dimensions. */ }
  }
  if (image.isConnected && state.value.url === url) imageSize.value = size;
}
async function perform(action) {
  if (actionBusy.value) return; actionBusy.value = true; actionError.value = ''; const owner = sessionContextKey(), target = props.target;
  try { await action(); } catch (e) { if (target === props.target && owner === sessionContextKey() && e?.name !== 'AbortError') actionError.value = e?.message || '文件处理失败，请重试。'; }
  finally { if (target === props.target && owner === sessionContextKey()) actionBusy.value = false; }
}
function keys(e) {
  if (e.key === 'Escape') { e.stopPropagation(); emit('close'); return; }
  if (e.key !== 'Tab') return;
  const list = [...panel.value.querySelectorAll('button:not(:disabled),input:not(:disabled),[tabindex="0"]')].filter(node => node.getClientRects().length);
  if (!list.length) return;
  if (e.shiftKey && document.activeElement === list[0]) { e.preventDefault(); list[list.length - 1].focus(); }
  else if (!e.shiftKey && document.activeElement === list[list.length - 1]) { e.preventDefault(); list[0].focus(); }
}
function visibility() { controller.setVisible(!document.hidden && embedState.visible !== false); }
function sessionChanged() { if(mountedOwner !== sessionContextKey()) { controller.close(); emit('close'); } }
watch(() => props.target, value => { imageError.value = ''; actionError.value = ''; actionBusy.value = false; imageSize.value = { width: 1, height: 1 }; void controller.open(value); }, { immediate: true });
watch(() => embedState.visible, visibility);
onMounted(async () => {
  savedFocus = document.activeElement; savedOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden';
  mountedOwner = sessionContextKey(); window.addEventListener(CLOUDNEXT_SESSION_CHANGED_EVENT, sessionChanged);
  setEmbeddedPreviewOpen(true); unregister = registerEmbeddedBack('file-preview', () => true, () => emit('close'));
  document.addEventListener('visibilitychange', visibility); visibility(); await nextTick(); closeButton.value?.focus();
});
onUnmounted(() => {
  controller.dispose(); unregister?.(); document.removeEventListener('visibilitychange', visibility); setEmbeddedPreviewOpen(false);
  window.removeEventListener(CLOUDNEXT_SESSION_CHANGED_EVENT, sessionChanged);
  document.body.style.overflow = savedOverflow || ''; if (mountedOwner === sessionContextKey() && savedFocus?.isConnected) savedFocus.focus({ preventScroll: true });
});
</script>
