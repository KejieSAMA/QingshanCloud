package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.drive.UnifiedSearchResponse.Result;

@Service
public class UnifiedSearchService {
    private static final Logger LOG = LoggerFactory.getLogger(UnifiedSearchService.class);
    private final DriveItemRepository files;
    private final WorkspaceRepository spaces;
    private final WorkspaceMemberRepository members;
    private final DriveShareService shares;
    public UnifiedSearchService(DriveItemRepository files, WorkspaceRepository spaces,
            WorkspaceMemberRepository members, DriveShareService shares) {
        this.files = files; this.spaces = spaces; this.members = members; this.shares = shares;
    }

    @Transactional(readOnly = true)
    public UnifiedSearchResponse search(AuthLoginResponse session, UnifiedSearchRequest q) {
        long started = System.nanoTime();
        String keyword = text(q.getKeyword());
        if (keyword.isEmpty() || keyword.length() > 100) throw bad("keyword must contain 1 to 100 characters");
        String scope = choice(q.getScope(), "all", "all", "personal", "shared", "workspace");
        String type = choice(q.getItemType(), "all", "all", "file", "folder", "workspace");
        String fileType = choice(q.getFileType(), "", "", "word", "excel", "ppt", "pdf", "image", "video", "audio", "archive", "other");
        String sort = choice(q.getSortBy(), "relevance", "relevance", "name", "updatedAt");
        String order = choice(q.getSortOrder(), "desc", "asc", "desc");
        Instant from = date(q.getUpdatedFrom()), to = date(q.getUpdatedTo());
        if (from != null && to != null && from.isAfter(to)) throw bad("invalid date range");
        Map<String, Result> visible = visibleItems(session, scope);
        List<Result> filtered = visible.values().stream().filter(r -> matches(r.getName(), keyword))
                .filter(r -> type.equals("all") || type.equals(r.resultType))
                .filter(r -> fileType.isEmpty() || ("file".equals(r.resultType) && matchesFileType(r.getFileType(), fileType)))
                .filter(r -> within(r.getUpdatedAt(), from, to)).collect(Collectors.toList());
        UnifiedSearchResponse response = new UnifiedSearchResponse();
        Map<String, String> owners = new TreeMap<>();
        filtered.forEach(r -> { if (!text(r.getOwnerPersonOaId()).isEmpty()) owners.put(r.getOwnerPersonOaId(), text(r.getOwnerName())); });
        owners.forEach((id, name) -> response.owners.add(new UnifiedSearchResponse.Owner(id, name)));
        filtered.removeIf(r -> !text(q.getOwner()).isEmpty() && !text(q.getOwner()).equals(r.getOwnerPersonOaId()));
        Comparator<Result> names = (a, b) -> NaturalOrder.compare(a.getName(), b.getName());
        Comparator<Result> time = Comparator.comparing(r -> safeDate(r.getUpdatedAt()));
        Comparator<Result> comparator = sort.equals("name") ? names : time;
        if (order.equals("desc")) comparator = comparator.reversed();
        if (sort.equals("relevance")) comparator = Comparator.comparingInt((Result r) -> rank(r.getName(), keyword)).thenComparing(time.reversed());
        filtered.sort(comparator.thenComparing(r -> r.resultType + ":" + r.getId()));
        response.size = Math.min(100, Math.max(1, q.getSize() == null ? 30 : q.getSize()));
        response.total = filtered.size(); response.totalPages = (filtered.size() + response.size - 1) / response.size;
        response.page = Math.max(1, Math.min(q.getPage() == null ? 1 : q.getPage(), Math.max(1, response.totalPages)));
        int start = (response.page - 1) * response.size;
        response.items = filtered.subList(start, Math.min(filtered.size(), start + response.size));
        response.hasNext = response.page < response.totalPages;
        LOG.info("cloudnext.timing op=unified-search scope={} keyword_len={} visible={} matched={} ms={}", scope, keyword.length(), visible.size(), response.total, (System.nanoTime()-started)/1_000_000);
        return response;
    }

    /** Permission-filtered inventory shared with the independent desktop experience. */
    @Transactional(readOnly = true)
    public Map<String, Result> visibleItems(AuthLoginResponse session, String scope) {
        Map<String, WorkspaceRole> roles = new HashMap<>();
        for (WorkspaceMember member : members.findByPersonOaId(session.getPersonOaId())) {
            if (member.getRole() != null) roles.put(member.getWorkspaceId(), member.getRole());
        }
        Map<String, Workspace> activeSpaces = new LinkedHashMap<>();
        spaces.findAllById(roles.keySet()).forEach(w -> {
            if (Workspace.STATUS_ACTIVE.equals(w.getStatus())) activeSpaces.put(w.getId(), w);
        });
        Map<String, Result> visible = new LinkedHashMap<>();
        Map<String, Map<String, DriveItem>> trees = new HashMap<>();
        if (scope.equals("all") || scope.equals("personal")) {
            Map<String, DriveItem> tree = tree(trees, DriveScopeType.PERSONAL, session.getPersonOaId());
            for (DriveItem item : tree.values()) {
                List<DriveItem> path = path(item, tree);
                if (path == null || !session.getPersonOaId().equals(item.getOwnerPersonOaId())) continue;
                Result r = result(item, "personal", path, "我的文件", null);
                r.allowDownload = r.allowPreview;
                r.allowCopy = true;
                r.allowManage = true; r.allowEdit = r.allowPreview; r.allowVersions = r.allowPreview;
                visible.put(item.getId(), r);
            }
        }
        if (scope.equals("all") || scope.equals("workspace")) {
            for (Workspace w : activeSpaces.values()) {
                Map<String, DriveItem> tree = tree(trees, DriveScopeType.WORKSPACE, w.getId());
                DriveItem root = tree.get(w.getRootItemId());
                if (root == null || path(root, tree) == null) continue;
                for (DriveItem item : tree.values()) {
                    List<DriveItem> path = path(item, tree);
                    if (path == null || item.getId().equals(w.getRootItemId())) continue;
                    if (path.stream().noneMatch(p -> p.getId().equals(w.getRootItemId()))) continue;
                    Result r = result(item, "workspace", path, w.getName(), w.getRootItemId());
                    r.workspaceId = w.getId();
                    r.allowManage = roles.get(w.getId()) != WorkspaceRole.VIEWER;
                    r.allowEdit = r.allowManage && r.allowPreview; r.allowVersions = r.allowEdit;
                    r.allowDownload = r.allowPreview && roles.get(w.getId()) != WorkspaceRole.VIEWER;
                    r.allowCopy = roles.get(w.getId()) != WorkspaceRole.VIEWER;
                    visible.putIfAbsent(item.getId(), r);
                }
                Result r = new Result();
                r.setId(w.getId()); r.setName(w.getName()); r.setItemType("workspace");
                r.resultType = "workspace"; r.source = "workspace"; r.workspaceId = w.getId();
                r.displayPath = "协作空间"; r.setOwnerPersonOaId(w.getCreatorPersonOaId());
                r.setOwnerName(w.getCreatorName()); r.setCreatedAt(w.getCreatedAt()); r.setUpdatedAt(w.getUpdatedAt());
                visible.put("workspace:" + w.getId(), r);
            }
        }
        if (scope.equals("all") || scope.equals("shared")) {
            List<DriveShare> matched = shares.loadMatchedShares(session);
            Map<String, DriveItem> roots = new HashMap<>();
            files.findAllById(matched.stream().map(DriveShare::getItemId).collect(Collectors.toSet()))
                    .forEach(i -> roots.put(i.getId(), i));
            matched.sort(Comparator.comparing(s -> text(s.getId())));
            Instant now = Instant.now();
            for (DriveShare share : matched) {
                if (!activeShare(share, now)) continue;
                DriveItem root = roots.get(share.getItemId());
                if (root == null || root.isDeleted()) continue;
                // Workspace access remains membership-based, exactly as the file gateway requires.
                if (root.getScopeType() == DriveScopeType.WORKSPACE && !activeSpaces.containsKey(root.getScopeId())) continue;
                Map<String, DriveItem> tree = tree(trees, root.getScopeType(), root.getScopeId());
                for (DriveItem item : tree.values()) {
                    List<DriveItem> fullPath = path(item, tree);
                    if (fullPath == null) continue;
                    int rootIndex = -1;
                    for (int i = 0; i < fullPath.size(); i++) if (root.getId().equals(fullPath.get(i).getId())) rootIndex = i;
                    if (rootIndex < 0 || (root.getItemType() != DriveItemType.FOLDER && !item.getId().equals(root.getId()))) continue;
                    Result r = result(item, "shared", fullPath.subList(rootIndex, fullPath.size()), "收到的共享", null);
                    r.shareId = share.getId();
                    r.allowEdit = r.allowPreview && share.getPermission() == DriveSharePermission.EDIT;
                    r.allowDownload = r.allowPreview && share.getPermission().satisfies(DriveSharePermission.DOWNLOAD)
                            && (root.getScopeType() != DriveScopeType.WORKSPACE || roles.get(root.getScopeId()) != WorkspaceRole.VIEWER);
                    r.allowCopy = share.getPermission().satisfies(DriveSharePermission.DOWNLOAD)
                            && (root.getScopeType() != DriveScopeType.WORKSPACE || roles.get(root.getScopeId()) != WorkspaceRole.VIEWER);
                    if (item.getId().equals(root.getId())) {
                        r.setParentId(""); r.locationParentId = "";
                    }
                    Result existing = visible.get(item.getId());
                    if (existing == null || ("shared".equals(existing.source) && r.allowDownload && !existing.allowDownload)) visible.put(item.getId(), r);
                }
            }
        }
        return visible;
    }

    private Map<String, DriveItem> tree(Map<String, Map<String, DriveItem>> cache, DriveScopeType type, String id) {
        return cache.computeIfAbsent(type + ":" + id, k -> files.findByScopeTypeAndScopeIdAndDeletedFalse(type, id)
                .stream().collect(Collectors.toMap(DriveItem::getId, i -> i)));
    }
    // Missing/deleted/cross-scope ancestors and cycles are never visible.
    static List<DriveItem> path(DriveItem item, Map<String, DriveItem> tree) {
        List<DriveItem> path = new ArrayList<>(); Set<String> seen = new HashSet<>();
        DriveItem current = item;
        while (current != null) {
            if (current.isDeleted() || !seen.add(current.getId()) || current.getScopeType() != item.getScopeType()
                    || !Objects.equals(current.getScopeId(), item.getScopeId())) return null;
            path.add(current);
            if (text(current.getParentId()).isEmpty()) { Collections.reverse(path); return path; }
            current = tree.get(current.getParentId());
            if (current != null && current.getItemType() != DriveItemType.FOLDER) return null;
        }
        return null;
    }
    private Result result(DriveItem i, String source, List<DriveItem> path, String prefix, String hiddenRoot) {
        Result r = new Result(); r.setId(i.getId()); r.setName(i.getName()); r.setParentId(i.getParentId());
        r.scopeType = i.getScopeType().name(); r.scopeId = i.getScopeId();
        r.setItemType(i.getItemType().name().toLowerCase(Locale.ROOT)); r.resultType = r.getItemType(); r.source = source;
        r.setFileType(i.getFileType()); r.setContentType(i.getContentType()); r.setSizeBytes(i.getSizeBytes());
        r.setOwnerPersonOaId(i.getOwnerPersonOaId()); r.setOwnerName(i.getOwnerName());
        r.setUpdatedAt(i.getUpdatedAt()); r.setCreatedAt(i.getCreatedAt());
        r.locationParentId = text(i.getParentId()).equals(hiddenRoot) ? "" : text(i.getParentId());
        List<String> names = new ArrayList<>(); names.add(prefix);
        for (int n = 0; n < path.size()-1; n++) if (!path.get(n).getId().equals(hiddenRoot)) names.add(path.get(n).getName());
        r.displayPath = String.join(" / ", names); r.allowPreview = i.getItemType() == DriveItemType.FILE;
        return r;
    }
    static boolean activeShare(DriveShare share, Instant now) {
        if (share.isRevoked() || share.getPermission() == null) return false;
        if (text(share.getExpiresAt()).isEmpty()) return true;
        try { return Instant.parse(share.getExpiresAt()).isAfter(now); } catch (Exception e) { return false; }
    }
    static boolean matches(String name, String keyword) {
        String value = text(name).toLowerCase(Locale.ROOT);
        for (String term : text(keyword).toLowerCase(Locale.ROOT).split("\\s+")) if (!value.contains(term)) return false;
        return true;
    }
    private static boolean matchesFileType(String stored, String requested) {
        String value = text(stored).toLowerCase(Locale.ROOT);
        if (requested.equals("other")) return !Arrays.asList("word", "excel", "ppt", "pdf", "image", "video", "audio", "archive").contains(value);
        return value.equals(requested);
    }
    static int rank(String name, String keyword) {
        String n = text(name).toLowerCase(Locale.ROOT), k = text(keyword).toLowerCase(Locale.ROOT);
        return n.equals(k) ? 0 : n.startsWith(k) ? 1 : 2;
    }
    static int naturalCompare(String a, String b) {
        return NaturalOrder.compare(a, b);
    }
    private static String text(String s) { return s == null ? "" : s.trim(); }
    private static String choice(String value, String fallback, String... allowed) {
        String s = text(value); if (s.isEmpty()) s = fallback;
        if (!Arrays.asList(allowed).contains(s)) throw bad("invalid search filter"); return s;
    }
    private static Instant date(String s) {
        if (text(s).isEmpty()) return null;
        try { return Instant.parse(s); } catch (Exception e) { throw bad("invalid search date"); }
    }
    private static Instant safeDate(String s) { try { return Instant.parse(s); } catch (Exception e) { return Instant.EPOCH; } }
    private static boolean within(String s, Instant from, Instant to) {
        Instant d = safeDate(s); return (from == null || !d.isBefore(from)) && (to == null || d.isBefore(to));
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
