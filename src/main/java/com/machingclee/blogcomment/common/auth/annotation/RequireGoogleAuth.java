package com.machingclee.blogcomment.common.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or handler method as requiring a valid Google ID
 * token in the `Authorization: Bearer <jwt>` header.
 * <p>
 * Validation happens in {@code GoogleAuthHandlerInterceptor} (registered in
 * {@code WebConfig}): missing, malformed, expired, or forged tokens are
 * rejected with 401 via {@code UnauthorizedException}.
 * <p>
 * Endpoints that must stay public (e.g. POST /api/auth/google, the token
 * entry point) must NOT carry this annotation.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireGoogleAuth {
}
