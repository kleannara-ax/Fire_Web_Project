package com.company.module.user.service;

import com.company.core.exception.BusinessException;
import com.company.core.exception.ResourceNotFoundException;
import com.company.core.security.JwtTokenProvider;
import com.company.module.user.dto.*;
import com.company.module.user.entity.WebUser;
import com.company.module.user.repository.WebUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final WebUserRepository webUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse login(String username, String password) {
        String loginUsername = username == null ? "" : username.trim();
        String rawPassword = password == null ? "" : password;

        WebUser user = webUserRepository.findByUsernameAndActiveTrue(loginUsername)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found or inactive: {}", loginUsername);
                    return new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.");
                });

        if (!matchesAndUpgradePassword(user, rawPassword)) {
            log.warn("Login failed - wrong password: {}", loginUsername);
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        log.info("Login success: {}", loginUsername);

        return new LoginResponse(
                token,
                user.getUsername(),
                user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                user.getRole()
        );
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BusinessException("새 비밀번호가 일치하지 않습니다.");
        }

        if (!isPasswordStrongEnough(req.getNewPassword())) {
            throw new BusinessException("비밀번호는 최소 8자이며 영문/숫자/특수문자 조합을 권장합니다.");
        }

        WebUser user = webUserRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", null));

        if (!matchesAndUpgradePassword(user, req.getCurrentPassword())) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        }

        user.changePassword(passwordEncoder.encode(req.getNewPassword()));
        log.info("Password changed: {}", username);
    }

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

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return webUserRepository.findAll().stream()
                .map(UserResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        WebUser user = webUserRepository.findByUsernameAndActiveTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", null));
        return new UserResponse(user);
    }

    @Transactional
    public UserResponse updateDisplayName(Long userId, String displayName) {
        WebUser user = webUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
        String normalized = (displayName == null || displayName.trim().isEmpty()) ? null : displayName.trim();
        user.updateDisplayName(normalized);
        return new UserResponse(user);
    }

    @Transactional
    public void adminResetPassword(Long userId, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException("새 비밀번호가 일치하지 않습니다.");
        }
        if (!isPasswordStrongEnough(newPassword)) {
            throw new BusinessException("비밀번호는 최소 8자이며 영문/숫자/특수문자 조합을 권장합니다.");
        }
        WebUser user = webUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
        user.changePassword(passwordEncoder.encode(newPassword));
        user.clearLegacyPassword();
    }

    @Transactional
    public UserResponse setUserActive(Long userId, boolean active) {
        WebUser user = webUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
        if (active) {
            user.activate();
        } else {
            user.deactivate();
        }
        return new UserResponse(user);
    }

    @Transactional
    public void deactivateUser(Long userId) {
        WebUser user = webUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자", userId));
        user.deactivate();
        log.info("User deactivated: {} (id={})", user.getUsername(), userId);
    }

    private boolean isPasswordStrongEnough(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        return hasLetter && hasDigit && hasSpecial;
    }

    private boolean matchesAndUpgradePassword(WebUser user, String rawPassword) {
        String currentHash = user.getPasswordHash() == null ? null : user.getPasswordHash().trim();
        if (currentHash != null && !currentHash.isBlank()) {
            if (verifyBcrypt(rawPassword, currentHash)) {
                if (!currentHash.equals(user.getPasswordHash())) {
                    user.changePassword(currentHash);
                }
                return true;
            }
        }

        if (!verifyLegacyPassword(user, rawPassword)) {
            return false;
        }

        user.changePassword(passwordEncoder.encode(rawPassword));
        user.clearLegacyPassword();
        log.info("Legacy password upgraded to BCrypt: {}", user.getUsername());
        return true;
    }

    private boolean verifyBcrypt(String rawPassword, String hash) {
        try {
            if (passwordEncoder.matches(rawPassword, hash)) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }

        try {
            return BCrypt.checkpw(rawPassword, normalizeBcryptPrefix(hash));
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeBcryptPrefix(String hash) {
        if (hash == null || hash.length() < 4) return hash;
        if (hash.startsWith("$2y$") || hash.startsWith("$2x$") || hash.startsWith("$2b$")) {
            return "$2a$" + hash.substring(4);
        }
        return hash;
    }

    private boolean verifyLegacyPassword(WebUser user, String rawPassword) {
        if (user.getLegacyPasswordHashB64() == null || user.getLegacyPasswordSaltB64() == null || user.getLegacyIterations() == null) {
            return false;
        }

        try {
            byte[] expectedHash = Base64.getDecoder().decode(user.getLegacyPasswordHashB64());
            byte[] salt = Base64.getDecoder().decode(user.getLegacyPasswordSaltB64());

            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, user.getLegacyIterations(), expectedHash.length * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] computedHash = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expectedHash, computedHash);
        } catch (Exception e) {
            log.warn("Legacy password verification failed for user={}", user.getUsername(), e);
            return false;
        }
    }
}
