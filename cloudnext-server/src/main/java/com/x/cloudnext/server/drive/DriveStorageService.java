package com.x.cloudnext.server.drive;

import java.util.Collections;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface DriveStorageService {

    DriveStoredFile store(String itemId, MultipartFile file);

    DriveStoredFile store(String itemId, String fileName, byte[] content, String contentType);

    /** Copies a stored object without loading the whole file into application memory. */
    DriveStoredFile copy(String sourceStorageKey, String targetItemId, String targetFileName, String contentType);

    Resource load(String storageKey);

    void delete(String storageKey);

    default boolean supportsInventory(){return false;}
    default Set<String> listStorageKeys(){return Collections.emptySet();}
}
