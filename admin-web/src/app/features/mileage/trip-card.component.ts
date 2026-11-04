import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TripResponse } from '../../core/api/mileage.api';
import {
  durationLabel,
  flagLabel,
  formatKm,
  formatRate,
  formatRupees,
  indiaClock,
  indiaDateTime,
  startKindLabel,
  tripColor,
} from './mileage-format';

@Component({
  selector: 'app-trip-card',
  standalone: true,
  imports: [RouterLink],
  template: `
    @let t = trip();
    <article class="trip" [class.cancelled]="t.status === 'CANCELLED'">
      <span class="num" [style.background]="color()" aria-hidden="true">{{ index() + 1 }}</span>
      <div class="body">
        <div class="line head">
          <strong>{{ timeRange() }}</strong>
          <span class="muted">· {{ duration(t.durationSeconds) }}</span>
          @if (t.status === 'ACTIVE') {
            <span class="chip chip-live">In progress</span>
          } @else if (t.status === 'CANCELLED') {
            <span class="chip chip-neutral">Cancelled</span>
          }
          @if (canAdjust() && t.status === 'ENDED') {
            <button type="button" class="ghost-btn small adjust" (click)="adjust.emit(t)">Adjust km</button>
          }
        </div>
        <div class="line">
          <span>{{ startLabel() }}</span>
          <span class="muted">→</span>
          @if (showJob()) {
            <a class="ref" [routerLink]="['/jobs', t.jobId]">{{ t.jobReference }}</a>
          } @else {
            <span class="ref plain">{{ t.jobReference }}</span>
          }
          @if (showTechnician()) {
            <span class="muted">· {{ t.technicianName }}</span>
          }
        </div>
        <dl class="figures">
          <div><dt>Tracked</dt><dd>{{ km(t.trackedDistanceM + t.estimatedGapM) }} km</dd></div>
          <div>
            <dt>Planned</dt>
            <dd>{{ t.plannedDistanceM == null ? '—' : km(t.plannedDistanceM) + ' km' + (t.plannedEstimated ? ' (estimate)' : '') }}</dd>
          </div>
          <div><dt>Payable</dt><dd class="strong">{{ km(t.payableDistanceM) }} km</dd></div>
          <div>
            <dt>Amount</dt>
            <dd>{{ t.amountPaise == null ? '—' : money(t.amountPaise) }}@if (t.ratePaise != null) {<span class="muted"> · {{ rate(t.ratePaise) }}</span>}</dd>
          </div>
        </dl>
        @if (t.flags.length) {
          <div class="flags">
            @for (f of t.flags; track f) {
              <span class="chip chip-warning">{{ flag(f) }}</span>
            }
          </div>
        }
        @if (t.adjustedDistanceM != null) {
          <p class="note">{{ adjustedNote() }}</p>
        }
      </div>
    </article>
  `,
  styles: `
    .trip { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--color-divider); }
    :host:first-of-type .trip { border-top: none; padding-top: 0; }
    .trip.cancelled .body { opacity: 0.7; }
    .num {
      flex: 0 0 26px; height: 26px; border-radius: 50%; color: #fff;
      display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700;
    }
    .body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; font-size: 14px; }
    .line { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .muted { color: var(--color-text-secondary); }
    .ref { font-family: var(--font-mono); font-size: 13px; font-weight: 600; color: var(--color-primary); text-decoration: none; }
    a.ref:hover { text-decoration: underline; }
    .ref.plain { color: inherit; }
    .figures { display: flex; flex-wrap: wrap; gap: 4px 20px; margin: 0; }
    .figures dt { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: var(--color-text-secondary); }
    .figures dd { margin: 0; font-variant-numeric: tabular-nums; }
    .figures .strong { font-weight: 700; }
    .flags { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip {
      display: inline-flex; align-items: center; height: 22px; padding: 0 10px;
      border-radius: var(--radius-pill); font-size: 12px; font-weight: 600; white-space: nowrap;
    }
    .chip-warning { background: #fff3e0; color: #e65100; }
    .chip-neutral { background: #eef2f7; color: #616161; }
    .chip-live { background: var(--color-primary-soft); color: var(--color-primary-dark); }
    .note { margin: 0; padding: 8px 10px; border-radius: 8px; background: #f6faff; font-size: 13px; color: var(--color-text-secondary); }
    .ghost-btn.small {
      margin-left: auto; height: 32px; padding: 0 12px; border-radius: var(--radius-pill);
      border: 1px solid var(--color-divider); background: var(--color-surface); color: var(--color-text-primary);
      font: inherit; font-size: 13px; font-weight: 600; cursor: pointer;
    }
  `,
})
export class TripCardComponent {
  readonly trip = input.required<TripResponse>();
  readonly index = input(0);
  readonly showJob = input(true);
  readonly showTechnician = input(false);
  readonly canAdjust = input(false);
  readonly adjust = output<TripResponse>();

  readonly km = formatKm;
  readonly money = formatRupees;
  readonly rate = formatRate;
  readonly duration = durationLabel;
  readonly flag = flagLabel;

  readonly color = computed(() => tripColor(this.index(), this.trip().status));
  readonly startLabel = computed(() => startKindLabel(this.trip()));
  readonly timeRange = computed(() => {
    const t = this.trip();
    const start = indiaClock(t.startedAt);
    if (t.status === 'ACTIVE') return `${start} – now`;
    return t.endedAt ? `${start} – ${indiaClock(t.endedAt)}` : start;
  });
  readonly adjustedNote = computed(() => {
    const t = this.trip();
    const original = formatKm(t.trackedDistanceM + t.estimatedGapM);
    const by = t.adjustedByName ? ` by ${t.adjustedByName}` : '';
    const at = t.adjustedAt ? `, ${indiaDateTime(t.adjustedAt)}` : '';
    const reason = t.adjustReason ? ` — ${t.adjustReason}` : '';
    return `Adjusted from ${original} km${by}${at}${reason}`;
  });
}
