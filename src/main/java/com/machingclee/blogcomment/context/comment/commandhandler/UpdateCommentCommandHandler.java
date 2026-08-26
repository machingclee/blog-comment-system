package com.machingclee.blogcomment.context.comment.commandhandler;

import com.machingclee.domain.util.common.interfaces.CommandHandler;
import com.machingclee.domain.util.common.interfaces.EventQueue;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.jpa.DTOMapper;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.context.comment.command.UpdateCommentCommand;
import com.machingclee.blogcomment.context.comment.event.CommentUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateCommentCommandHandler
        implements CommandHandler<UpdateCommentCommand, Comment.DTO> {

    private final CommentRepository commentRepository;
    private final DTOMapper dtoMapper;

    @Override
    public Comment.DTO handle(EventQueue eventQueue, UpdateCommentCommand command) {
        if (command.getMessage() == null || command.getMessage().isBlank()) {
            throw new BadRequestException("message is required");
        }

        Comment comment = commentRepository.findById(command.getCommentId())
                .orElseThrow(() -> new BadRequestException("comment not found"));

        // Capture pre-mutation state for the policy to enforce business rules.
        String commentOwnerEmail = comment.getUserEmail();

        comment.setMessage(command.getMessage().trim());
        comment = commentRepository.saveAndFlush(comment);

        Comment.DTO dto = dtoMapper.toDTO(comment);
        eventQueue.add(CommentUpdatedEvent.builder()
                .updatedComment(dto)
                .requestUserEmailFromToken(command.getRequestUserEmailFromToken())
                .commentOwnerEmail(commentOwnerEmail)
                .build());
        return dto;
    }
}
