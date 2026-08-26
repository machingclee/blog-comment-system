package com.machingclee.blogcomment.context.comment.queryhandler;

import com.machingclee.domain.util.common.query.interfaces.QueryHandler;
import com.machingclee.blogcomment.common.dto.CommentsWithTotal;
import com.machingclee.blogcomment.common.jpa.DTOMapper;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.entity.User;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.common.jpa.repository.UserRepository;
import com.machingclee.blogcomment.context.comment.query.GetCommentsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Returns a PAGED list of root-level comments for one article, each with
 * one level of direct children (for the numbered-reply preview list).
 * Nested descendants beyond that hop load via {@link GetCommentRepliesQueryHandler}.
 */
@Component
@RequiredArgsConstructor
public class GetCommentsQueryHandler implements QueryHandler<GetCommentsQuery, CommentsWithTotal> {

    private final CommentRepository repository;
    private final UserRepository userRepository;
    private final DTOMapper dtoMapper;

    @Override
    @Transactional(readOnly = true)
    public CommentsWithTotal handle(GetCommentsQuery query) {
        var pageable = PageRequest.of(query.getPage(), query.getLimit());
        Page<Comment> page = repository.findRootComments(query.getArticleUuid(), pageable);

        List<Comment> roots = page.getContent();
        Set<UUID> rootIds = roots.stream()
                .map(Comment::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // One hop only via join table — same source as countDirectReplies.
        List<Object[]> childRows = rootIds.isEmpty()
                ? List.of()
                : repository.findDirectChildrenRows(rootIds);

        List<Comment.DTO> childDtosFlat = new ArrayList<>();
        List<String> childEmails = new ArrayList<>();
        for (Object[] row : childRows) {
            Comment.DTO childDto = mapChildRow(row);
            if (childDto == null) continue;
            String email = row[1] != null ? ((String) row[1]).trim() : "";
            childEmails.add(email);
            childDtosFlat.add(childDto);
        }

        // Pictures for roots + their direct children.
        Set<String> emails = new HashSet<>();
        for (Comment c : roots) {
            if (c.getUserEmail() != null && !c.getUserEmail().isBlank()) {
                emails.add(c.getUserEmail());
            }
        }
        for (String e : childEmails) {
            if (!e.isEmpty()) emails.add(e);
        }
        Map<String, String> pictureByEmail = loadPicturesByEmail(emails);

        // replyCount for roots and each direct child (badge on preview rows).
        Set<UUID> countIds = new HashSet<>(rootIds);
        for (Comment.DTO child : childDtosFlat) {
            if (child.getId() != null) countIds.add(child.getId());
        }
        Map<UUID, Long> replyCounts = loadReplyCounts(countIds);

        Instant now = Instant.now();
        for (int i = 0; i < childDtosFlat.size(); i++) {
            Comment.DTO childDto = childDtosFlat.get(i);
            String email = childEmails.get(i);
            if (!email.isEmpty()) {
                childDto.setPicture(pictureByEmail.get(email));
            }
            if (childDto.getId() != null) {
                childDto.setReplyCount(replyCounts.getOrDefault(childDto.getId(), 0L).intValue());
            } else {
                childDto.setReplyCount(0);
            }
            childDto.setTimeAgo(formatTimeAgo(childDto.getCreatedAt(), now));
            childDto.setChildren(new ArrayList<>()); // one level only
        }

        Map<UUID, List<Comment.DTO>> childrenByParent = new HashMap<>();
        for (Comment.DTO child : childDtosFlat) {
            UUID parentId = child.getParentCommentId();
            if (parentId == null) continue;
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(child);
        }

        List<Comment.DTO> dtos = new ArrayList<>();
        for (Comment c : roots) {
            if (c.getId() == null) continue;
            Comment.DTO dto = dtoMapper.toDTO(c);
            if (c.getUserEmail() != null && !c.getUserEmail().isBlank()) {
                dto.setPicture(pictureByEmail.get(c.getUserEmail()));
            }
            dto.setReplyCount(replyCounts.getOrDefault(c.getId(), 0L).intValue());
            dto.setTimeAgo(formatTimeAgo(dto.getCreatedAt(), now));
            dto.setChildren(childrenByParent.getOrDefault(c.getId(), new ArrayList<>()));
            // flatReplies stay null — full descendant thread loads on demand.
            dtos.add(dto);
        }

        return CommentsWithTotal.builder()
                .comments(dtos)
                .total(page.getTotalElements())
                .build();
    }

    /**
     * Map a direct-child row to {@link Comment.DTO}.
     * Column order: [id, user_email, user_name, message, is_deleted,
     *                 article_uuid, created_at, created_at_hk, parent_comment_id]
     */
    private static Comment.DTO mapChildRow(Object[] row) {
        if (row == null || row[0] == null) return null;
        Comment.DTO dto = new Comment.DTO();
        dto.setId((UUID) row[0]);
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

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Direct (one-hop) non-deleted child counts for the given parent ids. */
    private Map<UUID, Long> loadReplyCounts(Set<UUID> parentIds) {
        if (parentIds.isEmpty()) return Map.of();
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : repository.countDirectReplies(parentIds)) {
            UUID parentId = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            map.put(parentId, count);
        }
        return map;
    }

    /** Compute a human-readable relative time label (mirrors frontend formatCommentTime). */
    static String formatTimeAgo(Double createdAtEpochMs, Instant now) {
        if (createdAtEpochMs == null) return "";
        long diffMs = now.toEpochMilli() - createdAtEpochMs.longValue();
        long mins = diffMs / 60_000;
        if (mins < 1) return "just now";
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return java.time.Instant.ofEpochMilli(createdAtEpochMs.longValue())
                .atZone(java.time.ZoneId.of("Asia/Hong_Kong"))
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d"));
    }

    private Map<String, String> loadPicturesByEmail(Set<String> emails) {
        if (emails.isEmpty()) return Map.of();
        Map<String, String> pictureByEmail = new HashMap<>();
        for (User u : userRepository.findByUserEmailIn(emails)) {
            if (u.getUserPicture() != null && !u.getUserPicture().isBlank()) {
                pictureByEmail.put(u.getUserEmail(), u.getUserPicture());
            }
        }
        return pictureByEmail;
    }
}
