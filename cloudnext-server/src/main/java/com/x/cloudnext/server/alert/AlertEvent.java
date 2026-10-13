package com.x.cloudnext.server.alert;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条告警事件 — 不可变 DTO，用于穿透 channel 链。
 *
 * 字段约定：
 *   - source     ：告警来源，建议用稳定字符串（如 "ONLYOFFICE_HEALTH"），用于防抖去重；
 *   - title      ：单行简短标题（< 60 字符），适合钉钉/邮件主题；
 *   - detail     ：多行展开描述，可选；
 *   - metadata   ：附加结构化数据，便于聚合检索；
 *   - severity   ：严重等级；
 *   - occurredAt ：发生时间（默认 now）。
 */
public final class AlertEvent {

    private final AlertSeverity severity;
    private final String source;
    private final String title;
    private final String detail;
    private final Map<String, Object> metadata;
    private final Instant occurredAt;

    private AlertEvent(Builder b) {
        this.severity = b.severity == null ? AlertSeverity.WARNING : b.severity;
        this.source = b.source == null ? "" : b.source;
        this.title = b.title == null ? "" : b.title;
        this.detail = b.detail == null ? "" : b.detail;
        this.metadata = b.metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(b.metadata));
        this.occurredAt = b.occurredAt == null ? Instant.now() : b.occurredAt;
    }

    public AlertSeverity getSeverity() { return severity; }
    public String getSource() { return source; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getOccurredAt() { return occurredAt; }

    /** 防抖去重键：source + title + severity 三元组。同一三元组在去重窗口内仅发一次。 */
    public String dedupKey() {
        return severity + "|" + source + "|" + title;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AlertSeverity severity;
        private String source;
        private String title;
        private String detail;
        private Map<String, Object> metadata;
        private Instant occurredAt;

        public Builder severity(AlertSeverity s) { this.severity = s; return this; }
        public Builder source(String s) { this.source = s; return this; }
        public Builder title(String t) { this.title = t; return this; }
        public Builder detail(String d) { this.detail = d; return this; }
        public Builder occurredAt(Instant t) { this.occurredAt = t; return this; }

        public Builder meta(String key, Object value) {
            if (metadata == null) {
                metadata = new LinkedHashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        public AlertEvent build() {
            return new AlertEvent(this);
        }
    }
}
