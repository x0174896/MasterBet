package it.masterbet.betting.domain;

import it.masterbet.betting.domain.BettingRound.*;
import it.masterbet.simulation.domain.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SlipSelector {
  private final ProfitTargetStaking staking;

  public SlipSelector(ProfitTargetStaking staking) {
    this.staking = staking;
  }

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
    BigDecimal budget = available.max(BigDecimal.ZERO);
    BigDecimal spent = BigDecimal.ZERO;
    int singles = legs.size() >= 6 ? 4 : Math.min(5, legs.size());
    for (int i = 0; i < singles; i++) {
      var slip = slip("S" + (i + 1), List.of(legs.get(i)), config);
      if (spent.add(slip.stake()).compareTo(budget) <= 0) {
        slips.add(slip);
        spent = spent.add(slip.stake());
      }
    }
    if (legs.size() >= 6) {
      var slip = slip("D1", List.of(legs.get(4), legs.get(5)), config);
      if (spent.add(slip.stake()).compareTo(budget) <= 0) slips.add(slip);
    }
    return List.copyOf(slips);
  }

  private Slip slip(String id, List<Leg> legs, BettingConfig config) {
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
        BigDecimal.ONE,
        staking.stake(config.unitValue(), price),
        price,
        probability,
        price.doubleValue() * probability - 1,
        staking.rationale(legs.size() == 1 ? "SINGLE" : "DOUBLE"));
  }
}
