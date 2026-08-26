package com.machingclee.blogcomment.context.user.commandhandler;

import com.machingclee.domain.util.common.interfaces.CommandHandler;
import com.machingclee.domain.util.common.interfaces.EventQueue;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.jpa.entity.User;
import com.machingclee.blogcomment.common.jpa.repository.UserRepository;
import com.machingclee.blogcomment.context.user.command.CreateUserCommand;
import com.machingclee.blogcomment.context.user.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, User.DTO> {

    private final UserRepository repository;

    @Override
    public User.DTO handle(EventQueue eventQueue, CreateUserCommand command) {
        if (command.getUserEmail() == null || command.getUserEmail().isBlank()
                || command.getUserName() == null || command.getUserName().isBlank()) {
            throw new BadRequestException("userEmail and userName are required");
        }

        // Idempotent: if the user already exists, return the existing one.
        var existing = repository.findByUserEmail(command.getUserEmail().trim());
        if (existing.isPresent()) {
            return toDTO(existing.get());
        }

        String picture = blankToNull(command.getUserPicture());
        User user = User.create(
                command.getUserEmail().trim(),
                command.getUserName().trim(),
                picture);

        User saved = repository.saveAndFlush(user);

        User.DTO dto = toDTO(saved);
        eventQueue.add(UserCreatedEvent.builder().savedUser(dto).build());
        return dto;
    }

    private static User.DTO toDTO(User entity) {
        return User.DTO.builder()
                .id(entity.getId())
                .userEmail(entity.getUserEmail())
                .userName(entity.getUserName())
                .userPicture(entity.getUserPicture())
                .createdAt(entity.getCreatedAt())
                .createdAtHk(entity.getCreatedAtHk())
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank())
            return null;
        return value.trim();
    }
}
