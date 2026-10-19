-- La proposta iniziale resta immutabile in betting_round; la revisione e separata.
CREATE TABLE betting_proposal (
  scenario_id VARCHAR(80) PRIMARY KEY REFERENCES betting_round(scenario_id),
  payload TEXT NOT NULL
);
CREATE TABLE betting_reveal_lock (
  scenario_id VARCHAR(80) PRIMARY KEY REFERENCES betting_round(scenario_id)
);
-- Archivio di consultazione: non viene letto da training, statistiche o bilancio al taglio.
CREATE TABLE betting_reveal (
  scenario_id VARCHAR(80) NOT NULL REFERENCES betting_round(scenario_id),
  model_variant VARCHAR(10) NOT NULL,
  slip_id VARCHAR(20) NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY(scenario_id,model_variant,slip_id)
);
