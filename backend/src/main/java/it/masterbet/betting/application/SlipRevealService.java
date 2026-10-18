package it.masterbet.betting.application;

import it.masterbet.betting.domain.*;
import it.masterbet.betting.infrastructure.RevealRepository;
import it.masterbet.football.domain.Fixture;
import it.masterbet.football.infrastructure.*;
import it.masterbet.simulation.infrastructure.SnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class SlipRevealService {
  private final BettingWorkspaceService workspace;
  private final RevealRepository repository;
  private final SnapshotRepository snapshots;
  private final NffSchedule calendar;
  private final NffScoreDownloader scores;
  private final MarketSettlement settlement;

  public SlipRevealService(
      BettingWorkspaceService workspace,
      RevealRepository repository,
      SnapshotRepository snapshots,
      NffSchedule calendar,
      NffScoreDownloader scores,
      MarketSettlement settlement) {
    this.workspace = workspace;
    this.repository = repository;
    this.snapshots = snapshots;
    this.calendar = calendar;
    this.scores = scores;
    this.settlement = settlement;
  }

  public SlipReveal reveal(String scenarioId, String variant, String slipId) {
    var round = workspace.claimReveal(scenarioId, variant, slipId);
    var cached = repository.find(scenarioId, variant, slipId);
    if (cached.isPresent() && !cached.get().status().equals("PENDING")) return cached.get();
    var snapshot = snapshots.find(scenarioId).orElseThrow();
    if (!snapshot.datasetSha256().equals(round.datasetSha256()))
      throw new IllegalStateException("Snapshot diverso dalle scelte salvate");
    var slip =
        (variant.equals("cups") ? round.cups() : round.league())
            .stream().filter(s -> s.id().equals(slipId)).findFirst().orElseThrow();
    Instant now = Instant.now();
    var results = new ArrayList<SlipReveal.Result>();
    for (var leg : slip.legs()) {
      var target =
          snapshot.dataset().targets().stream()
              .filter(f -> f.id().equals(leg.quote().fixtureId()))
              .findFirst()
              .orElseThrow();
      if (target.kickoff().plusSeconds(86400).isAfter(now)) {
        results.add(new SlipReveal.Result(leg, null, null, "Risultato non ancora disponibile"));
        continue;
      }
      var match = calendar.identify(target, calendar.load());
      var availableAt = match.kickoff().plusSeconds(86400);
      if (availableAt.isBefore(target.kickoff().plusSeconds(86400)))
        availableAt = target.kickoff().plusSeconds(86400);
      if (availableAt.isAfter(now)) {
        results.add(new SlipReveal.Result(leg, null, null, "Risultato non ancora disponibile"));
        continue;
      }
      var historical =
          new Fixture(
              target.id(),
              target.homeId(),
              target.awayId(),
              target.home(),
              target.away(),
              target.tournamentId(),
              target.competition(),
              target.seasonId(),
              match.kickoff(),
              availableAt,
              "REVEAL_ONLY_CONSERVATIVE_24H");
      var score = scores.download(historical, match, now);
      results.add(
          new SlipReveal.Result(
              leg,
              score,
              settlement.won(leg.quote(), score),
              "https://www.fotball.no/fotballdata/kamp/?fiksId=" + match.nffId()));
    }
    boolean complete = results.stream().allMatch(r -> r.score() != null);
    boolean won = complete && results.stream().allMatch(r -> Boolean.TRUE.equals(r.won()));
    BigDecimal payout = !complete ? null : won ? slip.potentialPayout() : new BigDecimal("0.00");
    return repository.save(
        new SlipReveal(
            scenarioId,
            variant,
            slipId,
            now,
            complete ? won ? "WON" : "LOST" : "PENDING",
            slip.stake(),
            payout,
            payout == null ? null : payout.subtract(slip.stake()),
            results));
  }
}
