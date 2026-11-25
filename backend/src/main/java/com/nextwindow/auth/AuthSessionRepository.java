package com.nextwindow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByTokenHash(String tokenHash);

    List<AuthSession> findByUserIdOrderByLastUsedAtDesc(Long userId);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < ?1")
    int deleteExpired(Instant now);

    @Modifying
    @Query("delete from AuthSession s where s.userId = ?1 and s.id <> ?2")
    int deleteOtherSessions(Long userId, Long keepSessionId);
}
