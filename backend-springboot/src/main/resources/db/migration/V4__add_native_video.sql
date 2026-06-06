-- Native uploaded videos: distinguish source and store local file paths.
-- Existing rows (imported from CSV) default to 'youtube'.
ALTER TABLE nodes ADD COLUMN source     VARCHAR(16)  NOT NULL DEFAULT 'youtube';
ALTER TABLE nodes ADD COLUMN media_path VARCHAR(255);
ALTER TABLE nodes ADD COLUMN thumb_path VARCHAR(255);
