package com.x.cloudnext.server.orgsync.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgPersonMirror;

public interface OrgPersonMirrorRepository extends JpaRepository<OrgPersonMirror, String> {
    List<OrgPersonMirror> findByEmployeeNoIgnoreCase(String employeeNo);
}
