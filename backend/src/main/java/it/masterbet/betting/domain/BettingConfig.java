package it.masterbet.betting.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

// Legge gli archivi precedenti senza riattivare il vecchio limite di spesa.
@JsonIgnoreProperties({"maxRoundUnits"})
public record BettingConfig(BigDecimal unitValue, BigDecimal initialBankrollUnits) {
  public BettingConfig {
    if (unitValue == null
        || initialBankrollUnits == null
        || unitValue.scale() > 2
        || unitValue.compareTo(new BigDecimal("0.04")) < 0
        || unitValue.compareTo(new BigDecimal("10000")) > 0
        || initialBankrollUnits.compareTo(BigDecimal.ONE) < 0
        || initialBankrollUnits.compareTo(new BigDecimal("100000")) > 0)
      throw new IllegalArgumentException("Utile obiettivo o capitale iniziale non validi");
  }

  public static BettingConfig defaults() {
    return new BettingConfig(new BigDecimal("5.00"), new BigDecimal("100"));
  }
}
