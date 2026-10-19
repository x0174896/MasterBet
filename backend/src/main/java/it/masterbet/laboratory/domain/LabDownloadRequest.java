package it.masterbet.laboratory.domain;

import java.time.*;
import java.util.*;

public record LabDownloadRequest(
    List<Integer> competitionIds,
    LocalDate from,
    LocalDate to,
    List<String> bookmakers,
    boolean refresh) {
  public LabDownloadRequest {
    competitionIds = competitionIds == null ? List.of() : List.copyOf(competitionIds);
    from = from == null ? LocalDate.of(2026, 1, 1) : from;
    to = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
    bookmakers =
        bookmakers == null || bookmakers.isEmpty()
            ? List.of("unibet", "pinnacle", "bet365")
            : List.copyOf(bookmakers);
    if (from.isBefore(LocalDate.of(2026, 1, 1))
        || to.isBefore(from)
        || to.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(14))
        || Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 730
        || bookmakers.size() > 3
        || bookmakers.stream().anyMatch(b -> !b.matches("[a-z0-9._-]{1,80}")))
      throw new IllegalArgumentException("Periodo o bookmaker non validi");
  }

  public static LabDownloadRequest defaults() {
    return new LabDownloadRequest(null, null, null, null, false);
  }
}
