package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabMarketLearningTest {
  final Instant start = Instant.parse("2026-01-01T00:00:00Z");
  final LabMarketLearning learner = new LabMarketLearning();

  LabMarketLearning.Observation observation(int i, String type, boolean won, double probability) {
    Instant predicted = start.plus(Duration.ofDays(7L * (i / 20)));
    Instant kickoff = predicted.plusSeconds(3600L * (1 + i % 20));
    var q =
        new QuotedOutcome(
            "f" + i,
            "test",
            new Market(type, type, type, "fulltime", new BigDecimal("1.5"), false, Map.of()),
            "Under",
            "Under",
            new BigDecimal("2.4"),
            kickoff.minusSeconds(60));
    return new LabMarketLearning.Observation(
        new LabLearning.Observation(
            q.fixtureId(),
            1,
            "2026",
            kickoff,
            predicted,
            kickoff.plusSeconds(7200),
            q,
            probability,
            won,
            5,
            won ? 7 : -5,
            Collections.nCopies(32, 0.)),
        false);
  }

  List<LabMarketLearning.Observation> history() {
    List<LabMarketLearning.Observation> history = new ArrayList<>();
    for (int i = 0; i < 400; i++) history.add(observation(i, "teamtotals-team1", i % 2 == 0, .75));
    return history;
  }

  @Test
  void alternativesGetTheirOwnChronologicalCalibrationAndPrimaryIsNotRequired() {
    var past = history();
    for (int i = 0; i < 400; i++) past.add(observation(i, "teamtotals-team2", i % 5 != 0, .75));
    var fitted = learner.fit(past, start.plus(Duration.ofDays(160)), false, List.of());
    var home = fitted.calibration("teamtotals-team1:fulltime:Under").orElseThrow();
    var away = fitted.calibration("teamtotals-team2:fulltime:Under").orElseThrow();
    assertTrue(home.accepted());
    assertTrue(home.apply(.75) < .6);
    assertTrue(home.supports(.75));
    assertTrue(away.apply(.75) > home.apply(.75));
    assertEquals(400, fitted.fixtures());
    assertEquals(800, fitted.alternatives());
    assertTrue(home.latestFitResult().isBefore(home.validationFrom()));
    assertTrue(home.calibratedBrier() < home.rawBrier());
  }

  @Test
  void futureAndSimultaneousResultsCannotChangeLearningAndBoundaryPurgesLateTrainingLabels() {
    var past = history();
    var cutoff = start.plus(Duration.ofDays(80));
    var visible = past.stream().filter(o -> o.forecast().settledAt().isBefore(cutoff)).toList();
    assertEquals(
        learner.fit(visible, cutoff, false, List.of()),
        learner.fit(past, cutoff, false, List.of()));
    var fitted = learner.fit(past, start.plus(Duration.ofDays(160)), false, List.of());
    Instant boundary = fitted.global().validationFrom();
    var o = past.getFirst().forecast();
    past.set(
        0,
        new LabMarketLearning.Observation(
            new LabLearning.Observation(
                o.fixtureId(),
                o.competitionId(),
                o.season(),
                o.kickoff(),
                o.predictedAt(),
                boundary,
                o.quote(),
                o.probability(),
                o.won(),
                o.stake(),
                o.net(),
                o.features()),
            false));
    var purged = learner.fit(past, start.plus(Duration.ofDays(160)), false, List.of()).global();
    assertEquals(boundary, purged.validationFrom());
    assertEquals(fitted.global().fitted() - 1, purged.fitted());
    assertTrue(purged.latestFitResult().isBefore(boundary));
    assertTrue(
        purged.bands().stream()
            .allMatch(b -> b.support() == 0 || b.support() <= purged.validation()));
  }

  @Test
  void validationOutcomesMayChangeAcceptanceButNeverTheFittedCoefficientsOrGlobalPrior() {
    var past = history();
    var cutoff = start.plus(Duration.ofDays(160));
    var original = learner.fit(past, cutoff, false, List.of());
    var boundary = original.global().validationFrom();
    var changed =
        past.stream()
            .map(
                item -> {
                  var o = item.forecast();
                  if (o.predictedAt().isBefore(boundary)) return item;
                  return new LabMarketLearning.Observation(
                      new LabLearning.Observation(
                          o.fixtureId(),
                          o.competitionId(),
                          o.season(),
                          o.kickoff(),
                          o.predictedAt(),
                          o.settledAt(),
                          o.quote(),
                          o.probability(),
                          true,
                          o.stake(),
                          7,
                          o.features()),
                      item.primary());
                })
            .toList();
    var fitted = learner.fit(changed, cutoff, false, List.of());
    assertEquals(original.global().offset(), fitted.global().offset(), 1e-12);
    assertEquals(original.global().slope(), fitted.global().slope(), 1e-12);
    assertEquals(
        original.families().getFirst().offset(), fitted.families().getFirst().offset(), 1e-12);
    assertNotEquals(
        original.families().getFirst().accepted(), fitted.families().getFirst().accepted());
  }

  @Test
  void extraLinesCannotTurnFiftyFixturesIntoAnAdequateSample() {
    var past = new ArrayList<LabMarketLearning.Observation>();
    for (int i = 0; i < 50; i++)
      for (int line = 0; line < 10; line++)
        past.add(observation(i, "teamtotals-team1", i % 2 == 0, .75));
    var fitted = learner.fit(past, start.plus(Duration.ofDays(160)), false, List.of());
    var family = fitted.calibration("teamtotals-team1:fulltime:Under").orElseThrow();
    assertEquals(50, family.support());
    assertFalse(family.supports(.75));
    assertFalse(family.accepted());
  }

  @Test
  void unsupportedProbabilityBandsAndExtrapolationsAreNotPromoted() {
    var fitted = learner.fit(history(), start.plus(Duration.ofDays(160)), false, List.of());
    var family = fitted.families().getFirst();
    assertTrue(family.supports(.75));
    assertFalse(family.supports(.55));
    assertFalse(family.supports(.95));
    assertTrue(learner.fit(List.of(), start, true, List.of()).families().isEmpty());
  }
}
