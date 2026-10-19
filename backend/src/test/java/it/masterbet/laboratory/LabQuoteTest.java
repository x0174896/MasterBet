package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.laboratory.domain.*;
import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LabQuoteTest {
  @Test
  void equivalentDecimalRepresentationsAreOneUpdate() {
    var first = q(List.of(new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("2.00"), true)));
    var second = q(List.of(new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("2.0"), true)));
    assertEquals(1, first.merge(second).updates().size());
  }

  @Test
  void operationalModeRejectsPricesPublishedAfterNow() {
    var history =
        q(
            List.of(
                new PriceUpdate(kickoff.minusSeconds(86400), new BigDecimal("2"), true),
                new PriceUpdate(kickoff.minusSeconds(3600), new BigDecimal("3"), true)));
    assertEquals(
        new BigDecimal("2"),
        history.asOf(kickoff.minusSeconds(7200), kickoff).orElseThrow().price());
    assertEquals(new BigDecimal("3"), history.closing(kickoff).orElseThrow().price());
  }

  final Instant kickoff = Instant.parse("2026-05-23T15:00:00Z");
  final Market market = new Market("1", "1X2", "1x2", "fulltime", null, false, Map.of("1", "1"));

  LabQuote q(List<PriceUpdate> updates) {
    return new LabQuote("f", "pinnacle", market, "1", "1", updates);
  }

  @Test
  void allowsWednesdayOddsForMondayWeekButExcludesInPlay() {
    var quote =
        q(
            List.of(
                new PriceUpdate(kickoff.minusSeconds(200000), new BigDecimal("2"), true),
                new PriceUpdate(kickoff.plusSeconds(1), new BigDecimal("4"), true)));
    assertEquals(new BigDecimal("2"), quote.closing(kickoff).orElseThrow().price());
  }

  @Test
  void suspensionCannotFallBackToOlderWinningPrice() {
    assertTrue(
        q(List.of(
                new PriceUpdate(kickoff.minusSeconds(10), new BigDecimal("2"), true),
                new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("2"), false)))
            .closing(kickoff)
            .isEmpty());
  }

  @Test
  void repeatedDownloadsPreserveConflictsButNeverSelectAnAmbiguousLastPrice() {
    var update = new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("2"), true);
    assertEquals(1, q(List.of(update)).merge(q(List.of(update))).updates().size());
    var ambiguous =
        q(List.of(update))
            .merge(q(List.of(new PriceUpdate(update.timestamp(), new BigDecimal("3"), true))));
    assertEquals(2, ambiguous.updates().size());
    assertEquals(2, ambiguous.merge(ambiguous).updates().size());
    assertTrue(ambiguous.closing(kickoff).isEmpty());
  }

  @Test
  void inPlayConflictsCannotInvalidateOrChangeThePreMatchPrice() {
    var pre = new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("2"), true);
    var first =
        q(List.of(pre, new PriceUpdate(kickoff.plusSeconds(60), new BigDecimal("5.1"), true)));
    var second =
        q(List.of(pre, new PriceUpdate(kickoff.plusSeconds(60), new BigDecimal("5.2"), true)));
    var merged = first.merge(second);
    assertEquals(3, merged.updates().size());
    assertEquals(new BigDecimal("2"), merged.closing(kickoff).orElseThrow().price());
  }

  @Test
  void aLaterUnambiguousUpdateResolvesTheCurrentCutButNotAnEarlierAmbiguousCut() {
    var at = kickoff.minusSeconds(120);
    var history =
        q(
            List.of(
                new PriceUpdate(at, new BigDecimal("2"), true),
                new PriceUpdate(at, new BigDecimal("3"), true),
                new PriceUpdate(kickoff.minusSeconds(1), new BigDecimal("4"), true)));
    assertTrue(history.asOf(kickoff.minusSeconds(60), kickoff).isEmpty());
    assertEquals(new BigDecimal("4"), history.closing(kickoff).orElseThrow().price());
  }

  @Test
  void equivalentDecimalPricesDoNotCreateAmbiguityInTheSelector() {
    var at = kickoff.minusSeconds(1);
    var history =
        q(
            List.of(
                new PriceUpdate(at, new BigDecimal("2.00"), true),
                new PriceUpdate(at, new BigDecimal("2.0"), true)));
    assertEquals(0, history.closing(kickoff).orElseThrow().price().compareTo(new BigDecimal("2")));
  }
}
