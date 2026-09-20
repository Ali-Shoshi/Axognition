-- Fast resume is independent of the potentially large activity history.
CREATE TABLE child_lecture_progress (
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    lecture_id VARCHAR(80) NOT NULL,
    state JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (child_id, lecture_id)
);

CREATE TABLE child_lecture_sessions (
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    lecture_id VARCHAR(80) NOT NULL,
    session_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (child_id, lecture_id, session_id)
);

-- Hash partitions distribute writes without requiring monthly DDL on requests.
-- The child is included in the deduplication key and all history queries.
CREATE TABLE child_lecture_events (
    child_id UUID NOT NULL REFERENCES children(child_id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    lecture_id VARCHAR(80) NOT NULL,
    session_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correct BOOLEAN,
    event JSONB NOT NULL,
    PRIMARY KEY (child_id, event_id)
) PARTITION BY HASH (child_id);

DO $$
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TABLE child_lecture_events_p%s PARTITION OF child_lecture_events FOR VALUES WITH (MODULUS 16, REMAINDER %s)', i, i);
    END LOOP;
END $$;

CREATE INDEX child_lecture_events_history ON child_lecture_events (child_id, lecture_id, received_at, event_id);
CREATE INDEX child_lecture_events_received ON child_lecture_events USING BRIN (received_at);
