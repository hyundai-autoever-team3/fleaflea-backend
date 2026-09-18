-- reference_type 기존 제약 제거
ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

-- 기존 FRIEND_REQUEST 데이터를 FRIENDSHIP으로 변경
UPDATE notifications
SET reference_type = 'FRIENDSHIP'
WHERE reference_type = 'FRIEND_REQUEST';

-- reference_type 새 제약 생성
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type
        CHECK (
            reference_type IN (
                               'ITEM_TRADE_REQUEST',
                               'COLLECTION_TRADE_REQUEST',
                               'BEG_REQUEST',
                               'FRIENDSHIP'
                )
            );


-- notification type 기존 제약 제거
ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS ck_notifications_type;

-- 기존 TRADE_COMPLETION_CONFIRMED 데이터가 있다면
-- 현재 정책상 TRADE_COMPLETED로 통합
UPDATE notifications
SET type = 'TRADE_COMPLETED'
WHERE type = 'TRADE_COMPLETION_CONFIRMED';

-- notification type 새 제약 생성
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type
        CHECK (
            type IN (
                     'TRADE_REQUESTED',
                     'TRADE_ACCEPTED',
                     'TRADE_REJECTED',
                     'TRADE_CANCELLED',
                     'TRADE_COMPLETED',
                     'FRIEND_REQUESTED',
                     'FRIEND_ACCEPTED'
                )
            );