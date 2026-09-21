ALTER TABLE beg_requests
DROP CONSTRAINT IF EXISTS beg_requests_status_check;

ALTER TABLE beg_requests
DROP CONSTRAINT IF EXISTS ck_beg_requests_status;

ALTER TABLE beg_requests
    ADD CONSTRAINT beg_requests_status_check
        CHECK (
            status IN (
                       'PENDING',
                       'ACCEPTED',
                       'REJECTED',
                       'CANCELLED',
                       'COMPLETED'
                )
            );