package it.masterbet.laboratory.application;

import it.masterbet.betting.domain.MarketSettlement;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LabQueryService {
  public record Coverage(
      int id,
      String name,
      String country,
      boolean cup,
      Integer oddsTournamentId,
      String status,
      int matches,
      int complete,
      int withOdds,
      int completeWithOdds,
      int unlinkedQuotes,
      int readyTeams,
      Instant firstOdds,
      Instant lastOdds) {}

  public record BetView(
      LabBet bet,
      String status,
      BigDecimal net,
      boolean revealed,
      String spoiler,
      Instant resultAvailableAt,
      String timingBasis) {}

  public record Point(
      int index, String betId, Instant settledAt, BigDecimal profit, BigDecimal cumulative) {}

  public record Workspace(
      LabRunRepository.Run run,
      Instant from,
      Instant to,
      Instant cursor,
      boolean includeCupScores,
      int competitionId,
      int totalBets,
      int settled,
      int won,
      int lost,
      BigDecimal net,
      List<BetView> bets,
      int page,
      int pages,
      List<Point> chart,
      List<LabStep> model) {}

  public record TeamStats(
      long id,
      String name,
      int competitionId,
      String competition,
      String season,
      int played,
      int goalsFor,
      int goalsAgainst,
      int firstHalfFor,
      int firstHalfAgainst) {}

  public record FullStatistics(
      int competitionId,
      String competition,
      String season,
      boolean cup,
      it.masterbet.statistics.domain.TeamStatistics team) {}

  private final LabRepository sources;
  private final LabRunRepository runs;
  private final MarketSettlement settlement;

  public LabQueryService(
      LabRepository sources, LabRunRepository runs, MarketSettlement settlement) {
    this.sources = sources;
    this.runs = runs;
    this.settlement = settlement;
  }

  public List<Coverage> coverage() {
    var matches = sources.matches();
    Set<String> priced = sources.quotedFixtures();
    return sources.competitions().stream()
        .map(
            c -> {
              var own = matches.stream().filter(m -> m.fixture().tournamentId() == c.id()).toList();
              var complete = own.stream().filter(m -> m.score() != null).toList();
              Map<String, Integer> counts = new HashMap<>();
              complete.forEach(
                  m -> {
                    counts.merge(m.season() + ":" + m.fixture().homeId(), 1, Integer::sum);
                    counts.merge(m.season() + ":" + m.fixture().awayId(), 1, Integer::sum);
                  });
              var withOdds =
                  own.stream()
                      .filter(
                          m ->
                              !m.fixture().id().startsWith("odds:")
                                  && priced.contains(m.fixture().id()))
                      .toList();
              return new Coverage(
                  c.id(),
                  c.name(),
                  c.country(),
                  c.cup(),
                  c.oddsTournamentId(),
                  c.coverage(),
                  own.size(),
                  complete.size(),
                  withOdds.size(),
                  (int) withOdds.stream().filter(m -> m.score() != null).count(),
                  (int)
                      own.stream()
                          .filter(
                              m ->
                                  m.fixture().id().startsWith("odds:")
                                      && priced.contains(m.fixture().id()))
                          .count(),
                  (int) counts.values().stream().filter(n -> n >= 7).count(),
                  withOdds.stream()
                      .map(m -> m.fixture().kickoff())
                      .min(Comparator.naturalOrder())
                      .orElse(null),
                  withOdds.stream()
                      .map(m -> m.fixture().kickoff())
                      .max(Comparator.naturalOrder())
                      .orElse(null));
            })
        .toList();
  }

  public Workspace workspace(
      String id, Instant requested, boolean cups, int competition, int page) {
    var run = runs.run(id);
    if (!run.status().equals("COMPLETED"))
      throw new IllegalArgumentException("Elaborazione non completa");
    var bets =
        runs.bets(id).stream()
            .filter(
                b ->
                    b.includeCupScores() == cups
                        && (competition == 0 || b.competitionId() == competition))
            .toList();
    Map<String, LabMatch> matches = new HashMap<>();
    sources.matches().forEach(m -> matches.put(m.fixture().id(), m));
    Instant from =
        bets.stream().map(LabBet::availableAt).min(Comparator.naturalOrder()).orElse(null);
    Instant to =
        bets.stream()
            .map(
                b -> {
                  var m = matches.get(b.fixtureId());
                  return m != null && m.fixture().endedAt() != null
                      ? m.fixture().endedAt()
                      : b.kickoff().plus(Duration.ofHours(24));
                })
            .max(Comparator.naturalOrder())
            .orElse(null);
    Instant cursor = requested == null ? from : requested;
    if (run.config().mode().equals("OPERATIONAL")) cursor = Instant.now();
    if (from != null && cursor.isBefore(from)) cursor = from;
    if (to != null && cursor.isAfter(to) && !run.config().mode().equals("OPERATIONAL")) cursor = to;
    final Instant at = cursor;
    var settled =
        bets.stream()
            .filter(b -> complete(matches.get(b.fixtureId()), at))
            .sorted(
                Comparator.comparing((LabBet b) -> matches.get(b.fixtureId()).fixture().endedAt())
                    .thenComparing(LabBet::id))
            .toList();
    List<Point> chart = new ArrayList<>();
    chart.add(new Point(0, null, from, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)));
    BigDecimal balance = BigDecimal.ZERO.setScale(2);
    int won = 0;
    for (var bet : settled) {
      BigDecimal profit = profit(bet, matches.get(bet.fixtureId()));
      balance = balance.add(profit);
      if (profit.signum() > 0) won++;
      chart.add(
          new Point(
              chart.size(),
              bet.id(),
              matches.get(bet.fixtureId()).fixture().endedAt(),
              profit,
              balance));
    }
    int pages = Math.max(1, (bets.size() + 49) / 50);
    int selected = Math.max(0, Math.min(page, pages - 1));
    var views =
        bets.stream()
            .skip(selected * 50L)
            .limit(50)
            .map(
                b -> {
                  var m = matches.get(b.fixtureId());
                  boolean done = complete(m, at), revealed = runs.revealed(b.id());
                  return new BetView(
                      b,
                      done ? (profit(b, m).signum() > 0 ? "WON" : "LOST") : "PENDING",
                      done ? profit(b, m) : null,
                      revealed,
                      !done && revealed && m != null && m.score() != null
                          ? (profit(b, m).signum() > 0 ? "WON" : "LOST")
                          : null,
                      m == null ? null : m.fixture().endedAt(),
                      m == null ? null : m.fixture().timingBasis());
                })
            .toList();
    var model =
        runs.steps(id).stream()
            .filter(s -> s.includeCupScores() == cups && at != null && !s.cutoff().isAfter(at))
            .reduce((a, b) -> b)
            .map(List::of)
            .orElse(List.of());
    return new Workspace(
        run,
        from,
        to,
        cursor,
        cups,
        competition,
        bets.size(),
        settled.size(),
        won,
        settled.size() - won,
        balance,
        views,
        selected,
        pages,
        List.copyOf(chart),
        model);
  }

  public String reveal(String runId, String betId) {
    var b = runs.bets(runId).stream().filter(v -> v.id().equals(betId)).findFirst().orElseThrow();
    var m = sources.match(b.fixtureId()).filter(v -> v.score() != null).orElseThrow();
    runs.reveal(b.id());
    return profit(b, m).signum() > 0 ? "WON" : "LOST";
  }

  public List<TeamStats> statistics(Instant cutoff, boolean cups, int competition) {
    Map<String, int[]> values = new TreeMap<>();
    Map<String, LabMatch> examples = new HashMap<>();
    Map<String, Long> ids = new HashMap<>();
    Map<String, String> names = new HashMap<>();
    for (var m : sources.matches())
      if (m.score() != null
          && m.fixture().finishedBefore(cutoff)
          && (cups || !m.cup())
          && (competition == 0 || m.fixture().tournamentId() == competition)) {
        for (boolean home : List.of(true, false)) {
          long team = home ? m.fixture().homeId() : m.fixture().awayId();
          String key = m.fixture().tournamentId() + ":" + m.season() + ":" + team;
          var v = values.computeIfAbsent(key, k -> new int[5]);
          v[0]++;
          v[1] += home ? m.score().homeFt() : m.score().awayFt();
          v[2] += home ? m.score().awayFt() : m.score().homeFt();
          v[3] += home ? m.score().homeHt() : m.score().awayHt();
          v[4] += home ? m.score().awayHt() : m.score().homeHt();
          examples.put(key, m);
          ids.put(key, team);
          names.put(key, home ? m.fixture().home() : m.fixture().away());
        }
      }
    return values.entrySet().stream()
        .map(
            e -> {
              var m = examples.get(e.getKey());
              var v = e.getValue();
              return new TeamStats(
                  ids.get(e.getKey()),
                  names.get(e.getKey()),
                  m.fixture().tournamentId(),
                  m.fixture().competition(),
                  m.season(),
                  v[0],
                  v[1],
                  v[2],
                  v[3],
                  v[4]);
            })
        .toList();
  }

  public List<FullStatistics> fullStatistics(Instant cutoff, boolean cups, int competition) {
    var all = sources.matches();
    Set<Long> selectedTeams = new HashSet<>();
    all.stream()
        .filter(m -> !m.cup() && (competition == 0 || m.fixture().tournamentId() == competition))
        .forEach(
            m -> {
              selectedTeams.add(m.fixture().homeId());
              selectedTeams.add(m.fixture().awayId());
            });
    var groups =
        all.stream()
            .filter(
                m ->
                    m.score() != null
                        && m.fixture().finishedBefore(cutoff)
                        && (!m.cup()
                                && (competition == 0 || m.fixture().tournamentId() == competition)
                            || cups
                                && m.cup()
                                && (selectedTeams.contains(m.fixture().homeId())
                                    || selectedTeams.contains(m.fixture().awayId()))))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    m -> m.fixture().tournamentId() + ":" + m.season(),
                    TreeMap::new,
                    java.util.stream.Collectors.toList()));
    List<FullStatistics> result = new ArrayList<>();
    var calculator = new it.masterbet.statistics.application.StatisticsService();
    for (var group : groups.values()) {
      var first = group.getFirst();
      for (var team : calculator.calculate(group.stream().map(LabMatch::played).toList(), cutoff))
        if (!first.cup() || selectedTeams.contains(team.teamId()))
          result.add(
              new FullStatistics(
                  first.fixture().tournamentId(),
                  first.fixture().competition(),
                  first.season(),
                  first.cup(),
                  team));
    }
    return List.copyOf(result);
  }

  private boolean complete(LabMatch m, Instant at) {
    return m != null && m.score() != null && at != null && m.fixture().finishedBefore(at);
  }

  private BigDecimal profit(LabBet bet, LabMatch m) {
    return settlement.won(bet.quote(), m.score())
        ? bet.stake()
            .multiply(bet.quote().price().subtract(BigDecimal.ONE))
            .setScale(2, RoundingMode.HALF_UP)
        : bet.stake().negate();
  }
}
