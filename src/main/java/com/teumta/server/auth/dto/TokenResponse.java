package com.teumta.server.auth.dto;

// 로그인·가입·갱신 응답. expiresIn은 access token이 몇 초 뒤 만료되는지
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
