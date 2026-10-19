package it.masterbet.odds;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.football.domain.Fixture;
import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.odds.domain.*;
import it.masterbet.odds.infrastructure.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class HistoricalOddsCoverageTest {
  @Test
  void laterUnibetMarketsAndSuspensionsDoNotHideAvailableQuotesFromOtherBookmakers()
      throws Exception {
    var cutoff = Instant.parse("2026-05-27T12:00:00Z");
    var fixture = new Fixture(
        "id1000002067015210", 1, 2, "SK Brann", "Sarpsborg 08", 20, "Eliteserien",
        138092L, Instant.parse("2026-05-29T17:00:00Z"), null);
    var client = mock(OddsPapiClient.class);
    var catalog = mock(OddsPapiMarketCatalog.class);
    when(catalog.load()).thenReturn(Map.of(
        "ft", new Market("ft", "Match result", "1x2", "fulltime", BigDecimal.ZERO,
            false, Map.of("home", "1", "draw", "X", "away", "2")),
        "sh", new Market("sh", "Second half totals", "totals", "p2", new BigDecimal("1.5"),
            false, Map.of("over", "Over", "under", "Under"))));
    var books = List.of("unibet", "pinnacle", "bet365");
    when(client.get("historical-odds", Map.of(
        "fixtureId", fixture.id(), "bookmakers", String.join(",", books))))
        .thenReturn(new ObjectMapper().readTree("""
            {"fixtureId":"id1000002067015210","bookmakers":{
              "pinnacle":{"markets":{"ft":{"outcomes":{
                "home":{"players":{"0":[
                  {"createdAt":"2026-05-27T11:00:00Z","price":2.1,"active":true}]}},
                "draw":{"players":{"0":[
                  {"createdAt":"2026-05-27T10:00:00Z","price":3.0,"active":true},
                  {"createdAt":"2026-05-27T11:00:00Z","price":3.0,"active":false}]}}
              }}}},
              "unibet":{"markets":{"sh":{"outcomes":{
                "over":{"players":{"0":[
                  {"createdAt":"2026-05-28T12:00:00Z","price":2.0,"active":true}]}},
                "under":{"players":{"0":[
                  {"createdAt":"2026-05-28T12:00:00Z","price":1.9,"active":true}]}}
              }}}}
            }}
            """));
    var result = new OddsPapiHistoricalOddsDownloader(client, catalog, new AsOfPriceSelector())
        .downloadAt(fixture, cutoff, books);
    assertEquals(1, result.size());
    assertEquals("pinnacle", result.getFirst().bookmaker());
    assertEquals("home", result.getFirst().outcomeId());
    assertEquals(new BigDecimal("2.1"), result.getFirst().price());
    assertEquals(Instant.parse("2026-05-27T11:00:00Z"), result.getFirst().observedAt());
  }
}
