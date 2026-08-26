package com.machingclee.blogcomment.common.jpa.entity;

import com.machingclee.domain.util.annotation.BoundedContext;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Per-user fixed-window rate limit counters.
 * <p>
 * One row per {@code user_email}. Concurrent writers lock the row with
 * {@code SELECT … FOR UPDATE} (pessimistic write), then either increment
 * or reject when over the configured limit (10/min, 60/hour).
 */
@BoundedContext("Blog Comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_rate_limit", schema = "blog_system")
public class UserRateLimit {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;

    // region columns
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_email", nullable = false, updatable = false)
    private String userEmail;

    @Column(name = "minute_count", nullable = false)
    private Integer minuteCount = 0;

    @Column(name = "hour_count", nullable = false)
    private Integer hourCount = 0;

    /**
     * Epoch-ms of the start of the current fixed minute window (UTC).
     */
    @Column(name = "minute_window_start", nullable = false)
    private Double minuteWindowStart;

    /**
     * Epoch-ms of the start of the current fixed hour window (UTC).
     */
    @Column(name = "hour_window_start", nullable = false)
    private Double hourWindowStart;

    @Column(name = "updated_at", nullable = false)
    private Double updatedAt;
    // endregion

    // region factories
    public static UserRateLimit create(String userEmail, long nowMs) {
        UserRateLimit r = new UserRateLimit();
        r.userEmail = userEmail;
        r.minuteCount = 0;
        r.hourCount = 0;
        r.minuteWindowStart = (double) nowMs;
        r.hourWindowStart = (double) nowMs;
        r.updatedAt = (double) nowMs;
        return r;
    }
    // endregion

    // region domain methods

    /**
     * Roll windows forward if {@code nowMs} is past the window end, then treat
     * this call as one more request. Returns which window (if any) was exceeded.
     * Caller must hold a pessimistic lock on this row.
     */
    public LimitExceeded checkAndIncrement(long nowMs, int maxPerMinute, int maxPerHour) {
        rollWindowsIfNeeded(nowMs);

        if (minuteCount >= maxPerMinute) {
            return LimitExceeded.MINUTE;
        }
        if (hourCount >= maxPerHour) {
            return LimitExceeded.HOUR;
        }

        minuteCount = minuteCount + 1;
        hourCount = hourCount + 1;
        // Keep updated_at in sync without relying on DB triggers for updates.
        this.updatedAt = (double) nowMs;
        return LimitExceeded.NONE;
    }

    private void rollWindowsIfNeeded(long nowMs) {
        long minuteStart = minuteWindowStart == null ? nowMs : minuteWindowStart.longValue();
        long hourStart = hourWindowStart == null ? nowMs : hourWindowStart.longValue();

        if (nowMs - minuteStart >= MINUTE_MS) {
            // Align to current fixed minute window start (floor).
            long newStart = nowMs - (nowMs % MINUTE_MS);
            minuteWindowStart = (double) newStart;
            minuteCount = 0;
        }
        if (nowMs - hourStart >= HOUR_MS) {
            long newStart = nowMs - (nowMs % HOUR_MS);
            hourWindowStart = (double) newStart;
            hourCount = 0;
        }
    }
    // endregion

    public enum LimitExceeded {
        NONE,
        MINUTE,
        HOUR
    }
}
