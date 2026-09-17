ALTER TABLE trade_requests
DROP CONSTRAINT uk_trade_requests_item_requester;

CREATE UNIQUE INDEX uk_trade_requests_pending
    ON trade_requests (item_id, requester_id)
    WHERE status = 'PENDING';