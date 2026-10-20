package it.masterbet.laboratory.application;

import it.masterbet.betting.domain.ProfitTargetStaking;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.LabRunRepository;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Reuses trained goal models and frozen forecasts without rescanning odds or calling providers.
 */
@Service
public class LabReplayService {
  private final LabRunRepository runs;
  private final LabForecastHistory history;
  private final ProfitTargetStaking staking;
  private final LabMarketLearning learning = new LabMarketLearning();
  private final LabMarketSelector selector = new LabMarketSelector();

  public LabReplayService(
      LabRunRepository runs, LabForecastHistory history, ProfitTargetStaking staking) {
    this.runs = runs;
    this.history = history;
    this.staking = staking;
  }

  public Consumer<LabJobs.Progress> recalculate(LabRunRepository.Run expected) {
    return progress -> {
      if (expected.config().strategy().equals("BASELINE")
          || !expected.config().mode().equals("HISTORICAL")
          || !expected.status().equals("COMPLETED")
          || !expected.equals(runs.run(expected.id())))
        throw new IllegalArgumentException(
            "Ricalcolo riservato all'esperimento storico completo autorizzato");
      var frames = runs.marketFrames(expected.id());
      var steps = runs.steps(expected.id());
      var results = runs.results(expected.id());
      if (frames.isEmpty()
          || steps.isEmpty()
          || frames.stream().anyMatch(f -> !results.containsKey(f.fixtureId())))
        throw new IllegalStateException(
            "Previsioni, modelli o esiti congelati incompleti: usare il calcolo completo");
      var shadows = runs.shadows(expected.id());
      Map<Boolean, List<LabMarketFrame>> past = new HashMap<>();
      past.put(false, new ArrayList<>());
      past.put(true, new ArrayList<>());
      List<LabRunRepository.Batch> batches = new ArrayList<>();
      progress.total(steps.size());
      for (var old : steps) {
        boolean cups = old.includeCupScores();
        var cutoff = old.cutoff();
        var prior = past.get(cups);
        var unit = expected.config().unitProfit();
        var learned =
            learning.fit(
                history.observations(prior, results, cutoff, unit),
                cutoff,
                Set.of("ADAPTIVE", "FILTERED").contains(expected.config().strategy()),
                history.policies(prior, results, cutoff, unit),
                history.replacements(prior, results, cutoff, unit));
        List<LabBet> bets = new ArrayList<>();
        List<LabMarketFrame> current = new ArrayList<>();
        Map<String, Integer> rejected = new TreeMap<>();
        Map<String, String> exclusions = new TreeMap<>(old.exclusions());
        for (var frame : frames) {
          if (frame.includeCupScores() != cups || !frame.predictedAt().equals(cutoff)) continue;
          var selection =
              selector.select(
                  frame.candidates(),
                  frame.kickoff(),
                  learned,
                  frame.competitionId(),
                  frame.season(),
                  frame.features());
          selection.rejected().forEach((key, count) -> rejected.merge(key, count, Integer::sum));
          var replacement =
              new LabMarketFrame(
                  frame.fixtureId(),
                  frame.competitionId(),
                  frame.season(),
                  frame.kickoff(),
                  frame.predictedAt(),
                  cups,
                  frame.features(),
                  frame.candidates(),
                  selection.policyPicks());
          current.add(replacement);
          var fixture = results.get(frame.fixtureId()).fixture();
          String label = fixture.home() + " - " + fixture.away() + " (" + fixture.id() + ")";
          if (selection.best().isEmpty()) {
            exclusions.put(
                label,
                "Nessuna singola ammessa dopo calibrazione, condizioni del mercato e verifica delle"
                    + " sostituzioni");
            continue;
          }
          exclusions.remove(label);
          var choice = selection.best().get();
          var q = choice.source().quote();
          var c = learned.calibration(LabMarketFrame.family(q)).orElseThrow();
          List<String> explanation = new ArrayList<>();
          explanation.add(
              "Sette precedenti di campionato per squadra; input e modello dei gol congelati al"
                  + " taglio "
                  + cutoff);
          explanation.add(
              cups
                  ? "Gol di coppa inclusi; calendario e riposo di coppa considerati"
                  : "Gol di coppa esclusi; calendario e riposo di coppa considerati");
          explanation.add(
              String.format(
                  Locale.ITALY,
                  "Probabilita grezza %.1f%%, corretta %.1f%%; verifica specifica su %d incontri"
                      + " del mercato",
                  100 * choice.source().probability(),
                  100 * choice.probability(),
                  c.support()));
          explanation.add(
              choice.source().primary()
                  ? "Prima scelta grezza confermata: nessuna sostituzione senza vantaggio"
                        + " verificato viene imposta"
                  : "Alternativa ammessa dopo confronto su incontri abbinati, rendimento nei due"
                        + " blocchi e controllo settimanale dell'incertezza");
          explanation.add(
              "Quote storiche prepartita; disponibilita eseguibile non certificata. Nessun"
                  + " risultato del target usato nella selezione.");
          bets.add(
              new LabBet(
                  expected.id()
                      + ":"
                      + old.number()
                      + ":"
                      + (cups ? "C" : "L")
                      + ":"
                      + fixture.id(),
                  expected.id(),
                  fixture.id(),
                  frame.competitionId(),
                  fixture.competition(),
                  frame.season(),
                  fixture.home(),
                  fixture.away(),
                  frame.kickoff(),
                  cutoff,
                  old.number(),
                  cups,
                  q,
                  choice.probability(),
                  choice.ev(),
                  staking.stake(unit, q.price()),
                  unit,
                  List.copyOf(explanation),
                  old.trainingHash(),
                  frame.features(),
                  "lab-poisson-market-v4/"
                      + expected.config().strategy()
                      + "/features-"
                      + frame.features().size()));
        }
        prior.addAll(current);
        var global = learned.global();
        var snapshot =
            new LabLearning.Snapshot(
                "market-learning-v4",
                learned.fixtures(),
                learned.latestResult(),
                new LabLearning.Calibration(
                    learned.fixtures(),
                    global.fitted(),
                    global.validation(),
                    0,
                    global.rawBrier(),
                    global.calibratedBrier(),
                    false),
                List.of(),
                old.learning() == null ? List.of() : old.learning().signals(),
                rejected,
                learned);
        var step =
            new LabStep(
                expected.id(),
                old.number(),
                cutoff,
                cups,
                old.trainingRows(),
                old.validationRows(),
                old.validationMae(),
                old.model(),
                old.patterns(),
                old.rowsPerCompetition(),
                old.trainingHash(),
                bets.size(),
                old.proposed() + old.excluded() - bets.size(),
                old.policy() + "; sostituzioni solo con vantaggio abbinato verificato",
                exclusions,
                snapshot);
        var raw =
            shadows.stream()
                .filter(b -> b.includeCupScores() == cups && b.step() == old.number())
                .toList();
        batches.add(new LabRunRepository.Batch(step, bets, raw, current));
        progress.item(
            "Ricalibrazione "
                + old.number()
                + " / coppe "
                + cups
                + ": "
                + bets.size()
                + " singole; modello dei gol riutilizzato");
      }
      progress.update("Pubblicazione atomica delle nuove selezioni nello stesso ID");
      runs.replace(
          expected,
          expected.dataHash(),
          batches,
          "Ricalcolo offline dei mercati completato con modelli dei gol e previsioni grezze"
              + " congelati. Sostituzioni ammesse solo dopo verifica cronologica e confronto"
              + " abbinato. Nessuna nuova campagna o download. Esperimento retrospettivo, senza"
              + " garanzia di rendimento futuro.");
    };
  }
}
