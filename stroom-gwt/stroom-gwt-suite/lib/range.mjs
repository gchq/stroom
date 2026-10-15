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
// The pager's ROW RANGE — typing a row number to jump there (BEHAVIOUR-PLAN.md § B3).
//
// Every grid in the application carries a `Pager`, and its range is not just the First/Backward/
// Forward/Last buttons the walk already presses. The "1 to 100" text is two LABELS that turn into
// two TEXT BOXES when clicked, and typing a row number into one and pressing Enter moves the grid:
//
//   Pager:152  @UiHandler("lblFrom") onClickFrom  -> setEditing(true); txtFrom.setFocus(true)
//   Pager:178  setEditing(true)   -> txtFrom/txtTo show the current range, labels hide
//   Pager:132  @UiHandler("txtFrom") onKeyDownFrom -> ENTER calls setEditing(false)
//   Pager:190  setEditing(false)  -> fireMoveEvent(), labels show again
//   Pager:142/147  focus and blur handlers keep `focussed` in step
//
// So one gesture exercises eight handlers on a class every grid screen shares — two clicks, two key
// presses and four focus/blur — and tests a behaviour nothing else reaches.
//
// The DOM is `Pager.ui.xml`: `.pager-paging` holding `.pager-label` captions and two hidden
// `.pager-textBox` boxes. The labels all share one class, so `lblFrom` is identified by POSITION
// relative to the boxes — it is the label immediately before the first `.pager-textBox` — rather
// than by counting captions, which would break the moment a caption were added.

/** The first visible pager's range, and where to click to edit it. */
export function readRange(page) {
  return page.evaluate(() => {
    const num = (t) => {
      const m = /(-?[\d,]+)/.exec(String(t ?? ''));
      return m ? Number(m[1].replace(/,/g, '')) : null;
    };
    const pt = (el) => {
      const r = el.getBoundingClientRect();
      return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
    };
    /**
     * Is this label the thing a click would actually reach? The spinner driver needed exactly this
     * and for exactly this reason: a pager on a screen with a DIALOG over it is still laid out and
     * still visible, but the click lands on the modal glass, so the range editor never opens — and
     * the driver read that as `lblFrom`'s handler not firing. One NEW fail on a working pager, under
     * `Batch Edit Permissions For Filtered Documents`.
     */
    const hittable = (el) => {
      const r = el.getBoundingClientRect();
      if (!(r.width > 0 && r.height > 0)) return false;
      const at = document.elementFromPoint(Math.round(r.x + r.width / 2), Math.round(r.y + r.height / 2));
      return !!at && (at === el || el.contains(at) || at.contains(el));
    };
    // Scope to the topmost dialog when one is up, so the pager under it is never even considered.
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document;
    for (const pager of scope.querySelectorAll('.pager-paging')) {
      if (!(pager.offsetWidth > 0)) continue;
      const boxes = [...pager.querySelectorAll('.pager-textBox')];
      if (boxes.length < 2) continue;
      const kids = [...pager.children];
      // `lblFrom` is the label immediately before the FIRST text box; `lblTo` the one before the
      // second. Both come from the ui.xml's order, which is the only thing that names them.
      const i0 = kids.indexOf(boxes[0]);
      const i1 = kids.indexOf(boxes[1]);
      const lblFrom = i0 > 0 ? kids[i0 - 1] : null;
      const lblTo = i1 > 0 ? kids[i1 - 1] : null;
      if (!lblFrom || !lblTo) continue;
      // The "of N" caption is the last label with a number in it.
      const labels = [...pager.querySelectorAll('.pager-label')].filter((e) => e.offsetWidth > 0);
      const of = labels.length ? num(labels[labels.length - 1].textContent) : null;
      // A label that something covers cannot be clicked, so there is nothing to assert about it.
      if (!hittable(lblFrom)) continue;
      return {
        from: num(lblFrom.textContent),
        to: num(lblTo.textContent),
        of,
        editing: boxes.some((b) => b.offsetWidth > 0),
        fromAt: lblFrom.offsetWidth > 0 ? pt(lblFrom) : null,
        toAt: lblTo.offsetWidth > 0 ? pt(lblTo) : null,
        boxAt: boxes[0].offsetWidth > 0 ? pt(boxes[0]) : null,
      };
    }
    return null;
  }).catch(() => null);
}

/** The visible text boxes' values, once the range editor is open. */
const boxValues = (page) => page.evaluate(() => {
  const pager = [...document.querySelectorAll('.pager-paging')].find((p) => p.offsetWidth > 0
    && [...p.querySelectorAll('.pager-textBox')].some((b) => b.offsetWidth > 0));
  if (!pager) return null;
  const boxes = [...pager.querySelectorAll('.pager-textBox')];
  const pt = (b) => {
    const r = b?.getBoundingClientRect();
    return r && r.width > 0 ? { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) } : null;
  };
  return {
    values: boxes.map((b) => String(b.value ?? '')),
    visible: boxes.filter((b) => b.offsetWidth > 0).length,
    at: pt(boxes[0]),
    toAt: pt(boxes[1]),
  };
}).catch(() => null);

/**
 * Open the range editor, type a row number, press Enter, then put the range back.
 *
 * The target row is chosen INSIDE the known range so `AbstractPager.setPageStart`'s clamp cannot
 * apply — the same restraint the dialog drag uses, and for the same reason: a checker that models a
 * clamp exactly is a checker that fails working screens.
 */
export async function jumpRange(page, r) {
  if (!r) return { skipped: 'no pager with a range editor' };
  if (!r.fromAt) return { skipped: 'the range labels are not visible' };
  const total = r.of;
  const span = (r.to ?? 1) - (r.from ?? 1) + 1;
  // Nothing to jump to: one page, or a count we cannot read.
  if (!total || !span || total <= span) return { skipped: `one page only (${total ?? '?'} row(s))` };
  const want = (r.from ?? 1) === 2 ? 1 : 2;
  await page.mouse.click(r.fromAt.x, r.fromAt.y);
  await page.waitForTimeout(250);
  const opened = await boxValues(page);
  if (!opened?.at || !opened.visible) {
    return { asked: want, opened: false, before: r, skipped: 'clicking the from label did not open the editor' };
  }
  // Pass 1 drives the FROM side end to end: lblFrom's click already focused txtFrom, so typing and
  // pressing Enter there fires onKeyDownFrom, and `setEditing(false)` hiding the box fires onBlurFrom.
  await page.mouse.click(opened.at.x, opened.at.y);
  await page.keyboard.press('Control+A');
  await page.keyboard.type(String(want), { delay: 20 });
  await page.keyboard.press('Enter');
  await page.waitForTimeout(700);
  const after = await readRange(page);
  // Pass 2 puts the range back THROUGH THE OTHER LABEL, which is what drives the TO side: lblTo's
  // click (onClickTo) focuses txtTo (onFocusTo), and pressing Enter there is onKeyDownTo followed by
  // onBlurTo. `fireMoveEvent` reads BOTH boxes (Pager:202-203), so the from value typed here still
  // applies even though Enter is pressed in the other one — which is why one restore can cover four
  // handlers the jump itself never touches.
  let toSide = false;
  if (after && after.toAt) {
    await page.mouse.click(after.toAt.x, after.toAt.y);
    await page.waitForTimeout(250);
    const again = await boxValues(page);
    if (again?.at && again?.toAt) {
      await page.mouse.click(again.at.x, again.at.y);
      await page.keyboard.press('Control+A');
      await page.keyboard.type(String(r.from ?? 1), { delay: 20 });
      // Into the TO box, then Enter: blurs txtFrom, focuses txtTo, and keys there.
      await page.mouse.click(again.toAt.x, again.toAt.y);
      await page.waitForTimeout(120);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(700);
      toSide = true;
    }
  }
  const restored = await readRange(page);
  return {
    asked: want,
    opened: true,
    prefilled: opened.values,
    before: r,
    after: after ? { from: after.from, to: after.to, of: after.of } : null,
    // Whether the TO side was driven as well, which is what the inventory credits txtTo by.
    toSide,
    restored: restored ? restored.from === r.from : null,
  };
}

/**
 * The editor opening is itself a verdict — that is `lblFrom`'s click handler — and the jump is the
 * Enter handler. A range that will not move when asked for a row inside its own count is a fail;
 * a single-page grid is unchecked, because there is nowhere to go.
 */
export function rangeVerdict(ev) {
  if (!ev || ev.skipped) {
    if (ev && ev.opened === false) {
      return { verdict: 'fail', why: 'clicking the pager\'s "from" label did not open the range editor (Pager:152 setEditing(true))' };
    }
    return { verdict: 'unchecked', why: ev?.skipped ?? 'no pager' };
  }
  if (!ev.after || ev.after.from === null) return { verdict: 'unchecked', why: 'the range could not be read back' };
  if (ev.after.from === ev.asked) {
    return { verdict: 'pass', why: `typing ${ev.asked} in the pager moved the range to ${ev.after.from}-${ev.after.to} of ${ev.after.of}` };
  }
  if (ev.after.from === ev.before.from) {
    return { verdict: 'fail', why: `typing ${ev.asked} and pressing Enter left the range at ${ev.before.from}-${ev.before.to} (Pager:132 Enter -> setEditing(false) -> fireMoveEvent)` };
  }
  // It moved, but not to the row asked for. Say so rather than judging it: the pager may round to a
  // page boundary, which is a design choice this driver has no business calling wrong.
  return { verdict: 'pass', why: `typing ${ev.asked} moved the range to ${ev.after.from}-${ev.after.to}, not to ${ev.asked} — the pager may round to a page` };
}
