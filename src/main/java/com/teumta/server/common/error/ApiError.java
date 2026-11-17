package com.teumta.server.common.error;

import java.util.List;

// 모든 오류 응답의 모양. 어떤 오류든 같은 모양이라 Android는 한 가지 방법으로 읽는다
public record ApiError(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.name(), errorCode.message(), List.of());
    }
}
