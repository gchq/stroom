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
// Compensating cycles — BEHAVIOUR-PLAN.md § B4.
//
// Every OK that WRITES is untested: the read-only guard aborts it, and 442 recorded `blocked`
// outcomes say so. The way to test a write without leaving the instance changed is to undo it —
// the user's own decision when asked ("can the tests try to undo actions to restore previous
// state, i.e. delete an item after create?") — so a cycle is:
//
//     create -> read it back -> edit -> save -> RELOAD -> read it back -> delete -> read it absent
//
// and the compensation (the delete) runs whether or not the middle succeeded. A cycle that cannot
// compensate has LEAKED a row, and that is the loudest finding this file can produce: it is the one
// failure that makes the next run's baseline wrong.
//
// Three things make it a test rather than a script:
//   * every phase has an ORACLE — the row is there / is not there / holds the edited value, read
//     from the grid, not from the request;
//   * the RELOAD between save and read-back is the point. Without it a presenter that only updated
//     its own view would pass;
//   * the guard stays attached with an ALLOW-LIST built from the cycle's own spec, so a cycle that
//     writes anything it did not declare fails on that alone.

import { readTopDialogBody } from './alerts.mjs';
import { setAce } from './fills.mjs';

// NOTE: a button's doubled text is undoubled browser-side, inside `clickButton`.

// ── Reading ─────────────────────────────────────────────────────────────────
/** Every visible row's text in the topmost grid (a dialog's grid when one is open). */
export function readRows(page) {
  return page.evaluate(() => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const grid = [...scope.querySelectorAll('.dataGridWidget, [role=grid]')].find((g) => g.offsetWidth > 0);
    if (!grid) return [];
    return [...grid.querySelectorAll('tbody tr, [role=row]')]
      .filter((r) => r.offsetHeight > 0 && n(r.textContent)).map((r) => n(r.textContent));
  }).catch(() => []);
}

/**
 * A row's SIGNATURE: its text plus the state of any tick box in it. A grid's boolean column is a
 * `TickBoxCell` — a `div.tickBox`, not a checkbox — so it contributes NO text, and a row whose
 * Enabled flag was just toggled reads identically by text. The tick's class names carry the state,
 * so they are part of the signature.
 */
export function rowSignature(page, text) {
  return page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const grid = [...scope.querySelectorAll('.dataGridWidget, [role=grid]')].find((g) => g.offsetWidth > 0);
    const row = [...(grid?.querySelectorAll('tbody tr, [role=row]') ?? [])]
      .find((r) => r.offsetHeight > 0 && n(r.textContent).includes(wanted));
    if (!row) return null;
    const ticks = [...row.querySelectorAll('[class*=tick], [class*=Tick], input[type=checkbox]')]
      .map((e) => (e.tagName === 'INPUT' ? `checkbox:${e.checked}` : String(e.className?.baseVal ?? e.className ?? '')));
    return `${n(row.textContent)} ¦ ${ticks.join(' ')}`;
  }, [text]).catch(() => null);
}

/** The captions of every visible dialog, outermost first. */
export function dialogCaptions(page) {
  return page.evaluate(() => [...document.querySelectorAll('.dialog-titleText')]
    .filter((e) => e.offsetWidth > 0).map((e) => e.textContent.replace(/\s+/g, ' ').trim())).catch(() => []);
}

// ── Driving ─────────────────────────────────────────────────────────────────
/** Click a button by label, in the TOPMOST dialog if one is open, else on the screen. */
export async function clickButton(page, label) {
  const box = await page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const un = (t) => { const s = n(t); const h = s.slice(0, s.length / 2);
      if (s.length % 2 === 0 && h === s.slice(s.length / 2)) return h.trim();
      // "Delete Annotation Delete Annotation": a multi-word label doubled with a space between.
      const twice = s.match(/^(.+) \1$/);
      if (twice) return twice[1];
      return /^(\S+)( \1)+$/.test(s) ? s.split(' ')[0] : s; };
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const el = [...scope.querySelectorAll('button, [role=button], .Button')].find((b) => b.offsetWidth > 0 && !b.disabled
      && !b.classList.contains('disabled')
      && (un(b.textContent) === wanted || n(b.getAttribute('title')) === wanted || n(b.getAttribute('aria-label')) === wanted));
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }, [label]);
  if (!box) return false;
  await page.mouse.click(box.x, box.y);
  return true;
}

/**
 * Type into the box whose FormGroup label is `label` — a string, or a list of acceptable labels,
 * because a create dialog and its edit dialog are different views and rarely agree: Node Groups
 * creates through `NameViewImpl` ("Name") and edits through `NodeGroupEditViewImpl`
 * ("Node Group Name"). With no label, the first EMPTY box.
 */
export async function fillField(page, label, text) {
  const box = await page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const boxes = [...scope.querySelectorAll('input[type="text"], input[type="password"], input:not([type]), textarea')]
      .filter((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly && !e.classList.contains('quickFilter-textBox'));
    const want = wanted === null ? null : (Array.isArray(wanted) ? wanted : [wanted]);
    const el = want
      ? boxes.find((e) => want.includes(n(e.closest('.form-group')?.querySelector('.form-group__label')?.textContent)))
      : boxes.find((e) => !n(e.value));
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }, [label ?? null]);
  if (!box) return false;
  await page.mouse.click(box.x, box.y);
  await page.keyboard.press('Control+A');
  if (text) await page.keyboard.type(text, { delay: 15 });
  else await page.keyboard.press('Delete');
  return true;
}

/**
 * Click the tick box labelled `label`: the one inside the FormGroup of that label, or a
 * `CustomCheckBox` that carries the label itself (`.SimpleTickBox` with its own `.form-label`, in a
 * FormGroup with none, as Node Group's "Enabled" is). GWT renders a tick box as a div, not as a
 * bare checkbox (the CustomCheckBox's input is hidden), so the click has to land on the div.
 */
export async function clickTickBox(page, label) {
  const box = await page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const group = [...scope.querySelectorAll('.form-group')]
      .find((g) => n(g.querySelector('.form-group__label')?.textContent) === wanted);
    const own = [...scope.querySelectorAll('.SimpleTickBox')]
      .find((t) => t.offsetWidth > 0 && n(t.querySelector('.form-label')?.textContent) === wanted);
    const tick = group?.querySelector('.SimpleTickBox, .tickBox') ?? own;
    const el = tick?.querySelector('.checkmark') ?? tick ?? group?.querySelector('input[type="checkbox"]');
    if (!el || !(el.offsetWidth > 0)) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }, [label]);
  if (!box) return false;
  await page.mouse.click(box.x, box.y);
  return true;
}

/**
 * Is the tick box labelled `label` ticked? Found as `clickTickBox` finds it; null when there is none.
 * A `CustomCheckBox` keeps its state on a hidden input, a `TickBoxCell` in its class names.
 */
export async function readTick(page, label) {
  return page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const group = [...scope.querySelectorAll('.form-group')]
      .find((g) => n(g.querySelector('.form-group__label')?.textContent) === wanted);
    const own = [...scope.querySelectorAll('.SimpleTickBox')]
      .find((t) => t.offsetWidth > 0 && n(t.querySelector('.form-label')?.textContent) === wanted);
    const tick = group?.querySelector('.SimpleTickBox, .tickBox') ?? own;
    if (!tick) return null;
    const input = tick.querySelector('input[type="checkbox"]');
    return input ? input.checked : /\btickBox-tick\b/.test(tick.className);
  }, [label]).catch(() => null);
}

/** Click the row whose text contains `text`, on a cell with nothing interactive in it. */
export async function selectRow(page, text) {
  const box = await page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const grid = [...scope.querySelectorAll('.dataGridWidget, [role=grid]')].find((g) => g.offsetWidth > 0);
    const row = [...(grid?.querySelectorAll('tbody tr, [role=row]') ?? [])]
      .find((r) => r.offsetHeight > 0 && n(r.textContent).includes(wanted));
    if (!row) return null;
    // A cell with a link or a tick box consumes the click; prefer a plain one. And one WITH TEXT, as
    // the walker's `select-row` does: a query result's first column is blank, and a click there left
    // the row unselected, so the Annotate menu had no annotation ids and offered no Change Status.
    const cells = [...row.querySelectorAll('td, [role=gridcell]')]
      .filter((c) => c.offsetWidth > 0 && !c.querySelector('a, .tickBox, button, [role=button], svg'));
    const cell = cells.find((c) => n(c.textContent)) ?? cells[0] ?? row;
    // A row below the grid's fold is measured off-screen, and the click landed on nothing.
    cell.scrollIntoView({ block: 'center' });
    const r = cell.getBoundingClientRect();
    return { x: r.x + Math.min(r.width / 2, 60), y: r.y + r.height / 2 };
  }, [text]);
  if (!box) return false;
  await page.mouse.click(box.x, box.y);
  return true;
}

/**
 * Close whatever is open. A cycle that leaves a dialog up cannot reach the main menu at all — two
 * annotation cycles failed with `no main-menu group "Annotations"` for exactly that reason, and the
 * failure looked like the menu had vanished rather than like something was standing in front of it.
 */
export async function closeAnyDialogs(page, settle, limit = 4) {
  for (let i = 0; i < limit; i += 1) {
    const open = await dialogCaptions(page);
    if (!open.length) return true;
    if (!(await clickButton(page, 'Cancel')) && !(await clickButton(page, 'Close'))) {
      await page.keyboard.press('Escape').catch(() => {});
    }
    await settle(700);
  }
  return !(await dialogCaptions(page)).length;
}

/** Open the screen a cycle lives on: Main Menu -> group -> leaf. */
export async function openScreen(page, { clickMenuItem, settle }, group, leaf) {
  await closeAnyDialogs(page, settle);
  await clickButton(page, 'Main Menu');
  await settle(600);
  if (!(await clickMenuItem(page, group))) throw new Error(`no main-menu group "${group}"`);
  await settle(500);
  if (!(await clickMenuItem(page, leaf))) throw new Error(`no menu leaf "${leaf}"`);
  await settle(1500);
}

// ── The cycle ───────────────────────────────────────────────────────────────
/**
 * `spec` describes ONE thing that can be created, edited and deleted through the UI:
 *
 *   { name, menu: [group, leaf], nameField, seed, edited,
 *     create: 'New', edit: 'Edit', remove: 'Delete', confirm: true,
 *     writes: [{ method: 'POST', path: /\/api\/node\/nodeGroup\// }, …] }
 *
 * Returns a record: every phase with `ok` and why, whether the row leaked, and the writes the guard
 * let through against the writes the spec declared.
 */
export async function runCycle(page, spec, { guard, settle, log = () => {} }) {
  const rec = { name: spec.name, phases: [], leaked: false, writes: [], undeclared: [] };
  const phase = async (what, fn) => {
    const t = Date.now();
    try {
      const why = await fn();
      rec.phases.push({ what, ok: true, why, ms: Date.now() - t });
      log(`    ✓ ${what}${why ? ` — ${why}` : ''}`);
      return true;
    } catch (e) {
      rec.phases.push({ what, ok: false, why: String(e?.message ?? e).slice(0, 1200), ms: Date.now() - t });
      log(`    ✗ ${what} — ${String(e?.message ?? e).slice(0, 1000)}`);
      return false;
    }
  };
  /**
   * Is `text` in the grid? On a screen that PAGES, the visible rows are not the grid, so when the
   * spec says `findBy: 'filter'` the grid's own quick filter is used to bring the row into view —
   * and cleared again afterwards so the next phase starts from the whole list.
   */
  const has = async (text) => {
    if ((await readRows(page)).some((r) => r.includes(text))) return true;
    if (spec.findBy !== 'filter') return false;
    if (!(await filterGrid(page, text, settle))) return false;
    const found = (await readRows(page)).some((r) => r.includes(text));
    await filterGrid(page, '', settle);
    return found;
  };
  const at = async () => { await openScreen(page, spec.helpers, spec.menu[0], spec.menu[1]); };
  /** Select a row, filtering the grid first when the screen pages and the row may not be on it. */
  const pick = async (text) => {
    if (await selectRow(page, text)) return true;
    if (spec.findBy !== 'filter') return false;
    if (!(await filterGrid(page, text, settle))) return false;
    return selectRow(page, text);
  };

  let created = false;
  try {
    if (!await phase('open the screen', async () => {
      await at();
      return `${(await readRows(page)).length} row(s)`;
    })) return rec;

    // NEVER WRITE WHAT YOU CANNOT FIND. User Groups holds 163 rows and shows 100, so a `zz-cycle-…`
    // name sorts onto page two: the create succeeded, nothing could see it, and the compensation could
    // not select it to delete it — one group left behind on the instance. A screen that pages needs a
    // way to find the row, and without one this refuses to create rather than leak.
    if (!await phase('the new row will be findable', async () => {
      const total = await gridTotal(page);
      const shown = (await readRows(page)).length;
      const more = await morePages(page);
      const paging = more || (total !== null && total > shown);
      const how = total !== null ? `${total} row(s) over ${shown} shown` : `${shown} shown and a next page`;
      if (!paging) return `${total ?? shown} row(s), all on one page`;
      if (spec.findBy === 'filter') {
        if (!(await gridFilter(page))) throw new Error(`${how} and no quick filter belongs to this grid`);
        return `${how} — the grid's quick filter will find it`;
      }
      throw new Error(`${how}, and this spec has no \`findBy\` — the new row could sort onto a later page, nothing would find it, and the create would LEAK`);
    })) return rec;

    if (!await phase('the seed name is not already there', async () => {
      if (await has(spec.seed)) throw new Error(`"${spec.seed}" is already present — a previous cycle leaked it`);
      if (await has(spec.edited)) throw new Error(`"${spec.edited}" is already present — a previous cycle leaked it`);
      return 'clean';
    })) return rec;

    // The number of writes the guard has ALLOWED so far. If a create goes through, the thing
    // exists whether or not the assertion after it held — and compensation must run either way.
    // Keying `created` off the phase passing is how the first real run leaked a row.
    const allowedBefore = (guard.allowed ?? []).length;
    const madeIt = await phase(`create "${spec.seed}"`, async () => {
      if (!(await clickButton(page, spec.create ?? 'New'))) throw new Error(`no "${spec.create ?? 'New'}" button`);
      await settle(1000);
      if (!(await fillField(page, spec.nameField, spec.seed))) throw new Error('no name box in the create dialog');
      // Whatever else the create insists on, by label: New Credentials' default type wants a user
      // name and password ("Username must not be empty").
      for (const [label, value] of Object.entries(spec.createFields ?? {})) {
        if (!(await fillField(page, label, value))) throw new Error(`no "${label}" box in the create dialog`);
      }
      if (!(await clickButton(page, 'OK'))) throw new Error('no OK in the create dialog');
      await settle(1500);
      const open = await dialogCaptions(page);
      if (!open.length) return 'dialog closed';
      const top = String(open.at(-1));
      // A create dialog that comes back as an EDIT dialog on the thing just made has succeeded and
      // stayed open on it — `New` on Node Groups reopens as `Edit Node Group - <name>`. That is the
      // UI's pattern, not a failure; close it and let the read-back say whether it is really there.
      if (/^Edit\b/i.test(top) || top.includes(spec.seed)) {
        if (!(await clickButton(page, 'Cancel')) && !(await clickButton(page, 'Close'))) {
          throw new Error(`created, but "${top}" would not close`);
        }
        await settle(1200);
        return `created; the dialog reopened as "${top}" and was closed`;
      }
      const said = await readTopDialogBody(page);
      throw new Error(`the dialog stayed open: [${open.join(', ')}]${said?.text ? ` — "${String(said.text).slice(0, 300)}"` : ''}`);
    });
    // Either the phase held, or the guard saw the write go out. Both mean there is something to
    // undo, and the `finally` below is what undoes it.
    created = madeIt || (guard.allowed ?? []).length > allowedBefore;
    if (!madeIt) return rec;

    await phase('read it back', async () => {
      if (await has(spec.seed)) return 'present';
      // The list did not show it. Refresh, then re-open the screen — and say which was needed,
      // because "the grid does not refresh itself after a create" is a finding about the screen,
      // not a reason to abandon the cycle.
      if (await clickButton(page, 'Refresh')) {
        await settle(1200);
        if (await has(spec.seed)) return 'present, but only after pressing Refresh — the list does not update itself on create';
      }
      await at();
      if (await has(spec.seed)) return 'present, but only after re-opening the screen — the list does not update itself on create';
      throw new Error(`"${spec.seed}" is not in the grid after create, even after Refresh and re-opening`);
    });

    // What the edit CHANGES depends on the resource. A node group's name is not editable at all —
    // `NodeGroupEditViewImpl`'s box comes back `disabled` — so a spec may nominate a tick box
    // instead, and the oracle becomes "the row renders differently", read from the grid.
    const rowOf = async (text) => (spec.editToggle ? rowSignature(page, text)
      : (await readRows(page)).find((r) => r.includes(text)) ?? null);
    const rowBefore = await rowOf(spec.seed);
    const toggling = !!spec.editToggle;
    await phase(toggling ? `edit it — toggle "${spec.editToggle}"` : `edit it to "${spec.edited}"`, async () => {
      if (!(await pick(spec.seed))) throw new Error('could not select the new row');
      await settle(600);
      // Some screens interpolate the ROW's name into the button: User Groups offers
      // `Edit group 'Administrators'`, not `Edit`. So a spec may give a function of the name.
      const editLabel = typeof spec.edit === 'function' ? spec.edit(spec.seed) : (spec.edit ?? 'Edit');
      if (!(await clickButton(page, editLabel))) throw new Error(`no "${editLabel}" button (is it disabled without a selection?)`);
      await settle(1000);
      if (toggling) {
        if (!(await clickTickBox(page, spec.editToggle))) throw new Error(`no "${spec.editToggle}" tick box in the edit dialog`);
      } else if (!(await fillField(page, spec.editNameField ?? spec.nameField, spec.edited))) {
        const open = (await dialogCaptions(page)).join(', ');
        throw new Error(`no editable name box in [${open}] — labels tried: ${[spec.editNameField ?? spec.nameField].flat().join(' / ')}`);
      }
      // What the edit insists on having entered again: a credential's secret is write-only, so its
      // edit dialog opens with the user name and password empty and refuses OK until both are given.
      for (const [label, value] of Object.entries(spec.editFields ?? {})) {
        if (!(await fillField(page, label, value))) throw new Error(`no "${label}" box in the edit dialog`);
      }
      if (!(await clickButton(page, 'OK'))) throw new Error('no OK in the edit dialog');
      await settle(1500);
      const open = await dialogCaptions(page);
      if (open.length) {
        // Quote what it SAID. "[Edit Comment - …, Alert]" on its own needed a second run to read.
        const said = await readTopDialogBody(page);
        throw new Error(`the edit dialog stayed open: [${open.join(', ')}]`
          + `${said?.text ? ` — "${String(said.text).slice(0, 600)}"` : ''}`
          + `${said?.detail ? ` · detail: "${String(said.detail).slice(0, 600)}"` : ''}`);
      }
      return 'dialog closed';
    });

    // The RELOAD is the point: a presenter that only updated its own view passes without it.
    await phase('reload the screen and read it back', async () => {
      await page.reload({ waitUntil: 'domcontentloaded' });
      await settle(4000);
      await at();
      if (toggling) {
        const now = await rowOf(spec.seed);
        if (!now) throw new Error(`"${spec.seed}" is gone after the edit`);
        if (now === rowBefore) throw new Error(`the row is unchanged after toggling "${spec.editToggle}": "${now}" — the edit did not persist`);
        return `the row changed and survived a reload: "${rowBefore}" -> "${now}"`;
      }
      if (!(await has(spec.edited))) throw new Error(`"${spec.edited}" is not there after a reload — the edit did not persist`);
      // The old name must be GONE — but `edited` is `seed + " (edited)"`, so a plain substring
      // test finds the seed inside the edited row and the check could never pass. Look for a row
      // that carries the seed and NOT the edit.
      const stale = (await readRows(page)).filter((r) => r.includes(spec.seed) && !r.includes(spec.edited));
      if (stale.length) throw new Error(`"${spec.seed}" is STILL there after the edit (${stale.length} row(s)) — the edit added a row instead of changing one`);
      return 'the edit survived a reload';
    });
  } finally {
    // ── Compensation. Runs whatever happened above, and its failure is the finding. ──
    if (created) {
      const gone = await phase('delete it again (compensation)', async () => {
        await at();
        const target = (!spec.editToggle && await has(spec.edited)) ? spec.edited : spec.seed;
        if (!(await pick(target))) throw new Error(`could not select "${target}" to delete it`);
        await settle(600);
        const removeLabel = typeof spec.remove === 'function' ? spec.remove(target) : (spec.remove ?? 'Delete');
        if (!(await clickButton(page, removeLabel))) throw new Error(`no "${removeLabel}" button`);
        await settle(900);
        if (spec.confirm !== false) {
          const captions = await dialogCaptions(page);
          if (captions.length && !(await clickButton(page, 'OK')) && !(await clickButton(page, 'Yes'))) {
            throw new Error(`a confirm appeared and neither OK nor Yes was there: [${captions.join(', ')}]`);
          }
          await settle(1500);
        }
        return 'deleted';
      });
      await phase('read it absent', async () => {
        await at();
        // These grids do not refresh themselves after a mutation (the read-back phase says so on
        // create), so a stale list would report a LEAK that is not one. Refresh before concluding:
        // claiming the instance is dirty when it is clean is the worst thing this file can say.
        if (await clickButton(page, 'Refresh')) await settle(1200);
        if (await has(spec.edited) || await has(spec.seed)) {
          await at();
          if (await clickButton(page, 'Refresh')) await settle(1200);
          if (await has(spec.edited) || await has(spec.seed)) {
            rec.leaked = true;
            throw new Error('the row is STILL in the grid after the delete, after a Refresh and a re-open — THE INSTANCE IS NOW DIRTY');
          }
        }
        return 'gone';
      });
      if (!gone) rec.leaked = true;
      // A second thing the create made somewhere else, which the delete above leaves: an account's
      // Stroom user (AccountServiceImpl.create makes one; deleting the account keeps it, as the user
      // holds permissions and history). Removed on its own screen, and read absent there.
      const also = spec.alsoRemove;
      if (also) {
        const name = also.text ?? spec.seed;
        const there = async () => {
          if ((await readRows(page)).some((r) => r.includes(name))) return true;
          if (!(await filterGrid(page, name, settle))) return false;
          return (await readRows(page)).some((r) => r.includes(name));
        };
        const removed = await phase(`delete the ${also.what} it also made (second compensation)`, async () => {
          await openScreen(page, spec.helpers, also.menu[0], also.menu[1]);
          if (!(await there())) throw new Error(`no ${also.what} "${name}" to delete — was one made?`);
          if (!(await selectRow(page, name))) throw new Error(`could not select the ${also.what} "${name}"`);
          await settle(600);
          const label = typeof also.remove === 'function' ? also.remove(name) : also.remove;
          if (!(await clickButton(page, label))) throw new Error(`no "${label}" button`);
          await settle(900);
          if ((await dialogCaptions(page)).length && !(await clickButton(page, 'OK')) && !(await clickButton(page, 'Yes'))) {
            throw new Error(`a confirm appeared with neither OK nor Yes: [${(await dialogCaptions(page)).join(', ')}]`);
          }
          await settle(1500);
          return `deleted the ${also.what} "${name}"`;
        });
        await phase(`read the ${also.what} absent`, async () => {
          await openScreen(page, spec.helpers, also.menu[0], also.menu[1]);
          if (await clickButton(page, 'Refresh')) await settle(1200);
          if (await there()) {
            rec.leaked = true;
            throw new Error(`the ${also.what} "${name}" is STILL there after its delete — THE INSTANCE IS NOW DIRTY`);
          }
          return 'gone';
        });
        if (!removed) rec.leaked = true;
      }
    }
    // What the guard let through, against what the spec said it would.
    rec.writes = (guard.allowed ?? []).map((w) => `${w.method} ${w.path}`);
    rec.undeclared = (guard.violations ?? []).map((v) => `${v.method} ${v.path}`);
  }
  rec.ok = rec.phases.every((p) => p.ok) && !rec.leaked && !rec.undeclared.length;
  return rec;
}

/** The value a field holds right now, by its FormGroup label, inside the topmost dialog. */
export function readField(page, label) {
  return page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const want = Array.isArray(wanted) ? wanted : [wanted];
    const el = [...scope.querySelectorAll('input[type="text"], input:not([type]), textarea')]
      .filter((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly && !e.classList.contains('quickFilter-textBox'))
      .find((e) => want.includes(n(e.closest('.form-group')?.querySelector('.form-group__label')?.textContent)));
    return el ? String(el.value ?? '') : null;
  }, [label]).catch(() => null);
}

/**
 * The EDIT-AND-REVERT cycle — § B4's second shape.
 *
 * Most write paths are a create, an edit and a delete, and `runCycle` undoes them by deleting what it
 * made. But a dozen of the OK handlers the read-only guard blocks belong to things that cannot be
 * created or deleted at all: a global property, the user's own preferences, a document permission, a
 * node's status. There is exactly one of each and it is always there.
 *
 * For those, the compensation is to put the OLD VALUE back, and the cycle is:
 *
 *   read the row -> open the editor -> read the field -> change it -> OK
 *   RELOAD -> the row must show the new value          <- the phase that matters
 *   open the editor -> set the old value -> OK
 *   RELOAD -> the row must show the old value again    <- the compensation, verified
 *
 * The reload is the point, exactly as in `runCycle`: a presenter that only updated its own view
 * passes without one. And the revert is verified rather than assumed — an edit cycle that cannot put
 * the value back is a LEAK, and it says which value it was, because nothing else will know.
 */
export async function runEditCycle(page, spec, { guard, settle, log = () => {} }) {
  const rec = { name: spec.name, kind: 'edit', phases: [], leaked: false, writes: [], undeclared: [] };
  const phase = async (what, fn) => {
    const t = Date.now();
    try {
      const why = await fn();
      rec.phases.push({ what, ok: true, why, ms: Date.now() - t });
      log(`    ✓ ${what}${why ? ` — ${why}` : ''}`);
      return true;
    } catch (e) {
      rec.phases.push({ what, ok: false, why: String(e?.message ?? e).slice(0, 1200), ms: Date.now() - t });
      log(`    ✗ ${what} — ${String(e?.message ?? e).slice(0, 1000)}`);
      return false;
    }
  };
  const at = async () => { await openScreen(page, spec.helpers, spec.menu[0], spec.menu[1]); };
  /** Open the row's editor and leave the dialog up. */
  const openEditor = async () => {
    // A long list (Properties holds hundreds) is filtered to the row first, when the spec says so.
    if (spec.row && !(await selectRow(page, spec.row))
      && !(spec.findBy === 'filter' && await filterGrid(page, spec.row, settle) && await selectRow(page, spec.row))) {
      throw new Error(`no row matching "${spec.row}"`);
    }
    await settle(400);
    // An opener can be several presses: Ask Stroom AI's config is its pane's Configure button, and the
    // pane opens from the top bar's Ask Stroom AI.
    for (const opener of [spec.opener ?? 'Edit'].flat()) {
      if (!(await clickButton(page, opener))) throw new Error(`"${opener}" would not take a click`);
      await settle(1200);
    }
    if (!(await dialogCaptions(page)).length) throw new Error('no dialog opened');
  };
  let original = null;
  let changed = false;
  // A value that only counts when a tick box says so — a property's "Set Database value" — is
  // ticked for the change and, if it was unticked before, UNTICKED for the revert: that removes the
  // database row, where typing the old value back would leave one holding it.
  let wasTicked = null;
  try {
    if (!await phase('open the screen', async () => { await at(); return `${(await readRows(page)).length} row(s)`; })) return rec;

    if (!await phase(`open the editor and read "${spec.field}"`, async () => {
      await openEditor();
      if (spec.overrideTick) {
        wasTicked = await readTick(page, spec.overrideTick);
        if (wasTicked === null) throw new Error(`no "${spec.overrideTick}" tick box in the dialog`);
        if (!wasTicked && !(await clickTickBox(page, spec.overrideTick))) throw new Error(`"${spec.overrideTick}" would not take a click`);
        await settle(300);
      }
      original = await readField(page, spec.field);
      if (original === null) throw new Error(`no field labelled "${spec.field}" in the dialog`);
      if (spec.overrideTick && !wasTicked) original = '';
      return `it holds ${JSON.stringify(original)}${spec.overrideTick ? ` ("${spec.overrideTick}" was ${wasTicked ? 'ticked' : 'unticked'})` : ''}`;
    })) { await closeAnyDialogs(page, settle); return rec; }

    const target = typeof spec.value === 'function' ? spec.value(original) : spec.value;
    if (String(target) === String(original)) {
      rec.phases.push({ what: 'choose a new value', ok: false, why: `the new value equals the old one (${JSON.stringify(original)}) — nothing would be tested` });
      log(`    ✗ the new value equals the old one — nothing would be tested`);
      await closeAnyDialogs(page, settle);
      return rec;
    }

    if (!await phase(`change it to ${JSON.stringify(target)}`, async () => {
      if (!(await fillField(page, spec.field, target))) throw new Error('could not type into the field');
      if (!(await clickButton(page, 'OK'))) throw new Error('OK would not take a click');
      await settle(1500);
      const still = await dialogCaptions(page);
      if (still.length) throw new Error(`the dialog stayed open: [${still.join(', ')}]`);
      changed = true;
      return 'saved';
    })) { await closeAnyDialogs(page, settle); return rec; }

    // THE phase: a reload proves the server has it, not just this view.
    await phase('reload the screen and read it back', async () => {
      await page.reload({ waitUntil: 'domcontentloaded', timeout: 60_000 });
      await settle(2500);
      if (await page.locator('input[type=password]').count()) throw new Error('the reload signed us out');
      await at();
      await openEditor();
      const now = await readField(page, spec.field);
      await closeAnyDialogs(page, settle);
      if (String(now) !== String(target)) throw new Error(`after a reload it holds ${JSON.stringify(now)}, not ${JSON.stringify(target)}`);
      return `still ${JSON.stringify(now)} after a reload`;
    });
  } finally {
    // Compensation: put the old value back, and PROVE it went back.
    if (changed) {
      const back = await phase(`put ${JSON.stringify(original)} back (compensation)`, async () => {
        await closeAnyDialogs(page, settle);
        await at();
        await openEditor();
        const untick = spec.overrideTick && wasTicked === false;
        if (untick) {
          if (!(await clickTickBox(page, spec.overrideTick))) throw new Error(`"${spec.overrideTick}" would not take a click`);
        } else if (!(await fillField(page, spec.field, original))) throw new Error('could not type the old value');
        if (!(await clickButton(page, 'OK'))) throw new Error('OK would not take a click');
        await settle(1500);
        return untick ? `restored by unticking "${spec.overrideTick}"` : 'restored';
      });
      const verified = back && await phase('reload and confirm it is back', async () => {
        await page.reload({ waitUntil: 'domcontentloaded', timeout: 60_000 });
        await settle(2500);
        await at();
        await openEditor();
        if (spec.overrideTick && wasTicked === false) {
          const ticked = await readTick(page, spec.overrideTick);
          await closeAnyDialogs(page, settle);
          if (ticked !== false) throw new Error(`"${spec.overrideTick}" is still ticked: the database value is still set`);
          return `"${spec.overrideTick}" unticked again`;
        }
        const now = await readField(page, spec.field);
        await closeAnyDialogs(page, settle);
        if (String(now) !== String(original)) throw new Error(`it holds ${JSON.stringify(now)}, not the original ${JSON.stringify(original)}`);
        return `${JSON.stringify(now)}`;
      });
      if (!verified) {
        rec.leaked = `${spec.name}: "${spec.field}" may still hold a test value — the original was ${JSON.stringify(original)}`;
        log(`    ! LEAKED — ${rec.leaked}`);
      }
    }
    await closeAnyDialogs(page, settle);
    // As `runCycle` records them: the guard's `allowed` are the writes, and `ok` is what the runner
    // and the inventory read. (This shape first ran with MUTATE=1 on 2026-10-12, and recorded
    // neither, so a cycle whose every phase held was reported FAIL with nothing written.)
    rec.writes = (guard?.allowed ?? []).map((w) => `${w.method} ${w.path}`);
    rec.undeclared = (guard?.violations ?? []).map((v) => `${v.method} ${v.path}`);
  }
  rec.ok = rec.phases.every((p) => p.ok) && !rec.leaked && !rec.undeclared.length;
  rec.verdict = rec.ok ? 'pass' : 'fail';
  return rec;
}

/**
 * The quick filter that belongs to the FIRST VISIBLE GRID — not merely the first on the page.
 *
 * User Groups carries three: the explorer sidebar's, the groups grid's and the members grid's. Taking
 * the first visible one types into the SIDEBAR, which filters nothing and leaves the grid showing all
 * 100 rows of page one. A filter sits directly above its own grid, so the right one is the box whose
 * x falls inside the grid's columns and whose y is above its top edge.
 */
export function gridFilter(page) {
  return page.evaluate(() => {
    const grid = [...document.querySelectorAll('.dataGridWidget, [role=grid]')].find((g) => g.offsetWidth > 0);
    if (!grid) return null;
    const g = grid.getBoundingClientRect();
    const boxes = [...document.querySelectorAll('.quickFilter-textBox')].filter((e) => e.offsetWidth > 0);
    const mine = boxes
      .map((e) => ({ e, r: e.getBoundingClientRect() }))
      .filter(({ r }) => r.x + r.width > g.x && r.x < g.x + g.width && r.y <= g.y)
      // The closest one above the grid, when a screen stacks two of them.
      .sort((a, b) => (g.y - a.r.y) - (g.y - b.r.y))[0];
    if (!mine) return null;
    return { x: Math.round(mine.r.x + mine.r.width / 2), y: Math.round(mine.r.y + mine.r.height / 2) };
  }).catch(() => null);
}

/**
 * Is there MORE than what is on screen? An enabled Forward/Next/Last button says so.
 *
 * This exists because reading the pager's "of N" is not enough: User Groups shows no total at all, so
 * the total-based check fell through to "assuming 100 rows is all of them" on a screen holding 163 —
 * the guard passed by accident on the very screen whose leak it was written for. An enabled
 * next-page button is the signal that does not depend on a total being displayed.
 */
export function morePages(page) {
  return page.evaluate(() => {
    const un = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const btns = [...document.querySelectorAll('button, [role=button], .Button')].filter((b) => b.offsetWidth > 0);
    return btns.some((b) => /^(Forward|Next|Last)$/i.test(un(b.getAttribute('title')) || un(b.textContent))
      && !b.disabled && !b.classList.contains('disabled'));
  }).catch(() => false);
}

/** Type `text` into the grid's own quick filter and let it settle. Returns false if there is none. */
export async function filterGrid(page, text, settle) {
  const at = await gridFilter(page);
  if (!at) return false;
  await page.mouse.click(at.x, at.y);
  await page.keyboard.press('Control+A');
  if (text) await page.keyboard.type(text, { delay: 20 });
  else await page.keyboard.press('Delete');
  await settle(2200);
  return true;
}

/**
 * How many rows the grid says it HAS, from the pager's "of N" — not how many are on screen.
 *
 * This is the number that matters before a cycle writes anything: User Groups holds 163 and shows 100,
 * so a row named `zz-cycle-…` sorts onto page two and `readRows` can never see it. A cycle that cannot
 * find what it made cannot delete it either, and that is exactly how one group got left behind.
 */
export function gridTotal(page) {
  return page.evaluate(() => {
    const pager = [...document.querySelectorAll('.pager-paging')].find((p) => p.offsetWidth > 0);
    if (!pager) return null;
    const labels = [...pager.querySelectorAll('.pager-label')].filter((e) => e.offsetWidth > 0);
    const last = labels[labels.length - 1];
    const m = /(-?[\d,]+)/.exec(String(last?.textContent ?? ''));
    return m ? Number(m[1].replace(/,/g, '')) : null;
  }).catch(() => null);
}

/**
 * The DOCUMENT cycle — § B4's third shape, for the explorer's own write paths.
 *
 *   New > [category >] type under the seed folder -> name it -> OK          (CreateDocumentPresenter)
 *   RELOAD the tree -> the document is there
 *   Rename -> the new name -> OK                                             (NameDocumentPresenter)
 *   RELOAD the tree -> the new name is there and the old one is not
 *   Delete -> confirm -> RELOAD the tree -> it is gone                       (compensation, always)
 *
 * The tree is read after a full page reload, so what is read back is the server's, not the
 * explorer's own idea of what it just did. #31 lived here: a create the server refused with a 403
 * while the first seed script read the open dialog as success.
 *
 * `spec.helpers` supplies `clickMenuItem`, `rightClickByText`, `dismissMenus` and `reloadTree`
 * (a page reload, signed in, with the explorer fully expanded).
 */
export async function runDocCycle(page, spec, { guard, settle, log = () => {} }) {
  const { clickMenuItem, rightClickByText, dismissMenus, reloadTree } = spec.helpers;
  const rec = { name: spec.name, kind: 'doc', phases: [], leaked: false, writes: [], undeclared: [] };
  const phase = async (what, fn) => {
    const t = Date.now();
    try {
      const why = await fn();
      rec.phases.push({ what, ok: true, why, ms: Date.now() - t });
      log(`    ✓ ${what}${why ? ` — ${why}` : ''}`);
      return true;
    } catch (e) {
      rec.phases.push({ what, ok: false, why: String(e?.message ?? e).slice(0, 1200), ms: Date.now() - t });
      log(`    ✗ ${what} — ${String(e?.message ?? e).slice(0, 1000)}`);
      return false;
    }
  };
  // The tree's rows as `type|label`: the icon's title is the document type.
  const tree = () => page.evaluate(() => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    return [...document.querySelectorAll('.explorerCell')]
      // The name without the icon's own text: an inline SVG's <style> or <title> is in textContent
      // (Elastic Index's `.st0{fill:…}`, OpenAI Model's "OpenAI icon"), so a name never matched.
      .map((c) => `${n(c.querySelector('.explorerCell-icon')?.getAttribute('title'))}|${n([...c.querySelectorAll('*:not(svg):not(svg *)')]
        .filter((e) => !e.children.length).map((e) => e.textContent).join(' '))}`);
  }).catch(() => []);
  // By name alone: the icon's title is not always the menu's name for the type (XSL Translation),
  // and the stamped name is unique to this run anyway.
  const has = async (name) => (await tree()).some((r) => r.endsWith(`|${name}`));
  // What an Alert said, so a refusal is quoted rather than inferred from a missing row.
  const alertText = async () => {
    if (!(await dialogCaptions(page)).some((c) => /alert/i.test(c))) return null;
    return (await readTopDialogBody(page))?.text ?? 'an Alert with no text';
  };
  const menuPath = async (target, items) => {
    await dismissMenus(page).catch(() => {});
    if (!(await rightClickByText(page, '.explorerCell', target))) throw new Error(`no explorer row "${target}"`);
    for (const item of items) {
      if (!(await clickMenuItem(page, item))) throw new Error(`no "${item}" in the menu (after ${items.slice(0, items.indexOf(item)).join(' › ') || 'the right-click'})`);
      await settle(600);
    }
  };

  let created = false;
  // The server said no to the create. Only then may an absent row mean "nothing was made": a row the
  // tree reader misses must still be reported, as an SVG icon's text once hid two from it.
  let refused = false;
  try {
    if (!await phase('open the tree', async () => {
      await reloadTree();
      if (!(await tree()).some((r) => r.endsWith(`|${spec.parent}`))) throw new Error(`no "${spec.parent}" folder to create in`);
      return `${(await tree()).length} row(s), "${spec.parent}" present`;
    })) return rec;

    if (!await phase('the seed name is not already there', async () => {
      if (await has(spec.seed)) throw new Error(`"${spec.seed}" is already present — a previous cycle leaked it`);
      if (await has(spec.edited)) throw new Error(`"${spec.edited}" is already present — a previous cycle leaked it`);
      return 'clean';
    })) return rec;

    const allowedBefore = (guard.allowed ?? []).length;
    const madeIt = await phase(`create ${spec.type} "${spec.seed}"`, async () => {
      await menuPath(spec.parent, ['New', ...(spec.category ? [spec.category] : []), spec.type]);
      await settle(600);
      if (!(await dialogCaptions(page)).length) throw new Error('New opened no dialog');
      // The name box is the full-width one that is not the folder picker's quick filter.
      const nameBox = page.locator('.resizableDialog-popup, .dialog-popup').last()
        .locator('input.gwt-TextBox.w-100:not(.quickFilter-textBox)').first();
      await nameBox.fill(spec.seed, { timeout: 10_000 });
      if (!(await clickButton(page, 'OK'))) throw new Error('no OK in the New dialog');
      await settle(2000);
      const said = await alertText();
      if (said) { refused = true; throw new Error(`the create was refused: "${String(said).slice(0, 300)}"`); }
      if ((await dialogCaptions(page)).length) throw new Error(`the dialog stayed open: [${(await dialogCaptions(page)).join(', ')}]`);
      return 'dialog closed';
    });
    created = madeIt || (guard.allowed ?? []).length > allowedBefore;
    if (!madeIt) return rec;

    if (!await phase('reload and read it back', async () => {
      await reloadTree();
      if (!(await has(spec.seed))) throw new Error(`"${spec.seed}" is not in the tree after a reload`);
      return 'present';
    })) return rec;

    // What a spec does to the made document before its rename: the permission cycle's changes and
    // reverts (`permissionSteps`). A failed step stops the rest; the delete below still runs, and
    // it takes whatever the steps left with it.
    for (const [what, fn] of spec.steps ?? []) {
      if (!await phase(what, () => fn({ page, settle, menuPath, alertText, name: spec.seed }))) return rec;
    }

    // Some types can't be renamed at all — a feed's name is its identity (gchq/stroom#2912), and
    // Rename is disabled for it — so the spec says so and the cycle is create, read back, delete.
    if (spec.rename === false) {
      const why = spec.renameWhy ?? `not offered for ${spec.type} (by design)`;
      rec.phases.push({ what: 'rename', ok: true, why, ms: 0 });
      log(`    · rename — ${why}`);
      return rec;
    }
    await phase(`rename it to "${spec.edited}"`, async () => {
      await menuPath(spec.seed, ['Rename']);
      if (!(await dialogCaptions(page)).length) throw new Error('Rename opened no dialog');
      if (!(await fillField(page, null, spec.edited)) && !(await fillField(page, 'Name', spec.edited))) {
        // A pre-filled box: fillField(null) wants an empty one, so take the dialog's only text box.
        const box = page.locator('.resizableDialog-popup, .dialog-popup').last().locator('input.gwt-TextBox').first();
        await box.fill(spec.edited, { timeout: 5_000 });
      }
      if (!(await clickButton(page, 'OK'))) throw new Error('no OK in the Rename dialog');
      await settle(1500);
      const said = await alertText();
      if (said) throw new Error(`the rename was refused: "${String(said).slice(0, 300)}"`);
      if ((await dialogCaptions(page)).length) throw new Error(`the dialog stayed open: [${(await dialogCaptions(page)).join(', ')}]`);
      return 'dialog closed';
    });

    await phase('reload and read the new name back', async () => {
      await reloadTree();
      if (!(await has(spec.edited))) throw new Error(`"${spec.edited}" is not in the tree after a reload — the rename did not persist`);
      if (await has(spec.seed)) throw new Error(`"${spec.seed}" is STILL in the tree — the rename added a document instead of renaming one`);
      return 'renamed';
    });
  } finally {
    if (created) {
      const gone = await phase('delete it again (compensation)', async () => {
        await closeAnyDialogs(page, settle);
        await reloadTree();
        // A create the server REFUSED made nothing (Plan B's "The state store key must match…"),
        // though the request went out, so there is nothing to delete and nothing has leaked.
        if (!(await has(spec.edited)) && !(await has(spec.seed))) {
          if (refused) return 'nothing to delete: the server refused the create';
          throw new Error(`"${spec.seed}" was created but is not in the tree to delete — find it by hand`);
        }
        const target = (await has(spec.edited)) ? spec.edited : spec.seed;
        await menuPath(target, ['Delete']);
        // "Are you sure you want to delete …?"
        if ((await dialogCaptions(page)).length && !(await clickButton(page, 'OK')) && !(await clickButton(page, 'Yes'))) {
          throw new Error(`a confirm appeared with neither OK nor Yes: [${(await dialogCaptions(page)).join(', ')}]`);
        }
        await settle(1500);
        const said = await alertText();
        if (said) throw new Error(`the delete was refused: "${String(said).slice(0, 300)}"`);
        return `deleted "${target}"`;
      });
      await phase('reload and read it absent', async () => {
        await reloadTree();
        if (await has(spec.edited) || await has(spec.seed)) {
          rec.leaked = true;
          throw new Error('the document is STILL in the tree after the delete and a reload — THE INSTANCE IS NOW DIRTY');
        }
        return 'gone';
      });
      if (!gone) rec.leaked = true;
    }
    await closeAnyDialogs(page, settle);
    rec.writes = (guard?.allowed ?? []).map((w) => `${w.method} ${w.path}`);
    rec.undeclared = (guard?.violations ?? []).map((v) => `${v.method} ${v.path}`);
    // Set here, not after the try: a cycle that returns early (no rename) still gets a verdict.
    rec.ok = rec.phases.every((p) => p.ok) && !rec.leaked && !rec.undeclared.length;
  }
  return rec;
}

// ── Document permissions ────────────────────────────────────────────────────
/**
 * Choose `item` in the `SelectionBox` whose form group is labelled `label`, in the topmost dialog.
 * Opened by its ICON and chosen with a mousedown, as `nudge` in dirty.mjs does: a click on the text
 * box opens the list and then bubbles to the box, which shuts it again, and `BaseSelectionBox`
 * listens for mousedown, not click. Returns the text the box shows afterwards.
 */
export async function chooseInSelectionBox(page, label, item, settle) {
  const scope = page.locator('.dialog-popup, .resizableDialog-popup').last();
  const group = scope.locator('.form-group').filter({ has: page.locator('.form-group__label', { hasText: label }) }).first();
  await group.locator('.SelectionBox .svgIconBox-icon-outer').first().click({ timeout: 5_000 });
  await settle(600);
  const at = await page.evaluate((wanted) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const row = [...document.querySelectorAll('.selectionList-elementChooser tr, .selectionList-elementChooser .dataGridRow')]
      .find((e) => e.offsetWidth > 0 && n(e.textContent) === wanted);
    if (!row) return null;
    const r = row.getBoundingClientRect();
    return { x: r.x + Math.min(r.width / 2, 40), y: r.y + r.height / 2 };
  }, item);
  if (!at) {
    await page.keyboard.press('Escape').catch(() => {});
    throw new Error(`"${item}" is not in the "${label}" list`);
  }
  await page.mouse.move(at.x, at.y);
  await page.mouse.down();
  await page.mouse.up();
  await settle(400);
  return (await group.locator('.SelectionBox-renderBox').first().textContent()).replace(/\s+/g, ' ').trim();
}

/**
 * The cells of the first row containing `text` in the topmost grid, keyed by column heading. A
 * heading's sort marker ("Display Name 1") is dropped, so the keys are the headings as read. A cell
 * that draws icons instead of text (a permission's create types, one icon per type) reads as its
 * icons' titles; an inline SVG's own text is never read, as it is not what the cell says.
 */
export function readGridRow(page, text) {
  return page.evaluate((wanted) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const grid = [...scope.querySelectorAll('.dataGridWidget, [role=grid]')].find((g) => g.offsetWidth > 0);
    if (!grid) return null;
    const heads = [...grid.querySelectorAll('thead th')].map((h) => n(h.textContent).replace(/\s\d+$/, ''));
    const row = [...grid.querySelectorAll('tbody tr')].find((r) => r.offsetHeight > 0 && n(r.textContent).includes(wanted));
    if (!row) return null;
    const said = (c) => n([...c.querySelectorAll('*:not(svg):not(svg *)')].filter((e) => !e.children.length)
      .map((e) => e.textContent).join(' '))
      || [...c.querySelectorAll('[title]:not(svg *)')].map((e) => n(e.getAttribute('title'))).filter(Boolean).join(', ');
    return Object.fromEntries([...row.querySelectorAll('td')].map((c, i) => [heads[i] ?? `#${i}`, said(c)]));
  }, text).catch(() => null);
}

/**
 * The phases that change a document's permissions for `user` and put them back, for a `doc`
 * cycle's `steps`: the permission (DocumentUserPermissionsEditPresenter, "Set Permissions") goes
 * `from` -> `to` -> `from`, then one document type is ticked and unticked in its create permissions
 * (DocumentUserCreatePermissionsEditPresenter, "Set Document Create Permissions"). Each change is
 * read back from the grid after its Refresh, which fetches the permissions from the server again.
 * A new folder's creator is its Owner, so that is the `from` a fresh folder starts with.
 */
export function permissionSteps({ user = 'admin', from = 'Owner', to = 'View', createType = 'Dictionary' } = {}) {
  let typesBefore = null;
  const caption = async (page, want) => {
    const cs = await dialogCaptions(page);
    if (cs.at(-1) !== want) throw new Error(`expected "${want}", the dialogs are [${cs.join(', ')}]`);
  };
  // Refresh is the grid's own button: it fetches again, so the row is the server's, not the view's.
  const readBack = async ({ page, settle }) => {
    await page.locator('[title="Refresh"]:visible').first().click({ timeout: 5_000 });
    await settle(1500);
    const row = await readGridRow(page, user);
    if (!row) throw new Error(`no "${user}" row in the permissions grid after a refresh`);
    return row;
  };
  const openEdit = async ({ page, settle }) => {
    if (!(await selectRow(page, user))) throw new Error(`no "${user}" row to select`);
    await settle(400);
    await page.locator('[title="Edit Permissions For Selected User"]:visible').first().click({ timeout: 5_000 });
    await settle(1500);
    await caption(page, 'Set Permissions');
  };
  const closed = async ({ page, settle, alertText }, what) => {
    await settle(1500);
    const said = await alertText();
    if (said) throw new Error(`${what} was refused: "${String(said).slice(0, 300)}"`);
  };
  const setPermission = (value) => async (ctx) => {
    const { page, settle } = ctx;
    await openEdit(ctx);
    const shown = await chooseInSelectionBox(page, 'Permission', value, settle);
    if (shown !== value) throw new Error(`the box shows "${shown}" after choosing "${value}"`);
    if (!(await clickButton(page, 'OK'))) throw new Error('no OK in "Set Permissions"');
    await closed(ctx, 'the change');
    if ((await dialogCaptions(page)).length) throw new Error(`a dialog stayed open: [${(await dialogCaptions(page)).join(', ')}]`);
    const row = await readBack(ctx);
    if (row['Explicit Permission'] !== value) {
      throw new Error(`the explicit permission reads "${row['Explicit Permission']}" after a refresh, not "${value}"`);
    }
    return `"${value}" after a refresh`;
  };
  // Tick or untick one type in "Set Document Create Permissions", whose own OK writes; the
  // "Set Permissions" dialog under it is then cancelled, which writes nothing.
  const toggleType = (expectListed) => async (ctx) => {
    const { page, settle } = ctx;
    await openEdit(ctx);
    if (!(await clickButton(page, 'Set Document Create Permissions'))) throw new Error('no "Set Document Create Permissions" button');
    await settle(1500);
    await caption(page, 'Set Document Create Permissions');
    const row = page.locator('.dialog-popup, .resizableDialog-popup').last().locator('tbody tr')
      .filter({ has: page.locator('td:last-child', { hasText: new RegExp(`^\\s*${createType}\\s*$`) }) }).first();
    await row.locator('.tickBox').first().click({ timeout: 5_000 });
    await settle(400);
    if (!(await clickButton(page, 'OK'))) throw new Error('no OK in "Set Document Create Permissions"');
    await closed(ctx, 'the create permission change');
    await caption(page, 'Set Permissions');
    if (!(await clickButton(page, 'Cancel'))) throw new Error('no Cancel in "Set Permissions"');
    await settle(800);
    const types = (await readBack(ctx))['Explicit Create Document Types'] ?? '';
    if (expectListed && !types.includes(createType)) {
      throw new Error(`"${createType}" is not among the explicit create types after a refresh: "${types}"`);
    }
    if (!expectListed && types !== typesBefore) {
      throw new Error(`the explicit create types read "${types}" after the revert, not "${typesBefore}" as before`);
    }
    return `explicit create types "${types || '(none)'}"`;
  };
  return [
    ['open its Permissions', async (ctx) => {
      await ctx.menuPath(ctx.name, ['Permissions']);
      await ctx.settle(1500);
      const row = await readBack(ctx);
      if (row['Explicit Permission'] !== from) throw new Error(`"${user}" starts as "${row['Explicit Permission']}", not "${from}"`);
      typesBefore = row['Explicit Create Document Types'] ?? '';
      return `"${user}" is "${from}", create types "${typesBefore || '(none)'}"`;
    }],
    [`change it to ${to} (Set Permissions)`, setPermission(to)],
    [`put it back to ${from}`, setPermission(from)],
    [`change it to allow creating ${createType} (Set Document Create Permissions)`, toggleType(true)],
    [`put the create types back`, toggleType(false)],
  ];
}

// ── Annotations ─────────────────────────────────────────────────────────────
/**
 * The annotation cycle: the bulk Change Status and Change Assigned To dialogs, which open from a
 * query table's Annotate menu for selected rows that carry annotation ids. They can't be changed
 * back to "never had a status", and each change adds history, so the cycle makes an annotation of
 * its own and deletes it rather than changing the seeded one:
 *
 *     Create Annotation on an event row -> read its id from the editor tab (`#<id> <title>`)
 *     -> `from "Annotations"` -> select its row -> Change Status -> re-run, read the status
 *     -> Change Assigned To (Assign Yourself) -> re-run, read the assignee
 *     -> Delete Annotation in its editor -> re-run, read it absent
 *
 * `spec.query` names any Query document: its text is replaced in the browser and never saved.
 * `spec.helpers.openQuery(name)` opens it.
 */
export async function runAnnotationCycle(page, spec, { guard, settle, log = () => {} }) {
  const { clickMenuItem, openQuery } = spec.helpers;
  const rec = { name: spec.name, kind: 'annotation', phases: [], leaked: false, writes: [], undeclared: [] };
  const phase = async (what, fn) => {
    const t = Date.now();
    try {
      const why = await fn();
      rec.phases.push({ what, ok: true, why, ms: Date.now() - t });
      log(`    ✓ ${what}${why ? ` — ${why}` : ''}`);
      return true;
    } catch (e) {
      rec.phases.push({ what, ok: false, why: String(e?.message ?? e).slice(0, 1200), ms: Date.now() - t });
      log(`    ✗ ${what} — ${String(e?.message ?? e).slice(0, 1000)}`);
      return false;
    }
  };
  const alertText = async () => {
    if (!(await dialogCaptions(page)).some((c) => /alert/i.test(c))) return null;
    return (await readTopDialogBody(page))?.text ?? 'an Alert with no text';
  };
  const tabs = () => page.evaluate(() => [...document.querySelectorAll('.curveTab')]
    .filter((e) => e.offsetWidth > 0).map((e) => e.innerText.replace(/\s+/g, ' ').trim())).catch(() => []);
  const toTab = async (re) => {
    const tab = page.locator('.curveTab').filter({ hasText: re }).first();
    await tab.click({ timeout: 5_000 });
    await settle(800);
  };
  const runQuery = async (text) => {
    await toTab(new RegExp(spec.query));
    if (!(await setAce(page, text))) throw new Error(`no query editor in "${spec.query}"`);
    await settle(300);
    if (!(await clickButton(page, 'Execute Query'))) throw new Error('no Execute Query button');
    await settle(4000);
  };
  const annotationsQuery = 'from "Annotations" select Id, Title, Status, AssignedTo';
  let id = null;
  let title = null;
  // The row by its id and title, both of which its text starts with: `includes` on the id alone
  // would take 115 for 15.
  const rowOf = async () => {
    await runQuery(annotationsQuery);
    return readGridRow(page, `${id}${title}`);
  };
  const annotate = async (item) => {
    if (!(await selectRow(page, `${id}${title}`))) throw new Error(`no row for annotation #${id}`);
    await settle(500);
    if (!(await clickButton(page, 'Annotate'))) throw new Error('no Annotate button');
    await settle(800);
    if (!(await clickMenuItem(page, item))) throw new Error(`no "${item}" in the Annotate menu — is the row selected?`);
    await settle(1500);
  };
  const okAndCheck = async (caption) => {
    if (!(await clickButton(page, 'OK'))) throw new Error(`no OK in "${caption}"`);
    await settle(1500);
    const said = await alertText();
    if (said) throw new Error(`"${caption}" was refused: "${String(said).slice(0, 300)}"`);
    if ((await dialogCaptions(page)).length) throw new Error(`a dialog stayed open: [${(await dialogCaptions(page)).join(', ')}]`);
  };

  let created = false;
  try {
    if (!await phase(`open the query "${spec.query}"`, async () => {
      await openQuery(spec.query);
      if (!(await tabs()).some((t) => t.includes(spec.query))) throw new Error(`no "${spec.query}" tab`);
      return 'open';
    })) return rec;

    const allowedBefore = (guard.allowed ?? []).length;
    const madeIt = await phase('create an annotation on an event (Create Annotation)', async () => {
      await runQuery(spec.eventQuery);
      const first = (await readRows(page))[0];
      if (!first) throw new Error(`"${spec.eventQuery}" returned no rows`);
      if (!(await selectRow(page, first))) throw new Error('could not select the first event row');
      await settle(500);
      if (!(await clickButton(page, 'Annotate'))) throw new Error('no Annotate button');
      await settle(800);
      if (!(await clickMenuItem(page, 'Create Annotation'))) throw new Error('no "Create Annotation" in the Annotate menu');
      await settle(3000);
      const said = await alertText();
      if (said) throw new Error(`the create was refused: "${String(said).slice(0, 300)}"`);
      const tab = (await tabs()).map((t) => t.match(/^#(\d+) (.+)$/)).find(Boolean);
      if (!tab) throw new Error(`no "#<id> …" annotation tab opened: [${(await tabs()).join(', ')}]`);
      [, id, title] = tab;
      return `annotation #${id} "${title}"`;
    });
    created = madeIt || (guard.allowed ?? []).length > allowedBefore;
    if (!madeIt) return rec;

    if (!await phase('read it back from "Annotations"', async () => {
      const row = await rowOf();
      if (!row) throw new Error(`annotation #${id} is not in "${annotationsQuery}"`);
      if (row.Status === spec.status) throw new Error(`a new annotation already has the status "${spec.status}", so changing to it proves nothing`);
      return `status "${row.Status || '(none)'}", assigned to "${row.AssignedTo || '(nobody)'}"`;
    })) return rec;

    if (!await phase(`change it to ${spec.status} (Change Status)`, async () => {
      await annotate('Change Status');
      const dlg = page.locator('.dialog-popup, .resizableDialog-popup').last();
      await dlg.locator('.annotationSettingHeading').first().click({ timeout: 5_000 });
      await settle(1000);
      // The chooser is a popup of its own, not inside the dialog.
      await page.locator('.popupContent').filter({ hasNotText: 'Change Status' })
        .getByText(spec.status, { exact: true }).first().click({ timeout: 5_000 });
      await settle(600);
      const shown = (await dlg.locator('.annotationLabel').first().textContent()).trim();
      if (shown !== spec.status) throw new Error(`the dialog shows "${shown}" after choosing "${spec.status}"`);
      await okAndCheck('Change Status');
      const row = await rowOf();
      if (row?.Status !== spec.status) throw new Error(`the status reads "${row?.Status}" after re-running the query, not "${spec.status}"`);
      return `"${spec.status}" after re-running the query`;
    })) return rec;

    await phase('change it to assigned to me (Change Assigned To)', async () => {
      await annotate('Change Assigned To');
      const dlg = page.locator('.dialog-popup, .resizableDialog-popup').last();
      await dlg.locator('.annotationAssignYourself').first().click({ timeout: 5_000 });
      await settle(600);
      const shown = (await dlg.locator('.annotationLabel').first().textContent()).trim();
      if (!shown || shown === 'Nobody') throw new Error(`the dialog still shows "${shown}" after Assign Yourself`);
      await okAndCheck('Change Assigned To');
      const row = await rowOf();
      if (!row?.AssignedTo || !row.AssignedTo.includes(shown)) {
        throw new Error(`assigned to reads "${row?.AssignedTo}" after re-running the query, not "${shown}"`);
      }
      return `"${row.AssignedTo}" after re-running the query`;
    });
  } finally {
    if (created) {
      const gone = await phase('delete it again (compensation)', async () => {
        await closeAnyDialogs(page, settle);
        if (!id) throw new Error('an annotation was created but its id was never read — find it by hand');
        if (!(await tabs()).some((t) => t.startsWith(`#${id} `))) {
          await runQuery(annotationsQuery);
          await annotate('Edit Annotation');
        }
        // Unanchored: a tab's text carries more than its caption (`innerText` above is just the label).
        await toTab(new RegExp(`#${id} `));
        if (!(await clickButton(page, 'Delete Annotation'))) throw new Error('no "Delete Annotation" in the editor');
        await settle(800);
        // "Are you sure you want to delete this annotation?"
        if (!(await clickButton(page, 'OK')) && !(await clickButton(page, 'Yes'))) throw new Error('no OK in the confirm');
        await settle(2000);
        const said = await alertText();
        if (said) throw new Error(`the delete was refused: "${String(said).slice(0, 300)}"`);
        return `deleted #${id}`;
      });
      await phase('read it absent', async () => {
        if (await rowOf()) {
          rec.leaked = true;
          throw new Error(`annotation #${id} is STILL in "Annotations" after the delete — THE INSTANCE IS NOW DIRTY`);
        }
        return 'gone';
      });
      if (!gone) rec.leaked = true;
    }
    await closeAnyDialogs(page, settle);
    rec.writes = (guard?.allowed ?? []).map((w) => `${w.method} ${w.path}`);
    rec.undeclared = (guard?.violations ?? []).map((v) => `${v.method} ${v.path}`);
    rec.ok = rec.phases.every((p) => p.ok) && !rec.leaked && !rec.undeclared.length;
  }
  return rec;
}
