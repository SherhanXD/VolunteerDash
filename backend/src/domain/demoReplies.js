import crypto from 'node:crypto';
import { tx } from '../db.js';
import { randomId } from '../util/security.js';

function firstName(n) {
  return String(n || '').split(/\s+/)[0] || 'there';
}

/** Friendly, relevant canned reply from a demo organization's coordinator. */
export function demoReplyText(volunteerName, opp, orgName, incoming) {
  const t = String(incoming || '').toLowerCase();
  const hi = `Hi ${firstName(volunteerName)}! Thanks for your message about "${opp.title}".`;
  const arrival = opp.arrival_lead_minutes
    ? `Please plan to arrive ${opp.arrival_lead_minutes} minutes before your slot starts. ${opp.arrival_note || ''}`.trim()
    : opp.arrival_note || `Just come to ${opp.location_name} a couple of minutes before your slot starts.`;
  const prep = opp.preparation ? `Quick prep tip: ${opp.preparation}` : '';
  let middle;
  if (/park|parking|car|bus|light rail|bike/.test(t)) {
    middle = `Getting to ${opp.location_name} (${opp.address}) is easy. There's free parking nearby and the Orbit shuttles stop close by.`;
  } else if (/late|running behind|traffic/.test(t)) {
    middle = `No worries if you're running a few minutes late. Just check in with the coordinator when you arrive.`;
  } else if (/cancel|can't make|cannot make|sick/.test(t)) {
    middle = `Totally understood. You can cancel from the booking screen so someone else can take the spot. Hope to see you another time!`;
  } else if (/kid|child|son|daughter|family/.test(t)) {
    middle = `Families are welcome when an adult stays with their kids the whole time. Let us know how many are coming.`;
  } else if (/bring|wear|need|shoes|clothes/.test(t)) {
    middle = 'You do not need to bring much. We provide gloves, supplies, and water.';
  } else {
    middle = `We're glad you're joining us at ${opp.location_name}.`;
  }
  return [hi, middle, arrival, prep, `See you there! (${orgName})`].filter(Boolean).join(' ');
}

/** Send any due auto-replies. Safe to run repeatedly and after restarts (state is in the DB). */
export function processDueDemoReplies(ctx) {
  const now = ctx.now().toISOString();
  const due = ctx.q.all(
    `SELECT m.*, c.opportunity_id, c.volunteer_id FROM messages m JOIN conversations c ON c.id = m.conversation_id
      WHERE m.reply_due_at IS NOT NULL AND m.reply_due_at <= ? ORDER BY m.reply_due_at LIMIT 50`,
    now,
  );
  let sent = 0;
  for (const m of due) {
    tx(ctx.db, () => {
      const still = ctx.q.get('SELECT reply_due_at FROM messages WHERE id = ?', m.id);
      if (!still || !still.reply_due_at) return;
      ctx.q.run('UPDATE messages SET reply_due_at = NULL WHERE id = ?', m.id);
      const opp = ctx.q.get('SELECT * FROM opportunities WHERE id = ?', m.opportunity_id);
      const org = opp && ctx.q.get('SELECT * FROM organizations WHERE id = ?', opp.org_id);
      if (!opp || !org || !org.is_demo) return;
      const sender = ctx.q.get(
        `SELECT u.id FROM org_members mb JOIN users u ON u.id = mb.user_id
          WHERE mb.org_id = ? AND u.is_demo = 1 AND u.deleted_at IS NULL ORDER BY mb.created_at LIMIT 1`,
        org.id,
      );
      if (!sender) return;
      const vol = ctx.q.get('SELECT display_name FROM users WHERE id = ?', m.volunteer_id);
      const createdAt = new Date(Math.max(Date.parse(now), Date.parse(m.created_at) + 1)).toISOString();
      ctx.q.run(
        `INSERT INTO messages(id, conversation_id, sender_id, client_id, body, is_demo_reply, created_at)
         VALUES (?, ?, ?, ?, ?, 1, ?)`,
        randomId('msg'),
        m.conversation_id,
        sender.id,
        `auto-${m.id}-${crypto.randomUUID()}`,
        demoReplyText(vol?.display_name, opp, org.name, m.body),
        createdAt,
      );
      ctx.q.run('UPDATE conversations SET last_message_at = ? WHERE id = ?', createdAt, m.conversation_id);
      sent++;
    });
  }
  return sent;
}
