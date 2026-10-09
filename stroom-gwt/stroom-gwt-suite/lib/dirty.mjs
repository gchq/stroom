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
// Does typing into a document's settings mark it DIRTY? — BEHAVIOUR-PLAN.md § B3, the second driver.
//
// 112 of the 303 unexercised `value` handlers live in `*Settings*` views: the Feed's Settings tab,
// Elastic cluster and index settings, Index settings, Pathways settings, the scheduled process
// editors. Those are document editor TABS, and a tab has no OK — it is saved by the document's
// Save button — so the illegal-value driver, which runs on a dialog's OK, can never reach them.
//
// The oracle is one line of GWT, and it holds for every document editor in the application:
//
//     DocTabPresenter.onDirty():274      saveButton.setEnabled(isDirty());
//
// So Save is enabled if and only if the document is dirty, and "typing into this box marks the
// document dirty" is checkable on any editor tab without knowing which presenter is behind it.
// That is also exactly what `addValueChangeHandler` and `addDirtyHandler` are FOR — the 303 and the
// 15 — so driving it is what moves them.
//
// Undo: the box is put back to what it held. The document stays dirty (GWT's flag is one-way until
// a save or a reload), which the walker already copes with — closing a dirty tab asks to discard
// and `discardIfAsked` answers it.

/** The Save button's state on the editor behind any open dialog: present, and enabled or not. */
export function readSave(page) {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const un = (t) => { const s = norm(t); return /^(\S+)( \1)+$/.test(s) ? s.split(' ')[0] : s; };
    // The document's toolbar, NOT a dialog's — a dirty check is about the tab underneath.
    const btns = [...document.querySelectorAll('button, [role=button], .Button')].filter((b) => b.offsetWidth > 0
      && !b.closest('.dialog-popup, .resizableDialog-popup'));
    const save = btns.find((b) => un(b.getAttribute('title') ?? '') === 'Save'
      || un(b.getAttribute('aria-label') ?? '') === 'Save' || un(b.textContent) === 'Save');
    if (!save) return null;
    return { enabled: !save.disabled && !save.classList.contains('disabled') };
  }).catch(() => null);
}

/**
 * Every editable SETTINGS control on the tab itself (not in a dialog), by kind: `text`, `tick`,
 * `select`, `chooser` or `ace`. The label on its `.form-group` is the signature of a document field — an unlabelled box
 * is a quick filter, a pager or an expression term, and belongs to a widget rather than to the
 * document. Kind matters because they are driven differently, and because a Feed's Settings tab is
 * eleven fields of which only three are text: reading text boxes alone reaches a quarter of them.
 */
export function readTabFields(page, { inDialogScope = false } = {}) {
  return page.evaluate(({ inDialogScope }) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    /**
     * Normally a field inside a dialog is somebody else's business — the illegal-value driver owns
     * dialogs, and a dialog's boxes are not the document's. But a TAB INSIDE A DIALOG is neither: Ask
     * Stroom AI › Configure › General is an `editor-tab` node with no Save button, so the dirty driver
     * bailed, and its fields sit inside a popup, so this reader skipped them. Nothing drove them at
     * all, and their views stayed `needs-value` for want of anyone looking. In that scope the topmost
     * dialog IS the form.
     */
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const top = popups[popups.length - 1] ?? null;
    const inDialog = (e) => (inDialogScope
      ? !(top && top.contains(e))
      : !!e.closest('.dialog-popup, .resizableDialog-popup'));
    /**
     * An ACE editor's text, enough of it to tell a change by. This used to be the empty string,
     * which made the editor the one field whose `value` check could never pass — so the ace
     * editor's SECOND proof (its text changing) was inert, and on a tab holding both text boxes
     * and an editor the ace credit would have been lost silently to whichever field dirtied the
     * document first. ACE virtualises its lines, so this is the VISIBLE text; the driver types at
     * the cursor it just clicked, which is on screen by construction.
     */
    const aceText = (el) => {
      const root = el.closest('.ace_editor') ?? el;
      const content = root.querySelector('.ace_content') ?? el;
      const t = String(content.textContent ?? '');
      return `${t.length}:${t.slice(0, 120)}`;
    };
    const out = [];
    for (const group of document.querySelectorAll('.form-group')) {
      if (!(group.offsetWidth > 0) || inDialog(group)) continue;
      const label = norm(group.querySelector('.form-group-label')?.textContent);
      // ONLY a labelled FormGroup field. Not every box on a document editor belongs to the
      // document: the Feed's Data tab is a stream browser, and its filter and pager boxes have no
      // business marking the Feed dirty. Typing in one and calling Save's silence a defect is how
      // this driver's first run produced a fail against correct behaviour.
      if (!label) continue;
      const at = (el, dx = 0.5) => {
        const r = el.getBoundingClientRect();
        return { x: r.x + r.width * dx, y: r.y + r.height / 2 };
      };
      // A SelectionBox first: its inner text box is an <input> too, and typing into one does
      // nothing — the value is chosen from the popup its icon opens.
      const select = group.querySelector('.SelectionBox');
      if (select && select.offsetWidth > 0) {
        const icon = select.querySelector('.svgIconBox-icon') ?? select;
        out.push({ label, kind: 'select', value: norm(select.querySelector('.SelectionBox-renderBox')?.textContent
          ?? select.querySelector('input')?.value), ...at(icon) });
        continue;
      }
      // A tick box is usually a div whose CLASS carries the state (TickBoxCell), but a form's own
      // checkbox is a real `input.checkbox` (CustomCheckBox) whose class never changes and whose
      // `checked` does. Reading the class for both made every form checkbox look like a control
      // that refused to move, while its document went dirty — so read whichever actually varies.
      const tick = group.querySelector('.tickBox, input[type="checkbox"]');
      if (tick && tick.offsetWidth > 0) {
        const state = tick.tagName === 'INPUT'
          ? `checked=${tick.checked}`
          : String(tick.className?.baseVal ?? tick.className ?? '');
        out.push({ label, kind: 'tick', value: state, ...at(tick) });
        continue;
      }
      // A VALUE SPINNER before any text box: its value IS an <input type=text>, but typing into
      // one is the wrong gesture. The box rejects anything non-numeric, so the dirty driver's
      // `${value}x` changed nothing and reported `unchecked` on every spinner it ever met — which
      // read like a missing handler and is not. The arrow is the gesture (lib/mouse.mjs), and
      // pressing it fires the same ValueChangeEvent while also exercising `Spinner`'s mousedown.
      const spin = group.querySelector('.valueSpinner');
      if (spin && spin.offsetWidth > 0 && !spin.querySelector('[class*=arrowUpDisabled]')) {
        const up = spin.querySelector('[class*=arrowUp]');
        const down = spin.querySelector('[class*=arrowDown]');
        if (up) {
          out.push({ label, kind: 'spinner', value: String(spin.querySelector('input')?.value ?? ''),
            ...at(up), down: down ? at(down) : null });
          continue;
        }
      }
      const box = [...group.querySelectorAll('input[type="text"], input:not([type]), textarea')]
        .find((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly
          && !e.classList.contains('quickFilter-textBox')
          && !e.classList.contains('ace_text-input') && !e.closest('.ace_editor'));
      if (box) { out.push({ label, kind: 'text', value: String(box.value ?? ''), ...at(box) }); continue; }
      // A DOCUMENT CHOOSER — `DocSelectionBoxPresenter` over `DropDownViewImpl`, rendered as
      // `.dropDownView-container`. It opens on MOUSEDOWN, not click (`addDomHandler(...,
      // MouseDownEvent.getType())`), and picking a document fires `addDataSelectionHandler`, which
      // the presenter turns into onChange -> setDirty (`DataGenSettingsPresenter:61`). DataGen's
      // destination feed and Script's dependencies are reachable no other way.
      const chooser = group.querySelector('.dropDownView-container');
      if (chooser && chooser.offsetWidth > 0) {
        out.push({ label, kind: 'chooser',
          value: norm(chooser.querySelector('.dropDownView-label')?.textContent), ...at(chooser, 0.4) });
        continue;
      }
      // An ACE editor. Every other probe avoids it — the popup framework ignores keys typed there,
      // which is why Ctrl+Enter is excused inside one — but for DIRTINESS it is the point: the
      // editor fires a ValueChangeEvent and its presenter turns that into setDirty
      // (`XsltPresenter:56`, `TextConverterPresenter:73`). Settings tabs built entirely from
      // editors — Script, View, DataGen's template — are reachable no other way.
      const ace = group.querySelector('.ace_content, .ace_editor');
      if (ace && ace.offsetWidth > 0) out.push({ label, kind: 'ace', value: aceText(ace), ...at(ace, 0.3) });
    }
    // A tab that IS an editor — Script, XSLT, TextConverter — has no FormGroup at all: the editor
    // fills the pane. Drive it when the tab offers nothing else, named for the tab so the record
    // still says what was typed in. A read-only viewer (the stream preview) simply will not dirty
    // the document, and an unchanged Save is `unchecked`, never a fail.
    if (!out.length) {
      const solo = [...document.querySelectorAll('.ace_content')]
        .find((e) => e.offsetWidth > 100 && e.offsetHeight > 40
          && (inDialogScope ? (top && top.contains(e)) : !e.closest('.dialog-popup, .resizableDialog-popup')));
      if (solo) {
        const tab = norm([...document.querySelectorAll('.linkTab-selected .linkTab-label, .curveTab-selected .curveTab-label')]
          .find((e) => e.offsetWidth > 0)?.textContent) || 'the editor';
        const r = solo.getBoundingClientRect();
        out.push({ label: tab, kind: 'ace', value: aceText(solo), x: r.x + r.width * 0.3, y: r.y + Math.min(r.height / 2, 60) });
      }
    }
    return out.map((f, i) => ({ ...f, i }));
  }, { inDialogScope }).catch(() => []);
}

/**
 * Change one control, whatever kind it is, and say what was done so it can be undone. A text box is
 * typed into and blurred (a GWT TextBox fires ValueChangeEvent on blur); a tick box is clicked; a
 * SelectionBox is opened by its ICON — clicking its text box calls showPopup() and then bubbles to
 * the box's own handler, which hides it again — and an item other than the current one is chosen
 * with a mousedown, which is what `BaseSelectionBox` listens for.
 */
export async function nudge(page, f) {
  if (f.kind === 'text') {
    await page.mouse.click(f.x, f.y);
    await page.keyboard.press('Control+A');
    await page.keyboard.type(`${f.value}x`.slice(-60) || 'x', { delay: 10 });
    await page.keyboard.press('Tab');
    return 'typed';
  }
  if (f.kind === 'spinner') {
    // One short press: `Spinner` starts a repeating timer at 30ms, so a long hold would run the
    // value away. The value is NOT put back — the document is dirty either way, exactly as for a
    // tick or a selection, and the walker's tab-close machinery answers the discard prompt.
    await page.mouse.move(f.x, f.y);
    await page.mouse.down();
    await page.waitForTimeout(60);
    await page.mouse.up();
    return 'stepped the spinner';
  }
  if (f.kind === 'tick') {
    await page.mouse.click(f.x, f.y);
    return 'ticked';
  }
  if (f.kind === 'chooser') {
    // NOT DRIVEN YET. The box is recognised and counted, but choosing a document through it is
    // unfinished, so the driver does not spend a walk's time attempting it.
    //
    // What is known: mousedown (not click) opens a `Choose item` dialog — `DropDownViewImpl`
    // registers `MouseDownEvent`, not `ClickEvent`. The tree inside opens on its BRANCHES: folders,
    // and documents that contain others (a Git Repo holding feeds), while the leaves the chooser
    // accepts are collapsed inside them. Filtering surfaces more rows, but clicking one and
    // pressing OK leaves the box showing its original value, so the selection is not being made —
    // a row click alone is evidently not what `DocSelectionBoxPresenter` listens for. The walker
    // reaches these with `fill` + `select-tree`, and lifting that pair here is the next attempt.
    return null;
  }
  if (f.kind === 'ace') {
    // Click into the text, go to the end of the line so nothing is overwritten, and type one
    // character. ACE routes the keystroke through its hidden `textarea.ace_text-input`, which is
    // what fires the editor's ValueChangeEvent.
    await page.mouse.click(f.x, f.y);
    await page.keyboard.press('End');
    await page.keyboard.type(' ', { delay: 20 });
    return 'typed in the editor';
  }
  await page.mouse.click(f.x, f.y);
  await page.waitForTimeout(500);
  const item = await page.evaluate((current) => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const rows = [...document.querySelectorAll('.selectionList-elementChooser tr, .selectionList-elementChooser .dataGridRow')]
      .filter((e) => e.offsetWidth > 0 && e.getBoundingClientRect().x >= 0 && e.getBoundingClientRect().y >= 0);
    const other = rows.find((e) => norm(e.textContent) && norm(e.textContent) !== norm(current));
    if (!other) return null;
    const r = other.getBoundingClientRect();
    return { x: r.x + Math.min(r.width / 2, 40), y: r.y + r.height / 2, text: norm(other.textContent) };
  }, f.value ?? '');
  if (!item) {
    await page.keyboard.press('Escape').catch(() => {});
    return null;
  }
  await page.mouse.move(item.x, item.y);
  await page.mouse.down();
  await page.mouse.up();
  await page.waitForTimeout(300);
  return `chose "${item.text}"`;
}

/**
 * Did typing mark the document dirty? `before` / `after` are `readSave` captures.
 *   unchecked  no Save button (not a document editor); or Save was ALREADY enabled on arrival, so
 *              a document left dirty by an earlier step proves nothing about this control; or the
 *              control changed and Save did not move — see below
 *   pass       Save went from disabled to enabled: this control IS a document field and its
 *              handler fired
 *
 * There is deliberately no `fail`. Not every labelled control on an editor tab belongs to the
 * document: the Permissions tab carries a `Permission Visibility` selector that chooses between
 * effective and explicit permissions, and the Data tab is a stream browser. Changing one of those
 * SHOULD leave the document clean. From inside the driver a view control and a field whose handler
 * is missing look identical, so claiming a defect here would be guessing — and it did, on
 * `Permission Visibility`, the first time it met one.
 *
 * The distinction is available OFFLINE, where the handler inventory knows which class declares a
 * value or dirty handler for which receiver and the ledger attributes the node to a presenter: a
 * control that did not dirty the document, on a tab whose presenter declares a handler for it, is
 * the real finding. That triage belongs in the ledger, not here.
 */
export function dirtyVerdict({ before, after, field, how = 'typing' }) {
  const where = field ? `"${field}"` : 'the box';
  if (!before || !after) return { verdict: 'unchecked', why: 'no Save button on this screen — not a document editor' };
  if (before.enabled) return { verdict: 'unchecked', why: 'the document was already dirty on arrival' };
  if (after.enabled) return { verdict: 'pass', why: `${how} ${where} marked the document dirty` };
  return { verdict: 'unchecked', why: `${how} ${where} left Save disabled — either a view control, or a document field whose handler is missing; the inventory decides which` };
}

/**
 * Did driving this control actually CHANGE it? — the question `value` coverage turns on.
 *
 * A GWT `ValueChangeEvent` fires when a widget's value changes: on blur for a TextBox, on pick for
 * a SelectionBox, on click for a tick, on the editor's own change event for ACE. So a control whose
 * displayed value differs after the nudge has fired its handler, and one that is unmoved has not —
 * which is the honest reading of typing a letter into a number box, and the reason this is a
 * separate verdict from `dirty` rather than a second opinion about Save.
 *
 * It is never a `fail`: a control refusing a value is the control working, not a defect.
 */
export function valueVerdict({ before, after, label, kind, how }) {
  if (after === null || after === undefined) {
    return { verdict: 'unchecked', why: `"${label}" could not be read back after ${how ?? 'the nudge'}` };
  }
  if (String(before ?? '') === String(after ?? '')) {
    return { verdict: 'unchecked', why: `"${label}" [${kind}] did not accept the change — its value is still ${JSON.stringify(String(before ?? ''))}` };
  }
  return { verdict: 'pass', why: `"${label}" [${kind}] changed ${JSON.stringify(String(before ?? ''))} -> ${JSON.stringify(String(after ?? ''))}, so its ValueChangeEvent fired` };
}
