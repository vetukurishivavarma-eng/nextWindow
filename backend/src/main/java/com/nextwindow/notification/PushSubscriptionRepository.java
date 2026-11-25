package com.nextwindow.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByIdAndUserId(Long id, Long userId);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, Long userId);

    long countByUserId(Long userId);
}
