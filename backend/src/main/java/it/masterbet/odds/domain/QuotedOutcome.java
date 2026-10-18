package it.masterbet.odds.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record QuotedOutcome(
    String fixtureId,
    String bookmaker,
    Market market,
    String outcomeId,
    String outcome,
    BigDecimal price,
    Instant observedAt) {}
