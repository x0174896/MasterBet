import { Routes } from '@angular/router';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./scenario/scenario-page').then((m) => m.ScenarioPage),
    title: 'MasterBet · Laboratorio storico',
  },
  { path: '**', redirectTo: '' },
];
