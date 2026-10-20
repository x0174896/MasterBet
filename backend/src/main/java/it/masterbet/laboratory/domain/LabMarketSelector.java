package it.masterbet.laboratory.domain;

import it.masterbet.model.domain.GoalProbabilities;
import java.time.Instant;
import java.util.*;

/**
 * Selection has no access to scores. Unvalidated alternatives are never promoted by a global
 * offset.
 */
public final class LabMarketSelector {
  public record Choice(LabMarketFrame.Candidate source, double probability, double ev) {}

  public record Selection(
      Optional<Choice> best,
      Optional<LabMarketFrame.Candidate> primary,
      List<LabMarketFrame.Candidate> controls,
      Map<String, String> policyPicks,
      Map<String, Integer> rejected) {}

  public Selection select(
      GoalProbabilities model,
      List<LabQuote> quotes,
      Instant kickoff,
      LabMarketLearning.Snapshot learned,
      int competition,
      String season,
      List<Double> features) {
    Map<String, LabMarketFrame.Candidate> representatives = new TreeMap<>();
    for (var history : quotes) {
      var price = history.closing(kickoff);
      if (price.isEmpty() || price.get().price().doubleValue() > 5) continue;
      var probability = model.probability(price.get());
      if (probability.isEmpty()) continue;
      double p = probability.getAsDouble();
      if (p < .5 || p * price.get().price().doubleValue() < 1.05) continue;
      String key = LabMarketFrame.key(price.get());
      var candidate = new LabMarketFrame.Candidate(key, price.get(), p, false);
      representatives.merge(key, candidate, (a, b) -> rawOrder().compare(a, b) <= 0 ? a : b);
    }
    var primary = representatives.values().stream().min(rawOrder());
    var controls =
        representatives.values().stream()
            .map(
                c ->
                    new LabMarketFrame.Candidate(
                        c.key(),
                        c.quote(),
                        c.probability(),
                        primary.map(p -> p.key().equals(c.key())).orElse(false)))
            .toList();
    return select(controls, kickoff, learned, competition, season, features);
  }

  public Selection select(
      List<LabMarketFrame.Candidate> controls,
      Instant kickoff,
      LabMarketLearning.Snapshot learned,
      int competition,
      String season,
      List<Double> features) {
    var primary = controls.stream().filter(LabMarketFrame.Candidate::primary).findFirst();
    List<Choice> calibrated = new ArrayList<>(), allowed = new ArrayList<>();
    Map<String, Integer> rejected = new TreeMap<>();
    var keys = new LabLearning();
    for (var candidate : controls) {
      String family = LabMarketFrame.family(candidate.quote());
      var calibration = learned.calibration(family);
      if (calibration.isEmpty() || !calibration.get().supports(candidate.probability())) {
        rejected.merge("Mercato/fascia senza verifica sufficiente", 1, Integer::sum);
        continue;
      }
      double p = calibration.get().apply(candidate.probability()),
          ev = p * candidate.quote().price().doubleValue() - 1;
      if (p < .5 || ev < .05) {
        rejected.merge("Calibrazione del mercato: probabilita o EV insufficienti", 1, Integer::sum);
        continue;
      }
      var choice = new Choice(candidate, p, ev);
      calibrated.add(choice);
      var conditions =
          keys.keys(
              candidate.quote(), competition, season, features, kickoff, candidate.probability());
      var rejection =
          learned.conditions().stream()
              .filter(f -> f.family().equals(family))
              .flatMap(f -> f.rules().stream())
              .filter(r -> r.excluded() && conditions.contains(r.key()))
              .findFirst();
      if (rejection.isPresent())
        rejected.merge("market:" + family + " | " + rejection.get().key(), 1, Integer::sum);
      else allowed.add(choice);
    }
    Comparator<Choice> order =
        Comparator.comparingDouble(Choice::ev)
            .reversed()
            .thenComparing(c -> c.source().quote().bookmaker())
            .thenComparing(c -> c.source().quote().market().id())
            .thenComparing(c -> c.source().quote().outcomeId());
    Map<String, String> picks = new TreeMap<>();
    // Keep out-of-time shadow replacements before the promotion guard, including every abstention.
    Map<String, Choice> replacementShadows = new TreeMap<>();
    for (var choice : allowed)
      if (!choice.source().primary())
        replacementShadows.merge(
            LabMarketFrame.family(choice.source().quote()),
            choice,
            (a, b) -> order.compare(a, b) <= 0 ? a : b);
    replacementShadows.forEach(
        (family, choice) -> picks.put("alternative:" + family, choice.source().key()));
    if (learned.version().equals("conditional-markets-v2")) {
      var permitted =
          learned.replacements().stream()
              .filter(LabReplacementEvidence.Check::allowed)
              .map(LabReplacementEvidence.Check::family)
              .collect(java.util.stream.Collectors.toSet());
      for (var choice : allowed)
        if (!choice.source().primary()
            && !permitted.contains(LabMarketFrame.family(choice.source().quote())))
          rejected.merge(
              "Sostituzione senza vantaggio verificato: "
                  + LabMarketFrame.family(choice.source().quote()),
              1,
              Integer::sum);
      allowed.removeIf(
          c ->
              !c.source().primary()
                  && !permitted.contains(LabMarketFrame.family(c.source().quote())));
      calibrated.removeIf(
          c ->
              !c.source().primary()
                  && !permitted.contains(LabMarketFrame.family(c.source().quote())));
    }
    var best = allowed.stream().min(order);
    best.ifPresent(c -> picks.put("adaptive", c.source().key()));
    calibrated.stream().min(order).ifPresent(c -> picks.put("calibrated", c.source().key()));
    allowed.stream()
        .filter(c -> c.source().primary())
        .min(order)
        .ifPresent(c -> picks.put("primary-only", c.source().key()));
    return new Selection(
        best, primary, List.copyOf(controls), Map.copyOf(picks), Map.copyOf(rejected));
  }

  private Comparator<LabMarketFrame.Candidate> rawOrder() {
    return Comparator.comparingDouble(
            (LabMarketFrame.Candidate c) -> c.probability() * c.quote().price().doubleValue() - 1)
        .reversed()
        .thenComparing(c -> c.quote().bookmaker())
        .thenComparing(c -> c.quote().market().id())
        .thenComparing(c -> c.quote().outcomeId());
  }
}
