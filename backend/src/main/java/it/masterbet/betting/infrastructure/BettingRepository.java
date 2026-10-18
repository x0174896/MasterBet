package it.masterbet.betting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.domain.BettingRound;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BettingRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public BettingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<BettingRound> all() {
    return jdbc.query(
        "SELECT COALESCE(p.payload,r.payload) FROM betting_round r LEFT JOIN betting_proposal p ON"
            + " p.scenario_id=r.scenario_id ORDER BY r.cutoff,r.scenario_id",
        (rs, n) -> decode(rs.getString(1)));
  }

  public Optional<BettingRound> find(String id) {
    return all().stream().filter(r -> r.scenarioId().equals(id)).findFirst();
  }

  public void save(BettingRound round) {
    try {
      jdbc.update(
          "INSERT INTO betting_round(scenario_id,cutoff,payload) VALUES(?,?,?)",
          round.scenarioId(),
          round.cutoff().toString(),
          mapper.writeValueAsString(round));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Serializzazione schedine non riuscita");
    }
  }

  public void saveProposal(BettingRound round) {
    try {
      jdbc.update(
          "INSERT INTO betting_proposal(scenario_id,payload) VALUES(?,?)",
          round.scenarioId(),
          mapper.writeValueAsString(round));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Serializzazione proposta non riuscita");
    }
  }

  public void lock(String id) {
    jdbc.queryForObject(
        "SELECT scenario_id FROM betting_round WHERE scenario_id=? FOR UPDATE", String.class, id);
  }

  /** Chiamato solo dal ricalcolo transazionale esplicito, con blocco della giornata. */
  public void replaceAmounts(BettingRound round) {
    jdbc.update(
        "INSERT INTO"
            + " betting_round_revision(scenario_id,policy_version,archived_at,previous_payload)"
            + " SELECT r.scenario_id,?,?,COALESCE(p.payload,r.payload) FROM betting_round r LEFT"
            + " JOIN betting_proposal p ON p.scenario_id=r.scenario_id WHERE r.scenario_id=?",
        round.policyVersion(),
        java.time.Instant.now().toString(),
        round.scenarioId());
    try {
      int count =
          jdbc.update(
              "UPDATE betting_proposal SET payload=? WHERE scenario_id=?",
              mapper.writeValueAsString(round),
              round.scenarioId());
      if (count == 0) saveProposal(round);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Ricalcolo schedine non serializzabile");
    }
  }

  public boolean revealed(String id) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM betting_reveal_lock WHERE scenario_id=?", Integer.class, id)
        > 0;
  }

  public void lockReveal(String id) {
    if (!revealed(id)) jdbc.update("INSERT INTO betting_reveal_lock(scenario_id) VALUES(?)", id);
  }

  private BettingRound decode(String json) {
    try {
      return mapper.readValue(json, BettingRound.class);
    } catch (Exception e) {
      throw new IllegalStateException("Schedine persistite non leggibili");
    }
  }
}
