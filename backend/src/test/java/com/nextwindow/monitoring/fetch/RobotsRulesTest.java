package com.nextwindow.monitoring.fetch;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsRulesTest {
    @Test
    void appliesWildcardGroupWithLongestMatch() {
        var r = RobotsRules.parse("""
                User-agent: *
                Disallow: /cart
                Disallow: /private/
                Allow: /private/public
                Disallow: /*.pdf$
                Crawl-delay: 5
                """, "nextwindowbot");
        assertThat(r.isAllowed("/")).isTrue();
        assertThat(r.isAllowed("/cart/checkout")).isFalse();
        assertThat(r.isAllowed("/private/x")).isFalse();
        assertThat(r.isAllowed("/private/public/page")).isTrue();
        assertThat(r.isAllowed("/docs/a.pdf")).isFalse();
        assertThat(r.isAllowed("/docs/a.pdf?x=1")).isTrue();
        assertThat(r.crawlDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void specificAgentGroupOverridesWildcard() {
        var r = RobotsRules.parse("""
                User-agent: *
                Disallow: /

                User-agent: NextWindowBot
                Disallow: /admin
                """, "nextwindowbot");
        assertThat(r.isAllowed("/products/1")).isTrue();
        assertThat(r.isAllowed("/admin")).isFalse();
    }

    @Test
    void disallowAllForOurAgent() {
        var r = RobotsRules.parse("User-agent: nextwindowbot\nUser-agent: other\nDisallow: /\n", "nextwindowbot");
        assertThat(r.isAllowed("/anything")).isFalse();
        assertThat(r.isAllowed("/robots.txt")).isTrue();
        assertThat(RobotsRules.parse("", "nextwindowbot").isAllowed("/x")).isTrue();
        assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n", "nextwindowbot").isAllowed("/x")).isTrue();
    }
}
