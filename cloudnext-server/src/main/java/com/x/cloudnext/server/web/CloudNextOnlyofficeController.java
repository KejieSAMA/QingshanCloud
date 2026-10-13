package com.x.cloudnext.server.web;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.x.cloudnext.server.drive.DriveOnlyofficeCallbackRequest;
import com.x.cloudnext.server.drive.DriveOnlyofficeForceSaveResponse;
import com.x.cloudnext.server.drive.DriveOnlyofficeCallbackResponse;
import com.x.cloudnext.server.drive.DriveOnlyofficeSaveStatusResponse;
import com.x.cloudnext.server.drive.DriveOnlyofficeViewConfigResponse;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.drive.OnlyofficeHealthProbeJob;
import com.x.cloudnext.server.drive.OnlyofficeHealthStatus;

@RestController
@RequestMapping("/api/onlyoffice")
public class CloudNextOnlyofficeController {

    private final DriveService driveService;
    private final OnlyofficeHealthProbeJob onlyofficeHealthProbeJob;

    public CloudNextOnlyofficeController(DriveService driveService, OnlyofficeHealthProbeJob onlyofficeHealthProbeJob) {
        this.driveService = driveService;
        this.onlyofficeHealthProbeJob = onlyofficeHealthProbeJob;
    }

    @GetMapping("/health")
    public OnlyofficeHealthStatus health() {
        return onlyofficeHealthProbeJob.status();
    }

    @GetMapping("/view-config")
    public DriveOnlyofficeViewConfigResponse viewConfig(
            @RequestParam("previewTicket") String previewTicket) {
        String requestBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return driveService.getOnlyofficeViewConfig(previewTicket, requestBaseUrl);
    }

    @GetMapping("/edit-config")
    public DriveOnlyofficeViewConfigResponse editConfig(
            @RequestParam("editTicket") String editTicket) {
        String requestBaseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return driveService.getOnlyofficeEditConfig(editTicket, requestBaseUrl);
    }

    @PostMapping("/force-save")
    public DriveOnlyofficeForceSaveResponse forceSave(
            @RequestParam("editTicket") String editTicket,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        return driveService.forceOnlyofficeSave(editTicket, authorizationHeader);
    }

    @GetMapping("/save-status")
    public DriveOnlyofficeSaveStatusResponse saveStatus(
            @RequestParam("editTicket") String editTicket) {
        return driveService.getOnlyofficeSaveStatus(editTicket);
    }

    @GetMapping("/content")
    public ResponseEntity<?> content(@RequestParam("previewTicket") String previewTicket) {
        DriveService.DriveDownloadPayload payload = driveService.loadOnlyofficeContent(previewTicket);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(payload.getContentType());
        } catch (Exception e) {
        }
        String encodedName = UriUtils.encode(payload.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .body(payload.getResource());
    }

    @PostMapping("/callback")
    public DriveOnlyofficeCallbackResponse callback(
            @RequestParam("editTicket") String editTicket,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestHeader(name = "AuthorizationJwt", required = false) String authorizationJwtHeader,
            @RequestBody(required = false) DriveOnlyofficeCallbackRequest request) {
        String header = authorizationHeader != null ? authorizationHeader : authorizationJwtHeader;
        if (request == null) {
            request = new DriveOnlyofficeCallbackRequest();
        }
        return driveService.handleOnlyofficeCallback(editTicket, request, header);
    }
}
