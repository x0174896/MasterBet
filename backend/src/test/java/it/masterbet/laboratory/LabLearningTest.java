package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabLearningTest {
  final LabLearning learner = new LabLearning();
  final Instant start = Instant.parse("2026-01-01T00:00:00Z");

  LabLearning.Observation observation(int i, boolean busy, boolean won, double p) {
    var kickoff = start.plus(Duration.ofDays(i));
    var x = new ArrayList<>(Collections.nCopies(32, 0.));
    if (busy) {
      x.set(21, 2.);
      x.set(24, 2.);
    }
    var q =
        new QuotedOutcome(
            "m" + i,
            "test",
            new Market("m", "1X2", "1x2", "fulltime", null, false, Map.of()),
            "1",
            "1",
            new BigDecimal("2"),
            kickoff.minusSeconds(3600));
    return new LabLearning.Observation(
        q.fixtureId(),
        1,
        "2026",
        kickoff,
        kickoff.minusSeconds(86400),
        kickoff.plusSeconds(7200),
        q,
        p,
        won,
        5,
        won ? 5 : -5,
        x);
  }

  @Test
  void calibrationCorrectsOverconfidenceOnlyWhenItImprovesALaterSuffix() {
    List<LabLearning.Observation> history = new ArrayList<>();
    for (int i = 0; i < 200; i++) history.add(observation(i, false, i % 2 == 0, .75));
    var fitted = learner.fit(history, start.plus(Duration.ofDays(300)), false, List.of());
    assertTrue(fitted.calibration().accepted());
    assertTrue(fitted.calibration().apply(.75) < .6);
    assertTrue(fitted.calibration().calibratedBrier() < fitted.calibration().rawBrier());
    assertEquals(140, fitted.calibration().fitted());
    assertEquals(60, fitted.calibration().validation());
    assertTrue(fitted.rules().isEmpty());
    history.clear();
    for (int i = 0; i < 200; i++) history.add(observation(i, false, i >= 140, .7));
    assertFalse(
        learner
            .fit(history, start.plus(Duration.ofDays(300)), false, List.of())
            .calibration()
            .accepted());
  }

  @Test
  void futureOutcomesAndUnavailableResultsCannotChangePastLearning() {
    var history = new ArrayList<LabLearning.Observation>();
    for (int i = 0; i < 400; i++) history.add(observation(i, i % 2 == 0, i % 3 == 0, .7));
    var cutoff = start.plus(Duration.ofDays(200));
    var expected = learner.fit(history.subList(0, 200), cutoff, true, List.of());
    assertEquals(expected, learner.fit(history, cutoff, true, List.of()));
    var unknown = observation(199, true, true, .9);
    var late =
        new LabLearning.Observation(
            unknown.fixtureId(),
            1,
            "2026",
            unknown.kickoff(),
            unknown.predictedAt(),
            cutoff,
            unknown.quote(),
            .9,
            true,
            5,
            5,
            unknown.features());
    assertEquals(0, learner.fit(List.of(late), cutoff, true, List.of()).observations());
  }

  @Test
  void negativePatternRequiresComparisonCasesAndPersistenceAcrossCalendarBlocks() {
    var history = new ArrayList<LabLearning.Observation>();
    for (int i = 0; i < 400; i++) history.add(observation(i, i % 2 == 0, i % 2 != 0, .7));
    var snapshot = learner.fit(history, start.plus(Duration.ofDays(500)), true, List.of());
    var rule =
        snapshot.rules().stream()
            .filter(r -> r.key().equals("calendar:busy"))
            .findFirst()
            .orElseThrow();
    assertTrue(rule.excluded());
    assertEquals(200, rule.support());
    assertTrue(rule.upperMeanReturn() < 0);
    var q = observation(450, true, false, .7);
    assertEquals(
        Optional.of("calendar:busy"),
        learner.rejection(
            snapshot, q.quote(), 1, "2026", q.features(), q.kickoff(), q.probability()));
    var allBusy = new ArrayList<LabLearning.Observation>();
    for (int i = 0; i < 400; i++) allBusy.add(observation(i, true, false, .7));
    assertTrue(
        learner.fit(allBusy, start.plus(Duration.ofDays(500)), true, List.of()).rules().isEmpty());
  }

  @Test
  void aPatternThatReversesLaterIsNotExcludedAndSmallSamplesAbstainFromLearning() {
    var history = new ArrayList<LabLearning.Observation>();
    for (int i = 0; i < 400; i++)
      history.add(observation(i, i % 2 == 0, i >= 200 || i % 2 != 0, .7));
    var snapshot = learner.fit(history, start.plus(Duration.ofDays(500)), true, List.of());
    assertTrue(snapshot.rules().stream().noneMatch(LabLearning.Rule::excluded));
    var small =
        learner.fit(history.subList(0, 50), start.plus(Duration.ofDays(500)), true, List.of());
    assertFalse(small.calibration().accepted());
    assertTrue(small.rules().isEmpty());
    assertEquals(.7, small.calibration().apply(.7));
  }
}
