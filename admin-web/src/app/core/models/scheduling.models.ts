export interface WorkingHoursDay {
  dayOfWeek: number; // 0 = Monday … 6 = Sunday
  isClosed: boolean;
  openTime?: string | null;
  closeTime?: string | null;
}

export interface SchedulingConfig {
  slotDurationMinutes: number;
  bookingHorizonDays: number;
  workingHours: WorkingHoursDay[];
}

export interface CreateAppointmentBody {
  technicianId: string;
  scheduledDate: string; // YYYY-MM-DD
  startTime: string; // HH:mm
  notes?: string;
}

export interface RescheduleAppointmentBody {
  scheduledDate: string;
  startTime: string;
  notes?: string;
}

/** JS getDay() Sunday=0 → shop Monday=0. */
export function shopDayOfWeek(date: Date): number {
  return (date.getDay() + 6) % 7;
}

export function parseTimeToMinutes(time: string | null | undefined): number | null {
  if (!time) return null;
  const [h, m] = time.split(':').map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
}

export function minutesToTime(total: number): string {
  const h = Math.floor(total / 60);
  const m = total % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function formatTimeLabel(time: string): string {
  const mins = parseTimeToMinutes(time);
  if (mins == null) return time;
  const d = new Date();
  d.setHours(Math.floor(mins / 60), mins % 60, 0, 0);
  return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

/**
 * Shop weekday labels — index is dayOfWeek where **0 = Monday … 6 = Sunday**.
 * Exactly seven entries; never use Date.getDay() / Intl for these labels (Bugs #14).
 */
export const WEEKDAY_FULL: readonly string[] = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
] as const;

export function weekdayFull(dayOfWeek: number): string {
  return WEEKDAY_FULL[dayOfWeek] ?? '';
}

export function weekdayShort(dayOfWeek: number): string {
  return ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][dayOfWeek] ?? '';
}

/** Normalize API LocalTime (`HH:mm:ss` or `HH:mm`) to `HH:mm` for `<input type="time">`. */
export function normalizeTimeValue(time: string | null | undefined): string {
  if (!time) return '';
  const m = /^(\d{1,2}):(\d{2})/.exec(time);
  if (!m) return '';
  return `${m[1].padStart(2, '0')}:${m[2]}`;
}

/**
 * Build exactly seven working-hours rows (Mon–Sun), filling missing days as closed.
 * Labels/order always come from dayOfWeek, never response array position.
 */
export function normalizeWorkingHoursWeek(
  hours: WorkingHoursDay[] | null | undefined,
): WorkingHoursDay[] {
  const byDay = new Map((hours ?? []).map((h) => [h.dayOfWeek, h]));
  return WEEKDAY_FULL.map((_, dayOfWeek) => {
    const existing = byDay.get(dayOfWeek);
    if (!existing) {
      return { dayOfWeek, isClosed: true, openTime: null, closeTime: null };
    }
    return {
      dayOfWeek,
      isClosed: !!existing.isClosed,
      openTime: existing.openTime ?? null,
      closeTime: existing.closeTime ?? null,
    };
  });
}

export function toDateInputValue(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function parseDateInput(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

export function hoursForDate(
  config: SchedulingConfig,
  date: Date,
): WorkingHoursDay | null {
  const dow = shopDayOfWeek(date);
  return config.workingHours.find((h) => h.dayOfWeek === dow) ?? null;
}

export function slotStartsForDate(
  config: SchedulingConfig,
  dateStr: string,
  now: Date = new Date(),
): string[] {
  const date = parseDateInput(dateStr);
  if (!date) return [];
  const hours = hoursForDate(config, date);
  if (!hours || hours.isClosed) return [];
  const open = parseTimeToMinutes(hours.openTime ?? undefined);
  const close = parseTimeToMinutes(hours.closeTime ?? undefined);
  const step = config.slotDurationMinutes;
  if (open == null || close == null || step <= 0) return [];

  const slots: string[] = [];
  for (let start = open; start + step <= close; start += step) {
    slots.push(minutesToTime(start));
  }

  const todayStr = toDateInputValue(now);
  if (dateStr === todayStr) {
    const nowMins = now.getHours() * 60 + now.getMinutes();
    return slots.filter((s) => (parseTimeToMinutes(s) ?? 0) > nowMins);
  }
  return slots;
}

export function endTimeForStart(startTime: string, slotMinutes: number): string {
  const start = parseTimeToMinutes(startTime);
  if (start == null) return '';
  return minutesToTime(start + slotMinutes);
}

export function timesOverlap(
  aStart: string,
  aEnd: string | null | undefined,
  bStart: string,
  bEnd: string,
): boolean {
  const as = parseTimeToMinutes(aStart);
  const ae = parseTimeToMinutes(aEnd ?? aStart) ?? as;
  const bs = parseTimeToMinutes(bStart);
  const be = parseTimeToMinutes(bEnd);
  if (as == null || ae == null || bs == null || be == null) return false;
  return as < be && ae > bs;
}

export type PrefInvalidReason =
  | 'past'
  | 'closed'
  | 'outside_hours'
  | 'horizon'
  | 'not_slot'
  | null;

export function preferenceValidity(
  config: SchedulingConfig,
  preferredDate: string | null | undefined,
  preferredTime: string | null | undefined,
  now: Date = new Date(),
): PrefInvalidReason {
  if (!preferredDate) return null;
  const date = parseDateInput(preferredDate);
  if (!date) return 'past';
  const today = startOfToday();
  if (date < today) return 'past';
  const max = addDays(today, config.bookingHorizonDays);
  if (date > max) return 'horizon';
  const hours = hoursForDate(config, date);
  if (!hours || hours.isClosed) return 'closed';
  if (!preferredTime) return 'outside_hours';
  const normalized = preferredTime.length >= 5 ? preferredTime.slice(0, 5) : preferredTime;
  const slots = slotStartsForDate(config, preferredDate, now);
  if (!slots.includes(normalized)) {
    const mins = parseTimeToMinutes(normalized);
    const open = parseTimeToMinutes(hours.openTime ?? undefined);
    const close = parseTimeToMinutes(hours.closeTime ?? undefined);
    if (mins == null || open == null || close == null || mins < open || mins >= close) {
      return 'outside_hours';
    }
    return 'not_slot';
  }
  return null;
}

export function prefReasonLabel(reason: PrefInvalidReason): string {
  switch (reason) {
    case 'past':
      return 'Preference is in the past';
    case 'closed':
      return 'Shop is closed that day';
    case 'outside_hours':
      return 'Outside working hours';
    case 'horizon':
      return 'Beyond the booking horizon';
    case 'not_slot':
      return 'Not on a slot boundary';
    default:
      return '';
  }
}
