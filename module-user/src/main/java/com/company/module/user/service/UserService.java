package com.company.module.user.service;

import com.company.core.exception.BusinessException;
import com.company.core.exception.ResourceNotFoundException;
import com.company.core.security.JwtTokenProvider;
import com.company.module.user.dto.*;
import com.company.module.user.entity.WebUser;
import com.company.module.user.repository.WebUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 사용자 인증 및 관리 서비스
 * <p>
 * - @Transactional은 Service 계층에서만 사용 (Controller/Repository 적용 금지)
 * - 기존 ASP.NET의 PasswordHasher(PBKDF2) → BCrypt로 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final WebUserRepository webUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 로그인 처리 - JWT 토큰 발급
     * <p>
     * 기존 ASP.NET LoginModel.OnPostAsync() 대응
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        WebUser user = webUserRepository.findByUsernameAndIsActiveTrue(username)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found or inactive: {}", username);
                    return new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.");
                });

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Login failed - wrong password: {}", username);
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        log.info("Login success: {}", username);

        return new LoginResponse(
                token,
                user.getUsername(),
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                user.getRole()
        );
    }

    /**
     * 비밀번호 변경
     * <p>
     * 기존 ASP.NET Account/Index.OnPostChangePasswordAsync() 대응
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("새 비밀번호가 일치하지 않습니다.");
        }

        if (!isPasswordStrongEnough(req.getNewPassword())) {
            throw new BusinessException("비밀번호는 최소 8자이며 영문/숫자/특수문자 조합을 권장합니다.");
        }

        WebUser user = webUserRepository.findByUsernameAndIsActiveTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", null));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(req.getNewPassword()));
        log.info("Password changed: {}", username);
    }

    /**
     * 사용자 등록 (Admin 전용)
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest req) {
        if (webUserRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("이미 사용 중인 아이디입니다.");
        }

        if (!isPasswordStrongEnough(req.getPassword())) {
            throw new BusinessException("비밀번호는 최소 8자이며 영문/숫자/특수문자 조합을 권장합니다.");
        }

        String role = (req.getRole() != null && req.getRole().equalsIgnoreCase("ADMIN"))
                ? "ADMIN" : "USER";

        WebUser user = WebUser.builder()
                .username(req.getUsername())
                .displayName(req.getDisplayName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .isActive(true)
                .build();

        webUserRepository.save(user);
        log.info("User created: {} (role={})", user.getUsername(), user.getRole());
        return new UserResponse(user);
    }

    /**
     * 전체 사용자 목록 조회 (Admin 전용)
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return webUserRepository.findAll().stream()
                .map(UserResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * 사용자 비활성화 (Admin 전용)
     */
    @Transactional
    public void deactivateUser(Long userId) {
        WebUser user = webUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
        user.deactivate();
        log.info("User deactivated: {} (id={})", user.getUsername(), userId);
    }

    /**
     * 비밀번호 강도 검사
     * - 최소 8자, 영문 + 숫자 + 특수문자 조합 권장
     */
    private boolean isPasswordStrongEnough(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasLetter && hasDigit && hasSpecial;
    }
}
