package com.x.cloudnext.server.alert;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 告警聚合服务 — 接受所有 {@link AlertChannel} bean，并对相同 dedupKey 做 60s 防抖去重。
 *
 * 设计取舍：
 *   - 防抖逻辑用 ConcurrentHashMap 内存实现。重启后窗口重置；分布式部署需要外接 Redis（N+3 阶段）；
 *   - 单条 alert 同步发到所有 enabled channels；LogAlertChannel 是 slf4j 写日志，不阻塞业务调用方；
 *   - 后续若加邮件 / 钉钉 channel 含网络 IO，须自行 @Async 或线程池调度。
 */
@Service
public class AlertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertService.class);

    /** 同 dedupKey 在此窗口内最多发一次告警 */
    static final Duration DEDUP_WINDOW = Duration.ofSeconds(60);

    private final List<AlertChannel> channels;
    private final ConcurrentMap<String, Long> lastSentAtMs = new ConcurrentHashMap<>();

    public AlertService(List<AlertChannel> channels) {
        this.channels = channels == null ? List.of() : channels;
    }

    /** 发送一条告警；若在去重窗口内已发同 dedupKey 则跳过 */
    public void raise(AlertEvent event) {
        if (event == null) {
            return;
        }
        String dedup = event.dedupKey();
        long now = System.currentTimeMillis();
        Long last = lastSentAtMs.get(dedup);
        if (last != null && (now - last) < DEDUP_WINDOW.toMillis()) {
            return; // 防抖跳过
        }
        lastSentAtMs.put(dedup, now);
        for (AlertChannel channel : channels) {
            if (channel == null || !channel.enabled()) {
                continue;
            }
            try {
                channel.send(event);
            } catch (RuntimeException e) {
                LOGGER.warn("cloudnext alert channel {} send failed: {}", channel.name(), e.getMessage());
            }
        }
    }

    /** 测试 / Admin 健康面板用：列出已注册 channels */
    public List<AlertChannel> getChannels() {
        return channels;
    }
}
