package com.machingclee.blogcomment.common.jpa.entity;

import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.interfaces.AuditEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit trail for commands and domain events.
 * Physical storage: the "event" table in the "blog_system" Postgres schema
 * (owned by the Prisma migrations in db/prisma — see db/prisma/schema.prisma).
 */
@BoundedContext("Blog Comments")
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event", schema = "blog_system")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BlogcommentEvent implements AuditEvent {

    @Setter(AccessLevel.NONE)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "created_at")
    private Double createdAt;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "event_order")
    private Integer eventOrder;

    @Column(name = "request_user_email")
    private String requestUserEmail;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason = "";
}
