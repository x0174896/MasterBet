package it.masterbet.simulation.domain;

import java.time.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StepWindowPlanner {
  public record Window(Instant cutoff, Instant end) {}

  public record CalendarSlice(Instant from, Instant to) {}

  public Window next(Instant previousCutoff, List<Instant> schedule, Instant now) {
    for (Instant cutoff = previousCutoff.plus(Duration.ofDays(7));
        cutoff.isBefore(now) && cutoff.isBefore(Instant.parse("2026-12-01T00:00:00Z"));
        cutoff = cutoff.plus(Duration.ofDays(7))) {
      Instant candidate = cutoff, end = cutoff.plus(Duration.ofDays(7));
      if (schedule.stream().anyMatch(t -> t.isAfter(candidate) && t.isBefore(end)))
        return new Window(cutoff, end);
    }
    throw new IllegalArgumentException(
        "Nessuna ulteriore finestra storica disponibile nel calendario 2026");
  }

  public List<CalendarSlice> slices(Instant previousCutoff, Instant cutoff) {
    var slices = new java.util.ArrayList<CalendarSlice>();
    for (Instant from = previousCutoff.minus(Duration.ofDays(1)); from.isBefore(cutoff); ) {
      Instant to = from.plus(Duration.ofDays(8));
      if (to.isAfter(cutoff)) to = cutoff;
      slices.add(new CalendarSlice(from, to));
      from = to;
    }
    return List.copyOf(slices);
  }
}
