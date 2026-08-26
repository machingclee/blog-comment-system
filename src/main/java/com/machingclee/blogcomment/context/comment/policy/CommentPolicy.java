package com.machingclee.blogcomment.context.comment.policy;

import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.exception.UnauthorizedException;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.context.comment.command.SendCommentGotRepliedNotificationCommand;
import com.machingclee.blogcomment.context.comment.event.CommentCreatedEvent;
import com.machingclee.blogcomment.context.comment.event.CommentDeletedEvent;
import com.machingclee.blogcomment.context.comment.event.CommentUpdatedEvent;
import com.machingclee.domain.util.common.interfaces.CommandInvoker;
import com.machingclee.domain.util.common.interfaces.Invariant;
import com.machingclee.domain.util.common.interfaces.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enforces comment business rules from domain events that are dispatched
 * inside the command transaction (update / delete).
 * <p>
 * Create-time invariants that must roll back the write (e.g. reply to a
 * deleted parent) live in {@code CreateCommentCommandHandler} because
 * {@code CommentCreatedEvent} is post-commit.
 * <p>
 * Always dispatches {@link SendCommentGotRepliedNotificationCommand} after create
 * (root-level comments have {@code parent == null} — that is normal).
 * Recipient resolution and skip decisions belong to that command's handler.
 */
@Component
public class CommentPolicy implements Policy {

    private static final Logger log = LoggerFactory.getLogger(CommentPolicy.class);
    private final CommandInvoker commandInvoker;

    public CommentPolicy(CommandInvoker commandInvoker) {
        this.commandInvoker = commandInvoker;
    }

    @EventListener
    @Invariant("""
            - Only the comment owner can edit the comment.
            - Cannot edit a (soft) deleted comment.
            """)
    public void onCommentUpdated(CommentUpdatedEvent event) {
        Comment.DTO dto = event.getUpdatedComment();

        if (Boolean.TRUE.equals(dto.getIsDeleted())) {
            throw new BadRequestException("cannot edit a deleted comment");
        }

        if (!event.getRequestUserEmailFromToken().equalsIgnoreCase(event.getCommentOwnerEmail())) {
            throw new UnauthorizedException("you can only edit your own comments");
        }
    }

    @EventListener
    @Invariant("""
            - Only the comment owner can delete the comment.
            - If it is already deleted, cannot delete again.
            """)
    public void onCommentDeleted(CommentDeletedEvent event) {
        Comment.DTO dto = event.getDeletedComment();

        if (event.isWasAlreadyDeleted()) {
            throw new BadRequestException("comment is already deleted");
        }

        if (!event.getRequestUserEmailFromToken().equalsIgnoreCase(event.getCommentOwnerEmail())) {
            throw new UnauthorizedException("only the comment owner can delete this comment");
        }

        log.info("Comment soft-deleted id={}", dto.getId());
    }


    @Invariant("""
            Every new comment (root or reply) schedules SendCommentReplyNotificationCommand.
            Root-level comments have no parent — that is expected. Whether mail is sent
            is decided by the command handler (thread participants + site owners).
            """)
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) throws Exception {
        Comment.DTO comment = event.getSavedComment();
        Comment.DTO parent = event.getParentComment(); // null for root-level — correct

        log.info("Comment created id={} parentId={} — dispatching notification command",
                comment != null ? comment.getId() : null,
                parent != null ? parent.getId() : null);

        commandInvoker.invoke(SendCommentGotRepliedNotificationCommand.builder()
                .replierName(comment != null ? comment.getName() : null)
                .replierEmail(event.getAuthorEmail())
                .parentOwnerName(event.getParentOwnerName())
                .parentOwnerEmail(event.getParentOwnerEmail())
                .replyMessage(comment != null ? comment.getMessage() : null)
                .replyCommentId(comment != null ? comment.getId() : null)
                .parentCommentId(parent != null ? parent.getId() : null)
                .articleUuid(comment != null ? comment.getArticleUuid() : null)
                .articleTitle(event.getArticleTitle())
                .articleTitleTc(event.getArticleTitleTc())
                .articleUrl(event.getArticleUrl())
                .parentCommentMessage(parent != null ? parent.getMessage() : null)
                .parentCommentCreatedAt(parent != null ? parent.getCreatedAt() : null)
                .build());
    }
}
