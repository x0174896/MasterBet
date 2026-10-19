package it.masterbet.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawResponseArchiveTest {
  @TempDir Path dir;

  @Test
  void largeHistoryEnvelopeIsReadableAndRefreshableWithoutRelaxingPayloadLimits() {
    var mapper = new ObjectMapper();
    int originalLimit = mapper.getFactory().streamReadConstraints().getMaxStringLength();
    var archive = new RawResponseArchive(dir.toString(), mapper);
    var parameters = Map.of("fixtureId", "large");
    String body = "{\"score\":2}" + " ".repeat(20_000_100);
    archive.save("historical-odds", parameters, body);
    assertEquals(
        2, archive.read("historical-odds", parameters).orElseThrow().path("score").asInt());
    archive.save("historical-odds", parameters, "{\"score\":3}");
    assertEquals(
        3, archive.read("historical-odds", parameters).orElseThrow().path("score").asInt());
    assertEquals(originalLimit, mapper.getFactory().streamReadConstraints().getMaxStringLength());
  }

  @Test
  void refreshPreservesImmutablePreviousResponse() throws Exception {
    var archive = new RawResponseArchive(dir.toString(), new ObjectMapper());
    archive.save("fixture", Map.of("id", "f"), "{\"date\":1}");
    archive.save("fixture", Map.of("id", "f"), "{\"date\":2}");
    assertEquals(2, archive.read("fixture", Map.of("id", "f")).orElseThrow().path("date").asInt());
    try (var files = Files.list(dir.resolve("raw/revisions"))) {
      var revision = files.findFirst().orElseThrow();
      assertTrue(Files.readString(revision).contains("date\\\":1"));
    }
  }

  @Test
  void cachesExactBodyAndRejectsTampering() throws Exception {
    var archive = new RawResponseArchive(dir.toString(), new ObjectMapper());
    archive.save("scores", Map.of("fixtureId", "f"), "{\"score\":2}");
    assertEquals(
        2, archive.read("scores", Map.of("fixtureId", "f")).orElseThrow().path("score").asInt());
    Path file;
    try (var files = Files.list(dir.resolve("raw"))) {
      file = files.findFirst().orElseThrow();
    }
    Files.writeString(file, Files.readString(file).replace("score\\\":2", "score\\\":3"));
    assertThrows(
        IllegalStateException.class, () -> archive.read("scores", Map.of("fixtureId", "f")));
  }
}
