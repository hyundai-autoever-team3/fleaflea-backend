ALTER TABLE collection_trade_requests
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS requester_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS owner_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE collection_trade_requests
DROP CONSTRAINT IF EXISTS ck_collection_trade_requests_status;

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