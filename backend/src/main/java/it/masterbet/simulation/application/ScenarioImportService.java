package it.masterbet.simulation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.model.application.ModelTrainingService;
import it.masterbet.simulation.domain.*;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ScenarioImportService {
  private final ScenarioDownloader downloader;
  private final DatasetValidator validator;
  private final ModelTrainingService training;
  private final PredictionService predictions;
  private final SnapshotPublisher publisher;
  private final ObjectMapper mapper;

  public ScenarioImportService(
      ScenarioDownloader downloader,
      DatasetValidator validator,
      ModelTrainingService training,
      PredictionService predictions,
      SnapshotPublisher publisher,
      ObjectMapper mapper) {
    this.downloader = downloader;
    this.validator = validator;
    this.training = training;
    this.predictions = predictions;
    this.publisher = publisher;
    this.mapper = mapper;
  }

  public synchronized Snapshot importScenario() {
    var data = downloader.download();
    validator.validate(data);
    var model = training.train(data.trainingHistory(), data.calendar());
    try {
      var snapshot =
          new Snapshot(
              data.id(),
              Instant.now(),
              RawResponseArchive.hash(mapper.writeValueAsString(data)),
              data,
              model,
              predictions.predict(data, model));
      publisher.publish(snapshot);
      return snapshot;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Impossibile calcolare l'impronta dei dati");
    }
  }
}
