package com.machingclee.blogcomment.context.comment.event;

import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

/**
 * Raised after a comment's message is updated.
 * Delivered to policies and the domain event logger via the EventQueue.
 */
@Builder
@Data
public class CommentUpdatedEvent {
    private Comment.DTO updatedComment;
    private String requestUserEmailFromToken;
    private String commentOwnerEmail;
}
