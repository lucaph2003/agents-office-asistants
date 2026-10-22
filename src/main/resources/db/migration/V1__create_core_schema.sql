CREATE TABLE tasks (
    id                      UUID         PRIMARY KEY,
    title                   VARCHAR(200) NOT NULL,
    description             TEXT         NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    priority                VARCHAR(10)  NOT NULL,
    assigned_agent          VARCHAR(20),
    source                  VARCHAR(20)  NOT NULL,
    external_reference      VARCHAR(255),
    requires_human_approval BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt                 INTEGER      NOT NULL DEFAULT 0,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    completed_at            TIMESTAMPTZ,

    CONSTRAINT chk_tasks_title CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_tasks_description CHECK (length(description) BETWEEN 1 AND 10000),
    CONSTRAINT chk_tasks_status CHECK (status IN
        ('INBOX', 'PLANNED', 'IN_PROGRESS', 'REVIEW', 'BLOCKED', 'DONE', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_tasks_assigned_agent CHECK (assigned_agent IN ('RESEARCH', 'DEVELOPER')),
    CONSTRAINT chk_tasks_source CHECK (source IN ('TELEGRAM', 'TRELLO', 'API', 'SCHEDULED')),
    CONSTRAINT chk_tasks_attempt CHECK (attempt >= 0),
    CONSTRAINT chk_tasks_completed_at CHECK (
        (status IN ('DONE', 'FAILED', 'CANCELLED')) = (completed_at IS NOT NULL))
);

CREATE INDEX idx_tasks_status_created_at ON tasks (status, created_at DESC);
CREATE INDEX idx_tasks_assigned_agent ON tasks (assigned_agent) WHERE assigned_agent IS NOT NULL;
CREATE INDEX idx_tasks_created_at ON tasks (created_at DESC);

CREATE TABLE agent_executions (
    id             UUID         PRIMARY KEY,
    task_id        UUID         NOT NULL REFERENCES tasks (id),
    agent_type     VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    attempt        INTEGER      NOT NULL,
    model          VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50)  NOT NULL,
    output         TEXT,
    error_code     VARCHAR(50),
    created_at     TIMESTAMPTZ  NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,

    CONSTRAINT chk_executions_agent_type CHECK (agent_type IN ('MANAGER', 'RESEARCH', 'DEVELOPER', 'REVIEWER')),
    CONSTRAINT chk_executions_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_executions_attempt CHECK (attempt >= 1),
    CONSTRAINT chk_executions_output_length CHECK (length(output) <= 20000),
    CONSTRAINT chk_executions_error_code CHECK ((status = 'FAILED') = (error_code IS NOT NULL))
);

CREATE INDEX idx_executions_task_id ON agent_executions (task_id, created_at);

CREATE TABLE reviews (
    id           UUID        PRIMARY KEY,
    task_id      UUID        NOT NULL REFERENCES tasks (id),
    execution_id UUID        NOT NULL REFERENCES agent_executions (id),
    reviewer     VARCHAR(10) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    feedback     TEXT,
    created_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_reviews_reviewer CHECK (reviewer IN ('AGENT', 'HUMAN')),
    CONSTRAINT chk_reviews_status CHECK (status IN ('APPROVED', 'CHANGES_REQUESTED', 'REJECTED')),
    CONSTRAINT chk_reviews_feedback CHECK (status = 'APPROVED' OR length(trim(feedback)) > 0),
    CONSTRAINT chk_reviews_feedback_length CHECK (length(feedback) <= 4000)
);

CREATE INDEX idx_reviews_task_id ON reviews (task_id, created_at);
CREATE INDEX idx_reviews_execution_id ON reviews (execution_id);

CREATE TABLE task_events (
    id         UUID          PRIMARY KEY,
    task_id    UUID          NOT NULL REFERENCES tasks (id),
    event_type VARCHAR(50)   NOT NULL,
    actor      VARCHAR(100)  NOT NULL,
    details    VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_task_events_task_id ON task_events (task_id, created_at);
