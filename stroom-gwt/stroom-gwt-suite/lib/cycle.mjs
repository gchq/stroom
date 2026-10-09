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
    const boxes = [...scope.querySelectorAll('input[type="text"], input:not([type]), textarea')]
      .filter((e) => e.offsetWidth > 0 && !e.disabled && !e.readOnly && !e.classList.contains('quickFilter-textBox'));
    const want = wanted === null ? null : (Array.isArray(wanted) ? wanted : [wanted]);
    const el = want
      ? boxes.find((e) => want.includes(n(e.closest('.form-group')?.querySelector('.form-group-label')?.textContent)))
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
 * Click the tick box inside the FormGroup labelled `label`. GWT renders one as `div.tickBox`
 * (TickBoxCell / CustomCheckBox), not as a bare checkbox, so the click has to land on the div.
 */
export async function clickTickBox(page, label) {
  const box = await page.evaluate(([wanted]) => {
    const n = (t) => String(t ?? '').replace(/\s+/g, ' ').trim();
    const popups = [...document.querySelectorAll('.dialog-popup, .resizableDialog-popup')].filter((e) => e.offsetWidth > 0);
    const scope = popups.length ? popups[popups.length - 1] : document.body;
    const group = [...scope.querySelectorAll('.form-group')]
      .find((g) => n(g.querySelector('.form-group-label')?.textContent) === wanted);
    const el = group?.querySelector('.tickBox, input[type="checkbox"]');
    if (!el || !(el.offsetWidth > 0)) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  }, [label]);
  if (!box) return false;
  await page.mouse.click(box.x, box.y);
  return true;
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
    // A cell with a link or a tick box consumes the click; prefer a plain one.
    const cell = [...row.querySelectorAll('td, [role=gridcell]')]
      .find((c) => c.offsetWidth > 0 && !c.querySelector('a, .tickBox, button, [role=button], svg')) ?? row;
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
      throw new Error(`the dialog stayed open: [${open.join(', ')}]`);
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
      .find((e) => want.includes(n(e.closest('.form-group')?.querySelector('.form-group-label')?.textContent)));
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
    if (spec.row && !(await selectRow(page, spec.row))) throw new Error(`no row matching "${spec.row}"`);
    await settle(400);
    if (!(await clickButton(page, spec.opener ?? 'Edit'))) throw new Error(`"${spec.opener ?? 'Edit'}" would not take a click`);
    await settle(1200);
    if (!(await dialogCaptions(page)).length) throw new Error('no dialog opened');
  };
  let original = null;
  let changed = false;
  try {
    if (!await phase('open the screen', async () => { await at(); return `${(await readRows(page)).length} row(s)`; })) return rec;

    if (!await phase(`open the editor and read "${spec.field}"`, async () => {
      await openEditor();
      original = await readField(page, spec.field);
      if (original === null) throw new Error(`no field labelled "${spec.field}" in the dialog`);
      return `it holds ${JSON.stringify(original)}`;
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
        if (!(await fillField(page, spec.field, original))) throw new Error('could not type the old value');
        if (!(await clickButton(page, 'OK'))) throw new Error('OK would not take a click');
        await settle(1500);
        return 'restored';
      });
      const verified = back && await phase('reload and confirm it is back', async () => {
        await page.reload({ waitUntil: 'domcontentloaded', timeout: 60_000 });
        await settle(2500);
        await at();
        await openEditor();
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
    rec.writes = guard?.writes?.slice?.() ?? [];
    rec.undeclared = (guard?.violations ?? []).map((v) => `${v.method} ${v.path}`);
  }
  rec.verdict = rec.phases.every((p) => p.ok) && !rec.leaked ? 'pass' : 'fail';
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
