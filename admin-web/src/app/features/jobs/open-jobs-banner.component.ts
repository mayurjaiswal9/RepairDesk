import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { JobsApi } from '../../core/api/jobs.api';
import { OPEN_JOB_STATUSES, formatIndiaDate } from '../../core/india-date';

export interface CarryoverDay {
  date: string;
  count: number;
}

@Component({
  selector: 'app-open-jobs-banner',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './open-jobs-banner.component.html',
  styleUrl: './open-jobs-banner.component.scss',
})
export class OpenJobsBannerComponent {
  private readonly api = inject(JobsApi);

  /** Bump this to reload the warning after a manual refresh. */
  readonly refreshToken = input(0);

  readonly days = signal<CarryoverDay[]>([]);
  readonly formatIndiaDate = formatIndiaDate;

  constructor() {
    effect(() => {
      this.refreshToken();
      this.api.carryover().subscribe({
        next: (body) => this.days.set(body.days ?? []),
        error: () => this.days.set([]),
      });
    });
  }

  line(day: CarryoverDay): string {
    const jobs = day.count === 1 ? 'job' : 'jobs';
    return `${day.count} ${jobs} still open from ${formatIndiaDate(day.date)}`;
  }

  showQuery(date: string): Record<string, string | number> {
    return {
      status: OPEN_JOB_STATUSES,
      from: date,
      to: date,
      page: 0,
      size: 20,
      sort: 'updatedAt,desc',
    };
  }
}
