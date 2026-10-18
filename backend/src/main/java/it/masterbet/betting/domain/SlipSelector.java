package it.masterbet.betting.domain;

import it.masterbet.betting.domain.BettingRound.*;
import it.masterbet.simulation.domain.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SlipSelector {
  public List<Slip> select(
      Snapshot snapshot, List<Prediction> predictions, BettingConfig config, BigDecimal available) {
    var candidates =
        predictions.stream()
            .filter(
                p ->
                    p.suggested()
                        && p.probability() != null
                        && Double.isFinite(p.probability())
                        && p.probability() >= .5
                        && p.probability() < 1
                        && Double.isFinite(p.expectedReturn())
                        && p.quote().bookmaker().equals("unibet")
                        && p.quote().price().compareTo(new BigDecimal("5")) <= 0)
            .sorted(
                Comparator.comparingDouble((Prediction p) -> p.expectedReturn())
                    .reversed()
                    .thenComparing(p -> p.quote().fixtureId())
                    .thenComparing(p -> p.quote().market().id())
                    .thenComparing(p -> p.quote().outcomeId()))
            .toList();
    Map<String, Leg> unique = new LinkedHashMap<>();
    for (var p : candidates) {
      var q = p.quote();
      var f =
          snapshot.dataset().targets().stream()
              .filter(t -> t.id().equals(q.fixtureId()))
              .findFirst()
              .orElseThrow();
      if (q.observedAt().isAfter(snapshot.dataset().cutoff())
          || !f.kickoff().isAfter(snapshot.dataset().cutoff()))
        throw new IllegalArgumentException("Quota futura nel portafoglio");
      unique.putIfAbsent(
          q.fixtureId(), new Leg(q, f.home(), f.away(), f.kickoff(), p.probability()));
    }
    List<Leg> legs = new ArrayList<>(unique.values());
    List<Slip> slips = new ArrayList<>();
    BigDecimal budget =
        config.unitValue().multiply(config.maxRoundUnits()).min(available).max(BigDecimal.ZERO);
    BigDecimal spent = BigDecimal.ZERO;
    int singles = legs.size() >= 6 ? 4 : Math.min(5, legs.size());
    for (int i = 0; i < singles; i++) {
      var slip = slip("S" + (i + 1), List.of(legs.get(i)), new BigDecimal("0.4"), config);
      if (spent.add(slip.stake()).compareTo(budget) <= 0) {
        slips.add(slip);
        spent = spent.add(slip.stake());
      }
    }
    if (legs.size() >= 6) {
      var slip = slip("D1", List.of(legs.get(4), legs.get(5)), new BigDecimal("0.25"), config);
      if (spent.add(slip.stake()).compareTo(budget) <= 0) slips.add(slip);
    }
    return List.copyOf(slips);
  }

  private Slip slip(String id, List<Leg> legs, BigDecimal units, BettingConfig config) {
    BigDecimal price = BigDecimal.ONE;
    double probability = 1;
    for (var leg : legs) {
      price = price.multiply(leg.quote().price());
      probability *= leg.probability();
    }
    return new Slip(
        id,
        legs.size() == 1 ? "SINGLE" : "DOUBLE",
        "unibet",
        legs,
        units,
        config.unitValue().multiply(units).setScale(2, RoundingMode.DOWN),
        price,
        probability,
        price.doubleValue() * probability - 1,
        "EV >= 5%, probabilita stimata >= 50%, quota singola <= 5; una esposizione per partita. "
            + (legs.size() == 1
                ? "Puntata fissa, nessun recupero delle perdite."
                : "Prodotto simulato delle quote; probabilita sotto ipotesi di indipendenza,"
                    + " multipla non verificata dal bookmaker."));
  }
}
