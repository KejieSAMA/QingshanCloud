package com.x.cloudnext.server.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.auth.entity.SsoBootstrap;

public interface SsoBootstrapRepository extends JpaRepository<SsoBootstrap,String> {
}
