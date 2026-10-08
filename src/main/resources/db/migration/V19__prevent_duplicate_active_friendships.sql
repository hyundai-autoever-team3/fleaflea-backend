-- Finished requests remain as history; only pending/accepted unordered pairs are unique.
-- Existing active duplicates must be reviewed before applying this migration.
CREATE UNIQUE INDEX uk_friendships_active_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
    WHERE status IN ('PENDING', 'ACCEPTED');
