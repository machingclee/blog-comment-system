package com.machingclee.blogcomment.controller;

import com.machingclee.domain.util.common.interfaces.CommandInvoker;
import com.machingclee.domain.util.common.query.interfaces.QueryInvoker;
import com.machingclee.blogcomment.common.aop.logging.LogQuery;
import com.machingclee.blogcomment.common.aop.logging.LogRequest;
import com.machingclee.blogcomment.common.auth.VerifiedIdentity;
import com.machingclee.blogcomment.common.auth.annotation.RequireGoogleAuth;
import com.machingclee.blogcomment.common.auth.annotation.GoogleAuthUser;
import com.machingclee.blogcomment.common.dto.ApiResponse;
import com.machingclee.blogcomment.common.dto.CommentsWithTotal;
import com.machingclee.blogcomment.common.dto.request.CreateCommentDTO;
import com.machingclee.blogcomment.common.dto.request.UpdateCommentDTO;
import com.machingclee.blogcomment.common.exception.BadRequestException;
import com.machingclee.blogcomment.common.jpa.DTOMapper;
import com.machingclee.blogcomment.common.jpa.entity.Comment;
import com.machingclee.blogcomment.common.jpa.entity.User;
import com.machingclee.blogcomment.common.jpa.repository.UserRepository;
import com.machingclee.blogcomment.common.ratelimit.RateLimitService;
import com.machingclee.blogcomment.context.comment.command.CreateCommentCommand;
import com.machingclee.blogcomment.context.comment.command.DeleteCommentCommand;
import com.machingclee.blogcomment.context.comment.command.UpdateCommentCommand;
import com.machingclee.blogcomment.context.comment.query.GetCommentCountsQuery;
import com.machingclee.blogcomment.context.comment.query.GetCommentRepliesQuery;
import com.machingclee.blogcomment.context.comment.query.GetCommentsQuery;
import com.machingclee.blogcomment.context.user.command.CreateUserCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@Tag(name = "Comments", description = "Read, create, update, and delete blog comments")
@RestController
@LogRequest
@LogQuery
@RequiredArgsConstructor
@RequestMapping("/comments")
public class CommentController {

    private final CommandInvoker commandInvoker;
    private final QueryInvoker queryInvoker;
    private final UserRepository userRepository;
    private final DTOMapper dTOMapper;
    private final RateLimitService rateLimitService;

    @GetMapping("/ping")
    public ApiResponse<Object> ping() {
        return ApiResponse.success(Map.of("message", "pong"));
    }

    @Operation(summary = "Get paged root comments for an article", description = "Returns a page of top-level comments (no parent). Replies are loaded separately via the replies endpoint. Public endpoint.")
    @GetMapping("")
    public ApiResponse<CommentsWithTotal> getComments(
            @Parameter(description = "UUID of the article") @RequestParam(required = false) UUID articleUuid,
            @Parameter(description = "0-indexed page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int limit)
            throws Exception {
        if (articleUuid == null) {
            throw new BadRequestException("articleUuid is required");
        }
        var result = queryInvoker.invoke(GetCommentsQuery.builder()
                .articleUuid(articleUuid)
                .page(page)
                .limit(limit)
                .build());
        return ApiResponse.success(ApiResponse.SuccessParam.<CommentsWithTotal>builder()
                .payload(result)
                .build());
    }

    @Operation(summary = "Get paged descendant replies for a root comment", description = "Uses a recursive CTE to find all nested descendants and returns them as a paged flat list. Public endpoint.")
    @GetMapping("/{commentId}/replies")
    public ApiResponse<CommentsWithTotal> getCommentReplies(
            @Parameter(description = "UUID of the root comment") @PathVariable("commentId") UUID commentId,
            @Parameter(description = "0-indexed page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int limit)
            throws Exception {
        var result = queryInvoker.invoke(GetCommentRepliesQuery.builder()
                .commentId(commentId)
                .page(page)
                .limit(limit)
                .build());
        return ApiResponse.success(ApiResponse.SuccessParam.<CommentsWithTotal>builder()
                .payload(result)
                .build());
    }

    @Operation(summary = "Get comment counts per article", description = "Returns a map of article_uuid → comment count (non-deleted only) for all articles.")
    @GetMapping("/counts")
    public ApiResponse<Map<UUID, Long>> getCommentCounts() throws Exception {
        Map<UUID, Long> counts = queryInvoker.invoke(GetCommentCountsQuery.builder().build());
        return ApiResponse.success(ApiResponse.SuccessParam.<Map<UUID, Long>>builder()
                .payload(counts)
                .build());
    }

    @Operation(summary = "Create a comment or reply", description = "Requires Google ID token in Authorization header.")
    @PostMapping("")
    @RequireGoogleAuth
    public ApiResponse<Comment.DTO> createComment(
            @RequestBody CreateCommentDTO body,
            @GoogleAuthUser VerifiedIdentity identity) throws Exception {

        String name = identity.name() != null && !identity.name().isBlank() ? identity.name() : body.name();
        String email = identity.email() != null && !identity.email().isBlank() ? identity.email() : body.email();
        String picture = identity.picture() != null && !identity.picture().isBlank() ? identity.picture() : null;
        User.DTO userDTO = getUserDTO(email, picture, name);
        // After user exists (FK on user_rate_limit.user_email → comment_user).
        rateLimitService.checkAndConsume(userDTO.getUserEmail());

        var cmd = CreateCommentCommand.builder()
                .articleUuid(body.articleUuid())
                .articleTitle(body.articleTitle())
                .articleTitleTc(body.articleTitleTc())
                .articleUrl(body.articleUrl())
                .name(userDTO.getUserName())
                .email(userDTO.getUserEmail())
                .message(body.message())
                .parentId(body.parentId())
                .picture(userDTO.getUserPicture() != null ? userDTO.getUserPicture() : picture)
                .build();
        Comment.DTO dto = commandInvoker.invoke(cmd);
        return ApiResponse.success(ApiResponse.SuccessParam.<Comment.DTO>builder()
                .payload(dto)
                .build());
    }

    @Operation(summary = "Update a comment", description = "Requires Google ID token. Only the comment author can edit.")
    @PutMapping("/{commentId}")
    @RequireGoogleAuth
    public ApiResponse<Comment.DTO> updateComment(
            @Parameter(description = "UUID of the comment") @PathVariable("commentId") UUID commentId,
            @RequestBody UpdateCommentDTO body,
            @GoogleAuthUser VerifiedIdentity identity) throws Exception {

        rateLimitService.checkAndConsume(identity.email());

        var cmd = UpdateCommentCommand.builder()
                .commentId(commentId)
                .message(body.message())
                .requestUserEmailFromToken(identity.email())
                .build();
        Comment.DTO dto = commandInvoker.invoke(cmd);
        return ApiResponse.success(ApiResponse.SuccessParam.<Comment.DTO>builder()
                .payload(dto)
                .build());
    }

    @Operation(summary = "Delete a comment (soft-delete)", description = "Requires Google ID token. Only the comment author can delete.")
    @DeleteMapping("/{commentId}")
    @RequireGoogleAuth
    public ApiResponse<Comment.DTO> deleteComment(
            @Parameter(description = "UUID of the comment") @PathVariable("commentId") UUID commentId,
            @GoogleAuthUser VerifiedIdentity identity) throws Exception {

        rateLimitService.checkAndConsume(identity.email());

        var cmd = DeleteCommentCommand.builder()
                .commentId(commentId)
                .requestUserEmailFromToken(identity.email())
                .build();
        Comment.DTO dto = commandInvoker.invoke(cmd);
        return ApiResponse.success(ApiResponse.SuccessParam.<Comment.DTO>builder()
                .payload(dto)
                .build());
    }

    private User.DTO getUserDTO(String email, String picture, String name) throws Exception {
        User.DTO userDTO = null;
        var user = userRepository.findByUserEmail(email).orElse(null);

        if (user != null) {
            // Keep avatar fresh when Google returns an updated picture URL.
            if (picture != null && !picture.equals(user.getUserPicture())) {
                user.setUserPicture(picture);
                userRepository.save(user);
            }
            userDTO = dTOMapper.toDTO(user);
        } else {
            var cmd = CreateUserCommand.builder()
                    .userEmail(email)
                    .userName(name)
                    .userPicture(picture)
                    .build();
            userDTO = commandInvoker.invoke(cmd);
        }
        return userDTO;
    }
}
