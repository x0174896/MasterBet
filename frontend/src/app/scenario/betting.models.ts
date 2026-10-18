export interface BettingConfig {
  unitValue: number;
  initialBankrollUnits: number;
  maxRoundUnits: number;
}
export interface BetLeg {
  home: string;
  away: string;
  kickoff: string;
  probability: number;
  quote: {
    fixtureId: string;
    bookmaker: string;
    outcome: string;
    price: number;
    observedAt: string;
    market: { name: string; type: string; period: string; line: number | null };
  };
}
export interface BetSlip {
  id: string;
  kind: string;
  bookmaker: string;
  legs: BetLeg[];
  units: number;
  stake: number;
  price: number;
  probability: number;
  expectedReturn: number;
  rationale: string;
  potentialPayout: number;
  potentialProfit: number;
}
export interface BettingRound {
  scenarioId: string;
  datasetSha256: string;
  modelId: string;
  cutoff: string;
  frozenAt: string;
  config: BettingConfig;
  openingBankroll: number;
  leagueOpeningBankroll: number;
  cups: BetSlip[];
  league: BetSlip[];
  policyVersion: string | null;
}
export interface SettledSlip {
  scenarioId: string;
  slipId: string;
  kind: string;
  status: string;
  stake: number;
  payout: number | null;
  profit: number | null;
}
export interface BettingEvaluation {
  slips: SettledSlip[];
  closedStake: number;
  profit: number;
  pendingStake: number;
  bankroll: number;
  available: number;
  roi: number | null;
  maxDrawdown: number;
  singlesProfit: number;
  doublesProfit: number;
  resolvedLegs: number;
  binaryBrier: number | null;
}
export interface BettingView {
  current: BettingRound;
  cups: BettingEvaluation;
  league: BettingEvaluation;
  frozen: boolean;
}
export interface AdvanceJob {
  jobId: string | null;
  sourceId: string | null;
  status: string;
  message: string;
  updatedAt: string;
}

export interface SlipReveal {
  scenarioId: string;
  variant: string;
  slipId: string;
  checkedAt: string;
  status: 'WON' | 'LOST' | 'PENDING';
  stake: number;
  payout: number | null;
  profit: number | null;
  results: {
    leg: BetLeg;
    score: { homeHt: number; awayHt: number; homeFt: number; awayFt: number } | null;
    won: boolean | null;
    source: string;
  }[];
}
