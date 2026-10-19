package it.masterbet.laboratory;

import static org.junit.jupiter.api.Assertions.*;

import it.masterbet.football.domain.*;
import it.masterbet.laboratory.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FixtureIdentityResolverTest {
  final FixtureIdentityResolver resolver = new FixtureIdentityResolver();
  final Instant at = Instant.parse("2026-03-01T15:00:00Z");

  LabMatch official(String id, int day, long h, long a, String hn, String an) {
    var f = new Fixture(id, h, a, hn, an, 8, "League", 2026L, at.plusSeconds(day * 86400L), null);
    return new LabMatch(f, "2026", "1", false, null, "source", null, "hash");
  }

  Fixture provider(String id, int day, long h, long a, String hn, String an) {
    return new Fixture(id, h, a, hn, an, 17, "League", 2026L, at.plusSeconds(day * 86400L), null);
  }

  @Test
  void verifiedNamesKeepWomenAndReservesDistinct() {
    assertEquals(SourceIdentity.team("Arsenal W"), SourceIdentity.team("Arsenal WFC"));
    assertEquals(
        SourceIdentity.team("Tottenham W"), SourceIdentity.team("Tottenham Hotspur Women"));
    assertNotEquals(SourceIdentity.team("Arsenal"), SourceIdentity.team("Arsenal W"));
    assertNotEquals(SourceIdentity.team("Jong Ajax"), SourceIdentity.team("Ajax Amsterdam"));
    assertEquals(SourceIdentity.team("Jong Ajax"), SourceIdentity.team("Jong Ajax Amsterdam"));
    assertEquals(SourceIdentity.team("Nürnberg"), SourceIdentity.team("1 FC Nuremberg"));
  }

  @Test
  void twoDifferentKnownOpponentsOnDifferentDaysCanEstablishAnIdentity() {
    var official =
        List.of(official("a", 0, 1, 2, "Alpha", "Beta"), official("b", 7, 1, 3, "Alpha", "Gamma"));
    var provider =
        List.of(
            provider("p", 0, 10, 20, "Other spelling", "Beta"),
            provider("q", 7, 10, 30, "Other spelling", "Gamma"));
    var result = resolver.resolve(official, provider);
    assertEquals(2, result.matches().size());
    assertTrue(
        result.evidence().get("a").stream().anyMatch(s -> s.contains("avversario verificato")));
  }

  @Test
  void oneCoincidenceIsInsufficientAndAmbiguousProviderEventsRemainExcluded() {
    var m = official("a", 0, 1, 2, "Alpha", "Beta");
    assertTrue(
        resolver
            .resolve(List.of(m), List.of(provider("p", 0, 10, 20, "Other", "Beta")))
            .matches()
            .isEmpty());
    assertTrue(
        resolver
            .resolve(
                List.of(m),
                List.of(
                    provider("p", 0, 10, 20, "Alpha", "Beta"),
                    provider("q", 0, 10, 20, "Alpha", "Beta")))
            .matches()
            .isEmpty());
  }

  @Test
  void sameOpponentTwiceDoesNotEstablishUnknownIdentity() {
    var official =
        List.of(official("a", 0, 1, 2, "Alpha", "Beta"), official("b", 7, 1, 2, "Alpha", "Beta"));
    var provider =
        List.of(
            provider("p", 0, 10, 20, "Other", "Beta"), provider("q", 7, 10, 20, "Other", "Beta"));
    assertTrue(resolver.resolve(official, provider).matches().isEmpty());
  }

  @Test
  void conflictingParticipantIdsCannotBeResolvedByInputOrder() {
    var official =
        List.of(official("a", 0, 1, 2, "Alpha", "Beta"), official("b", 7, 1, 3, "Alpha", "Gamma"));
    var provider =
        List.of(
            provider("p", 0, 10, 20, "Alpha", "Beta"), provider("q", 7, 11, 30, "Alpha", "Gamma"));
    assertTrue(resolver.resolve(official, provider).matches().isEmpty());
  }

  @Test
  void postponedDifferentDatesAndEmptyNamesCannotMatch() {
    assertTrue(
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "Alpha", "Beta")),
                List.of(provider("p", 1, 10, 20, "Alpha", "Beta")))
            .matches()
            .isEmpty());
    assertTrue(
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "???", "!!!")),
                List.of(provider("p", 0, 10, 20, "@@@", "###")))
            .matches()
            .isEmpty());
  }
}
