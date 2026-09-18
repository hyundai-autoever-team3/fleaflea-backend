ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS ck_notifications_type;

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
                     'FRIEND_ACCEPTED',
                     'POKE_RECEIVED'
                )
            );

ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS ck_notifications_reference_type;

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_reference_type
        CHECK (
            reference_type IN (
                               'ITEM_TRADE_REQUEST',
                               'COLLECTION_TRADE_REQUEST',
                               'BEG_REQUEST',
                               'FRIENDSHIP',
                               'MEMBER_POKE'
                )
            );