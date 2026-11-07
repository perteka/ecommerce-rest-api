ALTER TABLE refresh_tokens
    ADD COLUMN session_started_at TIMESTAMPTZ NOT NULL DEFAULT now();
