package com.x.cloudnext.server.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.x.cloudnext.server.drive.DriveBatchDownloadRequest;
import com.x.cloudnext.server.drive.DriveBatchIdsRequest;
import com.x.cloudnext.server.drive.DriveBatchOperationResponse;
import com.x.cloudnext.server.drive.DriveBatchRenameRequest;
import com.x.cloudnext.server.drive.DriveConflictStrategy;
import com.x.cloudnext.server.drive.DriveCopyItemsRequest;
import com.x.cloudnext.server.drive.DriveCopyItemsResponse;
import com.x.cloudnext.server.drive.DriveCopyService;
import com.x.cloudnext.server.drive.DriveCreateFolderRequest;
import com.x.cloudnext.server.drive.DriveItemResponse;
import com.x.cloudnext.server.drive.DriveMoveItemsRequest;
import com.x.cloudnext.server.drive.DriveOnlyofficeEditTicketResponse;
import com.x.cloudnext.server.drive.DrivePageResponse;
import com.x.cloudnext.server.drive.DrivePathNodeResponse;
import com.x.cloudnext.server.drive.DrivePreviewResponse;
import com.x.cloudnext.server.drive.DriveQueryRequest;
import com.x.cloudnext.server.drive.DriveRenameItemRequest;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.drive.DriveSortPreferenceRequest;
import com.x.cloudnext.server.drive.DriveSortPreferenceResponse;
import com.x.cloudnext.server.drive.DriveSortPreferenceService;
import com.x.cloudnext.server.drive.DriveAccessService;
import com.x.cloudnext.server.drive.DriveZipPackagingService;

@RestController
@RequestMapping("/api/drive")
public class CloudNextDriveController {

    private final DriveService driveService;
    private final DriveZipPackagingService driveZipPackagingService;
    private final DriveSortPreferenceService driveSortPreferenceService;
    private final DriveAccessService driveAccessService;
    private final DriveCopyService driveCopyService;

    public CloudNextDriveController(
            DriveService driveService,
            DriveZipPackagingService driveZipPackagingService,
            DriveSortPreferenceService driveSortPreferenceService,
            DriveAccessService driveAccessService,
            DriveCopyService driveCopyService) {
        this.driveService = driveService;
        this.driveZipPackagingService = driveZipPackagingService;
        this.driveSortPreferenceService = driveSortPreferenceService;
        this.driveAccessService = driveAccessService;
        this.driveCopyService = driveCopyService;
    }

    @GetMapping("/preferences/sort")
    public DriveSortPreferenceResponse getSortPreference(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam("context") String context) {
        return driveSortPreferenceService.get(driveAccessService.requireSession(token), context);
    }

    @PutMapping("/preferences/sort")
    public DriveSortPreferenceResponse updateSortPreference(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveSortPreferenceRequest request) {
        return driveSortPreferenceService.update(driveAccessService.requireSession(token), request);
    }

    @GetMapping("/me/items")
    public List<DriveItemResponse> myItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "parentId", required = false) String parentId) {
        return driveService.listMyItems(token, parentId);
    }

    @GetMapping("/me/items/query")
    public DrivePageResponse myItemsQuery(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            DriveQueryRequest request) {
        return driveService.queryMyItems(token, request);
    }

    @GetMapping("/me/recent")
    public List<DriveItemResponse> myRecentItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        return driveService.listRecentItems(token);
    }

    @GetMapping("/me/recent-opened")
    public List<DriveItemResponse> myRecentOpenedItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return driveService.listRecentOpenedItems(token, limit);
    }

    @GetMapping("/me/path")
    public List<DrivePathNodeResponse> myPath(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "itemId", required = false) String itemId) {
        return driveService.getPath(token, itemId);
    }

    @PostMapping("/me/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItemResponse createFolder(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @RequestBody DriveCreateFolderRequest request) {
        return driveService.createFolder(token, request);
    }

    @PostMapping(value = "/me/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItemResponse uploadFile(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "parentId", required = false) String parentId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "conflictStrategy", required = false) String conflictStrategy) {
        return driveService.uploadFile(token, parentId, file, DriveConflictStrategy.fromValue(conflictStrategy));
    }

    @GetMapping("/items/{id}/preview")
    public DrivePreviewResponse preview(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        return driveService.getPreview(token, id);
    }

    @GetMapping("/items/{id}/edit")
    public DriveOnlyofficeEditTicketResponse edit(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        return driveService.getOnlyofficeEditTicket(token, id);
    }

    @GetMapping("/items/{id}/download")
    public ResponseEntity<?> download(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        DriveService.DriveDownloadPayload payload = driveService.downloadItem(token, id);
        String encodedName = UriUtils.encode(payload.getFileName(), StandardCharsets.UTF_8);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(payload.getContentType());
        } catch (Exception e) {
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(payload.getResource());
    }

    @GetMapping("/items/{id}/content")
    public ResponseEntity<?> content(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        DriveService.DriveDownloadPayload payload = driveService.contentItem(token, id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(payload.getContentType());
        } catch (Exception e) {
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(payload.getResource());
    }

    @DeleteMapping("/items/{id}")
    public DriveItemResponse deleteItem(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        return driveService.deleteItem(token, id);
    }

    @PatchMapping("/items/{id}/name")
    public DriveItemResponse renameItem(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @Valid @RequestBody DriveRenameItemRequest request) {
        return driveService.renameItem(token, id, request);
    }

    @PostMapping("/items/move")
    public DriveBatchOperationResponse moveItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @RequestBody DriveMoveItemsRequest request) {
        return driveService.moveItems(token, request);
    }

    @PostMapping("/items/copy")
    public DriveCopyItemsResponse copyItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveCopyItemsRequest request) {
        return driveCopyService.copy(token, request);
    }

    @PostMapping("/items/batch-rename")
    public DriveBatchOperationResponse batchRename(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @RequestBody DriveBatchRenameRequest request) {
        return driveService.batchRename(token, request);
    }

    @PostMapping("/items/batch-download")
    public void batchDownload(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveBatchDownloadRequest request,
            HttpServletResponse response) throws IOException {
        driveZipPackagingService.streamZip(token, request, response);
    }

    @GetMapping("/trash/items")
    public List<DriveItemResponse> trashItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        return driveService.listTrashItems(token);
    }

    @GetMapping("/trash/items/query")
    public DrivePageResponse trashItemsQuery(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            DriveQueryRequest request) {
        return driveService.queryTrashItems(token, request);
    }

    @PostMapping("/trash/items/{id}/restore")
    public DriveItemResponse restoreItem(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        return driveService.restoreItem(token, id);
    }

    @PostMapping("/trash/items/restore")
    public DriveBatchOperationResponse restoreItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveBatchIdsRequest request) {
        return driveService.restoreTrashItems(token, request);
    }

    @PostMapping("/trash/items/purge")
    public DriveBatchOperationResponse purgeItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody DriveBatchIdsRequest request) {
        return driveService.purgeTrashItems(token, request);
    }

    @PostMapping("/trash/clear")
    public DriveBatchOperationResponse clearTrash(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        return driveService.clearTrash(token);
    }
}
