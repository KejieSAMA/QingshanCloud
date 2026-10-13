package com.x.cloudnext.server.workspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.drive.WorkspaceRole;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;

/**
 * Spring Data repository for {@link WorkspaceMember}.
 *
 * <p>Method names map directly to the access-gateway / WorkspaceService
 * lookups described in design §"WorkspaceService":
 * <ul>
 *   <li>{@link #findByWorkspaceId(String)} — list members of a workspace.</li>
 *   <li>{@link #findByPersonOaId(String)} — list workspaces a person belongs
 *       to (used by both list-my-workspaces and the search "workspace" scope).</li>
 *   <li>{@link #findByWorkspaceIdAndPersonOaId(String, String)} — resolve the
 *       caller's role for {@code DriveScopeAccessService.resolveWorkspaceRole}.</li>
 *   <li>{@link #countByWorkspaceIdAndRole(String, WorkspaceRole)} — owner
 *       conservation guard: any change that would drop the OWNER count to 0
 *       must be rejected (task 1.6).</li>
 * </ul>
 */
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {

    List<WorkspaceMember> findByWorkspaceId(String workspaceId);

    List<WorkspaceMember> findByPersonOaId(String personOaId);

    Optional<WorkspaceMember> findByWorkspaceIdAndPersonOaId(String workspaceId, String personOaId);

    long countByWorkspaceIdAndRole(String workspaceId, WorkspaceRole role);
}
