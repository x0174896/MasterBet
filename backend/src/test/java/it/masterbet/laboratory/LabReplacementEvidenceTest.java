package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabReplacementEvidenceTest {
  final Instant start = Instant.parse("2026-01-01T00:00:00Z");
  final LabReplacementEvidence learner = new LabReplacementEvidence();

  List<LabReplacementEvidence.Observation> history(double primary) {
    List<LabReplacementEvidence.Observation> rows = new ArrayList<>();
    for (int i = 0; i < 120; i++) {
      var predicted = start.plus(Duration.ofDays(7L * (i / 20)));
      rows.add(
          new LabReplacementEvidence.Observation(
              "f" + i,
              "market",
              predicted,
              predicted.plus(Duration.ofDays(2)),
              5,
              i % 10 < 7 ? 5 : -5,
              primary));
    }
    return rows;
  }

  @Test
  void aReplacementMustEarnAndBeatTheMatchedPrimaryAcrossWeeks() {
    var accepted = learner.evaluate(history(0), start.plus(Duration.ofDays(60))).getFirst();
    assertTrue(accepted.allowed());
    assertEquals(120, accepted.support());
    assertEquals(6, accepted.weeks());
    assertTrue(accepted.lowerRoi() > 0);
    assertTrue(accepted.lowerDifference() > 0);
    var rejected = learner.evaluate(history(5), start.plus(Duration.ofDays(60))).getFirst();
    assertFalse(rejected.allowed());
    assertTrue(rejected.firstRoi() > 0);
    assertTrue(rejected.secondRoi() > 0);
    assertTrue(rejected.lowerDifference() < 0);
  }

  @Test
  void futureResultsAndDuplicateQuotesCannotIncreaseTheEvidence() {
    var rows = history(0);
    var cutoff = start.plus(Duration.ofDays(21));
    var prefix = rows.stream().filter(o -> o.settledAt().isBefore(cutoff)).toList();
    assertEquals(learner.evaluate(prefix, cutoff), learner.evaluate(rows, cutoff));
    assertFalse(learner.evaluate(rows, cutoff).getFirst().allowed());
    rows.addAll(new ArrayList<>(rows));
    assertEquals(120, learner.evaluate(rows, start.plus(Duration.ofDays(60))).getFirst().support());
  }

  @Test
  void aBadFirstBlockCannotBeHiddenByTheLaterWins() {
    var rows = history(0);
    List<LabReplacementEvidence.Observation> changed = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      var o = rows.get(i);
      changed.add(
          new LabReplacementEvidence.Observation(
              o.fixtureId(), o.family(), o.predictedAt(), o.settledAt(), 5, i < 60 ? -5 : 5, 0));
    }
    var check = learner.evaluate(changed, start.plus(Duration.ofDays(60))).getFirst();
    assertFalse(check.allowed());
    assertTrue(check.firstRoi() < 0);
    assertTrue(check.secondRoi() > 0);
  }
}
