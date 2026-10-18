package it.masterbet.football.domain;

import java.time.Instant;

public record Fixture(
    String id,
    long homeId,
    long awayId,
    String home,
    String away,
    int tournamentId,
    String competition,
    Long seasonId,
    Instant kickoff,
    Instant endedAt,
    String timingBasis) {
  public Fixture(
      String id,
      long homeId,
      long awayId,
      String home,
      String away,
      int tournamentId,
      String competition,
      Long seasonId,
      Instant kickoff,
      Instant endedAt) {
    this(
        id,
        homeId,
        awayId,
        home,
        away,
        tournamentId,
        competition,
        seasonId,
        kickoff,
        endedAt,
        endedAt == null ? "SCHEDULED" : "ACTUAL_END");
  }

  public Fixture {
    if (id == null
        || id.isBlank()
        || homeId == awayId
        || homeId <= 0
        || awayId <= 0
        || home == null
        || away == null
        || kickoff == null) throw new IllegalArgumentException("Partita non valida");
    if (endedAt != null && endedAt.isBefore(kickoff))
      throw new IllegalArgumentException("Disponibilita risultato antecedente all'inizio");
  }

  public boolean involves(long team) {
    return homeId == team || awayId == team;
  }

  public boolean finishedBefore(Instant cutoff) {
    return endedAt != null && !endedAt.isAfter(cutoff);
  }
}
