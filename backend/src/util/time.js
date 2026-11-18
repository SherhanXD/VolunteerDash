// Time-zone helpers built only on Intl (works for any IANA zone, DST-aware).

const fmtCache = new Map();
function fmt(tz) {
  let f = fmtCache.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: tz,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      weekday: 'short',
    });
    fmtCache.set(tz, f);
  }
  return f;
}

const WD = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };

/** Local wall-clock parts of an instant in tz. weekday: 0=Sun..6=Sat */
export function zonedParts(date, tz) {
  const d = date instanceof Date ? date : new Date(date);
  const parts = {};
  for (const p of fmt(tz).formatToParts(d)) parts[p.type] = p.value;
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour) % 24,
    minute: Number(parts.minute),
    second: Number(parts.second),
    weekday: WD[parts.weekday],
  };
}

/** Offset (minutes) of tz from UTC at the given instant. */
export function tzOffsetMinutes(date, tz) {
  const p = zonedParts(date, tz);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  const ms = date.getTime() - (date.getTime() % 1000);
  return Math.round((asUtc - ms) / 60000);
}

/** Convert local wall-clock time in tz to a UTC Date. Day overflow (e.g. day 32) is normalized. */
export function zonedToUtc(year, month, day, hour = 0, minute = 0, tz) {
  const guess = Date.UTC(year, month - 1, day, hour, minute);
  let off = tzOffsetMinutes(new Date(guess), tz);
  let t = guess - off * 60000;
  const off2 = tzOffsetMinutes(new Date(t), tz);
  if (off2 !== off) t = guess - off2 * 60000;
  return new Date(t);
}

/** 'YYYY-MM-DD' local date string of instant in tz. */
export function localDateStr(date, tz) {
  const p = zonedParts(date, tz);
  return `${p.year}-${String(p.month).padStart(2, '0')}-${String(p.day).padStart(2, '0')}`;
}

export function parseDateStr(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || '');
  if (!m) return null;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  const dt = new Date(Date.UTC(y, mo - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== mo - 1 || dt.getUTCDate() !== d) return null;
  return { year: y, month: mo, day: d };
}

/** Add days to a YYYY-MM-DD string (calendar arithmetic, tz-independent). */
export function addDaysStr(s, n) {
  const p = parseDateStr(s);
  const dt = new Date(Date.UTC(p.year, p.month - 1, p.day + n));
  return dt.toISOString().slice(0, 10);
}

export function weekdayOfDateStr(s) {
  const p = parseDateStr(s);
  return new Date(Date.UTC(p.year, p.month - 1, p.day)).getUTCDay();
}

/** UTC instant of local midnight starting the given local date. */
export function startOfLocalDay(dateStr, tz) {
  const p = parseDateStr(dateStr);
  return zonedToUtc(p.year, p.month, p.day, 0, 0, tz);
}

/** Week (Monday 00:00 local → next Monday 00:00 local) containing the instant. */
export function weekBounds(date, tz) {
  const today = localDateStr(date, tz);
  const wd = weekdayOfDateStr(today);
  const back = (wd + 6) % 7; // days since Monday
  const mondayStr = addDaysStr(today, -back);
  return weekBoundsFromMonday(mondayStr, tz);
}

export function weekBoundsFromMonday(mondayStr, tz) {
  const nextStr = addDaysStr(mondayStr, 7);
  return {
    startDate: mondayStr,
    endDate: nextStr,
    start: startOfLocalDay(mondayStr, tz),
    end: startOfLocalDay(nextStr, tz),
  };
}

/** Week containing a local date string. */
export function weekBoundsForDateStr(dateStr, tz) {
  const wd = weekdayOfDateStr(dateStr);
  return weekBoundsFromMonday(addDaysStr(dateStr, -((wd + 6) % 7)), tz);
}

/** Minutes after local midnight for instant in tz. */
export function localMinuteOfDay(date, tz) {
  const p = zonedParts(date, tz);
  return p.hour * 60 + p.minute;
}

export const iso = (d) => (d instanceof Date ? d : new Date(d)).toISOString();
export const addMinutes = (d, m) => new Date((d instanceof Date ? d : new Date(d)).getTime() + m * 60000);
