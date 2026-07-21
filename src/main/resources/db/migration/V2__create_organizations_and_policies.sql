DROP TABLE IF EXISTS health_check;

CREATE TABLE organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50),
    website_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE regions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    CONSTRAINT uk_regions_code UNIQUE (code),
    CONSTRAINT fk_regions_parent FOREIGN KEY (parent_id) REFERENCES regions (id)
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_categories_code UNIQUE (code)
);

CREATE TABLE policies (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(300),
    description TEXT,
    target_description TEXT,
    benefit_description TEXT,
    application_method TEXT,
    application_type VARCHAR(20) NOT NULL,
    application_start_date DATE,
    application_end_date DATE,
    region_scope VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_url VARCHAR(500),
    source_updated_at TIMESTAMP,
    last_verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_policies_organization FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE RESTRICT
);

CREATE TABLE policy_categories (
    policy_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (policy_id, category_id),
    CONSTRAINT fk_policy_categories_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_categories_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);

CREATE TABLE policy_regions (
    policy_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    PRIMARY KEY (policy_id, region_id),
    CONSTRAINT fk_policy_regions_policy FOREIGN KEY (policy_id) REFERENCES policies (id) ON DELETE CASCADE,
    CONSTRAINT fk_policy_regions_region FOREIGN KEY (region_id) REFERENCES regions (id) ON DELETE RESTRICT
);

CREATE INDEX idx_policies_status ON policies (status);
CREATE INDEX idx_policies_application_end_date ON policies (application_end_date);
CREATE INDEX idx_policies_organization_id ON policies (organization_id);
CREATE INDEX idx_policy_categories_category_id ON policy_categories (category_id);
CREATE INDEX idx_policy_regions_region_id ON policy_regions (region_id);