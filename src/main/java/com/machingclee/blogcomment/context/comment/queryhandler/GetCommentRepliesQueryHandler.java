package com.machingclee.blogcomment.context.comment.queryhandler;

import com.machingclee.domain.util.common.query.interfaces.QueryHandler;
import com.machingclee.blogcomment.common.dto.CommentsWithTotal;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.entity.User;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.common.jpa.repository.UserRepository;
import com.machingclee.blogcomment.context.comment.query.GetCommentRepliesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Returns a PAGED flat list of all descendant replies for a root comment,
 * found via a recursive CTE ({@code WITH RECURSIVE}).
 * <p>
 * Native queries can't be mapped to {@link Comment} entities because Hibernate
 * tries to resolve the {@code parentComment} join-table columns. Instead we use
 * raw {@code Object[]} rows and build the DTOs manually.
 */
@Component
@RequiredArgsConstructor
public class GetCommentRepliesQueryHandler implements QueryHandler<GetCommentRepliesQuery, CommentsWithTotal> {

    private final CommentRepository repository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CommentsWithTotal handle(GetCommentRepliesQuery query) {
        int offset = query.getPage() * query.getLimit();
        List<Object[]> rows = repository.findDescendantComments(
                query.getCommentId(), query.getLimit(), offset);
        long total = repository.countDescendantComments(query.getCommentId());

        // Collect emails for picture lookup while building DTOs.
        List<String> dtoEmails = new ArrayList<>();
        List<Comment.DTO> dtos = new ArrayList<>();
        Instant now = Instant.now();

        for (Object[] row : rows) {
            Comment.DTO dto = mapRow(row);
            if (dto == null) continue;
            String email = row[1] != null ? ((String) row[1]).trim() : "";
            dtoEmails.add(!email.isEmpty() ? email : "");
            dto.setTimeAgo(GetCommentsQueryHandler.formatTimeAgo(dto.getCreatedAt(), now));
            dtos.add(dto);
        }

        // Load pictures and apply to DTOs.
        Set<String> uniqueEmails = new HashSet<>(dtoEmails);
        uniqueEmails.remove("");
        if (!uniqueEmails.isEmpty()) {
            Map<String, String> pictureByEmail = new HashMap<>();
            for (User u : userRepository.findByUserEmailIn(uniqueEmails)) {
                if (u.getUserPicture() != null && !u.getUserPicture().isBlank()) {
                    pictureByEmail.put(u.getUserEmail(), u.getUserPicture());
                }
            }
            for (int i = 0; i < dtos.size(); i++) {
                String email = dtoEmails.get(i);
                if (!email.isEmpty()) {
                    dtos.get(i).setPicture(pictureByEmail.get(email));
                }
            }
        }

        // Direct-child reply counts (same semantics as root GET /comments).
        // Nested replies never ship a `children` tree, so the UI relies on this field.
        Set<UUID> replyIds = new HashSet<>();
        for (Comment.DTO dto : dtos) {
            if (dto.getId() != null) replyIds.add(dto.getId());
        }
        Map<UUID, Long> replyCounts = loadDirectReplyCounts(replyIds);
        for (Comment.DTO dto : dtos) {
            if (dto.getId() == null) continue;
            dto.setReplyCount(replyCounts.getOrDefault(dto.getId(), 0L).intValue());
        }

        return CommentsWithTotal.builder()
                .comments(dtos)
                .total(total)
                .build();
    }

    /** Non-deleted direct children only (not recursive descendants). */
    private Map<UUID, Long> loadDirectReplyCounts(Set<UUID> parentIds) {
        if (parentIds.isEmpty()) return Map.of();
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : repository.countDirectReplies(parentIds)) {
            UUID parentId = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            map.put(parentId, count);
        }
        return map;
    }

    /**
     * Map a raw row to {@link Comment.DTO}.
     * Column order: [id(0), user_email(1), user_name(2), message(3), is_deleted(4),
     *                 article_uuid(5), created_at(6), created_at_hk(7), parent_comment_id(8)]
     */
    private Comment.DTO mapRow(Object[] row) {
        if (row[0] == null) return null;
        Comment.DTO dto = new Comment.DTO();
        dto.setId((UUID) row[0]);
        // user_email at row[1] — not exposed on DTO
        dto.setName(row[2] != null ? (String) row[2] : "Anonymous");
        String rawMessage = row[3] != null ? (String) row[3] : "";
        boolean isDeleted = Boolean.TRUE.equals(row[4]);
        dto.setIsDeleted(isDeleted);
        dto.setMessage(isDeleted ? "Deleted by user" : rawMessage);
        dto.setArticleUuid(row[5] != null ? (UUID) row[5] : null);
        dto.setCreatedAt(row[6] instanceof Number n ? n.doubleValue() : null);
        dto.setCreatedAtHk(row[7] != null ? (String) row[7] : null);
        dto.setParentCommentId(row[8] != null ? (UUID) row[8] : null);
        dto.setChildren(new ArrayList<>());
        return dto;
    }
}
