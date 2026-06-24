import {Routes} from '@angular/router';
import {authGuard} from './core/guards/auth.guard';
import {guestGuard} from './core/guards/guest.guard';
import {roleGuard} from './core/guards/role.guard';

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
        path: 'login',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/pages/login/login').then(c => c.Login)
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/pages/register/register').then(c => c.Register)
      },
      {
        path: 'forgot-password',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/pages/forgot-password/forgot-password').then(c => c.ForgotPassword)
      },
      {
        path: 'reset-password',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/pages/reset-password/reset-password').then(c => c.ResetPassword)
      },
      {
        path: 'resend-verification',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/pages/resend-verification/resend-verification').then(c => c.ResendVerification)
      },
      {
        path: 'verify-email',
        loadComponent: () => import('./features/auth/pages/verify-email/verify-email').then(c => c.VerifyEmail)
      }
    ]
  },
  {
    path: '',
    loadComponent: () => import('./layouts/private-layout/private-layout').then(c => c.PrivateLayout),
    canActivate: [authGuard],
    children: [
      {
        path: 'profile',
        loadComponent: () => import('./features/auth/pages/profile/profile').then(c => c.Profile)
      },
      {
        path: 'projects',
        loadComponent: () => import('./features/projects/pages/projects/projects').then(c => c.Projects)
      },
      {
        path: 'tasks',
        loadComponent: () => import('./features/tasks/pages/tasks/tasks').then(c => c.Tasks)
      },
      {
        path: 'files',
        loadComponent: () => import('./features/files/pages/files/files').then(c => c.Files)
      },
      {
        path: 'users',
        canActivate: [roleGuard],
        data: {roles: ['ADMIN']},
        loadComponent: () => import('./features/users/pages/users/users').then(c => c.Users)
      },
      {
        path: 'dev-inbox',
        canActivate: [roleGuard],
        data: {roles: ['ADMIN']},
        loadComponent: () => import('./features/dev-inbox/pages/dev-inbox/dev-inbox').then(c => c.DevInbox)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
