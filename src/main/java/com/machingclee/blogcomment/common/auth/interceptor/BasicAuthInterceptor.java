package com.machingclee.blogcomment.common.auth.interceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.machingclee.blogcomment.common.config.WebConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple HTTP Basic Authentication interceptor — no Spring Security needed.
 * <p>
 * When a request to a protected path lacks a valid {@code Authorization: Basic
 * <credentials>} header, the interceptor sends a {@code 401 Unauthorized} with
 * {@code WWW-Authenticate: Basic realm="..."}.  Browsers interpret this as a
 * cue to show their native login dialog.
 * <p>
 * Credentials are configured in {@code application.yml} under
 * {@code app.basic-auth.username} / {@code app.basic-auth.password}.
 * Registered in {@link WebConfig}
 * for {@code /api/**} and {@code /docs/**}.
 */
@Component
public class BasicAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthInterceptor.class);
    private static final String BASIC_PREFIX = "Basic ";

    private final String expectedCredentials;

    public BasicAuthInterceptor(
            @Value("${app.basic-auth.username}") String username,
            @Value("${app.basic-auth.password}") String password) {
        this.expectedCredentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Let CORS preflight requests through — browsers don't send auth headers on OPTIONS.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null
                || !authHeader.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())
                || !authHeader.substring(BASIC_PREFIX.length()).trim().equals(expectedCredentials)) {

            log.debug("Basic auth failed for {} — sending 401 + WWW-Authenticate challenge",
                    request.getRequestURI());

            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Basic realm=\"blog-comment-system\"");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"success":false,"errorMessage":"authentication required"}""");
            return false;
        }

        // Valid credentials — let the request through to the controller.
        return true;
    }
}
