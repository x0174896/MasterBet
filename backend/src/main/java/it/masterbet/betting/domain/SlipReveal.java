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
  public SlipReveal withAmounts(BettingRound.Slip slip) {
    if (!slipId.equals(slip.id())
        || !results.stream().map(Result::leg).toList().equals(slip.legs()))
      throw new IllegalStateException("Le selezioni dell'esito non coincidono con la schedina");
    BigDecimal updatedPayout =
        switch (status) {
          case "PENDING" -> null;
          case "WON" -> slip.potentialPayout();
          case "LOST" -> new BigDecimal("0.00");
          default -> throw new IllegalStateException("Stato esito non riconosciuto");
        };
    return new SlipReveal(
        scenarioId,
        variant,
        slipId,
        checkedAt,
        status,
        slip.stake(),
        updatedPayout,
        updatedPayout == null ? null : updatedPayout.subtract(slip.stake()),
        results);
  }

  public record Result(BettingRound.Leg leg, Score score, Boolean won, String source) {}
}
