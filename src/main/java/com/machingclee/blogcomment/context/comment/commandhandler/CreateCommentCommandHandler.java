package com.machingclee.blogcomment.context.comment.commandhandler;

import com.machingclee.domain.util.common.interfaces.CommandHandler;
import com.machingclee.domain.util.common.interfaces.EventQueue;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.jpa.DTOMapper;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.context.comment.command.CreateCommentCommand;
import com.machingclee.blogcomment.context.comment.event.CommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateCommentCommandHandler
        implements CommandHandler<CreateCommentCommand, Comment.DTO> {

    private final CommentRepository commentRepository;
    private final DTOMapper dtoMapper;

    @Override
    public Comment.DTO handle(EventQueue eventQueue, CreateCommentCommand command) {
        if (command.getName() == null || command.getName().isBlank()
                || command.getMessage() == null || command.getMessage().isBlank()) {
            throw new BadRequestException("name and message are required");
        }

        Comment comment = Comment.create(
                parseUuid(command.getArticleUuid(), "invalid articleUuid"),
                command.getName().trim(),
                command.getEmail() == null || command.getEmail().isBlank() ? "" : command.getEmail().trim(),
                command.getMessage().trim());

        Comment.DTO parentDTO = null;
        String parentOwnerEmail = null;
        String parentOwnerName = null;
        UUID parentId = null;

        if (command.getParentId() != null && !command.getParentId().isBlank()) {
            parentId = parseUuid(command.getParentId(), "invalid parentId");
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BadRequestException("parent comment not found"));
            // Invariant at write time (create event is post-commit and cannot roll this back).
            if (Boolean.TRUE.equals(parent.getIsDeleted())) {
                throw new BadRequestException("cannot reply to a deleted comment");
            }
            parentOwnerEmail = parent.getUserEmail();
            parentOwnerName = parent.getUserName();
            // Persist the child FIRST so @Generated(INSERT) re-reads id/createdAt/
            // createdAtHk onto the instance (cascade-persisted children miss the
            // re-read and the create response would ship a null id — the frontend
            // then fabricates "unknown-<ts>" ids and socket fan-out can never
            // dedupe against GET /replies rows). The rel edge is owned by the
            // parent's childComments collection (the child's parentComment
            // association is read-only), so link and flush the parent after.
            comment = commentRepository.saveAndFlush(comment);
            parent.addComment(comment);
            commentRepository.saveAndFlush(parent);
            parentDTO = dtoMapper.toDTO(parent);
        } else {
            comment = commentRepository.saveAndFlush(comment);
        }

        // saveAndFlush so the DB-generated id/createdAt/createdAtHk are re-read
        // (@Generated(INSERT)) before mapping the response.

        Comment.DTO commentDTO = dtoMapper.toDTO(comment);
        // The child's parentComment association is read-only and was never set on
        // the entity — stamp parentCommentId manually so replies carry their parent.
        if (parentId != null) {
            commentDTO.setParentCommentId(parentId);
        }
        // Avatar lives on comment_user; stamp it onto the response from the command.
        if (command.getPicture() != null && !command.getPicture().isBlank()) {
            commentDTO.setPicture(command.getPicture().trim());
        }

        // POST_COMMIT: notification policy / SES must not roll back the comment.
        eventQueue.addTransactional(CommentCreatedEvent.builder()
                .savedComment(commentDTO)
                .parentComment(parentDTO)
                .parentOwnerEmail(parentOwnerEmail)
                .parentOwnerName(parentOwnerName)
                .authorEmail(comment.getUserEmail())
                .articleTitle(command.getArticleTitle())
                .articleTitleTc(command.getArticleTitleTc())
                .articleUrl(command.getArticleUrl())
                .build());
        return commentDTO;
    }

    private static UUID parseUuid(String raw, String errorMessage) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(errorMessage);
        }
    }
}
