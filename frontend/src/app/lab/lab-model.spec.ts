import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LabModel } from './lab-model';
import { LabState } from './lab-state';
import { ModelStep, Workspace } from './lab.models';

describe('Verifiche dei segnali nel modello', () => {
  it('mostra decisioni salvate e calibrazione senza attribuire causalità ai pattern', async () => {
    TestBed.configureTestingModule({
      imports: [LabModel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const state = TestBed.inject(LabState);
    const step = {
      number: 1,
      cutoff: '2026-05-01T00:00:00Z',
      includeCupScores: false,
      rowsPerCompetition: { '1': 200 },
      trainingHash: 'prefix',
      trainingRows: 200,
      validationRows: 40,
      validationMae: 1,
      policy: 'Test',
      proposed: 4,
      excluded: 2,
      exclusions: {},
      patterns: [],
      learning: {
        version: 'learning-v1',
        observations: 200,
        latestResult: '2026-04-30T18:00:00Z',
        calibration: {
          support: 200,
          fitted: 140,
          validation: 60,
          offset: -0.5,
          rawBrier: 0.26,
          calibratedBrier: 0.24,
          accepted: true,
        },
        signals: [
          {
            group: 'Ultima partita',
            observations: 40,
            firstGain: 0.02,
            secondGain: 0.03,
            disabled: true,
          },
        ],
        rules: [
          {
            key: 'calendar:busy',
            description: 'Calendario fitto',
            support: 100,
            firstSupport: 50,
            secondSupport: 50,
            firstRoi: -0.1,
            secondRoi: -0.12,
            comparisonFirstRoi: 0.01,
            comparisonSecondRoi: 0.02,
            upperMeanReturn: -0.03,
            excluded: true,
          },
        ],
        rejectedCandidates: { 'calendar:busy': 3 },
      },
    } as ModelStep;
    state.workspace.set({ model: [step] } as Workspace);
    const fixture = TestBed.createComponent(LabModel);
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Calibrazione applicata');
    expect(text).toContain('escluso dal nuovo addestramento');
    expect(text).toContain('Calendario fitto');
    expect(text).toContain('Esclusione attiva');
    expect(text).toContain('Le verifiche sono esplorative');
    expect(
      fixture.componentInstance.ruleLabel('market:teamtotals-team1:fulltime:Under', 'raw'),
    ).toBe('Gol della squadra di casa · partita · Under');
    expect(fixture.componentInstance.ruleLabel('ev:15-30', 'raw')).toContain('meno del 30%');
    expect(
      fixture.componentInstance.ruleLabel(
        'market:teamtotals-team1:fulltime:Under | calendar:busy',
        'raw',
      ),
    ).toContain('Under · Almeno quattro incontri');
    state.workspace.set({ model: [{ ...step, learning: null }] } as Workspace);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Calibrazione applicata');
    fixture.destroy();
  });

  it('distingue mercati e fasce verificate e confronti diagnostici senza una campagna aggiuntiva', async () => {
    TestBed.configureTestingModule({
      imports: [LabModel],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const state = TestBed.inject(LabState);
    const step = {
      number: 1,
      cutoff: '2026-06-01T00:00:00Z',
      patterns: [],
      exclusions: {},
      learning: {
        observations: 300,
        latestResult: '2026-05-31T18:00:00Z',
        signals: [],
        rules: [],
        rejectedCandidates: {},
        markets: {
          version: 'conditional-markets-v2',
          fixtures: 300,
          candidates: 900,
          alternatives: 600,
          families: [
            {
              family: 'teamtotals-team2:fulltime:Under',
              support: 200,
              fitted: 140,
              validation: 60,
              rawBrier: 0.26,
              calibratedBrier: 0.24,
              accepted: true,
              reason: 'Verifica specifica delle alternative',
              validationFrom: '2026-05-01T00:00:00Z',
              latestFitResult: '2026-04-30T18:00:00Z',
              bands: [
                {
                  band: '70-80',
                  support: 50,
                  selected: 30,
                  predicted: 0.6,
                  observed: 0.6,
                  ready: true,
                },
              ],
            },
          ],
          conditions: [],
          policies: [{ policy: 'primary-only', settled: 20, turnover: 100, net: -5 }],
          replacements: [
            {
              family: 'teamtotals-team2:fulltime:Under',
              support: 50,
              weeks: 6,
              firstSupport: 25,
              secondSupport: 25,
              firstRoi: 0.1,
              secondRoi: -0.1,
              lowerRoi: -0.2,
              lowerDifference: -0.3,
              allowed: false,
              reason: 'Vantaggio non confermato',
            },
          ],
        },
      },
    } as unknown as ModelStep;
    state.workspace.set({ model: [step] } as Workspace);
    const fixture = TestBed.createComponent(LabModel);
    await fixture.whenStable();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('300 incontri');
    expect(text).toContain('600 alternative');
    expect(text).toContain('Gol della squadra ospite');
    expect(text).toContain('fascia ammessa');
    expect(text).toContain('senza sostituzioni');
    expect(text).toContain('nessuna campagna aggiuntiva');
    expect(text).toContain('Sostituzione non confermata');
    expect(text).toContain('prima di conoscerne l’esito');
    expect(text).not.toContain('Calibrazione non applicata');
    fixture.destroy();
  });
});
