# From reaching the GWT UI to testing it — the behaviour plan

Companion to [COVERAGE-PLAN.md](./COVERAGE-PLAN.md) (how every screen and dialog came to be
reachable) and [BRANCHING-PLAN.md](./BRANCHING-PLAN.md) (the graph the walk descends). This plan is
about what happens *after* a door is opened: whether the thing behind it does what the source says
it does. The subject is the **GWT UI**, tested on its own terms.

> **Status (2026-09-22, afternoon): B0 done for the round (1,111 alerts read, 3 exceptions, all
> filed); B1 at 32.9% / presenters 47.3%; B2's seven checkers in (sort, pager, Refresh, OK, Filter,
> toggle, selection-vs-spec) with a tick-row rule; five bugs filed this round (#37–#41); B3's
> first row in.** Every number below was measured from the repository on the day; the estimates are the
> author's and are marked as such. Progress is logged at the end (§ 9).

## 1. Where it stands

Reach is done, near enough. **DOORS 182/189 (96.3%)** — the seven left are guard-blocked mutations
or this instance's configuration (COVERAGE-PLAN.md § pass 6). Reaching a screen is not testing it,
and the ledger has always said so. What we hold, and have not used:

| evidence | count | what it is | what it is NOT |
| --- | ---: | --- | --- |
| affordance observations, `stroom-gwt-suite/out/crawl/*.json` | **13,172** | one click on one labelled control at one place, and the CLASS of what happened: `changed` 4,652 · `nothing` 3,603 · `dialog` 1,672 · `closed` 1,211 · `alert` 720 · `menu` 549 · `unclickable` 456 · `grid-reordered` 189 · `vanished` 65 · `skipped` 43 · `grid-rows` 12 | a check. `changed` says buttons appeared; it does not say the right ones did. `alert` says a dialog captioned Alert opened; the walker does not keep its TEXT (that is why gwt-bugs #36 needed a probe) |
| blocked mutations | **442** | clicks whose request the read-only guard aborted (`POST /api/…/create`, `PUT`, `DELETE`) | anything about what the mutation would have done — every OK path is untested |
| capability specs, `oracles/capability-specs.json` | **421** presenters; 218 declare toolbar buttons, 60 grid columns, 102 a PopupType, 200 name server resources; `SvgPresets` gives disabled-at-rest for 72 presets | an oracle mined from the source for what a presenter SHOULD offer; **132** have crawl data to diff against today, and the diff is read as a test of the miner, not of the UI |
| the corpus, `stroom-gwt-suite/corpus/default` | 185 screens, 1,593 API exchanges, re-recorded 2026-09-21 | pixel baselines for screens as they OPEN; nothing about what a click does |
| `stroom-gwt/ISSUES.md` | **41** open entries | defects found by reading the code, by probes, by the workbench's stories and by the walk (#36) |

The walker's model is one click deep by design: click, classify, pop, next. Behaviour is what a
click DOES, and that needs (a) something to compare the outcome against and (b) sequences longer
than one click. Both exist in pieces — the specs are (a) for buttons, directed seeds are (b) — and
neither has been turned into a test.

## 2. What "behaviour" means here, and the denominator

A behaviour in a GWT presenter is a **handler**: the code that runs when the user does something.
Counted in `stroom-core-client`, `stroom-core-client-widget` and `stroom-app-client` on
2026-09-21, by registration idiom:

| idiom | sites | fired by | the walk today |
| --- | ---: | --- | --- |
| `addClickHandler` | 337 | a click on a button / label / cell | **yes** — this is what the walk does |
| `onHideRequest` (OK / Cancel branches) | 154 | a dialog's OK or Cancel | Cancel yes; **OK only when the guard allows it** |
| `addSelectionHandler` / `addSelectionChangeHandler` / `addDataSelectionHandler` | 119 / 16 / 55 | a row, tree item or tab selected | partly — `select-row`, `select-tree`, `select-element`, the generic selection rule |
| `addValueChangeHandler` / `addChangeHandler` | 113 / 45 | a text box, select, tick box or editor changing value | rarely — `set-ace`, `set-select`, `fill` exist as route steps only |
| `addDomHandler` (mouse, key, focus) | 56 | mousedown / keydown / focus on a specific element | `entry:` mousedown only |
| `addKeyDownHandler` | 33 | a key — Enter to submit, Escape to close, arrows in grids | **no** |
| `addDirtyHandler` | 30 | an editor becoming dirty | incidentally (`discardIfAsked` answers the Confirm) |
| `addContextMenuHandler` | 19 | a right-click | `context`, `tab-context` (explorer + content tabs only) |
| `addCloseHandler` / `addDoubleClickHandler` / `addMouseDownHandler` | 8 / 6 / 5 | closing a tab, a double-click, a mousedown | `dblclick` exists |

**~1,000 handler sites across 197 presenters.** That is the behaviour denominator, and it has the
property DOORS had: it comes from the source, so a handler nothing fires is a gap and not noise. The
ledger becomes **handlers exercised / handlers declared**, per presenter, with each unexercised one
classified by what would fire it — the same shape as `door-preconditions.md`.

Two cautions before the number is trusted. A registration is not a behaviour of its own: a
`FormGroup` help icon's click handler is chrome repeated 200 times, and one `addClickHandler` in a
base class (`ButtonPanel`, `MyDataGrid`) is inherited by fifty presenters. The miner has to
attribute by DECLARING class and let inheritance be the ledger's problem, as the specs already do
("whose spec includes inherited buttons"). And 154 `onHideRequest` sites are two behaviours each —
OK and Cancel — and the OK branch is where the bugs are.

## 3. The phases

Ordered by how soon each finds a GWT bug, and by what each needs from the phase before it.

### B0 — the alerts we have already caused (days; bugs on day one)

720 `alert` outcomes were recorded with the caption `Alert` and nothing else. The walker's own
`discardIfAsked` already reads a Confirm's body; `readDialogs` returns captions only.

1. **Keep the text.** Record `dialog-content` for every `alert` outcome (and for `dialog` outcomes,
   the first 200 characters — validation dialogs are dialogs too). Strip `Show Detail` / stack
   traces into a separate `detail` field, because the stack IS the finding.
2. **Classify**, with the rule written down and every rule that misfires corrected in the rule, not
   the data:
   - `guard` — the walker's own aborted request (`blocked` is non-empty); the body says
     `Request aborted` or the guard's message. Not a bug.
   - `validation` — a message the presenter composed on purpose (`… is required`, `must be`,
     `already exists`, `Are you sure`). A behaviour, and B3's raw material.
   - `exception` — `500`, `Cannot invoke`, `NullPointer`, `ResponseException`, `Unable to`,
     `Internal Server Error`, `is null`. **A GWT bug candidate**, route attached.
   - `environment` — this instance, not the code: `Connection refused` to `localhost:10080`
     (node2a is down), `Remote node '' has no URL set` (Pathways config).
3. **Re-run** the shards that produced alerts (Monitoring, Administration, Security, the doc
   recipes, the directed groups) with the text kept — about 3 hours of browser across parallel
   shards — then triage every `exception` into `stroom-gwt/ISSUES.md` with a repro that is the
   node's route.
4. **Make it standing:** `coverage.mjs` gains an *alerts* section (counts by class, the exception
   list by route), so a new exception in a future run is a visible delta and not a line in a log.

*Exit:* every alert classified; every `exception` either in [ISSUES.md](../ISSUES.md) or shown to be
`environment`. Expected yield (author's guess): 3–8 new entries — #36 was found by the first probe
that read an alert, and 719 have never been read.

### B1 — the handler ledger (a week)

The denominator of § 2 as a generated artefact, `oracles/handler-inventory.{json,md}`, built by a
miner alongside `build-capability-specs.mjs`:

1. **Mine registrations** per declaring class: idiom, the widget it is on (field name, and the
   button's `SvgPresets` title or the `@UiField` label where the view declares it), the handler's
   body's first statement class (fires an event / opens a popup / calls a resource / sets state).
2. **Join to the walk.** A click handler on a button the specs already name joins on
   `(presenter, title)` to the affordance observations; a selection handler joins to the generic
   selection rule's post-action nodes; `onHideRequest` joins to a dialog node's OK / Cancel
   affordances. Everything that joins is *exercised*; the outcome class travels with it.
3. **Classify what did not join**, by what would fire it — the door-preconditions treatment:
   `value` (needs a value typed or chosen), `key` (a keyboard event), `ok-blocked` (the guard),
   `inherited-chrome` (help icons, pagers — covered once), `dead` (a handler on a widget nothing
   shows; the miner found a live menu item behind a dead GIN binding before, #34).
4. **Report** `handlers exercised / declared` overall and per presenter, in `coverage.mjs`'s
   output next to DOORS.

*Exit:* the number exists, is explained per unexercised handler, and moves when B2–B4 land.

**First reading (2026-09-21, `oracles/handler-inventory.md`): 438/1,530 exercised (28.6%); in
presenters 316/804 (39.3%).** The miner counts more than § 2's grep did because it reads menu-item
`.command(…)` (213) and every `@UiHandler`, and splits `onHideRequest` into OK and Cancel. By kind:
click 158/474, command 97/213, ok 58/98, cancel 77/98, selection 31/159, value 0/305, key 0/78.
The unexercised by why: `unreached` 337, `needs-value` 305, `unlabelled` 152 (a miner gap: the
receiver's label is not in the file — a view's `@UiField`, a grid's cell), `widget` 119 (declared
in a widget, inherited everywhere, counted once), `needs-key` 78, `view` 52, `ok-blocked` 18.
What the `unreached` click list says, read down (§ handler-inventory.md): almost all are
**Edit / Remove / Up / Down on lists that are EMPTY here** — conditional-formatting rules,
selection handlers, index fields, notifications, execution schedules, pathways, constraints,
annotation and event links. Those lists fill CLIENT-SIDE from their own `Add` (no write until
Save), so the fix is a walker rule, not data: after `Add › OK` on a client-side list, walk the
selection-gated buttons — the same shape as pass 4's `add Reference Data Filter`. That is B2's
first job, and it moves `selection` as much as `click`.

### B2 — checkers: what `changed` has to mean (two weeks)

Generic, in the walker, keyed on the CLASS of control clicked — never a per-screen script. Each
checker has an oracle that is either the DOM before the click or the source; a failed check is
filed as `walker-bug` or `gwt-bug` after triage, never left as a count.

| control | checker | oracle | today's evidence it is needed |
| --- | --- | --- | --- |
| a sortable header | rows are ordered by that column, in the direction the sort icon shows; a second click reverses; Ctrl-click adds a secondary sort (`nextSortWithDefault`) | the cell texts before and after, parsed by the column's type (`headingBuilder` in the spec says text / number / date) | 189 `grid-reordered` outcomes, never checked for order |
| a pager button | the "x to y of z" label moves by the page size; First / Last disable at the ends; the rows change | the pager label before/after; `PageRequest` length from the spec's resource | 84 grids were once found that "never said how many", and pager labels have never been asserted |
| Filter › OK | rows after are a subset of rows before (or the total falls); an empty filter restores; the quick-filter syntax help lists what the field parser accepts | the grid before/after | `Filter Streams`, `Filter Documents`, 30 `Filter '…'` column dialogs |
| select a row | the set of buttons that became enabled equals the spec's disabled-at-rest set for that presenter; deselect (Ctrl-click) disables them again | `capability-specs` `disabledAtRest` | the generic selection rule records what enabled; the spec says what should have |
| a tick box / tri-state header | ticking N rows enables the batch buttons; the header cycles none → all → none; the count in `Selection summary` matches | the tick states + the summary dialog's text | the `TickBoxCell` trap (a click checks without selecting) cost a pass; the reverse — batch buttons on a tick — was never checked |
| OK on an empty required form | a `validation` alert, not an `exception`; Cancel discards without a prompt; a dirty editor's close asks | B0's classes; `addDirtyHandler` sites | 154 `onHideRequest` sites; the dirty prompt is already answered by `discardIfAsked` but never asserted |
| a toggle (`InlineSvgToggleButton`) | `.on` flips; the INVERSE label appears; a second click restores the first state exactly | the survey's button set before/after | the INVERSE map exists for re-sync; it is not a check |
| Refresh | the grid's rows are the same set (a read is idempotent); the pager label does not move | rows before/after | 3,603 `nothing` outcomes, most of them Refresh, all unverified |

The last row matters more than it looks: a Refresh that changes the rows on a screen with no live
data is a bug (an unstable sort, a non-deterministic query), and that class has been found twice
before (`screens-that-lie`).

*Exit:* every `changed` / `grid-reordered` / `nothing` outcome carries a checker verdict or an
explicit `unchecked: <why>`; the verdicts are in the ledger; [ISSUES.md](../ISSUES.md) has what failed.

### B3 — value and key handlers: driving the forms (two weeks)

The 158 `addValueChangeHandler` / `addChangeHandler` and 33 `addKeyDownHandler` sites are the part
of the UI the walk has never touched: what a screen does as you TYPE.

1. **Mine the validation** each form applies — `isValid()`, `getValidationMessage()`, regexes,
   `NullSafe.isBlankString(name)` guards before a resource call — into the handler inventory, so
   each form has a source-derived matrix: required fields, illegal characters
   (`ILLEGAL_ASSET_NAME_CHARACTERS`), numeric ranges (`ProcessorLimits` minutes and records),
   name clashes (`getNonClashingLabel`).
2. **Drive the matrix** with the steps that exist (`fill`, `set-select`, `set-ace`) and one new one,
   `key` (Enter, Escape, arrows, Ctrl-A), against the SAME dialogs the walk already opens: blank →
   OK expects the `validation` class; illegal value → its message; valid → OK proceeds (to the
   guard, until B4). Every dialog's OK is thereby exercised twice with different expectations.
3. **The quick filters** are a family of their own: `QuickFilter` fields parse a syntax
   (`type:`, `status:`, quoted phrases, `-` negation) whose help the walk clicks 200 times
   (`Quick Filter Syntax Help`). Mine the field set from each `QuickFilterConfig` /
   `FilterFieldDefinition` and assert that every documented qualifier narrows the grid.
4. **Keyboard**: Enter submits the focused dialog, Escape closes it (`closeDialogs` relies on
   this and has never asserted it), arrows move a grid's keyboard-selected row, `Ctrl/Shift+Enter`
   in the stepping editor triggers a REFRESH step (`SteppingScreen` documents it; GWT's
   `addKeyDownHandler` sites are the oracle).

*Exit:* the validation matrix per form exists as data; every row of it has a verdict.

### B4 — the OK paths: Stage C, mutations that undo themselves (three weeks)

442 clicks were aborted because they would have written. Create, rename, copy, move, delete, save,
enable/disable, assign, terminate — the half of the application that changes something is the
half no walk has tested, and #31 (`create` → 403, four document types) lived exactly there.

**The rule is compensation, not snapshot** (decided 2026-09-21). Every mutation group is a
**cycle that ends where it began**, and the suite only ever deletes what the cycle itself created:

1. **The cycle is the test**: create → read back through the API → edit → save → reload the
   editor → read back the edited value → delete → read back absent. One cycle per document type
   (26) and per non-document entity (users, groups, API keys, annotations, tags, node groups,
   volumes, properties, tab sessions), driven by the routes the walk already has. Verification is
   always a READ-BACK, never the UI's own report; `seed-data.mjs` already knows every read of this
   kind and is the natural home for the verify helpers.
2. **Reversible-by-inverse** actions carry their inverse in the same cycle: rename → rename back;
   enable/disable → toggle back; move → move back; a property change → the old value, fetched
   first; a permission change → reverted; save over an existing document → the original,
   fetched before the edit, saved back.
3. **Irreversible but harmless** side effects are accepted and NAMED per cycle: ids and uuids
   consumed, history and audit entries appended (annotation entries, the activity log). They
   change nothing any screen shows at rest.
4. **Not undoable → not done.** Deleting pre-existing content, `Delete Store` / `Terminate`,
   anything that writes streams, index shards or result stores (the file store and the nodes'
   memory, not the DB), and anything on the admin user's own credentials. Those stay blocked and
   the handler ledger classifies them `irreversible`.
5. **The safety net is a state check, not a restore**: the entity set a cycle touches is listed
   before and after (the `have` reads of `seed-data.mjs`, generalised), and a difference fails the
   run and names what leaked. A `mysqldump` of `stroom_stroom_react` (the `bounceit_stroom-all-dbs`
   container, mysql 8.4) is taken by hand before the FIRST run of each new cycle type, and never
   relied on by the suite. Any other Stroom using the same database stays down during Stage C
   runs.
6. The guard's allow-list gains a third class, `cycle`, enabled only under `MUTATE=1` and only
   for the request shapes a registered cycle makes.

*Exit:* every `ok-blocked` handler in the B1 ledger is either exercised inside a cycle or
classified `irreversible` / `outside-db` with the reason.

### B5 — a suite, not a probe: replay (one week, after B2)

Everything above runs live behind the guard. The suite's contract (README.md) is that a screen
recorded once replays without a Stroom, and behaviour should keep that contract:

1. The walker attaches `lib/corpus.mjs`'s `attachRecorder` under `RECORD=1`, so a directed walk
   records every exchange its routes and checks caused, keyed the way the corpus keys
   (method + path + body hash, minted ids rewritten).
2. `serve.mjs` answers those keys; the same walk under `URL=http://localhost:9099` replays with
   the same verdicts. A verdict that differs between live and replay is a corpus miss (the key
   drifted — a timestamp in a body, a minted id the rewriter missed), which is the class
   `mintedIdMap` / `rewriteMintedIds` already handle for screens.
3. Not everything replays: searches carry `queryKey`s the server mints per run, `Refresh` on live
   screens (Nodes, User Access) answers differently by design. Those groups stay live-only and
   say so; the rest — every editor, every dialog, every grid sort/page/filter — replays.
4. B4's mutation cycles replay too, and they are the *better* case: on the simulated backend a
   create needs no snapshot, because the corpus answers `create` with what it recorded.

*Exit:* `node stroom-gwt/stroom-gwt-suite/run.mjs` runs screens AND behaviours from the corpus; a behaviour that
regresses fails the run by name.

### B6 — the configuration-gated features (a day each, when wanted)

BRANCHING-PLAN.md § Layer 4 enumerated them and the second-corpus mechanism (`NAME=`) that
keeps them from polluting the default baselines: the activity chooser (4 presenters), the splash,
the info popup (`QueryInfoPresenter` — one of the 7 unreached doors), the banner. Each is a
property change under Administration › Properties, a recording under its own corpus name, and a
walk of the screens it changes. B4's property cycle (set → record → set back) makes it reversible.

## 4. How a finding becomes a bug

The rule holds: **never bury a GWT defect in a commit**. `stroom-gwt/ISSUES.md` is the list: each
entry says how sure it is (**Reported** from reading the code, **Confirmed** in the code,
**Reproduced** in a running Stroom), fixed entries move to its Fixed section, and what was checked
and found not to be a bug goes under "Checked and not bugs". What changes for this work:

- every entry carries the **route** that reproduces it as the walk recorded it (`docType` /
  `menu` / `select-row` / … steps), so the repro is a seed anyone can run, not prose;
- the classification rules of B0 and the checkers of B2 are the *only* source of new entries —
  a bug the suite cannot re-find on the next run is not fixed, it is hidden;
- an entry's fix is verified by the same run, so its move to Fixed is evidence, not memory;
- Pathways is experimental and half-built: its known failures are the ISSUES.md entry "The
  Pathways editor can't create a pathway or add a constraint", and its expected gaps are under
  "Checked and not bugs".

## 5. Metrics — what "done" reads as

| metric | source | now | target |
| --- | --- | ---: | --- |
| DOORS reached | `coverage.mjs` | 182/189 | holds |
| alerts classified | B0 | 0/720 | 100%, `exception` count → 0 or filed |
| handlers exercised / declared | B1 | 692/1530 | reported; every unexercised one classified |
| affordance verdicts | B2 | 0/13,172 | every `changed`/`reordered`/`nothing` checked or `unchecked: why` |
| validation matrix rows | B3 | — | every form's rows have a verdict |
| OK paths exercised | B4 | 57/98 ok, 18 `ok-blocked` | every `ok-blocked` handler in a cycle, or `irreversible` / `outside-db` |
| replayable share | B5 | screens only | every non-live group replays with equal verdicts |
| ISSUES.md (gwt-bugs) | all | #46 | grows by what is found; entries carry routes |

## 6. Order and effort (author's estimates)

| phase | depends on | effort | first bug expected |
| --- | --- | --- | --- |
| B0 alerts | — | 2–3 days | day 1 |
| B1 handler ledger | — (parallel with B0) | 1 week | none directly; it steers |
| B2 checkers | B0 (alert classes), B1 (specs joined) | 2 weeks | week 1 (sort order, pager, Refresh) |
| B3 forms and keys | B2 | 2 weeks | week 1 (validation gaps, keyboard) |
| B4 mutations | B3 | 3 weeks | week 1 (#31's siblings) |
| B5 replay | B2 stable | 1 week | none; it is what makes the rest a suite |
| B6 config gates | B4's property cycle | 1 day each | — |

B0 and B1 start together. B2 is the centre of gravity: it is where the 13,172 observations
become tests, and everything after it reuses its checkers.

## 7. Decisions — taken 2026-09-21

1. **Mutations undo themselves** (compensation, § B4), never a suite-driven restore. A manual
   `mysqldump` precedes the first run of a new cycle type only.
2. **Guard allow-list changes** — a blocked READ is allowed when the server source proves it is
   one, cited in the allow-list comment (the stepping standard), and listed in the phase write-up
   for veto. Anything that contacts a remote (git, HTTP) is asked about first.
   `POST /gitRepo/v1/areUpdatesAvailable` is the first on that list: it contacts the remote, so it
   waits.
3. **node2a stays down; the single-node shape is recorded.** Its `Connection refused` alerts are
   the `environment` class in B0, and the cluster screens (Nodes, Caches, Server Tasks, Search
   Result Stores) are tested one node wide, and say so.
4. **Whose bugs they are** — the list is for upstream; the decision per entry (fix upstream /
   replicate / diverge) stays with the owner.
5. **Start** — B0 and B1 together.

## 8. Research before B2 (small, and to be answered by reading, not building)

- the column TYPE per grid column (`headingBuilder` gives the heading; the cell class — date,
  number, `DocRefCell` — is what the sort checker needs to parse by): is it in the specs already,
  or does `build-capability-specs.mjs` need to read the `addColumn` call's cell type?
- `disabledAtRest` completeness: 72 `SvgPresets` constants resolve; the selection checker is only
  as good as that map, so the unresolved presets need listing before B2 trusts it.
- the exact set of `onHideRequest` sites whose OK writes NOTHING (settings applied client-side,
  choosers that return a value) — those OKs are safe under the guard today and are B2's, not B4's.

## 9. Progress log

**2026-09-21.**
- **B0** — `lib/alerts.mjs` reads an alert's message, hidden detail and GWT's own severity icon,
  and classes it; the ledger re-classes every recorded alert under the current rules and lists the
  exceptions by route (`coverage.md` § What the alerts said). Two rule corrections on the first
  read: the alert presenter is one instance whose detail pane keeps the LAST detail (a validation
  warning read after a failed request carried that request's exception), and `missing` /
  `from all nodes` were misfiled. The full re-run is in progress (13 shards, two lanes plus the
  four document shards, ~4h wall). **First exception, first bug:** #37, `Filter Schedules` › OK
  with an empty expression — `ExpressionOperator.Builder.build()` stores `children = null`, and
  `formatISOExpressions` iterates it. Found by the first read of 720 alerts nobody had read.
- **B1** — `build-handler-inventory.mjs`: 1,530 registrations, 444 exercised (29.0%), presenters
  321/804 (39.9%). The join uses the ledger's node attribution (now exported), subclassing,
  one-level embedding, ancestor inheritance, and a marked label-only join for distinctive labels.
- **B2** — `lib/checks.mjs`: sort, pager and Refresh checkers (first smoke pass=10 unchecked=1);
  the offline selection-vs-spec check (22 states, 22 agree); and the first walker rule the
  inventory asked for — a client-side `Add › OK` that fills a grid walks the list with its new row
  (Conditional Formatting's `Edit Rule` and the selection handler's Edit are now reached from a row
  the walk added). Also fixed on the way: nested dialogs' OK / Cancel were clicked on the dialog
  BEHIND (first-in-DOM), so every stacked dialog's buttons had read as unclickable.

**2026-09-22.**
- **B0 done for this round.** Thirteen shards re-run with the body kept (~4h wall across four
  document shards and two lanes); the four document shards alone grew from 657 to 970 reached
  nodes because the nested-dialog fix landed first. Read: **1,040 alerts** — guard 463, validation
  128, info 43, environment 29, **exception 30 (3 distinct)**, 363 still `unread` in the older
  directed files. Every distinct exception is filed: **#37** (Filter Schedules' empty operator),
  **#38** (a Query component's Settings cannot be OK'd when its refresh interval is blank —
  `parseDurationString` returns null and `.intValue()` is called on it; twelve alerts, one route),
  and, from the guard class rather than the exception class, **#39** (413 of the 463 guard alerts
  showed `org.fusesource.restygwt.client.FailedResponseException` as their whole message —
  `DefaultErrorHandler` falls back to the exception's class name when a failure has no message).
  `KNOWN_BUGS` in `lib/alerts.mjs` names them in the ledger so a fix upstream shows as the pattern
  matching nothing. Rule corrections along the way, each from a misfile: the detail pane is read
  only when this alert shows `Show Detail` (the presenter is one instance and keeps the last
  detail); `localhost:<port>` is NOT the environment (every server exception's URL has it — it
  filed #36 as node2a); `Unable to connect to` / `Could not resolve host` / `from all nodes` are.
  Two seed-data notes for later: the seeded Query/Report/Analytic queries error with `Unexpected
  token LIMIT after SELECT` (`fill.mjs` puts `limit` after `select`; StroomQL wants it before),
  and the seeded OpenAI model's `Test Model` resolves `openai.seed.invalid` (by design).
- **B1** re-read after the merge: 459/1,530 (30.0%), presenters 336/804 (41.8%).
- **B2** checks from the runs that had them: pager 59 pass / 5 fail (all five from the checker
  before its two fixes — no captures to re-judge, they clear on the next Security/Tools run),
  refresh 211 pass / 1 unstable (System root › Processors — a live task count), sort 34 pass /
  65 unchecked (0–1 rows, or all values equal). No GWT sort or pager defect so far.
- **B3** row one: on a validation alert after OK, the walker fills the first empty text box and
  presses OK again (not for the explorer's create dialog). `fields` seed group added.
  The Lucene index Fields tab's `New Field` › OK that registered as `nothing` was read
  (`probe-newfield.mjs`): the dialog is left with BOTH buttons disabled and no message —
  **#40**, `onAdd` calls a throwing `write()` with no `try` and no `e.reset()`. Found by the
  outcome class, before the checker for it exists.

**2026-09-22, afternoon.**
- Loose ends cleared: Security and Tools re-run (the five stale pager fails are gone — pass 74);
  `seed-import.mjs`'s query fixed (`limit` before `select`) and written to the two live documents.
- **#40** — the Lucene index `New Field` › OK on a blank name leaves the dialog with both buttons
  disabled and no message (`onAdd` calls a throwing `write()` with no `try` and no `e.reset()`;
  `onEdit` and the Solr / receipt-rules siblings do it right). Read from the `nothing` outcome.
- **B2 checkers, the rest:** `ok` (OK on a dialog closes, validates, or is blocked — silence is a
  dead OK, an exception is a bug), `filter` (Filter › OK must not grow the rows), `toggle` (the
  inverse restores the node); `KNOWN_FAILS` names filed ones in the ledger. The `ok` checker's
  first run found **#41**: `BatchProcessorFilterEditPresenter` and
  `BatchDocumentPermissionsEditPresenter` report their four validation failures on the Properties
  screen's `ErrorEvent`, which only that screen handles — OK silently does nothing.
- **A `tick-row` rule** ticks a `TickBoxCell` row (the batch selection, distinct from selecting the
  row) and walks what it enables: Process, Delete, `Selection summary`, Download on the stream
  browser — MetaPresenter's handlers, unreached until now. (On a Processors grid the tick is the
  filter's *enabled* toggle — a write, which the guard blocked; recorded, harmless.)
- **B1 join fix:** ancestor attribution is inherited by PATH across node kinds (a post-action's
  parent is an editor-tab) — 459 → **504 exercised (32.9%)**, presenters 380/804 (47.3%).
- Ledger now: sort 42 pass, pager 74 pass, refresh 259 pass / 2 unstable (Processors' "last
  poll" seconds), filter 4 pass, ok 3 pass / 1 fail (filed #41). No unfiled fail, no unfiled
  exception.

**2026-09-22, evening — B3's denominator.** The handler inventory now mines every message the
client can show — `AlertEvent.fireWarn / fireError / fireInfo`, `ConfirmEvent.fire`,
`ErrorEvent.fire`, `throw new ValidationException` with a literal part, as a template — and joins
them by prefix to the alert and confirm bodies the walk read: **47 of 273 observed (17.2%)**
(error 26/120, confirm 11/59, warn 10/50, info 0/37). The never-observed list, per presenter, IS
the validation matrix to drive (`handler-inventory.md` § Messages). Two readings of it: the
`confirm` rows are mostly Delete › Confirm bodies the walk HAS opened but in shards that predate
the body capture (the 363 `unread`); the `error`/`warn` rows are the forms' own rules — invalid
event id, invalid email, a name with illegal characters, a range — which need a second retry
variant (an ILLEGAL value, not a seed name) and the `key` step. Next: the illegal-value retry for
dialogs whose presenter declares a pattern or range message, Escape-closes as a check (today
Escape is the pop's fallback after Cancel, so it is measured only when Cancel fails), then B4's
first cycle.

**2026-09-22, evening — B3's driver: illegal values and the keyboard.** Two new libraries, both
with their oracle mined from the GWT source rather than invented here.

- **`stroom-gwt-suite/lib/illegal.mjs`** — before a dialog's OK is clicked for real, the form is driven
  with values it cannot hold: an email box gets `not-an-email`, a time box `not-a-time`, a numeric
  box `9z9`, an id box `ab`, anything else `a/b*?"<>`. **One variant per rule**, because a form
  reports only the FIRST rule that fails: a single pass with every box illegal would have
  `EditAccountPresenter` answer "A user id must be at least 3 characters." for ever and never reach
  "Invalid email address." (`:143` is checked before `:146`). So each variant makes exactly one box
  illegal and gives the others something the form accepts — a box that already holds a value keeps
  it, since whatever the form loaded there is legal by construction. One variant per ASSERTED box
  (a kind some presenter declares a message for: email, time, number, id), at most three; a form
  with none gets a single generic text probe. The verdict is strict but bounded by what the source
  claims: only a `validation` message is the form doing its job, an `exception` is a rule that
  threw, an `info` or `environment` message means the value got past the form into a request and
  the user is reading whatever answered it, and **acceptance is a fault only for an asserted kind**
  — nothing forbids odd characters in a free-text name, so a form that shrugs at `a/b*?"<>` is
  within its rights. A guard-aborted write is `unchecked`, and says what it means: the form's rules
  run in `onHideRequest`, before the request, so the client side accepted the value. Between
  variants the boxes are put back; once per dialog caption per run.
- **`stroom-gwt-suite/lib/keys.mjs`** — `AbstractPopupPanel.onPreviewNativeEvent` acts on exactly two
  `KeyBinding` actions, and `KeyBinding:73/79` say which keys they are: **Escape** is
  `Action.CLOSE` → the dialog's Cancel path, **Ctrl+Enter** is `Action.OK` → its OK path. Plain
  Enter is `Action.EXECUTE`, which the panel ignores. So every dialog in the application owes two
  behaviours that can be checked without knowing its presenter, and the walk now checks both:
  Escape must close it (a Confirm interposed — "There are unsaved changes" — is the close path
  running, and passes), and Ctrl+Enter must do what the OK button then does. Plain Enter is pressed
  too, in the form's first box: there is no contract to check, but it is what fires a view's own
  `addKeyDownHandler`, and the FIELD it was typed in travels with the record.
- **The join** (`build-handler-inventory.mjs`): a `key` handler counts as exercised when Enter was
  pressed in a box whose label reduces to the same word as the handler's RECEIVER (`nameTextBox`,
  `nameFilter` and a FormGroup labelled "Name" all reduce to `name`), and a `value` handler when
  the illegal driver typed in such a box — pressing OK blurs it, which is what fires
  `addValueChangeHandler`. A handler the probes did not reach stays `needs-key` / `needs-value`:
  a press somewhere under the class is not a press on the receiver, and `SolrIndexSettingsViewImpl`
  binds five boxes.

**The first finding, on the first dialog the driver touched: #42.** Administration › Data Volumes ›
`New`, name `A/B volumes` → an Alert reading `Ambiguous URI path separator`. Four create dialogs
ask whether a name is taken through `@GET @Path("/fetchByName/{name}")`; RestyGWT encodes the
parameter correctly and **Jetty rejects `%2F` inside a path segment** before the resource is
reached, so an ordinary name like `Logs/Archive` is unusable and the user is shown the container's
error. Measured three ways in `stroom-gwt-suite/probe-uri.mjs` (200 for a plain name, 400 encoded, 404
unencoded). `FsVolumeGroupResource:69`, `IndexVolumeGroupResource:68`, `NodeGroupResource:65`,
`ProcessorProfileResource:67`.

Next: re-run the shards so the key and value joins have data (the Security lane first — accounts,
API keys, users and groups are where the mined `error` messages live), then B4's first compensating
cycle on node groups.

**Pending, to apply the moment the B3 re-run finishes** (not now: the lanes start each shard as a
fresh process, so editing the walker mid-run would put two walker versions in one re-run):

1. **`probeEscape` must check for ACE focus, as `probeKeys` already does.** `d-dash` recorded
   `Escape → nothing {key fail}` on `Set Expression For '…'`; that dialog is
   `ColumnFunctionEditorPresenter`, whose `:135` calls `editorPresenter.focus()`, and
   `AbstractPopupPanel.onPreviewNativeEvent` skips key handling outright when the target carries
   `ace_text-input`. Escape doing nothing there is the framework working as written. The verdict
   must be `unchecked` with that cite, and the capture must record `aceFocused` so the ledger can
   re-judge it. Until then that one fail per Ace-hosting dialog is a known mis-verdict, not a bug.
2. **`readFields` must skip the ACE editor's hidden `textarea.ace_text-input`.** It matches the
   `textarea` selector, so on `Filter 'New Field 0'` the "first box" the probes clicked WAS the Ace
   editor: `Enter in "Filter" → nothing`, then `the text box did not take the value` (typing into
   Ace sets no `.value` — the `landed` check caught it), then `Ctrl+Enter → nothing {key fail}`.
   `ColumnFilterPresenter:57` calls `editorPresenter.focus()` on show, so the panel's `isEditor`
   exclusion applies and Ctrl+Enter doing nothing there is correct.
3. **`aceFocused` must be sampled at the moment of each key press, not once at probe start.** It is
   read before the probe clicks a box, so a click that moves focus INTO Ace is invisible to it —
   which is why the `Filter` dialog reported a fail instead of `unchecked`. Escape needs the same
   sample; today it takes none at all.
4. **The probes dedupe by CAPTION, and a caption carries its subject.** `Filter 'New Field 0'`,
   `Filter 'New Field 1'` … are one dialog, so every column re-pays Escape / Enter / Ctrl+Enter and
   re-files the same verdict. `coverage.mjs:94` already has the rule — `normaliseCaption` replaces
   a quoted subject with `'…'` — so lift it into a shared lib and key `keysDone` / `escapeDone` /
   `illegalDone` on the normalised caption.
5. **Inline feedback must be judged by PRESENCE, not by change.** `Enter Your Current Password` ›
   OK still reported `OK did nothing` with the inline reader in place, because Ctrl+Enter had
   already fired the same validation a moment earlier: the `.feedback` label read
   "Password is required" both before and after, so the delta was empty. The user is looking at a
   message either way. `feedbackSaid` keeps its meaning (what is NEW), and the `ok` checker should
   fall back to "any non-empty `.feedback` text is showing" — the class is used by exactly the four
   identity views and is cleared to "" when the form is valid, so presence IS a message. The
   capture must also land on the record so the ledger can re-judge it.
   *(This corrects what I said earlier: these three fails do NOT clear by re-running the lane.)*
6. **The sort checker must accept the server's collation.** Two fails on Administration › Properties
   — `Name` desc showing `content-length` before `X-Forwarded-For`, and `Value` desc showing
   `|accountid|…` before `|TOKEN|…`. Both are correct DESCENDING order under a case-sensitive
   (ASCII / binary) collation, where every uppercase letter sorts before every lowercase one; the
   checker lower-cases before comparing and so disagrees. A column should pass if it is ordered
   under EITHER collation — the checker's business is that the page is sorted, not which collation
   the database uses.
7. **Use `isFilterAction` in the walker too.** The ledger now re-judges a mis-scoped `filter`
   check, but the walker still applies the rule to any opener containing the word — so it keeps
   recording them and counting a `checkFails`. One line, `lib/checks.mjs` already has the helper.
8. Re-walk `d-dash` **and `security`** afterwards. The `b3-security` shard finished at 14:43,
   before the `landed` read-back, the inline-feedback reader and the alert-vs-dialog rule went in,
   so its four remaining fails (three Change Password, one API-key expiry) are all artefacts of
   that vintage rather than findings. It is the one shard in this round walked by an older walker.

   One root cause, three symptoms, and the pattern is worth keeping: an ACE-hosting dialog is where
   the framework's keyboard contract does NOT apply, and the walker has to know that as precisely
   as the panel does. Escape still closes such a dialog — GWT's own `PopupPanel(modal)` hides on
   Escape underneath `AbstractPopupPanel` — which is why Escape passes there and Ctrl+Enter does
   not, and why the two must be judged separately rather than as one contract.


**2026-09-22, evening — the first measurement, and what it reframes.** Seven of the fourteen B3
shards merged (lane A complete, plus `d-dash` and `d-stepping`):

| | before B3 | now |
| --- | ---: | ---: |
| handlers exercised | 507 / 1530 (33.1%) | **513 / 1530 (33.5%)** |
| `key` | 1 / 78 | **3 / 78** |
| `value` | 0 / 305 | **2 / 305** |
| `ok-blocked` | 22 | 20 |
| messages observed | 49 / 273 (17.9%) | **55 / 273 (20.1%)** |

The joins work — `CreateExternalUserViewImpl.subjectId` and `CreateDocumentViewImpl.name` joined
through the ui.xml FormGroup label, `EntityTreeViewImpl.nameFilter` and
`NavigationViewImpl.nameFilter` through the illegal driver's typing — but the yield is small, and
the split of what is left says why:

- **112 of the 303 remaining `value` handlers are in `*Settings*` views**: the Feed's Settings tab
  (11 alone), Elastic cluster and index settings, Index settings, Pathways settings, the scheduled
  process editors. Those are document editor **TABS**, and a tab has no OK — it is saved by the
  document's Save button. **The illegal driver runs on `node.kind === 'dialog'` and `label === 'OK'`,
  so it can never touch them.** Only 41 are in `*Edit*` dialogs, which the driver does reach.

So "drive more values" does not mean more dialogs. It means a **second driver for editor tabs**:
type into each settings box and assert the document becomes DIRTY — which is an oracle the source
states plainly (`addDirtyHandler`, 15 of them, all `needs-dirty` today), costs no write, and is
undone by discarding the tab. That is B3's real remaining work, and it subsumes `needs-dirty`
entirely. The write that follows — Save, reload, assert it persisted — is B4's shape, and the
cycle runner already has it.


**An operational lesson, recorded because it cost 3¼ hours.** Lane A finished, so I parallelised
the remaining docs shards from one sequential lane into three concurrent walkers — a fourth browser
on a box already at 21/31 GB with swap FULL. Thirty-eight minutes later `docs1`'s renderer died
(`page.waitForTimeout: Page crashed`), losing the walk; its checkpoint JSON held 250 of the 436
nodes it had reached, honestly marked `complete: false`. **Three walkers is this box's ceiling**,
and the biggest docs shard should run alone. The estimate that prompted the change was wrong too:
`b0-docs1` took 298 minutes, not the ~40 I had assumed when I said the whole re-run was ~2½ hours.
Both are now in the `gwt-suite-speed` memory.


**2026-09-22, night — B3's second driver: does typing mark the document DIRTY?**
`stroom-gwt-suite/lib/dirty.mjs` + `probeDirty` in the walker, and `stroom-gwt-suite/probe-dirty.mjs` to drive
one document directly.

The oracle is a single line of GWT that holds for every document editor in the application:

```java
// DocTabPresenter.onDirty():274
saveButton.setEnabled(isDirty());
```

So Save's state IS the document's dirty flag, and "typing in this box marks the document dirty" is
checkable on any editor tab without knowing the presenter — which is what
`addValueChangeHandler` (305) and `addDirtyHandler` (15) are for. Verdicts: `unchecked` with no
Save button (not a document editor) or when the document arrived dirty (which proves nothing about
this box); `pass` when Save goes disabled -> enabled; `fail` when the value changed and the document
did not notice. The box is put back afterwards; the document stays dirty, because GWT's flag is
one-way until a save or a reload, and the walker's tab-close machinery already answers the discard
prompt.

**Its first run failed against correct behaviour, and that fixed the driver.** It typed into an
UNLABELLED box on the Feed's *Data* tab — a stream browser — and called Save's silence a defect. A
settings field is a text box inside a `.form-group` **that carries a label**; an unlabelled box is a
quick filter, a pager or an expression term, and belongs to a widget rather than to the document.
With that filter, Feed › Settings reads 3 fields out of 11 form groups (the rest are SelectionBoxes
and tick boxes) and `Classification` passes.


**The ceiling counts every walker, including a test one.** `docs2` crashed the same way `docs1` did,
because a one-document test walk was started while three shards were running. Free memory at launch
is not the test — a walker's browser grows as it walks, so 12 GB free with three already running
still ends in a renderer crash. `docs1` (250/436 nodes) and `docs2` (243/314) both need re-running,
alone. A `probe-*.mjs` with its own short page session is cheap and does not count against the
three; anything that runs `walk.mjs` does.


**The `feedbackSaid` regression, and what it costs.** When the `ok` checker moved to
`feedbackShown` the name was dropped from the import while the ILLEGAL driver still called it, so
that driver threw on every dialog it reached — silently, behind its own catch — in `docs3`, `docs5`
and the `docs1`/`docs2` re-runs. It survived because **the Node tooling was never linted**:
`eslint.config.js` matched only `**/*.{ts,tsx}`, so `eslint stroom-gwt-suite/` matched no files and
printed nothing, which reads exactly like a pass. There is now a `**/*.mjs` block with
`js.configs.recommended`, verified against a planted undefined call.

**Decided, rather than re-running five hours twice.** Of the 57 dialog captions `docs1` and `docs2`
reach, 50 have never been driven by the illegal driver — but 27 of those are the explorer's
`New › <type>` create dialogs, which the driver skips on purpose (their OK is a server create the
guard aborts, and `seed.mjs` is how documents are made). The genuinely new surface is 23, and the
document-specific ones are few: `Add Notification`, `Edit Notification`, `Set Constraints`,
`Add new rule`, `Edit Permissions For Selected User`, `Add To Annotation`, `View Current Selection`.
A SHORT targeted walk of just those document types with the fixed walker buys the same illegal
coverage at a fraction of the cost, so that is the plan once the two re-runs land.


## The measurement — all 14 shards re-walked (2026-09-23, 05:40)

| | before B3 | after |
| --- | ---: | ---: |
| handlers exercised | 504 / 1530 (32.9%) | **536 / 1530 (35.0%)** |
| presenters | 380 / 804 (47.3%) | **394 / 804 (49.0%)** |
| `value` | 0 / 305 | **14 / 305** |
| `key` | 0 / 78 | **6 / 78** |
| `dirty` | 0 / 15 | **2 / 15** |
| `ok-blocked` | 22 | 19 |
| messages observed | 47 / 273 (17.2%) | **58 / 273 (21.2%)** |
| nodes attributed | — | 7,827 |

Checker verdicts, which did not exist at all two days ago: **refresh 921 pass · key 615 pass ·
ok 299 pass · toggle 181 · pager 168 · sort 95 · filter 25 · illegal 16 · dirty 10**.

**56 fails, of which 17 are already-filed bugs** (#38 ×3, #40, #41 ×4, #42 ×9). The 39 NEW ones
fall into four signatures, and three of the four are the pre-fix vintage rather than findings:

| signature | n | reading |
| --- | ---: | --- |
| `OK did nothing: the dialog is still open with no message` | 14 | the inline-feedback presence rule went in AFTER most shards walked; those captures have no feedback capture to re-judge. Clears on a re-walk. |
| `Ctrl+Enter did nothing where OK closed` | 12 | ACE-hosting dialogs, walked before the per-press focus sample. Clears on a re-walk. |
| `still open after Escape` | 5 | same ACE cause. Clears on a re-walk. |
| `Expand All → Collapse All did not restore the node` (5) and `Ask Stroom AI → Ask Stroom AI` (2) | 7 | **NEW, and worth triage** — the `toggle` checker says a control and its inverse must leave the node as it was. |

So the honest reading is that B3's drivers moved four denominators off zero and the messages
observed by a fifth, and that the outstanding triage is ONE signature: the toggle round-trip.


**The toggle triage, in full.** Seven `toggle` fails, none of them a GWT defect:

- **Five** were `Expand All → Collapse All did not restore the node`. Those two are **absolute**
  operations, not inverses: `Collapse All` closes the folders that were open before `Expand All`
  ran, so the pair restores the previous state only if the tree began fully collapsed — and the
  walker calls `expandExplorer()` for its document steps, so it never does. They stay paired for
  POPPING, which is what the pairing is for, and are no longer judged as a round-trip.
- **Two** were `Ask Stroom AI → Ask Stroom AI` on `Dashboard / Params / after Duplicate`, and the
  delta before them was `+Save` — the Save button APPEARING as the dock opened, which would mean
  opening a chat panel dirties the document. `stroom-gwt-suite/probe-ai-dirty.mjs` settles it: on a
  freshly-opened Dashboard, Save reads `disabled` before the dock, with it open, and after closing
  it. The `+Save` came from the `Duplicate` that preceded the node — its dirty flag landed a beat
  after the node was surveyed, so the round-trip compared against a stale baseline. That is
  `unstable` (the state moved under us), the same verdict a Refresh over live data gets, and the
  check now records the buttons gained and lost so the ledger can tell the two apart.

Worth keeping as the pattern: of every fail these checkers have raised, the ones that were real
(#42, #43, #44, #45) came from the WRITE paths and the illegal values, and every fail from the
structural checkers so far has been the oracle's fault. A structural checker earns its place by
being right about the boring cases — 921 refreshes, 615 keys, 181 toggles — not by finding bugs.


**Not every settings tab can be driven, and the ones that cannot are worth naming.** The dirty
driver reads labelled `.form-group` controls of three kinds — text, tick, select. A settings tab
built from EMBEDDED widgets has none of them: DataGen's two form groups wrap a `<g:SimplePanel>`
each (a document chooser and an Ace editor), and `ScriptSettings` and `ViewSettings` are the same
shape. Counted across the settings views:

| view | drivable | embedded-only |
| --- | ---: | ---: |
| `FeedSettings` | 11 | 0 |
| `ElasticIndexSettings` / `SolrIndexSettings` / `IndexSettings` | 7 each | 1–4 |
| `PathwaysSettings` | 5 | 2 |
| `XMLSchemaSettings` | 4 | 0 |
| `DataGenSettings` / `ScriptSettings` / `ViewSettings` | **0** | all |

So a shard that opens DataGen first and reports no dirty hits is correct, not broken. Reaching the
rest of `needs-value` past those tabs needs a driver for the embedded widgets themselves — a
document chooser (pick a doc) and an Ace editor (type into `.ace_text-input`, which every other
probe deliberately avoids). Both are feasible and both are new drivers, not a tweak.


## 2026-10-04 — B3 completed across five gesture classes, and what the numbers cost to earn

`handlers 604 -> 692/1530 (45.2%)` · `presenters 380 -> 449/804` · `value 21 -> 61` ·
`context 0 -> 8/11` · `close 0 -> 3` · `dirty 2 -> 5` · `messages 47 -> 64/273` ·
cycles `7/8 pass, 0 leaked` · ledger `79 = 27 stale + 52 filed + 0 unexplained`.

### What was added

| driver | what it drives | oracle |
| --- | --- | --- |
| `lib/mouse.mjs` | dialog drag, SE resize, value-spinner press/release/hover/leave | `Dialog:203` clamp, `popupPanel-dragGlass`, `Spinner:105-137` classes |
| `lib/range.mjs` | the pager's row jump — type a row number, Enter | `Pager:132/152/190`, `fireMoveEvent` reads BOTH boxes |
| `lib/context.mjs` | right-click on explorer rows, editors, grid rows | a context handler that runs puts a `.menuItem-outer` menu on screen |
| `lib/illegal.mjs` cleared variant | a PRE-FILLED dialog emptied, then OK | a dead OK is a FAIL here, unlike an illegal value with no rule |
| `lib/cycle.mjs` edit shape | things that cannot be created — change, reload, change back, reload | the revert is VERIFIED, not assumed |

Plus: every field on a settings tab rather than the first; Enter in every named box rather than
one; fields on a tab INSIDE a dialog, which no driver had ever read.

### The joins mattered as much as the drivers

Several kinds were not awaiting a driver at all — they were awaiting a join:

- `close 0 -> 3`: a completed selection hides the SelectionBox popup, so
  `BaseSelectionBox:163` and `SelectionPopup:47` had been firing all along.
- `dirty 2 -> 5`: Save only lights up because the dirtiness TRAVELLED, so
  `DocTabProvider:65` and `TabContentProvider:115` ran every time.
- 12 `unlabelled` click handlers were nameable once a button whose caption CHANGES with its state
  (`RefreshButton`, the auto-refresh toggle) was given a SET of labels.
- `ok-blocked` falls when a compensated cycle drives the OK with the write going through.
- A tab reached by EXPLORING had no owner for the `tabsOf` lookup, so every embedded panel it
  held was invisible: `embedded 54 -> 62/193`, `never seen 153 -> 145`.

### Five driver artefacts, caught before any became a bug report

Every structural fail this suite has ever produced has turned out to be the oracle's fault, and
that held again. In one session: a spinner behind a modal (6 fails on a working spinner), a pager
behind a modal (1), Enter ANSWERING A CONFIRM — "You are about to process all feeds" — which only
the read-only guard stopped from writing, a dialog's own alert swallowing later field reads, and
ordinary column sorting reported as a broken toggle.

The rule that caught them: **a NEW fail gets a probe before it gets a bug number.** The probe
disagreed with the walk twice, and the probe was right both times.

Two measurement faults were found the same way, by a number moving the WRONG way:

- Adding three shards took `ok` 61 -> 60. `seen()` took the FIRST owner's record instead of
  pooling them, so the answer depended on Set iteration order. Pooled, the honest figure is 57 —
  LOWER, because three credits had rested on a technicality.
- `ONLY=` overwrote `cycles.json`, discarding other cycles' evidence; `handlers` DROPPED after a
  passing run.

### What `done` now reads as

Not 1530/1530. About 165 of the remainder are reachable only by WRITING — 18 `ok-blocked`, 47
unprovoked `confirm` messages, 99 `error` messages that need a request to reach the server — and
the standing decision (2026-10-04) is compensated cycles only, guard armed. The honest ceiling on
read-only driving is roughly 1100-1200 handlers and ~120/273 messages. `done` is every handler
either exercised or in a NAMED exclusion class, the ledger at 0 unexplained, and the bug list
no longer growing when driving is added.
