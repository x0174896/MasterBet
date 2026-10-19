package it.masterbet.laboratory.infrastructure;

import com.fasterxml.jackson.databind.*;
import it.masterbet.football.domain.*;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.laboratory.domain.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class CalcioParser {
  private final ObjectMapper mapper;

  public CalcioParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Decodifica il JSON RSC pubblico senza eseguire JavaScript o usare nomi/date dello slug. */
  public JsonNode embedded(String html, String field) {
    var frames =
        Pattern.compile("self\\.__next_f\\.push\\((\\[.*?\\])\\)</script>", Pattern.DOTALL)
            .matcher(html);
    StringBuilder payload = new StringBuilder();
    try {
      while (frames.find()) {
        var frame = mapper.readTree(frames.group(1));
        if (frame.size() > 1 && frame.get(1).isTextual()) payload.append(frame.get(1).asText());
      }
      String key = "\"" + field + "\":";
      int start = payload.indexOf(key);
      if (start < 0) throw new IllegalArgumentException("Campo Calcio assente: " + field);
      return mapper.readTree(payload.substring(start + key.length()));
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Dati strutturati Calcio non validi");
    }
  }

  public List<LabCompetition> catalog(String html) {
    List<LabCompetition> result = new ArrayList<>();
    for (var row : embedded(html, "leagues")) {
      String type = row.path("subType").asText();
      if (!Set.of("domestic", "domestic_cup", "cup_international").contains(type)) continue;
      List<String> seasons = new ArrayList<>();
      row.path("totalSeasons").forEach(s -> seasons.add(s.asText()));
      result.add(
          new LabCompetition(
              row.required("id").asInt(),
              row.required("slug").asText(),
              row.required("name").asText(),
              row.path("countryName").asText(),
              !type.equals("domestic") || row.path("id").asInt() == 1479,
              seasons,
              null,
              "DA_INCROCIARE"));
    }
    if (result.isEmpty()) throw new IllegalArgumentException("Catalogo Calcio vuoto");
    return List.copyOf(result);
  }

  public LabMatch match(
      LabCompetition competition, String season, String round, JsonNode row, String body) {
    try {
      JsonNode data = mapper.readTree(body).required("data");
      if (data.required("id").asLong() != row.required("id").asLong())
        throw new IllegalArgumentException("Identita Calcio discordante");
      JsonNode home = null, away = null;
      for (var team : data.path("participants")) {
        if (team.path("meta").path("location").asText().equals("home")) home = team;
        if (team.path("meta").path("location").asText().equals("away")) away = team;
      }
      if (home == null || away == null)
        throw new IllegalArgumentException("Squadre Calcio mancanti");
      if (home.path("id").asLong() != row.path("home").path("id").asLong()
          || away.path("id").asLong() != row.path("away").path("id").asLong())
        throw new IllegalArgumentException("Squadre Calcio discordanti");
      Instant kickoff = Instant.parse(data.required("starting_at").asText());
      String id = "calcio:" + data.required("id").asLong();
      int state = data.path("state").path("id").asInt();
      boolean finished = Set.of(5, 7, 8).contains(state);
      // L'API non espone una fine reale: stesso limite prudenziale esplicito del vecchio import.
      var fixture =
          new Fixture(
              id,
              home.required("id").asLong(),
              away.required("id").asLong(),
              home.required("name").asText(),
              away.required("name").asText(),
              competition.id(),
              competition.name(),
              (long) season.hashCode(),
              kickoff,
              finished ? kickoff.plus(Duration.ofHours(24)) : null,
              finished ? "CALCIO_CONSERVATIVE_24H_BOUND" : "CALCIO_SCHEDULE_" + state);
      JsonNode ht = null, ft = null;
      for (var score : data.path("fixtureScore")) {
        if (score.path("type").asText().equals("HT")) ht = score;
        // Nei supplementari 2H e il cumulativo a 90', mai sottrarre HT da ET/PEN.
        if (score.path("type").asText().equals(state == 5 ? "FT" : "2H")) ft = score;
      }
      // Some regulation-time finals expose only the cumulative 2H score (verified in 2025/26).
      if (ft == null && state == 5)
        for (var cumulative : data.path("fixtureScore"))
          if (cumulative.path("type").asText().equals("2H")) ft = cumulative;
      Score score = null;
      if (finished
          && ht != null
          && ft != null
          && number(ht, "homeScore")
          && number(ht, "awayScore")
          && number(ft, "homeScore")
          && number(ft, "awayScore"))
        score =
            new Score(
                id,
                ht.get("homeScore").asInt(),
                ht.get("awayScore").asInt(),
                ft.get("homeScore").asInt(),
                ft.get("awayScore").asInt(),
                state != 5);
      return new LabMatch(
          fixture,
          season,
          round,
          competition.cup(),
          score,
          "https://www.calcio.com/partite/" + row.required("slug").asText(),
          null,
          RawResponseArchive.hash(body));
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Dettaglio Calcio non valido");
    }
  }

  private boolean number(JsonNode n, String field) {
    return n.path(field).isIntegralNumber();
  }
}
