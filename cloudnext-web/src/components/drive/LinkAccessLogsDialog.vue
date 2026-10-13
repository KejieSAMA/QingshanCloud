<template>
  <div v-if="open" class="dialog-mask" @click.self="$emit('close')">
    <div class="dialog-panel link-access-dialog" role="dialog" aria-modal="true" aria-labelledby="link-access-title">
      <div class="dialog-head">
        <h2 id="link-access-title">访问统计</h2>
        <p class="link-access-file" :title="link?.fileName || ''">{{ link?.fileName || '(已删除)' }}</p>
      </div>

      <div class="link-access-summary">
        <span class="link-access-chip is-total">共 {{ totalCount }} 次</span>
        <span class="link-access-chip is-ok">成功 {{ successCount }}</span>
        <span class="link-access-chip is-fail">失败 {{ failCount }}</span>
      </div>

      <p v-if="errorText" class="status-error">{{ errorText }}</p>
      <div v-else-if="loading" class="link-access-empty">加载中…</div>
      <div v-else-if="!items.length" class="link-access-empty">暂无访问记录。</div>

      <div v-else class="link-access-table-wrap">
        <table class="link-access-table">
          <thead>
            <tr>
              <th class="col-time">时间</th>
              <th class="col-action">操作</th>
              <th class="col-status">状态</th>
              <th class="col-reason">原因</th>
              <th class="col-ip">IP</th>
              <th class="col-ua">UA</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td class="link-access-mono">{{ formatTime(row.attemptedAt) }}</td>
              <td>{{ actionLabel(row.actionType) }}</td>
              <td>
                <span class="link-access-status" :class="row.success ? 'is-ok' : 'is-fail'">
                  {{ row.success ? '成功' : '失败' }}
                </span>
              </td>
              <td>{{ row.success ? '—' : reasonLabel(row.failReason) }}</td>
              <td class="link-access-mono">{{ row.ipAddress || '—' }}</td>
              <td class="link-access-ua" :title="row.userAgent || ''">{{ truncate(row.userAgent, 40) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="link-access-pagination">
        <button class="ghost-button" type="button" :disabled="page <= 1 || loading" @click="goPage(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button class="ghost-button" type="button" :disabled="page >= totalPages || loading" @click="goPage(page + 1)">下一页</button>
      </div>

      <div class="dialog-actions">
        <button class="secondary-button" type="button" @click="$emit('close')">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { getLinkAccessLogs } from '../../api/cloudnext';
import { formatTime } from '../../utils/formatters';

const props = defineProps({
  open: { type: Boolean, default: false },
  link: { type: Object, default: null }
});

defineEmits(['close']);

const items = ref([]);
const loading = ref(false);
const errorText = ref('');
const page = ref(1);
const pageSize = ref(20);
const totalPages = ref(0);
const totalCount = ref(0);
const successCount = ref(0);
const failCount = ref(0);

const ACTION_LABELS = {
  VERIFY: '密码校验',
  PREVIEW: '预览',
  DOWNLOAD: '下载'
};
const REASON_LABELS = {
  WRONG_PASSWORD: '密码错误',
  EXPIRED: '已过期',
  EXHAUSTED: '次数用完',
  REVOKED: '已撤销',
  NOT_FOUND: '链接不存在',
  PERMISSION_DENIED: '权限不足',
  RATE_LIMITED: '触发限流',
  OTHER: '其它原因',
  OK: '—'
};

const actionLabel = (a) => ACTION_LABELS[a] || a || '—';
const reasonLabel = (r) => REASON_LABELS[r] || r || '—';

const truncate = (s, n) => {
  if (!s) return '—';
  return s.length > n ? `${s.slice(0, n)}…` : s;
};

const fetchPage = async () => {
  if (!props.link?.id) return;
  loading.value = true;
  errorText.value = '';
  try {
    const resp = await getLinkAccessLogs(props.link.id, page.value, pageSize.value);
    items.value = Array.isArray(resp?.logs?.content) ? resp.logs.content : [];
    totalPages.value = Number(resp?.logs?.totalPages || 0);
    totalCount.value = Number(resp?.totalCount || 0);
    successCount.value = Number(resp?.successCount || 0);
    failCount.value = Number(resp?.failCount || 0);
  } catch (e) {
    errorText.value = e?.message || '访问统计加载失败';
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const goPage = (next) => {
  if (next < 1 || (totalPages.value && next > totalPages.value)) return;
  page.value = next;
  void fetchPage();
};

watch(
  () => [props.open, props.link?.id],
  ([nextOpen, nextId]) => {
    if (nextOpen && nextId) {
      page.value = 1;
      void fetchPage();
    } else if (!nextOpen) {
      items.value = [];
      errorText.value = '';
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.link-access-dialog {
  width: min(800px, 100%);
}

.link-access-file {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  word-break: break-all;
}

.link-access-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.link-access-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
}

.link-access-chip.is-total {
  background: var(--bg-primary-soft);
  color: var(--bg-primary);
}

.link-access-chip.is-ok {
  background: rgba(31, 122, 58, 0.12);
  color: #1f7a3a;
}

.link-access-chip.is-fail {
  background: rgba(220, 38, 38, 0.12);
  color: #b42318;
}

.link-access-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.link-access-table-wrap {
  overflow-x: auto;
  max-height: 50vh;
  overflow-y: auto;
}

.link-access-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.link-access-table th,
.link-access-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.link-access-table th {
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-panel-soft);
  font-size: 12px;
  position: sticky;
  top: 0;
}

.link-access-mono {
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-size: 12px;
}

.link-access-ua {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-tertiary);
}

.link-access-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.link-access-status.is-ok {
  background: rgba(31, 122, 58, 0.1);
  color: #1f7a3a;
}

.link-access-status.is-fail {
  background: rgba(220, 38, 38, 0.1);
  color: #b42318;
}

.link-access-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
