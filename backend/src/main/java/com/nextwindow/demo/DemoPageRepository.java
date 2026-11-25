package com.nextwindow.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DemoPageRepository extends JpaRepository<DemoPage, String> {
    /** Atomically consumes one pending simulated failure. */
    @Modifying
    @Transactional
    @Query("update DemoPage d set d.failNext = d.failNext - 1 where d.slug = ?1 and d.failNext > 0")
    int consumeFailure(String slug);
}
