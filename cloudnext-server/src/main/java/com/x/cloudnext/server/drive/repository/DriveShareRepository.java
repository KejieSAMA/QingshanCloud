package com.x.cloudnext.server.drive.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.x.cloudnext.server.drive.DriveShareTargetType;
import com.x.cloudnext.server.drive.entity.DriveShare;

public interface DriveShareRepository extends JpaRepository<DriveShare, String> {

    List<DriveShare> findByOwnerPersonOaIdOrderByUpdatedAtDesc(String ownerPersonOaId);

    List<DriveShare> findByItemIdAndOwnerPersonOaIdOrderByUpdatedAtDesc(String itemId, String ownerPersonOaId);

    List<DriveShare> findByItemIdOrderByUpdatedAtDesc(String itemId);

    List<DriveShare> findByIdIn(Collection<String> ids);

    List<DriveShare> findByItemIdIn(Collection<String> itemIds);

    List<DriveShare> findByItemIdInAndOwnerPersonOaIdAndRevokedFalse(
            Collection<String> itemIds,
            String ownerPersonOaId);

    List<DriveShare> findByTargetTypeAndTargetIdInOrderByUpdatedAtDesc(DriveShareTargetType targetType, Collection<String> targetIds);

    Optional<DriveShare> findByItemIdAndOwnerPersonOaIdAndTargetTypeAndTargetId(
            String itemId,
            String ownerPersonOaId,
            DriveShareTargetType targetType,
            String targetId);

    /**
     * N+2.4 期限自动清理 — 找出所有"有过期时间且已过期且未被撤销"的 share。
     * expiresAt 是 ISO 8601 字符串，字典序与时间序一致；空字符串视为永久（已被
     * LENGTH > 0 过滤掉）。
     */
    @Query("SELECT s FROM DriveShare s WHERE LENGTH(s.expiresAt) > 0 "
            + "AND s.expiresAt < :nowIso AND s.revoked = false")
    List<DriveShare> findExpiredActiveShares(@Param("nowIso") String nowIso);
}
