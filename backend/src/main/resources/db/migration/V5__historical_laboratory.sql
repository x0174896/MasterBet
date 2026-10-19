CREATE TABLE lab_competition (id INTEGER PRIMARY KEY, payload TEXT NOT NULL);
CREATE TABLE lab_match (
  id VARCHAR(180) PRIMARY KEY, competition_id INTEGER NOT NULL,
  season_name VARCHAR(40) NOT NULL, kickoff VARCHAR(50) NOT NULL,
  is_cup BOOLEAN NOT NULL, has_score BOOLEAN NOT NULL, payload TEXT NOT NULL
);
CREATE INDEX lab_match_competition ON lab_match(competition_id, season_name, kickoff);
CREATE TABLE lab_quote (id VARCHAR(64) PRIMARY KEY, fixture_id VARCHAR(180) NOT NULL, payload TEXT NOT NULL);
CREATE INDEX lab_quote_fixture ON lab_quote(fixture_id);
CREATE TABLE lab_revision (
  id VARCHAR(64) PRIMARY KEY, entity_type VARCHAR(40) NOT NULL,
  entity_id VARCHAR(180) NOT NULL, archived_at VARCHAR(50) NOT NULL, payload TEXT NOT NULL
);
CREATE TABLE lab_job (
  id VARCHAR(80) PRIMARY KEY, kind VARCHAR(40) NOT NULL, status VARCHAR(30) NOT NULL,
  completed INTEGER NOT NULL, total INTEGER NOT NULL, message TEXT NOT NULL,
  started_at VARCHAR(50) NOT NULL, updated_at VARCHAR(50) NOT NULL, payload TEXT NOT NULL
);
CREATE TABLE lab_run (
  id VARCHAR(80) PRIMARY KEY, status VARCHAR(30) NOT NULL, created_at VARCHAR(50) NOT NULL,
  first_bet VARCHAR(50), last_bet VARCHAR(50), data_hash VARCHAR(64) NOT NULL,
  config TEXT NOT NULL, report TEXT NOT NULL
);
CREATE TABLE lab_step (
  run_id VARCHAR(80) NOT NULL REFERENCES lab_run(id), step_number INTEGER NOT NULL,
  cutoff VARCHAR(50) NOT NULL, include_cup_scores BOOLEAN NOT NULL, payload TEXT NOT NULL,
  PRIMARY KEY(run_id, step_number, include_cup_scores)
);
CREATE TABLE lab_bet (
  id VARCHAR(180) PRIMARY KEY, run_id VARCHAR(80) NOT NULL REFERENCES lab_run(id),
  fixture_id VARCHAR(180) NOT NULL, competition_id INTEGER NOT NULL,
  include_cup_scores BOOLEAN NOT NULL, kickoff VARCHAR(50) NOT NULL,
  available_at VARCHAR(50) NOT NULL, payload TEXT NOT NULL,
  UNIQUE(run_id, fixture_id, include_cup_scores)
);
CREATE INDEX lab_bet_run ON lab_bet(run_id, include_cup_scores, available_at);
CREATE TABLE lab_spoiler (
  bet_id VARCHAR(180) PRIMARY KEY REFERENCES lab_bet(id), revealed_at VARCHAR(50) NOT NULL
);
