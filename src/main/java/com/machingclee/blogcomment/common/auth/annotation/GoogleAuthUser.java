package com.machingclee.blogcomment.common.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the {@code VerifiedIdentity} from a verified Google ID token into a
 * controller method parameter.
 *
 * <p>The {@code GoogleAuthHandlerInterceptor} validates the token and stores the
 * parsed identity as a request attribute; this resolver reads it back so
 * controllers never touch the {@code Authorization} header directly.
 *
 * <pre>{@code
 * &#64;PostMapping("/api/comments")
 * &#64;RequireGoogleAuth
 * public ApiResponse<Comment.DTO> createComment(
 *         &#64;RequestBody CreateCommentDTO body,
 *         &#64;RequestUser VerifiedIdentity identity) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface GoogleAuthUser {
}
