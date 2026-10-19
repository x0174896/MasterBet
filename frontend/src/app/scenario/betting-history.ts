import { Component, computed, input, linkedSignal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SettledSlip } from './betting.models';
import { SlipResult } from './slip-result';

type CompletedSlip = SettledSlip & { payout: number; profit: number };

@Component({
  selector: 'app-betting-history',
  imports: [CurrencyPipe, FormsModule, SlipResult],
  templateUrl: './betting-history.html',
})
export class BettingHistory {
  readonly slips = input.required<SettledSlip[]>();
  readonly variant = input.required<'cups' | 'league'>();
  readonly showResults = input(false);
  private readonly settled = computed(() =>
    this.slips().filter(
      (row): row is CompletedSlip =>
        (row.status === 'WON' || row.status === 'LOST') &&
        row.payout !== null &&
        row.profit !== null,
    ),
  );
  readonly steps = computed(() =>
    [...new Set(this.settled().map((row) => row.scenarioId))].sort((a, b) =>
      a.localeCompare(b, undefined, { numeric: true }),
    ),
  );
  readonly step = linkedSignal<string[], string>({
    source: this.steps,
    computation: (steps, previous) =>
      previous && steps.includes(previous.value) ? previous.value : '',
  });
  readonly visible = computed(() =>
    this.settled().filter((row) => !this.step() || row.scenarioId === this.step()),
  );
  readonly totals = computed(() => {
    let stake = 0;
    let payout = 0;
    // Sommare centesimi interi evita residui binari negli importi mostrati.
    for (const row of this.visible()) {
      stake += Math.round(row.stake * 100);
      payout += Math.round(row.payout * 100);
    }
    return { stake: stake / 100, payout: payout / 100, profit: (payout - stake) / 100 };
  });

  stepLabel(id: string) {
    const step = /round(\d+)$/.exec(id)?.[1];
    return step ? `Step ${step}` : id;
  }
}
