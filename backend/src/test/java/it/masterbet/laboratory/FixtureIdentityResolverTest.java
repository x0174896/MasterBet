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
    return official(id, day, h, a, hn, an, 8);
  }

  LabMatch official(String id, int day, long h, long a, String hn, String an, int competition) {
    var f =
        new Fixture(
            id, h, a, hn, an, competition, "League", 2026L, at.plusSeconds(day * 86400L), null);
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

  @Test
  void croatianClubPrefixesAreRecognizedOnlyInsideTheVerifiedLeague() {
    var game = official("a", 0, 1, 2, "Istra 1961", "Gorica", 244);
    var feed = provider("p", 0, 10, 20, "NK Istra 1961", "HNK Gorica");
    assertEquals("p", resolver.resolve(List.of(game), List.of(feed)).matches().get("a").id());
    assertTrue(
        resolver
            .resolve(List.of(official("a", 0, 1, 2, "Istra 1961", "Gorica")), List.of(feed))
            .matches()
            .isEmpty());
  }

  @Test
  void brazilianRegionalAliasesResolveFixturesWithoutRemovingRegionalNamesGlobally() {
    var game = official("a", 0, 1, 2, "São Paulo", "Flamengo", 648);
    var feed = provider("p", 0, 10, 20, "Sao Paulo FC SP", "CR Flamengo RJ");
    assertEquals("p", resolver.resolve(List.of(game), List.of(feed)).matches().get("a").id());
    assertNotEquals(SourceIdentity.team("São Paulo"), SourceIdentity.team("Sao Paulo FC SP"));
    assertEquals(
        SourceIdentity.team("Internacional RS W", 1631),
        SourceIdentity.team("SC Internacional RS", 1631));
    assertNotEquals(SourceIdentity.team("Santos", 648), SourceIdentity.team("Santos", 1631));
  }

  @Test
  void catalogConfirmedWomenLeagueSuppliesGenderOmittedByProviderNames() {
    var game = official("a", 0, 1, 2, "Sydney W", "Adelaide United W", 1583);
    var feed = provider("p", 0, 10, 20, "Sydney FC", "Adelaide United FC");
    assertEquals(1, resolver.resolve(List.of(game), List.of(feed)).matches().size());
    assertTrue(
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "Sydney W", "Adelaide United W", 1356)),
                List.of(feed))
            .matches()
            .isEmpty());
    assertNotEquals(SourceIdentity.team("Sydney W"), SourceIdentity.team("Sydney FC"));
  }

  @Test
  void scopedBelgianAliasesDoNotCollapseReserveOrBlankIdentities() {
    var game = official("a", 0, 1, 2, "Liège", "Francs Borains", 211);
    var feed = provider("p", 0, 10, 20, "RFC Liege", "Royal Francs Borains");
    assertEquals(1, resolver.resolve(List.of(game), List.of(feed)).matches().size());
    assertNotEquals(
        SourceIdentity.team("Club Brugge II", 211), SourceIdentity.team("Club Brugge", 211));
    assertEquals("", SourceIdentity.team(null, 1583));
    assertEquals("", SourceIdentity.team("W", 1631));
  }

  @Test
  void regionalClubNamesAreResolvedInTheirOwnLeague() {
    assertEquals(
        1,
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "Keçiörengücü", "Hatayspor", 603)),
                List.of(provider("p", 0, 10, 20, "Ankara Keciorengucu", "Hatayspor")))
            .matches()
            .size());
    assertEquals(
        1,
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "Sporting Delhi", "ATK Mohun Bagan", 1007)),
                List.of(provider("p", 0, 10, 20, "Sporting Club Delhi", "Mohun Bagan Super Giant")))
            .matches()
            .size());
    assertEquals(
        1,
        resolver
            .resolve(
                List.of(official("a", 0, 1, 2, "Central Coast Mariners", "Newcastle Jets", 1356)),
                List.of(
                    provider("p", 0, 10, 20, "Central Coast Mariners", "Newcastle United Jets")))
            .matches()
            .size());
  }
}
