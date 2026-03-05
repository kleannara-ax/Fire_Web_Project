package com.company.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 사용자 등록 요청 DTO (Admin 전용)
 */
@Getter
@Setter
public class UserCreateRequest {

    @NotBlank(message = "아이디를 입력하세요.")
    @Size(max = 100, message = "아이디는 100자 이하여야 합니다.")
    private String username;

    @Size(max = 200, message = "표시 이름은 200자 이하여야 합니다.")
    private String displayName;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String password;

    /** ADMIN / USER */
    private String role = "USER";
}
