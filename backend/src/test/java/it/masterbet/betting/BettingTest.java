package it.masterbet.betting;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.betting.domain.*;
import it.masterbet.betting.domain.BettingRound.*;
import it.masterbet.football.domain.*;
import it.masterbet.odds.domain.*;
import it.masterbet.simulation.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class BettingTest {
  final Instant cutoff = Instant.parse("2026-05-06T12:00:00Z");

  QuotedOutcome quote(String id, String type, String period, String outcome, String line) {
    return new QuotedOutcome(
        id,
        "unibet",
        new Market(type + period + line, type, type, period, new BigDecimal(line), false, Map.of()),
        outcome,
        outcome,
        new BigDecimal("2"),
        cutoff.minusSeconds(1));
  }

  Snapshot snapshot(List<Prediction> predictions) {
    var targets =
        predictions.stream()
            .map(p -> p.quote().fixtureId())
            .distinct()
            .map(
                id ->
                    new Fixture(
                        id, 1, 2, "A", "B", 20, "League", 138092L, cutoff.plusSeconds(86400), null))
            .toList();
    return new Snapshot(
        "step",
        cutoff,
        "hash",
        new SimulationDataset(
            "step",
            cutoff,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            targets,
            predictions.stream().map(Prediction::quote).toList()),
        null,
        predictions);
  }

  Prediction prediction(String id) {
    return new Prediction(
        quote(id, "totals", "p1", "Over", "0.5"), .6, .5, 1 / .6, .2, true, "test");
  }

  @Test
  void selectsDistinctMatchesAndRespectsMoneyAndProbability() {
    var rows = new ArrayList<Prediction>();
    for (int i = 0; i < 7; i++) rows.add(prediction("f" + i));
    rows.add(prediction("f0"));
    var slips =
        new SlipSelector(new ProfitTargetStaking())
            .select(snapshot(rows), rows, BettingConfig.defaults(), new BigDecimal("500"));
    assertEquals(5, slips.size());
    assertEquals(
        new BigDecimal("21.67"),
        slips.stream().map(Slip::stake).reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(
        6,
        slips.stream()
            .flatMap(s -> s.legs().stream())
            .map(l -> l.quote().fixtureId())
            .distinct()
            .count());
    assertEquals(.36, slips.getLast().probability(), 1e-12);
    assertEquals(0, new BigDecimal("4").compareTo(slips.getLast().price()));
  }

  @Test
  void cannotSpendUnavailableCapitalAndDoesNotForceBets() {
    var rows = List.of(prediction("f"));
    assertTrue(
        new SlipSelector(new ProfitTargetStaking())
            .select(snapshot(rows), rows, BettingConfig.defaults(), new BigDecimal("1"))
            .isEmpty());
    assertTrue(
        new SlipSelector(new ProfitTargetStaking())
            .select(snapshot(List.of()), List.of(), BettingConfig.defaults(), new BigDecimal("500"))
            .isEmpty());
  }

  @Test
  void unsupportedOrWeakSignalsAreExcluded() {
    var rows =
        List.of(
            new Prediction(
                quote("f", "correctscore", "fulltime", "4-3", "0"), .1, .1, 10., 1., true, "test"));
    assertTrue(
        new SlipSelector(new ProfitTargetStaking())
            .select(snapshot(rows), rows, BettingConfig.defaults(), new BigDecimal("500"))
            .isEmpty());
  }

  @Test
  void settlesCorrectPeriodsAndRejectsPushMarkets() {
    var s = new Score("f", 1, 0, 1, 2, false);
    var calc = new MarketSettlement();
    assertTrue(calc.won(quote("f", "1x2", "p1", "1", "0"), s));
    assertTrue(calc.won(quote("f", "1x2", "p2", "2", "0"), s));
    assertTrue(calc.won(quote("f", "halftime-fulltime", "fulltime", "1/2", "0"), s));
    assertTrue(calc.won(quote("f", "totals", "p2", "Over", "1.5"), s));
    assertFalse(calc.won(quote("f", "totals", "p1", "Over", "1.5"), s));
    assertTrue(calc.won(quote("f", "teamtotals-team2", "p2", "Over", "1.5"), s));
    assertTrue(calc.won(quote("f", "correctscore", "fulltime", "1:2", "0"), s));
    assertTrue(calc.won(quote("f", "bothteamsscore", "fulltime", "Yes", "0"), s));
    assertThrows(
        IllegalArgumentException.class, () -> calc.won(quote("f", "totals", "p2", "Over", "2"), s));
    assertThrows(
        IllegalArgumentException.class, () -> calc.won(quote("wrong", "1x2", "p1", "1", "0"), s));
  }

  @Test
  void bankrollIncludesPendingStakesAndFutureResultsNeverSettle() {
    var rows = List.of(prediction("f"));
    var source = snapshot(rows);
    var slips =
        new SlipSelector(new ProfitTargetStaking())
            .select(source, rows, BettingConfig.defaults(), new BigDecimal("500"));
    var round =
        new BettingRound(
            "step",
            "hash",
            "model",
            cutoff,
            cutoff,
            BettingConfig.defaults(),
            new BigDecimal("500"),
            new BigDecimal("500"),
            slips,
            slips);
    var f = source.dataset().targets().getFirst();
    var played =
        new PlayedMatch(
            new Fixture(
                f.id(),
                1,
                2,
                "A",
                "B",
                20,
                "League",
                138092L,
                f.kickoff(),
                f.kickoff().plusSeconds(7200)),
            new Score("f", 1, 0, 2, 0, false));
    var eval = new BettingEvaluator(new MarketSettlement());
    var pending = eval.evaluate(List.of(round), List.of(played), cutoff, true);
    assertEquals("PENDING", pending.slips().getFirst().status());
    assertEquals(new BigDecimal("495.00"), pending.available());
    var won = eval.evaluate(List.of(round), List.of(played), cutoff.plusSeconds(172800), true);
    assertEquals(new BigDecimal("505.00"), won.bankroll());
    assertEquals(1., won.roi());
    assertEquals(.16, won.binaryBrier(), 1e-12);
  }

  @Test
  void invalidConfigCannotCreateNegativeOrUnlimitedStakes() {
    assertThrows(
        IllegalArgumentException.class, () -> new BettingConfig(BigDecimal.ZERO, BigDecimal.TEN));
    assertThrows(
        IllegalArgumentException.class, () -> new BettingConfig(BigDecimal.TEN, BigDecimal.ZERO));
  }

  @Test
  void fiveEligibleFixturesProduceFiveSinglesWithoutDuplicatingExposure() {
    var rows = java.util.stream.IntStream.range(0, 5).mapToObj(i -> prediction("f" + i)).toList();
    var slips =
        new SlipSelector(new ProfitTargetStaking())
            .select(snapshot(rows), rows, BettingConfig.defaults(), new BigDecimal("500"));
    assertEquals(5, slips.size());
    assertTrue(slips.stream().allMatch(s -> s.kind().equals("SINGLE")));
    assertEquals(
        new BigDecimal("25.00"),
        slips.stream().map(Slip::stake).reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Test
  void monetaryProjectionsRoundGrossOnceThenSubtractStake() {
    var slip =
        new Slip(
            "S",
            "SINGLE",
            "unibet",
            List.of(),
            new BigDecimal("0.5"),
            new BigDecimal("2.50"),
            new BigDecimal("2.05"),
            .6,
            .23,
            "test");
    assertEquals(new BigDecimal("5.13"), slip.potentialPayout());
    assertEquals(new BigDecimal("2.63"), slip.potentialProfit());
  }
}
