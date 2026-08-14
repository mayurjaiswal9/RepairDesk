import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { finalize } from 'rxjs';
import { NotificationsApi, AppNotification } from '../../core/api/notifications.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { UnreadBadgeService } from '../../core/notifications/unread-badge.service';

type PageState = 'loading' | 'ready' | 'error';
type Filter = 'all' | 'unread';

function formatNotificationTime(iso: string): { label: string; absolute: string } {
  const date = new Date(iso);
  const absolute = date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
  const diffMs = Date.now() - date.getTime();
  const dayMs = 86_400_000;
  if (diffMs < 0) return { label: absolute, absolute };
  if (diffMs < 60_000) return { label: 'Just now', absolute };
  if (diffMs < 3_600_000) {
    const m = Math.round(diffMs / 60_000);
    return { label: m === 1 ? '1 minute ago' : `${m} minutes ago`, absolute };
  }
  if (diffMs < dayMs) {
    const h = Math.round(diffMs / 3_600_000);
    return { label: h === 1 ? '1 hour ago' : `${h} hours ago`, absolute };
  }
  if (diffMs < 2 * dayMs) return { label: 'Yesterday', absolute };
  if (diffMs < 7 * dayMs) {
    const d = Math.round(diffMs / dayMs);
    return { label: d === 1 ? '1 day ago' : `${d} days ago`, absolute };
  }
  return { label: absolute, absolute };
}

function recordLink(n: AppNotification): string | null {
  if (!n.relatedEntityId || !n.relatedType) return null;
  switch (n.relatedType) {
    case 'REPAIR_REQUEST':
      return `/requests/${n.relatedEntityId}`;
    case 'JOB':
      return `/jobs/${n.relatedEntityId}`;
    case 'APPOINTMENT':
      // Appointment notifications currently store the job id; job detail hosts the appointment.
      return `/jobs/${n.relatedEntityId}`;
    default:
      return null;
  }
}

@Component({
  selector: 'app-notifications-list',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './notifications-list.component.html',
  styleUrl: './notifications-list.component.scss',
})
export class NotificationsListComponent implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly badge = inject(UnreadBadgeService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatNotificationTime = formatNotificationTime;
  readonly recordLink = recordLink;

  readonly state = signal<PageState>('loading');
  readonly items = signal<AppNotification[]>([]);
  readonly error = signal<MappedApiError | null>(null);
  readonly filter = signal<Filter>('all');
  readonly markingAll = signal(false);
  readonly markingIds = signal<Set<string>>(new Set());

  readonly bellCount = this.badge.count;

  readonly unreadInList = computed(() => this.items().filter((n) => !n.read).length);
  readonly rows = computed(() => {
    const all = this.items();
    return this.filter() === 'unread' ? all.filter((n) => !n.read) : all;
  });

  readonly summary = computed(() => {
    if (this.state() !== 'ready') return '';
    const n = this.items().length;
    const u = this.unreadInList();
    return `${n} notification${n === 1 ? '' : 's'} · ${u} unread`;
  });

  readonly empty = computed(() => this.state() === 'ready' && this.items().length === 0);
  readonly filteredEmpty = computed(
    () =>
      this.state() === 'ready' &&
      this.filter() === 'unread' &&
      this.items().length > 0 &&
      this.unreadInList() === 0,
  );
  readonly showCapNote = computed(
    () => this.state() === 'ready' && this.items().length >= 30,
  );
  readonly staleBar = computed(
    () => this.state() === 'ready' && this.bellCount() > this.unreadInList(),
  );

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.filter.set(params.get('filter') === 'unread' ? 'unread' : 'all');
    });
    this.fetch();
  }

  setFilter(filter: Filter): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: filter === 'unread' ? { filter: 'unread' } : {},
    });
  }

  refresh(): void {
    this.fetch();
  }

  retry(): void {
    this.fetch();
  }

  markAllRead(): void {
    if (this.markingAll() || this.unreadInList() === 0) return;
    this.markingAll.set(true);
    this.api
      .markAllRead()
      .pipe(finalize(() => this.markingAll.set(false)))
      .subscribe({
        next: () => {
          this.items.update((list) => list.map((n) => ({ ...n, read: true })));
          this.badge.zero();
          this.snack.open('All notifications marked as read', undefined, { duration: 3000 });
        },
        error: (err) => {
          this.snack.open(mapHttpError(err).message, undefined, { duration: 4000 });
        },
      });
  }

  onRowActivate(n: AppNotification, event?: MouseEvent): void {
    const link = recordLink(n);
    const openInNew = !!(event && (event.metaKey || event.ctrlKey || event.button === 1));
    if (!n.read) this.markRead(n);
    if (link) {
      if (openInNew) {
        window.open(link, '_blank');
      } else {
        void this.router.navigateByUrl(link);
      }
    }
  }

  markReadOnly(n: AppNotification): void {
    if (n.read) return;
    this.markRead(n);
  }

  ariaLabel(n: AppNotification): string {
    const time = formatNotificationTime(n.createdAt).label;
    const state = n.read ? 'Read' : 'Unread';
    const ref = n.referenceNumber ? `, ${n.referenceNumber}` : '';
    return `${state}: ${n.message}${ref}, ${time}`;
  }

  private markRead(n: AppNotification): void {
    if (this.markingIds().has(n.id)) return;
    const next = new Set(this.markingIds());
    next.add(n.id);
    this.markingIds.set(next);

    this.api
      .markRead(n.id)
      .pipe(
        finalize(() => {
          const cleared = new Set(this.markingIds());
          cleared.delete(n.id);
          this.markingIds.set(cleared);
        }),
      )
      .subscribe({
        next: () => {
          this.items.update((list) =>
            list.map((item) => (item.id === n.id ? { ...item, read: true } : item)),
          );
          this.badge.decrement(1);
        },
        error: (err) => {
          this.snack.open(mapHttpError(err).message, undefined, { duration: 4000 });
        },
      });
  }

  private fetch(): void {
    this.state.set('loading');
    this.error.set(null);
    this.api.list().subscribe({
      next: (list) => {
        this.items.set(list ?? []);
        this.state.set('ready');
      },
      error: (err) => {
        const mapped = mapHttpError(err);
        this.error.set(mapped);
        this.state.set('error');
      },
    });
  }
}
