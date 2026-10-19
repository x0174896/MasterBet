import { Component, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LabState } from './lab-state';

@Component({
  selector: 'app-lab-shell',
  imports: [DatePipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './lab-shell.html',
  styleUrl: './lab-shell.scss',
})
export class LabShell implements OnInit {
  readonly state = inject(LabState);
  ngOnInit() {
    this.state.init();
  }
}
