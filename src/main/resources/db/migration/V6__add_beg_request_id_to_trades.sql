ALTER TABLE trades
    ADD COLUMN beg_request_id BIGINT;

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_beg_request
        FOREIGN KEY (beg_request_id)
            REFERENCES beg_requests (beg_request_id);

ALTER TABLE trades
    ADD CONSTRAINT uk_trades_beg_request_id
        UNIQUE (beg_request_id);

ALTER TABLE beg_requests
DROP CONSTRAINT IF EXISTS ck_beg_requests_status;

ALTER TABLE beg_requests
    ADD CONSTRAINT ck_beg_requests_status
        CHECK (
            status IN (
                       'PENDING',
                       'ACCEPTED',
                       'REJECTED',
                       'CANCELLED',
                       'COMPLETED'
                )
            );