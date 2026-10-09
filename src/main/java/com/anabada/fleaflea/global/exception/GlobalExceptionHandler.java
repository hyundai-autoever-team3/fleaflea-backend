package com.anabada.fleaflea.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e
    ) {
        ErrorCode errorCode = e.getErrorCode();

        if (errorCode.getStatus().is5xxServerError()) {
            log.atError()
                    .addKeyValue("code", errorCode.getCode())
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .setCause(e)
                    .log("business_exception");
        } else {
            log.atDebug()
                    .addKeyValue("code", errorCode.getCode())
                    .addKeyValue("exceptionType", e.getClass().getSimpleName())
                    .log("business_exception");
        }

        return toResponse(errorCode);
    }

    // @RequestBody 객체를 @Valid 또는 @Validated로 검증할 때 발생하는 오류
    // @Validated가 적용된 서비스 메서드 등의 제약 조건 검증 오류
    // 컨트롤러의 @RequestParam, @PathVariable 또는 반환값 제약 조건 검증 오류
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationFailure(
            Exception e
    ) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @ModelAttribute 객체의 바인딩 또는 @Valid 검증에서 발생하는 오류
    // @RequestParam 또는 @PathVariable 값을 대상 타입으로 변환하지 못한 오류
    @ExceptionHandler({
            BindException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBindingFailure(
            Exception e
    ) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @RequestBody의 잘못된 JSON이나 지원하지 않는 enum 값으로 인한 파싱 오류
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException e
    ) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // 지원하지 않는 HTTP Method 요청
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e
    ) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // DB 유니크, 외래키, NOT NULL 등의 무결성 제약 조건 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        ErrorCode errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION;

        log.atWarn()
                .addKeyValue("code", errorCode.getCode())
                .addKeyValue("exceptionType", e.getClass().getSimpleName())
                .addKeyValue(
                        "causeType",
                        e.getMostSpecificCause()
                                .getClass()
                                .getSimpleName()
                )
                .log("data_integrity_violation");

        return toResponse(errorCode);
    }

    // multipart 요청이 애플리케이션의 파일 업로드 제한을 초과한 경우
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e
    ) {
        ErrorCode errorCode = ErrorCode.IMAGE_TOO_LARGE;

        log.atDebug()
                .addKeyValue("code", errorCode.getCode())
                .addKeyValue("exceptionType", e.getClass().getSimpleName())
                .log("upload_size_exceeded");

        return toResponse(errorCode);
    }

    // 처리되지 않은 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e
    ) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        log.atError()
                .addKeyValue("code", errorCode.getCode())
                .addKeyValue("exceptionType", e.getClass().getSimpleName())
                .setCause(e)
                .log("unhandled_exception");

        return toResponse(errorCode);
    }

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode
    ) {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {

            attributes.getRequest()
                    .setAttribute(
                            "fleaflea.code",
                            errorCode.getCode()
                    );
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(
                        ErrorResponse.of(
                                errorCode.getCode(),
                                errorCode.getMessage()
                        )
                );
    }
}
