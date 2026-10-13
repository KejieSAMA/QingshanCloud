package com.x.cloudnext.server.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.drive.DriveBatchIdsRequest;
import com.x.cloudnext.server.drive.DriveBatchOperationResponse;
import com.x.cloudnext.server.drive.DriveCreateShareRequest;
import com.x.cloudnext.server.drive.DrivePathNodeResponse;
import com.x.cloudnext.server.drive.DriveSharePageResponse;
import com.x.cloudnext.server.drive.DriveShareQueryRequest;
import com.x.cloudnext.server.drive.DriveShareResponse;
import com.x.cloudnext.server.drive.DriveShareService;
import com.x.cloudnext.server.drive.DriveShareSummaryResponse;
import com.x.cloudnext.server.drive.DriveUpdateShareRequest;

@RestController
@RequestMapping("/api/drive/shares")
public class CloudNextDriveShareController {

    private final DriveShareService driveShareService;

    public CloudNextDriveShareController(DriveShareService driveShareService) {
        this.driveShareService = driveShareService;
    }

    @PostMapping
    public DriveBatchOperationResponse createShares(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveCreateShareRequest request) {
        return driveShareService.createShares(token, request);
    }

    @GetMapping("/outgoing")
    public DriveSharePageResponse outgoingShares(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            DriveShareQueryRequest request) {
        return driveShareService.queryOutgoingShares(token, request);
    }

    @GetMapping("/incoming")
    public DriveSharePageResponse incomingShares(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            DriveShareQueryRequest request) {
        return driveShareService.queryIncomingShares(token, request);
    }

    @GetMapping("/summary")
    public DriveShareSummaryResponse shareSummary(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "itemId") String itemId) {
        return driveShareService.getShareSummary(token, itemId);
    }

    @GetMapping("/incoming/path")
    public List<DrivePathNodeResponse> incomingPath(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "shareId") String shareId,
            @RequestParam(name = "itemId", required = false) String itemId) {
        return driveShareService.getIncomingPath(token, shareId, itemId);
    }

    @PatchMapping("/{id}")
    public DriveShareResponse updateShare(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @RequestBody DriveUpdateShareRequest request) {
        return driveShareService.updateShare(token, id, request);
    }

    @PostMapping("/{id}/revoke")
    public DriveShareResponse revokeShare(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        return driveShareService.revokeShare(token, id);
    }

    @PostMapping("/revoke")
    public DriveBatchOperationResponse revokeShares(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveBatchIdsRequest request) {
        return driveShareService.revokeShares(token, request);
    }
}
