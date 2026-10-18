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
    var weights = new PoissonModel(new double[] {0}, new double[] {1}, new double[4][2]);
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
}
