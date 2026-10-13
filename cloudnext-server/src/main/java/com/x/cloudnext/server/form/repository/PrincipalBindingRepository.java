package com.x.cloudnext.server.form.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.form.entity.PrincipalBinding;

public interface PrincipalBindingRepository extends JpaRepository<PrincipalBinding,String> {
    Optional<PrincipalBinding> findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId(String provider,String tenantId,String employeeId);
    boolean existsByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId(String provider,String tenantId,String employeeId);
    Optional<PrincipalBinding> findByCanonicalPersonId(String canonicalPersonId);
    List<PrincipalBinding> findByFormEmployeeId(String formEmployeeId);
    List<PrincipalBinding> findByEmployeeNoSnapshotIgnoreCase(String employeeNo);
    Optional<PrincipalBinding> findByLegacyOaPersonId(String legacyOaPersonId);
    long countByStatus(String status);
    long countByMigrationDecision(String decision);
}
