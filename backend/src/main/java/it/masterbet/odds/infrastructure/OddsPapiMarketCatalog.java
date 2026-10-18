package it.masterbet.odds.infrastructure;

import it.masterbet.ingestion.infrastructure.OddsPapiClient;
import it.masterbet.odds.domain.Market;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiMarketCatalog {
  private final OddsPapiClient client;
  private Map<String, Market> markets;

  public OddsPapiMarketCatalog(OddsPapiClient client) {
    this.client = client;
  }

  public synchronized Map<String, Market> load() {
    if (markets != null) return markets;
    Map<String, Market> result = new HashMap<>();
    for (var m : client.get("markets", Map.of("sportId", "10"))) {
      if (m.path("sportId").asInt() != 10) continue;
      Map<String, String> outcomes = new LinkedHashMap<>();
      for (var o : m.required("outcomes"))
        outcomes.put(o.required("outcomeId").asText(), o.required("outcomeName").asText());
      String id = m.required("marketId").asText();
      result.put(
          id,
          new Market(
              id,
              m.required("marketName").asText(),
              m.required("marketType").asText(),
              m.required("period").asText(),
              m.required("handicap").decimalValue(),
              m.path("playerProp").asBoolean(),
              Map.copyOf(outcomes)));
    }
    markets = Map.copyOf(result);
    return markets;
  }
}
