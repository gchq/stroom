# GWT UI regression suite

A repeatable, full-menu-coverage test suite for the **GWT Stroom UI**, running against a simulated
backend. No cluster, no database, no Stroom instance.

It turns "does the GWT UI still render correctly" into a command that takes minutes and needs
nothing deployed. It drives the whole app as a user would, which complements the GWT Workbench
(`stroom-gwt-workbench`), whose stories test widgets and screens one at a time.

## In this repository

Copied here (October 2026) from `stroom-ui-react/gwt-suite`, where it began alongside a React port
comparison. Only the suite came across:

* the 16 scripts, `lib/`, and the four `compare/` modules they use (`adapters/gwt.mjs` and
  `lib/credentials`, `readonly-guard`, `settle`, `structure`), now in this folder's `compare/`;
* not the one-off `probe-*.mjs` investigation scripts, the React adapter (so `walk.mjs` no longer
  takes `ADAPTER=react`), or the rest of `compare/`.

Later, the probes that `stroom-gwt/ISSUES.md` names for checking a fix were copied too
(`probe-stepping`, `probe-newfield`, `probe-apikey-expiry`, `probe-apikey-badtime`,
`probe-ctrlenter`, `probe-loadfail`), and `probe-prefs-failsave` was added for #48. ISSUES.md's
"Checking a fix" says what each shows.

Paths are now relative to this folder, so the scripts can be run from anywhere (`lib/paths.mjs`);
a relative path given in an environment variable (`OUT=out/r8-nav`, `RECIPES=oracles/route-recipes.json`)
is relative to this folder too.

The behaviour work came across too (October 2026), so a round runs entirely from here:

* `tools/` - the miners (`enumerate`, `build-inventory`, `build-reachability-graph`,
  `build-route-recipes`, `build-capability-specs`, `build-door-preconditions`,
  `build-handler-inventory`, `build-crawl-coverage`), which mine THIS repository's GWT source
  (`STROOM_SOURCE=<checkout>` mines another, `ORACLES=<dir>` writes elsewhere). The React port's
  `port-status.tsv` is optional and not used.
* `oracles/` - what they write, which the walk, `coverage.mjs` and the ledger read. Mined from this
  repository; re-mine after GWT changes that add or move screens, dialogs or handlers:

  ```bash
  for t in enumerate build-inventory build-reachability-graph build-route-recipes \
           build-capability-specs build-door-preconditions build-handler-inventory; do
    node tools/$t.mjs
  done
  ```

  Mining `/mnt/shared/stroom` at `783d672e40` with these tools reproduces the original oracles
  (kept in `out/oracles-783d672e`), and the ledger rebuilt from here matches the original.
* `lane.sh` and `merge.sh` - walk a round's shards and merge them into the evidence (below).
* `lib/walker-history.txt` - the original repository's history, so that `lib/vintage.mjs` can
  still tell shards walked there that predate a checker rule (`stale`) from findings.
* `out/crawl`, `out/cycles` etc. - the evidence walked so far, copied locally (git-ignored).

### Running a round

At most **two** lanes at once (three or more have run this machine out of memory), against a live
Stroom on :8080 serving a current `gwtDraftCompile`:

```bash
./lane.sh r8 "nav:AREA=Navigation" "ann:AREA=Annotations" "admin:AREA=Administration" \
             "sec:AREA=Security" "mon:AREA=Monitoring" "tools:AREA=Tools" &
./lane.sh r8 "docs-a:RECIPES=oracles/route-recipes.json RECIPEKIND=doc DOCTYPES=DataGen,TextConverter,Xslt" &
./merge.sh r8     # once both lanes have finished: out/r8-lanes.log
```

`MEMLOG=1` in a shard's settings writes the renderer's memory and DOM counts per node to its
`memory.jsonl`. Run
`npm install` here once for Playwright.

`corpus/` and `out/` are git-ignored and kept locally: the corpus holds a live Stroom's data and
its compiled UI, which would need cleaning before it could be committed. Copy or record one into
`corpus/default` to run the suite.

Also added here: `mem-leak.mjs` (from the original's `probe-leak.mjs`), which signs in to a live
Stroom and opens and closes a document type, a menu or a dialog repeatedly, measuring what survives
a forced garbage collection, with `CLASSES=1` to name the Java classes kept per cycle
(`lib/heap-growth.mjs`):

```bash
STROOM_USER=admin STROOM_PASS=… DOCTYPE=Dashboard ROUNDS=5 CLASSES=1 node stroom-gwt/stroom-gwt-suite/mem-leak.mjs
STROOM_USER=admin STROOM_PASS=… MODE=menu node stroom-gwt/stroom-gwt-suite/mem-leak.mjs
```

Not done yet:

* `serve.mjs` serves the UI recorded in the corpus, not this repository's build, so the suite tests
  the Stroom that was recorded. To test changes made here it needs an option to serve the local
  GWT compile (with the recorded API).
* There are no Gradle tasks for it yet (like the workbench's `workbenchTest`).
* `run.mjs` signs in, so needs `STROOM_PASS` set even against the simulated backend (any value).

## Why a simulated backend

Driving two live UIs makes some screens unreadable. `Monitoring > Nodes` renders ping times that
differ every run; the Welcome screen shows whichever cluster node answered (`node1a` vs `node2a`,
build dates 40 seconds apart); `User Access` lists live sessions. On those screens a real regression
and a data wobble look identical — and on 2026-08-15 that cost a correct 61-site change, reverted on
a signal that turned out to be noise.

Recording once and replaying the same bytes removes the ambiguity. It does not matter that the data
is live; it matters that every run sees the same data.

## Usage

```bash
# Record from a live Stroom (the only step that needs one)
URL=http://localhost:8080 USER=admin PASS=a node stroom-gwt/stroom-gwt-suite/record.mjs

# Run the suite — no Stroom required
node stroom-gwt/stroom-gwt-suite/run.mjs                       # exit 1 if any screen moved
ONLY=monitoring-jobs,security-users node stroom-gwt/stroom-gwt-suite/run.mjs

# Or just boot the simulated UI and click around it yourself
node stroom-gwt/stroom-gwt-suite/serve.mjs                     # http://localhost:9099
```

Content seeding — needs a live Stroom, **mutates it**, and is only run when the instance is missing
document types (see [Seeding](#seeding-the-missing-document-types)):

```bash
URL=… USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/probe-doctypes.mjs   # which types have no example? (read-only)
URL=… USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/seed.mjs             # create one of each, with test data
URL=… USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/seed-import.mjs      # the four that cannot be created
URL=… USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/fill.mjs             # re-apply test data to existing seeds
URL=… USER=admin PASS=… node stroom-gwt/stroom-gwt-suite/seed-data.mjs        # the ROWS the unreached doors need (see below)
```

`record.mjs` walks the menu **live** rather than from a list in the source, so a screen added to
Stroom appears in the next recording instead of being quietly uncovered.

## Current coverage

**184 targets: 44 menu leaves + 28 documents + 111 editor sub-tabs + 1 interaction.**

*Menu* — 50 leaves across 8 groups, 44 recorded and 6 skipped. The skips are deliberate: Sign Out and
Sign Out Other Sessions end the session the rest of the run depends on, Change Password would
invalidate the credentials, and the tab-session and favourites items write user state. Everything
else runs behind `attachReadOnlyGuard`, which fails the run rather than letting a mutation through.

*Documents* — one per document TYPE discovered in the explorer, so every editor is covered without
opening thousands of documents, plus a pinned list for the ones where the first-of-type is not the
interesting one. Both entries in `PINNED_DOCS` carry a type because the explorer holds FOUR things
called "Example Index" — a Folder, a Pipeline, an XSL Translation and the index itself.
**All 26 creatable types are now present**, because the ten that were missing were seeded.

*Sub-tabs* — an editor is photographed as it opens, which is one pane of several. Every other tab —
`Settings`, `Fields`, `Shards`, `Documentation`, `Permissions`, and the Query/Notifications/Execution
tabs of the analytic editors — is now a target of its own. That is where most of the editor surface
lives: the Elastic Index editor opens on a form and keeps a whole grid one tab away.

29 sub-tabs are recorded as **failed** rather than quietly dropped, and they are a real finding
rather than flakiness. Two causes: the Dashboard's tab bar also carries the dashboard's own
component tabs (`Params`, `Query`, `Bar`, `Doughnut`, `Line`), which vanish the moment you leave the
canvas; and the Analytic Rule editor changes its own tab set as you move through it, so a tab read at
the start is gone by the time its turn comes. Both need the tab list re-read per click, and both are
in [COVERAGE-PLAN.md](./COVERAGE-PLAN.md).

### What is NOT covered, and why

| Gap | Size | Why |
| --- | --- | --- |
| Nested dialogs | ~81 | Reached by an action inside a screen, not by navigation. |
| Explorer context menus | ~10 | Same as dialogs: an action, not a destination. |
| Post-action states | ? | Only the searched dashboard so far. |

So this is **not** yet everything in the port status. The reachability audit
(`stroom-ui-react/porting/comparison-reachability.md`) counts 51 menu leaves, 38 screen plugins and 81 nested
dialogs resolving to 192 React units; the menu, the editors and their tabs are the first half, and
the dialogs are the long tail. [COVERAGE-PLAN.md](./COVERAGE-PLAN.md) is the plan for the rest.

## Seeding the missing document types

The corpus can only record documents that **exist**, and this instance held 16 of the 26 creatable
types. Ten editors were therefore unrecordable — not because the harness could not reach them, but
because there was nothing to open. That is a content problem, not a harness one.

`probe-doctypes.mjs` derives the gap live, by walking the explorer's New menu and comparing it with
the tree, so a type added to Stroom shows up as a gap on the next run rather than being missed.
`seed.mjs` then creates one of each in a `Seed Content` folder and fills it with deterministic test
data (`lib/fills.mjs`) — an empty editor renders the same whatever the code does. It is the one
script here that runs **without** the read-only guard, deliberately, and it is re-runnable.

**Four of the ten cannot be created at all.** `View`, `Analytic Rule`, `Report` and `Data Generator`
answer `POST /api/explorer/v2/create` with a 403 — their stores write inside
`securityContext.asProcessingUser(...)` and the audit stamp needs a user ref the processing identity
does not have. That is a server defect, written up as **#31** in `stroom-gwt/ISSUES.md` with the four
file:line sites, and `probe-createfail.mjs` reproduces it. Import does not go through the broken
path, so `seed-import.mjs` seeds those four from a generated content zip instead. When #31 is fixed,
that script becomes unnecessary.

**Seeding invalidates every baseline** recorded before it — this suite's and `compare/`'s — because
it changes the explorer tree, which is on screen in every shot. A full re-record follows a seed.

Two traps, both of which cost a run:

* a failed create leaves the New dialog open **behind an Alert**, and a script that waits for the
  dialog to close reads that as success. Four types reported ✓ and none of them existed. `seed.mjs`
  now reads the Alert and fails with the server's own message.
* the import confirmation grid arrives with every row **unticked**, and OK on an unticked grid
  imports nothing and reports success. The ticks are `div.tickBox`, not checkboxes, and the header's
  select-all ticks the header only.

## Seeding the data the doors need

Types were the first gap; rows were the second. After directed pass 5 (COVERAGE-PLAN.md) the ledger's
unreached doors were classified, and a third of them sat behind data this instance did not hold: no
node group, so the Node Groups screen's Edit is never enabled; no saved tab session, so `Open Tab
Session` is disabled; no annotation with a comment entry, no visualisation with an asset, no Git
repository that can push, no search result store. A walk cannot open the Edit dialog of a row that
does not exist.

`seed-data.mjs` creates those rows through the REST API from inside the signed-in page — the same
session cookie and `X-CSRF` header the GWT client sends — rather than by driving the UI, because a
row is data and what the walk then does with it is the test (creating a document IS a surface under
test, which is why `seed.mjs` drives the UI). Like `seed.mjs` it runs **without** the guard, on
purpose and visibly; it is idempotent by name, so a rerun is a no-op; `DRYRUN=1` reports what is
missing, `ONLY=tags,annotation` narrows it, `REMOVE=1` takes everything out again. The names it uses
live in `lib/seed-names.mjs`, shared with `walk.mjs`, so a directed seed can address *the* seeded row
by name. The one exception to "create once" is the result store: it lives in a node's memory and is
gone after a restart, so it is re-created whenever the list is empty.

Three things it learned about the API, each the kind that fails silently: `NodeGroupResource.create`
takes a bare `String`, which Jersey reads as text whatever the media type says, so a JSON-encoded
name is stored with its quotes; `annotation/v1/create` writes no comment entry whatever its
`comment` field holds — the comment is a `change` of type `comment`; and `page.request` from outside
the page answers 403, because the session is a cookie plus a header.

## Layout

| | |
| --- | --- |
| `BEHAVIOUR-PLAN.md` | the plan for testing what the UI DOES, now that it can be reached — alerts, a handler ledger, checkers, forms, mutations under snapshot, replay |
| `record.mjs` | drive live Stroom; write the corpus + baselines + `coverage.json` |
| `serve.mjs` | the simulated backend — serves the app and answers its API calls |
| `run.mjs` | boot the simulation, drive the coverage, diff against the baselines |
| `seed.mjs` | create one document of each missing type, with test data — **mutates** |
| `seed-import.mjs` | import the four types the server refuses to create — **mutates** |
| `fill.mjs` | re-apply the test data to documents that already exist — **mutates** |
| `seed-data.mjs` | create the rows the unreached doors need (node group, tab sessions, tags, a commented annotation, an asset, a pushable repo, a result store) — **mutates** |
| `lib/seed-names.mjs` | the names `seed-data.mjs` gives what it creates, shared with `walk.mjs` |
| `probe-doctypes.mjs` | which document types have no example in this instance |
| `probe-editors.mjs` | what controls each editor has, per sub-tab |
| `probe-newdialog.mjs` · `probe-createfail.mjs` | the create dialog, and why a create fails |
| `lib/corpus.mjs` | record/replay, request keying, the frozen clock |
| `lib/shoot.mjs` | settle-then-screenshot, shared by recorder and runner |
| `lib/tabs.mjs` | reading and clicking editor sub-tabs, shared by recorder and runner |
| `lib/fills.mjs` | the per-type test data, and a save that proves it saved |
| `corpus/<name>/api/` | REST exchanges (~3.5M) |
| `corpus/<name>/app/` | the compiled UI (~8.5M) — refresh only when GWT is rebuilt |
| `corpus/<name>/baseline/` | what each screen looked like (~6M) |
| `corpus/<name>/dom/` | each screen's full markup, gzipped (~2.8M) |

## Six things that are easy to get wrong

**Responses are sequences, not values.** Stroom's search endpoints poll: the client asks the same
question until the result is complete. A corpus that replays the first answer forever leaves those
screens spinning. Each key holds an ordered list, replayed in order, with the last entry repeating —
so a poll that outlives the recording still terminates on the completed response.

**A merged corpus can serve the wrong bytes, and look perfectly healthy doing it.** `save()` merges
into the manifest already on disk, so a re-record keeps entries from earlier sessions — and those
entries name blob FILES. The blob counter used to restart at `r0b00000` every session, so the second
recording rewrote the first's files while the first's manifest entries went on pointing at them.

The failure mode is the nasty one: every key present, **zero misses**, no JS error, no server error —
and `GET /api/dictionary/v1/<uuid>` answering `200 {"authenticated":true,…}`. The editor simply never
opened, on 95 of 184 targets, with nothing anywhere saying why. Blob names now carry a session token
(`s2r0b00042`), so the collision cannot happen rather than being unlikely. If you ever see a target
whose screen is right but empty, `probe-replay.mjs` prints what the wire actually carried.

**`route.fetch()` follows redirects by default.** That swallows the 302s in Stroom's sign-in chain,
and the username field never appears — a 45-second timeout with no clue as to why. The recorder
passes `maxRedirects: 0` and stores each hop, so the browser drives the chain itself.

**`page.clock.install()` stops timers.** GWT's bootstrap is driven by them, so the app never
finishes loading. `setFixedTime` is what you want: `Date.now()` is pinned so relative-time cells
("5 days ago") are reproducible, while `setTimeout` keeps running.

**The baseline and the run must be photographed identically.** The first version screenshotted raw
while recording and parked the pointer while running; every screen came back 600–2600px "changed",
all of it a hover highlight on the row the last click landed on. Both now go through
`lib/shoot.mjs`, and that is why it is shared rather than duplicated.

**A tab is not its label, and the order you visit them is part of the state.** A `.linkTab` holds
the label *and* a hidden width-reserving copy, so its `textContent` reads
`DocumentationDocumentation`; and a `.linkTab-hotspot` overlays the label and takes the pointer, so
a click aimed at `.linkTab-label` is intercepted rather than delivered. Match on the label, click
the tab. Both sides drive this through `lib/tabs.mjs` — and the runner walks a document's tabs in
the **recorded order, in one open document**, because the recorder did: an editor that carries state
across its tabs is a different screen if you arrive at tab 4 without passing through tabs 1–3.

## DOM dumps

Every screen's full markup is stored gzipped alongside its screenshot, in both the corpus (GWT's
ground truth) and each run's output.

The screenshot answers *did this change*; the DOM answers *what is this*, which is the question
actually asked when diagnosing a port gap. A pixel diff can say a row is 4px tall of GWT's; only the
markup says the cell holds a 22px `IconButton` where GWT emits a bare 17px `svgCell` div. Having
GWT's real markup on disk, per screen, turns that from a live-probing exercise into a grep — the
`.buttonPanel`, `icon-button--active` and `multiline` findings were all of that shape and each cost a
round of live probing to establish.

Stored raw rather than summarised: the summaries worth having (class lists, geometry, cell contents)
can all be derived later, and none of them can be un-derived from a summary that dropped the wrong
thing. 1.5MB of markup per screen compresses to ~100KB.

## Interactions, and a limit worth knowing

Some screens only become interesting after an action. An unsearched dashboard is an empty frame; the
state worth testing is after the search returns — a 108-row table, a doughnut, ten series on a line
chart. `lib/actions.mjs` holds those, and each returns only when the screen has genuinely settled
(for a search that is not a fixed wait: the pagers and the row count are watched together, because a
pager alone reads "1 to 1 of ?" both before a search and during one).

**Searches do not yet replay exactly.** The structure comes back right — same panels, same columns,
pagers reading "1 to 100 of 108" and "1 to 10 of 10" — and the table CELLS stay blank.

The cause is **not** yet identified, and one plausible-sounding explanation has already been
disproved: it is not a client-minted query key. The search request carries only stable document
UUIDs (dashboard, data source, extraction pipeline, visualisations); the query key is minted by the
SERVER and appears only in the response, so there is nothing to map between them. A whole
identifier-rewriting layer was built on that premise and it fires zero times, correctly. The
`result-store/v1/find` polls that carry the values match EXACTLY — all 46 recorded responses,
the full sequence — and the cells are still empty.

Until it is understood, the target carries a `replayLimitation`: still measured, still printed with
its number, not failing the run.

Building this found two real bugs in the replay engine, both of which would have silently poisoned
every polled screen:

* **The sequence cursor was keyed by the exact key**, while the match had fallen back to a relaxed
  tier. A body that varies per run is never seen twice, so index 0 was returned forever — for a poll,
  the first empty response.
* **The relaxed index kept the first sequence for a URL, not the longest.** Opening a dashboard polls
  once and gets nothing; running its search polls until the rows arrive. Choosing the first meant the
  searched dashboard replayed the unsearched capture.

## Status

**167/184 targets pixel-identical**, 17 known-limited, 1099 exchanges served exactly, 76 relaxed,
**0 misses**. The baselines were captured against a live server and the run against the simulation,
so that result also shows the simulation is faithful — not merely self-consistent.

The 17 are two families, both carrying their evidence in `lib/known.mjs` and both still measured and
printed with their number rather than muted:

* **16 markdown `Documentation` panes**, differing by 204–8798px. The replay is not the side at
  fault: replay-vs-replay is 0px, a fresh live drive vs a second fresh live drive is 0px, and a fresh
  live drive vs the REPLAY is **0px** — while either of them against the recorded BASELINE is 204px.
  The recorded and replayed DOM are byte-identical apart from the origin, so the baseline is the
  outlier and the difference was introduced when recording. The remaining suspect is the recorder's
  own request interception; that is a suspect, not a finding, and `lib/known.mjs` says which
  experiment settles it.
* **the searched dashboard**, which replays structure but not cell values (below).
