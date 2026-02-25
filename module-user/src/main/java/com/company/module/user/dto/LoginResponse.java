package com.company.module.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 성공 응답 DTO
 */
@Getter
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String username;
    private String displayName;
    private String role;
}
