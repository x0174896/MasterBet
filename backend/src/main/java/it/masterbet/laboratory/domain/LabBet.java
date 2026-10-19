package it.masterbet.laboratory.domain;

import it.masterbet.odds.domain.QuotedOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Frozen proposal. The target result is intentionally absent. */
public record LabBet(
    String id,
    String runId,
    String fixtureId,
    int competitionId,
    String competition,
    String season,
    String home,
    String away,
    Instant kickoff,
    Instant availableAt,
    int step,
    boolean includeCupScores,
    QuotedOutcome quote,
    double probability,
    double expectedValue,
    BigDecimal stake,
    BigDecimal unitProfit,
    List<String> explanation,
    String trainingHash,
    List<Double> features,
    String modelVersion) {
  public LabBet {
    features = features == null ? List.of() : List.copyOf(features);
  }
}
