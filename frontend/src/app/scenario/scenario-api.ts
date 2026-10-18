import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Quote, Scenario } from './scenario.models';
@Injectable({ providedIn: 'root' })
export class ScenarioApi {
  private readonly http = inject(HttpClient);
  scenario() {
    return this.http.get<Scenario>('/api/scenario');
  }
  quotes(id: string) {
    return this.http.get<Quote[]>(`/api/matches/${encodeURIComponent(id)}/quotes`);
  }
}
