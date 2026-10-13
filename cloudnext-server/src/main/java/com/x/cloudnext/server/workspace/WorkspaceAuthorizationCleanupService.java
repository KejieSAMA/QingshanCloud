package com.x.cloudnext.server.workspace;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DrivePublicLink;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;

@Service
public class WorkspaceAuthorizationCleanupService {

    private final DriveItemRepository driveItemRepository;
    private final DriveShareRepository driveShareRepository;
    private final DrivePublicLinkRepository drivePublicLinkRepository;

    public WorkspaceAuthorizationCleanupService(
            DriveItemRepository driveItemRepository,
            DriveShareRepository driveShareRepository,
            DrivePublicLinkRepository drivePublicLinkRepository) {
        this.driveItemRepository = driveItemRepository;
        this.driveShareRepository = driveShareRepository;
        this.drivePublicLinkRepository = drivePublicLinkRepository;
    }

    @Transactional
    public void revokeWorkspaceGrantsCreatedBy(String workspaceId, String personOaId) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(personOaId)) {
            return;
        }
        List<String> itemIds = driveItemRepository.findByScopeTypeAndScopeId(
                        DriveScopeType.WORKSPACE,
                        workspaceId.trim())
                .stream()
                .map(DriveItem::getId)
                .collect(Collectors.toList());
        if (itemIds.isEmpty()) {
            return;
        }

        List<DriveShare> shares = driveShareRepository
                .findByItemIdInAndOwnerPersonOaIdAndRevokedFalse(itemIds, personOaId.trim());
        for (DriveShare share : shares) {
            share.setRevoked(true);
        }
        if (!shares.isEmpty()) {
            driveShareRepository.saveAll(shares);
        }

        List<DrivePublicLink> links = drivePublicLinkRepository
                .findByItemIdInAndCreatorPersonOaIdAndRevokedFalse(itemIds, personOaId.trim());
        for (DrivePublicLink link : links) {
            link.setRevoked(true);
        }
        if (!links.isEmpty()) {
            drivePublicLinkRepository.saveAll(links);
        }
    }
}
