package it.masterbet.simulation.api;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(
    String bookmaker,
    String marketId,
    String market,
    String type,
    String period,
    BigDecimal line,
    String outcomeId,
    String outcome,
    BigDecimal price,
    Instant observedAt,
    Double probability,
    Double baselineProbability,
    Double fairPrice,
    Double expectedReturn,
    boolean suggested,
    String calculationStatus) {}
