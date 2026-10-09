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
// Which RULE a recorded capture was walked under — BEHAVIOUR-PLAN.md.
//
// A checker corrected after a shard was walked leaves that shard's captures judged by the old rule
// whenever the correction needs data the old walker never recorded. Those fails are not findings
// and they are not the checker being wrong NOW: they are the wrong vintage. Re-walking to clear
// them costs hours of machine time for a cosmetic result, so the ledger says so instead.
//
// Every shard records the walker's commit (`summary.walkerVersion`), and each rule below names the
// commit that introduced it. A fail whose shard predates its rule is `stale`, not `NEW`.
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Commit -> position in history, newest first. A larger index is older.
 *
 * The walker was written in another repository (stroom-ui-react/gwt-suite), whose history is kept in
 * walker-history.txt, and every rule below names one of its commits. This repository's history comes
 * first: anything walked here is newer than every rule written there.
 */
const order = (() => {
  let here = [];
  try {
    here = execSync('git log --format=%h', { cwd: import.meta.dirname, stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 64 * 1024 * 1024 })
      .toString().trim().split('\n');
  } catch { /* not a git checkout: only the walker's own history */ }
  const there = readFileSync(join(import.meta.dirname, 'walker-history.txt'), 'utf8')
    .split('\n').map((line) => line.trim()).filter((line) => line && !line.startsWith('#'));
  const map = new Map();
  for (const sha of [...here, ...there]) {
    if (!map.has(sha)) map.set(sha, map.size);
  }
  return map;
})();

/**
 * Each entry: the rule, the commit that landed it, and the fail signature it governs. The signature
 * matters because a shard predating a rule is only stale for the fails THAT rule would have judged
 * differently — everything else it recorded is as good as any other shard's.
 */
export const RULES = [
  { rule: 'inline feedback judged by presence', since: 'ffd09b3', kind: 'ok',
    why: /OK did nothing: the dialog is still open with no message/ },
  { rule: 'ACE focus sampled at each key press', since: 'ffd09b3', kind: 'key',
    why: /Ctrl\+Enter did nothing where OK closed|still open after Escape/ },
  { rule: 'the toggle check records the buttons gained and lost', since: '82083dc', kind: 'toggle',
    why: /did not restore the node/ },
  { rule: 'the illegal driver reads the value back', since: 'd8d60c4', kind: 'illegal',
    why: /accepted \(and asked to confirm\)/ },
  // A spinner on a dialog with ANOTHER dialog over it is laid out and visible, but every press lands
  // on the modal glass in front. `readSpinners` had no way to know, so six gestures against one
  // working spinner ("Max Processing Tasks" on Add Processor) read as its handlers not firing. The
  // arrow is now tested with `elementFromPoint` and an unreachable one is `unchecked`.
  { rule: 'a spinner arrow is tested for occlusion', since: '8121518', kind: 'mouseover',
    why: /left the class as valueSpinner-arrow valueSpinner-arrowUp\b/ },
  { rule: 'a spinner arrow is tested for occlusion', since: '8121518', kind: 'mousedown',
    why: /changed neither the class nor the value/ },
  // The same occlusion mistake in `readRange`: with a dialog open it found the pager on the screen
  // BEHIND the modal, where the click lands on the glass and the range editor never opens.
  { rule: 'the pager is read inside the topmost dialog and tested for occlusion', since: '5d1c021', kind: 'key',
    why: /clicking the pager's "from" label did not open the range editor/ },
  // `Data Size` and `Data Size 1` are one column with and without its sort indicator, so the
  // inverse-pairing logic read them as a toggle pair and expected a round trip that sorting cannot
  // give. The toggle check now asks `isColumnHeader` first.
  { rule: 'a column header is excluded from the toggle round-trip', since: 'ace5492', kind: 'toggle',
    why: /did not restore the node \(lost [A-Z]/ },
  // The Enter probe pressed into whatever was in front, so a confirm raised by an earlier box got
  // ANSWERED — "You are about to process all feeds" — and the write it started was aborted by the
  // guard, surfacing as a FailedResponseException the suite had caused itself.
  { rule: 'Enter is not pressed when something is in front of the form', since: 'a49bc79', kind: 'key',
    why: /FailedResponseException/ },
  // The field driver leaves the pointer on the arrow it pressed, and a move to where the pointer
  // already is fires no mouseover — so the hover read as not working on a spinner that works.
  { rule: 'the pointer is parked before a spinner gesture', since: '9dff71e', kind: 'mouseover',
    why: /left the class as valueSpinner-arrow valueSpinner-arrowUp\b/ },
  { rule: 'the pointer is parked before a spinner gesture', since: '9dff71e', kind: 'mousedown',
    why: /changed neither the class nor the value/ },
  // A flow's next dialog REPLACES the one it closed, so a count of the stack reads `nothing`.
  // Until the probes compared captions, `Create Processors` › Ctrl+Enter — which opens
  // `Choose Pipeline To Process Data With` — was recorded as the key doing nothing.
  { rule: 'the key probes read the dialog stack by caption', since: '99e0120', kind: 'key',
    why: /Ctrl\+Enter did nothing where OK closed/ },
  // A dialog can answer INLINE and stay open — "Password is required" written into a `.feedback`
  // label, with the box marked `invalid` — which the key probes read as `nothing` until they
  // consulted the feedback too, the way the `ok` checker already did.
  { rule: 'the key probes read inline feedback', since: '2190b0f', kind: 'key',
    why: /Ctrl\+Enter did nothing where OK/ },
];

/**
 * Was this fail recorded before the rule that governs it existed? Returns the rule's name, or null.
 * An unknown commit (a shard walked from a dirty tree, or history rewritten) counts as CURRENT —
 * calling a capture stale on a guess would hide a real finding.
 */
const warned = new Set();
export function staleBy({ kind, why, walkerVersion }) {
  const at = order.get(String(walkerVersion ?? '').trim());
  if (at === undefined) return null;
  for (const r of RULES) {
    if (r.kind !== kind || !r.why.test(String(why ?? ''))) continue;
    const landed = order.get(r.since);
    if (landed === undefined) {
      // The rule names a commit that is not in HEAD's history — an amend or a rebase moved it. Say
      // so: a rule that silently stops applying turns explained captures back into NEW findings,
      // which is exactly what happened when `178e12c` was amended into `2190b0f`.
      if (!warned.has(r.since)) {
        warned.add(r.since);
        console.warn(`  (vintage: rule "${r.rule}" names commit ${r.since}, which is not in this history — the rule is inert)`);
      }
      continue;
    }
    if (at > landed) return r.rule;     // the shard's commit is OLDER than the rule's
  }
  return null;
}
