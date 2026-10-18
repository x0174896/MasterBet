package it.masterbet.betting.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.betting.domain.BettingRound;
import it.masterbet.betting.domain.SlipReveal;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RevealRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final BettingRepository rounds;

  public RevealRepository(JdbcTemplate jdbc, ObjectMapper mapper, BettingRepository rounds) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.rounds = rounds;
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
    // Un download iniziato prima del ricalcolo non può ripristinare gli importi precedenti.
    reveal = reveal.withAmounts(slip(rounds.find(reveal.scenarioId()).orElseThrow(), reveal));
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

  public void reprice(BettingRound round) {
    var values =
        jdbc.query(
            "SELECT payload FROM betting_reveal WHERE scenario_id=?",
            (rs, n) -> rs.getString(1),
            round.scenarioId());
    for (var json : values) {
      var original = decode(json);
      var updated = original.withAmounts(slip(round, original));
      jdbc.update(
          "INSERT INTO betting_reveal_revision(scenario_id,model_variant,slip_id,"
              + "policy_version,archived_at,previous_payload) VALUES(?,?,?,?,?,?)",
          original.scenarioId(),
          original.variant(),
          original.slipId(),
          round.policyVersion(),
          java.time.Instant.now().toString(),
          json);
      try {
        jdbc.update(
            "UPDATE betting_reveal SET payload=? WHERE scenario_id=? AND model_variant=? AND"
                + " slip_id=?",
            mapper.writeValueAsString(updated),
            original.scenarioId(),
            original.variant(),
            original.slipId());
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException("Ricalcolo esiti non serializzabile");
      }
    }
  }

  private BettingRound.Slip slip(BettingRound round, SlipReveal reveal) {
    var slips =
        switch (reveal.variant()) {
          case "cups" -> round.cups();
          case "league" -> round.league();
          default -> throw new IllegalArgumentException("Modello non valido");
        };
    return slips.stream()
        .filter(s -> s.id().equals(reveal.slipId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Schedina dell'esito assente"));
  }

  private SlipReveal decode(String json) {
    try {
      return mapper.readValue(json, SlipReveal.class);
    } catch (Exception e) {
      throw new IllegalStateException("Esiti salvati non leggibili");
    }
  }
}
