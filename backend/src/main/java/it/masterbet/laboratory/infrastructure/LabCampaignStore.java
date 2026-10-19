package it.masterbet.laboratory.infrastructure;

import it.masterbet.laboratory.application.LabJobs;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LabCampaignStore {
  private final JdbcTemplate jdbc;

  public LabCampaignStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean complete(String hash, String stage) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM lab_campaign_stage WHERE request_hash=? AND stage=? AND status IN"
                + " ('SUCCEEDED','PARTIAL')",
            Integer.class,
            hash,
            stage)
        > 0;
  }

  public void save(String hash, String stage, String config, LabJobs.Status job) {
    if (jdbc.update(
            "UPDATE lab_campaign_stage SET job_id=?,status=?,config=?,updated_at=? WHERE"
                + " request_hash=? AND stage=?",
            job.id(),
            job.status(),
            config,
            Instant.now().toString(),
            hash,
            stage)
        == 0)
      jdbc.update(
          "INSERT INTO lab_campaign_stage(request_hash,stage,job_id,status,config,updated_at)"
              + " VALUES(?,?,?,?,?,?)",
          hash,
          stage,
          job.id(),
          job.status(),
          config,
          Instant.now().toString());
  }

  public Set<String> incompleteQuoteImports() {
    return new HashSet<>(
        jdbc.query(
            "SELECT fixture_id FROM lab_download_checkpoint WHERE status='DOWNLOADED'",
            (r, n) -> r.getString(1)));
  }

  public List<String> sourceFailures() {
    return jdbc.query(
        "SELECT kind,status FROM (SELECT kind,status,technical_errors,ROW_NUMBER() OVER(PARTITION"
            + " BY kind ORDER BY started_at DESC) AS position FROM lab_job WHERE kind IN"
            + " ('results','quotes','reconcile')) recent WHERE position=1 AND (status IN"
            + " ('FAILED','INTERRUPTED','RUNNING') OR technical_errors>0)",
        (r, n) -> r.getString(1) + ": " + r.getString(2));
  }
}
