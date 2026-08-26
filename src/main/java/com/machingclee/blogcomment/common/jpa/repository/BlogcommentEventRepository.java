package com.machingclee.blogcomment.common.jpa.repository;

import com.machingclee.domain.util.common.interfaces.AuditEventRepository;
import com.machingclee.blogcomment.common.jpa.entity.BlogcommentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for {@link BlogcommentEvent}.
 * {@link AuditEventRepository} already declares {@code findAllByRequestId}.
 */
public interface BlogcommentEventRepository extends AuditEventRepository<BlogcommentEvent> {

    List<BlogcommentEvent> findAllByRequestIdAndEventType(String requestId, String eventType);

    @Query("""
                select e from BlogcommentEvent e
                where (:requestId IS NULL OR e.requestId = :requestId)
                  and (:success IS NULL OR e.success = :success)
                order by e.createdAt desc, e.eventOrder desc
            """)
    Page<BlogcommentEvent> findByPageAndLimit(
            @Param("requestId") String requestId,
            @Param("success") Boolean success,
            Pageable pageable);
}
