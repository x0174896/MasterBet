import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ScenarioState } from './scenario-state';
import { QuoteTable } from './quote-table';
import { StatisticsPanel } from './statistics-panel';
import { ModelPanel } from './model-panel';
import { BettingPanel } from './betting-panel';
@Component({
  selector: 'app-scenario-page',
  providers: [ScenarioState],
  imports: [DatePipe, DecimalPipe, QuoteTable, StatisticsPanel, ModelPanel, BettingPanel],
  templateUrl: './scenario-page.html',
})
export class ScenarioPage implements OnInit {
  readonly state = inject(ScenarioState);
  readonly tab = signal<'odds' | 'stats' | 'model' | 'betting'>('betting');
  ngOnInit() {
    this.state.load();
  }
}
