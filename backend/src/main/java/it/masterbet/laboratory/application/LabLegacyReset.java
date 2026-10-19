package it.masterbet.laboratory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.laboratory.infrastructure.LabRepository;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabLegacyReset {
  private static final List<String> TABLES =
      List.of(
          "betting_reveal_revision",
          "betting_round_revision",
          "betting_reveal",
          "betting_reveal_lock",
          "betting_proposal",
          "betting_round");
  private final JdbcTemplate jdbc;
  private final LabRepository repo;
  private final ObjectMapper mapper;
  private final String url;
  private final Path backupDirectory;

  public LabLegacyReset(
      JdbcTemplate jdbc,
      LabRepository repo,
      ObjectMapper mapper,
      @Value("${spring.datasource.url}") String url,
      @Value("${masterbet.data-dir}") String directory) {
    this.jdbc = jdbc;
    this.repo = repo;
    this.mapper = mapper;
    this.url = url;
    backupDirectory =
        Path.of(directory).toAbsolutePath().normalize().getParent().resolve("backups");
  }

  @Transactional
  public int reset() {
    if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):5432/masterbet_local"))
      throw new IllegalArgumentException("Reset soltanto sul database locale documentato");
    try {
      var manifest =
          mapper.readTree(Files.readString(backupDirectory.resolve("verified-lab-backup.json")));
      Path backup = Path.of(manifest.required("path").asText()).toAbsolutePath().normalize();
      if (!backup.startsWith(backupDirectory)
          || !manifest.path("database").asText().equals("masterbet_local")
          || Files.size(backup) < 1024) throw new IllegalArgumentException("Backup non valido");
      var digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(backup)) {
        byte[] block = new byte[65536];
        for (int n; (n = input.read(block)) >= 0; ) digest.update(block, 0, n);
      }
      if (!HexFormat.of()
          .formatHex(digest.digest())
          .equalsIgnoreCase(manifest.required("sha256").asText()))
        throw new IllegalArgumentException("Hash backup non valido");
      int deleted = 0;
      for (String table : TABLES) {
        var rows = jdbc.queryForList("SELECT * FROM " + table);
        for (var row : rows) {
          String payload = repo.json(row), id = RawResponseArchive.hash(table + payload);
          if (jdbc.queryForObject("SELECT COUNT(*) FROM lab_revision WHERE id=?", Integer.class, id)
              == 0)
            jdbc.update(
                "INSERT INTO lab_revision(id,entity_type,entity_id,archived_at,payload)"
                    + " VALUES(?,?,?,?,?)",
                id,
                "legacy-reset",
                table,
                Instant.now().toString(),
                payload);
        }
        deleted += jdbc.update("DELETE FROM " + table);
      }
      return deleted;
    } catch (java.io.IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Verifica del backup fallita; reset non eseguito");
    }
  }
}
