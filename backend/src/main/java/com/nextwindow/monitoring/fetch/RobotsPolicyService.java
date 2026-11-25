package com.nextwindow.monitoring.fetch;

import com.nextwindow.config.NextWindowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fetches and caches robots.txt per origin and answers whether a URL may be fetched. */
@Service
public class RobotsPolicyService {
    private static final Logger log = LoggerFactory.getLogger(RobotsPolicyService.class);
    private static final long MAX_ROBOTS_BYTES = 512 * 1024;
    private static final Duration UNREACHABLE_TTL = Duration.ofMinutes(30);
    private static final String AGENT_TOKEN = "nextwindowbot";

    private record Entry(RobotsRules rules, Instant expiresAt) {}

    private final SafeHttpClient http;
    private final HostPolicy hostPolicy;
    private final NextWindowProperties.Fetch props;
    private final Clock clock;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public RobotsPolicyService(SafeHttpClient http, HostPolicy hostPolicy, NextWindowProperties properties, Clock clock) {
        this.http = http;
        this.hostPolicy = hostPolicy;
        this.props = properties.fetch();
        this.clock = clock;
    }

    public RobotsRules rulesFor(URI uri) {
        if (!props.respectRobots()) return RobotsRules.allowAll();
        String origin = origin(uri);
        Instant now = clock.instant();
        Entry e = cache.get(origin);
        if (e != null && e.expiresAt().isAfter(now)) return e.rules();
        Entry fresh = load(origin, now);
        if (cache.size() > 5000) cache.entrySet().removeIf(x -> x.getValue().expiresAt().isBefore(now));
        cache.put(origin, fresh);
        return fresh.rules();
    }

    public void clear() {
        cache.clear();
    }

    private Entry load(String origin, Instant now) {
        URI target = URI.create(origin + "/robots.txt");
        try {
            for (int hop = 0; hop <= 5; hop++) {
                hostPolicy.validate(target.getHost(), port(target));
                SafeHttpClient.Response r = http.get(target, MAX_ROBOTS_BYTES, "text/plain,*/*;q=0.1");
                int s = r.status();
                if (s >= 300 && s < 400 && r.location() != null) {
                    target = target.resolve(r.location());
                    if (!"http".equals(target.getScheme()) && !"https".equals(target.getScheme())) break;
                    continue;
                }
                if (s >= 200 && s < 300) {
                    return new Entry(RobotsRules.parse(new String(r.body(), StandardCharsets.UTF_8), AGENT_TOKEN),
                            now.plus(props.robotsCacheTtl()));
                }
                if (s == 429 || s >= 500) {
                    return new Entry(RobotsRules.disallowAll(), now.plus(UNREACHABLE_TTL));
                }
                // 4xx: no robots policy, crawling allowed (RFC 9309 §2.3.1.3)
                return new Entry(RobotsRules.allowAll(), now.plus(props.robotsCacheTtl()));
            }
            return new Entry(RobotsRules.allowAll(), now.plus(props.robotsCacheTtl()));
        } catch (IOException | RuntimeException ex) {
            log.debug("robots.txt unreachable for {}: {}", origin, ex.getMessage());
            return new Entry(RobotsRules.disallowAll(), now.plus(UNREACHABLE_TTL));
        }
    }

    static int port(URI u) {
        return u.getPort() != -1 ? u.getPort() : ("https".equals(u.getScheme()) ? 443 : 80);
    }

    private static String origin(URI u) {
        String base = u.getScheme() + "://" + u.getHost();
        return u.getPort() == -1 ? base : base + ":" + u.getPort();
    }
}
