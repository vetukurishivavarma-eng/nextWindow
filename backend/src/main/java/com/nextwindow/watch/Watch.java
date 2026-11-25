package com.nextwindow.watch;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "watch")
public class Watch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "watch_type", nullable = false)
    private WatchType watchType;

    @Column(name = "target_price", precision = 14, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "target_keyword", length = 200)
    private String targetKeyword;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_mode", nullable = false)
    private AlertMode alertMode = AlertMode.REPEAT;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes = 60;

    @Column(name = "check_interval_minutes", nullable = false)
    private int checkIntervalMinutes = 60;

    /** Condition-specific state from the last evaluation; null until a baseline exists. */
    @Column(name = "last_observed_value", length = 500)
    private String lastObservedValue;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "last_alert_fingerprint", length = 64)
    private String lastAlertFingerprint;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    protected Watch() {}

    public Watch(Long userId, Long sourceId, String title, WatchType type, Instant now) {
        this.userId = userId;
        this.sourceId = sourceId;
        this.title = title;
        this.watchType = type;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public WatchType getWatchType() { return watchType; }
    public void setWatchType(WatchType watchType) { this.watchType = watchType; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }
    public String getTargetKeyword() { return targetKeyword; }
    public void setTargetKeyword(String targetKeyword) { this.targetKeyword = targetKeyword; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public AlertMode getAlertMode() { return alertMode; }
    public void setAlertMode(AlertMode alertMode) { this.alertMode = alertMode; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    public int getCheckIntervalMinutes() { return checkIntervalMinutes; }
    public void setCheckIntervalMinutes(int checkIntervalMinutes) { this.checkIntervalMinutes = checkIntervalMinutes; }
    public String getLastObservedValue() { return lastObservedValue; }
    public void setLastObservedValue(String lastObservedValue) { this.lastObservedValue = lastObservedValue; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
    public String getLastAlertFingerprint() { return lastAlertFingerprint; }
    public void setLastAlertFingerprint(String lastAlertFingerprint) { this.lastAlertFingerprint = lastAlertFingerprint; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(Instant lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
}
