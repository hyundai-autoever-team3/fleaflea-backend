package com.anabada.fleaflea.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    @DisplayName("에러 코드는 대문자 스네이크 케이스이며 중복되지 않는다")
    void getCodeReturnsUniqueUpperSnakeCase() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode))
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"))
                .doesNotHaveDuplicates();
    }
}