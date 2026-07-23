ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN provider VARCHAR(20),
    ADD COLUMN provider_user_id VARCHAR(100);

UPDATE users
SET provider = 'LOCAL',
    provider_user_id = id::text
WHERE provider IS NULL;

ALTER TABLE users
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN provider_user_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_provider_provider_user_id UNIQUE (provider, provider_user_id);