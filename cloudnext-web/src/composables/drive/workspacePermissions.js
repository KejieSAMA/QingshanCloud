/**
 * Workspace role permission helpers.
 *
 * Pure JS module so it can be unit-tested in vitest's default node environment
 * without `@vue/test-utils` or jsdom. Both `WorkspaceDetailPage`,
 * `WorkspacesPage` and `WorkspaceMembersDialog` import from here so the
 * rendered UI behavior is governed by the same predicates under test.
 *
 * Validates: Requirements 4.2, 4.3, 4.4, 4.5, 4.6, 5.5, 5.6
 */

export const WORKSPACE_ROLE_LABELS = Object.freeze({
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
  VIEWER: '只读'
});

/** Translate a workspace role enum value into a Chinese label. */
export function formatRoleLabel(role) {
  if (role && Object.prototype.hasOwnProperty.call(WORKSPACE_ROLE_LABELS, role)) {
    return WORKSPACE_ROLE_LABELS[role];
  }
  return '--';
}

/**
 * Whether the caller can write inside the workspace
 * (upload, create folder, delete to workspace trash).
 *
 * Validates: Requirement 4.2 (viewer cannot write), 4.3 (member can write),
 * 4.6 (owner can do anything).
 */
export function canWriteInWorkspace(role) {
  return role === 'OWNER' || role === 'ADMIN' || role === 'MEMBER';
}

/**
 * Whether the caller can manage members at all (open the dialog, see the list,
 * add/remove members in general).
 *
 * Validates: Requirement 4.4 (admin/owner only).
 */
export function canManageMembers(role) {
  return role === 'OWNER' || role === 'ADMIN';
}

/** Whether the caller can rename the workspace itself. */
export function canRenameWorkspace(role) {
  return role === 'OWNER' || role === 'ADMIN';
}

/** Whether the caller can archive/delete the workspace itself. */
export function canDeleteWorkspace(role) {
  return role === 'OWNER';
}

/**
 * Whether the caller can change a target member's role.
 *
 * Validates: Requirement 4.5 (admin cannot touch owner).
 */
export function canChangeMemberRole({ myRole, targetRole } = {}) {
  if (!canManageMembers(myRole)) return false;
  if (myRole === 'ADMIN' && targetRole === 'OWNER') return false;
  return true;
}

/**
 * Whether the caller can promote the target member to OWNER.
 *
 * Validates: Requirement 4.5 (admin cannot create owner; owner is the only
 * actor that can hand over ownership).
 */
export function canPromoteToOwner({ myRole, targetRole } = {}) {
  return myRole === 'OWNER' && targetRole !== 'OWNER';
}

/**
 * Whether the caller can remove the target member.
 *
 * Validates: Requirement 4.5 (admin cannot remove owner) plus the ergonomic
 * rule that you cannot remove yourself from the dialog (the backend's
 * "owner must remain >= 1" guard is enforced server-side and is not
 * duplicated here).
 */
export function canRemoveMember({
  myRole,
  targetRole,
  targetPersonKey,
  myPersonKey,
  targetPersonOaId,
  myPersonOaId
} = {}) {
  if (!canManageMembers(myRole)) return false;
  if (myRole === 'ADMIN' && targetRole === 'OWNER') return false;
  const targetKey = targetPersonKey || targetPersonOaId || '';
  const callerKey = myPersonKey || myPersonOaId || '';
  if (targetKey && callerKey && targetKey === callerKey) return false;
  return true;
}
