package com.machingclee.blogcomment.common.auth.interceptor;

import com.machingclee.blogcomment.common.auth.resolver.RequestUserArgumentResolver;
import com.machingclee.domain.util.common.MdcContextKeys;
import com.machingclee.blogcomment.common.auth.GoogleTokenVerifier;
import com.machingclee.blogcomment.common.auth.VerifiedIdentity;
import com.machingclee.blogcomment.common.auth.annotation.RequireGoogleAuth;
import com.machingclee.blogcomment.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates the {@code Authorization: Bearer <google-id-token>} header for
 * handlers annotated with {@link RequireGoogleAuth} and stores the parsed
 * {@link VerifiedIdentity} as a request attribute so
 * {@link RequestUserArgumentResolver}
 * can inject it into controller parameters via {@code @RequestUser}.
 *
 * <p>Follows the user.authentication skill's
 * {@code AccessTokenHandlerInterceptor} pattern.
 */
@Component
@RequiredArgsConstructor
public class GoogleAuthHandlerInterceptor implements HandlerInterceptor {

    /**
     * Hand-off key between interceptor and {@code RequestUserArgumentResolver}.
     */
    public static final String GOOGLE_AUTH_IDENTITY_ATTR = "com.machingclee.googleAuthIdentity";

    private static final String BEARER_PREFIX = "Bearer ";

    private final GoogleTokenVerifier googleTokenVerifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean requiresAuth = handlerMethod.getMethodAnnotation(RequireGoogleAuth.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(RequireGoogleAuth.class);
        if (!requiresAuth) {
            return true;
        }

        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        VerifiedIdentity identity = googleTokenVerifier.verify(token != null ? token : "")
                .orElseThrow(() -> new UnauthorizedException("invalid google id token"));
        MDC.put(MdcContextKeys.USER_ID, identity.email());
        // Store so @RequestUser argument resolver can inject it — controllers
        // never need to re-parse the Authorization header.
        request.setAttribute(GOOGLE_AUTH_IDENTITY_ATTR, identity);
        return true;
    }

    /**
     * Extracts a Bearer token from the Authorization header value, or null.
     */
    public static String extractBearerToken(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return trimmed.substring(BEARER_PREFIX.length()).trim();
    }
}
