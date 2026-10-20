package it.masterbet.laboratory.application;

import it.masterbet.laboratory.domain.LabRunConfig;
import it.masterbet.laboratory.infrastructure.LabRunRepository;
import org.springframework.stereotype.Service;

@Service
public class LabExperimentService {
  private final LabRunRepository runs;
  private final LabSimulationService simulation;
  private final LabJobs jobs;

  public LabExperimentService(
      LabRunRepository runs, LabSimulationService simulation, LabJobs jobs) {
    this.runs = runs;
    this.simulation = simulation;
    this.jobs = jobs;
  }

  public LabRunConfig config(String referenceRunId, String name, String strategy) {
    if (referenceRunId == null || referenceRunId.isBlank())
      throw new IllegalArgumentException("Campagna di riferimento mancante");
    var reference = runs.run(referenceRunId);
    if (!reference.status().equals("COMPLETED")
        || !reference.config().strategy().equals("BASELINE")
        || !reference.config().mode().equals("HISTORICAL"))
      throw new IllegalArgumentException("Scegliere una campagna storica originale completa");
    if (!java.util.Set.of("CALIBRATED", "SIGNALS", "FILTERED", "ADAPTIVE")
        .contains(strategy == null ? "" : strategy))
      throw new IllegalArgumentException("Strategia sperimentale non valida");
    var c = reference.config();
    return new LabRunConfig(
        c.from(),
        c.to(),
        c.competitionIds(),
        c.unitProfit(),
        c.mode(),
        name,
        strategy,
        reference.id());
  }

  public LabJobs.Status start(String referenceRunId, String name, String strategy) {
    return jobs.start("calculate", simulation.calculate(config(referenceRunId, name, strategy)));
  }
}
