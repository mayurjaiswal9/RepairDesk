import { WEEKDAY_FULL, normalizeWorkingHoursWeek, weekdayFull } from './scheduling.models';

describe('shop weekday mapping (AP-15 / Bugs #14)', () => {
  it('has exactly seven labels with Monday at index 0 and Sunday at index 6', () => {
    expect(WEEKDAY_FULL.length).toBe(7);
    expect(WEEKDAY_FULL[0]).toBe('Monday');
    expect(WEEKDAY_FULL[6]).toBe('Sunday');
    expect(weekdayFull(0)).toBe('Monday');
    expect(weekdayFull(6)).toBe('Sunday');
  });

  it('normalizes hours by dayOfWeek, not array position, and fills missing days as closed', () => {
    const rows = normalizeWorkingHoursWeek([
      { dayOfWeek: 6, isClosed: false, openTime: '10:00', closeTime: '14:00' },
      { dayOfWeek: 0, isClosed: false, openTime: '09:00', closeTime: '18:00' },
    ]);
    expect(rows.length).toBe(7);
    expect(rows[0].dayOfWeek).toBe(0);
    expect(weekdayFull(rows[0].dayOfWeek)).toBe('Monday');
    expect(rows[0].openTime).toBe('09:00');
    expect(rows[6].dayOfWeek).toBe(6);
    expect(weekdayFull(rows[6].dayOfWeek)).toBe('Sunday');
    expect(rows[6].openTime).toBe('10:00');
    expect(rows[1].isClosed).toBe(true);
  });
});
