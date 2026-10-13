<template>
  <div v-if="open" class="dialog-mask" @click.self="onClose">
    <div
      class="dialog-panel link-create-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="link-create-title"
    >
      <div class="dialog-head">
        <h2 id="link-create-title">创建外链</h2>
        <p>从个人文件中选择文件，并设置访问权限、密码、有效期与下载次数。</p>
      </div>

      <!-- 关联文件 -->
      <div class="link-create-field">
        <span class="link-create-label">关联文件</span>
        <div class="link-create-file">
          <div v-if="selectedItem" class="link-create-file-info">
            <strong>{{ selectedItem.name }}</strong>
            <span>{{ selectedItem.fileTypeLabel || selectedItem.fileType || '文件' }}</span>
          </div>
          <span v-else class="link-create-file-empty">尚未选择文件</span>
          <button
            class="secondary-button link-create-file-btn"
            type="button"
            :disabled="submitting"
            @click="openPicker"
          >
            {{ selectedItem ? '重新选择' : '选择文件' }}
          </button>
        </div>
      </div>

      <!-- 权限 -->
      <div class="link-create-field">
        <span class="link-create-label">权限</span>
        <div class="link-create-radios">
          <label class="link-create-radio">
            <input
              v-model="form.permission"
              type="radio"
              value="preview"
              :disabled="submitting"
            />
            <span>仅预览</span>
          </label>
          <label class="link-create-radio">
            <input
              v-model="form.permission"
              type="radio"
              value="download"
              :disabled="submitting"
            />
            <span>可预览可下载</span>
          </label>
        </div>
      </div>

      <!-- 访问密码 -->
      <div class="link-create-field">
        <span class="link-create-label">访问密码</span>
        <div class="link-create-radios">
          <label class="link-create-radio">
            <input
              v-model="form.requirePassword"
              type="radio"
              :value="false"
              :disabled="submitting"
            />
            <span>无密码</span>
          </label>
          <label class="link-create-radio">
            <input
              v-model="form.requirePassword"
              type="radio"
              :value="true"
              :disabled="submitting"
            />
            <span>需要密码</span>
          </label>
        </div>
        <input
          v-if="form.requirePassword"
          v-model.trim="form.password"
          type="text"
          class="link-create-input"
          placeholder="已设置"
          maxlength="64"
          :disabled="submitting"
        />
      </div>

      <!-- 有效期 -->
      <div class="link-create-field">
        <span class="link-create-label">有效期</span>
        <input
          v-model="form.expiresAt"
          type="date"
          class="link-create-input"
          :disabled="submitting"
        />
        <span class="link-create-hint">留空表示永久有效。</span>
      </div>

      <!-- 下载次数 -->
      <div class="link-create-field">
        <span class="link-create-label">下载次数</span>
        <input
          v-model.number="form.downloadLimit"
          type="number"
          min="1"
          class="link-create-input"
          placeholder="不限"
          :disabled="submitting"
        />
        <span class="link-create-hint">留空表示不限制下载次数。</span>
      </div>

      <div v-if="errorText" class="dialog-error">{{ errorText }}</div>

      <div class="dialog-actions">
        <button
          class="secondary-button"
          type="button"
          :disabled="submitting"
          @click="onClose"
        >
          取消
        </button>
        <button
          class="primary-button"
          type="button"
          :disabled="submitting || !canSubmit"
          @click="onConfirm"
        >
          {{ submitting ? '创建中…' : '创建' }}
        </button>
      </div>
    </div>

    <!-- 文件选择子弹窗 -->
    <div v-if="pickerOpen" class="dialog-mask picker-mask" @click.self="closePicker">
      <div
        class="dialog-panel link-picker-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="link-picker-title"
      >
        <div class="dialog-head">
          <h2 id="link-picker-title">选择文件</h2>
          <p>从你的个人文件中选择一个文件。文件夹只能浏览，不能作为外链目标。</p>
        </div>

        <nav class="link-picker-breadcrumbs" aria-label="当前目录">
          <button
            class="breadcrumb-text"
            :class="{ active: !pickerParentId }"
            type="button"
            :disabled="pickerLoading"
            @click="pickerGoToRoot"
          >
            我的文件
          </button>
          <template v-for="crumb in pickerBreadcrumbs" :key="crumb.id">
            <span class="breadcrumb-divider">&gt;</span>
            <button
              v-if="crumb.id !== pickerParentId"
              class="breadcrumb-text"
              type="button"
              :disabled="pickerLoading"
              @click="pickerGoTo(crumb.id)"
            >
              {{ crumb.name }}
            </button>
            <span v-else class="breadcrumb-current">{{ crumb.name }}</span>
          </template>
        </nav>

        <div class="link-picker-body">
          <div v-if="pickerLoading" class="link-picker-empty">加载中…</div>
          <div v-else-if="pickerError" class="link-picker-error">{{ pickerError }}</div>
          <div v-else-if="!pickerItems.length" class="link-picker-empty">当前目录为空。</div>
          <ul v-else class="link-picker-list">
            <li
              v-for="item in pickerItems"
              :key="item.id"
              class="link-picker-item"
              :class="{ folder: item.itemType === 'folder', file: item.itemType !== 'folder' }"
            >
              <button
                v-if="item.itemType === 'folder'"
                class="link-picker-row"
                type="button"
                @click="pickerEnter(item)"
              >
                <AppIcon name="folder" :size="16" />
                <span class="link-picker-name">{{ item.name }}</span>
                <span class="link-picker-meta">文件夹</span>
              </button>
              <button
                v-else
                class="link-picker-row"
                type="button"
                @click="pickerChooseFile(item)"
              >
                <AppIcon name="file" :size="16" />
                <span class="link-picker-name">{{ item.name }}</span>
                <span class="link-picker-meta">{{ formatFileSize(item.sizeBytes) }}</span>
              </button>
            </li>
          </ul>
        </div>

        <div class="dialog-actions">
          <button class="secondary-button" type="button" @click="closePicker">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import AppIcon from '../icons/AppIcon.vue';
import { createLink, getDrivePath, getMyDriveItems } from '../../api/cloudnext';
import { formatFileSize } from '../../utils/formatters';

const props = defineProps({
  open: { type: Boolean, default: false }
});

const emit = defineEmits(['close', 'created']);

const submitting = ref(false);
const errorText = ref('');
const selectedItem = ref(null);

const form = reactive({
  permission: 'preview',
  requirePassword: false,
  password: '',
  expiresAt: '',
  downloadLimit: ''
});

const pickerOpen = ref(false);
const pickerLoading = ref(false);
const pickerError = ref('');
const pickerParentId = ref('');
const pickerItems = ref([]);
const pickerBreadcrumbs = ref([]);

const canSubmit = computed(() => {
  if (!selectedItem.value) return false;
  if (!form.permission) return false;
  if (form.requirePassword && !form.password) return false;
  if (form.downloadLimit !== '' && form.downloadLimit !== null) {
    const limit = Number(form.downloadLimit);
    if (!Number.isFinite(limit) || limit <= 0 || !Number.isInteger(limit)) {
      return false;
    }
  }
  return true;
});

const resetForm = () => {
  selectedItem.value = null;
  form.permission = 'preview';
  form.requirePassword = false;
  form.password = '';
  form.expiresAt = '';
  form.downloadLimit = '';
  errorText.value = '';
  pickerOpen.value = false;
  pickerParentId.value = '';
  pickerItems.value = [];
  pickerBreadcrumbs.value = [];
  pickerError.value = '';
  pickerLoading.value = false;
};

watch(
  () => props.open,
  (next) => {
    if (next) {
      resetForm();
    }
  }
);

const onClose = () => {
  if (submitting.value) return;
  emit('close');
};

const loadPickerItems = async (parentId) => {
  pickerLoading.value = true;
  pickerError.value = '';
  try {
    const list = await getMyDriveItems(parentId || '');
    pickerItems.value = Array.isArray(list) ? list : [];
    if (parentId) {
      try {
        const path = await getDrivePath(parentId);
        pickerBreadcrumbs.value = Array.isArray(path) ? path : [];
      } catch (error) {
        pickerBreadcrumbs.value = [];
      }
    } else {
      pickerBreadcrumbs.value = [];
    }
  } catch (error) {
    pickerItems.value = [];
    pickerError.value = error?.message || '加载目录失败';
  } finally {
    pickerLoading.value = false;
  }
};

const openPicker = async () => {
  pickerOpen.value = true;
  pickerParentId.value = '';
  pickerBreadcrumbs.value = [];
  pickerItems.value = [];
  pickerError.value = '';
  await loadPickerItems('');
};

const closePicker = () => {
  pickerOpen.value = false;
};

const pickerGoToRoot = async () => {
  pickerParentId.value = '';
  pickerBreadcrumbs.value = [];
  await loadPickerItems('');
};

const pickerGoTo = async (id) => {
  pickerParentId.value = id;
  await loadPickerItems(id);
};

const pickerEnter = async (folder) => {
  pickerParentId.value = folder.id;
  await loadPickerItems(folder.id);
};

const pickerChooseFile = (file) => {
  selectedItem.value = {
    id: file.id,
    name: file.name,
    fileType: file.fileType,
    fileTypeLabel: file.fileTypeLabel,
    sizeBytes: file.sizeBytes
  };
  pickerOpen.value = false;
};

const onConfirm = async () => {
  if (!canSubmit.value || submitting.value) return;
  errorText.value = '';

  const payload = {
    itemId: selectedItem.value.id,
    permission: form.permission
  };

  if (form.requirePassword && form.password) {
    payload.password = form.password;
  }

  if (form.expiresAt) {
    payload.expiresAt = form.expiresAt;
  }

  if (form.downloadLimit !== '' && form.downloadLimit !== null) {
    const limit = Number(form.downloadLimit);
    if (Number.isFinite(limit) && limit > 0 && Number.isInteger(limit)) {
      payload.downloadLimit = limit;
    }
  }

  submitting.value = true;
  try {
    const link = await createLink(payload);
    emit('created', link);
  } catch (error) {
    errorText.value = error?.message || '创建外链失败';
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.18);
}

.picker-mask {
  z-index: 70;
  background: rgba(15, 23, 42, 0.24);
}

.dialog-panel {
  width: min(560px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
}

.link-picker-dialog {
  width: min(620px, 100%);
}

.dialog-head {
  display: grid;
  gap: 6px;
}

.dialog-head h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-primary);
}

.dialog-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.link-create-field {
  display: grid;
  gap: 8px;
}

.link-create-label {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-primary);
}

.link-create-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.link-create-radios {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 16px;
}

.link-create-radio {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
}

.link-create-radio input[type='radio'] {
  margin: 0;
}

.link-create-input {
  width: 100%;
  height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 12px;
  color: var(--text-primary);
  background: var(--bg-panel);
  font-size: 13px;
}

.link-create-file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
}

.link-create-file-info {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.link-create-file-info strong {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 400;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-create-file-info span {
  color: var(--text-tertiary);
  font-size: 12px;
}

.link-create-file-empty {
  flex: 1;
  color: var(--text-tertiary);
  font-size: 13px;
}

.link-create-file-btn {
  flex-shrink: 0;
  font-weight: 400;
}

.dialog-error {
  color: var(--danger-color, #d33);
  font-size: 13px;
  white-space: pre-wrap;
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.dialog-actions .primary-button,
.dialog-actions .secondary-button {
  font-weight: 400;
}

.link-picker-breadcrumbs {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.breadcrumb-text {
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
}

.breadcrumb-text:hover:not(:disabled),
.breadcrumb-text.active,
.breadcrumb-current {
  color: var(--text-primary);
}

.breadcrumb-text:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.breadcrumb-current {
  font-weight: 400;
  color: var(--text-primary);
  font-size: 13px;
}

.breadcrumb-divider {
  color: var(--text-tertiary);
  font-size: 12px;
}

.link-picker-body {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  min-height: 220px;
  max-height: 320px;
  overflow: auto;
}

.link-picker-empty,
.link-picker-error {
  padding: 24px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.link-picker-error {
  color: var(--danger-color, #d33);
}

.link-picker-list {
  list-style: none;
  margin: 0;
  padding: 6px;
  display: grid;
  gap: 4px;
}

.link-picker-row {
  width: 100%;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--text-primary);
  transition: background 0.18s ease, border-color 0.18s ease;
}

.link-picker-row:hover {
  background: rgba(47, 107, 255, 0.08);
  border-color: var(--bg-primary);
}

.link-picker-name {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-picker-meta {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 600px) {
  .link-create-file {
    flex-direction: column;
    align-items: stretch;
  }
  .link-create-file-btn {
    align-self: flex-end;
  }
}

/* === 弹窗手机端紧凑化（≤559px） === */
@media (max-width: 559px) {
  .dialog-mask,
  .picker-mask {
    padding: 12px;
    align-items: center;
  }

  .dialog-panel,
  .link-picker-dialog {
    width: 100%;
    max-width: 100%;
    padding: 16px;
    gap: 12px;
    max-height: 90vh;
    overflow-y: auto;
  }

  .dialog-head h2 {
    font-size: 17px;
  }

  .dialog-head p {
    font-size: 12px;
  }

  .dialog-field {
    gap: 6px;
  }

  .dialog-field input,
  .dialog-field select,
  .dialog-field textarea {
    height: 40px;
    font-size: 14px;
  }

  .dialog-actions {
    flex-direction: column-reverse;
    gap: 8px;
  }

  .dialog-actions > button {
    width: 100%;
    min-height: 44px;
  }

  .link-picker-list,
  .link-create-file-picker-list {
    max-height: 40vh;
  }
}
</style>
