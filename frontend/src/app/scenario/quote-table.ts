import { Component, computed, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Quote } from './scenario.models';
import { FormsModule } from '@angular/forms';
@Component({
  selector: 'app-quote-table',
  imports: [DatePipe, DecimalPipe, PercentPipe, FormsModule],
  templateUrl: './quote-table.html',
})
export class QuoteTable {
  readonly quotes = input.required<Quote[]>();
  readonly bookmaker = signal('unibet');
  readonly period = signal('');
  readonly family = signal('');
  readonly suggestionsOnly = signal(false);
  readonly limit = signal(60);
  readonly bookmakers = computed(() => [...new Set(this.quotes().map((q) => q.bookmaker))].sort());
  readonly families = computed(() => [...new Set(this.quotes().map((q) => q.type))].sort());
  readonly filtered = computed(() =>
    this.quotes()
      .filter(
        (q) =>
          (!this.bookmaker() || q.bookmaker === this.bookmaker()) &&
          (!this.period() || q.period === this.period()) &&
          (!this.family() || q.type === this.family()) &&
          (!this.suggestionsOnly() || q.suggested),
      )
      .sort(
        (a, b) =>
          this.rank(a.type) - this.rank(b.type) ||
          a.market.localeCompare(b.market) ||
          (a.line ?? 0) - (b.line ?? 0),
      ),
  );
  readonly visible = computed(() => this.filtered().slice(0, this.limit()));
  showMore() {
    this.limit.update((n) => n + 60);
  }
  hasLine(q: Quote) {
    return q.line !== null && /^(totals|teamtotals|spreads)/.test(q.type);
  }
  private rank(type: string) {
    const rank = ['1x2', 'totals', 'bothteamsscore', 'halftime-fulltime', 'correctscore'].indexOf(
      type,
    );
    return rank < 0 ? 99 : rank;
  }
  periodLabel(period: string) {
    return (
      ({ fulltime: 'Finale', p1: '1° tempo', p2: '2° tempo' } as Record<string, string>)[period] ??
      period
    );
  }
  familyLabel(family: string) {
    return (
      (
        {
          '1x2': 'Esito 1 X 2',
          totals: 'Over / Under',
          bothteamsscore: 'Entrambe segnano',
          'halftime-fulltime': 'Parziale / Finale',
          correctscore: 'Risultato esatto',
          handicap: 'Handicap',
          teamtotals: 'Gol squadra',
          doublechance: 'Doppia chance',
        } as Record<string, string>
      )[family] ?? family
    );
  }
}
