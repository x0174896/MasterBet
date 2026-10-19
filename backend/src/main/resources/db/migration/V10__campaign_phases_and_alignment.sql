ALTER TABLE lab_job ADD COLUMN campaign_id VARCHAR(80);
ALTER TABLE lab_job ADD COLUMN technical_errors INTEGER NOT NULL DEFAULT 0;
CREATE TABLE lab_campaign_stage (
  request_hash VARCHAR(64) NOT NULL, stage VARCHAR(40) NOT NULL,
  job_id VARCHAR(80) NOT NULL, status VARCHAR(30) NOT NULL,
  config TEXT NOT NULL, updated_at VARCHAR(50) NOT NULL,
  PRIMARY KEY(request_hash,stage)
);
CREATE TABLE lab_fixture_alignment (
  official_id VARCHAR(180) PRIMARY KEY, provider_id VARCHAR(180) NOT NULL,
  competition_id INTEGER NOT NULL, season_name VARCHAR(40) NOT NULL,
  payload TEXT NOT NULL
);
