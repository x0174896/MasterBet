package it.masterbet.simulation.application;

import it.masterbet.football.application.*;
import it.masterbet.football.domain.*;
import it.masterbet.odds.application.HistoricalOddsSource;
import it.masterbet.odds.domain.QuotedOutcome;
import it.masterbet.simulation.domain.SimulationDataset;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ScenarioDownloader {
  public static final Instant CUTOFF = Instant.parse("2026-04-29T12:00:00Z");
  private final FixtureSource fixtures;
  private final ScoreSource scores;
  private final HistoricalOddsSource odds;

  public ScenarioDownloader(FixtureSource fixtures, ScoreSource scores, HistoricalOddsSource odds) {
    this.fixtures = fixtures;
    this.scores = scores;
    this.odds = odds;
  }

  public SimulationDataset download() {
    var season =
        fixtures.tournament(
            20, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-05-05T00:00:00Z"));
    var completed = season.stream().filter(f -> f.finishedBefore(CUTOFF)).toList();
    var targets =
        season.stream()
            .filter(f -> !f.kickoff().isBefore(Instant.parse("2026-05-01T00:00:00Z")))
            .map(
                f ->
                    new Fixture(
                        f.id(),
                        f.homeId(),
                        f.awayId(),
                        f.home(),
                        f.away(),
                        f.tournamentId(),
                        f.competition(),
                        f.seasonId(),
                        f.kickoff(),
                        null))
            .toList();
    List<QuotedOutcome> quotes = new ArrayList<>();
    for (var f : targets)
      quotes.addAll(odds.downloadAt(f, CUTOFF, List.of("unibet", "pinnacle", "bet365")));
    var old =
        fixtures
            .tournament(
                20, Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-12-01T00:00:00Z"))
            .stream()
            .filter(
                f ->
                    Objects.equals(f.seasonId(), 127337L)
                        && f.finishedBefore(Instant.parse("2026-01-01T00:00:00Z")))
            .sorted(Comparator.comparing(Fixture::kickoff).thenComparing(Fixture::id))
            .toList();
    if (old.size() < 80)
      throw new IllegalStateException("Meno di 80 partite storiche con fine partita verificabile");
    var sample = old.subList(old.size() - 80, old.size());
    List<PlayedMatch> training = new ArrayList<>(), history = new ArrayList<>();
    for (var f : sample)
      training.add(new PlayedMatch(f, scores.download(f.id(), MatchFormat.REGULATION_ONLY)));
    for (var f : completed)
      history.add(new PlayedMatch(f, scores.download(f.id(), MatchFormat.REGULATION_ONLY)));
    Set<Long> teams = new TreeSet<>();
    for (var f : sample) {
      teams.add(f.homeId());
      teams.add(f.awayId());
    }
    for (var f : completed) {
      teams.add(f.homeId());
      teams.add(f.awayId());
    }
    Map<String, Fixture> calendar = new TreeMap<>();
    for (var f : old) if (f.finishedBefore(CUTOFF)) calendar.put(f.id(), f);
    for (var f : completed) calendar.put(f.id(), f);
    Instant calendarStart = sample.getFirst().kickoff().minus(Duration.ofDays(30));
    for (long team : teams)
      for (var f : fixtures.team(team, calendarStart, CUTOFF))
        if (f.finishedBefore(CUTOFF)) calendar.put(f.id(), f);
    var cupSample =
        calendar.values().stream()
            .filter(
                f ->
                    f.tournamentId() == 29
                        && f.kickoff().isAfter(Instant.parse("2026-04-01T00:00:00Z")))
            .sorted(Comparator.comparing(Fixture::kickoff).reversed())
            .limit(2)
            .toList();
    List<PlayedMatch> cupResults = new ArrayList<>();
    for (var f : cupSample)
      cupResults.add(new PlayedMatch(f, scores.download(f.id(), MatchFormat.KNOCKOUT)));
    return new SimulationDataset(
        "eliteserien-2026-round7",
        CUTOFF,
        List.copyOf(training),
        List.copyOf(history),
        List.copyOf(calendar.values()),
        List.copyOf(cupResults),
        targets,
        List.copyOf(quotes));
  }
}
