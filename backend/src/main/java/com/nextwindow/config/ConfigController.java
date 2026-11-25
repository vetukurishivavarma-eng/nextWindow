package com.nextwindow.config;

import com.nextwindow.notification.PushService;
import com.nextwindow.watch.WatchDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, non-sensitive client configuration. */
@RestController
public class ConfigController {
    public record ClientConfig(boolean demoEnabled, boolean pushConfigured, String vapidPublicKey,
                               List<Integer> intervalOptions, int defaultIntervalMinutes) {}

    private final NextWindowProperties props;
    private final PushService push;

    public ConfigController(NextWindowProperties props, PushService push) {
        this.props = props;
        this.push = push;
    }

    @GetMapping("/api/config")
    public ClientConfig config() {
        boolean pushOk = push.isConfigured();
        return new ClientConfig(props.demo().enabled(), pushOk, pushOk ? props.push().vapidPublicKey() : null,
                WatchDtos.INTERVAL_OPTIONS.stream().filter(i -> i >= props.monitoring().minIntervalMinutes()).toList(),
                props.monitoring().defaultIntervalMinutes());
    }
}
