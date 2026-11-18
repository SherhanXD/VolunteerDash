import { E, fromZod } from './errors.js';

/**
 * Base URL for absolute links in responses. Prefer the host the client actually used
 * (honoring X-Forwarded-Proto/Host from a reverse proxy or tunnel) so media works from
 * emulator, LAN device, or tunnel alike; fall back to PUBLIC_BASE_URL.
 */
export function baseUrlFor(req, config) {
  if (!req || !req.headers) return config.publicBaseUrl;
  const fHost = firstHeader(req.headers['x-forwarded-host']);
  const host = fHost || req.headers.host;
  if (!host || !/^[A-Za-z0-9.\-:\[\]]+$/.test(host)) return config.publicBaseUrl;
  let proto = firstHeader(req.headers['x-forwarded-proto']);
  if (!proto) proto = req.protocol || 'http';
  proto = proto === 'https' ? 'https' : 'http';
  return `${proto}://${host}`;
}

function firstHeader(v) {
  if (!v) return '';
  return String(Array.isArray(v) ? v[0] : v).split(',')[0].trim();
}

export function mediaUrl(base, key) {
  if (!key) return null;
  return `${base}/media/${key.split('/').map(encodeURIComponent).join('/')}`;
}

/** Parse with zod; throw VALIDATION on failure. */
export function parse(schema, data) {
  const r = schema.safeParse(data ?? {});
  if (!r.success) throw fromZod(r.error);
  return r.data;
}

export function encodeCursor(offset) {
  return Buffer.from(JSON.stringify({ o: offset })).toString('base64url');
}

export function decodeCursor(cursor) {
  if (!cursor) return 0;
  try {
    const v = JSON.parse(Buffer.from(String(cursor), 'base64url').toString('utf8'));
    if (Number.isInteger(v.o) && v.o >= 0) return v.o;
  } catch {
    /* fallthrough */
  }
  throw E.validation({ cursor: 'Invalid cursor.' });
}

export function parseLimit(v) {
  if (v === undefined || v === '') return 20;
  const n = Number(v);
  if (!Number.isInteger(n) || n < 1) throw E.validation({ limit: 'limit must be a positive integer.' });
  return Math.min(n, 50);
}

/** Paginate an in-memory array with an opaque offset cursor. */
export function paginate(items, query) {
  const limit = parseLimit(query.limit);
  const offset = decodeCursor(query.cursor);
  const page = items.slice(offset, offset + limit);
  const next = offset + limit < items.length ? encodeCursor(offset + limit) : null;
  return { items: page, nextCursor: next };
}

export function escapeHtml(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function parseCookies(header) {
  const out = {};
  if (!header) return out;
  for (const part of String(header).split(';')) {
    const i = part.indexOf('=');
    if (i < 0) continue;
    const k = part.slice(0, i).trim();
    const v = part.slice(i + 1).trim();
    try {
      out[k] = decodeURIComponent(v);
    } catch {
      out[k] = v;
    }
  }
  return out;
}
