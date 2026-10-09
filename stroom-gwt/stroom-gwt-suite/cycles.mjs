#!/usr/bin/env node
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
//
// Compensating cycles — the write paths, tested by undoing them (BEHAVIOUR-PLAN.md § B4).
//
//   STROOM_USER=admin STROOM_PASS=… node stroom-gwt/stroom-gwt-suite/cycles.mjs             # DRY RUN: print the plan
//   ... REHEARSE=1 node stroom-gwt/stroom-gwt-suite/cycles.mjs                              # drive it, then Cancel
//   ... MUTATE=1 node stroom-gwt/stroom-gwt-suite/cycles.mjs                                # actually write
//   ... MUTATE=1 ONLY="node group" node stroom-gwt/stroom-gwt-suite/cycles.mjs
//
// THIS SCRIPT WRITES TO THE INSTANCE, and says so before it does. It is the only part of the suite
// that does, apart from the seed scripts, and unlike them it is expected to leave NOTHING behind:
// each cycle creates one row, proves the UI can read it back, edit it, persist the edit across a
// reload, and then deletes it and proves it is gone. The delete runs in a `finally`, so a cycle
// that fails in the middle still cleans up; a row it could not delete is reported as `leaked` and
// is the most important line in the output, because it makes every later baseline wrong.
//
// The read-only guard stays attached with an ALLOW-LIST built from each spec's own `writes`: a
// cycle may make the requests it declares and no others. An undeclared mutation is still aborted
// and reported, so a cycle that reaches further than its spec says fails on that alone.
import { existsSync, readFileSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
import { gwt } from './compare/adapters/gwt.mjs';
import { credentials } from './compare/lib/credentials.mjs';
import { attachReadOnlyGuard } from './compare/lib/readonly-guard.mjs';
import { clickMenuItem } from './compare/lib/structure.mjs';
import { clickButton, dialogCaptions, fillField, openScreen, readRows, runCycle, runEditCycle } from './lib/cycle.mjs';
import { fromSuite } from './lib/paths.mjs';

// The suite's folder, so its corpus and output are found wherever the suite is run from
const SUITE = import.meta.dirname;

const env = (n, d) => (process.env[n] === undefined || process.env[n] === '' ? d : process.env[n]);
const URL = env('URL', 'http://localhost:8080');
const MUTATE = env('MUTATE', '') === '1';
/**
 * REHEARSE drives everything a cycle does except the writing: open the screen, read the rows, open
 * the create dialog, fill the name — and then CANCEL. It proves the four driving primitives find
 * what they need on this screen (which is where a cycle would otherwise fail on its first run,
 * after it had already written), and it changes nothing, so it is safe to run while the walk lanes
 * are recording.
 */
const REHEARSE = env('REHEARSE', '') === '1';
const ONLY = (env('ONLY', '') || '').split(',').map((x) => x.trim()).filter(Boolean);
const OUT = fromSuite(env('OUT', `${SUITE}/out/cycles`));
/**
 * A name no human would pick, recognisable in a grid if one ever leaks, and UNIQUE PER RUN down to
 * the minute. Per-day was not enough: gwt-bugs #44 means a deleted annotation tag keeps its
 * `(type, name)` slot for ever, so a second run of the same cycle on the same day could never
 * rename onto the name the first run had deleted — the suite would be tripping over its own
 * leavings and reporting it as a defect every time.
 */
const STAMP = `zz-cycle-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, '')}`;

/**
 * One spec per thing that can be created, edited and deleted through the UI. `writes` is the
 * contract with the guard, and it is deliberately narrow: the resource, not `/api/`.
 */
const SPECS = [
  {
    name: 'node group',
    // The OK handler this cycle drives, for the inventory's `cycle` join: `onHideRequest` at
    // NodeGroupEditPresenter:139, which the read-only guard otherwise leaves `ok-blocked`.
    // The CREATE dialog is a different presenter from the edit one, and the cycle drives both:
    // `NewNodeGroupPresenter:61` is captioned "New", which is the button this spec presses.
    okPresenters: ['NodeGroupEditPresenter', 'NewNodeGroupPresenter'],
    menu: ['Monitoring', 'Node Groups'],
    // Create is `NameViewImpl` ("Name"). The edit view's name box is DISABLED — a node group
    // cannot be renamed — so the edit under test is its `Enabled` tick box, and the oracle is that
    // the row renders differently after a reload.
    nameField: ['Name', 'Node Group Name'],
    editToggle: 'Enabled',
    seed: `${STAMP} node group`,
    edited: `${STAMP} node group (edited)`,
    // NodeGroupResource: @POST /node/nodeGroup/v2, @PUT /{id}, @DELETE /{id}.
    writes: [{ path: /\/api\/node\/nodeGroup\/v\d+/ }],
  },
  {
    name: 'data volume group',
    okPresenters: ['FsVolumeGroupEditPresenter'],   // onHideRequest at :203
    menu: ['Administration', 'Data Volumes'],
    // Edit is `FsVolumeGroupEditViewImpl` — ui.xml labels its only box "Volume Group Name", the same
    // shape as the index volume group below, which is why this spec needs nothing new.
    nameField: ['Name', 'Volume Group Name'],
    seed: `${STAMP} data volume group`,
    edited: `${STAMP} data volume group (edited)`,
    // FsVolumeGroupResource: @Path("/fsVolume/volumeGroup" + V2).
    writes: [{ path: /\/api\/fsVolume\/volumeGroup\/v\d+/ }],
  },
  {
    name: 'user group',
    menu: ['Security', 'User Groups'],
    // This screen names the ROW in its buttons — `Create Group`, then `Edit group '<name>'` and
    // `Delete group '<name>'` — so the edit and delete labels are functions of the name. Read from a
    // crawl of the screen with a row selected, not guessed.
    create: 'Create Group',
    edit: (n) => `Edit group '${n}'`,
    remove: (n) => `Delete group '${n}'`,
    nameField: ['Name', 'Display Name'],
    // 163 groups over 100 to a page, so `zz-cycle-…` lands on page two: the row has to be found
    // through the grid's own quick filter, not by reading the visible rows.
    findBy: 'filter',
    seed: `${STAMP} user group`,
    edited: `${STAMP} user group (edited)`,
    okPresenters: ['CreateUserPresenter'], // onHideRequest at :100
    // UserResource: @Path("/users" + V1).
    writes: [{ path: /\/api\/users\/v\d+/ }],
  },
  {
    name: 'index volume group',
    okPresenters: ['IndexVolumeGroupEditPresenter', 'NewIndexVolumeGroupPresenter'], // :217 and :65
    menu: ['Administration', 'Index Volumes'],
    // Edit is `IndexVolumeGroupEditViewImpl` ("Volume Group Name").
    nameField: ['Name', 'Volume Group Name'],
    seed: `${STAMP} index volume group`,
    edited: `${STAMP} index volume group (edited)`,
    // IndexVolumeGroupResource: @Path("/index/volumeGroup" + V2).
    writes: [{ path: /\/api\/index\/volumeGroup\/v\d+/ }],
  },
  // The four annotation tag screens are one resource with a `type`; each is its own cycle because
  // each is its own screen, and a create on one must not show up on another.
  ...['Annotation Collections', 'Annotation Comments', 'Annotation Labels', 'Annotation Statuses']
    .map((leaf) => ({
      name: leaf.replace(/^Annotation /, 'annotation ').toLowerCase(),
    // `AnnotationTagCreatePresenter:69` is captioned "Create New <type>" — the create dialog these
    // cycles open — while the edit dialog is AnnotationTagEditPresenter. Both are driven.
    okPresenters: ['AnnotationTagEditPresenter', 'AnnotationTagCreatePresenter'],
      menu: ['Annotations', leaf],
      nameField: 'Name',
      seed: `${STAMP} ${leaf.toLowerCase()}`,
      edited: `${STAMP} ${leaf.toLowerCase()} (edited)`,
      // AnnotationResource: @POST createAnnotationTag, @PUT updateAnnotationTag,
      // @DELETE deleteAnnotationTag.
      writes: [{ path: /\/api\/annotation\/v\d+\/(create|update|delete)AnnotationTag/ }],
    })),
];

const specs = SPECS.filter((s) => !ONLY.length || ONLY.includes(s.name));
if (!specs.length) { console.error(`no cycle matches ONLY="${ONLY.join(',')}"`); process.exit(2); }

console.log(`cycles: ${specs.map((s) => s.name).join(', ')}`);
for (const s of specs) {
  console.log(`  ${s.name}: ${s.menu.join(' › ')} · creates "${s.seed}" · edits to "${s.edited}" · deletes it`);
  console.log(`    may write: ${s.writes.map((w) => `${w.method ?? '*'} ${w.path}`).join(', ')}`);
}
if (!MUTATE && !REHEARSE) {
  console.log('\nDRY RUN — nothing was written. REHEARSE=1 drives everything but the write; MUTATE=1 writes.');
  process.exit(0);
}

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
// Sign-in happens BEFORE the allow-list is narrowed to the cycle's own resource, so the guard is
// attached per cycle rather than once — `page.route` handlers stack, so each is removed after.
await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await gwt.signInFully(page, credentials());

const settle = (ms) => page.waitForTimeout(ms);
const results = [];

if (REHEARSE) {
  // Everything but the write. The guard stays fully armed — no allow-list — so a rehearsal that
  // somehow reaches a mutation is aborted and says so.
  const guard = attachReadOnlyGuard(page, { enabled: true });
  let bad = 0;
  for (const spec of specs) {
    console.log(`\n── ${spec.name} (rehearsal) ──`);
    const steps = [];
    try {
      await openScreen(page, { clickMenuItem, settle }, spec.menu[0], spec.menu[1]);
      const rows = await readRows(page);
      steps.push(`opened ${spec.menu.join(' › ')} — ${rows.length} row(s)`);
      const dirty = rows.filter((r) => r.includes('zz-cycle-'));
      if (dirty.length) throw new Error(`a previous cycle LEFT ROWS BEHIND: ${dirty.join(' | ').slice(0, 140)}`);
      if (!(await clickButton(page, spec.create ?? 'New'))) throw new Error(`no "${spec.create ?? 'New'}" button on the screen`);
      await settle(1000);
      const caption = (await dialogCaptions(page)).at(-1);
      if (!caption) throw new Error('the create button opened no dialog');
      steps.push(`"${spec.create ?? 'New'}" opened "${caption}"`);
      if (!(await fillField(page, spec.nameField, spec.seed))) throw new Error(`no "${spec.nameField}" box in "${caption}"`);
      steps.push(`filled "${spec.nameField}"`);
      if (!(await clickButton(page, 'Cancel'))) throw new Error(`no Cancel in "${caption}"`);
      await settle(900);
      if ((await dialogCaptions(page)).length) throw new Error('Cancel left the dialog open');
      steps.push('cancelled — nothing written');
      for (const st of steps) console.log(`    ✓ ${st}`);
      console.log('  READY');
    } catch (e) {
      bad += 1;
      for (const st of steps) console.log(`    ✓ ${st}`);
      console.log(`    ✗ ${String(e?.message ?? e).slice(0, 180)}`);
      console.log('  NOT READY');
    }
  }
  const wrote = (guard.violations ?? []).map((v) => `${v.method} ${v.path}`);
  console.log(`\n=== rehearsal === ${specs.length - bad}/${specs.length} ready`
    + `${wrote.length ? ` · the guard ABORTED ${wrote.join(', ')} — a rehearsal must not reach a write` : ' · no write attempted'}`);
  await browser.close();
  process.exit(bad || wrote.length ? 1 : 0);
}

for (const spec of specs) {
  console.log(`\n── ${spec.name} ──`);
  const guard = attachReadOnlyGuard(page, { enabled: true, allow: spec.writes });
  // Two shapes (§ B4): most things are created, edited and deleted, and the compensation is the
  // delete. A dozen of the blocked OK handlers belong to things that cannot be created at all — a
  // global property, the user's own preferences, a document permission — and for those the
  // compensation is putting the old value back, verified across a reload like everything else.
  const run = spec.kind === 'edit' ? runEditCycle : runCycle;
  const rec = await run(page, { ...spec, helpers: { clickMenuItem, settle } },
    { guard, settle, log: (l) => console.log(l) });
  await page.unrouteAll({ behavior: 'ignoreErrors' }).catch(() => {});
  // The record carries the OK handlers this cycle drove, so the inventory can credit them: a cycle
  // that created a row through a dialog's OK proves that `onHideRequest` ran AND that its write went
  // through, which is exactly what `ok-blocked` says has not been shown.
  results.push({ ...rec, okPresenters: spec.okPresenters ?? [] });
  console.log(`  ${rec.ok ? 'PASS' : 'FAIL'} — wrote [${rec.writes.join(', ') || 'nothing'}]`
    + `${rec.undeclared.length ? ` · UNDECLARED [${rec.undeclared.join(', ')}]` : ''}`
    + `${rec.leaked ? ' · LEAKED A ROW' : ''}`);
}

await mkdir(OUT, { recursive: true });
// MERGE, do not replace. A run with ONLY= used to overwrite the whole report with its one result, so
// every other cycle's evidence vanished and the inventory handed back the OK handlers they had earned
// — `ok-blocked` went 15 -> 18 for no reason but the file. Each cycle's record is keyed by name and
// the newest wins; everything else stays as it was last measured, with the date it was measured on.
const merged = new Map();
if (existsSync(`${OUT}/cycles.json`)) {
  try {
    const old = JSON.parse(readFileSync(`${OUT}/cycles.json`, 'utf8'));
    for (const r of old.results ?? []) merged.set(r.name, r);
  } catch { /* a corrupt report is replaced rather than trusted */ }
}
const when = new Date().toISOString();
for (const r of results) merged.set(r.name, { ...r, ran: when });
await writeFile(`${OUT}/cycles.json`, JSON.stringify({ generated: when, url: URL, results: [...merged.values()] }, null, 1));
const failed = results.filter((r) => !r.ok);
const leaked = results.filter((r) => r.leaked);
console.log(`\n=== cycles === ${results.length - failed.length}/${results.length} passed`
  + `${leaked.length ? ` · ${leaked.length} LEAKED — the instance is dirty, clean up before recording anything` : ''}`);
for (const r of failed) console.log(`  FAIL ${r.name}: ${r.phases.filter((p) => !p.ok).map((p) => `${p.what} (${p.why})`).join('; ')}`);
await browser.close();
process.exit(failed.length ? 1 : 0);
