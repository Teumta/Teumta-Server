package com.teumta.server.common.error;

import org.springframework.http.HttpStatus;

// 서버가 돌려주는 오류 종류. Android는 message 문장이 아니라 code로 오류를 구분한다
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "이미 가입한 이메일이에요."),
    // 이메일이 없는지, 비밀번호가 틀렸는지 알려 주지 않는다. 남의 가입 여부를 떠보지 못하게 한다
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    // 앱은 이 코드를 받으면 다시 로그인 화면으로 보낸다
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
