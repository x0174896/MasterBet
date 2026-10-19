package it.masterbet.laboratory.domain;

import it.masterbet.football.domain.*;

public record LabMatch(
    Fixture fixture,
    String season,
    String round,
    boolean cup,
    Score score,
    String sourceUrl,
    String oddsFixtureId,
    String sourceHash) {
  public LabMatch {
    if (fixture == null || season == null || season.isBlank() || sourceUrl == null)
      throw new IllegalArgumentException("Incontro non valido");
    if (score != null && (!fixture.id().equals(score.fixtureId()) || fixture.endedAt() == null))
      throw new IllegalArgumentException("Risultato senza identita o disponibilita verificabile");
  }

  public PlayedMatch played() {
    if (score == null) throw new IllegalStateException("HT/FT non disponibili");
    return new PlayedMatch(fixture, score);
  }

  public LabMatch linked(String oddsId) {
    return new LabMatch(fixture, season, round, cup, score, sourceUrl, oddsId, sourceHash);
  }

  public LabMatch linked(Fixture provider) {
    var f = fixture;
    if (score != null
        && provider.timingBasis().equals("ACTUAL_END")
        && provider.endedAt() != null
        && !provider.endedAt().isBefore(f.kickoff().plusSeconds(3600))
        && provider.endedAt().isBefore(f.kickoff().plusSeconds(21600)))
      f =
          new Fixture(
              f.id(),
              f.homeId(),
              f.awayId(),
              f.home(),
              f.away(),
              f.tournamentId(),
              f.competition(),
              f.seasonId(),
              f.kickoff(),
              provider.endedAt(),
              "CALCIO_SCORE_ODDSPAPI_ACTUAL_END");
    return new LabMatch(f, season, round, cup, score, sourceUrl, provider.id(), sourceHash);
  }
}
