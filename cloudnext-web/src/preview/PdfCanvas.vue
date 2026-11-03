<template><canvas ref="canvas" class="file-view-canvas" :aria-label="'PDF 第 ' + page.pageNumber + ' 页'" :style="{ width: display.width + 'px', height: display.height + 'px' }" /></template>
<script setup>
import { computed, ref, watch, onUnmounted } from 'vue';
const props = defineProps({ page: Object, scale: Number, angle: Number });
const emit = defineEmits(['busy', 'error']), canvas = ref(null);
const display = computed(() => props.page.getViewport({ scale: props.scale, rotation: ((props.page.rotate || 0) + props.angle) % 360 }));
let task, generation = 0;
watch([canvas, () => props.page, () => props.scale, () => props.angle], async () => {
  const n = ++generation, old = task; task = null;
  if (old) { old.cancel(); await old.promise.catch(() => {}); }
  if (!canvas.value || n !== generation) return;
  emit('busy', true); emit('error', '');
  try {
    const pixels = Math.max(1, display.value.width * display.value.height);
    const ratio = Math.min(2, window.devicePixelRatio || 1, Math.sqrt(16000000 / pixels));
    const view = props.page.getViewport({ scale: props.scale * ratio, rotation: ((props.page.rotate || 0) + props.angle) % 360 });
    const element = canvas.value; element.width = Math.max(1, Math.ceil(view.width)); element.height = Math.max(1, Math.ceil(view.height));
    const render = props.page.render({ canvas: element, canvasContext: element.getContext('2d'), viewport: view }); task = render;
    await render.promise;
    if (n === generation) emit('busy', false);
  } catch (e) { if (n === generation && e?.name !== 'RenderingCancelledException') { emit('busy', false); emit('error', 'PDF 页面渲染失败，请重试。'); } }
}, { flush: 'post' });
onUnmounted(() => { generation++; task?.cancel(); });
</script>
