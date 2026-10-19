package it.masterbet.laboratory.domain;

import it.masterbet.football.domain.Fixture;
import java.time.*;
import java.util.*;

/** One-to-one identities supported by names or repeated fixtures with known opponents. */
public final class FixtureIdentityResolver {
  public record Result(Map<String, Fixture> matches, Map<String, List<String>> evidence) {}

  private record Team(String season, long id) {}

  private record Pair(Team official, long provider) {}

  private record Vote(String fixture, LocalDate day, long opponent) {}

  public Result resolve(List<LabMatch> official, List<Fixture> provider) {
    if (official.stream().map(m -> m.fixture().tournamentId()).distinct().count() > 1
        || provider.stream().map(Fixture::tournamentId).distinct().count() > 1)
      throw new IllegalArgumentException("Riconciliazione limitata a un campionato per fonte");
    Map<Team, Long> identities = new HashMap<>();
    Map<Team, Set<Long>> named = new HashMap<>();
    for (var m : official)
      for (var p : provider)
        if (near(m, p) && same(m, p, Map.of(), true) && same(m, p, Map.of(), false)) {
          named
              .computeIfAbsent(new Team(m.season(), m.fixture().homeId()), k -> new HashSet<>())
              .add(p.homeId());
          named
              .computeIfAbsent(new Team(m.season(), m.fixture().awayId()), k -> new HashSet<>())
              .add(p.awayId());
        }
    named.forEach(
        (team, ids) -> {
          if (ids.size() > 1
              || named.entrySet().stream()
                  .anyMatch(
                      e ->
                          !e.getKey().equals(team)
                              && e.getKey().season().equals(team.season())
                              && !Collections.disjoint(e.getValue(), ids)))
            identities.put(team, -1L);
        });
    Map<String, Fixture> matched = new TreeMap<>();
    Map<String, List<String>> evidence = new TreeMap<>();
    Map<Pair, List<String>> learned = new HashMap<>();
    for (int iteration = 0; iteration <= official.size() * 2; iteration++) {
      int before = identities.size();
      Map<LabMatch, List<Fixture>> proposals = new LinkedHashMap<>();
      for (var m : official) {
        var candidates =
            provider.stream()
                .filter(p -> near(m, p))
                .filter(p -> same(m, p, identities, true) && same(m, p, identities, false))
                .toList();
        proposals.put(m, candidates);
      }
      Map<String, Integer> uses = new HashMap<>();
      proposals.values().stream()
          .filter(v -> v.size() == 1)
          .forEach(v -> uses.merge(v.getFirst().id(), 1, Integer::sum));
      for (var entry : proposals.entrySet()) {
        var m = entry.getKey();
        if (entry.getValue().size() != 1) continue;
        var p = entry.getValue().getFirst();
        if (uses.get(p.id()) != 1 || !compatible(m, p, identities)) continue;
        identities.put(new Team(m.season(), m.fixture().homeId()), p.homeId());
        identities.put(new Team(m.season(), m.fixture().awayId()), p.awayId());
        matched.put(m.fixture().id(), p);
        List<String> proof = new ArrayList<>();
        proof.add("Stesso campionato, casa/ospite e orario entro due ore");
        for (var pair :
            List.of(
                new Pair(new Team(m.season(), m.fixture().homeId()), p.homeId()),
                new Pair(new Team(m.season(), m.fixture().awayId()), p.awayId())))
          proof.addAll(learned.getOrDefault(pair, List.of()));
        if (proof.size() == 1) proof.add("Nomi normalizzati o alias espliciti verificati");
        evidence.put(m.fixture().id(), List.copyOf(proof));
      }
      Map<Pair, List<Vote>> votes = new HashMap<>();
      for (var m : official) {
        if (matched.containsKey(m.fixture().id())) continue;
        var anchored =
            provider.stream()
                .filter(p -> near(m, p))
                .filter(p -> same(m, p, identities, true) || same(m, p, identities, false))
                .toList();
        if (anchored.size() != 1) continue;
        var p = anchored.getFirst();
        boolean home = same(m, p, identities, true);
        var pair =
            new Pair(
                new Team(m.season(), home ? m.fixture().awayId() : m.fixture().homeId()),
                home ? p.awayId() : p.homeId());
        votes
            .computeIfAbsent(pair, k -> new ArrayList<>())
            .add(
                new Vote(
                    m.fixture().id(),
                    m.fixture().kickoff().atZone(ZoneOffset.UTC).toLocalDate(),
                    home ? m.fixture().homeId() : m.fixture().awayId()));
      }
      for (var entry : votes.entrySet()) {
        var pair = entry.getKey();
        var support = entry.getValue();
        if (support.stream().map(Vote::day).distinct().count() < 2
            || support.stream().map(Vote::opponent).distinct().count() < 2) continue;
        if (votes.keySet().stream()
            .anyMatch(
                other ->
                    !other.equals(pair)
                        && (other.official().equals(pair.official())
                            || other.official().season().equals(pair.official().season())
                                && other.provider() == pair.provider()))) continue;
        if (!canBind(pair.official(), pair.provider(), identities)) continue;
        identities.put(pair.official(), pair.provider());
        learned.put(
            pair,
            support.stream()
                .map(v -> "Identita da avversario verificato: " + v.fixture())
                .distinct()
                .toList());
      }
      if (identities.size() == before) break;
    }
    return new Result(Map.copyOf(matched), Map.copyOf(evidence));
  }

  private boolean near(LabMatch m, Fixture p) {
    return Duration.between(m.fixture().kickoff(), p.kickoff()).abs().compareTo(Duration.ofHours(2))
        <= 0;
  }

  private boolean same(LabMatch m, Fixture p, Map<Team, Long> identities, boolean home) {
    var f = m.fixture();
    Team team = new Team(m.season(), home ? f.homeId() : f.awayId());
    long id = home ? p.homeId() : p.awayId();
    if (identities.containsKey(team)) return identities.get(team) == id;
    String a = SourceIdentity.team(home ? f.home() : f.away());
    String b = SourceIdentity.team(home ? p.home() : p.away());
    return !a.isBlank() && a.equals(b);
  }

  private boolean compatible(LabMatch m, Fixture p, Map<Team, Long> ids) {
    return canBind(new Team(m.season(), m.fixture().homeId()), p.homeId(), ids)
        && canBind(new Team(m.season(), m.fixture().awayId()), p.awayId(), ids);
  }

  private boolean canBind(Team team, long provider, Map<Team, Long> ids) {
    return (!ids.containsKey(team) || ids.get(team) == provider)
        && ids.entrySet().stream()
            .noneMatch(
                e ->
                    !e.getKey().equals(team)
                        && e.getKey().season().equals(team.season())
                        && e.getValue() == provider);
  }
}
