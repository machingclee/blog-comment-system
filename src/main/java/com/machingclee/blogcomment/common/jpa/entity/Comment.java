package com.machingclee.blogcomment.common.jpa.entity;

import com.machingclee.domain.util.annotation.BoundedContext;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.*;

@BoundedContext("Blog Comments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@DynamicInsert
@DynamicUpdate
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "comment", schema = "blog_system")
public class Comment {

    // region columns
    @Id
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("ulid_as_uuid()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "message", nullable = false)
    private String message;

    @ColumnDefault("false")
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "article_uuid", nullable = false)
    private UUID articleUuid;

    // DB-generated on insert (gen_created_at()); re-read after flush via
    // @Generated(INSERT)
    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_created_at()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private Double createdAt;

    @Setter(AccessLevel.NONE)
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_created_at_hk_timestr()")
    @Column(name = "created_at_hk", nullable = false, updatable = false)
    private String createdAtHk;
    // endregion

    // region factories

    public static Comment create(UUID articleUuid, String userName, String userEmail, String message) {
        Comment c = new Comment();
        c.articleUuid = articleUuid;
        c.userName = userName;
        c.userEmail = userEmail;
        c.message = message;
        return c;
    }

    // endregion

    // region relations

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinTable(name = "rel_comment_comment", schema = "blog_system",
            joinColumns = @JoinColumn(name = "child_comment_id", referencedColumnName = "id", insertable = false, updatable = false),
            inverseJoinColumns = @JoinColumn(name = "parent_comment_id", referencedColumnName = "id", insertable = false, updatable = false)
    )
    private Comment parentComment;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(name = "rel_comment_comment", schema = "blog_system",
            joinColumns = @JoinColumn(name = "parent_comment_id", referencedColumnName = "id", insertable = true, updatable = true),
            inverseJoinColumns = @JoinColumn(name = "child_comment_id", referencedColumnName = "id", insertable = true, updatable = true)
    )
    private Set<Comment> childComments = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_email", insertable = false, updatable = false)
    private User user;
    // endregion

    // region domain methods
    public void addComment(Comment comment) {
        this.childComments.add(comment);
    }
    // endregion

    // region DTOs — Style A (domain return shape, see the domain-util skill)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DTO {
        private UUID id;
        private UUID articleUuid;
        private String name; // maps from userName
        // user_email is stored on the entity for auth / notifications / rate limits,
        // but never exposed on API responses (privacy).
        private String picture; // Google avatar URL (from comment_user; not on comment row)
        private String message;
        private Boolean isDeleted;
        private Double createdAt;
        private String createdAtHk;
        private UUID parentCommentId; // from parentComment.id
        private List<DTO> children; // one level, from childComments

        // ── Pre-computed display fields (set by GetCommentsQueryHandler) ──────
        /**
         * Count of non-deleted direct children (shallow).
         */
        private Integer replyCount;
        /**
         * Relative time label computed server-side (e.g. "3h ago").
         */
        private String timeAgo;
        /**
         * Pre-flattened nested replies with quote chains.
         * Only populated on top-level comments (null for replies themselves).
         * Eliminates the O(N²) frontend collectFlatReplies tree walk.
         */
        private List<FlatReply> flatReplies;
    }

    /**
     * A single flattened reply entry: the reply comment itself plus the chain of
     * ancestor comments (oldest → newest, excluding the top-level root) needed to
     * render the LIHKG-style nested quote rails.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlatReply {
        private DTO comment;
        /**
         * Ancestors from depth-1 reply down to the direct parent (oldest-first).
         */
        private List<DTO> quoteChain;
    }
    // endregion
}
