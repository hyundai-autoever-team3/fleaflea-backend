ALTER TABLE member
    ADD COLUMN oauth2_provider VARCHAR(20) NULL,
    ADD COLUMN oauth2_id VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_member_oauth2_provider_id
    ON member (oauth2_provider, oauth2_id);