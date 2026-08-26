package com.machingclee.blogcomment.context.comment.command;

import com.machingclee.domain.util.annotation.Actor;
import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.interfaces.Command;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

/**
 * Create a comment (or a reply when parentId is set).
 * Identity (name/email) is resolved from the VERIFIED Google token by the
 * controller before the command is built. Annotations feed /docs only.
 */
@BoundedContext("Blog Comments")
@Actor("Commenter")
@Builder
@Data
public class CreateCommentCommand implements Command<Comment.DTO> {
    private String articleUuid;
    private String articleTitle;
    private String articleTitleTc;
    private String articleUrl;
    private String name;
    private String email;
    private String message;
    private String parentId;
    /**
     * Google avatar URL; stamped onto the response DTO (stored on comment_user).
     */
    private String picture;
}