package com.nextwindow.monitoring.fetch;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.source.FetchMethod;
import com.nextwindow.source.MonitoringSource;
import com.nextwindow.source.UrlNormalizer;
import com.nextwindow.source.UrlRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches publicly accessible static HTML pages responsibly: validates every redirect hop against the
 * SSRF policy, honours robots.txt and crawl-delay, throttles per host, limits size and time, and never
 * attempts to bypass authentication, CAPTCHAs, paywalls or bot protection.
 */
@Component
public class GenericStaticPageFetcher implements SourceFetcher {
    private static final Logger log = LoggerFactory.getLogger(GenericStaticPageFetcher.class);
    private static final String ACCEPT = "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.5";
    private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset=[\"']?([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Duration MAX_INLINE_WAIT = Duration.ofSeconds(15);

    private final SafeHttpClient http;
    private final HostPolicy hostPolicy;
    private final RobotsPolicyService robots;
    private final HostThrottle throttle;
    private final UrlNormalizer normalizer;
    private final NextWindowProperties.Fetch props;

    public GenericStaticPageFetcher(SafeHttpClient http, HostPolicy hostPolicy, RobotsPolicyService robots,
                                    HostThrottle throttle, UrlNormalizer normalizer, NextWindowProperties properties) {
        this.http = http;
        this.hostPolicy = hostPolicy;
        this.robots = robots;
        this.throttle = throttle;
        this.normalizer = normalizer;
        this.props = properties.fetch();
    }

    @Override
    public boolean supports(FetchMethod method) {
        return method == FetchMethod.STATIC_HTTP;
    }

    @Override
    public FetchResult fetch(MonitoringSource source) {
        return fetchUrl(source.getCanonicalUrl());
    }

    @Override
    public FetchResult fetchUrl(String url) {
        FetchResult last = null;
        for (int attempt = 0; attempt <= props.maxRetries(); attempt++) {
            if (attempt > 0) {
                sleep(Duration.ofSeconds(2L * attempt));
            }
            last = fetchOnce(url);
            boolean retryable = last.outcome() == FetchResult.Outcome.TIMEOUT
                    || last.outcome() == FetchResult.Outcome.NETWORK_ERROR
                    || (last.outcome() == FetchResult.Outcome.HTTP_ERROR && last.httpStatus() != null && last.httpStatus() >= 500);
            if (!retryable) break;
        }
        return last;
    }

    private FetchResult fetchOnce(String startUrl) {
        URI uri = URI.create(startUrl);
        for (int hop = 0; hop <= props.maxRedirects(); hop++) {
            String host = uri.getHost();
            int port = RobotsPolicyService.port(uri);
            try {
                hostPolicy.validate(host, port);
            } catch (UrlRejectedException e) {
                return FetchResult.failure(FetchResult.Outcome.UNSAFE_URL, null, e.getMessage());
            }
            RobotsRules rules = robots.rulesFor(uri);
            String pathQuery = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            if (!rules.isAllowed(pathQuery)) {
                return FetchResult.failure(FetchResult.Outcome.BLOCKED, null,
                        "This site's robots.txt does not allow automated monitoring of this page.");
            }
            Duration minDelay = rules.crawlDelay() != null && rules.crawlDelay().compareTo(props.hostMinDelay()) > 0
                    ? rules.crawlDelay() : props.hostMinDelay();
            Duration wait = throttle.reserve(host, minDelay);
            if (!wait.isZero()) {
                if (wait.compareTo(MAX_INLINE_WAIT) > 0) {
                    return FetchResult.rateLimited(null, "Waiting to respect this site's request rate.", wait);
                }
                sleep(wait);
                throttle.reserve(host, minDelay);
            }

            SafeHttpClient.Response r;
            try {
                r = http.get(uri, props.maxBodyBytes(), ACCEPT);
            } catch (SafeHttpClient.FetchException e) {
                return FetchResult.failure(e.outcome(), null, e.getMessage());
            } catch (IOException e) {
                log.debug("Fetch failed for host {}: {}", host, e.getClass().getSimpleName());
                return FetchResult.failure(FetchResult.Outcome.NETWORK_ERROR, null, "Could not connect to the site.");
            }

            int s = r.status();
            if (s >= 300 && s < 400 && s != 304) {
                if (r.location() == null) {
                    return FetchResult.failure(FetchResult.Outcome.HTTP_ERROR, s, "The site sent a redirect without a target.");
                }
                URI next;
                try {
                    next = URI.create(normalizer.normalize(uri.resolve(r.location().trim()).toString()).canonical());
                } catch (RuntimeException e) {
                    return FetchResult.failure(FetchResult.Outcome.UNSAFE_URL, s, "The site redirected to an address we can't monitor.");
                }
                if (!"http".equals(next.getScheme()) && !"https".equals(next.getScheme())) {
                    return FetchResult.failure(FetchResult.Outcome.UNSAFE_URL, s, "The site redirected to an unsupported address.");
                }
                uri = next;
                continue;
            }
            if (s == 401 || s == 403 || s == 402 || s == 451) {
                return FetchResult.failure(FetchResult.Outcome.BLOCKED, s,
                        "The site refused access (HTTP " + s + "). NextWindow does not bypass access restrictions.");
            }
            if (s == 404 || s == 410) {
                return FetchResult.failure(FetchResult.Outcome.NOT_FOUND, s, "The page was not found (HTTP " + s + ").");
            }
            if (s == 429 || s == 503 && r.retryAfter() != null) {
                return FetchResult.rateLimited(s, "The site asked us to slow down (HTTP " + s + ").", parseRetryAfter(r.retryAfter()));
            }
            if (s < 200 || s >= 300) {
                return FetchResult.failure(FetchResult.Outcome.HTTP_ERROR, s, "The site returned an error (HTTP " + s + ").");
            }
            String ct = r.contentType() == null ? "" : r.contentType().toLowerCase(Locale.ROOT);
            if (!(ct.isEmpty() || ct.startsWith("text/html") || ct.startsWith("application/xhtml+xml") || ct.startsWith("text/plain"))) {
                return FetchResult.failure(FetchResult.Outcome.UNSUPPORTED_CONTENT, s,
                        "This address is not a web page (" + ct.split(";")[0] + ").");
            }
            if (r.truncated()) {
                return FetchResult.failure(FetchResult.Outcome.TOO_LARGE, s, "The page is too large to monitor.");
            }
            return FetchResult.success(s, decode(r.body(), ct), ct, uri.toString());
        }
        return FetchResult.failure(FetchResult.Outcome.TOO_MANY_REDIRECTS, null, "The page redirected too many times.");
    }

    static String decode(byte[] body, String contentType) {
        Charset cs = charsetFrom(contentType);
        if (cs != null) return new String(body, cs);
        String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
        Matcher m = META_CHARSET.matcher(head);
        if (m.find()) {
            try {
                return new String(body, Charset.forName(m.group(1)));
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static Charset charsetFrom(String contentType) {
        int i = contentType.indexOf("charset=");
        if (i < 0) return null;
        String name = contentType.substring(i + 8).split(";")[0].replace("\"", "").trim();
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Duration parseRetryAfter(String value) {
        if (value == null) return Duration.ofMinutes(30);
        try {
            long secs = Long.parseLong(value.trim());
            return Duration.ofSeconds(Math.max(60, Math.min(secs, 86_400)));
        } catch (NumberFormatException e) {
            return Duration.ofMinutes(30);
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
