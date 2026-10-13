package com.x.cloudnext.server.drive;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DriveTrashCleanupService {

    private final DriveService driveService;

    public DriveTrashCleanupService(DriveService driveService) {
        this.driveService = driveService;
    }

    @Scheduled(cron = "${cloudnext.drive.trash-cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredTrash() {
        driveService.cleanupExpiredTrash();
    }
}
