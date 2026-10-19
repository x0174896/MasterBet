package it.masterbet.laboratory.application;

import it.masterbet.football.domain.Fixture;
import it.masterbet.laboratory.domain.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FixtureMatcher {
  public FixtureIdentityResolver.Result align(List<LabMatch> official, List<Fixture> candidates) {
    return new FixtureIdentityResolver().resolve(official, candidates);
  }

  public Optional<Fixture> match(LabMatch official, List<Fixture> candidates) {
    var f = official.fixture();
    var matches =
        candidates.stream()
            .filter(
                p ->
                    SourceIdentity.team(f.home()).equals(SourceIdentity.team(p.home()))
                        && SourceIdentity.team(f.away()).equals(SourceIdentity.team(p.away()))
                        && Duration.between(f.kickoff(), p.kickoff())
                                .abs()
                                .compareTo(Duration.ofHours(2))
                            <= 0)
            .toList();
    if (matches.size() > 1)
      throw new IllegalArgumentException("Abbinamento partite ambiguo: " + f.id());
    return matches.stream().findFirst();
  }
}
