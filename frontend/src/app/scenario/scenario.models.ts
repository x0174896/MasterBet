export interface Fixture {
  id: string;
  homeId: number;
  awayId: number;
  home: string;
  away: string;
  tournamentId: number;
  competition: string;
  seasonId: number | null;
  kickoff: string;
  endedAt: string | null;
}
export interface Score {
  fixtureId: string;
  homeHt: number;
  awayHt: number;
  homeFt: number;
  awayFt: number;
  extraTime: boolean | null;
}
export interface PlayedMatch {
  fixture: Fixture;
  score: Score;
}
export interface TargetMatch {
  fixture: Fixture;
  quotes: number;
  markets: number;
  modelledOutcomes: number;
  suggestions: number;
}
export interface TeamStatistics {
  teamId: number;
  team: string;
  played: number;
  won: number;
  drawn: number;
  lost: number;
  points: number;
  goalsFor: number;
  goalsAgainst: number;
  firstHalfFor: number;
  firstHalfAgainst: number;
  secondHalfFor: number;
  secondHalfAgainst: number;
  homePlayed: number;
  awayPlayed: number;
  homePoints: number;
  awayPoints: number;
  homeGoalsFor: number;
  homeGoalsAgainst: number;
  awayGoalsFor: number;
  awayGoalsAgainst: number;
  form: string;
}
export interface ModelSummary {
  algorithm: string;
  trainingMatches: number;
  validationMatches: number;
  trainingFrom: string;
  trainingThrough: string;
  validationFrom: string;
  validationThrough: string;
  features: string[];
  baselineBrier: number;
  leagueModelBrier: number;
  cupsModelBrier: number;
  limitation: string;
}
export interface Scenario {
  id: string;
  competition: string;
  season: string;
  cutoff: string;
  importedAt: string;
  source: string;
  datasetSha256: string;
  historicalMatches: number;
  trainingMatches: number;
  quoteCount: number;
  matches: TargetMatch[];
  history: PlayedMatch[];
  standings: TeamStatistics[];
  recentCups: Fixture[];
  cupResults: PlayedMatch[];
  model: ModelSummary;
  notes: string[];
}
export interface Quote {
  bookmaker: string;
  marketId: string;
  market: string;
  type: string;
  period: string;
  line: number | null;
  outcomeId: string;
  outcome: string;
  price: number;
  observedAt: string;
  probability: number | null;
  baselineProbability: number | null;
  fairPrice: number | null;
  expectedReturn: number | null;
  suggested: boolean;
  calculationStatus: string;
}
