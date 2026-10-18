package it.masterbet.betting.domain;

import java.math.BigDecimal;
import java.util.List;

public record BettingEvaluation(
    List<SettledSlip> slips,
    BigDecimal closedStake,
    BigDecimal profit,
    BigDecimal pendingStake,
    BigDecimal bankroll,
    BigDecimal available,
    Double roi,
    BigDecimal maxDrawdown,
    BigDecimal singlesProfit,
    BigDecimal doublesProfit,
    int resolvedLegs,
    Double binaryBrier) {
  public record SettledSlip(
      String scenarioId,
      String slipId,
      String kind,
      String status,
      BigDecimal stake,
      BigDecimal payout,
      BigDecimal profit) {}
}
