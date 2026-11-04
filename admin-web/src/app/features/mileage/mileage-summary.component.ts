import { Component, input } from '@angular/core';
import { MileageSummary } from '../../core/api/mileage.api';
import { formatKm, formatRupees } from './mileage-format';

@Component({
  selector: 'app-mileage-summary',
  standalone: true,
  template: `
    @let s = summary();
    <div class="strip" role="group" aria-label="Mileage summary">
      <div class="stat">
        <div class="value">{{ km(s.payableDistanceM) }} <span class="unit">km</span></div>
        <div class="label">Total km</div>
      </div>
      <div class="stat">
        <div class="value">{{ s.trips }}</div>
        <div class="label">Paid trips</div>
      </div>
      <div class="stat">
        <div class="value">{{ money(s.amountPaise) }}</div>
        <div class="label">Amount</div>
      </div>
      <div class="stat" [class.warn]="s.flaggedTrips > 0">
        <div class="value">{{ s.flaggedTrips }}</div>
        <div class="label">Flagged trips</div>
      </div>
      <div class="stat" [class.warn]="s.noTripJobs > 0">
        <div class="value">{{ s.noTripJobs }}</div>
        <div class="label">No-trip jobs</div>
      </div>
    </div>
    @if (s.activeTrips > 0) {
      <p class="live">
        {{ s.activeTrips }} {{ s.activeTrips === 1 ? 'trip' : 'trips' }} in progress · counted when they end
      </p>
    }
  `,
  styles: `
    .strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
    .stat {
      position: relative; padding: 12px 16px; background: var(--color-surface);
      border: 1px solid var(--color-divider); border-radius: 12px; overflow: hidden;
    }
    .stat.warn::before {
      content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--color-warning);
    }
    .value { font-size: 22px; font-weight: 700; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
    .unit { font-size: 14px; font-weight: 600; color: var(--color-text-secondary); }
    .label { margin-top: 2px; font-size: 13px; font-weight: 600; color: var(--color-text-secondary); }
    .live {
      margin: 12px 0 0; padding: 10px 14px; border-radius: 12px;
      background: var(--color-primary-soft); color: var(--color-brand-navy); font-size: 14px;
    }
  `,
})
export class MileageSummaryComponent {
  readonly summary = input.required<MileageSummary>();
  readonly km = formatKm;
  readonly money = formatRupees;
}
