package it.masterbet;

import it.masterbet.football.domain.*;
import java.time.Instant;

public final class TestMatches {
  public static Fixture fixture(String id, long home, long away, String kickoff) {
    Instant time = Instant.parse(kickoff);
    return new Fixture(
        id,
        home,
        away,
        "Team " + home,
        "Team " + away,
        20,
        "Eliteserien",
        1L,
        time,
        time.plusSeconds(7200));
  }

  public static PlayedMatch played(String id, String kickoff, int home, int away) {
    return new PlayedMatch(fixture(id, 1, 2, kickoff), new Score(id, 0, 0, home, away, false));
  }
}
