-- SynchPlay v2 — add watch history tracking

CREATE TABLE watch_history (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES app_users(id),
    video_node_id  VARCHAR(64) NOT NULL,
    video_id       VARCHAR(32) NOT NULL,
    title          TEXT,
    channel        TEXT,
    watched_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_watch_history_user ON watch_history(user_id);
CREATE INDEX idx_watch_history_time ON watch_history(user_id, watched_at DESC);
