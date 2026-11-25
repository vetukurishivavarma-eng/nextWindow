package com.nextwindow.monitoring.fetch;

import com.nextwindow.config.NextWindowProperties;
import com.nextwindow.source.UrlSafetyValidator;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Single-hop HTTP GET with SSRF-safe DNS (validated addresses are pinned to the connection), no cookies,
 * no automatic redirects or retries, and a hard cap on the decoded body size.
 */
@Component
public class SafeHttpClient {

    public record Response(int status, byte[] body, boolean truncated, String contentType, String location, String retryAfter) {}

    public static class FetchException extends IOException {
        private final FetchResult.Outcome outcome;

        public FetchException(FetchResult.Outcome outcome, String message) {
            super(message);
            this.outcome = outcome;
        }

        public FetchResult.Outcome outcome() { return outcome; }
    }

    private final CloseableHttpClient client;
    private final PoolingHttpClientConnectionManager connectionManager;
    private final NextWindowProperties.Fetch props;

    public SafeHttpClient(HostPolicy hostPolicy, NextWindowProperties properties) {
        this.props = properties.fetch();
        DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return hostPolicy.resolve(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        this.connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(props.connectTimeout()))
                        .setSocketTimeout(Timeout.of(props.readTimeout()))
                        .build())
                .setMaxConnTotal(32)
                .setMaxConnPerRoute(2)
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAutomaticRetries()
                .disableAuthCaching()
                .setUserAgent(props.userAgent())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(props.readTimeout()))
                        .setConnectionRequestTimeout(Timeout.of(props.connectTimeout()))
                        .build())
                .build();
    }

    public Response get(URI uri, long maxBytes, String accept) throws IOException {
        HttpGet get = new HttpGet(uri);
        get.setHeader("Accept", accept);
        get.setHeader("Accept-Language", "en;q=0.9,*;q=0.5");
        try {
            return client.execute(get, response -> toResponse(response, maxBytes));
        } catch (UrlSafetyValidator.UnsafeAddressException e) {
            throw new FetchException(FetchResult.Outcome.UNSAFE_URL, "The site resolves to a non-public address.");
        } catch (SocketTimeoutException e) {
            throw new FetchException(FetchResult.Outcome.TIMEOUT, "The site took too long to respond.");
        } catch (UnknownHostException e) {
            throw new FetchException(FetchResult.Outcome.NETWORK_ERROR, "The site's address could not be resolved.");
        }
    }

    private Response toResponse(ClassicHttpResponse response, long maxBytes) throws IOException {
        HttpEntity entity = response.getEntity();
        byte[] body = new byte[0];
        boolean truncated = false;
        String contentType = header(response, "Content-Type");
        if (entity != null) {
            if (contentType == null && entity.getContentType() != null) contentType = entity.getContentType();
            if (response.getCode() >= 200 && response.getCode() < 300) {
                long started = System.nanoTime();
                long deadlineNanos = props.readTimeout().multipliedBy(3).toNanos();
                try (InputStream in = entity.getContent()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > maxBytes) {
                            truncated = true;
                            break;
                        }
                        out.write(buf, 0, n);
                        if (System.nanoTime() - started > deadlineNanos) {
                            throw new FetchException(FetchResult.Outcome.TIMEOUT, "The site sent its response too slowly.");
                        }
                    }
                    body = out.toByteArray();
                }
            }
        }
        return new Response(response.getCode(), body, truncated, contentType,
                header(response, "Location"), header(response, "Retry-After"));
    }

    private static String header(ClassicHttpResponse r, String name) {
        Header h = r.getFirstHeader(name);
        return h == null ? null : h.getValue();
    }

    @PreDestroy
    public void close() throws IOException {
        client.close();
        connectionManager.close();
    }
}
