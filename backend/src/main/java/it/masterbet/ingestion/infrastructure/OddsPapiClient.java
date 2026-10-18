package it.masterbet.ingestion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OddsPapiClient {
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  private final ObjectMapper mapper;
  private final RawResponseArchive archive;
  private final String key;
  private final int reserve;
  private final Map<String, Long> lastRequest = new HashMap<>();
  private Integer remaining;

  public OddsPapiClient(
      ObjectMapper mapper,
      RawResponseArchive archive,
      @Value("${masterbet.odds-api-key}") String key,
      @Value("${masterbet.api-reserve}") int reserve) {
    this.mapper = mapper;
    this.archive = archive;
    this.key = key;
    this.reserve = reserve;
  }

  public synchronized JsonNode get(String endpoint, Map<String, String> parameters) {
    var cached = archive.read(endpoint, parameters);
    if (cached.isPresent()) return cached.get();
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
    if (remaining <= reserve)
      throw new IllegalStateException(
          "Riserva API raggiunta. Importazione sospesa; archivio locale conservato.");
    if (!endpoint.equals("historical-odds")) remaining--;
    JsonNode response = request(endpoint, parameters);
    return response;
  }

  private JsonNode request(String endpoint, Map<String, String> parameters) {
    if (key.isBlank()) throw new IllegalStateException("ODDSPAPI_KEY non configurata");
    long interval =
        endpoint.equals("historical-odds") ? 5200 : endpoint.equals("fixtures") ? 2200 : 1200;
    long wait = interval - (System.currentTimeMillis() - lastRequest.getOrDefault(endpoint, 0L));
    try {
      if (wait > 0) Thread.sleep(wait);
      var query = new TreeMap<>(parameters);
      query.put("apiKey", key);
      String encoded =
          query.entrySet().stream()
              .map(
                  e ->
                      URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                          + "="
                          + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
              .collect(Collectors.joining("&"));
      var request =
          HttpRequest.newBuilder(
                  URI.create("https://api.oddspapi.io/v4/" + endpoint + "?" + encoded))
              .timeout(Duration.ofSeconds(60))
              .header("Accept", "application/json")
              .header("User-Agent", "MasterBet-local/0.1")
              .GET()
              .build();
      lastRequest.put(endpoint, System.currentTimeMillis());
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new IllegalStateException(
            "OddsPapi "
                + endpoint
                + ": HTTP "
                + response.statusCode()
                + ". Nessun dato sostituito o inventato.");
      JsonNode data = mapper.readTree(response.body());
      if (data.has("error"))
        throw new IllegalStateException("OddsPapi ha restituito un errore su " + endpoint);
      if (!endpoint.equals("account")) archive.save(endpoint, parameters, response.body());
      System.out.println("Importazione OddsPapi: " + endpoint + " archiviato");
      return data;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Importazione interrotta");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "Connessione OddsPapi non riuscita su " + endpoint + "; verificare rete e certificati");
    }
  }
}
