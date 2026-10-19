package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.MasterBetApplication;
import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class LabIntegrationTest {
  @Test
  void stalePidCannotKeepAJobLockedAfterRestart() {
    var now = Instant.now().toString();
    jdbc.update(
        "INSERT INTO"
            + " lab_job(id,kind,status,completed,total,message,started_at,updated_at,payload,owner_pid,owner_started)"
            + " VALUES('stale','results','RUNNING',0,0,'test',?,?,'[]',?,?)",
        now,
        now,
        ProcessHandle.current().pid(),
        Instant.EPOCH.toString());
    jdbc.update("INSERT INTO lab_process_lock(kind,job_id) VALUES('results','stale')");
    context.getBean(LabJobs.class).recover();
    assertEquals("INTERRUPTED", context.getBean(LabJobs.class).list().getFirst().status());
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lab_process_lock", Integer.class));
  }

  @TempDir static java.nio.file.Path directory;
  static ConfigurableApplicationContext context;
  static LabRepository repo;
  static LabRunRepository runs;
  static JdbcTemplate jdbc;
  static LabQueryService query;
  static int port;
  final Instant kickoff = Instant.parse("2026-03-07T15:00:00Z");

  @BeforeAll
  static void start() {
    context =
        new SpringApplicationBuilder(MasterBetApplication.class)
            .profiles("test")
            .run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:lab-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--masterbet.data-dir=" + directory);
    repo = context.getBean(LabRepository.class);
    runs = context.getBean(LabRunRepository.class);
    jdbc = context.getBean(JdbcTemplate.class);
    query = context.getBean(LabQueryService.class);
    port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
  }

  @AfterAll
  static void stop() {
    if (context != null) context.close();
  }

  @AfterEach
  void clean() {
    for (String table :
        List.of(
            "lab_process_lock",
            "lab_download_checkpoint",
            "lab_spoiler",
            "lab_bet",
            "lab_step",
            "lab_run",
            "lab_quote",
            "lab_match",
            "lab_competition",
            "lab_revision",
            "lab_job")) jdbc.update("DELETE FROM " + table);
  }

  @Test
  void independentDownloadsCanCoexistButCalculationCannotReadAMovingArchive() throws Exception {
    var jobs = context.getBean(LabJobs.class);
    var release = new java.util.concurrent.CountDownLatch(1);
    var entered = new java.util.concurrent.CountDownLatch(2);
    java.util.function.Consumer<LabJobs.Progress> operation =
        p -> {
          entered.countDown();
          try {
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
              throw new IllegalStateException("Timeout");
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        };
    var results = jobs.start("results", operation);
    var quotes = jobs.start("quotes", operation);
    try {
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      assertThrows(IllegalStateException.class, () -> jobs.start("results", operation));
      assertThrows(IllegalStateException.class, () -> jobs.start("calculate", p -> {}));
    } finally {
      release.countDown();
      jobs.await(results.id());
      jobs.await(quotes.id());
    }
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lab_process_lock", Integer.class));
  }

  @Test
  void verifiedNoDataCheckpointIsSeparatedByBookmakerAndCanBeRefreshed() {
    var checkpoints = context.getBean(LabDownloadCheckpoints.class);
    checkpoints.save("f", List.of("pinnacle", "bet365"), "NO_DATA");
    assertEquals("NO_DATA", checkpoints.status("f", List.of("bet365", "pinnacle")).orElseThrow());
    assertTrue(checkpoints.status("f", List.of("unibet")).isEmpty());
    checkpoints.save("f", List.of("pinnacle", "bet365"), "DOWNLOADED");
    assertEquals(
        "DOWNLOADED", checkpoints.status("f", List.of("pinnacle", "bet365")).orElseThrow());
  }

  LabQuote quote(String id) {
    return new LabQuote(
        id,
        "pinnacle",
        new Market("101", "Risultato finale", "1x2", "fulltime", null, false, Map.of("101", "1")),
        "101",
        "1",
        List.of(new PriceUpdate(kickoff.minusSeconds(86400), new BigDecimal("2.00"), true)));
  }

  @Test
  void restartingAnImportPreservesInPlayConflictsWithoutDuplicatingOrLosingPreMatchOdds() {
    repo.save(LabData.match("calcio:1", 384, "2025/2026", kickoff, 1, 2, 1, 2, false));
    var original = quote("calcio:1");
    var updates = new ArrayList<>(original.updates());
    updates.add(new PriceUpdate(kickoff.plusSeconds(30), new BigDecimal("5.1"), true));
    updates.add(new PriceUpdate(kickoff.plusSeconds(30), new BigDecimal("5.2"), true));
    var history =
        new LabQuote(
            original.fixtureId(),
            original.bookmaker(),
            original.market(),
            original.outcomeId(),
            original.outcome(),
            updates);
    repo.save(history);
    repo.save(history);
    assertEquals(1, repo.quotes("calcio:1").size());
    var stored = repo.quotes("calcio:1").getFirst();
    assertEquals(3, stored.updates().size());
    assertEquals(0, stored.closing(kickoff).orElseThrow().price().compareTo(new BigDecimal("2")));
  }

  LabBet bet(String id, String run, LabMatch match, Instant at) {
    return new LabBet(
        id,
        run,
        match.fixture().id(),
        384,
        "Serie A",
        match.season(),
        match.fixture().home(),
        match.fixture().away(),
        match.fixture().kickoff(),
        at,
        1,
        false,
        quote(match.fixture().id()).closing(kickoff).orElseThrow(),
        .7,
        .4,
        new BigDecimal("5.00"),
        new BigDecimal("5.00"),
        List.of("test"),
        "prefix",
        List.of(),
        "test");
  }

  @Test
  void quoteFirstIsReconciledWithoutDuplicationAndSourceIsRevisioned() {
    var official = LabData.match("calcio:123", 384, "2025/2026", kickoff, 1, 2, 1, 2, false);
    var stub =
        new LabMatch(
            new it.masterbet.football.domain.Fixture(
                "odds:provider", 9, 10, "Team 1", "Team 2", 384, "Serie A", 2L, kickoff, null),
            "ODDSPAPI:2",
            "",
            false,
            null,
            "provider",
            "provider",
            "source");
    repo.save(stub);
    repo.save(quote(stub.fixture().id()));
    repo.save(official);
    assertEquals(1, repo.matches().size());
    assertEquals("provider", repo.match("calcio:123").orElseThrow().oddsFixtureId());
    assertEquals(Set.of("calcio:123"), repo.quotedFixtures());
    assertEquals(1, repo.quotes("calcio:123").size());
    repo.save(quote("calcio:123"));
    assertEquals(1, repo.quotes("calcio:123").getFirst().updates().size());
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM lab_revision", Integer.class) > 0);
  }

  @Test
  void netStartsAtZeroAndEverySimultaneousSingleIsAPointWhileSpoilerDoesNotSettle() {
    var at = kickoff.minusSeconds(86400 * 3);
    String id = "chart";
    runs.create(
        id,
        new LabRunConfig(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), List.of(), null, "HISTORICAL"),
        "hash");
    var a = LabData.match("a", 384, "2025/2026", kickoff, 1, 2, 0, 0, false); // draw -> loss
    var b = LabData.match("b", 384, "2025/2026", kickoff, 3, 4, 1, 2, false);
    var c = LabData.match("c", 384, "2025/2026", kickoff.plusSeconds(86400), 5, 6, 1, 2, false);
    repo.save(a);
    repo.save(b);
    repo.save(c);
    var bets = List.of(bet("1", id, a, at), bet("2", id, b, at), bet("3", id, c, at));
    runs.step(
        new LabStep(id, 1, at, false, 30, 6, .5, null, List.of(), Map.of(), "hash", 3, 0, "test"),
        bets);
    runs.finish(id, "COMPLETED", "test");
    var pending = query.workspace(id, at, false, 0, 0);
    assertEquals(3, pending.totalBets());
    assertEquals(0, pending.settled());
    assertEquals(1, pending.chart().size());
    assertEquals("WON", query.reveal(id, "3"));
    assertEquals("WON", query.workspace(id, at, false, 0, 0).bets().get(2).spoiler());
    assertEquals(0, query.workspace(id, at, false, 0, 0).net().signum());
    var after = query.workspace(id, kickoff.plusSeconds(7300), false, 0, 0);
    assertEquals(2, after.settled());
    assertEquals(3, after.chart().size());
    assertEquals(new BigDecimal("-5.00"), after.chart().get(1).cumulative());
    assertEquals(new BigDecimal("0.00"), after.chart().get(2).cumulative());
    assertEquals(after.chart().get(1).settledAt(), after.chart().get(2).settledAt());
    assertEquals("PENDING", after.bets().get(2).status());
    assertEquals(at, query.workspace(id, at.minusSeconds(1), false, 0, 0).cursor());
  }

  @Test
  void engineTrainsBothModesAcrossLeaguesAndFutureResultsCannotChangePastModels() {
    var start = Instant.parse("2026-01-01T15:00:00Z");
    for (int league : List.of(384, 82)) {
      repo.save(
          new LabCompetition(
              league,
              "league-" + league,
              "League " + league,
              "Country",
              false,
              List.of("2026"),
              league,
              "COMUNE"));
      for (var m : LabData.season(league, "2026", start, 21)) {
        repo.save(m);
        repo.save(quote(m.fixture().id()));
      }
    }
    var config =
        new LabRunConfig(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 20), List.of(), null, "HISTORICAL");
    var jobs = context.getBean(LabJobs.class);
    var simulation = context.getBean(LabSimulationService.class);
    assertEquals("SUCCEEDED", jobs.blocking("calculate", simulation.calculate(config)).status());
    var first = runs.runs().getFirst();
    var firstSteps = runs.steps(first.id());
    assertEquals(6, firstSteps.size());
    assertTrue(firstSteps.getFirst().trainingRows() >= 25);
    assertEquals(2, firstSteps.getFirst().rowsPerCompetition().size());
    assertTrue(runs.bets(first.id()).stream().anyMatch(LabBet::includeCupScores));
    assertTrue(runs.bets(first.id()).stream().anyMatch(b -> !b.includeCupScores()));
    for (var b : runs.bets(first.id())) {
      var frozen =
          firstSteps.stream()
              .filter(s -> s.number() == b.step() && s.includeCupScores() == b.includeCupScores())
              .findFirst()
              .orElseThrow();
      var probability =
          new it.masterbet.model.domain.GoalProbabilities(
                  frozen
                      .model()
                      .predict(b.features().stream().mapToDouble(Double::doubleValue).toArray()))
              .probability(b.quote())
              .orElseThrow();
      assertEquals(b.probability(), probability, 1e-12);
    }
    assertEquals(
        runs.bets(first.id()).size(),
        runs.bets(first.id()).stream()
            .map(b -> b.fixtureId() + ":" + b.includeCupScores())
            .distinct()
            .count());
    // A match whose result is after the first weekly cut must not alter that model/hash.
    var future =
        repo.matches().stream()
            .filter(m -> m.fixture().kickoff().isAfter(Instant.parse("2026-03-10T00:00:00Z")))
            .findFirst()
            .orElseThrow();
    repo.save(
        new LabMatch(
            future.fixture(),
            future.season(),
            future.round(),
            false,
            new it.masterbet.football.domain.Score(future.fixture().id(), 10, 9, 20, 18, false),
            future.sourceUrl(),
            null,
            "changed"));
    assertEquals("SUCCEEDED", jobs.blocking("calculate", simulation.calculate(config)).status());
    var second = runs.runs().getFirst();
    var secondStep = runs.steps(second.id()).getFirst();
    assertEquals(firstSteps.getFirst().trainingHash(), secondStep.trainingHash());
    assertArrayEquals(firstSteps.getFirst().model().weights()[0], secondStep.model().weights()[0]);
    assertNotEquals(first.dataHash(), second.dataHash());
  }

  @Test
  void adminRequiresLoginAndCsrfHeaderAndPasswordIsNeverReturned() throws Exception {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    assertEquals(401, send(client, "/admin/jobs", "GET", null, false).statusCode());
    var foreignOrigin =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/lab/admin/login"))
            .header("Content-Type", "application/json")
            .header("X-MasterBet-Admin", "1")
            .header("Origin", "http://attacker.example")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"password\":\"test-private-password\",\"setup\":false}"))
            .build();
    assertEquals(
        403, client.send(foreignOrigin, HttpResponse.BodyHandlers.ofString()).statusCode());
    assertEquals(
        403,
        send(
                client,
                "/admin/login",
                "POST",
                "{\"password\":\"test-private-password\",\"setup\":true}",
                false)
            .statusCode());
    var login =
        send(
            client,
            "/admin/login",
            "POST",
            "{\"password\":\"test-private-password\",\"setup\":true}",
            true);
    assertEquals(200, login.statusCode());
    assertFalse(login.body().contains("password"));
    assertTrue(login.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly"));
    assertEquals(200, send(client, "/admin/jobs", "GET", null, false).statusCode());
    assertEquals(403, send(client, "/admin/calculate", "POST", "{}", false).statusCode());
    assertEquals(200, send(client, "/admin/logout", "POST", "{}", true).statusCode());
    assertEquals(401, send(client, "/admin/jobs", "GET", null, false).statusCode());
  }

  private HttpResponse<String> send(
      HttpClient client, String path, String method, String body, boolean header) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/lab" + path))
            .header("Content-Type", "application/json")
            .header("Origin", "http://127.0.0.1:" + port);
    if (header) b.header("X-MasterBet-Admin", "1");
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }
}
