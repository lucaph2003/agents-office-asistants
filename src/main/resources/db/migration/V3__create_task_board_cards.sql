-- Link between a task and its card on the external board. Kept out of "tasks" so board
-- synchronisation never competes with the pipeline for the task row.
CREATE TABLE task_board_cards (
    task_id       UUID         PRIMARY KEY REFERENCES tasks (id),
    card_id       VARCHAR(64)  NOT NULL UNIQUE,
    card_url      VARCHAR(255) NOT NULL,
    synced_status VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
