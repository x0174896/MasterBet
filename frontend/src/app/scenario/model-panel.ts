import { Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Fixture, ModelSummary, PlayedMatch } from './scenario.models';
@Component({
  selector: 'app-model-panel',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './model-panel.html',
})
export class ModelPanel {
  readonly model = input.required<ModelSummary>();
  readonly cups = input.required<Fixture[]>();
  readonly cupResults = input.required<PlayedMatch[]>();
}
