import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ScenarioApi } from './scenario-api';
import { Quote, Scenario, TargetMatch } from './scenario.models';
@Injectable()
export class ScenarioState {
  private readonly api = inject(ScenarioApi);
  private readonly destroy = inject(DestroyRef);
  private quoteRequest?: Subscription;
  readonly data = signal<Scenario | null>(null);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly selected = signal<TargetMatch | null>(null);
  readonly quotes = signal<Quote[]>([]);
  readonly quotesError = signal('');
  readonly quotesLoading = signal(false);
  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.api
      .scenario()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.loading.set(false);
          if (data.matches.length) this.select(data.matches[0]);
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(
            error.status === 404
              ? 'Lo scenario non è ancora stato importato. Esegui l’importazione descritta nella guida del progetto.'
              : 'Il backend non è raggiungibile. Verifica che MasterBet sia avviato e riprova.',
          );
        },
      });
  }
  select(match: TargetMatch) {
    this.quoteRequest?.unsubscribe();
    this.selected.set(match);
    this.quotes.set([]);
    this.quotesError.set('');
    this.quotesLoading.set(true);
    this.quoteRequest = this.api
      .quotes(match.fixture.id)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (quotes) => {
          this.quotes.set(quotes);
          this.quotesLoading.set(false);
        },
        error: () => {
          this.quotesLoading.set(false);
          this.quotesError.set('Impossibile caricare le quote di questa partita. Riprova.');
        },
      });
  }
}
