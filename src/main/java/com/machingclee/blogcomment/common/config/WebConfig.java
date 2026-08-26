package com.machingclee.blogcomment.common.config;

import com.machingclee.blogcomment.common.auth.interceptor.BasicAuthInterceptor;
import com.machingclee.blogcomment.common.auth.interceptor.GoogleAuthHandlerInterceptor;
import com.machingclee.blogcomment.common.auth.resolver.RequestUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final GoogleAuthHandlerInterceptor googleAuthHandlerInterceptor;
    private final BasicAuthInterceptor basicAuthInterceptor;
    private final RequestUserArgumentResolver requestUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Enforces @RequireGoogleAuth on annotated controllers; other handlers pass through.
        registry.addInterceptor(googleAuthHandlerInterceptor);

        // HTTP Basic Auth for /api/** and /docs/** — triggers browser native login dialog.
        // Order: runs AFTER googleAuthHandlerInterceptor (so /api/auth/google can stay public
        // if you ever remove @RequireGoogleAuth from it — Basic Auth is a separate concern).
        registry.addInterceptor(basicAuthInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Injects @RequestUser VerifiedIdentity — interceptor stores, resolver reads.
        resolvers.add(requestUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Vite dev runs on 3000 (3001 when 3000 is busy); prod is machingclee.com.
        // Google ID token is sent as Authorization: Bearer <jwt> (not cookies).
        // PUT/DELETE must be listed so the browser CORS preflight can succeed —
        // otherwise the request never reaches CommentController.
        registry.addMapping("/**")
                .allowedOrigins(
                        "https://www.machingclee.com",
                        "https://machingclee.com",
                        "http://localhost:3000",
                        "http://localhost:3001")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
