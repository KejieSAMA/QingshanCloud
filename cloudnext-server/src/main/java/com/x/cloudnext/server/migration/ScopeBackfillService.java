package com.x.cloudnext.server.migration;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.migration.entity.CloudNextMeta;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

/**
 * Transactional component that owns the {@code scope_backfill_v1} migration
 * step. Lives in its own bean (separate from {@link OneTimeBackfillRunner})
 * so that {@link Transactional} survives Spring proxying — calling
 * {@code applyScopeBackfillV1()} on the same instance from a runner method
 * would self-invoke and bypass the proxy, which is exactly what blew up the
 * iteration-1 surefire run with {@code TransactionRequiredException}.
 */
@Service
public class ScopeBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScopeBackfillService.class);

    static final String SCOPE_BACKFILL_V1_KEY = "scope_backfill_v1";
    private static final String DONE = "done";

    private final CloudNextMetaRepository metaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ScopeBackfillService(CloudNextMetaRepository metaRepository) {
        this.metaRepository = metaRepository;
    }

    /**
     * 执行 scope_backfill_v1：把 PERSONAL 且 scopeId 为空的 DriveItem 回填为
     * ownerPersonOaId，并在同一事务内写入版本标记，避免数据更新与标记落库之间漂移。
     */
    @Transactional
    public void applyScopeBackfillV1() {
        int rowsUpdated = entityManager
                .createQuery(
                        "UPDATE DriveItem d SET d.scopeId = d.ownerPersonOaId "
                                + "WHERE d.scopeType = :scope AND d.scopeId = ''")
                .setParameter("scope", DriveScopeType.PERSONAL)
                .executeUpdate();

        CloudNextMeta marker = new CloudNextMeta();
        marker.setMetaKey(SCOPE_BACKFILL_V1_KEY);
        marker.setMetaValue(DONE);
        metaRepository.save(marker);

        LOGGER.info("scope_backfill_v1 applied: rowsUpdated={}", rowsUpdated);
    }
}
