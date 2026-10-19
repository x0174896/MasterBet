CREATE TABLE lab_download_checkpoint (
  fixture_id VARCHAR(180) NOT NULL,
  bookmakers VARCHAR(250) NOT NULL,
  status VARCHAR(20) NOT NULL,
  checked_at VARCHAR(50) NOT NULL,
  PRIMARY KEY(fixture_id,bookmakers)
);
