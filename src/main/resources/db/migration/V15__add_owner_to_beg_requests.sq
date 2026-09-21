ALTER TABLE beg_requests
    ADD COLUMN owner_id BIGINT;

UPDATE beg_requests request
SET owner_id = COALESCE(
        (
            SELECT trade.seller_id
            FROM trades trade
            WHERE trade.beg_request_id = request.beg_request_id
        ),
        item.member_id
    )
FROM collection_items item
WHERE item.collection_item_id = request.collection_item_id;

ALTER TABLE beg_requests
    ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX idx_beg_requests_owner_created_at
    ON beg_requests (owner_id, created_at DESC);