package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;

class LabCampaignServiceTest {
  final LabJobs jobs = mock(LabJobs.class);
  final LabDownloadService downloads = mock(LabDownloadService.class);
  final LabReconciliationService reconciliation = mock(LabReconciliationService.class);
  final LabDataValidation validation = mock(LabDataValidation.class);
  final LabSimulationService simulation = mock(LabSimulationService.class);
  final LabCampaignStore store = mock(LabCampaignStore.class);
  final LabRepository repo = mock(LabRepository.class);
  final LabJobs.Progress parent = mock(LabJobs.Progress.class);
  final List<String> events = new ArrayList<>();
  final LabDownloadRequest request =
      new LabDownloadRequest(
          List.of(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null, false);
  final LabCampaignService campaign =
      new LabCampaignService(jobs, downloads, reconciliation, validation, simulation, store, repo);
  String failing;
  List<String> quoteErrors = List.of();

  @BeforeEach
  void configure() {
    when(parent.id()).thenReturn("flow");
    when(repo.json(any())).thenReturn("configuration");
    when(downloads.results(any())).thenReturn(p -> events.add("results"));
    when(downloads.quotes(any())).thenReturn(p -> events.add("quotes"));
    when(reconciliation.reconcile(any())).thenReturn(p -> events.add("reconcile"));
    when(simulation.calculate(any())).thenReturn(p -> events.add("calculate"));
    when(validation.verify(any()))
        .thenAnswer(
            i -> {
              events.add("validate");
              return new LabDataValidation.Report(List.of(8), List.of());
            });
    when(downloads.selected(any()))
        .thenReturn(
            List.of(
                new LabCompetition(
                    8, "league", "League", "England", false, List.of("2026"), 17, "COMUNE")));
    when(repo.matches())
        .thenReturn(
            List.of(
                LabData.match(
                    "calcio:1",
                    8,
                    "2026",
                    Instant.parse("2026-01-03T12:00:00Z"),
                    1,
                    2,
                    0,
                    1,
                    false)));
    doAnswer(
            i -> {
              String kind = i.getArgument(0);
              if (!kind.equals(failing))
                ((Consumer<LabJobs.Progress>) i.getArgument(1))
                    .accept(mock(LabJobs.Progress.class));
              var errors = kind.equals("quotes") ? quoteErrors : List.<String>of();
              when(jobs.technicalErrors(kind))
                  .thenReturn(errors.stream().anyMatch(e -> !e.contains("404")) ? 1 : 0);
              String state =
                  kind.equals(failing) ? "FAILED" : errors.isEmpty() ? "SUCCEEDED" : "PARTIAL";
              return new LabJobs.Status(
                  kind, kind, state, 1, 1, "test", Instant.EPOCH, Instant.EPOCH, errors);
            })
        .when(jobs)
        .phase(anyString(), any(), eq("flow"));
  }

  @Test
  void stagesRunInOrderAndCalculationIsLast() {
    campaign.run(request, null).accept(parent);
    assertEquals(
        List.of("results", "reconcile", "quotes", "reconcile", "validate", "validate", "calculate"),
        events);
    verify(parent).total(6);
  }

  @Test
  void failedResultsCannotStartOddsOrCalculation() {
    failing = "results";
    assertThrows(IllegalStateException.class, () -> campaign.run(request, null).accept(parent));
    assertTrue(events.isEmpty());
    verify(jobs, never()).phase(eq("quotes"), any(), anyString());
    verify(jobs, never()).phase(eq("calculate"), any(), anyString());
  }

  @Test
  void technicalOddsErrorCannotBeMistakenForAnExpectedCoverageGap() {
    quoteErrors = List.of("Archivio illeggibile");
    assertThrows(IllegalStateException.class, () -> campaign.run(request, null).accept(parent));
    verify(jobs, never()).phase(eq("calculate"), any(), anyString());
  }

  @Test
  void missingHistoricalOddsAreReportedAndDoNotPreventValidationOfOtherMatches() {
    quoteErrors = List.of("fixture: archivio quote assente (404)");
    campaign.run(request, null).accept(parent);
    assertEquals("calculate", events.getLast());
    verify(parent).warning(contains("GAP: fase quotes"));
  }

  @Test
  void restartReusesCompletedImportsButRechecksAlignmentAndValidationBeforeCalculation() {
    when(store.complete(anyString(), anyString())).thenReturn(true);
    campaign.run(request, null).accept(parent);
    assertEquals(List.of("reconcile", "validate", "validate", "calculate"), events);
    verify(jobs, never()).phase(eq("results"), any(), anyString());
    verify(jobs, never()).phase(eq("quotes"), any(), anyString());
  }

  @Test
  void incompleteQuotesInvalidateACompletedPhaseCheckpoint() {
    when(store.complete(anyString(), anyString())).thenReturn(true);
    when(store.incompleteQuoteImports()).thenReturn(Set.of("provider"));
    campaign.run(request, null).accept(parent);
    assertEquals(List.of("quotes", "reconcile", "validate", "validate", "calculate"), events);
  }

  @Test
  void aLaterFailedSourceJobInvalidatesTheCompletedSourcePhase() {
    when(store.complete(anyString(), anyString())).thenReturn(true);
    when(store.sourceFailures()).thenReturn(List.of("results: FAILED"));
    campaign.run(request, null).accept(parent);
    assertEquals(List.of("results", "reconcile", "validate", "validate", "calculate"), events);
  }
}
