import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { LabState } from './lab-state';
@Component({
  selector: 'app-lab-coverage',
  imports: [DatePipe],
  templateUrl: './lab-coverage.html',
})
export class LabCoverage {
  readonly state = inject(LabState);
}
