package it.masterbet;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.application.*;
import it.masterbet.betting.domain.*;
import it.masterbet.betting.domain.BettingRound.*;
import it.masterbet.betting.infrastructure.*;
import it.masterbet.football.domain.*;
import it.masterbet.model.domain.*;
import it.masterbet.odds.domain.*;
import it.masterbet.simulation.domain.*;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class BettingRepricingIntegrationTest {
  static ConfigurableApplicationContext context;
  static JdbcTemplate jdbc;
  static BettingRepository rounds;
  static RevealRepository reveals;
  static BettingRepricingService repricing;
  static SnapshotRepository snapshots;
  final Instant cut = Instant.parse("2026-04-29T12:00:00Z");

  @BeforeAll
  static void start() {
    context =
        new SpringApplicationBuilder(MasterBetApplication.class)
            .profiles("test")
            .run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:repricing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    jdbc = context.getBean(JdbcTemplate.class);
    rounds = context.getBean(BettingRepository.class);
    reveals = context.getBean(RevealRepository.class);
    repricing = context.getBean(BettingRepricingService.class);
    snapshots = context.getBean(SnapshotRepository.class);
  }

  @AfterAll
  static void stop() {
    if (context != null) context.close();
  }

  @AfterEach
  void clean() {
    for (var table :
        List.of(
            "betting_reveal_revision",
            "betting_round_revision",
            "betting_reveal",
            "betting_reveal_lock",
            "betting_proposal",
            "betting_round",
            "simulation_snapshot")) jdbc.update("DELETE FROM " + table);
    jdbc.update("UPDATE advance_job SET status='IDLE' WHERE singleton=1");
  }

  Slip slip(String id, Instant cutoff, String price) {
    var q =
        new QuotedOutcome(
            id,
            "unibet",
            new Market("m", "First half result", "1x2", "p1", null, false, Map.of()),
            "1",
            "1",
            new BigDecimal(price),
            cutoff.minusSeconds(1));
    return new Slip(
        id,
        "SINGLE",
        "unibet",
        List.of(new Leg(q, "A", "B", cutoff.plusSeconds(86400), .6)),
        new BigDecimal("0.4"),
        new BigDecimal("2.00"),
        q.price(),
        .6,
        .6 * q.price().doubleValue() - 1,
        "vecchia politica");
  }

  Snapshot source(String id, Instant cutoff, List<Slip> slips, List<PlayedMatch> history) {
    var fixtures =
        slips.stream()
            .map(
                s ->
                    new Fixture(
                        s.id(),
                        1,
                        2,
                        "A",
                        "B",
                        20,
                        "League",
                        138092L,
                        cutoff.plusSeconds(86400),
                        null))
            .toList();
    var data =
        new SimulationDataset(
            id,
            cutoff,
            List.of(),
            history,
            List.of(),
            List.of(),
            fixtures,
            slips.stream().map(s -> s.legs().getFirst().quote()).toList());
    var scales = new double[16];
    Arrays.fill(scales, 1);
    var weights = new PoissonModel(new double[16], scales, new double[4][17]);
    var report =
        new ModelReport(
            "model-" + id,
            "test",
            64,
            16,
            cutoff.minusSeconds(500),
            cutoff.minusSeconds(400),
            cutoff.minusSeconds(300),
            cutoff.minusSeconds(200),
            List.of("x"),
            .6,
            .7,
            .8,
            weights,
            weights,
            "test");
    return new Snapshot(id, cutoff, "hash-" + id, data, report, List.of());
  }

  BettingRound legacy(Snapshot source, List<Slip> slips, BettingConfig config) {
    snapshots.save(source);
    var round =
        new BettingRound(
            source.id(),
            source.datasetSha256(),
            source.model().id(),
            source.dataset().cutoff(),
            source.createdAt(),
            config,
            new BigDecimal("500"),
            new BigDecimal("500"),
            slips,
            slips,
            "five-slips-v2");
    rounds.save(round);
    return round;
  }

  PlayedMatch played(String id, int home) {
    return new PlayedMatch(
        new Fixture(
            id,
            1,
            2,
            "A",
            "B",
            20,
            "League",
            138092L,
            cut.plusSeconds(86400),
            cut.plusSeconds(93600)),
        new Score(id, home, 0, home, 0, false));
  }

  SlipReveal reveal(String scenario, String variant, Slip slip, String status) {
    Score score =
        status.equals("PENDING")
            ? null
            : new Score(
                slip.id(), status.equals("WON") ? 1 : 0, 0, status.equals("WON") ? 2 : 0, 0, false);
    var payout =
        status.equals("PENDING")
            ? null
            : status.equals("WON") ? slip.potentialPayout() : new BigDecimal("0.00");
    return new SlipReveal(
        scenario,
        variant,
        slip.id(),
        cut.plusSeconds(1000000),
        status,
        slip.stake(),
        payout,
        payout == null ? null : payout.subtract(slip.stake()),
        List.of(
            new SlipReveal.Result(
                slip.legs().getFirst(),
                score,
                score == null ? null : status.equals("WON"),
                "official-test-source")));
  }

  @Test
  void repricesBothVariantsAndRevealsAtomicallyWithoutFutureLeakOrNewSelections() throws Exception {
    var choices =
        List.of(slip("won", cut, "3.15"), slip("lost", cut, "2"), slip("pending", cut, "2"));
    var first = source("round7", cut, choices, List.of());
    legacy(first, choices, BettingConfig.defaults());
    // Archivio legacy reale: il campo rimosso resta preservato byte per byte nell'audit.
    String original =
        jdbc.queryForObject("SELECT payload FROM betting_round", String.class)
            .replace(
                "\"initialBankrollUnits\":100", "\"maxRoundUnits\":2,\"initialBankrollUnits\":100");
    jdbc.update("UPDATE betting_round SET payload=?", original);
    var nextChoices = List.of(slip("next", cut.plusSeconds(604800), "1.80"));
    var next =
        source(
            "round8",
            cut.plusSeconds(604800),
            nextChoices,
            List.of(played("won", 1), played("lost", 0)));
    var originalNext = legacy(next, nextChoices, BettingConfig.defaults());
    // Una proposta separata esistente deve essere archiviata prima di essere sostituita.
    rounds.saveProposal(originalNext);
    var oldReveals = new ArrayList<SlipReveal>();
    for (var variant : List.of("cups", "league")) {
      for (int i = 0; i < choices.size(); i++)
        oldReveals.add(
            reveals.save(
                reveal(
                    "round7", variant, choices.get(i), List.of("WON", "LOST", "PENDING").get(i))));
      oldReveals.add(reveals.save(reveal("round8", variant, nextChoices.getFirst(), "WON")));
    }
    rounds.lockReveal("round7");
    rounds.lockReveal("round8");
    var unchanged =
        jdbc.queryForList("SELECT payload FROM simulation_snapshot ORDER BY cutoff", String.class);
    var job = jdbc.queryForMap("SELECT * FROM advance_job");
    assertEquals(2, repricing.reprice());
    assertEquals(0, repricing.reprice());
    assertEquals(
        2, jdbc.queryForObject("SELECT count(*) FROM betting_round_revision", Integer.class));
    assertEquals(
        8, jdbc.queryForObject("SELECT count(*) FROM betting_reveal_revision", Integer.class));
    assertEquals(
        original,
        jdbc.queryForObject(
            "SELECT previous_payload FROM betting_round_revision WHERE scenario_id='round7'",
            String.class));
    assertEquals(
        original,
        jdbc.queryForObject(
            "SELECT payload FROM betting_round WHERE scenario_id='round7'", String.class));
    assertEquals(
        unchanged,
        jdbc.queryForList("SELECT payload FROM simulation_snapshot ORDER BY cutoff", String.class));
    assertEquals(job, jdbc.queryForMap("SELECT * FROM advance_job"));
    assertEquals("round8", snapshots.latest().orElseThrow().id());
    var revised = rounds.find("round8").orElseThrow();
    assertEquals(new BigDecimal("495.01"), revised.openingBankroll());
    assertEquals(revised.openingBankroll(), revised.leagueOpeningBankroll());
    assertEquals(originalNext.frozenAt(), revised.frozenAt());
    assertEquals(originalNext.cups().getFirst().legs(), revised.cups().getFirst().legs());
    assertEquals(new BigDecimal("6.25"), revised.cups().getFirst().stake());
    for (var old : oldReveals) {
      var current = reveals.find(old.scenarioId(), old.variant(), old.slipId()).orElseThrow();
      assertEquals(old.status(), current.status());
      assertEquals(old.results(), current.results());
      assertEquals(old.checkedAt(), current.checkedAt());
      var selected =
          (old.variant().equals("cups")
                  ? rounds.find(old.scenarioId()).orElseThrow().cups()
                  : rounds.find(old.scenarioId()).orElseThrow().league())
              .stream().filter(s -> s.id().equals(old.slipId())).findFirst().orElseThrow();
      assertEquals(old.withAmounts(selected), current);
    }
    var view = context.getBean(BettingService.class).view(next);
    for (var eval : List.of(view.cups(), view.league())) {
      assertEquals(new BigDecimal("500.01"), eval.bankroll());
      assertEquals(new BigDecimal("488.76"), eval.available());
      assertEquals(
          "PENDING", eval.slips().getLast().status()); // L'occhio ha vinto, al taglio resta futuro.
    }
    // Un referto in attesa iniziato prima della revisione viene salvato con gli importi nuovi.
    var oldPending = reveal("round7", "cups", choices.getLast(), "WON");
    assertEquals(new BigDecimal("5.00"), reveals.save(oldPending).profit());
    String archived =
        jdbc.queryForObject(
            "SELECT previous_payload FROM betting_reveal_revision WHERE scenario_id='round7' AND"
                + " model_variant='cups' AND slip_id='pending'",
            String.class);
    assertEquals(
        "PENDING",
        context.getBean(ObjectMapper.class).readValue(archived, SlipReveal.class).status());
  }

  @Test
  void insufficientCapitalInLaterRoundRollsBackEarlierRepricingAndAudit() {
    var choices = List.of(slip("first", cut, "2"));
    legacy(source("round7", cut, choices, List.of()), choices, BettingConfig.defaults());
    var nextChoices = List.of(slip("next", cut.plusSeconds(604800), "1.001"));
    legacy(
        source("round8", cut.plusSeconds(604800), nextChoices, List.of()),
        nextChoices,
        BettingConfig.defaults());
    var originals = rounds.all();
    assertThrows(IllegalStateException.class, repricing::reprice);
    assertEquals(originals, rounds.all());
    assertEquals(
        0, jdbc.queryForObject("SELECT count(*) FROM betting_round_revision", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM betting_proposal", Integer.class));
  }

  @Test
  void mixedPoliciesCannotLeaveLaterOpeningCapitalInconsistent() {
    var choices = List.of(slip("first", cut, "2"));
    legacy(source("round7", cut, choices, List.of()), choices, BettingConfig.defaults());
    var nextChoices = List.of(slip("next", cut.plusSeconds(604800), "2"));
    var next = source("round8", cut.plusSeconds(604800), nextChoices, List.of());
    var original = legacy(next, nextChoices, BettingConfig.defaults());
    rounds.saveProposal(
        new BettingRound(
            original.scenarioId(),
            original.datasetSha256(),
            original.modelId(),
            original.cutoff(),
            original.frozenAt(),
            original.config(),
            original.openingBankroll(),
            original.leagueOpeningBankroll(),
            original.cups(),
            original.league()));
    var before = rounds.all();
    assertThrows(IllegalStateException.class, repricing::reprice);
    assertEquals(before, rounds.all());
    assertEquals(
        0, jdbc.queryForObject("SELECT count(*) FROM betting_round_revision", Integer.class));
  }

  @Test
  void runningAdvanceOrMismatchingSnapshotPreventsMutation() {
    var choices = List.of(slip("first", cut, "2"));
    var src = source("round7", cut, choices, List.of());
    legacy(src, choices, BettingConfig.defaults());
    jdbc.update("UPDATE advance_job SET status='RUNNING'");
    assertThrows(IllegalStateException.class, repricing::reprice);
    jdbc.update("UPDATE advance_job SET status='IDLE'");
    snapshots.save(
        new Snapshot(
            src.id(), src.createdAt(), "wrong", src.dataset(), src.model(), src.predictions()));
    assertThrows(IllegalStateException.class, repricing::reprice);
    assertEquals(
        0, jdbc.queryForObject("SELECT count(*) FROM betting_round_revision", Integer.class));
  }
}
