package com.machingclee.blogcomment.common.dto.request;

/**
 * POST /api/comments request body (domain-util skill, Style B).
 * Identity (name/email) is overridden by the verified Google token in the
 * controller; UUID strings are parsed in the command handler with friendly 400s.
 */
public record CreateCommentDTO(
        String articleUuid,
        String articleTitle,
        String articleTitleTc,
        String articleUrl,
        String name,
        String email,
        String message,
        String parentId) {
}
