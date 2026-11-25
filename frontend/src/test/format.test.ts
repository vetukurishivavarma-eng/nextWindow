import { describe, expect, it } from 'vitest';
import { displayUrl, interval, isSafeExternalUrl, money, relativeTime } from '../utils/format';

describe('format utils', () => {
  it('formats money with and without currency', () => {
    expect(money(12.5, 'USD')).toContain('12.50');
    expect(money(null, 'USD')).toBe('—');
    expect(money(3, null)).toBe('3.00');
    expect(money(3, 'x1')).toBe('3.00');
  });

  it('formats relative times', () => {
    const now = Date.parse('2026-01-01T12:00:00Z');
    expect(relativeTime(null)).toBe('never');
    expect(relativeTime('2026-01-01T11:59:50Z', now)).toBe('just now');
    expect(relativeTime('2026-01-01T11:30:00Z', now)).toMatch(/30 minutes ago/);
    expect(relativeTime('2026-01-01T14:00:00Z', now)).toMatch(/in 2 hours/);
  });

  it('describes intervals and urls', () => {
    expect(interval(15)).toBe('every 15 min');
    expect(interval(60)).toBe('hourly');
    expect(interval(360)).toBe('every 6 hours');
    expect(interval(1440)).toBe('daily');
    expect(displayUrl('https://shop.example.com/p/1?x=2')).toBe('shop.example.com/p/1?x=2');
    expect(displayUrl('demo://headphones')).toBe('demo://headphones');
  });

  it('only treats http(s) links as safe external links', () => {
    expect(isSafeExternalUrl('https://example.com')).toBe(true);
    expect(isSafeExternalUrl('javascript:alert(1)')).toBe(false);
    expect(isSafeExternalUrl('demo://x')).toBe(false);
  });
});
