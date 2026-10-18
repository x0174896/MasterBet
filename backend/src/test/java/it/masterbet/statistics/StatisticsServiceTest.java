package it.masterbet.statistics;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.TestMatches;
import it.masterbet.statistics.application.StatisticsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsServiceTest {
  @Test
  void aggregatesHomeAwayAndPoints() {
    var match = TestMatches.played("f", "2026-04-25T12:00:00Z", 3, 1);
    var rows =
        new StatisticsService().calculate(List.of(match), Instant.parse("2026-04-29T12:00:00Z"));
    assertEquals(3, rows.getFirst().points());
    assertEquals(1, rows.getFirst().homePlayed());
    assertEquals(3, rows.getFirst().secondHalfFor());
    assertEquals(1, rows.getLast().awayPlayed());
    assertEquals(0, rows.getLast().points());
  }

  @Test
  void refusesFutureResults() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StatisticsService()
                .calculate(
                    List.of(TestMatches.played("f", "2026-05-01T12:00:00Z", 3, 1)),
                    Instant.parse("2026-04-29T12:00:00Z")));
  }
}
