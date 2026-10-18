package it.masterbet.football.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import it.masterbet.football.application.ScoreSource;
import it.masterbet.football.domain.MatchFormat;
import it.masterbet.football.domain.Score;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiScoreDownloader implements ScoreSource {
  private final OddsPapiClient client;

  public OddsPapiScoreDownloader(OddsPapiClient client) {
    this.client = client;
  }

  public Score download(String fixtureId, MatchFormat format) {
    return parse(fixtureId, client.get("scores", Map.of("fixtureId", fixtureId)), format);
  }

  public static Score parse(String fixtureId, JsonNode response) {
    return parse(fixtureId, response, MatchFormat.KNOCKOUT);
  }

  public static Score parse(String fixtureId, JsonNode response, MatchFormat format) {
    if (!fixtureId.equals(response.path("fixtureId").asText()))
      throw new IllegalArgumentException("ID risultato diverso dalla richiesta");
    var p = response.required("scores").required("periods");
    String finalPeriod = "fulltime";
    if (format == MatchFormat.REGULATION_ONLY) {
      if (p.has("overtime") || p.has("penalties"))
        throw new IllegalArgumentException("Supplementari inattesi in campionato");
      if (!p.has("fulltime")) finalPeriod = "result";
      else if (p.has("result")
          && (integer(p, "fulltime", "participant1Score")
                  != integer(p, "result", "participant1Score")
              || integer(p, "fulltime", "participant2Score")
                  != integer(p, "result", "participant2Score")))
        throw new IllegalArgumentException(
            "Risultato finale discordante per partita senza supplementari: " + fixtureId);
    }
    Boolean extraTime =
        format == MatchFormat.REGULATION_ONLY
            ? Boolean.FALSE
            : p.has("overtime") ? Boolean.TRUE : null;
    return new Score(
        fixtureId,
        integer(p, "p1", "participant1Score"),
        integer(p, "p1", "participant2Score"),
        integer(p, finalPeriod, "participant1Score"),
        integer(p, finalPeriod, "participant2Score"),
        extraTime);
  }

  private static int integer(JsonNode periods, String period, String field) {
    var value = periods.path(period).path(field);
    if (!value.isIntegralNumber())
      throw new IllegalArgumentException(
          "Punteggio regolamentare mancante: " + period + "/" + field);
    return value.intValue();
  }
}
