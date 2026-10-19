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
    try {
      return new AsOfPriceSelector()
          .select(updates, cutoff.isBefore(kickoff) ? cutoff : kickoff.minusNanos(1), kickoff)
          .map(
              p ->
                  new QuotedOutcome(
                      fixtureId, bookmaker, market, outcomeId, outcome, p.price(), p.timestamp()));
    } catch (AmbiguousPriceUpdateException e) {
      // Preserve the conflicting source history, but never choose one of its prices.
      return Optional.empty();
    }
  }

  public LabQuote merge(LabQuote other) {
    if (!key().equals(other.key()) || !market.equals(other.market()))
      throw new IllegalArgumentException("Serie quote incompatibili");
    List<PriceUpdate> all = new ArrayList<>(updates);
    all.addAll(other.updates);
    all = all.stream().distinct().sorted(Comparator.comparing(PriceUpdate::timestamp)).toList();
    var distinct = new ArrayList<PriceUpdate>();
    var byTime = new HashMap<Instant, List<PriceUpdate>>();
    for (var update : all) {
      var atTime = byTime.computeIfAbsent(update.timestamp(), t -> new ArrayList<>());
      if (atTime.stream()
          .noneMatch(
              u -> u.price().compareTo(update.price()) == 0 && u.active() == update.active())) {
        atTime.add(update);
        distinct.add(update);
      }
    }
    return new LabQuote(fixtureId, bookmaker, market, outcomeId, outcome, distinct);
  }
}
