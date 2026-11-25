package com.nextwindow.notification;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "alert")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "watch_id", nullable = false)
    private Long watchId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(nullable = false, length = 250)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false)
    private PushStatus pushStatus = PushStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Alert() {}

    public Alert(Long userId, Long watchId, String alertType, String title, String message, String oldValue,
                 String newValue, String fingerprint, Instant createdAt) {
        this.userId = userId;
        this.watchId = watchId;
        this.alertType = alertType;
        this.title = clip(title, 250);
        this.message = clip(message, 1000);
        this.oldValue = clip(oldValue, 500);
        this.newValue = clip(newValue, 500);
        this.fingerprint = fingerprint;
        this.createdAt = createdAt;
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getWatchId() { return watchId; }
    public String getAlertType() { return alertType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getFingerprint() { return fingerprint; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public PushStatus getPushStatus() { return pushStatus; }
    public void setPushStatus(PushStatus pushStatus) { this.pushStatus = pushStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
