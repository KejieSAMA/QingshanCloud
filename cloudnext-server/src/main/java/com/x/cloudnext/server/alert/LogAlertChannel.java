package com.x.cloudnext.server.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认告警通道 — 把告警以 [CLOUDNEXT ALERT] 前缀写入应用日志，
 * 方便运维通过 Filebeat / Logstash / Loki 抓取并外接到任意聚合通道。
 *
 * MVP 阶段始终启用；其它 channel（邮件、钉钉、企微）N+3 阶段实现。
 */
@Component
public class LogAlertChannel implements AlertChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger("cloudnext.alert");

    @Override
    public String name() {
        return "log";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void send(AlertEvent event) {
        if (event == null) {
            return;
        }
        String marker = "[CLOUDNEXT ALERT] [" + event.getSeverity() + "] " + event.getSource()
                + " :: " + event.getTitle();
        try {
            switch (event.getSeverity()) {
                case ERROR:
                    LOGGER.error("{} | detail={} | meta={}", marker, event.getDetail(), event.getMetadata());
                    break;
                case WARNING:
                    LOGGER.warn("{} | detail={} | meta={}", marker, event.getDetail(), event.getMetadata());
                    break;
                case INFO:
                default:
                    LOGGER.info("{} | detail={} | meta={}", marker, event.getDetail(), event.getMetadata());
                    break;
            }
        } catch (RuntimeException ignore) {
            /* logger 自身故障不阻塞业务 */
        }
    }
}
