ALTER TABLE collection_trade_requests
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN requester_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN owner_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE collection_trade_requests
    ADD CONSTRAINT ck_collection_trade_requests_status
        CHECK (
            status IN (
                       'PENDING',
                       'ACCEPTED',
                       'REJECTED',
                       'CANCELLED',
                       'COMPLETED'
                )
            );