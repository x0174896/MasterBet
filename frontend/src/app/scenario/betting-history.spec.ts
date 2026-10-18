import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BettingHistory } from './betting-history';
import { SettledSlip } from './betting.models';

describe('Filtri e totali del bilancio virtuale', () => {
  const slips: SettledSlip[] = [
    {
      scenarioId: 'round7',
      slipId: 'S1',
      kind: 'SINGLE',
      status: 'WON',
      stake: 5.1,
      payout: 10.1,
      profit: 5,
    },
    {
      scenarioId: 'round7',
      slipId: 'S2',
      kind: 'DOUBLE',
      status: 'LOST',
      stake: 6.25,
      payout: 0,
      profit: -6.25,
    },
    {
      scenarioId: 'round10',
      slipId: 'S1',
      kind: 'SINGLE',
      status: 'LOST',
      stake: 1.65,
      payout: 0,
      profit: -1.65,
    },
    {
      scenarioId: 'round6',
      slipId: 'S1',
      kind: 'SINGLE',
      status: 'WON',
      stake: 1.33,
      payout: 6.33,
      profit: 5,
    },
    {
      scenarioId: 'round8',
      slipId: 'S1',
      kind: 'SINGLE',
      status: 'PENDING',
      stake: 99,
      payout: null,
      profit: null,
    },
    {
      scenarioId: 'round7',
      slipId: 'S3',
      kind: 'SINGLE',
      status: 'PENDING',
      stake: 3,
      payout: null,
      profit: null,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function setup(rows: SettledSlip[] = slips) {
    const f = TestBed.createComponent(BettingHistory);
    f.componentRef.setInput('slips', rows);
    f.componentRef.setInput('variant', 'cups');
    f.detectChanges();
    return f;
  }

  it('senza filtro somma solo i record visibili ed esclude le puntate in attesa', () => {
    const f = setup();
    expect(f.componentInstance.visible().length).toBe(4);
    expect(f.componentInstance.totals()).toEqual({ stake: 14.33, payout: 16.43, profit: 2.1 });
    const body = f.nativeElement.querySelector('tbody') as HTMLElement;
    expect(body.querySelectorAll('tr').length).toBe(4);
    expect(body.textContent).not.toContain('Step 8');
    expect(body.textContent).not.toContain('In attesa');
    const footer = f.nativeElement.querySelector('tfoot') as HTMLElement;
    expect(footer.textContent).toContain('Totale record visualizzati (4)');
    expect(footer.textContent).toContain('14.33');
    expect(footer.textContent).toContain('16.43');
    expect(footer.textContent).toContain('2.10');
    expect(footer.querySelector('.text-success')).not.toBeNull();
  });

  it('filtra dalla select, ricalcola i tre totali e mostra il netto negativo', async () => {
    const f = setup();
    await f.whenStable();
    const select = f.nativeElement.querySelector('select') as HTMLSelectElement;
    expect([...select.options].map((o) => o.textContent?.trim())).toEqual([
      'Tutti gli step',
      'Step 6',
      'Step 7',
      'Step 10',
    ]);
    select.value = 'round7';
    select.dispatchEvent(new Event('change'));
    f.detectChanges();
    expect(f.componentInstance.visible().length).toBe(2);
    expect(f.componentInstance.totals()).toEqual({ stake: 11.35, payout: 10.1, profit: -1.25 });
    const body = f.nativeElement.querySelector('tbody') as HTMLElement;
    expect(body.textContent).not.toContain('Step 10');
    expect(body.textContent).not.toContain('S3');
    const footer = f.nativeElement.querySelector('tfoot') as HTMLElement;
    expect(footer.textContent).toContain('11.35');
    expect(footer.textContent).toContain('10.10');
    expect(footer.querySelector('.text-danger')?.textContent).toMatch(/-.*1\.25/);
    (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    await f.whenStable();
    expect(select.value).toBe('');
    expect(f.componentInstance.visible().length).toBe(4);
    expect(f.componentInstance.totals().profit).toBe(2.1);
  });

  it('una perdita senza vincite conserva ritorno zero e netto negativo', () => {
    const f = setup();
    f.componentInstance.step.set('round10');
    f.detectChanges();
    expect(f.componentInstance.totals()).toEqual({ stake: 1.65, payout: 0, profit: -1.65 });
    expect(f.nativeElement.querySelector('tfoot .text-danger').textContent).toMatch(/-.*1\.65/);
  });

  it('gestisce storico vuoto, sole puntate future e filtri senza risultati con totali zero', () => {
    for (const rows of [[], slips.filter((r) => r.status === 'PENDING'), slips]) {
      const f = setup(rows);
      f.componentInstance.step.set('round99');
      f.detectChanges();
      expect(f.componentInstance.visible()).toEqual([]);
      expect(f.componentInstance.totals()).toEqual({ stake: 0, payout: 0, profit: 0 });
      expect(f.nativeElement.querySelector('tbody').textContent).toContain('Nessuna schedina');
      expect(f.nativeElement.querySelector('tfoot').textContent).toContain('visualizzati (0)');
      f.destroy();
    }
  });

  it('aggiorna righe e totali quando arrivano i dati di un altro modello o step', () => {
    const f = setup();
    f.componentInstance.step.set('round7');
    f.componentRef.setInput('variant', 'league');
    f.componentRef.setInput('slips', [
      { ...slips[0], status: 'LOST', stake: 2.33, payout: 0, profit: -2.33 },
      slips[4],
    ]);
    f.detectChanges();
    expect(f.componentInstance.totals()).toEqual({ stake: 2.33, payout: 0, profit: -2.33 });
    f.componentRef.setInput('slips', [
      { ...slips[0], status: 'WON', stake: 2.33, payout: 7.34, profit: 5.01 },
      slips[4],
    ]);
    f.detectChanges();
    expect(f.componentInstance.totals()).toEqual({ stake: 2.33, payout: 7.34, profit: 5.01 });
  });

  it('mantiene gli esiti consultabili senza richieste automatiche né effetti sui totali', () => {
    const f = setup([slips[0]]);
    f.componentRef.setInput('showResults', true);
    f.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectNone('/api/betting/round7/cups/S1/reveal');
    (f.nativeElement.querySelector('app-slip-result button') as HTMLButtonElement).click();
    http.expectOne('/api/betting/round7/cups/S1/reveal').flush({
      scenarioId: 'round7',
      variant: 'cups',
      slipId: 'S1',
      checkedAt: '2026-10-18T12:00:00Z',
      status: 'WON',
      stake: 5.1,
      payout: 10.1,
      profit: 5,
      results: [],
    });
    f.detectChanges();
    expect(f.componentInstance.totals()).toEqual({ stake: 5.1, payout: 10.1, profit: 5 });
  });

  it('torna a tutti gli step se il modello aggiornato non contiene più lo step selezionato', async () => {
    const f = setup();
    f.componentInstance.step.set('round7');
    f.componentRef.setInput('variant', 'league');
    f.componentRef.setInput('slips', [slips[2], slips[4]]);
    f.detectChanges();
    await f.whenStable();
    expect(f.componentInstance.step()).toBe('');
    expect((f.nativeElement.querySelector('select') as HTMLSelectElement).value).toBe('');
    expect(f.componentInstance.visible()).toEqual([slips[2]]);
    expect(f.componentInstance.totals()).toEqual({ stake: 1.65, payout: 0, profit: -1.65 });
  });
});
