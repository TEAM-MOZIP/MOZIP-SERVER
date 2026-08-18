CREATE TABLE policy_application_info (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    application_procedure TEXT,
    required_documents_text TEXT,
    application_url VARCHAR(500),
    contact_info TEXT,
    application_notes TEXT,
    source_url VARCHAR(500) NOT NULL,
    verified_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_application_info_policy_id UNIQUE (policy_id),
    CONSTRAINT fk_policy_application_info_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE
);
