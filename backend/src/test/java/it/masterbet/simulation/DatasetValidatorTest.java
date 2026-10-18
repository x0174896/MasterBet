package it.masterbet.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import it.masterbet.betting.application.BettingService;
import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.BettingRepository;
import it.masterbet.football.domain.*;
import it.masterbet.model.application.ModelTrainingService;
import it.masterbet.model.domain.*;
import it.masterbet.odds.domain.*;
import it.masterbet.simulation.application.PredictionService;
import it.masterbet.simulation.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatasetValidatorTest {
  final Instant cut = Instant.parse("2026-04-29T12:00:00Z");
  final DatasetValidator validator = new DatasetValidator();

  SimulationDataset valid() {
    List<PlayedMatch> past = new ArrayList<>(), train = new ArrayList<>();
    List<Fixture> targets = new ArrayList<>();
    List<QuotedOutcome> quotes = new ArrayList<>();
    for (int i = 0; i < 47; i++) {
      var f =
          new Fixture(
              "h" + i,
              1,
              2,
              "A",
              "B",
              20,
              "League",
              138092L,
              cut.minusSeconds(20000 + i * 10000),
              cut.minusSeconds(10000 + i * 10000));
      past.add(new PlayedMatch(f, new Score(f.id(), 0, 0, 1, 0, false)));
    }
    for (int i = 0; i < 80; i++) {
      var t = Instant.parse("2025-08-01T00:00:00Z").plusSeconds(i * 86400);
      var f = new Fixture("tr" + i, 1, 2, "A", "B", 20, "League", 127337L, t, t.plusSeconds(7200));
      train.add(new PlayedMatch(f, new Score(f.id(), 0, 0, 1, 0, false)));
    }
    for (int i = 0; i < 8; i++) {
      var f =
          new Fixture(
              "t" + i,
              1,
              2,
              "A",
              "B",
              20,
              "League",
              138092L,
              cut.plusSeconds(86400 + i * 3600),
              null);
      targets.add(f);
      for (String family :
          List.of(
              "1x2/fulltime",
              "bothteamsscore/fulltime",
              "totals/fulltime",
              "totals/p1",
              "totals/p2",
              "halftime-fulltime/fulltime")) {
        String[] parts = family.split("/");
        int count = family.startsWith("1x2") ? 3 : family.startsWith("halftime") ? 9 : 2;
        Map<String, String> outcomes = new HashMap<>();
        for (int j = 0; j < count; j++) outcomes.put("o" + j, "Outcome " + j);
        var market =
            new Market(family, family, parts[0], parts[1], new BigDecimal("2.5"), false, outcomes);
        for (var o : outcomes.entrySet())
          quotes.add(
              new QuotedOutcome(
                  f.id(),
                  "book",
                  market,
                  o.getKey(),
                  o.getValue(),
                  new BigDecimal("2.1"),
                  cut.minusSeconds(5)));
      }
    }
    return new SimulationDataset("test", cut, train, past, List.of(), List.of(), targets, quotes);
  }

  @Test
  void completeAsOfDatasetPasses() {
    assertDoesNotThrow(() -> validator.validate(valid()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1x2/fulltime",
        "bothteamsscore/fulltime",
        "totals/fulltime",
        "totals/p1",
        "totals/p2",
        "halftime-fulltime/fulltime"
      })
  void unavailableMarketFamilyDoesNotInvalidateOtherQuotes(String family) {
    var d = valid();
    d.quotes().removeIf(q -> (q.market().type() + "/" + q.market().period()).equals(family));
    var available = List.copyOf(d.quotes());
    assertDoesNotThrow(() -> validator.validate(d));
    assertEquals(available, d.quotes());
  }

  @Test
  void individualOutcomeAndUnquotedFixtureRemainUsableWithoutInventingCoverage() {
    var d = valid();
    var only = d.quotes().getFirst();
    d.quotes().clear();
    d.quotes().add(only);
    assertDoesNotThrow(() -> validator.validate(d));
    assertEquals(List.of(only), d.quotes());
    assertEquals(8, d.targets().size());
    d.quotes().clear();
    assertDoesNotThrow(() -> validator.validate(d));
    assertTrue(d.quotes().isEmpty());
  }

  @Test
  void unavailableFamiliesStillAllowFiveRealSlipsForBothModels() {
    var d = valid();
    d.quotes().clear();
    var market =
        new Market(
            "btts", "Both teams to score", "bothteamsscore", "fulltime", BigDecimal.ZERO,
            false, Map.of("yes", "Yes", "no", "No"));
    // Sei partite con un solo esito Unibet, una solo Pinnacle e una priva di quote.
    for (int i = 0; i < 7; i++)
      d.quotes()
          .add(
              new QuotedOutcome(
                  d.targets().get(i).id(), i < 6 ? "unibet" : "pinnacle", market,
                  "no", "No", new BigDecimal("2.1"), cut.minusSeconds(1)));
    validator.validate(d);
    var features = new FeatureBuilder();
    var past = new ArrayList<>(d.trainingHistory());
    past.addAll(d.leagueHistory());
    var model = new ModelTrainingService(features, new PoissonTrainer()).train(past, d.calendar());
    var predictions = new PredictionService(features);
    var snapshot =
        new Snapshot(d.id(), cut, "sparse-quotes", d, model, predictions.predict(d, model));
    var repository = mock(BettingRepository.class);
    var betting =
        new BettingService(
            repository, new SlipSelector(new ProfitTargetStaking()),
            predictions, new BettingEvaluator(new MarketSettlement()));
    var round = betting.prepare(snapshot, BettingConfig.defaults());
    for (var slips : List.of(round.cups(), round.league())) {
      assertEquals(5, slips.size());
      var legs = slips.stream().flatMap(s -> s.legs().stream()).toList();
      assertEquals(6, legs.size());
      assertEquals(6, legs.stream().map(l -> l.quote().fixtureId()).distinct().count());
      assertTrue(legs.stream().allMatch(l -> d.quotes().contains(l.quote())));
      assertTrue(legs.stream().allMatch(l -> l.quote().bookmaker().equals("unibet")));
    }
  }

  @Test
  void invalidPriceAndUnknownFixtureStillFailWithPartialCoverage() {
    var d = valid();
    var q = d.quotes().getFirst();
    d.quotes().clear();
    d.quotes()
        .add(
            new QuotedOutcome(
                q.fixtureId(), q.bookmaker(), q.market(), q.outcomeId(), q.outcome(),
                BigDecimal.ONE, q.observedAt()));
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
    d.quotes()
        .set(
            0,
            new QuotedOutcome(
                "unknown", q.bookmaker(), q.market(), q.outcomeId(), q.outcome(),
                q.price(), q.observedAt()));
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
  }

  @Test
  void duplicateQuoteFails() {
    var d = valid();
    d.quotes().add(d.quotes().getFirst());
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
  }

  @Test
  void futureQuoteFails() {
    var d = valid();
    var q = d.quotes().getFirst();
    d.quotes()
        .set(
            0,
            new QuotedOutcome(
                q.fixtureId(),
                q.bookmaker(),
                q.market(),
                q.outcomeId(),
                q.outcome(),
                q.price(),
                cut.plusSeconds(1)));
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
  }

  @Test
  void duplicateTrainingResultFails() {
    var d = valid();
    d.trainingHistory().set(1, d.trainingHistory().getFirst());
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
  }

  @Test
  void targetResultMetadataFails() {
    var d = valid();
    var f = d.targets().getFirst();
    d.targets()
        .set(
            0,
            new Fixture(
                f.id(),
                1,
                2,
                "A",
                "B",
                20,
                "League",
                138092L,
                f.kickoff(),
                f.kickoff().plusSeconds(7200)));
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d));
  }
}
