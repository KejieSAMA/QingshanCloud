<template>
  <div v-if="open" class="dialog-mask" @click.self="onClose">
    <div
      class="dialog-panel members-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ws-members-title"
    >
      <div class="dialog-head">
        <h2 id="ws-members-title">成员管理</h2>
        <p>当前空间共 {{ members.length }} 名成员。</p>
      </div>

      <!-- 现有成员列表 -->
      <div class="members-section">
        <div class="members-section-head">
          <strong>已加入成员</strong>
          <span v-if="loading">加载中…</span>
        </div>
        <div v-if="!loading && !members.length" class="members-empty">暂无成员。</div>
        <div v-else class="members-list">
          <div v-for="m in members" :key="m.id" class="member-row">
            <span class="member-avatar">{{ (m.personName || '?').slice(0, 1) }}</span>
            <div class="member-copy">
              <strong>{{ m.personName }}</strong>
              <span class="member-meta">{{ m.employeeNo || memberPersonKey(m) }} · {{ formatRoleLabel(m.role) }}</span>
            </div>
            <div v-if="canManage" class="member-row-actions">
              <select
                class="member-role-select"
                :value="m.role"
                :disabled="!canChangeMember(m) || busyMemberId === m.id"
                @change="onChangeRole(m, $event.target.value)"
              >
                <option value="OWNER" :disabled="!canPromoteToOwner(m)">所有者</option>
                <option value="ADMIN">管理员</option>
                <option value="MEMBER">成员</option>
                <option value="VIEWER">只读</option>
              </select>
              <button
                class="ghost-button danger member-remove-btn"
                type="button"
                :disabled="!canRemoveMember(m) || busyMemberId === m.id"
                @click="onRemove(m)"
              >
                移除
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 添加成员（搜索 + 待添加 + 角色 + 添加） -->
      <section v-if="canManage" class="members-add-section">
        <div class="members-section-head">
          <strong>添加成员</strong>
          <span v-if="searchLoading">搜索中…</span>
        </div>

        <label class="dialog-field">
          <span>搜索人员</span>
          <input
            v-model.trim="searchKeyword"
            type="text"
            placeholder="输入姓名 / 工号 / 手机号"
            :disabled="adding"
          />
        </label>

        <div class="members-add-grid">
          <div class="members-search-results">
            <div class="members-section-subhead">
              <strong>搜索结果</strong>
            </div>
            <div class="members-result-list">
              <button
                v-for="target in searchResults"
                :key="target.id"
                class="members-result-item"
                type="button"
                :disabled="isAlreadyListed(target) || isAlreadySelected(target)"
                @click="onPickTarget(target)"
              >
                <span class="members-result-name">{{ target.name }}</span>
                <span class="members-result-meta">
                  {{ target.distinguishedName || target.id }}
                </span>
              </button>
              <div v-if="!searchResults.length" class="members-empty-text">
                {{ searchKeyword ? '未找到匹配的人员。' : '请输入关键字搜索。' }}
              </div>
            </div>
          </div>

          <div class="members-pending">
            <div class="members-section-subhead">
              <strong>待添加</strong>
              <span>{{ pendingTargets.length }} 个</span>
            </div>
            <div class="members-pending-list">
              <div
                v-for="target in pendingTargets"
                :key="target.id"
                class="members-pending-chip"
              >
                <div class="members-pending-copy">
                  <strong>{{ target.name }}</strong>
                  <span>{{ target.distinguishedName || target.id }}</span>
                </div>
                <button
                  class="members-pending-remove"
                  type="button"
                  @click="onUnpickTarget(target)"
                >移除</button>
              </div>
              <span v-if="!pendingTargets.length" class="members-empty-text">未选择人员</span>
            </div>
          </div>
        </div>

        <div class="members-add-footer">
          <label class="members-role-field">
            <span>角色</span>
            <select v-model="addRole" :disabled="adding">
              <option value="MEMBER">成员</option>
              <option value="VIEWER">只读</option>
              <option value="ADMIN">管理员</option>
              <option v-if="myRole === 'OWNER'" value="OWNER">所有者</option>
            </select>
          </label>
          <button
            class="primary-button"
            type="button"
            :disabled="adding || !pendingTargets.length"
            @click="onConfirmAdd"
          >
            {{ adding ? '添加中…' : `添加成员（${pendingTargets.length}）` }}
          </button>
        </div>
      </section>

      <div v-if="errorText" class="dialog-error">{{ errorText }}</div>

      <div class="dialog-actions">
        <button class="secondary-button" type="button" @click="onClose">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import {
  addWorkspaceMember,
  isAbortError,
  listWorkspaceMembers,
  removeWorkspaceMember,
  searchOrgTargets,
  updateWorkspaceMember
} from '../../api/cloudnext';
import {
  canChangeMemberRole,
  canManageMembers as roleCanManageMembers,
  canPromoteToOwner as roleCanPromoteToOwner,
  canRemoveMember as roleCanRemoveMember,
  formatRoleLabel as roleFormatRoleLabel
} from '../../composables/drive/workspacePermissions';

const props = defineProps({
  workspaceId: { type: String, required: true },
  myRole: { type: String, default: '' },
  myPersonKey: { type: String, default: '' },
  open: { type: Boolean, default: false }
});
const emit = defineEmits(['close', 'changed']);

const members = ref([]);
const loading = ref(false);
const errorText = ref('');
const busyMemberId = ref('');
const adding = ref(false);

const searchKeyword = ref('');
const searchResults = ref([]);
const searchLoading = ref(false);
let searchSeq = 0;
let searchTimer = null;

const pendingTargets = ref([]);
const addRole = ref('MEMBER');

const canManage = computed(() => roleCanManageMembers(props.myRole));

const formatRoleLabel = (role) => roleFormatRoleLabel(role);
const memberPersonKey = (member) => member?.personKey || member?.personOaId || '';
const targetPersonKey = (target) => target?.personKey || target?.id || '';

const canChangeMember = (m) =>
  canChangeMemberRole({ myRole: props.myRole, targetRole: m.role });

const canPromoteToOwner = (m) =>
  roleCanPromoteToOwner({ myRole: props.myRole, targetRole: m.role });

const canRemoveMember = (m) =>
  roleCanRemoveMember({
    myRole: props.myRole,
    targetRole: m.role,
    targetPersonKey: memberPersonKey(m),
    myPersonKey: props.myPersonKey
  });

const reload = async () => {
  loading.value = true;
  errorText.value = '';
  try {
    const list = await listWorkspaceMembers(props.workspaceId);
    members.value = Array.isArray(list) ? list : [];
  } catch (error) {
    errorText.value = error?.message || '加载成员失败';
    members.value = [];
  } finally {
    loading.value = false;
  }
};

const isAlreadyListed = (target) =>
  members.value.some((m) => memberPersonKey(m) === targetPersonKey(target));

const isAlreadySelected = (target) =>
  pendingTargets.value.some((t) => t.id === target.id);

const onPickTarget = (target) => {
  if (isAlreadyListed(target) || isAlreadySelected(target)) return;
  pendingTargets.value = [...pendingTargets.value, target];
};

const onUnpickTarget = (target) => {
  pendingTargets.value = pendingTargets.value.filter((t) => t.id !== target.id);
};

const runSearch = async (keyword) => {
  const seq = ++searchSeq;
  searchLoading.value = true;
  try {
    const list = await searchOrgTargets('person', keyword);
    if (seq !== searchSeq) return;
    searchResults.value = Array.isArray(list) ? list : [];
  } catch (error) {
    if (isAbortError(error) || seq !== searchSeq) return;
    searchResults.value = [];
    errorText.value = error?.message || '搜索失败';
  } finally {
    if (seq === searchSeq) searchLoading.value = false;
  }
};

watch(searchKeyword, (next) => {
  if (searchTimer) {
    window.clearTimeout(searchTimer);
    searchTimer = null;
  }
  if (!next) {
    searchResults.value = [];
    return;
  }
  searchTimer = window.setTimeout(() => {
    void runSearch(next);
  }, 220);
});

watch(
  () => props.open,
  (next) => {
    if (next) {
      reload();
    } else {
      members.value = [];
      errorText.value = '';
      searchKeyword.value = '';
      searchResults.value = [];
      pendingTargets.value = [];
      addRole.value = 'MEMBER';
    }
  }
);

const onClose = () => emit('close');

const onChangeRole = async (member, nextRole) => {
  if (member.role === nextRole) return;
  busyMemberId.value = member.id;
  errorText.value = '';
  try {
    await updateWorkspaceMember(props.workspaceId, member.id, { role: nextRole });
    await reload();
    emit('changed');
  } catch (error) {
    errorText.value = error?.message || '修改角色失败';
  } finally {
    busyMemberId.value = '';
  }
};

const onRemove = async (member) => {
  if (!window.confirm(`确认移除成员 ${member.personName || member.employeeNo || memberPersonKey(member)}?`)) return;
  busyMemberId.value = member.id;
  errorText.value = '';
  try {
    await removeWorkspaceMember(props.workspaceId, member.id);
    await reload();
    emit('changed');
  } catch (error) {
    errorText.value = error?.message || '移除成员失败';
  } finally {
    busyMemberId.value = '';
  }
};

const onConfirmAdd = async () => {
  if (!pendingTargets.value.length) return;
  adding.value = true;
  errorText.value = '';
  const failures = [];
  for (const target of pendingTargets.value) {
    try {
      await addWorkspaceMember(props.workspaceId, {
        personKey: targetPersonKey(target),
        personOaId: targetPersonKey(target),
        employeeNo: target.employeeNo || '',
        personName: target.name || targetPersonKey(target),
        role: addRole.value
      });
    } catch (error) {
      failures.push(`${target.name || target.id}: ${error?.message || '添加失败'}`);
    }
  }
  pendingTargets.value = [];
  searchKeyword.value = '';
  searchResults.value = [];
  if (failures.length) {
    errorText.value = `部分成员添加失败:\n${failures.join('\n')}`;
  }
  adding.value = false;
  await reload();
  emit('changed');
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
  width: min(720px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  display: grid;
  gap: 18px;
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

.members-section,
.members-add-section {
  display: grid;
  gap: 10px;
}

.members-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-primary);
}

.members-section-head span {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 400;
}

.members-section-subhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 6px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.members-section-subhead strong {
  color: var(--text-secondary);
  font-weight: 600;
}

.members-list {
  display: grid;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

.member-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-panel);
}

.member-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--bg-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.member-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.member-copy strong {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-meta {
  color: var(--text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-row-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.member-role-select {
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-panel);
  color: var(--text-primary);
  font-size: 13px;
}

.member-remove-btn {
  height: 30px;
  padding: 0 12px;
  border-radius: 6px;
}

.members-empty,
.members-empty-text {
  padding: 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.dialog-field {
  display: grid;
  gap: 6px;
}

.dialog-field span {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.dialog-field input {
  width: 100%;
  height: 36px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 0 12px;
  color: var(--text-primary);
  background: var(--bg-panel);
  font-size: 13px;
}

.members-add-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.members-search-results,
.members-pending {
  display: grid;
  gap: 6px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 10px;
  background: var(--bg-panel-soft, var(--bg-panel));
}

.members-result-list {
  display: grid;
  gap: 4px;
  max-height: 180px;
  overflow: auto;
}

.members-result-item {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: var(--bg-panel);
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.members-result-item:hover:not(:disabled) {
  background: rgba(47, 107, 255, 0.08);
  border-color: var(--primary-color);
}

.members-result-item:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.members-result-name {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-result-meta {
  color: var(--text-tertiary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-pending-list {
  display: grid;
  gap: 4px;
  max-height: 180px;
  overflow: auto;
}

.members-pending-chip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: rgba(47, 107, 255, 0.06);
  border: 1px solid rgba(47, 107, 255, 0.2);
}

.members-pending-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.members-pending-copy strong {
  color: var(--text-primary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-pending-copy span {
  color: var(--text-tertiary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.members-pending-remove {
  background: none;
  border: none;
  color: var(--danger-color, #d33);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
}

.members-pending-remove:hover {
  text-decoration: underline;
}

.members-add-footer {
  display: flex;
  align-items: end;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.members-role-field {
  display: grid;
  gap: 6px;
  min-width: 140px;
}

.members-role-field span {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.members-role-field select {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  color: var(--text-primary);
  font-size: 13px;
}

.dialog-error {
  color: var(--danger-color, #d33);
  font-size: 13px;
  white-space: pre-wrap;
}

.ghost-button.danger {
  color: var(--danger-color, #d33);
}

.dialog-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 720px) {
  .members-add-grid {
    grid-template-columns: 1fr;
  }
  .member-row {
    grid-template-columns: 32px minmax(0, 1fr);
  }
  .member-row-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}

/* === 弹窗手机端紧凑化（≤559px） === */
@media (max-width: 559px) {
  .dialog-mask {
    padding: 12px;
    align-items: center;
  }

  .dialog-panel,
  .members-dialog {
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

  .members-list,
  .member-search-results {
    max-height: 50vh;
  }
}
</style>
