CREATE TABLE policy_eligibility (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    minimum_age INTEGER,
    maximum_age INTEGER,
    gender_condition VARCHAR(20),
    income_type VARCHAR(20),
    minimum_income_value INTEGER,
    maximum_income_value INTEGER,
    allowed_employment_statuses JSONB,
    allowed_household_types JSONB,
    additional_conditions JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_eligibility_policy_id UNIQUE (policy_id),
    CONSTRAINT fk_policy_eligibility_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE
);