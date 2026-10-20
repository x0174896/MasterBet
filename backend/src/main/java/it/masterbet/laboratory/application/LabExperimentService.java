package it.masterbet.laboratory.application;

import it.masterbet.laboratory.domain.LabRunConfig;
import it.masterbet.laboratory.infrastructure.LabRunRepository;
import org.springframework.stereotype.Service;

@Service
public class LabExperimentService {
  private final LabRunRepository runs;
  private final LabSimulationService simulation;
  private final LabJobs jobs;
  private final LabReplayService replay;

  public LabExperimentService(
      LabRunRepository runs,
      LabSimulationService simulation,
      LabJobs jobs,
      LabReplayService replay) {
    this.runs = runs;
    this.simulation = simulation;
    this.jobs = jobs;
    this.replay = replay;
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

  public java.util.function.Consumer<LabJobs.Progress> recalculate(String runId) {
    var run = runs.run(runId);
    if (!run.status().equals("COMPLETED")
        || run.config().strategy().equals("BASELINE")
        || !run.config().mode().equals("HISTORICAL"))
      throw new IllegalArgumentException(
          "Scegliere un esperimento storico completo, mai la campagna originale");
    return runs.marketFrames(run.id()).isEmpty()
        ? simulation.recalculate(run)
        : replay.recalculate(run);
  }
}
