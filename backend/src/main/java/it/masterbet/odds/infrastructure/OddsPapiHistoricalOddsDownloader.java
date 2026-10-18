package it.masterbet.odds.infrastructure;

import it.masterbet.football.domain.Fixture;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.odds.application.HistoricalOddsSource;
import it.masterbet.odds.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiHistoricalOddsDownloader implements HistoricalOddsSource {
  private final OddsPapiClient client;
  private final OddsPapiMarketCatalog catalog;
  private final AsOfPriceSelector selector;

  public OddsPapiHistoricalOddsDownloader(
      OddsPapiClient client, OddsPapiMarketCatalog catalog, AsOfPriceSelector selector) {
    this.client = client;
    this.catalog = catalog;
    this.selector = selector;
  }

  public List<QuotedOutcome> downloadAt(Fixture fixture, Instant cutoff, List<String> bookmakers) {
    if (bookmakers.isEmpty() || bookmakers.size() > 3)
      throw new IllegalArgumentException("Richiesti da uno a tre bookmaker");
    var markets = catalog.load();
    var data =
        client.get(
            "historical-odds",
            Map.of("fixtureId", fixture.id(), "bookmakers", String.join(",", bookmakers)));
    if (!fixture.id().equals(data.path("fixtureId").asText()))
      throw new IllegalArgumentException("ID quote non corrispondente");
    List<QuotedOutcome> result = new ArrayList<>();
    data.required("bookmakers")
        .fields()
        .forEachRemaining(
            book ->
                book.getValue()
                    .required("markets")
                    .fields()
                    .forEachRemaining(
                        m -> {
                          Market market = markets.get(m.getKey());
                          if (market == null)
                            throw new IllegalArgumentException(
                                "Mercato non censito: " + m.getKey());
                          if (market.playerProp()) return;
                          m.getValue()
                              .required("outcomes")
                              .fields()
                              .forEachRemaining(
                                  o -> {
                                    if (!market.outcomes().containsKey(o.getKey()))
                                      throw new IllegalArgumentException("Esito non censito");
                                    List<PriceUpdate> updates = new ArrayList<>();
                                    for (var r : o.getValue().path("players").path("0")) {
                                      Instant timestamp =
                                          Instant.parse(r.required("createdAt").asText());
                                      // Non interpretare neppure gli aggiornamenti successivi al
                                      // taglio.
                                      if (timestamp.isAfter(cutoff)) continue;
                                      if (!r.path("price").isNumber()
                                          || !r.path("active").isBoolean())
                                        throw new IllegalArgumentException(
                                            "Quota senza prezzo/stato");
                                      updates.add(
                                          new PriceUpdate(
                                              timestamp,
                                              r.path("price").decimalValue(),
                                              r.path("active").asBoolean()));
                                    }
                                    selector
                                        .select(updates, cutoff, fixture.kickoff())
                                        .ifPresent(
                                            p ->
                                                result.add(
                                                    new QuotedOutcome(
                                                        fixture.id(),
                                                        book.getKey(),
                                                        market,
                                                        o.getKey(),
                                                        market.outcomes().get(o.getKey()),
                                                        p.price(),
                                                        p.timestamp())));
                                  });
                        }));
    return List.copyOf(result);
  }
}
