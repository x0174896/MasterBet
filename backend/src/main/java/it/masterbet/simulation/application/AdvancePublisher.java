package it.masterbet.simulation.application;

import it.masterbet.betting.domain.BettingRound;
import it.masterbet.betting.infrastructure.BettingRepository;
import it.masterbet.simulation.domain.Snapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvancePublisher {
  private final SnapshotPublisher publisher;
  private final BettingRepository bets;
  private final it.masterbet.simulation.infrastructure.AdvanceJobRepository jobs;

  public AdvancePublisher(
      SnapshotPublisher publisher,
      BettingRepository bets,
      it.masterbet.simulation.infrastructure.AdvanceJobRepository jobs) {
    this.publisher = publisher;
    this.bets = bets;
    this.jobs = jobs;
  }

  @Transactional
  public void publish(Snapshot snapshot, BettingRound round, String job) {
    publisher.publish(snapshot);
    bets.save(round);
    jobs.update(
        job, "SUCCEEDED", "Step completato: risultati, quote, modello e schedine pubblicati");
  }
}
