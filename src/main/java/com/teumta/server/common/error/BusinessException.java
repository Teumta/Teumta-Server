package com.teumta.server.common.error;

// 규칙에 맞지 않는 요청을 알리는 예외.
// 서비스는 이 예외만 던지고, 응답으로 바꾸는 일은 GlobalExceptionHandler가 맡는다
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
