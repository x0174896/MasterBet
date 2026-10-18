package it.masterbet.simulation.application;

import it.masterbet.simulation.api.*;
import it.masterbet.simulation.domain.Snapshot;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import it.masterbet.statistics.application.StatisticsService;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScenarioQueryService {
  private final SnapshotRepository repository;
  private final StatisticsService statistics;

  public ScenarioQueryService(SnapshotRepository repository, StatisticsService statistics) {
    this.repository = repository;
    this.statistics = statistics;
  }

  private Snapshot load() {
    return repository
        .find("eliteserien-2026-round7")
        .orElseThrow(() -> new NoSuchElementException("Scenario non ancora importato"));
  }

  public ScenarioResponse scenario() {
    var s = load();
    var d = s.dataset();
    var m = s.model();
    var targets =
        d.targets().stream()
            .map(
                f -> {
                  var rows =
                      s.predictions().stream()
                          .filter(p -> p.quote().fixtureId().equals(f.id()))
                          .toList();
                  return new ScenarioResponse.TargetMatch(
                      f,
                      rows.size(),
                      rows.stream().map(p -> p.quote().market().id()).distinct().count(),
                      rows.stream().filter(p -> p.probability() != null).count(),
                      rows.stream().filter(p -> p.suggested()).count());
                })
            .toList();
    var model =
        new ScenarioResponse.ModelSummary(
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
            m.limitation());
    var cups =
        d.calendar().stream()
            .filter(
                f ->
                    it.masterbet.football.domain.CompetitionPolicy.isCup(f)
                        && f.kickoff().isAfter(d.cutoff().minus(Duration.ofDays(30))))
            .sorted(
                Comparator.comparing((it.masterbet.football.domain.Fixture f) -> f.kickoff())
                    .reversed())
            .toList();
    return new ScenarioResponse(
        d.id(),
        "Eliteserien · Norvegia",
        "2026",
        d.cutoff(),
        s.createdAt(),
        "OddsPapi · archivio storico",
        s.datasetSha256(),
        d.leagueHistory().size(),
        d.trainingHistory().size(),
        s.predictions().size(),
        targets,
        d.leagueHistory(),
        statistics.calculate(d.leagueHistory(), d.cutoff()),
        cups,
        d.cupResults(),
        model,
        List.of(
            "Scenario congelato dopo la sesta giornata: anticipi e recuperi seguono la data"
                + " effettiva. Le squadre hanno da 5 a 7 partite disputate.",
            "Quote: ultimo aggiornamento attivo entro il taglio. Nessuna quota successiva o"
                + " risultato della settima giornata entra nei calcoli.",
            "Classifica ricostruita dai risultati; nessuna penalizzazione amministrativa"
                + " applicata.",
            "Orari programmati conservati. Se gli orari effettivi sono incoerenti, i risultati"
                + " diventano utilizzabili solo dopo 24 ore (limite conservativo, non orario di"
                + " fine). Il riposo e misurato fra calci d'inizio.",
            "Prezzi nulli, zero o non superiori a 1 non sono quote utilizzabili. L'ultimo"
                + " aggiornamento invalido o sospeso non viene sostituito da un prezzo precedente.",
            "Modello sperimentale: valore atteso stimato, non rendimento verificato. Le quote non"
                + " implicano disponibilita attuale presso un bookmaker."));
  }

  public List<QuoteResponse> quotes(String fixtureId) {
    var s = load();
    if (s.dataset().targets().stream().noneMatch(f -> f.id().equals(fixtureId)))
      throw new NoSuchElementException("Partita non presente nello scenario");
    return s.predictions().stream()
        .filter(p -> p.quote().fixtureId().equals(fixtureId))
        .map(
            p -> {
              var q = p.quote();
              var m = q.market();
              return new QuoteResponse(
                  q.bookmaker(),
                  m.id(),
                  m.name(),
                  m.type(),
                  m.period(),
                  m.line(),
                  q.outcomeId(),
                  q.outcome(),
                  q.price(),
                  q.observedAt(),
                  p.probability(),
                  p.baselineProbability(),
                  p.fairPrice(),
                  p.expectedReturn(),
                  p.suggested(),
                  p.calculationStatus());
            })
        .sorted(
            Comparator.comparing(QuoteResponse::bookmaker)
                .thenComparing(QuoteResponse::market)
                .thenComparing(
                    QuoteResponse::line, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(QuoteResponse::outcome))
        .toList();
  }
}
