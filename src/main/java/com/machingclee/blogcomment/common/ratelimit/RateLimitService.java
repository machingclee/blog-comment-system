package com.machingclee.blogcomment.common.ratelimit;

import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.exception.RateLimitExceededException;
import com.machingclee.blogcomment.common.jpa.entity.UserRateLimit;
import com.machingclee.blogcomment.common.jpa.repository.UserRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed-window rate limiter backed by {@code user_rate_limit}.
 * <p>
 * Limits authenticated writes to {@value #MAX_PER_MINUTE}/minute and
 * {@value #MAX_PER_HOUR}/hour per user email. Concurrent requests for the same
 * email are serialized with a pessimistic row lock.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    public static final int MAX_PER_MINUTE = 10;
    public static final int MAX_PER_HOUR = 60;

    private final UserRateLimitRepository userRateLimitRepository;

    /**
     * Consume one request for {@code userEmail}. Throws
     * {@link RateLimitExceededException} when either window is full.
     * Must run inside a transaction (this method is {@code @Transactional}).
     */
    @Transactional
    public void checkAndConsume(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("user email is required for rate limiting");
        }
        String email = userEmail.trim();
        long nowMs = System.currentTimeMillis();

        UserRateLimit row = userRateLimitRepository.findByUserEmailForUpdate(email)
                .orElseGet(() -> createRow(email, nowMs));

        UserRateLimit.LimitExceeded exceeded =
                row.checkAndIncrement(nowMs, MAX_PER_MINUTE, MAX_PER_HOUR);

        if (exceeded == UserRateLimit.LimitExceeded.MINUTE) {
            throw new RateLimitExceededException(
                    "rate limit exceeded: at most " + MAX_PER_MINUTE + " requests per minute");
        }
        if (exceeded == UserRateLimit.LimitExceeded.HOUR) {
            throw new RateLimitExceededException(
                    "rate limit exceeded: at most " + MAX_PER_HOUR + " requests per hour");
        }

        userRateLimitRepository.save(row);
    }

    private UserRateLimit createRow(String email, long nowMs) {
        try {
            // Insert first so subsequent concurrent callers block on FOR UPDATE
            // rather than both inserting. Unique PK on user_email handles races.
            UserRateLimit created = UserRateLimit.create(email, nowMs);
            userRateLimitRepository.saveAndFlush(created);
            // Re-read with lock so we own the row for this TX.
            return userRateLimitRepository.findByUserEmailForUpdate(email)
                    .orElse(created);
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same email; lock the existing row.
            return userRateLimitRepository.findByUserEmailForUpdate(email)
                    .orElseThrow(() -> e);
        }
    }
}
