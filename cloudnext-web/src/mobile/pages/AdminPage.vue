<template>
  <!-- N+2.10: AdminPage 重构 — 风格与 HomePage（云盘多彩商务风）一致 -->
  <section class="admin-page">
    <!-- 顶部 Hero + 4 KPI -->
    <div class="ap-kpi-row">
      <div class="ap-kpi-welcome">
        <div>
          <h1 class="ap-greeting">服务状态与组织同步</h1>
          <p class="ap-date">查看 Form 权威目录同步、迁移映射与审计日志</p>
        </div>
        <div class="ap-hero-actions">
          <button class="ap-btn ap-btn-secondary" type="button" :disabled="triggeringSync" @click="loadAll()">
            <AppIcon name="refresh" :size="14" />
            <span>刷新状态</span>
          </button>
          <button class="ap-btn ap-btn-primary" type="button" :disabled="triggeringSync || isSyncBusy" @click="onTriggerSync">
            <AppIcon name="upload" :size="14" />
            <span>{{ triggeringSync || isSyncBusy ? '同步中...' : '立即同步' }}</span>
          </button>
        </div>
      </div>

      <div class="ap-kpi">
        <div class="ap-kpi-head">
          <span class="ap-kpi-label">Form 组织</span>
          <span class="ap-kpi-icon ap-kpi-icon-blue">
            <AppIcon name="workspace" :size="16" />
          </span>
        </div>
        <div class="ap-kpi-value">{{ stats.units || 0 }}</div>
        <div class="ap-kpi-trend">人员 {{ stats.persons || 0 }}</div>
      </div>

      <div class="ap-kpi">
        <div class="ap-kpi-head">
          <span class="ap-kpi-label">Form 任职</span>
          <span class="ap-kpi-icon ap-kpi-icon-green">
            <AppIcon name="user" :size="16" />
          </span>
        </div>
        <div class="ap-kpi-value">{{ stats.identities || 0 }}</div>
        <div class="ap-kpi-trend">成员关系 {{ stats.memberships || 0 }}</div>
      </div>

      <div class="ap-kpi">
        <div class="ap-kpi-head">
          <span class="ap-kpi-label">历史角色 · 群组</span>
          <span class="ap-kpi-icon ap-kpi-icon-purple">
            <AppIcon name="share" :size="16" />
          </span>
        </div>
        <div class="ap-kpi-value">{{ stats.roles || 0 }}</div>
        <div class="ap-kpi-trend">群组 {{ stats.groups || 0 }}</div>
      </div>
    </div>

    <!-- 同步状态 banner -->
    <div class="ap-sync-banner" :class="`is-${sync.phase || 'idle'}`">
      <span class="ap-sync-dot"></span>
      <div class="ap-sync-copy">
        <strong>{{ syncPhaseLabel }}</strong>
        <span>{{ syncStageDescription }}</span>
      </div>
      <span class="ap-sync-badge">{{ sync.running ? '运行中' : '空闲' }}</span>
    </div>

    <!-- 主区 + 右栏 -->
    <div class="ap-main-row">
      <!-- 主区：执行进度 + 配置 + 审计日志 -->
      <div class="ap-main-col">
        <!-- 执行进度 -->
        <section class="ap-panel">
          <div class="ap-panel-head">
            <h2 class="ap-panel-title">执行进度</h2>
            <span class="ap-panel-sub">最近一次同步任务的执行状态</span>
          </div>
          <div class="ap-status-grid">
            <div class="ap-status-item">
              <span class="ap-status-label">当前阶段</span>
              <strong>{{ sync.phase || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">进度说明</span>
              <strong>{{ sync.progressStage || sync.lastMessage || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">开始时间</span>
              <strong>{{ sync.lastStartedAt || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">完成时间</span>
              <strong>{{ sync.lastFinishedAt || '--' }}</strong>
            </div>
          </div>

          <div v-if="sync.phase === 'sync-failed' && sync.failureLog" class="ap-fail-log">
            <div class="ap-fail-log-head">
              <strong>失败日志</strong>
              <span>{{ sync.lastOutcome }}</span>
            </div>
            <pre class="ap-fail-log-body">{{ sync.failureLog }}</pre>
          </div>

          <div v-if="pageError" class="ap-error">{{ pageError }}</div>
        </section>

        <!-- 运行状态 / 配置 -->
        <section class="ap-panel">
          <div class="ap-panel-head">
            <h2 class="ap-panel-title">配置与连通性</h2>
            <span class="ap-panel-sub">系统运行参数与连接状态</span>
          </div>
          <div class="ap-status-grid">
            <div class="ap-status-item">
              <span class="ap-status-label">服务阶段</span>
              <strong>{{ service.phase || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">同步模式</span>
              <strong>{{ sync.mode || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">Form SQL 权威源</span>
              <strong>
                <span :class="['ap-dot', sync.hasReadonlyJdbcSource ? 'is-ok' : 'is-warn']"></span>
                {{ sync.hasReadonlyJdbcSource ? '已配置' : '未配置' }}
              </strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">认证模式</span>
              <strong>{{ config.authMode || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">对象存储</span>
              <strong>{{ config.objectStorageBucket || '--' }}</strong>
            </div>
            <div class="ap-status-item">
              <span class="ap-status-label">统计时间</span>
              <strong>{{ stats.capturedAt || '--' }}</strong>
            </div>
          </div>
        </section>

        <AdminIdentityMigrationPanel />

        <!-- 审计日志 -->
        <AdminAuditPanel />
      </div>

      <!-- 右侧栏：同步节奏 + 组织镜像统计（紧凑） -->
      <aside class="ap-side">
        <section class="ap-panel">
          <h3 class="ap-side-title">同步节奏</h3>
          <div class="ap-rhythm">
            <div class="ap-rhythm-row">
              <span class="ap-rhythm-label">最近结果</span>
              <span :class="['ap-rhythm-pill', resolveOutcomeClass(sync.lastOutcome)]">
                {{ sync.lastOutcome || '--' }}
              </span>
            </div>
            <div class="ap-rhythm-row">
              <span class="ap-rhythm-label">同步计划</span>
              <code class="ap-rhythm-code">{{ sync.cron || '--' }}</code>
            </div>
            <div class="ap-rhythm-row">
              <span class="ap-rhythm-label">下一步</span>
              <span class="ap-rhythm-value">{{ sync.nextAction || service.nextAction || '--' }}</span>
            </div>
          </div>
        </section>

        <section class="ap-panel">
          <h3 class="ap-side-title">目录与历史归档</h3>
          <div class="ap-mirror-grid">
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">Form 组织</span>
              <strong>{{ stats.units || 0 }}</strong>
            </div>
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">Form 人员</span>
              <strong>{{ stats.persons || 0 }}</strong>
            </div>
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">Form 任职</span>
              <strong>{{ stats.identities || 0 }}</strong>
            </div>
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">历史角色</span>
              <strong>{{ stats.roles || 0 }}</strong>
            </div>
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">历史群组</span>
              <strong>{{ stats.groups || 0 }}</strong>
            </div>
            <div class="ap-mirror-item">
              <span class="ap-mirror-label">历史成员关系</span>
              <strong>{{ stats.memberships || 0 }}</strong>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </section>
</template>
<script setup>

import { computed, onMounted, onUnmounted, ref } from 'vue';
import {
  getAdminConfig,
  getFormDirectorySyncStatus,
  getStatus,
  getSyncStats,
  triggerFormDirectorySync
} from '../../api/cloudnext';
import AdminAuditPanel from '../../components/admin/AdminAuditPanel.vue';
import AdminIdentityMigrationPanel from '../../components/admin/AdminIdentityMigrationPanel.vue';
import AppIcon from '../../components/icons/AppIcon.vue';

const service = ref({
  phase: 'loading',
  nextAction: '--'
});

const sync = ref({
  mode: 'mirror',
  phase: 'bootstrap',
  lastOutcome: 'loading',
  hasReadonlyJdbcSource: false,
  cron: '',
  nextAction: '--',
  lastMessage: '',
  lastStartedAt: '',
  lastFinishedAt: '',
  currentRunId: '',
  running: false,
  progressStage: '',
  failureLog: ''
});

const config = ref({
  authMode: '',
  objectStorageBucket: ''
});

const stats = ref({
  units: 0,
  persons: 0,
  identities: 0,
  roles: 0,
  groups: 0,
  memberships: 0,
  capturedAt: ''
});

const pageError = ref('');
const triggeringSync = ref(false);
let syncPollTimer = null;

const isSyncBusy = computed(() => ['sync-queued', 'sync-running'].includes(sync.value.phase));

const syncPhaseLabel = computed(() => {
  const phase = sync.value.phase;
  if (phase === 'sync-queued') {
    return '已提交同步任务';
  }
  if (phase === 'sync-running') {
    return '正在同步组织镜像';
  }
  if (phase === 'sync-ready') {
    return '同步完成';
  }
  if (phase === 'sync-failed') {
    return '同步失败';
  }
  if (phase === 'waiting-config') {
    return '等待配置';
  }
  return '等待执行';
});

const syncStageDescription = computed(() => {
  if (sync.value.phase === 'waiting-config') {
    return '请先配置 Form HTTPS 内部接口和独立服务令牌，再执行目录同步。';
  }
  if (sync.value.phase === 'sync-queued') {
    return '同步任务已进入队列，正在等待执行。';
  }
  if (sync.value.progressStage === 'loading-source') {
    return '正在拉取并校验 Form 原子目录快照。';
  }
  if (sync.value.progressStage) {
    return sync.value.progressStage;
  }
  if (sync.value.lastMessage) {
    return sync.value.lastMessage;
  }
  return sync.value.nextAction || '--';
});

const resolveOutcomeClass = (outcome) => {
  const normalized = String(outcome || '').toLowerCase();
  if (normalized === 'success' || normalized === 'not_modified') return 'is-ok';
  if (normalized === 'failed') return 'is-fail';
  return 'is-neutral';
};

const normalizeSyncSnapshot = (value) => ({
  mode: value?.mode || (value?.sourceKey ? 'form-snapshot' : 'mirror'),
  phase: value?.phase || (
    value?.lastOutcome === 'RUNNING' ? 'sync-running'
      : ['SUCCESS', 'NOT_MODIFIED'].includes(value?.lastOutcome) ? 'sync-ready'
        : value?.lastOutcome === 'FAILED' ? 'sync-failed'
          : 'bootstrap'
  ),
  lastOutcome: value?.lastOutcome || '',
  hasReadonlyJdbcSource: typeof value?.hasReadonlyJdbcSource === 'boolean'
    ? value.hasReadonlyJdbcSource
    : value?.foundationSot === 'sql' && value?.foundationWriteImpl === 'sql',
  cron: value?.cron || '',
  nextAction: value?.nextAction || '',
  lastMessage: value?.lastMessage || '',
  lastStartedAt: value?.lastStartedAt || '',
  lastFinishedAt: value?.lastFinishedAt || value?.lastSuccessAt || value?.lastFailureAt || '',
  currentRunId: value?.currentRunId || '',
  running: Boolean(value?.running || value?.lastOutcome === 'RUNNING'),
  progressStage: value?.progressStage || '',
  failureLog: value?.failureLog || value?.failureMessage || ''
});

const stopSyncPolling = () => {
  if (syncPollTimer) {
    window.clearTimeout(syncPollTimer);
    syncPollTimer = null;
  }
};

const scheduleSyncPolling = () => {
  stopSyncPolling();
  if (!isSyncBusy.value) {
    return;
  }
  syncPollTimer = window.setTimeout(() => {
    void loadAll({ silent: true });
  }, 1000);
};

const loadAll = async ({ silent = false } = {}) => {
  if (!silent) {
    pageError.value = '';
  }
  try {
    const [serviceStatus, syncStatus, syncStats, adminConfig] = await Promise.all([
      getStatus(),
      getFormDirectorySyncStatus(),
      getSyncStats(),
      getAdminConfig()
    ]);
    service.value = serviceStatus || service.value;
    sync.value = normalizeSyncSnapshot(syncStatus);
    stats.value = {
      ...(syncStats || stats.value),
      units: syncStatus?.unitCount ?? syncStats?.units ?? stats.value.units,
      persons: syncStatus?.personCount ?? syncStats?.persons ?? stats.value.persons,
      identities: syncStatus?.identityCount ?? syncStats?.identities ?? stats.value.identities
    };
    config.value = adminConfig || config.value;
    pageError.value = '';
    scheduleSyncPolling();
  } catch (error) {
    stopSyncPolling();
    pageError.value = error?.message || '状态接口请求失败';
  }
};

const onTriggerSync = async () => {
  pageError.value = '';
  triggeringSync.value = true;
  try {
    const result = await triggerFormDirectorySync();
    sync.value = {
      ...sync.value,
      ...normalizeSyncSnapshot(result)
    };
    scheduleSyncPolling();
    await loadAll({ silent: true });
  } catch (error) {
    pageError.value = error?.message || '同步触发失败';
  } finally {
    triggeringSync.value = false;
  }
};

onMounted(() => {
  void loadAll();
});

onUnmounted(() => {
  stopSyncPolling();
});
</script>
<style scoped>

.admin-page {
  display: grid;
  gap: 16px;
}
.ap-kpi-row {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ap-kpi-welcome,
.ap-kpi {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 16px;
}

.ap-kpi-welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: linear-gradient(135deg, #f5f8ff 0%, #e8efff 100%);
}

.ap-greeting {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.ap-date {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.ap-hero-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.ap-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.ap-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ap-btn-secondary {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
}

.ap-btn-secondary:hover:not(:disabled) {
  background: var(--bg-panel-soft);
  border-color: var(--border-strong);
}

.ap-btn-primary {
  background: var(--bg-primary);
  border: 1px solid var(--bg-primary);
  color: #fff;
}

.ap-btn-primary:hover:not(:disabled) {
  background: var(--bg-primary-hover);
}

.ap-kpi {
  display: grid;
  gap: 6px;
  min-height: 96px;
}

.ap-kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ap-kpi-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.ap-kpi-icon {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.ap-kpi-icon-blue {
  background: rgba(47, 107, 255, 0.12);
  color: #1d6df0;
}

.ap-kpi-icon-green {
  background: rgba(18, 183, 106, 0.12);
  color: #12b76a;
}

.ap-kpi-icon-purple {
  background: rgba(124, 58, 237, 0.12);
  color: #7c3aed;
}

.ap-kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}

.ap-kpi-trend {
  font-size: 12px;
  color: var(--text-tertiary);
}
.ap-sync-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.ap-sync-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--text-tertiary);
  flex-shrink: 0;
}

@keyframes ap-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.ap-sync-copy {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.ap-sync-copy strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.ap-sync-copy span {
  font-size: 12px;
  color: var(--text-secondary);
}

.ap-sync-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--bg-panel-soft);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 11px;
  flex-shrink: 0;
}
.ap-main-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.ap-main-col {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.ap-side {
  display: grid;
  gap: 16px;
  align-content: start;
}
.ap-panel {
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 16px;
}

.ap-panel-head {
  display: grid;
  gap: 2px;
  margin-bottom: 12px;
}

.ap-panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.ap-panel-sub {
  font-size: 12px;
  color: var(--text-tertiary);
}
.ap-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.ap-status-item {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-md);
  min-height: 60px;
}

.ap-status-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.ap-status-item strong {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  word-break: break-word;
}

.ap-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.ap-dot.is-ok {
  background: #12b76a;
}

.ap-dot.is-warn {
  background: #f79009;
}
.ap-fail-log {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(217, 45, 32, 0.22);
  background: rgba(217, 45, 32, 0.04);
  border-radius: var(--radius-md);
}

.ap-fail-log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ap-fail-log-head strong {
  font-size: 13px;
  color: var(--text-primary);
}

.ap-fail-log-head span {
  font-size: 11px;
  color: var(--text-tertiary);
}

.ap-fail-log-body {
  margin: 0;
  padding: 10px;
  max-height: 240px;
  overflow: auto;
  background: var(--bg-panel);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.ap-error {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--bg-danger-soft);
  color: #b42318;
  font-size: 13px;
  border-radius: var(--radius-md);
}
.ap-side-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.ap-rhythm {
  display: grid;
  gap: 12px;
}

.ap-rhythm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ap-rhythm-label {
  font-size: 12px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.ap-rhythm-value {
  font-size: 13px;
  color: var(--text-primary);
  text-align: right;
  word-break: break-word;
}

.ap-rhythm-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  padding: 2px 6px;
  background: var(--bg-panel-soft);
  border-radius: 4px;
  color: var(--text-primary);
}

.ap-rhythm-pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}

.ap-rhythm-pill.is-ok {
  background: rgba(18, 183, 106, 0.12);
  color: #12b76a;
}

.ap-rhythm-pill.is-fail {
  background: rgba(217, 45, 32, 0.12);
  color: #d92d20;
}

.ap-rhythm-pill.is-neutral {
  background: var(--bg-panel-soft);
  color: var(--text-secondary);
}
.ap-mirror-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.ap-mirror-item {
  display: grid;
  gap: 2px;
  padding: 10px;
  background: var(--bg-panel-soft);
  border-radius: var(--radius-md);
}

.ap-mirror-label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.ap-mirror-item strong {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}
@media (max-width: 1280px) {
  .ap-kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ap-main-row {
    grid-template-columns: 1fr;
  }

  .ap-side {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .ap-side {
    grid-template-columns: 1fr;
  }

  .ap-status-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .ap-kpi-row {
    grid-template-columns: 1fr;
  }

  .ap-kpi-welcome {
    flex-direction: column;
    align-items: stretch;
  }

  .ap-hero-actions {
    width: 100%;
  }

  .ap-hero-actions .ap-btn {
    flex: 1;
    justify-content: center;
  }

  .ap-mirror-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .ap-mirror-item strong {
    font-size: 15px;
  }
}

</style>
