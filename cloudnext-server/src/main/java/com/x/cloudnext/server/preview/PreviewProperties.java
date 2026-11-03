package com.x.cloudnext.server.preview;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Conversion settings are independent of the retired document editor. */
@Component
@ConfigurationProperties(prefix = "cloudnext.preview")
public class PreviewProperties {
    private String cacheRoot = ".data/preview-cache";
    private String sofficePath = "soffice";
    private String pythonPath = "python3";
    private String converterVersion = "libreoffice-pdf-v1";
    private String fontPackVersion = "cjk-v1";
    private String signingSecret = "";
    private long maxSourceBytes = 80L * 1024 * 1024;
    private long maxPdfBytes = 100L * 1024 * 1024;
    private int maxPages = 500;
    private long timeoutMs = 120000;
    private long startupTimeoutMs = 60000;
    private long retentionDays = 7;
    private long maxCacheBytes = 10L * 1024 * 1024 * 1024;
    private int queueCapacity = 100;
    private boolean isolatePresentations;
    public Path root() { return Paths.get(cacheRoot).toAbsolutePath().normalize(); }
    public String getCacheRoot() { return cacheRoot; }
    public void setCacheRoot(String v) { cacheRoot = v; }
    public String getSofficePath() { return sofficePath; }
    public void setSofficePath(String v) { sofficePath = v; }
    public String getPythonPath() { return pythonPath; }
    public void setPythonPath(String v) { pythonPath = v; }
    public String getConverterVersion() { return converterVersion; }
    public void setConverterVersion(String v) { converterVersion = v; }
    public String getFontPackVersion() { return fontPackVersion; }
    public void setFontPackVersion(String v) { fontPackVersion = v; }
    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String v) { signingSecret = v; }
    public long getMaxSourceBytes() { return maxSourceBytes; }
    public void setMaxSourceBytes(long v) { maxSourceBytes = positive(v); }
    public long getMaxPdfBytes() { return maxPdfBytes; }
    public void setMaxPdfBytes(long v) { maxPdfBytes = positive(v); }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int v) { maxPages = (int) positive(v); }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long v) { timeoutMs = positive(v); }
    public long getStartupTimeoutMs() { return startupTimeoutMs; }
    public void setStartupTimeoutMs(long v) { startupTimeoutMs = positive(v); }
    public long getRetentionDays() { return retentionDays; }
    public void setRetentionDays(long v) { retentionDays = positive(v); }
    public long getMaxCacheBytes() { return maxCacheBytes; }
    public void setMaxCacheBytes(long v) { maxCacheBytes = positive(v); }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int v) { queueCapacity = (int) positive(v); }
    public boolean isIsolatePresentations() { return isolatePresentations; }
    public void setIsolatePresentations(boolean v) { isolatePresentations = v; }
    private static long positive(long v) { if (v <= 0) throw new IllegalArgumentException("preview limits must be positive"); return v; }
}
