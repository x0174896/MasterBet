import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LabCampaigns } from './lab-campaigns';
import { LabState } from './lab-state';
import { Campaign, Run } from './lab.models';

describe('Archivio delle campagne', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LabCampaigns],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const original = {
    id: 'original',
    status: 'COMPLETED',
    config: {
      from: '2026-01-01',
      to: '2026-10-18',
      mode: 'HISTORICAL',
      unitProfit: 5,
    },
  } as Run;
  const campaign = {
    run: original,
    metrics: [
      {
        includeCupScores: false,
        proposed: 100,
        settled: 100,
        net: -50,
        turnover: 500,
        roi: -0.1,
        maxDrawdown: 75,
        brier: 0.26,
        meanProbability: 0.61,
        winRate: 0.49,
        quotesOlder24h: 2,
      },
      {
        includeCupScores: true,
        proposed: 101,
        settled: 101,
        net: -60,
        turnover: 500,
        roi: -0.12,
        maxDrawdown: 80,
        brier: 0.27,
        meanProbability: 0.62,
        winRate: 0.48,
        quotesOlder24h: 3,
      },
    ],
  } as Campaign;

  it('mostra saldo originale, modalità separate e tutti gli stati senza avviare calcoli', async () => {
    const state = TestBed.inject(LabState);
    state.runs.set([original]);
    const fixture = TestBed.createComponent(LabCampaigns);
    fixture.detectChanges();
    const request = http.expectOne((r) => r.url === '/api/lab/campaigns');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.has('cursor')).toBe(false);
    request.flush([
      campaign,
      { run: { ...original, id: 'pending', status: 'RUNNING' }, metrics: [] },
    ]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('strong.text-danger').textContent).toContain('50');
    expect(fixture.nativeElement.textContent).toContain('RUNNING');
    const buttons = fixture.nativeElement.querySelectorAll('button');
    expect(buttons[1].disabled).toBe(true);
    state.setCups(true);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('strong.text-danger').textContent).toContain('60');
    fixture.destroy();
  });
  it('permette il confronto al tempo simulato e gestisce errori e archivio vuoto', async () => {
    const state = TestBed.inject(LabState);
    state.cursor.set('2026-05-20T12:00:00Z');
    const fixture = TestBed.createComponent(LabCampaigns);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/lab/campaigns').flush([]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Nessuna campagna disponibile');
    fixture.componentInstance.finalTotals.set(false);
    fixture.detectChanges();
    const request = http.expectOne((r) => r.url === '/api/lab/campaigns');
    expect(request.request.params.get('cursor')).toBe('2026-05-20T12:00:00Z');
    request.flush({}, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Confronto delle campagne non disponibile');
    fixture.destroy();
  });
});
