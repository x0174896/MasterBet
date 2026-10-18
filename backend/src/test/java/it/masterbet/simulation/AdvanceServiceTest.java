package it.masterbet.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.application.BettingService;
import it.masterbet.betting.domain.BettingConfig;
import it.masterbet.model.application.ModelTrainingService;
import it.masterbet.simulation.application.*;
import it.masterbet.simulation.domain.*;
import it.masterbet.simulation.infrastructure.AdvanceJobRepository;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AdvanceServiceTest {
  @Test
  void freezesBeforeDownloadingAndDoesNotPublishOnFailure() throws Exception {
    var query = mock(ScenarioQueryService.class);
    var download = mock(NextStepDownloader.class);
    var betting = mock(BettingService.class);
    var publisher = mock(AdvancePublisher.class);
    var jobs = mock(AdvanceJobRepository.class);
    var training = mock(ModelTrainingService.class);
    var snapshot = new Snapshot("source", Instant.EPOCH, "hash", null, null, java.util.List.of());
    when(query.load()).thenReturn(snapshot);
    when(jobs.status()).thenReturn(new AdvanceJobRepository.Status(null, null, "IDLE", "", ""));
    when(jobs.claim(anyString(), eq("source"))).thenReturn(true);
    when(download.download(eq(snapshot), any()))
        .thenThrow(new IllegalArgumentException("Dati incompleti"));
    var finished = new CountDownLatch(1);
    doAnswer(
            i -> {
              finished.countDown();
              return null;
            })
        .when(jobs)
        .update(anyString(), eq("FAILED"), anyString());
    var service =
        new AdvanceService(
            query,
            download,
            new DatasetValidator(),
            training,
            mock(PredictionService.class),
            betting,
            publisher,
            jobs,
            new ObjectMapper());
    service.start("source", BettingConfig.defaults());
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    var order = inOrder(betting, download);
    order.verify(betting).freeze(snapshot, BettingConfig.defaults());
    order.verify(download).download(eq(snapshot), any());
    verifyNoInteractions(publisher, training);
  }

  @Test
  void runningJobDoesNotStartSecondDownload() {
    var query = mock(ScenarioQueryService.class);
    var download = mock(NextStepDownloader.class);
    var jobs = mock(AdvanceJobRepository.class);
    var running = new AdvanceJobRepository.Status("job", "source", "RUNNING", "download", "");
    when(jobs.status()).thenReturn(running);
    var service = new AdvanceService(query, download, null, null, null, null, null, jobs, null);
    assertEquals(running, service.start("source", BettingConfig.defaults()));
    verifyNoInteractions(query, download);
  }
}
