ALTER TABLE tasks
    ADD COLUMN scheduled_date DATE NULL AFTER sort_order,
    ADD COLUMN due_at TIMESTAMP(6) NULL AFTER scheduled_date,
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' AFTER due_at,
    ADD COLUMN completed_at TIMESTAMP(6) NULL AFTER priority,
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE AFTER completed_at,
    ADD CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    ADD INDEX idx_tasks_owner_views (owner_id, is_archived, is_done, scheduled_date, due_at, sort_order);

UPDATE tasks
SET completed_at = updated_at
WHERE is_done = TRUE AND completed_at IS NULL;
