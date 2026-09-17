DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'member_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'notifications'
          AND column_name = 'receiver_id'
    ) THEN
        ALTER TABLE notifications RENAME COLUMN member_id TO receiver_id;
    END IF;
END $$;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS reference_id BIGINT;

ALTER TABLE notifications
    ALTER COLUMN reference_type SET NOT NULL,
    ALTER COLUMN reference_id SET NOT NULL;
