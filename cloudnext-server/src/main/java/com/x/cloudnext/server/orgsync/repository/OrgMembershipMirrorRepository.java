package com.x.cloudnext.server.orgsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgMembershipMirror;

public interface OrgMembershipMirrorRepository extends JpaRepository<OrgMembershipMirror, String> {
}
