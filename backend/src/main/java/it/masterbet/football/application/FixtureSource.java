package it.masterbet.football.application;

import it.masterbet.football.domain.Fixture;
import java.time.Instant;
import java.util.List;

public interface FixtureSource {
  List<Fixture> tournament(int tournamentId, Instant from, Instant to);

  List<Fixture> team(long teamId, Instant from, Instant to);
}
