package it.masterbet.laboratory.infrastructure;

import it.masterbet.laboratory.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LabRunRepository {
  public record Run(
      String id,
      String status,
      Instant createdAt,
      Instant firstBet,
      Instant lastBet,
      String dataHash,
      LabRunConfig config,
      String report) {}

  private final JdbcTemplate jdbc;
  private final LabRepository json;

  public LabRunRepository(JdbcTemplate jdbc, LabRepository json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<Run> runs() {
    return jdbc.query(
        "SELECT * FROM lab_run ORDER BY created_at DESC",
        (r, n) ->
            new Run(
                r.getString("id"),
                r.getString("status"),
                Instant.parse(r.getString("created_at")),
                instant(r.getString("first_bet")),
                instant(r.getString("last_bet")),
                r.getString("data_hash"),
                json.read(r.getString("config"), LabRunConfig.class),
                r.getString("report")));
  }

  public Run run(String id) {
    return runs().stream()
        .filter(r -> r.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Campagna non trovata"));
  }

  public void create(String id, LabRunConfig config, String hash) {
    jdbc.update(
        "INSERT INTO lab_run(id,status,created_at,data_hash,config,report)"
            + " VALUES(?,'RUNNING',?,?,?,?)",
        id,
        Instant.now().toString(),
        hash,
        json.json(config),
        "Calcolo in corso");
  }

  public List<LabBet> bets(String id) {
    return jdbc.query(
        "SELECT payload FROM lab_bet WHERE run_id=? ORDER BY kickoff,id",
        (r, n) -> json.read(r.getString(1), LabBet.class),
        id);
  }

  public List<LabStep> steps(String id) {
    return jdbc.query(
        "SELECT payload FROM lab_step WHERE run_id=? ORDER BY step_number,include_cup_scores",
        (r, n) -> json.read(r.getString(1), LabStep.class),
        id);
  }

  @Transactional
  public void step(LabStep step, List<LabBet> bets) {
    step(step, bets, List.of());
  }

  @Transactional
  public void step(LabStep step, List<LabBet> bets, List<LabBet> shadows) {
    jdbc.update(
        "INSERT INTO lab_step(run_id,step_number,cutoff,include_cup_scores,payload)"
            + " VALUES(?,?,?,?,?)",
        step.runId(),
        step.number(),
        step.cutoff().toString(),
        step.includeCupScores(),
        json.json(step));
    for (var b : bets)
      jdbc.update(
          "INSERT INTO"
              + " lab_bet(id,run_id,fixture_id,competition_id,include_cup_scores,kickoff,available_at,payload)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          b.id(),
          b.runId(),
          b.fixtureId(),
          b.competitionId(),
          b.includeCupScores(),
          b.kickoff().toString(),
          b.availableAt().toString(),
          json.json(b));
    for (var b : shadows)
      jdbc.update(
          "INSERT INTO lab_learning_prediction(run_id,fixture_id,include_cup_scores,payload)"
              + " VALUES(?,?,?,?)",
          step.runId(),
          b.fixtureId(),
          b.includeCupScores(),
          json.json(b));
  }

  @Transactional
  public void finish(String id, String status, String report) {
    freezeResults(id);
    var bets = bets(id);
    Instant first =
        bets.stream().map(LabBet::availableAt).min(Comparator.naturalOrder()).orElse(null);
    Instant last = bets.stream().map(LabBet::kickoff).max(Comparator.naturalOrder()).orElse(null);
    jdbc.update(
        "UPDATE lab_run SET status=?,first_bet=?,last_bet=?,report=? WHERE id=?",
        status,
        first == null ? null : first.toString(),
        last == null ? null : last.toString(),
        report,
        id);
  }

  /** Append only: later corrections must not rewrite a campaign's saved financial result. */
  public void freezeResults(String id) {
    jdbc.update(
        "INSERT INTO lab_run_result(run_id,fixture_id,payload) SELECT ?,m.id,m.payload FROM"
            + " lab_match m WHERE m.has_score=TRUE AND (EXISTS (SELECT 1 FROM lab_bet b WHERE"
            + " b.run_id=? AND b.fixture_id=m.id) OR EXISTS (SELECT 1 FROM lab_learning_prediction"
            + " p WHERE p.run_id=? AND p.fixture_id=m.id)) AND NOT EXISTS (SELECT 1 FROM"
            + " lab_run_result f WHERE f.run_id=? AND f.fixture_id=m.id)",
        id,
        id,
        id,
        id);
  }

  public Map<String, LabMatch> results(String id) {
    Map<String, LabMatch> result = new HashMap<>();
    jdbc.query(
        "SELECT fixture_id,payload FROM lab_run_result WHERE run_id=?",
        (org.springframework.jdbc.core.RowCallbackHandler)
            r -> result.put(r.getString(1), json.read(r.getString(2), LabMatch.class)),
        id);
    return Map.copyOf(result);
  }

  public boolean revealed(String id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM lab_spoiler WHERE bet_id=?", Integer.class, id)
        > 0;
  }

  public synchronized void reveal(String id) {
    if (!revealed(id))
      jdbc.update(
          "INSERT INTO lab_spoiler(bet_id,revealed_at) VALUES(?,?)", id, Instant.now().toString());
  }

  private static Instant instant(String value) {
    return value == null ? null : Instant.parse(value);
  }
}
