import { TestBed } from '@angular/core/testing';
import { QuoteTable } from './quote-table';
import { Quote } from './scenario.models';
describe('Consultazione quote', () => {
  const quote: Quote = {
    bookmaker: 'unibet',
    marketId: 'totals',
    market: 'Total goals 2.5',
    type: 'totals',
    period: 'fulltime',
    line: 2.5,
    outcomeId: 'over',
    outcome: 'Over',
    price: 1.91,
    observedAt: '2026-04-28T08:25:40Z',
    probability: 0.6,
    baselineProbability: 0.5,
    fairPrice: 1 / 0.6,
    expectedReturn: 0.146,
    suggested: true,
    calculationStatus: 'MODELLED',
  };
  function setup(quotes: Quote[]) {
    const fixture = TestBed.createComponent(QuoteTable);
    fixture.componentRef.setInput('quotes', quotes);
    fixture.detectChanges();
    return fixture;
  }
  it('filtra bookmaker, periodo, mercato e segnali insieme', () => {
    const fixture = setup([
      quote,
      { ...quote, outcomeId: 'under', suggested: false },
      { ...quote, bookmaker: 'pinnacle' },
      { ...quote, period: 'p1', marketId: 'ht' },
    ]);
    const c = fixture.componentInstance;
    expect(c.filtered().length).toBe(3);
    c.period.set('fulltime');
    c.family.set('totals');
    c.suggestionsOnly.set(true);
    expect(c.filtered()).toEqual([quote]);
    c.bookmaker.set('pinnacle');
    expect(c.filtered().length).toBe(1);
  });
  it('mostra la quota reale ma non inventa una previsione per mercati non supportati', () => {
    const f = setup([
      {
        ...quote,
        probability: null,
        baselineProbability: null,
        expectedReturn: null,
        suggested: false,
      },
    ]);
    const text = (f.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('1.91');
    expect(text).toContain('Non modellato');
    expect(text).not.toContain('60.0%');
    expect(text).toContain('Over 2.5');
  });
  it('sincronizza il bookmaker mostrato con il filtro anche dopo arrivo asincrono delle opzioni', async () => {
    const f = setup([]);
    f.componentRef.setInput('quotes', [quote]);
    f.detectChanges();
    await f.whenStable();
    expect((f.nativeElement.querySelector('#bookmaker') as HTMLSelectElement).value).toBe('unibet');
  });
  it('gestisce filtri senza risultati e caricamento progressivo', () => {
    const f = setup(Array.from({ length: 65 }, (_, i) => ({ ...quote, outcomeId: `${i}` })));
    expect(f.componentInstance.visible().length).toBe(60);
    f.componentInstance.showMore();
    expect(f.componentInstance.visible().length).toBe(65);
    f.componentInstance.period.set('p2');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Nessuna quota');
  });
});
