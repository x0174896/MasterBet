package it.masterbet.laboratory.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.ingestion.infrastructure.RawResponseArchive;
import it.masterbet.laboratory.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LabRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public LabRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Serializzazione laboratorio fallita");
    }
  }

  public <T> T read(String value, Class<T> type) {
    try {
      return mapper.readValue(value, type);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Archivio laboratorio non valido");
    }
  }

  public List<LabCompetition> competitions() {
    return jdbc.query(
        "SELECT payload FROM lab_competition ORDER BY id",
        (r, n) -> read(r.getString(1), LabCompetition.class));
  }

  public List<LabMatch> matches() {
    return jdbc.query(
        "SELECT payload FROM lab_match ORDER BY kickoff,id",
        (r, n) -> read(r.getString(1), LabMatch.class));
  }

  public List<LabQuote> quotes() {
    return jdbc.query(
        "SELECT payload FROM lab_quote ORDER BY id",
        (r, n) -> read(r.getString(1), LabQuote.class));
  }

  public List<LabQuote> quotes(String fixtureId) {
    return jdbc.query(
        "SELECT payload FROM lab_quote WHERE fixture_id=? ORDER BY id",
        (r, n) -> read(r.getString(1), LabQuote.class),
        fixtureId);
  }

  public Set<String> quotedFixtures() {
    return new HashSet<>(
        jdbc.query("SELECT DISTINCT fixture_id FROM lab_quote", (r, n) -> r.getString(1)));
  }

  /** Keyset pages bound memory even when full market histories occupy many gigabytes. */
  public String fingerprint() {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      for (String table : List.of("lab_match", "lab_quote")) {
        String after = "";
        while (true) {
          var page =
              jdbc.query(
                  "SELECT id,payload FROM " + table + " WHERE id>? ORDER BY id LIMIT 100",
                  (r, n) -> Map.entry(r.getString(1), r.getString(2)),
                  after);
          if (page.isEmpty()) break;
          for (var row : page) {
            digest.update(row.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(row.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          }
          after = page.getLast().getKey();
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Hash non disponibile");
    }
  }

  public Optional<LabMatch> match(String id) {
    return jdbc
        .query(
            "SELECT payload FROM lab_match WHERE id=?",
            (r, n) -> read(r.getString(1), LabMatch.class),
            id)
        .stream()
        .findFirst();
  }

  @Transactional
  public void reconcile() {
    var all = matches();
    for (var stub : all)
      if (stub.fixture().id().startsWith("odds:")) {
        var official =
            all.stream()
                .filter(
                    m ->
                        !m.fixture().id().startsWith("odds:")
                            && !m.cup()
                            && m.fixture().tournamentId() == stub.fixture().tournamentId()
                            && SourceIdentity.team(m.fixture().home())
                                .equals(SourceIdentity.team(stub.fixture().home()))
                            && SourceIdentity.team(m.fixture().away())
                                .equals(SourceIdentity.team(stub.fixture().away()))
                            && Math.abs(
                                    java.time.Duration.between(
                                            m.fixture().kickoff(), stub.fixture().kickoff())
                                        .toMinutes())
                                <= 120)
                .toList();
        if (official.size() == 1) save(official.getFirst());
      }
  }

  @Transactional
  public synchronized void save(LabCompetition value) {
    String payload = json(value);
    if (jdbc.update("UPDATE lab_competition SET payload=? WHERE id=?", payload, value.id()) == 0)
      jdbc.update("INSERT INTO lab_competition(id,payload) VALUES(?,?)", value.id(), payload);
  }

  @Transactional
  public synchronized void save(LabMatch value) {
    if (!value.fixture().id().startsWith("odds:") && !value.cup()) {
      var official = value;
      var stubs =
          jdbc
              .query(
                  "SELECT payload FROM lab_match WHERE id LIKE 'odds:%' AND competition_id=?",
                  (r, n) -> read(r.getString(1), LabMatch.class), official.fixture().tournamentId())
              .stream()
              .filter(
                  m ->
                      m.fixture().tournamentId() == official.fixture().tournamentId()
                          && (official.oddsFixtureId() != null
                                  && official.oddsFixtureId().equals(m.oddsFixtureId())
                              || SourceIdentity.team(m.fixture().home())
                                      .equals(SourceIdentity.team(official.fixture().home()))
                                  && SourceIdentity.team(m.fixture().away())
                                      .equals(SourceIdentity.team(official.fixture().away())))
                          && Math.abs(
                                  java.time.Duration.between(
                                          m.fixture().kickoff(), official.fixture().kickoff())
                                      .toMinutes())
                              <= 120)
              .toList();
      if (stubs.size() > 1) throw new IllegalArgumentException("Calendario provider ambiguo");
      if (stubs.size() == 1) {
        var stub = stubs.getFirst();
        var sf = stub.fixture();
        value =
            value.linked(
                new it.masterbet.football.domain.Fixture(
                    stub.oddsFixtureId(),
                    sf.homeId(),
                    sf.awayId(),
                    sf.home(),
                    sf.away(),
                    sf.tournamentId(),
                    sf.competition(),
                    sf.seasonId(),
                    sf.kickoff(),
                    sf.endedAt(),
                    sf.timingBasis()));
        for (var q :
            jdbc.query(
                "SELECT payload FROM lab_quote WHERE fixture_id=?",
                (r, n) -> read(r.getString(1), LabQuote.class),
                stub.fixture().id())) {
          {
            archivePrevious(
                "quote-source",
                RawResponseArchive.hash(q.key()),
                "SELECT payload FROM lab_quote WHERE id=?");
            save(
                new LabQuote(
                    value.fixture().id(),
                    q.bookmaker(),
                    q.market(),
                    q.outcomeId(),
                    q.outcome(),
                    q.updates()));
          }
        }
        archivePrevious("match", stub.fixture().id(), "SELECT payload FROM lab_match WHERE id=?");
        jdbc.update("DELETE FROM lab_quote WHERE fixture_id=?", stub.fixture().id());
        jdbc.update("DELETE FROM lab_match WHERE id=?", stub.fixture().id());
      }
    }
    var prior = match(value.fixture().id());
    if (prior.isPresent()
        && (prior.get().fixture().tournamentId() != value.fixture().tournamentId()
            || !prior.get().season().equals(value.season())))
      throw new IllegalArgumentException("Identita Calcio discordante fra campionati o stagioni");
    if (prior.isPresent()
        && prior.get().fixture().timingBasis().equals("CALCIO_SCORE_ODDSPAPI_ACTUAL_END")
        && prior.get().fixture().kickoff().equals(value.fixture().kickoff())
        && value.score() != null) {
      var f = prior.get().fixture();
      value =
          value.linked(
              new it.masterbet.football.domain.Fixture(
                  prior.get().oddsFixtureId(),
                  f.homeId(),
                  f.awayId(),
                  f.home(),
                  f.away(),
                  f.tournamentId(),
                  f.competition(),
                  f.seasonId(),
                  f.kickoff(),
                  f.endedAt(),
                  "ACTUAL_END"));
    }
    archivePrevious("match", value.fixture().id(), "SELECT payload FROM lab_match WHERE id=?");
    String payload = json(value);
    if (jdbc.update(
            "UPDATE lab_match SET"
                + " competition_id=?,season_name=?,kickoff=?,is_cup=?,has_score=?,payload=? WHERE"
                + " id=?",
            value.fixture().tournamentId(),
            value.season(),
            value.fixture().kickoff().toString(),
            value.cup(),
            value.score() != null,
            payload,
            value.fixture().id())
        == 0)
      jdbc.update(
          "INSERT INTO lab_match(id,competition_id,season_name,kickoff,is_cup,has_score,payload)"
              + " VALUES(?,?,?,?,?,?,?)",
          value.fixture().id(),
          value.fixture().tournamentId(),
          value.season(),
          value.fixture().kickoff().toString(),
          value.cup(),
          value.score() != null,
          payload);
  }

  @Transactional
  public synchronized void link(
      LabMatch official, it.masterbet.football.domain.Fixture provider, List<String> evidence) {
    if (Math.abs(
            java.time.Duration.between(official.fixture().kickoff(), provider.kickoff())
                .toSeconds())
        > 7200) throw new IllegalArgumentException("Data provider discordante");
    save(official.linked(provider));
    String payload =
        json(
            Map.of(
                "official",
                official.fixture(),
                "provider",
                provider,
                "sourceHash",
                official.sourceHash(),
                "evidence",
                evidence));
    archivePrevious(
        "alignment",
        official.fixture().id(),
        "SELECT payload FROM lab_fixture_alignment WHERE official_id=?");
    if (jdbc.update(
            "UPDATE lab_fixture_alignment SET"
                + " provider_id=?,competition_id=?,season_name=?,payload=? WHERE official_id=?",
            provider.id(),
            official.fixture().tournamentId(),
            official.season(),
            payload,
            official.fixture().id())
        == 0)
      jdbc.update(
          "INSERT INTO"
              + " lab_fixture_alignment(official_id,provider_id,competition_id,season_name,payload)"
              + " VALUES(?,?,?,?,?)",
          official.fixture().id(),
          provider.id(),
          official.fixture().tournamentId(),
          official.season(),
          payload);
  }

  @Transactional
  public synchronized void save(LabQuote value) {
    String id = RawResponseArchive.hash(value.key());
    var previous =
        jdbc.query(
            "SELECT payload FROM lab_quote WHERE id=?",
            (r, n) -> read(r.getString(1), LabQuote.class),
            id);
    if (!previous.isEmpty()) {
      value = previous.getFirst().merge(value);
      if (json(value).equals(json(previous.getFirst()))) return;
    }
    archivePrevious("quote", id, "SELECT payload FROM lab_quote WHERE id=?");
    if (jdbc.update("UPDATE lab_quote SET payload=? WHERE id=?", json(value), id) == 0)
      jdbc.update(
          "INSERT INTO lab_quote(id,fixture_id,payload) VALUES(?,?,?)",
          id,
          value.fixtureId(),
          json(value));
  }

  private void archivePrevious(String kind, String id, String query) {
    var old = jdbc.query(query, (r, n) -> r.getString(1), id);
    if (old.isEmpty()) return;
    String key = RawResponseArchive.hash(kind + id + old.getFirst());
    if (jdbc.queryForObject("SELECT COUNT(*) FROM lab_revision WHERE id=?", Integer.class, key)
        == 0)
      jdbc.update(
          "INSERT INTO lab_revision(id,entity_type,entity_id,archived_at,payload)"
              + " VALUES(?,?,?,?,?)",
          key,
          kind,
          id,
          Instant.now().toString(),
          old.getFirst());
  }
}
