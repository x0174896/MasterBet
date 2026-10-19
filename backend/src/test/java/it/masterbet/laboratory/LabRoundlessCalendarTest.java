package it.masterbet.laboratory;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.football.infrastructure.OddsPapiFixtureDownloader;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import it.masterbet.odds.infrastructure.OddsPapiHistoricalOddsDownloader;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabRoundlessCalendarTest {
  final LabRepository repo = mock(LabRepository.class);
  final CalcioSource calcio = mock(CalcioSource.class);
  final OddsPapiClient odds = mock(OddsPapiClient.class);
  final LabJobs.Progress progress = mock(LabJobs.Progress.class);
  final LabCompetition mls =
      new LabCompetition(779, "mls", "MLS", "USA", false, List.of("2026"), 242, "COMUNE");
  final LabDownloadService service =
      new LabDownloadService(
          repo,
          calcio,
          new CompetitionMatcher(),
          new FixtureMatcher(),
          odds,
          mock(OddsPapiFixtureDownloader.class),
          mock(OddsPapiHistoricalOddsDownloader.class),
          mock(LabDownloadCheckpoints.class));

  @Test
  void mlsWithoutNumberedRoundsUsesTheRoundlessCalendarAndPreservesFinalScores() throws Exception {
    var mapper = new ObjectMapper();
    var rows =
        mapper.readTree(
            "[{\"id\":123,\"date\":\"2026-03-01T12:00:00Z\",\"home\":{\"id\":1},\"away\":{\"id\":2},\"slug\":\"test\"}]");
    when(repo.competitions()).thenReturn(List.of(mls));
    when(calcio.rounds(mls, "2026", true)).thenReturn(mapper.createArrayNode());
    when(calcio.schedule(mls, "2026", true)).thenReturn(rows);
    var saved =
        LabData.match(
            "calcio:123", 779, "2026", Instant.parse("2026-03-01T12:00:00Z"), 1, 2, 1, 2, false);
    when(calcio.detail(eq(mls), eq("2026"), anyString(), any(), eq(false))).thenReturn(saved);
    service
        .results(
            new LabDownloadRequest(
                List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2), null, false))
        .accept(progress);
    verify(repo).save(saved);
    verifyNoInteractions(odds);
  }

  @Test
  void anEmptyRoundlessCalendarIsReportedInsteadOfInventingResults() throws Exception {
    var empty = new ObjectMapper().createArrayNode();
    when(repo.competitions()).thenReturn(List.of(mls));
    when(calcio.rounds(mls, "2026", true)).thenReturn(empty);
    when(calcio.schedule(mls, "2026", true)).thenReturn(empty);
    service
        .results(
            new LabDownloadRequest(
                List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2), null, false))
        .accept(progress);
    verify(progress).warning(contains("GAP:"));
    verify(calcio, never()).detail(any(), anyString(), anyString(), any(), anyBoolean());
    verifyNoInteractions(odds);
  }
}
