ALTER TABLE trade_requests
    ADD COLUMN requester_confirmed BOOLEAN NOT NULL DEFAULT FALSE;