CREATE TABLE health_check (
                              id BIGSERIAL PRIMARY KEY,
                              message VARCHAR(100) NOT NULL,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
