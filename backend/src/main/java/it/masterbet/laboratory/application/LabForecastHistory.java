package it.masterbet.laboratory.application;

import it.masterbet.betting.domain.*;
import it.masterbet.laboratory.domain.*;
import it.masterbet.odds.domain.QuotedOutcome;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

/** Labels are constructed only after settlement; selection receives score-free frames. */
@Component
public class LabForecastHistory {
  private final ProfitTargetStaking staking;
  private final MarketSettlement settlement = new MarketSettlement();

  public LabForecastHistory(ProfitTargetStaking staking) {
    this.staking = staking;
  }

  public BigDecimal net(QuotedOutcome quote, LabMatch result, BigDecimal unit) {
    var stake = staking.stake(unit, quote.price());
    return settlement.won(quote, result.score())
        ? stake.multiply(quote.price().subtract(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP)
        : stake.negate();
  }

  public List<LabMarketLearning.Observation> observations(
      List<LabMarketFrame> frames, Map<String, LabMatch> results, Instant cutoff, BigDecimal unit) {
    List<LabMarketLearning.Observation> observations = new ArrayList<>();
    for (var frame : frames) {
      var result = results.get(frame.fixtureId());
      if (!available(frame, result, cutoff)) continue;
      for (var candidate : frame.candidates())
        observations.add(
            new LabMarketLearning.Observation(
                new LabLearning.Observation(
                    frame.fixtureId(),
                    frame.competitionId(),
                    frame.season(),
                    frame.kickoff(),
                    frame.predictedAt(),
                    result.fixture().endedAt(),
                    candidate.quote(),
                    candidate.probability(),
                    settlement.won(candidate.quote(), result.score()),
                    staking.stake(unit, candidate.quote().price()).doubleValue(),
                    net(candidate.quote(), result, unit).doubleValue(),
                    frame.features()),
                candidate.primary()));
    }
    return observations;
  }

  public List<LabReplacementEvidence.Observation> replacements(
      List<LabMarketFrame> frames, Map<String, LabMatch> results, Instant cutoff, BigDecimal unit) {
    List<LabReplacementEvidence.Observation> observations = new ArrayList<>();
    for (var frame : frames) {
      var result = results.get(frame.fixtureId());
      if (!available(frame, result, cutoff)) continue;
      var primary =
          frame.candidates().stream()
              .filter(LabMarketFrame.Candidate::primary)
              .findFirst()
              .orElseThrow();
      for (var candidate : frame.candidates()) {
        String family = LabMarketFrame.family(candidate.quote());
        if (!candidate.key().equals(frame.policyPicks().get("alternative:" + family))) continue;
        observations.add(
            new LabReplacementEvidence.Observation(
                frame.fixtureId(),
                family,
                frame.predictedAt(),
                result.fixture().endedAt(),
                staking.stake(unit, candidate.quote().price()).doubleValue(),
                net(candidate.quote(), result, unit).doubleValue(),
                net(primary.quote(), result, unit).doubleValue()));
      }
    }
    return observations;
  }

  public List<LabMarketLearning.PolicyCheck> policies(
      List<LabMarketFrame> frames, Map<String, LabMatch> results, Instant cutoff, BigDecimal unit) {
    List<LabMarketLearning.PolicyCheck> checks = new ArrayList<>();
    for (String policy : List.of("adaptive", "calibrated", "primary-only")) {
      int settled = 0;
      BigDecimal turnover = BigDecimal.ZERO, net = BigDecimal.ZERO;
      for (var frame : frames) {
        var result = results.get(frame.fixtureId());
        if (!available(frame, result, cutoff)) continue;
        var picked =
            frame.candidates().stream()
                .filter(c -> c.key().equals(frame.policyPicks().get(policy)))
                .findFirst();
        if (picked.isEmpty()) continue;
        turnover = turnover.add(staking.stake(unit, picked.get().quote().price()));
        net = net.add(net(picked.get().quote(), result, unit));
        settled++;
      }
      checks.add(
          new LabMarketLearning.PolicyCheck(
              policy, settled, turnover.doubleValue(), net.doubleValue()));
    }
    return checks;
  }

  private boolean available(LabMarketFrame frame, LabMatch result, Instant cutoff) {
    return result != null
        && result.score() != null
        && result.fixture().finishedBefore(cutoff)
        && frame.predictedAt().isBefore(result.fixture().endedAt());
  }
}
