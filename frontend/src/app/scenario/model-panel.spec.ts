import { TestBed } from '@angular/core/testing';
import { ModelPanel } from './model-panel';
describe('Confronto modello', () => {
  it('mostra anche prestazioni peggiori della base senza nasconderle', () => {
    const f = TestBed.createComponent(ModelPanel);
    f.componentRef.setInput('model', {
      trainingMatches: 64,
      validationMatches: 16,
      trainingFrom: '2025-09-01T00:00:00Z',
      trainingThrough: '2025-10-01T00:00:00Z',
      validationFrom: '2025-10-02T00:00:00Z',
      validationThrough: '2025-11-01T00:00:00Z',
      features: ['rest'],
      baselineBrier: 0.5,
      leagueModelBrier: 0.7,
      cupsModelBrier: 0.8,
      limitation: 'Campione ridotto',
    });
    f.componentRef.setInput('cups', []);
    f.componentRef.setInput('cupResults', []);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('0.5000');
    expect(f.nativeElement.textContent).toContain('0.8000');
    expect(f.nativeElement.textContent).toContain('Campione ridotto');
    expect(f.nativeElement.textContent).toContain('64 partite');
  });
});
