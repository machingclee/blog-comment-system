package com.machingclee.blogcomment.common.dto;

import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Paged wrapper: the list of DTOs for the current page plus the total count
 * across all pages. The frontend uses {@code total} to render "Load More"
 * buttons and page indicators.
 */
@Data
@Builder
public class CommentsWithTotal {
    private List<Comment.DTO> comments;
    private long total;
}
