import { tx } from '../db.js';
import { E } from '../util/errors.js';
import { baseUrlFor } from '../util/http.js';
import { randomId } from '../util/security.js';
import { bookingSummary, getSlotRow } from '../domain/opportunities.js';
import { teammateMap } from '../domain/users.js';

const ACTIVE = `('booked','completed')`;

/**
 * Book a slot atomically. Returns { booking, created }.
 * Runs under BEGIN IMMEDIATE so capacity checks and the insert are serialized across
 * requests/processes.
 */
export function bookSlot(ctx, userId, slotId, idemKey, base = ctx.config.publicBaseUrl) {
  return tx(ctx.db, () => {
    const now = ctx.now().toISOString();
    const s = getSlotRow(ctx, slotId);
    if (!s) throw E.notFound('Slot');
    const o = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', s.opportunity_id);
    if (!o || o.status === 'draft') throw E.notFound('Slot');

    if (idemKey) {
      const prior = ctx.q.get(`SELECT resource_id FROM idempotency_keys WHERE user_id = ? AND scope = 'booking' AND key = ?`, userId, idemKey);
      if (prior) {
        const b = ctx.q.get('SELECT * FROM bookings WHERE id = ?', prior.resource_id);
        if (b && b.slot_id === slotId) return { booking: b, created: false };
        if (b) throw E.validation({ 'Idempotency-Key': 'This Idempotency-Key was already used for a different slot.' });
      }
    }
    if (s.status === 'cancelled' || o.status === 'cancelled') {
      throw E.conflict('SLOT_CANCELLED', 'This time slot was cancelled by the organizer.');
    }
    if (s.starts_at <= now) throw E.conflict('SLOT_EXPIRED', 'This time slot has already started.');

    const existing = ctx.q.get(`SELECT id FROM bookings WHERE slot_id = ? AND user_id = ? AND status IN ${ACTIVE}`, slotId, userId);
    if (existing) throw E.conflict('ALREADY_BOOKED', 'You already booked this time slot.', { bookingId: existing.id });

    const count = ctx.q.get(`SELECT COUNT(*) AS n FROM bookings WHERE slot_id = ? AND status IN ${ACTIVE}`, slotId).n;
    if (count >= s.capacity) throw E.conflict('SLOT_FULL', 'Sorry, this time slot just filled up.');

    // Overlap check including each opportunity's required early arrival.
    const newStart = Date.parse(s.starts_at) - o.arrival_lead_minutes * 60000;
    const newEnd = Date.parse(s.ends_at);
    const mine = ctx.q.all(
      `SELECT b.*, s.starts_at, s.ends_at, o.arrival_lead_minutes FROM bookings b
         JOIN slots s ON s.id = b.slot_id JOIN opportunities o ON o.id = s.opportunity_id
        WHERE b.user_id = ? AND b.status = 'booked' AND s.ends_at > ? AND s.starts_at < ?`,
      userId,
      new Date(newStart).toISOString(),
      new Date(newEnd + 24 * 3600000).toISOString(),
    );
    for (const m of mine) {
      const st = Date.parse(m.starts_at) - m.arrival_lead_minutes * 60000;
      const en = Date.parse(m.ends_at);
      if (st < newEnd && newStart < en) {
        const conflictRow = ctx.q.get('SELECT * FROM bookings WHERE id = ?', m.id);
        throw E.conflict('BOOKING_CONFLICT', 'This overlaps another shift you booked.', {
          conflict: bookingSummary(ctx, base, conflictRow),
        });
      }
    }

    const org = ctx.q.get('SELECT name FROM organizations WHERE id = ?', o.org_id);
    const id = randomId('bkg');
    ctx.q.run(
      `INSERT INTO bookings(id, slot_id, user_id, status, idempotency_key, title_snapshot, org_name_snapshot, created_at)
       VALUES (?, ?, ?, 'booked', ?, ?, ?, ?)`,
      id,
      slotId,
      userId,
      idemKey || null,
      o.title,
      org.name,
      now,
    );
    if (idemKey) {
      ctx.q.run(
        `INSERT INTO idempotency_keys(user_id, scope, key, resource_id, created_at) VALUES (?, 'booking', ?, ?, ?)`,
        userId,
        idemKey,
        id,
        now,
      );
    }
    return { booking: ctx.q.get('SELECT * FROM bookings WHERE id = ?', id), created: true };
  });
}

export default async function bookingRoutes(app) {
  const ctx = app.ctx;
  const base = (req) => baseUrlFor(req, ctx.config);

  app.post('/slots/:id/bookings', async (req, reply) => {
    const user = ctx.auth(req);
    let key = req.headers['idempotency-key'];
    if (Array.isArray(key)) key = key[0];
    if (key !== undefined && (typeof key !== 'string' || key.length < 1 || key.length > 200)) {
      throw E.validation({ 'Idempotency-Key': 'Idempotency-Key must be 1-200 characters.' });
    }
    const { booking, created } = bookSlot(ctx, user.id, req.params.id, key || null, base(req));
    const viewer = { userId: user.id, teammates: teammateMap(ctx, user.id) };
    return reply.code(created ? 201 : 200).send(bookingSummary(ctx, base(req), booking, viewer));
  });

  const isOrganizerFor = (userId, booking) =>
    !!ctx.q.get(
      `SELECT 1 FROM slots s JOIN opportunities o ON o.id = s.opportunity_id
         JOIN org_members m ON m.org_id = o.org_id AND m.user_id = ? WHERE s.id = ?`,
      userId,
      booking.slot_id,
    );

  app.get('/bookings/:id', async (req) => {
    const user = ctx.auth(req);
    const b = ctx.q.get('SELECT * FROM bookings WHERE id = ?', req.params.id);
    if (!b) throw E.notFound('Booking');
    if (b.user_id !== user.id && !isOrganizerFor(user.id, b)) throw E.notFound('Booking');
    return bookingSummary(ctx, base(req), b);
  });

  app.post('/bookings/:id/cancel', async (req) => {
    const user = ctx.auth(req);
    const result = tx(ctx.db, () => {
      const b = ctx.q.get('SELECT * FROM bookings WHERE id = ?', req.params.id);
      if (!b || b.user_id !== user.id) throw E.notFound('Booking');
      if (b.status === 'cancelled' || b.status === 'cancelled_by_org') return b;
      if (b.status !== 'booked') throw E.conflict('NOT_CANCELLABLE', 'Completed shifts cannot be cancelled.');
      const s = getSlotRow(ctx, b.slot_id);
      if (s.ends_at <= ctx.now().toISOString()) throw E.conflict('NOT_CANCELLABLE', 'This shift has already ended.');
      ctx.q.run(`UPDATE bookings SET status = 'cancelled', cancelled_at = ? WHERE id = ?`, ctx.now().toISOString(), b.id);
      return ctx.q.get('SELECT * FROM bookings WHERE id = ?', b.id);
    });
    return bookingSummary(ctx, base(req), result);
  });
}
