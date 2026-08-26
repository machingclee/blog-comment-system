package com.machingclee.blogcomment.context.comment.query;

import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.query.interfaces.Query;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Count non-deleted comments for every article that has at least one comment.
 */
@BoundedContext("Blog Comments")
@Builder
@Data
public class GetCommentCountsQuery implements Query<Map<UUID, Long>> {
}
