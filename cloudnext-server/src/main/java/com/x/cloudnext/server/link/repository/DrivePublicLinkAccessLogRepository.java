package com.x.cloudnext.server.link.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.x.cloudnext.server.link.entity.DrivePublicLinkAccessLog;

public interface DrivePublicLinkAccessLogRepository
        extends JpaRepository<DrivePublicLinkAccessLog, String> {

    /** owner 端"访问统计"主查询：按 token 倒序拉取最近访问。 */
    Page<DrivePublicLinkAccessLog> findByTokenOrderByAttemptedAtDesc(String token, Pageable pageable);

    /** 统计成功 / 失败 数量（owner 端面板顶部 chip 显示） */
    long countByToken(String token);

    long countByTokenAndSuccess(String token, boolean success);
}
