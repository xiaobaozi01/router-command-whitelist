package com.example.whitelist.auth;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        CurrentUser currentUser = request.getSession(false) == null
                ? null
                : authService.resolve(request.getSession(false));
        if (currentUser == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRole requirement = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequireRole.class);
            if (requirement == null) {
                requirement = AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), RequireRole.class);
            }
            if (requirement != null && Arrays.stream(requirement.value())
                    .noneMatch(role -> role == currentUser.role())) {
                throw new BusinessException(403, "当前用户没有执行此操作的权限");
            }
        }
        AuthContext.set(currentUser);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        AuthContext.clear();
    }
}
