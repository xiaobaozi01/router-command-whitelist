package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.AuthSession;
import com.example.whitelist.auth.CurrentUser;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.AdminProperties;
import com.example.whitelist.dto.ChangePasswordRequest;
import com.example.whitelist.dto.LoginRequest;
import com.example.whitelist.entity.AppUser;
import com.example.whitelist.mapper.AppUserMapper;
import com.example.whitelist.util.PasswordUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public static final String SESSION_KEY = "authenticated-user";

    private final AdminProperties adminProperties;
    private final AppUserMapper userMapper;

    public AuthService(AdminProperties adminProperties, AppUserMapper userMapper) {
        this.adminProperties = adminProperties;
        this.userMapper = userMapper;
    }

    public CurrentUser login(LoginRequest request, HttpSession session) {
        String username = request.username().trim();
        if (adminProperties.username().equals(username)) {
            if (adminProperties.password().equals(request.password())) {
                session.setAttribute(SESSION_KEY, new AuthSession(null, true));
                return configuredAdmin();
            }
            throw new BusinessException(401, "用户名或密码错误");
        }

        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
        if (user == null || !PasswordUtils.hash(request.password()).equals(user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        session.setAttribute(SESSION_KEY, new AuthSession(user.getId(), false));
        return toCurrentUser(user);
    }

    public CurrentUser resolve(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (!(value instanceof AuthSession authSession)) {
            return null;
        }
        if (authSession.configuredAdmin()) {
            return configuredAdmin();
        }
        AppUser user = userMapper.selectById(authSession.userId());
        if (user == null) {
            session.invalidate();
            return null;
        }
        return toCurrentUser(user);
    }

    public void changePassword(ChangePasswordRequest request, CurrentUser currentUser) {
        if (!currentUser.passwordChangeable()) {
            throw new BusinessException(403, "管理员密码请在后端 application.yml 中修改");
        }
        AppUser user = userMapper.selectById(currentUser.id());
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效");
        }
        if (!PasswordUtils.hash(request.currentPassword()).equals(user.getPasswordHash())) {
            throw new BusinessException(400, "原密码不正确");
        }
        user.setPasswordHash(PasswordUtils.hash(request.newPassword()));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
    }

    private CurrentUser configuredAdmin() {
        return new CurrentUser(null, adminProperties.username(), adminProperties.displayName(), AuthRole.ADMIN, false);
    }

    private CurrentUser toCurrentUser(AppUser user) {
        return new CurrentUser(
                user.getId(), user.getUsername(), user.getDisplayName(),
                AuthRole.valueOf(user.getRole()), true);
    }
}
