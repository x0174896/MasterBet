package it.masterbet.model.domain;

import it.masterbet.football.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FeatureBuilder {
  public static final List<String> NAMES =
      List.of(
          "home_attack_ht",
          "away_defence_ht",
          "away_attack_ht",
          "home_defence_ht",
          "home_attack_sh",
          "away_defence_sh",
          "away_attack_sh",
          "home_defence_sh",
          "home_rest_days",
          "away_rest_days",
          "home_matches_14d",
          "away_matches_14d",
          "home_cups_14d",
          "away_cups_14d",
          "home_away_games_7d",
          "away_away_games_7d");

  public double[] build(
      Fixture target,
      Instant cutoff,
      List<PlayedMatch> history,
      List<Fixture> calendar,
      boolean cups) {
    if (cutoff.isAfter(target.kickoff()))
      throw new IllegalArgumentException("Feature successive alla partita");
    var home = recent(target.homeId(), cutoff, history);
    var away = recent(target.awayId(), cutoff, history);
    var homeCalendar = calendar(target.homeId(), cutoff, calendar, cups);
    var awayCalendar = calendar(target.awayId(), cutoff, calendar, cups);
    // Il calendario resta limitato al taglio; il riposo cresce fino al calcio d'inizio previsto.
    Instant predictionTime = target.kickoff();
    return new double[] {
      rate(home, target.homeId(), true, true),
      rate(away, target.awayId(), false, true),
      rate(away, target.awayId(), true, true),
      rate(home, target.homeId(), false, true),
      rate(home, target.homeId(), true, false),
      rate(away, target.awayId(), false, false),
      rate(away, target.awayId(), true, false),
      rate(home, target.homeId(), false, false),
      rest(homeCalendar, predictionTime),
      rest(awayCalendar, predictionTime),
      count(homeCalendar, predictionTime, 14, false, false, target.homeId()),
      count(awayCalendar, predictionTime, 14, false, false, target.awayId()),
      cups ? count(homeCalendar, predictionTime, 14, true, false, target.homeId()) : 0,
      cups ? count(awayCalendar, predictionTime, 14, true, false, target.awayId()) : 0,
      count(homeCalendar, predictionTime, 7, false, true, target.homeId()),
      count(awayCalendar, predictionTime, 7, false, true, target.awayId())
    };
  }

  public double[] baseline(Fixture target, Instant cutoff, List<PlayedMatch> history) {
    var h = recent(target.homeId(), cutoff, history);
    var a = recent(target.awayId(), cutoff, history);
    double[] rates =
        new double[] {
          (rate(h, target.homeId(), true, true) + rate(a, target.awayId(), false, true)) / 2,
          (rate(a, target.awayId(), true, true) + rate(h, target.homeId(), false, true)) / 2,
          (rate(h, target.homeId(), true, false) + rate(a, target.awayId(), false, false)) / 2,
          (rate(a, target.awayId(), true, false) + rate(h, target.homeId(), false, false)) / 2
        };
    // Stesso dominio numerico del modello: evita instabilita con risultati estremi.
    return Arrays.stream(rates).map(value -> Math.max(0.05, Math.min(6, value))).toArray();
  }

  private List<PlayedMatch> recent(long team, Instant cutoff, List<PlayedMatch> history) {
    return history.stream()
        .filter(m -> m.fixture().involves(team) && m.fixture().finishedBefore(cutoff))
        .sorted(Comparator.comparing((PlayedMatch m) -> m.fixture().kickoff()).reversed())
        .limit(5)
        .toList();
  }

  private double rate(List<PlayedMatch> matches, long team, boolean scored, boolean first) {
    double sum =
        first
            ? 1.2
            : 1.4; // Due osservazioni a priori, fisse e documentate, per squadre senza storico.
    for (var match : matches) {
      boolean home = match.fixture().homeId() == team;
      var s = match.score();
      sum +=
          home == scored
              ? (first ? s.homeHt() : s.homeSecondHalf())
              : (first ? s.awayHt() : s.awaySecondHalf());
    }
    return sum / (matches.size() + 2);
  }

  private List<Fixture> calendar(long team, Instant cutoff, List<Fixture> all, boolean cups) {
    return all.stream()
        .filter(
            f ->
                f.involves(team)
                    && f.finishedBefore(cutoff)
                    && CompetitionPolicy.isOfficial(f)
                    && (cups || f.tournamentId() == 20))
        .toList();
  }

  private double rest(List<Fixture> events, Instant cutoff) {
    // Distanza fra calci d'inizio, non ore di recupero esatte: non usa gli orari
    // di fine incoerenti presenti in parte dell'archivio del provider.
    return events.stream()
        .map(Fixture::kickoff)
        .max(Comparator.naturalOrder())
        .map(t -> Math.min(14, Duration.between(t, cutoff).toSeconds() / 86400.0))
        .orElse(14.0);
  }

  private long count(
      List<Fixture> events,
      Instant cutoff,
      int days,
      boolean cupOnly,
      boolean awayOnly,
      long team) {
    return events.stream()
        .filter(
            f ->
                f.kickoff().isAfter(cutoff.minus(Duration.ofDays(days)))
                    && (!cupOnly || CompetitionPolicy.isCup(f))
                    && (!awayOnly || f.awayId() == team))
        .count();
  }
}
