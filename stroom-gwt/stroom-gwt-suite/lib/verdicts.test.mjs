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
// The verdict functions, pinned — `node --test stroom-gwt-suite/lib/verdicts.test.mjs`.
//
// Every one of these cases is a correction that was actually made, most of them on the day the
// checker first ran against real screens. They are pinned because the cost of getting one wrong is
// not a red test: it is a GWT bug filed against a working form, or a real defect reported as a pass.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { assertedKind, clearedVerdict, illegalVerdict, planCleared } from './illegal.mjs';
import { ctrlEnterVerdict, enterVerdict, escapeVerdict } from './keys.mjs';
import { classifyAlert, isAlertCaption, recordedOutcome } from './alerts.mjs';
import { feedbackSaid, isFilterAction, pagerVerdict, sortVerdict, togglesRoundTrip } from './checks.mjs';
import { dirtyVerdict, valueVerdict } from './dirty.mjs';
import { dragVerdict, menubarVerdicts, resizeVerdict, spinnerVerdicts } from './mouse.mjs';
import { rangeVerdict } from './range.mjs';
import { contextVerdict } from './context.mjs';

const plan = (kind, label = 'Box', extra = {}) => [{ kind, label, text: 'x', target: true, ...extra }];

test('illegal: only a validation message is the form doing its job', () => {
  assert.equal(illegalVerdict({ outcome: 'alert', said: { class: 'validation', text: 'Invalid email address.' }, plan: plan('email') }).verdict, 'pass');
  assert.equal(illegalVerdict({ outcome: 'alert', said: { class: 'exception', text: 'Ambiguous URI path separator' }, plan: plan('text') }).verdict, 'fail');
  // An info/environment message means the value reached a request — #42's shape.
  assert.equal(illegalVerdict({ outcome: 'alert', said: { class: 'info', text: 'something else' }, plan: plan('text') }).verdict, 'fail');
});

test('illegal: acceptance is a fault only for an ASSERTED kind', () => {
  assert.equal(assertedKind('email'), true);
  assert.equal(assertedKind('time'), true);
  assert.equal(assertedKind('id'), true);
  // Demoted: the mined number rules belong to two particular boxes, not every numeric box.
  assert.equal(assertedKind('number'), false);
  assert.equal(assertedKind('text'), false);
  assert.equal(illegalVerdict({ outcome: 'closed', plan: plan('email') }).verdict, 'fail');
  assert.equal(illegalVerdict({ outcome: 'closed', plan: plan('number', 'Decimal Places') }).verdict, 'unchecked');
  assert.equal(illegalVerdict({ outcome: 'closed', plan: plan('text', 'Rule Name (optional)') }).verdict, 'unchecked');
});

test('illegal: assertedness is read from the table, not from what the capture stored', () => {
  // A capture taken while `number` was asserted must re-judge as unchecked now.
  assert.equal(illegalVerdict({ outcome: 'closed', plan: plan('number', 'Decimal Places', { asserted: true }) }).verdict, 'unchecked');
});

test('illegal: a Confirm is acceptance, not a rule', () => {
  const v = illegalVerdict({ outcome: 'dialog', plan: plan('time', 'Expiry Date') });
  assert.equal(v.verdict, 'fail');
  assert.match(v.why, /asked to confirm/);
});

test('illegal: a value the widget refused proves nothing', () => {
  const v = illegalVerdict({ outcome: 'closed', plan: plan('time'), landed: false });
  assert.equal(v.verdict, 'unchecked');
  assert.match(v.why, /did not take the value/);
});

test('illegal: a blocked write means the FORM accepted it', () => {
  const v = illegalVerdict({ outcome: 'nothing', blocked: ['POST /api/x'], plan: plan('text') });
  assert.equal(v.verdict, 'unchecked');
  assert.match(v.why, /no client-side rule/);
});

test('illegal: an inline message counts — the identity views do not fire alerts', () => {
  const v = illegalVerdict({ outcome: 'nothing', plan: plan('text'), inline: 'Password is required' });
  assert.equal(v.verdict, 'pass');
  assert.match(v.why, /inline/);
});

test('keys: Ctrl+Enter must not be a no-op where the button acts', () => {
  assert.equal(ctrlEnterVerdict({ keyOutcome: 'closed', okOutcome: 'closed' }).verdict, 'pass');
  // Pressed in sequence and re-synced between, so a flow's dialog can answer them differently —
  // `Create Processors` opens a pipeline chooser for one and closes for the other. Both acted.
  assert.equal(ctrlEnterVerdict({ keyOutcome: 'dialog', okOutcome: 'closed' }).verdict, 'pass');
  // OK doing nothing is the dead OK the `ok` checker reports; not a second finding here.
  assert.equal(ctrlEnterVerdict({ keyOutcome: 'dialog', okOutcome: 'nothing' }).verdict, 'pass');
  assert.equal(ctrlEnterVerdict({ keyOutcome: 'nothing', okOutcome: 'nothing' }).verdict, 'pass');
  // The contract's actual promise: the key is not a no-op where the button acts.
  const f = ctrlEnterVerdict({ keyOutcome: 'nothing', okOutcome: 'closed' });
  assert.equal(f.verdict, 'fail');
  assert.match(f.why, /KeyBinding:79/);
  assert.equal(ctrlEnterVerdict({ keyOutcome: 'nothing', okOutcome: 'closed', aceFocused: true }).verdict, 'unchecked');
});

test('keys: Escape closes, and a Confirm interposed is the close path running', () => {
  assert.equal(escapeVerdict({ before: 1, after: 0 }).verdict, 'pass');
  assert.equal(escapeVerdict({ before: 1, after: 2, said: { class: 'validation', text: 'There are unsaved changes' } }).verdict, 'pass');
  assert.equal(escapeVerdict({ before: 1, after: 1 }).verdict, 'fail');
});

test('keys: plain Enter has no contract to check', () => {
  assert.equal(enterVerdict({ outcome: 'nothing', field: 'Name' }).verdict, 'unchecked');
  assert.equal(enterVerdict({ outcome: 'closed', field: 'Name' }).verdict, 'pass');
});

test('alerts: a dialog is an alert when its CAPTION says so', () => {
  assert.equal(isAlertCaption('Alert'), true);
  assert.equal(isAlertCaption('Confirm'), false);
  // A capture taken while every new dialog was called an `alert` re-reads correctly.
  assert.equal(recordedOutcome('alert', { caption: 'Confirm' }), 'dialog');
  assert.equal(recordedOutcome('alert', { caption: 'Alert' }), 'alert');
  assert.equal(recordedOutcome('closed', { caption: 'Confirm' }), 'closed');
});

test('alerts: the classes that cost a bug report to get right', () => {
  assert.equal(classifyAlert({ text: 'anything', blocked: ['POST /api/x'] }), 'guard');
  // A request with no response is the instance (gwt-bugs #39), though its detail names an exception
  assert.equal(classifyAlert({
    text: 'The server did not respond. Check that Stroom is running and try again.',
    detail: 'http://localhost/api/x\n\norg.fusesource.restygwt.client.FailedStatusCodeException',
  }), 'environment');
  // A question icon is a Confirm the presenter asked on purpose.
  assert.equal(classifyAlert({ text: 'Unable to delete', icon: 'question' }), 'validation');
  assert.equal(classifyAlert({ text: 'Ambiguous URI path separator' }), 'exception');
  assert.equal(classifyAlert({ text: 'Connection refused' }), 'environment');
  assert.equal(classifyAlert({ text: 'A name must be provided' }), 'validation');
  // A parser complaining about the input is validation, not a defect: the user reads the sentence
  // and only the detail pane carries the exception class.
  assert.equal(classifyAlert({ text: 'Unexpected trailing equality', detail: 'TokenException{tokenType=GREATER_THAN}' }), 'validation');
});

test('checks: the pager is judged by direction and edge, since some grids clamp and some do not', () => {
  const p = (from, to, of_, enabled = {}) => ({ from, to, of: of_, text: `${from} to ${to} of ${of_}`,
    enabled: { First: true, Backward: true, Forward: true, Last: true, ...enabled } });
  // Adjacent pages.
  assert.equal(pagerVerdict('Forward', p(1, 100, 341), p(101, 200, 341)).verdict, 'pass');
  assert.equal(pagerVerdict('Backward', p(101, 200, 341), p(1, 100, 341)).verdict, 'pass');
  // A grid with an EXACT count clamps the last page to a full one (AbstractPager.setPageStart).
  assert.equal(pagerVerdict('Forward', p(1, 100, 152), p(53, 152, 152)).verdict, 'pass');
  assert.equal(pagerVerdict('Backward', p(53, 152, 152), p(1, 100, 152)).verdict, 'pass');
  // A grid whose count is an estimate shows a SHORT last page. Both are the widget working.
  assert.equal(pagerVerdict('Last', p(1, 100, 341), p(301, 341, 341)).verdict, 'pass');
  assert.equal(pagerVerdict('Backward', p(301, 341, 341), p(201, 300, 341)).verdict, 'pass');
  assert.equal(pagerVerdict('First', p(301, 341, 341), p(1, 100, 341)).verdict, 'pass');
  // Still catches the real failures.
  assert.equal(pagerVerdict('Forward', p(1, 100, 341), p(1, 100, 341)).verdict, 'fail');
  assert.equal(pagerVerdict('Forward', p(1, 100, 341), p(201, 300, 341)).verdict, 'fail');
  assert.equal(pagerVerdict('Backward', p(1, 100, 341, { Backward: false }), p(1, 100, 341)).verdict, 'pass');
});
test('checks: a sort is judged by the direction the header shows AFTER the click', () => {
  const cap = (direction, values) => ({ direction, values, rows: values.length });
  assert.equal(sortVerdict(null, cap('asc', ['a', 'b', 'c'])).verdict, 'pass');
  assert.equal(sortVerdict(null, cap('asc', ['b', 'a'])).verdict, 'fail');
  // A size column compares numerically, not as text: 9K sorts before 32K.
  assert.equal(sortVerdict(null, cap('asc', ['9K', '32K', '1M'])).verdict, 'pass');
  assert.equal(sortVerdict(null, cap('asc', ['x', 'x'])).verdict, 'unchecked');
  // The server sorts in the database's collation. Descending under a case-sensitive one puts every
  // lowercase word before every uppercase one; lower-casing first invented two fails on Properties.
  assert.equal(sortVerdict(null, cap('desc', ['user-agent', 'content-length', 'X-Forwarded-For', 'TOKEN'])).verdict, 'pass');
  assert.equal(sortVerdict(null, cap('asc', ['apple', 'Banana', 'cherry'])).verdict, 'pass');
  // Ordered under NEITHER collation is still a fail.
  assert.equal(sortVerdict(null, cap('asc', ['zebra', 'apple'])).verdict, 'fail');
  // A count is grouped: `44,730` is a number, and as text it would sort before `6,773`.
  assert.equal(sortVerdict(null, cap('desc', ['44,730', '6,773', '1,011', '52'])).verdict, 'pass');
  assert.equal(sortVerdict(null, cap('asc', ['52', '1,011', '6,773', '44,730'])).verdict, 'pass');
  // Only a fully grouped number counts — `1,234 items` is still text.
  assert.equal(sortVerdict(null, cap('asc', ['1,234 items', '2,000 items'])).verdict, 'pass');
});

test('checks: inline feedback is new text, or a box newly marked invalid', () => {
  assert.equal(feedbackSaid({ texts: [], invalid: 0 }, { texts: ['Password is short'], invalid: 1 }), 'Password is short');
  assert.equal(feedbackSaid({ texts: ['Password is short'], invalid: 1 }, { texts: ['Password is short'], invalid: 1 }), null);
  assert.match(feedbackSaid({ texts: [], invalid: 0 }, { texts: [], invalid: 2 }), /2 box/);
});

test('checks: the Filter rule applies only to a dialog that filters', () => {
  assert.equal(isFilterAction('Filter'), true);
  assert.equal(isFilterAction('Filter Documents To Apply Permissions Changes On'), true);
  // `Add XPath Filter` adds a row by design — accusing it of growing the grid was a scoping bug.
  assert.equal(isFilterAction('Add XPath Filter'), false);
  assert.equal(isFilterAction('Manage Step Filters'), false);
  assert.equal(isFilterAction('New Field'), false);
});

test('dirty: Save is the document\'s flag, and silence is not a defect', () => {
  const off = { enabled: false };
  const on = { enabled: true };
  assert.equal(dirtyVerdict({ before: off, after: on, field: 'Classification' }).verdict, 'pass');
  assert.equal(dirtyVerdict({ before: null, after: null }).verdict, 'unchecked');
  assert.equal(dirtyVerdict({ before: on, after: on }).verdict, 'unchecked');
  // `Permission Visibility` is a VIEW control — changing it should leave the document clean, and a
  // driver cannot tell that from a field whose handler is missing. So: never a fail.
  const quiet = dirtyVerdict({ before: off, after: off, field: 'Permission Visibility' });
  assert.equal(quiet.verdict, 'unchecked');
  assert.match(quiet.why, /view control/);
});

test('checks: only a pair that undoes itself is a toggle round-trip', () => {
  assert.equal(togglesRoundTrip('Ask Stroom AI'), true);
  assert.equal(togglesRoundTrip('Turn Auto Refresh On'), true);
  // Both are absolute: Collapse All closes folders that were open before Expand All ran.
  assert.equal(togglesRoundTrip('Expand All'), false);
  assert.equal(togglesRoundTrip('Collapse All'), false);
});

// ── The mouse gestures (lib/mouse.mjs) ──────────────────────────────────────

test('drag: a dialog that does not move is a fail, and a leaked glass is too', () => {
  const ok = { asked: { dx: -60, dy: 40 }, got: { dx: -60, dy: 40 }, glassWhilePressed: true,
    glassAfterRelease: false, resized: false, stillOpen: true };
  assert.equal(dragVerdict(ok).verdict, 'pass');
  // Nothing moved: three handlers are registered and the press was on the caption.
  assert.equal(dragVerdict({ ...ok, got: { dx: 0, dy: 0 } }).verdict, 'fail');
  // The glass is removed by Glass.hide(); still attached means it covers the whole page.
  const leak = dragVerdict({ ...ok, glassAfterRelease: true });
  assert.equal(leak.verdict, 'fail');
  assert.match(leak.why, /glass/);
  // Dragging the caption must not resize it as well.
  assert.equal(dragVerdict({ ...ok, resized: true }).verdict, 'fail');
  // A drag that would hit the 22px clamp asserts nothing rather than guessing.
  assert.equal(dragVerdict({ skipped: 'the clamp would apply' }).verdict, 'unchecked');
  assert.equal(dragVerdict(null).verdict, 'unchecked');
  // A dialog that closed mid-drag proves nothing either way.
  assert.equal(dragVerdict({ ...ok, stillOpen: false }).verdict, 'unchecked');
});

test('drag: moving by the wrong amount still exercised the handlers', () => {
  // It moved, so press-move-release ran. How far is a weaker claim than this driver should make.
  const v = dragVerdict({ asked: { dx: -60, dy: 40 }, got: { dx: -58, dy: 40 },
    glassAfterRelease: false, resized: false, stillOpen: true });
  assert.equal(v.verdict, 'pass');
  assert.match(v.why, /where -60,40 was asked/);
});

test('resize: no change is UNCHECKED, because isResizable is invisible to the DOM', () => {
  const base = { asked: { dw: 70, dh: 50 }, stillOpen: true, glassAfterRelease: false };
  assert.equal(resizeVerdict({ ...base, got: { dw: 70, dh: 50 } }).verdict, 'pass');
  const quiet = resizeVerdict({ ...base, got: { dw: 0, dh: 0 } });
  assert.equal(quiet.verdict, 'unchecked');
  assert.match(quiet.why, /may not be resizable/);
  assert.equal(resizeVerdict({ skipped: 'no SE resize handle' }).verdict, 'unchecked');
  assert.equal(resizeVerdict({ ...base, got: { dw: 70, dh: 50 }, glassAfterRelease: true }).verdict, 'fail');
});

test('spinner: four gestures, four verdicts, and a disabled arrow proves nothing', () => {
  const ev = {
    rest: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '5' },
    hovered: { cls: 'valueSpinner-arrow valueSpinner-arrowUpHover', value: '5' },
    left: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '5' },
    pressed: { cls: 'valueSpinner-arrow valueSpinner-arrowUpPressed', value: '6' },
    released: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '6' },
  };
  const v = spinnerVerdicts(ev, 'Partition Size');
  assert.deepEqual(v.map((x) => x.kind), ['mouseover', 'mouseout', 'mousedown', 'mouseup']);
  assert.ok(v.every((x) => x.verdict === 'pass'));
  // Every handler is wrapped in `if (enabled)`, so a disabled arrow is unchecked, never a fail.
  const off = spinnerVerdicts({ skipped: 'the arrow is disabled' }, 'Retain For');
  assert.equal(off.length, 1);
  assert.equal(off[0].verdict, 'unchecked');
});

test('spinner: a press that moves the value counts even without the pressed class', () => {
  const ev = {
    rest: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '5' },
    hovered: { cls: 'valueSpinner-arrow valueSpinner-arrowUpHover', value: '5' },
    left: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '5' },
    // The class was missed (read after the repeat timer reset it) but the value moved: increase() ran.
    pressed: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '6' },
    released: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '6' },
  };
  const down = spinnerVerdicts(ev, 'Partition Size').find((x) => x.kind === 'mousedown');
  assert.equal(down.verdict, 'pass');
  assert.match(down.why, /without setting/);
  // Neither the class nor the value: that is the handler not running.
  const dead = spinnerVerdicts({ ...ev, pressed: { cls: 'valueSpinner-arrow valueSpinner-arrowUp', value: '5' } },
    'Partition Size').find((x) => x.kind === 'mousedown');
  assert.equal(dead.verdict, 'fail');
});

test('menu bar: the hover oracle is the exact opacity MenubarItem sets', () => {
  const v = menubarVerdicts({ rest: null, over: 0.3, out: 0 }, 'Administration');
  assert.ok(v.every((x) => x.verdict === 'pass'));
  assert.equal(menubarVerdicts({ over: null, out: 0 }, 'x')[0].verdict, 'fail');
  assert.equal(menubarVerdicts({ over: 0.3, out: 0.3 }, 'x')[1].verdict, 'fail');
});

test('value: a control that did not move fired nothing, and that is not a defect', () => {
  const moved = valueVerdict({ before: '1', after: '2', label: 'Partition Size', kind: 'spinner', how: 'stepped the spinner' });
  assert.equal(moved.verdict, 'pass');
  assert.match(moved.why, /ValueChangeEvent fired/);
  // A number box handed a letter: the box is working, so this is unchecked and never a fail.
  const refused = valueVerdict({ before: '1000000000', after: '1000000000', label: 'Max Docs Per Shard', kind: 'text' });
  assert.equal(refused.verdict, 'unchecked');
  assert.match(refused.why, /did not accept/);
  assert.equal(valueVerdict({ before: 'a', after: null, label: 'x', kind: 'text' }).verdict, 'unchecked');
  // Empty-to-filled counts; so does filled-to-empty.
  assert.equal(valueVerdict({ before: '', after: 'x', label: 'x', kind: 'text' }).verdict, 'pass');
  assert.equal(valueVerdict({ before: 'x', after: '', label: 'x', kind: 'text' }).verdict, 'pass');
  // Never a fail, whatever happens.
  for (const c of [{ before: '1', after: '1' }, { before: '1', after: null }, { before: '1', after: '2' }]) {
    assert.notEqual(valueVerdict({ ...c, label: 'x', kind: 'text' }).verdict, 'fail');
  }
});

test('cleared: a form that opens blank is the walk\'s own OK, so only a pre-filled one is planned', () => {
  assert.deepEqual(planCleared([{ i: 0, label: 'Name', value: '' }, { i: 1, label: 'Age', value: '  ' }]), []);
  const v = planCleared([{ i: 0, label: 'Name', value: '' }, { i: 1, label: 'Expiry', value: '2026-01-01' }]);
  assert.equal(v.length, 1);
  // Every box is emptied, and the TARGET is the first that held something.
  assert.ok(v[0].every((f) => f.text === ''));
  assert.deepEqual(v[0].map((f) => f.target), [false, true]);
  assert.equal(v[0][1].kind, 'cleared');
});

test('cleared: a dead OK is a FAIL here, unlike an illegal value with no rule', () => {
  // The one place this verdict departs from illegalVerdict, and the reason it exists (#43, #46).
  const dead = clearedVerdict({ outcome: 'nothing', label: 'Expiry' });
  assert.equal(dead.verdict, 'fail');
  assert.match(dead.why, /did nothing/);
  assert.equal(illegalVerdict({ outcome: 'nothing', plan: [{ kind: 'text', target: true }] }).verdict, 'unchecked');
  // A rule firing is the form working.
  assert.equal(clearedVerdict({ outcome: 'alert', said: { class: 'validation', text: 'A name is required' } }).verdict, 'pass');
  assert.equal(clearedVerdict({ outcome: 'nothing', inline: 'Name is required' }).verdict, 'pass');
  // Accepting an empty box is legitimate — it may be optional.
  assert.equal(clearedVerdict({ outcome: 'closed', label: 'Notes' }).verdict, 'unchecked');
  // An exception, or a non-rule message, is a defect.
  assert.equal(clearedVerdict({ outcome: 'alert', said: { class: 'exception', text: 'NPE' } }).verdict, 'fail');
  assert.equal(clearedVerdict({ outcome: 'alert', said: { class: 'info', text: 'Done' } }).verdict, 'fail');
  // A box that would not clear proves nothing; a blocked write means no client-side rule.
  assert.equal(clearedVerdict({ outcome: 'nothing', landed: false }).verdict, 'unchecked');
  assert.equal(clearedVerdict({ outcome: 'closed', blocked: ['PUT /api/x'] }).verdict, 'unchecked');
});

test('range: the pager\'s row jump, and the two ways it can be wrong', () => {
  const before = { from: 1, to: 100, of: 500 };
  assert.equal(rangeVerdict({ asked: 2, opened: true, before, after: { from: 2, to: 101, of: 500 } }).verdict, 'pass');
  // Asked for row 2, range unmoved: the Enter handler did not fire.
  const dead = rangeVerdict({ asked: 2, opened: true, before, after: { from: 1, to: 100, of: 500 } });
  assert.equal(dead.verdict, 'fail');
  assert.match(dead.why, /left the range at 1-100/);
  // The label click not opening the editor is its own failure, and a distinct one.
  const shut = rangeVerdict({ opened: false, skipped: 'clicking the from label did not open the editor' });
  assert.equal(shut.verdict, 'fail');
  assert.match(shut.why, /did not open the range editor/);
  // Moved, but rounded to a page boundary: the pager is allowed to do that.
  const rounded = rangeVerdict({ asked: 2, opened: true, before, after: { from: 101, to: 200, of: 500 } });
  assert.equal(rounded.verdict, 'pass');
  assert.match(rounded.why, /may round to a page/);
  // A single-page grid has nowhere to jump, so it asserts nothing.
  assert.equal(rangeVerdict({ skipped: 'one page only (3 row(s))' }).verdict, 'unchecked');
  assert.equal(rangeVerdict(null).verdict, 'unchecked');
  assert.equal(rangeVerdict({ asked: 2, opened: true, before, after: null }).verdict, 'unchecked');
});

test('context: a menu is the handler running; no menu is normal; a stuck menu is a fail', () => {
  const open = contextVerdict({ what: 'an explorer row', text: 'My Feed', before: 0, after: 7, items: ['New', 'Copy'], left: 0 });
  assert.equal(open.verdict, 'pass');
  assert.match(open.why, /opened a menu/);
  // Most elements have no context menu, and a registration says one EXISTS on the view, not that
  // every pixel answers — so silence is never a defect here.
  assert.equal(contextVerdict({ what: 'the editor', before: 0, after: 0, items: [], left: 0 }).verdict, 'unchecked');
  // A menu left on screen is a real problem: the walk's next click lands inside it.
  const stuck = contextVerdict({ what: 'a grid row', before: 0, after: 5, items: [], left: 5 });
  assert.equal(stuck.verdict, 'fail');
  assert.match(stuck.why, /would not close/);
  assert.equal(contextVerdict({ skipped: 'nothing on this screen worth right-clicking' }).verdict, 'unchecked');
  assert.equal(contextVerdict(null).verdict, 'unchecked');
});
