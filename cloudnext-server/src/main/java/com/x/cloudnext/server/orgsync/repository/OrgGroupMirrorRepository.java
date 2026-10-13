package com.x.cloudnext.server.orgsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgGroupMirror;

public interface OrgGroupMirrorRepository extends JpaRepository<OrgGroupMirror, String> {
}
