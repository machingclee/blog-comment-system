package com.machingclee.blogcomment.context.comment.query;

import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.query.interfaces.Query;
import com.machingclee.blogcomment.common.dto.CommentsWithTotal;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Paged root-level comments for one article (GET /comments).
 * Only includes comments without a parent — replies load separately
 * via {@link GetCommentRepliesQuery}.
 */
@BoundedContext("Blog Comments")
@Builder
@Data
public class GetCommentsQuery implements Query<CommentsWithTotal> {
    private UUID articleUuid;
    /** 0-indexed page number (default 0). */
    @Builder.Default
    private int page = 0;
    /** Page size (default 20). */
    @Builder.Default
    private int limit = 20;
}
