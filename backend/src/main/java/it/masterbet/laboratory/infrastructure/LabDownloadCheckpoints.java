package it.masterbet.laboratory.infrastructure;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LabDownloadCheckpoints {
  private final JdbcTemplate jdbc;

  public LabDownloadCheckpoints(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private String books(List<String> books) {
    return String.join(",", new TreeSet<>(books));
  }

  public Optional<String> status(String fixture, List<String> books) {
    return jdbc
        .query(
            "SELECT status FROM lab_download_checkpoint WHERE fixture_id=? AND bookmakers=?",
            (r, n) -> r.getString(1),
            fixture,
            books(books))
        .stream()
        .findFirst();
  }

  public synchronized void save(String fixture, List<String> books, String status) {
    if (jdbc.update(
            "UPDATE lab_download_checkpoint SET status=?,checked_at=? WHERE fixture_id=? AND"
                + " bookmakers=?",
            status,
            Instant.now().toString(),
            fixture,
            books(books))
        == 0)
      jdbc.update(
          "INSERT INTO lab_download_checkpoint(fixture_id,bookmakers,status,checked_at)"
              + " VALUES(?,?,?,?)",
          fixture,
          books(books),
          status,
          Instant.now().toString());
  }
}
