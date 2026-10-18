package it.masterbet.football.domain;

import java.util.Set;

/**
 * Classificazione esplicita delle competizioni verificate nell'archivio norvegese. Nuove
 * competizioni richiedono una classificazione: non sono coppe per esclusione.
 */
public final class CompetitionPolicy {
  private static final Set<Integer> CUPS = Set.of(7, 29, 679, 34480);
  private static final Set<Integer> LEAGUES = Set.of(20, 22);

  private CompetitionPolicy() {}

  public static boolean isCup(Fixture fixture) {
    return CUPS.contains(fixture.tournamentId());
  }

  public static boolean isOfficial(Fixture fixture) {
    return isCup(fixture) || LEAGUES.contains(fixture.tournamentId());
  }
}
