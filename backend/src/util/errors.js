export class ApiError extends Error {
  constructor(statusCode, code, message, details) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
  toBody() {
    const error = { code: this.code, message: this.message };
    if (this.details !== undefined) error.details = this.details;
    return { error };
  }
}

export const E = {
  validation: (fields, message = 'Some fields are invalid.') => new ApiError(400, 'VALIDATION', message, { fields }),
  unauthenticated: () => new ApiError(401, 'UNAUTHENTICATED', 'Sign in to continue.'),
  sessionExpired: () => new ApiError(401, 'SESSION_EXPIRED', 'Your session has expired. Please sign in again.'),
  forbidden: (msg = 'You do not have permission to do that.') => new ApiError(403, 'FORBIDDEN', msg),
  notFound: (what = 'Resource') => new ApiError(404, 'NOT_FOUND', `${what} not found.`),
  conflict: (code, message, details) => new ApiError(409, code, message, details),
};

/** Convert a ZodError into our VALIDATION error with details.fields = {path: message}. */
export function fromZod(err) {
  const fields = {};
  for (const issue of err.issues || []) {
    const key = issue.path && issue.path.length ? issue.path.join('.') : '_';
    if (!fields[key]) fields[key] = issue.message;
  }
  return E.validation(fields);
}
