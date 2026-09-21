ALTER TABLE beg_requests
    ADD COLUMN owner_id BIGINT;

-- 기존 데이터 보정
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

-- owner_id가 반드시 존재하도록 설정
ALTER TABLE beg_requests
    ALTER COLUMN owner_id SET NOT NULL;

-- owner가 존재하는 회원을 참조하도록 FK 추가
ALTER TABLE beg_requests
    ADD CONSTRAINT fk_beg_requests_owner
        FOREIGN KEY (owner_id)
            REFERENCES members (member_id);

-- owner 기준 조회 최적화
CREATE INDEX idx_beg_requests_owner_created_at
    ON beg_requests (owner_id, created_at DESC);