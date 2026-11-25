package com.nextwindow.extraction;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layer 3: conservative DOM heuristics. A value is only reported when the evidence is unambiguous,
 * e.g. exactly one distinct price in price-labelled elements; otherwise the value stays unknown.
 */
@Component
public class HeuristicExtractor {
    private static final Pattern OLD_PRICE = Pattern.compile("(?i)(old|was|strike|compare|list|msrp|regular|original|before|save|saving|discount|per-?unit|installment)");
    private static final Pattern OUT_PHRASE = Pattern.compile("(?i)\\b(out of stock|sold out|currently unavailable|temporarily unavailable|no longer available|not in stock)\\b");
    private static final Pattern IN_PHRASE = Pattern.compile("(?i)^\\s*(add to (cart|bag|basket|trolley)|buy (it )?now|in stock)\\b");

    PartialExtraction extract(Document doc) {
        PartialExtraction out = new PartialExtraction();
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) out.title = h1.text();
        else if (!doc.title().isBlank()) out.title = doc.title();

        Set<BigDecimal> prices = new HashSet<>();
        String currency = null;
        for (Element e : doc.select("[class*=price], [id*=price], [data-price]")) {
            if (e.parents().stream().anyMatch(p -> p.tagName().equals("del") || p.tagName().equals("s"))
                    || e.tagName().equals("del") || e.tagName().equals("s")) continue;
            String marker = (e.className() + " " + e.id()).toLowerCase(Locale.ROOT);
            if (OLD_PRICE.matcher(marker).find()) continue;
            if (!e.children().isEmpty() && e.children().stream().anyMatch(c -> !c.text().isBlank() && c.text().length() > 1)) {
                continue; // only leaf-ish elements; containers are handled via their children
            }
            String text = e.hasAttr("data-price") && !e.attr("data-price").isBlank() ? e.attr("data-price") : e.text();
            var parsed = PriceParser.parse(text);
            if (parsed.isEmpty()) continue;
            String cur = parsed.get().currency();
            if (cur == null && !e.hasAttr("data-price")) continue; // require a currency marker in visible text
            prices.add(parsed.get().amount());
            if (cur != null) currency = cur;
            if (prices.size() > 1) break;
        }
        if (prices.size() == 1) {
            out.price = prices.iterator().next();
            out.currency = currency;
            out.notes.add("Price was estimated from the page layout; verify it matches the page.");
        }

        out.availability = availability(doc);
        return out;
    }

    private Availability availability(Document doc) {
        boolean inStock = false;
        boolean disabledBuy = false;
        for (Element b : doc.select("button, input[type=submit], input[type=button], [role=button], a[class*=cart]")) {
            String label = b.tagName().equals("input") ? b.attr("value") : b.text();
            if (IN_PHRASE.matcher(label).find()) {
                if (b.hasAttr("disabled") || "true".equals(b.attr("aria-disabled"))) disabledBuy = true;
                else inStock = true;
            }
        }
        boolean outOfStock = disabledBuy;
        for (Element e : doc.select("[class*=stock], [id*=stock], [class*=availability], [id*=availability], [class*=inventory]")) {
            if (OUT_PHRASE.matcher(e.ownText() + " " + (e.children().isEmpty() ? "" : e.text())).find()) {
                outOfStock = true;
                break;
            }
        }
        if (inStock && !outOfStock) return Availability.IN_STOCK;
        if (outOfStock && !inStock) return Availability.OUT_OF_STOCK;
        return Availability.UNKNOWN;
    }
}
