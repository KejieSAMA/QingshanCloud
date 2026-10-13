package com.x.cloudnext.server.alert;

/**
 * 告警严重等级。
 * - INFO：信息（如 OnlyOffice 恢复）
 * - WARNING：可降级使用（如 forceSave 单次失败）
 * - ERROR：影响核心链路（如 OnlyOffice 持续不可用、callback 多次失败）
 */
public enum AlertSeverity {
    INFO,
    WARNING,
    ERROR
}
