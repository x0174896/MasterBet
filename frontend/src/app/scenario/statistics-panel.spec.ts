import { TestBed } from '@angular/core/testing';
import { StatisticsPanel } from './statistics-panel';
describe('Statistiche per tempo', () => {
  it('calcola il secondo tempo per differenza e tiene separato il finale', () => {
    const f = TestBed.createComponent(StatisticsPanel);
    f.componentRef.setInput('teams', []);
    f.componentRef.setInput('history', [
      {
        fixture: { id: 'one', home: 'Casa', away: 'Ospite', kickoff: '2026-04-20T15:00:00Z' },
        score: { homeHt: 1, awayHt: 0, homeFt: 3, awayFt: 1 },
      },
    ]);
    f.detectChanges();
    const cells = f.nativeElement.querySelectorAll('tbody tr td');
    expect(cells[2].textContent.trim()).toBe('1 : 0');
    expect(cells[3].textContent.trim()).toBe('2 : 1');
    expect(cells[4].textContent.trim()).toBe('3 : 1');
  });
});
