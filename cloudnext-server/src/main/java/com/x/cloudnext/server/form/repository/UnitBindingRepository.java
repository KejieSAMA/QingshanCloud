package com.x.cloudnext.server.form.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.form.entity.UnitBinding;

public interface UnitBindingRepository extends JpaRepository<UnitBinding,String> {
    Optional<UnitBinding> findByTenantIdAndFormOrgId(String tenantId,String formOrgId);
    List<UnitBinding> findByFormOrgId(String formOrgId);
    List<UnitBinding> findByCanonicalUnitId(String canonicalUnitId);
    Optional<UnitBinding> findByLegacyOaUnitId(String legacyOaUnitId);
    long countByStatus(String status);
    long countByMigrationDecision(String decision);
}
