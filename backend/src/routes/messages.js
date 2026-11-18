import { z } from 'zod';
import { tx } from '../db.js';
import { E } from '../util/errors.js';
import { baseUrlFor, mediaUrl, parse } from '../util/http.js';
import { randomId } from '../util/security.js';
import { userRateKey } from '../auth.js';
import { compactUser, getUserRow } from '../domain/users.js';
import { photoKeys } from '../domain/opportunities.js';

export default async function messageRoutes(app) {
  const ctx = app.ctx;
  const base = (req) => baseUrlFor(req, ctx.config);

  const isMember = (userId, orgId) => !!ctx.q.get('SELECT 1 FROM org_members WHERE user_id = ? AND org_id = ?', userId, orgId);

  /** Load conversation + access role for user, or throw. */
  function access(userId, convId) {
    const c = ctx.q.get(
      `SELECT c.*, o.org_id, o.title FROM conversations c JOIN opportunities o ON o.id = c.opportunity_id WHERE c.id = ?`,
      convId,
    );
    if (!c) throw E.notFound('Conversation');
    if (c.volunteer_id === userId) return { c, role: 'volunteer' };
    if (isMember(userId, c.org_id)) return { c, role: 'organizer' };
    throw E.forbidden();
  }

  function convObj(req, userId, c) {
    const opp = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', c.opportunity_id);
    const org = ctx.q.get('SELECT name FROM organizations WHERE id = ?', opp.org_id);
    const last = ctx.q.get('SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at DESC, id DESC LIMIT 1', c.id);
    const read = ctx.q.get('SELECT last_read_at FROM conversation_reads WHERE conversation_id = ? AND user_id = ?', c.id, userId);
    const unread = ctx.q.get(
      `SELECT COUNT(*) AS n FROM messages WHERE conversation_id = ? AND (sender_id IS NULL OR sender_id <> ?) AND created_at > ?`,
      c.id,
      userId,
      read ? read.last_read_at : '',
    ).n;
    const keys = photoKeys(ctx, opp.id);
    return {
      id: c.id,
      opportunity: { id: opp.id, title: opp.title, photo: keys.length ? mediaUrl(base(req), keys[0]) : null },
      organizationName: org.name,
      volunteer: compactUser(ctx, base(req), getUserRow(ctx, c.volunteer_id), { viewerId: userId, forceVisible: true }),
      lastMessage: last ? { body: last.body, createdAt: last.created_at, senderIsMe: last.sender_id === userId } : null,
      unread,
    };
  }

  app.get('/conversations', async (req) => {
    const user = ctx.auth(req);
    const rows = ctx.q.all(
      `SELECT DISTINCT c.* FROM conversations c JOIN opportunities o ON o.id = c.opportunity_id
        LEFT JOIN org_members m ON m.org_id = o.org_id AND m.user_id = ?
        WHERE c.volunteer_id = ? OR m.user_id IS NOT NULL
        ORDER BY COALESCE(c.last_message_at, c.created_at) DESC`,
      user.id,
      user.id,
    );
    return { items: rows.map((c) => convObj(req, user.id, c)) };
  });

  const createSchema = z.object({ opportunityId: z.string().min(1), bookingId: z.string().optional().nullable() });
  app.post('/conversations', async (req, reply) => {
    const user = ctx.auth(req);
    const d = parse(createSchema, req.body);
    const opp = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', d.opportunityId);
    if (!opp || opp.status === 'draft') throw E.notFound('Opportunity');
    let volunteerId = user.id;
    let bookingId = null;
    if (d.bookingId) {
      const b = ctx.q.get(
        `SELECT b.* FROM bookings b JOIN slots s ON s.id = b.slot_id WHERE b.id = ? AND s.opportunity_id = ?`,
        d.bookingId,
        opp.id,
      );
      if (!b) throw E.notFound('Booking');
      if (b.user_id !== user.id) {
        if (!isMember(user.id, opp.org_id)) throw E.forbidden();
        volunteerId = b.user_id; // organizer messaging a volunteer about their booking
      }
      bookingId = b.id;
    } else if (isMember(user.id, opp.org_id)) {
      throw E.validation({ bookingId: 'Organizers reply in existing conversations or pass a bookingId.' });
    }
    const c = tx(ctx.db, () => {
      let row = ctx.q.get('SELECT * FROM conversations WHERE opportunity_id = ? AND volunteer_id = ?', opp.id, volunteerId);
      if (!row) {
        const id = randomId('cnv');
        ctx.q.run(
          'INSERT INTO conversations(id, opportunity_id, volunteer_id, booking_id, created_at) VALUES (?, ?, ?, ?, ?)',
          id,
          opp.id,
          volunteerId,
          bookingId,
          ctx.now().toISOString(),
        );
        row = ctx.q.get('SELECT * FROM conversations WHERE id = ?', id);
        reply.code(201);
      } else if (bookingId && !row.booking_id) {
        ctx.q.run('UPDATE conversations SET booking_id = ? WHERE id = ?', bookingId, row.id);
      }
      return row;
    });
    return convObj(req, user.id, c);
  });

  const msgObj = (req, userId, m) => ({
    id: m.id,
    clientId: m.client_id,
    body: m.body,
    sender: compactUser(ctx, base(req), getUserRow(ctx, m.sender_id), { viewerId: userId, forceVisible: true }),
    senderIsMe: m.sender_id === userId,
    createdAt: m.created_at,
    isDemoReply: !!m.is_demo_reply,
  });

  app.get('/conversations/:id/messages', async (req) => {
    const user = ctx.auth(req);
    const { c } = access(user.id, req.params.id);
    let after = '';
    if (req.query.after) {
      if (Number.isNaN(Date.parse(req.query.after))) throw E.validation({ after: 'after must be an ISO-8601 instant.' });
      after = new Date(req.query.after).toISOString();
    }
    const nowIso = ctx.now().toISOString();
    const rows = ctx.q.all(
      'SELECT * FROM messages WHERE conversation_id = ? AND created_at > ? AND created_at <= ? ORDER BY created_at ASC, id ASC LIMIT 500',
      c.id,
      after,
      nowIso,
    );
    ctx.q.run(
      `INSERT INTO conversation_reads(conversation_id, user_id, last_read_at) VALUES (?, ?, ?)
       ON CONFLICT(conversation_id, user_id) DO UPDATE SET last_read_at = excluded.last_read_at`,
      c.id,
      user.id,
      nowIso,
    );
    return { items: rows.map((m) => msgObj(req, user.id, m)) };
  });

  const sendSchema = z.object({
    clientId: z.string().uuid('clientId must be a UUID.'),
    body: z.string().trim().min(1, 'Message cannot be empty.').max(1000, 'Message must be 1000 characters or fewer.'),
  });
  app.post(
    '/conversations/:id/messages',
    { config: { rateLimit: { max: 30, timeWindow: 60_000, keyGenerator: userRateKey } } },
    async (req, reply) => {
      const user = ctx.auth(req);
      const { c, role } = access(user.id, req.params.id);
      const d = parse(sendSchema, req.body);
      let scheduled = false;
      const m = tx(ctx.db, () => {
        const existing = ctx.q.get('SELECT * FROM messages WHERE sender_id = ? AND client_id = ?', user.id, d.clientId);
        if (existing) {
          if (existing.conversation_id !== c.id) throw E.validation({ clientId: 'clientId already used in another conversation.' });
          return existing;
        }
        const org = ctx.q.get('SELECT is_demo FROM organizations WHERE id = ?', c.org_id);
        const now = ctx.now();
        let due = null;
        if (role === 'volunteer' && org.is_demo) {
          due = new Date(now.getTime() + ctx.config.demoReplyDelayMs).toISOString();
          scheduled = true;
        }
        const id = randomId('msg');
        ctx.q.run(
          `INSERT INTO messages(id, conversation_id, sender_id, client_id, body, reply_due_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
          id,
          c.id,
          user.id,
          d.clientId,
          d.body,
          due,
          now.toISOString(),
        );
        ctx.q.run('UPDATE conversations SET last_message_at = ? WHERE id = ?', now.toISOString(), c.id);
        reply.code(201);
        return ctx.q.get('SELECT * FROM messages WHERE id = ?', id);
      });
      if (scheduled) ctx.scheduleDemoReplies();
      return msgObj(req, user.id, m);
    },
  );
}
