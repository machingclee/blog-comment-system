package com.machingclee.blogcomment.context.comment.event;

import com.machingclee.blogcomment.common.jpa.entity.Comment;
import lombok.Builder;
import lombok.Data;

/**
 * Raised after a comment is persisted. Delivered to policies and the
 * domain event logger via the EventQueue.
 * <p>
 * Enqueued with {@code addTransactional} so listeners run only after the
 * create TX commits — SES / notification side effects must not roll back
 * the saved comment.
 * <p>
 * {@code parentOwnerEmail} / {@code authorEmail} are carried separately because
 * {@link Comment.DTO} never exposes user_email (privacy — stored on the entity only).
 */
@Builder
@Data
public class CommentCreatedEvent {
    private Comment.DTO savedComment;
    private Comment.DTO parentComment;
    /** Parent row's user_email; null when this is a top-level comment. */
    private String parentOwnerEmail;
    private String parentOwnerName;
    /**
     * Author of the new comment (entity user_email). Carried separately because
     * {@link Comment.DTO} never exposes email.
     */
    private String authorEmail;
    /** Article title (English) for notification emails. */
    private String articleTitle;
    /** Article title (Traditional Chinese) for notification emails. */
    private String articleTitleTc;
    /** Full URL to the article page, for one-click navigation from the email. */
    private String articleUrl;
}
