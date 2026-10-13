<template>
  <div v-if="open" class="dialog-mask" @click.self="onClose">
    <div
      class="dialog-panel link-update-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="link-update-title"
    >
      <div class="dialog-head">
        <h2 id="link-update-title">修改外链</h2>
        <p>仅更新你勾选的字段，其余字段保持原值不变。</p>
      </div>

      <!-- 关联文件（只读） -->
      <div class="link-update-field">
        <span class="link-update-label">关联文件</span>
        <div class="link-update-file">
          <strong :title="link?.fileName || '(已删除)'">{{ link?.fileName || '(已删除)' }}</strong>
          <span class="link-update-hint">外链所关联的文件不可更改。</span>
        </div>
      </div>

      <!-- 权限（始终提交） -->
      <div class="link-update-field">
        <span class="link-update-label">权限</span>
        <div class="link-update-radios">
          <label class="link-update-radio">
            <input
              v-model="form.permission"
              type="radio"
              value="preview"
              :disabled="submitting"
            />
            <span>仅预览</span>
          </label>
          <label class="link-update-radio">
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

      <!-- 访问密码（三态） -->
      <div class="link-update-field">
        <span class="link-update-label">访问密码</span>
        <div class="link-update-radios">
          <label class="link-update-radio">
            <input
              v-model="form.passwordMode"
              type="radio"
              value="keep"
              :disabled="submitting"
            />
            <span>保持原值</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.passwordMode"
              type="radio"
              value="clear"
              :disabled="submitting"
            />
            <span>清除密码</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.passwordMode"
              type="radio"
              value="set"
              :disabled="submitting"
            />
            <span>设置新密码</span>
          </label>
        </div>
        <input
          v-if="form.passwordMode === 'set'"
          v-model.trim="form.password"
          type="text"
          class="link-update-input"
          placeholder="输入新密码"
          maxlength="64"
          :disabled="submitting"
        />
        <span class="link-update-hint">原密码以哈希形式保存，无法回显，因此只能保持、清除或重新设置。</span>
      </div>

      <!-- 有效期（三态） -->
      <div class="link-update-field">
        <span class="link-update-label">有效期</span>
        <div class="link-update-radios">
          <label class="link-update-radio">
            <input
              v-model="form.expiresMode"
              type="radio"
              value="keep"
              :disabled="submitting"
            />
            <span>保持原值</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.expiresMode"
              type="radio"
              value="clear"
              :disabled="submitting"
            />
            <span>永久有效</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.expiresMode"
              type="radio"
              value="set"
              :disabled="submitting"
            />
            <span>设置新有效期</span>
          </label>
        </div>
        <input
          v-if="form.expiresMode === 'set'"
          v-model="form.expiresAt"
          type="date"
          class="link-update-input"
          :disabled="submitting"
        />
      </div>

      <!-- 下载次数（三态） -->
      <div class="link-update-field">
        <span class="link-update-label">下载次数</span>
        <div class="link-update-radios">
          <label class="link-update-radio">
            <input
              v-model="form.limitMode"
              type="radio"
              value="keep"
              :disabled="submitting"
            />
            <span>保持原值</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.limitMode"
              type="radio"
              value="clear"
              :disabled="submitting"
            />
            <span>不限</span>
          </label>
          <label class="link-update-radio">
            <input
              v-model="form.limitMode"
              type="radio"
              value="set"
              :disabled="submitting"
            />
            <span>设置上限</span>
          </label>
        </div>
        <input
          v-if="form.limitMode === 'set'"
          v-model.number="form.downloadLimit"
          type="number"
          min="1"
          class="link-update-input"
          placeholder="例如 10"
          :disabled="submitting"
        />
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
          {{ submitting ? '保存中…' : '保存修改' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { updateLink } from '../../api/cloudnext';

const props = defineProps({
  open: { type: Boolean, default: false },
  link: { type: Object, default: null }
});

const emit = defineEmits(['close', 'updated']);

const submitting = ref(false);
const errorText = ref('');

const form = reactive({
  permission: 'preview',
  passwordMode: 'keep',
  password: '',
  expiresMode: 'keep',
  expiresAt: '',
  limitMode: 'keep',
  downloadLimit: ''
});

const toDateInputValue = (raw) => {
  if (!raw) return '';
  const date = raw instanceof Date ? raw : new Date(raw);
  if (Number.isNaN(date.getTime())) return '';
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

const resetFromLink = (link) => {
  errorText.value = '';
  form.permission = link?.permission === 'download' ? 'download' : 'preview';
  form.passwordMode = 'keep';
  form.password = '';
  form.expiresMode = 'keep';
  form.expiresAt = toDateInputValue(link?.expiresAt);
  form.limitMode = 'keep';
  const lim = Number(link?.downloadLimit ?? -1);
  form.downloadLimit = Number.isFinite(lim) && lim > 0 ? lim : '';
};

watch(
  () => [props.open, props.link?.id],
  ([next]) => {
    if (next) {
      resetFromLink(props.link);
    }
  },
  { immediate: true }
);

const canSubmit = computed(() => {
  if (!props.link?.id) return false;
  if (!form.permission) return false;
  if (form.passwordMode === 'set' && !form.password) return false;
  if (form.expiresMode === 'set' && !form.expiresAt) return false;
  if (form.limitMode === 'set') {
    const limit = Number(form.downloadLimit);
    if (!Number.isFinite(limit) || limit <= 0 || !Number.isInteger(limit)) {
      return false;
    }
  }
  return true;
});

const onClose = () => {
  if (submitting.value) return;
  emit('close');
};

const onConfirm = async () => {
  if (!canSubmit.value || submitting.value) return;
  errorText.value = '';

  // permission is always sent (radio bound, no "keep" mode for it).
  const payload = {
    permission: form.permission
  };

  // password tri-state
  if (form.passwordMode === 'clear') {
    payload.password = '';
  } else if (form.passwordMode === 'set') {
    payload.password = form.password;
  }
  // 'keep' → omit field entirely (server treats null/undefined as unchanged)

  // expiresAt tri-state
  if (form.expiresMode === 'clear') {
    payload.expiresAt = '';
  } else if (form.expiresMode === 'set') {
    payload.expiresAt = form.expiresAt;
  }

  // downloadLimit tri-state
  if (form.limitMode === 'clear') {
    payload.downloadLimit = -1;
  } else if (form.limitMode === 'set') {
    payload.downloadLimit = Number(form.downloadLimit);
  }

  submitting.value = true;
  try {
    const updated = await updateLink(props.link.id, payload);
    emit('updated', updated);
  } catch (error) {
    errorText.value = error?.message || '修改外链失败';
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

.link-update-field {
  display: grid;
  gap: 8px;
}

.link-update-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.link-update-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.link-update-radios {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 16px;
}

.link-update-radio {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
}

.link-update-radio input[type='radio'] {
  margin: 0;
}

.link-update-input {
  width: 100%;
  height: 38px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 12px;
  color: var(--text-primary);
  background: var(--bg-panel);
  font-size: 13px;
}

.link-update-file {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-panel-soft);
}

.link-update-file strong {
  color: var(--text-primary);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* === 弹窗手机端紧凑化（≤559px） === */
@media (max-width: 559px) {
  .dialog-mask {
    padding: 12px;
    align-items: center;
  }

  .dialog-panel {
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
}
</style>
