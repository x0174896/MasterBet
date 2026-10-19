package it.masterbet.simulation.infrastructure;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvanceJobRepository {
  public record Status(
      String jobId, String sourceId, String status, String message, String updatedAt) {}

  private final JdbcTemplate jdbc;

  public AdvanceJobRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Status status() {
    var row = jdbc.queryForMap("SELECT * FROM advance_job WHERE singleton=1");
    if ("RUNNING".equals(row.get("status"))) {
      long pid = ((Number) row.get("owner_pid")).longValue();
      boolean alive =
          ProcessHandle.of(pid)
              .filter(ProcessHandle::isAlive)
              .flatMap(p -> p.info().startInstant())
              .map(t -> t.toString().equals(row.get("owner_started")))
              .orElse(false);
      if (!alive)
        update(
            (String) row.get("job_id"),
            "FAILED",
            "Processo interrotto. Riprovare lo step: archivio e scelte congelate conservati.");
    }
    return jdbc.queryForObject(
        "SELECT * FROM advance_job WHERE singleton=1",
        (r, n) ->
            new Status(
                r.getString("job_id"),
                r.getString("source_id"),
                r.getString("status"),
                r.getString("message"),
                r.getString("updated_at")));
  }

  public boolean claim(String id, String source) {
    return jdbc.update(
            "UPDATE advance_job SET job_id=?,source_id=?,status='RUNNING',message='Preparazione e"
                + " congelamento schedine',updated_at=?,owner_pid=?,owner_started=? WHERE"
                + " singleton=1 AND status<>'RUNNING'",
            id,
            source,
            Instant.now().toString(),
            ProcessHandle.current().pid(),
            ProcessHandle.current().info().startInstant().orElseThrow().toString())
        == 1;
  }

  public void update(String id, String status, String message) {
    jdbc.update(
        "UPDATE advance_job SET status=?,message=?,updated_at=? WHERE singleton=1 AND job_id=?",
        status,
        message,
        Instant.now().toString(),
        id);
  }
}
