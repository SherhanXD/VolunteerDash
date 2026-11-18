import { mediaUrl } from '../util/http.js';
import { attendanceVisible } from './users.js';

export const SLOT_SELECT = `SELECT s.*,
  (SELECT COUNT(*) FROM bookings b WHERE b.slot_id = s.id AND b.status IN ('booked','completed')) AS booked_count
  FROM slots s`;

export function orgObj(ctx, base, o) {
  return {
    id: o.id,
    name: o.name,
    description: o.description,
    logoUrl: mediaUrl(base, o.logo_key),
    website: o.website,
    email: o.email,
    phone: o.phone,
    isDemo: !!o.is_demo,
  };
}

export function slotStatus(ctx, s, oppStatus) {
  if (s.status === 'cancelled' || oppStatus === 'cancelled') return 'cancelled';
  if (s.starts_at <= ctx.now().toISOString()) return 'expired';
  if (s.booked_count >= s.capacity) return 'full';
  return 'open';
}

/**
 * viewer: { userId, teammates: Map } or null.
 */
export function slotObj(ctx, s, viewer, oppStatus) {
  let myBookingId = null;
  let familiarCount = 0;
  if (viewer && viewer.userId) {
    const mine = ctx.q.get(
      `SELECT id FROM bookings WHERE slot_id = ? AND user_id = ? AND status IN ('booked','completed')`,
      s.id,
      viewer.userId,
    );
    myBookingId = mine ? mine.id : null;
    if (viewer.teammates && viewer.teammates.size) {
      const rows = ctx.q.all(
        `SELECT u.id, u.deleted_at, u.profile_public, u.show_attendance FROM bookings b JOIN users u ON u.id = b.user_id
          WHERE b.slot_id = ? AND b.status IN ('booked','completed') AND b.user_id <> ?`,
        s.id,
        viewer.userId,
      );
      familiarCount = rows.filter((r) => viewer.teammates.has(r.id) && attendanceVisible(r)).length;
    }
  }
  if (oppStatus === undefined) {
    const o = ctx.q.get('SELECT status FROM opportunities WHERE id = ?', s.opportunity_id);
    oppStatus = o ? o.status : 'published';
  }
  return {
    id: s.id,
    opportunityId: s.opportunity_id,
    startsAt: s.starts_at,
    endsAt: s.ends_at,
    durationMinutes: s.duration_minutes,
    capacity: s.capacity,
    bookedCount: s.booked_count,
    spotsLeft: Math.max(0, s.capacity - s.booked_count),
    status: slotStatus(ctx, s, oppStatus),
    myBookingId,
    familiarCount,
  };
}

export function getSlotRow(ctx, id) {
  return ctx.q.get(`${SLOT_SELECT} WHERE s.id = ?`, id);
}

export function photoKeys(ctx, oppId) {
  return ctx.q.all('SELECT media_key FROM opportunity_photos WHERE opportunity_id = ? ORDER BY sort_order, created_at', oppId).map((r) => r.media_key);
}

export function haversineMiles(lat1, lng1, lat2, lng2) {
  const R = 3958.7613;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/**
 * Serialize an opportunity row. opts: { viewer, slots: rows to include as nextSlots, distanceMiles }
 */
export function oppObj(ctx, base, o, opts = {}) {
  const org = ctx.q.get('SELECT * FROM organizations WHERE id = ?', o.org_id);
  return {
    id: o.id,
    title: o.title,
    organization: orgObj(ctx, base, org),
    category: o.category,
    summary: o.summary,
    description: o.description,
    photos: photoKeys(ctx, o.id).map((k) => mediaUrl(base, k)),
    location: { name: o.location_name, address: o.address, lat: o.lat, lng: o.lng },
    cityId: o.city_id,
    costCents: o.cost_cents,
    costNote: o.cost_note,
    accessibility: o.accessibility,
    preparation: o.preparation,
    arrivalLeadMinutes: o.arrival_lead_minutes,
    arrivalNote: o.arrival_note,
    minCommitmentMinutes: o.min_commitment_minutes,
    contactName: o.contact_name,
    contactEmail: o.contact_email,
    contactPhone: o.contact_phone,
    status: o.status,
    isDemo: !!o.is_demo,
    distanceMiles: opts.distanceMiles ?? null,
    nextSlots: (opts.slots || []).map((s) => slotObj(ctx, s, opts.viewer, o.status)),
    tags: JSON.parse(o.tags || '[]'),
  };
}

/** Upcoming slots (<= 60 days) for detail view, including full/cancelled ones. */
export function upcomingSlotRows(ctx, oppId) {
  const now = ctx.now();
  const until = new Date(now.getTime() + 60 * 86400000).toISOString();
  return ctx.q.all(`${SLOT_SELECT} WHERE s.opportunity_id = ? AND s.starts_at > ? AND s.starts_at <= ? ORDER BY s.starts_at`, oppId, now.toISOString(), until);
}

export function bookingSummary(ctx, base, b, viewer) {
  const s = getSlotRow(ctx, b.slot_id);
  const o = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', s.opportunity_id);
  const org = ctx.q.get('SELECT name FROM organizations WHERE id = ?', o.org_id);
  const started = s.starts_at <= ctx.now().toISOString();
  const keys = photoKeys(ctx, o.id);
  return {
    id: b.id,
    status: b.status,
    slot: slotObj(ctx, s, viewer || { userId: b.user_id }, o.status),
    opportunity: {
      id: o.id,
      title: started && b.title_snapshot ? b.title_snapshot : o.title,
      category: o.category,
      photo: keys.length ? mediaUrl(base, keys[0]) : null,
      organizationName: started && b.org_name_snapshot ? b.org_name_snapshot : org.name,
      location: { name: o.location_name, address: o.address, lat: o.lat, lng: o.lng },
      arrivalLeadMinutes: o.arrival_lead_minutes,
    },
    checkedInAt: b.checked_in_at,
    confirmedMinutes: b.confirmed_minutes,
    confirmedAt: b.confirmed_at,
    createdAt: b.created_at,
    cancelledAt: b.cancelled_at,
  };
}
