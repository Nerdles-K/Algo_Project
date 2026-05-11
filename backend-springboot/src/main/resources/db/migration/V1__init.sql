-- SynchPlay v2 — initial schema

CREATE TABLE nodes (
    node_id      VARCHAR(64) PRIMARY KEY,
    node_type    VARCHAR(16) NOT NULL,
    original_id  VARCHAR(64),
    display_name TEXT,
    channel      TEXT,
    views        BIGINT,
    likes        BIGINT
);

CREATE INDEX idx_nodes_type ON nodes(node_type);

CREATE TABLE edges (
    id        BIGSERIAL PRIMARY KEY,
    src       VARCHAR(64) NOT NULL REFERENCES nodes(node_id),
    dst       VARCHAR(64) NOT NULL REFERENCES nodes(node_id),
    edge_type VARCHAR(16) NOT NULL,
    weight    DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

CREATE INDEX idx_edges_src  ON edges(src);
CREATE INDEX idx_edges_dst  ON edges(dst);
CREATE INDEX idx_edges_type ON edges(edge_type);

CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(72)  NOT NULL,
    graph_node_id VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_users_graph_node ON app_users(graph_node_id);
