package com.company.core.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 핵심 설정
 * <p>
 * - core 모듈에서 전역으로 설정하며, 업무 모듈(module-*)은 수정 불가
 * - JWT Stateless 방식 (기존 ASP.NET Cookie 인증 대체)
 * - Role 기반 인가: ROLE_ADMIN / ROLE_USER
 * - 비밀번호: BCrypt (기존 PBKDF2-SHA256 로직은 module-user에서 마이그레이션 처리)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API이므로 CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless JWT: 세션 미사용
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인증 실패 처리 (401 JSON 응답)
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))

                // 요청 인가 규칙
                .authorizeHttpRequests(auth -> auth
                        // 인증 API (로그인): 모든 접근 허용
                        .requestMatchers("/api/auth/**").permitAll()
                        // 관리자 전용 엔드포인트
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 소화기/소화전 API: 인증된 사용자
                        .requestMatchers("/fire-api/**").authenticated()
                        // 영업 모듈 API: 인증된 사용자
                        .requestMatchers("/sales-api/**").authenticated()
                        // 나머지 모든 요청: 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 등록
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
