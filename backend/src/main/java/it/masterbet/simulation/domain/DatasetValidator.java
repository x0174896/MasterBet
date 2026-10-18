package it.masterbet.simulation.domain;

import it.masterbet.football.domain.PlayedMatch;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DatasetValidator {
  public void validate(SimulationDataset data) {
    if (data.id().equals("eliteserien-2026-round7")
        && (data.leagueHistory().size() != 47 || data.targets().size() != 8))
      throw new IllegalArgumentException(
          "Copertura scenario incompleta: attese 47 partite concluse e 8 eventi futuri");
    if (data.leagueHistory().size() < 47 || data.targets().isEmpty())
      throw new IllegalArgumentException("Copertura scenario incompleta");
    if (data.trainingHistory().size() != 80)
      throw new IllegalArgumentException("Campione addestramento incompleto: attese 80 partite");
    Set<String> seen = new HashSet<>();
    for (PlayedMatch m : data.leagueHistory()) {
      if (!m.fixture().finishedBefore(data.cutoff())
          || m.fixture().tournamentId() != 20
          || !seen.add(m.fixture().id()))
        throw new IllegalArgumentException("Storico non coerente con il taglio temporale");
    }
    for (var m : data.trainingHistory())
      if (!m.fixture().finishedBefore(Instant.parse("2026-01-01T00:00:00Z")))
        throw new IllegalArgumentException("Il training deve precedere il campionato simulato");
    for (var m : data.trainingHistory())
      if (m.fixture().tournamentId() != 20
          || !Objects.equals(m.fixture().seasonId(), 127337L)
          || !seen.add(m.fixture().id()))
        throw new IllegalArgumentException(
            "Campione di training duplicato o estraneo alla stagione regolare 2025");
    Set<String> calendarIds = new HashSet<>();
    for (var f : data.calendar())
      if (!f.finishedBefore(data.cutoff()) || !calendarIds.add(f.id()))
        throw new IllegalArgumentException("Calendario futuro o duplicato");
    for (var m : data.cupResults())
      if (!m.fixture().finishedBefore(data.cutoff()) || m.fixture().tournamentId() == 20)
        throw new IllegalArgumentException("Risultati di coppa non coerenti");
    var targets = data.targets().stream().collect(Collectors.toMap(f -> f.id(), f -> f));
    for (var f : targets.values())
      if (!f.kickoff().isAfter(data.cutoff()) || f.endedAt() != null || seen.contains(f.id()))
        throw new IllegalArgumentException("Evento target contaminato con dati successivi");
    Set<String> keys = new HashSet<>();
    for (var q : data.quotes()) {
      if (!targets.containsKey(q.fixtureId())
          || q.observedAt().isAfter(data.cutoff())
          || q.price().compareTo(java.math.BigDecimal.ONE) <= 0
          || !keys.add(
              q.fixtureId() + "|" + q.bookmaker() + "|" + q.market().id() + "|" + q.outcomeId()))
        throw new IllegalArgumentException("Quota duplicata o successiva al taglio temporale");
    }
    // La copertura commerciale varia per partita, bookmaker e istante: una famiglia
    // assente (o un solo esito disponibile) non invalida le altre quote al taglio.
    // Predizioni e schedine usano solo gli esiti presenti; nessuna quota viene ricostruita.
  }
}
