package com.teumta.server.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

// 모든 Controller에서 난 예외를 ApiError 모양의 응답으로 바꾼다
// ResponseEntityExceptionHandler를 이어받아 없는 주소(404)나 잘못된 JSON(400)처럼
// Spring이 먼저 알아채는 오류도 알맞은 상태 코드로 같은 모양에 담는다
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.status()).body(ApiError.of(errorCode));
    }

    // @Valid 검사에 걸린 칸과 이유를 모두 담아, 화면이 칸마다 안내할 수 있게 한다
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage())).toList();
        ErrorCode errorCode = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(errorCode.status())
                .body(new ApiError(errorCode.name(), errorCode.message(), fieldErrors));
    }

    // Spring이 알아챈 나머지 오류(404, 405, 잘못된 JSON 등). 상태 코드 이름을 code로 쓴다
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());
        ApiError apiError = new ApiError(httpStatus.name(), "요청을 처리할 수 없어요.", List.of());
        return ResponseEntity.status(status).headers(headers).body(apiError);
    }

    // 예상하지 못한 오류. 원인은 서버 로그에만 남기고, 응답에는 내부 내용을 넣지 않는다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("처리하지 못한 오류", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.status()).body(ApiError.of(errorCode));
    }
}
