package com.company.core.security;

import com.company.core.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 인증되지 않은 요청에 대한 401 응답 처리
 * <p>
 * 기존 ASP.NET의 LoginPath 리다이렉트 대신,
 * REST API 방식의 JSON 오류 응답을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized request: {} {}", request.getMethod(), request.getRequestURI());

        String uri = Optional.ofNullable(request.getRequestURI()).orElse("/");
        String query = request.getQueryString();
        String fullUrl = uri + (query == null || query.isBlank() ? "" : "?" + query);

        String accept = Optional.ofNullable(request.getHeader("Accept")).orElse("");
        String fetchMode = Optional.ofNullable(request.getHeader("Sec-Fetch-Mode")).orElse("");
        String fetchDest = Optional.ofNullable(request.getHeader("Sec-Fetch-Dest")).orElse("");

        boolean browserNavigation =
                accept.contains("text/html")
                        || "navigate".equalsIgnoreCase(fetchMode)
                        || "document".equalsIgnoreCase(fetchDest);

        if (browserNavigation) {
            String returnUrl = URLEncoder.encode(fullUrl, StandardCharsets.UTF_8);
            response.sendRedirect("/login.html?returnUrl=" + returnUrl);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.fail("인증이 필요합니다. 로그인 후 이용하세요.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
