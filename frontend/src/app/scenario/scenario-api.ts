import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Quote, Scenario } from './scenario.models';
import { AdvanceJob, BettingConfig, BettingView, SlipReveal } from './betting.models';
@Injectable({ providedIn: 'root' })
export class ScenarioApi {
  private readonly http = inject(HttpClient);
  scenario() {
    return this.http.get<Scenario>('/api/scenario');
  }
  quotes(id: string) {
    return this.http.get<Quote[]>(`/api/matches/${encodeURIComponent(id)}/quotes`);
  }
  betting() {
    return this.http.get<BettingView>('/api/betting');
  }
  saveBets(expectedScenarioId: string) {
    return this.http.post<BettingView>('/api/betting/save', { expectedScenarioId });
  }
  reveal(scenario: string, variant: string, slip: string) {
    const path = [scenario, variant, slip].map(encodeURIComponent).join('/');
    return this.http.post<SlipReveal>(`/api/betting/${path}/reveal`, {});
  }
  job() {
    return this.http.get<AdvanceJob>('/api/advance-job');
  }
  advance(expectedScenarioId: string, config: BettingConfig) {
    return this.http.post<AdvanceJob>('/api/advance-job', { expectedScenarioId, config });
  }
}
