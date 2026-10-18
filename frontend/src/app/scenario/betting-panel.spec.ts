import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BettingPanel } from './betting-panel';
import { BettingView } from './betting.models';
describe('Schedine e job unico', () => {
  let http: HttpTestingController;
  const evaluation = {
    slips: [],
    closedStake: 0,
    profit: 0,
    pendingStake: 0,
    bankroll: 500,
    available: 500,
    roi: null,
    maxDrawdown: 0,
    singlesProfit: 0,
    doublesProfit: 0,
    resolvedLegs: 0,
    binaryBrier: null,
  };
  const view: BettingView = {
    current: {
      scenarioId: 'round7',
      datasetSha256: 'hash',
      modelId: 'model',
      cutoff: '2026-04-29T12:00:00Z',
      frozenAt: '2026-10-18T12:00:00Z',
      config: { unitValue: 5, initialBankrollUnits: 100, maxRoundUnits: 2 },
      openingBankroll: 500,
      leagueOpeningBankroll: 500,
      cups: [],
      league: [],
      policyVersion: 'five-slips-v2',
    },
    cups: evaluation,
    league: evaluation,
    frozen: false,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function setup() {
    const f = TestBed.createComponent(BettingPanel);
    f.detectChanges();
    http.expectOne('/api/betting').flush(view);
    http.expectOne('/api/advance-job').flush({ status: 'IDLE', message: 'Nessun job' });
    f.detectChanges();
    return f;
  }
  it('legge senza avviare download e mostra assenza di proposte', () => {
    const f = setup();
    expect(f.nativeElement.textContent).toContain('Nessuna schedina');
    expect(f.componentInstance.busy()).toBe(false);
  });
  it('invia una sola richiesta completa e blocca doppi clic', () => {
    const f = setup();
    f.componentInstance.advance();
    f.componentInstance.advance();
    const r = http.expectOne('/api/advance-job');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({
      expectedScenarioId: 'round7',
      config: { unitValue: 5, initialBankrollUnits: 100, maxRoundUnits: 2 },
    });
    r.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(f.componentInstance.error()).toContain('Avvio non confermato');
    expect(f.componentInstance.busy()).toBe(false);
  });
  it('rifiuta limiti non validi senza contattare il backend', () => {
    const f = setup();
    f.componentInstance.maxRoundUnits = 20;
    f.componentInstance.advance();
    expect(f.componentInstance.error()).toContain('massimo 2');
    http.expectNone('/api/advance-job');
  });
  it('salva la proposta una sola volta e mantiene lo scenario corrente', () => {
    const f = setup();
    f.componentInstance.save();
    f.componentInstance.save();
    const request = http.expectOne('/api/betting/save');
    expect(request.request.body).toEqual({ expectedScenarioId: 'round7' });
    request.flush({ ...view, frozen: true });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Ultime schedine salvate');
    expect(f.componentInstance.busy()).toBe(false);
    http.expectNone('/api/advance-job');
  });
  it('mostra linea e periodo della giocata senza confondere primo e secondo tempo', () => {
    const f = setup();
    f.componentInstance.view.set({
      ...view,
      current: {
        ...view.current,
        cups: [
          {
            id: 'S1',
            kind: 'SINGLE',
            bookmaker: 'unibet',
            units: 0.5,
            stake: 2.5,
            price: 1.98,
            potentialPayout: 4.95,
            potentialProfit: 2.45,
            probability: 0.62,
            expectedReturn: 0.2276,
            rationale: 'test',
            legs: [
              {
                home: 'A',
                away: 'B',
                kickoff: '2026-05-10T15:00:00Z',
                probability: 0.62,
                quote: {
                  fixtureId: 'f',
                  bookmaker: 'unibet',
                  outcome: 'Under',
                  price: 1.98,
                  observedAt: '2026-05-06T10:00:00Z',
                  market: {
                    name: 'Over Under Second Half',
                    type: 'totals',
                    period: 'p2',
                    line: 1.5,
                  },
                },
              },
            ],
          },
        ],
      },
    });
    f.detectChanges();
    const text = (f.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(text).toMatch(/Under\s*1\.5/);
    expect(text).toContain('Secondo tempo');
  });
  it('recupera lo stato fallito lasciando consultabili le schedine', () => {
    const f = TestBed.createComponent(BettingPanel);
    f.detectChanges();
    http.expectOne('/api/betting').flush(view);
    http.expectOne('/api/advance-job').flush({ status: 'FAILED', message: 'Quota insufficiente' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Quota insufficiente');
    expect(f.componentInstance.view()).not.toBeNull();
  });
});
