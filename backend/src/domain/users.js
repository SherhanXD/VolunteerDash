import { BADGES, BADGE_BY_ID, levelFor } from './catalog.js';
import { mediaUrl } from '../util/http.js';
import { iso } from '../util/time.js';

const TIER_RANK = { gold: 4, special: 3, silver: 2, bronze: 1 };

export function computeMetrics(ctx, userId) {
  const shifts = ctx.q
    .all(
      `SELECT b.confirmed_minutes AS minutes, s.duration_minutes AS duration, o.category AS category,
              COALESCE(b.confirmed_at, s.ends_at) AS at
         FROM bookings b JOIN slots s ON s.id = b.slot_id JOIN opportunities o ON o.id = s.opportunity_id
        WHERE b.user_id = ? AND b.status = 'completed' AND b.confirmed_minutes > 0
        ORDER BY at, b.id`,
      userId,
    )
    .map((r) => ({ ...r }));
  const referrals = ctx.q
    .all(`SELECT created_at AS at FROM referrals WHERE inviter_id = ? AND status = 'qualified' ORDER BY created_at, id`, userId)
    .map((r) => ({ ...r }));
  return { shifts, referrals };
}

/**
 * Recompute badges from source records. Inserts newly earned badges, revokes ones no longer
 * eligible (revoked_at), and clears revoked_at when re-earned. Never duplicates (PK user+badge).
 */
export function recomputeUser(ctx, userId) {
  const user = ctx.q.get('SELECT id, deleted_at FROM users WHERE id = ?', userId);
  if (!user || user.deleted_at) return;
  const now = iso(ctx.now());
  const m = computeMetrics(ctx, userId);
  const existing = new Map(ctx.q.all('SELECT * FROM badges_earned WHERE user_id = ?', userId).map((r) => [r.badge_id, r]));
  for (const b of BADGES) {
    const r = b.check(m);
    const row = existing.get(b.id);
    if (r.earned) {
      if (!row) {
        ctx.q.run('INSERT INTO badges_earned(user_id, badge_id, earned_at) VALUES (?, ?, ?)', userId, b.id, r.at || now);
      } else if (row.revoked_at) {
        ctx.q.run(
          'UPDATE badges_earned SET revoked_at = NULL, earned_at = ?, celebrated_at = NULL WHERE user_id = ? AND badge_id = ?',
          r.at || now,
          userId,
          b.id,
        );
      }
    } else if (row && !row.revoked_at) {
      ctx.q.run('UPDATE badges_earned SET revoked_at = ? WHERE user_id = ? AND badge_id = ?', now, userId, b.id);
    }
  }
}

export function userTotals(ctx, userId) {
  const r = ctx.q.get(
    `SELECT COALESCE(SUM(confirmed_minutes), 0) AS minutes, COUNT(*) AS shifts
       FROM bookings WHERE user_id = ? AND status = 'completed' AND confirmed_minutes > 0`,
    userId,
  );
  return { confirmedMinutes: r.minutes, completedShifts: r.shifts };
}

function badgeObj(def, row, progressText) {
  const o = {
    id: def.id,
    name: def.name,
    description: def.description,
    rule: def.rule,
    tier: def.tier,
    category: def.category,
    earnedAt: row && !row.revoked_at ? row.earned_at : null,
    celebrated: !!(row && !row.revoked_at && row.celebrated_at),
  };
  if (progressText !== undefined) o.progressText = progressText;
  return o;
}

export function earnedBadges(ctx, userId) {
  return ctx.q
    .all('SELECT * FROM badges_earned WHERE user_id = ? AND revoked_at IS NULL ORDER BY earned_at, badge_id', userId)
    .filter((r) => BADGE_BY_ID.has(r.badge_id))
    .map((r) => badgeObj(BADGE_BY_ID.get(r.badge_id), r));
}

export function allBadges(ctx, userId) {
  const m = computeMetrics(ctx, userId);
  const rows = new Map(ctx.q.all('SELECT * FROM badges_earned WHERE user_id = ?', userId).map((r) => [r.badge_id, r]));
  return BADGES.map((b) => badgeObj(b, rows.get(b.id), b.check(m).progressText));
}

export function topBadges(ctx, userId, n = 3) {
  return earnedBadges(ctx, userId)
    .sort((a, b) => TIER_RANK[b.tier] - TIER_RANK[a.tier] || (b.earnedAt < a.earnedAt ? -1 : 1))
    .slice(0, n);
}

export function cityLabel(ctx, cityId) {
  const c = ctx.q.get('SELECT name, state FROM cities WHERE id = ?', cityId);
  return c ? `${c.name}, ${c.state}` : null;
}

export function isHidden(row) {
  return !!row.deleted_at || !row.profile_public;
}

function privateStub(row, full) {
  const o = {
    id: row.id,
    displayName: row.deleted_at ? 'Deleted volunteer' : 'Private volunteer',
    avatarUrl: null,
    isPrivate: true,
  };
  if (full) {
    Object.assign(o, {
      city: null,
      interests: [],
      bio: null,
      isDemo: !!row.is_demo,
      confirmedMinutes: null,
      level: null,
      badges: [],
      completedShifts: null,
    });
  } else {
    Object.assign(o, { level: null, topBadges: [] });
  }
  return o;
}

/** Compact user (id, displayName, avatarUrl, level, topBadges). forceVisible used by opted-in leaderboard rows. */
export function compactUser(ctx, base, row, { viewerId = null, forceVisible = false } = {}) {
  if (!row) return null;
  const self = viewerId && viewerId === row.id;
  if (!self && (row.deleted_at || (!row.profile_public && !forceVisible))) return privateStub(row, false);
  const t = userTotals(ctx, row.id);
  return {
    id: row.id,
    displayName: row.display_name,
    avatarUrl: mediaUrl(base, row.avatar_key),
    level: levelFor(t.confirmedMinutes),
    topBadges: topBadges(ctx, row.id),
    isPrivate: !row.profile_public,
  };
}

export function publicUser(ctx, base, row, { viewerId = null } = {}) {
  const self = viewerId && viewerId === row.id;
  if (!self && isHidden(row)) return privateStub(row, true);
  const t = userTotals(ctx, row.id);
  return {
    id: row.id,
    displayName: row.display_name,
    avatarUrl: mediaUrl(base, row.avatar_key),
    city: cityLabel(ctx, row.city_id),
    interests: JSON.parse(row.interests || '[]'),
    bio: row.bio || '',
    isDemo: !!row.is_demo,
    confirmedMinutes: t.confirmedMinutes,
    level: levelFor(t.confirmedMinutes),
    badges: earnedBadges(ctx, row.id),
    completedShifts: t.completedShifts,
    isPrivate: !row.profile_public,
  };
}

export function meUser(ctx, base, row) {
  const pub = publicUser(ctx, base, row, { viewerId: row.id });
  const roles = ctx.q
    .all(
      `SELECT m.org_id AS orgId, o.name AS orgName, m.role AS role FROM org_members m
         JOIN organizations o ON o.id = m.org_id WHERE m.user_id = ? ORDER BY o.name`,
      row.id,
    )
    .map((r) => ({ ...r }));
  return {
    ...pub,
    email: row.email,
    cityId: row.city_id,
    inviteCode: row.invite_code,
    inviteUrl: row.invite_code ? `${base}/i/${encodeURIComponent(row.invite_code)}` : null,
    privacy: {
      profilePublic: !!row.profile_public,
      showAttendance: !!row.show_attendance,
      leaderboardOptIn: !!row.leaderboard_opt_in,
    },
    roles,
    allBadges: allBadges(ctx, row.id),
  };
}

export function getUserRow(ctx, id) {
  return ctx.q.get('SELECT * FROM users WHERE id = ?', id);
}

/**
 * Teammates of a user: other users who shared a slot where both bookings are `completed`.
 * Returns Map(userId -> {sharedShifts, lastSharedTitle, lastSharedAt}).
 */
export function teammateMap(ctx, userId) {
  const rows = ctx.q.all(
    `SELECT b2.user_id AS uid, s.starts_at AS at, o.title AS title
       FROM bookings b1
       JOIN bookings b2 ON b2.slot_id = b1.slot_id AND b2.user_id <> b1.user_id AND b2.status = 'completed'
       JOIN slots s ON s.id = b1.slot_id JOIN opportunities o ON o.id = s.opportunity_id
      WHERE b1.user_id = ? AND b1.status = 'completed'
      ORDER BY s.starts_at`,
    userId,
  );
  const map = new Map();
  for (const r of rows) {
    const cur = map.get(r.uid) || { sharedShifts: 0, lastSharedTitle: null, lastSharedAt: null };
    cur.sharedShifts += 1;
    cur.lastSharedTitle = r.title;
    cur.lastSharedAt = r.at;
    map.set(r.uid, cur);
  }
  return map;
}

/** Whether a user's attendance may be shown to other volunteers. */
export const attendanceVisible = (row) => !row.deleted_at && !!row.profile_public && !!row.show_attendance;

export function attendeeObj(ctx, base, row, tm) {
  const t = tm || null;
  return {
    user: compactUser(ctx, base, row),
    isFamiliar: !!t,
    sharedShifts: t ? t.sharedShifts : 0,
    lastSharedTitle: t ? t.lastSharedTitle : null,
    lastSharedAt: t ? t.lastSharedAt : null,
  };
}
