import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { E } from './errors.js';

export function sniffImage(buf) {
  if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'jpg';
  if (buf.length >= 8 && buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'png';
  if (buf.length >= 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP') return 'webp';
  return null;
}

/**
 * Read the single multipart `file` field, validate size + magic bytes, and save under
 * UPLOAD_DIR/<subdir>/<random>.<ext>. Returns the media key "uploads/<subdir>/<name>".
 */
export async function saveImageUpload(ctx, req, subdir, maxBytes) {
  let part;
  try {
    part = await req.file({ limits: { fileSize: maxBytes, files: 1 } });
  } catch {
    throw E.validation({ file: 'Send the image as multipart/form-data field "file".' });
  }
  if (!part || part.fieldname !== 'file') throw E.validation({ file: 'Image file is required (field "file").' });
  let buf;
  try {
    buf = await part.toBuffer();
  } catch (err) {
    if (err && (err.code === 'FST_REQ_FILE_TOO_LARGE' || err.statusCode === 413)) {
      throw E.validation({ file: `Image must be ${Math.round(maxBytes / 1024 / 1024)} MB or smaller.` });
    }
    throw err;
  }
  if (part.file && part.file.truncated) throw E.validation({ file: `Image must be ${Math.round(maxBytes / 1024 / 1024)} MB or smaller.` });
  const ext = sniffImage(buf);
  if (!ext) throw E.validation({ file: 'Only JPEG, PNG, or WebP images are allowed.' });
  const dir = path.join(ctx.config.uploadDir, subdir);
  fs.mkdirSync(dir, { recursive: true });
  const name = `${crypto.randomBytes(16).toString('hex')}.${ext}`;
  fs.writeFileSync(path.join(dir, name), buf);
  return `uploads/${subdir}/${name}`;
}
