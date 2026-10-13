package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;

/**
 * 共享期限自动清理任务（N+2.4）。
 *
 * 每日凌晨 1 点扫描所有"有过期时间且已过期且未被撤销"的 share，批量标记
 * revoked=true，并写 SHARE_REVOKE audit（每条独立记录便于事后追溯）。
 *
 * 调度时间避开 OnlyOffice 健康探测（每分钟）和 trash cleanup（凌晨 3 点），
 * 落在低峰窗口。
 *
 * 出于运维考虑，每个 share 失败独立 catch 不影响其它；累计 succeeded/failed
 * 统计写一条聚合日志，方便排查异常清理。
 */
@Service
public class DriveShareCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveShareCleanupService.class);

    private final DriveShareRepository driveShareRepository;
    private final AuditLogService auditLogService;

    public DriveShareCleanupService(
            DriveShareRepository driveShareRepository,
            AuditLogService auditLogService) {
        this.driveShareRepository = driveShareRepository;
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "${cloudnext.drive.share-cleanup-cron:0 0 1 * * *}")
    @Transactional
    public void cleanupExpiredShares() {
        String nowIso = Instant.now().toString();
        List<DriveShare> expired = driveShareRepository.findExpiredActiveShares(nowIso);
        if (expired.isEmpty()) {
            return;
        }
        LOGGER.info("cloudnext share cleanup: {} expired shares to revoke", expired.size());
        int succeeded = 0;
        int failed = 0;
        for (DriveShare share : expired) {
            try {
                share.setRevoked(true);
                driveShareRepository.save(share);
                auditLogService.record(
                        "system",
                        AuditAction.SHARE_REVOKE,
                        "DRIVE_SHARE",
                        share.getId(),
                        "itemId", share.getItemId(),
                        "targetType", share.getTargetType() == null ? "" : share.getTargetType().name(),
                        "targetId", share.getTargetId(),
                        "expiresAt", share.getExpiresAt(),
                        "reason", "auto-cleanup-expired");
                succeeded++;
            } catch (RuntimeException e) {
                LOGGER.warn("cloudnext share cleanup failed for shareId={}: {}", share.getId(), e.getMessage());
                failed++;
            }
        }
        LOGGER.info("cloudnext share cleanup done: succeeded={}, failed={}", succeeded, failed);
    }
}
