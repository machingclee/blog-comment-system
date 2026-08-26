package com.machingclee.blogcomment.context.user.command;

import com.machingclee.domain.util.annotation.Actor;
import com.machingclee.domain.util.annotation.BoundedContext;
import com.machingclee.domain.util.common.interfaces.Command;
import com.machingclee.blogcomment.common.jpa.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * Create a blog user before they can post comments.
 * Identity (email/name) is resolved from the VERIFIED Google token by the
 * controller before the command is built. Annotations feed /docs only.
 */
@BoundedContext("Blog Comments")
@Actor("System")
@Builder
@Data
public class CreateUserCommand implements Command<User.DTO> {
    private String userEmail;
    private String userName;
    /**
     * Google profile photo URL from the ID token (optional).
     */
    private String userPicture;
}
