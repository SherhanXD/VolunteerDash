import { E } from './util/errors.js';
import { newToken, sha256 } from './util/security.js';

const SESSION_DAYS = 30;
const SLIDE_AFTER_MS = 5 * 60 * 1000;

export function createSession(ctx, userId, installId) {
  const token = newToken();
  const now = ctx.now();
  const exp = new Date(now.getTime() + SESSION_DAYS * 86400000);
  ctx.q.run(
    'INSERT INTO sessions(token_hash, user_id, install_id, created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)',
    sha256(token),
    userId,
    String(installId),
    now.toISOString(),
    now.toISOString(),
    exp.toISOString(),
  );
  return token;
}

function bearer(req) {
  const h = req.headers.authorization;
  if (!h) return null;
  const m = /^Bearer\s+(\S+)$/i.exec(h);
  return m ? m[1] : '';
}

/** ctx.auth(req, {optional}) → user row (also sets req.user / req.sessionHash). */
export function makeAuth(ctx) {
  return function auth(req, { optional = false } = {}) {
    if (req.user) return req.user;
    const token = bearer(req);
    if (token === null) {
      if (optional) return null;
      throw E.unauthenticated();
    }
    if (!token) throw E.unauthenticated();
    const hash = sha256(token);
    const s = ctx.q.get('SELECT * FROM sessions WHERE token_hash = ?', hash);
    if (!s) throw E.unauthenticated();
    const now = ctx.now();
    if (s.expires_at <= now.toISOString()) {
      ctx.q.run('DELETE FROM sessions WHERE token_hash = ?', hash);
      throw E.sessionExpired();
    }
    const user = ctx.q.get('SELECT * FROM users WHERE id = ?', s.user_id);
    if (!user || user.deleted_at) throw E.unauthenticated();
    if (now.getTime() - Date.parse(s.last_used_at) > SLIDE_AFTER_MS) {
      ctx.q.run(
        'UPDATE sessions SET last_used_at = ?, expires_at = ? WHERE token_hash = ?',
        now.toISOString(),
        new Date(now.getTime() + SESSION_DAYS * 86400000).toISOString(),
        hash,
      );
    }
    req.user = user;
    req.sessionHash = hash;
    return user;
  };
}

/** Rate-limit key for per-user limits: hash of the bearer token, else client IP. */
export function userRateKey(req) {
  const h = req.headers.authorization;
  return h ? `u:${sha256(h)}` : `ip:${req.ip}`;
}
