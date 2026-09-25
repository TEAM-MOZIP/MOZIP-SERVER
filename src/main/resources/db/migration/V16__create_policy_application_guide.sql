CREATE TABLE policy_application_guide (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_application_guide_policy_id UNIQUE (policy_id),
    CONSTRAINT fk_policy_application_guide_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE
);
