package it.masterbet.laboratory.domain;

import it.masterbet.model.domain.PoissonModel;
import java.time.Instant;
import java.util.*;

public record LabStep(
    String runId,
    int number,
    Instant cutoff,
    boolean includeCupScores,
    int trainingRows,
    int validationRows,
    Double validationMae,
    PoissonModel model,
    List<LabPatternMiner.Pattern> patterns,
    Map<Integer, Integer> rowsPerCompetition,
    String trainingHash,
    int proposed,
    int excluded,
    String policy,
    Map<String, String> exclusions) {
  public LabStep {
    exclusions = exclusions == null ? Map.of() : Map.copyOf(exclusions);
  }

  public LabStep(
      String runId,
      int number,
      Instant cutoff,
      boolean includeCupScores,
      int trainingRows,
      int validationRows,
      Double validationMae,
      PoissonModel model,
      List<LabPatternMiner.Pattern> patterns,
      Map<Integer, Integer> rowsPerCompetition,
      String trainingHash,
      int proposed,
      int excluded,
      String policy) {
    this(
        runId,
        number,
        cutoff,
        includeCupScores,
        trainingRows,
        validationRows,
        validationMae,
        model,
        patterns,
        rowsPerCompetition,
        trainingHash,
        proposed,
        excluded,
        policy,
        Map.of());
  }
}
