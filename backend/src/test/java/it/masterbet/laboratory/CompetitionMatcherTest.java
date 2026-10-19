package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.masterbet.laboratory.application.*;
import it.masterbet.laboratory.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CompetitionMatcherTest {
  @Test
  void archivedLeytonOrientSpellingMatchesOnlyTheSameOpponentAndTime() {
    var at = java.time.Instant.parse("2026-01-01T15:00:00Z");
    var fixture =
        new it.masterbet.football.domain.Fixture(
            "calcio:1", 1, 2, "Leyton Orient", "AFC Wimbledon", 12, "League One", 2026L, at, null);
    var official = new LabMatch(fixture, "2025/2026", "25", false, null, "source", null, "test");
    var provider =
        new it.masterbet.football.domain.Fixture(
            "provider",
            10,
            20,
            "Leyton Orient London",
            "AFC Wimbledon",
            24,
            "League One",
            2026L,
            at,
            null);
    assertEquals(
        "provider", new FixtureMatcher().match(official, List.of(provider)).orElseThrow().id());
    assertNotEquals(
        SourceIdentity.team("Leyton Orient London"), SourceIdentity.team("London City"));
  }

  @Test
  void countryAndVerifiedAliasAreRequiredAndAmbiguityExcludes() throws Exception {
    var c =
        new LabCompetition(
            9,
            "inghilterra-championship",
            "Campionato di calcio",
            "Inghilterra",
            false,
            List.of("2025/2026"),
            null,
            "pending");
    var mapper = new ObjectMapper();
    var matcher = new CompetitionMatcher();
    var data =
        mapper.readTree(
            "[{\"tournamentId\":18,\"categoryName\":\"England\",\"tournamentName\":\"Championship\"},{\"tournamentId\":206,\"categoryName\":\"Scotland\",\"tournamentName\":\"Championship\"}]");
    assertEquals(18, matcher.match(List.of(c), data).getFirst().oddsTournamentId());
    ((com.fasterxml.jackson.databind.node.ArrayNode) data).add(data.get(0));
    assertNull(matcher.match(List.of(c), data).getFirst().oddsTournamentId());
  }

  @Test
  void fixtureMatcherRejectsAmbiguousNamesAndDoesNotGuessOtherDates() {
    var m =
        LabData.match(
            "f", 384, "2026", java.time.Instant.parse("2026-03-01T12:00:00Z"), 1, 2, 0, 1, false);
    var f = m.fixture();
    assertThrows(
        IllegalArgumentException.class, () -> new FixtureMatcher().match(m, List.of(f, f)));
    assertTrue(
        new FixtureMatcher()
            .match(
                m,
                List.of(
                    new it.masterbet.football.domain.Fixture(
                        "g",
                        1,
                        2,
                        f.home(),
                        f.away(),
                        384,
                        "Serie A",
                        2026L,
                        f.kickoff().plusSeconds(86400),
                        null)))
            .isEmpty());
  }
}
