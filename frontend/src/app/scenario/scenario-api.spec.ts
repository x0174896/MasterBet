import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScenarioApi } from './scenario-api';
import { ScenarioState } from './scenario-state';
import { Scenario, TargetMatch } from './scenario.models';

describe('API e stato scenario', () => {
  let http: HttpTestingController;
  let state: ScenarioState;
  const target = (id: string) => ({ fixture: { id } }) as TargetMatch;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ScenarioState],
    });
    http = TestBed.inject(HttpTestingController);
    state = TestBed.inject(ScenarioState);
  });
  afterEach(() => http.verify());
  it('carica lo scenario e seleziona la prima partita senza scritture o chiamate al provider', () => {
    state.load();
    expect(state.loading()).toBe(true);
    const req = http.expectOne('/api/scenario');
    expect(req.request.method).toBe('GET');
    req.flush({ matches: [target('fixture-1')] } as Scenario);
    expect(state.selected()?.fixture.id).toBe('fixture-1');
    http.expectOne('/api/matches/fixture-1/quotes').flush([]);
    expect(state.quotesLoading()).toBe(false);
    expect(state.error()).toBe('');
  });
  it('distingue scenario mancante e consente un nuovo tentativo', () => {
    state.load();
    http.expectOne('/api/scenario').flush({}, { status: 404, statusText: 'Not Found' });
    expect(state.error()).toContain('non è ancora stato importato');
    expect(state.loading()).toBe(false);
    state.load();
    http.expectOne('/api/scenario').flush({ matches: [] });
    expect(state.error()).toBe('');
  });
  it('annulla la vecchia richiesta quando cambia partita', () => {
    state.select(target('one'));
    const first = http.expectOne('/api/matches/one/quotes');
    state.select(target('two'));
    expect(first.cancelled).toBe(true);
    http.expectOne('/api/matches/two/quotes').flush([{ marketId: 'selected' }]);
    expect(state.quotes()[0].marketId).toBe('selected');
  });
  it('rimuove le quote precedenti in caso di errore', () => {
    state.select(target('one'));
    http.expectOne('/api/matches/one/quotes').flush([{ marketId: 'one' }]);
    state.select(target('two'));
    http.expectOne('/api/matches/two/quotes').flush({}, { status: 500, statusText: 'Error' });
    expect(state.quotes()).toEqual([]);
    expect(state.quotesError()).toContain('Impossibile');
  });
  it('codifica gli identificatori nel percorso', () => {
    TestBed.inject(ScenarioApi).quotes('a/b').subscribe();
    http.expectOne('/api/matches/a%2Fb/quotes').flush([]);
  });
});
