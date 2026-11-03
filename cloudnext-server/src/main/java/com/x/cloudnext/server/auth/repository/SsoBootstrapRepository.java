package com.x.cloudnext.server.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.x.cloudnext.server.auth.entity.SsoBootstrap;

public interface SsoBootstrapRepository extends JpaRepository<SsoBootstrap,String> {
    @org.springframework.data.jpa.repository.Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from SsoBootstrap b where b.tokenHash=:hash")
    java.util.Optional<SsoBootstrap> findForUpdate(@org.springframework.data.repository.query.Param("hash") String hash);
}
