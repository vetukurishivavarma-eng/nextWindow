package com.nextwindow.source;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard. Rejects hosts that are, or resolve to, loopback, private, link-local (including cloud
 * metadata at 169.254.169.254), carrier-grade NAT, multicast, reserved or documentation ranges.
 * The fetcher calls {@link #resolveSafe(String)} from its DNS resolver so the validated addresses are the
 * exact ones connected to, which defeats DNS rebinding between validation and connection.
 */
@Component
public class UrlSafetyValidator {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "metadata", "metadata.google.internal", "metadata.goog", "instance-data", "kubernetes.default");
    private static final List<String> BLOCKED_SUFFIXES = List.of(
            ".localhost", ".local", ".internal", ".intranet", ".lan", ".home", ".corp", ".localdomain", ".home.arpa",
            ".svc", ".cluster.local");
    private static final Set<Integer> ALLOWED_LOW_PORTS = Set.of(80, 443);

    public static class UnsafeAddressException extends UnknownHostException {
        public UnsafeAddressException(String message) { super(message); }
    }

    /** Syntactic checks that need no network access. */
    public void validateHost(String host, int port) {
        String h = host.toLowerCase(Locale.ROOT);
        if (port < 1 || port > 65535 || (port < 1024 && !ALLOWED_LOW_PORTS.contains(port))) {
            throw new UrlRejectedException("That port is not allowed. Use a standard web address.");
        }
        if (BLOCKED_HOSTS.contains(h) || BLOCKED_SUFFIXES.stream().anyMatch(h::endsWith)) {
            throw new UrlRejectedException("Local and internal network addresses cannot be monitored.");
        }
        if (h.startsWith("[") || isIpLiteral(h)) {
            InetAddress addr = parseLiteral(h);
            if (addr == null || !isPublic(addr)) {
                throw new UrlRejectedException("Private, local or reserved IP addresses cannot be monitored.");
            }
            return;
        }
        if (!h.contains(".")) {
            throw new UrlRejectedException("Use a full public domain name (for example example.com).");
        }
    }

    /** Resolves a host and returns its addresses only if every one is public. */
    public InetAddress[] resolveSafe(String host) throws UnknownHostException {
        InetAddress[] all = InetAddress.getAllByName(host);
        if (all.length == 0) throw new UnknownHostException(host);
        for (InetAddress a : all) {
            if (!isPublic(a)) {
                throw new UnsafeAddressException("Host resolves to a non-public address");
            }
        }
        return all;
    }

    /** Full check (syntax + DNS). Used when a user submits a URL. */
    public void validateResolvable(String host, int port) {
        validateHost(host, port);
        try {
            resolveSafe(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnsafeAddressException e) {
            throw new UrlRejectedException("That address points to a private or internal network and cannot be monitored.");
        } catch (UnknownHostException e) {
            throw new UrlRejectedException("We couldn't find that website. Check the address and try again.");
        }
    }

    public static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) return isPublicV4(b);
        if (a instanceof Inet6Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if ((b0 & 0xfe) == 0xfc) return false;                 // fc00::/7 unique local
            if (b0 == 0xfe && (b1 & 0xc0) == 0xc0) return false;   // fec0::/10 site local (deprecated)
            if (b0 == 0x20 && b1 == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) return false; // 2001:db8::/32
            if (b0 == 0x20 && b1 == 0x02) {                        // 2002::/16 6to4 embeds IPv4
                return isPublicV4(Arrays.copyOfRange(b, 2, 6));
            }
            if (isZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) { // ::ffff:a.b.c.d
                return isPublicV4(Arrays.copyOfRange(b, 12, 16));
            }
            if (isZero(b, 0, 12)) return false;                    // ::/96 IPv4-compatible and ::
            if (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) { // 64:ff9b::/96 NAT64
                return isPublicV4(Arrays.copyOfRange(b, 12, 16));
            }
            if (b0 == 0x01 && b1 == 0x00 && isZero(b, 2, 8)) return false; // 100::/64 discard
            return true;
        }
        return false;
    }

    private static boolean isPublicV4(byte[] b) {
        int a0 = b[0] & 0xff, a1 = b[1] & 0xff, a2 = b[2] & 0xff;
        if (a0 == 0 || a0 == 10 || a0 == 127) return false;
        if (a0 == 100 && a1 >= 64 && a1 <= 127) return false;       // 100.64.0.0/10 CGNAT
        if (a0 == 169 && a1 == 254) return false;                   // link local + metadata
        if (a0 == 172 && a1 >= 16 && a1 <= 31) return false;
        if (a0 == 192 && a1 == 168) return false;
        if (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) return false; // 192.0.0.0/24, 192.0.2.0/24
        if (a0 == 192 && a1 == 88 && a2 == 99) return false;        // 6to4 relay anycast
        if (a0 == 198 && (a1 == 18 || a1 == 19)) return false;      // benchmarking
        if (a0 == 198 && a1 == 51 && a2 == 100) return false;
        if (a0 == 203 && a1 == 0 && a2 == 113) return false;
        if (a0 >= 224) return false;                                // multicast, reserved, broadcast
        return true;
    }

    private static boolean isZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) if (b[i] != 0) return false;
        return true;
    }

    private static boolean isIpLiteral(String h) {
        // Covers dotted quads plus shorthand/integer/hex forms some resolvers accept (e.g. 2130706433, 0x7f.1).
        return h.matches("[0-9.]+") || h.matches("(0x[0-9a-f]+\\.?)+") || h.contains(":");
    }

    private static InetAddress parseLiteral(String h) {
        String v = h.startsWith("[") && h.endsWith("]") ? h.substring(1, h.length() - 1) : h;
        if (v.matches("\\d{1,3}(\\.\\d{1,3}){3}") || v.contains(":")) {
            try {
                return InetAddress.getByName(v); // literal: no DNS lookup performed
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null; // non-canonical numeric forms are rejected outright
    }
}
