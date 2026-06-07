-- 给视频节点存储清洗后的题材标签（用于内容相似度 similar 边）
-- 幂等：允许手动先执行后再由 Flyway 记录
ALTER TABLE nodes ADD COLUMN IF NOT EXISTS tags TEXT;
