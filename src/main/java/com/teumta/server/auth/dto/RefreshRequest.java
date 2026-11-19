package com.teumta.server.auth.dto;

import jakarta.validation.constraints.NotBlank;

// 토큰 갱신과 로그아웃에 쓴다
public record RefreshRequest(@NotBlank(message = "refresh token이 없어요.") String refreshToken) {
}
