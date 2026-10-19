ALTER TABLE friendships
    ADD CONSTRAINT fk_friendships_requester
        FOREIGN KEY (requester_id) REFERENCES members(member_id),
    ADD CONSTRAINT fk_friendships_addressee
        FOREIGN KEY (addressee_id) REFERENCES members(member_id);

ALTER TABLE collection_trade_requests
    ADD CONSTRAINT fk_collection_trade_requests_requester
        FOREIGN KEY (requester_id) REFERENCES members(member_id),
    ADD CONSTRAINT fk_collection_trade_requests_owner
        FOREIGN KEY (owner_id) REFERENCES members(member_id);

ALTER TABLE beg_requests
    ADD CONSTRAINT fk_beg_requests_applicant
        FOREIGN KEY (applicant_id) REFERENCES members(member_id);

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_buyer
        FOREIGN KEY (buyer_id) REFERENCES members(member_id),
    ADD CONSTRAINT fk_trades_seller
        FOREIGN KEY (seller_id) REFERENCES members(member_id),
    ADD CONSTRAINT fk_trades_trade_request
        FOREIGN KEY (trade_request_id)
        REFERENCES trade_requests(trade_request_id),
    ADD CONSTRAINT fk_trades_collection_trade_request
        FOREIGN KEY (collection_trade_request_id)
        REFERENCES collection_trade_requests(collection_trade_request_id),
    ADD CONSTRAINT fk_trades_item
        FOREIGN KEY (item_id) REFERENCES items(item_id);

ALTER TABLE item_comments
    ADD CONSTRAINT fk_item_comments_author
        FOREIGN KEY (author_id) REFERENCES members(member_id);