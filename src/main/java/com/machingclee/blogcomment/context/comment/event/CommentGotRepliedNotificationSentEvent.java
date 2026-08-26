package com.machingclee.blogcomment.context.comment.event;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Raised after SES accepted a reply-notification email.
 * Logged in the audit event table for the notification command's request chain.
 */
@Builder
@Data
public class CommentGotRepliedNotificationSentEvent {
    private String messageId;
    private String toAddress;
    private String subject;
    private UUID replyCommentId;
    private UUID parentCommentId;
    private UUID articleUuid;
    private List<String> threadEmails;
}
