package com.x.cloudnext.server.drive.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.entity.DriveItem;

public interface DriveItemRepository extends JpaRepository<DriveItem, String> {
    boolean existsByStorageKey(String storageKey);

    List<DriveItem> findByOwnerPersonOaIdAndDeletedFalseAndParentId(String ownerPersonOaId, String parentId);

    List<DriveItem> findByOwnerPersonOaIdAndDeletedFalse(String ownerPersonOaId);

    List<DriveItem> findByOwnerPersonOaIdAndParentId(String ownerPersonOaId, String parentId);

    List<DriveItem> findByOwnerPersonOaIdAndDeletedFalseAndParentIdAndName(String ownerPersonOaId, String parentId, String name);

    List<DriveItem> findByOwnerPersonOaIdAndDeletedTrueOrderByDeletedAtDesc(String ownerPersonOaId);

    List<DriveItem> findByOwnerPersonOaIdAndDeletedTrue(String ownerPersonOaId);

    List<DriveItem> findByDeletedTrue();

    Page<DriveItem> findByOwnerPersonOaIdAndDeletedFalseOrderByUpdatedAtDesc(String ownerPersonOaId, Pageable pageable);

    Page<DriveItem> findByScopeTypeAndScopeIdAndDeletedFalseOrderByUpdatedAtDesc(
            DriveScopeType scopeType,
            String scopeId,
            Pageable pageable);

    Page<DriveItem> findByOwnerPersonOaIdAndDeletedFalseAndItemTypeAndLastAccessedAtNotOrderByLastAccessedAtDesc(
            String ownerPersonOaId,
            DriveItemType itemType,
            String lastAccessedAt,
            Pageable pageable);

    Optional<DriveItem> findByIdAndOwnerPersonOaId(String id, String ownerPersonOaId);

    // Scope-aware queries (PERSONAL / WORKSPACE).
    // 工作区目录浏览：scope_type + scope_id + deleted=false + parent_id
    List<DriveItem> findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
            DriveScopeType scopeType,
            String scopeId,
            String parentId);

    List<DriveItem> findByScopeTypeAndScopeIdAndDeletedFalseAndParentIdAndName(
            DriveScopeType scopeType,
            String scopeId,
            String parentId,
            String name);

    List<DriveItem> findByScopeTypeAndScopeIdAndParentId(
            DriveScopeType scopeType,
            String scopeId,
            String parentId);

    List<DriveItem> findByScopeTypeAndScopeIdAndDeletedFalse(
            DriveScopeType scopeType,
            String scopeId);

    List<DriveItem> findByScopeTypeAndScopeId(
            DriveScopeType scopeType,
            String scopeId);

    // 工作区回收站隔离：scope_type + scope_id + deleted=true（无序）
    List<DriveItem> findByScopeTypeAndScopeIdAndDeletedTrue(
            DriveScopeType scopeType,
            String scopeId);

    // 工作区回收站隔离（按删除时间倒序）；与个人 trash 既有方法风格一致
    List<DriveItem> findByScopeTypeAndScopeIdAndDeletedTrueOrderByDeletedAtDesc(
            DriveScopeType scopeType,
            String scopeId);

    List<DriveItem> findByIdIn(Collection<String> ids);

    @Modifying
    @Query("UPDATE DriveItem i SET i.lastAccessedAt = :lastAccessedAt WHERE i.id = :id")
    int updateLastAccessedAt(@Param("id") String id, @Param("lastAccessedAt") String lastAccessedAt);
}
