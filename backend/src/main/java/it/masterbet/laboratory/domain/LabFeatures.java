package it.masterbet.laboratory.domain;

import it.masterbet.football.domain.Fixture;
import java.time.*;
import java.util.*;

/** Every feature is reconstructed from a completed prefix, never from the target score. */
public final class LabFeatures {
  public record Input(double[] values, int homeCount, int awayCount, List<String> evidence) {}

  public Input build(
      Fixture target, String season, Instant cutoff, List<LabMatch> prefix, boolean cups) {
    if (cutoff.isAfter(target.kickoff()))
      throw new IllegalArgumentException("Taglio successivo alla partita");
    var known =
        prefix.stream()
            .filter(
                m ->
                    m.fixture().finishedBefore(cutoff)
                        && m.fixture().kickoff().isBefore(target.kickoff())
                        && !m.fixture().id().equals(target.id()))
            .toList();
    var league =
        known.stream()
            .filter(
                m ->
                    m.score() != null
                        && !m.cup()
                        && m.fixture().tournamentId() == target.tournamentId()
                        && m.season().equals(season))
            .toList();
    var home = team(league, target.homeId());
    var away = team(league, target.awayId());
    List<Double> x = new ArrayList<>();
    List<String> evidence = new ArrayList<>();
    double[] h = rates(last(home, 7), target.homeId()), a = rates(last(away, 7), target.awayId());
    add(x, h);
    add(x, a);
    double[] baseline = new double[4];
    for (var m : league) {
      baseline[0] += m.score().homeHt();
      baseline[1] += m.score().awayHt();
      baseline[2] += m.score().homeSecondHalf();
      baseline[3] += m.score().awaySecondHalf();
    }
    for (int k = 0; k < 4; k++) baseline[k] /= Math.max(1, league.size());
    add(x, baseline);
    // Rates and for/against interactions are derived within this league/season.
    add(
        x,
        new double[] {
          (h[0] + a[1]) / 2,
          (a[0] + h[1]) / 2,
          (h[2] + a[3]) / 2,
          (a[2] + h[3]) / 2,
          h[0] - a[1],
          a[0] - h[1],
          h[2] - a[3],
          a[2] - h[3]
        });
    for (long team : new long[] {target.homeId(), target.awayId()}) {
      var calendar = team(known, team); // cup calendar ALWAYS included in both modes
      double rest =
          calendar.isEmpty()
              ? 30
              : Math.min(
                  30,
                  Duration.between(calendar.getLast().fixture().kickoff(), target.kickoff())
                          .toHours()
                      / 24.0);
      long frequency =
          calendar.stream()
              .filter(
                  m -> m.fixture().kickoff().isAfter(target.kickoff().minus(Duration.ofDays(14))))
              .count();
      long travels =
          calendar.stream()
              .filter(
                  m ->
                      m.fixture().awayId() == team
                          && m.fixture()
                              .kickoff()
                              .isAfter(target.kickoff().minus(Duration.ofDays(14))))
              .count();
      add(x, new double[] {rest, frequency, travels});
    }
    for (var pair :
        List.of(new TeamHistory(target.homeId(), home), new TeamHistory(target.awayId(), away))) {
      var previous = pair.history().isEmpty() ? null : pair.history().getLast();
      add(
          x,
          previous == null
              ? new double[3]
              : new double[] {
                against(previous, pair.team()) >= 3 ? 1 : 0,
                goals(previous, pair.team(), false) == 0 ? 1 : 0,
                goals(previous, pair.team(), true) == 0 ? 1 : 0
              });
    }
    if (cups)
      for (long team : new long[] {target.homeId(), target.awayId()}) {
        var recent =
            team(
                known.stream()
                    .filter(m -> m.cup() && m.score() != null)
                    .filter(
                        m ->
                            m.fixture()
                                .kickoff()
                                .isAfter(target.kickoff().minus(Duration.ofDays(90))))
                    .toList(),
                team);
        add(x, rates(last(recent, 7), team));
        x.add((double) recent.size());
      }
    evidence.add(
        "Campionato e stagione: "
            + home.size()
            + " precedenti casa, "
            + away.size()
            + " ospite; ultime 7 per le medie gol");
    evidence.add(
        "Delta gol 1T casa/ospite: "
            + String.format(Locale.ROOT, "%.2f / %.2f", h[0] - a[1], a[0] - h[1]));
    evidence.add(
        cups
            ? "Gol di coppa aggiunti come indicatori separati; calendario completo"
            : "Gol di coppa esclusi; calendario, riposo e trasferte di coppa inclusi");
    return new Input(
        x.stream().mapToDouble(Double::doubleValue).toArray(),
        home.size(),
        away.size(),
        List.copyOf(evidence));
  }

  private record TeamHistory(long team, List<LabMatch> history) {}

  private static List<LabMatch> team(List<LabMatch> list, long team) {
    return list.stream()
        .filter(m -> m.fixture().homeId() == team || m.fixture().awayId() == team)
        .sorted(Comparator.comparing(m -> m.fixture().kickoff()))
        .toList();
  }

  private static List<LabMatch> last(List<LabMatch> list, int n) {
    return list.subList(Math.max(0, list.size() - n), list.size());
  }

  private static void add(List<Double> values, double[] more) {
    for (double v : more) values.add(v);
  }

  private static double[] rates(List<LabMatch> list, long team) {
    double[] r = new double[4];
    for (var m : list) {
      boolean h = m.fixture().homeId() == team;
      r[0] += h ? m.score().homeHt() : m.score().awayHt();
      r[1] += h ? m.score().awayHt() : m.score().homeHt();
      r[2] += h ? m.score().homeSecondHalf() : m.score().awaySecondHalf();
      r[3] += h ? m.score().awaySecondHalf() : m.score().homeSecondHalf();
    }
    for (int k = 0; k < 4; k++) r[k] /= Math.max(1, list.size());
    return r;
  }

  public static int goals(LabMatch m, long team, boolean half) {
    boolean h = m.fixture().homeId() == team;
    return half
        ? (h ? m.score().homeHt() : m.score().awayHt())
        : (h ? m.score().homeFt() : m.score().awayFt());
  }

  private static int against(LabMatch m, long team) {
    return goals(
        m, m.fixture().homeId() == team ? m.fixture().awayId() : m.fixture().homeId(), false);
  }
}
