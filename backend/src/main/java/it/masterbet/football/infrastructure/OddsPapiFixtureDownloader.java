package it.masterbet.football.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import it.masterbet.football.application.FixtureSource;
import it.masterbet.football.domain.Fixture;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiFixtureDownloader implements FixtureSource {
  private final OddsPapiClient client;

  public OddsPapiFixtureDownloader(OddsPapiClient client) {
    this.client = client;
  }

  public List<Fixture> tournament(int id, Instant from, Instant to) {
    return download("tournamentId", String.valueOf(id), from, to);
  }

  public List<Fixture> team(long id, Instant from, Instant to) {
    return download("participantId", String.valueOf(id), from, to);
  }

  private List<Fixture> download(String filter, String id, Instant from, Instant to) {
    var data =
        client.get("fixtures", Map.of(filter, id, "from", from.toString(), "to", to.toString()));
    if (!data.isArray()) throw new IllegalArgumentException("Elenco partite non valido");
    Map<String, Fixture> fixtures = new LinkedHashMap<>();
    for (var f : data) {
      if (f.path("sportId").asInt() != 10) throw new IllegalArgumentException("Sport inatteso");
      if (f.path("statusName").asText().equals("Cancelled")) continue;
      Fixture fixture = parse(f);
      if (fixtures.putIfAbsent(fixture.id(), fixture) != null)
        throw new IllegalArgumentException("Partita duplicata nella sorgente");
    }
    return fixtures.values().stream()
        .sorted(Comparator.comparing(Fixture::kickoff).thenComparing(Fixture::id))
        .toList();
  }

  public static Fixture parse(JsonNode f) {
    Instant scheduled = Instant.parse(f.required("startTime").asText());
    var timing = FixtureTiming.from(f, scheduled);
    return new Fixture(
        f.required("fixtureId").asText(),
        f.required("participant1Id").asLong(),
        f.required("participant2Id").asLong(),
        f.required("participant1Name").asText(),
        f.required("participant2Name").asText(),
        f.required("tournamentId").asInt(),
        f.path("tournamentName").asText(),
        f.path("seasonId").isNumber() ? f.path("seasonId").asLong() : null,
        scheduled,
        timing.completedBy(),
        timing.basis());
  }
}
