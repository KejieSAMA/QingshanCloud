package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

@Service
public class DriveSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveSearchService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    /**
     * 单页最大返回数。N+2.5 设为 100；若后续上调，需要同步前端 chip 数与分页 UI 测试基线。
     */
    private static final int MAX_SIZE = 100;
    /**
     * 关键词最大长度。防止 ReDoS / 慢 LIKE 查询。前端 input maxlength 应保持同步。
     */
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemRepository driveItemRepository;
    private final DriveShareRepository driveShareRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final DriveItemResponseAssembler driveItemResponseAssembler;

    public DriveSearchService(
            DriveScopeAccessService driveScopeAccessService,
            DriveItemRepository driveItemRepository,
            DriveShareRepository driveShareRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository,
            DriveItemResponseAssembler driveItemResponseAssembler) {
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemRepository = driveItemRepository;
        this.driveShareRepository = driveShareRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
    }

    @Transactional(readOnly = true)
    public DrivePageResponse searchFiles(AuthLoginResponse session, DriveSearchRequest request) {
        long t0 = System.nanoTime();
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        DriveSearchRequest safeRequest = request == null ? new DriveSearchRequest() : request;
        String scope = normalizeScope(safeRequest.getScope());
        String keyword = normalizeKeyword(safeRequest.getKeyword());
        String itemType = normalizeItemType(safeRequest.getItemType());
        String owner = trimToEmpty(safeRequest.getOwner());
        Instant updatedFrom = parseInstantOrNull(safeRequest.getUpdatedFrom(), "invalid updatedFrom");
        Instant updatedTo = parseInstantOrNull(safeRequest.getUpdatedTo(), "invalid updatedTo");
        String fileType = trimToEmpty(safeRequest.getFileType());
        int size = normalizeSize(safeRequest.getSize());

        List<DriveItem> candidates = loadCandidates(session, scope);
        // N+2.7 F6: 先做廉价过滤（keyword/type/owner/time/fileType），最后一次批量权限检查
        // 避免对所有候选项逐项调用 canActOn（workspace 场景下省去 N-1 次 role 查询）。
        List<DriveItem> filtered = candidates.stream()
                .filter(item -> matchesKeyword(item, keyword))
                .filter(item -> matchesItemType(item, itemType))
                .filter(item -> matchesOwner(item, owner))
                .filter(item -> matchesUpdatedFrom(item, updatedFrom))
                .filter(item -> matchesUpdatedTo(item, updatedTo))
                .filter(item -> matchesFileType(item, fileType))
                .collect(Collectors.toList());
        List<DriveItem> matched = driveScopeAccessService
                .filterByAccess(session, filtered, DriveScopeAction.READ)
                .stream()
                .sorted(resolveComparator())
                .collect(Collectors.toList());

        DrivePageResponse response = pageResults(matched, normalizePage(safeRequest.getPage()), size);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        LOGGER.info("cloudnext.timing op=search scope={} keyword_len={} candidates={} matched={} ms={}",
                scope, keyword.length(), candidates.size(), matched.size(), ms);
        return response;
    }

    private List<DriveItem> loadCandidates(AuthLoginResponse session, String scope) {
        switch (scope) {
            case "workspace":
                return loadWorkspaceCandidates(session);
            case "shared":
                return loadSharedCandidates(session);
            case "personal":
            default:
                return loadPersonalCandidates(session);
        }
    }

    private List<DriveItem> loadPersonalCandidates(AuthLoginResponse session) {
        return driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalse(
                DriveScopeType.PERSONAL,
                session.getPersonOaId());
    }

    private List<DriveItem> loadWorkspaceCandidates(AuthLoginResponse session) {
        List<String> workspaceIds = workspaceMemberRepository.findByPersonOaId(session.getPersonOaId()).stream()
                .map(WorkspaceMember::getWorkspaceId)
                .distinct()
                .filter(this::isActiveWorkspace)
                .collect(Collectors.toList());
        if (workspaceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DriveItem> all = new ArrayList<>();
        for (String workspaceId : workspaceIds) {
            all.addAll(driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalse(
                    DriveScopeType.WORKSPACE,
                    workspaceId));
        }
        return deduplicateById(all);
    }

    private boolean isActiveWorkspace(String workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(workspace -> Workspace.STATUS_ACTIVE.equals(workspace.getStatus()))
                .orElse(false);
    }

    private List<DriveItem> loadSharedCandidates(AuthLoginResponse session) {
        Set<String> targetIds = collectShareTargetIds(session);
        if (targetIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DriveShare> shares = new ArrayList<>();
        if (StringUtils.hasText(session.getPersonOaId())) {
            shares.addAll(driveShareRepository.findByTargetTypeAndTargetIdInOrderByUpdatedAtDesc(
                    DriveShareTargetType.PERSON,
                    Collections.singleton(session.getPersonOaId())));
        }
        if (!session.getIdentityList().isEmpty()) {
            Set<String> unitIds = session.getIdentityList().stream()
                    .filter(identity -> identity != null)
                    .map(AuthIdentityDto::getUnit)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!unitIds.isEmpty()) {
                shares.addAll(driveShareRepository.findByTargetTypeAndTargetIdInOrderByUpdatedAtDesc(
                        DriveShareTargetType.UNIT,
                        unitIds));
            }
            Set<String> identityIds = session.getIdentityList().stream()
                    .filter(identity -> identity != null)
                    .map(AuthIdentityDto::getId)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!identityIds.isEmpty()) {
                shares.addAll(driveShareRepository.findByTargetTypeAndTargetIdInOrderByUpdatedAtDesc(
                        DriveShareTargetType.IDENTITY,
                        identityIds));
            }
        }
        LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        for (DriveShare share : shares) {
            if (share == null || share.isRevoked()) {
                continue;
            }
            itemIds.add(share.getItemId());
        }
        if (itemIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<DriveItem> items = driveItemRepository.findByIdIn(itemIds);
        return deduplicateById(items.stream()
                .filter(item -> item != null && !item.isDeleted())
                .collect(Collectors.toList()));
    }

    private Set<String> collectShareTargetIds(AuthLoginResponse session) {
        LinkedHashSet<String> targetIds = new LinkedHashSet<>();
        if (session != null && StringUtils.hasText(session.getPersonOaId())) {
            targetIds.add(session.getPersonOaId());
        }
        for (AuthIdentityDto identity : session == null ? Collections.<AuthIdentityDto>emptyList() : session.getIdentityList()) {
            if (identity == null) {
                continue;
            }
            if (StringUtils.hasText(identity.getUnit())) {
                targetIds.add(identity.getUnit());
            }
            if (StringUtils.hasText(identity.getId())) {
                targetIds.add(identity.getId());
            }
        }
        return targetIds;
    }

    private List<DriveItem> deduplicateById(List<DriveItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<DriveItem> deduplicated = new ArrayList<>();
        for (DriveItem item : items) {
            if (item == null || !seen.add(item.getId())) {
                continue;
            }
            deduplicated.add(item);
        }
        return deduplicated;
    }

    private boolean matchesKeyword(DriveItem item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String current = keyword.toLowerCase(Locale.ROOT);
        String text = (trimToEmpty(item.getName())
                + " "
                + trimToEmpty(item.getFileType())
                + " "
                + trimToEmpty(item.getContentType())
                + " "
                + trimToEmpty(item.getOwnerName()))
                .toLowerCase(Locale.ROOT);
        return text.contains(current);
    }

    private boolean matchesFileType(DriveItem item, String fileType) {
        if (!StringUtils.hasText(fileType) || "all".equalsIgnoreCase(fileType)) {
            return true;
        }
        // item.fileType 由 DriveService.resolveFileType 在上传时确定，常见值：
        // word/excel/ppt/pdf/image/video/archive/file 等
        String itemFt = item.getFileType();
        return itemFt != null && itemFt.equalsIgnoreCase(fileType);
    }

    private boolean matchesItemType(DriveItem item, String itemType) {
        if (!StringUtils.hasText(itemType) || "all".equalsIgnoreCase(itemType)) {
            return true;
        }
        return item.getItemType() != null && item.getItemType().name().equalsIgnoreCase(itemType);
    }

    private boolean matchesOwner(DriveItem item, String owner) {
        if (!StringUtils.hasText(owner)) {
            return true;
        }
        return owner.equals(item.getOwnerPersonOaId());
    }

    private boolean matchesUpdatedFrom(DriveItem item, Instant updatedFrom) {
        if (updatedFrom == null) {
            return true;
        }
        Instant updatedAt = parseInstantOrNull(item.getUpdatedAt(), null);
        return updatedAt != null && !updatedAt.isBefore(updatedFrom);
    }

    private boolean matchesUpdatedTo(DriveItem item, Instant updatedTo) {
        if (updatedTo == null) {
            return true;
        }
        Instant updatedAt = parseInstantOrNull(item.getUpdatedAt(), null);
        return updatedAt != null && !updatedAt.isAfter(updatedTo);
    }

    private Instant parseInstantOrNull(String value, String errorMessage) {
        String current = trimToEmpty(value);
        if (!StringUtils.hasText(current)) {
            return null;
        }
        try {
            return Instant.parse(current);
        } catch (Exception e) {
            if (errorMessage != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
            }
            return null;
        }
    }

    private String normalizeScope(String scope) {
        String current = trimToEmpty(scope);
        if (!StringUtils.hasText(current)) {
            return "personal";
        }
        String normalized = current.toLowerCase(Locale.ROOT);
        if ("personal".equals(normalized) || "shared".equals(normalized) || "workspace".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope");
    }

    private String normalizeItemType(String itemType) {
        String current = trimToEmpty(itemType);
        if (!StringUtils.hasText(current) || "all".equalsIgnoreCase(current)) {
            return "";
        }
        String normalized = current.toLowerCase(Locale.ROOT);
        if ("file".equals(normalized) || "folder".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid itemType");
    }

    private String normalizeKeyword(String keyword) {
        String current = trimToEmpty(keyword);
        if (!StringUtils.hasText(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword is required");
        }
        if (current.length() > MAX_KEYWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword is too long");
        }
        return current;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        int current = size == null ? DEFAULT_SIZE : size;
        if (current < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(current, MAX_SIZE);
    }

    private Comparator<DriveItem> resolveComparator() {
        return Comparator
                .comparing(DriveItem::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(DriveItem::getName, Comparator.nullsLast(String::compareTo))
                .thenComparing(DriveItem::getId, Comparator.nullsLast(String::compareTo));
    }

    private DrivePageResponse pageResults(List<DriveItem> items, int page, int size) {
        long total = items.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int actualPage = totalPages > 0 ? Math.min(page, totalPages) : DEFAULT_PAGE;
        int fromIndex = total == 0 ? 0 : Math.min((actualPage - 1) * size, items.size());
        int toIndex = total == 0 ? 0 : Math.min(fromIndex + size, items.size());

        DrivePageResponse response = new DrivePageResponse();
        response.setPage(actualPage);
        response.setSize(size);
        response.setTotal(total);
        response.setTotalPages(totalPages);
        response.setHasNext(totalPages > 0 && actualPage < totalPages);
        response.setItems(driveItemResponseAssembler.toResponses(items.subList(fromIndex, toIndex)));
        return response;
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
