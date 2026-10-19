package it.masterbet.laboratory;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import it.masterbet.football.domain.Fixture;
import it.masterbet.football.infrastructure.OddsPapiFixtureDownloader;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import it.masterbet.odds.infrastructure.OddsPapiHistoricalOddsDownloader;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabDownloadResumeTest {
  final LabRepository repo = mock(LabRepository.class);
  final OddsPapiFixtureDownloader fixtures = mock(OddsPapiFixtureDownloader.class);
  final OddsPapiHistoricalOddsDownloader prices = mock(OddsPapiHistoricalOddsDownloader.class);
  final LabDownloadCheckpoints checkpoints = mock(LabDownloadCheckpoints.class);
  final LabJobs.Progress progress = mock(LabJobs.Progress.class);
  final Fixture fixture =
      new Fixture(
          "provider",
          1,
          2,
          "Home",
          "Away",
          17,
          "League",
          2026L,
          Instant.parse("2026-01-03T15:00:00Z"),
          Instant.parse("2026-01-03T17:00:00Z"));
  final LabDownloadRequest request =
      new LabDownloadRequest(
          List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), null, false);
  final LabDownloadService service =
      new LabDownloadService(
          repo,
          mock(CalcioSource.class),
          new CompetitionMatcher(),
          new FixtureMatcher(),
          mock(OddsPapiClient.class),
          fixtures,
          prices,
          checkpoints);

  @BeforeEach
  void sources() {
    when(repo.competitions())
        .thenReturn(
            List.of(
                new LabCompetition(
                    8,
                    "league",
                    "League",
                    "England",
                    false,
                    List.of("2026"),
                    17,
                    "CATALOGO_COMUNE")));
    when(fixtures.tournament(eq(17), any(), any())).thenReturn(List.of(fixture));
  }

  @Test
  void aFullyImportedFinishedFixtureReusesTheDatabaseWithoutDownloadingAgain() {
    when(checkpoints.status("provider", request.bookmakers())).thenReturn(Optional.of("IMPORTED"));
    service.quotes(request).accept(progress);
    verifyNoInteractions(prices);
  }

  @Test
  void noDataRemainsReusableWithoutDownloadingAgain() {
    when(checkpoints.status("provider", request.bookmakers())).thenReturn(Optional.of("NO_DATA"));
    service.quotes(request).accept(progress);
    verifyNoInteractions(prices);
  }

  @Test
  void downloadedRawIsNotCompleteUntilEveryOutcomeIsPersisted() {
    when(checkpoints.status("provider", request.bookmakers()))
        .thenReturn(Optional.of("DOWNLOADED"));
    when(prices.downloadHistory(fixture, request.bookmakers(), false)).thenReturn(List.of(quote()));
    service.quotes(request).accept(progress);
    var ordered = inOrder(repo, checkpoints);
    ordered.verify(checkpoints).save("provider", request.bookmakers(), "DOWNLOADED");
    ordered.verify(repo).save(any(LabQuote.class));
    ordered.verify(checkpoints).save("provider", request.bookmakers(), "IMPORTED");
  }

  @Test
  void aPersistenceFailureCannotLeaveAFalselyCompleteCheckpoint() {
    when(prices.downloadHistory(fixture, request.bookmakers(), false)).thenReturn(List.of(quote()));
    doThrow(new IllegalStateException("Storage unavailable")).when(repo).save(any(LabQuote.class));
    service.quotes(request).accept(progress);
    verify(checkpoints, never()).save(anyString(), anyList(), eq("IMPORTED"));
    verify(progress).error(contains("Storage unavailable"));
  }

  LabQuote quote() {
    return new LabQuote(
        "provider",
        "pinnacle",
        new it.masterbet.odds.domain.Market(
            "101", "1X2", "1x2", "fulltime", null, false, Map.of("101", "1")),
        "101",
        "1",
        List.of());
  }
}
