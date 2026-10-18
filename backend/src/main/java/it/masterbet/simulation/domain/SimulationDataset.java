package it.masterbet.simulation.domain;

import it.masterbet.football.domain.*;
import it.masterbet.odds.domain.QuotedOutcome;
import java.time.Instant;
import java.util.List;

public record SimulationDataset(
    String id,
    Instant cutoff,
    List<PlayedMatch> trainingHistory,
    List<PlayedMatch> leagueHistory,
    List<Fixture> calendar,
    List<PlayedMatch> cupResults,
    List<Fixture> targets,
    List<QuotedOutcome> quotes) {}
