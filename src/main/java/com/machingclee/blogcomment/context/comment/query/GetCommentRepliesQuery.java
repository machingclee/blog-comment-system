package com.machingclee.blogcomment.context.comment.query;

import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.query.interfaces.Query;
import com.machingclee.blogcomment.common.dto.CommentsWithTotal;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Paged descendant replies for a root comment (GET /comments/{commentId}/replies).
 * Uses a recursive CTE to find all nested descendants and pages them as a flat list.
 */
@BoundedContext("Blog Comments")
@Builder
@Data
public class GetCommentRepliesQuery implements Query<CommentsWithTotal> {
    private UUID commentId;
    /** 0-indexed page number (default 0). */
    @Builder.Default
    private int page = 0;
    /** Page size (default 20). */
    @Builder.Default
    private int limit = 20;
}
