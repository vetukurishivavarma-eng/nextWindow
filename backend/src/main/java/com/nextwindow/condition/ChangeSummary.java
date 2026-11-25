package com.nextwindow.condition;

import java.util.Arrays;

/** Word-level prefix/suffix diff that describes what changed between two texts in a sentence. */
public final class ChangeSummary {
    private static final int MAX = 240;

    public record Summary(String description, String removed, String added) {}

    private ChangeSummary() {}

    public static Summary summarize(String before, String after) {
        if (before == null || after == null) {
            return new Summary("The page content changed.", null, null);
        }
        String[] a = before.split(" ");
        String[] b = after.split(" ");
        int prefix = 0;
        while (prefix < a.length && prefix < b.length && a[prefix].equals(b[prefix])) prefix++;
        int suffix = 0;
        while (suffix < a.length - prefix && suffix < b.length - prefix
                && a[a.length - 1 - suffix].equals(b[b.length - 1 - suffix])) suffix++;
        String removed = clip(String.join(" ", Arrays.copyOfRange(a, prefix, a.length - suffix)));
        String added = clip(String.join(" ", Arrays.copyOfRange(b, prefix, b.length - suffix)));
        String desc;
        if (removed.isEmpty() && added.isEmpty()) desc = "Page details changed (price, stock or title).";
        else if (removed.isEmpty()) desc = "New content: “" + added + "”";
        else if (added.isEmpty()) desc = "Removed content: “" + removed + "”";
        else desc = "Changed “" + removed + "” to “" + added + "”";
        return new Summary(clip(desc), removed.isEmpty() ? null : removed, added.isEmpty() ? null : added);
    }

    private static String clip(String s) {
        return s.length() > MAX ? s.substring(0, MAX - 1) + "…" : s;
    }
}
