package com.x.cloudnext.server.alert;

/**
 * 告警通道抽象。
 *
 * 已规划的实现：
 *   - LogAlertChannel：写 slf4j WARN/ERROR，便于日志聚合（默认实现，N+1.5 MVP）
 *   - EmailAlertChannel：Spring Mail（N+3）
 *   - DingTalkWebhookAlertChannel：钉钉自定义机器人（N+3）
 *   - WeWorkWebhookAlertChannel：企微自定义机器人（N+3）
 *
 * 实现注意：
 *   - send() 必须 catch 所有运行时异常，单 channel 失败不应阻断其它 channel；
 *   - 实现可异步（自管线程池），但 AlertService 不要求 channel 自己异步。
 */
public interface AlertChannel {

    /** 通道名称；用于日志与 health/admin 列表 */
    String name();

    /** 是否启用 */
    boolean enabled();

    /** 发送一条告警；实现应 catch 所有异常 */
    void send(AlertEvent event);
}
