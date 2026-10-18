package it.masterbet.model.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.model.domain.ModelReport;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ModelRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void save(ModelReport model) {
    try {
      String json = mapper.writeValueAsString(model), at = Instant.now().toString();
      if (jdbc.update(
              "UPDATE trained_model SET trained_at=?,payload=? WHERE model_id=?",
              at,
              json,
              model.id())
          == 0)
        jdbc.update(
            "INSERT INTO trained_model(model_id,trained_at,payload) VALUES(?,?,?)",
            model.id(),
            at,
            json);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Impossibile salvare il modello");
    }
  }
}
