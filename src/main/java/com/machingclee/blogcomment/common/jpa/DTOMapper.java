package com.machingclee.blogcomment.common.jpa;

import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.entity.User;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Entity → nested DTO mapping (domain-util skill, Style A).
 * `unmappedTargetPolicy = ERROR` makes forgotten target fields a compile error.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DTOMapper {

    String DELETED_MESSAGE = "Deleted by user";

    @Mapping(target = "name", source = "userName")
    // user_email is intentionally not on Comment.DTO — stored server-side only.
    @Mapping(target = "picture", ignore = true) // filled from comment_user after map
    @Mapping(target = "parentCommentId", source = "parentComment.id")
    // Children are assembled in the query handler from a flat list so we never
    // touch the lazy childComments collection (avoids N+1 on rel_comment_comment).
    @Mapping(target = "children", ignore = true)
    // Pre-computed display fields — set by GetCommentsQueryHandler after mapping.
    @Mapping(target = "replyCount", ignore = true)
    @Mapping(target = "timeAgo", ignore = true)
    @Mapping(target = "flatReplies", ignore = true)
    Comment.DTO toDTO(Comment entity);

    /**
     * Soft-deleted comments keep their row (for thread structure) but never expose
     * the original body — clients render this placeholder message instead.
     */
    @AfterMapping
    default void maskDeletedCommentMessage(Comment entity, @MappingTarget Comment.DTO dto) {
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            dto.setMessage(DELETED_MESSAGE);
            dto.setIsDeleted(true);
        }
    }

    User.DTO toDTO(User user);
}
