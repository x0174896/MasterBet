package it.masterbet.football;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.masterbet.football.domain.*;
import it.masterbet.football.infrastructure.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NffScoreTest {
  @Test
  void unrelatedInvalidFixturesDoNotInvalidateTeamCalendar() throws Exception {
    var client = mock(it.masterbet.ingestion.infrastructure.OddsPapiClient.class);
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree("[{\"participant1Id\":99,\"participant2Id\":99}]");
    when(client.get(eq("fixtures"), anyMap())).thenReturn(json);
    assertTrue(
        new OddsPapiFixtureDownloader(client)
            .teamsCalendar(
                java.util.Set.of(1L, 2L), Instant.EPOCH, Instant.EPOCH.plusSeconds(86400))
            .isEmpty());
    assertThrows(
        RuntimeException.class,
        () ->
            new OddsPapiFixtureDownloader(client)
                .teamsCalendar(
                    java.util.Set.of(99L), Instant.EPOCH, Instant.EPOCH.plusSeconds(86400)));
  }

  final Instant kickoff = Instant.parse("2026-05-01T16:00:00Z");
  final Fixture fixture =
      new Fixture(
          "f",
          1,
          2,
          "Viking FK",
          "Rosenborg BK",
          20,
          "League",
          138092L,
          kickoff,
          kickoff.plusSeconds(86400));
  final NffSchedule.Match match = new NffSchedule.Match("123", 7, kickoff, "Viking", "Rosenborg");

  String html() {
    return "<meta property=\"og:title\" content=\"Viking - Rosenborg - 01.05.2026 18:00\"><meta"
        + " property=\"og:url\""
        + " content=\"https://www.fotball.no/fotballdata/kamp/?fiksId=123\"><div"
        + " class=\"a_matchCard\"><div class=\"endResult\">3 - 0</div><div"
        + " class=\"halfTime\">(1 - 0)</div><div class=\"cardFooter\"><div"
        + " class=\"endResult\">99 - 99</div>";
  }

  @Test
  void parsesOnlyMatchCardAndChecksIdentity() {
    var parser = new NffScoreDownloader(null);
    var s = parser.parse(fixture, match, html());
    assertEquals(1, s.homeHt());
    assertEquals(3, s.homeFt());
    assertEquals(2, s.homeSecondHalf());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.parse(
                fixture, match, html().replace("Viking - Rosenborg", "Rosenborg - Viking")));
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(fixture, match, html().replace("(1 - 0)", "")));
  }

  @Test
  void neverDownloadsFutureResult() {
    var archive = mock(NffArchive.class);
    var downloader = new NffScoreDownloader(archive);
    assertThrows(
        IllegalArgumentException.class, () -> downloader.download(fixture, match, kickoff));
    verifyNoInteractions(archive);
  }

  @Test
  void incompleteScheduleFailsInsteadOfInventingFixtures() {
    assertThrows(
        IllegalArgumentException.class, () -> new NffSchedule(null).parse("<html></html>"));
  }
}
