package com.x.cloudnext.server.drive;

import java.time.Instant;

/**
 * OnlyOffice DocumentServer 健康探测的最近一次结果。
 * 由 {@link OnlyofficeHealthProbeJob} 周期性更新，前端通过 GET /api/onlyoffice/health 拉取。
 */
public class OnlyofficeHealthStatus {

    /** 是否最近一次探测成功 */
    private boolean healthy;

    /** 是否配置了 DocumentServer（未配置时 healthy=false 但属于"待配置"而非"故障"） */
    private boolean configured;

    /** 最近一次探测时间；从未探测过为 null */
    private Instant lastProbeAt;

    /** 失败时的错误描述；成功时为空字符串 */
    private String lastError = "";

    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }

    public Instant getLastProbeAt() { return lastProbeAt; }
    public void setLastProbeAt(Instant lastProbeAt) { this.lastProbeAt = lastProbeAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError == null ? "" : lastError; }
}
