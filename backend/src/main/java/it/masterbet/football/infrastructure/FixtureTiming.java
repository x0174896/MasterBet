package it.masterbet.football.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;

/**
 * I timestamp reali di alcune risposte storiche hanno un offset incoerente. Non correggiamo il fuso
 * per ipotesi: in quel caso il risultato diventa utilizzabile solo 24 ore dopo l'inizio
 * programmato, come limite conservativo.
 */
public record FixtureTiming(Instant completedBy, String basis) {
  public static FixtureTiming from(JsonNode source, Instant scheduled) {
    if (!source.path("statusName").asText().equals("Finished"))
      return new FixtureTiming(null, "SCHEDULED");
    Instant start = read(source, "trueStartTime"), end = read(source, "trueEndTime");
    if (start != null
        && end != null
        && Math.abs(Duration.between(scheduled, start).toSeconds()) <= 900
        && !end.isBefore(scheduled)
        && !end.isBefore(start)
        && end.isBefore(scheduled.plus(Duration.ofHours(6))))
      return new FixtureTiming(end, "ACTUAL_END");
    return new FixtureTiming(scheduled.plus(Duration.ofDays(1)), "CONSERVATIVE_24H_BOUND");
  }

  private static Instant read(JsonNode source, String field) {
    return source.path(field).isTextual() ? Instant.parse(source.path(field).asText()) : null;
  }
}
