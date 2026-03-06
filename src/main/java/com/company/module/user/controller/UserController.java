package com.company.module.user.controller;

import com.company.core.common.ApiResponse;
import com.company.module.user.dto.*;
import com.company.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentUser(principal.getName())));
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @PostMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/api/admin/users/{userId}/display-name")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateDisplayName(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateDisplayNameRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateDisplayName(userId, request.getDisplayName())
        ));
    }

    @PostMapping("/api/admin/users/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(userId, request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/api/admin/users/{userId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> setActive(
            @PathVariable Long userId,
            @RequestBody UpdateUserActiveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.setUserActive(userId, request.isActive())
        ));
    }

    @DeleteMapping("/api/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable Long userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
