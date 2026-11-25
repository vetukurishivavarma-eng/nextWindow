package com.nextwindow.source;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "monitoring_source")
public class MonitoringSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_url", nullable = false, length = 2048)
    private String canonicalUrl;

    @Column(nullable = false)
    private String host;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_method", nullable = false)
    private FetchMethod fetchMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "last_content_hash", length = 64)
    private String lastContentHash;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "next_fetch_at", nullable = false)
    private Instant nextFetchAt;

    @Column(name = "check_interval_minutes", nullable = false)
    private int checkIntervalMinutes;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected MonitoringSource() {}

    public Long getId() { return id; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getHost() { return host; }
    public SourceType getSourceType() { return sourceType; }
    public FetchMethod getFetchMethod() { return fetchMethod; }
    public SourceStatus getStatus() { return status; }
    public void setStatus(SourceStatus status) { this.status = status; }
    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage == null ? null : statusMessage.substring(0, Math.min(500, statusMessage.length()));
    }
    public String getLastContentHash() { return lastContentHash; }
    public void setLastContentHash(String lastContentHash) { this.lastContentHash = lastContentHash; }
    public Instant getLastFetchedAt() { return lastFetchedAt; }
    public void setLastFetchedAt(Instant lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Instant getNextFetchAt() { return nextFetchAt; }
    public void setNextFetchAt(Instant nextFetchAt) { this.nextFetchAt = nextFetchAt; }
    public int getCheckIntervalMinutes() { return checkIntervalMinutes; }
    public void setCheckIntervalMinutes(int checkIntervalMinutes) { this.checkIntervalMinutes = checkIntervalMinutes; }
    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
