package com.nextwindow.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Layer 1: schema.org Product/Offer data from JSON-LD and microdata. */
@Component
public class StructuredDataExtractor {
    private static final int MAX_JSON_LD_CHARS = 500_000;
    private final ObjectMapper mapper;

    public StructuredDataExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    PartialExtraction fromJsonLd(Document doc) {
        PartialExtraction out = new PartialExtraction();
        List<JsonNode> products = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json.isBlank() || json.length() > MAX_JSON_LD_CHARS) continue;
            try {
                collectProducts(mapper.readTree(json), products, 0);
            } catch (Exception ignored) {
                // malformed JSON-LD is common; skip the block
            }
        }
        if (products.isEmpty()) return out;
        out.product = true;
        JsonNode p = products.get(0);
        out.title = text(p.get("name"));

        List<JsonNode> offers = new ArrayList<>();
        collectOffers(p.get("offers"), offers);
        Set<BigDecimal> prices = new TreeSet<>();
        Set<String> currencies = new TreeSet<>();
        List<Availability> avail = new ArrayList<>();
        for (JsonNode o : offers) {
            Optional<BigDecimal> price = PriceParser.parseAmount(text(o.get("price")));
            if (price.isEmpty() && o.has("priceSpecification")) {
                JsonNode spec = o.get("priceSpecification");
                if (spec.isArray() && !spec.isEmpty()) spec = spec.get(0);
                price = PriceParser.parseAmount(text(spec.get("price")));
                if (spec.has("priceCurrency")) currencies.add(text(spec.get("priceCurrency")).toUpperCase(Locale.ROOT));
            }
            if (price.isEmpty() && "AggregateOffer".equals(text(o.get("@type")))) {
                Optional<BigDecimal> low = PriceParser.parseAmount(text(o.get("lowPrice")));
                Optional<BigDecimal> high = PriceParser.parseAmount(text(o.get("highPrice")));
                if (low.isPresent() && (high.isEmpty() || high.get().compareTo(low.get()) == 0)) price = low;
                else if (low.isPresent()) out.notes.add("This page lists a price range; no single price could be determined.");
            }
            price.ifPresent(prices::add);
            String cur = text(o.get("priceCurrency"));
            if (cur != null && !cur.isBlank()) currencies.add(cur.trim().toUpperCase(Locale.ROOT));
            Availability a = AvailabilityParser.parse(text(o.get("availability")));
            if (a.isKnown()) avail.add(a);
        }
        if (prices.size() == 1) {
            out.price = prices.iterator().next();
            out.currency = currencies.size() == 1 ? currencies.iterator().next() : null;
        } else if (prices.size() > 1) {
            out.notes.add("This page lists several different prices (variants or sellers); price tracking is unavailable.");
        }
        if (!avail.isEmpty()) {
            out.availability = avail.stream().anyMatch(Availability::isPurchasable)
                    ? avail.stream().filter(Availability::isPurchasable).findFirst().get()
                    : avail.get(0);
        }
        return out;
    }

    PartialExtraction fromMicrodata(Document doc) {
        PartialExtraction out = new PartialExtraction();
        Element scope = doc.selectFirst("[itemscope][itemtype~=(?i)schema.org/(Product|IndividualProduct|ProductModel)]");
        if (scope == null) return out;
        out.product = true;
        Element name = scope.selectFirst("[itemprop=name]");
        if (name != null) out.title = attrOrText(name);
        Element price = scope.selectFirst("[itemprop=price]");
        if (price != null) {
            out.price = PriceParser.parseAmount(attrOrText(price)).orElse(null);
        }
        Element cur = scope.selectFirst("[itemprop=priceCurrency]");
        if (cur != null) out.currency = PriceParser.normalizeCurrency(attrOrText(cur));
        Element av = scope.selectFirst("[itemprop=availability]");
        if (av != null) {
            String v = av.hasAttr("href") ? av.attr("href") : attrOrText(av);
            out.availability = AvailabilityParser.parse(v);
        }
        return out;
    }

    private void collectProducts(JsonNode node, List<JsonNode> out, int depth) {
        if (node == null || depth > 6 || out.size() > 5) return;
        if (node.isArray()) {
            node.forEach(n -> collectProducts(n, out, depth + 1));
            return;
        }
        if (!node.isObject()) return;
        if (isProductType(node.get("@type"))) {
            out.add(node);
            return;
        }
        collectProducts(node.get("@graph"), out, depth + 1);
        collectProducts(node.get("mainEntity"), out, depth + 1);
        // ProductGroup variants
        collectProducts(node.get("hasVariant"), out, depth + 1);
    }

    private static boolean isProductType(JsonNode type) {
        if (type == null) return false;
        if (type.isArray()) {
            for (JsonNode t : type) if (isProductType(t)) return true;
            return false;
        }
        String t = type.asText();
        int slash = t.lastIndexOf('/');
        if (slash >= 0) t = t.substring(slash + 1);
        return t.equals("Product") || t.equals("IndividualProduct") || t.equals("ProductModel") || t.equals("Vehicle")
                || t.equals("Book");
    }

    private static void collectOffers(JsonNode offers, List<JsonNode> out) {
        if (offers == null) return;
        if (offers.isArray()) {
            offers.forEach(o -> collectOffers(o, out));
        } else if (offers.isObject()) {
            if (offers.has("offers") && "AggregateOffer".equals(text(offers.get("@type"))) && !offers.has("lowPrice")) {
                collectOffers(offers.get("offers"), out);
            } else {
                out.add(offers);
            }
        }
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isArray()) return n.isEmpty() ? null : text(n.get(0));
        if (n.isObject()) return n.has("@id") ? n.get("@id").asText() : null;
        return n.asText();
    }

    private static String attrOrText(Element e) {
        if (e.hasAttr("content")) return e.attr("content");
        if (e.hasAttr("value")) return e.attr("value");
        return e.text();
    }
}
