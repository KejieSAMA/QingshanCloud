package com.x.cloudnext.server.namespace;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DriveNamespaceRepository extends JpaRepository<DriveNamespace, String> {
    @Modifying
    @Query(value = "INSERT INTO cloudnext_drive_namespace (id, tenant_id, entry_id) VALUES (:id, :tenant, :entry) ON DUPLICATE KEY UPDATE id=id", nativeQuery = true)
    int register(@Param("id") String id, @Param("tenant") String tenant, @Param("entry") String entry);
}
