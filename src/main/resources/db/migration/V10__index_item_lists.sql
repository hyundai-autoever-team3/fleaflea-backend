CREATE INDEX IF NOT EXISTS idx_items_market_created_at
    ON items (market_id, created_at DESC, item_id DESC);
