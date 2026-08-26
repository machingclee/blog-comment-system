package com.machingclee.blogcomment.context.user.event;

import com.machingclee.blogcomment.common.jpa.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * Raised after a user is persisted. Delivered to policies and the
 * domain event logger via the EventQueue.
 */
@Builder
@Data
public class UserCreatedEvent {
    private User.DTO savedUser;
}
