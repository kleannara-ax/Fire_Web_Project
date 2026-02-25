package com.company.module.user.controller;

import com.company.core.common.ApiResponse;
import com.company.module.user.dto.*;
import com.company.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * 사용자 인증/관리 API Controller
 * <p>
 * 기존 ASP.NET Pages/Login, Pages/Account 대응
 * URL: /api/auth/**, /api/admin/users/**
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/auth/login
     * 로그인 - JWT 토큰 발급
     * <p>
     * 기존 ASP.NET: POST /Login
     */
    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/auth/change-password
     * 비밀번호 변경 (로그인 사용자 본인)
     * <p>
     * 기존 ASP.NET: POST /Account?handler=ChangePassword
     */
    @PostMapping("/api/auth/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * GET /api/admin/users
     * 전체 사용자 목록 (Admin 전용)
     * <p>
     * 기존 ASP.NET: GET /Account/Users
     */
    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    /**
     * POST /api/admin/users
     * 사용자 등록 (Admin 전용)
     * <p>
     * 기존 ASP.NET: POST /Account/Users?handler=Create
     */
    @PostMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * DELETE /api/admin/users/{userId}
     * 사용자 비활성화 (Admin 전용)
     */
    @DeleteMapping("/api/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
