<template>
  <section class="admin-audit-panel surface-panel">
    <div class="section-head">
      <div class="section-head-copy">
        <span class="section-kicker">N+2.2 审计</span>
        <h2>审计日志</h2>
        <p>追溯文件 / 共享 / 外链 / 协作空间的关键写操作。</p>
      </div>
    </div>

    <div v-if="checkingPermission" class="audit-empty">正在校验访问权限…</div>

    <div v-else-if="!isAdmin" class="audit-empty audit-empty-muted">
      你不在 cloudnext 管理员白名单内，无法查看审计日志。<br />
      （管理员权限由服务端 canonical 人员白名单或 Form SYSTEM_ADMIN 角色控制）
    </div>

    <template v-else>
      <div class="audit-filter">
        <label class="audit-filter-field">
          <span>操作者 key / 工号</span>
          <input v-model.trim="filters.person" type="text" placeholder="输入 canonical key 或工号" @keydown.enter.prevent="onSearch" />
        </label>
        <label class="audit-filter-field">
          <span>行为</span>
          <select v-model="filters.action">
            <option value="">全部</option>
            <option v-for="opt in actionOptions" :key="opt" :value="opt">{{ ACTION_LABEL[opt] || opt }}</option>
          </select>
        </label>
        <label class="audit-filter-field">
          <span>起始日期</span>
          <input v-model="filters.sinceDate" type="date" />
        </label>
        <label class="audit-filter-field">
          <span>截止日期</span>
          <input v-model="filters.untilDate" type="date" />
        </label>
        <div class="audit-filter-actions">
          <button class="primary-button" type="button" :disabled="loading" @click="onSearch">查询</button>
          <button class="secondary-button" type="button" :disabled="loading" @click="onReset">重置</button>
        </div>
      </div>

      <p v-if="errorText" class="status-error">{{ errorText }}</p>

      <div v-if="!loading && items.length === 0" class="audit-empty audit-empty-muted">无匹配的审计记录。</div>

      <div v-else class="audit-table-wrap">
        <table class="audit-table">
          <thead>
            <tr>
              <th class="audit-col-time">时间</th>
              <th class="audit-col-person">操作者</th>
              <th class="audit-col-action">行为</th>
              <th class="audit-col-target">目标</th>
              <th class="audit-col-summary">摘要</th>
              <th class="audit-col-detail"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td class="audit-cell-mono">{{ formatTime(row.createdAt) }}</td>
              <td>{{ auditPerson(row) }}</td>
              <td>
                <span class="audit-action-chip" :class="`is-${actionTone(row.action)}`">
                  {{ ACTION_LABEL[row.action] || row.action }}
                </span>
              </td>
              <td class="audit-cell-mono">
                <span class="audit-target-type">{{ TARGET_LABEL[row.targetType] || row.targetType }}</span>
                <span class="audit-target-id"> / {{ truncate(row.targetId, 12) }}</span>
              </td>
              <td class="audit-cell-summary">{{ payloadSummary(row.payload) }}</td>
              <td>
                <button class="ghost-button audit-detail-btn" type="button" @click="onShowDetail(row)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 0" class="audit-pagination">
        <button class="ghost-button" type="button" :disabled="page <= 1 || loading" @click="changePage(page - 1)">上一页</button>
        <span class="audit-page-info">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条</span>
        <button class="ghost-button" type="button" :disabled="page >= totalPages || loading" @click="changePage(page + 1)">下一页</button>
      </div>
    </template>

    <div v-if="detail" class="audit-drawer-mask" @click.self="onCloseDetail">
      <div class="audit-drawer" role="dialog" aria-modal="true">
        <div class="audit-drawer-head">
          <h3>审计详情</h3>
          <button class="ghost-button" type="button" @click="onCloseDetail">关闭</button>
        </div>
        <dl class="audit-drawer-fields">
          <div><dt>时间</dt><dd>{{ formatTime(detail.createdAt) }}</dd></div>
          <div><dt>操作者</dt><dd>{{ auditPerson(detail) }}</dd></div>
          <div>
            <dt>行为</dt>
            <dd>{{ ACTION_LABEL[detail.action] || detail.action }} <span class="audit-drawer-raw">({{ detail.action }})</span></dd>
          </div>
          <div>
            <dt>目标</dt>
            <dd>{{ TARGET_LABEL[detail.targetType] || detail.targetType }} / {{ detail.targetId }}</dd>
          </div>
          <div><dt>来源 IP</dt><dd>{{ detail.ipAddress || '—' }}</dd></div>
          <div><dt>UA</dt><dd class="audit-drawer-ua">{{ detail.userAgent || '—' }}</dd></div>
        </dl>
        <h4>Payload</h4>
        <pre class="audit-drawer-payload">{{ formatPayload(detail.payload) }}</pre>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { getAuditLogs, getCurrentAdminStatus } from '../../api/cloudnext';
import { formatTime } from '../../utils/formatters';

const auditPerson = (row) => row?.employeeNo || row?.personKey || row?.personOaId || '—';

const actionOptions = [
  'UPLOAD', 'DOWNLOAD', 'DELETE', 'RESTORE', 'PURGE', 'MOVE', 'COPY', 'RENAME',
  'SHARE_CREATE', 'SHARE_UPDATE', 'SHARE_REVOKE',
  'LINK_CREATE', 'LINK_UPDATE', 'LINK_REVOKE', 'LINK_ACCESS',
  'EDIT_OPEN', 'EDIT_COMPLETED', 'EDIT_FAILED',
  'MEMBER_ADD', 'MEMBER_REMOVE'
];

// N+2.10: 审计动作 / 目标类型中文映射 + 按类别上色
const ACTION_LABEL = {
  UPLOAD: '上传',
  DOWNLOAD: '下载',
  DELETE: '移入回收站',
  RESTORE: '还原',
  PURGE: '彻底删除',
  MOVE: '移动',
  COPY: '复制',
  RENAME: '重命名',
  SHARE_CREATE: '创建共享',
  SHARE_UPDATE: '更新共享',
  SHARE_REVOKE: '撤销共享',
  LINK_CREATE: '创建外链',
  LINK_UPDATE: '更新外链',
  LINK_REVOKE: '撤销外链',
  LINK_ACCESS: '访问外链',
  EDIT_OPEN: '打开编辑',
  EDIT_COMPLETED: '编辑完成',
  EDIT_FAILED: '编辑失败',
  MEMBER_ADD: '成员加入',
  MEMBER_REMOVE: '成员移除'
};

const TARGET_LABEL = {
  DRIVE_ITEM: '文件/文件夹',
  DRIVE_SHARE: '内部共享',
  DRIVE_PUBLIC_LINK: '外链',
  WORKSPACE_MEMBER: '协作空间成员',
  ONLYOFFICE_CALLBACK: 'OnlyOffice 回调',
  ONLYOFFICE_FORCESAVE: 'OnlyOffice 强制保存'
};

// 行为按风险等级上色：info 蓝 / success 绿 / warn 橙 / danger 红
const ACTION_TONE = {
  // info（中性/查看类）
  UPLOAD: 'info',
  DOWNLOAD: 'info',
  EDIT_OPEN: 'info',
  EDIT_COMPLETED: 'info',
  LINK_ACCESS: 'info',
  // success（建设性）
  RESTORE: 'success',
  SHARE_CREATE: 'success',
  LINK_CREATE: 'success',
  MEMBER_ADD: 'success',
  // warn（修改类）
  MOVE: 'warn',
  COPY: 'success',
  RENAME: 'warn',
  SHARE_UPDATE: 'warn',
  LINK_UPDATE: 'warn',
  // danger（删除/撤销/失败）
  DELETE: 'danger',
  PURGE: 'danger',
  SHARE_REVOKE: 'danger',
  LINK_REVOKE: 'danger',
  EDIT_FAILED: 'danger',
  MEMBER_REMOVE: 'danger'
};

const actionTone = (action) => ACTION_TONE[action] || 'info';

const checkingPermission = ref(true);
const isAdmin = ref(false);
const loading = ref(false);
const errorText = ref('');
const items = ref([]);
const total = ref(0);
const totalPages = ref(0);
const page = ref(1);
const pageSize = ref(20);
const detail = ref(null);

const filters = reactive({
  person: '',
  action: '',
  sinceDate: '',
  untilDate: ''
});

const buildRequest = () => {
  const req = { page: page.value, size: pageSize.value };
  if (filters.person) req.person = filters.person;
  if (filters.action) req.action = filters.action;
  if (filters.sinceDate) req.since = new Date(`${filters.sinceDate}T00:00:00`).getTime();
  if (filters.untilDate) {
    // 截止日期含当日：用次日 00:00 作为 until 的开区间上限
    const d = new Date(`${filters.untilDate}T00:00:00`);
    d.setDate(d.getDate() + 1);
    req.until = d.getTime();
  }
  return req;
};

const fetchPage = async () => {
  loading.value = true;
  errorText.value = '';
  try {
    const resp = await getAuditLogs(buildRequest());
    items.value = Array.isArray(resp?.content) ? resp.content : [];
    total.value = Number(resp?.totalElements || 0);
    totalPages.value = Number(resp?.totalPages || 0);
  } catch (e) {
    errorText.value = e?.message || '审计日志加载失败';
    items.value = [];
    total.value = 0;
    totalPages.value = 0;
  } finally {
    loading.value = false;
  }
};

const onSearch = () => {
  page.value = 1;
  void fetchPage();
};

const onReset = () => {
  filters.person = '';
  filters.action = '';
  filters.sinceDate = '';
  filters.untilDate = '';
  page.value = 1;
  void fetchPage();
};

const changePage = (next) => {
  if (next < 1 || next > totalPages.value) return;
  page.value = next;
  void fetchPage();
};

const onShowDetail = (row) => {
  detail.value = row;
};

const onCloseDetail = () => {
  detail.value = null;
};

const truncate = (s, n) => {
  if (!s) return '—';
  return s.length > n ? `${s.slice(0, n)}…` : s;
};

const payloadSummary = (raw) => {
  if (!raw) return '—';
  try {
    const obj = JSON.parse(raw);
    const keys = Object.keys(obj).slice(0, 3);
    return keys.map((k) => `${k}=${JSON.stringify(obj[k])}`).join(', ');
  } catch (e) {
    return raw.length > 80 ? `${raw.slice(0, 80)}…` : raw;
  }
};

const formatPayload = (raw) => {
  if (!raw) return '(空)';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch (e) {
    return raw;
  }
};

onMounted(async () => {
  try {
    const resp = await getCurrentAdminStatus();
    isAdmin.value = Boolean(resp?.admin);
  } catch (e) {
    isAdmin.value = false;
  } finally {
    checkingPermission.value = false;
  }
  if (isAdmin.value) {
    void fetchPage();
  }
});
</script>

<style scoped>
.admin-audit-panel {
  display: grid;
  gap: 14px;
}

.audit-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-secondary);
}

.audit-empty-muted code {
  background: var(--bg-panel-soft);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.audit-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: 10px;
  align-items: end;
}

.audit-filter-field {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.audit-filter-field span {
  font-size: 12px;
  color: var(--text-secondary);
}

.audit-filter-field input,
.audit-filter-field select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  color: var(--text-primary);
  font-size: 13px;
}

.audit-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-table-wrap {
  overflow-x: auto;
}

.audit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.audit-table th,
.audit-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
  vertical-align: middle;
}

.audit-table th {
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-panel-soft);
  font-size: 12px;
}

.audit-cell-mono {
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-size: 12px;
  color: var(--text-primary);
}

.audit-cell-summary {
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
  font-size: 12px;
}

.audit-action-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.audit-action-chip.is-info {
  background: rgba(47, 107, 255, 0.12);
  color: #1d6df0;
}

.audit-action-chip.is-success {
  background: rgba(18, 183, 106, 0.12);
  color: #027a48;
}

.audit-action-chip.is-warn {
  background: rgba(245, 158, 11, 0.14);
  color: #b54708;
}

.audit-action-chip.is-danger {
  background: rgba(217, 45, 32, 0.12);
  color: #b42318;
}

.audit-target-type {
  color: var(--text-primary);
  font-weight: 500;
}

.audit-target-id {
  color: var(--text-tertiary);
}

.audit-drawer-raw {
  color: var(--text-tertiary);
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-size: 11px;
  margin-left: 4px;
}

.audit-detail-btn {
  min-height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.audit-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.audit-page-info {
  font-size: 12px;
  color: var(--text-secondary);
}

.audit-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.4);
}

.audit-drawer {
  width: min(640px, 100%);
  max-height: 80vh;
  display: grid;
  gap: 14px;
  padding: 20px;
  background: var(--bg-panel);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  overflow-y: auto;
}

.audit-drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.audit-drawer-head h3 {
  margin: 0;
  font-size: 18px;
}

.audit-drawer-fields {
  margin: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
}

.audit-drawer-fields > div {
  display: grid;
  gap: 2px;
}

.audit-drawer-fields dt {
  font-size: 11px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.audit-drawer-fields dd {
  margin: 0;
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}

.audit-drawer-ua {
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-size: 11px;
}

.audit-drawer-payload {
  margin: 0;
  padding: 12px;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-md);
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow-y: auto;
}

@media (max-width: 720px) {
  .audit-filter {
    grid-template-columns: 1fr 1fr;
  }

  .audit-filter-actions {
    grid-column: 1 / -1;
  }

  .audit-drawer-fields {
    grid-template-columns: 1fr;
  }
}
</style>
