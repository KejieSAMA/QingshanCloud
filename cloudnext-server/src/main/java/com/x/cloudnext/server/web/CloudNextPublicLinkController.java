package com.x.cloudnext.server.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.x.cloudnext.server.drive.DrivePreviewResponse;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.link.DrivePublicLinkMetaResponse;
import com.x.cloudnext.server.link.DrivePublicLinkService;
import com.x.cloudnext.server.link.DrivePublicLinkVerifyResponse;
import com.x.cloudnext.server.link.PublicLinkAccessLogger;
import com.x.cloudnext.server.link.PublicLinkRateLimiter;

/**
 * Anonymous public-end surface for {@code DrivePublicLink} (iteration two,
 * task 2.9 — design §"Controllers / CloudNextPublicLinkController").
 *
 * <p>Mounted under {@code /public/links/**} so it is structurally outside the
 * authenticated {@code /api/**} namespace. {@code WebMvcConfig} additionally
 * registers {@code /public/links/**} as an explicit exclusion on
 * {@code AuthSessionInterceptor} (Requirement 1.7) so even if the interceptor's
 * path scope is widened in the future, anonymous public-link traffic still
 * bypasses the session check.
 *
 * <p>The bean is gated by {@code cloudnext.iteration.public-links.enabled}
 * (default {@code false}). When the flag is off the bean is not registered
 * and every {@code /public/links/**} request returns 404 — matching the
 * iteration-gate behavior asserted by the {@code 4.2} pre-flight checklist.
 *
 * <p>Failure semantics intentionally NEVER produce a {@code 401}: the public
 * end must not leak the existence of the internal session model. All errors
 * propagate from {@link DrivePublicLinkService} as {@link ResponseStatusException}
 * with status codes per design §"Error Handling":
 * <ul>
 *   <li>{@code 404} — token does not resolve / underlying item missing.</li>
 *   <li>{@code 410} — link revoked / expired / exhausted.</li>
 *   <li>{@code 403} — wrong password, missing/invalid access ticket,
 *       permission insufficient (e.g. download against a preview-only link).</li>
 *   <li>{@code 429} — verify rate limit (issued from this controller).</li>
 * </ul>
 *
 * <p>Sensitive inputs (password, access ticket) are intentionally not logged.
 */
@RestController
@RequestMapping("/public/links")
@ConditionalOnProperty(prefix = "cloudnext.iteration.public-links", name = "enabled")
public class CloudNextPublicLinkController {

    /** Header alternative to the {@code ?accessTicket=} query parameter. */
    private static final String ACCESS_TICKET_HEADER = "X-CloudNext-Public-Ticket";

    private final DrivePublicLinkService drivePublicLinkService;
    private final PublicLinkRateLimiter publicLinkRateLimiter;
    private final PublicLinkAccessLogger publicLinkAccessLogger;
    private final Clock clock;

    @Autowired
    public CloudNextPublicLinkController(
            DrivePublicLinkService drivePublicLinkService,
            PublicLinkRateLimiter publicLinkRateLimiter,
            PublicLinkAccessLogger publicLinkAccessLogger) {
        this(drivePublicLinkService, publicLinkRateLimiter, publicLinkAccessLogger, Clock.systemUTC());
    }

    /** Package-private overload for unit tests that need to inject a fixed {@link Clock}. */
    CloudNextPublicLinkController(
            DrivePublicLinkService drivePublicLinkService,
            PublicLinkRateLimiter publicLinkRateLimiter,
            PublicLinkAccessLogger publicLinkAccessLogger,
            Clock clock) {
        this.drivePublicLinkService = drivePublicLinkService;
        this.publicLinkRateLimiter = publicLinkRateLimiter;
        this.publicLinkAccessLogger = publicLinkAccessLogger;
        this.clock = clock;
    }

    /**
     * Sanitized link metadata. Per Requirement 8.1 / 8.2 this endpoint is
     * non-throwing for revoked / expired / exhausted links — the response
     * carries {@code open=false} instead. {@code 404} is reserved for tokens
     * that do not resolve at all.
     */
    @GetMapping("/{token}/meta")
    public DrivePublicLinkMetaResponse meta(@PathVariable("token") String token) {
        return drivePublicLinkService.getMeta(token);
    }

    /**
     * Validate the optional access password and issue a short-lived
     * {@code accessTicket} (Requirement 8.3 / 8.4).
     *
     * <p>This endpoint is the only one rate-limited at the controller layer
     * (10 attempts per token+IP per minute, design §"Error Handling" 外链密码暴力破解保护).
     * The 11th attempt within the same minute is rejected with {@code 429}
     * before the service call is made — i.e. before BCrypt gets to do any
     * work. The rate limit check intentionally happens regardless of whether
     * the link actually requires a password, so an attacker cannot probe
     * "is this link password-protected" cheaply by spamming verify.
     */
    @PostMapping("/{token}/verify")
    public DrivePublicLinkVerifyResponse verify(
            @PathVariable("token") String token,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        try {
            if (!publicLinkRateLimiter.tryAcquire(token, ip, Instant.now(clock))) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, PublicLinkRateLimiter.EXCEEDED_MESSAGE);
            }
            String password = body == null ? null : body.get("password");
            DrivePublicLinkVerifyResponse resp = drivePublicLinkService.verify(token, password);
            publicLinkAccessLogger.recordSuccess(token, ip, ua, "VERIFY");
            return resp;
        } catch (ResponseStatusException e) {
            publicLinkAccessLogger.recordFailure(token, ip, ua, "VERIFY", e);
            throw e;
        }
    }

    /**
     * Build a preview payload for the public-end viewer
     * (Requirement 8.5 / 8.10). The {@code accessTicket} can be supplied
     * either as a query parameter or via {@link #ACCESS_TICKET_HEADER}; the
     * service treats blank as "no ticket", which is acceptable for
     * no-password links and rejected for password-protected ones.
     */
    @GetMapping("/{token}/preview")
    public DrivePreviewResponse preview(
            @PathVariable("token") String token,
            @RequestParam(name = "accessTicket", required = false) String accessTicket,
            @RequestHeader(name = ACCESS_TICKET_HEADER, required = false) String headerTicket,
            HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        try {
            DrivePreviewResponse resp = drivePublicLinkService.preview(token, firstNonBlank(accessTicket, headerTicket));
            publicLinkAccessLogger.recordSuccess(token, ip, ua, "PREVIEW");
            return resp;
        } catch (ResponseStatusException e) {
            publicLinkAccessLogger.recordFailure(token, ip, ua, "PREVIEW", e);
            throw e;
        }
    }

    /**
     * Stream the file backing a public link
     * (Requirement 8.6 / 8.9 / 8.10). Atomic limit accounting and the
     * {@code revoked → expired → exhausted → permission} ordering live
     * inside {@code DrivePublicLinkService.download}; this controller only
     * frames the response (Content-Disposition with RFC 5987 filename* and
     * a Content-Type that falls back to {@code application/octet-stream}).
     *
     * <p>The download budget is updated inside the service transaction
     * before the {@code Resource} is loaded, so a successful 200 always
     * implies the count was incremented; conversely any failure path
     * (410 / 403 / 404) rolls back so the budget is preserved.
     */
    @GetMapping("/{token}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("token") String token,
            @RequestParam(name = "accessTicket", required = false) String accessTicket,
            @RequestHeader(name = ACCESS_TICKET_HEADER, required = false) String headerTicket,
            HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ua = request == null ? "" : request.getHeader("User-Agent");
        DriveService.DriveDownloadPayload payload;
        try {
            payload = drivePublicLinkService.download(token, firstNonBlank(accessTicket, headerTicket));
            publicLinkAccessLogger.recordSuccess(token, ip, ua, "DOWNLOAD");
        } catch (ResponseStatusException e) {
            publicLinkAccessLogger.recordFailure(token, ip, ua, "DOWNLOAD", e);
            throw e;
        }

        String encodedName = UriUtils.encode(
                payload.getFileName() == null ? "" : payload.getFileName(), StandardCharsets.UTF_8);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(payload.getContentType())) {
            try {
                mediaType = MediaType.parseMediaType(payload.getContentType());
            } catch (Exception ignored) {
                // fall back to octet-stream — match CloudNextDriveController.download
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(payload.getResource());
    }

    /**
     * Resolve the client IP for rate-limiting purposes. Honors a single
     * {@code X-Forwarded-For} header by taking the leftmost, comma-separated
     * value (the originating client when behind one or more reverse proxies),
     * trimmed. Falls back to {@link HttpServletRequest#getRemoteAddr()} if
     * the header is absent or blank. Never returns {@code null} — callers
     * pass the result straight to {@link PublicLinkRateLimiter}, which
     * normalizes {@code ""} to a sentinel.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
            String trimmed = first.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

    /**
     * @return the first argument that has visible text; {@code ""} if both
     *         are blank. {@link DrivePublicLinkService} interprets
     *         {@code ""} as "ticket not supplied", which is acceptable for
     *         no-password links and rejected with 403 for password-protected
     *         ones.
     */
    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        if (StringUtils.hasText(b)) {
            return b;
        }
        return "";
    }
}
