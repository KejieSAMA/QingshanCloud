<template>
  <div class="file-view-surface" :class="{ 'is-image': image }">
    <div v-if="$slots.leading" class="file-view-pages"><slot name="leading" /></div>
    <div ref="viewport" class="file-view-viewport" :class="{ 'is-dragging': dragging }" tabindex="0" :aria-label="label"
      @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerEnd" @pointercancel="pointerEnd"
      @wheel="wheel" @dblclick.prevent="setZoom(zoom === 1 ? 2 : 1)">
      <div class="file-view-spacer" :style="{ width: bounds.width + 'px', height: bounds.height + 'px' }">
        <slot :scale="scale" :rotation="angle" :width="bounds.width" :height="bounds.height" />
      </div>
    </div>
    <div class="file-view-controls" role="toolbar" aria-label="预览工具">
      <button type="button" aria-label="缩小" :disabled="zoom <= .5" @click="setZoom(zoom - .25)">−</button>
      <output>{{ Math.round(scale * 100) }}%</output>
      <button type="button" aria-label="放大" :disabled="zoom >= 4" @click="setZoom(zoom + .25)">＋</button>
      <button type="button" @click="reset">适应页面</button>
      <button type="button" aria-label="向左旋转" @click="turn(-90)">↶</button>
      <button type="button" aria-label="向右旋转" @click="turn(90)">↷</button>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { rotation, rotatedSize, clampZoom, fitScale, zoomAnchor } from './viewerGeometry.js';
const props = defineProps({ width: Number, height: Number, resetKey: [String, Number], label: String, image: Boolean });
const viewport = ref(null), size = ref({ width: 1, height: 1 }), zoom = ref(1), angle = ref(0), dragging = ref(false);
const fitted = computed(() => { const s = rotatedSize(props.width || 1, props.height || 1, angle.value); return fitScale(s.width, s.height, size.value.width, size.value.height); });
const scale = computed(() => fitted.value * zoom.value);
const bounds = computed(() => rotatedSize(Math.max(1, Math.round((props.width || 1) * scale.value)), Math.max(1, Math.round((props.height || 1) * scale.value)), angle.value));
let observer, pointers = new Map(), gesture = null;
function measure() { if (viewport.value) size.value = { width: viewport.value.clientWidth, height: viewport.value.clientHeight }; }
function center() { const v = viewport.value; if (v) { v.scrollLeft = Math.max(0, (v.scrollWidth - v.clientWidth) / 2); v.scrollTop = Math.max(0, (v.scrollHeight - v.clientHeight) / 2); } }
async function setZoom(value, anchor) {
  const v = viewport.value, before = v ? { x: v.scrollLeft, y: v.scrollTop, w: v.scrollWidth, h: v.scrollHeight } : null;
  const point = anchor || { x: (v?.clientWidth || 0) / 2, y: (v?.clientHeight || 0) / 2 };
  zoom.value = clampZoom(value); await nextTick();
  if (v && before) { v.scrollLeft = zoomAnchor(before.x, point.x, before.w, v.scrollWidth); v.scrollTop = zoomAnchor(before.y, point.y, before.h, v.scrollHeight); }
}
async function reset() { zoom.value = 1; pointers.clear(); gesture = null; dragging.value = false; await nextTick(); center(); }
async function turn(delta) { angle.value = rotation(angle.value + delta); await reset(); }
function point(e) { const rect = viewport.value.getBoundingClientRect(); return { x: e.clientX - rect.left, y: e.clientY - rect.top }; }
function pointerDown(e) {
  if (e.button !== 0) return;
  const v = viewport.value; pointers.set(e.pointerId, point(e)); v.setPointerCapture?.(e.pointerId);
  if (pointers.size === 1) gesture = { point: point(e), left: v.scrollLeft, top: v.scrollTop };
  else if (pointers.size === 2) { const [a, b] = [...pointers.values()]; gesture = { distance: Math.hypot(a.x - b.x, a.y - b.y), zoom: zoom.value }; }
  dragging.value = true;
}
function pointerMove(e) {
  if (!pointers.has(e.pointerId)) return;
  pointers.set(e.pointerId, point(e)); const v = viewport.value;
  if (pointers.size === 2 && gesture?.distance) {
    const [a, b] = [...pointers.values()]; void setZoom(gesture.zoom * Math.hypot(a.x - b.x, a.y - b.y) / Math.max(1, gesture.distance), { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });
  } else if (pointers.size === 1 && gesture?.point) { const p = point(e); v.scrollLeft = gesture.left + gesture.point.x - p.x; v.scrollTop = gesture.top + gesture.point.y - p.y; }
}
function pointerEnd(e) {
  pointers.delete(e.pointerId); if(viewport.value?.hasPointerCapture?.(e.pointerId))viewport.value.releasePointerCapture(e.pointerId); dragging.value = false;
  const remaining = [...pointers.values()][0]; gesture = remaining ? { point: remaining, left: viewport.value.scrollLeft, top: viewport.value.scrollTop } : null;
}
function wheel(e) { if (e.ctrlKey || e.metaKey) { e.preventDefault(); void setZoom(zoom.value + (e.deltaY < 0 ? .25 : -.25), point(e)); } }
watch(() => props.resetKey, () => { angle.value = 0; void reset(); });
watch([() => props.width, () => props.height, size], () => { if (zoom.value === 1) void nextTick(center); });
onMounted(() => { measure(); if (typeof ResizeObserver !== 'undefined') { observer = new ResizeObserver(measure); observer.observe(viewport.value); } window.addEventListener('resize', measure); });
onUnmounted(() => { observer?.disconnect(); window.removeEventListener('resize', measure); pointers.clear(); });
</script>
