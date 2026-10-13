package com.x.cloudnext.server.drive.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.drive.DriveItemQueryResult;
import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.NaturalOrder;
import com.x.cloudnext.server.drive.DriveQueryRequest;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.entity.DriveItem;

@Repository
public class DriveItemReadRepository {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int[] ALLOWED_PAGE_SIZES = new int[] { 20, 50, 100 };

    @PersistenceContext
    private EntityManager entityManager;

    public DriveItemQueryResult queryDirectoryItems(String ownerPersonOaId, DriveQueryRequest request) {
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        return queryItems(ownerPersonOaId, DriveScopeType.PERSONAL, ownerPersonOaId, safeRequest, false);
    }

    public DriveItemQueryResult queryWorkspaceDirectoryItems(String workspaceId, DriveQueryRequest request) {
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        return queryItems(null, DriveScopeType.WORKSPACE, workspaceId, safeRequest, false);
    }

    public DriveItemQueryResult queryTrashItems(String ownerPersonOaId, DriveQueryRequest request) {
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        return queryItems(ownerPersonOaId, DriveScopeType.PERSONAL, ownerPersonOaId, safeRequest, true);
    }

    public List<DriveItem> findRecentOpenedItems(String ownerPersonOaId, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DriveItem> query = cb.createQuery(DriveItem.class);
        Root<DriveItem> root = query.from(DriveItem.class);
        query.select(root)
                .where(
                        cb.equal(root.get("ownerPersonOaId"), ownerPersonOaId),
                        cb.equal(root.get("scopeType"), DriveScopeType.PERSONAL),
                        cb.equal(root.get("scopeId"), ownerPersonOaId),
                        cb.isFalse(root.get("deleted")),
                        cb.equal(root.get("itemType"), DriveItemType.FILE),
                        cb.notEqual(root.get("lastAccessedAt"), ""))
                .orderBy(cb.desc(root.get("lastAccessedAt")), cb.asc(cb.lower(root.get("name"))));
        return entityManager.createQuery(query)
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    private DriveItemQueryResult queryItems(
            String ownerPersonOaId,
            DriveScopeType scopeType,
            String scopeId,
            DriveQueryRequest request,
            boolean trashMode) {
        long total = countItems(ownerPersonOaId, scopeType, scopeId, request, trashMode);
        int pageSize = normalizePageSize(request.getSize());
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
        int page = normalizePage(request.getPage(), totalPages);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DriveItem> contentQuery = cb.createQuery(DriveItem.class);
        Root<DriveItem> root = contentQuery.from(DriveItem.class);
        contentQuery.select(root)
                .where(buildPredicates(cb, root, ownerPersonOaId, scopeType, scopeId, request, trashMode).toArray(new Predicate[0]))
                .orderBy(buildOrders(cb, root, request, trashMode));

        TypedQuery<DriveItem> typedQuery = entityManager.createQuery(contentQuery);
        List<DriveItem> allItems = typedQuery.getResultList();
        allItems.sort(itemComparator(request, trashMode));
        int fromIndex = total == 0 ? 0 : Math.max(0, (page - 1) * pageSize);
        int toIndex = Math.min(allItems.size(), fromIndex + pageSize);
        return new DriveItemQueryResult(
                fromIndex >= toIndex ? new ArrayList<>() : new ArrayList<>(allItems.subList(fromIndex, toIndex)),
                total,
                page,
                pageSize);
    }

    private long countItems(
            String ownerPersonOaId,
            DriveScopeType scopeType,
            String scopeId,
            DriveQueryRequest request,
            boolean trashMode) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<DriveItem> root = countQuery.from(DriveItem.class);
        countQuery.select(cb.count(root))
                .where(buildPredicates(cb, root, ownerPersonOaId, scopeType, scopeId, request, trashMode).toArray(new Predicate[0]));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            CriteriaBuilder cb,
            Root<DriveItem> root,
            String ownerPersonOaId,
            DriveScopeType scopeType,
            String scopeId,
            DriveQueryRequest request,
            boolean trashMode) {
        List<Predicate> predicates = new ArrayList<>();
        if (StringUtils.hasText(ownerPersonOaId)) {
            predicates.add(cb.equal(root.get("ownerPersonOaId"), ownerPersonOaId));
        }
        predicates.add(cb.equal(root.get("scopeType"), scopeType));
        predicates.add(cb.equal(root.get("scopeId"), scopeId));
        predicates.add(trashMode ? cb.isTrue(root.get("deleted")) : cb.isFalse(root.get("deleted")));

        if (!trashMode) {
            predicates.add(cb.equal(root.get("parentId"), normalizeParentId(request.getParentId())));
        }

        String normalizedItemType = normalizeRequestedItemType(request.getItemType());
        if ("folder".equals(normalizedItemType)) {
            predicates.add(cb.equal(root.get("itemType"), DriveItemType.FOLDER));
        } else if ("file".equals(normalizedItemType)) {
            predicates.add(cb.equal(root.get("itemType"), DriveItemType.FILE));
        } else if (StringUtils.hasText(normalizedItemType)) {
            predicates.add(cb.equal(cb.lower(root.get("fileType")), normalizedItemType));
        }

        if (StringUtils.hasText(request.getKeyword())) {
            String likeValue = "%" + request.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.like(cb.lower(root.get("name")), likeValue));
        }

        String targetField = trashMode ? "deletedAt" : "updatedAt";
        if (StringUtils.hasText(request.getTimeFrom())) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(targetField), request.getTimeFrom()));
        }
        if (StringUtils.hasText(request.getTimeTo())) {
            predicates.add(cb.lessThanOrEqualTo(root.get(targetField), request.getTimeTo()));
        }

        return predicates;
    }

    private List<Order> buildOrders(CriteriaBuilder cb, Root<DriveItem> root, DriveQueryRequest request, boolean trashMode) {
        String sortBy = normalizeSortBy(request, trashMode);
        boolean ascending = "asc".equalsIgnoreCase(request.getSortOrder());

        List<Order> orders = new ArrayList<>();
        if (!trashMode) {
            Expression<Integer> folderFirst = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("itemType"), DriveItemType.FOLDER), 0)
                    .otherwise(1);
            orders.add(cb.asc(folderFirst));
        }

        Expression<?> sortExpression;
        switch (sortBy) {
            case "name":
                sortExpression = cb.lower(root.get("name"));
                break;
            case "createdAt":
                sortExpression = root.get("createdAt");
                break;
            case "sizeBytes":
                sortExpression = root.get("sizeBytes");
                break;
            case "lastAccessedAt":
                sortExpression = root.get("lastAccessedAt");
                break;
            case "deletedAt":
                sortExpression = root.get("deletedAt");
                break;
            case "updatedAt":
            default:
                sortExpression = root.get("updatedAt");
                break;
        }
        orders.add(ascending ? cb.asc(sortExpression) : cb.desc(sortExpression));
        orders.add(cb.asc(cb.lower(root.get("name"))));
        orders.add(cb.asc(root.get("id")));
        return orders;
    }

    private Comparator<DriveItem> itemComparator(DriveQueryRequest request, boolean trashMode) {
        boolean ascending = "asc".equalsIgnoreCase(request.getSortOrder());
        Comparator<DriveItem> folderFirst = Comparator.comparing(
                item -> item.getItemType() == DriveItemType.FOLDER ? 0 : 1);
        String sortBy = normalizeSortBy(request, trashMode);
        Comparator<DriveItem> primary;
        switch (sortBy) {
            case "name":
                primary = (left, right) -> NaturalOrder.compare(left.getName(), right.getName());
                break;
            case "createdAt":
                primary = Comparator.comparing(DriveItem::getCreatedAt, Comparator.nullsLast(String::compareTo));
                break;
            case "sizeBytes":
                primary = Comparator.comparing(DriveItem::getSizeBytes, Comparator.nullsLast(Long::compareTo));
                break;
            case "lastAccessedAt":
                primary = Comparator.comparing(DriveItem::getLastAccessedAt, Comparator.nullsLast(String::compareTo));
                break;
            case "deletedAt":
                primary = Comparator.comparing(DriveItem::getDeletedAt, Comparator.nullsLast(String::compareTo));
                break;
            case "updatedAt":
            default:
                primary = Comparator.comparing(DriveItem::getUpdatedAt, Comparator.nullsLast(String::compareTo));
                break;
        }
        if (!ascending) primary = primary.reversed();
        Comparator<DriveItem> stableName = (left, right) -> NaturalOrder.compare(left.getName(), right.getName());
        return folderFirst.thenComparing(primary).thenComparing(stableName)
                .thenComparing(DriveItem::getId, Comparator.nullsLast(String::compareTo));
    }

    private String normalizeSortBy(DriveQueryRequest request, boolean trashMode) {
        return StringUtils.hasText(request.getSortBy())
                ? request.getSortBy().trim()
                : (trashMode ? "deletedAt" : "updatedAt");
    }

    private String normalizeRequestedItemType(String value) {
        if (!StringUtils.hasText(value) || "all".equalsIgnoreCase(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("folders".equals(normalized)) {
            return "folder";
        }
        if ("files".equals(normalized)) {
            return "file";
        }
        return normalized;
    }

    private String normalizeParentId(String parentId) {
        return StringUtils.hasText(parentId) ? parentId.trim() : "";
    }

    private int normalizePage(Integer page, int totalPages) {
        int normalized = page == null ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, page);
        if (totalPages > 0) {
            return Math.min(normalized, totalPages);
        }
        return DEFAULT_PAGE;
    }

    private int normalizePageSize(Integer size) {
        int requested = size == null ? DEFAULT_PAGE_SIZE : size;
        for (int allowed : ALLOWED_PAGE_SIZES) {
            if (requested <= allowed) {
                return allowed;
            }
        }
        return ALLOWED_PAGE_SIZES[ALLOWED_PAGE_SIZES.length - 1];
    }
}
