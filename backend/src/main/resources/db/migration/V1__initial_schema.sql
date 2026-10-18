CREATE TABLE source_fixture (
    fixture_id VARCHAR(100) PRIMARY KEY,
    kickoff VARCHAR(40) NOT NULL,
    tournament_id INTEGER NOT NULL,
    payload TEXT NOT NULL
);
CREATE TABLE match_result (
    fixture_id VARCHAR(100) PRIMARY KEY REFERENCES source_fixture(fixture_id),
    home_ht INTEGER NOT NULL CHECK (home_ht >= 0),
    away_ht INTEGER NOT NULL CHECK (away_ht >= 0),
    home_ft INTEGER NOT NULL CHECK (home_ft >= home_ht),
    away_ft INTEGER NOT NULL CHECK (away_ft >= away_ht),
    payload TEXT NOT NULL
);
CREATE TABLE simulation_snapshot (
    snapshot_id VARCHAR(80) PRIMARY KEY,
    cutoff VARCHAR(40) NOT NULL,
    created_at VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL
);
CREATE TABLE trained_model (
    model_id VARCHAR(80) PRIMARY KEY,
    trained_at VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL
);
