package com.nextwindow.history;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "check_run")
public class CheckRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckOutcome outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(length = 500)
    private String message;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "alerts_created", nullable = false)
    private int alertsCreated;

    protected CheckRun() {}

    public CheckRun(Long sourceId, Instant startedAt, Instant finishedAt, CheckOutcome outcome, Integer httpStatus,
                    String message, Long snapshotId, int alertsCreated) {
        this.sourceId = sourceId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.durationMs = Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        this.message = message == null ? null : message.substring(0, Math.min(500, message.length()));
        this.snapshotId = snapshotId;
        this.alertsCreated = alertsCreated;
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public CheckOutcome getOutcome() { return outcome; }
    public Integer getHttpStatus() { return httpStatus; }
    public long getDurationMs() { return durationMs; }
    public String getMessage() { return message; }
    public Long getSnapshotId() { return snapshotId; }
    public int getAlertsCreated() { return alertsCreated; }
}
