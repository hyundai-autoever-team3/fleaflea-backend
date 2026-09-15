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

    // MyPage
    PASSWORD_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PASSWORD_MISMATCH",
            "현재 비밀번호가 일치하지 않습니다."
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

    // Item
    ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ITEM_NOT_FOUND",
            "존재하지 않는 상품입니다."
    ),
    ITEM_NOT_OWNER(
            HttpStatus.FORBIDDEN,
            "ITEM_NOT_OWNER",
            "상품 등록자만 변경할 수 있습니다."
    ),
    ITEM_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "ITEM_ALREADY_COMPLETED",
            "거래 완료된 상품은 수정할 수 없습니다."
    ),
    ITEM_TRADE_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "ITEM_TRADE_IN_PROGRESS",
            "거래 진행 중인 상품은 삭제할 수 없습니다."
    ),

    // Collection Item
    COLLECTION_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COLLECTION_ITEM_NOT_FOUND",
            "존재하지 않는 도감 아이템입니다."
    ),
    COLLECTION_ITEM_NOT_OWNER(
            HttpStatus.FORBIDDEN,
            "COLLECTION_ITEM_NOT_OWNER",
            "자신의 도감 아이템만 상품으로 등록할 수 있습니다."
    ),
    COLLECTION_ITEM_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "COLLECTION_ITEM_ACCESS_DENIED",
            "도감 아이템에 접근할 권한이 없습니다."
    ),

    // Market
    MARKET_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MARKET_NOT_FOUND",
            "존재하지 않는 플리마켓입니다."
    ),
    MARKET_NOT_PARTICIPANT(
            HttpStatus.FORBIDDEN,
            "MARKET_NOT_PARTICIPANT",
            "플리마켓 참여자만 상품을 등록할 수 있습니다."
    ),
    INVALID_MARKET_INVITE_CODE(
            HttpStatus.NOT_FOUND,
            "INVALID_MARKET_INVITE_CODE",
            "유효하지 않은 플리마켓 초대 코드입니다."
    ),
    ALREADY_JOINED_MARKET(
            HttpStatus.CONFLICT,
            "ALREADY_JOINED_MARKET",
            "이미 참여한 플리마켓입니다."
    ),
    MARKET_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "MARKET_ACCESS_DENIED",
            "참여 중인 플리마켓만 조회할 수 있습니다."
    ),
    MARKET_HOST_ONLY(
            HttpStatus.FORBIDDEN,
            "MARKET_HOST_ONLY",
            "플리마켓 개설자만 수행할 수 있습니다."
    ),
    MARKET_HOST_CANNOT_LEAVE(
            HttpStatus.CONFLICT,
            "MARKET_HOST_CANNOT_LEAVE",
            "플리마켓 개설자는 플리마켓을 나갈 수 없습니다."
    ),
    MARKET_MEMBERSHIP_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MARKET_MEMBERSHIP_NOT_FOUND",
            "플리마켓 참여 정보를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}