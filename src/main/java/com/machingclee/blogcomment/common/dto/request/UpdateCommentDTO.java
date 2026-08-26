package com.machingclee.blogcomment.common.dto.request;

/**
 * PUT /comments/{commentId} request body.
 * Only the message can be changed; identity is verified from the Google token.
 */
public record UpdateCommentDTO(
        String message) {
}
