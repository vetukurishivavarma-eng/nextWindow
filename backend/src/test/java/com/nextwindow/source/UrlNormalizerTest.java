package com.nextwindow.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlNormalizerTest {
    private final UrlNormalizer n = new UrlNormalizer();

    @Test
    void removesTrackingAndSessionParametersButKeepsContentParameters() {
        String c = n.normalize("https://Shop.Example.com/item?utm_source=x&size=10&gclid=abc&color=red&fbclid=1&jsessionid=XYZ#reviews").canonical();
        assertThat(c).isEqualTo("https://shop.example.com/item?color=red&size=10");
    }

    @Test
    void equivalentUrlsShareCanonicalForm() {
        assertThat(n.normalize("example.com").canonical()).isEqualTo("https://example.com/");
        assertThat(n.normalize("https://example.com:443/a/./b/../c").canonical()).isEqualTo("https://example.com/a/c");
        assertThat(n.normalize("http://example.com:80/x").canonical()).isEqualTo("http://example.com/x");
        assertThat(n.normalize("https://example.com:8443/x").canonical()).isEqualTo("https://example.com:8443/x");
        assertThat(n.normalize("https://example.com/p;jsessionid=abc123?id=5").canonical()).isEqualTo("https://example.com/p?id=5");
    }

    @Test
    void keepsRepeatedParameterOrder() {
        assertThat(n.normalize("https://example.com/s?tag=b&q=1&tag=a").canonical()).isEqualTo("https://example.com/s?q=1&tag=b&tag=a");
    }

    @Test
    void preservesPathCase() {
        assertThat(n.normalize("https://example.com/Product/ABC").canonical()).isEqualTo("https://example.com/Product/ABC");
    }

    @Test
    void rejectsUnsupportedOrUnsafeForms() {
        assertThatThrownBy(() -> n.normalize("ftp://example.com/file")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("file:///etc/passwd")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("javascript:alert(1)")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("https://user:pass@example.com/")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("  ")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("https://exa\nmple.com")).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> n.normalize("https://" + "a".repeat(2100) + ".com")).isInstanceOf(UrlRejectedException.class);
    }

    @Test
    void normalizesDemoUrls() {
        var d = n.normalize("demo://Headphones/extra?x=1");
        assertThat(d.canonical()).isEqualTo("demo://headphones");
        assertThat(d.isDemo()).isTrue();
        assertThatThrownBy(() -> n.normalize("demo://../etc")).isInstanceOf(UrlRejectedException.class);
    }
}
