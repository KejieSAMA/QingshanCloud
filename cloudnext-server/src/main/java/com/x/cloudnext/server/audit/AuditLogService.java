package com.x.cloudnext.server.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.audit.entity.AuditLog;
import com.x.cloudnext.server.audit.repository.AuditLogRepository;

/**
 * 审计日志写入入口。
 *
 * 设计取舍：
 * - 当前版本同步写入。性能开销可接受（每个写操作多一条 insert），换得"调用方一定能看到落盘失败异常"的强一致语义；
 * - 后续若 audit 量级显著上升（>1000 行/秒），改为 @Async + 队列即可，调用点签名不变；
 * - IP / UA 暂不采集（service 层无 request 上下文）。N+2 阶段在 controller 层用 @Aspect 注入。
 */
@Service
public class AuditLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一条审计日志。任何调用点不应让 audit 写失败阻塞业务 —— 内部捕获异常仅打 warn。
     *
     * @param personOaId 操作者 OA id；系统行为可传 "system"
     * @param action 行为枚举
     * @param targetType 目标实体类型字符串（DRIVE_ITEM / LINK / ...）
     * @param targetId 目标 id；可为空
     * @param payload 额外字段 map；可为 null
     */
    public void record(String personOaId, AuditAction action, String targetType, String targetId, Map<String, Object> payload) {
        if (action == null) {
            return;
        }
        try {
            AuditLog log = new AuditLog();
            log.setId(UUID.randomUUID().toString());
            log.setPersonOaId(personOaId == null ? "" : personOaId);
            log.setAction(action);
            log.setTargetType(targetType == null ? "" : targetType);
            log.setTargetId(targetId == null ? "" : targetId);
            log.setPayload(serialize(payload));
            log.setCreatedAt(Instant.now());
            auditLogRepository.save(log);
        } catch (RuntimeException e) {
            LOGGER.warn("cloudnext audit log persistence failed: action={}, target={}/{}", action, targetType, targetId, e);
        }
    }

    /**
     * 便捷重载：payload 为 key/value 数组（偶数个），自动组装成 Map。
     * 例：record(person, EDIT_COMPLETED, "DRIVE_ITEM", id, "size", 12345, "duration", 2300)
     */
    public void record(String personOaId, AuditAction action, String targetType, String targetId, Object... kv) {
        record(personOaId, action, targetType, targetId, toMap(kv));
    }

    /**
     * N+2.7 F1: 异步记录审计日志。
     *
     * 仅高频读路径（如 contentItem 预览取内容）应使用此方法以避免阻塞业务线程；
     * 低频写路径（upload/delete/edit-completed 等）保持调用同步 {@link #record}。
     *
     * 线程池满（队列 200 满）时回退到调用方线程同步写入（CallerRunsPolicy），
     * 保证审计 best-effort 不丢失。
     */
    @Async(AuditAsyncConfig.AUDIT_TASK_EXECUTOR)
    public void recordAsync(String personOaId, AuditAction action, String targetType, String targetId, Object... kv) {
        record(personOaId, action, targetType, targetId, toMap(kv));
    }

    private Map<String, Object> toMap(Object... kv) {
        if (kv == null || kv.length == 0) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            if (key == null) {
                continue;
            }
            map.put(String.valueOf(key), kv[i + 1]);
        }
        return map;
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOGGER.warn("cloudnext audit log payload serialize failed", e);
            return null;
        }
    }
}
