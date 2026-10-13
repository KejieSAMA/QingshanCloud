package com.x.cloudnext.server.audit;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * N+2.7 F1: 审计写入的异步执行器配置。
 *
 * 高频热点路径（如 contentItem 预览）通过 {@link AuditLogService#recordAsync}
 * 把审计落盘从业务线程剥离到独立线程池，降低 P95 延迟。
 *
 * 容量取舍：核心 2 / 最大 4 / 队列 200。队列满时回退到调用方线程同步写入
 * （CallerRunsPolicy），保证审计不丢失但接受降级延迟 —— 这比默认 Abort
 * 抛异常更稳妥，符合 audit "best effort 但不阻塞业务" 的语义。
 *
 * 低频路径（downloadItem / deleteItem / EDIT_COMPLETED 等）保持调用同步
 * {@link AuditLogService#record}，强一致语义不变。
 */
@Configuration
@EnableAsync
public class AuditAsyncConfig {

    public static final String AUDIT_TASK_EXECUTOR = "cloudnextAuditTaskExecutor";

    @Bean(AUDIT_TASK_EXECUTOR)
    public Executor cloudnextAuditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cloudnext-audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
