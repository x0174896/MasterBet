import { Injectable, inject, signal } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { LabApi } from './lab-api';
import { Coverage, Run, Workspace, TeamStats } from './lab.models';

@Injectable({ providedIn: 'root' })
export class LabState {
  readonly api = inject(LabApi);
  readonly coverage = signal<Coverage[]>([]);
  readonly runs = signal<Run[]>([]);
  readonly workspace = signal<Workspace | null>(null);
  readonly statistics = signal<TeamStats[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly cups = signal(false);
  readonly competition = signal(0);
  readonly runId = signal('');
  readonly cursor = signal<string | null>(null);
  private pending?: Subscription;
  private statisticsRequest?: Subscription;
  private initialized = false;
  init() {
    if (!this.initialized) {
      this.initialized = true;
      this.refresh();
    }
  }
  refresh() {
    this.loading.set(true);
    this.error.set('');
    forkJoin({ coverage: this.api.coverage(), runs: this.api.runs() }).subscribe({
      next: ({ coverage, runs }) => {
        this.coverage.set(coverage);
        this.runs.set(runs);
        if (!runs.some((r) => r.id === this.runId() && r.status === 'COMPLETED'))
          this.runId.set(runs.find((r) => r.status === 'COMPLETED')?.id ?? '');
        this.load();
      },
      error: () => {
        this.loading.set(false);
        this.error.set(
          'Impossibile caricare il laboratorio. Verificare che il backend sia avviato.',
        );
      },
    });
  }
  setRun(id: string) {
    this.runId.set(id);
    this.cursor.set(null);
    this.load();
  }
  setCups(value: boolean) {
    this.cups.set(value);
    this.load();
  }
  setCompetition(value: number) {
    this.competition.set(value);
    this.cursor.set(null);
    this.load();
  }
  setCursor(value: string) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) {
      this.cursor.set(date.toISOString());
      this.load();
    }
  }
  load(page = 0) {
    this.pending?.unsubscribe();
    this.statisticsRequest?.unsubscribe();
    this.workspace.set(null);
    this.statistics.set([]);
    this.error.set('');
    if (!this.runId()) {
      this.workspace.set(null);
      this.statistics.set([]);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.pending = this.api
      .workspace(this.runId(), this.cursor(), this.cups(), this.competition(), page)
      .subscribe({
        next: (w) => {
          this.workspace.set(w);
          this.cursor.set(w.cursor);
          this.loading.set(false);
          if (w.cursor)
            this.statisticsRequest = this.api
              .statistics(w.run.id, w.cursor, this.cups(), this.competition())
              .subscribe({
                next: (s) => this.statistics.set(s),
                error: () => this.error.set('Statistiche non disponibili per questo istante.'),
              });
          else this.statistics.set([]);
        },
        error: () => {
          this.loading.set(false);
          this.workspace.set(null);
          this.error.set(
            'Periodo non disponibile. Scegli un’altra elaborazione o un campionato con proposte.',
          );
        },
      });
  }
  reveal(id: string) {
    this.api.reveal(this.runId(), id).subscribe({
      next: () => this.load(this.workspace()?.page ?? 0),
      error: () => this.error.set('Risultato finale non ancora disponibile per lo spoiler.'),
    });
  }
  localDate(instant: string | null | undefined) {
    if (!instant) return '';
    const date = new Date(instant);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }
}
