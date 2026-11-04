import {
  formatKm,
  formatRate,
  formatRupees,
  formatSignedKm,
  monthRange,
  noTripReasonLabel,
  presetRange,
  rangeProblem,
  startKindLabel,
} from './mileage-format';

describe('mileage formatting', () => {
  it('shows km with one decimal and paise as rupees with Indian grouping', () => {
    expect(formatKm(36700)).toBe('36.7');
    expect(formatSignedKm(-1500)).toBe('\u22121.5');
    expect(formatSignedKm(1200)).toBe('+1.2');
    expect(formatSignedKm(0)).toBe('0.0');
    expect(formatRupees(218400)).toBe('₹2,184.00');
    expect(formatRupees(12345678)).toBe('₹1,23,456.78');
    expect(formatRate(500)).toBe('₹5.00/km');
    expect(formatRate(null, 3)).toBe('Mixed');
  });

  it('labels start kinds and no-trip reasons', () => {
    expect(startKindLabel({ startKind: 'SHOP', startJobReference: null })).toBe('From shop');
    expect(startKindLabel({ startKind: 'PREVIOUS_JOB', startJobReference: 'RD-1' })).toBe('From previous job RD-1');
    expect(startKindLabel({ startKind: 'UNKNOWN', startJobReference: null })).toBe('Unknown start');
    expect(noTripReasonLabel('AT_SHOP', null)).toBe('Customer came to the shop');
    expect(noTripReasonLabel('OTHER', 'Bike broke down')).toBe('Bike broke down');
  });
});

describe('mileage date presets', () => {
  const today = '2026-11-04'; // a Wednesday

  it('computes India-date ranges from a fixed today', () => {
    expect(presetRange('today', today)).toEqual({ from: today, to: today });
    expect(presetRange('yesterday', today)).toEqual({ from: '2026-11-03', to: '2026-11-03' });
    expect(presetRange('this-week', today)).toEqual({ from: '2026-11-02', to: today });
    expect(presetRange('last-7', today)).toEqual({ from: '2026-10-29', to: today });
    expect(presetRange('this-month', today)).toEqual({ from: '2026-11-01', to: today });
    expect(presetRange('last-month', today)).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(presetRange('this-week', '2026-11-01')).toEqual({ from: '2026-10-26', to: '2026-11-01' });
  });

  it('clamps a month to today and limits ranges to 93 days', () => {
    expect(monthRange('2026-02', today)).toEqual({ from: '2026-02-01', to: '2026-02-28' });
    expect(monthRange('2026-11', today)).toEqual({ from: '2026-11-01', to: today });
    expect(rangeProblem('2026-08-04', '2026-11-04')).toBeNull();
    expect(rangeProblem('2026-08-03', '2026-11-04')).toContain('93 days');
    expect(rangeProblem('2026-11-05', '2026-11-04')).toContain('on or before');
  });
});
