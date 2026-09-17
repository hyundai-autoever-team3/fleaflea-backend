package com.anabada.fleaflea.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    @DisplayName("비즈니스 예외가 발생하면 해당 에러 코드와 상태를 반환한다")
    void businessExceptionReturnsErrorCode() {
        BusinessException exception =
                new MemberNotFoundException();

        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(exception);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("MEMBER_NOT_FOUND");
        assertThat(response.getBody().message())
                .isEqualTo("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("검증 예외가 발생하면 400과 INVALID_REQUEST를 반환한다")
    void validationExceptionReturnsInvalidRequest() {
        ConstraintViolationException exception =
                new ConstraintViolationException(Set.of());

        ResponseEntity<ErrorResponse> response =
                handler.handleConstraintViolation(exception);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message())
                .isEqualTo("잘못된 요청입니다.");
    }

    @Test
    @DisplayName("DB 무결성 예외가 발생하면 409와 공통 충돌 코드를 반환한다")
    void dataIntegrityViolationReturnsConflict() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate");

        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(response.getBody().message())
                .isEqualTo("요청한 데이터가 기존 데이터와 충돌합니다.");
    }

    @Test
    @DisplayName("처리되지 않은 예외가 발생하면 500과 INTERNAL_SERVER_ERROR를 반환한다")
    void unexpectedExceptionReturnsInternalServerError() {
        Exception exception = new RuntimeException("unexpected error");

        ResponseEntity<ErrorResponse> response =
                handler.handleException(exception);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message())
                .isEqualTo("서버 내부 오류입니다.");
    }
}
