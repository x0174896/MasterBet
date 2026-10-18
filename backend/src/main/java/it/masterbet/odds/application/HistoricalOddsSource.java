package it.masterbet.odds.application;

import it.masterbet.football.domain.Fixture;
import it.masterbet.odds.domain.QuotedOutcome;
import java.time.Instant;
import java.util.List;

public interface HistoricalOddsSource {
  List<QuotedOutcome> downloadAt(Fixture fixture, Instant cutoff, List<String> bookmakers);
}
