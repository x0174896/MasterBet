package it.masterbet.simulation.application;

import it.masterbet.football.application.FixtureSource;
import it.masterbet.football.domain.*;
import it.masterbet.football.infrastructure.*;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.odds.application.HistoricalOddsSource;
import it.masterbet.odds.domain.QuotedOutcome;
import it.masterbet.simulation.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NextStepDownloader {
  private final FixtureSource fixtures;
  private final HistoricalOddsSource odds;
  private final NffSchedule official;
  private final NffScoreDownloader scores;
  private final OddsPapiClient client;
  private final StepWindowPlanner planner;

  public NextStepDownloader(
      FixtureSource fixtures,
      HistoricalOddsSource odds,
      NffSchedule official,
      NffScoreDownloader scores,
      OddsPapiClient client,
      StepWindowPlanner planner) {
    this.fixtures = fixtures;
    this.odds = odds;
    this.official = official;
    this.scores = scores;
    this.client = client;
    this.planner = planner;
  }

  public SimulationDataset download(
      Snapshot previous, java.util.function.Consumer<String> progress) {
    var old = previous.dataset();
    var schedule = official.load();
    var window =
        planner.next(
            old.cutoff(),
            schedule.stream().map(NffSchedule.Match::kickoff).toList(),
            Instant.now());
    var cutoff = window.cutoff();
    var end = window.end();
    var slices = planner.slices(old.cutoff(), cutoff);
    var seasonFrom = Instant.parse("2026-01-01T00:00:00Z");
    var seasonTo = Instant.parse("2026-12-01T00:00:00Z");
    var requests = new ArrayList<Map<String, String>>();
    requests.add(
        Map.of("tournamentId", "20", "from", seasonFrom.toString(), "to", seasonTo.toString()));
    slices.forEach(
        s ->
            requests.add(
                Map.of("sportId", "10", "from", s.from().toString(), "to", s.to().toString())));
    client.preflightFixtures(requests);
    progress.accept("Download calendario e verifica delle identita NFF");
    var season =
        fixtures.tournament(20, seasonFrom, seasonTo).stream()
            .filter(f -> Objects.equals(f.seasonId(), 138092L))
            .toList();
    var targetSource =
        season.stream()
            .filter(f -> f.kickoff().isAfter(cutoff) && f.kickoff().isBefore(end))
            .toList();
    var expected =
        schedule.stream()
            .filter(m -> m.kickoff().isAfter(cutoff) && m.kickoff().isBefore(end))
            .toList();
    var targetIds = new HashSet<String>();
    for (var f : targetSource) targetIds.add(official.identify(f, schedule).nffId());
    if (targetIds.isEmpty()
        || !targetIds.equals(
            new HashSet<>(expected.stream().map(NffSchedule.Match::nffId).toList())))
      throw new IllegalArgumentException(
          "Calendario futuro incompleto o settimana senza partite; nessuno scenario pubblicato");
    var targets =
        targetSource.stream()
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
    progress.accept("Download quote storiche al nuovo taglio, prima dei risultati");
    List<QuotedOutcome> quotes = new ArrayList<>();
    for (var f : targets)
      quotes.addAll(odds.downloadAt(f, cutoff, List.of("unibet", "pinnacle", "bet365")));
    Map<String, PlayedMatch> history = new TreeMap<>();
    old.leagueHistory().forEach(m -> history.put(m.fixture().id(), m));
    progress.accept("Download referti ufficiali: primo tempo e finale");
    for (var f : season)
      if (f.finishedBefore(cutoff) && !history.containsKey(f.id()))
        history.put(
            f.id(), new PlayedMatch(f, scores.download(f, official.identify(f, schedule), cutoff)));
    var expectedHistory =
        schedule.stream()
            .filter(m -> !m.kickoff().plus(Duration.ofDays(1)).isAfter(cutoff))
            .toList();
    for (var m : expectedHistory)
      if (history.values().stream()
          .noneMatch(
              p ->
                  p.fixture().kickoff().equals(m.kickoff())
                      && NffSchedule.alias(p.fixture().home()).equals(m.home())
                      && NffSchedule.alias(p.fixture().away()).equals(m.away())))
        throw new IllegalArgumentException(
            "Risultato storico mancante nel provider: NFF " + m.nffId());
    for (var f : old.targets())
      if (f.kickoff().plus(Duration.ofDays(1)).isBefore(cutoff) && !history.containsKey(f.id()))
        throw new IllegalArgumentException("Risultato precedente mancante");
    progress.accept("Aggiornamento calendario coppe e trasferte");
    Map<String, Fixture> calendar = new TreeMap<>();
    old.calendar().forEach(f -> calendar.put(f.id(), f));
    Set<Long> teams = new HashSet<>();
    old.trainingHistory()
        .forEach(
            m -> {
              teams.add(m.fixture().homeId());
              teams.add(m.fixture().awayId());
            });
    season.forEach(
        f -> {
          teams.add(f.homeId());
          teams.add(f.awayId());
        });
    for (var slice : slices)
      for (var f : fixtures.teamsCalendar(teams, slice.from(), slice.to()))
        if (f.finishedBefore(cutoff) && (teams.contains(f.homeId()) || teams.contains(f.awayId())))
          calendar.put(f.id(), f);
    history.values().forEach(m -> calendar.put(m.fixture().id(), m.fixture()));
    int step = Integer.parseInt(old.id().substring(old.id().lastIndexOf("round") + 5)) + 1;
    return new SimulationDataset(
        "eliteserien-2026-round" + step,
        cutoff,
        old.trainingHistory(),
        history.values().stream().sorted(Comparator.comparing(m -> m.fixture().kickoff())).toList(),
        List.copyOf(calendar.values()),
        old.cupResults(),
        targets,
        List.copyOf(quotes));
  }
}
