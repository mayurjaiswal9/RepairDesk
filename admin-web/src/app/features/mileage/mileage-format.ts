import { TripResponse } from '../../core/api/mileage.api';

const KM = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const RUPEES = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const MINUS = '\u2212';

export const TRIP_FLAG_LABELS: Record<string, string> = {
  LONGER_THAN_ROUTE: 'Longer than route',
  SHORTER_THAN_ROUTE: 'Shorter than route',
  MOCKED: 'Mock location',
  GAPS: 'GPS gaps (estimated)',
  FAR_FROM_JOB: 'Ended far from job',
  NO_POINTS: 'No GPS points',
};

export const NO_TRIP_REASON_LABELS: Record<string, string> = {
  AT_SHOP: 'Customer came to the shop',
  ALREADY_ON_SITE: 'Already at this site',
  PHONE_PROBLEM: 'Phone or GPS problem',
  OTHER: 'Other',
};

/** Trip colours on maps and timelines; the n-th trip uses the n-th colour. */
export const TRIP_COLORS = ['#1552cc', '#c2185b', '#2e7d32', '#ef6c00', '#6a1b9a', '#00838f', '#6d4c41', '#37474f'];
export const CANCELLED_TRIP_COLOR = '#9aa5b4';

export function tripColor(index: number, status?: string | null): string {
  if (status === 'CANCELLED') return CANCELLED_TRIP_COLOR;
  return TRIP_COLORS[index % TRIP_COLORS.length];
}

export function formatKm(metres: number | null | undefined): string {
  if (metres == null || !Number.isFinite(metres)) return '—';
  return KM.format(metres / 1000);
}

export function formatSignedKm(metres: number | null | undefined): string {
  if (metres == null || !Number.isFinite(metres)) return '—';
  const text = KM.format(Math.abs(metres) / 1000);
  if (Math.round(Math.abs(metres) / 100) === 0) return text;
  return metres < 0 ? `${MINUS}${text}` : `+${text}`;
}

export function formatRupees(paise: number | null | undefined): string {
  if (paise == null || !Number.isFinite(paise)) return '—';
  return RUPEES.format(paise / 100);
}

export function formatRate(ratePaise: number | null | undefined, trips = 1): string {
  if (ratePaise == null) return trips > 0 ? 'Mixed' : '—';
  return `${RUPEES.format(ratePaise / 100)}/km`;
}

export function vehicleLabel(vehicle: string | null | undefined): string {
  if (vehicle === 'TWO_WHEELER') return 'Two-wheeler';
  if (vehicle === 'FOUR_WHEELER') return 'Four-wheeler';
  return '—';
}

export function flagLabel(flag: string): string {
  return TRIP_FLAG_LABELS[flag] ?? flag.replaceAll('_', ' ').toLowerCase();
}

export function startKindLabel(trip: Pick<TripResponse, 'startKind' | 'startJobReference'>): string {
  switch (trip.startKind) {
    case 'SHOP':
      return 'From shop';
    case 'PREVIOUS_JOB':
      return trip.startJobReference ? `From previous job ${trip.startJobReference}` : 'From previous job';
    case 'OTHER':
      return 'From another place';
    default:
      return 'Unknown start';
  }
}

export function noTripReasonLabel(code: string | null | undefined, text: string | null | undefined): string {
  if (code === 'OTHER') return text?.trim() || 'Other';
  if (!code) return text?.trim() || 'No reason given';
  return NO_TRIP_REASON_LABELS[code] ?? code;
}

export function durationLabel(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return `${hours} h ${String(minutes % 60).padStart(2, '0')} min`;
}

export function indiaClock(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(iso));
}

export function indiaDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(iso));
}

/** `YYYY-MM-DD` → `01 Nov 2026`; `withWeekday` → `Sun 01 Nov 2026`. */
export function dateLabel(date: string | null | undefined, withWeekday = false): string {
  if (!date) return '—';
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return date;
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'UTC',
    weekday: withWeekday ? 'short' : undefined,
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(parsed);
}

/** `YYYY-MM` → `Nov 2026`. */
export function monthLabel(month: string | null | undefined): string {
  if (!month) return '—';
  const parsed = new Date(`${month}-01T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return month;
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', month: 'short', year: 'numeric' }).format(parsed);
}

export function indiaToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}

export function addDays(date: string, days: number): string {
  const parsed = new Date(`${date}T00:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}

export type DatePreset = 'today' | 'yesterday' | 'this-week' | 'last-7' | 'this-month' | 'last-month';

export const DATE_PRESETS: { key: DatePreset; label: string }[] = [
  { key: 'today', label: 'Today' },
  { key: 'yesterday', label: 'Yesterday' },
  { key: 'this-week', label: 'This week' },
  { key: 'last-7', label: 'Last 7 days' },
  { key: 'this-month', label: 'This month' },
  { key: 'last-month', label: 'Last month' },
];

/** Weeks start on Monday. All dates are India dates. */
export function presetRange(preset: DatePreset, today = indiaToday()): { from: string; to: string } {
  switch (preset) {
    case 'yesterday': {
      const day = addDays(today, -1);
      return { from: day, to: day };
    }
    case 'this-week': {
      const weekday = new Date(`${today}T00:00:00Z`).getUTCDay();
      return { from: addDays(today, -((weekday + 6) % 7)), to: today };
    }
    case 'last-7':
      return { from: addDays(today, -6), to: today };
    case 'this-month':
      return { from: `${today.slice(0, 7)}-01`, to: today };
    case 'last-month': {
      const lastDay = addDays(`${today.slice(0, 7)}-01`, -1);
      return { from: `${lastDay.slice(0, 7)}-01`, to: lastDay };
    }
    default:
      return { from: today, to: today };
  }
}

export function monthRange(month: string, today = indiaToday()): { from: string; to: string } {
  const [year, monthNumber] = month.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).toISOString().slice(0, 10);
  return { from: `${month}-01`, to: lastDay > today ? today : lastDay };
}

export function rangeProblem(from: string, to: string): string | null {
  if (!from || !to) return 'Choose a from date and a to date.';
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(start) || Number.isNaN(end)) return 'Choose a from date and a to date.';
  const days = Math.round((end - start) / 86_400_000);
  if (days < 0) return 'From date must be on or before the to date.';
  if (days > 92) return 'Date range must be 93 days or less.';
  return null;
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
