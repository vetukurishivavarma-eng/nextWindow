package com.nextwindow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, Long userId);

    long countByUserId(Long userId);

    // Bulk statements keep delivery bookkeeping idempotent when several dispatches for one user run concurrently.
    @Modifying
    @Query("delete from PushSubscription s where s.id = ?1")
    int deleteQuietly(Long id);

    @Modifying
    @Query("update PushSubscription s set s.failureCount = 0, s.lastSuccessAt = ?2 where s.id = ?1")
    int markDelivered(Long id, Instant at);

    @Modifying
    @Query("update PushSubscription s set s.failureCount = s.failureCount + 1 where s.id = ?1")
    int markFailed(Long id);

    @Modifying
    @Query("delete from PushSubscription s where s.id = ?1 and s.failureCount >= ?2")
    int deleteIfFailedAtLeast(Long id, int failures);
}
