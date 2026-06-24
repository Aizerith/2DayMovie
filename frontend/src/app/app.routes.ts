import {Routes} from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layouts/public-layout/public-layout').then(c => c.PublicLayout),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home').then(c => c.Home)
      },
      {
        path: 'watch/:shareCode',
        loadComponent: () => import('./features/watch/watch').then(c => c.Watch)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
