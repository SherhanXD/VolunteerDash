-- VolunteerDash initial schema. All instants are ISO-8601 UTC strings (sortable).

CREATE TABLE cities (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  state         TEXT NOT NULL,
  time_zone     TEXT NOT NULL,
  lat           REAL NOT NULL,
  lng           REAL NOT NULL,
  default_zoom  REAL NOT NULL DEFAULT 12,
  is_active     INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE users (
  id                  TEXT PRIMARY KEY,
  email               TEXT UNIQUE,              -- NULL after account deletion
  password_hash       TEXT,
  display_name        TEXT NOT NULL,
  bio                 TEXT NOT NULL DEFAULT '',
  interests           TEXT NOT NULL DEFAULT '[]', -- JSON array of category ids
  city_id             TEXT NOT NULL REFERENCES cities(id),
  avatar_key          TEXT,                     -- relative media key, e.g. uploads/avatars/x.jpg
  invite_code         TEXT UNIQUE,
  profile_public      INTEGER NOT NULL DEFAULT 1,
  show_attendance     INTEGER NOT NULL DEFAULT 1,
  leaderboard_opt_in  INTEGER NOT NULL DEFAULT 1,
  is_demo             INTEGER NOT NULL DEFAULT 0,
  demo_persona        TEXT UNIQUE,              -- 'volunteer' | 'organizer' for the demo login accounts
  deleted_at          TEXT,
  created_at          TEXT NOT NULL
);

CREATE TABLE sessions (
  token_hash    TEXT PRIMARY KEY,               -- sha256(token) hex; raw token never stored
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  install_id    TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  last_used_at  TEXT NOT NULL,
  expires_at    TEXT NOT NULL
);
CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_install ON sessions(install_id);

CREATE TABLE organizations (
  id           TEXT PRIMARY KEY,
  name         TEXT NOT NULL,
  description  TEXT NOT NULL DEFAULT '',
  logo_key     TEXT,
  website      TEXT NOT NULL DEFAULT '',
  email        TEXT NOT NULL DEFAULT '',
  phone        TEXT NOT NULL DEFAULT '',
  city_id      TEXT NOT NULL REFERENCES cities(id),
  is_demo      INTEGER NOT NULL DEFAULT 0,
  created_at   TEXT NOT NULL
);

CREATE TABLE org_members (
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  org_id      TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  role        TEXT NOT NULL DEFAULT 'coordinator',
  created_at  TEXT NOT NULL,
  PRIMARY KEY (user_id, org_id)
);
CREATE INDEX idx_org_members_org ON org_members(org_id);

CREATE TABLE opportunities (
  id                      TEXT PRIMARY KEY,
  org_id                  TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  city_id                 TEXT NOT NULL REFERENCES cities(id),
  title                   TEXT NOT NULL,
  category                TEXT NOT NULL,
  summary                 TEXT NOT NULL DEFAULT '',
  description             TEXT NOT NULL DEFAULT '',
  location_name           TEXT NOT NULL DEFAULT '',
  address                 TEXT NOT NULL DEFAULT '',
  lat                     REAL NOT NULL,
  lng                     REAL NOT NULL,
  cost_cents              INTEGER NOT NULL DEFAULT 0,
  cost_note               TEXT NOT NULL DEFAULT 'Free',
  accessibility           TEXT NOT NULL DEFAULT '',
  preparation             TEXT NOT NULL DEFAULT '',
  arrival_lead_minutes    INTEGER NOT NULL DEFAULT 0,
  arrival_note            TEXT NOT NULL DEFAULT '',
  min_commitment_minutes  INTEGER NOT NULL DEFAULT 10,
  contact_name            TEXT NOT NULL DEFAULT '',
  contact_email           TEXT NOT NULL DEFAULT '',
  contact_phone           TEXT NOT NULL DEFAULT '',
  tags                    TEXT NOT NULL DEFAULT '[]',
  status                  TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('published','draft','cancelled')),
  cancel_reason           TEXT,
  is_demo                 INTEGER NOT NULL DEFAULT 0,
  created_at              TEXT NOT NULL,
  updated_at              TEXT NOT NULL
);
CREATE INDEX idx_opportunities_city_status ON opportunities(city_id, status);
CREATE INDEX idx_opportunities_org ON opportunities(org_id);

CREATE TABLE opportunity_photos (
  id              TEXT PRIMARY KEY,
  opportunity_id  TEXT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
  media_key       TEXT NOT NULL,
  sort_order      INTEGER NOT NULL DEFAULT 0,
  created_at      TEXT NOT NULL
);
CREATE INDEX idx_opportunity_photos_opp ON opportunity_photos(opportunity_id, sort_order);

CREATE TABLE slots (
  id                TEXT PRIMARY KEY,
  opportunity_id    TEXT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
  starts_at         TEXT NOT NULL,
  ends_at           TEXT NOT NULL,
  duration_minutes  INTEGER NOT NULL,
  capacity          INTEGER NOT NULL,
  status            TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','cancelled')),
  is_demo           INTEGER NOT NULL DEFAULT 0,
  created_at        TEXT NOT NULL
);
CREATE INDEX idx_slots_starts_at ON slots(starts_at);
CREATE INDEX idx_slots_opportunity ON slots(opportunity_id, starts_at);

CREATE TABLE bookings (
  id                  TEXT PRIMARY KEY,
  slot_id             TEXT NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
  user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status              TEXT NOT NULL CHECK (status IN ('booked','cancelled','cancelled_by_org','completed','no_show')),
  idempotency_key     TEXT,
  checked_in_at       TEXT,
  confirmed_minutes   INTEGER,
  confirmed_by        TEXT REFERENCES users(id) ON DELETE SET NULL,
  confirmed_at        TEXT,
  title_snapshot      TEXT,
  org_name_snapshot   TEXT,
  created_at          TEXT NOT NULL,
  cancelled_at        TEXT
);
CREATE UNIQUE INDEX ux_bookings_active ON bookings(slot_id, user_id) WHERE status IN ('booked','completed');
CREATE INDEX idx_bookings_user_status ON bookings(user_id, status);
CREATE INDEX idx_bookings_slot_status ON bookings(slot_id, status);

CREATE TABLE idempotency_keys (
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  scope        TEXT NOT NULL,
  key          TEXT NOT NULL,
  resource_id  TEXT NOT NULL,
  created_at   TEXT NOT NULL,
  PRIMARY KEY (user_id, scope, key)
);

CREATE TABLE attendance_audit (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  booking_id   TEXT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  actor_id     TEXT REFERENCES users(id) ON DELETE SET NULL,
  action       TEXT NOT NULL,      -- checkin | confirm | correct | no_show | auto_confirm
  old_minutes  INTEGER,
  new_minutes  INTEGER,
  old_status   TEXT,
  new_status   TEXT,
  reason       TEXT,
  created_at   TEXT NOT NULL
);
CREATE INDEX idx_attendance_audit_booking ON attendance_audit(booking_id, id);

CREATE TABLE badges_earned (
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  badge_id      TEXT NOT NULL,
  earned_at     TEXT NOT NULL,
  celebrated_at TEXT,
  revoked_at    TEXT,
  PRIMARY KEY (user_id, badge_id)
);

CREATE TABLE referrals (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  inviter_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invitee_id  TEXT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  code        TEXT NOT NULL,
  status      TEXT NOT NULL CHECK (status IN ('qualified','rejected_self')),
  created_at  TEXT NOT NULL
);
CREATE INDEX idx_referrals_inviter ON referrals(inviter_id);

CREATE TABLE conversations (
  id              TEXT PRIMARY KEY,
  opportunity_id  TEXT NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
  volunteer_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  booking_id      TEXT REFERENCES bookings(id) ON DELETE SET NULL,
  created_at      TEXT NOT NULL,
  last_message_at TEXT,
  UNIQUE (opportunity_id, volunteer_id)
);
CREATE INDEX idx_conversations_volunteer ON conversations(volunteer_id);

CREATE TABLE messages (
  id               TEXT PRIMARY KEY,
  conversation_id  TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id        TEXT REFERENCES users(id) ON DELETE CASCADE,
  client_id        TEXT NOT NULL,
  body             TEXT NOT NULL,
  is_demo_reply    INTEGER NOT NULL DEFAULT 0,
  reply_due_at     TEXT,          -- set on volunteer messages to demo orgs; cleared when auto-reply is sent
  created_at       TEXT NOT NULL,
  UNIQUE (sender_id, client_id)
);
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_reply_due ON messages(reply_due_at) WHERE reply_due_at IS NOT NULL;

CREATE TABLE conversation_reads (
  conversation_id  TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id          TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  last_read_at     TEXT NOT NULL,
  PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE certificates (
  id            TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  period_start  TEXT NOT NULL,   -- YYYY-MM-DD local date in the user's city time zone
  period_end    TEXT NOT NULL,   -- inclusive
  minutes       INTEGER NOT NULL,
  shifts        INTEGER NOT NULL,
  issued_at     TEXT NOT NULL,
  snapshot      TEXT NOT NULL    -- JSON
);
CREATE INDEX idx_certificates_user ON certificates(user_id);
