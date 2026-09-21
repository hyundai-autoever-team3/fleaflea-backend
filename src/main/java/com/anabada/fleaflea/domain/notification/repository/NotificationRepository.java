package com.anabada.fleaflea.domain.notification.repository;

import com.anabada.fleaflea.domain.notification.domain.Notification;
import com.anabada.fleaflea.domain.notification.domain.NotificationReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying
    @Query("""
            delete from Notification n
            where n.referenceType = :referenceType
              and n.referenceId in (
                  select tr.tradeRequestId from TradeRequest tr where tr.item.itemId = :itemId
              )
            """)
    void deleteAllByItemTradeRequests(
            Long itemId,
            NotificationReferenceType referenceType
    );

    Page<Notification> findAllByReceiver_MemberId(
            Long memberId,
            Pageable pageable
    );

    long countByReceiver_MemberIdAndIsReadFalse(Long memberId);

    // 전체 삭제 / 읽음 전체 삭제 -> 벌크 쿼리로 처리
    @Modifying(clearAutomatically = true) // 벌크 처리 후 영속성 컨텍스트를 비워준다
    @Query("""
            update Notification n
            set n.isRead = true
            where n.receiver.memberId = :memberId
              and n.isRead = false
            """)
    void readAllByMemberId(Long memberId);

    @Modifying(clearAutomatically = true)
    @Query("""
            delete from Notification n
            where n.receiver.memberId = :memberId
            """)
    void deleteAllByMemberId(Long memberId);
}