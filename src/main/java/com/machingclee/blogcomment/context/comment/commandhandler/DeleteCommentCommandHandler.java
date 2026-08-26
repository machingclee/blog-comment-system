package com.machingclee.blogcomment.context.comment.commandhandler;

import com.machingclee.domain.util.common.interfaces.CommandHandler;
import com.machingclee.domain.util.common.interfaces.EventQueue;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.jpa.DTOMapper;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.context.comment.command.DeleteCommentCommand;
import com.machingclee.blogcomment.context.comment.event.CommentDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteCommentCommandHandler
        implements CommandHandler<DeleteCommentCommand, Comment.DTO> {

    private final CommentRepository commentRepository;
    private final DTOMapper dtoMapper;

    @Override
    public Comment.DTO handle(EventQueue eventQueue, DeleteCommentCommand command) {
        Comment comment = commentRepository.findById(command.getCommentId())
                .orElseThrow(() -> new BadRequestException("comment not found"));

        // Capture pre-mutation state for the policy to enforce business rules.
        String commentOwnerEmail = comment.getUserEmail();
        boolean wasAlreadyDeleted = Boolean.TRUE.equals(comment.getIsDeleted());

        comment.setIsDeleted(true);
        comment = commentRepository.saveAndFlush(comment);

        Comment.DTO dto = dtoMapper.toDTO(comment);
        eventQueue.add(CommentDeletedEvent.builder()
                .deletedComment(dto)
                .requestUserEmailFromToken(command.getRequestUserEmailFromToken())
                .commentOwnerEmail(commentOwnerEmail)
                .wasAlreadyDeleted(wasAlreadyDeleted)
                .build());
        return dto;
    }
}
