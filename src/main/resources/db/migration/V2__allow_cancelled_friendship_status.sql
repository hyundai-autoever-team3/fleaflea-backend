ALTER TABLE friendships
DROP CONSTRAINT IF EXISTS friendships_status_check;

ALTER TABLE friendships
    ADD CONSTRAINT friendships_status_check
        CHECK (status IN (
                          'PENDING',
                          'ACCEPTED',
                          'REJECTED',
                          'CANCELLED'
            ));