package it.masterbet.odds.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AsOfPriceSelector {
  public Optional<PriceUpdate> select(List<PriceUpdate> updates, Instant cutoff, Instant kickoff) {
    if (!cutoff.isBefore(kickoff))
      throw new IllegalArgumentException("Il taglio quote deve precedere il calcio d'inizio");
    var eligible = updates.stream().filter(u -> !u.timestamp().isAfter(cutoff)).toList();
    var last = eligible.stream().max(Comparator.comparing(PriceUpdate::timestamp));
    if (last.isEmpty()) return Optional.empty();
    boolean conflicting =
        eligible.stream()
            .filter(u -> u.timestamp().equals(last.get().timestamp()))
            .anyMatch(
                u ->
                    u.price().compareTo(last.get().price()) != 0
                        || u.active() != last.get().active());
    if (conflicting) throw new AmbiguousPriceUpdateException();
    // Se l'ultimo aggiornamento sospende il mercato, non riesumare la quota precedente.
    // Il provider puo inviare price=0 anche con active=true: resta indisponibile,
    // senza recuperare un prezzo precedente e senza trasformarlo in una quota.
    return last.filter(PriceUpdate::active)
        .filter(p -> p.price().compareTo(java.math.BigDecimal.ONE) > 0);
  }
}
