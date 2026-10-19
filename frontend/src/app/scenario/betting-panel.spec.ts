import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BettingPanel } from './betting-panel';
import { BetSlip, BettingView } from './betting.models';
import { vi } from 'vitest';
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
  const single: BetSlip = {
    id: 'S1',
    kind: 'SINGLE',
    bookmaker: 'unibet',
    units: 1,
    stake: 5.1,
    price: 1.98,
    potentialPayout: 10.1,
    potentialProfit: 5,
    probability: 0.62,
    expectedReturn: 0.2276,
    rationale: 'Motivazione estesa da non mostrare',
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
  };
  const view: BettingView = {
    current: {
      scenarioId: 'round7',
      datasetSha256: 'hash',
      modelId: 'model',
      cutoff: '2026-04-29T12:00:00Z',
      frozenAt: '2026-10-18T12:00:00Z',
      config: { unitValue: 5, initialBankrollUnits: 100 },
      openingBankroll: 500,
      leagueOpeningBankroll: 500,
      cups: [],
      league: [],
      policyVersion: 'net-profit-unit-v3',
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
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  function setup(selectedView: BettingView = view) {
    const f = TestBed.createComponent(BettingPanel);
    f.detectChanges();
    http.expectOne('/api/betting').flush(selectedView);
    http.expectOne('/api/advance-job').flush({ status: 'IDLE', message: 'Nessun job' });
    f.detectChanges();
    return f;
  }
  it('legge senza avviare download e mostra assenza di proposte', () => {
    const f = setup();
    expect(f.nativeElement.textContent).toContain('Nessuna schedina');
    expect(f.componentInstance.busy()).toBe(false);
    expect(f.nativeElement.querySelector('#cap')).toBeNull();
    expect(f.nativeElement.querySelector('label[for="unit"]').textContent).toContain('utile netto');
  });
  it('invia una sola richiesta completa e blocca doppi clic', () => {
    const f = setup();
    f.componentInstance.unitValue = 10;
    f.componentInstance.advance();
    f.componentInstance.advance();
    const r = http.expectOne('/api/advance-job');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({
      expectedScenarioId: 'round7',
      config: { unitValue: 10, initialBankrollUnits: 100 },
    });
    r.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(f.componentInstance.error()).toContain('Avvio non confermato');
    expect(f.componentInstance.busy()).toBe(false);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
  });
  it('rifiuta utili obiettivo non validi senza contattare il backend', () => {
    const f = setup();
    f.componentInstance.unitValue = 0;
    f.componentInstance.advance();
    expect(f.componentInstance.error()).toContain('Verifica utile obiettivo');
    http.expectNone('/api/advance-job');
  });
  it('salva la proposta una sola volta e mantiene lo scenario corrente', () => {
    const f = setup();
    f.componentInstance.save();
    f.componentInstance.save();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    const request = http.expectOne('/api/betting/save');
    expect(request.request.body).toEqual({ expectedScenarioId: 'round7' });
    request.flush({ ...view, frozen: true });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Ultime schedine salvate');
    expect(f.componentInstance.busy()).toBe(false);
    http.expectNone('/api/advance-job');
  });
  it('mostra solo i cinque indicatori richiesti conservando partita, linea e periodo', () => {
    const f = setup({
      ...view,
      current: {
        ...view.current,
        cups: [single],
      },
    });
    const article = (f.nativeElement as HTMLElement).querySelector('article')!;
    const text = article.textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('A – B');
    expect(text).toMatch(/Under\s*1\.5/);
    expect(text).toContain('Secondo tempo');
    expect(text).not.toContain('Utile netto');
    expect(text).not.toContain('Quota aggiornata');
    expect(text).not.toContain(single.rationale);
    expect([...article.querySelectorAll('dt')].map((n) => n.textContent!.trim())).toEqual([
      'Puntata virtuale',
      'Quota reale',
      'Probabilità stimata',
      'Ritorno lordo',
      'Delta (EV)',
    ]);
    const values = [...article.querySelectorAll('dd')].map((n) =>
      n.textContent!.replace(/\s+/g, '').trim(),
    );
    expect(values).toHaveLength(5);
    expect(values[0]).toContain('5.10');
    expect(values[1]).toBe('1.98');
    expect(values[2]).toBe('62.0%');
    expect(values[3]).toContain('10.10');
    expect(values[4]).toBe('+22.8%');
  });
  it.each([
    [-0.1234, '-12.3%'],
    [0, '0.0%'],
  ])('mostra il delta %s ricevuto dal backend anche nel modello campionato', (delta, formatted) => {
    const f = setup({
      ...view,
      current: { ...view.current, league: [{ ...single, expectedReturn: delta }] },
    });
    f.componentInstance.model.set('league');
    f.detectChanges();
    const article = (f.nativeElement as HTMLElement).querySelector('article')!;
    expect(article.querySelectorAll('dt')).toHaveLength(5);
    expect(article.querySelector('dd:last-child')!.textContent!.replace(/\s+/g, '')).toBe(
      formatted,
    );
  });
  it('distingue la quota totale simulata della doppia dalle quote reali delle due selezioni', () => {
    const double: BetSlip = {
      ...single,
      id: 'D1',
      kind: 'DOUBLE',
      stake: 1.71,
      price: 3.9204,
      probability: 0.3844,
      expectedReturn: 0.50700376,
      potentialPayout: 6.7,
      legs: [
        single.legs[0],
        {
          ...single.legs[0],
          home: 'C',
          away: 'D',
          quote: { ...single.legs[0].quote, fixtureId: 'f2' },
        },
      ],
    };
    const f = setup({ ...view, current: { ...view.current, cups: [double] } });
    const article = (f.nativeElement as HTMLElement).querySelector('article')!;
    const text = article.textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('A – B');
    expect(text).toContain('C – D');
    expect(text.match(/@\s*1\.98/g)).toHaveLength(2);
    expect(article.querySelectorAll('dt')).toHaveLength(5);
    expect(text).toContain('Quota totale simulata');
    const values = [...article.querySelectorAll('dd')].map((n) =>
      n.textContent!.replace(/\s+/g, ''),
    );
    expect(values[1]).toBe('3.9204');
    expect(values[2]).toBe('38.4%');
    expect(values[4]).toBe('+50.7%');
    expect(text).not.toContain('Utile netto');
    expect(text).not.toContain(double.rationale);
  });
  it('rifiuta importi con frazioni di centesimo e capitali fuori intervallo', () => {
    const f = setup();
    for (const [unit, capital] of [
      [5.001, 100],
      [5, 100001],
      [Number.NaN, 100],
    ]) {
      f.componentInstance.unitValue = unit;
      f.componentInstance.initialBankrollUnits = capital;
      f.componentInstance.advance();
      expect(f.componentInstance.busy()).toBe(false);
      expect(f.componentInstance.error()).toContain('Verifica utile obiettivo');
    }
    http.expectNone('/api/advance-job');
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

  it('mostra subito la barra centrale, segue le fasi e la chiude al completamento', () => {
    vi.useFakeTimers();
    const f = setup();
    const emit = vi.spyOn(f.componentInstance.advanced, 'emit');
    f.componentInstance.advance();
    f.detectChanges();
    const progress = f.nativeElement.querySelector('[role="progressbar"]') as HTMLElement;
    expect(progress.closest('.advance-progress')).not.toBeNull();
    expect(progress.hasAttribute('aria-valuenow')).toBe(false);
    expect(f.nativeElement.textContent).toContain('Avvio del job');
    http.expectOne('/api/advance-job').flush({ status: 'RUNNING', message: 'Download quote' });
    vi.advanceTimersByTime(0);
    http.expectOne('/api/advance-job').flush({ status: 'RUNNING', message: 'Addestramento' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('.advance-progress').textContent).toContain(
      'Addestramento',
    );
    expect(f.componentInstance.busy()).toBe(true);
    vi.advanceTimersByTime(1500);
    http.expectOne('/api/advance-job').flush({ status: 'SUCCEEDED', message: 'Step completato' });
    http.expectOne('/api/betting').flush(view);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    expect(f.componentInstance.busy()).toBe(false);
    expect(emit).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(3000);
    http.expectNone('/api/advance-job');
    f.destroy();
  });

  it('ripristina la barra aprendo la pagina durante un job già in corso e gestisce il fallimento', () => {
    vi.useFakeTimers();
    const f = TestBed.createComponent(BettingPanel);
    const emit = vi.spyOn(f.componentInstance.advanced, 'emit');
    f.detectChanges();
    http.expectOne('/api/betting').flush(view);
    http.expectOne('/api/advance-job').flush({ status: 'RUNNING', message: 'Controlli dati' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).not.toBeNull();
    expect(f.componentInstance.busy()).toBe(true);
    vi.advanceTimersByTime(0);
    http.expectOne('/api/advance-job').flush({ status: 'FAILED', message: 'Quota insufficiente' });
    http.expectOne('/api/betting').flush(view);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    expect(f.nativeElement.textContent).toContain('Quota insufficiente');
    expect(f.componentInstance.busy()).toBe(false);
    expect(emit).not.toHaveBeenCalled();
    f.destroy();
  });

  it('chiude la barra e segnala la perdita della connessione durante il polling', () => {
    vi.useFakeTimers();
    const f = setup();
    f.componentInstance.advance();
    http.expectOne('/api/advance-job').flush({ status: 'RUNNING', message: 'Download quote' });
    vi.advanceTimersByTime(0);
    http.expectOne('/api/advance-job').flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role="progressbar"]')).toBeNull();
    expect(f.componentInstance.error()).toContain('Connessione interrotta');
    expect(f.componentInstance.busy()).toBe(false);
    f.destroy();
  });
});
