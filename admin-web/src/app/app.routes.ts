import { Routes } from '@angular/router';
import { authGuard, guestGuard, mustChangeGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'change-password',
    canActivate: [mustChangeGuard],
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component').then(
        (m) => m.ChangePasswordComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'jobs',
        loadComponent: () =>
          import('./features/jobs/jobs-list.component').then((m) => m.JobsListComponent),
      },
      {
        path: 'requests',
        loadComponent: () =>
          import('./features/requests/requests-list.component').then((m) => m.RequestsListComponent),
      },
      {
        path: 'requests/new',
        loadComponent: () =>
          import('./features/requests/create-request.component').then((m) => m.CreateRequestComponent),
      },
      {
        path: 'requests/:id',
        loadComponent: () =>
          import('./features/requests/request-detail.component').then(
            (m) => m.RequestDetailComponent,
          ),
      },
      {
        path: 'customers/:id',
        loadComponent: () =>
          import('./features/customers/customer-detail.component').then(
            (m) => m.CustomerDetailComponent,
          ),
      },
      {
        path: 'jobs/:id/schedule',
        loadComponent: () =>
          import('./features/jobs/schedule-appointment.component').then(
            (m) => m.ScheduleAppointmentComponent,
          ),
      },
      {
        path: 'jobs/:id',
        loadComponent: () =>
          import('./features/jobs/job-detail.component').then((m) => m.JobDetailComponent),
      },
      {
        path: 'appointments',
        loadComponent: () =>
          import('./features/appointments/appointments-calendar.component').then(
            (m) => m.AppointmentsCalendarComponent,
          ),
      },
      {
        path: 'customers',
        loadComponent: () =>
          import('./features/customers/customers-list.component').then(
            (m) => m.CustomersListComponent,
          ),
      },
      {
        path: 'staff',
        loadComponent: () =>
          import('./features/staff/staff-list.component').then((m) => m.StaffListComponent),
      },
      {
        path: 'catalog/categories/:id/brands',
        loadComponent: () =>
          import('./features/catalog/brands-list.component').then((m) => m.BrandsListComponent),
      },
      {
        path: 'catalog/categories',
        loadComponent: () =>
          import('./features/catalog/categories-list.component').then(
            (m) => m.CategoriesListComponent,
          ),
      },
      {
        path: 'settings/notifications',
        loadComponent: () =>
          import('./features/settings/notification-settings.component').then(
            (m) => m.NotificationSettingsComponent,
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/business-settings.component').then(
            (m) => m.BusinessSettingsComponent,
          ),
      },
      {
        path: 'settings/location',
        loadComponent: () =>
          import('./features/settings/location-settings.component').then(
            (m) => m.LocationSettingsComponent,
          ),
      },
      {
        path: 'location/live/:technicianId/:jobId',
        loadComponent: () =>
          import('./features/location/live-track.component').then((m) => m.LiveTrackComponent),
      },
      {
        path: 'location',
        loadComponent: () =>
          import('./features/location/location-board.component').then(
            (m) => m.LocationBoardComponent,
          ),
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./features/attendance/attendance-list.component').then(
            (m) => m.AttendanceListComponent,
          ),
      },
      {
        path: 'mileage',
        loadComponent: () =>
          import('./features/mileage/mileage-list.component').then((m) => m.MileageListComponent),
      },
      {
        path: 'mileage/:technicianId/:date',
        loadComponent: () =>
          import('./features/mileage/mileage-day.component').then((m) => m.MileageDayComponent),
      },
      { path: 'milage', pathMatch: 'full', redirectTo: 'mileage' },
      { path: 'milage/:technicianId/:date', redirectTo: 'mileage/:technicianId/:date' },
      {
        path: 'account',
        loadComponent: () =>
          import('./features/account/account.component').then((m) => m.AccountComponent),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications-list.component').then(
            (m) => m.NotificationsListComponent,
          ),
      },
      {
        path: '403',
        loadComponent: () =>
          import('./features/common/forbidden.component').then((m) => m.ForbiddenComponent),
      },
      {
        path: '**',
        loadComponent: () =>
          import('./features/common/simple-placeholder.component').then(
            (m) => m.SimplePlaceholderComponent,
          ),
        data: {
          title: 'Page not found',
          blurb: 'That URL does not match a panel page. Use the sidebar to continue.',
        },
      },
    ],
  },
];
