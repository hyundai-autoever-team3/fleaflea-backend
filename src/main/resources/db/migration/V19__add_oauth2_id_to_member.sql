ALTER TABLE members
    ADD COLUMN oauth2_provider VARCHAR(20) NULL,
    ADD COLUMN oauth2_id VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_members_oauth2_provider_id
    ON members (oauth2_provider, oauth2_id);