package it.masterbet.football;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.football.domain.Fixture;
import it.masterbet.football.infrastructure.NffSchedule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NffScheduleTest {
  private final NffSchedule schedule = new NffSchedule(null);
  private final Instant kickoff = Instant.parse("2026-05-20T17:00:00Z");
  private final NffSchedule.Match match =
      new NffSchedule.Match("8986140", 2, kickoff, "Lillestrøm", "Kristiansund");

  private Fixture fixture(String id, Instant time, Instant endedAt, String basis) {
    return new Fixture(
        id,
        664,
        957,
        "Lillestroem SK",
        "Kristiansund BK",
        20,
        "Eliteserien",
        138092L,
        time,
        endedAt,
        basis);
  }

  @Test
  void reconcilesObservedThirtyMinuteDifferenceWithoutChangingProviderIdentityOrOriginal() {
    var provider =
        fixture(
            "id1000002070302534",
            kickoff.plusSeconds(1800),
            kickoff.plusSeconds(88200),
            "CONSERVATIVE_24H_BOUND");
    assertEquals(match, schedule.identify(provider, List.of(match)));
    var normalized = schedule.reconcile(provider, List.of(match));
    assertEquals("id1000002070302534", normalized.id());
    assertEquals(kickoff, normalized.kickoff());
    assertEquals(kickoff.plusSeconds(86400), normalized.endedAt());
    assertEquals("NFF_CONSERVATIVE_24H_BOUND", normalized.timingBasis());
    assertFalse(normalized.finishedBefore(kickoff.plusSeconds(86399)));
    assertTrue(normalized.finishedBefore(kickoff.plusSeconds(86400)));
    assertEquals(kickoff.plusSeconds(1800), provider.kickoff());
    assertEquals(kickoff.plusSeconds(88200), provider.endedAt());
  }

  @Test
  void keepsAnExactFixtureAndRetainsAValidActualEndWhenOnlyScheduleChanges() {
    var exact = fixture("exact", kickoff, kickoff.plusSeconds(7200), "ACTUAL_END");
    assertSame(exact, schedule.reconcile(exact, List.of(match)));
    var shifted =
        fixture("shifted", kickoff.plusSeconds(1800), kickoff.plusSeconds(9000), "ACTUAL_END");
    var normalized = schedule.reconcile(shifted, List.of(match));
    assertEquals(shifted.endedAt(), normalized.endedAt());
    assertEquals("ACTUAL_END", normalized.timingBasis());
    var future =
        schedule.reconcile(
            fixture("future", kickoff.plusSeconds(1800), null, "SCHEDULED"), List.of(match));
    assertNull(future.endedAt());
  }

  @Test
  void acceptsAtMostTwoHoursAndRejectsDifferentDaysEvenAcrossMidnight() {
    assertEquals(
        match,
        schedule.identify(
            fixture("two-hours", kickoff.plusSeconds(7200), null, "SCHEDULED"), List.of(match)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            schedule.identify(
                fixture("too-far", kickoff.plusSeconds(7201), null, "SCHEDULED"), List.of(match)));
    var late =
        new NffSchedule.Match(
            "late", 2, Instant.parse("2026-05-20T21:30:00Z"), "Lillestrøm", "Kristiansund");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            schedule.identify(
                fixture("next-local-day", Instant.parse("2026-05-20T22:30:00Z"), null, "SCHEDULED"),
                List.of(late)));
  }

  @Test
  void rejectsWrongTeamsCompetitionSeasonAndAmbiguousOfficialMatches() {
    var provider = fixture("f", kickoff, null, "SCHEDULED");
    var reversed = new NffSchedule.Match("reverse", 2, kickoff, "Kristiansund", "Lillestrøm");
    assertThrows(
        IllegalArgumentException.class, () -> schedule.identify(provider, List.of(reversed)));
    for (var wrong :
        List.of(
            new Fixture(
                "cup",
                664,
                957,
                provider.home(),
                provider.away(),
                22,
                "Cup",
                138092L,
                kickoff,
                null),
            new Fixture(
                "old",
                664,
                957,
                provider.home(),
                provider.away(),
                20,
                "League",
                127337L,
                kickoff,
                null)))
      assertThrows(IllegalArgumentException.class, () -> schedule.identify(wrong, List.of(match)));
    var another =
        new NffSchedule.Match("other", 2, kickoff.plusSeconds(1800), "Lillestrøm", "Kristiansund");
    assertThrows(
        IllegalArgumentException.class, () -> schedule.identify(provider, List.of(match, another)));
  }

  @Test
  void selectsWindowByOfficialTimeAndStripsAllFutureResultMetadata() {
    var cutoff = kickoff.plusSeconds(900);
    var end = kickoff.plusSeconds(86400);
    var before = fixture("before", kickoff.plusSeconds(1800), null, "SCHEDULED");
    // Il provider e' fuori dalla finestra; l'orario ufficiale e' dentro.
    var insideTime = kickoff.plusSeconds(86100);
    var inside =
        fixture(
            "inside",
            kickoff.plusSeconds(87000),
            kickoff.plusSeconds(170000),
            "CONSERVATIVE_24H_BOUND");
    var nextDay = new NffSchedule.Match("next-day", 2, insideTime, "Lillestrøm", "Kristiansund");
    var targets = schedule.targets(List.of(before, inside), List.of(match, nextDay), cutoff, end);
    assertEquals(1, targets.size());
    assertEquals("inside", targets.getFirst().id());
    assertEquals(insideTime, targets.getFirst().kickoff());
    assertNull(targets.getFirst().endedAt());
    assertEquals("NFF_SCHEDULE", targets.getFirst().timingBasis());
  }

  @Test
  void includesOfficialTargetEvenIfProviderPutsItBeforeCutoff() {
    var cutoff = kickoff.minusSeconds(900);
    var before = fixture("before-provider", kickoff.minusSeconds(1800), null, "SCHEDULED");
    var targets =
        schedule.targets(List.of(before), List.of(match), cutoff, kickoff.plusSeconds(86400));
    assertEquals(kickoff, targets.getFirst().kickoff());
  }

  @Test
  void rejectsMissingOrDuplicateProviderIdentityInsteadOfSilentlyDroppingAMatch() {
    var cutoff = kickoff.minusSeconds(3600);
    var end = kickoff.plusSeconds(3600);
    assertThrows(
        IllegalArgumentException.class,
        () -> schedule.targets(List.of(), List.of(match), cutoff, end));
    var a = fixture("a", kickoff, null, "SCHEDULED");
    var b = fixture("b", kickoff.plusSeconds(1800), null, "SCHEDULED");
    assertThrows(
        IllegalArgumentException.class,
        () -> schedule.targets(List.of(a, b), List.of(match), cutoff, end));
  }
}
