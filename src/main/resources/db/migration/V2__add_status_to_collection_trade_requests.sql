ALTER TABLE collection_trade_requests
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE collection_trade_requests
    ALTER COLUMN status DROP DEFAULT;
