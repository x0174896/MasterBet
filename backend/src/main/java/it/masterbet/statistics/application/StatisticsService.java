package it.masterbet.statistics.application;

import it.masterbet.football.domain.*;
import it.masterbet.statistics.domain.TeamStatistics;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
  public List<TeamStatistics> calculate(List<PlayedMatch> matches, Instant cutoff) {
    Map<Long, String> teams = new TreeMap<>();
    for (var m : matches) {
      if (!m.fixture().finishedBefore(cutoff))
        throw new IllegalArgumentException("Risultato futuro nelle statistiche");
      teams.put(m.fixture().homeId(), m.fixture().home());
      teams.put(m.fixture().awayId(), m.fixture().away());
    }
    List<TeamStatistics> result = new ArrayList<>();
    for (var team : teams.entrySet()) {
      int played = 0,
          won = 0,
          draw = 0,
          lost = 0,
          gf = 0,
          ga = 0,
          hf = 0,
          ha = 0,
          hp = 0,
          ap = 0,
          hpts = 0,
          apts = 0,
          hgf = 0,
          hga = 0,
          agf = 0,
          aga = 0;
      StringBuilder form = new StringBuilder();
      var ordered =
          matches.stream()
              .filter(m -> m.fixture().involves(team.getKey()))
              .sorted(Comparator.comparing(m -> m.fixture().kickoff()))
              .toList();
      for (var m : ordered) {
        boolean home = m.fixture().homeId() == team.getKey();
        var s = m.score();
        int scored = home ? s.homeFt() : s.awayFt(),
            conceded = home ? s.awayFt() : s.homeFt(),
            pts = scored > conceded ? 3 : scored == conceded ? 1 : 0;
        played++;
        won += pts == 3 ? 1 : 0;
        draw += pts == 1 ? 1 : 0;
        lost += pts == 0 ? 1 : 0;
        gf += scored;
        ga += conceded;
        hf += home ? s.homeHt() : s.awayHt();
        ha += home ? s.awayHt() : s.homeHt();
        if (home) {
          hp++;
          hpts += pts;
          hgf += scored;
          hga += conceded;
        } else {
          ap++;
          apts += pts;
          agf += scored;
          aga += conceded;
        }
        form.append(pts == 3 ? 'V' : pts == 1 ? 'N' : 'P');
      }
      result.add(
          new TeamStatistics(
              team.getKey(),
              team.getValue(),
              played,
              won,
              draw,
              lost,
              won * 3 + draw,
              gf,
              ga,
              hf,
              ha,
              gf - hf,
              ga - ha,
              hp,
              ap,
              hpts,
              apts,
              hgf,
              hga,
              agf,
              aga,
              form.substring(Math.max(0, form.length() - 5))));
    }
    return result.stream()
        .sorted(
            Comparator.comparingInt(TeamStatistics::points)
                .reversed()
                .thenComparing(
                    Comparator.comparingInt((TeamStatistics t) -> t.goalsFor() - t.goalsAgainst())
                        .reversed())
                .thenComparing(Comparator.comparingInt(TeamStatistics::goalsFor).reversed())
                .thenComparing(TeamStatistics::team))
        .toList();
  }
}
