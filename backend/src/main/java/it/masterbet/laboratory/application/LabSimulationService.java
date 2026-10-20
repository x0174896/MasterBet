package it.masterbet.laboratory.application;

import it.masterbet.betting.domain.MarketSettlement;
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

  private record Candidate(
      QuotedOutcome quote, double rawProbability, double probability, double ev) {}

  private record Selection(
      Optional<Candidate> best,
      Optional<Candidate> shadow,
      Map<String, Integer> rejected,
      List<LabMarketFrame.Candidate> controls,
      Map<String, String> policyPicks) {}

  private final LabRepository source;
  private final LabRunRepository runs;
  private final PoissonTrainer trainer;
  private final ProfitTargetStaking staking;
  private final LabDataValidation validation;
  private final LabForecastHistory history;
  private final LabFeatures features = new LabFeatures();
  private final LabPatternMiner patterns = new LabPatternMiner();
  private final LabLearning learning = new LabLearning();
  private final LabSignalAudit signalAudit = new LabSignalAudit();
  private final LabMarketLearning marketLearning = new LabMarketLearning();
  private final LabMarketSelector marketSelector = new LabMarketSelector();
  private final MarketSettlement settlement = new MarketSettlement();

  public LabSimulationService(
      LabRepository source,
      LabRunRepository runs,
      PoissonTrainer trainer,
      ProfitTargetStaking staking,
      LabDataValidation validation,
      LabForecastHistory history) {
    this.source = source;
    this.runs = runs;
    this.trainer = trainer;
    this.staking = staking;
    this.validation = validation;
    this.history = history;
  }

  public Consumer<LabJobs.Progress> calculate(LabRunConfig config) {
    return calculate(config, null);
  }

  public Consumer<LabJobs.Progress> recalculate(LabRunRepository.Run run) {
    return calculate(run.config(), run);
  }

  private Consumer<LabJobs.Progress> calculate(
      LabRunConfig config, LabRunRepository.Run replacement) {
    return progress -> {
      String id = replacement == null ? UUID.randomUUID().toString() : replacement.id();
      // Calculation is strictly offline and never rewrites downloaded sources or alignments.
      var reference = reference(config);
      var validated = new HashSet<>(validation.verify(config).eligible());
      Map<Integer, String> competitionNames = new HashMap<>();
      source.competitions().forEach(c -> competitionNames.put(c.id(), c.name()));
      var common =
          source.competitions().stream()
              .filter(
                  c ->
                      !c.cup()
                          && validated.contains(c.id())
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
      Map<String, LabMatch> resultById = new HashMap<>();
      all.forEach(m -> resultById.put(m.fixture().id(), m));
      var referenceResults =
          reference == null ? Map.<String, LabMatch>of() : runs.results(reference.id());
      var referenceBets = reference == null ? List.<LabBet>of() : runs.bets(reference.id());
      progress.update("Verifica e hash degli archivi locali");
      String hash = source.fingerprint();
      if (replacement == null) runs.create(id, config, hash);
      else if (!replacement.equals(runs.run(id))
          || !hash.equals(replacement.dataHash())
          || config.strategy().equals("BASELINE")
          || !config.mode().equals("HISTORICAL"))
        throw new IllegalStateException(
            "Campagna o sorgenti cambiate: ricalcolo annullato senza modifiche");
      List<LabRunRepository.Batch> pending = new ArrayList<>();
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
        Map<Boolean, List<Integer>> previousDisabled = new HashMap<>();
        Map<Boolean, Map<String, LabBet>> shadows = new HashMap<>();
        shadows.put(false, new HashMap<>());
        shadows.put(true, new HashMap<>());
        Map<Boolean, List<LabMarketFrame>> marketFrames = new HashMap<>();
        marketFrames.put(false, new ArrayList<>());
        marketFrames.put(true, new ArrayList<>());
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
            List<LabSignalAudit.Check> signalChecks = List.of();
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
              if (Set.of("ADAPTIVE", "SIGNALS").contains(config.strategy())) {
                Instant auditEnd =
                    auditRows.stream()
                        .map(r -> r.source().fixture().endedAt())
                        .max(Comparator.naturalOrder())
                        .orElse(asOf);
                var safeValidation =
                    training.subList(split, training.size()).stream()
                        .filter(r -> r.source().fixture().kickoff().isAfter(auditEnd))
                        .toList();
                signalChecks =
                    signalAudit.evaluate(
                        audit,
                        safeValidation.stream().map(Row::x).toList(),
                        safeValidation.stream().map(Row::y).toList());
              }
              var disabled = LabSignalAudit.disabled(signalChecks);
              model =
                  trainer.train(
                      training.stream()
                          .map(r -> LabSignalAudit.mask(r.x(), disabled))
                          .toArray(double[][]::new),
                      training.stream().map(Row::y).toArray(double[][]::new),
                      disabled.equals(previousDisabled.getOrDefault(cups, List.of()))
                          ? previous.get(cups)
                          : null);
              previous.put(cups, model); // the warm start was fitted on an earlier prefix only
              previousDisabled.put(cups, disabled);
            }
            LabLearning.Snapshot learned = null;
            if (reference != null) {
              Map<String, LabLearning.Observation> observations = new HashMap<>();
              for (var bet : referenceBets)
                if (bet.includeCupScores() == cups)
                  observe(bet, referenceResults.get(bet.fixtureId()), asOf)
                      .ifPresent(o -> observations.put(o.fixtureId(), o));
              for (var bet : shadows.get(cups).values())
                observe(bet, resultById.get(bet.fixtureId()), asOf)
                    .ifPresent(o -> observations.put(o.fixtureId(), o));
              learned =
                  learning.fit(
                      List.copyOf(observations.values()),
                      asOf,
                      Set.of("ADAPTIVE", "FILTERED").contains(config.strategy()),
                      signalChecks);
              var markets =
                  marketLearning.fit(
                      history.observations(
                          marketFrames.get(cups), resultById, asOf, config.unitProfit()),
                      asOf,
                      Set.of("ADAPTIVE", "FILTERED").contains(config.strategy()),
                      history.policies(
                          marketFrames.get(cups), resultById, asOf, config.unitProfit()),
                      history.replacements(
                          marketFrames.get(cups), resultById, asOf, config.unitProfit()));
              learned =
                  new LabLearning.Snapshot(
                      "market-learning-v4",
                      learned.observations(),
                      markets.latestResult(),
                      learned.calibration(),
                      List.of(),
                      signalChecks,
                      Map.of(),
                      markets);
            }
            Map<String, Integer> rejectedCandidates = new TreeMap<>();
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
            List<LabBet> shadowProposals = new ArrayList<>();
            List<LabMarketFrame> frameProposals = new ArrayList<>();
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
              var selection =
                  choose(
                      new GoalProbabilities(model.predict(input.values())),
                      source.quotes(target.fixture().id()),
                      target.fixture().kickoff(),
                      config.mode().equals("OPERATIONAL") ? cutoff : null,
                      learned,
                      target.fixture().tournamentId(),
                      target.season(),
                      Arrays.stream(input.values()).boxed().toList());
              selection
                  .rejected()
                  .forEach((key, count) -> rejectedCandidates.merge(key, count, Integer::sum));
              if (reference != null && !selection.controls().isEmpty()) {
                var frame =
                    new LabMarketFrame(
                        target.fixture().id(),
                        target.fixture().tournamentId(),
                        target.season(),
                        target.fixture().kickoff(),
                        cutoff,
                        cups,
                        Arrays.stream(input.values()).boxed().toList(),
                        selection.controls(),
                        selection.policyPicks());
                frameProposals.add(frame);
                marketFrames.get(cups).add(frame);
              }
              // Raw best prediction is kept even if the calibrated policy abstains. Its result is
              // admitted only in a later step, preventing selective learning from accepted bets.
              if (reference != null && selection.shadow().isPresent()) {
                var raw = selection.shadow().get();
                shadows
                    .get(cups)
                    .put(
                        target.fixture().id(),
                        new LabBet(
                            "shadow:" + target.fixture().id(),
                            id,
                            target.fixture().id(),
                            target.fixture().tournamentId(),
                            target.fixture().competition(),
                            target.season(),
                            target.fixture().home(),
                            target.fixture().away(),
                            target.fixture().kickoff(),
                            cutoff,
                            step,
                            cups,
                            raw.quote(),
                            raw.rawProbability(),
                            raw.rawProbability() * raw.quote().price().doubleValue() - 1,
                            staking.stake(config.unitProfit(), raw.quote().price()),
                            config.unitProfit(),
                            List.of(),
                            trainingHash,
                            Arrays.stream(input.values()).boxed().toList(),
                            "shadow-v1"));
                shadowProposals.add(shadows.get(cups).get(target.fixture().id()));
              }
              var best = selection.best();
              if (best.isEmpty()) {
                exclusions.put(
                    target.fixture().home()
                        + " - "
                        + target.fixture().away()
                        + " ("
                        + target.fixture().id()
                        + ")",
                    "Nessuna quota ammessa: EV >= 5%, probabilita >= 50%, quota <= 5"
                        + (learned == null ? "" : "; controlli appresi: " + selection.rejected()));
                excluded++;
                continue;
              }
              var selected = best.get();
              var fixture = target.fixture();
              List<String> explanation = new ArrayList<>(input.evidence());
              if (learned != null) {
                var conditional =
                    learned.markets() == null
                        ? Optional.<LabMarketLearning.Calibration>empty()
                        : learned.markets().calibration(LabMarketFrame.family(selected.quote()));
                explanation.add(
                    String.format(
                        Locale.ITALY,
                        "Probabilita prima della calibrazione %.1f%%, dopo %.1f%%; %d osservazioni"
                            + " pregresse. %s",
                        100 * selected.rawProbability(),
                        100 * selected.probability(),
                        conditional
                            .map(LabMarketLearning.Calibration::support)
                            .orElse(learned.observations()),
                        conditional.isPresent()
                            ? "Verifica specifica del mercato e della fascia; alternative incluse"
                                + " nei controlli."
                            : learned.calibration().accepted()
                                ? "Correzione verificata sul suffisso pregresso."
                                : "Correzione non applicata: evidenza insufficiente o mancato"
                                    + " miglioramento."));
                if (conditional.isPresent())
                  explanation.add(
                      selection.shadow().map(c -> c.quote().equals(selected.quote())).orElse(false)
                          ? "Confermata la prima scelta grezza dopo i controlli specifici del"
                              + " mercato."
                          : "Mercato alternativo alla prima scelta grezza, ammesso dopo i propri"
                              + " controlli di affidabilita.");
                signalChecks.stream()
                    .filter(LabSignalAudit.Check::disabled)
                    .forEach(
                        c ->
                            explanation.add(
                                "Gruppo statistico escluso dopo verifica: " + c.group()));
              }
              explanation.add(
                  "Ultimo prezzo osservato "
                      + Duration.between(selected.quote().observedAt(), target.fixture().kickoff())
                          .toMinutes()
                      + " minuti prima del calcio d'inizio; disponibilita storica, eseguibilita non"
                      + " certificata.");
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
                      (reference == null
                              ? "lab-poisson-v1"
                              : "lab-poisson-market-v4/" + config.strategy())
                          + "/features-"
                          + input.values().length));
            }
            if (learned != null)
              learned =
                  new LabLearning.Snapshot(
                      learned.version(),
                      learned.observations(),
                      learned.latestResult(),
                      learned.calibration(),
                      learned.rules(),
                      signalChecks,
                      rejectedCandidates,
                      learned.markets());
            var batch =
                new LabRunRepository.Batch(
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
                        exclusions,
                        learned),
                    proposed,
                    shadowProposals,
                    frameProposals);
            if (replacement == null) runs.save(batch);
            else pending.add(batch);
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
        String report =
            "Calcolo offline completato in entrambe le modalita. Gli incontri senza sette"
                + " precedenti, quote/mercati idonei o dati sufficienti sono esclusi, non"
                + " forzati. Strategia "
                + config.strategy()
                + (reference == null
                    ? "."
                    : ". Calibrazione per mercato/fascia, alternative controllate e filtri"
                        + " specifici del mercato.")
                + " Esperimento retrospettivo: nessuna garanzia di rendimento futuro.";
        if (replacement == null) runs.finish(id, "COMPLETED", report);
        else {
          progress.update("Pubblicazione atomica del ricalcolo nella campagna " + id);
          runs.replace(replacement, hash, pending, report);
        }
      } catch (Exception e) {
        if (replacement == null) runs.finish(id, "FAILED", LabJobs.safe(e));
        throw e;
      }
    };
  }

  private Selection choose(
      GoalProbabilities model,
      List<LabQuote> quotes,
      Instant kickoff,
      Instant liveCutoff,
      LabLearning.Snapshot learned,
      int competition,
      String season,
      List<Double> input) {
    if (learned != null && learned.markets() != null) {
      var selected =
          marketSelector.select(
              model, quotes, kickoff, learned.markets(), competition, season, input);
      return new Selection(
          selected
              .best()
              .map(
                  c ->
                      new Candidate(
                          c.source().quote(), c.source().probability(), c.probability(), c.ev())),
          selected
              .primary()
              .map(
                  c ->
                      new Candidate(
                          c.quote(),
                          c.probability(),
                          c.probability(),
                          c.probability() * c.quote().price().doubleValue() - 1)),
          selected.rejected(),
          selected.controls(),
          selected.policyPicks());
    }
    List<Candidate> candidates = new ArrayList<>();
    List<Candidate> shadows = new ArrayList<>();
    Map<String, Integer> rejected = new TreeMap<>();
    for (var history : quotes) {
      var q = liveCutoff == null ? history.closing(kickoff) : history.asOf(liveCutoff, kickoff);
      if (q.isEmpty() || q.get().price().compareTo(new BigDecimal("5")) > 0) continue;
      var probability = model.probability(q.get());
      if (probability.isEmpty()) continue;
      double raw = probability.getAsDouble(), rawEv = raw * q.get().price().doubleValue() - 1;
      // The calibrator was learned on this eligibility regime; do not promote unsupported events.
      if (raw < .5 || rawEv < .05) continue;
      shadows.add(new Candidate(q.get(), raw, raw, rawEv));
      double calibrated = learned == null ? raw : learned.calibration().apply(raw);
      double ev = calibrated * q.get().price().doubleValue() - 1;
      if (calibrated < .5 || ev < .05) {
        if (raw >= .5 && rawEv >= .05)
          rejected.merge("Calibrazione: probabilita o EV insufficienti", 1, Integer::sum);
        continue;
      }
      var reason =
          learned == null
              ? Optional.<String>empty()
              : learning.rejection(learned, q.get(), competition, season, input, kickoff, raw);
      if (reason.isPresent()) {
        rejected.merge(reason.get(), 1, Integer::sum);
        continue;
      }
      candidates.add(new Candidate(q.get(), raw, calibrated, ev));
    }
    var order =
        Comparator.comparingDouble(Candidate::ev)
            .reversed()
            .thenComparing(c -> c.quote().bookmaker())
            .thenComparing(c -> c.quote().market().id())
            .thenComparing(c -> c.quote().outcomeId());
    return new Selection(
        candidates.stream().min(order),
        shadows.stream().min(order),
        Map.copyOf(rejected),
        List.of(),
        Map.of());
  }

  private LabRunRepository.Run reference(LabRunConfig config) {
    if (config.strategy().equals("BASELINE")) return null;
    var parent = runs.run(config.referenceRunId());
    var c = parent.config();
    if (!parent.status().equals("COMPLETED")
        || !c.strategy().equals("BASELINE")
        || !c.mode().equals("HISTORICAL")
        || !c.from().equals(config.from())
        || !c.to().equals(config.to())
        || !new HashSet<>(c.competitionIds()).equals(new HashSet<>(config.competitionIds()))
        || c.unitProfit().compareTo(config.unitProfit()) != 0
        || !c.mode().equals(config.mode()))
      throw new IllegalArgumentException(
          "L'esperimento deve conservare periodo, campionati e puntata della campagna originale");
    return parent;
  }

  private Optional<LabLearning.Observation> observe(LabBet bet, LabMatch result, Instant cutoff) {
    if (result == null
        || result.score() == null
        || !result.fixture().finishedBefore(cutoff)
        || !bet.availableAt().isBefore(cutoff)) return Optional.empty();
    boolean won = settlement.won(bet.quote(), result.score());
    double net =
        won
            ? bet.stake()
                .multiply(bet.quote().price().subtract(BigDecimal.ONE))
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .doubleValue()
            : -bet.stake().doubleValue();
    return Optional.of(
        new LabLearning.Observation(
            bet.fixtureId(),
            bet.competitionId(),
            bet.season(),
            bet.kickoff(),
            bet.availableAt(),
            result.fixture().endedAt(),
            bet.quote(),
            bet.probability(),
            won,
            bet.stake().doubleValue(),
            net,
            bet.features()));
  }

  private List<LabMatch> visible(List<LabMatch> all, Instant cutoff) {
    return all.stream().filter(m -> m.fixture().finishedBefore(cutoff)).toList();
  }
}
