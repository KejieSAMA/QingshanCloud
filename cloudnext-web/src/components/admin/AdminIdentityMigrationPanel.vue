<template>
  <section class="migration-panel">
    <header class="migration-head">
      <div>
        <h2>Form 身份迁移</h2>
        <p>确认人员、组织与任职的 canonical 绑定；校验通过前不会允许最终切换。</p>
      </div>
      <div class="migration-actions">
        <button type="button" :disabled="busy" @click="loadAll">刷新</button>
        <button type="button" :disabled="busy" @click="runValidation">切换校验</button>
      </div>
    </header>

    <div class="migration-summary">
      <div v-for="card in summaryCards" :key="card.label">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </div>
      <div :class="validation?.ready ? 'is-ready' : 'is-blocked'">
        <span>切换状态</span>
        <strong>{{ validation?.ready ? '可切换' : '存在阻断项' }}</strong>
      </div>
    </div>

    <div v-if="validation?.blockers?.length" class="migration-blockers">
      <strong>阻断项</strong>
      <ul>
        <li v-for="(blocker, index) in validation.blockers" :key="`${index}-${blocker.code || blocker}`">
          {{ blocker.message || blocker.code || blocker }}
        </li>
      </ul>
    </div>

    <nav class="migration-tabs" aria-label="映射类型">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        :class="{ active: activeKind === item.key }"
        @click="selectKind(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="activeKind === 'units'" class="migration-csv-actions">
      <button type="button" :disabled="busy" @click="exportFormUnitMappings">导出组织 CSV</button>
      <label>
        导入组织 CSV
        <input type="file" accept=".csv,text/csv" :disabled="busy" @change="onCsvSelected" />
      </label>
    </div>

    <div class="migration-table-wrap">
      <table class="migration-table">
        <thead>
          <tr>
            <th>Form 对象</th>
            <th>稳定 ID</th>
            <th>canonical / 历史 OA</th>
            <th>决策</th>
            <th>影响引用</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="rowStableId(row)">
            <td>
              <strong>{{ row.displayName || row.name || row.employeeName || row.unitName || row.identityName || '—' }}</strong>
              <small v-if="row.employeeNoSnapshot || row.employeeNo">工号 {{ row.employeeNoSnapshot || row.employeeNo }}</small>
              <small v-if="row.displayPath">{{ row.displayPath }}</small>
            </td>
            <td><code>{{ rowStableId(row) }}</code></td>
            <td>
              <code>{{ rowCanonicalId(row) || '未绑定' }}</code>
              <small v-if="rowLegacyId(row)">OA {{ rowLegacyId(row) }}</small>
              <small v-if="row.suggestedLegacyOaIds?.length">
                唯一工号候选 {{ row.suggestedLegacyOaIds.join('、') }}
              </small>
            </td>
            <td>{{ row.migrationDecision || row.status || 'PENDING' }}</td>
            <td>{{ row.affectedReferenceCount || 0 }}</td>
            <td><button type="button" @click="beginEdit(row)">处理</button></td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="6" class="migration-empty">{{ busy ? '加载中…' : '暂无记录' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <form v-if="editing" class="migration-editor" @submit.prevent="saveDecision">
      <div>
        <strong>处理 {{ editing.displayName || editing.name || editing.unitName || editing.identityName || rowStableId(editing) }}</strong>
        <span>Form 稳定 ID：{{ rowStableId(editing) }}</span>
      </div>
      <label>
        处置
        <select v-model="decision.migrationDecision" required>
          <option value="" disabled>请选择</option>
          <option v-for="value in availableDecisions" :key="value" :value="value">{{ value }}</option>
        </select>
      </label>
      <label>
        canonical key / 目标 key
        <input v-model.trim="decision.canonicalId" placeholder="NEW/ARCHIVE 可按后端规则留空" />
      </label>
      <label>
        历史 OA key
        <input v-model.trim="decision.legacyOaId" placeholder="绑定、合并或拆分时填写" />
      </label>
      <label>
        其他旧别名
        <input v-model.trim="decision.aliases" placeholder="多个 key 用 | 分隔" />
      </label>
      <label>
        备注
        <input v-model.trim="decision.note" placeholder="记录人工判断依据" />
      </label>
      <div class="migration-editor-actions">
        <button type="button" @click="editing = null">取消</button>
        <button type="submit" :disabled="busy">保存并留痕</button>
      </div>
    </form>

    <p v-if="message" class="migration-message">{{ message }}</p>
    <p v-if="error" class="migration-error">{{ error }}</p>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import {
  exportFormUnitMappings,
  getFormMigrationOverview,
  importFormUnitMappings,
  listFormMigrationBindings,
  updateFormMigrationDecision,
  validateFormMigration
} from '../../api/cloudnext';

const tabs = [
  { key: 'principals', label: 'Form 人员' },
  { key: 'units', label: 'Form 组织' },
  { key: 'identities', label: 'Form 任职' },
  { key: 'legacy/principals', label: '历史 OA 人员' },
  { key: 'legacy/units', label: '历史 OA 单位' },
  { key: 'legacy/identities', label: '历史 OA 身份' }
];
const activeKind = ref('principals');
const overview = ref({});
const validation = ref({ ready: false, blockers: [] });
const rows = ref([]);
const editing = ref(null);
const busy = ref(false);
const message = ref('');
const error = ref('');
const decision = reactive({ migrationDecision: '', canonicalId: '', legacyOaId: '', aliases: '', note: '' });

const resolvedCount = (counts = {}) => Object.entries(counts)
  .filter(([status]) => !['PENDING', 'UNRESOLVED'].includes(status))
  .reduce((total, [, count]) => total + Number(count || 0), 0);
const summaryCards = computed(() => [
  { label: '人员已处理', value: resolvedCount(overview.value.principalCounts) },
  { label: '组织已处理', value: resolvedCount(overview.value.unitCounts) },
  { label: '身份已处理', value: resolvedCount(overview.value.identityCounts) },
  { label: '阻断项', value: overview.value.blockerCount ?? validation.value.blockers?.length ?? 0 }
]);

const availableDecisions = computed(() => {
  if (activeKind.value === 'legacy/units') return ['REUSE', 'MERGE', 'SPLIT', 'ARCHIVE', 'REVOKED'];
  if (activeKind.value === 'legacy/principals') return ['TOMBSTONE', 'ARCHIVE', 'TRANSFER'];
  if (activeKind.value === 'legacy/identities') return ['ARCHIVE', 'MERGE', 'REVOKED'];
  if (activeKind.value === 'units') return ['REUSE', 'MERGE', 'SPLIT', 'ARCHIVE', 'NEW', 'REVOKED'];
  if (activeKind.value === 'principals') return ['CONFIRMED', 'BIND', 'TRANSFER', 'TOMBSTONE', 'NEW', 'REVOKED'];
  return ['CONFIRMED', 'REUSE', 'ARCHIVE', 'NEW', 'REVOKED'];
});

const rowStableId = (row) => {
  if (activeKind.value.startsWith('legacy/')) {
    return row?.oaId || rowLegacyId(row) || '';
  }
  return row?.formEmployeeId || row?.formOrgId || row?.formAssignmentId || row?.externalId || '';
};
const rowCanonicalId = (row) =>
  row?.canonicalPersonId || row?.canonicalUnitId || row?.canonicalIdentityId || row?.canonicalId || '';
const rowLegacyId = (row) =>
  row?.legacyOaPersonId || row?.legacyOaUnitId || row?.legacyOaIdentityId || row?.legacyOaId || '';

const normalizeRows = (value) => {
  if (Array.isArray(value)) return value;
  return value?.items || value?.content || [];
};

const loadRows = async () => {
  if (!activeKind.value.startsWith('legacy/')) {
    rows.value = normalizeRows(await listFormMigrationBindings(activeKind.value));
    return;
  }
  const bindingKind = activeKind.value.slice('legacy/'.length);
  const [legacyValue, bindingValue] = await Promise.all([
    listFormMigrationBindings(activeKind.value),
    listFormMigrationBindings(bindingKind)
  ]);
  const bindings = normalizeRows(bindingValue);
  const legacyField = bindingKind === 'principals'
    ? 'legacyOaPersonId'
    : bindingKind === 'units' ? 'legacyOaUnitId' : 'legacyOaIdentityId';
  const byLegacyId = new Map(bindings.filter((row) => row?.[legacyField]).map((row) => [row[legacyField], row]));
  rows.value = normalizeRows(legacyValue).map((row) => ({
    ...row,
    ...(byLegacyId.get(row.oaId) || {})
  }));
};

const loadAll = async () => {
  busy.value = true;
  error.value = '';
  try {
    const [nextOverview, nextValidation] = await Promise.all([
      getFormMigrationOverview(),
      validateFormMigration()
    ]);
    overview.value = nextOverview || {};
    validation.value = nextValidation || { ready: false, blockers: [] };
    await loadRows();
  } catch (e) {
    error.value = e?.message || '迁移映射加载失败';
  } finally {
    busy.value = false;
  }
};

const selectKind = async (kind) => {
  activeKind.value = kind;
  editing.value = null;
  busy.value = true;
  error.value = '';
  try {
    await loadRows();
  } catch (e) {
    error.value = e?.message || '映射列表加载失败';
  } finally {
    busy.value = false;
  }
};

const beginEdit = (row) => {
  editing.value = row;
  decision.migrationDecision = row.migrationDecision || '';
  decision.canonicalId = rowCanonicalId(row);
  decision.legacyOaId = rowLegacyId(row);
  decision.aliases = '';
  decision.note = row.note || '';
};

const saveDecision = async () => {
  if (!editing.value) return;
  busy.value = true;
  error.value = '';
  try {
    const inferredLegacyId = ['BIND', 'TRANSFER', 'REUSE', 'MERGE', 'SPLIT']
      .includes(decision.migrationDecision)
      ? decision.canonicalId
      : '';
    await updateFormMigrationDecision(activeKind.value, rowStableId(editing.value), {
      decision: decision.migrationDecision,
      canonicalKey: decision.canonicalId || null,
      legacyOaId: decision.legacyOaId || rowLegacyId(editing.value) || inferredLegacyId || null,
      aliases: decision.aliases.split('|').map((value) => value.trim()).filter(Boolean),
      note: decision.note || null
    });
    editing.value = null;
    message.value = '映射决策已保存。';
    await loadAll();
  } catch (e) {
    error.value = e?.message || '映射决策保存失败';
  } finally {
    busy.value = false;
  }
};

const runValidation = async () => {
  busy.value = true;
  error.value = '';
  try {
    validation.value = (await validateFormMigration()) || { ready: false, blockers: [] };
    message.value = validation.value.ready ? '切换校验通过。' : '校验完成，仍有阻断项。';
  } catch (e) {
    error.value = e?.message || '切换校验失败';
  } finally {
    busy.value = false;
  }
};

const onCsvSelected = async (event) => {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) return;
  busy.value = true;
  error.value = '';
  try {
    await importFormUnitMappings(file);
    message.value = '组织 crosswalk 已导入，请复核校验结果。';
    await loadAll();
  } catch (e) {
    error.value = e?.message || 'CSV 导入失败';
  } finally {
    busy.value = false;
  }
};

onMounted(loadAll);
</script>

<style scoped>
.migration-panel {
  display: grid;
  gap: 14px;
  padding: 16px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}
.migration-head,
.migration-actions,
.migration-tabs,
.migration-csv-actions,
.migration-editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.migration-head { justify-content: space-between; }
.migration-head h2 { margin: 0; font-size: 15px; }
.migration-head p { margin: 4px 0 0; font-size: 12px; color: var(--text-tertiary); }
button,
.migration-csv-actions label {
  border: 1px solid var(--border-color);
  border-radius: 7px;
  padding: 7px 10px;
  background: var(--bg-panel);
  color: var(--text-primary);
  cursor: pointer;
  font-size: 12px;
}
button:disabled { opacity: .5; cursor: not-allowed; }
.migration-summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.migration-summary > div { display: grid; gap: 4px; padding: 10px; background: var(--bg-panel-soft); border-radius: var(--radius-md); }
.migration-summary span { font-size: 11px; color: var(--text-tertiary); }
.migration-summary strong { font-size: 15px; }
.migration-summary .is-ready strong { color: #087a4b; }
.migration-summary .is-blocked strong { color: #b42318; }
.migration-blockers { padding: 10px 12px; background: var(--bg-danger-soft); border-radius: var(--radius-md); font-size: 12px; color: #b42318; }
.migration-blockers ul { margin: 6px 0 0; padding-left: 18px; }
.migration-tabs { border-bottom: 1px solid var(--border-color); }
.migration-tabs button { border: 0; border-radius: 0; }
.migration-tabs button.active { color: var(--bg-primary); border-bottom: 2px solid var(--bg-primary); }
.migration-csv-actions input { display: none; }
.migration-table-wrap { overflow: auto; }
.migration-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.migration-table th,
.migration-table td { padding: 9px 8px; border-bottom: 1px solid var(--border-color); text-align: left; vertical-align: top; }
.migration-table th { color: var(--text-tertiary); font-weight: 500; }
.migration-table td strong,
.migration-table td small { display: block; }
.migration-table td small { margin-top: 3px; color: var(--text-tertiary); }
.migration-table code { font-size: 11px; word-break: break-all; }
.migration-empty { text-align: center !important; color: var(--text-tertiary); }
.migration-editor { display: grid; grid-template-columns: minmax(180px, 1fr) repeat(2, minmax(160px, 1fr)); gap: 10px; padding: 12px; background: var(--bg-panel-soft); border-radius: var(--radius-md); }
.migration-editor > div:first-child,
.migration-editor label { display: grid; gap: 5px; font-size: 11px; color: var(--text-tertiary); }
.migration-editor input,
.migration-editor select { min-height: 34px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-panel); padding: 0 8px; color: var(--text-primary); }
.migration-editor-actions { grid-column: 1 / -1; justify-content: flex-end; }
.migration-message { margin: 0; color: #087a4b; font-size: 12px; }
.migration-error { margin: 0; color: #b42318; font-size: 12px; }
@media (max-width: 900px) {
  .migration-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .migration-head { align-items: flex-start; flex-direction: column; }
  .migration-editor { grid-template-columns: 1fr; }
}
</style>
