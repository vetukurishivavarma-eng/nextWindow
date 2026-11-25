package com.nextwindow.history;

import com.nextwindow.extraction.Availability;
import com.nextwindow.extraction.ValueSource;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Meaningful monitoring state of a source at a point in time. Only stored when the state changes. */
@Entity
@Table(name = "snapshot")
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "text_hash", length = 64)
    private String textHash;

    @Column(name = "extracted_title", length = 500)
    private String extractedTitle;

    @Column(name = "extracted_price", precision = 14, scale = 2)
    private BigDecimal extractedPrice;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source")
    private ValueSource priceSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Availability availability = Availability.UNKNOWN;

    @Column(name = "normalized_text", columnDefinition = "text")
    private String normalizedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Snapshot() {}

    public Snapshot(Long sourceId, String contentHash, String textHash, String title, BigDecimal price, String currency,
                    ValueSource priceSource, Availability availability, String normalizedText, Instant createdAt) {
        this.sourceId = sourceId;
        this.contentHash = contentHash;
        this.textHash = textHash;
        this.extractedTitle = title;
        this.extractedPrice = price;
        this.currency = currency;
        this.priceSource = priceSource;
        this.availability = availability == null ? Availability.UNKNOWN : availability;
        this.normalizedText = normalizedText;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getContentHash() { return contentHash; }
    public String getTextHash() { return textHash; }
    public String getExtractedTitle() { return extractedTitle; }
    public BigDecimal getExtractedPrice() { return extractedPrice; }
    public String getCurrency() { return currency; }
    public ValueSource getPriceSource() { return priceSource; }
    public Availability getAvailability() { return availability; }
    public String getNormalizedText() { return normalizedText; }
    public Instant getCreatedAt() { return createdAt; }
}
