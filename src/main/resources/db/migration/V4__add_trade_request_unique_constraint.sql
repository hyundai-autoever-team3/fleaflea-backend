ALTER TABLE trade_requests
    ADD CONSTRAINT uk_trade_requests_item_requester
        UNIQUE (item_id, requester_id);
