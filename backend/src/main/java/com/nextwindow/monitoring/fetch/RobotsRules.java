package com.nextwindow.monitoring.fetch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal RFC 9309 robots.txt evaluation: picks the most specific matching user-agent group and applies
 * longest-match Allow/Disallow with {@code *} and {@code $} wildcards. Also reads Crawl-delay.
 */
public final class RobotsRules {
    private record Rule(boolean allow, String pattern) {}

    private final List<Rule> rules;
    private final Duration crawlDelay;
    private final boolean disallowAll;

    private RobotsRules(List<Rule> rules, Duration crawlDelay, boolean disallowAll) {
        this.rules = rules;
        this.crawlDelay = crawlDelay;
        this.disallowAll = disallowAll;
    }

    public static RobotsRules allowAll() {
        return new RobotsRules(List.of(), null, false);
    }

    public static RobotsRules disallowAll() {
        return new RobotsRules(List.of(), null, true);
    }

    public Duration crawlDelay() { return crawlDelay; }

    public static RobotsRules parse(String content, String agentToken) {
        String agent = agentToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = null;
        List<Rule> wildcard = null;
        Duration specificDelay = null;
        Duration wildcardDelay = null;

        List<String> groupAgents = new ArrayList<>();
        List<Rule> groupRules = new ArrayList<>();
        Duration groupDelay = null;
        boolean inRules = false;

        List<String> lines = new ArrayList<>(content.lines().toList());
        lines.add("user-agent: __end__");
        for (String rawLine : lines) {
            String line = rawLine;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (inRules) {
                    for (String ga : groupAgents) {
                        if (ga.equals("*")) {
                            if (wildcard == null) wildcard = new ArrayList<>();
                            wildcard.addAll(groupRules);
                            if (groupDelay != null) wildcardDelay = groupDelay;
                        } else if (!ga.isEmpty() && agent.startsWith(ga)) {
                            if (specific == null) specific = new ArrayList<>();
                            specific.addAll(groupRules);
                            if (groupDelay != null) specificDelay = groupDelay;
                        }
                    }
                    groupAgents = new ArrayList<>();
                    groupRules = new ArrayList<>();
                    groupDelay = null;
                    inRules = false;
                }
                groupAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow") || key.equals("disallow")) {
                inRules = true;
                if (!value.isEmpty()) groupRules.add(new Rule(key.equals("allow"), value));
            } else if (key.equals("crawl-delay")) {
                inRules = true;
                try {
                    double secs = Double.parseDouble(value);
                    if (secs > 0) groupDelay = Duration.ofMillis((long) (Math.min(secs, 3600) * 1000));
                } catch (NumberFormatException ignored) {
                    // ignore malformed value
                }
            }
        }
        if (specific != null) return new RobotsRules(specific, specificDelay, false);
        if (wildcard != null) return new RobotsRules(wildcard, wildcardDelay, false);
        return allowAll();
    }

    /** @param pathAndQuery path plus optional query, e.g. {@code /p/1?x=2} */
    public boolean isAllowed(String pathAndQuery) {
        if (disallowAll) return false;
        if (pathAndQuery.equals("/robots.txt")) return true;
        Rule best = null;
        for (Rule r : rules) {
            if (matches(r.pattern(), pathAndQuery)) {
                if (best == null || r.pattern().length() > best.pattern().length()
                        || (r.pattern().length() == best.pattern().length() && r.allow())) {
                    best = r;
                }
            }
        }
        return best == null || best.allow();
    }

    static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        return matchFrom(p, 0, path, 0, anchored);
    }

    private static boolean matchFrom(String p, int pi, String s, int si, boolean anchored) {
        while (pi < p.length()) {
            char c = p.charAt(pi);
            if (c == '*') {
                for (int k = si; k <= s.length(); k++) {
                    if (matchFrom(p, pi + 1, s, k, anchored)) return true;
                }
                return false;
            }
            if (si >= s.length() || s.charAt(si) != c) return false;
            pi++;
            si++;
        }
        return !anchored || si == s.length();
    }
}
