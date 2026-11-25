package com.nextwindow.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Alert> findByUserIdAndReadFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Alert> findByUserIdAndWatchIdOrderByCreatedAtDescIdDesc(Long userId, Long watchId, Pageable pageable);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    long countByUserIdAndWatchIdAndReadFalse(Long userId, Long watchId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    boolean existsByWatchIdAndFingerprintAndCreatedAtAfter(Long watchId, String fingerprint, Instant after);

    @Modifying
    @Query("update Alert a set a.read = true where a.userId = ?1 and a.read = false")
    int markAllRead(Long userId);
}
