package it.masterbet.laboratory.domain;

import it.masterbet.odds.domain.QuotedOutcome;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

/** All learning labels must already be available at the simulated cut. No target score API. */
public final class LabLearning {
  public record Observation(
      String fixtureId,
      int competitionId,
      String season,
      Instant kickoff,
      Instant predictedAt,
      Instant settledAt,
      QuotedOutcome quote,
      double probability,
      boolean won,
      double stake,
      double net,
      List<Double> features) {}

  public record Calibration(
      int support,
      int fitted,
      int validation,
      double offset,
      Double rawBrier,
      Double calibratedBrier,
      boolean accepted) {
    public double apply(double probability) {
      double p = Math.max(.001, Math.min(.999, probability));
      return accepted ? logistic(logit(p) + offset) : probability;
    }
  }

  public record Rule(
      String key,
      String description,
      int support,
      int firstSupport,
      int secondSupport,
      double firstRoi,
      double secondRoi,
      double comparisonFirstRoi,
      double comparisonSecondRoi,
      double upperMeanReturn,
      boolean excluded) {}

  public record Snapshot(
      String version,
      int observations,
      Instant latestResult,
      Calibration calibration,
      List<Rule> rules,
      List<LabSignalAudit.Check> signals,
      Map<String, Integer> rejectedCandidates) {
    public Snapshot {
      rules = rules == null ? List.of() : List.copyOf(rules);
      signals = signals == null ? List.of() : List.copyOf(signals);
      rejectedCandidates = rejectedCandidates == null ? Map.of() : Map.copyOf(rejectedCandidates);
    }
  }

  public Snapshot fit(
      List<Observation> history,
      Instant cutoff,
      boolean adaptive,
      List<LabSignalAudit.Check> signals) {
    var past =
        history.stream()
            .filter(
                o ->
                    o.settledAt().isBefore(cutoff)
                        && o.predictedAt().isBefore(o.settledAt())
                        && o.predictedAt().isBefore(cutoff))
            .sorted(
                Comparator.comparing(Observation::settledAt).thenComparing(Observation::fixtureId))
            .toList();
    return new Snapshot(
        "learning-v1",
        past.size(),
        past.isEmpty() ? null : past.getLast().settledAt(),
        calibrate(past),
        adaptive ? rules(past) : List.of(),
        signals,
        Map.of());
  }

  public Optional<String> rejection(
      Snapshot snapshot,
      QuotedOutcome quote,
      int league,
      String season,
      List<Double> features,
      Instant kickoff,
      double rawProbability) {
    return keys(quote, league, season, features, kickoff, rawProbability).stream()
        .filter(k -> snapshot.rules().stream().anyMatch(r -> r.excluded() && r.key().equals(k)))
        .findFirst();
  }

  public List<String> keys(
      QuotedOutcome quote,
      int league,
      String season,
      List<Double> features,
      Instant kickoff,
      double rawProbability) {
    List<String> result = new ArrayList<>();
    result.add(
        "market:" + quote.market().type() + ":" + quote.market().period() + ":" + quote.outcome());
    result.add("league:" + league + ":" + season);
    double ev = rawProbability * quote.price().doubleValue() - 1;
    result.add("ev:" + (ev < .15 ? "5-15" : ev < .30 ? "15-30" : "30-plus"));
    result.add(
        "odds:"
            + (quote.price().doubleValue() < 1.5
                ? "below-1.5"
                : quote.price().doubleValue() < 2.5 ? "1.5-2.5" : "2.5-plus"));
    if (features.size() >= 32) {
      if (features.get(21) + features.get(24) >= 4) result.add("calendar:busy");
      if (features.get(28) == 1 && features.get(31) == 1) result.add("previous:both-ht-zero");
      if (features.get(26) == 1 || features.get(29) == 1) result.add("previous:conceded-three");
    }
    if (Duration.between(quote.observedAt(), kickoff).toHours() >= 24)
      result.add("quote:older-24h");
    return List.copyOf(result);
  }

  private Calibration calibrate(List<Observation> past) {
    if (past.size() < 100) return new Calibration(past.size(), 0, 0, 0, null, null, false);
    int split = (int) (past.size() * .7);
    // Keep simultaneous results on the same side of the split.
    while (split > 0
        && split < past.size()
        && past.get(split - 1).settledAt().equals(past.get(split).settledAt())) split--;
    if (split < 60 || past.size() - split < 30)
      return new Calibration(past.size(), 0, 0, 0, null, null, false);
    double offset = 0;
    for (int iteration = 0; iteration < 30; iteration++) {
      double gradient = 10 * offset, curvature = 10;
      for (var o : past.subList(0, split)) {
        double p = logistic(logit(o.probability()) + offset);
        gradient += p - (o.won() ? 1 : 0);
        curvature += p * (1 - p);
      }
      double next = Math.max(-1.5, Math.min(1.5, offset - gradient / curvature));
      if (Math.abs(next - offset) < 1e-9) {
        offset = next;
        break;
      }
      offset = next;
    }
    double raw = 0, adjusted = 0, rawLog = 0, adjustedLog = 0;
    for (var o : past.subList(split, past.size())) {
      double actual = o.won() ? 1 : 0, p = o.probability();
      double corrected = logistic(logit(p) + offset);
      raw += Math.pow(p - actual, 2);
      adjusted += Math.pow(corrected - actual, 2);
      rawLog += logLoss(p, actual);
      adjustedLog += logLoss(corrected, actual);
    }
    int n = past.size() - split;
    boolean accepted = adjusted / n + .001 < raw / n && adjustedLog < rawLog;
    return new Calibration(past.size(), split, n, offset, raw / n, adjusted / n, accepted);
  }

  private List<Rule> rules(List<Observation> past) {
    if (past.size() < 200) return List.of();
    // Calendar blocks prevent a busy week from masquerading as a large independent sample.
    var weeks = past.stream().map(o -> week(o.settledAt())).distinct().sorted().toList();
    if (weeks.size() < 6) return List.of();
    long middle = weeks.get(weeks.size() / 2);
    var first = past.stream().filter(o -> week(o.settledAt()) < middle).toList();
    var second = past.stream().filter(o -> week(o.settledAt()) >= middle).toList();
    Set<String> candidates = new TreeSet<>();
    past.forEach(
        o ->
            candidates.addAll(
                keys(
                    o.quote(),
                    o.competitionId(),
                    o.season(),
                    o.features(),
                    o.kickoff(),
                    o.probability())));
    List<Rule> result = new ArrayList<>();
    for (String key : candidates) {
      Predicate<Observation> condition =
          o ->
              keys(
                      o.quote(),
                      o.competitionId(),
                      o.season(),
                      o.features(),
                      o.kickoff(),
                      o.probability())
                  .contains(key);
      var a = first.stream().filter(condition).toList();
      var b = second.stream().filter(condition).toList();
      if (a.size() < 30 || b.size() < 30) continue;
      var otherA = first.stream().filter(condition.negate()).toList();
      var otherB = second.stream().filter(condition.negate()).toList();
      if (otherA.size() < 30 || otherB.size() < 30) continue;
      var own = past.stream().filter(condition).toList();
      // Conservative weekly-cluster upper bound; an exploratory heuristic, not a causal test.
      var weekly =
          own.stream().collect(java.util.stream.Collectors.groupingBy(o -> week(o.settledAt())));
      double mean = own.stream().mapToDouble(this::flatReturn).average().orElse(0);
      double squared = 0;
      for (var group : weekly.values()) {
        double residual = group.stream().mapToDouble(o -> flatReturn(o) - mean).sum();
        squared += residual * residual;
      }
      double se = Math.sqrt(squared * weekly.size() / Math.max(1., weekly.size() - 1)) / own.size();
      double upper = mean + 2.58 * se;
      double ar = roi(a), br = roi(b), ac = roi(otherA), bc = roi(otherB);
      boolean excluded =
          weekly.size() >= 6 && upper < 0 && ar < 0 && br < 0 && ar + .02 < ac && br + .02 < bc;
      result.add(
          new Rule(
              key,
              description(key),
              own.size(),
              a.size(),
              b.size(),
              ar,
              br,
              ac,
              bc,
              upper,
              excluded));
    }
    return List.copyOf(result);
  }

  private String description(String key) {
    if (key.startsWith("ev:")) return "Fascia EV grezzo " + key.substring(3) + "%";
    if (key.startsWith("odds:")) return "Fascia di quota " + key.substring(5);
    if (key.equals("calendar:busy"))
      return "Almeno quattro incontri complessivi nei 14 giorni precedenti";
    if (key.equals("previous:both-ht-zero"))
      return "Entrambe le squadre senza gol nel precedente primo tempo";
    if (key.equals("previous:conceded-three"))
      return "Almeno una squadra reduce da tre o piu gol subiti";
    if (key.equals("quote:older-24h"))
      return "Ultimo prezzo osservato oltre 24 ore prima della partita";
    return key.startsWith("league:")
        ? "Campionato e stagione " + key.substring(7)
        : "Categoria " + key.substring(7);
  }

  private double roi(List<Observation> observations) {
    return observations.stream().mapToDouble(Observation::net).sum()
        / Math.max(.01, observations.stream().mapToDouble(Observation::stake).sum());
  }

  private double flatReturn(Observation o) {
    return o.won() ? o.quote().price().doubleValue() - 1 : -1;
  }

  private static long week(Instant date) {
    return Math.floorDiv(date.getEpochSecond(), 7 * 86400);
  }

  private static double logit(double p) {
    p = Math.max(.001, Math.min(.999, p));
    return Math.log(p / (1 - p));
  }

  private static double logistic(double value) {
    return 1 / (1 + Math.exp(-value));
  }

  private static double logLoss(double p, double actual) {
    p = Math.max(.001, Math.min(.999, p));
    return -actual * Math.log(p) - (1 - actual) * Math.log(1 - p);
  }
}
