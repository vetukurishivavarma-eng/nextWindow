package com.nextwindow.demo;

import com.nextwindow.extraction.Availability;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demo_page")
public class DemoPage {
    @Id
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(precision = 14, scale = 2)
    private BigDecimal price;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Availability availability;

    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    private String bodyText;

    @Column(name = "fail_next", nullable = false)
    private int failNext;

    @Column(nullable = false)
    private int revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DemoPage() {}

    public DemoPage(String slug, String title, BigDecimal price, String currency, Availability availability, String bodyText, Instant now) {
        this.slug = slug;
        this.title = title;
        this.price = price;
        this.currency = currency;
        this.availability = availability;
        this.bodyText = bodyText;
        this.updatedAt = now;
    }

    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Availability getAvailability() { return availability; }
    public void setAvailability(Availability availability) { this.availability = availability; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public int getFailNext() { return failNext; }
    public void setFailNext(int failNext) { this.failNext = failNext; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
