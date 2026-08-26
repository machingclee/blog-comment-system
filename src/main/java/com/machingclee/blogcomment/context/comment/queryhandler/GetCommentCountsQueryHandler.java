package com.machingclee.blogcomment.context.comment.queryhandler;

import com.machingclee.domain.util.common.query.interfaces.QueryHandler;
import com.machingclee.blogcomment.common.dto.CommentCountDTO;
import com.machingclee.blogcomment.common.jpa.repository.CommentRepository;
import com.machingclee.blogcomment.context.comment.query.GetCommentCountsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetCommentCountsQueryHandler implements QueryHandler<GetCommentCountsQuery, Map<UUID, Long>> {

    private final CommentRepository commentRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> handle(GetCommentCountsQuery query) {
        List<CommentCountDTO> rows = commentRepository.countAllComments();
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (CommentCountDTO row : rows) {
            counts.put(row.articleUuid(), row.count());
        }
        return counts;
    }
}
