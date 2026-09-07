-- A child account is separate from adult users. A tablet can be assigned to one
-- child without requiring the child to have an email address.
CREATE TABLE child_credentials (
    child_id UUID PRIMARY KEY REFERENCES children(child_id) ON DELETE CASCADE,
    username VARCHAR(50) NOT NULL,
    password_hash TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts SMALLINT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Usernames are case-insensitive but preserve their original spelling.
CREATE UNIQUE INDEX uq_child_credentials_username_lower
    ON child_credentials (LOWER(username));
