import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { interval } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LabState } from './lab-state';
import { Job, Session } from './lab.models';

@Component({
  selector: 'app-lab-admin',
  imports: [FormsModule, DatePipe],
  templateUrl: './lab-admin.html',
})
export class LabAdmin {
  readonly state = inject(LabState);
  private readonly api = this.state.api;
  private readonly destroy = inject(DestroyRef);
  readonly session = signal<Session | null>(null);
  readonly jobs = signal<Job[]>([]);
  readonly error = signal('');
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly jobRunning = (job: Job) => job.status === 'RUNNING';
  password = '';
  confirmation = '';
  from = '2026-01-01';
  to = new Date().toISOString().slice(0, 10);
  competitionIds: number[] = [];
  bookmakers = 'unibet,pinnacle,bet365';
  refresh = false;
  unitProfit = 5;
  mode: 'HISTORICAL' | 'OPERATIONAL' = 'HISTORICAL';
  constructor() {
    this.api
      .session()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (s) => {
          this.session.set(s);
          if (s.authenticated) this.refreshJobs();
        },
        error: () => this.error.set('Stato amministratore non disponibile.'),
      });
    interval(5000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        if (this.session()?.authenticated) this.refreshJobs();
      });
  }
  login() {
    const setup = !this.session()?.configured;
    this.error.set('');
    if (setup && this.password !== this.confirmation) {
      this.error.set('Le due password devono coincidere.');
      return;
    }
    this.busy.set(true);
    this.api.login(this.password, setup).subscribe({
      next: () => {
        this.password = '';
        this.confirmation = '';
        this.session.set({ configured: true, authenticated: true });
        this.busy.set(false);
        this.refreshJobs();
      },
      error: (e) => {
        this.busy.set(false);
        this.password = '';
        this.error.set(
          e.status === 429
            ? 'Troppi tentativi. Attendi 15 minuti.'
            : 'Accesso non riuscito. Verifica la password (almeno 12 caratteri).',
        );
      },
    });
  }
  logout() {
    this.api.logout().subscribe({
      next: () => {
        this.session.set({ configured: true, authenticated: false });
        this.jobs.set([]);
      },
      error: () => this.error.set('Uscita non riuscita.'),
    });
  }
  refreshJobs() {
    this.api.jobs().subscribe({
      next: (j) => this.jobs.set(j),
      error: (e) => {
        if (e.status === 401) this.session.set({ configured: true, authenticated: false });
        else this.error.set('Avanzamento dei processi non disponibile.');
      },
    });
  }
  start(kind: 'catalog' | 'results' | 'quotes' | 'calculate' | 'campaign') {
    this.error.set('');
    this.notice.set('');
    this.busy.set(true);
    const body = {
      from: this.from,
      to: this.to,
      competitionIds: this.competitionIds.map(Number),
      bookmakers: this.bookmakers
        .split(',')
        .map((b) => b.trim())
        .filter(Boolean),
      refresh: this.refresh,
    };
    const request =
      kind === 'campaign'
        ? this.api.campaign(body, this.unitProfit)
        : kind === 'catalog'
          ? this.api.catalog(this.refresh)
          : kind === 'calculate'
            ? this.api.calculate({
                from: this.from,
                to: this.to,
                competitionIds: body.competitionIds,
                unitProfit: this.unitProfit,
                mode: this.mode,
              })
            : this.api.download(kind, body);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set(
          'Processo avviato. Avanzamento e segnalazioni sono conservati nel database.',
        );
        this.refreshJobs();
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Avvio non riuscito. Verifica configurazione e processi già in corso.');
      },
    });
  }
  running(kind: string) {
    return this.jobs().some((j) => j.kind === kind && j.status === 'RUNNING');
  }
  jobLabel(kind: string) {
    const labels: Record<string, string> = {
      campaign: 'Campagna automatica',
      results: 'Risultati',
      quotes: 'Quote',
      reconcile: 'Abbinamenti',
      validate: 'Controlli finali',
      calculate: 'Elaborazione',
      catalog: 'Catalogo',
    };
    return labels[kind] ?? kind;
  }
}
