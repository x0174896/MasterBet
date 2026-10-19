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
  private final ObjectMapper envelopeMapper;

  public RawResponseArchive(@Value("${masterbet.data-dir}") String directory, ObjectMapper mapper) {
    this.directory = Path.of(directory).resolve("raw");
    this.mapper = mapper;
    // The envelope stores the entire JSON response as one string. Real histories exceed
    // Jackson's default 20M string limit even though the individual payload fields do not.
    this.envelopeMapper = mapper.copy();
    this.envelopeMapper
        .getFactory()
        .setStreamReadConstraints(
            mapper
                .getFactory()
                .streamReadConstraints()
                .rebuild()
                .maxStringLength(128_000_000)
                .build());
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
      JsonNode envelope = envelopeMapper.readTree(Files.readString(path));
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
      if (Files.exists(destination)) {
        String previous = Files.readString(destination);
        var prior = envelopeMapper.readTree(previous);
        if (prior.path("sha256").asText().equals(hash(body))) return;
        Path revisions = directory.resolve("revisions");
        Files.createDirectories(revisions);
        Path revision = revisions.resolve(hash(previous) + ".json");
        if (!Files.exists(revision))
          Files.writeString(revision, previous, StandardOpenOption.CREATE_NEW);
      }
      Path temporary = Files.createTempFile(directory, "download-", ".tmp");
      Files.writeString(temporary, mapper.writeValueAsString(envelope));
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
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
