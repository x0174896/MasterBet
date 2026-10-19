package it.masterbet.laboratory.application;

import com.fasterxml.jackson.databind.JsonNode;
import it.masterbet.laboratory.domain.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CompetitionMatcher {
  public List<LabCompetition> match(List<LabCompetition> source, JsonNode tournaments) {
    if (!tournaments.isArray()) throw new IllegalArgumentException("Catalogo OddsPapi non valido");
    List<LabCompetition> result = new ArrayList<>();
    for (var league : source) {
      if (league.cup()) {
        result.add(league.matched(null, "CONTESTO_COPPE"));
        continue;
      }
      List<Integer> candidates = new ArrayList<>();
      Map<Integer, String> names = new HashMap<>();
      for (var t : tournaments) {
        boolean sameCountry =
            SourceIdentity.country(league.country())
                    .equals(SourceIdentity.country(t.path("categoryName").asText()))
                || league.id() == 45 && t.path("categoryName").asText().equals("England Amateur");
        if (sameCountry
            && (SourceIdentity.calcioLeague(league)
                    .equals(SourceIdentity.league(t.path("tournamentName").asText()))
                || SourceIdentity.league(league.name())
                    .equals(SourceIdentity.league(t.path("tournamentName").asText())))) {
          candidates.add(t.required("tournamentId").asInt());
          names.put(t.required("tournamentId").asInt(), t.path("tournamentName").asText());
        }
      }
      result.add(
          candidates.size() == 1
              ? new LabCompetition(
                  league.id(),
                  league.slug(),
                  names.get(candidates.getFirst()),
                  league.country(),
                  false,
                  league.seasons(),
                  candidates.getFirst(),
                  "CATALOGO_COMUNE")
              : league.matched(null, candidates.isEmpty() ? "NESSUNA_CORRISPONDENZA" : "AMBIGUO"));
    }
    return result;
  }
}
