package it.masterbet.simulation.application;

import it.masterbet.football.infrastructure.FootballRepository;
import it.masterbet.model.infrastructure.ModelRepository;
import it.masterbet.simulation.domain.Snapshot;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotPublisher {
  private final FootballRepository football;
  private final ModelRepository models;
  private final SnapshotRepository snapshots;

  public SnapshotPublisher(
      FootballRepository football, ModelRepository models, SnapshotRepository snapshots) {
    this.football = football;
    this.models = models;
    this.snapshots = snapshots;
  }

  @Transactional
  public void publish(Snapshot snapshot) {
    var data = snapshot.dataset();
    data.calendar().forEach(football::saveFixture);
    data.targets().forEach(football::saveFixture);
    data.trainingHistory().forEach(football::saveResult);
    data.leagueHistory().forEach(football::saveResult);
    data.cupResults().forEach(football::saveResult);
    models.save(snapshot.model());
    snapshots.save(snapshot);
  }
}
