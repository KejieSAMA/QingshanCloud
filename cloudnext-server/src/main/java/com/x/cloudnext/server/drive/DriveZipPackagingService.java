package com.x.cloudnext.server.drive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

/**
 * 批量打包下载服务 — N+1.4。
 *
 * 同步流式实现（MVP）：
 *   - 收集所有根 id 对应的 file items（文件夹递归展开成其子树文件）
 *   - 用 ZipOutputStream 直接写入 HttpServletResponse 的 OutputStream
 *   - 单文件超时风险由前端 fetch 处理（无 ticket / 任务系统）
 *
 * 适用范围（MVP）：仅个人 PERSONAL 文件树。WORKSPACE / 共享文件下次扩展。
 *
 * 上限保护：
 *   - 同时打包文件数 {@link #MAX_FILES_PER_ZIP}
 *   - 单次根目录最大遍历深度（防环 / 防爆）由 path 长度限制隐式保护
 */
@Service
public class DriveZipPackagingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveZipPackagingService.class);

    private static final int MAX_FILES_PER_ZIP = 1000;
    private static final String DEFAULT_ZIP_NAME = "cloudnext-files.zip";

    private final DriveAccessService driveAccessService;
    private final DriveItemRepository driveItemRepository;
    private final DriveStorageService driveStorageService;

    public DriveZipPackagingService(
            DriveAccessService driveAccessService,
            DriveItemRepository driveItemRepository,
            DriveStorageService driveStorageService) {
        this.driveAccessService = driveAccessService;
        this.driveItemRepository = driveItemRepository;
        this.driveStorageService = driveStorageService;
    }

    public void streamZip(String token, DriveBatchDownloadRequest request, HttpServletResponse response) throws IOException {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        String ownerOaId = session.getPersonOaId();

        List<String> rootIds = request == null ? null : request.getItemIds();
        if (rootIds == null || rootIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemIds 不能为空");
        }

        // 1. 校验 + 收集所有要打包的文件（递归展开文件夹）
        List<DriveItem> files = new ArrayList<>();
        Map<String, String> paths = new LinkedHashMap<>();
        for (String rootId : rootIds) {
            if (!StringUtils.hasText(rootId)) {
                continue;
            }
            DriveItem root = driveAccessService.requireOwnedItem(ownerOaId, rootId);
            if (root.isDeleted()) {
                continue;
            }
            collect(root, root.getName(), files, paths);
            if (files.size() > MAX_FILES_PER_ZIP) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "单次打包文件数超过上限 " + MAX_FILES_PER_ZIP);
            }
        }
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可打包的文件");
        }

        // 2. response headers
        String name = StringUtils.hasText(request.getZipName()) ? request.getZipName() : DEFAULT_ZIP_NAME;
        if (!name.toLowerCase().endsWith(".zip")) {
            name = name + ".zip";
        }
        String encodedName = UriUtils.encode(name, StandardCharsets.UTF_8);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName);
        // 关闭 spring 默认 buffer，让 ZipOutputStream 流式直送
        response.setBufferSize(0);

        // 3. 流式写 ZIP
        OutputStream out = response.getOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.setLevel(0); // 文件多为已压缩（office/pdf/图片），存档模式更快
            Set<String> usedEntryNames = new HashSet<>();
            for (DriveItem file : files) {
                String relPath = paths.getOrDefault(file.getId(), file.getName());
                String entryName = dedupEntryName(relPath, usedEntryNames);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setSize(file.getSizeBytes() == null ? -1 : file.getSizeBytes());
                zip.putNextEntry(entry);
                try {
                    Resource resource = driveStorageService.load(file.getStorageKey());
                    try (InputStream in = resource.getInputStream()) {
                        in.transferTo(zip);
                    }
                } catch (IOException ioe) {
                    LOGGER.warn("cloudnext zip skip file {} ({}): {}", file.getId(), file.getName(), ioe.getMessage());
                    // 单文件失败不中断整包（已写入的 entry 自然关闭即可）
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    /** 递归收集 file items 并维护其在 zip 中的相对路径 */
    private void collect(DriveItem item, String relPath, List<DriveItem> files, Map<String, String> paths) {
        if (item.isDeleted()) {
            return;
        }
        if (item.getItemType() == DriveItemType.FILE) {
            files.add(item);
            paths.put(item.getId(), sanitizePath(relPath));
            return;
        }
        // FOLDER：MVP 范围仅展开 PERSONAL scope；其它 scope 跳过
        if (item.getScopeType() != null && item.getScopeType() != DriveScopeType.PERSONAL) {
            return;
        }
        List<DriveItem> children = driveItemRepository
                .findByOwnerPersonOaIdAndDeletedFalseAndParentId(item.getOwnerPersonOaId(), item.getId());
        for (DriveItem child : children) {
            collect(child, relPath + "/" + child.getName(), files, paths);
        }
    }

    private String dedupEntryName(String path, Set<String> used) {
        if (used.add(path)) {
            return path;
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        // 仅在文件名层级（不在路径层级）追加 " (n)"
        String stem;
        String ext;
        if (dot > slash) {
            stem = path.substring(0, dot);
            ext = path.substring(dot);
        } else {
            stem = path;
            ext = "";
        }
        int n = 1;
        String candidate;
        do {
            candidate = stem + " (" + n + ")" + ext;
            n++;
        } while (!used.add(candidate));
        return candidate;
    }

    /** zip entry 路径不允许以 / 开头，过滤掉非法控制字符 */
    private String sanitizePath(String path) {
        if (path == null) {
            return "file";
        }
        String s = path;
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s.replaceAll("[\\x00-\\x1f]", "_");
    }
}
