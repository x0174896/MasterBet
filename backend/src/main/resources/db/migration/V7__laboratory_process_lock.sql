CREATE TABLE lab_process_lock (
  kind VARCHAR(40) PRIMARY KEY,
  job_id VARCHAR(80) NOT NULL REFERENCES lab_job(id)
);
CREATE TABLE lab_start_gate (singleton INTEGER PRIMARY KEY CHECK(singleton=1));
INSERT INTO lab_start_gate(singleton) VALUES(1);
