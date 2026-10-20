package it.masterbet.laboratory.domain;

import it.masterbet.odds.domain.QuotedOutcome;
import java.time.Instant;
import java.util.*;

/**
 * Score-free forecasts for primary and alternative markets, frozen before learning their outcomes.
 */
public record LabMarketFrame(
    String fixtureId,
    int competitionId,
    String season,
    Instant kickoff,
    Instant predictedAt,
    boolean includeCupScores,
    List<Double> features,
    List<Candidate> candidates,
    Map<String, String> policyPicks) {
  public record Candidate(String key, QuotedOutcome quote, double probability, boolean primary) {}

  public LabMarketFrame {
    features = List.copyOf(features);
    candidates = List.copyOf(candidates);
    policyPicks = Map.copyOf(policyPicks);
  }

  public static String family(QuotedOutcome quote) {
    return quote.market().type() + ":" + quote.market().period() + ":" + quote.outcome();
  }

  public static String band(double probability) {
    return probability < .6
        ? "50-60"
        : probability < .7 ? "60-70" : probability < .8 ? "70-80" : "80-100";
  }

  public static String key(QuotedOutcome quote) {
    return family(quote)
        + "|"
        + quote.bookmaker()
        + "|"
        + quote.market().id()
        + "|"
        + quote.outcomeId();
  }
}
