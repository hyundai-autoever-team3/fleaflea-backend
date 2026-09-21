ALTER TABLE collection_trade_requests
    ADD COLUMN owner_id BIGINT;

UPDATE collection_trade_requests request
SET owner_id = item.member_id
FROM collection_items item
WHERE item.collection_item_id = request.collection_item_id;

ALTER TABLE collection_trade_requests
    ALTER COLUMN owner_id SET NOT NULL,
    ADD CONSTRAINT fk_collection_trade_requests_owner
        FOREIGN KEY (owner_id) REFERENCES members (member_id);

CREATE INDEX idx_collection_trade_requests_owner_created_at
    ON collection_trade_requests (owner_id, created_at DESC);
