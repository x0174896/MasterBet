import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Coverage, Run, Workspace, TeamStats, Job, Session, RunConfig } from './lab.models';

@Injectable({ providedIn: 'root' })
export class LabApi {
  private readonly http = inject(HttpClient);
  coverage() {
    return this.http.get<Coverage[]>('/api/lab/coverage');
  }
  runs() {
    return this.http.get<Run[]>('/api/lab/runs');
  }
  workspace(
    runId: string,
    cursor: string | null,
    cups: boolean,
    competition: number,
    page: number,
  ) {
    let params = new HttpParams()
      .set('runId', runId)
      .set('cups', cups)
      .set('competition', competition)
      .set('page', page);
    if (cursor) params = params.set('cursor', cursor);
    return this.http.get<Workspace>('/api/lab/workspace', { params });
  }
  statistics(runId: string, cursor: string, cups: boolean, competition: number) {
    return this.http.get<TeamStats[]>('/api/lab/statistics', {
      params: { runId, cursor, cups, competition },
    });
  }
  reveal(runId: string, betId: string) {
    return this.http.post<{ status: 'WON' | 'LOST' }>('/api/lab/reveal', { runId, betId });
  }
  session() {
    return this.http.get<Session>('/api/lab/admin/session');
  }
  login(password: string, setup: boolean) {
    return this.http.post<Session>(
      '/api/lab/admin/login',
      { password, setup },
      { headers: { 'X-MasterBet-Admin': '1' } },
    );
  }
  logout() {
    return this.http.post<void>(
      '/api/lab/admin/logout',
      {},
      { headers: { 'X-MasterBet-Admin': '1' } },
    );
  }
  jobs() {
    return this.http.get<Job[]>('/api/lab/admin/jobs');
  }
  catalog(refresh: boolean) {
    return this.http.post<Job>('/api/lab/admin/catalog', null, {
      params: { refresh },
      headers: { 'X-MasterBet-Admin': '1' },
    });
  }
  download(
    kind: 'results' | 'quotes',
    body: {
      from: string;
      to: string;
      competitionIds: number[];
      bookmakers: string[];
      refresh: boolean;
    },
  ) {
    return this.http.post<Job>('/api/lab/admin/' + kind, body, {
      headers: { 'X-MasterBet-Admin': '1' },
    });
  }
  calculate(body: RunConfig) {
    return this.http.post<Job>('/api/lab/admin/calculate', body, {
      headers: { 'X-MasterBet-Admin': '1' },
    });
  }
  campaign(
    download: {
      from: string;
      to: string;
      competitionIds: number[];
      bookmakers: string[];
      refresh: boolean;
    },
    unitProfit: number,
  ) {
    return this.http.post<Job>(
      '/api/lab/admin/campaign',
      { download, unitProfit },
      {
        headers: { 'X-MasterBet-Admin': '1' },
      },
    );
  }
}
