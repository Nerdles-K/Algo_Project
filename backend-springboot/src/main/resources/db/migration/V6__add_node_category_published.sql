-- Video topic category (from USvideos.csv category_id via US_category_id.json) and
-- real publish time (publish_time). Category powers a meaningful content-diversity
-- signal in the cocoon score; published_at is stored for display / future freshness use.
ALTER TABLE nodes ADD COLUMN category     VARCHAR(64);
ALTER TABLE nodes ADD COLUMN published_at TIMESTAMPTZ;
