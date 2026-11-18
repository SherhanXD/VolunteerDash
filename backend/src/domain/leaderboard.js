import { weekBounds, weekBoundsForDateStr, addDaysStr, parseDateStr } from '../util/time.js';
import { E } from '../util/errors.js';

export function resolveWeek(ctx, week, tz) {
  const now = ctx.now();
  if (!week || week === 'current') return weekBounds(now, tz);
  if (week === 'previous') {
    const cur = weekBounds(now, tz);
    return weekBoundsForDateStr(addDaysStr(cur.startDate, -7), tz);
  }
  if (parseDateStr(week)) return weekBoundsForDateStr(week, tz);
  throw E.validation({ week: 'week must be current, previous, or YYYY-MM-DD.' });
}

/** Per-user totals of confirmed (completed, minutes > 0) shifts with slot start in [start, end). */
export function weekTotals(ctx, cityId, bounds) {
  return ctx.q
    .all(
      `SELECT u.id AS user_id, u.display_name, u.leaderboard_opt_in, u.deleted_at,
              SUM(b.confirmed_minutes) AS minutes, COUNT(*) AS shifts
         FROM bookings b
         JOIN slots s ON s.id = b.slot_id
         JOIN opportunities o ON o.id = s.opportunity_id
         JOIN users u ON u.id = b.user_id
        WHERE b.status = 'completed' AND b.confirmed_minutes > 0
          AND o.city_id = ? AND s.starts_at >= ? AND s.starts_at < ?
        GROUP BY u.id`,
      cityId,
      bounds.start.toISOString(),
      bounds.end.toISOString(),
    )
    .map((r) => ({ ...r }));
}

/** Ranked list: opted-in, non-deleted users; minutes desc, shifts desc, displayName asc, id asc. */
export function rankedLeaderboard(ctx, cityId, bounds) {
  const rows = weekTotals(ctx, cityId, bounds).filter((r) => r.leaderboard_opt_in && !r.deleted_at);
  rows.sort(
    (a, b) =>
      b.minutes - a.minutes ||
      b.shifts - a.shifts ||
      (a.display_name < b.display_name ? -1 : a.display_name > b.display_name ? 1 : 0) ||
      (a.user_id < b.user_id ? -1 : a.user_id > b.user_id ? 1 : 0),
  );
  return rows.map((r, i) => ({ rank: i + 1, userId: r.user_id, minutes: r.minutes, shifts: r.shifts }));
}

export function myWeek(ctx, cityId, bounds, userRow) {
  const all = weekTotals(ctx, cityId, bounds);
  const mine = all.find((r) => r.user_id === userRow.id);
  const ranked = rankedLeaderboard(ctx, cityId, bounds);
  const me = ranked.find((r) => r.userId === userRow.id);
  return {
    rank: me ? me.rank : null,
    minutes: mine ? mine.minutes : 0,
    shifts: mine ? mine.shifts : 0,
    optedIn: !!userRow.leaderboard_opt_in,
  };
}
