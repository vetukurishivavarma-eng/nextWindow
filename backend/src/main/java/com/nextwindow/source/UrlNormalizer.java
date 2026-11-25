package com.nextwindow.source;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces a canonical form of a URL so that equivalent URLs share one MonitoringSource.
 * Only parameters known to be tracking or session noise are removed; every other query
 * parameter is kept because it may select content (variant, size, page, ...).
 */
@Component
public class UrlNormalizer {
    public static final int MAX_URL_LENGTH = 2048;
    public static final String DEMO_SCHEME = "demo";

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "fbclid", "msclkid", "yclid", "twclid", "ttclid",
            "mc_cid", "mc_eid", "igshid", "_ga", "_gl", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
            "vero_id", "rb_clickid", "s_cid", "ref_src", "ref_url", "srsltid",
            "jsessionid", "phpsessid", "aspsessionid", "sessionid", "cfid", "cftoken");
    private static final Pattern PATH_SESSION = Pattern.compile(";(jsessionid|phpsessid|sid)=[^/?#]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEMO_SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");

    public record NormalizedUrl(String canonical, String scheme, String host, int port) {
        public boolean isDemo() { return DEMO_SCHEME.equals(scheme); }
    }

    public NormalizedUrl normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new UrlRejectedException("Enter a URL to monitor.");
        }
        String raw = input.trim();
        if (raw.length() > MAX_URL_LENGTH) {
            throw new UrlRejectedException("That URL is too long (max " + MAX_URL_LENGTH + " characters).");
        }
        if (raw.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            throw new UrlRejectedException("That URL contains invalid characters.");
        }
        if (raw.regionMatches(true, 0, "demo://", 0, 7)) {
            return normalizeDemo(raw);
        }
        if (!raw.contains("://")) {
            raw = "https://" + raw;
        }
        URI uri;
        try {
            uri = new URI(raw.replace(" ", "%20"));
        } catch (URISyntaxException e) {
            throw new UrlRejectedException("That doesn't look like a valid web address.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UrlRejectedException("Only http and https addresses can be monitored.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new UrlRejectedException("URLs containing usernames or passwords are not allowed.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UrlRejectedException("That URL has no valid host name.");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (!host.startsWith("[")) {
            try {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
            } catch (IllegalArgumentException e) {
                throw new UrlRejectedException("That host name is not valid.");
            }
        }
        int port = uri.getPort();
        int effectivePort = port == -1 ? (scheme.equals("https") ? 443 : 80) : port;
        boolean defaultPort = (scheme.equals("https") && effectivePort == 443) || (scheme.equals("http") && effectivePort == 80);

        String path = uri.normalize().getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        path = PATH_SESSION.matcher(path).replaceAll("");
        if (path.startsWith("/..")) path = "/";

        String query = canonicalQuery(uri.getRawQuery());

        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) sb.append(':').append(effectivePort);
        sb.append(path);
        if (!query.isEmpty()) sb.append('?').append(query);
        String canonical = sb.toString();
        if (canonical.length() > MAX_URL_LENGTH) {
            throw new UrlRejectedException("That URL is too long.");
        }
        return new NormalizedUrl(canonical, scheme, host, effectivePort);
    }

    private NormalizedUrl normalizeDemo(String raw) {
        String slug = raw.substring(7).toLowerCase(Locale.ROOT);
        int cut = indexOfAny(slug);
        if (cut >= 0) slug = slug.substring(0, cut);
        if (!DEMO_SLUG.matcher(slug).matches()) {
            throw new UrlRejectedException("Unknown demo source.");
        }
        return new NormalizedUrl(DEMO_SCHEME + "://" + slug, DEMO_SCHEME, slug, -1);
    }

    private static int indexOfAny(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#') return i;
        }
        return -1;
    }

    static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String[]> kept = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("utm_") || TRACKING_PARAMS.contains(lower)) continue;
            kept.add(new String[]{key, part});
        }
        // Stable sort by key: preserves the relative order of repeated keys, which can be significant.
        kept.sort(Comparator.comparing(a -> a[0]));
        return String.join("&", kept.stream().map(a -> a[1]).toList());
    }
}
