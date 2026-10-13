package com.x.cloudnext.server.orgsync.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.orgsync.entity.OrgIdentityMirror;

public interface OrgIdentityMirrorRepository extends JpaRepository<OrgIdentityMirror, String> {
    List<OrgIdentityMirror> findByPersonOaIdAndUnitOaId(String personOaId,String unitOaId);
}
