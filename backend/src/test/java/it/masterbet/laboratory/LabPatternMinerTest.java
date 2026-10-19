package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.LabPatternMiner;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabPatternMinerTest {
  @Test
  void leagueAndSeasonPopulationsCannotCreateAPooledSpuriousPattern() {
    var rows = new ArrayList<LabPatternMiner.Observation>();
    for (int i = 0; i < 100; i++) {
      double[] a = new double[32];
      a[26] = 1;
      rows.add(new LabPatternMiner.Observation("League A / 2026", a, new double[] {0, 0, 0, 0}));
      rows.add(
          new LabPatternMiner.Observation(
              "League B / 2026", new double[32], new double[] {1, 0, 0, 0}));
    }
    var patterns = new LabPatternMiner().mine(rows);
    assertFalse(patterns.isEmpty());
    assertTrue(patterns.stream().noneMatch(LabPatternMiner.Pattern::confirmed));
    var noFirstHalfGoal =
        patterns.stream()
            .filter(p -> p.outcome().equals("Casa: non segna nel primo tempo"))
            .findFirst()
            .orElseThrow();
    assertEquals("League A / 2026", noFirstHalfGoal.population());
    assertEquals(1, noFirstHalfGoal.base());
    assertEquals(1, noFirstHalfGoal.lift());
    assertEquals(80, noFirstHalfGoal.support());
  }

  @Test
  void cupGoalConditionsExistOnlyForEnabledMode() {
    for (boolean cups : List.of(false, true)) {
      var data = new ArrayList<LabPatternMiner.Observation>();
      for (int i = 0; i < 100; i++) {
        var x = new double[cups ? 42 : 32];
        if (cups) x[36] = 1;
        data.add(new LabPatternMiner.Observation("A / 2026", x, new double[] {0, 0, 0, 0}));
      }
      var found = new LabPatternMiner().mine(data);
      assertEquals(cups, found.stream().anyMatch(p -> p.condition().contains("coppe recenti")));
    }
  }
}
