package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.laboratory.domain.*;
import it.masterbet.laboratory.infrastructure.CalcioParser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalcioParserTest {
  final ObjectMapper mapper = new ObjectMapper();
  final CalcioParser parser = new CalcioParser(mapper);
  final LabCompetition c =
      new LabCompetition(
          444,
          "norvegia-eliteserien",
          "Eliteserien",
          "Norvegia",
          false,
          List.of("2026"),
          20,
          "COMUNE");
  final String body =
      """
      {"data":{"id":123,"starting_at":"2026-04-15T17:00:00Z","state":{"id":5},
      "participants":[{"id":1,"name":"Sarpsborg 08","meta":{"location":"home"}},{"id":2,"name":"Bodo/Glimt","meta":{"location":"away"}}],
      "fixtureScore":[{"type":"HT","homeScore":1,"awayScore":0},{"type":"FT","homeScore":1,"awayScore":1},{"type":"2H","homeScore":1,"awayScore":1}]}}
      """;
  String row =
      """
      {"id":123,"date":"2026-03-14T17:00:00Z","home":{"id":1},"away":{"id":2},"slug":"norvegia-eliteserien-2026-sarpsborg-08-bodo-glimt-2026-03-14"}
      """;

  @Test
  void postponedUsesActualDateNotOriginalRoundOrSlug() throws Exception {
    var m = parser.match(c, "2026", "1", mapper.readTree(row), body);
    assertEquals(Instant.parse("2026-04-15T17:00:00Z"), m.fixture().kickoff());
    assertEquals("1", m.round());
    assertEquals(0, m.score().homeSecondHalf());
    assertEquals(1, m.score().awaySecondHalf());
    assertEquals("calcio:123", m.fixture().id());
    assertFalse(m.fixture().finishedBefore(Instant.parse("2026-04-15T16:00:00Z")));
  }

  @Test
  void missingHalfTimeIsUnknownNeverZero() throws Exception {
    assertNull(
        parser
            .match(c, "2026", "1", mapper.readTree(row), body.replace("\"HT\"", "\"UNKNOWN\""))
            .score());
  }

  @Test
  void regularFinalMayExposeOnlyCumulativeSecondHalf() throws Exception {
    var parsed = mapper.readTree(body);
    ((com.fasterxml.jackson.databind.node.ArrayNode) parsed.path("data").path("fixtureScore"))
        .remove(1);
    var match =
        parser.match(c, "2026", "1", mapper.readTree(row), mapper.writeValueAsString(parsed));
    assertEquals(1, match.score().homeFt());
    assertEquals(1, match.score().awayFt());
  }

  @Test
  void unresolvedPostponementHasNoScoreDespiteOldScoreFields() throws Exception {
    var m =
        parser.match(c, "2026", "1", mapper.readTree(row), body.replace("\"id\":5", "\"id\":4"));
    assertNull(m.score());
    assertNull(m.fixture().endedAt());
  }

  @Test
  void extraTimeUsesRegulationCumulativeSecondHalfOnly() throws Exception {
    var m =
        parser.match(
            c,
            "2026",
            "1",
            mapper.readTree(row),
            body.replace("\"id\":5", "\"id\":7")
                .replace("\"type\":\"FT\",\"homeScore\":1", "\"type\":\"FT\",\"homeScore\":3"));
    assertEquals(1, m.score().homeFt());
    assertTrue(m.score().extraTime());
  }

  @Test
  void catalogDecodesFramesWithoutExecutingScripts() throws Exception {
    String payload =
        "1:{\"leagues\":[{\"id\":444,\"name\":\"Eliteserien\",\"slug\":\"norvegia-eliteserien\",\"countryName\":\"Norvegia\",\"subType\":\"domestic\",\"totalSeasons\":[\"2026\"]}]}";
    String html =
        "<script>self.__next_f.push("
            + mapper.writeValueAsString(List.of(1, payload))
            + ")</script>";
    assertEquals(444, parser.catalog(html).getFirst().id());
    assertEquals(List.of("2026"), parser.catalog(html).getFirst().seasons());
  }

  @Test
  void discordantTeamsAreRejected() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            parser.match(
                c, "2026", "1", mapper.readTree(row.replace("\"id\":2", "\"id\":3")), body));
  }
}
