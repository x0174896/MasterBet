package it.masterbet.football.domain;

public record PlayedMatch(Fixture fixture, Score score) {
  public PlayedMatch {
    if (!fixture.id().equals(score.fixtureId()))
      throw new IllegalArgumentException("Risultato associato alla partita errata");
  }
}
