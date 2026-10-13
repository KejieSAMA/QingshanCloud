package com.x.cloudnext.server.drive;

public class DriveStoredFile {

    private final String storageKey;
    private final long sizeBytes;
    private final String contentType;

    public DriveStoredFile(String storageKey, long sizeBytes, String contentType) {
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }
}
