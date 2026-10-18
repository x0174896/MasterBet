package it.masterbet.betting.application;

import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.BettingRepository;
import it.masterbet.simulation.application.PredictionService;
import it.masterbet.simulation.domain.Snapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class BettingService {
  private final BettingRepository repository;
  private final SlipSelector selector;
  private final PredictionService predictions;
  private final BettingEvaluator evaluator;

  public BettingService(
      BettingRepository repository,
      SlipSelector selector,
      PredictionService predictions,
      BettingEvaluator evaluator) {
    this.repository = repository;
    this.selector = selector;
    this.predictions = predictions;
    this.evaluator = evaluator;
  }

  public BettingRound prepare(Snapshot snapshot, BettingConfig config) {
    var existing = repository.find(snapshot.id());
    if (existing.isPresent()) {
      if (!existing.get().datasetSha256().equals(snapshot.datasetSha256()))
        throw new IllegalStateException("Snapshot diverso dalle schedine congelate");
      return existing.get();
    }
    return prepareFresh(snapshot, config);
  }

  public BettingRound prepareFresh(Snapshot snapshot, BettingConfig config) {
    var rounds =
        repository.all().stream().filter(r -> !r.scenarioId().equals(snapshot.id())).toList();
    BigDecimal initial = config.unitValue().multiply(config.initialBankrollUnits());
    BigDecimal cups =
        rounds.isEmpty()
            ? initial
            : evaluator
                .evaluate(
                    rounds, snapshot.dataset().leagueHistory(), snapshot.dataset().cutoff(), true)
                .available();
    BigDecimal league =
        rounds.isEmpty()
            ? initial
            : evaluator
                .evaluate(
                    rounds, snapshot.dataset().leagueHistory(), snapshot.dataset().cutoff(), false)
                .available();
    return new BettingRound(
        snapshot.id(),
        snapshot.datasetSha256(),
        snapshot.model().id(),
        snapshot.dataset().cutoff(),
        Instant.now(),
        config,
        cups,
        league,
        selector.select(snapshot, snapshot.predictions(), config, cups),
        selector.select(
            snapshot,
            predictions.predict(snapshot.dataset(), snapshot.model(), false),
            config,
            league));
  }

  public void freeze(Snapshot snapshot, BettingConfig config) {
    if (repository.find(snapshot.id()).isEmpty()) repository.save(prepare(snapshot, config));
  }

  public record View(
      BettingRound current, BettingEvaluation cups, BettingEvaluation league, boolean frozen) {}

  public View view(Snapshot snapshot) {
    var rounds = repository.all();
    var current = repository.find(snapshot.id());
    var preview = current.orElseGet(() -> prepare(snapshot, BettingConfig.defaults()));
    var evaluated = new java.util.ArrayList<>(rounds);
    if (current.isEmpty()) evaluated.add(preview);
    return new View(
        preview,
        evaluator.evaluate(
            evaluated, snapshot.dataset().leagueHistory(), snapshot.dataset().cutoff(), true),
        evaluator.evaluate(
            evaluated, snapshot.dataset().leagueHistory(), snapshot.dataset().cutoff(), false),
        current.isPresent());
  }
}
