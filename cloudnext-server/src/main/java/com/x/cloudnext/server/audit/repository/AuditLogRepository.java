package com.x.cloudnext.server.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByTargetTypeAndTargetIdInOrderByCreatedAtDesc(String targetType, java.util.Collection<String> targetIds);

    Page<AuditLog> findByPersonOaIdOrderByCreatedAtDesc(String personOaId, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);

    List<AuditLog> findByActionAndCreatedAtAfterOrderByCreatedAtDesc(AuditAction action, Instant since);

    /**
     * Admin 审计面板复合过滤查询（N+2.2）。
     * 任何参数为 null 都视为"不过滤"；时间范围 [since, until) 半开区间。
     * 排序固定 createdAt DESC（与索引 idx_audit_action_created / idx_audit_person_created 对齐）。
     */
    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:person IS NULL OR a.personOaId = :person) AND "
            + "(:action IS NULL OR a.action = :action) AND "
            + "(:since IS NULL OR a.createdAt >= :since) AND "
            + "(:until IS NULL OR a.createdAt < :until) "
            + "ORDER BY a.createdAt DESC")
    Page<AuditLog> queryForAdmin(
            @Param("person") String person,
            @Param("action") AuditAction action,
            @Param("since") Instant since,
            @Param("until") Instant until,
            Pageable pageable);
}
