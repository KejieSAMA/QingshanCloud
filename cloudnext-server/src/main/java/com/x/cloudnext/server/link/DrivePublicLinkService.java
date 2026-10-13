package com.x.cloudnext.server.link;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.AccessContext;
import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.DrivePreviewResponse;
import com.x.cloudnext.server.drive.DriveScopeAccessService;
import com.x.cloudnext.server.drive.DriveScopeAction;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.drive.DriveStorageService;
import com.x.cloudnext.server.drive.PublicLinkPermission;
import com.x.cloudnext.server.drive.WorkspaceRole;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DrivePublicLink;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository;

/**
 * Management surface for {@link DrivePublicLink} (iteration two,
 * task 2.4 — design §"DrivePublicLinkService" 管理端方法).
 *
 * <p>This service implements the four owner-facing operations: {@code create},
 * {@code listMine}, {@code update} and {@code revoke}. The public-facing
 * verification, preview and download endpoints land in task 2.5
 * ({@code getMeta / verify / preview / download / assertOpen}); they are
 * intentionally not implemented here.
 *
 * <p>Authorization rules follow the design's permission matrix and
 * Requirement 7.2 / 7.6:
 * <ul>
 *   <li>{@link #create(AuthLoginResponse, DrivePublicLinkCreateRequest)} —
 *       the caller must satisfy
 *       {@link DriveScopeAccessService#canActOn(AuthLoginResponse, DriveItem, DriveScopeAction)}
 *       with {@code MANAGE_SHARE}. PERSONAL items require ownership;
 *       WORKSPACE items require {@code member}/{@code admin}/{@code owner}
 *       (a {@code viewer} is rejected).</li>
 *   <li>{@link #update(AuthLoginResponse, String, DrivePublicLinkUpdateRequest)}
 *       and {@link #revoke(AuthLoginResponse, String)} — limited to the
 *       link creator for personal items; workspace owners/admins can govern
 *       links created by any member in the same workspace item.</li>
 *   <li>{@link #listMine(AuthLoginResponse)} — naturally scoped by
 *       creator via {@link DrivePublicLinkRepository#findByCreatorPersonOaIdAndRevokedFalseOrderByCreatedAtDesc(String)}.</li>
 * </ul>
 *
 * <p>Validation contracts mirror the request DTOs:
 * <ul>
 *   <li>{@code permission} — required on create, optional on update;
 *       must be one of {@link PublicLinkPermission}.</li>
 *   <li>{@code password} — blank means "no password" (stored as the empty
 *       string); non-blank is BCrypt-hashed via the
 *       {@code publicLinkPasswordEncoder} bean. Plaintext is never stored.</li>
 *   <li>{@code downloadLimit} — {@code null} normalises to {@code -1}
 *       (unlimited). Any other negative value (including {@code 0}) is
 *       rejected; values above {@link #MAX_DOWNLOAD_LIMIT} are rejected
 *       to prevent overflow shenanigans.</li>
 *   <li>{@code expiresAt} — blank means "never expires"; non-blank values
 *       are parsed as ISO-8601 (date-only is accepted and resolved to end
 *       of day local time, mirroring {@code DriveShareService}) and must
 *       lie strictly in the future.</li>
 * </ul>
 *
 * <p>Side notes:
 * <ul>
 *   <li>Token generation is delegated to {@link PublicLinkTokenGenerator},
 *       which retries once on collision and never logs the produced token.</li>
 *   <li>{@code shareUrl} composition reads
 *       {@code cloudnext.public-link.base-url}; a blank base falls back to
 *       a relative {@code "/" + token} URL.</li>
 *   <li>Tokens are deliberately NOT logged at INFO. The link id is
 *       sufficient for operational debugging.</li>
 * </ul>
 */
@Service
public class DrivePublicLinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DrivePublicLinkService.class);

    /** Upper bound on {@code downloadLimit}; prevents pathological values. */
    private static final long MAX_DOWNLOAD_LIMIT = 1_000_000L;

    /** Sentinel for {@code downloadLimit} meaning "unlimited". */
    private static final long UNLIMITED_DOWNLOAD_LIMIT = -1L;

    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemRepository driveItemRepository;
    private final DrivePublicLinkRepository drivePublicLinkRepository;
    private final PublicLinkTokenGenerator publicLinkTokenGenerator;
    private final BCryptPasswordEncoder publicLinkPasswordEncoder;
    private final PublicLinkSessionService publicLinkSessionService;
    private final DriveService driveService;
    private final DriveStorageService driveStorageService;
    private final AuditLogService auditLogService;
    private final String configuredBaseUrl;

    public DrivePublicLinkService(
            DriveScopeAccessService driveScopeAccessService,
            DriveItemRepository driveItemRepository,
            DrivePublicLinkRepository drivePublicLinkRepository,
            PublicLinkTokenGenerator publicLinkTokenGenerator,
            BCryptPasswordEncoder publicLinkPasswordEncoder,
            PublicLinkSessionService publicLinkSessionService,
            DriveService driveService,
            DriveStorageService driveStorageService,
            AuditLogService auditLogService,
            @Value("${cloudnext.public-link.base-url:}") String configuredBaseUrl) {
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemRepository = driveItemRepository;
        this.drivePublicLinkRepository = drivePublicLinkRepository;
        this.publicLinkTokenGenerator = publicLinkTokenGenerator;
        this.publicLinkPasswordEncoder = publicLinkPasswordEncoder;
        this.publicLinkSessionService = publicLinkSessionService;
        this.driveService = driveService;
        this.driveStorageService = driveStorageService;
        this.auditLogService = auditLogService;
        this.configuredBaseUrl = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
    }

    /**
     * Create a new public link for the given DriveItem.
     *
     * <p>Validation order:
     * <ol>
     *   <li>{@code itemId} present, item exists and is not soft-deleted.</li>
     *   <li>Caller authorised for {@code MANAGE_SHARE} on the item.</li>
     *   <li>{@code permission} parses to a {@link PublicLinkPermission} value.</li>
     *   <li>{@code expiresAt}, {@code downloadLimit} and {@code password}
     *       normalised through the same helpers used by
     *       {@link #update(AuthLoginResponse, String, DrivePublicLinkUpdateRequest)}.</li>
     * </ol>
     *
     * @return the saved link projected to {@link DrivePublicLinkResponse},
     *         including the absolute or relative {@code shareUrl}.
     */
    @Transactional
    public DrivePublicLinkResponse create(AuthLoginResponse session, DrivePublicLinkCreateRequest req) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }

        String itemId = trimToEmpty(req.getItemId());
        if (itemId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required");
        }
        DriveItem item = driveItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "item not found"));
        if (item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item not found");
        }

        if (!driveScopeAccessService.canActOn(session, item, DriveScopeAction.MANAGE_SHARE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "share permission denied");
        }
        if (isWorkspaceRoot(item)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot share workspace root");
        }

        PublicLinkPermission permission = parsePermissionRequired(req.getPermission());
        String passwordHash = hashPasswordOrEmpty(req.getPassword());
        long downloadLimit = normalizeDownloadLimit(req.getDownloadLimit());
        String expiresAt = normalizeExpiresAtForFuture(req.getExpiresAt());

        DrivePublicLink link = new DrivePublicLink();
        link.setId(UUID.randomUUID().toString());
        link.setItemId(item.getId());
        link.setCreatorPersonOaId(session.getPersonOaId());
        link.setCreatorName(resolveCreatorName(session));
        link.setToken(publicLinkTokenGenerator.generate());
        link.setPermission(permission);
        link.setPasswordHash(passwordHash);
        link.setExpiresAt(expiresAt);
        link.setDownloadLimit(downloadLimit);
        link.setDownloadCount(0L);
        link.setRevoked(false);

        drivePublicLinkRepository.save(link);

        // Token deliberately omitted from log — it is a bearer secret.
        LOGGER.info("public link created id={} itemId={} creator={}",
                link.getId(), link.getItemId(), link.getCreatorPersonOaId());

        auditLogService.record(
                link.getCreatorPersonOaId(),
                AuditAction.LINK_CREATE,
                "DRIVE_PUBLIC_LINK",
                link.getId(),
                "itemId", link.getItemId(),
                "itemName", item.getName(),
                "permission", link.getPermission() == null ? "" : link.getPermission().name(),
                "hasPassword", StringUtils.hasText(link.getPasswordHash()),
                "expiresAt", link.getExpiresAt(),
                "downloadLimit", link.getDownloadLimit());

        return toResponse(link, item);
    }

    /**
     * List the active (non-revoked) public links created by the caller,
     * newest first. {@code fileName} is filled by a single batch fetch
     * over {@link DriveItemRepository#findAllById(Iterable)} to avoid an
     * N+1 query when the list grows.
     */
    @Transactional(readOnly = true)
    public List<DrivePublicLinkResponse> listMine(AuthLoginResponse session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        List<DrivePublicLink> links = drivePublicLinkRepository
                .findByCreatorPersonOaIdAndRevokedFalseOrderByCreatedAtDesc(session.getPersonOaId());
        if (links.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, DriveItem> itemsById = batchLoadItems(links);
        return links.stream()
                .map(link -> toResponse(link, itemsById.get(link.getItemId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DrivePublicLinkResponse> listForItem(AuthLoginResponse session, String itemId) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        String normalizedItemId = trimToEmpty(itemId);
        if (normalizedItemId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        DriveItem item = driveScopeAccessService.requireItem(
                session, normalizedItemId, DriveScopeAction.MANAGE_SHARE);

        List<DrivePublicLink> links;
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = driveScopeAccessService.resolveWorkspaceRole(session, item.getScopeId());
            if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
                links = drivePublicLinkRepository.findByItemIdOrderByCreatedAtDesc(item.getId());
            } else {
                links = drivePublicLinkRepository.findByItemIdOrderByCreatedAtDesc(item.getId()).stream()
                        .filter(link -> session.getPersonOaId().equals(link.getCreatorPersonOaId()))
                        .collect(Collectors.toList());
            }
        } else {
            links = drivePublicLinkRepository.findByItemIdOrderByCreatedAtDesc(item.getId());
        }
        return links.stream()
                .filter(link -> !link.isRecordHidden())
                .map(link -> toResponse(link, item))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DrivePublicLinkResponse> listMineIncludingInactive(AuthLoginResponse session) {
        List<DrivePublicLink> links = drivePublicLinkRepository.findAll().stream()
                .filter(l -> session.getPersonOaId().equals(l.getCreatorPersonOaId()) && !l.isRecordHidden())
                .sorted(java.util.Comparator.comparing(DrivePublicLink::getCreatedAt).reversed())
                .collect(Collectors.toList());
        Map<String, DriveItem> itemsById = batchLoadItems(links);
        return links.stream().map(l -> toResponse(l, itemsById.get(l.getItemId()))).collect(Collectors.toList());
    }

    @Transactional
    public void hideInactiveRecord(AuthLoginResponse session, String id) {
        DrivePublicLink link = drivePublicLinkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "link not found"));
        if (!session.getPersonOaId().equals(link.getCreatorPersonOaId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "link not found");
        boolean expired = StringUtils.hasText(link.getExpiresAt()) && !java.time.Instant.parse(link.getExpiresAt()).isAfter(java.time.Instant.now());
        boolean exhausted = link.getDownloadLimit() >= 0 && link.getDownloadCount() >= link.getDownloadLimit();
        if (!link.isRevoked() && !expired && !exhausted)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revoke the active link before hiding its record");
        link.setRecordHidden(true);
        drivePublicLinkRepository.save(link);
    }

    /**
     * Apply a partial update to a public link.
     *
     * <p>Per Requirement 7.6 and design §"Error Handling", callers outside
     * the link's governance scope receive {@code 403}; missing or
     * already-revoked links receive {@code 400} so the caller can
     * distinguish "this link does not exist or is no longer editable" from
     * "you may not modify this link". {@code @PreUpdate} on the entity
     * refreshes {@code updatedAt}.
     */
    @Transactional
    public DrivePublicLinkResponse update(
            AuthLoginResponse session,
            String linkId,
            DrivePublicLinkUpdateRequest req) {
        DrivePublicLink link = requireManageableLink(session, linkId);
        if (link.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "link is revoked");
        }
        if (req != null) {
            if (req.getPermission() != null) {
                link.setPermission(parsePermissionRequired(req.getPermission()));
            }
            // password tri-state: null=unchanged, ""=clear, non-blank=re-hash
            if (req.getPassword() != null) {
                if (req.getPassword().isEmpty()) {
                    link.setPasswordHash("");
                } else if (!StringUtils.hasText(req.getPassword())) {
                    // whitespace-only is treated as clear too, matching create's "blank=no password"
                    link.setPasswordHash("");
                } else {
                    link.setPasswordHash(publicLinkPasswordEncoder.encode(req.getPassword()));
                }
            }
            if (req.getDownloadLimit() != null) {
                link.setDownloadLimit(normalizeDownloadLimit(req.getDownloadLimit()));
            }
            // expiresAt tri-state: null=unchanged, ""=clear, non-blank=validate-future
            if (req.getExpiresAt() != null) {
                if (req.getExpiresAt().isEmpty()) {
                    link.setExpiresAt("");
                } else {
                    link.setExpiresAt(normalizeExpiresAtForFuture(req.getExpiresAt()));
                }
            }
        }
        drivePublicLinkRepository.save(link);
        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        auditLogService.record(
                link.getCreatorPersonOaId(),
                AuditAction.LINK_UPDATE,
                "DRIVE_PUBLIC_LINK",
                link.getId(),
                "itemId", link.getItemId(),
                "permission", link.getPermission() == null ? "" : link.getPermission().name(),
                "hasPassword", StringUtils.hasText(link.getPasswordHash()),
                "expiresAt", link.getExpiresAt(),
                "downloadLimit", link.getDownloadLimit());
        return toResponse(link, item);
    }

    /**
     * Mark a public link as revoked. Idempotent on already-revoked links —
     * matches the {@code DriveShareService.revokeShare} contract — so a
     * client retry following a network blip will not surface a confusing
     * 400. Unauthorized callers still get {@code 403} via
     * {@link #requireManageableLink(AuthLoginResponse, String)}.
     */
    @Transactional
    public DrivePublicLinkResponse revoke(AuthLoginResponse session, String linkId) {
        DrivePublicLink link = requireManageableLink(session, linkId);
        boolean wasActive = !link.isRevoked();
        if (wasActive) {
            link.setRevoked(true);
            drivePublicLinkRepository.save(link);
            LOGGER.info("public link revoked id={} creator={}",
                    link.getId(), link.getCreatorPersonOaId());
            auditLogService.record(
                    link.getCreatorPersonOaId(),
                    AuditAction.LINK_REVOKE,
                    "DRIVE_PUBLIC_LINK",
                    link.getId(),
                    "itemId", link.getItemId());
        }
        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        return toResponse(link, item);
    }

    // -- public endpoints (anonymous) --------------------------------------

    /**
     * Strict failure ordering for {@code assertOpen} per Requirement 8.7-8.10
     * and design §"DrivePublicLinkService" / §"Error Handling". The ordering
     * is centralized so {@code verify} / {@code preview} / {@code download}
     * report the same severity-priority failure for the same link state.
     *
     * <p>The action only changes which checks apply on top of the always-on
     * {@code revoked → expired} pair. Specifically, only {@link #DOWNLOAD}
     * also enforces the {@code exhausted} early-out and the
     * {@code permission == DOWNLOAD} requirement; {@link #PREVIEW} accepts
     * either {@code PREVIEW} or {@code DOWNLOAD} permission; {@link #VERIFY}
     * and {@link #META} run only the revoked / expired gates.
     */
    enum AssertOpenAction {
        META,
        VERIFY,
        PREVIEW,
        DOWNLOAD
    }

    /**
     * Public-end response for {@code GET /public/links/{token}/meta}.
     *
     * <p>Per Requirement 8.1 / 8.2, this method intentionally does NOT
     * throw on {@code revoked}, expired, or exhausted links — it returns
     * the same projection with {@code open=false} so the front-end can
     * render a "已失效" UI without a separate request shape. {@code 404}
     * is the only failure path here, reserved for tokens that do not
     * resolve to any link at all.
     *
     * <p>If the underlying DriveItem has been hard- or soft-deleted out
     * from under the link, {@code fileName} / {@code fileType} fall back
     * to the empty string and {@code open} becomes {@code false} — again,
     * to keep the meta endpoint a non-throwing surface for the link card.
     */
    @Transactional(readOnly = true)
    public DrivePublicLinkMetaResponse getMeta(String token) {
        DrivePublicLink link = resolveLinkOr404(token);
        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        boolean itemAvailable = item != null && !item.isDeleted();
        boolean open = itemAvailable && isStillOpen(link);

        DrivePublicLinkMetaResponse response = new DrivePublicLinkMetaResponse();
        response.setFileName(itemAvailable ? trimToEmpty(item.getName()) : "");
        response.setFileType(itemAvailable ? trimToEmpty(item.getFileType()) : "");
        response.setPermission(link.getPermission().name().toLowerCase(Locale.ROOT));
        response.setRequirePassword(StringUtils.hasText(link.getPasswordHash()));
        response.setOpen(open);
        return response;
    }

    /**
     * Verify the optional access password and issue a short-lived
     * {@code accessTicket} (Requirement 8.3 / 8.4). For no-password links
     * this method still issues a ticket so the caller has a uniform code
     * path; the {@code preview} / {@code download} endpoints will simply
     * not require the ticket on no-password links.
     *
     * <p>Failure semantics per design §"Error Handling":
     * <ul>
     *   <li>{@code 410} — revoked / expired (via {@link #assertOpen}).</li>
     *   <li>{@code 403 "password required"} — the link has a password but
     *       the caller sent {@code null} or blank.</li>
     *   <li>{@code 403 "password incorrect"} — the supplied password did
     *       not match the BCrypt hash.</li>
     * </ul>
     *
     * <p>Marked {@code @Transactional} (read-write) rather than
     * {@code readOnly} so any in-flight read-then-decision flow stays
     * consistent if a future refinement starts persisting verify-side
     * state (e.g. a brute-force attempt counter).
     */
    @Transactional
    public DrivePublicLinkVerifyResponse verify(String token, String password) {
        DrivePublicLink link = assertOpen(token, AssertOpenAction.VERIFY);
        if (StringUtils.hasText(link.getPasswordHash())) {
            if (!StringUtils.hasText(password)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password required");
            }
            if (!publicLinkPasswordEncoder.matches(password, link.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password incorrect");
            }
        }
        // No-password links: skip password check entirely; ticket is issued
        // so the caller can use the same client-side flow regardless.
        return new DrivePublicLinkVerifyResponse(publicLinkSessionService.issue(link.getToken()));
    }

    /**
     * Build the preview response for an anonymous public-link viewer
     * (Requirement 8.5). Reuses {@link DriveService#buildPreviewForItem(DriveItem, AccessContext)}
     * so OnlyOffice ticket signing, allowDownload propagation, and audit
     * subject identification go through exactly the same path as the
     * authenticated preview flow.
     *
     * <p>Per design §"DrivePublicLinkService" accessTicket: a ticket is
     * required only when the link has a password. No-password links open
     * preview without one.
     *
     * <p>{@code allowDownload} on the resulting OnlyOffice payload is
     * derived from the link's stored permission ({@code DOWNLOAD} ⇒ true,
     * {@code PREVIEW} ⇒ false), matching Requirement 8.10's "preview-only
     * link cannot trigger download from inside the viewer".
     */
    @Transactional
    public DrivePreviewResponse preview(String token, String accessTicket) {
        DrivePublicLink link = assertOpen(token, AssertOpenAction.PREVIEW);
        requireTicketIfPasswordSet(link, accessTicket);
        DriveItem item = requireItemOr404(link);
        boolean downloadAllowed = link.getPermission() == PublicLinkPermission.DOWNLOAD;
        AccessContext context = AccessContext.publicLink(link.getToken(), true, downloadAllowed);
        return driveService.buildPreviewForItem(item, context);
    }

    /**
     * Atomically admit a download against the link's {@code downloadLimit}
     * and return the storage handle the controller will stream
     * (Requirement 8.6 / 8.9). The atomic increment is the single source
     * of truth for "may this download proceed?": if it returns 0 rows the
     * link has been revoked or has hit its limit since {@link #assertOpen}
     * ran (e.g. concurrent download race), and the file stream MUST NOT
     * be opened.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>{@link #assertOpen} with {@link AssertOpenAction#DOWNLOAD} —
     *       enforces revoked / expired / exhausted / permission gates with
     *       human-friendly messages.</li>
     *   <li>Ticket check (when the link has a password).</li>
     *   <li>Atomic
     *       {@link DrivePublicLinkRepository#incrementDownloadCountIfWithinLimit(String, java.time.Instant)
     *       incrementDownloadCountIfWithinLimit} — race-free admission.</li>
     *   <li>Item resolution and storage load — only after admission.</li>
     * </ol>
     *
     * <p>If the underlying DriveItem has vanished or been soft-deleted
     * between {@code assertOpen} and the admit, the increment has already
     * been paid for; throwing {@code 410 "link exhausted"} unwinds the
     * transaction so the count is rolled back, which keeps the on-disk
     * counter consistent with what the link owner observes.
     */
    @Transactional
    public DriveService.DriveDownloadPayload download(String token, String accessTicket) {
        DrivePublicLink link = assertOpen(token, AssertOpenAction.DOWNLOAD);
        requireTicketIfPasswordSet(link, accessTicket);

        int rows = drivePublicLinkRepository.incrementDownloadCountIfWithinLimit(
                link.getId(), Instant.now());
        if (rows == 0) {
            // Lost the race against a concurrent download or a revoke; do
            // NOT open a file stream. 410 mirrors the assertOpen messaging
            // so callers see a single coherent failure mode.
            throw new ResponseStatusException(HttpStatus.GONE, "link exhausted");
        }

        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        if (item == null || item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            // Throwing here rolls back the increment via @Transactional, so
            // the download budget is preserved when the underlying item is
            // gone.
            throw new ResponseStatusException(HttpStatus.GONE, "link exhausted");
        }

        Resource resource = driveStorageService.load(item.getStorageKey());
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.GONE, "link exhausted");
        }
        return new DriveService.DriveDownloadPayload(item.getName(), item.getContentType(), resource);
    }

    /**
     * Single chokepoint for the {@code revoked → expired → (exhausted) →
     * (permission)} ordering rule (Requirement 8.7 / 8.8 / 8.9 / 8.10 and
     * design §"Error Handling"). Returning the link lets callers continue
     * with action-specific checks (password, ticket, atomic admit) without
     * re-fetching by token.
     *
     * <p>Strict order:
     * <ol>
     *   <li>Token resolves → otherwise 404 {@code "link not found"}.</li>
     *   <li>{@code revoked} → 410 {@code "link revoked"}.</li>
     *   <li>{@code expiresAt} elapsed → 410 {@code "link expired"}.</li>
     *   <li>(DOWNLOAD only) {@code downloadCount >= downloadLimit} when
     *       {@code downloadLimit >= 0} → 410 {@code "link exhausted"}.
     *       Note: this is an early-out for messaging only; the atomic
     *       {@code incrementDownloadCountIfWithinLimit} is still the
     *       authoritative race-free guard inside {@link #download}.</li>
     *   <li>(DOWNLOAD only) {@code permission != DOWNLOAD} → 403
     *       {@code "permission denied"}. PREVIEW accepts both PREVIEW and
     *       DOWNLOAD permission; META and VERIFY do not check permission
     *       at all (meta is sanitized, verify is a precondition for both
     *       preview and download).</li>
     * </ol>
     */
    DrivePublicLink assertOpen(String token, AssertOpenAction action) {
        DrivePublicLink link = resolveLinkOr404(token);
        if (link.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.GONE, "link revoked");
        }
        if (isExpired(link)) {
            throw new ResponseStatusException(HttpStatus.GONE, "link expired");
        }
        if (action == AssertOpenAction.DOWNLOAD) {
            if (isExhausted(link)) {
                throw new ResponseStatusException(HttpStatus.GONE, "link exhausted");
            }
            if (link.getPermission() != PublicLinkPermission.DOWNLOAD) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permission denied");
            }
        }
        // PREVIEW: no extra check; an open link with PREVIEW or DOWNLOAD
        // permission is allowed to render a preview. META / VERIFY: only
        // revoked / expired matter at this gate.
        return link;
    }

    /**
     * Resolve a public link by token, throwing 404 if the token is blank
     * or does not exist. Centralised so {@link #getMeta(String)} (which
     * does not call {@link #assertOpen}) and {@link #assertOpen} share a
     * single not-found message.
     */
    private DrivePublicLink resolveLinkOr404(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "link not found");
        }
        return drivePublicLinkRepository.findByToken(token.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "link not found"));
    }

    /**
     * Resolve the underlying DriveItem for a non-meta public-end call.
     * Returns the item or throws 404 if it is missing or soft-deleted.
     * Used by {@code preview}; {@code download} runs its own check after
     * the atomic admit so it can roll back the increment.
     */
    private DriveItem requireItemOr404(DrivePublicLink link) {
        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        if (item == null || item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        return item;
    }

    /**
     * Enforce the "ticket required when password is set" rule
     * (design §"DrivePublicLinkService" accessTicket). For no-password
     * links the ticket is optional and intentionally accepted whether
     * blank or present, so {@code preview} / {@code download} on a
     * no-password link works without a prior {@code verify} round-trip.
     */
    private void requireTicketIfPasswordSet(DrivePublicLink link, String accessTicket) {
        if (!StringUtils.hasText(link.getPasswordHash())) {
            return;
        }
        if (!StringUtils.hasText(accessTicket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access ticket required");
        }
        if (!publicLinkSessionService.verify(link.getToken(), accessTicket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "access ticket invalid");
        }
    }

    /**
     * @return {@code true} iff the link is currently usable: not revoked,
     *         not past its {@code expiresAt}, and not at its
     *         {@code downloadLimit}. Used only by {@link #getMeta(String)}
     *         which does not throw — the public endpoints themselves go
     *         through {@link #assertOpen} for accurate per-failure
     *         messaging.
     */
    private boolean isStillOpen(DrivePublicLink link) {
        if (link.isRevoked()) {
            return false;
        }
        if (isExpired(link)) {
            return false;
        }
        if (isExhausted(link)) {
            return false;
        }
        return true;
    }

    /**
     * @return {@code true} iff {@code expiresAt} is set and has elapsed.
     *         A blank {@code expiresAt} (the "never expires" sentinel) and
     *         an unparseable value both yield {@code false} — we prefer
     *         "do not lock the user out on a malformed timestamp" over a
     *         silent 410 here, because the validation chain on create /
     *         update already rejects malformed values.
     */
    private boolean isExpired(DrivePublicLink link) {
        String value = link.getExpiresAt();
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return !Instant.parse(value.trim()).isAfter(Instant.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * @return {@code true} iff the link has a finite download budget and
     *         the budget has been spent. {@code downloadLimit < 0} is the
     *         "unlimited" sentinel and always returns {@code false}.
     */
    private boolean isExhausted(DrivePublicLink link) {
        Long limit = link.getDownloadLimit();
        if (limit == null || limit.longValue() < 0L) {
            return false;
        }
        Long count = link.getDownloadCount();
        long spent = count == null ? 0L : count.longValue();
        return spent >= limit.longValue();
    }

    // -- helpers -----------------------------------------------------------

    /**
     * Locate a link by id and assert the caller can manage it.
     *
     * <p>Write-side callers use {@code 400} ("link not found") for symmetry
     * with the design's error-handling table, which treats that surface as
     * request-validation rather than resource existence. Read-side callers
     * can opt into {@code 404}. Unauthorized callers collapse to
     * {@code 403} so the management surface does not expose links outside
     * the caller's governance scope.
     */
    @Transactional(readOnly = true)
    public DrivePublicLink requireManageableLinkForRead(AuthLoginResponse session, String linkId) {
        return requireManageableLink(session, linkId, HttpStatus.NOT_FOUND);
    }

    private DrivePublicLink requireManageableLink(AuthLoginResponse session, String linkId) {
        return requireManageableLink(session, linkId, HttpStatus.BAD_REQUEST);
    }

    private DrivePublicLink requireManageableLink(AuthLoginResponse session, String linkId, HttpStatus missingStatus) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        if (!StringUtils.hasText(linkId)) {
            throw new ResponseStatusException(missingStatus, "link not found");
        }
        DrivePublicLink link = drivePublicLinkRepository.findById(linkId.trim())
                .orElseThrow(() -> new ResponseStatusException(missingStatus, "link not found"));
        if (!canManageLink(session, link)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "link access denied");
        }
        return link;
    }

    private boolean canManageLink(AuthLoginResponse session, DrivePublicLink link) {
        if (session == null || link == null) {
            return false;
        }
        DriveItem item = driveItemRepository.findById(link.getItemId()).orElse(null);
        if (item == null) {
            return session.getPersonOaId().equals(link.getCreatorPersonOaId());
        }
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = driveScopeAccessService.resolveWorkspaceRole(session, item.getScopeId());
            if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
                return true;
            }
            return session.getPersonOaId().equals(link.getCreatorPersonOaId())
                    && driveScopeAccessService.canActOn(session, item, DriveScopeAction.MANAGE_SHARE);
        }
        return session.getPersonOaId().equals(link.getCreatorPersonOaId())
                && driveScopeAccessService.canActOn(session, item, DriveScopeAction.MANAGE_SHARE);
    }

    private boolean isWorkspaceRoot(DriveItem item) {
        return item != null
                && item.getScopeType() == DriveScopeType.WORKSPACE
                && !StringUtils.hasText(item.getParentId());
    }

    /**
     * Parse the user-supplied permission string and reject null / unknown
     * values with {@code 400}. Case-insensitive — both {@code preview} and
     * {@code PREVIEW} are accepted.
     */
    private PublicLinkPermission parsePermissionRequired(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "permission is required");
        }
        try {
            return PublicLinkPermission.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "permission is invalid");
        }
    }

    /**
     * Hash a password for storage. {@code null}, empty or whitespace-only
     * inputs collapse to the empty-string sentinel which represents "no
     * password" on the entity. Plaintext is never stored.
     */
    private String hashPasswordOrEmpty(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            return "";
        }
        return publicLinkPasswordEncoder.encode(rawPassword);
    }

    /**
     * Coerce an inbound {@code downloadLimit} into the persisted form:
     * {@code null} → {@code -1} (unlimited); {@code -1} kept verbatim;
     * {@code 0} or any other negative rejected; values above
     * {@link #MAX_DOWNLOAD_LIMIT} rejected to prevent overflow.
     */
    private long normalizeDownloadLimit(Long limit) {
        if (limit == null) {
            return UNLIMITED_DOWNLOAD_LIMIT;
        }
        long value = limit.longValue();
        if (value == UNLIMITED_DOWNLOAD_LIMIT) {
            return UNLIMITED_DOWNLOAD_LIMIT;
        }
        if (value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "downloadLimit is invalid");
        }
        if (value > MAX_DOWNLOAD_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "downloadLimit is too large");
        }
        return value;
    }

    /**
     * Normalize a user-supplied {@code expiresAt} value that MUST represent
     * a future instant. Blank input is rejected here; callers that wish to
     * accept "" as "no expiry" should branch before invoking this helper
     * (e.g. {@link #update(AuthLoginResponse, String, DrivePublicLinkUpdateRequest)}
     * for the clear-expiry case).
     *
     * <p>Accepted formats mirror {@code DriveShareService}: full ISO-8601
     * instant ({@code 2025-01-01T00:00:00Z}) or a date-only string
     * ({@code 2025-01-01}) which is interpreted as {@code 23:59:59} local
     * time.
     */
    private String normalizeExpiresAtForFuture(String value) {
        String current = trimToEmpty(value);
        if (current.isEmpty()) {
            // Used by create()'s direct call where blank means "no expiry";
            // returning "" preserves that semantics. update() pre-handles
            // the empty case before reaching here.
            return "";
        }
        Instant parsed;
        try {
            if (current.length() == 10) {
                parsed = LocalDate.parse(current)
                        .atTime(LocalTime.of(23, 59, 59))
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            } else {
                parsed = Instant.parse(current);
            }
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt is invalid");
        }
        if (!parsed.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
        }
        return parsed.toString();
    }

    /**
     * Batch-load all DriveItems referenced by a list of links so that
     * {@code listMine} can fill {@code fileName} without N+1 queries.
     */
    private Map<String, DriveItem> batchLoadItems(List<DrivePublicLink> links) {
        Set<String> itemIds = new LinkedHashSet<>();
        for (DrivePublicLink link : links) {
            if (StringUtils.hasText(link.getItemId())) {
                itemIds.add(link.getItemId());
            }
        }
        if (itemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, DriveItem> byId = new LinkedHashMap<>();
        for (DriveItem item : driveItemRepository.findAllById(itemIds)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    /**
     * Map a {@link DrivePublicLink} entity to its wire response. {@code item}
     * may be {@code null} when the underlying DriveItem has been hard-deleted
     * out from under the link; in that case {@code fileName} falls back to
     * an empty string rather than failing the entire list call.
     */
    private DrivePublicLinkResponse toResponse(DrivePublicLink link, DriveItem item) {
        DrivePublicLinkResponse response = new DrivePublicLinkResponse();
        response.setId(link.getId());
        response.setToken(link.getToken());
        response.setItemId(link.getItemId());
        response.setFileName(item == null ? "" : trimToEmpty(item.getName()));
        response.setPermission(link.getPermission().name().toLowerCase(Locale.ROOT));
        response.setRequirePassword(StringUtils.hasText(link.getPasswordHash()));
        response.setExpiresAt(trimToEmpty(link.getExpiresAt()));
        response.setDownloadLimit(link.getDownloadLimit() == null
                ? UNLIMITED_DOWNLOAD_LIMIT
                : link.getDownloadLimit().longValue());
        response.setDownloadCount(link.getDownloadCount() == null
                ? 0L
                : link.getDownloadCount().longValue());
        response.setRevoked(link.isRevoked());
        response.setCreatedAt(link.getCreatedAt());
        response.setUpdatedAt(link.getUpdatedAt());
        response.setShareUrl(buildShareUrl(link.getToken()));
        return response;
    }

    /**
     * Compose the {@code shareUrl}. When {@code cloudnext.public-link.base-url}
     * is configured we drop any trailing slashes and append {@code "/" + token};
     * otherwise we return the relative path so the caller's origin acts as
     * the implicit base.
     */
    private String buildShareUrl(String token) {
        if (configuredBaseUrl.isEmpty()) {
            return "/" + token;
        }
        String trimmed = configuredBaseUrl;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/" + token;
    }

    private String resolveCreatorName(AuthLoginResponse session) {
        if (StringUtils.hasText(session.getPersonName())) {
            return session.getPersonName();
        }
        return session.getPersonOaId();
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
