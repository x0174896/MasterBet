CREATE TABLE lab_run_result (
  run_id VARCHAR(80) NOT NULL REFERENCES lab_run(id),
  fixture_id VARCHAR(180) NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY (run_id, fixture_id)
);

CREATE TABLE lab_learning_prediction (
  run_id VARCHAR(80) NOT NULL REFERENCES lab_run(id),
  fixture_id VARCHAR(180) NOT NULL,
  include_cup_scores BOOLEAN NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY (run_id, fixture_id, include_cup_scores)
);

-- Freeze existing settlements without updating any original campaign or source row.
INSERT INTO lab_run_result(run_id, fixture_id, payload)
SELECT DISTINCT b.run_id, m.id, m.payload
FROM lab_bet b JOIN lab_match m ON m.id=b.fixture_id
WHERE m.has_score=TRUE;
