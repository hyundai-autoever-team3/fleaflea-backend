ALTER TABLE trades
    ALTER COLUMN collection_trade_request_id DROP NOT NULL,
ALTER COLUMN trade_request_id DROP NOT NULL;