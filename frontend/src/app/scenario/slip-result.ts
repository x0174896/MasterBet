import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ScenarioApi } from './scenario-api';
import { SlipReveal } from './betting.models';

@Component({
  selector: 'app-slip-result',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './slip-result.html',
})
export class SlipResult {
  readonly scenario = input.required<string>();
  readonly variant = input.required<string>();
  readonly slip = input.required<string>();
  readonly result = signal<SlipReveal | null>(null);
  readonly expanded = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  private readonly api = inject(ScenarioApi);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  constructor() {
    effect(() => {
      this.scenario();
      this.variant();
      this.slip();
      this.request?.unsubscribe();
      this.result.set(null);
      this.expanded.set(false);
      this.loading.set(false);
      this.error.set('');
    });
  }

  toggle() {
    if (this.loading()) return;
    if (this.expanded()) {
      this.expanded.set(false);
      return;
    }
    this.expanded.set(true);
    if (this.result()?.status !== 'PENDING' && this.result() !== null) return;
    this.loading.set(true);
    this.error.set('');
    this.request = this.api
      .reveal(this.scenario(), this.variant(), this.slip())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (value) => {
          this.result.set(value);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(
            'Esito non verificabile al momento. Chiudi e riapri per riprovare. Nessun risultato inventato.',
          );
        },
      });
  }
}
