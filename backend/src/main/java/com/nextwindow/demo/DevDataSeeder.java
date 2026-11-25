package com.nextwindow.demo;

import com.nextwindow.auth.AuthService;
import com.nextwindow.user.UserRepository;
import com.nextwindow.watch.AlertMode;
import com.nextwindow.watch.WatchDtos;
import com.nextwindow.watch.WatchService;
import com.nextwindow.watch.WatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Development-only seed: a demo account with watches on demo sources. Requires both
 * {@code nextwindow.demo.enabled} and {@code nextwindow.demo.seed-user}; never enable in production.
 */
@Component
@ConditionalOnProperty(prefix = "nextwindow.demo", name = {"enabled", "seed-user"}, havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final AuthService auth;
    private final WatchService watches;
    private final String email;
    private final String password;

    public DevDataSeeder(UserRepository users, AuthService auth, WatchService watches,
                         @Value("${nextwindow.demo.seed-email:demo@nextwindow.dev}") String email,
                         @Value("${nextwindow.demo.seed-password:demo-pass-2024}") String password) {
        this.users = users;
        this.auth = auth;
        this.watches = watches;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(email)) return;
        var user = auth.register(email, password);
        watches.create(user.getId(), new WatchDtos.CreateWatchRequest("demo://headphones", null, WatchType.PRICE_DROP,
                null, null, AlertMode.REPEAT, 0, 15, null));
        watches.create(user.getId(), new WatchDtos.CreateWatchRequest("demo://sneakers", null, WatchType.STOCK_AVAILABLE,
                null, null, AlertMode.ONE_TIME, 0, 15, null));
        watches.create(user.getId(), new WatchDtos.CreateWatchRequest("demo://console", "Console under €450", WatchType.PRICE_BELOW,
                new BigDecimal("450.00"), null, AlertMode.REPEAT, 0, 15, null));
        watches.create(user.getId(), new WatchDtos.CreateWatchRequest("demo://careers", "Java role at Lumen Labs", WatchType.KEYWORD_FOUND,
                null, "Java Engineer", AlertMode.REPEAT, 0, 15, null));
        log.info("Seeded development demo account {} (dev only)", email);
    }
}
