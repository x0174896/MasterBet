import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SlipResult } from './slip-result';
describe('Occhio sugli esiti salvati', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function setup() {
    const f = TestBed.createComponent(SlipResult);
    f.componentRef.setInput('scenario', 'round8');
    f.componentRef.setInput('variant', 'cups');
    f.componentRef.setInput('slip', 'S1');
    f.detectChanges();
    return f;
  }
  const result = {
    scenarioId: 'round8',
    variant: 'cups',
    slipId: 'S1',
    checkedAt: '2026-10-18T12:00:00Z',
    status: 'LOST',
    stake: 2,
    payout: 0,
    profit: -2,
    results: [
      {
        leg: {
          home: 'A',
          away: 'B',
          quote: {
            fixtureId: 'f',
            outcome: 'Under',
            market: { name: 'Totali secondo tempo', type: 'totals', line: 1.5 },
          },
        },
        score: { homeHt: 1, awayHt: 0, homeFt: 1, awayFt: 2 },
        won: false,
        source: 'https://www.fotball.no/fotballdata/kamp/?fiksId=123',
      },
    ],
  };
  it('scarica solo al clic, mostra HT/SH/FT e perdita, riapre senza riscaricare', () => {
    const f = setup();
    http.expectNone('/api/betting/round8/cups/S1/reveal');
    (f.nativeElement.querySelector('button') as HTMLButtonElement).click();
    const req = http.expectOne('/api/betting/round8/cups/S1/reveal');
    expect(req.request.method).toBe('POST');
    req.flush(result);
    f.detectChanges();
    const text = f.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('Schedina persa');
    expect(text).toContain('Primo tempo: 1–0');
    expect(text).toContain('Secondo tempo: 0–2');
    expect(text).toContain('Finale (90′): 1–2');
    f.componentInstance.toggle();
    f.componentInstance.toggle();
    http.expectNone('/api/betting/round8/cups/S1/reveal');
    f.componentRef.setInput('variant', 'league');
    f.detectChanges();
    expect(f.componentInstance.result()).toBeNull();
  });
  it('un errore non viene mostrato come perdita o pareggio', () => {
    const f = setup();
    f.componentInstance.toggle();
    http
      .expectOne('/api/betting/round8/cups/S1/reveal')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Esito non verificabile');
    expect(f.componentInstance.result()).toBeNull();
  });
});
