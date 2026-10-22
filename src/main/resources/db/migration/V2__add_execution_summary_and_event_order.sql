ALTER TABLE agent_executions ADD COLUMN summary VARCHAR(1000);

-- Several audit events can share a timestamp (e.g. REVIEW_APPROVED and TASK_COMPLETED);
-- the sequence keeps the order in which they were recorded.
ALTER TABLE task_events ADD COLUMN sequence BIGINT GENERATED ALWAYS AS IDENTITY;

DROP INDEX idx_task_events_task_id;
CREATE INDEX idx_task_events_task_id ON task_events (task_id, sequence);
