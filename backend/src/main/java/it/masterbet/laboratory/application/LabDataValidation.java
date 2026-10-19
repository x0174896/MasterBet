package it.masterbet.laboratory.application;

import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LabDataValidation {
  public record Report(List<Integer> eligible, List<String> gaps) {}

  private final LabRepository repo;
  private final LabCampaignStore store;

  public LabDataValidation(LabRepository repo, LabCampaignStore store) {
    this.repo = repo;
    this.store = store;
  }

  public Report verify(LabRunConfig config) {
    var failures = store.sourceFailures();
    if (!failures.isEmpty())
      throw new IllegalStateException(
          "Controlli sorgenti non superati: "
              + String.join(", ", failures)
              + "; riprendere o completare le fasi prima del calcolo");
    Instant from = config.from().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = config.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var matches = repo.matches();
    var quoted = repo.quotedFixtures();
    var incomplete = store.incompleteQuoteImports();
    List<Integer> eligible = new ArrayList<>();
    List<String> gaps = new ArrayList<>();
    for (var c : repo.competitions()) {
      if (c.cup()
          || c.oddsTournamentId() == null
          || !config.competitionIds().isEmpty() && !config.competitionIds().contains(c.id())
          || c.seasons().stream()
              .noneMatch(s -> LabDownloadService.seasonOverlaps(s, config.from(), config.to())))
        continue;
      var official =
          matches.stream()
              .filter(
                  m ->
                      !m.cup()
                          && !m.fixture().id().startsWith("odds:")
                          && m.fixture().tournamentId() == c.id()
                          && !m.fixture().kickoff().isBefore(from)
                          && m.fixture().kickoff().isBefore(to))
              .toList();
      for (var m : matches)
        if (!m.cup()
            && m.fixture().tournamentId() == c.id()
            && !m.fixture().kickoff().isBefore(from)
            && m.fixture().kickoff().isBefore(to)
            && m.oddsFixtureId() != null
            && incomplete.contains(m.oddsFixtureId()))
          throw new IllegalStateException(
              "Importazione quote incompleta: "
                  + m.fixture().id()
                  + "; riprendere il download prima del calcolo");
      boolean score = official.stream().anyMatch(m -> m.score() != null);
      boolean prices =
          official.stream()
              .anyMatch(
                  m ->
                      m.score() != null
                          && m.oddsFixtureId() != null
                          && quoted.contains(m.fixture().id()));
      if (score && prices) eligible.add(c.id());
      else
        gaps.add(
            "GAP: "
                + c.name()
                + ": escluso dal calcolo, "
                + (!score ? "risultati HT/FT assenti" : "quote abbinate assenti"));
    }
    if (eligible.isEmpty())
      throw new IllegalStateException(
          "Nessun campionato con risultati HT/FT e quote abbinate; calcolo non avviato");
    return new Report(List.copyOf(eligible), List.copyOf(gaps));
  }
}
