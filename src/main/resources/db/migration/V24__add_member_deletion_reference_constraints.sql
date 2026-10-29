-- 과거 삭제 로직으로 남은 고아 참조는 보존하고 신규 참조부터 FK를 적용한다.
-- 기존 데이터의 보존·정리 후 별도 마이그레이션에서 VALIDATE CONSTRAINT를 수행한다.
ALTER TABLE friendships
    ADD CONSTRAINT fk_friendships_requester
        FOREIGN KEY (requester_id) REFERENCES members(member_id) NOT VALID,
    ADD CONSTRAINT fk_friendships_addressee
        FOREIGN KEY (addressee_id) REFERENCES members(member_id) NOT VALID;

ALTER TABLE collection_trade_requests
    ADD CONSTRAINT fk_collection_trade_requests_requester
        FOREIGN KEY (requester_id) REFERENCES members(member_id) NOT VALID,
    ADD CONSTRAINT fk_collection_trade_requests_owner
        FOREIGN KEY (owner_id) REFERENCES members(member_id) NOT VALID;

ALTER TABLE beg_requests
    ADD CONSTRAINT fk_beg_requests_applicant
        FOREIGN KEY (applicant_id) REFERENCES members(member_id) NOT VALID;

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_buyer
        FOREIGN KEY (buyer_id) REFERENCES members(member_id) NOT VALID,
    ADD CONSTRAINT fk_trades_seller
        FOREIGN KEY (seller_id) REFERENCES members(member_id) NOT VALID,
    ADD CONSTRAINT fk_trades_trade_request
        FOREIGN KEY (trade_request_id)
        REFERENCES trade_requests(trade_request_id) NOT VALID,
    ADD CONSTRAINT fk_trades_collection_trade_request
        FOREIGN KEY (collection_trade_request_id)
        REFERENCES collection_trade_requests(collection_trade_request_id) NOT VALID,
    ADD CONSTRAINT fk_trades_item
        FOREIGN KEY (item_id) REFERENCES items(item_id) NOT VALID;

ALTER TABLE item_comments
    ADD CONSTRAINT fk_item_comments_author
        FOREIGN KEY (author_id) REFERENCES members(member_id) NOT VALID;
