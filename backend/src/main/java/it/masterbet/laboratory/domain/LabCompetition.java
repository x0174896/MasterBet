package it.masterbet.laboratory.domain;

import java.util.List;

public record LabCompetition(
    int id,
    String slug,
    String name,
    String country,
    boolean cup,
    List<String> seasons,
    Integer oddsTournamentId,
    String coverage) {
  public LabCompetition {
    if (id <= 0 || slug == null || slug.isBlank() || name == null || seasons == null)
      throw new IllegalArgumentException("Competizione non valida");
    seasons = List.copyOf(seasons);
  }

  public LabCompetition matched(Integer oddsId, String status) {
    return new LabCompetition(id, slug, name, country, cup, seasons, oddsId, status);
  }
}
