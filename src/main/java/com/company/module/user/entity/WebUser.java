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
 *
 * NOTE: boolean 필드명을 'active'로 선언하여 Lombok @Getter 가 getActive() 대신
 *       isActive() 를 생성하는 충돌을 방지한다.
 *       JPA Column 매핑은 @Column(name="is_active")으로 DB 컬럼명을 명시한다.
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

    @Column(name = "legacy_password_hash_b64", length = 512)
    private String legacyPasswordHashB64;

    @Column(name = "legacy_password_salt_b64", length = 512)
    private String legacyPasswordSaltB64;

    @Column(name = "legacy_iterations")
    private Integer legacyIterations;

    /**
     * 역할 (ADMIN / USER)
     * - Spring Security에서 ROLE_ prefix 자동 처리
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    /**
     * 계정 활성 여부
     * NOTE: 필드명을 'active'로 선언 → Lombok이 isActive()가 아닌 isActive()를 생성하는
     *       이중 충돌 방지. DB 컬럼은 @Column(name="is_active") 으로 명시 매핑.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

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
        this.active = isActive;
    }

    // ===== 편의 메서드 (기존 코드 호환) =====

    /**
     * isActive() 호환 메서드 - 기존 코드에서 isActive() 를 호출하는 경우 대응
     */
    public boolean isActive() {
        return this.active;
    }

    // ===== 비즈니스 메서드 =====

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void clearLegacyPassword() {
        this.legacyPasswordHashB64 = null;
        this.legacyPasswordSaltB64 = null;
        this.legacyIterations = null;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changeRole(String role) {
        this.role = role;
    }
}
