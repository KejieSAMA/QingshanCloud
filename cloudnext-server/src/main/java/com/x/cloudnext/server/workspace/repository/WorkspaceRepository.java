package com.x.cloudnext.server.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.workspace.entity.Workspace;

/**
 * Spring Data repository for {@link Workspace}. Iteration one only needs the
 * stock {@code JpaRepository} contract (findById / save / delete); the listing
 * page goes through {@code WorkspaceMemberRepository.findByPersonOaId(...)}
 * because it must be filtered by membership.
 */
public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
}
