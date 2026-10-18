package it.masterbet.football.application;

import it.masterbet.football.domain.Fixture;
import java.time.Instant;
import java.util.List;

public interface FixtureSource {
  List<Fixture> tournament(int tournamentId, Instant from, Instant to);

  List<Fixture> team(long teamId, Instant from, Instant to);

  default List<Fixture> sport(Instant from, Instant to) {
    throw new UnsupportedOperationException("Calendario aggregato non disponibile");
  }

  default List<Fixture> teamsCalendar(java.util.Set<Long> teams, Instant from, Instant to) {
    return sport(from, to).stream()
        .filter(f -> teams.contains(f.homeId()) || teams.contains(f.awayId()))
        .toList();
  }
}
