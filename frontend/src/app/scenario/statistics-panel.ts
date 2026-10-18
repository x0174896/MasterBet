import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PlayedMatch, TeamStatistics } from './scenario.models';
@Component({
  selector: 'app-statistics-panel',
  imports: [DatePipe],
  templateUrl: './statistics-panel.html',
})
export class StatisticsPanel {
  readonly teams = input.required<TeamStatistics[]>();
  readonly history = input.required<PlayedMatch[]>();
}
