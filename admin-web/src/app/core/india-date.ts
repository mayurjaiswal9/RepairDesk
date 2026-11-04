/** Calendar dates for the shop. The API treats these as India dates. */

export function indiaToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
}

export function addIndiaDays(date: string, days: number): string {
  const parsed = new Date(`${date}T00:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}

/** `2026-11-04` → `4 Nov 2026`. */
export function formatIndiaDate(iso: string): string {
  const parsed = new Date(`${iso}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return iso;
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'UTC',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(parsed);
}

export const OPEN_JOB_STATUSES = 'CREATED,ASSIGNED,SCHEDULED,IN_PROGRESS,AWAITING_PARTS';
