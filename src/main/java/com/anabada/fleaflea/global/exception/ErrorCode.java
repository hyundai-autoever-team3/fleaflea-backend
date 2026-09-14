package com.anabada.fleaflea.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            "잘못된 요청입니다."
    ),
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류입니다."
    ),

    // Member
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "존재하지 않는 사용자입니다."
    ),
    DUPLICATE_NICKNAME(
            HttpStatus.CONFLICT,
            "DUPLICATE_NICKNAME",
            "이미 존재하는 닉네임입니다."
    ),
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "DUPLICATE_EMAIL",
            "이미 존재하는 이메일입니다."
    ),
    INVALID_LOGIN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_LOGIN",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    // Refresh Token
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_TOKEN",
            "유효하지 않은 토큰입니다."
    ),

    // Image
    INVALID_IMAGE(
            HttpStatus.BAD_REQUEST,
            "INVALID_IMAGE",
            "유효하지 않은 이미지입니다."
    ),
    IMAGE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "IMAGE_TOO_LARGE",
            "허용된 이미지 크기를 초과했습니다."
    ),
    INVALID_IMAGE_CATEGORY(
            HttpStatus.BAD_REQUEST,
            "INVALID_IMAGE_CATEGORY",
            "지원하지 않는 이미지 분류입니다."
    ),
    INVALID_IMAGE_KEY(
            HttpStatus.BAD_REQUEST,
            "INVALID_IMAGE_KEY",
            "유효하지 않은 이미지 경로입니다."
    ),
    IMAGE_UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "IMAGE_UPLOAD_FAILED",
            "이미지 업로드에 실패했습니다."
    ),
    IMAGE_DELETE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "IMAGE_DELETE_FAILED",
            "이미지 삭제에 실패했습니다."
    ),

    // Market
    INVALID_MARKET_INVITE_CODE(
            HttpStatus.NOT_FOUND,
            "INVALID_MARKET_INVITE_CODE",
            "유효하지 않은 플리마켓 초대 코드입니다."
    ),
    ALREADY_JOINED_MARKET(
            HttpStatus.CONFLICT,
            "ALREADY_JOINED_MARKET",
            "이미 참여한 플리마켓입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}