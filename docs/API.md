# VolunteerDash API contract (v1)

Base path: `/api/v1`. JSON, camelCase. All instants are ISO-8601 UTC strings ending in `Z` (e.g. `2026-11-14T19:00:00.000Z`).
Auth: `Authorization: Bearer <token>` (opaque session token). Errors: HTTP status + body
`{"error":{"code":"SLOT_FULL","message":"Human readable message","details":{...}}}`.

Common error codes: `VALIDATION` (400, details.fields = {field: message}), `UNAUTHENTICATED` (401), `SESSION_EXPIRED` (401),
`FORBIDDEN` (403), `NOT_FOUND` (404), `SLOT_FULL` (409), `ALREADY_BOOKED` (409), `BOOKING_CONFLICT` (409, details.conflict = BookingSummary),
`SLOT_EXPIRED` (409), `SLOT_CANCELLED` (409), `CAPACITY_BELOW_BOOKINGS` (409), `RATE_LIMITED` (429), `EMAIL_TAKEN` (409), `INVALID_CREDENTIALS` (401).

Pagination: list endpoints accept `limit` (default 20, max 50) and `cursor` (opaque string); respond `{ "items": [...], "nextCursor": "..." | null }`.

## Shared objects

```jsonc
City { "id":"tempe-az", "name":"Tempe", "state":"AZ", "timeZone":"America/Phoenix", "lat":33.4255, "lng":-111.9400, "defaultZoom":12.5 }

UserPublic {            // privacy-filtered
  "id":"usr_...", "displayName":"Jordan Reyes", "avatarUrl":"https://host/media/..."|null, "city":"Tempe, AZ",
  "interests":["food","environment"], "bio":"...", "isDemo":true,
  "confirmedMinutes": 720, "level": Level, "badges":[BadgeEarned], "completedShifts": 14,
  "isPrivate": false        // when profile hidden: only id, displayName "Private volunteer", isPrivate=true, other fields null/empty
}
Level { "level":4, "name":"Contributor", "minHours":10, "nextLevelHours":20, "nextName":"Champion", "progress":0.2 }
BadgeEarned { "id":"first_shift", "name":"First Shift", "description":"...", "rule":"Complete your first organizer-confirmed shift.",
              "tier":"bronze|silver|gold|special", "category":null|"food", "earnedAt":"...Z"|null, "celebrated":true }

Me = UserPublic + { "email":"...", "cityId":"tempe-az", "inviteCode":"JORDAN7K", "inviteUrl":"https://host/i/JORDAN7K",
  "privacy": {"profilePublic":true, "showAttendance":true, "leaderboardOptIn":true},
  "roles": [{"orgId":"org_pantry","orgName":"Tempe Community Pantry","role":"coordinator"}],
  "allBadges":[BadgeEarned]   // full catalog, earnedAt null when not earned, plus "progressText":"2 of 3 shifts"
}

Organization { "id":"org_pantry", "name":"...", "description":"...", "logoUrl":null|"...", "website":"...", "email":"...", "phone":"...", "isDemo":true }

Opportunity {
  "id":"opp-pantry-boxes", "title":"Pack pantry boxes", "organization": Organization, "category":"food",
  "summary":"one line", "description":"long text", "photos":["https://host/media/photos/opp-pantry-boxes-1.jpg"],
  "location":{"name":"Escalante Community Center","address":"2150 E Orange St, Tempe, AZ 85281","lat":33.4237,"lng":-111.9014},
  "cityId":"tempe-az", "costCents":0, "costNote":"Free"|"$5 suggested materials donation",
  "accessibility":"Wheelchair accessible, seated tasks available", "preparation":"Closed-toe shoes...",
  "arrivalLeadMinutes":10,  // fixed early arrival required before slot start (0 = none)
  "arrivalNote":"Check in at the side door 10 minutes early.",
  "minCommitmentMinutes":10, "contactName":"Maya Torres", "contactEmail":"...", "contactPhone":"...",
  "status":"published|draft|cancelled", "isDemo":true,
  "distanceMiles": 0.8|null,     // when lat/lng given
  "nextSlots":[Slot],            // list endpoint: up to 4 matching upcoming slots; detail: all upcoming (<=60 days)
  "tags":["Lunch-break friendly"]
}

Slot { "id":"slt_...", "opportunityId":"...", "startsAt":"...Z", "endsAt":"...Z", "durationMinutes":15, "capacity":6,
       "bookedCount":3, "spotsLeft":3, "status":"open|full|cancelled|expired",
       "myBookingId": null|"bkg_...", "familiarCount": 1 }

BookingSummary { "id":"bkg_...", "status":"booked|cancelled|cancelled_by_org|completed|no_show",
  "slot": Slot, "opportunity": {"id","title","category","photo","organizationName","location":{...},"arrivalLeadMinutes"},
  "checkedInAt":null|"...Z", "confirmedMinutes":null|15, "confirmedAt":null|"...Z", "createdAt":"...Z", "cancelledAt":null }

Attendee { "user": UserPublic(compact: id, displayName, avatarUrl, level, topBadges[<=3]), "isFamiliar":true,
           "sharedShifts":3, "lastSharedTitle":"Pack pantry boxes", "lastSharedAt":"...Z" }
```

## Auth
- `POST /auth/register` `{email, password(min 8), displayName(2-40), cityId?, inviteCode?, installId}` → `201 {token, user: Me, referral: {status:"qualified|rejected_self|invalid|none"}}`
- `POST /auth/login` `{email, password, installId}` → `{token, user: Me}`
- `POST /auth/demo` `{persona: "volunteer"|"organizer", installId}` → `{token, user: Me}` (403 `DEMO_DISABLED` if disabled). Logs into seeded demo accounts (Jordan Reyes volunteer / Maya Torres organizer).
- `POST /auth/logout` → 204

## Me
- `GET /me` → Me
- `PATCH /me` `{displayName?, bio?(<=280), interests?([category ids]), cityId?, privacy?{...}}` → Me
- `PUT /me/avatar` multipart field `file` (jpeg/png/webp, <= 3 MB) → Me. `DELETE /me/avatar` → Me
- `GET /me/stats` → `{confirmedMinutes, completedShifts, level: Level, weekMinutes, weekRank: int|null, upcomingCount, newBadges:[BadgeEarned]}` (newBadges = earned & not celebrated)
- `POST /me/badges/ack` `{ids:[...]}` → 204
- `GET /me/bookings?scope=upcoming|past|all` → `{items:[BookingSummary]}` (upcoming sorted asc, past desc)
- `GET /me/teammates` → `{items:[Attendee]}` people with shared completed shifts
- `GET /me/referrals` → `{inviteCode, inviteUrl, qualifiedCount, items:[{displayName, joinedAt, status}]}`
- `GET /me/export` → JSON of all personal data
- `DELETE /me` `{password}` (demo accounts: `{confirm:"DELETE"}`) → 204

## Discovery
- `GET /cities` → `{items:[City]}`
- `GET /categories` → `{items:[{id:"food", name:"Food security", color:"#E8743B", icon:"food"}]}` ids: food, environment, animals, education, seniors, health, housing, arts
- `GET /opportunities` query: `city`(default tempe-az), `lat`,`lng`,`radiusMi`, `q`, `categories`(csv), `from`,`to` (ISO instants bounding slot start), `startMinMin`,`startMaxMin` (minutes after local midnight in city tz, slot start), `maxDuration` (minutes; slot duration <= value), `freeOnly`(bool), `openOnly`(bool, spotsLeft>0), `limit`,`cursor`, `sort=distance|soonest`.
  Only opportunities with at least one matching upcoming open (or full when openOnly=false) slot are returned. Response `{items:[Opportunity], nextCursor}`.
- `GET /opportunities/:id` → Opportunity (with all upcoming slots, myBookingId populated)
- `GET /recommendations?lat&lng` → `{items:[Opportunity]}` (interest match, short duration, familiar faces, soonest)
- `GET /slots/:id/attendees` → `{familiar:[Attendee], others:[Attendee], hiddenCount: 2, total: 5}`

## Booking
- `POST /slots/:id/bookings` header `Idempotency-Key: <uuid>` body `{}` → `201 BookingSummary` (or `200` same booking when replayed / already booked by same user with same key). Errors SLOT_FULL, SLOT_EXPIRED, SLOT_CANCELLED, ALREADY_BOOKED (different key, returns details.bookingId), BOOKING_CONFLICT (overlaps another active booking of the user).
- `GET /bookings/:id` → BookingSummary (owner or organizer)
- `POST /bookings/:id/cancel` → BookingSummary (status cancelled; idempotent; completed bookings cannot be cancelled → 409 `NOT_CANCELLABLE`)

## Community
- `GET /leaderboard?city=tempe-az&week=current|previous|YYYY-MM-DD` → `{weekStart, weekEnd, timeZone, items:[{rank, user: UserPublic(compact), minutes, shifts}], me:{rank|null, minutes, shifts, optedIn}}`
  Week = Monday 00:00 → next Monday 00:00 in city time zone. Counts `completed` bookings (organizer-confirmed) with slot start in week. Order: minutes desc, shifts desc, displayName asc, id asc. Opted-out users excluded from items.
- `GET /users/:id` → UserPublic + `{recentActivity:[{title, category, date, minutes}], sharedShiftsWithMe: 2}`

## Messages
- `GET /conversations` → `{items:[{id, opportunity:{id,title,photo}, organizationName, volunteer: compact, lastMessage:{body, createdAt, senderIsMe}, unread: 1}]}`
- `POST /conversations` `{opportunityId, bookingId?}` → conversation (get-or-create for volunteer; organizers use existing)
- `GET /conversations/:id/messages?after=<iso>` → `{items:[{id, clientId, body, sender: compact, senderIsMe, createdAt, isDemoReply}]}` asc
- `POST /conversations/:id/messages` `{clientId(uuid), body(1-1000)}` → message (idempotent on clientId)

## Certificates
- `GET /me/certificates` → `{items:[{id:"VD-7Q4K-2M9X", periodStart, periodEnd, minutes, shifts, issuedAt, status:"valid|superseded", currentMinutes, verifyUrl}]}`
- `POST /me/certificates` `{periodStart?: "YYYY-MM-DD", periodEnd?: "YYYY-MM-DD"}` (default all-time) → certificate. 409 `NO_CONFIRMED_HOURS`.
- `GET /certificates/:id/pdf` (owner) → application/pdf
- Public HTML: `GET /c/:id` verification page.

## Organizer (requires org role)
- `GET /org/mine` → `{items:[Organization + {role}]}`
- `GET /org/:orgId/opportunities` → `{items:[Opportunity]}` (all statuses)
- `POST /org/:orgId/opportunities` body = editable Opportunity fields (`title, category, summary, description, locationName, address, lat, lng, costCents, costNote, accessibility, preparation, arrivalLeadMinutes, arrivalNote, minCommitmentMinutes, contactName, contactEmail, contactPhone, status`) → Opportunity
- `PATCH /opportunities/:id` same fields (partial) → Opportunity
- `POST /opportunities/:id/cancel` `{reason}` → cancels opportunity & future slots; active bookings → `cancelled_by_org`
- `POST /opportunities/:id/photos` multipart `file` → Opportunity
- `POST /opportunities/:id/slots` `{startsAt, durationMinutes, capacity, repeatWeeks?(0-8)}` → `{items:[Slot]}` validation: future start, duration >= minCommitment, 5..480, capacity 1..200
- `PATCH /slots/:id` `{startsAt?, durationMinutes?, capacity?}` (capacity < bookedCount → CAPACITY_BELOW_BOOKINGS)
- `POST /slots/:id/cancel` → Slot
- `GET /slots/:id/roster` → `{slot, opportunity, items:[{bookingId, user: compact, status, checkedInAt, confirmedMinutes}]}`
- `POST /bookings/:id/checkin` → roster item
- `POST /bookings/:id/confirm` `{minutes(1..duration+120), note?}` → roster item (status completed; recompute stats/badges)
- `POST /bookings/:id/correct` `{minutes(0..), reason(required)}` → roster item (0 minutes => no_show)
- `POST /bookings/:id/no-show` → roster item
- `GET /bookings/:id/audit` → `{items:[{action, actor: compact, oldMinutes, newMinutes, reason, createdAt}]}`
Organizers cannot confirm/correct their own bookings (403).

## Web (non-API)
- `GET /` landing page, `GET /o/:opportunityId?ref=CODE`, `GET /i/:code` invite landing (install + register guidance, web registration form), `POST /join` web registration
- `GET /.well-known/assetlinks.json` (from env `ANDROID_CERT_SHA256`)
- `GET /media/...` uploaded/seed files
- `GET /health`
- `POST /admin/demo/reset` header `X-Admin-Token` (env `ADMIN_TOKEN`)
