import { Component, DestroyRef, inject, OnInit, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { exhaustMap, Subscription, timer } from 'rxjs';
import { ScenarioApi } from './scenario-api';
import { AdvanceJob, BettingView } from './betting.models';
import { SlipResult } from './slip-result';
import { BettingHistory } from './betting-history';
@Component({
  selector: 'app-betting-panel',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
    FormsModule,
    SlipResult,
    BettingHistory,
  ],
  templateUrl: './betting-panel.html',
  styleUrl: './betting-panel.scss',
})
export class BettingPanel implements OnInit {
  stepLabel(id: string) {
    const step = /round(\d+)$/.exec(id)?.[1];
    return step ? `Step ${step}` : id;
  }
  periodLabel(period: string) {
    return (
      (
        { p1: 'Primo tempo', p2: 'Secondo tempo', fulltime: 'Finale nei 90 minuti' } as Record<
          string,
          string
        >
      )[period] ?? period
    );
  }
  private readonly api = inject(ScenarioApi);
  private readonly destroy = inject(DestroyRef);
  private polling?: Subscription;
  readonly advanced = output<void>();
  readonly view = signal<BettingView | null>(null);
  readonly job = signal<AdvanceJob | null>(null);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly advancing = signal(false);
  readonly model = signal<'cups' | 'league'>('cups');
  unitValue = 5;
  initialBankrollUnits = 100;
  save() {
    const view = this.view();
    if (!view || this.busy()) return;
    this.busy.set(true);
    this.api
      .saveBets(view.current.scenarioId)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (value) => {
          this.view.set(value);
          this.busy.set(false);
        },
        error: () => {
          this.busy.set(false);
          this.error.set('Salvataggio non riuscito: ricarica lo stato prima di riprovare.');
        },
      });
  }
  ngOnInit() {
    this.load();
    this.api
      .job()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (job) => {
          this.job.set(job);
          if (job.status === 'RUNNING') {
            this.busy.set(true);
            this.advancing.set(true);
            this.poll();
          }
        },
        error: () => this.error.set('Stato del job non disponibile. Riprova.'),
      });
  }
  load() {
    this.api
      .betting()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (value) => {
          this.view.set(value);
          this.unitValue = value.current.config.unitValue;
          this.initialBankrollUnits = value.current.config.initialBankrollUnits;
        },
        error: () => this.error.set('Impossibile caricare schedine e bilancio. Riprova.'),
      });
  }
  advance() {
    const view = this.view();
    if (!view || this.busy()) return;
    if (
      !Number.isFinite(this.unitValue) ||
      this.unitValue < 0.04 ||
      this.unitValue > 10000 ||
      !Number.isFinite(this.initialBankrollUnits) ||
      this.initialBankrollUnits < 1 ||
      this.initialBankrollUnits > 100000 ||
      Math.abs(this.unitValue * 100 - Math.round(this.unitValue * 100)) > 1e-8
    ) {
      this.error.set(
        'Verifica utile obiettivo (0,04–10.000 €, al centesimo) e capitale iniziale (1–100.000 unità).',
      );
      return;
    }
    this.error.set('');
    this.busy.set(true);
    this.advancing.set(true);
    this.job.set(null);
    this.api
      .advance(view.current.scenarioId, {
        unitValue: this.unitValue,
        initialBankrollUnits: this.initialBankrollUnits,
      })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (job) => {
          this.job.set(job);
          this.poll();
        },
        error: () => {
          this.busy.set(false);
          this.advancing.set(false);
          this.error.set(
            'Avvio non confermato. Ricarica lo stato prima di riprovare: il job potrebbe essere già partito.',
          );
        },
      });
  }
  private poll() {
    this.polling?.unsubscribe();
    this.polling = timer(0, 1500)
      .pipe(
        exhaustMap(() => this.api.job()),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (job) => {
          this.job.set(job);
          if (job.status !== 'RUNNING') {
            this.polling?.unsubscribe();
            this.busy.set(false);
            this.advancing.set(false);
            this.load();
            if (job.status === 'SUCCEEDED') this.advanced.emit();
          }
        },
        error: () => {
          this.busy.set(false);
          this.advancing.set(false);
          this.error.set(
            'Connessione interrotta. Ricarica la pagina per recuperare lo stato del job.',
          );
        },
      });
  }
}
