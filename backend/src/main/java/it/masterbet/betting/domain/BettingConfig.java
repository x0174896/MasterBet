package it.masterbet.betting.domain;

import java.math.BigDecimal;

public record BettingConfig(
    BigDecimal unitValue, BigDecimal initialBankrollUnits, BigDecimal maxRoundUnits) {
  public BettingConfig {
    if (unitValue == null
        || initialBankrollUnits == null
        || maxRoundUnits == null
        || unitValue.scale() > 2
        || unitValue.compareTo(new BigDecimal("0.04")) < 0
        || unitValue.compareTo(new BigDecimal("10000")) > 0
        || initialBankrollUnits.compareTo(BigDecimal.ONE) < 0
        || initialBankrollUnits.compareTo(new BigDecimal("100000")) > 0
        || maxRoundUnits.signum() <= 0
        || maxRoundUnits.compareTo(new BigDecimal("2")) > 0)
      throw new IllegalArgumentException(
          "Unita/capitale non validi; limite massimo 2 unita per step");
  }

  public static BettingConfig defaults() {
    return new BettingConfig(new BigDecimal("5.00"), new BigDecimal("100"), new BigDecimal("2"));
  }
}
