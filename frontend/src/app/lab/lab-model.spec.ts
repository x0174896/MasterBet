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
    state.workspace.set({ model: [{ ...step, learning: null }] } as Workspace);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).not.toContain('Calibrazione applicata');
    fixture.destroy();
  });
});
