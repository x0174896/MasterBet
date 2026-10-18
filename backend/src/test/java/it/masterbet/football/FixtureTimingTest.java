package it.masterbet.football;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.football.infrastructure.FixtureTiming;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FixtureTimingTest {
  final ObjectMapper json = new ObjectMapper();
  final Instant start = Instant.parse("2026-04-06T12:30:00Z");

  @Test
  void inconsistentActualTimesNeverReplaceScheduledTime() throws Exception {
    var timing =
        FixtureTiming.from(
            json.readTree(
                "{\"statusName\":\"Finished\",\"trueStartTime\":\"2026-04-06T10:30:20Z\",\"trueEndTime\":\"2026-04-06T12:27:30Z\"}"),
            start);
    assertEquals(start.plusSeconds(86400), timing.completedBy());
    assertEquals("CONSERVATIVE_24H_BOUND", timing.basis());
  }

  @Test
  void consistentEndIsRetainedExactly() throws Exception {
    var timing =
        FixtureTiming.from(
            json.readTree(
                "{\"statusName\":\"Finished\",\"trueStartTime\":\"2026-04-06T12:31:20Z\",\"trueEndTime\":\"2026-04-06T14:27:30Z\"}"),
            start);
    assertEquals(Instant.parse("2026-04-06T14:27:30Z"), timing.completedBy());
    assertEquals("ACTUAL_END", timing.basis());
  }

  @Test
  void postponedEventNeverBecomesKnownResult() throws Exception {
    assertNull(
        FixtureTiming.from(json.readTree("{\"statusName\":\"Cancelled\"}"), start).completedBy());
  }
}
