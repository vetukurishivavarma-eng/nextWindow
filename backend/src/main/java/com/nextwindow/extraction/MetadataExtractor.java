package com.nextwindow.extraction;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Layer 2: Open Graph, product and common meta tags. */
@Component
public class MetadataExtractor {

    PartialExtraction extract(Document doc) {
        PartialExtraction out = new PartialExtraction();
        out.title = first(doc, "og:title", "twitter:title");
        String amount = first(doc, "product:price:amount", "og:price:amount");
        if (amount != null) {
            out.price = PriceParser.parseAmount(amount).orElse(null);
            out.currency = PriceParser.normalizeCurrency(first(doc, "product:price:currency", "og:price:currency"));
            out.product = true;
        }
        String availability = first(doc, "product:availability", "og:availability");
        if (availability != null) {
            out.availability = AvailabilityParser.parse(availability);
            out.product = true;
        }
        if ("product".equalsIgnoreCase(first(doc, "og:type"))) out.product = true;
        return out;
    }

    Map<String, String> metadata(Document doc) {
        Map<String, String> m = new LinkedHashMap<>();
        put(m, "description", first(doc, "og:description", "description", "twitter:description"));
        put(m, "siteName", first(doc, "og:site_name", "application-name"));
        put(m, "image", first(doc, "og:image", "twitter:image"));
        Element canonical = doc.selectFirst("link[rel=canonical][href]");
        if (canonical != null) put(m, "canonical", canonical.absUrl("href"));
        return m;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v.length() > 500 ? v.substring(0, 500) : v.trim());
    }

    static String first(Document doc, String... names) {
        for (String n : names) {
            Element e = doc.selectFirst("meta[property=" + n + "][content], meta[name=" + n + "][content], meta[itemprop=" + n + "][content]");
            if (e != null && !e.attr("content").isBlank()) return e.attr("content").trim();
        }
        return null;
    }
}
