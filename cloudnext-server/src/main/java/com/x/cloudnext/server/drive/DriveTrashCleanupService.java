package com.x.cloudnext.server.drive;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DriveTrashCleanupService {
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.namespace.DriveNamespaceService namespaces;

    private final DriveService driveService;

    public DriveTrashCleanupService(DriveService driveService) {
        this.driveService = driveService;
    }

    @Scheduled(cron = "${cloudnext.drive.trash-cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredTrash() {
        namespaces.forEachMaintenanceDrive(driveService::cleanupExpiredTrash);
    }
}
