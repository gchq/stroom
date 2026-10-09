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
// RIGHT-CLICK — the context menus (BEHAVIOUR-PLAN.md § B3).
//
// `context 0/11`, and not because it is hard: the walker's only right-click is the explorer seed that
// `EXPLORER=…` turns on, and no shard sets it. So eleven handlers on six presenters have never been
// touched, and a whole interaction class — the menu you get from the right button — is untested.
//
//   NavigationPresenter:337        explorerTree.addContextMenuHandler(…)   the explorer tree
//   EditorPresenter               view's context menu                     an ACE editor
//   ExpressionTreePresenter       getView()                               an expression term
//   PipelineTreePresenter         getView()                               a pipeline element
//   QueryPresenter / EditExpressionPresenter / SteppingPresenter / PipelineStructurePresenter
//
// The oracle is the same for all of them and needs no source reading beyond the registration: a
// context handler that runs puts a MENU on the screen. GWT's menus are `.menuItem-outer` (the same
// ones `clickMenuItem` drives), so "a menu appeared that was not there before" is the whole check.
//
// Nothing here is ever a `fail`. Most elements have no context menu and are not supposed to: the
// registration says a handler EXISTS on some element of the view, not that every pixel answers. A
// right-click that produces nothing is `unchecked`.

/**
 * The things worth right-clicking. EVERY kind present is tried, not just the first: the explorer is on
 * screen on every screen, so a first-match rule meant `.explorerCell` always won and the editor, the
 * pipeline elements and the grid rows were never reached at all.
 *
 * `skip` names rows that are explorer CHROME rather than documents. "Favourites" is the first
 * `.explorerCell` on the page and has no context menu, which is how three of this driver's four first
 * attempts came back `unchecked` against a feature that works perfectly.
 */
export const TARGETS = [
  { sel: '.explorerCell', what: 'an explorer row', skip: /^(Favourites|System)$/ },
  { sel: '.ExpressionTreeViewImpl-layoutPanel .expressionItem, .expressionItem', what: 'an expression term' },
  // Seen in the application as `pipelineElementBox-background` / `-label`, not `pipelineElementBox`.
  { sel: '[class*=pipelineElementBox]', what: 'a pipeline element' },
  { sel: '.ace_content', what: 'the editor' },
  { sel: '.dataGridWidget tbody tr', what: 'a grid row' },
];

/** How many menu items are on screen — the before/after of every check here. */
export const menuCount = (page) => page.evaluate(() => document.querySelectorAll('.menuItem-outer').length).catch(() => 0);

/**
 * The first target present on this screen, with a point to right-click. Skips anything inside a
 * dialog whose own node will be walked separately, and anything that is not actually hittable —
 * `elementFromPoint`, the same guard the spinner needed after six fails against a working spinner
 * sitting behind a modal.
 */
export function findTargets(page, targets = TARGETS) {
  return page.evaluate((list) => {
    const hittable = (el) => {
      const r = el.getBoundingClientRect();
      if (!(r.width > 8 && r.height > 4)) return false;
      const at = document.elementFromPoint(Math.round(r.x + Math.min(r.width / 2, 60)), Math.round(r.y + r.height / 2));
      return !!at && (at === el || el.contains(at) || at.contains(el));
    };
    const out = [];
    for (const t of list) {
      const skip = t.skip ? new RegExp(t.skip.source ?? t.skip, t.skip.flags ?? '') : null;
      for (const el of document.querySelectorAll(t.sel)) {
        if (!(el.offsetWidth > 0) || !hittable(el)) continue;
        const text = String(el.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 40);
        if (skip && skip.test(text)) continue;
        const r = el.getBoundingClientRect();
        out.push({
          what: t.what,
          sel: t.sel,
          text,
          x: Math.round(r.x + Math.min(r.width / 2, 60)),
          y: Math.round(r.y + r.height / 2),
        });
        break; // one per KIND — the point is the handler, not every row
      }
    }
    return out;
  }, targets.map((t) => ({ ...t, skip: t.skip ? { source: t.skip.source, flags: t.skip.flags } : null })))
    .catch(() => []);
}

/** Right-click it, read whether a menu arrived, and put the screen back. */
export async function rightClick(page, target) {
  if (!target) return { skipped: 'nothing on this screen worth right-clicking' };
  const before = await menuCount(page);
  await page.mouse.click(target.x, target.y, { button: 'right' });
  await page.waitForTimeout(500);
  const after = await menuCount(page);
  const items = after > before ? await page.evaluate(() => [...document.querySelectorAll('.menuItem-outer')]
    .filter((e) => e.offsetWidth > 0)
    .map((e) => String((e.querySelector('.menuItem-text, .menuItem-simpleText') ?? e).textContent ?? '').replace(/\s+/g, ' ').trim())
    .filter(Boolean).slice(0, 12)).catch(() => []) : [];
  // Close it the way the framework expects, and check it went.
  if (after > before) {
    await page.keyboard.press('Escape').catch(() => {});
    await page.waitForTimeout(300);
    if ((await menuCount(page)) > before) {
      await page.mouse.click(4, 4).catch(() => {});
      await page.waitForTimeout(300);
    }
  }
  return { what: target.what, text: target.text, before, after, items, left: (await menuCount(page)) - before };
}

/**
 * A menu appeared: the handler ran. Nothing appeared: this element has no context menu, which is the
 * normal case and not a defect. A menu that will NOT go away is a fail, though — it covers the screen
 * and the walk's next click lands in it.
 */
export function contextVerdict(ev) {
  if (!ev || ev.skipped) return { verdict: 'unchecked', why: ev?.skipped ?? 'no target' };
  if (ev.after <= ev.before) {
    return { verdict: 'unchecked', why: `right-clicking ${ev.what} opened no menu — most elements have none` };
  }
  if (ev.left > 0) {
    return { verdict: 'fail', why: `the context menu on ${ev.what} would not close — Escape and a click outside both left it up` };
  }
  return { verdict: 'pass', why: `right-clicking ${ev.what}${ev.text ? ` ("${ev.text}")` : ''} opened a menu: ${ev.items.join(', ').slice(0, 110)}` };
}
