import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LabAdmin } from './lab-admin';

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
});
