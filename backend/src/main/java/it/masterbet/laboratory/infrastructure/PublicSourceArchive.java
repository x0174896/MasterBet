package it.masterbet.laboratory.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublicSourceArchive {
  private final Path root;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  private long lastRequest;

  public PublicSourceArchive(
      @Value("${masterbet.data-dir}") String directory, ObjectMapper mapper) {
    root = Path.of(directory).resolve("calcio");
    this.mapper = mapper;
  }

  public synchronized String get(String url, boolean refresh) {
    URI uri = URI.create(url);
    if (!"https".equals(uri.getScheme())
        || !Set.of("www.calcio.com", "api.calcio.com").contains(uri.getHost())
        || uri.getUserInfo() != null
        || uri.getPort() != -1) throw new IllegalArgumentException("Fonte pubblica non consentita");
    Path pointer = root.resolve(RawResponseArchive.hash(url) + ".json");
    try {
      if (!refresh && Files.exists(pointer)) {
        var saved = mapper.readTree(Files.readString(pointer));
        String body = Files.readString(root.resolve(saved.required("hash").asText() + ".body"));
        if (!RawResponseArchive.hash(body).equals(saved.path("hash").asText()))
          throw new IllegalStateException("Archivio Calcio alterato");
        return body;
      }
      for (int attempt = 0; attempt < 3; attempt++) {
        long wait = 350 - (System.currentTimeMillis() - lastRequest);
        if (wait > 0) Thread.sleep(wait);
        var request =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json,text/html")
                .header("User-Agent", "MasterBet-LocalResearch/1.0")
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        lastRequest = System.currentTimeMillis();
        if ((response.statusCode() == 429 || response.statusCode() >= 500) && attempt < 2) {
          long seconds =
              response
                  .headers()
                  .firstValue("Retry-After")
                  .filter(v -> v.matches("\\d+"))
                  .map(Long::parseLong)
                  .orElse(3L * (attempt + 1));
          if (seconds > 30)
            throw new IllegalStateException(
                "Calcio richiede una pausa superiore a 30 secondi; riprendere il job");
          Thread.sleep(seconds * 1000);
          continue;
        }
        if (response.statusCode() != 200)
          throw new IllegalStateException(
              "Calcio HTTP " + response.statusCode() + " su " + uri.getPath());
        String body = response.body();
        if (body.length() > 8_000_000) throw new IllegalStateException("Risposta Calcio eccessiva");
        Files.createDirectories(root);
        String hash = RawResponseArchive.hash(body);
        Path original = root.resolve(hash + ".body");
        if (!Files.exists(original))
          Files.writeString(original, body, StandardOpenOption.CREATE_NEW);
        var envelope = Map.of("url", url, "hash", hash, "retrievedAt", Instant.now().toString());
        if (Files.exists(pointer)) {
          String previous = Files.readString(pointer);
          Path revisions = root.resolve("revisions");
          Files.createDirectories(revisions);
          Path revision = revisions.resolve(RawResponseArchive.hash(previous) + ".json");
          if (!Files.exists(revision))
            Files.writeString(revision, previous, StandardOpenOption.CREATE_NEW);
        }
        Path temp = Files.createTempFile(root, "source-", ".tmp");
        Files.writeString(temp, mapper.writeValueAsString(envelope));
        Files.move(
            temp, pointer, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return body;
      }
      throw new IllegalStateException("Fonte Calcio temporaneamente indisponibile");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Download interrotto; archivio conservato");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "Errore di rete o archivio Calcio: " + e.getClass().getSimpleName());
    }
  }
}
