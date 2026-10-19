package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabFeaturesTest {
  private final LabFeatures builder = new LabFeatures();
  private final Instant cutoff = Instant.parse("2026-02-01T00:00:00Z");
  private final LabMatch target =
      LabData.match("target", 384, "2025/2026", cutoff.plusSeconds(86400), 1, 2, 0, 1, false);

  @Test
  void sevenMeansSevenInSameCompetitionAndSeason() {
    var data =
        new ArrayList<>(LabData.season(384, "2025/2026", cutoff.minus(Duration.ofDays(30)), 7));
    data.addAll(LabData.season(82, "2025/2026", cutoff.minus(Duration.ofDays(30)), 7));
    data.addAll(LabData.season(384, "2024/2025", cutoff.minus(Duration.ofDays(30)), 7));
    data.add(LabData.match("cup", 2, "2025/2026", cutoff.minusSeconds(86400), 1, 2, 2, 4, true));
    var input = builder.build(target.fixture(), target.season(), cutoff, data, true);
    assertEquals(7, input.homeCount());
    assertEquals(7, input.awayCount());
    data.removeIf(m -> m.fixture().id().equals("calcio:384-6-0") && m.season().equals("2025/2026"));
    assertEquals(
        6, builder.build(target.fixture(), target.season(), cutoff, data, false).homeCount());
  }

  @Test
  void futureScoresAndOtherLeaguesCannotChangeLeagueFeatures() {
    var data =
        new ArrayList<>(LabData.season(384, "2025/2026", cutoff.minus(Duration.ofDays(40)), 7));
    var before = builder.build(target.fixture(), target.season(), cutoff, data, true);
    data.add(LabData.match("future", 384, "2025/2026", cutoff.plusSeconds(1), 1, 2, 12, 25, false));
    data.addAll(LabData.season(82, "2025/2026", cutoff.minus(Duration.ofDays(40)), 7));
    // Other leagues for the same team may affect calendar, never league goal rates/counts.
    assertArrayEquals(
        Arrays.copyOf(before.values(), 20),
        Arrays.copyOf(
            builder.build(target.fixture(), target.season(), cutoff, data, true).values(), 20));
    data.removeIf(m -> m.fixture().tournamentId() == 82);
    assertArrayEquals(
        before.values(),
        builder.build(target.fixture(), target.season(), cutoff, data, true).values());
  }

  @Test
  void cupGoalsChangeOnlyEnabledFeaturesWhileCalendarAlwaysChanges() {
    var data =
        new ArrayList<>(LabData.season(384, "2025/2026", cutoff.minus(Duration.ofDays(40)), 7));
    var without = builder.build(target.fixture(), target.season(), cutoff, data, false);
    data.add(
        LabData.match("cup", 2, "2025/2026", cutoff.minus(Duration.ofDays(2)), 1, 2, 0, 1, true));
    var off = builder.build(target.fixture(), target.season(), cutoff, data, false);
    var on = builder.build(target.fixture(), target.season(), cutoff, data, true);
    assertNotEquals(without.values()[20], off.values()[20]);
    assertArrayEquals(off.values(), Arrays.copyOf(on.values(), 32));
    data.set(
        data.size() - 1,
        LabData.match("cup", 2, "2025/2026", cutoff.minus(Duration.ofDays(2)), 1, 2, 4, 9, true));
    assertArrayEquals(
        off.values(),
        builder.build(target.fixture(), target.season(), cutoff, data, false).values());
    assertFalse(
        Arrays.equals(
            on.values(),
            builder.build(target.fixture(), target.season(), cutoff, data, true).values()));
  }

  @Test
  void missingCupScoreStillContributesToCalendar() {
    var cup = LabData.match("cup", 2, "2026", cutoff.minusSeconds(86400), 1, 2, 0, 1, true);
    var noScore =
        new LabMatch(
            cup.fixture(), cup.season(), cup.round(), true, null, cup.sourceUrl(), null, "test");
    var off = builder.build(target.fixture(), target.season(), cutoff, List.of(noScore), false);
    assertEquals(1, off.values()[21]);
    assertEquals(0, off.homeCount());
  }
}
