import { z } from 'zod';
import { E, ApiError } from '../util/errors.js';
import { baseUrlFor, parse } from '../util/http.js';
import { verifyPassword } from '../util/security.js';
import { createSession } from '../auth.js';
import { registerSchema, registerUser } from '../domain/accounts.js';
import { meUser } from '../domain/users.js';

const authLimit = { rateLimit: { max: 10, timeWindow: 60_000 } };

export default async function authRoutes(app) {
  const ctx = app.ctx;

  app.post('/auth/register', { config: authLimit }, async (req, reply) => {
    const data = parse(registerSchema, req.body);
    const { user, token, referral } = registerUser(ctx, data);
    return reply.code(201).send({ token, user: meUser(ctx, baseUrlFor(req, ctx.config), user), referral });
  });

  const loginSchema = z.object({
    email: z.string().trim().toLowerCase().min(1, 'Email is required.'),
    password: z.string().min(1, 'Password is required.'),
    installId: z.string().trim().min(1, 'installId is required.').max(200),
  });
  app.post('/auth/login', { config: authLimit }, async (req) => {
    const data = parse(loginSchema, req.body);
    const user = ctx.q.get('SELECT * FROM users WHERE email = ? AND deleted_at IS NULL', data.email);
    const ok = verifyPassword(data.password, user ? user.password_hash : null);
    if (!user || !ok) throw new ApiError(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect.');
    const token = createSession(ctx, user.id, data.installId);
    return { token, user: meUser(ctx, baseUrlFor(req, ctx.config), user) };
  });

  const demoSchema = z.object({
    persona: z.enum(['volunteer', 'organizer']),
    installId: z.string().trim().min(1, 'installId is required.').max(200),
  });
  app.post('/auth/demo', { config: authLimit }, async (req) => {
    if (!ctx.config.demoLoginEnabled) throw new ApiError(403, 'DEMO_DISABLED', 'Demo sign-in is disabled on this server.');
    const data = parse(demoSchema, req.body);
    const user = ctx.q.get('SELECT * FROM users WHERE demo_persona = ? AND deleted_at IS NULL', data.persona);
    if (!user) throw E.notFound('Demo account (run `npm run seed`)');
    const token = createSession(ctx, user.id, data.installId);
    return { token, user: meUser(ctx, baseUrlFor(req, ctx.config), user) };
  });

  app.post('/auth/logout', async (req, reply) => {
    ctx.auth(req);
    ctx.q.run('DELETE FROM sessions WHERE token_hash = ?', req.sessionHash);
    return reply.code(204).send();
  });
}
