package it.masterbet.ingestion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiClient {
  private final ObjectMapper mapper;
  private final RawResponseArchive archive;
  private final OddsPapiHttpTransport transport;
  private final int reserve;
  private Integer remaining;

  public OddsPapiClient(
      ObjectMapper mapper,
      RawResponseArchive archive,
      OddsPapiHttpTransport transport,
      @Value("${masterbet.api-reserve}") int reserve) {
    this.mapper = mapper;
    this.archive = archive;
    this.transport = transport;
    this.reserve = reserve;
  }

  public synchronized JsonNode get(String endpoint, Map<String, String> parameters) {
    return get(endpoint, parameters, false);
  }

  public synchronized JsonNode refresh(String endpoint, Map<String, String> parameters) {
    return get(endpoint, parameters, true);
  }

  public synchronized JsonNode account() {
    return request("account", Map.of());
  }

  private JsonNode get(String endpoint, Map<String, String> parameters, boolean refresh) {
    var cached = archive.read(endpoint, parameters);
    if (!refresh && cached.isPresent()) return cached.get();
    if (remaining == null) {
      var account = request("account", Map.of());
      for (var subscription : account.path("subscriptions")) {
        if (subscription.path("is_active").asBoolean()) {
          if (!subscription.path("price").isNull() && subscription.path("price").asDouble() > 0)
            throw new IllegalStateException(
                "Importazione configurata esclusivamente per piano gratuito");
          remaining =
              subscription.path("request_limit").asInt()
                  - subscription.path("request_count").asInt();
        }
      }
      if (remaining == null) throw new IllegalStateException("Piano API non disponibile");
    }
    if (!hasBudget(remaining, endpoint.equals("historical-odds") ? 0 : 1, reserve))
      throw new IllegalStateException(
          "Riserva API raggiunta. Importazione sospesa; archivio locale conservato.");
    if (!endpoint.equals("historical-odds")) remaining--;
    JsonNode response = request(endpoint, parameters);
    return response;
  }

  /** Verifica preventivamente le sole richieste non presenti in cache; account non e fatturato. */
  public synchronized void preflightFixtures(List<Map<String, String>> requests) {
    long needed = requests.stream().filter(p -> archive.read("fixtures", p).isEmpty()).count();
    if (needed == 0) return;
    var account = request("account", Map.of());
    remaining = null;
    for (var s : account.path("subscriptions"))
      if (s.path("is_active").asBoolean()) {
        if (!s.path("price").isNull() && s.path("price").asDouble() > 0)
          throw new IllegalStateException("Consentito solo piano gratuito");
        remaining = s.path("request_limit").asInt() - s.path("request_count").asInt();
      }
    if (remaining == null || !hasBudget(remaining, needed, reserve))
      throw new IllegalStateException(
          "Quota gratuita insufficiente per completare lo step; cache conservata");
  }

  static boolean hasBudget(int remaining, long cost, int reserve) {
    // Le quote storiche sono gratuite ma il provider richiede un saldo positivo.
    return remaining > 0 && remaining - cost >= reserve;
  }

  private JsonNode request(String endpoint, Map<String, String> parameters) {
    try {
      String body = transport.get(endpoint, parameters);
      JsonNode data = mapper.readTree(body);
      if (data.has("error"))
        throw new IllegalStateException("OddsPapi ha restituito un errore su " + endpoint);
      if (!endpoint.equals("account")) archive.save(endpoint, parameters, body);
      org.slf4j.LoggerFactory.getLogger(OddsPapiClient.class)
          .info(
              "OddsPapi {}: {}",
              endpoint,
              endpoint.equals("account") ? "account verificato" : "risposta archiviata");
      return data;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Risposta JSON OddsPapi non valida su " + endpoint);
    }
  }
}
