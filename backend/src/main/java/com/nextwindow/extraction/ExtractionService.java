package com.nextwindow.extraction;

import com.nextwindow.common.Hashing;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs the extraction layers in order of reliability: structured data, metadata, DOM heuristics, and
 * finally plain text. Each field is taken from the most reliable layer that provides it.
 */
@Service
public class ExtractionService {
    private final StructuredDataExtractor structured;
    private final MetadataExtractor metadata;
    private final HeuristicExtractor heuristics;

    public ExtractionService(StructuredDataExtractor structured, MetadataExtractor metadata, HeuristicExtractor heuristics) {
        this.structured = structured;
        this.metadata = metadata;
        this.heuristics = heuristics;
    }

    public ExtractionResult extract(String body, String contentType, String baseUrl) {
        boolean plain = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/plain");
        Document doc;
        if (plain) {
            doc = Document.createShell(baseUrl);
            doc.body().appendElement("pre").text(body);
        } else {
            doc = Jsoup.parse(body, baseUrl);
        }

        PartialExtraction ld = structured.fromJsonLd(doc);
        PartialExtraction micro = structured.fromMicrodata(doc);
        PartialExtraction meta = metadata.extract(doc);
        boolean product = ld.product || micro.product || meta.product;
        PartialExtraction heur = product || hasCommerceSignals(doc) ? heuristics.extract(doc) : new PartialExtraction();

        List<String> notes = new ArrayList<>(ld.notes);

        String title = firstNonBlank(ld.title, micro.title, meta.title, doc.title(), heur.title);

        var priceLayer = ld.price != null ? ld : micro.price != null ? micro : meta.price != null ? meta : null;
        ValueSource priceSource = ld.price != null ? ValueSource.STRUCTURED_DATA
                : micro.price != null ? ValueSource.MICRODATA : meta.price != null ? ValueSource.METADATA : null;
        // Structured data reported several prices: do not fall back to weaker layers that might pick one arbitrarily.
        boolean ambiguous = !ld.notes.isEmpty() && ld.price == null;
        if (priceLayer == null && heur.price != null && !ambiguous) {
            priceLayer = heur;
            priceSource = ValueSource.HEURISTIC;
            notes.addAll(heur.notes);
        }
        String currency = null;
        if (priceLayer != null) {
            currency = firstNonBlank(priceLayer.currency, ld.currency, micro.currency, meta.currency);
        }

        Availability availability = Availability.UNKNOWN;
        ValueSource availabilitySource = null;
        if (ld.availability.isKnown()) { availability = ld.availability; availabilitySource = ValueSource.STRUCTURED_DATA; }
        else if (micro.availability.isKnown()) { availability = micro.availability; availabilitySource = ValueSource.MICRODATA; }
        else if (meta.availability.isKnown()) { availability = meta.availability; availabilitySource = ValueSource.METADATA; }
        else if (heur.availability.isKnown()) { availability = heur.availability; availabilitySource = ValueSource.HEURISTIC; }

        String text = TextNormalizer.visibleText(doc);
        String textHash = Hashing.sha256Hex(TextNormalizer.forHash(text));

        if (priceLayer == null && notes.isEmpty()) {
            notes.add(product ? "A product was detected, but no reliable price was found."
                    : "No product price was found. You can still monitor page changes and keywords.");
        }
        if (!availability.isKnown() && product) {
            notes.add("Stock availability could not be determined reliably.");
        }
        if (text.isBlank()) {
            notes.add("The page has no readable text. It may require JavaScript, which NextWindow does not run.");
        }

        return new ExtractionResult(trim(title, 500), priceLayer == null ? null : priceLayer.price, currency, priceSource,
                availability, availabilitySource, product, text, textHash, metadata.metadata(doc), List.copyOf(notes));
    }

    private static boolean hasCommerceSignals(Document doc) {
        return !doc.select("[class*=price], [id*=price], [data-price], [class*=stock], [class*=cart]").isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private static String trim(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
