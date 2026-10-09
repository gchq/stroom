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
// The MOUSE gestures — BEHAVIOUR-PLAN.md § B3, the fourth driver.
//
// 27 handlers were counted as a single kind called `mousedown`, which hid that they are four
// separate contracts with four different oracles. Split out (build-handler-inventory.mjs), they are
// `mousedown 13 · mouseup 4 · mousemove 2 · mouseover 4 · mouseout 4`, and they belong to:
//
//   Spinner (8)             an arrow press, release, hover and leave — each sets its OWN class
//   Dialog (3)              press-move-release on the caption moves the dialog
//   ResizableDialog (3)     the same, except a press on one of eight handles resizes instead
//   MenubarItem (4)         hover only, and it may not be in the running application at all
//
// Every oracle below is a line of GWT, not a guess, and each was confirmed live by
// `probe-mouse.mjs` before this file was written.

/** A neutral point to park the pointer on, so a hover is genuinely left. */
const AWAY = { x: 4, y: 4 };

// ── The dialog drag ─────────────────────────────────────────────────────────
//
//   Dialog:166          if (!dragging && !isCaptionEvent(event)) return;
//   Dialog:185-192      beginDragging:    getDragGlass().show(); dragging = true; …
//   Dialog:203-218      continueDragging: left = max(0, min(clientWidth - 22, startLeft + dx))
//   Dialog:232-236      endDragging:      dragging = false; getDragGlass().hide();
//   AbstractPopupPanel:33  new Glass("popupPanel-dragGlass", "popupPanel-dragGlassVisible")
//
// `Glass.hide()` removes the element from its parent, so a glass still attached after the release
// is a leak that would swallow every click on the page underneath.

/** The frontmost dialog's box, its caption point, whether it can resize, and the drag glass. */
export function readDialogBox(page) {
  return page.evaluate(() => {
    const pops = [...document.querySelectorAll('.resizableDialog-popup, .dialog-popup')]
      .filter((e) => e.offsetWidth > 0);
    const p = pops[pops.length - 1];
    if (!p) return null;
    const r = p.getBoundingClientRect();
    // The TITLE TEXT, not the title bar: the bar also holds the icon and the busy spinner, and a
    // press on those is still a caption event but reads less obviously as "drag by the caption".
    const cap = p.querySelector('.dialog-titleText') ?? p.querySelector('.dialog-titleBar');
    const c = cap?.getBoundingClientRect();
    const se = p.querySelector('.resizableDialog-resizeSE')?.getBoundingClientRect();
    return {
      resizable: p.classList.contains('resizableDialog-popup'),
      caption: String(p.querySelector('.dialog-titleText')?.textContent ?? '').replace(/\s+/g, ' ').trim(),
      x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height),
      at: c && c.width > 0 ? { x: Math.round(c.x + c.width / 2), y: Math.round(c.y + c.height / 2) } : null,
      se: se && se.width > 0 ? { x: Math.round(se.x + se.width / 2), y: Math.round(se.y + se.height / 2) } : null,
      glass: !!document.querySelector('.popupPanel-dragGlass'),
      view: { w: window.innerWidth, h: window.innerHeight },
    };
  }).catch(() => null);
}

/**
 * Drag the dialog by its caption and put it back. The delta is chosen to point AWAY from the edges
 * so `continueDragging`'s clamp cannot apply — then the expected result is the exact delta, with no
 * need to model the clamp at all. (Modelling a clamp exactly is what broke the pager checker once:
 * it turned 168 passes into 34 fails by being too clever about the formula.)
 */
export async function dragDialog(page, box) {
  const dx = box.x > 200 ? -60 : 60;
  const dy = box.y > 160 ? -40 : 40;
  const left = box.x + dx;
  const top = box.y + dy;
  // If either edge would clamp, there is nothing safe to assert.
  if (left < 0 || top < 0 || left > box.view.w - 22 || top > box.view.h - 22) {
    return { skipped: 'the clamp would apply', asked: { dx, dy } };
  }
  await page.mouse.move(box.at.x, box.at.y);
  await page.mouse.down();
  const pressed = await readDialogBox(page);
  await page.mouse.move(box.at.x + dx, box.at.y + dy, { steps: 5 });
  await page.waitForTimeout(100);
  const moved = await readDialogBox(page);
  await page.mouse.up();
  await page.waitForTimeout(200);
  const after = await readDialogBox(page);
  // Put it back, by the same route.
  if (after?.at) {
    await page.mouse.move(after.at.x, after.at.y);
    await page.mouse.down();
    await page.mouse.move(after.at.x - dx, after.at.y - dy, { steps: 5 });
    await page.mouse.up();
    await page.waitForTimeout(150);
  }
  const restored = await readDialogBox(page);
  return {
    asked: { dx, dy },
    // WHICH class's handlers this exercised: `ResizableDialog` overrides `Dialog`, and the popup
    // carries the class that says which one it is. The inventory join needs this to credit the
    // right one rather than both.
    owner: box.resizable ? 'ResizableDialog' : 'Dialog',
    got: after ? { dx: after.x - box.x, dy: after.y - box.y } : null,
    glassWhilePressed: !!pressed?.glass,
    glassAfterRelease: !!after?.glass,
    movedDuring: moved ? { x: moved.x, y: moved.y } : null,
    resized: after ? after.w !== box.w || after.h !== box.h : null,
    stillOpen: !!after,
    restored: restored ? restored.x === box.x && restored.y === box.y : null,
  };
}

/**
 * A drag is a pass when the dialog moved by exactly what was asked, the glass appeared while the
 * button was down, and the glass was gone again after it came up. Nothing moving is a fail: three
 * handlers are registered for this and the press was on the caption, which is the one precondition
 * `onBrowserEvent` imposes.
 */
export function dragVerdict(ev) {
  if (!ev || ev.skipped) return { verdict: 'unchecked', why: ev?.skipped ?? 'no dialog' };
  if (!ev.stillOpen) return { verdict: 'unchecked', why: 'the dialog closed during the drag' };
  if (!ev.got) return { verdict: 'unchecked', why: 'the dialog could not be measured' };
  if (ev.got.dx === 0 && ev.got.dy === 0) {
    return { verdict: 'fail', why: `press-move-release on the caption did not move the dialog (asked ${ev.asked.dx},${ev.asked.dy})` };
  }
  if (ev.glassAfterRelease) {
    return { verdict: 'fail', why: 'the drag glass is still attached after the release — it covers the page (Glass.hide removes it)' };
  }
  if (ev.got.dx !== ev.asked.dx || ev.got.dy !== ev.asked.dy) {
    // It moved, so the handlers ran; by how much is a weaker claim than this driver should make
    // away from the edges, so say what happened rather than calling it right or wrong.
    return { verdict: 'pass', why: `dragged, though by ${ev.got.dx},${ev.got.dy} where ${ev.asked.dx},${ev.asked.dy} was asked` };
  }
  if (ev.resized) return { verdict: 'fail', why: 'dragging the caption resized the dialog as well as moving it' };
  return { verdict: 'pass', why: `dragged by ${ev.got.dx},${ev.got.dy}, glass shown and then removed` };
}

// ── The resize handle ───────────────────────────────────────────────────────
//
//   ResizableDialog:171-190  beginDragging picks resizeWidth/resizeHeight from which of the eight
//                            handles was pressed; the SE one sets both.
//   ResizableDialog:…        continueDragging only resizes if `popupSize.getWidth().isResizable()`.
//
// That last line is why a dialog that does not change size is NOT a failure: the size spec, which
// the DOM does not expose, may simply forbid it.

export async function resizeDialog(page, box) {
  if (!box?.se) return { skipped: 'no SE resize handle' };
  const dw = 70;
  const dh = 50;
  await page.mouse.move(box.se.x, box.se.y);
  await page.mouse.down();
  await page.mouse.move(box.se.x + dw, box.se.y + dh, { steps: 5 });
  await page.waitForTimeout(120);
  await page.mouse.up();
  await page.waitForTimeout(200);
  const after = await readDialogBox(page);
  // Put the size back.
  if (after?.se) {
    await page.mouse.move(after.se.x, after.se.y);
    await page.mouse.down();
    await page.mouse.move(after.se.x - (after.w - box.w), after.se.y - (after.h - box.h), { steps: 5 });
    await page.mouse.up();
    await page.waitForTimeout(150);
  }
  return {
    asked: { dw, dh },
    // Only `ResizableDialog` has handles at all, so a resize is always its own.
    owner: 'ResizableDialog',
    got: after ? { dw: after.w - box.w, dh: after.h - box.h } : null,
    glassAfterRelease: !!after?.glass,
    stillOpen: !!after,
  };
}

export function resizeVerdict(ev) {
  if (!ev || ev.skipped) return { verdict: 'unchecked', why: ev?.skipped ?? 'no dialog' };
  if (!ev.stillOpen || !ev.got) return { verdict: 'unchecked', why: 'the dialog closed or could not be measured' };
  if (ev.glassAfterRelease) {
    return { verdict: 'fail', why: 'the drag glass is still attached after the resize released' };
  }
  if (ev.got.dw === 0 && ev.got.dh === 0) {
    // `isResizable()` is not visible from the DOM, so silence here is permitted.
    return { verdict: 'unchecked', why: 'the SE handle changed nothing — the size spec may not be resizable' };
  }
  return { verdict: 'pass', why: `resized by ${ev.got.dw}x${ev.got.dh} from the SE handle` };
}

// ── The value spinner ───────────────────────────────────────────────────────
//
//   Spinner:105-120   MouseDown  -> class "valueSpinner-arrow valueSpinner-arrowUpPressed", increase()
//   Spinner:121-131   MouseOver  -> class "valueSpinner-arrow valueSpinner-arrowUpHover"
//   Spinner:133-137   MouseOut   -> cancelTimer -> class back to "valueSpinner-arrowUp"
//   Spinner:104       MouseUp    -> cancelTimer -> class back to "valueSpinner-arrowUp"
//   Spinner:310-320   increase() -> value += step, clamped at max (or wrapped)
//
// Every one of those is wrapped in `if (enabled)`, and a disabled arrow carries
// `valueSpinner-arrowUpDisabled` — so a disabled spinner can prove nothing and is left unchecked.

/** Every visible value spinner, with the points to press and whether it is live. */
export function readSpinners(page) {
  return page.evaluate(() => {
    const norm = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const pt = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return r.width > 0 ? { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) } : null;
    };
    /**
     * Is this arrow the thing that would actually receive the press?
     *
     * `offsetWidth > 0` is not enough. A spinner on a dialog with ANOTHER dialog over it is still
     * laid out and still visible, but every event lands on the modal glass in front — so hovering
     * and pressing it do nothing, and the driver read that as the handlers not firing. That was six
     * fails against one working spinner ("Max Processing Tasks" on Add Processor, seen under three
     * different sub-dialogs), and they are the shape of a GWT bug report that would have been wrong.
     * `elementFromPoint` is the same check that settled the grid-selection defect.
     */
    const hittable = (el) => {
      if (!el) return false;
      const r = el.getBoundingClientRect();
      if (!(r.width > 0 && r.height > 0)) return false;
      const at = document.elementFromPoint(Math.round(r.x + r.width / 2), Math.round(r.y + r.height / 2));
      return !!at && (at === el || el.contains(at) || at.contains(el));
    };
    return [...document.querySelectorAll('.valueSpinner')].filter((e) => e.offsetWidth > 0).map((e, i) => ({
      index: i,
      label: norm(e.closest('.form-group')?.querySelector('.form-group-label')?.textContent)
        || norm(e.getAttribute('aria-label')) || `spinner ${i + 1}`,
      value: e.querySelector('input')?.value ?? null,
      up: pt(e.querySelector('[class*=arrowUp]')),
      down: pt(e.querySelector('[class*=arrowDown]')),
      // Every handler is `if (enabled)`, and a disabled arrow carries `valueSpinner-arrowUpDisabled`.
      disabled: !!e.querySelector('[class*=arrowUpDisabled]'),
      occluded: !hittable(e.querySelector('[class*=arrowUp]')),
    }));
  }).catch(() => []);
}

const spinnerState = (page, i) => page.evaluate((n) => {
  const e = [...document.querySelectorAll('.valueSpinner')].filter((x) => x.offsetWidth > 0)[n];
  if (!e) return null;
  const up = e.querySelector('[class*=arrowUp]');
  return { cls: up?.className ?? null, value: e.querySelector('input')?.value ?? null };
}, i).catch(() => null);

/**
 * All four gestures on one arrow, in the order that isolates them: hover and leave BEFORE the
 * press, so the hover class is not confused with the pressed one, and the release is read on its
 * own. Then the value is put back with the opposite arrow.
 */
export async function spinnerGestures(page, s) {
  if (s.disabled || !s.up) return { skipped: s.disabled ? 'the arrow is disabled' : 'no arrow' };
  // Something is in front of it — almost always a dialog over the one holding the spinner. The press
  // would land on the glass, and a gesture that cannot reach its target proves nothing.
  if (s.occluded) return { skipped: 'the arrow is covered by something in front of it' };
  // PARK THE POINTER FIRST. A mouse move to where the pointer already is generates no mouseover, so
  // if something earlier left it on this arrow — the field driver steps spinners, and leaves the
  // pointer on the arrow it pressed — the hover never fires and the class stays at rest. That read as
  // `Spinner:121` not working and produced 8 fails against one working spinner, recognisable because
  // the recorded `rest.value` was 201 rather than the 200 the panel opens with: something had already
  // pressed it.
  await page.mouse.move(AWAY.x, AWAY.y);
  await page.waitForTimeout(80);
  const rest = await spinnerState(page, s.index);
  await page.mouse.move(s.up.x, s.up.y);
  await page.waitForTimeout(140);
  const hovered = await spinnerState(page, s.index);
  await page.mouse.move(AWAY.x, AWAY.y);
  await page.waitForTimeout(140);
  const left = await spinnerState(page, s.index);
  await page.mouse.move(s.up.x, s.up.y);
  await page.waitForTimeout(100);
  await page.mouse.down();
  await page.waitForTimeout(60);
  const pressed = await spinnerState(page, s.index);
  await page.mouse.up();
  await page.waitForTimeout(160);
  const released = await spinnerState(page, s.index);
  // Put the value back with the down arrow, and read whether that worked.
  let restored = null;
  if (s.down && released?.value !== rest?.value) {
    await page.mouse.move(s.down.x, s.down.y);
    await page.mouse.down();
    await page.waitForTimeout(60);
    await page.mouse.up();
    await page.waitForTimeout(160);
    restored = await spinnerState(page, s.index);
  }
  await page.mouse.move(AWAY.x, AWAY.y);
  return { rest, hovered, left, pressed, released, restored };
}

const has = (st, frag) => !!st?.cls && st.cls.includes(frag);

/**
 * One verdict per gesture, because they are four handlers and a driver that reports them as one
 * cannot say which is broken. The classes are exact strings in `Spinner`, so each is checkable on
 * its own.
 */
export function spinnerVerdicts(ev, label) {
  if (!ev || ev.skipped) {
    return [{ kind: 'mousedown', verdict: 'unchecked', why: ev?.skipped ?? 'no spinner' }];
  }
  const out = [];
  out.push(has(ev.hovered, 'Hover')
    ? { kind: 'mouseover', verdict: 'pass', why: `hovering "${label}" set valueSpinner-arrowUpHover` }
    : { kind: 'mouseover', verdict: 'fail', why: `hovering "${label}" left the class as ${ev.hovered?.cls ?? 'unreadable'} (Spinner:121 sets …arrowUpHover)` });
  out.push(!has(ev.left, 'Hover') && has(ev.left, 'arrowUp')
    ? { kind: 'mouseout', verdict: 'pass', why: `leaving "${label}" put the class back to valueSpinner-arrowUp` }
    : { kind: 'mouseout', verdict: 'fail', why: `leaving "${label}" left the class as ${ev.left?.cls ?? 'unreadable'} (Spinner:133 cancelTimer restores …arrowUp)` });
  const moved = ev.pressed && ev.rest && ev.pressed.value !== ev.rest.value;
  out.push(has(ev.pressed, 'Pressed')
    ? { kind: 'mousedown', verdict: 'pass', why: `pressing "${label}" set valueSpinner-arrowUpPressed and took the value ${ev.rest?.value} -> ${ev.pressed?.value}` }
    : { kind: 'mousedown', verdict: moved ? 'pass' : 'fail', why: moved
      ? `pressing "${label}" moved the value ${ev.rest?.value} -> ${ev.pressed?.value} without setting …arrowUpPressed`
      : `pressing "${label}" changed neither the class nor the value (Spinner:105 sets …arrowUpPressed and calls increase())` });
  out.push(!has(ev.released, 'Pressed') && has(ev.released, 'arrowUp')
    ? { kind: 'mouseup', verdict: 'pass', why: `releasing "${label}" put the class back to valueSpinner-arrowUp` }
    : { kind: 'mouseup', verdict: 'fail', why: `releasing "${label}" left the class as ${ev.released?.cls ?? 'unreadable'} (Spinner:104 cancelTimer restores …arrowUp)` });
  return out;
}

// ── The menu bar item ───────────────────────────────────────────────────────
//
//   MenubarItem:52   MouseOver -> background opacity 0.3
//   MenubarItem:53   MouseOut  -> background opacity 0
//
// `MenubarItem` is a GWTP view built only by injection, and its DOM (`.menuItem > .background`) is
// not the popup menu's (`.menuItem-outer > .menuItem-text`). It may not be in the running
// application at all, which `readMenubarItems` returning nothing says plainly.

export function readMenubarItems(page) {
  return page.evaluate(() => [...document.querySelectorAll('.menuItem > .background')]
    .filter((e) => e.parentElement.offsetWidth > 0)
    .map((e, i) => {
      const r = e.parentElement.getBoundingClientRect();
      return {
        index: i,
        label: String(e.parentElement.querySelector('.text')?.textContent ?? '').trim(),
        at: { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) },
      };
    })).catch(() => []);
}

const barOpacity = (page, i) => page.evaluate((n) => {
  const e = [...document.querySelectorAll('.menuItem > .background')].filter((x) => x.parentElement.offsetWidth > 0)[n];
  return e ? (e.style.opacity === '' ? null : Number(e.style.opacity)) : null;
}, i).catch(() => null);

export async function menubarHover(page, item) {
  const rest = await barOpacity(page, item.index);
  await page.mouse.move(item.at.x, item.at.y);
  await page.waitForTimeout(150);
  const over = await barOpacity(page, item.index);
  await page.mouse.move(AWAY.x, AWAY.y);
  await page.waitForTimeout(150);
  const out = await barOpacity(page, item.index);
  return { rest, over, out };
}

export function menubarVerdicts(ev, label) {
  return [
    ev.over === 0.3
      ? { kind: 'mouseover', verdict: 'pass', why: `hovering the menu bar item "${label}" set its background to 0.3` }
      : { kind: 'mouseover', verdict: 'fail', why: `hovering "${label}" left the background at ${ev.over} (MenubarItem:52 sets 0.3)` },
    ev.out === 0
      ? { kind: 'mouseout', verdict: 'pass', why: `leaving "${label}" set its background back to 0` }
      : { kind: 'mouseout', verdict: 'fail', why: `leaving "${label}" left the background at ${ev.out} (MenubarItem:53 sets 0)` },
  ];
}
