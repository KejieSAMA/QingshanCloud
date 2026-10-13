package com.x.cloudnext.server.drive.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.x.cloudnext.server.drive.entity.DrivePublicLink;

/**
 * Spring Data repository for {@link DrivePublicLink}.
 *
 * <p>Method names map directly to {@code DrivePublicLinkService}:
 * <ul>
 *   <li>{@link #findByToken(String)} — public endpoint resolution
 *       ({@code GET /public/links/{token}/...}).</li>
 *   <li>{@link #findByCreatorPersonOaIdAndRevokedFalseOrderByCreatedAtDesc(String)}
 *       — "list my active links" for the management UI on
 *       {@code GET /api/links}.</li>
 *   <li>{@link #incrementDownloadCountIfWithinLimit(String, Instant)} —
 *       atomic, race-free download accounting that simultaneously enforces
 *       the {@code revoked} flag and the {@code downloadLimit} cap.</li>
 * </ul>
 *
 * <p>The atomic increment is the single source of truth for download
 * admission: it returns the JDBC update count, so a return value of {@code 0}
 * means "rejected, do not open the file stream", and {@code 1} means
 * "admitted, the count has already been bumped". This prevents the classic
 * read-modify-write race where two concurrent downloads each see
 * {@code downloadCount < downloadLimit} and both proceed.
 */
public interface DrivePublicLinkRepository extends JpaRepository<DrivePublicLink, String> {

    Optional<DrivePublicLink> findByToken(String token);

    List<DrivePublicLink> findByCreatorPersonOaIdAndRevokedFalseOrderByCreatedAtDesc(String creatorPersonOaId);

    List<DrivePublicLink> findByItemIdOrderByCreatedAtDesc(String itemId);

    List<DrivePublicLink> findByItemIdInAndCreatorPersonOaIdAndRevokedFalse(
            Collection<String> itemIds,
            String creatorPersonOaId);

    /**
     * Atomically increments {@code downloadCount} by one and refreshes
     * {@code updatedAt}, but only when the link is still admissible:
     * not revoked, and either {@code downloadLimit < 0} (unlimited) or
     * {@code downloadCount < downloadLimit}.
     *
     * <p>Callers MUST inspect the return value: {@code 0} rows touched means
     * the link was already revoked or has hit its limit and the download
     * stream MUST NOT be opened. {@code 1} row touched means the call has
     * already paid for one download and the stream may be opened.
     *
     * @param id  the {@code DrivePublicLink} id (not the token).
     * @param now the timestamp written to {@code updatedAt}; converted to
     *            ISO-8601 to match the entity's {@code String} column.
     * @return the JDBC update row count (0 = refused, 1 = admitted).
     */
    default int incrementDownloadCountIfWithinLimit(String id, Instant now) {
        return incrementDownloadCountIfWithinLimitInternal(id, now.toString());
    }

    @Modifying
    @Transactional
    @Query("UPDATE DrivePublicLink l "
            + "SET l.downloadCount = l.downloadCount + 1, l.updatedAt = :now "
            + "WHERE l.id = :id "
            + "AND l.revoked = false "
            + "AND (l.downloadLimit < 0 OR l.downloadCount < l.downloadLimit)")
    int incrementDownloadCountIfWithinLimitInternal(@Param("id") String id, @Param("now") String now);
}
