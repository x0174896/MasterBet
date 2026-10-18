package it.masterbet;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.football.domain.Fixture;
import it.masterbet.football.infrastructure.FootballRepository;
import it.masterbet.model.domain.*;
import it.masterbet.simulation.application.SnapshotPublisher;
import it.masterbet.simulation.domain.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistenceApiIntegrationTest {
  static ConfigurableApplicationContext context;
  static int port;
  static JdbcTemplate jdbc;
  static FootballRepository repository;

  @BeforeAll
  static void start() {
    context =
        new SpringApplicationBuilder(MasterBetApplication.class)
            .profiles("test")
            .run("--server.port=0");
    port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    jdbc = context.getBean(JdbcTemplate.class);
    repository = context.getBean(FootballRepository.class);
  }

  @AfterAll
  static void stop() {
    if (context != null) context.close();
  }

  @AfterEach
  void clean() {
    jdbc.update("DELETE FROM betting_reveal");
    jdbc.update("DELETE FROM betting_reveal_lock");
    jdbc.update("DELETE FROM betting_proposal");
    jdbc.update("DELETE FROM betting_round");
    jdbc.update("UPDATE advance_job SET status='IDLE' WHERE singleton=1");
    jdbc.update("DELETE FROM simulation_snapshot");
    jdbc.update("DELETE FROM trained_model");
    jdbc.update("DELETE FROM match_result");
    jdbc.update("DELETE FROM source_fixture");
  }

  @Test
  void migrationsAndResultPersistenceWork() {
    var played = TestMatches.played("test", "2026-04-25T12:00:00Z", 2, 1);
    repository.saveResult(played);
    repository.saveResult(played);
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM match_result", Integer.class));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("UPDATE match_result SET home_ht=10 WHERE fixture_id='test'"));
  }

  @Test
  void healthAndMissingSnapshotUseExplicitHttpContracts() throws Exception {
    var client = HttpClient.newHttpClient();
    var health =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health")).build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, health.statusCode());
    assertTrue(health.body().contains("UP"));
    var missing =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/scenario"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(404, missing.statusCode());
    assertFalse(missing.body().contains("password"));
  }

  Snapshot snapshot(String modelId) {
    var cut = Instant.parse("2026-04-29T12:00:00Z");
    var history = TestMatches.played("past", "2026-04-25T12:00:00Z", 2, 1);
    var target =
        new Fixture("future", 1, 2, "A", "B", 20, "League", 138092L, cut.plusSeconds(86400), null);
    var data =
        new SimulationDataset(
            "eliteserien-2026-round7",
            cut,
            List.of(),
            List.of(history),
            List.of(history.fixture()),
            List.of(),
            List.of(target),
            List.of());
    var scales = new double[16];
    java.util.Arrays.fill(scales, 1);
    var weights = new PoissonModel(new double[16], scales, new double[4][17]);
    var report =
        new ModelReport(
            modelId,
            "test",
            64,
            16,
            cut.minusSeconds(86400 * 200),
            cut.minusSeconds(86400 * 100),
            cut.minusSeconds(86400 * 99),
            cut.minusSeconds(86400 * 90),
            List.of("x"),
            .6,
            .7,
            .8,
            weights,
            weights,
            "test limit");
    return new Snapshot(data.id(), Instant.now(), "test-hash", data, report, List.of());
  }

  @Test
  void publishedSnapshotRoundTripsThroughDatabaseAndHttp() throws Exception {
    context.getBean(SnapshotPublisher.class).publish(snapshot("test-model"));
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/scenario"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("test-hash"));
    assertTrue(response.body().contains("2026-04-29T12:00:00Z"));
    assertFalse(response.body().contains("weights"));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM match_result", Integer.class));
  }

  @Test
  void failedPublicationRollsBackAllTables() {
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> context.getBean(SnapshotPublisher.class).publish(snapshot(null)));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM source_fixture", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM match_result", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM simulation_snapshot", Integer.class));
  }

  @Test
  void frozenBetsAreImmutableAndJobClaimIsExclusive() {
    var s = snapshot("test-model");
    context.getBean(SnapshotPublisher.class).publish(s);
    var service = context.getBean(it.masterbet.betting.application.BettingService.class);
    service.freeze(s, it.masterbet.betting.domain.BettingConfig.defaults());
    service.freeze(s, it.masterbet.betting.domain.BettingConfig.defaults());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM betting_round", Integer.class));
    var jobs = context.getBean(it.masterbet.simulation.infrastructure.AdvanceJobRepository.class);
    assertTrue(jobs.claim("one", s.id()));
    assertFalse(jobs.claim("two", s.id()));
    jobs.update("one", "FAILED", "test");
    assertTrue(jobs.claim("two", s.id()));
  }

  @Test
  void staleAdvanceReturnsConflictWithoutDownload() throws Exception {
    context.getBean(SnapshotPublisher.class).publish(snapshot("test-model"));
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/advance-job"))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"expectedScenarioId\":\"stale\",\"config\":{\"unitValue\":5,\"initialBankrollUnits\":100,\"maxRoundUnits\":2}}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertEquals(409, response.statusCode());
    assertEquals(
        "IDLE",
        context
            .getBean(it.masterbet.simulation.infrastructure.AdvanceJobRepository.class)
            .status()
            .status());
  }

  @Test
  void currentProposalUpgradePreservesOriginalAndCannotRewriteRevealedChoices() {
    var source = snapshot("test-model");
    context.getBean(SnapshotPublisher.class).publish(source);
    var bets = context.getBean(it.masterbet.betting.application.BettingService.class);
    var repo = context.getBean(it.masterbet.betting.infrastructure.BettingRepository.class);
    var workspace = context.getBean(it.masterbet.betting.application.BettingWorkspaceService.class);
    var fresh = bets.prepare(source, it.masterbet.betting.domain.BettingConfig.defaults());
    var legacy =
        new it.masterbet.betting.domain.BettingRound(
            fresh.scenarioId(),
            fresh.datasetSha256(),
            fresh.modelId(),
            fresh.cutoff(),
            fresh.frozenAt(),
            fresh.config(),
            fresh.openingBankroll(),
            fresh.leagueOpeningBankroll(),
            fresh.cups(),
            fresh.league(),
            null);
    repo.save(legacy);
    var original = jdbc.queryForObject("SELECT payload FROM betting_round", String.class);
    assertTrue(workspace.save(source.id()).frozen());
    assertEquals("five-slips-v2", repo.find(source.id()).orElseThrow().policyVersion());
    workspace.save(source.id());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM betting_proposal", Integer.class));
    assertEquals(original, jdbc.queryForObject("SELECT payload FROM betting_round", String.class));
    jdbc.update("DELETE FROM betting_proposal");
    repo.lockReveal(source.id());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> workspace.save(source.id()));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM betting_proposal", Integer.class));
  }

  @Test
  void saveApiIsIdempotentAndRejectsStaleScenario() throws Exception {
    context.getBean(SnapshotPublisher.class).publish(snapshot("test-model"));
    for (String id : List.of("stale", "eliteserien-2026-round7", "eliteserien-2026-round7")) {
      var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(
                          URI.create("http://127.0.0.1:" + port + "/api/betting/save"))
                      .header("Content-Type", "application/json")
                      .POST(
                          HttpRequest.BodyPublishers.ofString(
                              "{\"expectedScenarioId\":\"" + id + "\"}"))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(id.equals("stale") ? 409 : 200, response.statusCode());
    }
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM betting_round", Integer.class));
  }

  @Test
  void invalidJobConfigurationReturnsBadRequestWithoutStartingWork() throws Exception {
    for (var body :
        List.of(
            "{",
            "{\"expectedScenarioId\":\"step\",\"config\":{\"unitValue\":0,\"initialBankrollUnits\":100,\"maxRoundUnits\":2}}")) {
      var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(
                          URI.create("http://127.0.0.1:" + port + "/api/advance-job"))
                      .header("Content-Type", "application/json")
                      .POST(HttpRequest.BodyPublishers.ofString(body))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(400, response.statusCode());
      assertFalse(response.body().contains("password"));
    }
    assertEquals(
        "IDLE",
        context
            .getBean(it.masterbet.simulation.infrastructure.AdvanceJobRepository.class)
            .status()
            .status());
  }

  @Test
  void consultedResultsPersistWithoutEnteringTheSnapshotOrMatchResults() {
    var source = snapshot("test-model");
    context.getBean(SnapshotPublisher.class).publish(source);
    context
        .getBean(it.masterbet.betting.application.BettingService.class)
        .freeze(source, it.masterbet.betting.domain.BettingConfig.defaults());
    var before = jdbc.queryForObject("SELECT payload FROM simulation_snapshot", String.class);
    var repo = context.getBean(it.masterbet.betting.infrastructure.RevealRepository.class);
    var reveal =
        new it.masterbet.betting.domain.SlipReveal(
            source.id(),
            "cups",
            "S1",
            Instant.now(),
            "LOST",
            new java.math.BigDecimal("2.00"),
            new java.math.BigDecimal("0.00"),
            new java.math.BigDecimal("-2.00"),
            List.of());
    repo.save(reveal);
    assertEquals(reveal, repo.find(source.id(), "cups", "S1").orElseThrow());
    assertEquals(
        before, jdbc.queryForObject("SELECT payload FROM simulation_snapshot", String.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM match_result", Integer.class));
    assertTrue(repo.find(source.id(), "league", "S1").isEmpty());
  }
}
