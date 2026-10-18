package it.masterbet.betting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.domain.SlipReveal;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RevealRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public RevealRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public Optional<SlipReveal> find(String scenario, String variant, String slip) {
    return jdbc
        .query(
            "SELECT payload FROM betting_reveal WHERE scenario_id=? AND model_variant=? AND"
                + " slip_id=?",
            (rs, n) -> decode(rs.getString(1)),
            scenario,
            variant,
            slip)
        .stream()
        .findFirst();
  }

  @Transactional
  public SlipReveal save(SlipReveal reveal) {
    jdbc.queryForObject(
        "SELECT scenario_id FROM betting_round WHERE scenario_id=? FOR UPDATE",
        String.class,
        reveal.scenarioId());
    var previous = find(reveal.scenarioId(), reveal.variant(), reveal.slipId());
    if (previous.isPresent() && !previous.get().status().equals("PENDING")) return previous.get();
    try {
      String json = mapper.writeValueAsString(reveal);
      int count =
          jdbc.update(
              "UPDATE betting_reveal SET payload=? WHERE scenario_id=? AND model_variant=? AND"
                  + " slip_id=?",
              json,
              reveal.scenarioId(),
              reveal.variant(),
              reveal.slipId());
      if (count == 0)
        jdbc.update(
            "INSERT INTO betting_reveal(scenario_id,model_variant,slip_id,payload) VALUES(?,?,?,?)",
            reveal.scenarioId(),
            reveal.variant(),
            reveal.slipId(),
            json);
      return reveal;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Salvataggio esiti non riuscito");
    }
  }

  private SlipReveal decode(String json) {
    try {
      return mapper.readValue(json, SlipReveal.class);
    } catch (Exception e) {
      throw new IllegalStateException("Esiti salvati non leggibili");
    }
  }
}
