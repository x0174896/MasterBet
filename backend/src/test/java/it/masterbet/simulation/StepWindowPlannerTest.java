package it.masterbet.simulation;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.simulation.domain.StepWindowPlanner;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StepWindowPlannerTest {
  @Test
  void keepsMidweekCutAndSkipsEmptyWeeks() {
    var planner = new StepWindowPlanner();
    var previous = Instant.parse("2026-04-29T12:00:00Z");
    var first =
        planner.next(
            previous,
            List.of(Instant.parse("2026-05-08T17:00:00Z")),
            Instant.parse("2026-10-18T00:00:00Z"));
    assertEquals(Instant.parse("2026-05-06T12:00:00Z"), first.cutoff());
    var later =
        planner.next(
            previous,
            List.of(Instant.parse("2026-05-23T17:00:00Z")),
            Instant.parse("2026-10-18T00:00:00Z"));
    assertEquals(Instant.parse("2026-05-20T12:00:00Z"), later.cutoff());
    var slices = planner.slices(previous, later.cutoff());
    assertEquals(3, slices.size());
    assertEquals(previous.minus(Duration.ofDays(1)), slices.getFirst().from());
    assertEquals(later.cutoff(), slices.getLast().to());
    for (int i = 1; i < slices.size(); i++)
      assertEquals(slices.get(i - 1).to(), slices.get(i).from());
  }

  @Test
  void doesNotAdvanceBeyondRealTime() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StepWindowPlanner()
                .next(
                    Instant.parse("2026-04-29T12:00:00Z"),
                    List.of(Instant.parse("2026-05-08T17:00:00Z")),
                    Instant.parse("2026-05-01T00:00:00Z")));
  }
}
