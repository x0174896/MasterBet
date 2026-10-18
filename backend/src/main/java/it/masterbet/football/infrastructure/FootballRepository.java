package it.masterbet.football.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.football.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FootballRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public FootballRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void saveFixture(Fixture f) {
    if (jdbc.update(
            "UPDATE source_fixture SET kickoff=?,tournament_id=?,payload=? WHERE fixture_id=?",
            f.kickoff().toString(),
            f.tournamentId(),
            json(f),
            f.id())
        == 0)
      jdbc.update(
          "INSERT INTO source_fixture(fixture_id,kickoff,tournament_id,payload) VALUES(?,?,?,?)",
          f.id(),
          f.kickoff().toString(),
          f.tournamentId(),
          json(f));
  }

  public void saveResult(PlayedMatch m) {
    saveFixture(m.fixture());
    var s = m.score();
    if (jdbc.update(
            "UPDATE match_result SET home_ht=?,away_ht=?,home_ft=?,away_ft=?,payload=? WHERE"
                + " fixture_id=?",
            s.homeHt(),
            s.awayHt(),
            s.homeFt(),
            s.awayFt(),
            json(s),
            s.fixtureId())
        == 0)
      jdbc.update(
          "INSERT INTO match_result(fixture_id,home_ht,away_ht,home_ft,away_ft,payload)"
              + " VALUES(?,?,?,?,?,?)",
          s.fixtureId(),
          s.homeHt(),
          s.awayHt(),
          s.homeFt(),
          s.awayFt(),
          json(s));
  }

  private String json(Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (Exception e) {
      throw new IllegalStateException("Serializzazione partita non riuscita");
    }
  }
}
