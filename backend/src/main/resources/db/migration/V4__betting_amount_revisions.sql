CREATE TABLE betting_round_revision (
    scenario_id VARCHAR(100) NOT NULL REFERENCES betting_round(scenario_id),
    policy_version VARCHAR(100) NOT NULL,
    archived_at VARCHAR(50) NOT NULL,
    previous_payload TEXT NOT NULL,
    PRIMARY KEY (scenario_id, policy_version)
);

CREATE TABLE betting_reveal_revision (
    scenario_id VARCHAR(100) NOT NULL REFERENCES betting_round(scenario_id),
    model_variant VARCHAR(20) NOT NULL,
    slip_id VARCHAR(50) NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    archived_at VARCHAR(50) NOT NULL,
    previous_payload TEXT NOT NULL,
    PRIMARY KEY (scenario_id, model_variant, slip_id, policy_version)
);
