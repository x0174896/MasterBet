import { Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe, PercentPipe, KeyValuePipe } from '@angular/common';
import { LabState } from './lab-state';
@Component({
  selector: 'app-lab-model',
  imports: [DatePipe, DecimalPipe, PercentPipe, KeyValuePipe],
  templateUrl: './lab-model.html',
})
export class LabModel {
  readonly state = inject(LabState);
}
