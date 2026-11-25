package com.nextwindow.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    Optional<Snapshot> findFirstBySourceIdOrderByCreatedAtDescIdDesc(Long sourceId);

    List<Snapshot> findTop2BySourceIdOrderByCreatedAtDescIdDesc(Long sourceId);

    Page<Snapshot> findBySourceIdOrderByCreatedAtDescIdDesc(Long sourceId, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT ON (source_id) * FROM snapshot WHERE source_id IN (:ids)
            ORDER BY source_id, created_at DESC, id DESC
            """, nativeQuery = true)
    List<Snapshot> findLatestForSources(@Param("ids") java.util.Collection<Long> sourceIds);

    /** Deletes all but the newest {@code keep} snapshots of each source. */
    @Modifying
    @Query(value = """
            DELETE FROM snapshot WHERE id IN (
              SELECT id FROM (
                SELECT id, row_number() OVER (PARTITION BY source_id ORDER BY created_at DESC, id DESC) AS rn FROM snapshot
              ) ranked WHERE ranked.rn > :keep)
            """, nativeQuery = true)
    int pruneBeyond(@Param("keep") int keep);
}
