package com.machingclee.blogcomment.context.comment.command;

import com.machingclee.domain.util.annotation.Actor;
import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.interfaces.Command;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Update a comment's message.
 * Only the comment author (matched by userEmail) can edit.
 */
@BoundedContext("Blog Comments")
@Actor("Commenter")
@Builder
@Data
public class UpdateCommentCommand implements Command<Comment.DTO> {
    private UUID commentId;
    private String message;
    private String requestUserEmailFromToken;
}
