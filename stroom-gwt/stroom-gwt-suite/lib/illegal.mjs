/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Illegal values — driving a form's OWN rules (BEHAVIOUR-PLAN.md § B3).
//
// The walk already answers a blank form: OK, "a name is required", fill a seed name, OK again. That
// exercises one row of a form's matrix — the blank row. The rest of the matrix is the values a form
// REJECTS, and `oracles/handler-inventory.md` mines them from the presenters that compose them:
//
//   ScheduledProcessEditPresenter:185  "Invalid start time"      getStartTime().isValid()
//   ProfilePeriodEditPresenter:80      "Invalid start time"      same shape
//   BatchExecutionScheduleEditPresenter:121/123                  same shape
//   AddEventLinkPresenter:56           "Invalid event id '…'"    name.split(":").length != 2
//   EditAccountPresenter:143           "A user id must be at least 3 characters."
//   EditAccountPresenter:146           "Invalid email address."
//   EditApiKeyPresenter:251/254        "API Key expiry date cannot be in the past …"
//
// Every one of them is the same act: type something the field cannot hold, press OK, and read what
// the presenter says. So the driver is a table of ILLEGAL values by the KIND of box — decided from
// the box's own label, its type and what it already holds — and the check is that OK answered with
// a validation message rather than accepting it or throwing.
//
// The value is chosen to be illegal for its kind and NON-BLANK for every other kind's rule, so a
// form whose name is blank and whose start time is bad reports the START TIME: filling every box at
// once walks past the blank-check row (already covered) and onto the row underneath it.

/** The topmost popup that is a FORM (an alert has `.alert-message`), and its fillable text boxes. */
export function readFields(page) {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const form = [...popups].reverse().find((p) => !p.querySelector('.alert-message'));
    if (!form) return null;
    const caption = norm(form.querySelector('.dialog-titleText')?.textContent);
    const boxes = [...form.querySelectorAll('input[type="text"], input[type="number"], input:not([type]), .gwt-TextBox, textarea')]
      .filter((e) => e.offsetWidth > 0 && e.offsetHeight > 0 && !e.disabled && !e.readOnly
        && !e.classList.contains('quickFilter-textBox')
        // ACE's hidden input IS a textarea. Typing into it goes to the editor, sets no `.value`,
        // and moves focus somewhere the popup framework deliberately ignores keys — so a dialog
        // hosting an editor had its editor probed as if it were the form's first field.
        && !e.classList.contains('ace_text-input') && !e.closest('.ace_editor'));
    return {
      caption,
      fields: boxes.map((e, i) => {
        // GWT lays a form out as FormGroup: `.form-group` > `.form-group-label` + the widget. Fall
        // back to a placeholder, a title, or the nearest preceding label-ish text.
        const group = e.closest('.form-group');
        const label = norm(group?.querySelector('.form-group-label')?.textContent)
          || norm(e.getAttribute('placeholder')) || norm(e.getAttribute('title'))
          || norm(e.getAttribute('aria-label')) || '';
        const r = e.getBoundingClientRect();
        return { i, label, value: String(e.value ?? ''), type: e.getAttribute('type') || e.tagName.toLowerCase(),
          x: r.x + r.width / 2, y: r.y + r.height / 2 };
      }),
    };
  }).catch(() => null);
}

/**
 * What to type, by the kind of box. First match wins, and each rule names the message it is aiming
 * at so a run that never provokes it is a gap in the DRIVER, not an unclassified blank.
 */
export const ILLEGAL_RULES = [
  { kind: 'email', asserted: true, when: (f) => /e-?mail/i.test(f.label), text: 'not-an-email',
    expects: 'Invalid email address (EditAccountPresenter:146)' },
  { kind: 'time', asserted: true, when: (f) => /\b(time|date|expir|from|to|when)\b/i.test(f.label) || /^\d{4}-\d{2}-\d{2}T/.test(f.value),
    expects: 'Invalid start/end time (ScheduledProcessEditPresenter:185)' },
  // NOT asserted. The mined messages for a bad number belong to two particular boxes (a global
  // property's value, a dashboard's refresh interval), not to every numeric box in the app: the
  // dashboard column format's `Decimal Places` took `9z9` and closed, and calling that a defect is
  // the same over-claim the free-text probe already made. What a number box does with rubbish is
  // answered by B4's round-trip, not by whether the dialog shut.
  { kind: 'number', asserted: false, when: (f) => f.type === 'number' || /^-?[\d.]+$/.test(f.value.trim())
      || /\b(count|size|limit|port|days|number|interval|depth|priority|max|min|timeout)\b/i.test(f.label),
    text: '9z9', expects: 'a number that will not parse (ManageGlobalPropertyEditPresenter, DashboardPresenter refresh interval)' },
  { kind: 'id', asserted: true, when: (f) => /\bid\b/i.test(f.label), text: 'ab',
    expects: "a user id under 3 characters / an event id without a ':' (EditAccountPresenter:143, AddEventLinkPresenter:56)" },
  // The default, and NOT asserted: non-blank (so blank-checks pass) and carrying characters a
  // name, a path or a regex is not expected to hold — but no presenter declares a rule against
  // them, so a form that accepts `a/b*?"<>` in a free-text box is within its rights. The probe is
  // exploratory here: what it is looking for is a form that THROWS on it (#42), not one that
  // shrugs.
  { kind: 'text', asserted: false, when: () => true, text: 'a/b*?"<>',
    expects: 'illegal characters in a name / an unparsable expression' },
];

/** A value for a time box that is a plausible shape and still not a time. */
const ILLEGAL_TIME = 'not-a-time';

/** One illegal value per fillable box, most specific rule first. */
/** A value each kind of box will ACCEPT, so the rule under test is the only one that can fire. */
const LEGAL = {
  email: 'seed@example.com', time: '2030-01-01T00:00:00.000Z', number: '1', id: 'seeduser',
  text: 'Seed value',
};

/** The kind of a box, and what is illegal for it. */
function kindOf(f) {
  const rule = ILLEGAL_RULES.find((r) => r.when(f));
  return rule && { kind: rule.kind, asserted: !!rule.asserted, illegal: rule.text ?? ILLEGAL_TIME, expects: rule.expects };
}

/**
 * ONE VARIANT PER RULE. A form reports the FIRST rule that fails, so filling every box with an
 * illegal value only ever exercises one row of its matrix — `EditAccountPresenter` would answer
 * "A user id must be at least 3 characters." for ever and never reach "Invalid email address."
 * (`:143` is checked before `:146`). So each variant makes exactly ONE box illegal and gives the
 * others something they accept; a box that already holds a value keeps it, because whatever the
 * form loaded there is legal by construction.
 *
 * One variant per ASSERTED box (a kind some presenter declares a message for), at most three. A
 * form with no asserted box gets a single variant with the generic text probe in the first box —
 * that is the one hunting a form that throws rather than a form that refuses (#42).
 */
/**
 * Is a kind's rule ASSERTED — does some presenter declare a message for it? Read from the table
 * rather than from what a capture recorded, so demoting a kind re-judges every capture already
 * taken (coverage.mjs re-runs `illegalVerdict` over the recorded plan).
 */
export const assertedKind = (kind) => !!ILLEGAL_RULES.find((r) => r.kind === kind)?.asserted;

export function planIllegal(fields = []) {
  const typed = fields.map((f) => ({ ...f, ...(kindOf(f) ?? {}) })).filter((f) => f.kind);
  if (!typed.length) return [];
  const targets = typed.filter((f) => f.asserted).slice(0, 3);
  const variantFor = (target) => typed.map((f) => ({
    ...f,
    target: f.i === target.i,
    text: f.i === target.i ? f.illegal : (f.value || LEGAL[f.kind] || LEGAL.text),
  }));
  return (targets.length ? targets : [typed[0]]).map(variantFor);
}

/**
 * Did the form REJECT it? `said` is `readTopDialogBody` after the OK, `outcome` the walker's class.
 *   pass       a validation message — the presenter's own rule, and its text is the evidence
 *   fail       an exception (a rule that threw), or OK accepted a value every rule forbids
 *   unchecked  the guard stopped the write, or the form has no rule for this box (silence)
 */
export function illegalVerdict({ outcome, said, blocked = [], plan = [], landed = true, inline = null }) {
  // The variant's TARGET is the box under test; the rest hold values the form accepts.
  const aimed = plan.filter((p) => p.target);
  const kinds = (aimed.length ? aimed : plan).map((p) => p.kind).join(', ');
  // The box refused or normalised what was typed, so nothing about the form's rules was tested —
  // a `DateTimeBox` that will not hold `not-a-time` is a widget doing its job.
  if (!landed) return { verdict: 'unchecked', why: `the ${kinds} box did not take the value — nothing was driven` };
  // Inline is a message too: the identity views write into a `.feedback` label rather than firing
  // an alert (lib/checks.mjs).
  if (inline) return { verdict: 'pass', why: `rejected inline: ${String(inline).slice(0, 90)}` };
  // A form's rules run in `onHideRequest`, BEFORE the request goes out. So a blocked request means
  // the form ACCEPTED the illegal value and the only rule that could still refuse it is the
  // server's — which the guard is there to stop us finding out. That is `unchecked`, but it is not
  // nothing: it says this form has no client-side rule for these boxes.
  if (blocked.length) return { verdict: 'unchecked', why: `the form accepted illegal ${kinds} and the guard stopped the write — no client-side rule here` };
  if (outcome === 'alert' && said?.class === 'exception') {
    return { verdict: 'fail', why: `illegal ${kinds} threw: ${String(said.text).slice(0, 90)}` };
  }
  // Only a VALIDATION message is the form doing its job. An illegal value that produces an `info`
  // or `environment` message got past the form and into a request, and what the user is reading is
  // whatever answered it — that is how the unencoded `fetchByName/{name}` path was found.
  if (outcome === 'alert' && said?.class === 'validation') {
    return { verdict: 'pass', why: `rejected: ${String(said.text).slice(0, 90)}` };
  }
  if (outcome === 'alert') {
    return { verdict: 'fail', why: `answered with a ${said?.class ?? 'plain'} message, not a rule: ${String(said?.text ?? '').slice(0, 80)}` };
  }
  // A CONFIRM is not a rule: the form took the value and is asking before it writes, which is the
  // same thing as accepting it.
  if (outcome === 'closed' || outcome === 'dialog') {
    // Only an ASSERTED kind makes acceptance a fault: a presenter declares a message for a bad
    // email, time, number or id, so taking one is a defect. The generic `text` probe asserts
    // nothing — a free-text name is allowed to hold odd characters.
    const claimed = (aimed.length ? aimed : plan).filter((p) => assertedKind(p.kind));
    const how = outcome === 'dialog' ? 'accepted (and asked to confirm)' : 'accepted';
    if (!claimed.length) return { verdict: 'unchecked', why: `${how} ${plan.map((p) => `${p.label || 'box'}="${p.text}"`).join(', ')} — no rule forbids it` };
    return { verdict: 'fail', why: `${how} illegal ${claimed.map((p) => p.kind).join(', ')}: ${claimed.map((p) => `${p.label || 'box'}="${p.text}"`).join(', ')}` };
  }
  if (outcome === 'nothing') return { verdict: 'unchecked', why: `no rule for ${kinds} — OK was silent and the form stayed open` };
  return { verdict: 'unchecked', why: `illegal ${kinds} → ${outcome}` };
}

/**
 * The CLEARED form — every box emptied, then OK.
 *
 * The walk already answers a form that OPENS blank: its own OK press is the blank row, and that is
 * how `#46 Save New Tab Session` was found. What nothing covers is a form that opens PRE-FILLED and
 * is then emptied, because the OK checker only ever presses OK in the state the dialog arrived in.
 * An Edit dialog is exactly that shape, and so was `#43` — an API key whose expiry was cleared left
 * OK dead, because the presenter unboxed a null `Long`.
 *
 * Returns one variant, or none when every box is already empty (the walk's own OK covers that) — and
 * the target is the first box that HELD something, since that is the one being taken away.
 */
export function planCleared(fields = []) {
  const filled = fields.filter((f) => String(f.value ?? '').trim() !== '');
  if (!filled.length) return [];
  const first = filled[0];
  return [fields.map((f) => ({ ...f, kind: 'cleared', target: f.i === first.i, text: '', expects: 'a message naming the field it needs' }))];
}

/**
 * The cleared form's verdict. It differs from `illegalVerdict` in exactly one place, and that place
 * is the point of it: OK doing NOTHING is a FAIL here, where an illegal value leaves it unchecked.
 *
 * The reasoning is that emptiness is not an exotic value a form may have no rule for. Either a box
 * was required — and the form owes a message saying so — or it was optional, and OK owes the save it
 * would have done anyway. Silence with the dialog still open is neither, and it is the defect family
 * already filed three times over (#40, #41, #46): a button that looks live and does nothing.
 */
export function clearedVerdict({ outcome, said, blocked = [], landed = true, inline = null, label = '' }) {
  const where = label ? `"${label}"` : 'the form';
  if (!landed) return { verdict: 'unchecked', why: `${where} would not clear — nothing was driven` };
  if (inline) return { verdict: 'pass', why: `clearing ${where} was rejected inline: ${String(inline).slice(0, 80)}` };
  if (blocked.length) return { verdict: 'unchecked', why: `the form accepted an empty ${where} and the guard stopped the write — no client-side rule here` };
  if (outcome === 'alert' && said?.class === 'exception') {
    return { verdict: 'fail', why: `clearing ${where} threw: ${String(said.text).slice(0, 90)}` };
  }
  if (outcome === 'alert' && said?.class === 'validation') {
    return { verdict: 'pass', why: `clearing ${where} was rejected: ${String(said.text).slice(0, 90)}` };
  }
  if (outcome === 'alert') {
    return { verdict: 'fail', why: `clearing ${where} answered with a ${said?.class ?? 'plain'} message, not a rule: ${String(said?.text ?? '').slice(0, 80)}` };
  }
  // Accepted: the box was optional, which is a legitimate answer and not this driver's business.
  if (outcome === 'closed' || outcome === 'dialog') {
    return { verdict: 'unchecked', why: `the form accepted an empty ${where} — it may be optional` };
  }
  if (outcome === 'nothing') {
    return { verdict: 'fail', why: `OK did nothing after clearing ${where}, and said nothing — the dialog is still open` };
  }
  return { verdict: 'unchecked', why: `clearing ${where} → ${outcome}` };
}
