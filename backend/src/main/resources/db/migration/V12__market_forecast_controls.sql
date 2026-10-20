-- Score-free forecasts of primary and alternative markets. No existing campaign is rewritten.
CREATE TABLE lab_market_frame (
  run_id VARCHAR(80) NOT NULL REFERENCES lab_run(id),
  fixture_id VARCHAR(180) NOT NULL,
  include_cup_scores BOOLEAN NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY (run_id, fixture_id, include_cup_scores)
);
