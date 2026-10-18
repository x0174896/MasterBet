package it.masterbet.simulation.domain;

import it.masterbet.odds.domain.QuotedOutcome;

public record Prediction(
    QuotedOutcome quote,
    Double probability,
    Double baselineProbability,
    Double fairPrice,
    Double expectedReturn,
    boolean suggested,
    String calculationStatus) {}
