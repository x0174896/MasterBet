package it.masterbet.betting.application;

import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.BettingRepository;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BettingWorkspaceService {
  private final BettingRepository repository;
  private final BettingService betting;
  private final SnapshotRepository snapshots;
  private final JdbcTemplate jdbc;

  public BettingWorkspaceService(
      BettingRepository repository,
      BettingService betting,
      SnapshotRepository snapshots,
      JdbcTemplate jdbc) {
    this.repository = repository;
    this.betting = betting;
    this.snapshots = snapshots;
    this.jdbc = jdbc;
  }

  @Transactional
  public BettingService.View save(String expectedScenarioId) {
    String job =
        jdbc.queryForObject(
            "SELECT status FROM advance_job WHERE singleton=1 FOR UPDATE", String.class);
    if ("RUNNING".equals(job)) throw conflict("Avanzamento in corso");
    var snapshot = snapshots.latest().orElseThrow(() -> conflict("Scenario assente"));
    if (!snapshot.id().equals(expectedScenarioId)) throw conflict("Scenario cambiato: ricaricare");
    betting.freeze(snapshot, BettingConfig.defaults());
    repository.lock(snapshot.id());
    // Il salvataggio ordinario non riscrive selezioni né applica revisioni retroattive.
    return betting.view(snapshot);
  }

  @Transactional
  public BettingRound claimReveal(String scenarioId, String variant, String slipId) {
    if (!variant.equals("cups") && !variant.equals("league"))
      throw new IllegalArgumentException("Modello non valido");
    if (repository.find(scenarioId).isEmpty()) {
      var snapshot = snapshots.latest().orElseThrow(() -> conflict("Scenario assente"));
      if (!snapshot.id().equals(scenarioId)) throw conflict("Schedine non salvate");
      betting.freeze(snapshot, BettingConfig.defaults());
    }
    repository.lock(scenarioId);
    var round = repository.find(scenarioId).orElseThrow();
    var slips = variant.equals("cups") ? round.cups() : round.league();
    if (slips.stream().noneMatch(s -> s.id().equals(slipId)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedina assente");
    // Il blocco e confermato prima dell'accesso ai risultati, anche se il download fallisce.
    repository.lockReveal(scenarioId);
    return round;
  }

  private ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
