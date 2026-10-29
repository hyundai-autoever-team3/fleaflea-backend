package com.anabada.fleaflea.domain.member.service;

import com.anabada.fleaflea.domain.chat.security.ChatWebSocketSessionDecorator;
import com.anabada.fleaflea.domain.member.exception.MemberNotFoundException;
import com.anabada.fleaflea.domain.member.exception.MemberOwnsMarketException;
import com.anabada.fleaflea.domain.member.exception.MemberWithdrawalBlockedException;
import com.anabada.fleaflea.domain.member.repository.MemberRepository;
import com.anabada.fleaflea.domain.member.repository.MemberWithdrawalRepository;
import com.anabada.fleaflea.domain.notification.sse.NotificationSseService;
import com.anabada.fleaflea.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import com.anabada.fleaflea.global.image.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {
    private final MemberRepository memberRepository;
    private final MemberWithdrawalRepository withdrawalRepository;

    private final ImageService imageService;
    private final RedisRefreshTokenRepository refreshTokenRepository;
    private final ChatWebSocketSessionDecorator chatSessions;
    private final NotificationSseService notificationSseService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void withdraw(Long memberId) {
        memberRepository.findLockedById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        withdrawalRepository.lockTargets(memberId);

        validateWithdrawal(memberId);

        List<String> imageKeys =
                withdrawalRepository.findImageKeys(memberId);

        withdrawalRepository.deleteNotifications(memberId);
        withdrawalRepository.deleteTrades(memberId);
        withdrawalRepository.deleteRequests(memberId);
        withdrawalRepository.deleteContents(memberId);
        withdrawalRepository.deleteRelations(memberId);

        memberRepository.deleteForWithdrawal(memberId);

        for (String imageKey : imageKeys) {
            if (!withdrawalRepository.isImageReferenced(imageKey)) {
                imageService.deleteAfterCommit(imageKey);
            }
        }

        registerAfterCommitCleanup(memberId);
    }

    private void validateWithdrawal(Long memberId) {
        if (withdrawalRepository.ownsMarket(memberId)) {
            throw new MemberOwnsMarketException();
        }

        if (withdrawalRepository.hasOngoingTrade(memberId)) {
            throw new MemberWithdrawalBlockedException();
        }
    }

    private void registerAfterCommitCleanup(Long memberId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanupSafely(
                                memberId,
                                "refresh-token",
                                () -> refreshTokenRepository
                                        .deleteByMemberId(memberId)
                        );
                        cleanupSafely(
                                memberId,
                                "chat-session",
                                () -> chatSessions.
                                closeMemberSessions(memberId)
                        );
                        cleanupSafely(
                                memberId,
                                "notification-sse",
                                () -> notificationSseService
                                        .closeMemberConnections(memberId)
                        );
                    }
                }
        );
    }

    private void cleanupSafely(
            Long memberId,
            String target,
            Runnable cleanup
    ) {
        try {
            cleanup.run();
        } catch (RuntimeException exception) {
            log.error(
                    "탈퇴 후 정리 실패 : memberId = {}, target = {}",
                    memberId,
                    target,
                    exception
            );
        }
    }

}
