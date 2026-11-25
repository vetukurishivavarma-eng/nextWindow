package com.nextwindow.extraction;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Produces stable, comparable page text: strips markup, scripts and hidden elements, collapses whitespace,
 * and (for hashing only) removes volatile tokens such as clock times, relative times, ISO timestamps and
 * long random identifiers, so cosmetic churn does not register as a change.
 */
public final class TextNormalizer {
    private static final String NOISE = "script, style, noscript, template, svg, canvas, iframe, object, embed, "
            + "link, meta, [hidden], [aria-hidden=true], [style*=display:none], [style*=display: none]";
    private static final Pattern[] VOLATILE = {
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[t ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(z|[+-]\\d{2}:?\\d{2})?\\b"),
            Pattern.compile("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\s?(am|pm|a\\.m\\.|p\\.m\\.)?(\\s?(utc|gmt|[a-z]{2,4}t))?\\b"),
            Pattern.compile("\\b(\\d+|an?|one)\\s+(second|sec|minute|min|hour|hr|day|week)s?\\s+ago\\b"),
            Pattern.compile("\\b(just now|moments ago|updated today)\\b"),
            Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"),
            Pattern.compile("\\b(?=[a-z0-9_-]*\\d)(?=[a-z0-9_-]*[a-z])[a-z0-9_-]{24,}\\b"),
            Pattern.compile("\\b\\d{10,13}\\b"), // epoch timestamps
    };
    private static final Pattern WS = Pattern.compile("\\s+");

    private TextNormalizer() {}

    /** Human-readable normalized text of the page body. */
    public static String visibleText(Document doc) {
        Document copy = doc.clone();
        copy.select(NOISE).remove();
        Element root = copy.body() != null ? copy.body() : copy;
        String text = root.text();
        text = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(' ', ' ');
        return WS.matcher(text).replaceAll(" ").trim();
    }

    /** Canonical form used for change hashing. */
    public static String forHash(String visibleText) {
        String t = visibleText.toLowerCase(Locale.ROOT);
        for (Pattern p : VOLATILE) {
            t = p.matcher(t).replaceAll(" ");
        }
        return WS.matcher(t).replaceAll(" ").trim();
    }

    /** Normalization applied to both page text and keywords before matching. */
    public static String forMatch(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC).replace(' ', ' ').toLowerCase(Locale.ROOT);
        return WS.matcher(t).replaceAll(" ").trim();
    }
}
