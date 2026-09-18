package com.anabada.fleaflea.domain.notification.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificationMessageFactory {

    public static String tradeRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname + "님이 "
                + targetName
                + "에 거래를 요청했습니다.";
    }

    public static String tradeAccepted(
            String targetName
    ) {
        return targetName
                + " 거래 요청이 수락되었습니다.";
    }

    public static String tradeRejected(
            String targetName
    ) {
        return targetName
                + " 거래 요청이 거절되었습니다.";
    }

    public static String tradeCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname + "님이 "
                + targetName
                + " 거래 요청을 취소했습니다.";
    }

    public static String tradeCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname + "님이 "
                + targetName
                + " 거래 완료를 확인했습니다.";
    }

    public static String begRequested(
            String applicantNickname,
            String targetName
    ) {
        return applicantNickname + "님이 "
                + targetName
                + " 물건을 구걸 요청했습니다.";
    }

    public static String begAccepted(
            String targetName
    ) {
        return targetName
                + " 구걸 요청이 수락되었습니다.";
    }

    public static String begRejected(
            String targetName
    ) {
        return targetName
                + " 구걸 요청이 거절되었습니다.";
    }

    public static String begCancelled(
            String applicantNickname,
            String targetName
    ) {
        return applicantNickname + "님이 "
                + targetName
                + " 구걸 요청을 취소했습니다.";
    }

    public static String friendRequested(
            String requesterNickname
    ) {
        return requesterNickname
                + "님이 친구 요청을 보냈습니다.";
    }

    public static String friendAccepted(
            String accepterNickname
    ) {
        return accepterNickname
                + "님이 친구 요청을 수락했습니다.";
    }

    public static String pokeReceived(
            String senderNickname
    ) {
        return senderNickname
                + "님이 나를 콕 찔렀습니다.";
    }
}