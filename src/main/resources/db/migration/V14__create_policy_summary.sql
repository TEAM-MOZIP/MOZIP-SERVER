CREATE TABLE policy_summary (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_summary_policy_id UNIQUE (policy_id),
    CONSTRAINT fk_policy_summary_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE
);
