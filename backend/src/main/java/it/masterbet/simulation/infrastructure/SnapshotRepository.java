package it.masterbet.simulation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.simulation.domain.Snapshot;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public SnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(Snapshot snapshot) {
    String json = encode(snapshot);
    int changed =
        jdbc.update(
            "UPDATE simulation_snapshot SET cutoff=?,created_at=?,payload=? WHERE snapshot_id=?",
            snapshot.dataset().cutoff().toString(),
            snapshot.createdAt().toString(),
            json,
            snapshot.id());
    if (changed == 0)
      jdbc.update(
          "INSERT INTO simulation_snapshot(snapshot_id,cutoff,created_at,payload) VALUES(?,?,?,?)",
          snapshot.id(),
          snapshot.dataset().cutoff().toString(),
          snapshot.createdAt().toString(),
          json);
  }

  public Optional<Snapshot> find(String id) {
    return jdbc
        .query(
            "SELECT payload FROM simulation_snapshot WHERE snapshot_id=?",
            (rs, n) -> decode(rs.getString(1)),
            id)
        .stream()
        .findFirst();
  }

  private String encode(Object data) {
    try {
      return mapper.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException("Serializzazione snapshot non riuscita");
    }
  }

  private Snapshot decode(String value) {
    try {
      return mapper.readValue(value, Snapshot.class);
    } catch (Exception e) {
      throw new IllegalStateException("Snapshot persistito non leggibile");
    }
  }
}
