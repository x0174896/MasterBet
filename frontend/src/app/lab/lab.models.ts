export interface Coverage {
  id: number;
  name: string;
  country: string;
  cup: boolean;
  oddsTournamentId: number | null;
  status: string;
  matches: number;
  complete: number;
  withOdds: number;
  completeWithOdds: number;
  unlinkedQuotes: number;
  readyTeams: number;
  firstOdds: string | null;
  lastOdds: string | null;
}
export interface RunConfig {
  from: string;
  to: string;
  competitionIds: number[];
  unitProfit: number;
  mode: 'HISTORICAL' | 'OPERATIONAL';
  name?: string;
  strategy?: 'BASELINE' | 'CALIBRATED' | 'SIGNALS' | 'FILTERED' | 'ADAPTIVE';
  referenceRunId?: string | null;
}
export interface Run {
  id: string;
  status: string;
  createdAt: string;
  firstBet: string | null;
  lastBet: string | null;
  dataHash: string;
  config: RunConfig;
  report: string;
}
export interface Bet {
  id: string;
  fixtureId: string;
  competitionId: number;
  competition: string;
  season: string;
  home: string;
  away: string;
  kickoff: string;
  availableAt: string;
  step: number;
  includeCupScores: boolean;
  quote: {
    bookmaker: string;
    market: { name: string; period: string };
    outcome: string;
    price: number;
    observedAt: string;
  };
  probability: number;
  expectedValue: number;
  stake: number;
  unitProfit: number;
  explanation: string[];
  trainingHash: string;
}
export interface BetView {
  resultAvailableAt: string | null;
  timingBasis: string | null;
  bet: Bet;
  status: 'PENDING' | 'WON' | 'LOST';
  net: number | null;
  revealed: boolean;
  spoiler: 'WON' | 'LOST' | null;
}
export interface Point {
  index: number;
  betId: string | null;
  settledAt: string | null;
  profit: number;
  cumulative: number;
}
export interface Pattern {
  population: string;
  condition: string;
  outcome: string;
  support: number;
  occurrences: number;
  frequency: number;
  base: number;
  lift: number;
  validationSupport: number;
  validationFrequency: number;
  validationBase: number;
  confirmed: boolean;
}
export interface ModelStep {
  number: number;
  cutoff: string;
  includeCupScores: boolean;
  trainingRows: number;
  validationRows: number;
  validationMae: number | null;
  patterns: Pattern[];
  rowsPerCompetition: Record<string, number>;
  trainingHash: string;
  proposed: number;
  excluded: number;
  policy: string;
  exclusions: Record<string, string>;
  learning?: LearningSnapshot | null;
}
export interface LearningSnapshot {
  version: string;
  observations: number;
  latestResult: string | null;
  calibration: {
    support: number;
    fitted: number;
    validation: number;
    offset: number;
    rawBrier: number | null;
    calibratedBrier: number | null;
    accepted: boolean;
  };
  signals: {
    group: string;
    observations: number;
    firstGain: number;
    secondGain: number;
    disabled: boolean;
  }[];
  rules: {
    key: string;
    description: string;
    support: number;
    firstSupport: number;
    secondSupport: number;
    firstRoi: number;
    secondRoi: number;
    comparisonFirstRoi: number;
    comparisonSecondRoi: number;
    upperMeanReturn: number;
    excluded: boolean;
  }[];
  rejectedCandidates: Record<string, number>;
}
export interface CampaignMetrics {
  includeCupScores: boolean;
  proposed: number;
  settled: number;
  won: number;
  turnover: number;
  net: number;
  roi: number;
  maxDrawdown: number;
  brier: number | null;
  meanProbability: number | null;
  winRate: number | null;
  quotesOlder24h: number;
}
export interface Campaign {
  run: Run;
  metrics: CampaignMetrics[];
}
export interface Workspace {
  run: Run;
  from: string | null;
  to: string | null;
  cursor: string | null;
  includeCupScores: boolean;
  competitionId: number;
  totalBets: number;
  settled: number;
  won: number;
  lost: number;
  net: number;
  bets: BetView[];
  page: number;
  pages: number;
  chart: Point[];
  model: ModelStep[];
}
export interface TeamStats {
  competitionId: number;
  competition: string;
  season: string;
  cup: boolean;
  team: {
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
  };
}
export interface Job {
  id: string;
  kind: string;
  status: string;
  completed: number;
  total: number;
  message: string;
  startedAt: string;
  updatedAt: string;
  errors: string[];
}
export interface Session {
  configured: boolean;
  authenticated: boolean;
}
