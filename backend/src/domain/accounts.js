import fs from 'node:fs';
import path from 'node:path';
import { z } from 'zod';
import { tx } from '../db.js';
import { E } from '../util/errors.js';
import { hashPassword, randomCode, randomId } from '../util/security.js';
import { createSession } from '../auth.js';
import { recomputeUser } from './users.js';

export const registerSchema = z.object({
  email: z.string().trim().toLowerCase().email('Enter a valid email address.').max(254),
  password: z.string().min(8, 'Password must be at least 8 characters.').max(200),
  displayName: z
    .string()
    .trim()
    .min(2, 'Display name must be 2-40 characters.')
    .max(40, 'Display name must be 2-40 characters.'),
  cityId: z.string().optional().nullable(),
  inviteCode: z.string().trim().max(32).optional().nullable(),
  installId: z.string().trim().min(1, 'installId is required.').max(200),
});

export function generateInviteCode(ctx, displayName) {
  const letters = (displayName || '')
    .normalize('NFKD')
    .replace(/[^A-Za-z]/g, '')
    .toUpperCase()
    .replace(/[OIL]/g, '')
    .slice(0, 6);
  const stem = letters.length >= 3 ? letters : 'VOL' + letters;
  for (let i = 0; i < 20; i++) {
    const code = stem.slice(0, 6) + randomCode(i < 10 ? 2 : 4);
    if (!ctx.q.get('SELECT 1 FROM users WHERE invite_code = ?', code)) return code;
  }
  return randomCode(10);
}

/** Create an account + session. Returns { user, token, referral: {status} }. */
export function registerUser(ctx, input) {
  const data = input;
  const cityId = data.cityId || 'tempe-az';
  if (!ctx.q.get('SELECT 1 FROM cities WHERE id = ?', cityId)) throw E.validation({ cityId: 'Unknown city.' });
  return tx(ctx.db, () => {
    if (ctx.q.get('SELECT 1 FROM users WHERE email = ?', data.email)) {
      throw E.conflict('EMAIL_TAKEN', 'An account with this email already exists.');
    }
    const id = randomId('usr');
    const now = ctx.now().toISOString();
    ctx.q.run(
      `INSERT INTO users(id, email, password_hash, display_name, city_id, invite_code, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      id,
      data.email,
      hashPassword(data.password),
      data.displayName,
      cityId,
      generateInviteCode(ctx, data.displayName),
      now,
    );
    let referral = { status: 'none' };
    if (data.inviteCode) {
      const code = data.inviteCode.trim().toUpperCase();
      const inviter = ctx.q.get('SELECT * FROM users WHERE invite_code = ? AND deleted_at IS NULL', code);
      if (!inviter) {
        referral = { status: 'invalid' };
      } else {
        const sameDevice = ctx.q.get('SELECT 1 FROM sessions WHERE user_id = ? AND install_id = ? LIMIT 1', inviter.id, data.installId);
        const status = sameDevice ? 'rejected_self' : 'qualified';
        ctx.q.run(
          'INSERT INTO referrals(inviter_id, invitee_id, code, status, created_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(invitee_id) DO NOTHING',
          inviter.id,
          id,
          code,
          status,
          now,
        );
        referral = { status };
        if (status === 'qualified') recomputeUser(ctx, inviter.id);
      }
    }
    const token = createSession(ctx, id, data.installId);
    const user = ctx.q.get('SELECT * FROM users WHERE id = ?', id);
    return { user, token, referral };
  });
}

export function removeUploadedFile(ctx, key) {
  if (!key || !key.startsWith('uploads/')) return;
  const rel = key.slice('uploads/'.length);
  const full = path.resolve(ctx.config.uploadDir, rel);
  if (!full.startsWith(path.resolve(ctx.config.uploadDir))) return;
  fs.rm(full, { force: true }, () => {});
}

/**
 * Anonymize the account. Completed attendance rows are kept (organizations' records),
 * but the user is detached from any personal data.
 */
export function deleteAccount(ctx, userId) {
  const user = ctx.q.get('SELECT * FROM users WHERE id = ?', userId);
  const now = ctx.now().toISOString();
  const affectedInviters = [];
  tx(ctx.db, () => {
    ctx.q.run(
      `UPDATE users SET display_name = 'Deleted volunteer', email = NULL, password_hash = NULL, avatar_key = NULL,
         bio = '', interests = '[]', profile_public = 0, show_attendance = 0, leaderboard_opt_in = 0,
         invite_code = NULL, demo_persona = NULL, deleted_at = ? WHERE id = ?`,
      now,
      userId,
    );
    ctx.q.run('DELETE FROM sessions WHERE user_id = ?', userId);
    ctx.q.run(`UPDATE messages SET body = '[deleted]', reply_due_at = NULL WHERE sender_id = ?`, userId);
    ctx.q.run(
      `UPDATE bookings SET status = 'cancelled', cancelled_at = ? WHERE user_id = ? AND status = 'booked'`,
      now,
      userId,
    );
    ctx.q.run('DELETE FROM badges_earned WHERE user_id = ?', userId);
    ctx.q.run('DELETE FROM certificates WHERE user_id = ?', userId);
    ctx.q.run('DELETE FROM conversation_reads WHERE user_id = ?', userId);
    ctx.q.run('DELETE FROM idempotency_keys WHERE user_id = ?', userId);
    ctx.q.run('DELETE FROM org_members WHERE user_id = ?', userId);
    for (const r of ctx.q.all('SELECT inviter_id FROM referrals WHERE invitee_id = ?', userId)) affectedInviters.push(r.inviter_id);
  });
  removeUploadedFile(ctx, user.avatar_key);
}

export function exportUserData(ctx, userId) {
  const u = { ...ctx.q.get('SELECT * FROM users WHERE id = ?', userId) };
  delete u.password_hash;
  u.interests = JSON.parse(u.interests || '[]');
  const plain = (rows) => rows.map((r) => ({ ...r }));
  return {
    exportedAt: ctx.now().toISOString(),
    user: u,
    sessions: plain(ctx.q.all('SELECT install_id, created_at, last_used_at, expires_at FROM sessions WHERE user_id = ?', userId)),
    orgRoles: plain(ctx.q.all('SELECT org_id, role, created_at FROM org_members WHERE user_id = ?', userId)),
    bookings: plain(
      ctx.q.all(
        `SELECT b.id, b.status, b.created_at, b.cancelled_at, b.checked_in_at, b.confirmed_minutes, b.confirmed_at,
                s.starts_at, s.ends_at, o.id AS opportunity_id, COALESCE(b.title_snapshot, o.title) AS title
           FROM bookings b JOIN slots s ON s.id = b.slot_id JOIN opportunities o ON o.id = s.opportunity_id
          WHERE b.user_id = ? ORDER BY s.starts_at`,
        userId,
      ),
    ),
    badges: plain(ctx.q.all('SELECT badge_id, earned_at, celebrated_at, revoked_at FROM badges_earned WHERE user_id = ?', userId)),
    referralsMade: plain(ctx.q.all('SELECT invitee_id, code, status, created_at FROM referrals WHERE inviter_id = ?', userId)),
    referredBy: plain(ctx.q.all('SELECT inviter_id, code, status, created_at FROM referrals WHERE invitee_id = ?', userId)),
    messages: plain(
      ctx.q.all(
        'SELECT m.id, m.conversation_id, m.client_id, m.body, m.created_at FROM messages m WHERE m.sender_id = ? ORDER BY m.created_at',
        userId,
      ),
    ),
    conversations: plain(ctx.q.all('SELECT id, opportunity_id, created_at FROM conversations WHERE volunteer_id = ?', userId)),
    certificates: plain(ctx.q.all('SELECT id, period_start, period_end, minutes, shifts, issued_at FROM certificates WHERE user_id = ?', userId)),
  };
}
