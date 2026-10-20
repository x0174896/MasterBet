import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from '../app.routes';
import { LabApi } from './lab-api';

describe('Navigazione del laboratorio', () => {
  it('divide andamento, statistiche e modello in pagine indipendenti', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: LabApi,
          useValue: { coverage: () => of([]), runs: () => of([]), campaigns: () => of([]) },
        },
      ],
    });
    const harness = await RouterTestingHarness.create('/andamento');
    expect(harness.routeNativeElement?.textContent).toContain('Andamento del guadagno netto');
    await harness.navigateByUrl('/campionati');
    expect(harness.routeNativeElement?.textContent).toContain('Copertura dei campionati');
    expect(harness.routeNativeElement?.textContent).not.toContain('Andamento del guadagno netto');
    await harness.navigateByUrl('/modello');
    expect(harness.routeNativeElement?.textContent).toContain('Modello e ricerca dei pattern');
    expect(harness.routeNativeElement?.querySelectorAll('nav a').length).toBe(6);
    await harness.navigateByUrl('/campagne');
    expect(harness.routeNativeElement?.textContent).toContain('Campagne e confronto');
  });
});
