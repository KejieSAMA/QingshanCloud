package com.x.cloudnext.server.web;

import java.util.List;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.DriveAccessService;
import com.x.cloudnext.server.drive.DriveConflictStrategy;
import com.x.cloudnext.server.drive.DriveItemResponse;
import com.x.cloudnext.server.drive.DrivePageResponse;
import com.x.cloudnext.server.drive.DriveQueryRequest;
import com.x.cloudnext.server.workspace.WorkspaceCreateRequest;
import com.x.cloudnext.server.workspace.WorkspaceDetailResponse;
import com.x.cloudnext.server.workspace.WorkspaceFolderCreateRequest;
import com.x.cloudnext.server.workspace.WorkspaceMemberAddRequest;
import com.x.cloudnext.server.workspace.WorkspaceMemberResponse;
import com.x.cloudnext.server.workspace.WorkspaceMemberUpdateRequest;
import com.x.cloudnext.server.workspace.WorkspaceService;
import com.x.cloudnext.server.workspace.WorkspaceSortPreferenceRequest;
import com.x.cloudnext.server.workspace.WorkspaceSortPreferenceResponse;
import com.x.cloudnext.server.workspace.WorkspaceSummaryResponse;
import com.x.cloudnext.server.workspace.WorkspaceUpdateRequest;

/**
 * REST surface for the iteration-one Workspace feature.
 *
 * <p>Mounted only when {@code cloudnext.iteration.workspaces.enabled=true}
 * (default true after iteration one ships). Disabling the flag removes the
 * bean from the container so {@code /api/workspaces*} returns 404 — the
 * iteration-gate strategy described in
 * {@code .kiro/specs/cloudnext-iteration-plan/design.md} → §"Iteration Gate
 * Strategy".
 *
 * <p>Workspace-scoped item deletion is intentionally not exposed here:
 * the design table lists ten mappings and file delete uses the existing
 * {@code DELETE /api/drive/items/{id}} endpoint, which now routes through
 * {@code DriveScopeAccessService} (task 0.5) and therefore handles
 * workspace items via the same scope-aware code path that puts them into
 * the WorkspaceTrash bucket.
 */
@RestController
@RequestMapping("/api/workspaces")
@ConditionalOnProperty(
        prefix = "cloudnext.iteration.workspaces",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CloudNextWorkspaceController {

    private final WorkspaceService workspaceService;
    private final DriveAccessService driveAccessService;

    public CloudNextWorkspaceController(
            WorkspaceService workspaceService,
            DriveAccessService driveAccessService) {
        this.workspaceService = workspaceService;
        this.driveAccessService = driveAccessService;
    }

    @GetMapping
    public List<WorkspaceSummaryResponse> listMyWorkspaces(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.listMyWorkspaces(session);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDetailResponse createWorkspace(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @RequestBody WorkspaceCreateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.createWorkspace(session, request);
    }

    @GetMapping("/sort-preference")
    public WorkspaceSortPreferenceResponse getWorkspaceSortPreference(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.getWorkspaceSortPreference(session);
    }

    @PutMapping("/sort-preference")
    public WorkspaceSortPreferenceResponse updateWorkspaceSortPreference(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestBody WorkspaceSortPreferenceRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.updateWorkspaceSortPreference(session, request);
    }

    @GetMapping("/{id}")
    public WorkspaceDetailResponse getWorkspace(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.getWorkspace(session, id);
    }

    @PatchMapping("/{id}")
    public WorkspaceDetailResponse updateWorkspace(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @Valid @RequestBody WorkspaceUpdateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.updateWorkspace(session, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveWorkspace(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        workspaceService.archiveWorkspace(session, id);
    }

    @GetMapping("/{id}/items")
    public List<DriveItemResponse> listItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @RequestParam(name = "parentId", required = false) String parentId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.listItems(session, id, parentId);
    }

    @GetMapping("/{id}/items/query")
    public DrivePageResponse queryItems(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            DriveQueryRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.queryItems(session, id, request);
    }

    @PostMapping("/{id}/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItemResponse createFolder(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @Valid @RequestBody WorkspaceFolderCreateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.createFolder(session, id, request);
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriveItemResponse uploadFile(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @RequestParam(name = "parentId", required = false) String parentId,
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "conflictStrategy", required = false) String conflictStrategy) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.uploadFile(
                session, id, parentId, file, DriveConflictStrategy.fromValue(conflictStrategy));
    }

    @GetMapping("/{id}/members")
    public List<WorkspaceMemberResponse> listMembers(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.listMembers(session, id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceMemberResponse addMember(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @Valid @RequestBody WorkspaceMemberAddRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.addMember(session, id, request);
    }

    @PatchMapping("/{id}/members/{memberId}")
    public WorkspaceMemberResponse updateMember(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @PathVariable("memberId") String memberId,
            @Valid @RequestBody WorkspaceMemberUpdateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return workspaceService.updateMemberRole(session, id, memberId, request);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @PathVariable("memberId") String memberId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        workspaceService.removeMember(session, id, memberId);
    }
}
