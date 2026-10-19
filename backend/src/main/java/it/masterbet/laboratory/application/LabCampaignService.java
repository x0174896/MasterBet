package it.masterbet.laboratory.application;

import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class LabCampaignService {
  private final LabJobs jobs;
  private final LabDownloadService downloads;
  private final LabReconciliationService reconciliation;
  private final LabDataValidation validation;
  private final LabSimulationService simulation;
  private final LabCampaignStore store;
  private final LabRepository repo;

  public LabCampaignService(
      LabJobs jobs,
      LabDownloadService downloads,
      LabReconciliationService reconciliation,
      LabDataValidation validation,
      LabSimulationService simulation,
      LabCampaignStore store,
      LabRepository repo) {
    this.jobs = jobs;
    this.downloads = downloads;
    this.reconciliation = reconciliation;
    this.validation = validation;
    this.simulation = simulation;
    this.store = store;
    this.repo = repo;
  }

  public Consumer<LabJobs.Progress> run(LabDownloadRequest request, BigDecimal unitProfit) {
    // Validate all parameters before any download or checkpoint mutation.
    var fullConfig =
        new LabRunConfig(
            request.from(), request.to(), request.competitionIds(), unitProfit, "HISTORICAL");
    return p -> {
      p.total(6);
      String config =
          repo.json(List.of("sequential-v1-identities-v1", request, fullConfig.unitProfit()));
      String hash = RawResponseArchive.hash(config);
      phase("results", "results", downloads.results(request), request, p, hash, config, true);
      phase(
          "reconcile",
          "reconcile",
          reconciliation.reconcile(request),
          request,
          p,
          hash,
          config,
          true);
      var selectedIds =
          downloads.selected(request).stream()
              .map(LabCompetition::id)
              .collect(java.util.stream.Collectors.toSet());
      var resultsIds =
          repo.matches().stream()
              .filter(
                  m ->
                      !m.cup()
                          && m.score() != null
                          && selectedIds.contains(m.fixture().tournamentId())
                          && !m.fixture()
                              .kickoff()
                              .isBefore(
                                  request.from().atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                          && m.fixture()
                              .kickoff()
                              .isBefore(
                                  request
                                      .to()
                                      .plusDays(1)
                                      .atStartOfDay()
                                      .toInstant(java.time.ZoneOffset.UTC)))
              .map(m -> m.fixture().tournamentId())
              .distinct()
              .sorted()
              .toList();
      if (resultsIds.isEmpty())
        throw new IllegalStateException(
            "Nessun campionato con risultati: quote e calcolo non avviati");
      var pricedRequest =
          new LabDownloadRequest(
              resultsIds, request.from(), request.to(), request.bookmakers(), request.refresh());
      phase("quotes", "quotes", downloads.quotes(pricedRequest), request, p, hash, config, true);
      var cachedRequest =
          new LabDownloadRequest(
              request.competitionIds(), request.from(), request.to(), request.bookmakers(), false);
      phase(
          "reconcile-final",
          "reconcile",
          reconciliation.reconcile(cachedRequest),
          request,
          p,
          hash,
          config,
          false);
      phase(
          "validate",
          "validate",
          v -> {
            var report = validation.verify(fullConfig);
            report.gaps().forEach(v::warning);
            v.item(
                "Controlli superati: "
                    + report.eligible().size()
                    + " campionati con HT/FT e quote abbinate");
          },
          request,
          p,
          hash,
          config,
          false);
      var report = validation.verify(fullConfig);
      var checked =
          new LabRunConfig(
              request.from(),
              request.to(),
              report.eligible(),
              fullConfig.unitProfit(),
              "HISTORICAL");
      phase(
          "calculate", "calculate", simulation.calculate(checked), request, p, hash, config, false);
      p.update(
          "Campagna completata: risultati, abbinamenti, quote, controlli e calcolo; consultare le"
              + " lacune segnalate");
    };
  }

  private void phase(
      String stage,
      String kind,
      Consumer<LabJobs.Progress> operation,
      LabDownloadRequest request,
      LabJobs.Progress parent,
      String hash,
      String config,
      boolean reusable) {
    if (reusable
        && !request.refresh()
        && store.complete(hash, stage)
        && store.sourceFailures().stream().noneMatch(s -> s.startsWith(kind + ":"))
        && !(stage.equals("quotes") && !store.incompleteQuoteImports().isEmpty())) {
      parent.item("Fase " + stage + " gia completata: archivio e checkpoint riutilizzati");
      return;
    }
    parent.update("Fase automatica: " + stage);
    var job = jobs.phase(kind, operation, parent.id());
    boolean technical = jobs.technicalErrors(job.id()) > 0;
    if (!Set.of("SUCCEEDED", "PARTIAL").contains(job.status()) || technical)
      throw new IllegalStateException(
          "Fase "
              + stage
              + " interrotta o con errori tecnici; dati conservati, calcolo bloccato. Job "
              + job.id());
    store.save(hash, stage, config, job);
    if (!job.errors().isEmpty())
      parent.warning(
          "GAP: fase "
              + stage
              + ": "
              + job.errors().size()
              + " lacune di copertura, dettagli nel job "
              + job.id());
    parent.item("Fase " + stage + " completata");
  }
}
