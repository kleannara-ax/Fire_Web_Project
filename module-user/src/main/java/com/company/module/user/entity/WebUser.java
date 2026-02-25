package com.company.module.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 웹 사용자 엔티티
 * <p>
 * 기존 ASP.NET WebUser 테이블을 Java/MariaDB로 변환.
 * - 기존: PBKDF2-SHA256 (PasswordHash + PasswordSalt + Iterations)
 * - 변환: BCrypt (passwordHash 단일 필드로 통합 관리)
 * - Role: ADMIN / USER (Spring Security ROLE_ prefix는 SecurityConfig에서 처리)
 *
 * 테이블명: web_user
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "web_user")
public class WebUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    /** 로그인 아이디 (유니크) */
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /** 표시 이름 (담당자/부서명) */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** BCrypt 해시된 비밀번호 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * 역할 (ADMIN / USER)
     * - Spring Security에서 ROLE_ prefix 자동 처리
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /** 계정 활성 여부 */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public WebUser(String username, String displayName, String passwordHash,
                   String role, boolean isActive) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.isActive = isActive;
    }

    // ===== 비즈니스 메서드 =====

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void changeRole(String role) {
        this.role = role;
    }
}
