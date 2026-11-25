package com.nextwindow.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CheckRunRepository extends JpaRepository<CheckRun, Long> {
    Page<CheckRun> findBySourceIdOrderByStartedAtDesc(Long sourceId, Pageable pageable);

    @Query("select c from CheckRun c where c.sourceId in :ids order by c.startedAt desc, c.id desc")
    Page<CheckRun> findBySourceIds(@Param("ids") Collection<Long> sourceIds, Pageable pageable);

    @Query("select c from CheckRun c where c.sourceId in :ids and c.outcome in :outcomes order by c.startedAt desc, c.id desc")
    Page<CheckRun> findBySourceIdsAndOutcomes(@Param("ids") Collection<Long> sourceIds,
                                             @Param("outcomes") Collection<CheckOutcome> outcomes, Pageable pageable);

    List<CheckRun> findTop20BySourceIdOrderByStartedAtDesc(Long sourceId);

    long countBySourceId(Long sourceId);

    @Modifying
    @Query("delete from CheckRun c where c.startedAt < ?1")
    int deleteOlderThan(Instant cutoff);
}
