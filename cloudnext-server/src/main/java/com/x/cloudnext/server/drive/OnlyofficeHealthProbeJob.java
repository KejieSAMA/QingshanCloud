package com.x.cloudnext.server.drive;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.x.cloudnext.server.alert.AlertEvent;
import com.x.cloudnext.server.alert.AlertService;
import com.x.cloudnext.server.alert.AlertSeverity;
import com.x.cloudnext.server.config.CloudNextProperties;

/**
 * OnlyOffice DocumentServer 健康探测任务。
 *
 * 周期 60 秒探测一次官方 /healthcheck 端点，把"健康/不健康/错误描述/最近探测时间"缓存到本对象，
 * 供 {@code GET /api/onlyoffice/health} 即时读取（无需穿透到外部服务，避免请求堆积）。
 *
 * 启动 15 秒后开始首次探测，给应用一个 warm-up 缓冲，避免与启动期其它初始化竞争资源。
 */
@Component
public class OnlyofficeHealthProbeJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnlyofficeHealthProbeJob.class);

    private final CloudNextProperties properties;
    private final RestTemplate restTemplate;
    private final AlertService alertService;

    private volatile boolean healthy = false;
    private volatile boolean configured = false;
    private volatile Instant lastProbeAt;
    private volatile String lastError = "";

    /** 标记是否完成过首次探测；首次探测不发"恢复"误报 */
    private volatile boolean primed = false;

    public OnlyofficeHealthProbeJob(
            CloudNextProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            AlertService alertService) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 15_000L)
    public void probe() {
        boolean prevHealthy = this.healthy;
        boolean prevPrimed = this.primed;

        String base = properties.getDrive().getOnlyoffice().getDocumentServerUrl();
        if (!StringUtils.hasText(base)) {
            this.configured = false;
            this.healthy = false;
            this.lastError = "document-server-url 未配置";
            this.lastProbeAt = Instant.now();
            this.primed = true;
            // 未配置不触发告警 — 那是部署状态，不是故障
            return;
        }
        this.configured = true;
        String url = stripTrailingSlash(base) + "/healthcheck";
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            String body = resp.getBody();
            boolean ok = resp.getStatusCode().is2xxSuccessful()
                    && body != null
                    && "true".equalsIgnoreCase(body.trim());
            this.healthy = ok;
            this.lastError = ok ? "" : ("non_true_response: status=" + resp.getStatusCode().value() + ", body=" + body);
            if (!ok) {
                LOGGER.warn("cloudnext onlyoffice healthcheck non-ok: status={}, body={}", resp.getStatusCode(), body);
            }
        } catch (RestClientException e) {
            this.healthy = false;
            this.lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOGGER.warn("cloudnext onlyoffice healthcheck failed: {}", this.lastError);
        }
        this.lastProbeAt = Instant.now();
        this.primed = true;

        // 状态转换触发告警（首次探测不告"恢复"）
        if (prevPrimed && prevHealthy != this.healthy) {
            if (!this.healthy) {
                alertService.raise(AlertEvent.builder()
                        .severity(AlertSeverity.ERROR)
                        .source("ONLYOFFICE_HEALTH")
                        .title("OnlyOffice DocumentServer 不可用")
                        .detail(this.lastError)
                        .meta("documentServerUrl", base)
                        .meta("probeAt", this.lastProbeAt)
                        .build());
            } else {
                alertService.raise(AlertEvent.builder()
                        .severity(AlertSeverity.INFO)
                        .source("ONLYOFFICE_HEALTH")
                        .title("OnlyOffice DocumentServer 已恢复")
                        .meta("documentServerUrl", base)
                        .meta("probeAt", this.lastProbeAt)
                        .build());
            }
        } else if (!prevPrimed && !this.healthy) {
            // 首次探测就异常：直接告警（防止应用启动时 OnlyOffice 未启动而无感知）
            alertService.raise(AlertEvent.builder()
                    .severity(AlertSeverity.ERROR)
                    .source("ONLYOFFICE_HEALTH")
                    .title("OnlyOffice DocumentServer 不可用（首次探测）")
                    .detail(this.lastError)
                    .meta("documentServerUrl", base)
                    .meta("probeAt", this.lastProbeAt)
                    .build());
        }
    }

    public OnlyofficeHealthStatus status() {
        OnlyofficeHealthStatus s = new OnlyofficeHealthStatus();
        s.setHealthy(this.healthy);
        s.setConfigured(this.configured);
        s.setLastProbeAt(this.lastProbeAt);
        s.setLastError(this.lastError);
        return s;
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
