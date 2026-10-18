package it.masterbet.simulation.domain;

import it.masterbet.model.domain.ModelReport;
import java.time.Instant;
import java.util.List;

public record Snapshot(
    String id,
    Instant createdAt,
    String datasetSha256,
    SimulationDataset dataset,
    ModelReport model,
    List<Prediction> predictions) {}
