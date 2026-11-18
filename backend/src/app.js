import fs from 'node:fs';
import Fastify from 'fastify';
import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';
import fastifyStatic from '@fastify/static';
import formbody from '@fastify/formbody';
import { ZodError } from 'zod';
import { loadConfig, SEED_ASSETS_DIR } from './config.js';
import { openDatabase, migrate, makeQ } from './db.js';
import { ApiError, fromZod } from './util/errors.js';
import { makeAuth } from './auth.js';
import { processDueDemoReplies } from './domain/demoReplies.js';

import authRoutes from './routes/auth.js';
import meRoutes from './routes/me.js';
import discoveryRoutes from './routes/discovery.js';
import bookingRoutes from './routes/bookings.js';
import communityRoutes from './routes/community.js';
import messageRoutes from './routes/messages.js';
import certificateRoutes from './routes/certificates.js';
import orgRoutes from './routes/org.js';
import webRoutes from './routes/web.js';
import adminRoutes from './routes/admin.js';

/**
 * Build the Fastify app. options:
 *  - config overrides (see loadConfig)
 *  - db: existing DatabaseSync (otherwise opened from config.databasePath)
 *  - now: () => Date clock (tests)
 */
export async function buildApp(options = {}) {
  const { db: givenDb, now, ...overrides } = options;
  const config = loadConfig(overrides);
  const db = givenDb || openDatabase(config.databasePath);
  migrate(db);
  fs.mkdirSync(config.uploadDir, { recursive: true });

  const app = Fastify({
    logger: config.logger
      ? {
          level: process.env.LOG_LEVEL || 'info',
          redact: {
            paths: ['req.headers.authorization', 'req.headers.cookie', 'req.headers["x-admin-token"]', 'headers.authorization'],
            censor: '[redacted]',
          },
        }
      : false,
    bodyLimit: 256 * 1024,
    trustProxy: true,
    routerOptions: { ignoreTrailingSlash: true },
  });

  const ctx = {
    db,
    q: makeQ(db),
    config,
    now: now || (() => new Date()),
    log: app.log,
    timers: new Set(),
  };
  ctx.auth = makeAuth(ctx);
  ctx.scheduleDemoReplies = (delayMs = config.demoReplyDelayMs) => {
    if (!config.backgroundJobs) return;
    const t = setTimeout(() => {
      ctx.timers.delete(t);
      try {
        processDueDemoReplies(ctx);
      } catch (err) {
        app.log.error({ err }, 'demo reply job failed');
      }
    }, delayMs + 50);
    t.unref?.();
    ctx.timers.add(t);
  };
  app.decorate('ctx', ctx);
  app.addHook('onClose', async () => {
    for (const t of ctx.timers) clearTimeout(t);
    if (!givenDb) db.close();
  });

  app.setErrorHandler((err, req, reply) => {
    if (err instanceof ApiError) return reply.code(err.statusCode).send(err.toBody());
    if (err instanceof ZodError) {
      const e = fromZod(err);
      return reply.code(400).send(e.toBody());
    }
    if (err.validation) {
      return reply.code(400).send({ error: { code: 'VALIDATION', message: err.message, details: { fields: {} } } });
    }
    if (err.code === 'FST_REQ_FILE_TOO_LARGE' || err.statusCode === 413) {
      return reply.code(413).send({
        error: { code: 'VALIDATION', message: 'The upload is too large.', details: { fields: { file: 'File is too large.' } } },
      });
    }
    if (err.statusCode === 429) {
      return reply.code(429).send({ error: { code: 'RATE_LIMITED', message: 'Too many requests. Please slow down.' } });
    }
    if (err.statusCode && err.statusCode >= 400 && err.statusCode < 500) {
      return reply.code(400).send({ error: { code: 'VALIDATION', message: err.message || 'Bad request.', details: { fields: {} } } });
    }
    req.log.error({ err }, 'unhandled error');
    return reply.code(500).send({ error: { code: 'INTERNAL', message: 'Something went wrong on our side.' } });
  });

  app.setNotFoundHandler((req, reply) => {
    reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Not found.' } });
  });

  if (config.rateLimit) {
    await app.register(rateLimit, {
      global: true,
      max: 300,
      timeWindow: 60_000,
      errorResponseBuilder: (req, c) =>
        new ApiError(429, 'RATE_LIMITED', `Too many requests. Try again in ${Math.ceil(c.ttl / 1000)} seconds.`, {
          retryAfterSeconds: Math.ceil(c.ttl / 1000),
        }),
    });
  }
  await app.register(formbody, { bodyLimit: 32 * 1024 });
  await app.register(multipart, { limits: { fileSize: 5 * 1024 * 1024, files: 1, fields: 10 } });

  await app.register(fastifyStatic, {
    root: config.uploadDir,
    prefix: '/media/uploads/',
    maxAge: '7d',
    immutable: true,
    index: false,
    list: false,
  });
  await app.register(fastifyStatic, {
    root: SEED_ASSETS_DIR,
    prefix: '/media/seed/',
    decorateReply: false,
    maxAge: '1d',
    index: false,
    list: false,
    allowedPath: (pathName) => /^\/?(photos|portraits)\/[A-Za-z0-9._-]+\.(jpe?g|png|webp)$/i.test(pathName),
  });

  const api = { prefix: '/api/v1' };
  await app.register(authRoutes, api);
  await app.register(meRoutes, api);
  await app.register(discoveryRoutes, api);
  await app.register(bookingRoutes, api);
  await app.register(communityRoutes, api);
  await app.register(messageRoutes, api);
  await app.register(certificateRoutes, api);
  await app.register(orgRoutes, api);
  await app.register(webRoutes);
  await app.register(adminRoutes);

  return app;
}
