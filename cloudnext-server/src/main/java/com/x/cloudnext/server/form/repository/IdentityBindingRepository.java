package com.x.cloudnext.server.form.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.form.entity.IdentityBinding;

public interface IdentityBindingRepository extends JpaRepository<IdentityBinding,String> {
    Optional<IdentityBinding> findByTenantIdAndFormAssignmentId(String tenantId,String assignmentId);
    List<IdentityBinding> findByFormAssignmentId(String assignmentId);
    List<IdentityBinding> findByCanonicalPersonIdAndStatus(String canonicalPersonId,String status);
    List<IdentityBinding> findByCanonicalIdentityId(String canonicalIdentityId);
    Optional<IdentityBinding> findByLegacyOaIdentityId(String legacyOaIdentityId);
    long countByStatus(String status);
    long countByMigrationDecision(String decision);
}
