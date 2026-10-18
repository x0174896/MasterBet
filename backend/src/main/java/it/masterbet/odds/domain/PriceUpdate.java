package it.masterbet.odds.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceUpdate(Instant timestamp, BigDecimal price, boolean active) {
  public PriceUpdate {
    if (timestamp == null || price == null)
      throw new IllegalArgumentException("Aggiornamento quota non valido");
  }
}
