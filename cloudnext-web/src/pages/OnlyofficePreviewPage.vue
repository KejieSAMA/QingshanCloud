<template>
  <div class="onlyoffice-page">
    <div v-if="isEditMode" class="onlyoffice-toolbar">
      <div class="onlyoffice-status" :class="`is-${saveStatus.state}`">
        <span class="status-dot"></span>
        <span class="status-text">{{ saveStatusText }}</span>
        <span v-if="saveStatus.message" class="status-message">{{ saveStatus.message }}</span>
      </div>
      <div class="onlyoffice-actions">
        <button class="toolbar-button" type="button" :disabled="!canForceSave" @click="handleForceSave">
          保存到云盘
        </button>
      </div>
    </div>

    <div v-if="serviceUnhealthy" class="onlyoffice-health-banner">
      <span class="health-dot"></span>
      <span class="health-text">OnlyOffice 服务异常</span>
      <span v-if="health.lastError" class="health-detail">{{ health.lastError }}</span>
      <span class="health-hint">编辑保存可能受影响；服务恢复后将自动消失。</span>
    </div>

    <div v-if="showNotice" class="onlyoffice-notice" :class="{ 'is-error': noticeError }">
      {{ notice }}
    </div>

    <div class="onlyoffice-workspace">
      <div id="cloudnext-onlyoffice-editor" ref="editorRef" class="onlyoffice-editor"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { forceSaveDriveItem, getOnlyofficeHealth, getOnlyofficeSaveStatus } from '../api/cloudnext';

const SAVE_STATUS_POLL_MS = 2500;
const HEALTH_POLL_MS = 30_000;

const route = useRoute();
const isEditMode = computed(() => route.name === 'edit-office' || route.path.includes('/edit/'));
const ticket = computed(() => (typeof route.query.ticket === 'string' ? route.query.ticket.trim() : ''));

const editorRef = ref(null);
const editorInstance = ref(null);
const notice = ref('');
const noticeError = ref(false);
const documentState = ref('clean');
const saveStatus = reactive({
  state: 'idle',
  message: '',
  pending: false,
  saved: false,
  failed: false
});

let saveStatusTimer = null;
let healthTimer = null;
let removeKeyboardHandler = null;
let removeBeforeUnloadHandler = null;
let removeResizeHandler = null;

const health = reactive({
  healthy: true,
  configured: true,
  lastProbeAt: null,
  lastError: ''
});
const serviceUnhealthy = computed(() => health.configured && !health.healthy);

async function refreshHealth() {
  try {
    const data = await getOnlyofficeHealth();
    if (data) {
      health.healthy = Boolean(data.healthy);
      health.configured = data.configured !== false;
      health.lastProbeAt = data.lastProbeAt || null;
      health.lastError = data.lastError || '';
    }
  } catch (e) {
    /* 探测请求自身失败不阻断业务 */
  }
}

function startPollingHealth() {
  stopPollingHealth();
  if (!isEditMode.value) {
    return;
  }
  void refreshHealth();
  healthTimer = window.setInterval(() => {
    void refreshHealth();
  }, HEALTH_POLL_MS);
}

function stopPollingHealth() {
  if (healthTimer) {
    window.clearInterval(healthTimer);
    healthTimer = null;
  }
}

const showNotice = computed(() => Boolean(notice.value));
const canForceSave = computed(() => isEditMode.value && Boolean(ticket.value) && !saveStatus.pending);
const showUnsavedHint = computed(() => documentState.value === 'dirty' || saveStatus.pending);
const saveStatusText = computed(() => {
  if (saveStatus.pending) {
    return '保存中';
  }
  if (saveStatus.saved) {
    return '已保存';
  }
  if (saveStatus.failed) {
    return '保存失败';
  }
  if (documentState.value === 'dirty') {
    return '有未保存修改';
  }
  return isEditMode.value ? '编辑就绪' : '预览就绪';
});

function setTitle(title) {
  document.title = title || (isEditMode.value ? 'OnlyOffice Edit' : 'OnlyOffice Preview');
}

function parseJsonSafely(text) {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return null;
  }
}

function parseResponseMessage(payload, fallback) {
  if (payload && typeof payload.message === 'string' && payload.message.trim()) {
    return payload.message;
  }
  return fallback;
}

async function nudgeEditorViewport() {
  await nextTick();
  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'));
  });
}

async function fetchOnlyofficeConfig(nextTicket) {
  const path = isEditMode.value
    ? `/api/onlyoffice/edit-config?editTicket=${encodeURIComponent(nextTicket)}`
    : `/api/onlyoffice/view-config?previewTicket=${encodeURIComponent(nextTicket)}`;
  const response = await fetch(path, { credentials: 'include' });
  const text = await response.text();
  const payload = parseJsonSafely(text);
  if (!response.ok) {
    const action = isEditMode.value ? 'edit' : 'preview';
    throw new Error(parseResponseMessage(payload, `Failed to load ${action} config: ${response.status}`));
  }
  return payload;
}

function loadOnlyofficeScript(src) {
  return new Promise((resolve, reject) => {
    if (window.DocsAPI && window.DocsAPI.DocEditor) {
      resolve();
      return;
    }
    const existing = document.querySelector(`script[data-onlyoffice-api="${src}"]`);
    if (existing) {
      existing.addEventListener('load', () => resolve(), { once: true });
      existing.addEventListener('error', () => reject(new Error('Failed to load the OnlyOffice script')), { once: true });
      return;
    }
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.dataset.onlyofficeApi = src;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Failed to load the OnlyOffice script'));
    document.head.appendChild(script);
  });
}

function destroyEditor() {
  if (editorInstance.value && typeof editorInstance.value.destroyEditor === 'function') {
    editorInstance.value.destroyEditor();
  }
  editorInstance.value = null;
  if (editorRef.value) {
    editorRef.value.innerHTML = '';
  }
}

function resetSaveStatus(state = 'idle', message = '') {
  saveStatus.state = state;
  saveStatus.message = message;
  saveStatus.pending = state === 'pending';
  saveStatus.saved = state === 'saved';
  saveStatus.failed = state === 'failed';
}

function applySaveStatus(payload) {
  if (!payload) {
    return;
  }
  resetSaveStatus(payload.state || 'idle', payload.message || '');
}

async function refreshSaveStatus() {
  if (!isEditMode.value || !ticket.value) {
    return null;
  }
  const payload = await getOnlyofficeSaveStatus(ticket.value);
  applySaveStatus(payload);
  return payload;
}

function stopPollingSaveStatus() {
  if (saveStatusTimer) {
    window.clearInterval(saveStatusTimer);
    saveStatusTimer = null;
  }
}

function startPollingSaveStatus() {
  stopPollingSaveStatus();
  if (!isEditMode.value || !ticket.value) {
    return;
  }
  saveStatusTimer = window.setInterval(() => {
    void refreshSaveStatus().catch(() => {});
  }, SAVE_STATUS_POLL_MS);
}

async function handleForceSave() {
  if (!isEditMode.value || !ticket.value) {
    return;
  }
  resetSaveStatus('pending', '正在触发保存');
  try {
    const resp = await forceSaveDriveItem(ticket.value);
    if (resp && resp.accepted === false) {
      resetSaveStatus('failed', resp.message || '保存失败');
      return;
    }
    if (resp && resp.state) {
      resetSaveStatus(resp.state, resp.message || '');
    }
    startPollingSaveStatus();
    void refreshSaveStatus().catch(() => {});
  } catch (error) {
    resetSaveStatus('failed', error?.message || '保存失败');
  }
}

function bindKeyboardSave() {
  const handler = (event) => {
    if (!isEditMode.value) {
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault();
      void handleForceSave();
    }
  };
  window.addEventListener('keydown', handler);
  return () => window.removeEventListener('keydown', handler);
}

function bindBeforeUnload() {
  const handler = (event) => {
    if (!showUnsavedHint.value) {
      return;
    }
    event.preventDefault();
    event.returnValue = '';
  };
  window.addEventListener('beforeunload', handler);
  return () => window.removeEventListener('beforeunload', handler);
}

function attachEditorEvents(config) {
  if (!config.editorConfig) {
    config.editorConfig = {};
  }
  if (!config.editorConfig.events) {
    config.editorConfig.events = {};
  }
  config.editorConfig.events.onDocumentStateChange = (event) => {
    const dirty = Boolean(event?.data);
    documentState.value = dirty ? 'dirty' : 'clean';
    if (dirty) {
      saveStatus.failed = false;
      saveStatus.message = '';
      if (saveStatus.state === 'idle' || saveStatus.state === 'saved') {
        saveStatus.state = 'dirty';
      }
    }
  };
  config.editorConfig.events.onAppReady = () => {
    documentState.value = 'clean';
  };
}

async function loadPreview() {
  destroyEditor();
  documentState.value = 'clean';
  resetSaveStatus('idle', '');
  notice.value = isEditMode.value ? '正在加载编辑器...' : '正在加载文档...';
  noticeError.value = false;
  setTitle(isEditMode.value ? 'OnlyOffice Edit' : 'OnlyOffice Preview');
  await nudgeEditorViewport();

  const nextTicket = ticket.value;
  if (!nextTicket) {
    notice.value = isEditMode.value ? '缺少编辑票据。' : '缺少预览票据。';
    noticeError.value = true;
    await nudgeEditorViewport();
    return;
  }

  try {
    const viewConfig = await fetchOnlyofficeConfig(nextTicket);
    if (!viewConfig || !viewConfig.docserviceApi || !viewConfig.docEditorConfig) {
      throw new Error(isEditMode.value ? 'OnlyOffice 编辑配置不完整' : 'OnlyOffice 预览配置不完整');
    }
    setTitle(viewConfig.fileName || (isEditMode.value ? 'OnlyOffice Edit' : 'OnlyOffice Preview'));
    await loadOnlyofficeScript(viewConfig.docserviceApi);
    if (!(window.DocsAPI && window.DocsAPI.DocEditor)) {
      throw new Error('Failed to initialize the OnlyOffice editor');
    }
    notice.value = '';
    noticeError.value = false;
    if (isEditMode.value) {
      attachEditorEvents(viewConfig.docEditorConfig);
    }
    await nudgeEditorViewport();
    editorInstance.value = new window.DocsAPI.DocEditor('cloudnext-onlyoffice-editor', viewConfig.docEditorConfig);
    if (isEditMode.value) {
      await refreshSaveStatus().catch(() => {});
      startPollingSaveStatus();
    }
    await nudgeEditorViewport();
  } catch (error) {
    notice.value = error?.message || (isEditMode.value ? 'Failed to load the document editor' : 'Failed to load the document preview');
    noticeError.value = true;
    await nudgeEditorViewport();
  }
}

watch(showNotice, () => {
  void nudgeEditorViewport();
});

watch(
  () => route.query.ticket,
  () => {
    void loadPreview();
  },
  { immediate: true }
);

onMounted(() => {
  void nudgeEditorViewport();
  const resizeHandler = () => {
    void nudgeEditorViewport();
  };
  window.addEventListener('resize', resizeHandler);
  removeResizeHandler = () => window.removeEventListener('resize', resizeHandler);
  removeKeyboardHandler = bindKeyboardSave();
  removeBeforeUnloadHandler = bindBeforeUnload();
  startPollingHealth();
});

onBeforeUnmount(() => {
  if (removeResizeHandler) {
    removeResizeHandler();
  }
  stopPollingSaveStatus();
  stopPollingHealth();
  destroyEditor();
  if (removeKeyboardHandler) {
    removeKeyboardHandler();
  }
  if (removeBeforeUnloadHandler) {
    removeBeforeUnloadHandler();
  }
});
</script>

<style scoped>
.onlyoffice-page {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #f5f7fa;
}

.onlyoffice-toolbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e6eb;
  background: #ffffff;
  box-sizing: border-box;
  flex-wrap: nowrap;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
}

.onlyoffice-status {
  display: flex;
  flex: 1 1 0;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #1f2329;
  font-size: 13px;
  overflow: hidden;
  white-space: nowrap;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #86909c;
  flex: 0 0 auto;
}

.onlyoffice-status.is-pending .status-dot {
  background: #f59e0b;
}

.onlyoffice-status.is-saved .status-dot {
  background: #2ba471;
}

.onlyoffice-status.is-failed .status-dot {
  background: #e5484d;
}

.onlyoffice-status.is-dirty .status-dot {
  background: #0057ff;
}

.status-text {
  font-weight: 600;
  flex: 0 0 auto;
  white-space: nowrap;
}

.status-message {
  flex: 1 1 auto;
  min-width: 0;
  color: #5f6b7a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.onlyoffice-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  flex-shrink: 0;
  white-space: nowrap;
  margin-left: auto;
}

.toolbar-button {
  min-width: 100px;
  height: 32px;
  padding: 0 14px;
  border: 1px solid #0057ff;
  border-radius: 6px;
  background: #0057ff;
  color: #ffffff;
  cursor: pointer;
}

.toolbar-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.onlyoffice-notice {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 44px;
  padding: 0 16px;
  color: #1f2329;
  background: #ffffff;
  border-bottom: 1px solid #e5e6eb;
  box-sizing: border-box;
}

.onlyoffice-notice.is-error {
  color: #c0392b;
}

.onlyoffice-health-banner {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 6px 16px;
  background: #fef2f2;
  color: #b42318;
  border-bottom: 1px solid rgba(180, 35, 24, 0.18);
  font-size: 13px;
}

.onlyoffice-health-banner .health-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dc2626;
  flex: 0 0 auto;
  animation: onlyoffice-health-pulse 1.4s ease-in-out infinite;
}

.onlyoffice-health-banner .health-text {
  font-weight: 600;
}

.onlyoffice-health-banner .health-detail {
  color: #7a1f15;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}

.onlyoffice-health-banner .health-hint {
  margin-left: auto;
  color: #7a1f15;
  font-size: 12px;
}

@keyframes onlyoffice-health-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.15); }
}

.onlyoffice-workspace {
  position: relative;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

.onlyoffice-editor {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  height: 100%;
  background: #ffffff;
}
</style>
