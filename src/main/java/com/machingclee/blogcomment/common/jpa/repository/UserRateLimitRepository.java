package com.machingclee.blogcomment.common.jpa.repository;

import com.machingclee.blogcomment.common.jpa.entity.UserRateLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRateLimitRepository extends JpaRepository<UserRateLimit, String> {

    /**
     * Pessimistic row lock ({@code SELECT … FOR UPDATE}) so concurrent requests
     * for the same user serialize check-and-increment of rate counters.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM UserRateLimit r WHERE r.userEmail = :userEmail")
    Optional<UserRateLimit> findByUserEmailForUpdate(@Param("userEmail") String userEmail);
}
