package com.x.cloudnext.server.workspace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.workspace.entity.UserPreference;

@org.springframework.transaction.annotation.Transactional(readOnly = true)
public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
    java.util.List<UserPreference> findByPersonOaId(String personOaId);

    Optional<UserPreference> findByPersonOaIdAndPreferenceKey(String personOaId, String preferenceKey);
}
