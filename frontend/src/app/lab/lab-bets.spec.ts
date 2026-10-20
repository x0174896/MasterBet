import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LabBets } from './lab-bets';
import { LabState } from './lab-state';
import { Workspace } from './lab.models';

describe('Identificazione del mercato proposto', () => {
  it('mostra linea e periodo e mantiene leggibili le quote senza linea', async () => {
    TestBed.configureTestingModule({
      imports: [LabBets],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const state = TestBed.inject(LabState);
    const workspace = {
      totalBets: 1,
      settled: 1,
      won: 0,
      lost: 1,
      net: -5,
      page: 0,
      pages: 1,
      bets: [
        {
          status: 'LOST',
          net: -5,
          bet: {
            id: 'b',
            competition: 'Test',
            season: '2026',
            step: 1,
            home: 'Casa',
            away: 'Ospite',
            kickoff: '2026-06-01T12:00:00Z',
            availableAt: '2026-05-28T00:00:00Z',
            quote: {
              market: { name: 'Gol casa', period: 'p1', line: 1.5 },
              outcome: 'Under',
              bookmaker: 'test',
              price: 2,
              observedAt: '2026-06-01T11:59:00Z',
            },
            probability: 0.6,
            expectedValue: 0.2,
            stake: 5,
            explanation: [],
          },
        },
      ],
    } as unknown as Workspace;
    state.workspace.set(workspace);
    const fixture = TestBed.createComponent(LabBets);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toMatch(/Gol casa\s+1[.,]5/);
    expect(fixture.nativeElement.textContent).toContain('Primo tempo');
    const bet = workspace.bets[0].bet;
    state.workspace.set({
      ...workspace,
      bets: [
        {
          ...workspace.bets[0],
          bet: {
            ...bet,
            quote: {
              ...bet.quote,
              market: { name: 'Risultato 1X2', period: 'fulltime', line: null },
            },
          },
        },
      ],
    });
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Partita intera');
    expect(text).not.toContain('null');
    expect(fixture.componentInstance.periodLabel('p2')).toBe('Secondo tempo');
    fixture.destroy();
  });
});
