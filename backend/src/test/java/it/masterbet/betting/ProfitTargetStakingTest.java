package it.masterbet.betting;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.betting.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitTargetStakingTest {
  private final ProfitTargetStaking staking = new ProfitTargetStaking();

  @Test
  void netTargetDiffersFromGrossAndRoundingsAreExplicit() {
    assertEquals(new BigDecimal("5.00"), staking.stake(new BigDecimal("5"), new BigDecimal("2")));
    assertEquals(new BigDecimal("2.50"), staking.stake(new BigDecimal("5"), new BigDecimal("3")));
    assertEquals(
        new BigDecimal("2.33"), staking.stake(new BigDecimal("5"), new BigDecimal("3.15")));
    assertEquals(
        new BigDecimal("1.65"), staking.stake(new BigDecimal("5"), new BigDecimal("4.028")));
    assertEquals(
        new BigDecimal("0.01"), staking.stake(new BigDecimal("0.04"), new BigDecimal("25")));
    assertEquals(
        new BigDecimal("500.00"), staking.stake(new BigDecimal("5"), new BigDecimal("1.01")));
  }

  @Test
  void invalidTargetsOrNonWinningPricesCannotBeUsed() {
    for (var price : List.of("-1", "0", "1"))
      assertThrows(
          IllegalArgumentException.class,
          () -> staking.stake(BigDecimal.ONE, new BigDecimal(price)));
    assertThrows(
        IllegalArgumentException.class, () -> staking.stake(BigDecimal.ZERO, BigDecimal.TEN));
  }

  @Test
  void futureProposalsUseConfiguredProfitWithoutDailyCapOrPartialStakes() {
    var helper = new BettingTest();
    var predictions =
        List.of(helper.prediction("a"), helper.prediction("b"), helper.prediction("c"));
    var selector = new SlipSelector(staking);
    var config = new BettingConfig(new BigDecimal("10"), new BigDecimal("100"));
    var full =
        selector.select(helper.snapshot(predictions), predictions, config, new BigDecimal("30"));
    assertEquals(3, full.size()); // Supera il vecchio tetto di due unità.
    assertTrue(full.stream().allMatch(s -> s.potentialProfit().equals(new BigDecimal("10.00"))));
    var limited =
        selector.select(helper.snapshot(predictions), predictions, config, new BigDecimal("19.99"));
    assertEquals(1, limited.size());
    assertEquals(new BigDecimal("10.00"), limited.getFirst().stake());
  }
}
