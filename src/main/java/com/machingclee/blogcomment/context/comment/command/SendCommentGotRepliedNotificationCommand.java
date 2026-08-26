package com.machingclee.blogcomment.context.comment.command;

import com.machingclee.domain.util.annotation.Actor;
import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.interfaces.Command;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Send reply-notification emails for a newly created reply.
 * <p>
 * Invoked from {@code CommentPolicy} after the comment TX commits so SES
 * failure cannot roll back the saved reply. Recipient resolution
 * (thread participants + site owners) and the skip decision live in the
 * command handler — the policy always dispatches this for replies.
 */
@BoundedContext("Notification")
@Actor("System")
@Builder
@Data
public class SendCommentGotRepliedNotificationCommand implements Command<SendCommentGotRepliedNotificationCommand.Result> {

    /**
     * Who wrote the reply
     */
    private String replierName;
    private String replierEmail;

    /**
     * The parent comment's owner — used for "You wrote" vs "Name wrote" label.
     */
    private String parentOwnerName;
    private String parentOwnerEmail;

    private String replyMessage;
    private UUID replyCommentId;
    private UUID parentCommentId;
    private UUID articleUuid;

    /**
     * Article title (English) for the notification email.
     */
    private String articleTitle;
    /**
     * Article title (Traditional Chinese) for the notification email.
     */
    private String articleTitleTc;
    /**
     * Full URL to the article page for one-click navigation.
     */
    private String articleUrl;
    /**
     * Parent comment message (the comment being replied to).
     */
    private String parentCommentMessage;
    /**
     * Parent comment createdAt (epoch millis).
     */
    private Double parentCommentCreatedAt;

    @Data
    @Builder
    public static class Result {
        private String messageId;
        private String toAddress;
        private String subject;
        /**
         * true when SES was skipped (e.g. no recipients)
         */
        private boolean skipped;
        private String skipReason;
        /**
         * How many addresses would / did receive mail (0 when skipped).
         */
        private int recipientCount;
    }
}
