package com.nextwindow.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MonitoringSourceRepository extends JpaRepository<MonitoringSource, Long> {

    Optional<MonitoringSource> findByCanonicalUrl(String canonicalUrl);

    /** Race-safe registration: concurrent inserts of the same canonical URL collapse into one row. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO monitoring_source (canonical_url, host, source_type, fetch_method, status, next_fetch_at,
                                           check_interval_minutes, failure_count, created_at, updated_at, version)
            VALUES (:url, :host, :type, :method, 'PENDING', :now, :interval, 0, :now, :now, 0)
            ON CONFLICT (canonical_url) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("url") String url, @Param("host") String host, @Param("type") String type,
                       @Param("method") String method, @Param("interval") int interval, @Param("now") Instant now);

    /**
     * Atomically claims due sources that have at least one active watch. {@code FOR UPDATE SKIP LOCKED}
     * plus the lease columns make this safe for several workers: a row is only ever claimed by one.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE monitoring_source SET locked_by = :worker, locked_until = :until
            WHERE id IN (
                SELECT s.id FROM monitoring_source s
                WHERE s.status <> 'DISABLED'
                  AND s.next_fetch_at <= :now
                  AND (s.locked_until IS NULL OR s.locked_until < :now)
                  AND EXISTS (SELECT 1 FROM watch w WHERE w.source_id = s.id AND w.active)
                ORDER BY s.next_fetch_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED)
            RETURNING id
            """, nativeQuery = true)
    List<Long> claimDue(@Param("worker") String worker, @Param("now") Instant now,
                        @Param("until") Instant until, @Param("limit") int limit);

    /** Claims a single source regardless of schedule (manual "check now"). Returns 1 if the lease was acquired. */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE monitoring_source SET locked_by = :worker, locked_until = :until
            WHERE id = :id AND (locked_until IS NULL OR locked_until < :now)
            """, nativeQuery = true)
    int tryLock(@Param("id") Long id, @Param("worker") String worker, @Param("now") Instant now, @Param("until") Instant until);

    @Modifying
    @Transactional
    @Query(value = "UPDATE monitoring_source SET locked_by = NULL, locked_until = NULL WHERE id = :id AND locked_by = :worker",
            nativeQuery = true)
    int release(@Param("id") Long id, @Param("worker") String worker);

    @Query(value = "SELECT MIN(w.check_interval_minutes) FROM watch w WHERE w.source_id = :id AND w.active", nativeQuery = true)
    Integer minActiveWatchInterval(@Param("id") Long id);

    @Query(value = "SELECT COUNT(*) FROM watch w WHERE w.source_id = :id", nativeQuery = true)
    long countWatches(@Param("id") Long id);
}
