package it.masterbet.laboratory.domain;

import it.masterbet.odds.domain.*;
import java.time.Instant;
import java.util.*;

public record LabQuote(
    String fixtureId,
    String bookmaker,
    Market market,
    String outcomeId,
    String outcome,
    List<PriceUpdate> updates) {
  public LabQuote {
    if (fixtureId == null || bookmaker == null || market == null || updates == null)
      throw new IllegalArgumentException("Storico quote non valido");
    updates = List.copyOf(updates);
  }

  public String key() {
    return fixtureId + "|" + bookmaker + "|" + market.id() + "|" + outcomeId;
  }

  public Optional<QuotedOutcome> closing(Instant kickoff) {
    // La deroga storica consente prezzi successivi al taglio, mai prezzi in-play.
    return asOf(kickoff.minusNanos(1), kickoff);
  }

  public Optional<QuotedOutcome> asOf(Instant cutoff, Instant kickoff) {
    return new AsOfPriceSelector()
        .select(updates, cutoff.isBefore(kickoff) ? cutoff : kickoff.minusNanos(1), kickoff)
        .map(
            p ->
                new QuotedOutcome(
                    fixtureId, bookmaker, market, outcomeId, outcome, p.price(), p.timestamp()));
  }

  public LabQuote merge(LabQuote other) {
    if (!key().equals(other.key()) || !market.equals(other.market()))
      throw new IllegalArgumentException("Serie quote incompatibili");
    List<PriceUpdate> all = new ArrayList<>(updates);
    all.addAll(other.updates);
    all = all.stream().distinct().sorted(Comparator.comparing(PriceUpdate::timestamp)).toList();
    var byTime = new HashMap<Instant, PriceUpdate>();
    for (var update : all) {
      var existing = byTime.putIfAbsent(update.timestamp(), update);
      if (existing != null
          && (existing.price().compareTo(update.price()) != 0
              || existing.active() != update.active()))
        throw new IllegalArgumentException("Prezzi discordanti allo stesso istante");
    }
    return new LabQuote(
        fixtureId,
        bookmaker,
        market,
        outcomeId,
        outcome,
        byTime.values().stream().sorted(Comparator.comparing(PriceUpdate::timestamp)).toList());
  }
}
