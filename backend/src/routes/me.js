import { z } from 'zod';
import { E, ApiError } from '../util/errors.js';
import { baseUrlFor, parse } from '../util/http.js';
import { verifyPassword } from '../util/security.js';
import { saveImageUpload } from '../util/uploads.js';
import { CATEGORY_IDS, levelFor } from '../domain/catalog.js';
import {
  meUser,
  userTotals,
  earnedBadges,
  teammateMap,
  attendanceVisible,
  attendeeObj,
  getUserRow,
} from '../domain/users.js';
import { bookingSummary } from '../domain/opportunities.js';
import { weekBounds } from '../util/time.js';
import { myWeek } from '../domain/leaderboard.js';
import { deleteAccount, exportUserData, removeUploadedFile } from '../domain/accounts.js';
import { BADGE_BY_ID } from '../domain/catalog.js';

export default async function meRoutes(app) {
  const ctx = app.ctx;
  const base = (req) => baseUrlFor(req, ctx.config);

  app.get('/me', async (req) => meUser(ctx, base(req), ctx.auth(req)));

  const patchSchema = z.object({
    displayName: z.string().trim().min(2, 'Display name must be 2-40 characters.').max(40, 'Display name must be 2-40 characters.').optional(),
    bio: z.string().max(280, 'Bio must be 280 characters or fewer.').optional(),
    interests: z
      .array(z.enum(CATEGORY_IDS, { message: `Interests must be among: ${CATEGORY_IDS.join(', ')}.` }))
      .max(8)
      .optional(),
    cityId: z.string().optional(),
    privacy: z
      .object({
        profilePublic: z.boolean().optional(),
        showAttendance: z.boolean().optional(),
        leaderboardOptIn: z.boolean().optional(),
      })
      .optional(),
  });
  app.patch('/me', async (req) => {
    const user = ctx.auth(req);
    const d = parse(patchSchema, req.body);
    if (d.cityId && !ctx.q.get('SELECT 1 FROM cities WHERE id = ?', d.cityId)) throw E.validation({ cityId: 'Unknown city.' });
    const sets = [];
    const vals = [];
    const set = (col, v) => {
      sets.push(`${col} = ?`);
      vals.push(v);
    };
    if (d.displayName !== undefined) set('display_name', d.displayName);
    if (d.bio !== undefined) set('bio', d.bio);
    if (d.interests !== undefined) set('interests', JSON.stringify([...new Set(d.interests)]));
    if (d.cityId !== undefined) set('city_id', d.cityId);
    if (d.privacy) {
      if (d.privacy.profilePublic !== undefined) set('profile_public', d.privacy.profilePublic ? 1 : 0);
      if (d.privacy.showAttendance !== undefined) set('show_attendance', d.privacy.showAttendance ? 1 : 0);
      if (d.privacy.leaderboardOptIn !== undefined) set('leaderboard_opt_in', d.privacy.leaderboardOptIn ? 1 : 0);
    }
    if (sets.length) ctx.q.run(`UPDATE users SET ${sets.join(', ')} WHERE id = ?`, ...vals, user.id);
    return meUser(ctx, base(req), getUserRow(ctx, user.id));
  });

  app.put('/me/avatar', async (req) => {
    const user = ctx.auth(req);
    const key = await saveImageUpload(ctx, req, 'avatars', 3 * 1024 * 1024);
    ctx.q.run('UPDATE users SET avatar_key = ? WHERE id = ?', key, user.id);
    removeUploadedFile(ctx, user.avatar_key);
    return meUser(ctx, base(req), getUserRow(ctx, user.id));
  });

  app.delete('/me/avatar', async (req) => {
    const user = ctx.auth(req);
    ctx.q.run('UPDATE users SET avatar_key = NULL WHERE id = ?', user.id);
    removeUploadedFile(ctx, user.avatar_key);
    return meUser(ctx, base(req), getUserRow(ctx, user.id));
  });

  app.get('/me/stats', async (req) => {
    const user = ctx.auth(req);
    const t = userTotals(ctx, user.id);
    const city = ctx.q.get('SELECT * FROM cities WHERE id = ?', user.city_id);
    const wk = myWeek(ctx, user.city_id, weekBounds(ctx.now(), city.time_zone), user);
    const upcoming = ctx.q.get(
      `SELECT COUNT(*) AS n FROM bookings b JOIN slots s ON s.id = b.slot_id
        WHERE b.user_id = ? AND b.status = 'booked' AND s.starts_at > ?`,
      user.id,
      ctx.now().toISOString(),
    ).n;
    return {
      confirmedMinutes: t.confirmedMinutes,
      completedShifts: t.completedShifts,
      level: levelFor(t.confirmedMinutes),
      weekMinutes: wk.minutes,
      weekRank: wk.rank,
      upcomingCount: upcoming,
      newBadges: earnedBadges(ctx, user.id).filter((b) => !b.celebrated),
    };
  });

  const ackSchema = z.object({ ids: z.array(z.string()).max(100) });
  app.post('/me/badges/ack', async (req, reply) => {
    const user = ctx.auth(req);
    const { ids } = parse(ackSchema, req.body);
    const now = ctx.now().toISOString();
    for (const id of ids) {
      if (!BADGE_BY_ID.has(id)) continue;
      ctx.q.run(
        'UPDATE badges_earned SET celebrated_at = ? WHERE user_id = ? AND badge_id = ? AND celebrated_at IS NULL',
        now,
        user.id,
        id,
      );
    }
    return reply.code(204).send();
  });

  app.get('/me/bookings', async (req) => {
    const user = ctx.auth(req);
    const scope = req.query.scope || 'upcoming';
    if (!['upcoming', 'past', 'all'].includes(scope)) throw E.validation({ scope: 'scope must be upcoming, past, or all.' });
    const now = ctx.now().toISOString();
    let rows;
    if (scope === 'upcoming') {
      // Active bookings whose slot has not ended yet (an in-progress shift stays "upcoming").
      rows = ctx.q.all(
        `SELECT b.* FROM bookings b JOIN slots s ON s.id = b.slot_id
          WHERE b.user_id = ? AND b.status = 'booked' AND s.ends_at > ? ORDER BY s.starts_at ASC`,
        user.id,
        now,
      );
    } else if (scope === 'past') {
      rows = ctx.q.all(
        `SELECT b.* FROM bookings b JOIN slots s ON s.id = b.slot_id
          WHERE b.user_id = ? AND (s.ends_at <= ? OR b.status <> 'booked') ORDER BY s.starts_at DESC`,
        user.id,
        now,
      );
    } else {
      rows = ctx.q.all(`SELECT b.* FROM bookings b JOIN slots s ON s.id = b.slot_id WHERE b.user_id = ? ORDER BY s.starts_at DESC`, user.id);
    }
    const viewer = { userId: user.id, teammates: teammateMap(ctx, user.id) };
    return { items: rows.map((b) => bookingSummary(ctx, base(req), b, viewer)) };
  });

  app.get('/me/teammates', async (req) => {
    const user = ctx.auth(req);
    const tm = teammateMap(ctx, user.id);
    const items = [];
    for (const [uid, t] of tm) {
      const row = getUserRow(ctx, uid);
      if (!row || !attendanceVisible(row)) continue;
      items.push(attendeeObj(ctx, base(req), row, t));
    }
    items.sort(
      (a, b) =>
        b.sharedShifts - a.sharedShifts ||
        (b.lastSharedAt > a.lastSharedAt ? 1 : b.lastSharedAt < a.lastSharedAt ? -1 : 0) ||
        a.user.displayName.localeCompare(b.user.displayName),
    );
    return { items };
  });

  app.get('/me/referrals', async (req) => {
    const user = ctx.auth(req);
    const rows = ctx.q.all(
      `SELECT r.status, r.created_at, u.display_name, u.deleted_at FROM referrals r JOIN users u ON u.id = r.invitee_id
        WHERE r.inviter_id = ? ORDER BY r.created_at DESC`,
      user.id,
    );
    const b = base(req);
    return {
      inviteCode: user.invite_code,
      inviteUrl: user.invite_code ? `${b}/i/${encodeURIComponent(user.invite_code)}` : null,
      qualifiedCount: rows.filter((r) => r.status === 'qualified').length,
      items: rows.map((r) => ({
        displayName: r.deleted_at ? 'Deleted volunteer' : r.display_name,
        joinedAt: r.created_at,
        status: r.status,
      })),
    };
  });

  app.get('/me/export', async (req, reply) => {
    const user = ctx.auth(req);
    reply.header('Content-Disposition', 'attachment; filename="volunteerdash-export.json"');
    return exportUserData(ctx, user.id);
  });

  const deleteSchema = z.object({ password: z.string().optional(), confirm: z.string().optional() });
  app.delete('/me', async (req, reply) => {
    const user = ctx.auth(req);
    const d = parse(deleteSchema, req.body || {});
    if (user.is_demo) {
      if (d.confirm !== 'DELETE') throw E.validation({ confirm: 'Type DELETE to confirm.' });
    } else if (!d.password || !verifyPassword(d.password, user.password_hash)) {
      throw new ApiError(401, 'INVALID_CREDENTIALS', 'Password is incorrect.');
    }
    deleteAccount(ctx, user.id);
    return reply.code(204).send();
  });
}
