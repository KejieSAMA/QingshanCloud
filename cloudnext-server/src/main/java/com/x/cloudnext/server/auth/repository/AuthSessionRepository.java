package com.x.cloudnext.server.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.auth.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    Optional<AuthSession> findByToken(String token);
}
