package it.masterbet.laboratory.infrastructure;

import com.fasterxml.jackson.databind.*;
import it.masterbet.laboratory.domain.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CalcioSource {
  private final PublicSourceArchive archive;
  private final CalcioParser parser;
  private final ObjectMapper mapper;

  public CalcioSource(PublicSourceArchive archive, CalcioParser parser, ObjectMapper mapper) {
    this.archive = archive;
    this.parser = parser;
    this.mapper = mapper;
  }

  public List<LabCompetition> catalog(boolean refresh) {
    return parser.catalog(archive.get("https://www.calcio.com/statistiche", refresh));
  }

  public JsonNode rounds(LabCompetition competition, String season, boolean refresh) {
    return json(
        "https://api.calcio.com/api/schedule-rounds?league="
            + competition.id()
            + "&season="
            + encode(season),
        refresh);
  }

  public JsonNode schedule(
      LabCompetition competition, String season, long roundId, boolean refresh) {
    return json(
        "https://api.calcio.com/api/schedule?league="
            + competition.id()
            + "&season="
            + encode(season)
            + "&roundId="
            + roundId,
        refresh);
  }

  /** Roundless competitions, including MLS, expose fixtures without a round parameter. */
  public JsonNode schedule(LabCompetition competition, String season, boolean refresh) {
    return json(
        "https://api.calcio.com/api/schedule?league="
            + competition.id()
            + "&season="
            + encode(season),
        refresh);
  }

  public LabMatch detail(
      LabCompetition competition, String season, String round, JsonNode row, boolean refresh) {
    long id = row.required("id").asLong();
    if (id <= 0) throw new IllegalArgumentException("Identificativo Calcio non valido");
    return parser.match(
        competition,
        season,
        round,
        row,
        archive.get("https://www.calcio.com/api/fixture/" + id, refresh));
  }

  private JsonNode json(String url, boolean refresh) {
    try {
      var root = mapper.readTree(archive.get(url, refresh));
      var data = root.required("data");
      if (!data.isArray()) throw new IllegalArgumentException("Elenco Calcio non valido");
      return data;
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("JSON Calcio non valido");
    }
  }

  private String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }
}
