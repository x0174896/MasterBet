package it.masterbet.odds;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.odds.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsOfPriceSelectorTest {
  private final AsOfPriceSelector selector = new AsOfPriceSelector();
  private final Instant cut = Instant.parse("2026-04-29T12:00:00Z"), start = cut.plusSeconds(86400);

  private PriceUpdate p(int seconds, String price, boolean active) {
    return new PriceUpdate(cut.plusSeconds(seconds), new BigDecimal(price), active);
  }

  @Test
  void choosesLastBeforeCutoffRegardlessOfInputOrder() {
    assertEquals(
        new BigDecimal("2.15"),
        selector
            .select(List.of(p(1, "9", true), p(-1, "2.15", true), p(-100, "2", true)), cut, start)
            .orElseThrow()
            .price());
  }

  @Test
  void doesNotResurrectSuspendedQuote() {
    assertTrue(
        selector.select(List.of(p(-100, "2", true), p(-1, "2", false)), cut, start).isEmpty());
  }

  @Test
  void futureSuspensionDoesNotAlterPast() {
    assertTrue(
        selector.select(List.of(p(-1, "2", true), p(1, "2", false)), cut, start).isPresent());
  }

  @Test
  void exactCutoffIsIncluded() {
    assertTrue(selector.select(List.of(p(0, "2", true)), cut, start).isPresent());
  }

  @Test
  void missingEarlierQuoteRemainsMissing() {
    assertTrue(selector.select(List.of(p(1, "2", true)), cut, start).isEmpty());
  }

  @Test
  void conflictingSameTimestampFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> selector.select(List.of(p(0, "2", true), p(0, "3", true)), cut, start));
  }

  @Test
  void kickoffOrLaterIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> selector.select(List.of(), start, start));
  }

  @Test
  void invalidLatestPriceDoesNotResurrectEarlierQuote() {
    assertTrue(selector.select(List.of(p(-20, "2", true), p(0, "0", true)), cut, start).isEmpty());
  }

  @Test
  void invalidOldUpdateDoesNotHideLaterValidPrice() {
    assertEquals(
        new BigDecimal("2"),
        selector
            .select(List.of(p(-20, "0", true), p(0, "2", true)), cut, start)
            .orElseThrow()
            .price());
  }
}
