package it.masterbet.model;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.TestMatches;
import it.masterbet.football.domain.*;
import it.masterbet.model.domain.FeatureBuilder;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class FeatureBuilderTest {
  private final FeatureBuilder builder = new FeatureBuilder();
  private final Instant cut = Instant.parse("2026-04-29T12:00:00Z");

  @Test
  void futureResultsCannotChangeFeatures() {
    var target = TestMatches.fixture("target", 1, 2, "2026-05-01T16:00:00Z");
    var past = TestMatches.played("past", "2026-04-25T12:00:00Z", 1, 1);
    var future = TestMatches.played("future", "2026-04-30T12:00:00Z", 9, 0);
    assertArrayEquals(
        builder.build(target, cut, List.of(past), List.of(past.fixture()), true),
        builder.build(
            target, cut, List.of(past, future), List.of(past.fixture(), future.fixture()), true));
  }

  @Test
  void cupChangesRestAndLoadButNotLeagueGoalRates() {
    var target = TestMatches.fixture("target", 1, 2, "2026-05-01T16:00:00Z");
    var league = TestMatches.played("league", "2026-04-22T12:00:00Z", 1, 0);
    var cup =
        new Fixture(
            "cup",
            3,
            1,
            "Third",
            "First",
            7,
            "Champions",
            null,
            cut.minusSeconds(86400),
            cut.minusSeconds(79200));
    var plain = builder.build(target, cut, List.of(league), List.of(league.fixture(), cup), false);
    var extended =
        builder.build(target, cut, List.of(league), List.of(league.fixture(), cup), true);
    assertEquals(plain[0], extended[0]);
    assertTrue(extended[8] < plain[8]);
    assertEquals(1, extended[12]);
    assertEquals(0, plain[12]);
  }

  @Test
  void formUsesNewestFiveMatches() {
    var target = TestMatches.fixture("target", 1, 2, "2026-05-01T16:00:00Z");
    List<PlayedMatch> history = new ArrayList<>();
    history.add(TestMatches.played("old", "2026-03-01T12:00:00Z", 20, 0));
    for (int i = 1; i <= 5; i++)
      history.add(TestMatches.played("recent" + i, "2026-04-0" + i + "T12:00:00Z", 0, 0));
    assertEquals(1.4 / 7, builder.build(target, cut, history, List.of(), false)[4], 1e-12);
  }

  @Test
  void restUsesScheduledKickoffsAndIncludesDaysUntilTarget() {
    var target = TestMatches.fixture("target", 1, 2, "2026-05-01T12:00:00Z");
    var past = TestMatches.played("past", "2026-04-25T12:00:00Z", 1, 0);
    assertEquals(
        6, builder.build(target, cut, List.of(past), List.of(past.fixture()), true)[8], 1e-12);
  }

  @Test
  void preseasonIsExcludedAndLowerLeagueIsNotMislabelledAsCup() {
    var target = TestMatches.fixture("target", 1, 2, "2026-05-01T12:00:00Z");
    var lower =
        new Fixture(
            "lower",
            1,
            3,
            "A",
            "C",
            22,
            "1st Division",
            null,
            cut.minusSeconds(86400),
            cut.minusSeconds(80000));
    var friendly =
        new Fixture(
            "friendly",
            1,
            3,
            "A",
            "C",
            23803,
            "Atlantic Cup",
            null,
            cut.minusSeconds(20000),
            cut.minusSeconds(10000));
    var x = builder.build(target, cut, List.of(), List.of(lower, friendly), true);
    assertEquals(1, x[10]);
    assertEquals(0, x[12]);
    assertEquals(3, x[8], 1e-12);
  }
}
