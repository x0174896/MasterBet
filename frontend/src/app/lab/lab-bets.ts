import { Component, inject } from '@angular/core';
import { CurrencyPipe, DatePipe, PercentPipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LabState } from './lab-state';

@Component({
  selector: 'app-lab-bets',
  imports: [CurrencyPipe, DatePipe, PercentPipe, DecimalPipe, RouterLink],
  templateUrl: './lab-bets.html',
})
export class LabBets {
  readonly state = inject(LabState);
  periodLabel(period: string) {
    const periods: Record<string, string> = {
      fulltime: 'Partita intera',
      p1: 'Primo tempo',
      p2: 'Secondo tempo',
    };
    return periods[period] ?? period;
  }
}
