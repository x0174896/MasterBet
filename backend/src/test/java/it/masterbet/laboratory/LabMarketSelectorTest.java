package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import it.masterbet.model.domain.GoalProbabilities;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabMarketSelectorTest {
  final Instant kickoff = Instant.parse("2026-06-01T12:00:00Z");
  final LabMarketSelector selector = new LabMarketSelector();
  final GoalProbabilities probabilities = new GoalProbabilities(new double[] {.2, .4, .2, .4});

  LabQuote quote(String team, String price) {
    return new LabQuote(
        "f",
        "test",
        new Market(
            team,
            team,
            team,
            "fulltime",
            new BigDecimal(team.endsWith("1") ? ".5" : "1.5"),
            false,
            Map.of()),
        "under",
        "Under",
        List.of(new PriceUpdate(kickoff.minusSeconds(60), new BigDecimal(price), true)));
  }

  LabMarketLearning.Calibration calibration(String team, String band) {
    return new LabMarketLearning.Calibration(
        team + ":fulltime:Under",
        300,
        200,
        100,
        kickoff.minusSeconds(604800),
        kickoff.minusSeconds(604801),
        0,
        1,
        .5,
        1.,
        .24,
        .24,
        false,
        List.of(new LabMarketLearning.BandCheck(band, 60, 40, .7, .7, true)),
        "Test");
  }

  LabMarketLearning.Snapshot learned(
      List<LabMarketLearning.Calibration> families, List<LabMarketLearning.FamilyRules> rules) {
    return new LabMarketLearning.Snapshot(
        "test", 300, 600, 300, kickoff.minusSeconds(604801), null, families, rules, List.of());
  }

  @Test
  void aRejectedHomeMarketDoesNotBlockAnIndependentlyValidatedAwayAlternative() {
    var rule =
        new LabLearning.Rule("calendar:busy", "Busy", 100, 50, 50, -.1, -.1, .1, .1, -.01, true);
    var snapshot =
        learned(
            List.of(
                calibration("teamtotals-team1", "60-70"),
                calibration("teamtotals-team2", "80-100")),
            List.of(
                new LabMarketLearning.FamilyRules(
                    "teamtotals-team1:fulltime:Under", List.of(rule))));
    var features = new ArrayList<>(Collections.nCopies(32, 0.));
    features.set(21, 2.);
    features.set(24, 2.);
    var selected =
        selector.select(
            probabilities,
            List.of(quote("teamtotals-team1", "3"), quote("teamtotals-team2", "2")),
            kickoff,
            snapshot,
            1,
            "2026",
            features);
    assertEquals("teamtotals-team1", selected.primary().orElseThrow().quote().market().type());
    assertEquals(
        "teamtotals-team2", selected.best().orElseThrow().source().quote().market().type());
    assertEquals(2, selected.controls().size());
    assertEquals(1, selected.controls().stream().filter(LabMarketFrame.Candidate::primary).count());
    assertFalse(selected.policyPicks().containsKey("primary-only"));
    assertNotEquals(
        selected.policyPicks().get("adaptive"), selected.policyPicks().get("calibrated"));
  }

  @Test
  void unconfirmedReplacementsAreShadowedAndCanBePromotedAfterMatchedEvidence() {
    var blocked =
        new LabLearning.Rule("calendar:busy", "Busy", 100, 50, 50, -.1, -.1, .1, .1, -.01, true);
    var cals =
        List.of(
            calibration("teamtotals-team1", "60-70"), calibration("teamtotals-team2", "80-100"));
    var conditions =
        List.of(
            new LabMarketLearning.FamilyRules("teamtotals-team1:fulltime:Under", List.of(blocked)));
    var before =
        new LabMarketLearning.Snapshot(
            "conditional-markets-v2",
            300,
            600,
            300,
            kickoff.minusSeconds(604801),
            null,
            cals,
            conditions,
            List.of(),
            List.of());
    var features = new ArrayList<>(Collections.nCopies(32, 0.));
    features.set(21, 2.);
    features.set(24, 2.);
    var quotes = List.of(quote("teamtotals-team1", "3"), quote("teamtotals-team2", "2"));
    var shadowed = selector.select(probabilities, quotes, kickoff, before, 1, "2026", features);
    assertTrue(shadowed.best().isEmpty());
    assertTrue(shadowed.policyPicks().containsKey("alternative:teamtotals-team2:fulltime:Under"));
    var evidence =
        new LabReplacementEvidence.Check(
            "teamtotals-team2:fulltime:Under", 120, 6, 60, 60, .1, .1, .02, .05, true, "verified");
    var after =
        new LabMarketLearning.Snapshot(
            before.version(),
            300,
            600,
            300,
            before.latestResult(),
            null,
            cals,
            conditions,
            List.of(),
            List.of(evidence));
    assertEquals(
        "teamtotals-team2",
        selector
            .select(probabilities, quotes, kickoff, after, 1, "2026", features)
            .best()
            .orElseThrow()
            .source()
            .quote()
            .market()
            .type());
  }

  @Test
  void aFreshBookmakerQuoteSurvivesWhenTheMoreAttractiveOldPriceIsRejected() {
    var old = quote("teamtotals-team1", "3");
    old =
        new LabQuote(
            old.fixtureId(),
            "old",
            old.market(),
            old.outcomeId(),
            old.outcome(),
            List.of(new PriceUpdate(kickoff.minusSeconds(172800), new BigDecimal("3"), true)));
    var fresh = quote("teamtotals-team1", "2");
    var rule =
        new LabLearning.Rule(
            "quote:older-24h", "Old quote", 100, 50, 50, -.1, -.1, .1, .1, -.01, true);
    var snapshot =
        learned(
            List.of(calibration("teamtotals-team1", "60-70")),
            List.of(
                new LabMarketLearning.FamilyRules(
                    "teamtotals-team1:fulltime:Under", List.of(rule))));
    var selection =
        selector.select(
            probabilities,
            List.of(old, fresh),
            kickoff,
            snapshot,
            1,
            "2026",
            Collections.nCopies(32, 0.));
    assertEquals(2, selection.controls().size());
    assertEquals("old", selection.primary().orElseThrow().quote().bookmaker());
    assertEquals("test", selection.best().orElseThrow().source().quote().bookmaker());
  }

  @Test
  void missingMarketEvidenceAbstainsButStillKeepsControlsForLaterLearning() {
    var result =
        selector.select(
            probabilities,
            List.of(quote("teamtotals-team1", "3")),
            kickoff,
            learned(List.of(), List.of()),
            1,
            "2026",
            Collections.nCopies(32, 0.));
    assertTrue(result.best().isEmpty());
    assertEquals(1, result.controls().size());
    assertEquals(1, result.rejected().get("Mercato/fascia senza verifica sufficiente"));
    assertTrue(result.policyPicks().isEmpty());
  }
}
