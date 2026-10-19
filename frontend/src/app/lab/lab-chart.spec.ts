import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { LabState } from './lab-state';
import { LabChart } from './lab-chart';
import { Workspace } from './lab.models';

describe('Grafico del netto', () => {
  it('mantiene un punto distinto per singola simultanea e valori sotto zero', async () => {
    const workspace = signal({
      net: 0,
      settled: 2,
      chart: [
        { index: 0, betId: null, settledAt: '2026-05-20T12:00:00Z', profit: 0, cumulative: 0 },
        { index: 1, betId: 'a', settledAt: '2026-05-21T12:00:00Z', profit: -5, cumulative: -5 },
        { index: 2, betId: 'b', settledAt: '2026-05-21T12:00:00Z', profit: 5, cumulative: 0 },
      ],
    } as Workspace);
    TestBed.configureTestingModule({
      imports: [LabChart],
      providers: [{ provide: LabState, useValue: { workspace, cups: signal(false) } }],
    });
    const fixture = TestBed.createComponent(LabChart);
    await fixture.whenStable();
    const chart = fixture.componentInstance;
    expect(chart.points().length).toBe(3);
    expect(chart.x(1)).not.toBe(chart.x(2));
    expect(chart.y(-5)).toBeGreaterThan(chart.y(0));
    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(3);
    chart.move(-1);
    expect(chart.selected()?.betId).toBe('a');
    workspace.set({ ...workspace(), chart: [workspace().chart[0]] });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(1);
  });
});
