import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, finalize } from 'rxjs';
import { DashboardApi } from '../../core/api/dashboard.api';
import { mapHttpError, MappedApiError } from '../../core/api/api-error';
import { SessionService } from '../../core/auth/session.service';
import { DashboardCounters } from '../../core/models/dashboard.models';
import { quickLinksForRole } from '../../core/nav/nav.config';

type LoadState = 'loading' | 'loaded' | 'error' | 'forbidden';

interface CounterCard {
  key: keyof DashboardCounters;
  label: string;
  tone: 'warning' | 'secondary' | 'primary';
  link: string | null;
  staffBlocked?: boolean;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(DashboardApi);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<LoadState>('loading');
  readonly counters = signal<DashboardCounters | null>(null);
  readonly refreshing = signal(false);
  readonly error = signal<MappedApiError | null>(null);

  readonly role = this.session.role;
  readonly greeting = computed(() => {
    const name = this.session.user()?.name?.trim();
    return name ? `Welcome back, ${name}` : 'Welcome back';
  });

  readonly quickLinks = computed(() => quickLinksForRole(this.role()));

  readonly allZero = computed(() => {
    const c = this.counters();
    if (!c || this.state() !== 'loaded') return false;
    return (
      c.pendingReview === 0 &&
      c.infoRequired === 0 &&
      c.activeJobs === 0 &&
      c.unscheduledJobs === 0
    );
  });

  readonly showBanner = computed(() => {
    const c = this.counters();
    return this.state() === 'loaded' && !!c && c.unscheduledJobs > 0;
  });

  readonly bannerText = computed(() => {
    const n = this.counters()?.unscheduledJobs ?? 0;
    return n === 1
      ? '1 job is not yet scheduled.'
      : `${n} jobs are not yet scheduled.`;
  });

  readonly cards = computed((): CounterCard[] => {
    const isAdmin = this.role() === 'ADMIN';
    return [
      {
        key: 'pendingReview',
        label: 'Pending review',
        tone: 'warning',
        link: isAdmin ? '/requests?status=PENDING_REVIEW' : null,
        staffBlocked: !isAdmin,
      },
      {
        key: 'infoRequired',
        label: 'Needs info',
        tone: 'secondary',
        link: isAdmin ? '/requests?status=INFORMATION_REQUIRED' : null,
        staffBlocked: !isAdmin,
      },
      {
        key: 'activeJobs',
        label: 'Active jobs',
        tone: 'primary',
        link: '/jobs?active=true',
      },
      {
        key: 'unscheduledJobs',
        label: 'Unscheduled jobs',
        tone: 'warning',
        link: '/jobs?scheduled=false',
      },
    ];
  });

  ngOnInit(): void {
    this.load(false);
    // Refetch when returning to the dashboard without destroying the component (AC-AP03-13).
    let skipNext = true;
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e) => {
        const onDash =
          e.urlAfterRedirects === '/dashboard' || e.urlAfterRedirects.startsWith('/dashboard?');
        if (!onDash) {
          skipNext = false;
          return;
        }
        if (skipNext) {
          skipNext = false;
          return;
        }
        this.load(true);
      });
  }

  refresh(): void {
    this.load(true);
  }

  retry(): void {
    this.load(false);
  }

  displayValue(key: keyof DashboardCounters): string {
    const c = this.counters();
    if (!c) return '—';
    const v = c[key];
    return v === undefined || v === null ? '—' : String(v);
  }

  ariaName(card: CounterCard): string {
    const value = this.displayValue(card.key);
    const unit =
      card.key === 'pendingReview' || card.key === 'infoRequired' ? 'requests' : 'jobs';
    return `${card.label}: ${value} ${unit}`;
  }

  /** Parse `path?a=b&c=d` into query params for routerLink. */
  linkQuery(link: string): Record<string, string> {
    const q = link.split('?')[1];
    if (!q) return {};
    const params: Record<string, string> = {};
    for (const part of q.split('&')) {
      const [k, v] = part.split('=');
      if (k) params[k] = decodeURIComponent(v ?? '');
    }
    return params;
  }

  private load(isRefresh: boolean): void {
    if (isRefresh) {
      this.refreshing.set(true);
    } else if (this.state() !== 'loaded') {
      this.state.set('loading');
    }

    this.api
      .getCounters()
      .pipe(finalize(() => this.refreshing.set(false)))
      .subscribe({
        next: (c) => {
          this.counters.set(c);
          this.error.set(null);
          this.state.set('loaded');
        },
        error: (err) => {
          const mapped = mapHttpError(err, "Couldn't load counts");
          this.error.set(mapped);
          if (mapped.status === 403) {
            this.state.set('forbidden');
          } else {
            this.state.set('error');
          }
          if (!isRefresh) {
            this.counters.set(null);
          }
        },
      });
  }
}
