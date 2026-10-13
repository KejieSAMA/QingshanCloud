package com.x.cloudnext.server.drive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class LocalDriveStorageService implements DriveStorageService {

    private final Path rootPath;

    public LocalDriveStorageService(CloudNextProperties properties) {
        this.rootPath = Paths.get(properties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
    }

    @Override
    public DriveStoredFile store(String itemId, MultipartFile file) {
        try {
            String storageKey = resolveStorageKey(itemId, file.getOriginalFilename());
            Path targetPath = resolveTargetPath(storageKey);
            writeAtomically(targetPath, temporary -> {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
            });
            return new DriveStoredFile(storageKey, file.getSize(), normalizeContentType(file.getContentType()));
        } catch (IOException e) {
            throw new UncheckedIOException("cloudnext drive failed to store file", e);
        }
    }

    @Override
    public DriveStoredFile store(String itemId, String fileName, byte[] content, String contentType) {
        try {
            byte[] bytes = content == null ? new byte[0] : content;
            String storageKey = resolveStorageKey(itemId, fileName);
            Path targetPath = resolveTargetPath(storageKey);
            writeAtomically(targetPath, temporary -> Files.write(temporary, bytes));
            return new DriveStoredFile(storageKey, bytes.length, normalizeContentType(contentType));
        } catch (IOException e) {
            throw new UncheckedIOException("cloudnext drive failed to store file", e);
        }
    }

    @Override
    public DriveStoredFile copy(String sourceStorageKey, String targetItemId, String targetFileName, String contentType) {
        try {
            Path sourcePath = resolveExistingPath(sourceStorageKey);
            String targetStorageKey = resolveStorageKey(targetItemId, targetFileName);
            Path targetPath = resolveTargetPath(targetStorageKey);
            writeAtomically(targetPath, temporary -> Files.copy(sourcePath, temporary, StandardCopyOption.REPLACE_EXISTING));
            return new DriveStoredFile(targetStorageKey, Files.size(targetPath), normalizeContentType(contentType));
        } catch (IOException e) {
            throw new UncheckedIOException("cloudnext drive failed to copy file", e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolveSafePath(storageKey);
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveSafePath(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("cloudnext drive failed to delete file", e);
        }
    }

    @Override public boolean supportsInventory(){return true;}

    @Override public Set<String> listStorageKeys(){
        Set<String> keys=new LinkedHashSet<>();if(!Files.isDirectory(rootPath))return keys;
        try(Stream<Path> paths=Files.walk(rootPath)){paths.filter(Files::isRegularFile).sorted().forEach(path->keys.add(rootPath.relativize(path).toString().replace('\\','/')));return keys;}catch(IOException e){throw new UncheckedIOException("cloudnext drive failed to inventory local storage",e);}
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(index);
    }

    @FunctionalInterface
    private interface FileWriter { void write(Path temporary) throws IOException; }

    /** A failed stream never publishes a partial storage object. */
    private void writeAtomically(Path target, FileWriter writer) throws IOException {
        Path temporary = Files.createTempFile(rootPath, ".cloudnext-write-", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String resolveStorageKey(String itemId, String fileName) {
        return itemId + resolveExtension(fileName);
    }

    private Path resolveTargetPath(String storageKey) throws IOException {
        Files.createDirectories(rootPath);
        return resolveSafePath(storageKey);
    }

    private Path resolveExistingPath(String storageKey) throws IOException {
        if (!StringUtils.hasText(storageKey)) {
            throw new IOException("source storage key is blank");
        }
        Path path = resolveSafePath(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IOException("source storage file does not exist");
        }
        return path;
    }

    private Path resolveSafePath(String storageKey) {
        Path path = rootPath.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!path.startsWith(rootPath)) {
            throw new IllegalArgumentException("storage key escapes configured root");
        }
        return path;
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
    }
}
