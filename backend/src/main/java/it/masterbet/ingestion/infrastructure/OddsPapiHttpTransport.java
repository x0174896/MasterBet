package it.masterbet.ingestion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Trasporto con attese limitate e diagnostica priva di URL autenticati o risposte private. */
@Component
public class OddsPapiHttpTransport {
  @FunctionalInterface
  interface Sleeper {
    void sleep(long milliseconds) throws InterruptedException;
  }

  private static final int MAX_ATTEMPTS = 3;
  private static final long MAX_RETRY_WAIT = 30_000;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final String key;
  private final Sleeper sleeper;
  private final Map<String, Long> lastRequest = new HashMap<>();

  @Autowired
  public OddsPapiHttpTransport(
      ObjectMapper mapper, @Value("${masterbet.odds-api-key}") String key) {
    this(
        mapper,
        key,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
        Thread::sleep);
  }

  OddsPapiHttpTransport(ObjectMapper mapper, String key, HttpClient http, Sleeper sleeper) {
    this.mapper = mapper;
    this.key = key;
    this.http = http;
    this.sleeper = sleeper;
  }

  public synchronized String get(String endpoint, Map<String, String> parameters) {
    if (key.isBlank()) throw new IllegalStateException("ODDSPAPI_KEY non configurata");
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
        HttpRequest.newBuilder(URI.create("https://api.oddspapi.io/v4/" + endpoint + "?" + encoded))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .header("User-Agent", "MasterBet-local/0.1")
            .GET()
            .build();
    long delay = 0;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        long interval =
            endpoint.equals("historical-odds") ? 5200 : endpoint.equals("fixtures") ? 2200 : 1200;
        long pacing =
            lastRequest.containsKey(endpoint)
                ? interval - (System.currentTimeMillis() - lastRequest.get(endpoint))
                : 0;
        long wait = Math.max(pacing, delay);
        if (wait > 0) sleeper.sleep(wait);
        HttpResponse<String> response;
        try {
          response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } finally {
          lastRequest.put(endpoint, System.currentTimeMillis());
        }
        if (response.statusCode() == 200) return response.body();
        var error = error(response.body());
        String code = error.path("code").asText();
        if (response.statusCode() == 429 && code.equals("REQUEST_LIMIT_EXCEEDED"))
          throw new IllegalStateException(
              "Quota OddsPapi esaurita; attendere il rinnovo. Cache conservata");
        boolean rateLimited =
            response.statusCode() == 429
                && (code.equals("RATE_LIMITED")
                    || response.headers().firstValue("Retry-After").isPresent());
        boolean transientServer =
            free(endpoint) && Set.of(502, 503, 504).contains(response.statusCode());
        if ((rateLimited || transientServer) && attempt < MAX_ATTEMPTS) {
          delay = Math.max(retryAfter(response), error.path("retryMs").asLong(0));
          if (delay <= 0) delay = 2000L * attempt;
          if (delay > MAX_RETRY_WAIT)
            throw new IllegalStateException(
                "OddsPapi richiede un'attesa superiore a 30 secondi su "
                    + endpoint
                    + "; riprovare piu tardi. Cache conservata");
          retryLog(endpoint, attempt, delay);
          continue;
        }
        String safeCode =
            code.matches("[A-Z0-9_]{1,80}") ? code : (rateLimited ? "RATE_LIMITED" : "HTTP_ERROR");
        throw new OddsPapiResponseException(response.statusCode(), safeCode, endpoint);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Importazione interrotta; cache conservata");
      } catch (IOException e) {
        if (free(endpoint) && !hasCause(e, SSLException.class) && attempt < MAX_ATTEMPTS) {
          delay = 2000L * attempt;
          retryLog(endpoint, attempt, delay);
          continue;
        }
        String detail =
            hasCause(e, SSLException.class)
                ? "verifica TLS non riuscita"
                : hasCause(e, HttpTimeoutException.class)
                    ? "tempo di attesa scaduto"
                    : hasCause(e, UnknownHostException.class)
                        ? "nome host non risolto"
                        : e.getClass().getSimpleName();
        throw new IllegalStateException(
            "Connessione OddsPapi non riuscita su "
                + endpoint
                + ": "
                + detail
                + " ("
                + attempt
                + " tentativi). Cache conservata");
      }
    }
    throw new IllegalStateException("Tentativi OddsPapi esauriti");
  }

  private boolean free(String endpoint) {
    return endpoint.equals("account") || endpoint.equals("historical-odds");
  }

  private JsonNode error(String body) {
    try {
      var data = mapper.readTree(body);
      if (data == null) return mapper.createObjectNode();
      return data.path("error").isObject() ? data.path("error") : data;
    } catch (IOException e) {
      return mapper.createObjectNode();
    }
  }

  private long retryAfter(HttpResponse<?> response) {
    String value = response.headers().firstValue("Retry-After").orElse("");
    if (value.isBlank()) return 0;
    try {
      double seconds = Double.parseDouble(value);
      if (!Double.isFinite(seconds) || seconds > Long.MAX_VALUE / 1000.0) return Long.MAX_VALUE;
      return Math.max(0, (long) Math.ceil(seconds * 1000));
    } catch (NumberFormatException e) {
      try {
        return Math.max(
            0,
            Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .toMillis());
      } catch (java.time.DateTimeException | ArithmeticException ignored) {
        return 0;
      }
    }
  }

  private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (type.isInstance(cause)) return true;
    return false;
  }

  private void retryLog(String endpoint, int attempt, long delay) {
    org.slf4j.LoggerFactory.getLogger(OddsPapiHttpTransport.class)
        .info(
            "OddsPapi {}: attesa {} ms prima del tentativo {}/{}",
            endpoint,
            delay,
            attempt + 1,
            MAX_ATTEMPTS);
  }
}
