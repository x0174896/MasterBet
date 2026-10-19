import { Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { LabState } from './lab-state';
import { Point } from './lab.models';

@Component({
  selector: 'app-lab-chart',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './lab-chart.html',
})
export class LabChart {
  readonly state = inject(LabState);
  readonly focused = signal<Point | null>(null);
  readonly points = computed(() => this.state.workspace()?.chart ?? []);
  readonly width = computed(() => Math.max(900, this.points().length * 8 + 80));
  readonly bounds = computed(() => {
    const values = this.points().map((p) => p.cumulative);
    return { min: Math.min(0, ...values) - 1, max: Math.max(0, ...values) + 1 };
  });
  x(index: number) {
    return 40 + (index * (this.width() - 80)) / Math.max(1, this.points().length - 1);
  }
  y(value: number) {
    const b = this.bounds();
    return 280 - ((value - b.min) / (b.max - b.min)) * 240;
  }
  readonly path = computed(() =>
    this.points()
      .map((p, i) => (i ? 'L' : 'M') + this.x(i) + ',' + this.y(p.cumulative))
      .join(' '),
  );
  readonly selected = computed(() => {
    const p = this.focused();
    return (
      this.points().find((v) => v.betId === p?.betId && v.index === p?.index) ??
      this.points().at(-1) ??
      null
    );
  });
  move(delta: number) {
    const index = this.selected()?.index ?? 0;
    this.focused.set(
      this.points()[Math.max(0, Math.min(this.points().length - 1, index + delta))] ?? null,
    );
  }
}
