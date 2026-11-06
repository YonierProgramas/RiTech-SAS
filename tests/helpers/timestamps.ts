import { expect } from '@playwright/test';

/** Compare timezone-free Java LocalDateTime values at MySQL DATETIME(6) precision. */
export function expectMySqlTimestamp(responseDate: string, storedDate: string): void {
  const nanoseconds = (value: string): bigint => {
    const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?$/.exec(value);
    expect(match, 'Fecha LocalDateTime ISO válida').not.toBeNull();
    // UTC is only a common comparison basis, not a timezone conversion of the application value.
    return BigInt(Date.parse(`${match![1]}Z`)) * 1_000_000n + BigInt((match![2] ?? '').padEnd(9, '0'));
  };
  const difference = nanoseconds(storedDate) - nanoseconds(responseDate);
  expect(difference < 0n ? -difference : difference, 'Precisión DATETIME(6): diferencia máxima 1 microsegundo')
    .toBeLessThanOrEqual(1_000n);
}
