import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LabShell } from './lab-shell';

describe('Selettore delle campagne', () => {
  it('seleziona la campagna caricata dopo il rendering e mostra anche quelle incomplete', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [LabShell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LabShell);
    fixture.detectChanges();
    http.expectOne('/api/lab/coverage').flush([]);
    http.expectOne('/api/lab/runs').flush([
      { id: 'pending', status: 'RUNNING', config: { name: 'In preparazione' } },
      { id: 'original', status: 'COMPLETED', config: { mode: 'HISTORICAL' } },
    ]);
    http
      .expectOne((r) => r.url === '/api/lab/workspace')
      .flush({ run: { id: 'original' }, cursor: null });
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#lab-run');
    expect(select.value).toBe('original');
    expect(select.options[1].disabled).toBe(true);
    fixture.destroy();
    http.verify();
  });
});
