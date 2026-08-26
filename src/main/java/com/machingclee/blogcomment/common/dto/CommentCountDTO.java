package com.machingclee.blogcomment.common.dto;

import java.util.UUID;

/**
 * Query projection for {@code SELECT articleUuid, COUNT(*)} queries on the
 * comment table. Used by {@code CommentRepository#countComments}.
 */
public record CommentCountDTO(UUID articleUuid, Long count) {
}
