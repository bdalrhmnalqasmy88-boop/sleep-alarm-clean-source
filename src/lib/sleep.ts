export const CYCLE_MINUTES = 90;
export const MIN_CYCLES = 3;
export const MAX_CYCLES = 6;

export type CycleOption = {
  cycles: number;
  bedtime: Date;
  wakeTime: Date;
  durationMin: number;
};

function atSameDate(base: Date, h: number, m: number): Date {
  const d = new Date(base);
  d.setHours(h, m, 0, 0);
  return d;
}

/** Parse "HH:MM" into {h, m}. */
export function parseTime(value: string): { h: number; m: number } {
  const [h, m] = value.split(':').map(Number);
  return { h: h ?? 8, m: m ?? 30 };
}

/** Format a Date as "HH:MM" in 24h. */
export function formatTime(d: Date): string {
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

/** Format a Date as 12h Arabic-friendly label, e.g. "08:30 ص". */
export function formatTimeAr(d: Date): string {
  let h = d.getHours();
  const m = String(d.getMinutes()).padStart(2, '0');
  const suffix = h < 12 ? 'ص' : 'م';
  h = h % 12;
  if (h === 0) h = 12;
  return `${String(h).padStart(2, '0')}:${m} ${suffix}`;
}

/**
 * Given a wake time "HH:MM" and a reference "now", compute the cycle start
 * points by counting backwards from the wake time in 90-minute steps.
 * The wake time is treated as the next occurrence of that clock time after now
 * (if it already passed today, it rolls to tomorrow).
 */
export function computeCycles(wakeTimeValue: string, now: Date): CycleOption[] {
  const { h, m } = parseTime(wakeTimeValue);
  let wake = atSameDate(now, h, m);
  if (wake.getTime() <= now.getTime()) {
    wake = new Date(wake.getTime() + 24 * 60 * 60 * 1000);
  }

  const options: CycleOption[] = [];
  for (let c = MAX_CYCLES; c >= MIN_CYCLES; c--) {
    const bedtime = new Date(wake.getTime() - c * CYCLE_MINUTES * 60 * 1000);
    options.push({
      cycles: c,
      bedtime,
      wakeTime: wake,
      durationMin: c * CYCLE_MINUTES,
    });
  }
  return options;
}

/** The nearest upcoming bedtime option relative to now. */
export function nearestUpcoming(options: CycleOption[], now: Date): CycleOption | null {
  const upcoming = options.filter((o) => o.bedtime.getTime() > now.getTime());
  if (upcoming.length === 0) return null;
  return upcoming.reduce((a, b) =>
    a.bedtime.getTime() - now.getTime() < b.bedtime.getTime() - now.getTime() ? a : b,
  );
}

export function formatDuration(totalMin: number): string {
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h === 0) return `${m} دقيقة`;
  if (m === 0) return `${h} ساعة`;
  return `${h} ساعة و ${m} دقيقة`;
}

export function formatCountdown(ms: number): string {
  if (ms < 0) ms = 0;
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
