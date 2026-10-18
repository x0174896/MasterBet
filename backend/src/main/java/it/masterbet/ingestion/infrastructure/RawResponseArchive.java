package it.masterbet.ingestion.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RawResponseArchive {
  private final Path directory;
  private final ObjectMapper mapper;

  public RawResponseArchive(@Value("${masterbet.data-dir}") String directory, ObjectMapper mapper) {
    this.directory = Path.of(directory).resolve("raw");
    this.mapper = mapper;
  }

  private String identity(String endpoint, Map<String, String> parameters) {
    return endpoint + new TreeMap<>(parameters);
  }

  private Path path(String endpoint, Map<String, String> parameters) {
    return directory.resolve(endpoint + "-" + hash(identity(endpoint, parameters)) + ".json");
  }

  public Optional<JsonNode> read(String endpoint, Map<String, String> parameters) {
    Path path = path(endpoint, parameters);
    if (!Files.exists(path)) return Optional.empty();
    try {
      JsonNode envelope = mapper.readTree(Files.readString(path));
      String body = envelope.path("body").asText();
      if (!hash(body).equals(envelope.path("sha256").asText())
          || !identity(endpoint, parameters).equals(envelope.path("request").asText()))
        throw new IllegalStateException("Archivio sorgente alterato: " + path.getFileName());
      return Optional.of(mapper.readTree(body));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Archivio sorgente illeggibile: " + path.getFileName());
    }
  }

  public void save(String endpoint, Map<String, String> parameters, String body) {
    try {
      Files.createDirectories(directory);
      var envelope =
          Map.of(
              "provider",
              "OddsPapi",
              "request",
              identity(endpoint, parameters),
              "retrievedAt",
              Instant.now().toString(),
              "sha256",
              hash(body),
              "body",
              body);
      Path destination = path(endpoint, parameters);
      Path temporary = Files.createTempFile(directory, "download-", ".tmp");
      Files.writeString(temporary, mapper.writeValueAsString(envelope));
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Impossibile archiviare la risposta sorgente");
    }
  }

  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
