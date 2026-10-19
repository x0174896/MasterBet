package it.masterbet.simulation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.application.BettingService;
import it.masterbet.betting.domain.BettingConfig;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.model.application.ModelTrainingService;
import it.masterbet.model.domain.ModelReport;
import it.masterbet.simulation.domain.*;
import it.masterbet.simulation.infrastructure.AdvanceJobRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdvanceService {
  private final ScenarioQueryService query;
  private final NextStepDownloader downloader;
  private final DatasetValidator validator;
  private final ModelTrainingService training;
  private final PredictionService predictions;
  private final BettingService betting;
  private final AdvancePublisher publisher;
  private final AdvanceJobRepository jobs;
  private final ObjectMapper mapper;

  public AdvanceService(
      ScenarioQueryService query,
      NextStepDownloader downloader,
      DatasetValidator validator,
      ModelTrainingService training,
      PredictionService predictions,
      BettingService betting,
      AdvancePublisher publisher,
      AdvanceJobRepository jobs,
      ObjectMapper mapper) {
    this.query = query;
    this.downloader = downloader;
    this.validator = validator;
    this.training = training;
    this.predictions = predictions;
    this.betting = betting;
    this.publisher = publisher;
    this.jobs = jobs;
    this.mapper = mapper;
  }

  public synchronized AdvanceJobRepository.Status start(
      String expectedScenarioId, BettingConfig config) {
    var status = jobs.status();
    if (status.status().equals("RUNNING")) return status;
    var source = query.load();
    if (!source.id().equals(expectedScenarioId))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Scenario cambiato: ricaricare prima di avanzare");
    String id = UUID.randomUUID().toString();
    if (!jobs.claim(id, source.id())) return jobs.status();
    Thread.ofVirtual().name("masterbet-advance").start(() -> run(id, source, config));
    return jobs.status();
  }

  private void run(String job, Snapshot source, BettingConfig config) {
    try {
      betting.freeze(source, config); // Commit precedente a QUALSIASI download di nuovi risultati.
      var data = downloader.download(source, m -> jobs.update(job, "RUNNING", m));
      validator.validate(data);
      jobs.update(
          job, "RUNNING", "Addestramento cronologico, confronto modelli e calcolo schedine");
      var historic = new ArrayList<>(data.trainingHistory());
      historic.addAll(data.leagueHistory());
      var m = training.train(historic, data.calendar());
      var model =
          new ModelReport(
              data.id() + "-poisson-v2",
              m.algorithm(),
              m.trainingMatches(),
              m.validationMatches(),
              m.trainingFrom(),
              m.trainingThrough(),
              m.validationFrom(),
              m.validationThrough(),
              m.features(),
              m.baselineBrier(),
              m.leagueModelBrier(),
              m.cupsModelBrier(),
              m.leagueModel(),
              m.cupsModel(),
              m.limitation());
      var snapshot =
          new Snapshot(
              data.id(),
              Instant.now(),
              RawResponseArchive.hash(mapper.writeValueAsString(data)),
              data,
              model,
              predictions.predict(data, model));
      var round = betting.prepare(snapshot, config);
      publisher.publish(snapshot, round, job);
    } catch (Exception e) {
      String detail =
          e instanceof IllegalArgumentException || e instanceof IllegalStateException
              ? e.getMessage()
              : "Verifica tecnica non riuscita";
      if (detail == null
          || detail.length() > 600
          || detail.contains("apiKey")
          || detail.contains("password")) detail = "Verifica tecnica non riuscita";
      jobs.update(
          job,
          "FAILED",
          detail
              + ". Lo scenario precedente resta consultabile; cache e schedine congelate"
              + " conservate.");
      org.slf4j.LoggerFactory.getLogger(AdvanceService.class)
          .warn("Avanzamento {} interrotto: {}", job, detail);
    }
  }
}
