package com.x.cloudnext.server.drive;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

@Service
public class DriveAccessService {

    private final AuthSessionService authSessionService;
    private final DriveItemRepository driveItemRepository;

    public DriveAccessService(AuthSessionService authSessionService, DriveItemRepository driveItemRepository) {
        this.authSessionService = authSessionService;
        this.driveItemRepository = driveItemRepository;
    }

    public AuthLoginResponse requireSession(String token) {
        AuthLoginResponse session = authSessionService.currentSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        return session;
    }

    public DriveItem requireOwnedItem(String ownerPersonOaId, String itemId) {
        return driveItemRepository.findByIdAndOwnerPersonOaId(itemId, ownerPersonOaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
    }

    public void assertParentAccessible(String ownerPersonOaId, String parentId) {
        if (!StringUtils.hasText(parentId)) {
            return;
        }
        DriveItem parent = requireOwnedItem(ownerPersonOaId, parentId);
        if (parent.isDeleted()
                || parent.getItemType() != DriveItemType.FOLDER
                || parent.getScopeType() != DriveScopeType.PERSONAL
                || !ownerPersonOaId.equals(parent.getScopeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid");
        }
    }

    public String normalizeParentId(String parentId) {
        return StringUtils.hasText(parentId) ? parentId.trim() : "";
    }
}
