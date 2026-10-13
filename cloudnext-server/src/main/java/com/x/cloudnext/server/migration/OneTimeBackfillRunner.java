package com.x.cloudnext.server.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

/**
 * 一次性数据迁移 Runner：
 * <p>
 * 在 {@link DriveScopeType} 字段上线后，把存量 {@code scope_type='PERSONAL'} 且 {@code scope_id=''} 的 DriveItem
 * 行的 {@code scope_id} 回填为 {@code owner_person_oa_id}。回填完成后写入 {@code cloudnext_meta.scope_backfill_v1=done}
 * 标记，避免下次启动重复执行。
 * <p>
 * Runner 在任意环境（空库 / 全量已迁移 / 部分已迁移）下都能安全运行；只要标记存在就直接跳过。
 * <p>
 * 实际的事务性回填逻辑落在独立的 {@link ScopeBackfillService} bean 上，这样 Spring 代理才会拦截
 * {@code @Transactional}；如果把方法和 runner 放同一个类，{@code run -> this.applyScopeBackfillV1()}
 * 是自调用，事务注解会被绕过（surefire 已经踩过这个坑）。
 */
@Component
public class OneTimeBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneTimeBackfillRunner.class);

    private final CloudNextMetaRepository metaRepository;
    private final ScopeBackfillService scopeBackfillService;

    public OneTimeBackfillRunner(
            CloudNextMetaRepository metaRepository,
            ScopeBackfillService scopeBackfillService) {
        this.metaRepository = metaRepository;
        this.scopeBackfillService = scopeBackfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (metaRepository.findByMetaKey(ScopeBackfillService.SCOPE_BACKFILL_V1_KEY).isPresent()) {
            LOGGER.info("scope_backfill_v1 already applied, skipping");
            return;
        }
        scopeBackfillService.applyScopeBackfillV1();
    }
}
