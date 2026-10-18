package it.masterbet.betting.domain;

import it.masterbet.odds.domain.QuotedOutcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public record BettingRound(
    String scenarioId,
    String datasetSha256,
    String modelId,
    Instant cutoff,
    Instant frozenAt,
    BettingConfig config,
    BigDecimal openingBankroll,
    BigDecimal leagueOpeningBankroll,
    List<Slip> cups,
    List<Slip> league,
    String policyVersion) {
  public static final String CURRENT_POLICY = "net-profit-unit-v3";

  public BettingRound(
      String scenarioId,
      String datasetSha256,
      String modelId,
      Instant cutoff,
      Instant frozenAt,
      BettingConfig config,
      BigDecimal openingBankroll,
      BigDecimal leagueOpeningBankroll,
      List<Slip> cups,
      List<Slip> league) {
    this(
        scenarioId,
        datasetSha256,
        modelId,
        cutoff,
        frozenAt,
        config,
        openingBankroll,
        leagueOpeningBankroll,
        cups,
        league,
        CURRENT_POLICY);
  }

  public record Leg(
      QuotedOutcome quote, String home, String away, Instant kickoff, double probability) {}

  public record Slip(
      String id,
      String kind,
      String bookmaker,
      List<Leg> legs,
      BigDecimal units,
      BigDecimal stake,
      BigDecimal price,
      double probability,
      double expectedReturn,
      String rationale,
      BigDecimal potentialPayout,
      BigDecimal potentialProfit) {
    public Slip {
      potentialPayout = stake.multiply(price).setScale(2, RoundingMode.HALF_UP);
      potentialProfit = potentialPayout.subtract(stake);
    }

    public Slip(
        String id,
        String kind,
        String bookmaker,
        List<Leg> legs,
        BigDecimal units,
        BigDecimal stake,
        BigDecimal price,
        double probability,
        double expectedReturn,
        String rationale) {
      this(
          id,
          kind,
          bookmaker,
          legs,
          units,
          stake,
          price,
          probability,
          expectedReturn,
          rationale,
          null,
          null);
    }
  }
}
