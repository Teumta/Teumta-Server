package com.teumta.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 로그인은 빈칸만 막는다. 형식 규칙은 가입 때 이미 검사했다
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password
) {
}
