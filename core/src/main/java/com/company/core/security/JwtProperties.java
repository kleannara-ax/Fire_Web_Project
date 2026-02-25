package com.company.core.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 프로퍼티
 * application.yml: security.jwt.secret, security.jwt.expiration-ms
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /** JWT 서명 시크릿 키 (Base64 인코딩 권장, 최소 256bit) */
    private String secret = "fireweb-default-secret-key-change-in-production-256bit";

    /** 토큰 만료 시간 (밀리초, 기본 1시간) */
    private long expirationMs = 3_600_000L;
}
