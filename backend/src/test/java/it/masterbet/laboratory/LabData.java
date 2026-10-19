package it.masterbet.laboratory;

import it.masterbet.football.domain.*;
import it.masterbet.laboratory.domain.*;
import java.time.*;
import java.util.*;

final class LabData {
  static LabMatch match(
      String id,
      int league,
      String season,
      Instant at,
      long home,
      long away,
      int ht,
      int ft,
      boolean cup) {
    var fixture =
        new Fixture(
            id,
            home,
            away,
            "Team " + home,
            "Team " + away,
            league,
            "League " + league,
            2026L,
            at,
            at.plusSeconds(7200),
            "TEST_ACTUAL_END");
    return new LabMatch(
        fixture,
        season,
        "1",
        cup,
        new Score(id, ht, 0, ft, 0, false),
        "https://www.calcio.com/test",
        null,
        "test");
  }

  static List<LabMatch> season(int league, String season, Instant from, int rounds) {
    List<LabMatch> rows = new ArrayList<>();
    for (int round = 0; round < rounds; round++)
      for (int game = 0; game < 4; game++) {
        long home = game * 2 + 1, away = game * 2 + 2;
        rows.add(
            match(
                "calcio:" + league + "-" + round + "-" + game,
                league,
                season,
                from.plus(Duration.ofDays(round * 4L)),
                home,
                away,
                round % 2,
                1 + round % 3,
                false));
      }
    return rows;
  }
}
