ALTER TABLE members
    RENAME COLUMN oauth2_provider TO social_provider;

ALTER TABLE members
    RENAME COLUMN oauth2_id TO provider_id;

ALTER INDEX uk_members_oauth2_provider_id
    RENAME TO uk_members_social_provider_id;