import { Routes } from '@angular/router';
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./lab/lab-shell').then((m) => m.LabShell),
    title: 'MasterBet · Laboratorio storico',
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'scommesse' },
      { path: 'scommesse', loadComponent: () => import('./lab/lab-bets').then((m) => m.LabBets) },
      { path: 'andamento', loadComponent: () => import('./lab/lab-chart').then((m) => m.LabChart) },
      {
        path: 'campionati',
        loadComponent: () => import('./lab/lab-coverage').then((m) => m.LabCoverage),
      },
      { path: 'modello', loadComponent: () => import('./lab/lab-model').then((m) => m.LabModel) },
      { path: 'admin', loadComponent: () => import('./lab/lab-admin').then((m) => m.LabAdmin) },
    ],
  },
  { path: '**', redirectTo: '' },
];
