package com.company.module.user.dto;

import com.company.module.user.entity.WebUser;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 응답 DTO (비밀번호 제외)
 */
@Getter
public class UserResponse {

    private final Long userId;
    private final String username;
    private final String displayName;
    private final String role;
    private final boolean isActive;
    private final LocalDateTime createdAt;

    public UserResponse(WebUser user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.isActive = user.isActive();  // WebUser.isActive() 호환 메서드 사용
        this.createdAt = user.getCreatedAt();
    }
}
