import { describe, expect, it } from 'vitest';
import {
  canDeleteWorkspace,
  canChangeMemberRole,
  canManageMembers,
  canPromoteToOwner,
  canRenameWorkspace,
  canRemoveMember,
  canWriteInWorkspace,
  formatRoleLabel
} from '../composables/drive/workspacePermissions';

describe('workspacePermissions.canWriteInWorkspace', () => {
  it('grants write access to OWNER, ADMIN, MEMBER', () => {
    expect(canWriteInWorkspace('OWNER')).toBe(true);
    expect(canWriteInWorkspace('ADMIN')).toBe(true);
    expect(canWriteInWorkspace('MEMBER')).toBe(true);
  });

  it('denies write access to VIEWER and unknown roles (Requirement 4.2)', () => {
    expect(canWriteInWorkspace('VIEWER')).toBe(false);
    expect(canWriteInWorkspace('GUEST')).toBe(false);
    expect(canWriteInWorkspace(null)).toBe(false);
    expect(canWriteInWorkspace(undefined)).toBe(false);
    expect(canWriteInWorkspace('')).toBe(false);
  });
});

describe('workspacePermissions.canManageMembers', () => {
  it('only OWNER and ADMIN can manage members (Requirement 4.4)', () => {
    expect(canManageMembers('OWNER')).toBe(true);
    expect(canManageMembers('ADMIN')).toBe(true);
    expect(canManageMembers('MEMBER')).toBe(false);
    expect(canManageMembers('VIEWER')).toBe(false);
    expect(canManageMembers(null)).toBe(false);
    expect(canManageMembers(undefined)).toBe(false);
  });
});

describe('workspacePermissions.canRenameWorkspace', () => {
  it('allows OWNER and ADMIN to rename the workspace', () => {
    expect(canRenameWorkspace('OWNER')).toBe(true);
    expect(canRenameWorkspace('ADMIN')).toBe(true);
  });

  it('denies MEMBER, VIEWER and unknown roles', () => {
    expect(canRenameWorkspace('MEMBER')).toBe(false);
    expect(canRenameWorkspace('VIEWER')).toBe(false);
    expect(canRenameWorkspace('GUEST')).toBe(false);
    expect(canRenameWorkspace(null)).toBe(false);
  });
});

describe('workspacePermissions.canDeleteWorkspace', () => {
  it('allows only OWNER to delete/archive the workspace', () => {
    expect(canDeleteWorkspace('OWNER')).toBe(true);
    expect(canDeleteWorkspace('ADMIN')).toBe(false);
    expect(canDeleteWorkspace('MEMBER')).toBe(false);
    expect(canDeleteWorkspace('VIEWER')).toBe(false);
    expect(canDeleteWorkspace(undefined)).toBe(false);
  });
});

describe('workspacePermissions.canChangeMemberRole', () => {
  it('admin cannot change an owner (Requirement 4.5)', () => {
    expect(canChangeMemberRole({ myRole: 'ADMIN', targetRole: 'OWNER' })).toBe(false);
  });

  it('admin can change non-owner members', () => {
    expect(canChangeMemberRole({ myRole: 'ADMIN', targetRole: 'ADMIN' })).toBe(true);
    expect(canChangeMemberRole({ myRole: 'ADMIN', targetRole: 'MEMBER' })).toBe(true);
    expect(canChangeMemberRole({ myRole: 'ADMIN', targetRole: 'VIEWER' })).toBe(true);
  });

  it('owner can change anyone (Requirement 4.6)', () => {
    expect(canChangeMemberRole({ myRole: 'OWNER', targetRole: 'OWNER' })).toBe(true);
    expect(canChangeMemberRole({ myRole: 'OWNER', targetRole: 'ADMIN' })).toBe(true);
    expect(canChangeMemberRole({ myRole: 'OWNER', targetRole: 'MEMBER' })).toBe(true);
    expect(canChangeMemberRole({ myRole: 'OWNER', targetRole: 'VIEWER' })).toBe(true);
  });

  it('member or viewer cannot change anyone (Requirement 4.4, 5.6)', () => {
    expect(canChangeMemberRole({ myRole: 'MEMBER', targetRole: 'MEMBER' })).toBe(false);
    expect(canChangeMemberRole({ myRole: 'MEMBER', targetRole: 'VIEWER' })).toBe(false);
    expect(canChangeMemberRole({ myRole: 'VIEWER', targetRole: 'MEMBER' })).toBe(false);
    expect(canChangeMemberRole({ myRole: '', targetRole: 'MEMBER' })).toBe(false);
  });
});

describe('workspacePermissions.canPromoteToOwner', () => {
  it('only owner may promote a non-owner to OWNER (Requirement 4.5)', () => {
    expect(canPromoteToOwner({ myRole: 'OWNER', targetRole: 'ADMIN' })).toBe(true);
    expect(canPromoteToOwner({ myRole: 'OWNER', targetRole: 'MEMBER' })).toBe(true);
    expect(canPromoteToOwner({ myRole: 'OWNER', targetRole: 'VIEWER' })).toBe(true);
  });

  it('owner cannot "promote" an existing owner', () => {
    expect(canPromoteToOwner({ myRole: 'OWNER', targetRole: 'OWNER' })).toBe(false);
  });

  it('non-owners cannot promote to OWNER', () => {
    expect(canPromoteToOwner({ myRole: 'ADMIN', targetRole: 'MEMBER' })).toBe(false);
    expect(canPromoteToOwner({ myRole: 'MEMBER', targetRole: 'MEMBER' })).toBe(false);
    expect(canPromoteToOwner({ myRole: 'VIEWER', targetRole: 'MEMBER' })).toBe(false);
  });
});

describe('workspacePermissions.canRemoveMember', () => {
  it('admin cannot remove an OWNER (Requirement 4.5)', () => {
    expect(
      canRemoveMember({
        myRole: 'ADMIN',
        targetRole: 'OWNER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(false);
  });

  it('cannot remove yourself from the dialog', () => {
    expect(
      canRemoveMember({
        myRole: 'OWNER',
        targetRole: 'OWNER',
        targetPersonOaId: 'me',
        myPersonOaId: 'me'
      })
    ).toBe(false);
    expect(
      canRemoveMember({
        myRole: 'ADMIN',
        targetRole: 'ADMIN',
        targetPersonOaId: 'me',
        myPersonOaId: 'me'
      })
    ).toBe(false);
  });

  it('member or viewer cannot remove anyone (Requirement 4.4, 5.6)', () => {
    expect(
      canRemoveMember({
        myRole: 'MEMBER',
        targetRole: 'VIEWER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(false);
    expect(
      canRemoveMember({
        myRole: 'VIEWER',
        targetRole: 'MEMBER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(false);
  });

  it('admin can remove non-owner members other than self', () => {
    expect(
      canRemoveMember({
        myRole: 'ADMIN',
        targetRole: 'MEMBER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(true);
    expect(
      canRemoveMember({
        myRole: 'ADMIN',
        targetRole: 'VIEWER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(true);
  });

  it('owner can remove any non-self member (including other owners)', () => {
    expect(
      canRemoveMember({
        myRole: 'OWNER',
        targetRole: 'OWNER',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(true);
    expect(
      canRemoveMember({
        myRole: 'OWNER',
        targetRole: 'ADMIN',
        targetPersonOaId: 'p1',
        myPersonOaId: 'p2'
      })
    ).toBe(true);
  });
});

describe('workspacePermissions.formatRoleLabel', () => {
  it('translates the four documented roles', () => {
    expect(formatRoleLabel('OWNER')).toBe('所有者');
    expect(formatRoleLabel('ADMIN')).toBe('管理员');
    expect(formatRoleLabel('MEMBER')).toBe('成员');
    expect(formatRoleLabel('VIEWER')).toBe('只读');
  });

  it('falls back to "--" for unknown / falsy roles', () => {
    expect(formatRoleLabel('GUEST')).toBe('--');
    expect(formatRoleLabel('')).toBe('--');
    expect(formatRoleLabel(null)).toBe('--');
    expect(formatRoleLabel(undefined)).toBe('--');
  });
});
