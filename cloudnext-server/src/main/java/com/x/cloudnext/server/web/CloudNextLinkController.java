package com.x.cloudnext.server.web;

import java.util.List;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.DriveAccessService;
import com.x.cloudnext.server.drive.entity.DrivePublicLink;
import com.x.cloudnext.server.link.DrivePublicLinkAccessLogResponse;
import com.x.cloudnext.server.link.DrivePublicLinkAccessLogsPageResponse;
import com.x.cloudnext.server.link.DrivePublicLinkCreateRequest;
import com.x.cloudnext.server.link.DrivePublicLinkResponse;
import com.x.cloudnext.server.link.DrivePublicLinkService;
import com.x.cloudnext.server.link.DrivePublicLinkUpdateRequest;
import com.x.cloudnext.server.link.entity.DrivePublicLinkAccessLog;
import com.x.cloudnext.server.link.repository.DrivePublicLinkAccessLogRepository;

/**
 * Management surface for the iteration-two Public Link feature
 * (design §"Controllers / CloudNextLinkController").
 *
 * <p>Mounted only when {@code cloudnext.iteration.public-links.enabled=true}.
 * The flag defaults to {@code false} per
 * {@code .kiro/specs/cloudnext-iteration-plan/design.md} → §"Iteration Gate
 * Strategy" / §"Configuration Inventory" — the four mappings here are
 * therefore not exposed in production until the iteration-two gate is
 * flipped on. {@code matchIfMissing = false} keeps the bean off when the
 * key is absent from {@code application.yml}, which matches the
 * "默认关闭" requirement (Requirement 1.3) and is the deliberate contrast
 * with the iteration-one workspace controller (which defaults open).
 *
 * <p>The four endpoints (Requirement 7.1-7.6) are deliberately
 * management-only; the anonymous {@code /public/links/*} surface is a
 * separate controller (task 2.9) so the auth interceptor exclusion can be
 * scoped to that path prefix without affecting these owner-facing routes.
 *
 * <p>Session resolution mirrors {@link CloudNextWorkspaceController}:
 * the {@code X-CloudNext-Token} header is read by the controller and the
 * raw value handed to {@link DriveAccessService#requireSession(String)},
 * which returns the {@link AuthLoginResponse} or throws
 * {@code 401 "cloudnext session not found"}. No new auth filter or
 * exception handler is introduced — the global
 * {@code CloudNextExceptionHandler} continues to translate
 * {@code ResponseStatusException} thrown by the service layer.
 */
@RestController
@RequestMapping("/api/links")
@ConditionalOnProperty(
        prefix = "cloudnext.iteration.public-links",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class CloudNextLinkController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final DrivePublicLinkService drivePublicLinkService;
    private final DriveAccessService driveAccessService;
    private final DrivePublicLinkAccessLogRepository drivePublicLinkAccessLogRepository;

    public CloudNextLinkController(
            DrivePublicLinkService drivePublicLinkService,
            DriveAccessService driveAccessService,
            DrivePublicLinkAccessLogRepository drivePublicLinkAccessLogRepository) {
        this.drivePublicLinkService = drivePublicLinkService;
        this.driveAccessService = driveAccessService;
        this.drivePublicLinkAccessLogRepository = drivePublicLinkAccessLogRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DrivePublicLinkResponse createLink(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @RequestBody DrivePublicLinkCreateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return drivePublicLinkService.create(session, request);
    }

    @GetMapping
    public List<DrivePublicLinkResponse> listMyLinks(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return drivePublicLinkService.listMine(session);
    }

    @GetMapping("/items/{itemId}")
    public List<DrivePublicLinkResponse> listItemLinks(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("itemId") String itemId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return drivePublicLinkService.listForItem(session, itemId);
    }

    @PatchMapping("/{id}")
    public DrivePublicLinkResponse updateLink(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @Valid @RequestBody DrivePublicLinkUpdateRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return drivePublicLinkService.update(session, id, request);
    }

    @PostMapping("/{id}/revoke")
    public DrivePublicLinkResponse revokeLink(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return drivePublicLinkService.revoke(session, id);
    }

    /**
     * Owner-facing 访问统计端点（N+2.3）。
     * 按公链治理权限访问：personal 仍限创建者/owner，workspace OWNER/ADMIN
     * 可查看空间内该 item 的链接日志；返回分页 + success/fail/total 聚合数。
     */
    @GetMapping("/{id}/access-logs")
    public DrivePublicLinkAccessLogsPageResponse accessLogs(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @PathVariable("id") String id,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DrivePublicLink link = drivePublicLinkService.requireManageableLinkForRead(session, id);
        int pageIndex = page == null || page < 1 ? 0 : page - 1;
        int pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String linkToken = link.getToken();
        Page<DrivePublicLinkAccessLog> logsPage = drivePublicLinkAccessLogRepository
                .findByTokenOrderByAttemptedAtDesc(linkToken, pageable);
        long total = drivePublicLinkAccessLogRepository.countByToken(linkToken);
        long successCount = drivePublicLinkAccessLogRepository.countByTokenAndSuccess(linkToken, true);

        DrivePublicLinkAccessLogsPageResponse response = new DrivePublicLinkAccessLogsPageResponse();
        response.setTotalCount(total);
        response.setSuccessCount(successCount);
        response.setFailCount(total - successCount);
        response.setLogs(logsPage.map(DrivePublicLinkAccessLogResponse::from));
        return response;
    }
}
