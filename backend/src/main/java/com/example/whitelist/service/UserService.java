package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.config.AdminProperties;
import com.example.whitelist.dto.ResetPasswordRequest;
import com.example.whitelist.dto.UserCreateRequest;
import com.example.whitelist.dto.UserResponse;
import com.example.whitelist.dto.UserUpdateRequest;
import com.example.whitelist.entity.AppUser;
import com.example.whitelist.mapper.AppUserMapper;
import com.example.whitelist.util.PasswordUtils;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final AppUserMapper userMapper;
    private final AdminProperties adminProperties;

    public UserService(AppUserMapper userMapper, AdminProperties adminProperties) {
        this.userMapper = userMapper;
        this.adminProperties = adminProperties;
    }

    public PageResponse<UserResponse> page(long current, long size, String keyword) {
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<AppUser>()
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(AppUser::getUsername, keyword)
                        .or().like(AppUser::getDisplayName, keyword))
                .orderByDesc(AppUser::getUpdatedAt);
        Page<AppUser> page = userMapper.selectPage(Page.of(current, size), query);
        List<UserResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(page, records);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        if (adminProperties.username().equals(username)) {
            throw new BusinessException(409, "该用户名已由 YAML 管理员使用");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username)) > 0) {
            throw new BusinessException(409, "用户名已存在，请使用其他用户名");
        }
        AuthRole role = editableRole(request.role());
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(PasswordUtils.hash(request.password()));
        user.setRole(role.name());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(user.getCreatedAt());
        userMapper.insert(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        AppUser user = requireUser(id);
        user.setDisplayName(request.displayName().trim());
        user.setRole(editableRole(request.role()).name());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = requireUser(id);
        user.setPasswordHash(PasswordUtils.hash(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id) {
        requireUser(id);
        userMapper.deleteById(id);
    }

    private AppUser requireUser(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private AuthRole editableRole(String value) {
        try {
            AuthRole role = AuthRole.valueOf(value);
            if (role == AuthRole.ADMIN) {
                throw new BusinessException(400, "管理员只能在 application.yml 中配置");
            }
            return role;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "用户角色无效");
        }
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(),
                AuthRole.valueOf(user.getRole()), user.getCreatedAt(), user.getUpdatedAt());
    }
}
