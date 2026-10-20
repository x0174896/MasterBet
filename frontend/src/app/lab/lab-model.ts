import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe, KeyValuePipe, CurrencyPipe } from '@angular/common';
import { LabState } from './lab-state';
@Component({
  selector: 'app-lab-model',
  imports: [DatePipe, DecimalPipe, PercentPipe, KeyValuePipe, CurrencyPipe],
  templateUrl: './lab-model.html',
})
export class LabModel {
  readonly state = inject(LabState);
  policyLabel(policy: string) {
    const labels: Record<string, string> = {
      adaptive: 'Calibrazione e filtri, con alternative',
      calibrated: 'Sola calibrazione, con alternative',
      'primary-only': 'Calibrazione e filtri, senza sostituzioni',
    };
    return labels[policy] ?? policy;
  }
  ruleLabel(key: string, fallback: string): string {
    if (key.startsWith('Sostituzione senza vantaggio verificato: ')) {
      const family = key.slice('Sostituzione senza vantaggio verificato: '.length);
      return 'Alternativa non confermata · ' + this.ruleLabel('market:' + family, family);
    }
    if (key.includes(' | ')) {
      return key
        .split(' | ')
        .map((part) => this.ruleLabel(part, part))
        .join(' · ');
    }
    if (key.startsWith('market:')) {
      const [, market, period, outcome] = key.split(':');
      const markets: Record<string, string> = {
        '1x2': 'Risultato 1X2',
        totals: 'Totale gol',
        'teamtotals-team1': 'Gol della squadra di casa',
        'teamtotals-team2': 'Gol della squadra ospite',
        bothteamsscore: 'Entrambe segnano',
        correctscore: 'Risultato esatto',
        'halftime-fulltime': 'Primo tempo / finale',
      };
      const periods: Record<string, string> = {
        fulltime: 'partita',
        p1: 'primo tempo',
        p2: 'secondo tempo',
      };
      return `${markets[market] ?? market} · ${periods[period] ?? period} · ${outcome}`;
    }
    if (key.startsWith('league:')) {
      const [, id, season] = key.split(':');
      const league = this.state.coverage().find((c) => c.id === Number(id));
      if (league) return `${league.country} · ${league.name} · ${season}`;
    }
    const labels: Record<string, string> = {
      'ev:5-15': 'EV prima della calibrazione dal 5% a meno del 15%',
      'ev:15-30': 'EV prima della calibrazione dal 15% a meno del 30%',
      'ev:30-plus': 'EV prima della calibrazione almeno 30%',
      'odds:below-1.5': 'Quote inferiori a 1,50',
      'odds:1.5-2.5': 'Quote da 1,50 a meno di 2,50',
      'odds:2.5-plus': 'Quote da 2,50 in su',
    };
    const conditions: Record<string, string> = {
      'calendar:busy': 'Almeno quattro incontri complessivi nei 14 giorni precedenti',
      'previous:both-ht-zero': 'Entrambe senza gol nel precedente primo tempo',
      'previous:conceded-three': 'Almeno una reduce da tre o più gol subiti',
      'quote:older-24h': 'Ultimo prezzo osservato oltre 24 ore prima della partita',
    };
    if (fallback === key && conditions[key]) return conditions[key];
    return labels[key] ?? fallback;
  }
}
