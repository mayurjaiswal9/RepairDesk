import {
  SchedulingConfig,
  WorkingHoursDay,
  addDays,
  hoursForDate,
  parseDateInput,
  parseTimeToMinutes,
  shopDayOfWeek,
  startOfToday,
  toDateInputValue,
} from '../../core/models/scheduling.models';
import { AppointmentSummary } from '../../core/models/job.models';

export type CalView = 'day' | 'week';

export interface DayColumn {
  date: Date;
  dateStr: string;
  label: string; // Mon 3
  longLabel: string;
  isToday: boolean;
  hours: WorkingHoursDay | null;
  closed: boolean;
}

export interface LaidOutAppt {
  appt: AppointmentSummary;
  topPct: number;
  heightPct: number;
  colIndex: number;
  colCount: number;
}

/** Monday (00:00) of the week containing `date`. */
export function mondayOf(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  const dow = shopDayOfWeek(d); // 0=Mon
  return addDays(d, -dow);
}

export function rangeForView(view: CalView, anchor: Date): { from: string; to: string; days: Date[] } {
  if (view === 'day') {
    const d = new Date(anchor);
    d.setHours(0, 0, 0, 0);
    return { from: toDateInputValue(d), to: toDateInputValue(d), days: [d] };
  }
  const mon = mondayOf(anchor);
  const days = Array.from({ length: 7 }, (_, i) => addDays(mon, i));
  return {
    from: toDateInputValue(days[0]),
    to: toDateInputValue(days[6]),
    days,
  };
}

export function formatRangeLabel(view: CalView, days: Date[]): string {
  if (!days.length) return '';
  if (view === 'day') {
    return days[0].toLocaleDateString(undefined, {
      weekday: 'long',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  }
  const a = days[0];
  const b = days[6];
  const sameMonth = a.getMonth() === b.getMonth();
  const left = a.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
  const right = b.toLocaleDateString(undefined, {
    day: 'numeric',
    month: sameMonth ? undefined : 'short',
    year: 'numeric',
  });
  return `${left} – ${right}`;
}

export function buildDayColumns(
  days: Date[],
  config: SchedulingConfig | null,
): DayColumn[] {
  const todayStr = toDateInputValue(startOfToday());
  return days.map((date) => {
    const dateStr = toDateInputValue(date);
    const hours = config ? hoursForDate(config, date) : null;
    return {
      date,
      dateStr,
      label: date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric' }),
      longLabel: date.toLocaleDateString(undefined, {
        weekday: 'long',
        day: 'numeric',
        month: 'short',
      }),
      isToday: dateStr === todayStr,
      hours,
      closed: !!hours?.isClosed,
    };
  });
}

/** Axis start/end in minutes from midnight. */
export function computeAxis(
  config: SchedulingConfig | null,
  days: Date[],
  appointments: AppointmentSummary[],
): { start: number; end: number; step: number } {
  let start = 8 * 60;
  let end = 20 * 60;
  const step = config?.slotDurationMinutes || 60;

  if (config?.workingHours?.length) {
    let openMin = Infinity;
    let closeMax = -Infinity;
    for (const day of days) {
      const h = hoursForDate(config, day);
      if (!h || h.isClosed) continue;
      const o = parseTimeToMinutes(h.openTime ?? undefined);
      const c = parseTimeToMinutes(h.closeTime ?? undefined);
      if (o != null) openMin = Math.min(openMin, o);
      if (c != null) closeMax = Math.max(closeMax, c);
    }
    if (openMin !== Infinity) start = openMin;
    if (closeMax !== -Infinity) end = closeMax;
  }

  for (const a of appointments) {
    const s = parseTimeToMinutes(a.startTime);
    const e = parseTimeToMinutes(a.endTime ?? a.startTime);
    if (s != null) start = Math.min(start, s);
    if (e != null) end = Math.max(end, e);
  }

  // pad one slot
  start = Math.max(0, start - step);
  end = Math.min(24 * 60, end + step);
  if (end <= start) end = start + step * 4;
  return { start, end, step };
}

export function timeLabels(axisStart: number, axisEnd: number, step: number): string[] {
  const labels: string[] = [];
  for (let t = axisStart; t < axisEnd; t += step) {
    const h = Math.floor(t / 60);
    const m = t % 60;
    const d = new Date();
    d.setHours(h, m, 0, 0);
    labels.push(d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }));
  }
  return labels;
}

export function layoutDayAppointments(
  appts: AppointmentSummary[],
  axisStart: number,
  axisEnd: number,
): LaidOutAppt[] {
  const span = Math.max(1, axisEnd - axisStart);
  const items = appts
    .map((appt) => {
      const s = parseTimeToMinutes(appt.startTime) ?? axisStart;
      const e = parseTimeToMinutes(appt.endTime ?? appt.startTime) ?? s + 60;
      return { appt, s, e: Math.max(e, s + 15) };
    })
    .sort((a, b) => a.s - b.s || a.appt.referenceNumber.localeCompare(b.appt.referenceNumber));

  // Greedy column assignment
  const colEnds: number[] = [];
  const assigned: Array<{ item: (typeof items)[0]; col: number }> = [];
  for (const item of items) {
    let col = colEnds.findIndex((end) => end <= item.s);
    if (col === -1) {
      col = colEnds.length;
      colEnds.push(item.e);
    } else {
      colEnds[col] = item.e;
    }
    assigned.push({ item, col });
  }

  // Cluster col counts: for each appt, max concurrent in its overlap set
  return assigned.map(({ item, col }) => {
    const overlapping = assigned.filter(
      (o) => o.item.s < item.e && o.item.e > item.s,
    );
    const colCount = Math.max(1, ...overlapping.map((o) => o.col + 1));
    return {
      appt: item.appt,
      topPct: ((item.s - axisStart) / span) * 100,
      heightPct: Math.max(2.5, ((item.e - item.s) / span) * 100),
      colIndex: col,
      colCount: Math.min(colCount, 3), // UI shows max 3 + overflow
    };
  });
}

/** Cap concurrent columns at 3; extras go to overflow list. */
export function visibleAndOverflow(
  laid: LaidOutAppt[],
): { visible: LaidOutAppt[]; overflow: AppointmentSummary[] } {
  const vis: LaidOutAppt[] = [];
  const over: AppointmentSummary[] = [];
  for (const item of laid) {
    if (item.colIndex >= 3) over.push(item.appt);
    else vis.push({ ...item, colCount: Math.min(item.colCount, 3) });
  }
  return { visible: vis, overflow: over };
}

export function nowLinePct(axisStart: number, axisEnd: number, now = new Date()): number | null {
  const mins = now.getHours() * 60 + now.getMinutes();
  if (mins < axisStart || mins > axisEnd) return null;
  return ((mins - axisStart) / (axisEnd - axisStart)) * 100;
}

export function parseAnchor(dateStr: string | null): Date {
  if (dateStr) {
    const d = parseDateInput(dateStr);
    if (d) return d;
  }
  return startOfToday();
}
