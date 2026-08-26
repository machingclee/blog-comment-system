package com.machingclee.blogcomment.common.auth.resolver;

import com.machingclee.blogcomment.common.auth.VerifiedIdentity;
import com.machingclee.blogcomment.common.auth.annotation.GoogleAuthUser;
import com.machingclee.blogcomment.common.auth.interceptor.GoogleAuthHandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects the {@link VerifiedIdentity} stored by
 * {@link GoogleAuthHandlerInterceptor} into parameters annotated with
 * {@link GoogleAuthUser}.
 *
 * <p>Follows the user.authentication skill's
 * {@code RequestUserArgumentResolver} pattern — the interceptor validates
 * the token and sets a request attribute; this resolver reads it back so
 * controllers never touch the {@code Authorization} header.
 */
@Component
public class RequestUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(GoogleAuthUser.class)
                && VerifiedIdentity.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }
        return request.getAttribute(GoogleAuthHandlerInterceptor.GOOGLE_AUTH_IDENTITY_ATTR);
    }
}
