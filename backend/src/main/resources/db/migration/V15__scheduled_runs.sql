-- V15: last-run marker so scheduled tasks can catch up after the app was scaled to zero.

CREATE TABLE scheduled_runs (
    task_name   varchar(60) PRIMARY KEY,
    last_run_at timestamptz NOT NULL
);
