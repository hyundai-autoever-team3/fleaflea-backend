CREATE INDEX IF NOT EXISTS idx_collection_items_member_created_at
    ON collection_items (member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_collection_items_member_public_created_at
    ON collection_items (member_id, is_public, created_at DESC);
