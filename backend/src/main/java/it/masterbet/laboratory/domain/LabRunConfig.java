package it.masterbet.laboratory.domain;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record LabRunConfig(
    LocalDate from,
    LocalDate to,
    List<Integer> competitionIds,
    BigDecimal unitProfit,
    String mode,
    String name,
    String strategy,
    String referenceRunId) {
  public LabRunConfig(
      LocalDate from,
      LocalDate to,
      List<Integer> competitionIds,
      BigDecimal unitProfit,
      String mode) {
    this(from, to, competitionIds, unitProfit, mode, null, null, null);
  }

  public LabRunConfig {
    if (from == null) from = LocalDate.of(2026, 1, 1);
    if (to == null) to = LocalDate.now(ZoneOffset.UTC);
    if (from.isBefore(LocalDate.of(2026, 1, 1))
        || to.isBefore(from)
        || to.isAfter(LocalDate.now(ZoneOffset.UTC))
        || to.isAfter(from.plusYears(2))) throw new IllegalArgumentException("Periodo non valido");
    competitionIds = competitionIds == null ? List.of() : List.copyOf(competitionIds);
    unitProfit =
        unitProfit == null
            ? new BigDecimal("5.00")
            : unitProfit.setScale(2, java.math.RoundingMode.HALF_UP);
    if (unitProfit.signum() <= 0 || unitProfit.compareTo(new BigDecimal("1000")) > 0)
      throw new IllegalArgumentException("Unita non valida");
    if (mode == null) mode = "HISTORICAL";
    if (!Set.of("HISTORICAL", "OPERATIONAL").contains(mode))
      throw new IllegalArgumentException("Modalita non valida");
    name = name == null ? "" : name.strip();
    if (name.length() > 120) throw new IllegalArgumentException("Nome campagna troppo lungo");
    strategy = strategy == null ? "BASELINE" : strategy;
    if (!Set.of("BASELINE", "CALIBRATED", "SIGNALS", "FILTERED", "ADAPTIVE").contains(strategy))
      throw new IllegalArgumentException("Strategia non valida");
    referenceRunId = referenceRunId == null || referenceRunId.isBlank() ? null : referenceRunId;
    if (!strategy.equals("BASELINE") && referenceRunId == null)
      throw new IllegalArgumentException("Campagna di riferimento necessaria");
  }
}
