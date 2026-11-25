package com.nextwindow.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionServiceTest {
    private final ExtractionService service = new ExtractionService(
            new StructuredDataExtractor(new ObjectMapper()), new MetadataExtractor(), new HeuristicExtractor());

    private ExtractionResult extract(String html) {
        return service.extract(html, "text/html", "https://shop.example.com/p/1");
    }

    @Test
    void readsJsonLdProduct() {
        var r = extract("""
                <html><head><title>Shop</title><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"Product","name":"Desk Lamp",
                 "offers":{"@type":"Offer","price":"39.50","priceCurrency":"usd","availability":"https://schema.org/OutOfStock"}}
                </script></head><body><h1>Desk Lamp</h1></body></html>""");
        assertThat(r.title()).isEqualTo("Desk Lamp");
        assertThat(r.price()).isEqualByComparingTo("39.50");
        assertThat(r.currency()).isEqualTo("USD");
        assertThat(r.priceSource()).isEqualTo(ValueSource.STRUCTURED_DATA);
        assertThat(r.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(r.productDetected()).isTrue();
    }

    @Test
    void readsProductInsideGraphWithTypeArray() {
        var r = extract("""
                <script type="application/ld+json">{"@graph":[{"@type":"WebPage"},{"@type":["Product","Thing"],"name":"Kettle",
                "offers":[{"@type":"Offer","price":20,"priceCurrency":"EUR","availability":"InStock"}]}]}</script>""");
        assertThat(r.title()).isEqualTo("Kettle");
        assertThat(r.price()).isEqualByComparingTo("20.00");
        assertThat(r.availability()).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void doesNotGuessWhenSeveralPricesExist() {
        var r = extract("""
                <script type="application/ld+json">{"@type":"Product","name":"Shirt","offers":[
                {"@type":"Offer","price":"10.00","priceCurrency":"USD"},{"@type":"Offer","price":"12.00","priceCurrency":"USD"}]}</script>
                <span class="price">$10.00</span>""");
        assertThat(r.price()).isNull();
        assertThat(r.notes()).anyMatch(n -> n.contains("several different prices"));
    }

    @Test
    void readsMicrodataAndMetaTags() {
        var micro = extract("""
                <div itemscope itemtype="https://schema.org/Product"><span itemprop="name">Chair</span>
                <meta itemprop="price" content="89.00"><meta itemprop="priceCurrency" content="GBP">
                <link itemprop="availability" href="https://schema.org/InStock"></div>""");
        assertThat(micro.price()).isEqualByComparingTo("89.00");
        assertThat(micro.currency()).isEqualTo("GBP");
        assertThat(micro.priceSource()).isEqualTo(ValueSource.MICRODATA);
        assertThat(micro.availability()).isEqualTo(Availability.IN_STOCK);

        var meta = extract("""
                <head><meta property="og:title" content="Sofa"><meta property="product:price:amount" content="499">
                <meta property="product:price:currency" content="EUR"><meta property="product:availability" content="oos"></head>""");
        assertThat(meta.title()).isEqualTo("Sofa");
        assertThat(meta.price()).isEqualByComparingTo("499.00");
        assertThat(meta.priceSource()).isEqualTo(ValueSource.METADATA);
        assertThat(meta.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void heuristicPriceOnlyWhenUnambiguous() {
        var single = extract("""
                <h1>Mug</h1><div class="product-price"><span class="price-now">$12.99</span><del class="price-was">$15.99</del>
                <span class="old-price">$15.99</span></div><button>Add to cart</button>""");
        assertThat(single.price()).isEqualByComparingTo("12.99");
        assertThat(single.priceSource()).isEqualTo(ValueSource.HEURISTIC);
        assertThat(single.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(single.availabilitySource()).isEqualTo(ValueSource.HEURISTIC);

        var multiple = extract("<span class=\"price\">$12.99</span><span class=\"price\">$19.99</span>");
        assertThat(multiple.price()).isNull();
    }

    @Test
    void heuristicOutOfStockFromDisabledButtonOrStockLabel() {
        var r = extract("<h1>Bike</h1><span class=\"price\">$300</span><p class=\"stock-status\">Sold out</p><button disabled>Add to cart</button>");
        assertThat(r.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void plainArticleHasNoPriceAndExplains() {
        var r = extract("<html><head><title>Blog</title></head><body><article>Hello world, nothing for sale.</article></body></html>");
        assertThat(r.price()).isNull();
        assertThat(r.availability()).isEqualTo(Availability.UNKNOWN);
        assertThat(r.productDetected()).isFalse();
        assertThat(r.notes()).anyMatch(n -> n.contains("still monitor page changes"));
        assertThat(r.normalizedText()).isEqualTo("Hello world, nothing for sale.");
    }

    @Test
    void textHashIgnoresScriptsWhitespaceTimestampsAndIds() {
        var a = extract("<body><p>Status:   all good</p><script>var t=1</script><footer>Generated at 10:31:02 UTC, 3 minutes ago, req 9f8a7b6c5d4e3f2a1b0c9d8e7f6a</footer></body>");
        var b = extract("<body>\n<p>Status: all good</p><script>var t=2</script><style>p{}</style><footer>Generated at 11:45:59 UTC, 12 minutes ago, req 1a2b3c4d5e6f7a8b9c0d1e2f3a4b</footer></body>");
        assertThat(a.textHash()).isEqualTo(b.textHash());
        var c = extract("<body><p>Status: delays expected</p><footer>Generated at 10:31:02 UTC</footer></body>");
        assertThat(c.textHash()).isNotEqualTo(a.textHash());
    }

    @Test
    void handlesPlainTextAndMalformedJsonLd() {
        var r = service.extract("Line one <b>not html</b>", "text/plain", "https://x.example.com/a.txt");
        assertThat(r.normalizedText()).contains("<b>not html</b>");
        var bad = extract("<script type=\"application/ld+json\">{not json</script><p>ok</p>");
        assertThat(bad.price()).isNull();
        assertThat(bad.normalizedText()).isEqualTo("ok");
    }
}
