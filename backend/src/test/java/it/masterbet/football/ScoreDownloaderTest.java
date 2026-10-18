package it.masterbet.football;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.football.domain.MatchFormat;
import it.masterbet.football.infrastructure.OddsPapiScoreDownloader;
import org.junit.jupiter.api.Test;

class ScoreDownloaderTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final String sample =
      """
      {"fixtureId":"cup","scores":{"periods":{
        "p1":{"participant1Score":1,"participant2Score":0},
        "fulltime":{"participant1Score":3,"participant2Score":0},
        "overtime":{"participant1Score":5,"participant2Score":0},
        "result":{"participant1Score":5,"participant2Score":0}}}}
      """;

  @Test
  void secondHalfUsesRegulationScoreNotExtraTime() throws Exception {
    var score = OddsPapiScoreDownloader.parse("cup", mapper.readTree(sample));
    assertEquals(3, score.homeFt());
    assertEquals(2, score.homeSecondHalf());
    assertTrue(score.extraTime());
  }

  @Test
  void missingHalfTimeIsNotZero() throws Exception {
    var data = mapper.readTree(sample);
    ((com.fasterxml.jackson.databind.node.ObjectNode) data.path("scores").path("periods"))
        .remove("p1");
    assertThrows(IllegalArgumentException.class, () -> OddsPapiScoreDownloader.parse("cup", data));
  }

  @Test
  void wrongFixtureIdFails() throws Exception {
    var data = mapper.readTree(sample);
    assertThrows(
        IllegalArgumentException.class, () -> OddsPapiScoreDownloader.parse("other", data));
  }

  @Test
  void halfTimeGreaterThanFullTimeFails() throws Exception {
    var data =
        mapper.readTree(sample.replace("\"participant1Score\":1", "\"participant1Score\":4"));
    assertThrows(IllegalArgumentException.class, () -> OddsPapiScoreDownloader.parse("cup", data));
  }

  @Test
  void stringScoreIsNotSilentlyCoerced() throws Exception {
    var data =
        mapper.readTree(sample.replace("\"participant1Score\":1", "\"participant1Score\":\"1\""));
    assertThrows(IllegalArgumentException.class, () -> OddsPapiScoreDownloader.parse("cup", data));
  }

  @Test
  void resultCanLabelOnlyKnownRegulationOnlyCompetition() throws Exception {
    var data = mapper.readTree(sample);
    var periods =
        (com.fasterxml.jackson.databind.node.ObjectNode) data.path("scores").path("periods");
    periods.remove("fulltime");
    periods.remove("overtime");
    assertEquals(
        5, OddsPapiScoreDownloader.parse("cup", data, MatchFormat.REGULATION_ONLY).homeFt());
    assertThrows(
        IllegalArgumentException.class,
        () -> OddsPapiScoreDownloader.parse("cup", data, MatchFormat.KNOCKOUT));
  }

  @Test
  void conflictingFullTimeAndResultInLeagueFails() throws Exception {
    var data = mapper.readTree(sample);
    ((com.fasterxml.jackson.databind.node.ObjectNode) data.path("scores").path("periods"))
        .remove("overtime");
    assertThrows(
        IllegalArgumentException.class,
        () -> OddsPapiScoreDownloader.parse("cup", data, MatchFormat.REGULATION_ONLY));
  }
}
