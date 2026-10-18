package it.masterbet.football.domain;

public record Score(
    String fixtureId, int homeHt, int awayHt, int homeFt, int awayFt, Boolean extraTime) {
  public Score {
    if (fixtureId == null
        || homeHt < 0
        || awayHt < 0
        || homeFt < homeHt
        || awayFt < awayHt
        || homeFt > 30
        || awayFt > 30) throw new IllegalArgumentException("Punteggio mancante o incoerente");
  }

  public int homeSecondHalf() {
    return homeFt - homeHt;
  }

  public int awaySecondHalf() {
    return awayFt - awayHt;
  }
}
