package it.masterbet.football.application;

import it.masterbet.football.domain.MatchFormat;
import it.masterbet.football.domain.Score;

public interface ScoreSource {
  Score download(String fixtureId, MatchFormat format);
}
