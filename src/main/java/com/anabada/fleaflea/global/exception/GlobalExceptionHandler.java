package com.anabada.fleaflea.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[BusinessException]: code={}, message={}", errorCode.getCode(), e.getMessage());

        return toResponse(errorCode);
    }

    // @RequestBody 객체를 @Valid 또는 @Validated로 검증할 때 발생하는 오류
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBindingFailure(MethodArgumentNotValidException e) {
        log.warn("[MethodArgumentNotValidException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @ModelAttribute 객체의 바인딩 또는 @Valid 검증에서 발생하는 오류
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        log.warn("[BindException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @Validated가 적용된 서비스 메서드 등 제약 조건 검증 오류
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("[ConstraintViolationException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // 컨트롤러의 @RequestParam, @PathVariable 또는 반환값 제약 조건 검증 오류
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("[HandlerMethodValidationException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @RequestParam 또는 @PathVariable 값을 대상 타입으로 변환하지 못한 오류
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[MethodArgumentTypeMismatchException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // @RequestBody의 잘못된 JSON이나 지원하지 않는 enum 값으로 인한 파싱 오류
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadableException]: code={}", ErrorCode.INVALID_REQUEST.getCode());
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // DB 유니크, 외래키, NOT NULL 등의 무결성 제약 조건 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        ErrorCode errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION;
        log.warn(
                "[DataIntegrityViolationException]: code={}, cause={}",
                errorCode.getCode(),
                e.getMostSpecificCause().getClass().getSimpleName()
        );

        return toResponse(errorCode);
    }

    // multipart 요청이 애플리케이션의 파일 업로드 제한을 초과한 경우
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e
    ) {
        log.warn(
                "[MaxUploadSizeExceededException]: code={}",
                ErrorCode.IMAGE_TOO_LARGE.getCode()
        );
        return toResponse(ErrorCode.IMAGE_TOO_LARGE);
    }

    // 처리되지 않은 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("[Exception]: code={}, message={}", errorCode.getCode(), e.getMessage(), e);

        return toResponse(errorCode);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage()));
    }
}
