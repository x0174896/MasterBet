package it.masterbet.betting.application;

import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.*;
import it.masterbet.simulation.domain.Snapshot;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revisione monetaria esplicita: nessuna nuova selezione, acquisizione o modifica dei modelli. */
@Service
public class BettingRepricingService {
  private final BettingRepository rounds;
  private final RevealRepository reveals;
  private final SnapshotRepository snapshots;
  private final ProfitTargetStaking staking;
  private final BettingEvaluator evaluator;
  private final JdbcTemplate jdbc;

  public BettingRepricingService(
      BettingRepository rounds,
      RevealRepository reveals,
      SnapshotRepository snapshots,
      ProfitTargetStaking staking,
      BettingEvaluator evaluator,
      JdbcTemplate jdbc) {
    this.rounds = rounds;
    this.reveals = reveals;
    this.snapshots = snapshots;
    this.staking = staking;
    this.evaluator = evaluator;
    this.jdbc = jdbc;
  }

  @Transactional
  public int reprice() {
    String job =
        jdbc.queryForObject(
            "SELECT status FROM advance_job WHERE singleton=1 FOR UPDATE", String.class);
    if ("RUNNING".equals(job)) throw new IllegalStateException("Avanzamento in corso");
    var previous = new ArrayList<BettingRound>();
    int updated = 0;
    boolean encounteredCurrent = false;
    for (var saved : rounds.all()) {
      rounds.lock(saved.scenarioId());
      var original = rounds.find(saved.scenarioId()).orElseThrow();
      if (BettingRound.CURRENT_POLICY.equals(original.policyVersion())) {
        if (updated > 0)
          throw new IllegalStateException("Politiche di puntata miste: ricalcolo interrotto");
        previous.add(original);
        encounteredCurrent = true;
        continue;
      }
      if (encounteredCurrent)
        throw new IllegalStateException("Politiche storiche non in ordine: ricalcolo interrotto");
      if (original.policyVersion() != null && !"five-slips-v2".equals(original.policyVersion()))
        throw new IllegalStateException("Politica di puntata non riconosciuta");
      var source = snapshots.find(original.scenarioId()).orElseThrow();
      if (!source.datasetSha256().equals(original.datasetSha256())
          || !source.dataset().cutoff().equals(original.cutoff())
          || !source.model().id().equals(original.modelId()))
        throw new IllegalStateException("Snapshot diverso dalle schedine salvate");
      BigDecimal cupsCapital = capital(previous, source, original.config(), true);
      BigDecimal leagueCapital = capital(previous, source, original.config(), false);
      var cups = amounts(original.cups(), original.config().unitValue(), cupsCapital);
      var league = amounts(original.league(), original.config().unitValue(), leagueCapital);
      var revised =
          new BettingRound(
              original.scenarioId(),
              original.datasetSha256(),
              original.modelId(),
              original.cutoff(),
              original.frozenAt(),
              original.config(),
              cupsCapital,
              leagueCapital,
              cups,
              league);
      rounds.replaceAmounts(revised);
      reveals.reprice(revised);
      previous.add(revised);
      updated++;
    }
    return updated;
  }

  private BigDecimal capital(
      List<BettingRound> previous, Snapshot snapshot, BettingConfig config, boolean cups) {
    return previous.isEmpty()
        ? config.unitValue().multiply(config.initialBankrollUnits())
        : evaluator
            .evaluate(
                previous, snapshot.dataset().leagueHistory(), snapshot.dataset().cutoff(), cups)
            .available();
  }

  private List<BettingRound.Slip> amounts(
      List<BettingRound.Slip> slips, BigDecimal unitValue, BigDecimal available) {
    var revised = slips.stream().map(s -> staking.reprice(s, unitValue)).toList();
    var spent =
        revised.stream().map(BettingRound.Slip::stake).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (spent.compareTo(available) > 0)
      throw new IllegalStateException("Capitale insufficiente per ricalcolare tutte le schedine");
    return revised;
  }
}
