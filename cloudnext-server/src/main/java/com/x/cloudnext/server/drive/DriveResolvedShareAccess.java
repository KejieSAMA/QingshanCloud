package com.x.cloudnext.server.drive;

import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;

public class DriveResolvedShareAccess {

    private final DriveItem item;
    private final DriveShare matchedShare;
    private final DriveSharePermission effectivePermission;

    public DriveResolvedShareAccess(DriveItem item, DriveShare matchedShare, DriveSharePermission effectivePermission) {
        this.item = item;
        this.matchedShare = matchedShare;
        this.effectivePermission = effectivePermission;
    }

    public static DriveResolvedShareAccess owner(DriveItem item) {
        return new DriveResolvedShareAccess(item, null, DriveSharePermission.EDIT);
    }

    public DriveItem getItem() {
        return item;
    }

    public DriveShare getMatchedShare() {
        return matchedShare;
    }

    public DriveSharePermission getEffectivePermission() {
        return effectivePermission;
    }

    public boolean allowsPreview() {
        return effectivePermission != null && effectivePermission.allowsPreview();
    }

    public boolean allowsDownload() {
        return effectivePermission != null && effectivePermission.allowsDownload();
    }
}
