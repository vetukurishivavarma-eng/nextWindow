package com.nextwindow.monitoring.fetch;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.source.UrlNormalizer;
import com.nextwindow.source.UrlRejectedException;
import com.nextwindow.source.UrlSafetyValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real HTTP fetch path against a loopback server. A test host policy permits only
 * 127.0.0.1 so we can verify redirects to other internal targets are still refused.
 */
class GenericStaticPageFetcherTest {
    private HttpServer server;
    private String base;
    private GenericStaticPageFetcher fetcher;
    private SafeHttpClient http;

    static NextWindowProperties props(Map<String, String> extra) {
        Map<String, String> m = new java.util.HashMap<>(Map.of("nextwindow.fetch.host-min-delay", "0s",
                "nextwindow.fetch.max-retries", "0", "nextwindow.fetch.max-body-bytes", "20000"));
        m.putAll(extra);
        return new Binder(new MapConfigurationPropertySource(m)).bindOrCreate("nextwindow", NextWindowProperties.class);
    }

    /** Allows only the loopback test server; everything else goes through the production policy. */
    static class LoopbackOnlyPolicy implements HostPolicy {
        final PublicInternetHostPolicy real = new PublicInternetHostPolicy(new UrlSafetyValidator());

        public void validate(String host, int port) {
            if (!host.equals("127.0.0.1")) real.validate(host, port);
        }

        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (host.equals("127.0.0.1")) return new InetAddress[]{InetAddress.getLoopbackAddress()};
            return real.resolve(host);
        }
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route("/robots.txt", 200, "text/plain", "User-agent: *\nDisallow: /secret\n", Map.of());
        route("/product", 200, "text/html; charset=utf-8", "<html><body><h1>Hi</h1></body></html>", Map.of());
        route("/redirect-ok", 302, "text/html", "", Map.of("Location", "/product"));
        route("/redirect-metadata", 302, "text/html", "", Map.of("Location", "http://169.254.169.254/latest/meta-data/"));
        route("/redirect-localhost", 301, "text/html", "", Map.of("Location", "http://localhost:8080/admin"));
        route("/loop", 302, "text/html", "", Map.of("Location", "/loop"));
        route("/forbidden", 403, "text/html", "no", Map.of());
        route("/gone", 404, "text/html", "no", Map.of());
        route("/slow-down", 429, "text/html", "", Map.of("Retry-After", "120"));
        route("/image", 200, "image/png", "PNG", Map.of());
        route("/huge", 200, "text/html", "x".repeat(50_000), Map.of());
        route("/secret", 200, "text/html", "secret", Map.of());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        NextWindowProperties p = props(Map.of());
        HostPolicy policy = new LoopbackOnlyPolicy();
        http = new SafeHttpClient(policy, p);
        fetcher = new GenericStaticPageFetcher(http, policy, new RobotsPolicyService(http, policy, p, Clock.systemUTC()),
                new HostThrottle(Clock.systemUTC()), new UrlNormalizer(), p);
    }

    private void route(String path, int status, String type, String body, Map<String, String> headers) {
        server.createContext(path, ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", type);
            headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) ex.getResponseBody().write(b);
            ex.close();
        });
    }

    @AfterEach
    void stop() throws IOException {
        server.stop(0);
        http.close();
    }

    @Test
    void fetchesHtmlAndFollowsSafeRedirects() {
        var r = fetcher.fetchUrl(base + "/redirect-ok");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.body()).contains("<h1>Hi</h1>");
        assertThat(r.finalUrl()).endsWith("/product");
    }

    @Test
    void refusesRedirectsToInternalAddresses() {
        assertThat(fetcher.fetchUrl(base + "/redirect-metadata").outcome()).isEqualTo(FetchResult.Outcome.UNSAFE_URL);
        assertThat(fetcher.fetchUrl(base + "/redirect-localhost").outcome()).isEqualTo(FetchResult.Outcome.UNSAFE_URL);
    }

    @Test
    void mapsHttpErrorsAndLimits() {
        assertThat(fetcher.fetchUrl(base + "/forbidden").outcome()).isEqualTo(FetchResult.Outcome.BLOCKED);
        assertThat(fetcher.fetchUrl(base + "/gone").outcome()).isEqualTo(FetchResult.Outcome.NOT_FOUND);
        var limited = fetcher.fetchUrl(base + "/slow-down");
        assertThat(limited.outcome()).isEqualTo(FetchResult.Outcome.RATE_LIMITED);
        assertThat(limited.retryAfter()).hasSeconds(120);
        assertThat(fetcher.fetchUrl(base + "/image").outcome()).isEqualTo(FetchResult.Outcome.UNSUPPORTED_CONTENT);
        assertThat(fetcher.fetchUrl(base + "/huge").outcome()).isEqualTo(FetchResult.Outcome.TOO_LARGE);
        assertThat(fetcher.fetchUrl(base + "/loop").outcome()).isEqualTo(FetchResult.Outcome.TOO_MANY_REDIRECTS);
    }

    @Test
    void respectsRobotsTxt() {
        var r = fetcher.fetchUrl(base + "/secret");
        assertThat(r.outcome()).isEqualTo(FetchResult.Outcome.BLOCKED);
        assertThat(r.message()).contains("robots.txt");
    }

    @Test
    void productionPolicyNeverConnectsToLoopback() throws IOException {
        NextWindowProperties p = props(Map.of());
        HostPolicy prod = new PublicInternetHostPolicy(new UrlSafetyValidator());
        SafeHttpClient client = new SafeHttpClient(prod, p);
        var f = new GenericStaticPageFetcher(client, prod, new RobotsPolicyService(client, prod, p, Clock.systemUTC()),
                new HostThrottle(Clock.systemUTC()), new UrlNormalizer(), p);
        assertThat(f.fetchUrl(base + "/product").outcome()).isEqualTo(FetchResult.Outcome.UNSAFE_URL);
        assertThat(f.fetchUrl("http://localhost:" + server.getAddress().getPort() + "/product").outcome())
                .isEqualTo(FetchResult.Outcome.UNSAFE_URL);
        client.close();
    }

    @Test
    void dnsResolverRejectsHostsResolvingPrivately() {
        HostPolicy prod = new PublicInternetHostPolicy(new UrlSafetyValidator());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> prod.resolve("localhost"))
                .isInstanceOf(UrlSafetyValidator.UnsafeAddressException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> prod.validate("10.1.1.1", 80))
                .isInstanceOf(UrlRejectedException.class);
    }
}
