import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LabState } from './lab-state';
import { Run, Workspace } from './lab.models';

describe('Stato del laboratorio', () => {
  let state: LabState;
  let http: HttpTestingController;
  const run = { id: 'run-1', status: 'COMPLETED', config: { mode: 'HISTORICAL' } } as Run;
  const workspace = (cups = false): Workspace => ({
    run,
    from: '2026-05-01T00:00:00Z',
    to: '2026-05-31T00:00:00Z',
    cursor: '2026-05-20T12:00:00Z',
    includeCupScores: cups,
    competitionId: 0,
    totalBets: 0,
    settled: 0,
    won: 0,
    lost: 0,
    net: 0,
    bets: [],
    page: 0,
    pages: 1,
    chart: [],
    model: [],
  });
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    state = TestBed.inject(LabState);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function load() {
    state.init();
    http.expectOne('/api/lab/coverage').flush([]);
    http.expectOne('/api/lab/runs').flush([run]);
    http.expectOne((r) => r.url === '/api/lab/workspace').flush(workspace());
    http.expectOne((r) => r.url === '/api/lab/statistics').flush([]);
  }
  it('carica solo gli archivi locali, senza avviare download o calcoli', () => {
    load();
    expect(state.runId()).toBe('run-1');
    expect(state.cursor()).toBe('2026-05-20T12:00:00Z');
    state.init();
    http.expectNone('/api/lab/coverage');
  });
  it('la checkbox seleziona elaborazioni distinte tramite GET, mantenendo il tempo', () => {
    load();
    state.setCups(true);
    expect(state.workspace()).toBeNull();
    expect(state.statistics()).toEqual([]);
    const request = http.expectOne((r) => r.url === '/api/lab/workspace');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('cups')).toBe('true');
    expect(request.request.params.get('cursor')).toBe('2026-05-20T12:00:00Z');
    request.flush(workspace(true));
    http.expectOne((r) => r.url === '/api/lab/statistics').flush([]);
  });
  it('annulla una richiesta precedente quando cambia campionato', () => {
    load();
    state.setCompetition(384);
    const old = http.expectOne((r) => r.url === '/api/lab/workspace');
    state.setCompetition(82);
    expect(old.cancelled).toBe(true);
    const latest = http.expectOne((r) => r.url === '/api/lab/workspace');
    expect(latest.request.params.get('competition')).toBe('82');
    expect(latest.request.params.has('cursor')).toBe(false);
    latest.flush(workspace());
    http.expectOne((r) => r.url === '/api/lab/statistics').flush([]);
  });
  it('lo spoiler aggiorna la consultazione senza chiamare il calcolo', () => {
    load();
    state.reveal('bet-1');
    const request = http.expectOne('/api/lab/reveal');
    expect(request.request.body).toEqual({ runId: 'run-1', betId: 'bet-1' });
    request.flush({ status: 'WON' });
    http.expectOne((r) => r.url === '/api/lab/workspace').flush(workspace());
    http.expectOne((r) => r.url === '/api/lab/statistics').flush([]);
    expect(state.workspace()?.net).toBe(0);
  });
  it('gestisce archivio vuoto ed errori senza inventare risultati', () => {
    state.init();
    http.expectOne('/api/lab/coverage').flush([]);
    http.expectOne('/api/lab/runs').flush([]);
    expect(state.workspace()).toBeNull();
    expect(state.loading()).toBe(false);
    state.refresh();
    http.expectOne('/api/lab/coverage').flush({}, { status: 500, statusText: 'Error' });
    expect(http.expectOne('/api/lab/runs').cancelled).toBe(true);
    expect(state.error()).toContain('backend');
  });
});
