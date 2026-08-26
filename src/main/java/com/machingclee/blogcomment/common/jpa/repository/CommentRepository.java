package com.machingclee.blogcomment.common.jpa.repository;

import com.machingclee.blogcomment.common.dto.CommentCountDTO;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * All comments of one article with the parent eager-loaded.
     * Soft-deleted rows are included so the UI can keep a tombstone in the thread
     * (and preserve reply tree structure under deleted parents).
     * `left join fetch` so top-level comments (no join row) are not dropped;
     * the fetch avoids N+1 when building the reply tree.
     */
    @Query("""
            select c from Comment c
            left join fetch c.parentComment
            where c.articleUuid = :articleUuid
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findByArticleUuidWithParent(@Param("articleUuid") UUID articleUuid);

    /**
     * Count non-deleted comments for every article that has at least one comment.
     */
    @Query("""
            SELECT new com.machingclee.blogcomment.common.dto.CommentCountDTO(c.articleUuid, COUNT(c))
            FROM Comment c
            WHERE c.isDeleted = false
            GROUP BY c.articleUuid
            """)
    List<CommentCountDTO> countAllComments();

    /**
     * Distinct participant emails for the whole thread that contains {@code startId}
     * (walk ancestors to the root, then all descendants), excluding {@code authorEmail}.
     * <p>
     * Single Postgres recursive CTE — avoids loading the full article tree and
     * the N+1 that came from lazy {@code childComments} traversal.
     */
    @Query(value = """
            WITH RECURSIVE
            climb AS (
                SELECT c.id AS id, r.parent_comment_id AS parent_id
                FROM blog_system.comment c
                LEFT JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
                WHERE c.id = :startId
                UNION ALL
                SELECT c.id, r.parent_comment_id
                FROM blog_system.comment c
                LEFT JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
                INNER JOIN climb cl ON cl.parent_id = c.id
            ),
            root AS (
                SELECT COALESCE(
                    (SELECT id FROM climb WHERE parent_id IS NULL LIMIT 1),
                    CAST(:startId AS uuid)
                ) AS id
            ),
            thread AS (
                SELECT c.id, c.user_email
                FROM blog_system.comment c
                WHERE c.id = (SELECT id FROM root)
                UNION ALL
                SELECT c.id, c.user_email
                FROM blog_system.comment c
                INNER JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
                INNER JOIN thread t ON r.parent_comment_id = t.id
            )
            SELECT DISTINCT LOWER(TRIM(t.user_email))
            FROM thread t
            WHERE t.user_email IS NOT NULL
              AND TRIM(t.user_email) <> ''
              AND LOWER(TRIM(t.user_email)) <> LOWER(TRIM(:authorEmail))
            """, nativeQuery = true)
    List<String> findThreadParticipantEmailsExcludingAuthor(
            @Param("startId") UUID startId,
            @Param("authorEmail") String authorEmail);

    // ── Paged root comments (for Load More on the article level) ──────

    /**
     * Page of top-level comments (no parent row in {@code rel_comment_comment}).
     * Uses JPQL so Hibernate handles the join-table mapping natively.
     */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.articleUuid = :articleUuid
            AND c.parentComment IS NULL
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    Page<Comment> findRootComments(@Param("articleUuid") UUID articleUuid, Pageable pageable);

    /**
     * Bulk count of non-deleted <em>direct</em> children for the given parent comment ids
     * (one hop via {@code rel_comment_comment} — not recursive descendants).
     * Returns {@code [parent_comment_id, count]} pairs. Parents with zero children are omitted.
     */
    @Query(value = """
            SELECT r.parent_comment_id, COUNT(*) FILTER (WHERE c.is_deleted = false)
            FROM blog_system.rel_comment_comment r
            JOIN blog_system.comment c ON c.id = r.child_comment_id
            WHERE r.parent_comment_id IN (:parentIds)
            GROUP BY r.parent_comment_id
            """, nativeQuery = true)
    List<Object[]> countDirectReplies(@Param("parentIds") Set<UUID> parentIds);

    /**
     * One-hop children of the given parents (includes soft-deleted rows so the UI
     * can keep tombstones). Ordered oldest-first for stable display.
     * <p>
     * Native query via {@code rel_comment_comment} — same edge table as
     * {@link #countDirectReplies(Set)} — so parent/child resolution stays consistent
     * with the join-table mapping (JPQL {@code parentComment.id} can be flaky there).
     * Column order matches {@link #findDescendantComments}: id, user_email, user_name,
     * message, is_deleted, article_uuid, created_at, created_at_hk, parent_comment_id.
     */
    @Query(value = """
            SELECT c.id, c.user_email, c.user_name, c.message, c.is_deleted,
                   c.article_uuid, c.created_at, c.created_at_hk, r.parent_comment_id
            FROM blog_system.comment c
            JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
            WHERE r.parent_comment_id IN (:parentIds)
            ORDER BY c.created_at ASC, c.id ASC
            """, nativeQuery = true)
    List<Object[]> findDirectChildrenRows(@Param("parentIds") Set<UUID> parentIds);

    // ── Paged descendant replies (WITH RECURSIVE for Load More under a comment) ──

    /**
     * Page of all descendant replies (direct + nested) of {@code rootCommentId},
     * returned as raw columns to avoid Hibernate trying to resolve the
     * {@code parentComment} join-table mapping from a native query.
     * <p>
     * Column order: {@code [id, user_email, user_name, message, is_deleted,
     * article_uuid, created_at, created_at_hk, parent_comment_id]}.
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
              SELECT c.id, c.user_email, c.user_name, c.message, c.is_deleted,
                     c.article_uuid, c.created_at, c.created_at_hk, r.parent_comment_id
              FROM blog_system.comment c
              JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
              WHERE r.parent_comment_id = :rootCommentId
              UNION ALL
              SELECT c.id, c.user_email, c.user_name, c.message, c.is_deleted,
                     c.article_uuid, c.created_at, c.created_at_hk, r.parent_comment_id
              FROM blog_system.comment c
              JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
              JOIN descendants d ON r.parent_comment_id = d.id
            )
            SELECT * FROM descendants
            ORDER BY created_at ASC, id ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Object[]> findDescendantComments(@Param("rootCommentId") UUID rootCommentId,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    @Query(value = """
            WITH RECURSIVE descendants AS (
              SELECT c.id
              FROM blog_system.comment c
              JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
              WHERE r.parent_comment_id = :rootCommentId
              UNION ALL
              SELECT c.id
              FROM blog_system.comment c
              JOIN blog_system.rel_comment_comment r ON r.child_comment_id = c.id
              JOIN descendants d ON r.parent_comment_id = d.id
            )
            SELECT COUNT(*) FROM descendants
            """, nativeQuery = true)
    long countDescendantComments(@Param("rootCommentId") UUID rootCommentId);
}
