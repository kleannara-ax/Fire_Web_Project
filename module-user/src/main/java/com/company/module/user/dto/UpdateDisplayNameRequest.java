package com.company.module.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDisplayNameRequest {

    @Size(max = 200, message = "표시 이름은 200자 이하로 입력하세요.")
    private String displayName;
}

