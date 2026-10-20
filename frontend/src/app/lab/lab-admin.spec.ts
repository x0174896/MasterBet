import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LabAdmin } from './lab-admin';
import { LabState } from './lab-state';
import { Run } from './lab.models';

describe('Amministrazione riservata', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LabAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('richiede accesso e non mostra i comandi agli utenti non autenticati', async () => {
    const fixture = TestBed.createComponent(LabAdmin);
    http.expectOne('/api/lab/admin/session').flush({ configured: true, authenticated: false });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Accesso riservato');
    expect(fixture.nativeElement.textContent).not.toContain('Scarica risultati');
    fixture.destroy();
  });
  it('mantiene separati download risultati, quote e calcolo con intestazione CSRF', () => {
    const fixture = TestBed.createComponent(LabAdmin);
    const admin = fixture.componentInstance;
    http.expectOne('/api/lab/admin/session').flush({ configured: true, authenticated: true });
    http.expectOne('/api/lab/admin/jobs').flush([]);
    for (const kind of ['results', 'quotes', 'calculate'] as const) {
      admin.start(kind);
      const request = http.expectOne('/api/lab/admin/' + kind);
      expect(request.request.headers.get('X-MasterBet-Admin')).toBe('1');
      expect(request.request.method).toBe('POST');
      request.flush({});
      http.expectOne('/api/lab/admin/jobs').flush([]);
    }
    fixture.destroy();
  });
  it('non configura una password senza conferma e cancella il valore dopo login', () => {
    const fixture = TestBed.createComponent(LabAdmin);
    const admin = fixture.componentInstance;
    http.expectOne('/api/lab/admin/session').flush({ configured: false, authenticated: false });
    admin.password = 'test-private-password';
    admin.login();
    expect(admin.error()).toContain('coincidere');
    http.expectNone('/api/lab/admin/login');
    admin.confirmation = admin.password;
    admin.login();
    const request = http.expectOne('/api/lab/admin/login');
    expect(request.request.body.setup).toBe(true);
    request.flush({ authenticated: true });
    http.expectOne('/api/lab/admin/jobs').flush([]);
    expect(admin.password).toBe('');
    fixture.destroy();
  });
  it('avvia la campagna con periodo, bookmaker e utile scelti e blocca i comandi durante la campagna', async () => {
    const fixture = TestBed.createComponent(LabAdmin);
    const admin = fixture.componentInstance;
    http.expectOne('/api/lab/admin/session').flush({ configured: true, authenticated: true });
    http.expectOne('/api/lab/admin/jobs').flush([]);
    admin.unitProfit = 7;
    admin.start('campaign');
    const request = http.expectOne('/api/lab/admin/campaign');
    expect(request.request.headers.get('X-MasterBet-Admin')).toBe('1');
    expect(request.request.body.unitProfit).toBe(7);
    expect(request.request.body.download.bookmakers).toEqual(['unibet', 'pinnacle', 'bet365']);
    request.flush({});
    http.expectOne('/api/lab/admin/jobs').flush([
      {
        id: 'flow',
        kind: 'campaign',
        status: 'RUNNING',
        completed: 1,
        total: 6,
        message: 'Fase abbinamenti',
        updatedAt: '2026-10-19T12:00:00Z',
        errors: [],
      },
    ]);
    await fixture.whenStable();
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    expect(buttons.find((b) => b.textContent?.includes('Scarica risultati'))?.disabled).toBe(true);
    expect(
      buttons.find((b) => b.textContent?.includes('Avvia campagna automatica'))?.disabled,
    ).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Campagna automatica');
    expect(admin.jobLabel('reconcile')).toBe('Abbinamenti');
    fixture.destroy();
  });
  it('crea un esperimento distinto senza inviare richieste di download o cambiare il periodo originale', () => {
    const fixture = TestBed.createComponent(LabAdmin);
    const admin = fixture.componentInstance;
    http.expectOne('/api/lab/admin/session').flush({ configured: true, authenticated: true });
    http.expectOne('/api/lab/admin/jobs').flush([]);
    TestBed.inject(LabState).runs.set([
      {
        id: 'original',
        status: 'COMPLETED',
        config: { mode: 'HISTORICAL', strategy: 'BASELINE' },
      } as Run,
    ]);
    admin.experimentName = 'Nuova ricerca';
    admin.startExperiment();
    const request = http.expectOne('/api/lab/admin/experiments');
    expect(request.request.headers.get('X-MasterBet-Admin')).toBe('1');
    expect(request.request.body).toEqual({
      referenceRunId: 'original',
      name: 'Nuova ricerca',
      strategy: 'ADAPTIVE',
    });
    request.flush({});
    http.expectOne('/api/lab/admin/jobs').flush([]);
    http.expectOne('/api/lab/coverage').flush([]);
    http.expectOne('/api/lab/runs').flush([]);
    http.expectNone('/api/lab/admin/results');
    http.expectNone('/api/lab/admin/quotes');
    fixture.destroy();
  });
});
