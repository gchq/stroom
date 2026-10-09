# Getting the GWT suite to full UI coverage

`README.md` says what the suite *is*. This says what it does not yet reach, in what order that
should change, and what "done" means for each layer — so a run can be picked up mid-way without
re-deriving the shape of the problem.

The unit of coverage is a **photographed, replayable state**: a screen, an editor tab, a dialog or a
post-action state, with its screenshot and its full DOM in the corpus, reproduced from the simulated
backend with no live Stroom.

## Where it stands

| Layer | Reachable | Covered | Notes |
| --- | ---: | ---: | --- |
| Main-menu leaves | 50 | 44 | 6 skipped deliberately — see below |
| Document types | 26 | 26 | was 16; the ten missing were seeded, see Phase 1 |
| Document editor sub-tabs | 140 | 111 | 29 fail because two editors change their tab set — Phase 2 |
| Nested dialogs | ~81 | 0 | reached by an action inside a screen, not by navigation |
| Explorer context menus | ~10 | 0 | one menu shape per node type |
| Post-action states | ? | 1 | the searched dashboard |

**184 targets run, 167 pixel-identical, 17 known-limited, 0 corpus misses.**

The ~81 comes from a reachability audit that bucketed the UI's 192 units by entry point. It is the
long tail and it is where nearly all the remaining work is.

## Phase 1 — seeding (done)

The corpus can only record documents that exist, and this instance held **16** of the 26 creatable
document types. Ten editors were therefore unrecordable, not because the harness could not reach
them but because there was nothing to open.

* `probe-doctypes.mjs` derives the gap **live**, by walking the explorer's New menu and comparing it
  with the tree. It is not a list in a source file, so a document type added to Stroom shows up as a
  gap on the next run.
* `seed.mjs` creates one of each missing type in a `Seed Content` folder, by driving the UI. It is
  the one script in the suite that runs *without* `attachReadOnlyGuard`, deliberately, and it is
  re-runnable: a type already present is skipped, so an interrupted run resumes.
* `seed-import.mjs` covers the four types that **cannot be created at all** on this build. `View`,
  `Analytic Rule`, `Report` and `Data Generator` answer `POST /api/explorer/v2/create` with a 403
  (`Expecting a stroom user identity … but got InternalIdpProcessingUserIdentity`) because their
  stores write inside `securityContext.asProcessingUser(...)`. That is a server defect, written up
  as **#31** in `stroom-gwt/ISSUES.md` with the four file:line sites. Import does not go through the
  broken path, so those four are seeded from a generated content zip instead. **When #31 is fixed,
  `seed-import.mjs` becomes redundant** — `seed.mjs` will create all ten.

Each seeded document carries deterministic test data (`lib/fills.mjs`), because an empty editor
renders the same whatever the code does.

**Consequence, and it is not small:** seeding changes the explorer tree, so every baseline recorded
before it is stale — this suite's and `compare/`'s. A full re-record follows a seed. That is the
price of the ten editors, and it is worth paying once rather than never.

## Phase 2 — the sub-tabs (done, with a known remainder)

An editor is photographed as it opens. `Settings`, `Fields`, `Documentation` and `Permissions` were
never seen, and they are where most of the editor's surface is: the Elastic Index editor's opening
pane is one form of eleven fields, and its `Fields` tab is a grid with its own dialog.

`record.mjs` now walks the open editor's tabs and shoots each as `doc-<type>-<label>-tab-<tab>`, and
`run.mjs` replays them **in the recorded order, in one open document**, because that is how they were
captured. Both sides go through `lib/tabs.mjs` for the same reason both go through `lib/shoot.mjs`:
a difference in how the two DRIVE the UI is indistinguishable from a difference in the UI.

**The remainder — 29 sub-tabs that fail to record, and it is a real finding.** The tab list is read
once, when the document opens, and two editors change it underneath you:

* the **Dashboard**'s tab bar also carries the dashboard's own component tabs (`Params`, `Query`,
  `Table`, `Bar`, `Doughnut`, `Line`), which exist only while the canvas is showing and vanish the
  moment you click `Documentation`;
* the **Analytic Rule** editor changes its own tab set as you move through it — `Table` and
  `Visualisation` are gone by the time their turn comes, and `Shards` times out.

Both want the same fix: re-read the tab list before each click and skip what is no longer there,
recording the disappearance rather than failing on it. A tab that vanishes when you leave a pane is
worth a baseline of its own, reached by re-opening the document — which is a different target shape,
not a bug in this one.

Exit: every sub-tab of every one of the 26 editors either has a baseline or a recorded reason.

> **Superseded for the layers below destinations.** This document plans *places you can navigate to*,
> and that layer is done. Everything past it — affordances, dialogs, entities that must exist first,
> and features that are config-gated out of this deployment — is planned in
> [BRANCHING-PLAN.md](./BRANCHING-PLAN.md), which treats the UI as a tree to be crawled rather than a
> list to be maintained.

## Phase 3 — nested dialogs

The ~81 dialogs are the largest single gap, and the temptation is to write 81 recipes. Don't. Nearly
every one opens from either a **toolbar button** or a **context-menu item**, both of which the
harness can already enumerate (`readButtons`, `readVisibleMenuItems`). So generate the worklist
instead of writing it:

1. for each recorded screen and editor tab, enumerate its buttons and its context-menu items;
2. click each, and if a `.dialog-popup` / `.resizableDialog-popup` appears, shoot it and close it;
3. record what each click produced — a dialog, nothing, or a blocker — so the list is auditable.

The classification in step 3 is the part worth doing properly: a click that opens nothing is not the
same as a click that is disabled, and an earlier audit sweep (A5c) found that treating them alike hid
real gaps. A dialog raised from *inside* another dialog is a second pass over the same machinery.

Exit: the generated list of (screen, affordance) pairs is either photographed or carries a reason.

## Phase 4 — explorer context menus

One right-click per *node type* (folder, each document type, System, Favourites), photographed with
the menu open. Small, bounded, and it pins the menu contents that Phase 3 then drives.

## Phase 5 — post-action states

The searched dashboard is the pattern (`lib/actions.mjs`): open, do the thing, wait for it to
genuinely settle, shoot. Worth having, in rough order of value: a run of pipeline **stepping**, a
**Query** execution, the **data browser** with a stream selected and its source pane populated, and
the **import/export** wizards mid-flight.

Each needs its own settle predicate. A fixed wait photographs a half-drawn screen and then fails
intermittently for months; `untilVisualisationsDrawn` exists because the tables settle before the
charts do.

## The six skipped menu leaves

`Sign Out`, `Sign Out Other Sessions`, `Change Password`, `Save Tab Session`, `Delete Tab Session`,
`Add Current Item to Favourites`. They are skipped because they end the session the rest of the run
depends on, or write user state.

Four of them are recoverable with a session per target rather than a rule change: run them **last**,
in a throwaway page, and let them destroy that session. `Sign Out` in particular has a screen worth
having a baseline of. `Change Password` would invalidate the credentials the whole run needs and
should stay skipped unless the seed provisions a throwaway user.

## What "full coverage" would mean

Every menu leaf, every document editor and every one of its tabs, every dialog those can raise,
every explorer context menu, and the post-action states listed above — each with a screenshot and a
DOM dump in the corpus, all replaying from the simulation with zero misses.

Two things will still be outside it, and should be said plainly rather than quietly counted as done:
data-dependent screens whose content the corpus fixes but whose *variety* it does not (a grid with
three rows recorded proves the grid, not the paging), and anything gated on a cluster feature this
instance does not run.

---

# Why a full pass takes hours — measured, 2026-08-20

`probe-timing.mjs` times the crawl's own steps against a live GWT. Everything below is measured, not
estimated:

| step | cost |
| --- | ---: |
| cold `goto` + `signInFully` | 8.9 s (once per browser) |
| `reset()` — reload, `waitForApp`, close dialogs, settle explorer | **1.58 s** |
| route step: main-menu group → leaf | 3.39 s (**2.4 s of it `waitForTimeout`**) |
| route step: expand-all + settle | 1.05 s |
| route step: open document | + 2.5 s fixed sleep |
| route step: editor sub-tab | + 1.0 s fixed sleep |
| post-click `classify()` | 1.2 s fixed sleep |

**The reload is not the problem — 1.58 s is cheap.** The problem is how often it happens.
`navigate(node)` is called once per node *and once per affordance*, and each call rebuilds the world
from a fresh app. The last wide run (250 nodes, 873 affordances) therefore paid for **1,123 full
navigations**:

```
node visits       28 min   (250 navigations)
affordances      130 min   (873 × [navigate 6.7s + click + classify])
TOTAL           2.63 hours
  of which unconditional waitForTimeout:  27 min
```

Scaled to the coverage this suite is aiming at — roughly 900 nodes and 3,000 affordances — that is
**~9.5 hours a pass**, which is why iteration has been so painful. Roughly 85% of the wall-clock is
spent restoring state we already had, and sleeping for events that had already happened.

## …and a 29× landmine that looks exactly like "the app is slow"

While measuring the above, the same probe reported `reset()` at **46.2 s** instead of 1.58 s, three
runs in a row, with the host idle and the server answering `GET /` in 1 ms. The constancy is the
tell: that is a timeout, not load.

The cause is `env('USER', 'admin')`. **Every login shell exports `$USER`**, so a suite script run
without an explicit `USER=` does not get the default — it gets the OS username (`dev1` here) and
tries to sign in as a user that does not exist. Sign-in then fails *slowly* rather than loudly:

| | with `USER=admin` | inheriting `$USER` |
| --- | ---: | ---: |
| cold `goto` + `signInFully` | 8.8 s | 38.8 s |
| `reset()` | 1.58 s | 46.2 s |

**29× on every navigation.** At that rate the last wide run would have taken 14 hours instead of 2.6,
and the output would look like a slow application rather than a broken login. The pattern is in
**ten** suite scripts (`crawl.mjs`, `fill.mjs`, `delete-docs.mjs`, and seven probes).

Two changes, both small:

- Don't fall back to the ambient `USER`. Read a suite-specific variable (`STROOM_USER`), or require
  it, so the value can only come from the caller.
- **Fail fast when sign-in did not land.** `signInFully` is followed by no assertion, and `reset()`
  never re-authenticates — it only does `goto` + `waitForApp`. So a session that expires *mid-crawl*
  produces exactly the same 46 s-per-node grind for the rest of the run, with no error and no
  indication in the output. A single "am I signed in?" check after each reset turns a silent
  multi-hour crawl into an immediate failure.

That second point is the one that matters for a long run: nothing in the suite currently notices
losing its session, and a crawl is precisely the workload most likely to lose one.

## Fixing it, in leverage order

**1. Restore instead of rebuild (~4–6×).** After classifying a click, try the cheap undo — Escape,
close the dialog, dismiss the menu — then re-`survey()` and compare the node signature. If it
matches, the next affordance runs on the same page with no reset and no route replay. Reset only
when the signature differs, the guard saw a mutation, or the click navigated away. This *keeps* the
isolation guarantee — it verifies it rather than buying it by rebuilding. Today's outcome mix says
how far it goes: `nothing` 289, `dialog` 182, `menu` 69 are trivially restorable (62% of clicks), and
most of `changed` 241 / `alert` 82 will be too.

**2. Measured waits, not sleeps (~1.5–2× on what is left).** Six `waitForTimeout` calls carry 27
minutes of that run. A menu leaf lands in 200–400 ms, not 1500. `compare/lib/settle.mjs` already
measures these, and the project rule is that we never sleep blind for a popup — the crawl is the one
place that still does.

**3. Sibling batching.** Order the queue by route prefix. 121 editor-tab nodes each re-open their
document today; one open should serve all of that editor's tabs.

**4. Parallel workers.** Nodes are independent by construction (every one is reached from a fresh
app), so shard the queue across N contexts. The 8.9 s cold sign-in is paid once per worker.

Together these should put the current 250-node run in the 5–10 minute range and a full ~900-node
pass in **30–45 minutes** — the difference between a nightly job and something you can run while you
work.

## The walk (2026-09-04) — one session, no reloads

`crawl.mjs` reached every node from a fresh page load and replayed its route. Measured on the first
full HEAD pass: **1,910 nodes, 4,296 navigations at 7.6s, 9.1 hours** — of which 78% of nodes were
dialogs, alerts and menus whose state the parent's own affordance loop had ALREADY surveyed and
thrown away, and 54% turned out to be duplicates, each paying a full navigation to be told so. The
1.2s blind sleep per affordance alone was 72 minutes.

The reload was buying an isolation it could not deliver: a reload gives a fresh **client**, not a
fresh **world** — anything the server persisted is still there — and what actually isolates a run is
the read-only guard. So `walk.mjs` is the test shaped as it should have been: click in, observe, pop
out, carry on. **Every action declares what it should do to the state, and the way back follows:**

| the click… | the way back | a mismatch is |
| --- | --- | --- |
| opened a dialog/alert/menu from a screen or dialog | close only the topmost thing; assert the parent's signature | a **finding** (Cancel did not cancel) |
| opened anything from a menu | a menu cannot outlive its child's dismissal — re-sync by route | expected |
| changed something with a known inverse (`Ask Stroom AI` dock, auto-refresh, wrapping, a `Click to show …` toggle) | click the inverse; assert | a finding |
| changed something else / a post-action state / a blocked mutation | no inverse is pretended — re-sync by route | expected |
| classified `nothing` | the state must be unchanged; assert | a finding (the classifier was wrong) |

A re-sync **closes the screen's content tab and reopens it**: re-clicking a menu leaf only focuses
the same tab, so a filter applied by `Filter Schedules → OK` stayed applied and every header click
after it sorted an empty grid. A page load happens only when `assertSignedIn` fails.

**Verified against `crawl.mjs` on the Monitoring shard** (`compare-crawls.mjs`, five rounds — each
round turned one "different" into a named expected-change class):

| round | what it exposed | fix |
| --- | --- | --- |
| 1 | 118 menu nodes under one screen: a submenu's survey listed the parent's sibling groups | a menu node's affordances and identity are the items that **appeared** |
| 2 | the body fingerprint drifted with the content-tab strip; `readEditorTabs` read the strip as sub-tabs | fingerprint uses editor sub-tabs only; the walk closes what a root opened |
| 3 | seeds included sibling groups (`Monitoring / Administration` was the Administration submenu); a menu's identity included the screen under it | seeds are the group's own leaves; a menu's signature is its items alone |
| 4 | the AI dock stayed open and its buttons appeared on every later screen; menu-chain leaves left screens open; 14 sorts read `nothing` from two reads taken before the grid re-rendered | inverse actions; close tabs that appeared; a 500ms lead before the first settle read |
| 5 | screen-local state (filters, sorts) survived a route re-sync | re-sync closes and reopens the tab; sort indices normalised out of the signature |

Result: **42 reached vs 51**, every difference named — 34 baseline-only nodes are the fake
`Monitoring / <group>` screens and the Main Menu subtree hung off them, 25 candidate-only nodes are
that same subtree under the first real leaf. **1 of 64 shared affordance outcomes moved** —
`Nodes :: Refresh`, on a live-data screen, the affordance P1 already recorded as timing-dependent.
Findings 152 → 59 → 25 → **3**. **780s against 2451s, 0 reloads.**

Three more rounds came from running whole shards rather than one area, each again a named class:

| round | what it exposed | fix |
| --- | --- | --- |
| 6 | a re-sync closed the route's FIRST leaf's tab, but a menu chain opens the LAST leaf's screen, so Properties kept its paging and Data Receipt Rules sat under seven later roots; a `changed` screen was surveyed before its grid landed | re-sync (and every root) closes every content tab; a `changed` click waits for grid rows to stabilise before the survey |
| 7 | 67 of 416 `changed` outcomes were a dialog's own OK/Cancel removing it; a child that closed itself was popped AGAIN by its parent, whose Escape then took the create dialog underneath (every `New › <type> › OK`); 48 false pop findings on `context` nodes, which are menus | `closed` outcome, and no re-sync when it was the node's last affordance; a self-closed child tells its parent; `context` treated as `menu` |
| 8 | every slow re-sync was the route replay at ~1.4s a step, budget-bound: a button step that opens a MENU waited the full dialog budget | each route step records what it opened and the replay waits for THAT — re-sync 9s → 4.4s, the Navigation probe 199s → 126s on identical places |
| 15 | every unreachable node in the document shards was a NESTED tab listed as a sibling — Report's and Query's `Table`/`Visualisation` under Query, Pipeline's `Info`/`Error`/`Data Preview`/`Meta`/`Context` under Data, Dashboard's five component tabs under `Dashboard`; the crawler marked the same ones unreachable | on a miss, a `tab` step clicks each visible top-level tab in turn and looks again |
| 14 | the document shard walked 26 editors in series at ~2 nodes/min while the menu workers sat idle, and a stopped run lost everything it had walked | `DOCTYPES` / `recipes:doc:TypeA|TypeB` shard the editors across workers; the output file is written after every root (`summary.complete` says whether the run finished). Analytic Rule's `Table` and `Visualisation` are NESTED tabs under Query that `readSubTabs` lists as siblings — reachable only via Query, recorded as unreachable at the top level |
| 13 | Administration on the discard-aware code still re-synced at 19.9s a click with zero Confirms answered, and the document shard's trace showed every timed `discardIfAsked` window empty: the prompt arrives 1–3s after the close under load, and the Escape pressed in between (`dismissMenus`, `clearOverlays`) cancelled it early or missed it late. `probe-closeall.mjs` reproduced the clean sequence in isolation | closing a tab is event-driven: after `Close`/`Close All`, wait for the strip to shrink OR a Confirm to appear, answer it with a real click, repeat — no Escape in between |
| 12 | the strip after Close All read `tabs [Seed Analytic Rule | * Data Retention*] dialogs [Confirm]` — the walker's own prompt was UP and three answers did nothing: the answer was an in-page `.click()` from `evaluate`, which GWT's Button ignores (the trap `structure.mjs` documents for explorer rows), and `closeTop`'s own-Cancel had the same flaw | `clickDialogButton` resolves the button in the topmost matching dialog to a point and clicks it with the mouse; both paths use it, and a Confirm with no answerable button is logged with the strip |
| 11 | still "no sub-tab" after round 10: the where-am-I diagnostic read `tabs [Seed Analytic Rule | * Data Retention]` with Data Retention selected after a re-sync — the tab reset had not closed the dirty tab, and re-opening a document that is already open does not bring its tab to the front, so the editor's sub-tab bar was rendered and hidden | `openDoc` and the `docType` step activate the document's content tab; `closeAllContentTabs` prints the strip once when tabs survive; the Confirm wait is 2s and matches any caption containing "confirm" |
| 10 | nine of Analytic Rule's ten sub-tabs "no sub-tab", and every re-sync through a tab step failed: after a fast re-sync reopened a document, `waitForDocOpen` was satisfied by the content strip's `Welcome` tab and returned before the editor rendered. `crawl.mjs` had the same test and got away with it because its reload made every open slow enough to lose the race | the wait targets the editor's own sub-tab bar (`.linkTab-label`); a `tab` step waits for its tab to exist before clicking |
| 9 | 22 of 26 document editors recorded as duplicates of a `Confirm`: closing a document the walk had left dirty asks "There are unsaved changes. Are you sure you want to close this tab?" — asynchronously, so the first fix read for it too early and answered none — `closeDialogs` never presses OK, so the tab stayed and the next root opened under the prompt. The same prompt made Monitoring's 317 re-syncs burn the three-attempt tab-close loop each: 6647s for a shard that takes ~2200s | the tab-closing path — and only it — answers the walker's own Confirm (`discardIfAsked`, counted as `discardedChanges`); a root that arrives under any dialog says so on its record (`blockedByDialog`) |

**Determinism check (P3, the six context shapes + a dashboard with post-action states on):** the same
walk on two consecutive versions gave **222 of 222 node ids in common, 0 surface differences, 0 of 517
affordance outcomes moved** — 1827s against 2262s, findings 51 → 5. The walker gives the same answers
run to run; the seconds are what change.

Where the walker's time goes now (P3, 517 affordances): clicking and settling 572s, getting back
868s (~1.7s per affordance), root navigation the rest. Re-syncs are still the lever if more speed is
wanted: a replayed route is ~0.5s a step.

**The full pass on one walker version (2026-09-05, `8c08ef6`–`927fc51`), against the same-day
crawler pass measured with the same ledger:**

| | crawler | walker |
| --- | ---: | ---: |
| DOORS reached | 95/201 | **97/201 (48.3%)** — all 26 document editors, every shard `complete` |
| places / affordances | 1,133 / 5,011 | **1,201 / 6,139** |
| post-action states | 0 | **405** |
| page reloads | 6,102 | **1** |
| menu areas (7 shards) | 354 places in 4.6h | **521 places in 2.8h** |
| documents (3 type shards + P3) | 779 places in 8.0h | 680 places in 9.8h |
| "unsaved changes" prompts answered | — | 324 |
| findings | — | 143 + 63: 31 `pop` (all Dashboard post-action states), the rest `drift` on live screens |

The menu areas are where the walker wins outright. **The documents are not faster yet and find
slightly fewer**: every editor dirties itself under the walk, so each re-sync is a document reopen
plus a discard round, and Dashboard alone is hours. That is the remaining lever — a re-sync that
replays only the suffix from a screen that is verifiably still there — and it is an optimisation,
not a correctness gap: the outcomes are stable and every difference is named. Coverage itself moved
by attribution today, not by crawling, which is the lesson P2 already taught.

Still unreachable, and honestly so: `Visualisation` on Report/Query exists only once a visualisation
component does, and Pipeline's `Info`/`Error`/`Data Preview`/`Meta`/`Context` are the stream
viewer's tabs, present only after a stream row is selected in Data — a post-action state to seed,
not a tab to click.

## The mining pass (2026-09-06) — reaching 100% of screens is a mining problem first

After the walker's first full pass the ledger read DOORS 97/201. Without walking anything, reading
the source moved it to **115/201 (57.2%)**, which is the P2 lesson a third time: coverage is limited
by what can be NAMED and by what the walk KNOWS TO DO, not by what it can reach. What was mined:

| idiom | what it names | doors |
| --- | --- | --- |
| `.text(SCREEN_NAME)` — a menu label that is a constant; `addMenuItem(KEY, var)` built earlier; `class UsersPlugin extends MonitoringPlugin<UsersPresenter>` | all nine Security leaves and their presenters (the miner had three) | 97 → 104 |
| `addTab(SETTINGS, new DocTabProvider<>(settingsPresenterProvider::get))`, the `TabDataImpl.builder().label(…)` form, `new AbstractTabProvider<Doc, MetaPresenter>(…)`, and a `*TabProvider` class's own generic | 130 (editor, tab label) → embedded presenter pairs across 29 editors — every sub-tab the walk had visited under its editor's name | 104 → 110 |
| `ComponentType TYPE = new ComponentType(n, "table", "Table", …)` | a dashboard's component tabs, where the component's name is its type | 110 → 113 |
| a dialog's shower may be a BASE class (`AbstractComponentPresenter` shows every component's `Settings`); helper classes that show on a presenter's behalf (`ColumnsManager`) are its children | route narrowing through inheritance; `Settings` no longer ambiguous | 113 → 114 |
| `.text(tabLabel)` where the label is a constructor parameter each subclass supplies in `super(…)`; a plugin inherits its presenter from the plugin it extends | the four Annotation tag screens | 114 → 115 |

**`oracles/door-preconditions.md`** then reads, for every door still unreached, every opener site in
the source — by declared type, through `builder(x)`, `x.show()`, `provider.get().show()` and the
events the door handles — and classifies the enclosing method by its guard. Triggers resolve
through the handler variable to its `SvgPresets` title (a preset declared `disabled()` is a
selection gate), and through the ViewImpl's `@UiHandler` to the `ui:field`'s text. The 88 split:

| class | doors | remedy |
| --- | ---: | --- |
| plain | 41 | 8 are column-menu items (`Filter`, `Format`, `Rename`, `Expression`, `Conditional Formatting`) — a dashboard table's header click opens that menu in design mode; 6 are buttons on the AI dock and the annotation editor; 25 have no trigger the miner could name yet |
| create | 9 | New/Add paths — the walk treated every screen's `New` as the explorer's chrome; fixed in `walk.mjs` (chrome only inside the explorer pane) |
| selection | 7 | a row must be selected first — seed a selection as a post-action |
| menu-screen / document-editor | 10 | attribution, or a document of the type |
| needs-component | 4 | dashboard component types that exist only where a dashboard holds one — add each type in design mode (client-side, no mutation until Save) |
| opener-not-mined / no-opener | 15 | miner gaps and research |
| permission / mutation | 2 | this user's permissions; a mutation-permitted pass |

And one walker rule found by the report: the once-per-run `Ask Stroom AI` click always landed on a
post-action node with `POSTDEPTH` spent, so the dock's `Configure` / `Conversation History` /
`Download` dialogs were never descended into. A chrome toggle's state is now walked regardless.

Both walker rules validated on an 80-node Administration walk (`58d65b6`): `Data Volumes › New` and
`Index Volumes › New` now open their dialogs (they had been chrome-skipped), and the AI dock's
post-action state was walked through to `Configure` (AskStroomAiConfigPresenter), `New Conversation`
and `Configure › Set As Default`.

## Directed seeding (2026-09-17) — the walk given the preconditions the source names

`door-preconditions.md` said what each unreached door needed; this pass taught the walk three of
those preconditions and ran them. **DOORS 118 → 127 in one 278s directed run**, → 132 with the
selection rule on three menu shards, → 141 from ledger and miner rules with no walking at all, → **142
(70.6%)** once a dialog's signature carried the screen it was opened from. No full pass. (`walk.mjs`
`DIRECTED=dashboard`, the `select-row` rule, and `crawl-all`'s `directed:dashboard` shard.)

The ledger and miner rules — each cost a door until it was found, and each is the P2 lesson again,
that coverage moves by NAMING: a menu is a route, not a screen; a parent's `P → P` self-edge is not
a candidate for its child; a popup is narrowed by its parent's OWN presenters, not the screen the
route's menu step names on every descendant; several edges to one presenter are one candidate;
`x.show(...)` is an edge whatever its arguments, its caption whichever argument carries a literal
(showEdges 217 → 289); a same-file `String foo() { return "literal"; }` is substituted into a caption
expression (`getEntityDisplayType() + " - " + name` had been `… - …`).

| precondition | how the walk establishes it | what it opened |
| --- | --- | --- |
| **design mode + a clickable header** | `Enter Design Mode`, then the Table pane's own tab menu → `Maximise` (the seed dashboard's Table pane is ~0px tall and its `th` sits BEHIND the next pane's tab bar — a blind click there opened that tab's menu). A header is offered as `column: <name>` only when `elementFromPoint` returns it. The click is move → press → release: `MyDataGrid` arms its heading preview handler on a MOUSEMOVE and `TablePresenter.setDesignMode` is what turns header selection on. | the column menu — Rename, Expression, Sort, Group, Format, Filter — and every dialog behind it: `RenameColumnPresenter`, `ColumnFunctionEditorPresenter`, `FormatPresenter`, `TableFilterPresenter` |
| **a component of each type** | `Add Component › <type>` puts the dashboard into DESTINATION mode (the new tab is "being dragged" with no button held) and a primary MOUSEDOWN on a pane places it — dropped on the tab bar holding `Query`, so it becomes a tab there and is selected. The Settings dialog it opens by itself is closed, so the node surveyed is the component. Client-side until Save, which the guard blocks anyway. | `KeyValueInputPresenter`, `ListInputPresenter`, `TextInputPresenter`, `TableFilterPresenter`, `EmbeddedQueryPresenter` — as `editor-tab` nodes the ledger already knew how to name (`tabsOf`) |
| **a selected row** | on every screen, editor tab, dialog or post-action state with a grid row that has text and something disabled at rest: select the first row and walk ONLY what that newly enabled. Named by its screen (`after select row in Data Volumes`), because `after select row` + `[Edit, Delete]` over a one-grid screen is the same signature on Data Volumes and Index Volumes — the second was filed as a duplicate of the first until it was. | `Edit` on Data Volumes / Index Volumes (the volume-group editors, and `New` volume inside them), `Edit` on Properties (→ the cluster-values dialog), `Delete user`, `Add` to a group, `End this session`, `Revoke` a signing key |

And one ledger rule the column menu forced: **a menu is a route, not a screen** — a dialog opened
from a menu item is narrowed by the screen that opened the menu (`coverage.mjs`, the same reading the
explorer's context menu already had). That alone moved the existing corpus 118 → 119.

**What the run says about the preconditions the walk still cannot make:**

- Node Groups, Processor Profiles and Search Results have NO DATA on this instance — the one "row"
  the survey counted is a 20px placeholder with no text (the gate now requires text). `NodeGroupEdit`,
  `ProcessorProfileEdit`, `ResultStoreSettings` need a node group, a profile and a completed search to
  exist first: seeding, i.e. a mutation outside the walk.
- `CreateNewGroupPresenter` is `Edit` on a selected GROUP row; the first row on User Groups is the
  user `admin` ("User editing is not supported"). The selection needs a row predicate — the next
  directed variant, not a new mechanism.
- Verification: Administration 0 of 186 shared outcomes moved; Monitoring 14 of 428 and Security 11
  of 429 moved, every one `dialog/changed → unclickable (did not accept a click within 3s)` or live
  Nodes/Server Tasks drift, on a box running FOUR browsers at once. The Administration re-run on its
  own — one browser — moved 0 of 318, which is the evidence that it was load. The new shard
  files sit BESIDE the corpus (`crawl-<area>-select.json`) so the ledger is cumulative and no
  `unclickable` displaces an earlier `dialog`.

## The research pass (2026-09-17) — 60 unreached doors read against the source

Source-side only, no browser except one config probe. **Denominator 201 → 189** (twelve were never
doors) and **DOORS 140/189 = 74.1%**, one credit withdrawn because it had been wrong.

**Came off the denominator, each with the line that proves it** (`build-reachability-graph.mjs`):
five abstract bases a plugin merely mentions (`DocPresenter`, `LinkTabPanelPresenter`,
`ContentTabPresenter`, `DocTabPresenter`, `AbstractFindPresenter` — the abstract test now runs
BEFORE the plugin/opener edges); three embedded panels that were "dialogs" because a holder held
them (`DenseVectorFieldPresenter`'s own `show()` is never called, its view goes into the index
field form; `FeedDependencyListPresenter`'s self-show is COMMENTED OUT and the graph read raw
source; `ContentStoreContentPackDetailsPresenter` is a `FlowPanel` added to the store screen);
`UnknownComponentPresenter` (fallback for an unregistered dashboard component type — needs corrupt
content) and `ContentTabPanePresenter` (the shell); and three config-gated doors, OFF on this
instance by `probe-uiconfig.mjs`: `SplashPresenter` (`stroom.ui.splash.enabled`),
`ManageActivityPresenter` and `ActivityEditPresenter` (`stroom.ui.activity.enabled`). Turning those
two properties on is a mutation and a decision; the graph names them so the choice is visible.

**Four miner idioms found by asking why an opener was "not mined"** (`build-route-recipes.mjs`,
showEdges 294 → 319): the event-fire regex's 400-character tail was CONSUMED, not looked ahead, so
every `fire(` within 400 chars of the previous one was invisible — `DependenciesPresenter`'s five
fires yielded two and `ShowDependenciesInfoDialogEvent`, one handler, was never an edge (the same
bug that hit `ShowPopupEvent.builder(` in August); `x.open(…)` and `provider.get().show(…)` are
edges (`AnnotationTagPresenter`'s create and edit dialogs); `new ShowPopupEvent.Builder(x)` is a
show site (`VisualisationAssetsEditAssetDialogPresenter`); and an `@ProxyEvent` dialog's opener is
whoever fires its event, not the Ginjector that holds an `AsyncProvider` for it.

**The 50 still unreached, by what the source says they need:**

| need | doors | how |
| --- | ---: | --- |
| a row's ACTION-CELL menu (the `⋮` on a grid row) | 2+ | `DependenciesInfoPresenter` (Dependencies › row › `Properties`), `UserTabPresenter` + `UserPermissionReportPresenter` (Users › row › `Open user '…'`) — one walker capability |
| a double-click on the toolbar SPINNER | 1 | `UserTaskManagerPresenter` (`MainPresenter`: click-timer on `getSpinner()`) |
| the SIGN-IN page surveyed before signing in | 1–2 | `EmailResetPasswordPresenter` (`allowPasswordResets: true` on this instance, so the link is there), `ChangePasswordPresenter` |
| a `select-row` with a PREDICATE | 3 | `CreateNewGroupPresenter` (a GROUP row on User Groups), `VisualisationAssetsEditAssetDialogPresenter` (a tree item, then `Edit`), `NewElementPresenter` (a pipeline element) |
| a re-run on the current walker | 2 | `AnnotationTagCreatePresenter` / `AnnotationTagEditPresenter` — the Annotations shard predates the chrome fix, so its screen-level `New` was skipped |
| a `docType` seed for `Folder` and the `System` root | 2 | `FolderPresenter`, `FolderRootPresenter` (`FolderPlugin` is a `DocumentPlugin`) |
| the Welcome tab, which every run has open | 1 | `WelcomePresenter` — a `content-tab` seed, attribution only |
| a dashboard/query table WITH RESULTS | 4+ | `ColumnFilter`, `RulesPresenter`, `ColumnValuesFilter`, `DownloadPresenter`, `ChangeStatus`/`ChangeAssignedTo` (rows carrying annotation ids) — whether `Execute Query` runs under the guard decides if this is a seed or data |
| DATA that does not exist here (a mutation to seed) | 5 | `NodeGroupEdit`, `ProcessorProfileEdit`, `ResultStoreSettings` (empty grids), `TabSessionChooserPresenter` (needs ≥2 saved tab sessions — with none it is the alert the walk saw), `SelectionSummary`/`ProcessChoice` (a stream) |
| a MUTATION path | 2 | `DocRefSelectionPresenter` (`PipelinePlugin.save` with several dirty documents), `GitRepoCommitDialogPresenter` (Push) |
| a PERMISSION or config | 1 | `CredentialsManagerDialogPresenter` (`AppPermission.CREDENTIALS` AND a content pack whose git needs auth) |

Everything in the first seven rows is one directed pass — three small walker capabilities (row
action cell, double-click, sign-in page) and four seeds — for ~12 doors. The last three rows are
yours to decide, because each changes the instance.

## Directed pass 2 (2026-09-18) — the seven rows, run

The pass the research named: `DIRECTED=rows,chrome,docs,signin` (105 nodes, 1997s, one browser) and
the Annotations re-run on the current walker (69 nodes, 718s, 0 of 79 shared outcomes moved against
the Sep 5 shard — the chrome fix added 14 nodes and displaced nothing). It had been launched on
2026-09-17 and cut off three minutes in when its session died, so the walker commit landed a day
before its evidence. **DOORS 140 → 152/189 (80.4%)**, all 194/392.

**Twelve doors from the walk, each by the precondition the source had named:**
`DependenciesInfoPresenter` (Dependencies › row `⋮` › Properties), `UserTabPresenter` +
`UserPermissionReportPresenter` (Users › row `⋮` › Open user — a content tab whose six sub-tabs the
lazy-tab miner idiom credits, `UserInfoPresenter` and `UserDependenciesListPresenter` among the
embedded), `CreateNewGroupPresenter` (`select-row` with `match: .svg-image__users` — the first GROUP
row, not the first row), `UserTaskManagerPresenter` (the spinner double-click), `WelcomePresenter`
(the content tab every run has open — attribution), `FolderPresenter` + `FolderRootPresenter`
(`docType: Folder` and the `System` root) and `DocumentUserCreatePermissionsEditPresenter` under the
Folder's Permissions tab, `EmailResetPasswordPresenter` (the sign-in page surveyed signed out,
`link: Forgot password?`; `LoginPresenter` is place-based, so it is credited but not a door),
`AnnotationTagCreatePresenter` (the re-run), and `ProcessChoicePresenter` — the System root's Data
tab HAS streams on this instance, so the "needs a stream" row above was wrong for the root, and
`select-row` there offered Process, Delete and Selection summary. The thirteenth, `SelectionSummary`,
was reached too but needed the miner; it is one of the two below, and the withdrawal is the other.

**Two miner gaps, found by the dialogs the pass reached that nothing could name** (`showEdges`
319 → 330):

- **A keyword is not a type.** `return userRefPopupPresenter;` matched the declaration regex as a
  variable of type `return`, and a later match overwrites an earlier one, so the real declaration
  eleven lines above was erased and `userRefPopupPresenter.show("Add User Or Group", …)` had no
  receiver type — UserAndGroupsPresenter published no edge to the popup at all. 2,042 `return x;`
  statements in the client, each able to erase the type of what it returns. Fixing it also gave
  `Select Group` its real owner, which WITHDREW a credit: `ItemSelectionPresenter` had been named by
  its `Select …` template on the User Groups `Select Group` dialog. It is honestly unreached.
- **A caption one call away.** `showSummary(criteria, VIEW, null, null, "Selection Summary", …)` →
  `private void showSummary(…, final String caption, …) { selectionSummaryPresenterProvider.get()
  .show(…, caption, …) }`. When a show-site argument is a bare parameter of the enclosing method
  NAMED `caption`/`title`, every same-file call contributes what it passes there. Six captions for
  `SelectionSummaryPresenter`, and `Add Volume` / `Edit Volume` for the two volume editors, whose
  edges had been captionless.

**What the pass could not do, and why — the next worklist:**

| door | what the run saw | what it needs |
| --- | --- | --- |
| `AiChatHistoryPresenter`, `DownloadChatPresenter`, `AiAttachmentDataPresenter` | `Conversation History → present when surveyed, not locatable when clicked`: `Ask Stroom AI` is a TOGGLE, and the re-sync after the blocked `New Conversation` re-clicked it, closing the dock | a directed `ai` seed that opens the dock as its route step, so the re-sync reopens rather than toggles — a walker rule, not data |
| `VisualisationAssetsEditAssetDialogPresenter` | `no tree item to select` — the seeded Visualisation has no assets | an asset in the seed (a mutation) |
| `AnnotationTagEditPresenter` | Labels / Collections / Statuses grids are empty (`10 actions, 8 disabled`, no text row for `select-row`) | a tag in the seed (a mutation) |
| `NewElementPresenter`, `NewPropertyPresenter`, `NewPipelineReferencePresenter` | not attempted — the pipeline structure is not a GWT `Tree`, so `select-tree` does not apply | a `select-element` step on the pipeline canvas |
| `Choose Pipeline To Process Data With` | reached under System › Data › Process; `DocSelectionPopup` is not in the reachability graph, so it is not a door — the caption is listed so the miner hole is visible, not because it counts | nothing, unless the graph decides `ExplorerPopupPresenter` subclasses are doors |

The data rows above still stand: `NodeGroupEdit`, `ProcessorProfileEdit`, `ResultStoreSettings`,
`TabSessionChooser` need data; `DocRefSelection`, `GitRepoCommitDialog` need a mutation path;
`CredentialsManagerDialog` needs a permission and a content pack. The rest of the 37 are the query
result-table dialogs (a search that has returned columns) and the Pathways / Data receipt / annotation
edit dialogs the preconditions file classifies as `plain` — each a directed seed once its screen has a
row to act on.

## Directed pass 3 (2026-09-18) — the walker rules, and a query that returns rows

Four short directed runs (`DIRECTED=ai,stream,pipeline,docs` then `results,pipeline` then `results`;
~50 minutes of browser in all) and three miner idioms. **DOORS 152 → 161/189 (85.2%)**, all
206/392. Every door came from a precondition named first and a seed built for it; nothing was
crawled blind.

| precondition | how the walk establishes it | what it opened |
| --- | --- | --- |
| **the AI dock open** | `Ask Stroom AI` is a TOGGLE (`InlineSvgToggleButton`, `.on`), and neither a closed tab nor a closed dialog turns it off — so the re-sync after the blocked `New Conversation` re-clicked it and CLOSED the dock (`Conversation History → present when surveyed, not locatable when clicked`). A toggle in a route is now a state to be in, not a click to be made, and the re-sync turns off any toggle the route ahead does not want on. | `AiChatHistoryPresenter` |
| **a stream really selected** | `select-row` was clicking the first cell wider than 20px — on a stream grid that is the 24px `TickBoxCell`, which CHECKS the row (Process / Delete light up, `getSelectionSummary` fires) without SELECTING it, so the relation list and data preview never loaded. `probe-source.mjs` showed it: no `/api/data/v1/fetch` at all. A cell WITH TEXT selects; the preview loads; its `View Source` label (`DataViewImpl.sourceLinkLabel`, a `clickableLabel`, not a button — the `link:` affordance) fires `ShowDataEvent` as a content tab `Stream <id> : 1 : 1`. | `SourceTabPresenter` |
| **a pipeline element selected** | the Structure canvas is not a GWT `Tree`: `DraggableTreePanel` selects on MOUSEUP when the target is the element the MOUSEDOWN landed on (movement between the two is a drag). `select-element` presses and releases on the LAST `.pipelineElementBox-label` — the box itself carries no class. With the seed's own steps not spending `POSTDEPTH` (`seedDepth`), the properties grid gets the generic row selection too. | `NewElementPresenter` (Add › Filter › `Create Element`), `NewPropertyPresenter` (a property row › `Edit Property`) |
| **a query that RETURNS ROWS** | `Execute Query` is a search and the read-only guard allows it (a search only makes transient server state) — the question in "The research pass" is answered. The seeded documents' queries all error; `probe-results.mjs` found `from "Example Index" limit 20 select StreamId, EventTime, UserId` answers 20 rows (`limit` goes BEFORE `select` — after it is `Unexpected token LIMIT`) and `from "Annotations" select Id, Title, Status` 10. A `set-ace` route step writes the query, client-side, nothing saved. `QueryResultTablePresenter` has `allowHeaderSelection` on by default (MyDataGrid's default; only the dashboard's TablePresenter ties it to design mode), so the column menu opens with no mode to enter — `columnMenus` takes a scope now. | `ColumnFilterPresenter`, `RulesPresenter` (Conditional Formatting), `DownloadPresenter`, and on a SELECTED annotation row's `Annotate` menu `ChangeStatusPresenter`, `ChangeAssignedToPresenter` |

**Naming what was reached** — the seed says whose surface it walks. The Query doc's result table is
three embeds below `QueryDocPresenter` (QueryDocEdit → ResultTableSplit → ResultTable), and the
ledger's narrowing is ONE level on purpose: transitive embedding was tried and TablePresenter's
closure is half the application, so `Settings` gained a second candidate and the Table's own
Settings dialog lost its name. `Filter '…'`, `Settings` and `Download Options` each belong to two
presenters application-wide, so only a parent can decide; the results seeds carry
`presenter: QueryResultTablePresenter` and the route names them. Two ledger rules came with it: an
exact caption beats a template INSIDE a parent too (with wider candidate sets `New Field` also met the
explorer popup's `New …`), and a popup under a post-action state that names nothing walks up to the
screen beneath it, as it already did through menus.

**Three miner idioms** (`showEdges` 330 → 332, `opener-not-mined` 3 → 0): a same-file lazy GETTER
as receiver (`getChangeStatusPresenter().show(list)` — typed by its return type); a class name that
is not unique (`RulePresenter` is the dashboard's conditional-formatting rule editor AND the
data-receipt one, and `fileOf` kept whichever came last — five presenter names collide; a shower's
sites are read from every file of that name); and a `*TabPresenterPlugin` answers an EVENT
(`ShowDataEvent`), not a menu leaf — `DataPreviewTab`/`SourceTab` were "attribution gaps" when they
needed a seed.

**Findings the runs left, for the next pass** (28 unreached; `door-preconditions.md`):

- **In the annotation editor, one level deeper**: `after Edit Annotation` was reached from the
  Annotate menu; `CommentEditPresenter` (`Edit Entry`, a comment row's menu), `DurationPresenter`
  (`Change Retention Period`) and `AddEventLinkPresenter` sit inside it — a seed rooted there.
- **A selected VALUE, not a row**: `CustomRowStylePresenter` appears when the rule editor's
  `Formatting Type` is set to Custom; `ColumnValuesFilterPresenter` is the header's filter icon,
  not the header. Both need a `set-select` / `click-icon` step.
- **Client-side creates**: `ConstraintEdit`/`PathwayEdit` (a pathway added in the editor),
  `NewPipelineReference` (an element with a reference property — `ReferenceDataFilter`, added
  client-side), `ElementPresenter`/`StepLocation` (stepping on a stream that produces output —
  `POST /api/stepping/v1/step` is BLOCKED by the guard, so this one is a whitelist decision).
- **The data preview's pager**: `ItemSelection`, `CharacterRange` — the preview now loads under
  the fixed selection; the pager's `…` buttons were not offered, so the reader does not see them.
- **Data or a mutation, as before**: `NodeGroupEdit`, `ResultStoreSettings`, `TabSessionChooser`,
  `VisualisationAssetsEditAsset`, `AnnotationTagEdit`, `FieldEdit` (a receipt rule with a field),
  `DataPreviewTab` (a task or hyperlink carrying a stream), `AiAttachmentData`/`DownloadChat`
  (`createChat` is a POST the guard blocks — a chat with messages is a mutation), `GitRepoCommit`,
  `DocRefSelection`, `CredentialsManager`.

## Directed pass 4 (2026-09-18) — one level deeper, and a value rather than a row

`DIRECTED=annotation,cf,pipeline-ref,pathways`, three short runs and two probes (`probe-pass4.mjs`),
~50 minutes of browser. **DOORS 161 → 166/189 (87.8%)**, all 217/392; one credit withdrawn.

| precondition | how the walk establishes it | what it opened |
| --- | --- | --- |
| **the annotation EDITOR open** | Annotate › `Edit Annotation` on a selected annotation row, walked as an EDITOR (its six sub-tabs) with two affordances the button reader cannot see: `titled: Change Retention Period` (a `SettingBlock` opens its chooser on a click) and `entry: comment` (a MOUSEDOWN on a history entry, `[entryType="4"]`, opens Edit Entry / Delete Entry) | `DurationPresenter`, `AddEventLinkPresenter` (Events › Add) — and not `CommentEditPresenter`: no annotation here has a comment entry to press |
| **a VALUE set, not a row selected** | `set-select`: a `SelectionBox` is opened by its ICON — a click on its text box calls `showPopup()` and then bubbles to the box's own handler, which calls it again and HIDES it — and ONE mousedown on the item selects and closes. The item must be ON SCREEN: the popup is laid out at (−956, −938) before it is positioned, and `offsetWidth > 0` passes that. `Formatting Type = Custom` reveals `Set Custom Style`. | `CustomRowStylePresenter` |
| **the header's ICON, not the header** | `click-in-header`: the same move → press → release MyDataGrid needs, aimed at `.column-valueFilterIcon`; what opens is a `simplePopup-popup`, not a captioned dialog, so the step REQUIRES the popup and the seed names it | `ColumnValuesFilterPresenter` |
| **the element that CONSUMES references** | `select-element` with `match: xslt`. A client-side-added Reference Data Filter enables Edit Element but not New Reference — that filter LOADS reference data; the pipelineReference PROPERTY is on the XSLT filter. | `NewPipelineReferencePresenter` |
| **the Pathways editor under its alert** | closing `Remote node '' has no URL set` (`findPathways`, 500 — this instance has no node URL configured) leaves a whole editor; `New Pathway` is enabled but its click does nothing, because the same failed load is what the dialog reads | `PathwaysSplitPresenter`, `PathwaysSettingsPresenter` — the two pathway dialogs are INSTANCE-gated, like splash and activity |

**One ledger rule, from a false credit it made:** a lone route candidate whose every edge carries a
caption must still FIT the caption observed — `Save 'Seed Pathways' as` had `PathwayEditPresenter`
(`New Pathway` / `Edit Pathway`) as its only candidate under the pathway list and was credited six
times. The same shape as `ItemSelectionPresenter`'s `Select …` credit. Withdrawn; an uncaptioned edge
stays route-only evidence. And one walker rule: a chrome label a SCREEN also uses (`New`, `Delete`)
clicks the screen's button, not the explorer's, which comes first in the DOM.

**The 23 left, and there is no more directed pass in them.** Every one is data, a mutation, a
permission, or this instance's configuration:

| class | doors |
| --- | --- |
| data to seed (a mutation) | `NodeGroupEdit`, `ResultStoreSettings`, `TabSessionChooser` (≥2 saved tab sessions), `VisualisationAssetsEditAsset` (an asset), `AnnotationTagEdit` (a tag), `CommentEdit` (an annotation with a comment), `FieldEdit` (a receipt rule with a field — its `New` is `create`), `DataPreviewTab` (a task or hyperlink carrying a stream), `ItemSelection` / `CharacterRange` (the data preview's pager labels, which the stream here is too small to show) |
| a mutation the guard blocks | `AiAttachmentData` / `DownloadChat` (`createChat`), `ElementPresenter` / `StepLocation` (`POST /api/stepping/v1/step` — **triaged and reached in pass 5, below**), `DocRefSelection` (a multi-document save), `GitRepoCommit` (push) |
| this instance's configuration | `PathwayEdit` / `ConstraintEdit` (a node URL), `CredentialsManager` (`CREDENTIALS` permission + an authenticated content pack) |
| a screen with its own data | `ProcessorLimits` / `QueryInfo` (dashboard query settings that prompt on a search with limits), `EditExpression` × 2 and `SelectionHandler` (Data Retention's impact filter, a dashboard selection handler — both `create` paths on screens whose lists are empty here) |

## Directed pass 5 (2026-09-21) — the guard decision: stepping is a read

`DIRECTED=stepping`, one 44-minute run (61 nodes, 21 places, 499 route steps, 110 re-syncs at ~35s
each — every re-sync reopens the pipeline and re-enters stepping mode) and one probe
(`probe-stepping.mjs`). **DOORS 166 → 168/189 (88.9%)**, all 220/392; one false credit prevented.

**The decision.** `POST /api/stepping/v1/step` had been blocked by the read-only guard as a mutation
because it EXECUTES a pipeline. Read against the server it is the result-store lifecycle again:
`SteppingService` captures each element's IO into a content-addressed scratch store under Stroom's
TEMP dir (`SteppingConfig.storeSubDir = "stepping"`, orphans evicted after an hour), creates no meta,
writes no stream and touches nothing the corpus records. `step` and `terminateStepping` are now on
the guard's allow-list (`compare/lib/readonly-guard.mjs`); `getPipelineForStepping` and
`findElementDoc` were already reads by name.

| precondition | how the walk establishes it | what it opened |
| --- | --- | --- |
| **stepping mode at rest** | `Enter Stepping Mode` on the Structure tab (a toggle the document re-open resets). The meta list finds 100 streams; nothing is selected. `Refresh Current Step` is enabled here and answers a **500 NPE** (`criteria` is null) — a GWT defect, logged as gwt-bugs #36. | `SteppingPresenter`; `Change Step Filters`, `Filter Streams` (already known) |
| **the location LABEL** | `selector: .stepLocationLink` — a new affordance class for a clickable whose text is DATA (`[<metaId>:<part>:<record>]`, `[??:??:??]` at rest), so no label can name it | `StepLocationPresenter` (`Set Location`) |
| **a stream the pipeline can PARSE** | `select-row` with `text: '^\\S+ Events '`: the list's first row is an Error stream — `step` runs (200) but `Content is not allowed in prolog` leaves `foundRecord:false` and the four step buttons disabled. An `Events` row steps to `[2054:1:1]`; Forward → `[2054:1:2]`; First/Backward/Last/Terminate all run for real. | the step controls, walked as post-action states (each is the toolbar again, POSTDEPTH-bounded) |
| **a NON-SOURCE element, on that stream** | the same `select-element match: xslt` as the Structure canvas — the stepping tree is the same `PipelineTreePresenter` | `ElementPresenter` — created client-side by `SteppingPresenter.getContent`; its code, input and output panes (three Ace editors) carry the XSLT filter's real IO from the response's `elementMap` |

**One miner rule, from a false credit it was about to make.** The merged shard credited
`ElementPresenter` from eleven `Filter Streams` dialogs. `SteppingPresenter.java` declares
`presenter` twice — `final ExpressionPresenter presenter` inside the stream-filter click handler
(line 304) and `final ElementPresenter presenter` in `getContent` (line 522) — and the miner's
`typeOf` map was file-wide, last declaration wins, so the `Filter Streams` show site was typed as
the element panel. A show site now resolves its variable to the nearest declaration BEFORE it
(`typeAt` in `build-route-recipes.mjs`); the file-wide answer stands in only for a field declared
below its use. Three edges moved, each checked against the source: `Filter Streams` →
`ExpressionPresenter`, `Filter Documents` (BatchDocumentPermissionsPresenter:151) →
`ExpressionPresenter`, and the hyperlink dialog (HyperlinkEventHandlerImpl:275) →
`IFramePresenter`. `ElementPresenter` is now credited from exactly one place: the selected element.

**The 21 left** are the table above minus the two stepping doors — data to seed (10), the three
mutations the guard still blocks (`createChat`, a multi-document save, git push), this instance's
configuration (3), and the four screens whose own lists are empty here.

## Directed pass 6 (2026-09-21) — the data the doors needed, and the tabs the walk never clicked

**DOORS 168 → 182/189 (96.3%)**, all 236/392, in one afternoon: thirteen walks (~2.9 hours of
browser, the two long ones the Monitoring and Administration shards at ~36 minutes each), one new
script, two miner rules. The "21 left, and there is no more directed pass in them" above was wrong
about fourteen of them, in two different ways.

**Half were data, and data is a script.** `seed-data.mjs` is the counterpart of `seed.mjs`: where
that creates the document TYPES the instance lacked, this creates the ROWS — through the REST API
from inside the signed-in page (session cookie + `X-CSRF`), because a row is data and what the walk
then does with it is the test. It runs unguarded, on purpose and visibly; it is idempotent by name
(`DRYRUN=1` reports, `REMOVE=1` takes everything out again); and it shares its names with the
walker through `lib/seed-names.mjs`, so a seed can address *the* seeded row rather than "the first
row". What it creates, and the door each row opened:

| seeded | how | door |
| --- | --- | --- |
| a node group | `POST /node/nodeGroup/v2` — a bare `String` body, which Jersey reads as TEXT whatever the media type: JSON-encoding the name stored it with its quotes | `NodeGroupEditPresenter` (Node Groups › select › Edit) |
| two tab sessions | `POST /tabSession/v1`, each naming two seed documents — `Open Tab Session` is disabled with none and opens the ONE without asking; the chooser needs two | `TabSessionChooserPresenter` |
| one annotation tag of each type | `createAnnotationTag` — Status, Label, Collection, and a Comment with its canned text | `AnnotationTagEditPresenter` (the four tag screens › select › Edit) |
| an annotation with a COMMENT entry | `create` writes no comment entry whatever its `comment` field says (AnnotationDaoImpl); the comment is a `change` of type `comment`, the way the Create dialog's own box sends it | `CommentEditPresenter` (the entry's mousedown menu › Edit Entry) |
| a visualisation with an asset | its own document in the seed folder + `visualisationAssets/updateNewFile` + `saveDraftToLive` (so it is not left dirty) — none of the thirty visualisations here had one | `VisualisationAssetsEditAssetDialogPresenter` (Assets › select › Rename) |
| a Git repository with a URL | every GitRepo here is a content pack, and `Push to Git` is hidden on those, on a repo with no URL and on one pinned to a commit; the URL is `git.invalid` by design | `GitRepoCommitDialogPresenter` — the commit dialog opens BEFORE any push; the push is its OK |
| a search result store | `POST /query/v1/search` over the Example Index; a search run from a fetch is not a tab, so `destroyOnTabClose` never fires and the store lives its 24h default. In-memory, so gone after a node restart — the one item re-seeded whenever the list is empty | `ResultStoreSettingsPresenter` (Search Result Stores › select › Store Settings) |

**The other half were the walker's, and the plan had misfiled them as data.** Read against the
source rather than the ledger's one-liners:

| precondition | how the walk establishes it | what it opened |
| --- | --- | --- |
| **a SCREEN's sub-tabs** | the sub-tab descent ran for `editor` nodes only. Data Receipt Rules is Rules / Fields / Documentation and Data Retention is Rules / Impact Summary — screens, so their second tabs had never been clicked. Screens now descend like editors. | `FieldEditPresenter` (`New Field`), `EditExpressionPresenter` (Impact Summary › `Set Query Filter`) |
| **a DIALOG's sub-tabs** | a dashboard component's Settings is Basic / Conditional Formatting / Selection Filter (the Query's: Basic / Selection Query). Three things had to give: the tabs are read from the TOPMOST popup only; nothing is cleared before a tab click (`clearOverlays` closes the dialog, and so does `dismissMenus` — its Escape is answered by the dialog); and a tab's own OK/Cancel closes the dialog, so the route is replayed before the next tab when no dialog is up. And a tab inside a dialog is named by the TAB as well as the caption in its signature: Conditional Formatting and Selection Filter both offer Add / Edit / Remove over the same strip, and the second read as the first. | `SelectionHandlerPresenter` (`Add New Selection Handler`), the Filter dialog's `Include Exclude` › `Add Dictionary` |
| **a clickable LABEL** | a GWT `Label` with a click handler is a div; nothing in the markup advertises it. `CLICKABLE_LABELS` (`.itemNavigator-label`, `.characterNavigator-label`, `.stepLocationLink`) are offered by the survey as `selector:` affordances wherever one is visible — which retired the stepping seeds' `extra`. The labels were never "hidden by a small stream": `refreshControls` shows them for any text stream. | `ItemSelectionPresenter` (`Select Record`), `CharacterRangeSelectionPresenter` (`Set Source Range`) |
| **a `data(…)` link cell** | `from "Example Index" limit 5 select StreamId, data('Open', StreamId, 1, 1, 1, 1, 10, 1, 'preview', 'tab')` — the cell renders `<u link="…">`, DataGridSelectionEventManager fires HyperlinkEvent for it, and `displayType=tab` makes ShowDataEvent a STROOM_TAB. Nothing to seed; the tab the link opens is walked as an editor the seed names. | `DataPreviewTabPresenter` — the one `event` door |
| **a pipeline CHOSEN for Process** | the chooser's tree shows folders until its quick filter is typed into (a new `fill` step, which waits for the list to CHANGE before settling — a count read before the answer arrives is stable at the wrong number), and its rows are `.explorerCell`, not `.gwt-TreeItem` (`select-tree` takes `selector` + `match`). | `ProcessorLimitsPresenter` (`Process Search Results`) |
| **a store ROW selected** | three walker defects in one seed: the menu step's `dismissMenus` pressed Escape after every leaf, and a leaf that opens a DIALOG (Search Results, Find, About…) closed on it — so every such leaf surveyed as the bare screen and its dialog was reached only round the long way, through the chrome walk's `Show Menu`; `select-row` clicked the first text cell, and the owner column is a UserRef link cell (`.userRefLinkContainer`) that CONSUMES the click; and the dialog repaints when its LAST node answers (node2a is down here: connection refused), so a selection made on the first paint was gone by the survey. `select-row` now polls for a row, lets the grid settle, and prefers a cell with nothing interactive in it. `Delete Store` joins SELF_DESTRUCTIVE: the guard allows `result-store/destroy` as a client's own lifecycle, and its Confirm › OK destroyed the seeded row. | `ResultStoreSettingsPresenter` |

**Two miner rules, each from a door that had been opened and not credited.** A builder site whose
caption is the ENCLOSING METHOD's parameter (`chooseTabSessionThenAccept(final String caption, …)`
→ `.caption(caption)`, called with `Select Tab Session To Open:` and `… To Delete:`) now takes its
captions from every same-file call of that method — the `show(…)` helper form already did; five
edges gained captions (the two tab-session choosers, the two selection-handler dialogs, `Add User
Group`, `Select User Or Group`), each checked in the source. And a menu leaf whose builder is a
same-file METHOD (`addMenuItem(KEY, createMenuItem())` — the three receive-rules plugins) is now
mined, which gave Data Retention, Data Receipt Rules and Content Templates their recipes; without
one, `New Field` — four presenters application-wide — had no parent to be narrowed by.

**The corpus is stale twice over and was re-recorded**: it dated from 2026-08-16, before two
upstream syncs, and seeding changes what the instance answers.

**The 7 left**, and what each is:

| class | doors |
| --- | --- |
| a mutation the guard blocks | `AiAttachmentData` / `DownloadChat` (`createChat`), `DocRefSelection` (a multi-document save) |
| this instance's configuration | `PathwayEdit` / `ConstraintEdit` (a node URL — the Pathways editor sits under `Remote node '' has no URL set`), `CredentialsManager` (`CREDENTIALS` permission + an authenticated content pack), `QueryInfo` (`stroom.ui.query.infoPopup.enabled`, which would make EVERY search prompt) |

A triage note for the guard: `POST /gitRepo/v1/areUpdatesAvailable` is blocked as a mutation and is
a read (it asks the remote whether there is anything to pull).

## Getting to full coverage

**The denominator is 414 presenters** (`oracles/reachability-graph.md`: Screen/Dialog + Shared), of
which 397 are non-abstract and **201 are DOORS** — a dialog, a screen or an editor you can actually
navigate to. The other 190 are embedded panels, covered when their parent is. Reaching a screen is
not the same as testing it, so the plan separates the two, and `capability-specs.md` supplies the
expectation for 320 of them (101 have none).

> **Re-based on GWT `783d672e` (2026-09-04.)** The figures below that read 414/424 are the pre-re-base
> record and are left as written: the artefacts they were measured against turned out to be two syncs
> stale (generated 2026-08-17/18, before the `cf2b9d53` sync), so three presenters in the old
> denominator had already been deleted upstream.
>
> **The ledger's denominator then changed**, from "whatever `capability-specs.json` mined" to the
> reachability list, because the spec list was wrong in both directions: 121 of its 421 entries were
> plugins, REST clients, view impls and cells that no crawl can reach as a surface, and a form-only
> presenter could be missing from it entirely. Coverage now reads **DOORS 89/201 = 44.3%**, with
> 93/397 across all presenters — the same crawl data, counted against the right list. The old 20.3%
> was a percentage of the wrong thing in both the numerator and the denominator. Attribution work
> later the same day took DOORS to **96/201 = 47.8%**; see the P2 attribution entry below.

- **P0 — stop lying to yourself.** ✅ **DONE 2026-08-20.**
  - `compare/lib/credentials.mjs` resolves the user from `STROOM_USER`, warns when `USER` is merely
    the shell's own login name, and refuses to start with no password rather than failing four
    frames later inside `locator.fill`. Wired into all 16 suite scripts.
  - `gwt.assertSignedIn` proves we are inside the app rather than looking at the sign-in form, and
    `crawl.mjs`'s `reset()` calls it on **every** node — which is what catches a session lost
    mid-run. A bad login now fails in 19s with the reason instead of grinding at 46s a node: the
    landmine invocation is back to a 1.58s reset.
  - `crawl.mjs` records `ms` per node and reports `wallMs` / `navigations` / `msPerNavigation` /
    `msByKind`. First measured run: **6.3s per navigation**, against the 6.7s this plan predicted.
  - `stroom-gwt-suite/coverage.mjs` writes the ledger. **Baseline: 47/424 presenters reached — 11.1%**,
    against 825 nodes across four crawl files. That is the honest number the "173 places" of the
    last run could not give, and the number every later phase moves.
  - It also reports 4 presenters the crawl names that the miner does not
    (`BrowseAnnotationPresenter`, `ContentStorePresenter`, `NavigationPresenter`, `NodePresenter`) —
    a miner gap, and the kind of thing a denominator only surfaces once it exists.
- **P1 — the four speed fixes above.** ✅ **DONE 2026-08-20.** Measured on a fixed workload
  (Monitoring, 15 nodes, 58 affordances) and verified affordance-by-affordance against a baseline —
  an optimisation that changes an outcome is a bug in a test suite, so every one of these was
  required to leave all 58 results identical.

  | fix | measured | verified |
  | --- | --- | --- |
  | 1 restore instead of rebuild | 466.7s → 359.3s (23%), 73 → 45 navigations | 0 of 58 changed |
  | 2 measured waits | with (1): → 241.1s, **48% total** | 0 of 58 changed |
  | 3 sibling tab reuse | 556.8s → 547.4s (2%) on the doc crawl | 0 of 111 changed |
  | 4 parallel shards | **3.0×** on 3 workers, no contention | identical to serial |

  Together: **~48% per area, times the worker count.** The ~9.5h full pass becomes ~1.6h at three
  workers and under an hour at six — the target this plan set.

  **Fix 3 under-delivered and is kept anyway**, honestly labelled: the affordance loop dominates
  (111 affordances against 12 nodes), and after most clicks the crawler no longer knows where it is,
  so the sibling shortcut rarely applies. It targets the node class the coverage work is heading into
  — editor sub-tabs are the largest at 121 — so it should pay better there than it did here.

  **What the measurements refuted, which is the real content of this phase:**
  - Restoring after *any* click moved five outcomes. The signature covers dialogs, buttons and the
    fingerprint but not a grid's sort, so a sorted grid leaked into the next affordance.
  - Four explorer-chrome affordances still came back `unclickable` from a reused page and the cause
    was never found (leftover modal glass was ruled out with `probe-glassleak.mjs`). The fast path is
    therefore optimistic, and any failed click from it is discarded and re-asked from a rebuild.
  - Returning from `classify()` as soon as a dialog or menu appeared saved another 20% and turned
    `Main Menu` into `nothing`: a classifier may not sample before the thing it classifies has
    finished happening.
  - Waiting only for the menu to close made `Refresh` on Monitoring → Nodes report `grid-reordered`,
    deterministically, because the grid was still filling. Route waits must wait for READY, not
    PRESENT — which is what the old blind 1.5s had been doing by accident.
- **P2 — breadth.** ✅ **first pass done 2026-08-20**, three shards in parallel (`recipes:doc`,
  `recipes:menu`, `Navigation`), 1,241 nodes, no cap except `recipes:doc` which hit 400 with more
  still queued.

  **Coverage 11.1% → 17.5%, and none of that came from crawling.** The first pass moved the number
  by *zero*: a node carries a `presenter` only when a recipe seeded it, so editors and their
  sub-tabs were named and the **156 dialogs and 60 alerts** of a single doc shard were not — and
  dialogs are exactly where the uncovered presenters live (112 classes show themselves as one).
  Three hours of crawling had been reaching places it could not name. `coverage.mjs` now also
  attributes a node by its dialog captions via the miner's own `captionOf`, which found 34
  presenters that were already reached and scoring nothing. **49% of nodes are still anonymous.**

  Two defects the run itself surfaced, both invisible before P0:
  - **The crawl signed itself out.** `Sign Out` in the User menu killed the session, and the 16
    Navigation and 15 recipes:menu nodes after it all failed. Fixed twice over — `reset()`
    re-authenticates, and sign-out affordances are `skipped` rather than clicked. Re-running gave
    Navigation 45 places instead of 39, and recipes:menu 87 instead of 79.
  - **`no menu leaf "User Permissions Report"`** — a recipe naming a leaf that does not exist in the
    running app. Miner artefact or missing registration; needs a human.

  **Then the anonymity itself was attacked, 17.5% → 19.8%.** Two changes, no new crawling:
  - A screen reached by walking the menu is the presenter that leaf opens — the recipes say so — so
    the ledger attributes by `group > leaf` too. And a **menu node is a route, not a screen**: GWT
    raises one *from* a presenter rather than being one, so counting all 396 as anonymous conflated
    a hole in the miner with a node that was never going to name anything. The real gap was 25%, not
    49%.
  - `name-captions.mjs` inverts the mining. `build-route-recipes.mjs` reads one shape
    (`ShowPopupEvent…caption("…")`) and misses locals, `setCaption` on a child, runtime
    concatenation and helper arguments. Chasing those with more regexes is how a miner accumulates
    false positives, so instead it takes the captions the crawl ACTUALLY SAW and looks each up in
    the source: unique hit names it, several candidates is reported AMBIGUOUS and named by nobody.
    14 of 22 resolved, 12 distinct presenters.

    Its first version had a false positive worth remembering: `New Folder` matched
    `.text("Add New Folder")`, a menu item in an unrelated presenter. Fixed by matching the whole
    quoted string and skipping `.text(`/`.title(`/`.tooltip(` — a caption is shown, not labelled.

  **Then attribution was rebuilt around the ROUTE, and the number went DOWN — 19.8% → 18.9%.**
  A caption is a label, not an identity: `Settings` belongs to two presenters, `New` to three,
  `Edit Rule` to three, and `Confirm` is every delete dialog in the application. Naming a dialog by
  its caption alone was picking a winner among them, and 29 node-attributions were coin-flips.

  What the ledger does now, strongest evidence first:
  1. **The route.** The crawl clicked a known button on a known screen, and the miner already
     records which presenter each screen shows from there (`shower` → presenter, 78 edges). No
     inference: 206 nodes named this way.
  2. **The route narrowed by a caption** — where a parent shows several dialogs, the caption picks
     between *its* children. This is the job a caption can actually do: 21 nodes.
  3. **A caption alone, and only when it names exactly one presenter** — 401 nodes.
  4. Otherwise **unnamed**, and printed: `Settings` (15×), `Filter Streams` (6×), `New` (4×) …

  Getting there took two wrong turns, both worth recording. Gating the caption pass on "the route
  already named something" dropped `NamePresenter` and `HttpTlsConfigPresenter` — dialogs **stack**,
  so `['Query Favourites', 'Create New Favourite']` is one node showing two, and the route names
  only the outer one. And collapsing `captionOf` to one presenter per caption hid the collisions
  entirely; it has to be a SET, or the ambiguity is invisible rather than absent.

  **Then the miner's edges were widened, because the route can only be as good as the edge list.**
  `build-route-recipes.mjs` recorded an edge only when a LITERAL caption sat next to the
  `ShowPopupEvent.builder(X)` call, so 68 of 154 sites were discarded for want of a label — which is
  why the ledger kept falling back to captions for dialogs whose parent was known all along. Edges
  are now mined independently of captions: 78 → 142 distinct `shower → presenter` pairs.

  Three things that took measuring rather than reasoning:
  - **A greedy tail was swallowing the next site.** The 400-character window after `builder(` was a
    consuming group, and `matchAll` resumes after the match, so two builder calls close together
    lost the second. `CommonAlertPresenter` is exactly that shape — a `Confirm` branch then an
    `Alert` branch — so `Alert` was never mined and **all 169 alert nodes the crawl reached were
    unnameable**. A lookahead fixed it: anonymous alerts 112 → 1.
  - **A parent screen rarely shows its own dialogs.** `FeedPresenter` opens nothing; it embeds
    `FeedSettingsPresenter` and `MetaPresenter`, which hold the calls. Joining through the specs'
    `embeds` is what makes the edge list and the crawl's attributions meet at all.
  - **Ungated, edge attribution explained everything.** Applying `shower → presenter` to any node
    named 1,119 of 1,241 by "route", including editor sub-tabs attributed to whatever dialog their
    editor happens to open. An evidence class that explains everything is not evidence; it is now
    gated to popups opened by a button or a context menu, and names 79.

  Net: anonymous presenter-bearing nodes 199 → 184, ambiguous captions 29 nodes → 18, and the
  attribution is route-led where it can be.

  **Document surface finished, uncapped: 612 nodes, 307 distinct places** (the capped run reached
  216), 291 duplicates, zero sign-outs, and P1's restore reused the page for 406 of 1,176
  navigations. **Coverage 20.3%** (86/424). Ledger artefacts live under the git-ignored
  `stroom-gwt-suite/out/`, so the numbers here are the record — regenerate with `coverage.mjs`.

  Still open here:
  the 14 click-timeout `unreachable` nodes; **201 anonymous dialogs** — the biggest single class is
  captions built at runtime from a document's name (`Save '…' as`, `Copy Test Dashboard`,
  `Rename …`), 34 of which `name-captions.mjs` reports as matching no source literal at all; and 31
  anonymous screens. 267 of 976 presenter-bearing nodes (27%) remain unnamed.
- **P2 (attribution, 2026-09-04) — every caption the crawl has ever seen is now named or honestly
  ambiguous.** The 34 captions that matched no source literal were not exotic; they were four idioms
  the miner did not read:

  | idiom | example | what it named |
  | --- | --- | --- |
  | an EVENT with exactly one handler | `ShowCreateDocumentDialogEvent.fire(this, "Save '" + name + "' as", …)` → `CreateDocumentPresenter` | 98 `Save '…' as` nodes + the `New <type>` family |
  | a caption built by concatenation | `"Copy " + node.getDisplayValue()` | Copy / Move / Rename / Edit Tags on |
  | a caption held in a local, incl. both branches of a ternary | `final String caption = single ? "Edit Tags on " + n : …` | the explorer's tag dialogs |
  | a caption passed to the dialog's own `show(…)` | `indexFieldEditPresenter.show("New Field", …)` | `New Field` / `Edit Field`, across four field editors |

  A caption with a variable part is mined as a **template** — `Copy …`, `Save '…' as` — which joins
  to what the crawl saw because the ledger already normalises a document's name out of a caption.
  **An exact caption beats a template**, being the more specific reading of the same source: `Rename
  Tab` is mined exactly from the dashboard and also matches NameDocumentPresenter's `Rename …`, and
  unioning the two made a correctly-named dialog ambiguous.

  Two guards, because an event bus explains everything if you let it: the handler must **show
  itself** (which drops `RefreshExplorerTreeEvent`, `FocusExplorerFilterEvent` and
  `HighlightExplorerNodeEvent`, all handled by NavigationPresenter, which redraws rather than opens),
  and `ShowPopupEvent` is excluded from event mining because it is the popup framework's own event,
  already mined properly, and one presenter implements its Handler.

  **The `New …` template is deliberately left ambiguous.** It has two sources — the explorer's create
  dialog and the visualisation assets dialog, which captions itself the same way through a
  `ShowPopupEvent.Builder` it is handed rather than one it raises. Mining that second shape cost 35
  node-attributions and no coverage (CreateDocumentPresenter is reached 152× via `Save '…' as`), and
  it is the right trade: naming them would have been a coin-flip between two real presenters.

  | | at re-base | after |
  | --- | ---: | ---: |
  | show edges (`shower` opens `presenter`) | 78 | **217** |
  | captions mapped to a presenter | 74 | **147** |
  | captions nothing could name | 34 | **0** |
  | anonymous presenter-bearing nodes | 267 | **112** |
  | …of those, dialogs | 201 | **54** |
  | DOORS reached | 89/201 | **96/201 (47.8%)** |

  What is left is not caption work: 31 anonymous screens, 10 editor-tabs, and 54 dialogs of which the
  bulk are the honestly-ambiguous `New <type>` family plus `Settings` (16×) and `Filter Streams` (3×)
  — all of which need the ROUTE, not another idiom.
- **P3 — the known holes**, from the table above: ~81 nested dialogs, ~10 explorer context menus,
  post-action states. Generate the worklist from toolbar buttons and context-menu items rather than
  writing a recipe each.

  **The context menu is not ~10 menus, it is six SHAPES** — read out of
  `DocumentPluginEventManager.addModifyMenuItems`, which is where the gates live:

  | shape | what is different about it | how the crawl reaches it |
  | --- | --- | --- |
  | single document | the baseline | `EXPLORER="<a doc>"` |
  | single **Feed** | no Rename, no Copy — feeds are special-cased (#2912, #3048) | `EXPLORER="<a feed>"` |
  | **Folder** | its New submenu is the whole document-type list | `EXPLORER="<a folder>"` |
  | **System root** | the tree root, not a document | `EXPLORER="System"` |
  | **multi-selection** | a DIFFERENT menu: `Remove Tags` appears only above >1 updatable item, and Favourites, Info, Rename, Dependencies and Permissions all disappear (`singleSelection` gates them) | `EXPLORER_MULTI="A|B"` — click, ctrl-click, right-click |
  | **content tab** | its own menu entirely — Close / Close Others / Close Saved / Close All / Save / Save All / Locate / Add To Favourites, built from `event.getTabData()` | seeded automatically by a `DOC=` crawl |

  The last two needed new crawl support (2026-09-04): `clickByText` + `ctrlClickByText`, an `also:`
  list on a `context` route step, and a `tab-context` step. Both are SEEDS rather than affordances
  for the same reason a right-click is — nothing in the markup advertises them, so no amount of
  surveying finds them.

  **All six observed, and the source's predictions hold:**

  | shape | observed | against the source |
  | --- | --- | --- |
  | single document | 14 actions, 0 disabled | the baseline |
  | **Feed** | 12 actions, disabled = **Copy, Rename** | exactly `isCopyEnabled`/`isRenameEnabled`'s `&& !hasFeed` (#3048, #2912) — the first time a mined gate and a live observation have met |
  | Folder | 14 actions, 0 disabled | same as a document, plus its own New submenu |
  | System root | 5 actions, **6 disabled** (Info, Edit Tags, Copy, Move, Rename, Delete) | not a document, so the whole modify block is dead |
  | **multi-selection** (2 rows) | **8 actions, 3 disabled** (New, Info, Rename) — with `Remove Tags`, `Add Tags to 2 Documents`, `Copy Multiple Items`, `Move Multiple Items`, and a plural `Copy As` (lines / comma-delimited) | `Remove Tags` appears exactly as `updatableItems.size() > 1` says. One nuance the source read got slightly wrong: `singleSelection` items are added DISABLED, not omitted |
  | content tab | 9 actions, 4 disabled (Close Tabs to the Right, Save, Save All, Move Last) | more items than `addModifyMenuItems`' neighbours show — there are `create*MenuItem` sites beyond the block that lists them |

  **The multi-selection shape was wrong first, and the count did not show it.** Ctrl-clicking the
  extra row and then right-clicking the target reset the selection back to one row, so the crawl
  recorded 14 actions with `Rename` present and no `Remove Tags` — the single-selection menu wearing
  a multi-selection label, which is worse than not covering the shape at all. It was caught by
  reading the ITEMS: the dialogs it opened were named after one document. Sequence is now click →
  ctrl-click → right-click inside the selection.

  Also fixed while choosing targets: an explorer row's `textContent` includes its type icon's inline
  SVG, and two icons carry a `<style>` block — so `Seed Elastic Index` read as
  `.st0{fill:#4A4B4C;} … Seed Elastic Index` and could never be matched by its own name. Row matching
  now compares against the label with `svg`/`style` stripped, which is what a user sees.

  **Post-action states were the one outcome the crawl could see and refused to walk into.** `changed`
  means the screen is no longer the screen that was surveyed — a search has run, a grid has filled, a
  panel has appeared — and only `dialog`, `alert` and `menu` were ever enqueued as nodes. `POSTDEPTH`
  (default 1) now enqueues them, bounded because a post-action state is the one outcome that can
  never be restored, so each costs a full rebuild. `POSTDEPTH=0` is the old behaviour exactly.

  A limit worth stating rather than discovering later: the crawl runs under `attachReadOnlyGuard`, so
  post-action states that require a MUTATION to exist — after Save, after Delete, after an import —
  are not reachable by it at all. Those need the guard off against seeded scratch content, which is a
  separate decision, not a crawler setting.
- **P4 — behaviours, not just reachability.** This is the half that is currently missing. The
  capability specs are the oracle: expected toolbar buttons, disabled-at-rest, grid columns,
  `PopupType`, REST resources. Diff observed against expected per presenter; every mismatch is either
  a suite gap or a GWT finding, and both are worth having. 77 of 412 presenters have that diff today.
  P4 is also where new upstream changes get found rather than hand-read: the `Settings` tab on
  Analytic Rule, Report's AI Summary fields and `Follow Redirects` are all spec-visible changes the
  diff should surface by itself.
- **P5 — only then turn the artefacts on.** `DEPTH=dom` / `pixel` and the replay corpus cost time and
  disk per node; capture them once the worklist is stable, not while it is still churning.

The ordering matters: P5 before P1 is how a 9-hour pass becomes a 20-hour one.
