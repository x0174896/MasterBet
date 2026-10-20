import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LabState } from './lab-state';
import { Campaign } from './lab.models';

@Component({
  selector: 'app-lab-campaigns',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, PercentPipe, RouterLink],
  templateUrl: './lab-campaigns.html',
})
export class LabCampaigns {
  readonly state = inject(LabState);
  readonly campaigns = signal<Campaign[]>([]);
  readonly finalTotals = signal(true);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly rows = computed(() =>
    this.campaigns().map((c) => ({
      ...c,
      metric:
        c.run.status === 'COMPLETED'
          ? c.metrics.find((m) => m.includeCupScores === this.state.cups())
          : undefined,
    })),
  );
  constructor() {
    effect((onCleanup) => {
      this.state.runs();
      const cursor = this.finalTotals() ? null : this.state.cursor();
      const competition = this.state.competition();
      this.loading.set(true);
      this.error.set('');
      const request = this.state.api.campaigns(cursor, competition).subscribe({
        next: (values) => {
          this.campaigns.set(values);
          this.loading.set(false);
        },
        error: () => {
          this.campaigns.set([]);
          this.loading.set(false);
          this.error.set(
            'Confronto delle campagne non disponibile. Riprova aggiornando l’archivio.',
          );
        },
      });
      onCleanup(() => request.unsubscribe());
    });
  }
}
