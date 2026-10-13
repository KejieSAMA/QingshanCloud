package com.x.cloudnext.server.link;

import org.springframework.data.domain.Page;

/**
 * Owner 端"访问统计"端点的复合响应：标准 Spring Page + 顶部聚合 counter。
 * 前端可以一次性渲染 chip（成功 N / 失败 N）+ 表格分页。
 */
public class DrivePublicLinkAccessLogsPageResponse {

    private long totalCount;
    private long successCount;
    private long failCount;
    private Page<DrivePublicLinkAccessLogResponse> logs;

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public long getFailCount() { return failCount; }
    public void setFailCount(long failCount) { this.failCount = failCount; }

    public Page<DrivePublicLinkAccessLogResponse> getLogs() { return logs; }
    public void setLogs(Page<DrivePublicLinkAccessLogResponse> logs) { this.logs = logs; }
}
