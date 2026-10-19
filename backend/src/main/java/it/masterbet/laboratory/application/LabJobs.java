package it.masterbet.laboratory.application;

import it.masterbet.laboratory.infrastructure.LabRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LabJobs {
  public record Status(
      String id,
      String kind,
      String status,
      int completed,
      int total,
      String message,
      Instant startedAt,
      Instant updatedAt,
      List<String> errors) {}

  private final JdbcTemplate jdbc;
  private final LabRepository repo;
  private final TransactionTemplate transaction;

  public LabJobs(JdbcTemplate jdbc, LabRepository repo, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.repo = repo;
    this.transaction = new TransactionTemplate(manager);
  }

  @EventListener(ApplicationStartedEvent.class)
  public void recover() {
    record Owner(String id, long pid, String started) {}
    jdbc.query(
            "SELECT id,owner_pid,owner_started FROM lab_job WHERE status='RUNNING'",
            (r, n) -> new Owner(r.getString(1), r.getLong(2), r.getString(3)))
        .forEach(
            job -> {
              var process =
                  job.pid() > 0
                      ? ProcessHandle.of(job.pid()).filter(ProcessHandle::isAlive)
                      : Optional.<ProcessHandle>empty();
              boolean reused =
                  process.isPresent()
                      && job.started() != null
                      && process
                          .get()
                          .info()
                          .startInstant()
                          .filter(t -> !t.equals(Instant.parse(job.started())))
                          .isPresent();
              if (process.isEmpty() || reused)
                transaction.executeWithoutResult(
                    t -> {
                      jdbc.update(
                          "UPDATE lab_job SET status='INTERRUPTED',message='Processo interrotto:"
                              + " ripetere la richiesta per riprendere dalla cache' WHERE id=?",
                          job.id());
                      jdbc.update("DELETE FROM lab_process_lock WHERE job_id=?", job.id());
                    });
            });
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM lab_job WHERE kind='calculate' AND status='RUNNING'",
            Integer.class)
        == 0)
      jdbc.update(
          "UPDATE lab_run SET status='INTERRUPTED',report='Calcolo interrotto: ripetere come nuova"
              + " elaborazione; step precedenti conservati' WHERE status='RUNNING'");
  }

  public List<Status> list() {
    return jdbc.query(
        "SELECT * FROM lab_job ORDER BY started_at DESC",
        (r, n) ->
            new Status(
                r.getString("id"),
                r.getString("kind"),
                r.getString("status"),
                r.getInt("completed"),
                r.getInt("total"),
                r.getString("message"),
                Instant.parse(r.getString("started_at")),
                Instant.parse(r.getString("updated_at")),
                List.of(repo.read(r.getString("payload"), String[].class))));
  }

  public synchronized Status start(String kind, Consumer<Progress> operation) {
    String id = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    transaction.executeWithoutResult(
        t -> {
          jdbc.queryForObject(
              "SELECT singleton FROM lab_start_gate WHERE singleton=1 FOR UPDATE", Integer.class);
          var active = list().stream().filter(s -> s.status().equals("RUNNING")).toList();
          if (active.stream()
              .anyMatch(
                  s ->
                      s.kind().equals(kind)
                          || (kind.equals("calculate")
                              && Set.of("catalog", "results", "quotes").contains(s.kind()))
                          || s.kind().equals("calculate")))
            throw new it.masterbet.laboratory.domain.LabProcessConflict(
                "Processo incompatibile gia in corso; attendere il completamento");
          jdbc.update(
              "INSERT INTO"
                  + " lab_job(id,kind,status,completed,total,message,started_at,updated_at,payload,owner_pid,owner_started)"
                  + " VALUES(?,?,'RUNNING',0,0,?,?,?,?,?,?)",
              id,
              kind,
              "Avvio",
              now,
              now,
              "[]",
              ProcessHandle.current().pid(),
              ProcessHandle.current().info().startInstant().orElseThrow().toString());
          jdbc.update("INSERT INTO lab_process_lock(kind,job_id) VALUES(?,?)", kind, id);
        });
    Thread.ofVirtual().name("lab-" + kind).start(() -> execute(id, kind, operation));
    return list().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
  }

  public Status blocking(String kind, Consumer<Progress> operation) {
    return await(start(kind, operation).id());
  }

  public Status await(String id) {
    try {
      while (true) {
        var state = list().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
        if (!state.status().equals("RUNNING")) return state;
        Thread.sleep(500);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Attesa interrotta");
    }
  }

  private void execute(String id, String kind, Consumer<Progress> operation) {
    var progress = new Progress(id);
    try {
      operation.accept(progress);
      progress.finish(
          progress.errors.isEmpty() ? "SUCCEEDED" : "PARTIAL",
          "Completato; verificare copertura e segnalazioni");
    } catch (Exception e) {
      progress.error(kind + ": " + safe(e));
      progress.finish("FAILED", "Operazione interrotta; dati e cache conservati");
    }
  }

  public static String safe(Exception e) {
    String value = e.getMessage();
    if (value == null
        || value.contains("apiKey")
        || value.contains("password")
        || value.contains("jdbc:")) return e.getClass().getSimpleName();
    return value.substring(0, Math.min(350, value.length()));
  }

  public final class Progress {
    private final String id;
    private int done, total;
    private final List<String> errors = new ArrayList<>();

    private Progress(String id) {
      this.id = id;
    }

    public void total(int count) {
      total = count;
    }

    public void update(String message) {
      persist("RUNNING", message);
    }

    public void item(String message) {
      done++;
      update(message);
    }

    public void error(String message) {
      errors.add(message);
      update(message);
    }

    private void finish(String status, String message) {
      transaction.executeWithoutResult(
          t -> {
            persist(status, message);
            jdbc.update("DELETE FROM lab_process_lock WHERE job_id=?", id);
          });
    }

    private void persist(String status, String message) {
      jdbc.update(
          "UPDATE lab_job SET status=?,completed=?,total=?,message=?,updated_at=?,payload=? WHERE"
              + " id=?",
          status,
          done,
          total,
          message,
          Instant.now().toString(),
          repo.json(errors),
          id);
    }
  }
}
