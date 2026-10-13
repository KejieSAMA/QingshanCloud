package com.x.cloudnext.server.orgsync.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgUnitMirror;

public interface OrgUnitMirrorRepository extends JpaRepository<OrgUnitMirror, String> {
    List<OrgUnitMirror> findByNameIgnoreCase(String name);
}
