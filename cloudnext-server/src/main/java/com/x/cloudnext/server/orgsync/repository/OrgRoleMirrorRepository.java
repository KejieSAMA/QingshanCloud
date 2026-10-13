package com.x.cloudnext.server.orgsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgRoleMirror;

public interface OrgRoleMirrorRepository extends JpaRepository<OrgRoleMirror, String> {
}
