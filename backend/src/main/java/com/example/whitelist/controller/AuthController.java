package com.example.whitelist.controller;

import com.example.whitelist.auth.AuthContext;
import com.example.whitelist.auth.CurrentUser;
import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.dto.ChangePasswordRequest;
import com.example.whitelist.dto.LoginRequest;
import com.example.whitelist.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUser> login(
            @Valid @RequestBody LoginRequest request,
            HttpSession session
    ) {
        return ApiResponse.success(authService.login(request, session));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.success(AuthContext.get());
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request, AuthContext.get());
        return ApiResponse.success();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.success();
    }
}
