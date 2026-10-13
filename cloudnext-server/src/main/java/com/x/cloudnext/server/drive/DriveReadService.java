package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemReadRepository;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

@Service
public class DriveReadService {

    private static final int RECENT_ITEMS_LIMIT = 8;

    private final DriveAccessService driveAccessService;
    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemRepository driveItemRepository;
    private final DriveItemReadRepository driveItemReadRepository;
    private final DriveItemResponseAssembler driveItemResponseAssembler;

    public DriveReadService(
            DriveAccessService driveAccessService,
            DriveScopeAccessService driveScopeAccessService,
            DriveItemRepository driveItemRepository,
            DriveItemReadRepository driveItemReadRepository,
            DriveItemResponseAssembler driveItemResponseAssembler) {
        this.driveAccessService = driveAccessService;
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemRepository = driveItemRepository;
        this.driveItemReadRepository = driveItemReadRepository;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listMyItems(String token, String parentId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        String normalizedParentId = driveAccessService.normalizeParentId(parentId);
        driveAccessService.assertParentAccessible(session.getPersonOaId(), normalizedParentId);
        List<DriveItem> items = driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                DriveScopeType.PERSONAL,
                session.getPersonOaId(),
                normalizedParentId);
        items.sort(resolveComparator(new DriveQueryRequest(), false));
        return driveItemResponseAssembler.toResponses(items);
    }

    @Transactional(readOnly = true)
    public DrivePageResponse queryMyItems(String token, DriveQueryRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        String normalizedParentId = driveAccessService.normalizeParentId(safeRequest.getParentId());
        safeRequest.setParentId(normalizedParentId);
        validateTimeFilters(safeRequest);
        driveAccessService.assertParentAccessible(session.getPersonOaId(), normalizedParentId);
        DriveItemQueryResult result = driveItemReadRepository.queryDirectoryItems(session.getPersonOaId(), safeRequest);
        return driveItemResponseAssembler.toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listTrashItems(String token) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<DriveItem> items = driveItemRepository.findByScopeTypeAndScopeIdAndDeletedTrue(
                DriveScopeType.PERSONAL,
                session.getPersonOaId());
        items.sort(resolveComparator(new DriveQueryRequest(), true));
        return driveItemResponseAssembler.toResponses(items);
    }

    @Transactional(readOnly = true)
    public DrivePageResponse queryTrashItems(String token, DriveQueryRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        validateTimeFilters(safeRequest);
        DriveItemQueryResult result = driveItemReadRepository.queryTrashItems(session.getPersonOaId(), safeRequest);
        return driveItemResponseAssembler.toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listRecentItems(String token) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<DriveItem> items = driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalseOrderByUpdatedAtDesc(
                DriveScopeType.PERSONAL,
                session.getPersonOaId(),
                PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .getContent();
        return driveItemResponseAssembler.toResponses(items);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listRecentOpenedItems(String token, Integer limit) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<DriveItem> items = driveItemReadRepository.findRecentOpenedItems(
                session.getPersonOaId(),
                normalizeRecentOpenedLimit(limit));
        return driveItemResponseAssembler.toResponses(items);
    }

    @Transactional(readOnly = true)
    public List<DrivePathNodeResponse> getPath(String token, String itemId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        if (!StringUtils.hasText(itemId)) {
            return Collections.emptyList();
        }
        DriveItem current = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);
        if (current.isDeleted() || current.getItemType() != DriveItemType.FOLDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item is not a folder");
        }
        List<DrivePathNodeResponse> path = new ArrayList<>();
        while (current != null) {
            path.add(new DrivePathNodeResponse(current.getId(), current.getName()));
            if (!StringUtils.hasText(current.getParentId())) {
                break;
            }
            current = driveScopeAccessService.requireItem(session, current.getParentId(), DriveScopeAction.READ);
        }
        Collections.reverse(path);
        return path;
    }

    private int normalizeRecentOpenedLimit(Integer limit) {
        if (limit == null) {
            return 6;
        }
        return Math.max(1, Math.min(limit, 20));
    }

    private void validateTimeFilters(DriveQueryRequest request) {
        validateInstant(request == null ? "" : request.getTimeFrom(), "invalid timeFrom");
        validateInstant(request == null ? "" : request.getTimeTo(), "invalid timeTo");
    }

    private void validateInstant(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }

    private Comparator<DriveItem> resolveComparator(DriveQueryRequest request, boolean trashMode) {
        String sortBy = StringUtils.hasText(request.getSortBy())
                ? request.getSortBy().trim()
                : (trashMode ? "deletedAt" : "updatedAt");
        boolean ascending = "asc".equalsIgnoreCase(request.getSortOrder());

        Comparator<DriveItem> comparator;
        switch (sortBy) {
            case "name":
                comparator = Comparator.comparing(item -> normalizeSortString(item.getName()));
                break;
            case "createdAt":
                comparator = Comparator.comparing(item -> normalizeSortString(item.getCreatedAt()));
                break;
            case "sizeBytes":
                comparator = Comparator.comparing(item -> item.getSizeBytes() == null ? 0L : item.getSizeBytes());
                break;
            case "lastAccessedAt":
                comparator = Comparator.comparing(item -> normalizeSortString(item.getLastAccessedAt()));
                break;
            case "deletedAt":
                comparator = Comparator.comparing(item -> normalizeSortString(item.getDeletedAt()));
                break;
            case "updatedAt":
            default:
                comparator = Comparator.comparing(item -> normalizeSortString(item.getUpdatedAt()));
                break;
        }
        if (!ascending) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparing(item -> normalizeSortString(item.getName()));
        if (!trashMode) {
            comparator = Comparator.comparing((DriveItem item) -> item.getItemType() != DriveItemType.FOLDER)
                    .thenComparing(comparator);
        }
        return comparator;
    }

    private String normalizeSortString(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
