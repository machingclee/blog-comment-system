package com.machingclee.blogcomment.context.comment.event;

import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

/**
 * Raised after a comment is soft-deleted (isDeleted = true).
 * Delivered to policies and the domain event logger via the EventQueue.
 */
@Builder
@Data
public class CommentDeletedEvent {
    private String requestUserEmailFromToken;
    private String commentOwnerEmail;
    private boolean wasAlreadyDeleted;
    private Comment.DTO deletedComment;
}
