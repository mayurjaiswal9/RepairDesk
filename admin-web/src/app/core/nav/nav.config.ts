import { UserRole } from '../models/auth.models';

/** Shared nav + quick-link definition (AP-02 §7 / AP-03 §4 #11). */
export interface NavItemDef {
  id: string;
  label: string;
  path: string;
  icon: string;
  roles: UserRole[];
  description: string;
}

export interface NavGroupDef {
  id: string;
  label: string;
  items: NavItemDef[];
}

export const NAV_GROUPS: NavGroupDef[] = [
  {
    id: 'work',
    label: 'Work',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        path: '/dashboard',
        icon: 'dashboard',
        roles: ['ADMIN', 'STAFF'],
        description: 'Shop-wide work counts and shortcuts',
      },
      {
        id: 'requests',
        label: 'Repair Requests',
        path: '/requests',
        icon: 'inbox',
        roles: ['ADMIN'],
        description: 'Review and approve incoming requests',
      },
      {
        id: 'create-request',
        label: 'Request repair',
        path: '/requests/new',
        icon: 'add_circle',
        roles: ['ADMIN', 'STAFF'],
        description: 'Walk-in or phone intake for a customer',
      },
      {
        id: 'location',
        label: 'Locations',
        path: '/location',
        icon: 'my_location',
        roles: ['ADMIN'],
        description: 'Office, technician positions, and job ETAs',
      },
      {
        id: 'attendance',
        label: 'Attendance',
        path: '/attendance',
        icon: 'fact_check',
        roles: ['ADMIN', 'STAFF'],
        description: 'Technician work days',
      },
      {
        id: 'mileage',
        label: 'Mileage',
        path: '/mileage',
        icon: 'route',
        roles: ['ADMIN'],
        description: 'Trip km, routes, and travel allowance',
      },
      {
        id: 'jobs',
        label: 'Jobs',
        path: '/jobs',
        icon: 'build',
        roles: ['ADMIN', 'STAFF', 'TECHNICIAN'],
        description: 'Track and manage repair jobs',
      },
      {
        id: 'appointments',
        label: 'Appointments',
        path: '/appointments',
        icon: 'event',
        roles: ['ADMIN', 'STAFF', 'TECHNICIAN'],
        description: 'Weekly calendar view',
      },
    ],
  },
  {
    id: 'people',
    label: 'People',
    items: [
      {
        id: 'customers',
        label: 'Customers',
        path: '/customers',
        icon: 'person',
        roles: ['ADMIN', 'STAFF'],
        description: 'Search customer profiles and history',
      },
      {
        id: 'staff',
        label: 'Staff',
        path: '/staff',
        icon: 'group',
        roles: ['ADMIN'],
        description: 'Manage team members',
      },
    ],
  },
  {
    id: 'catalog',
    label: 'Catalog',
    items: [
      {
        id: 'categories',
        label: 'Device Categories',
        path: '/catalog/categories',
        icon: 'devices',
        roles: ['ADMIN'],
        description: 'Manage the device catalog',
      },
    ],
  },
  {
    id: 'config',
    label: 'Configuration',
    items: [
      {
        id: 'settings',
        label: 'Business Settings',
        path: '/settings',
        icon: 'settings',
        roles: ['ADMIN'],
        description: 'Business details and working hours',
      },
      {
        id: 'notification-settings',
        label: 'Notification settings',
        path: '/settings/notifications',
        icon: 'notifications_active',
        roles: ['ADMIN'],
        description: 'Choose who is told, and how',
      },
      {
        id: 'office-location',
        label: 'Office location',
        path: '/settings/location',
        icon: 'place',
        roles: ['ADMIN'],
        description: 'Shop pin, geofence, and location features',
      },
    ],
  },
];

export function navGroupsForRole(role: UserRole | null): NavGroupDef[] {
  if (!role) return [];
  return NAV_GROUPS.map((g) => ({
    ...g,
    items: g.items.filter((i) => i.roles.includes(role)),
  })).filter((g) => g.items.length > 0);
}

export function quickLinksForRole(role: UserRole | null): NavItemDef[] {
  const links = navGroupsForRole(role)
    .flatMap((g) => g.items)
    .filter((i) => i.id !== 'dashboard');
  if (role === 'ADMIN' || role === 'STAFF') {
    if (!links.some((l) => l.id === 'create-request')) {
      const create: NavItemDef = {
        id: 'create-request',
        label: 'Request repair',
        path: '/requests/new',
        icon: 'add_circle',
        roles: ['ADMIN', 'STAFF'],
        description: 'Walk-in or phone intake for a customer',
      };
      const idx = links.findIndex((l) => l.id === 'requests');
      if (idx >= 0) links.splice(idx + 1, 0, create);
      else links.unshift(create);
    }
  }
  return links;
}

export function isNavActive(currentUrl: string, path: string): boolean {
  if (path === '/dashboard') {
    return currentUrl === '/dashboard' || currentUrl.startsWith('/dashboard?');
  }
  if (path === '/requests') {
    return (
      currentUrl === '/requests' ||
      currentUrl.startsWith('/requests?') ||
      (currentUrl.startsWith('/requests/') && !currentUrl.startsWith('/requests/new'))
    );
  }
  if (path === '/settings') {
    return currentUrl === '/settings' || currentUrl.startsWith('/settings?');
  }
  return currentUrl === path || currentUrl.startsWith(path + '/') || currentUrl.startsWith(path + '?');
}
