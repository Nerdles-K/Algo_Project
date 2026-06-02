-- SynchPlay v2 — add user roles for admin functionality

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';
