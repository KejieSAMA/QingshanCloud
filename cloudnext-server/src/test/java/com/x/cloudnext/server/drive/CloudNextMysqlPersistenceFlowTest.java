package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.entity.AuditLog;
import com.x.cloudnext.server.audit.repository.AuditLogRepository;
import com.x.cloudnext.server.drive.entity.DriveItem;
import static org.junit.jupiter.api.Assertions.*;

class CloudNextMysqlPersistenceFlowTest extends AbstractDriveIntegrationTest {
    @Autowired AuditLogRepository audit;
    @Autowired PlatformTransactionManager transactions;

    @Test void chineseEmojiLongTextAndMicrosecondsRoundTrip() {
        DriveItem folder = seedFolder(DEFAULT_OWNER_ID, "", "中文资料📁 12");
        assertEquals("中文资料📁 12", driveItemRepository.findById(folder.getId()).orElseThrow().getName());
        AuditLog row = new AuditLog();
        row.setId(UUID.randomUUID().toString()); row.setAction(AuditAction.COPY);
        row.setPayload("中文🗂".repeat(20000));
        row.setCreatedAt(Instant.parse("2026-10-13T01:02:03.123456789Z"));
        audit.saveAndFlush(row); entityManager.clear();
        AuditLog loaded = audit.findById(row.getId()).orElseThrow();
        assertEquals(row.getPayload(), loaded.getPayload());
        assertEquals(Instant.parse("2026-10-13T01:02:03.123456Z"), loaded.getCreatedAt());
    }

    @Test void canonicalIdentifiersRemainCaseSensitive() {
        DriveItem upper = seedFolder(DEFAULT_OWNER_ID, "", "Upper");
        DriveItem lower = seedFolder(DEFAULT_OWNER_ID, "", "Lower");
        jdbcTemplate.update("update cloudnext_drive_item set id=? where id=?", "CaseSensitive", upper.getId());
        jdbcTemplate.update("update cloudnext_drive_item set id=? where id=?", "casesensitive", lower.getId());
        entityManager.clear();
        assertEquals("Upper", driveItemRepository.findById("CaseSensitive").orElseThrow().getName());
        assertEquals("Lower", driveItemRepository.findById("casesensitive").orElseThrow().getName());
    }

    @Test void failedTransactionDoesNotLeaveMetadata() {
        long before = driveItemRepository.count();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            seedFolder(DEFAULT_OWNER_ID, "", "will roll back");
            throw new IllegalStateException("simulated failure");
        }));
        entityManager.clear();
        assertEquals(before, driveItemRepository.count());
    }
}
