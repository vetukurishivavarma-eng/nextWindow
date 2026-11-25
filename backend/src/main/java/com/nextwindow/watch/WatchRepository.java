package com.nextwindow.watch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WatchRepository extends JpaRepository<Watch, Long> {
    Optional<Watch> findByIdAndUserId(Long id, Long userId);

    Page<Watch> findByUserId(Long userId, Pageable pageable);

    Page<Watch> findByUserIdAndActive(Long userId, boolean active, Pageable pageable);

    @Query("select w from Watch w where w.userId = :uid and (lower(w.title) like :q) order by w.createdAt desc")
    Page<Watch> search(@Param("uid") Long userId, @Param("q") String likeQuery, Pageable pageable);

    List<Watch> findBySourceIdAndActiveTrueOrderByIdAsc(Long sourceId);

    List<Watch> findByUserId(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndActive(Long userId, boolean active);

    boolean existsBySourceIdAndUserId(Long sourceId, Long userId);

    @Query("select distinct w.sourceId from Watch w where w.userId = ?1")
    List<Long> findSourceIdsByUserId(Long userId);

    @Modifying
    @Query("update Watch w set w.active = false, w.completedAt = ?1, w.updatedAt = ?1 where w.active = true and w.expiresAt is not null and w.expiresAt < ?1")
    int expireDue(Instant now);
}
