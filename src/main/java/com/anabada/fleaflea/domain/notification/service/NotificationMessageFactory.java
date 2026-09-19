package com.anabada.fleaflea.domain.notification.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NotificationMessageFactory {

    public static String saleRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 구매를 요청했어요.";
    }

    public static String giveawayRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 나눔을 신청했어요.";
    }

    public static String itemRentalRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 대여를 요청했어요.";
    }

    public static String collectionRentalRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 도감 대여를 요청했어요.";
    }

    public static String exchangeRequested(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 교환을 제안했어요.";
    }

    public static String begRequested(
            String applicantNickname,
            String targetName
    ) {
        return applicantNickname
                + "님이 '"
                + targetName
                + "' 구걸을 요청했어요.";
    }

    public static String saleAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 구매 요청이 수락됐어요.";
    }

    public static String giveawayAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 나눔 신청이 수락됐어요.";
    }

    public static String itemRentalAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 대여 요청이 수락됐어요.";
    }

    public static String collectionRentalAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 도감 대여 요청이 수락됐어요.";
    }

    public static String exchangeAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 교환 제안이 수락됐어요.";
    }

    public static String begAccepted(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 구걸 요청이 수락됐어요.";
    }

    public static String saleRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 구매 요청이 거절됐어요.";
    }

    public static String giveawayRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 나눔 신청이 거절됐어요.";
    }

    public static String itemRentalRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 대여 요청이 거절됐어요.";
    }

    public static String collectionRentalRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 도감 대여 요청이 거절됐어요.";
    }

    public static String exchangeRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 교환 제안이 거절됐어요.";
    }

    public static String begRejected(
            String targetName
    ) {
        return "'"
                + targetName
                + "' 구걸 요청이 거절됐어요.";
    }

    public static String saleCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 구매 요청을 취소했어요.";
    }

    public static String giveawayCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 나눔 신청을 취소했어요.";
    }

    public static String itemRentalCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 대여 요청을 취소했어요.";
    }

    public static String collectionRentalCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 도감 대여 요청을 취소했어요.";
    }

    public static String exchangeCancelled(
            String requesterNickname,
            String targetName
    ) {
        return requesterNickname
                + "님이 '"
                + targetName
                + "' 교환 제안을 취소했어요.";
    }

    public static String begCancelled(
            String applicantNickname,
            String targetName
    ) {
        return applicantNickname
                + "님이 '"
                + targetName
                + "' 구걸 요청을 취소했어요.";
    }

    public static String saleCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname
                + "님과의 '"
                + targetName
                + "' 거래가 완료됐어요.";
    }

    public static String giveawayCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname
                + "님과의 '"
                + targetName
                + "' 나눔이 완료됐어요.";
    }

    public static String itemRentalCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname
                + "님과의 '"
                + targetName
                + "' 대여가 완료됐어요.";
    }

    public static String collectionRentalCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname
                + "님과의 '"
                + targetName
                + "' 도감 대여가 완료됐어요.";
    }

    public static String exchangeCompleted(
            String confirmerNickname,
            String targetName
    ) {
        return confirmerNickname
                + "님과의 '"
                + targetName
                + "' 교환이 완료됐어요.";
    }

    public static String begCompleted(
            String applicantNickname,
            String targetName
    ) {
        return applicantNickname
                + "님이 '"
                + targetName
                + "' 수령을 완료했어요.";
    }

    public static String friendRequested(
            String requesterNickname
    ) {
        return requesterNickname
                + "님이 친구 요청을 보냈어요.";
    }

    public static String friendAccepted(
            String accepterNickname
    ) {
        return accepterNickname
                + "님이 친구 요청을 수락했어요.";
    }

    public static String pokeReceived(
            String senderNickname
    ) {
        return senderNickname
                + "님이 나를 콕 찔렀어요.";
    }
}