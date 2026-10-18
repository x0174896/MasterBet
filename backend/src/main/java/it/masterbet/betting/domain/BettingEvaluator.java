package it.masterbet.betting.domain;

import it.masterbet.betting.domain.BettingEvaluation.*;
import it.masterbet.football.domain.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BettingEvaluator {
  private final MarketSettlement settlement;

  public BettingEvaluator(MarketSettlement settlement) {
    this.settlement = settlement;
  }

  public BettingEvaluation evaluate(
      List<BettingRound> rounds, List<PlayedMatch> history, Instant cutoff, boolean cups) {
    Map<String, Score> scores = new HashMap<>();
    for (var m : history)
      if (m.fixture().finishedBefore(cutoff)) scores.put(m.fixture().id(), m.score());
    BigDecimal initial =
        rounds.isEmpty()
            ? BigDecimal.ZERO
            : rounds
                .getFirst()
                .config()
                .unitValue()
                .multiply(rounds.getFirst().config().initialBankrollUnits());
    BigDecimal closed = BigDecimal.ZERO,
        profit = BigDecimal.ZERO,
        pending = BigDecimal.ZERO,
        peak = initial,
        drawdown = BigDecimal.ZERO,
        single = BigDecimal.ZERO,
        doubles = BigDecimal.ZERO;
    double brier = 0;
    int n = 0;
    List<SettledSlip> rows = new ArrayList<>();
    for (var round : rounds) {
      BigDecimal roundProfit = BigDecimal.ZERO;
      for (var slip : (cups ? round.cups() : round.league())) {
        boolean complete = true, won = true;
        for (var leg : slip.legs()) {
          var score = scores.get(leg.quote().fixtureId());
          if (score == null) {
            complete = false;
            continue;
          }
          boolean success = settlement.won(leg.quote(), score);
          won &= success;
          brier += Math.pow(leg.probability() - (success ? 1 : 0), 2);
          n++;
        }
        if (!complete) {
          pending = pending.add(slip.stake());
          rows.add(
              new SettledSlip(
                  round.scenarioId(), slip.id(), slip.kind(), "PENDING", slip.stake(), null, null));
          continue;
        }
        BigDecimal payout =
            won
                ? slip.stake().multiply(slip.price()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal net = payout.subtract(slip.stake());
        closed = closed.add(slip.stake());
        roundProfit = roundProfit.add(net);
        if (slip.kind().equals("SINGLE")) single = single.add(net);
        else doubles = doubles.add(net);
        rows.add(
            new SettledSlip(
                round.scenarioId(),
                slip.id(),
                slip.kind(),
                won ? "WON" : "LOST",
                slip.stake(),
                payout,
                net));
      }
      profit = profit.add(roundProfit);
      BigDecimal balance = initial.add(profit);
      peak = peak.max(balance);
      drawdown = drawdown.max(peak.subtract(balance));
    }
    BigDecimal bankroll = initial.add(profit);
    return new BettingEvaluation(
        List.copyOf(rows),
        closed,
        profit,
        pending,
        bankroll,
        bankroll.subtract(pending),
        closed.signum() == 0 ? null : profit.divide(closed, 10, RoundingMode.HALF_UP).doubleValue(),
        drawdown,
        single,
        doubles,
        n,
        n == 0 ? null : brier / n);
  }
}
