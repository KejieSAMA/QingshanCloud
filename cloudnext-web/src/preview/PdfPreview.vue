<template>
  <PanZoomSurface :width="pageSize.width" :height="pageSize.height" :reset-key="pageNumberValue" label="PDF 拖动预览">
    <template #leading>
      <button type="button" aria-label="上一页" :disabled="pageNumberValue <= 1 || busy" @click="pageNumberValue--">‹</button>
      <label v-if="total" class="file-view-page-number"><input :value="pageNumberValue" inputmode="numeric" aria-label="页码" :disabled="busy" @change="changePage" @keydown.enter.prevent="changePage" /> / {{ total }}</label>
      <span v-else>正在读取 PDF</span>
      <button type="button" aria-label="下一页" :disabled="!total || pageNumberValue >= total || busy" @click="pageNumberValue++">›</button>
    </template>
    <template #default="metrics"><PdfCanvas v-if="page" :page="page" :scale="metrics.scale" :angle="metrics.rotation" @busy="busy = $event" @error="error = $event" /></template>
  </PanZoomSurface>
  <div v-if="error" class="file-view-notice" role="alert"><span>{{ error }}</span><button v-if="retryable" type="button" @click="load">重试</button></div>
  <div v-else-if="busy" class="file-view-loading" role="status">正在加载预览…</div>
</template>
<script setup>
import './pdfRuntimePolyfills';
import workerUrl from './pdfWorker.ts?worker&url';
import { ref, shallowRef, watch, onUnmounted } from 'vue';
import { pageNumber } from './viewerGeometry.js';
import { previewHeaders } from './previewApi.js';
import PanZoomSurface from './PanZoomSurface.vue';
import PdfCanvas from './PdfCanvas.vue';
const props = defineProps({ ticket: { type: String, required: true }, sourceIdentity: String });
const documentProxy = shallowRef(null), page = shallowRef(null), pageSize = ref({ width: 1, height: 1 });
const pageNumberValue = ref(1), total = ref(0), busy = ref(true), error = ref(''), retryable = ref(true);
let loadingTask, generation = 0, pageGeneration = 0;
async function load() {
  const n = ++generation, previous = pageNumberValue.value; pageGeneration++;
  const old = loadingTask; loadingTask = null; void old?.destroy();
  documentProxy.value = null; page.value = null; busy.value = true; error.value = ''; retryable.value = true;
  try {
    const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs'); if (n !== generation) return;
    pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;
    const base = new URL('/vendor/pdfjs/5.4.296/', window.location.origin).href;
    const task = pdfjs.getDocument({ url: new URL('/api/previews/content', window.location.origin).href,
      httpHeaders: previewHeaders(props.ticket), rangeChunkSize: 256 * 1024, disableAutoFetch: true, disableStream: true,
      cMapUrl: base + 'cmaps/', cMapPacked: true, wasmUrl: base + 'wasm/', isEvalSupported: false,
      enableXfa: false, });
    loadingTask = task;
    task.onPassword = () => { if (n === generation) { error.value = '文件已加密，无法预览。'; retryable.value = false; busy.value = false; } void task.destroy(); };
    const document = await task.promise;
    if (n !== generation) { void document.destroy(); return; }
    total.value = document.numPages; pageNumberValue.value = Math.min(Math.max(1, previous), document.numPages); documentProxy.value = document;
    await readPage();
  } catch (e) {
    if (n !== generation || error.value) return;
    fail(e);
  }
}
async function readPage() {
  if (!documentProxy.value) return;
  const n = ++pageGeneration, doc = documentProxy.value; busy.value = true; error.value = ''; page.value = null;
  try {
    const next = await doc.getPage(pageNumberValue.value); if (n !== pageGeneration || doc !== documentProxy.value) return;
    const size = next.getViewport({ scale: 1 }); pageSize.value = { width: size.width, height: size.height }; page.value = next;
  } catch (e) { if (n === pageGeneration) fail(e); }
}
function fail(e) {
  busy.value=false;
  const messages={401:'预览已失效，请重新打开文件。',403:'无权预览该文件。',404:'文件不存在或已被删除。',409:'文件已更新，请重新打开预览。'};
  error.value=messages[e?.status]||(e?.name==='InvalidPDFException'?'PDF 已损坏，无法预览。':'PDF 加载失败，请重试。');
  retryable.value=![401,403,404,409,410].includes(e?.status)&&!['InvalidPDFException','PasswordException'].includes(e?.name);
}
function changePage(e) { const value = pageNumber(e.target.value, total.value); if (value !== null) pageNumberValue.value = value; e.target.value = String(pageNumberValue.value); }
watch(() => props.ticket, load, { immediate: true });
watch(() => props.sourceIdentity, () => { pageNumberValue.value = 1; });
watch(pageNumberValue, readPage);
onUnmounted(() => { generation++; pageGeneration++; void loadingTask?.destroy(); loadingTask = null; });
</script>
