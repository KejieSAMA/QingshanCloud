package com.x.cloudnext.server.migration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.migration.entity.CloudNextMeta;

public interface CloudNextMetaRepository extends JpaRepository<CloudNextMeta, String> {

    Optional<CloudNextMeta> findByMetaKey(String metaKey);
}
