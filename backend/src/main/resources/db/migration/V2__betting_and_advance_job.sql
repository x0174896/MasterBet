CREATE TABLE betting_round (
  scenario_id VARCHAR(80) PRIMARY KEY REFERENCES simulation_snapshot(snapshot_id),
  cutoff VARCHAR(40) NOT NULL,
  payload TEXT NOT NULL
);
CREATE TABLE advance_job (
  singleton INTEGER PRIMARY KEY CHECK (singleton=1),
  job_id VARCHAR(80), source_id VARCHAR(80), status VARCHAR(20) NOT NULL,
  message VARCHAR(1000) NOT NULL, updated_at VARCHAR(40) NOT NULL,
  owner_pid BIGINT, owner_started VARCHAR(40)
);
INSERT INTO advance_job(singleton,status,message,updated_at) VALUES(1,'IDLE','Nessun avanzamento avviato','2026-01-01T00:00:00Z');
