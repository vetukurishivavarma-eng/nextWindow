package com.nextwindow.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {
    private final UrlSafetyValidator v = new UrlSafetyValidator();

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "api.localhost", "printer.local", "db.internal", "metadata.google.internal",
            "127.0.0.1", "127.1.2.3", "10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "[::1]", "[::]", "[fd00:ec2::254]", "[fe80::1]",
            "[::ffff:127.0.0.1]", "[::ffff:169.254.169.254]", "[64:ff9b::a9fe:a9fe]", "[2002:7f00:1::]",
            "2130706433", "0x7f000001", "0x7f.0x0.0x0.0x1", "017700000001", "intranet"})
    void rejectsPrivateLocalAndMetadataTargets(String host) {
        assertThatThrownBy(() -> v.validateHost(host, 443)).isInstanceOf(UrlRejectedException.class);
    }

    @Test
    void rejectsSensitivePorts() {
        assertThatThrownBy(() -> v.validateHost("example.com", 22)).isInstanceOf(UrlRejectedException.class);
        assertThatThrownBy(() -> v.validateHost("example.com", 25)).isInstanceOf(UrlRejectedException.class);
        assertThatCode(() -> v.validateHost("example.com", 8080)).doesNotThrowAnyException();
    }

    @Test
    void allowsPublicHostsAndAddresses() {
        assertThatCode(() -> v.validateHost("example.com", 443)).doesNotThrowAnyException();
        assertThatCode(() -> v.validateHost("93.184.216.34", 80)).doesNotThrowAnyException();
        assertThatCode(() -> v.validateHost("[2606:2800:220:1:248:1893:25c8:1946]", 443)).doesNotThrowAnyException();
    }

    @Test
    void classifiesAddresses() throws Exception {
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("172.32.0.1"))).isTrue();
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("172.20.0.1"))).isFalse();
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("198.18.0.1"))).isFalse();
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("fc00::1"))).isFalse();
        assertThat(UrlSafetyValidator.isPublic(InetAddress.getByName("2001:db8::1"))).isFalse();
    }

    @Test
    void resolveSafeRejectsHostsResolvingToLoopback() {
        assertThatThrownBy(() -> v.resolveSafe("localhost")).isInstanceOf(UrlSafetyValidator.UnsafeAddressException.class);
        assertThatThrownBy(() -> v.validateResolvable("localhost.", 443)).isInstanceOf(UrlRejectedException.class);
    }
}
