package it.masterbet.football.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Referti ufficiali conservati separatamente, senza consumare la quota OddsPapi. */
@Component
public class NffArchive {
  private final Path directory;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

  public NffArchive(@Value("${masterbet.data-dir}") String directory, ObjectMapper mapper) {
    this.directory = Path.of(directory).resolve("nff");
    this.mapper = mapper;
  }

  public synchronized String get(String path) {
    if (!path.matches(
        "/fotballdata/(turnering/hjem/\\?fiksId=206092&underside=kamper|kamp/\\?fiksId=\\d+)"))
      throw new IllegalArgumentException("Percorso NFF non consentito");
    String url = "https://www.fotball.no" + path;
    Path file = directory.resolve(RawResponseArchive.hash(url) + ".json");
    try {
      if (Files.exists(file)) {
        var saved = mapper.readTree(Files.readString(file));
        String body = saved.required("body").asText();
        if (!url.equals(saved.path("url").asText())
            || !RawResponseArchive.hash(body).equals(saved.path("sha256").asText()))
          throw new IllegalStateException("Archivio NFF alterato");
        return body;
      }
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(url))
                  .timeout(Duration.ofSeconds(40))
                  .header("User-Agent", "MasterBet-local/0.2")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new IllegalStateException("Referto NFF non disponibile");
      Files.createDirectories(directory);
      var temporary = Files.createTempFile(directory, "nff-", ".tmp");
      Files.writeString(
          temporary,
          mapper.writeValueAsString(
              Map.of(
                  "url",
                  url,
                  "retrievedAt",
                  Instant.now().toString(),
                  "sha256",
                  RawResponseArchive.hash(response.body()),
                  "body",
                  response.body())));
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Download NFF interrotto");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Archivio o connessione NFF non disponibile");
    }
  }
}
