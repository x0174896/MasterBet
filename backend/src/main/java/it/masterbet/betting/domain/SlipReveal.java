package it.masterbet.betting.domain;

import it.masterbet.football.domain.Score;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SlipReveal(
    String scenarioId,
    String variant,
    String slipId,
    Instant checkedAt,
    String status,
    BigDecimal stake,
    BigDecimal payout,
    BigDecimal profit,
    List<Result> results) {
  public record Result(BettingRound.Leg leg, Score score, Boolean won, String source) {}
}
