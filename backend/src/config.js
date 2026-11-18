import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const BACKEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const SEED_ASSETS_DIR = path.join(BACKEND_ROOT, 'seed-assets');
export const MIGRATIONS_DIR = path.join(BACKEND_ROOT, 'migrations');

let envLoaded = false;
function loadEnvOnce() {
  if (envLoaded) return;
  envLoaded = true;
  const envPath = path.join(BACKEND_ROOT, '.env');
  if (fs.existsSync(envPath)) {
    try {
      process.loadEnvFile(envPath);
    } catch {
      // ignore malformed .env; defaults apply
    }
  }
}

function bool(v, dflt) {
  if (v === undefined || v === '') return dflt;
  return ['1', 'true', 'yes', 'on'].includes(String(v).toLowerCase());
}

function resolveFromRoot(p) {
  return path.isAbsolute(p) ? p : path.resolve(BACKEND_ROOT, p);
}

/** Build config from env, with optional overrides (used by tests). */
export function loadConfig(overrides = {}) {
  loadEnvOnce();
  const e = process.env;
  const cfg = {
    port: Number(e.PORT || 8787),
    host: e.HOST || '0.0.0.0',
    publicBaseUrl: (e.PUBLIC_BASE_URL || 'http://10.0.2.2:8787').replace(/\/+$/, ''),
    databasePath: e.DATABASE_PATH || './data/volunteerdash.db',
    uploadDir: e.UPLOAD_DIR || './data/uploads',
    adminToken: e.ADMIN_TOKEN || '',
    demoLoginEnabled: bool(e.DEMO_LOGIN_ENABLED, true),
    androidPackage: e.ANDROID_PACKAGE || 'com.volunteerdash.app',
    androidCertSha256: e.ANDROID_CERT_SHA256 || '',
    appDownloadUrl: e.APP_DOWNLOAD_URL || '',
    rateLimit: true,
    demoReplyDelayMs: 2500,
    backgroundJobs: true,
    logger: true,
    ...overrides,
  };
  if (cfg.databasePath !== ':memory:') cfg.databasePath = resolveFromRoot(cfg.databasePath);
  cfg.uploadDir = resolveFromRoot(cfg.uploadDir);
  return cfg;
}
