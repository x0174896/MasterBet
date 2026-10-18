package it.masterbet.simulation.api;

import it.masterbet.football.domain.*;
import it.masterbet.statistics.domain.TeamStatistics;
import java.time.Instant;
import java.util.List;

public record ScenarioResponse(
    String id,
    String competition,
    String season,
    Instant cutoff,
    Instant importedAt,
    String source,
    String datasetSha256,
    int historicalMatches,
    int trainingMatches,
    int quoteCount,
    List<TargetMatch> matches,
    List<PlayedMatch> history,
    List<TeamStatistics> standings,
    List<Fixture> recentCups,
    List<PlayedMatch> cupResults,
    ModelSummary model,
    List<String> notes) {
  public record TargetMatch(
      Fixture fixture, long quotes, long markets, long modelledOutcomes, long suggestions) {}

  public record ModelSummary(
      String algorithm,
      int trainingMatches,
      int validationMatches,
      Instant trainingFrom,
      Instant trainingThrough,
      Instant validationFrom,
      Instant validationThrough,
      List<String> features,
      double baselineBrier,
      double leagueModelBrier,
      double cupsModelBrier,
      String limitation) {}
}
