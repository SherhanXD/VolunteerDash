import { E } from '../util/errors.js';
import { baseUrlFor, parseLimit } from '../util/http.js';
import { resolveWeek, rankedLeaderboard, myWeek } from '../domain/leaderboard.js';
import { compactUser, publicUser, getUserRow, teammateMap, isHidden } from '../domain/users.js';

export default async function communityRoutes(app) {
  const ctx = app.ctx;
  const base = (req) => baseUrlFor(req, ctx.config);

  app.get('/leaderboard', async (req) => {
    const viewer = ctx.auth(req, { optional: true });
    const cityId = req.query.city || (viewer ? viewer.city_id : 'tempe-az');
    const city = ctx.q.get('SELECT * FROM cities WHERE id = ?', cityId);
    if (!city) throw E.validation({ city: 'Unknown city.' });
    const bounds = resolveWeek(ctx, req.query.week, city.time_zone);
    const limit = req.query.limit !== undefined ? parseLimit(req.query.limit) : 10;
    const ranked = rankedLeaderboard(ctx, city.id, bounds).slice(0, limit);
    const items = ranked.map((r) => ({
      rank: r.rank,
      user: compactUser(ctx, base(req), getUserRow(ctx, r.userId), { viewerId: viewer?.id, forceVisible: true }),
      minutes: r.minutes,
      shifts: r.shifts,
    }));
    return {
      weekStart: bounds.start.toISOString(),
      weekEnd: bounds.end.toISOString(),
      timeZone: city.time_zone,
      items,
      me: viewer ? myWeek(ctx, city.id, bounds, viewer) : null,
    };
  });

  app.get('/users/:id', async (req) => {
    const viewer = ctx.auth(req);
    const row = getUserRow(ctx, req.params.id);
    if (!row) throw E.notFound('User');
    const self = row.id === viewer.id;
    const pub = publicUser(ctx, base(req), row, { viewerId: viewer.id });
    if (!self && isHidden(row)) return { ...pub, recentActivity: [], sharedShiftsWithMe: 0 };
    let recentActivity = [];
    if (self || row.show_attendance) {
      recentActivity = ctx.q
        .all(
          `SELECT COALESCE(b.title_snapshot, o.title) AS title, o.category, s.starts_at AS date, b.confirmed_minutes AS minutes
             FROM bookings b JOIN slots s ON s.id = b.slot_id JOIN opportunities o ON o.id = s.opportunity_id
            WHERE b.user_id = ? AND b.status = 'completed' AND b.confirmed_minutes > 0
            ORDER BY s.starts_at DESC LIMIT 10`,
          row.id,
        )
        .map((r) => ({ ...r }));
    }
    const shared = self ? 0 : teammateMap(ctx, viewer.id).get(row.id)?.sharedShifts || 0;
    return { ...pub, recentActivity, sharedShiftsWithMe: shared };
  });
}
