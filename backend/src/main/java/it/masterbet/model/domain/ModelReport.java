package it.masterbet.model.domain;

import java.time.Instant;
import java.util.List;

public record ModelReport(
    String id,
    String algorithm,
    int trainingMatches,
    int validationMatches,
    Instant trainingFrom,
    Instant trainingThrough,
    Instant validationFrom,
    Instant validationThrough,
    List<String> features,
    double baselineBrier,
    double leagueModelBrier,
    double cupsModelBrier,
    PoissonModel leagueModel,
    PoissonModel cupsModel,
    String limitation) {}
