import { z } from 'zod';
import { E } from '../util/errors.js';
import { baseUrlFor, parse, paginate } from '../util/http.js';
import { localMinuteOfDay } from '../util/time.js';
import { CATEGORIES, CATEGORY_IDS } from '../domain/catalog.js';
import { SLOT_SELECT, oppObj, upcomingSlotRows, haversineMiles, getSlotRow } from '../domain/opportunities.js';
import { teammateMap, attendanceVisible, attendeeObj } from '../domain/users.js';

const boolStr = z
  .union([z.boolean(), z.enum(['true', 'false', '1', '0', ''])])
  .optional()
  .transform((v) => v === true || v === 'true' || v === '1');
const num = (msg) =>
  z
    .union([z.number(), z.string().trim().min(1)])
    .optional()
    .transform((v, c) => {
      if (v === undefined) return undefined;
      const n = Number(v);
      if (!Number.isFinite(n)) {
        c.addIssue({ code: 'custom', message: msg });
        return z.NEVER;
      }
      return n;
    });
const instant = z
  .string()
  .optional()
  .refine((v) => v === undefined || !Number.isNaN(Date.parse(v)), 'Must be an ISO-8601 instant.');

export const listQuerySchema = z.object({
  city: z.string().optional().default('tempe-az'),
  lat: num('lat must be a number.').refine((v) => v === undefined || (v >= -90 && v <= 90), 'lat out of range.'),
  lng: num('lng must be a number.').refine((v) => v === undefined || (v >= -180 && v <= 180), 'lng out of range.'),
  radiusMi: num('radiusMi must be a number.').refine((v) => v === undefined || v > 0, 'radiusMi must be positive.'),
  q: z.string().max(100).optional(),
  categories: z.string().optional(),
  from: instant,
  to: instant,
  startMinMin: num('startMinMin must be minutes after midnight.').refine((v) => v === undefined || (v >= 0 && v <= 1440), '0..1440'),
  startMaxMin: num('startMaxMin must be minutes after midnight.').refine((v) => v === undefined || (v >= 0 && v <= 1440), '0..1440'),
  maxDuration: num('maxDuration must be a number of minutes.').refine((v) => v === undefined || v > 0, 'Must be positive.'),
  freeOnly: boolStr,
  openOnly: boolStr,
  limit: z.any().optional(),
  cursor: z.string().optional(),
  sort: z.enum(['distance', 'soonest']).optional(),
});

export default async function discoveryRoutes(app) {
  const ctx = app.ctx;
  const base = (req) => baseUrlFor(req, ctx.config);

  const viewerFor = (req) => {
    const user = ctx.auth(req, { optional: true });
    return user ? { userId: user.id, teammates: teammateMap(ctx, user.id), user } : null;
  };

  app.get('/cities', async () => ({
    items: ctx.q.all('SELECT * FROM cities ORDER BY is_active DESC, name').map((c) => ({
      id: c.id,
      name: c.name,
      state: c.state,
      timeZone: c.time_zone,
      lat: c.lat,
      lng: c.lng,
      defaultZoom: c.default_zoom,
    })),
  }));

  app.get('/categories', async () => ({ items: CATEGORIES }));

  app.get('/opportunities', async (req) => {
    const f = parse(listQuerySchema, req.query);
    const city = ctx.q.get('SELECT * FROM cities WHERE id = ?', f.city);
    if (!city) throw E.validation({ city: 'Unknown city.' });
    const hasGeo = f.lat !== undefined && f.lng !== undefined;
    if ((f.lat === undefined) !== (f.lng === undefined)) throw E.validation({ lat: 'lat and lng must be given together.' });
    let cats = null;
    if (f.categories) {
      cats = f.categories.split(',').map((s) => s.trim()).filter(Boolean);
      const bad = cats.find((c) => !CATEGORY_IDS.includes(c));
      if (bad) throw E.validation({ categories: `Unknown category: ${bad}` });
    }
    const viewer = viewerFor(req);
    const now = ctx.now();
    const fromIso = new Date(Math.max(now.getTime(), f.from ? Date.parse(f.from) : 0)).toISOString();
    const horizon = new Date(now.getTime() + 60 * 86400000).toISOString();
    const toIso = f.to ? new Date(Math.min(Date.parse(f.to), Date.parse(horizon))).toISOString() : horizon;

    const opps = ctx.q.all(`SELECT * FROM opportunities WHERE city_id = ? AND status = 'published'`, city.id);
    const slotRows = ctx.q.all(
      `${SLOT_SELECT} JOIN opportunities o ON o.id = s.opportunity_id
        WHERE o.city_id = ? AND o.status = 'published' AND s.status = 'open'
          AND s.starts_at > ? AND s.starts_at >= ? AND s.starts_at <= ?
        ORDER BY s.starts_at`,
      city.id,
      now.toISOString(),
      fromIso,
      toIso,
    );
    const byOpp = new Map();
    for (const s of slotRows) {
      if (f.maxDuration !== undefined && s.duration_minutes > f.maxDuration) continue;
      if (f.openOnly && s.booked_count >= s.capacity) continue;
      if (f.startMinMin !== undefined || f.startMaxMin !== undefined) {
        const mod = localMinuteOfDay(new Date(s.starts_at), city.time_zone);
        if (f.startMinMin !== undefined && mod < f.startMinMin) continue;
        if (f.startMaxMin !== undefined && mod > f.startMaxMin) continue;
      }
      if (!byOpp.has(s.opportunity_id)) byOpp.set(s.opportunity_id, []);
      byOpp.get(s.opportunity_id).push(s);
    }

    const qText = f.q ? f.q.trim().toLowerCase() : '';
    const orgNames = new Map(ctx.q.all('SELECT id, name FROM organizations').map((o) => [o.id, o.name]));
    const catNames = new Map(CATEGORIES.map((c) => [c.id, c.name]));
    let results = [];
    for (const o of opps) {
      const slots = byOpp.get(o.id);
      if (!slots || !slots.length) continue;
      if (cats && !cats.includes(o.category)) continue;
      if (f.freeOnly && o.cost_cents > 0) continue;
      if (qText) {
        const hay = [o.title, o.summary, o.description, orgNames.get(o.org_id), o.category, catNames.get(o.category), o.tags]
          .join(' ')
          .toLowerCase();
        if (!qText.split(/\s+/).every((w) => hay.includes(w))) continue;
      }
      const distance = hasGeo ? haversineMiles(f.lat, f.lng, o.lat, o.lng) : null;
      if (hasGeo && f.radiusMi !== undefined && distance > f.radiusMi) continue;
      results.push({ o, slots, distance, first: slots[0].starts_at });
    }
    const sort = f.sort || (hasGeo ? 'distance' : 'soonest');
    results.sort((a, b) => {
      if (sort === 'distance' && hasGeo && a.distance !== b.distance) return a.distance - b.distance;
      if (a.first !== b.first) return a.first < b.first ? -1 : 1;
      return a.o.title.localeCompare(b.o.title);
    });
    const page = paginate(results, req.query);
    return {
      items: page.items.map((r) =>
        oppObj(ctx, base(req), r.o, {
          viewer,
          slots: r.slots.slice(0, 4),
          distanceMiles: r.distance === null ? null : Math.round(r.distance * 10) / 10,
        }),
      ),
      nextCursor: page.nextCursor,
    };
  });

  app.get('/opportunities/:id', async (req) => {
    const o = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', req.params.id);
    const viewer = viewerFor(req);
    if (!o) throw E.notFound('Opportunity');
    if (o.status === 'draft') {
      const member = viewer && ctx.q.get('SELECT 1 FROM org_members WHERE user_id = ? AND org_id = ?', viewer.userId, o.org_id);
      if (!member) throw E.notFound('Opportunity');
    }
    let distance = null;
    const lat = Number(req.query.lat);
    const lng = Number(req.query.lng);
    if (req.query.lat !== undefined && Number.isFinite(lat) && Number.isFinite(lng)) {
      distance = Math.round(haversineMiles(lat, lng, o.lat, o.lng) * 10) / 10;
    }
    return oppObj(ctx, base(req), o, { viewer, slots: upcomingSlotRows(ctx, o.id), distanceMiles: distance });
  });

  app.get('/recommendations', async (req) => {
    const viewer = viewerFor(req);
    const lat = req.query.lat !== undefined ? Number(req.query.lat) : NaN;
    const lng = req.query.lng !== undefined ? Number(req.query.lng) : NaN;
    const hasGeo = Number.isFinite(lat) && Number.isFinite(lng);
    const cityId = viewer ? viewer.user.city_id : req.query.city || 'tempe-az';
    const interests = viewer ? JSON.parse(viewer.user.interests || '[]') : [];
    const now = ctx.now();
    const until = new Date(now.getTime() + 30 * 86400000).toISOString();
    const slots = ctx.q.all(
      `${SLOT_SELECT} JOIN opportunities o ON o.id = s.opportunity_id
        WHERE o.city_id = ? AND o.status = 'published' AND s.status = 'open' AND s.starts_at > ? AND s.starts_at <= ?
        ORDER BY s.starts_at`,
      cityId,
      now.toISOString(),
      until,
    );
    const booked = new Set();
    if (viewer) {
      for (const r of ctx.q.all(
        `SELECT DISTINCT s.opportunity_id FROM bookings b JOIN slots s ON s.id = b.slot_id
          WHERE b.user_id = ? AND b.status = 'booked' AND s.starts_at > ?`,
        viewer.userId,
        now.toISOString(),
      ))
        booked.add(r.opportunity_id);
    }
    const byOpp = new Map();
    for (const s of slots) {
      if (s.booked_count >= s.capacity || booked.has(s.opportunity_id)) continue;
      if (!byOpp.has(s.opportunity_id)) byOpp.set(s.opportunity_id, []);
      byOpp.get(s.opportunity_id).push(s);
    }
    const familiarOn = (s) => {
      if (!viewer || !viewer.teammates.size) return 0;
      return ctx.q
        .all(
          `SELECT u.* FROM bookings b JOIN users u ON u.id = b.user_id WHERE b.slot_id = ? AND b.status IN ('booked','completed')`,
          s.id,
        )
        .filter((u) => viewer.teammates.has(u.id) && attendanceVisible(u)).length;
    };
    const scored = [];
    for (const [oppId, list] of byOpp) {
      const o = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', oppId);
      let score = 0;
      if (interests.includes(o.category)) score += 3;
      const shortest = Math.min(...list.map((s) => s.duration_minutes));
      if (shortest <= 15) score += 2.5;
      else if (shortest <= 30) score += 2;
      const familiar = Math.max(0, ...list.slice(0, 8).map(familiarOn));
      score += Math.min(familiar, 3) * 1.5;
      const hoursUntil = (Date.parse(list[0].starts_at) - now.getTime()) / 3600000;
      score += 2 * Math.max(0, 1 - hoursUntil / 168);
      let distance = null;
      if (hasGeo) {
        distance = haversineMiles(lat, lng, o.lat, o.lng);
        score += 1.5 * Math.max(0, 1 - distance / 10);
      }
      scored.push({ o, list, score, distance });
    }
    scored.sort((a, b) => b.score - a.score || a.o.title.localeCompare(b.o.title));
    return {
      items: scored.slice(0, 10).map((r) =>
        oppObj(ctx, base(req), r.o, {
          viewer,
          slots: r.list.slice(0, 4),
          distanceMiles: r.distance === null ? null : Math.round(r.distance * 10) / 10,
        }),
      ),
    };
  });

  app.get('/slots/:id/attendees', async (req) => {
    const user = ctx.auth(req);
    const s = getSlotRow(ctx, req.params.id);
    if (!s) throw E.notFound('Slot');
    const tm = teammateMap(ctx, user.id);
    const rows = ctx.q.all(
      `SELECT u.* FROM bookings b JOIN users u ON u.id = b.user_id
        WHERE b.slot_id = ? AND b.status IN ('booked','completed') AND b.user_id <> ?
        ORDER BY b.created_at`,
      s.id,
      user.id,
    );
    const familiar = [];
    const others = [];
    let hidden = 0;
    for (const r of rows) {
      if (!attendanceVisible(r)) {
        hidden++;
        continue;
      }
      const t = tm.get(r.id);
      (t ? familiar : others).push(attendeeObj(ctx, base(req), r, t));
    }
    familiar.sort((a, b) => b.sharedShifts - a.sharedShifts);
    return { familiar, others, hiddenCount: hidden, total: rows.length };
  });
}
