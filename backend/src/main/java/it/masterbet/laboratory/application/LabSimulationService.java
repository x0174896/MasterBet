package it.masterbet.laboratory.application;

import it.masterbet.betting.domain.ProfitTargetStaking;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import it.masterbet.model.domain.*;
import it.masterbet.odds.domain.QuotedOutcome;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class LabSimulationService {
  private record Row(LabMatch source, double[] x, double[] y) {}

  private record Candidate(QuotedOutcome quote, double probability, double ev) {}

  private final LabRepository source;
  private final LabRunRepository runs;
  private final PoissonTrainer trainer;
  private final ProfitTargetStaking staking;
  private final LabFeatures features = new LabFeatures();
  private final LabPatternMiner patterns = new LabPatternMiner();

  public LabSimulationService(
      LabRepository source,
      LabRunRepository runs,
      PoissonTrainer trainer,
      ProfitTargetStaking staking) {
    this.source = source;
    this.runs = runs;
    this.trainer = trainer;
    this.staking = staking;
  }

  public Consumer<LabJobs.Progress> calculate(LabRunConfig config) {
    return progress -> {
      String id = UUID.randomUUID().toString();
      source.reconcile();
      Map<Integer, String> competitionNames = new HashMap<>();
      source.competitions().forEach(c -> competitionNames.put(c.id(), c.name()));
      var common =
          source.competitions().stream()
              .filter(
                  c ->
                      !c.cup()
                          && c.oddsTournamentId() != null
                          && (config.competitionIds().isEmpty()
                              || config.competitionIds().contains(c.id())))
              .map(LabCompetition::id)
              .collect(java.util.stream.Collectors.toSet());
      var all =
          source.matches().stream()
              .filter(m -> m.cup() || common.contains(m.fixture().tournamentId()))
              .filter(m -> !m.fixture().id().startsWith("odds:"))
              .toList();
      var priced = source.quotedFixtures();
      progress.update("Verifica e hash degli archivi locali");
      String hash = source.fingerprint();
      runs.create(id, config, hash);
      try {
        if (common.isEmpty() || all.isEmpty())
          throw new IllegalArgumentException(
              "Scaricare catalogo comune e risultati prima del calcolo");
        Instant end = config.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant first =
            config.mode().equals("OPERATIONAL")
                ? Instant.now()
                : config.from().atStartOfDay().toInstant(ZoneOffset.UTC);
        if (config.mode().equals("OPERATIONAL")) end = first.plus(Duration.ofDays(7));
        var targets = all.stream().filter(m -> !m.cup()).toList();
        int total = (int) ((Duration.between(first, end).toDays() + 6) / 7) * 2;
        progress.total(total);
        Map<Boolean, List<Row>> rows = new HashMap<>();
        for (boolean cup : List.of(false, true)) {
          List<Row> prepared = new ArrayList<>();
          for (var m : all)
            if (!m.cup() && m.score() != null && m.fixture().kickoff().isBefore(end)) {
              // Row inputs use only matches that were already complete at that row's kickoff.
              var input =
                  features.build(
                      m.fixture(),
                      m.season(),
                      m.fixture().kickoff(),
                      visible(all, m.fixture().kickoff()),
                      cup);
              if (input.homeCount() >= 7 && input.awayCount() >= 7)
                prepared.add(
                    new Row(
                        m,
                        input.values(),
                        new double[] {
                          m.score().homeHt(),
                          m.score().awayHt(),
                          m.score().homeSecondHalf(),
                          m.score().awaySecondHalf()
                        }));
            }
          prepared.sort(Comparator.comparing(r -> r.source().fixture().kickoff()));
          rows.put(cup, prepared);
        }
        int step = 0;
        Map<Boolean, PoissonModel> previous = new HashMap<>();
        Map<Boolean, PoissonModel> previousAudit = new HashMap<>();
        Map<Boolean, Set<String>> previousAuditRows = new HashMap<>();
        for (Instant cutoff = first;
            cutoff.isBefore(end);
            cutoff = cutoff.plus(Duration.ofDays(7))) {
          step++;
          Instant boundary = cutoff.plus(Duration.ofDays(7));
          if (boundary.isAfter(end)) boundary = end;
          for (boolean cups : List.of(false, true)) {
            Instant asOf = cutoff;
            var training =
                rows.get(cups).stream()
                    .filter(r -> r.source().fixture().finishedBefore(asOf))
                    .toList();
            String trainingHash =
                RawResponseArchive.hash(
                    source.json(
                        training.stream()
                            .map(r -> List.of(r.source().fixture().id(), r.x(), r.y()))
                            .toList()));
            Map<Integer, Integer> perLeague = new TreeMap<>();
            training.forEach(
                r -> perLeague.merge(r.source().fixture().tournamentId(), 1, Integer::sum));
            PoissonModel model = null;
            Double mae = null;
            int validation = 0;
            if (training.size() >= 25) {
              int split = Math.max(20, (int) (training.size() * .8));
              validation = training.size() - split;
              var auditRows = training.subList(0, split);
              var auditIds =
                  auditRows.stream()
                      .map(r -> r.source().fixture().id())
                      .collect(java.util.stream.Collectors.toSet());
              // The validation warm start is reused only if every old observation is in the new
              // TRAIN prefix.
              var audit =
                  trainer.train(
                      auditRows.stream().map(Row::x).toArray(double[][]::new),
                      auditRows.stream().map(Row::y).toArray(double[][]::new),
                      auditIds.containsAll(previousAuditRows.getOrDefault(cups, Set.of()))
                          ? previousAudit.get(cups)
                          : null);
              previousAudit.put(cups, audit);
              previousAuditRows.put(cups, auditIds);
              double error = 0;
              for (var row : training.subList(split, training.size())) {
                var prediction = audit.predict(row.x());
                for (int k = 0; k < 4; k++) error += Math.abs(prediction[k] - row.y()[k]);
              }
              mae = validation == 0 ? null : error / (validation * 4);
              model =
                  trainer.train(
                      training.stream().map(Row::x).toArray(double[][]::new),
                      training.stream().map(Row::y).toArray(double[][]::new),
                      previous.get(cups));
              previous.put(cups, model); // the warm start was fitted on an earlier prefix only
            }
            var mined =
                patterns.mine(
                    training.stream()
                        .map(
                            r ->
                                new LabPatternMiner.Observation(
                                    competitionNames.getOrDefault(
                                            r.source().fixture().tournamentId(),
                                            r.source().fixture().competition())
                                        + " / "
                                        + r.source().season(),
                                    r.x(),
                                    r.y()))
                        .toList());
            List<LabBet> proposed = new ArrayList<>();
            Map<String, String> exclusions = new TreeMap<>();
            int excluded = 0;
            for (var target : targets) {
              if (target.fixture().kickoff().isBefore(cutoff)
                  || !target.fixture().kickoff().isBefore(boundary)) continue;
              if (config.mode().equals("HISTORICAL") && target.score() == null
                  || config.mode().equals("OPERATIONAL")
                      && !target.fixture().timingBasis().equals("CALCIO_SCHEDULE_1")) {
                exclusions.put(
                    target.fixture().home()
                        + " - "
                        + target.fixture().away()
                        + " ("
                        + target.fixture().id()
                        + ")",
                    "Risultato storico HT/FT mancante, oppure incontro operativo non confermato in"
                        + " calendario");
                excluded++;
                continue;
              }
              var input =
                  features.build(
                      target.fixture(), target.season(), cutoff, visible(all, cutoff), cups);
              if (model == null || input.homeCount() < 7 || input.awayCount() < 7) {
                exclusions.put(
                    target.fixture().home()
                        + " - "
                        + target.fixture().away()
                        + " ("
                        + target.fixture().id()
                        + ")",
                    input.homeCount() < 7 || input.awayCount() < 7
                        ? "Meno di 7 precedenti di campionato per almeno una squadra"
                        : "Meno di 25 osservazioni globali idonee all'addestramento");
                excluded++;
                continue;
              }
              if (!priced.contains(target.fixture().id())) {
                exclusions.put(
                    target.fixture().home()
                        + " - "
                        + target.fixture().away()
                        + " ("
                        + target.fixture().id()
                        + ")",
                    "Quote non disponibili o non abbinate");
                excluded++;
                continue;
              }
              // Selection receives a score-free fixture and frozen input, never
              // LabMatch/target.score.
              var best =
                  choose(
                      new GoalProbabilities(model.predict(input.values())),
                      source.quotes(target.fixture().id()),
                      target.fixture().kickoff(),
                      config.mode().equals("OPERATIONAL") ? cutoff : null);
              if (best.isEmpty()) {
                exclusions.put(
                    target.fixture().home()
                        + " - "
                        + target.fixture().away()
                        + " ("
                        + target.fixture().id()
                        + ")",
                    "Nessuna quota attiva prepartita su mercati supportati con EV >= 5%,"
                        + " probabilita >= 50% e quota <= 5");
                excluded++;
                continue;
              }
              var selected = best.get();
              var fixture = target.fixture();
              List<String> explanation = new ArrayList<>(input.evidence());
              explanation.add(
                  String.format(
                      Locale.ITALY,
                      "Probabilita stimata %.1f%%; quota %s; valore atteso %.1f%%. Migliore singola"
                          + " fra gli esiti idonei, senza conoscere il risultato.",
                      100 * selected.probability(),
                      selected.quote().price().toPlainString(),
                      100 * selected.ev()));
              proposed.add(
                  new LabBet(
                      id + ":" + step + ":" + (cups ? "C" : "L") + ":" + fixture.id(),
                      id,
                      fixture.id(),
                      fixture.tournamentId(),
                      competitionNames.getOrDefault(fixture.tournamentId(), fixture.competition()),
                      target.season(),
                      fixture.home(),
                      fixture.away(),
                      fixture.kickoff(),
                      cutoff,
                      step,
                      cups,
                      selected.quote(),
                      selected.probability(),
                      selected.ev(),
                      staking.stake(config.unitProfit(), selected.quote().price()),
                      config.unitProfit(),
                      List.copyOf(explanation),
                      trainingHash,
                      Arrays.stream(input.values()).boxed().toList(),
                      "lab-poisson-v1/features-" + input.values().length));
            }
            runs.step(
                new LabStep(
                    id,
                    step,
                    cutoff,
                    cups,
                    training.size(),
                    validation,
                    mae,
                    model,
                    mined,
                    Map.copyOf(perLeague),
                    trainingHash,
                    proposed.size(),
                    excluded,
                    "7 precedenti per squadra; EV >= 5%, probabilita >= 50%, quota <= 5; una"
                        + " singola per incontro idoneo; ultimo prezzo prepartita",
                    exclusions),
                proposed);
            progress.item(
                "Settimana "
                    + step
                    + " / "
                    + cutoff
                    + " / coppe "
                    + cups
                    + ": "
                    + proposed.size()
                    + " singole, "
                    + training.size()
                    + " righe pregresse");
          }
        }
        runs.finish(
            id,
            "COMPLETED",
            "Calcolo offline completato in entrambe le modalita. Gli incontri senza sette"
                + " precedenti, quote/mercati idonei o dati sufficienti sono esclusi, non"
                + " forzati.");
      } catch (Exception e) {
        runs.finish(id, "FAILED", LabJobs.safe(e));
        throw e;
      }
    };
  }

  private Optional<Candidate> choose(
      GoalProbabilities model, List<LabQuote> quotes, Instant kickoff, Instant liveCutoff) {
    List<Candidate> candidates = new ArrayList<>();
    for (var history : quotes) {
      var q = liveCutoff == null ? history.closing(kickoff) : history.asOf(liveCutoff, kickoff);
      if (q.isEmpty() || q.get().price().compareTo(new BigDecimal("5")) > 0) continue;
      var probability = model.probability(q.get());
      if (probability.isEmpty() || probability.getAsDouble() < .5) continue;
      double ev = probability.getAsDouble() * q.get().price().doubleValue() - 1;
      if (ev >= .05) candidates.add(new Candidate(q.get(), probability.getAsDouble(), ev));
    }
    return candidates.stream()
        .sorted(
            Comparator.comparingDouble(Candidate::ev)
                .reversed()
                .thenComparing(c -> c.quote().bookmaker())
                .thenComparing(c -> c.quote().market().id())
                .thenComparing(c -> c.quote().outcomeId()))
        .findFirst();
  }

  private List<LabMatch> visible(List<LabMatch> all, Instant cutoff) {
    return all.stream().filter(m -> m.fixture().finishedBefore(cutoff)).toList();
  }
}
