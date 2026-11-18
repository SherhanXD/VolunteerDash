export const CATEGORIES = [
  { id: 'food', name: 'Food security', color: '#E8743B', icon: 'food' },
  { id: 'environment', name: 'Environment', color: '#2E9E5B', icon: 'environment' },
  { id: 'animals', name: 'Animal welfare', color: '#B5651D', icon: 'animals' },
  { id: 'education', name: 'Education & youth', color: '#1F4FE0', icon: 'education' },
  { id: 'seniors', name: 'Seniors', color: '#8E5BD6', icon: 'seniors' },
  { id: 'health', name: 'Health & wellness', color: '#D93F5C', icon: 'health' },
  { id: 'housing', name: 'Housing & repair', color: '#5C6B7A', icon: 'housing' },
  { id: 'arts', name: 'Arts & culture', color: '#F2A30F', icon: 'arts' },
];
export const CATEGORY_IDS = CATEGORIES.map((c) => c.id);

export const LEVELS = [
  { level: 1, name: 'Newcomer', minHours: 0 },
  { level: 2, name: 'Helper', minHours: 2 },
  { level: 3, name: 'Neighbor', minHours: 5 },
  { level: 4, name: 'Contributor', minHours: 10 },
  { level: 5, name: 'Champion', minHours: 20 },
  { level: 6, name: 'Pillar', minHours: 35 },
  { level: 7, name: 'Trailblazer', minHours: 50 },
  { level: 8, name: 'Luminary', minHours: 75 },
  { level: 9, name: 'Legend', minHours: 100 },
  { level: 10, name: 'Hometown Hero', minHours: 150 },
];

export function levelFor(confirmedMinutes) {
  const hours = (confirmedMinutes || 0) / 60;
  let idx = 0;
  for (let i = 0; i < LEVELS.length; i++) if (hours >= LEVELS[i].minHours) idx = i;
  const cur = LEVELS[idx];
  const next = LEVELS[idx + 1];
  const progress = next ? (hours - cur.minHours) / (next.minHours - cur.minHours) : 1;
  return {
    level: cur.level,
    name: cur.name,
    minHours: cur.minHours,
    nextLevelHours: next ? next.minHours : null,
    nextName: next ? next.name : null,
    progress: Math.round(Math.min(1, Math.max(0, progress)) * 1000) / 1000,
  };
}

const CATEGORY_BADGES = {
  food: ['pantry_pal', 'Pantry Pal'],
  environment: ['green_guardian', 'Green Guardian'],
  animals: ['animal_ally', 'Animal Ally'],
  education: ['bright_mentor', 'Bright Mentor'],
  seniors: ['kind_companion', 'Kind Companion'],
  health: ['health_helper', 'Health Helper'],
  housing: ['home_builder', 'Home Builder'],
  arts: ['arts_ambassador', 'Arts Ambassador'],
};

/**
 * Badge catalog. `check(m)` returns { earned, at, progressText } given metrics m from recompute.
 * m: { shifts:[{minutes, duration, category, at}], referrals:[{at}] } sorted by `at` ascending.
 */
export const BADGES = [
  {
    id: 'first_shift',
    name: 'First Shift',
    description: 'You showed up and made a difference.',
    rule: 'Complete your first organizer-confirmed shift.',
    tier: 'bronze',
    category: null,
    check: (m) => nth(m.shifts, 1, `${Math.min(m.shifts.length, 1)} of 1 shift`),
  },
  ...[
    [5, 'hours_5', 'Five-Hour Friend', 'bronze'],
    [10, 'hours_10', 'Ten-Hour Teammate', 'silver'],
    [25, 'hours_25', 'Quarter-Century Helper', 'silver'],
    [50, 'hours_50', 'Fifty-Hour Force', 'gold'],
    [100, 'hours_100', 'Century Volunteer', 'gold'],
  ].map(([h, id, name, tier]) => ({
    id,
    name,
    description: `${h} hours of confirmed community service.`,
    rule: `Reach ${h} organizer-confirmed volunteer hours.`,
    tier,
    category: null,
    check: (m) => {
      let total = 0;
      for (const s of m.shifts) {
        total += s.minutes;
        if (total >= h * 60) return { earned: true, at: s.at, progressText: `${h} of ${h} hours` };
      }
      return { earned: false, at: null, progressText: `${fmtHours(total)} of ${h} hours` };
    },
  })),
  ...Object.entries(CATEGORY_BADGES).map(([cat, [id, name]]) => ({
    id,
    name,
    description: `Helped out with a ${cat === 'food' ? 'food security' : cat} opportunity.`,
    rule: `Complete one organizer-confirmed shift in the ${cat} category.`,
    tier: 'bronze',
    category: cat,
    check: (m) => {
      const s = m.shifts.find((x) => x.category === cat);
      return s ? { earned: true, at: s.at, progressText: '1 of 1 shift' } : { earned: false, at: null, progressText: '0 of 1 shift' };
    },
  })),
  {
    id: 'explorer',
    name: 'Explorer',
    description: 'You tried four different kinds of volunteering.',
    rule: 'Complete confirmed shifts in 4 different categories.',
    tier: 'silver',
    category: null,
    check: (m) => {
      const seen = new Set();
      for (const s of m.shifts) {
        seen.add(s.category);
        if (seen.size >= 4) return { earned: true, at: s.at, progressText: '4 of 4 categories' };
      }
      return { earned: false, at: null, progressText: `${seen.size} of 4 categories` };
    },
  },
  {
    id: 'lunch_break_hero',
    name: 'Lunch-Break Hero',
    description: 'Short on time, big on impact.',
    rule: 'Complete 3 confirmed shifts of 15 minutes or less.',
    tier: 'silver',
    category: null,
    check: (m) => {
      const short = m.shifts.filter((s) => s.duration <= 15);
      return nth(short, 3, `${Math.min(short.length, 3)} of 3 shifts`);
    },
  },
  {
    id: 'community_connector',
    name: 'Community Connector',
    description: 'You brought a friend into volunteering.',
    rule: 'Invite 1 friend who joins with your invite code.',
    tier: 'special',
    category: null,
    check: (m) => nth(m.referrals, 1, `${Math.min(m.referrals.length, 1)} of 1 friend`),
  },
  {
    id: 'ambassador',
    name: 'Ambassador',
    description: 'Five friends joined because of you.',
    rule: 'Invite 5 friends who join with your invite code.',
    tier: 'gold',
    category: null,
    check: (m) => nth(m.referrals, 5, `${Math.min(m.referrals.length, 5)} of 5 friends`),
  },
];
export const BADGE_BY_ID = new Map(BADGES.map((b) => [b.id, b]));

function nth(list, n, progressText) {
  if (list.length >= n) return { earned: true, at: list[n - 1].at, progressText };
  return { earned: false, at: null, progressText };
}

function fmtHours(min) {
  const h = min / 60;
  return Number.isInteger(h) ? String(h) : h.toFixed(1);
}
