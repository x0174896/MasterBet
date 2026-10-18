package it.masterbet.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import it.masterbet.football.application.FixtureSource;
import it.masterbet.football.domain.*;
import it.masterbet.football.infrastructure.*;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.odds.application.HistoricalOddsSource;
import it.masterbet.simulation.application.NextStepDownloader;
import it.masterbet.simulation.domain.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NextStepDownloaderTest {
  private Fixture fixture(
      String id,
      long homeId,
      String home,
      long awayId,
      String away,
      String time,
      boolean finished) {
    var kickoff = Instant.parse(time);
    return new Fixture(
        id,
        homeId,
        awayId,
        home,
        away,
        20,
        "Eliteserien",
        138092L,
        kickoff,
        finished ? kickoff.plusSeconds(86400) : null,
        "CONSERVATIVE_24H_BOUND");
  }

  @Test
  void advancesAcrossTwoWindowsWithDiscrepantTimesAndNeverDownloadsTargetResults() {
    var fixtures = mock(FixtureSource.class);
    var odds = mock(HistoricalOddsSource.class);
    var official = spy(new NffSchedule(null));
    var scores = mock(NffScoreDownloader.class);
    var client = mock(OddsPapiClient.class);
    var old = fixture("old", 1, "Viking FK", 2, "Rosenborg BK", "2026-05-01T16:00:00Z", true);
    var played = fixture("played", 3, "Molde FK", 4, "IK Start", "2026-05-16T16:30:00Z", true);
    var target =
        fixture(
            "id1000002070302534",
            664,
            "Lillestroem SK",
            957,
            "Kristiansund BK",
            "2026-05-20T17:30:00Z",
            true);
    var later = fixture("later", 5, "SK Brann", 6, "Tromsoe IL", "2026-05-30T16:00:00Z", false);
    var schedule =
        List.of(
            new NffSchedule.Match("old-nff", 7, old.kickoff(), "Viking", "Rosenborg"),
            new NffSchedule.Match(
                "played-nff", 9, Instant.parse("2026-05-16T16:00:00Z"), "Molde", "Start"),
            new NffSchedule.Match(
                "8986140", 2, Instant.parse("2026-05-20T17:00:00Z"), "Lillestrøm", "Kristiansund"),
            new NffSchedule.Match("later-nff", 11, later.kickoff(), "Brann", "Tromsø"));
    doReturn(schedule).when(official).load();
    when(fixtures.tournament(eq(20), any(), any())).thenReturn(List.of(old, played, target, later));
    when(fixtures.teamsCalendar(anySet(), any(), any())).thenReturn(List.of());
    when(odds.downloadAt(any(), any(), anyList())).thenReturn(List.of());
    when(scores.download(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Fixture f = invocation.getArgument(0);
              return new Score(f.id(), 0, 0, 1, 0, false);
            });
    var oldData =
        new SimulationDataset(
            "eliteserien-2026-round9",
            Instant.parse("2026-05-13T12:00:00Z"),
            List.of(),
            List.of(new PlayedMatch(old, new Score(old.id(), 0, 0, 1, 0, false))),
            List.of(old),
            List.of(),
            List.of(played),
            List.of());
    var downloader =
        new NextStepDownloader(fixtures, odds, official, scores, client, new StepWindowPlanner());
    var next =
        downloader.download(
            new Snapshot(oldData.id(), Instant.EPOCH, "hash", oldData, null, List.of()),
            message -> {});
    assertEquals("eliteserien-2026-round10", next.id());
    assertEquals(Instant.parse("2026-05-20T12:00:00Z"), next.cutoff());
    assertEquals(2, next.leagueHistory().size());
    assertEquals(
        Instant.parse("2026-05-16T16:00:00Z"),
        next.leagueHistory().stream()
            .filter(p -> p.fixture().id().equals("played"))
            .findFirst()
            .orElseThrow()
            .fixture()
            .kickoff());
    assertEquals("id1000002070302534", next.targets().getFirst().id());
    assertEquals(Instant.parse("2026-05-20T17:00:00Z"), next.targets().getFirst().kickoff());
    assertNull(next.targets().getFirst().endedAt());
    verify(scores, never()).download(argThat(f -> f.id().equals(target.id())), any(), any());
    var following =
        downloader.download(
            new Snapshot(next.id(), Instant.EPOCH, "hash2", next, null, List.of()), message -> {});
    assertEquals("eliteserien-2026-round11", following.id());
    assertEquals(3, following.leagueHistory().size());
    var settled =
        following.leagueHistory().stream()
            .filter(p -> p.fixture().id().equals(target.id()))
            .findFirst()
            .orElseThrow();
    assertEquals(Instant.parse("2026-05-20T17:00:00Z"), settled.fixture().kickoff());
    assertEquals(target.id(), settled.score().fixtureId());
    assertEquals("later", following.targets().getFirst().id());
    assertEquals(Instant.parse("2026-05-20T17:30:00Z"), target.kickoff());
    assertEquals(1, oldData.leagueHistory().size());
    verify(scores, never()).download(argThat(f -> f.id().equals(later.id())), any(), any());
  }
}
