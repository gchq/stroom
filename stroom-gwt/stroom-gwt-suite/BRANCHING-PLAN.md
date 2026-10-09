# Full coverage of the GWT UI — the branching plan

[COVERAGE-PLAN.md](./COVERAGE-PLAN.md) planned *destinations* — places you can navigate to. That layer
is done: 184 recorded targets, 167 pixel-identical, 0 corpus misses. It then said "dialogs are the
long tail" and stopped, which was an admission rather than a plan.

This is the plan for everything else. **It is research-first on purpose**: the numbers below are
measured against `oracles/gwt-inventory.csv` and the GWT client source, not estimated, because a plan
built on a tilde produces a suite that stops where the tilde was.

Its companion is [oracles/reachability-graph.md](../oracles/reachability-graph.md) — **generated** by
`tools/build-reachability-graph.mjs`, so it is re-derived rather than maintained.

## The target is 414 — and 414 decomposes into 196 doors

`gwt-inventory.csv` holds **414 presenters** in `Screen/Dialog` + `Shared Screen/Dialog` across 45
areas, essentially all ported (395 `ported`, 7 `done`, 12 `n/a`). That is the coverage target.

It is **not** 414 doors, and planning as though it were would send the crawler looking for 218 screens
that do not exist as destinations. Joined against the source
([reachability-graph.md](../oracles/reachability-graph.md)):

| what it is | count | what the crawler does with it |
| --- | ---: | --- |
| **Doors** | **198** | crawl them — each has a named route |
| Embedded panels | 193 | covered when their parent screen is; their *behaviour* is Layer 4b |
| Place-based | 6 | revealed by session state — reached by signing out / failing auth, not by clicking |
| Abstract base classes | 10 | never instantiated — excluded from the denominator |
| Genuinely unrouted | 7 | the research backlog |

**So the crawler's denominator is 205, and 198 of them (97%) have a known route today.**

The embedded 193 are the ones worth understanding: `BasicTableSettingsPresenter`,
`AccountsListPresenter`, `MetaListPresenter`, `ScheduledProcessListPresenter` — settings tabs, list
panes and toolbars that live *inside* a screen. They are reached by covering their parent, and what
they need is not navigation but the selection/sort/page/validate cases the screen profiles derive.
Counting them as missing destinations is how "218 unknown" appeared before the classification, and it
would have sent the crawler hunting for doors that were never there.

### Two corrections that moved the number, and both were method errors

**The file scan was too narrow.** It read `stroom-core-client` and `stroom-app-gwt` only, so the
entire **Statistics** area — five presenters in `stroom-statistics-client`, its own gradle module —
came back unrouted. `StatisticsPlugin` reaches them perfectly well. A whole area was reported as a
research backlog because of where the code lives. The roots are now derived from
`find . -name '*Presenter.java'` (three modules, not two).

**Place-based reveal was not modelled.** `LoginPresenter`, `AuthenticationErrorPresenter` and
`ResetPasswordPresenter` declare a GWTP `Proxy` and are revealed by the SESSION's state — you see them
because you are signed out or auth failed, not because anything called `show()`. No parent references
them, so no amount of crawling finds them. They need the throwaway-session recipe already sketched for
`Sign Out`, and `LoginPresenter` is the easy win: **the suite already drives that screen on every
single run to sign in, and has never photographed it.**

Together those took the backlog from 16 to **7**.

### The 7 still unrouted
### The 16 unrouted, and why they are interesting

They are not a random tail — most are reached by a mechanism the crawler does not model:

* **`NavigationPresenter`** — the explorer panel itself. Always on screen, never navigated to;
  arguably covered 184 times over already.
* **`TabSessionChooserPresenter`** ← `TabSessionManager`, reached from the tab-session menu items the
  recorder deliberately skips because they write user state.
* **`ContentStoreCredentialsDialogPresenter` — genuinely unreachable, and I called this a tool gap
  before checking.** It is not. There IS a third dialog shape the tool was missing — a class that
  *receives* a `ShowPopupEvent.Builder` and configures it, letting the caller do the showing; 11
  classes use it and the tool now recognises it. But this one still has no caller. Every reference to
  it in the entire repository is its own file, its own `ViewImpl`, and a `bindPresenterWidget` line in
  `ContentStoreModule` — nothing constructs it, nothing shows it. The nearby
  `ContentStoreContentPackDetailsPresenter` opens `CredentialsManagerDialogPresenter`, a different
  class with a similar name. So it is **bound but unwired**: a screen that cannot be covered because
  nothing opens it, and no crawler will ever find it. Logged as
  [gwt-bugs.md](../stroom-gwt/ISSUES.md) **#32** and excluded from the denominator rather than carried
  as a permanent false negative.
* **`SslConfigPresenter`, `IndexVolumeListPresenter`, `ReportDuplicateManagementPresenter`** — all
  `MyPresenterWidget`, i.e. embedded panels whose host does not name them textually. Almost certainly
  embedded; needs one look each to confirm rather than assume.
* **`UnknownComponentPresenter`** — by its name, what a dashboard renders for a component type it does
  not recognise. An error state, not a door. Reaching it means authoring a broken dashboard.

## What we are actually covering: 414, not 184

`gwt-inventory.csv` holds **414 presenters** in `Screen/Dialog` + `Shared Screen/Dialog`, across
**45 areas**. The suite covers 184 *destinations*. The difference is not a rounding error, it is the
majority of the UI.

The ten biggest areas, and none of them is fully covered:

| area | presenters | | area | presenters |
| --- | ---: | --- | --- | ---: |
| Dashboard | 62 | | Explorer | 16 |
| Security | 40 | | Data receipt | 14 |
| Analytics | 32 | | Query / search | 13 |
| Data / streams | 29 | | Processor | 13 |
| Pipeline | 20 | | Index | 13 |
| Annotation | 18 | | State (Plan B) | 11 |

## How each is reached — measured, not guessed

From the GWT client source:

| entry mechanism | count |
| --- | ---: |
| Document editors (`extends DocumentPlugin`) | 27 |
| Screens (`extends ContentTabPlugin` and friends) | 21 |
| Classes raising a context menu (`ShowMenuEvent`) | 19 |
| **Dialogs that show themselves** (`ShowPopupEvent.builder(this)`) | **99** |
| Parent→child dialog edges (`ShowPopupEvent.builder(other)`) | 40 |

The 99 self-shown dialogs were the unknown: the popup site names no parent. But the parent *is* in the
source — it is whoever holds a `Provider` for the dialog — so the tool resolves it, and **all 99 now
have a named opener. Zero orphans.** That is the difference between "~81 dialogs somewhere" and a
directed graph you can walk.

**This graph is the worklist.** `AnnotationEditPresenter → annotationCollectionPresenter,
annotationLabelPresenter, annotationStatusPresenter, commentEditPresenter, commentPresenter` is five
targets and the route to each, straight out of the inventory.

## The five layers

### Layer 0 — destinations · DONE
184 targets. Menu leaves, document editors, editor sub-tabs, one post-action state.

### Layer 1 — entities that must exist first · PART DONE
The prerequisite layer, and the one that silently makes everything above it worthless: a screen with
no data is a photograph of an empty grid that will pass forever.

| entity | state | evidence |
| --- | --- | --- |
| Document types | **DONE** 26/26 | `probe-doctypes.mjs` reports 0 missing |
| **Annotations** | **NOT DONE** | `annotations-browse-annotations` DOM has no data-grid rows |
| Streams / data | present | Data tabs and `MetaBrowser` render rows |
| Users / groups | present | Security screens render rows |
| Processor filters / tasks | **unknown** | not measured |

Annotations carry **18 presenters** and six menu leaves — Browse, Create New, Collections, Comments,
Labels, Statuses — five of which are about annotations that do not exist. The create form is recorded
and drivable (6 `form-group`s); its field semantics have **not** been probed.

### Layer 2 — affordances · NOT STARTED
Measured from the 184 recorded DOMs: **3,838 titled buttons, 224 distinct titles**, of which **13
appear on essentially every screen** (`Main Menu`, `Expand All`, `Find In Content`, `Ask Stroom AI`,
`New`, `Delete`, …) and are app chrome to be covered **once**. That leaves **211 screen-specific
actions**.

### Layer 3 — dialogs · NOT STARTED
99 self-shown + 40 edges. Each dialog is itself a node with its own affordances, so this layer
recurses; the graph gives the parent for every one.

### Layer 4 — config-gated features · BLOCKED, needs a decision
**A whole category that no amount of crawling reaches, because it does not exist in this deployment.**
Four found, and the set was never enumerated before:

| feature | gate | visible here? |
| --- | --- | --- |
| Activity chooser (**4 presenters**) | `ActivityConfig.enabled` | no |
| Splash screen | `SplashConfig.enabled` | no |
| Info popup | `InfoPopupConfig.enabled` | no |
| **Banner** | `uiConfig.getMaintenanceMessage()` non-empty | no |

The banner is the instructive one: gated by a *value* rather than a boolean, so it would never appear
in a search for `isEnabled()`. There are 123 `isEnabled()` call sites in the client and only one is
`getActivity()` — the rest have not been classified.

**They can be enabled, and the suite is already built for it.** Researched rather than assumed:

* `UiConfig extends AbstractConfig implements IsStroomConfig`, so every one of these is an **editable
  property** — `stroom.ui.activity.enabled`, `stroom.ui.splash.enabled`,
  `stroom.ui.infoPopup.enabled`, `stroom.ui.maintenanceMessage`. Confirmed from the corpus rather than
  the source: `stroom.ui.splash.body` appears in the recorded
  `administration-properties` DOM, so these sit in a screen the suite **already covers**.
* None carries a restart annotation, so the change should take effect without a bounce — to be
  verified when it is first tried, not assumed.

**But turning them on pollutes everything.** `MainPresenter.setBanner(...)` puts the banner on *every*
screen, the splash intercepts login, and the activity chooser changes the navigation panel. Enabling
them in place would move all 184 baselines and buy four features at the cost of the other 180.

**The suite already has the answer: a second corpus.** `record.mjs` and `run.mjs` both take
`NAME` (`corpus/<name>/`), which exists so a corpus can be recorded under different conditions:

```bash
# turn the gates on via Administration > Properties, then:
NAME=gated node stroom-gwt/stroom-gwt-suite/record.mjs
NAME=gated node stroom-gwt/stroom-gwt-suite/run.mjs
```

That gets the four features covered, keeps `default` clean, and has a bonus: **diffing `gated` against
`default` measures exactly what the banner costs in layout on every screen** — which is a real
regression risk nobody currently tests, since a banner shifts the content panel down by its height.

The remaining work is a decision, not research: who enables them, and whether `gated` is recorded every
run or only when those features change.

### Layer 4b — per-screen BEHAVIOUR · researched, not started
A screenshot proves a screen renders. It says nothing about what happens when you select a row, sort
a column, type in a filter, or press one of the buttons that is currently greyed out.

[stroom-ui-react/porting/screen-profiles.md](../../../stroom-ui-react/porting/screen-profiles.md) — **generated** by
`tools/build-screen-profiles.mjs` — derives that per screen from the recorded DOM, because
the machinery is in the markup: `button[disabled]` is a selection-gated action, `.dataGridWidget` is
sorting and selection, `.pager` is paging, `.tickBox` is multi-select, `.quickFilter-textBox` is
filtering, `.form-group` is validation and dirty state, `.ace_editor` is an editable pane.

| | count |
| --- | ---: |
| Screens profiled | 184 |
| …with at least one behaviour | 173 (11 are genuinely static) |
| **Distinct selection-gated actions** | **749** |
| **Derived behavioural test cases** | **544** |

| behaviour | screens | | behaviour | screens |
| --- | ---: | --- | --- | ---: |
| selection-gated actions | 171 | | form | 78 |
| sub-tabs | 144 | | filter | 49 |
| paging | 100 | | code editor | 39 |
| grid | 98 | | multi-select | 9 |

**Chrome is derived, not named.** The explorer panel is on all 184 screens — its quick filter, its
tree, its New/Delete/Locate buttons sitting disabled until something is selected. Counted naively,
every screen "has a filter" and the number is meaningless. A title present on ≥95% of screens is the
shell, so it is subtracted: filter falls from 184 screens to **49**, selection-gated actions from
1,076 to **749**. Chrome is one screen's worth of behaviour, tested once.

The 544 cases are a **worklist, not tests**. Deriving them says which screens have a behaviour worth
driving; a human still decides which are worth the effort, and what the assertion should be.

### Layer 4c — context menus · MEASURED, and bigger than the plan said
An earlier draft of this document gave context menus one line and a "~10" estimate. That was the same
tilde this plan exists to remove, left standing in the plan itself.

**19 classes raise `ShowMenuEvent`, declaring 70 distinct menu item labels.** They are not a footnote:
`Copy`, `Copy As`, `Move`, `Rename`, `Delete`, `Import`, `Export`, `Permissions`, `Add Term`,
`Add Operator`, `Change Status`, `Change Assigned To`, `Conditional Formatting`, `Create Annotation`,
`Close Others`, `Close Tabs to the Right`, seven flavours of copy-to-clipboard, and the whole `New`
sub-tree — which alone branches into 5 categories × 26 document types.

They are a third kind of affordance alongside buttons and sub-tabs, and they behave differently in
three ways the crawler has to handle:

* **They are contextual.** The explorer's menu differs by node type (System, Favourites, folder, each
  document type), so the same raiser yields different menus and each needs covering separately.
* **They are where most mutation lives.** Delete, Move, Rename, Copy — so the read-only guard will be
  doing most of its work here, and most blocked requests will come from this layer.
* **Some are pure client-side.** `Close Tabs to the Right` and the clipboard items make no request at
  all, so the guard sees nothing and cannot classify them. Their effect is only visible in the DOM.

That last point matters for the crawler's classification step: "made no request" is not the same as
"did nothing", and treating them alike would mark seven clipboard actions and six tab actions as inert.

### Layer 4d — keyboard and focus · MEASURED, uncovered
Never mentioned before this pass, and not small. From the recorded DOM and the client source:

| | count |
| --- | ---: |
| `tabindex` attributes | 2,092 (880 focusable, i.e. `>= 0`) |
| `role` attributes | 789 |
| `allow-focus` / `focus-without-border` classes | 811 / 736 |
| **`keyboardSelected` markers** | **365** |
| Key-handling sites in the client (`onKeyDown`, `KeyCodes.*`) | 312 |
| Shortcut / key-binding classes | 38 |

`keyboardSelected` is the interesting one: **GWT tracks keyboard selection as a distinct state from
mouse selection**, which means a grid can be keyboard-selected without being mouse-selected and the two
may enable different things. Every selection-gated assertion in Layer 4b is currently written as "click
a row"; the keyboard path is a second, untested way to reach the same state.

Cheap first cut, since none of it needs new navigation: on each screen with a grid, `Tab` to it,
`ArrowDown`, and assert the same buttons enable as a click does. Any divergence is a real defect.

### Layer 4e — error states · MEASURED, and actively suppressed today
The single largest dialog family in the application, and the harness **dismisses it on sight**.

| mechanism | sites |
| --- | ---: |
| **`AlertEvent`** | **527** |
| `RestErrorHandler` | 347 |
| `DefaultErrorHandler` | 57 |
| `ErrorEvent` | 13 |

527 `AlertEvent` sites is more than twice the dialog graph (203 doors). And `lib/shoot.mjs` →
`closeDialogs(page)` exists precisely to clear popups before photographing, so **the suite has never
deliberately photographed an error state.** It has met them by accident all day — the create 403, the
Pathways `Remote node '' has no URL set`, the S3 `Error deserialising object` — and each time treated
them as noise to be cleared before the "real" screenshot.

That is backwards. An Alert *is* a screen: it has a title, a message, a Show/Hide Detail toggle, a
stack pane and a Close button, and it is the most common thing a user sees when something goes wrong.
Covering it needs no new navigation either — the read-only guard is already provoking errors by
aborting mutations, so the crawler gets error states **for free** if it photographs the Alert instead
of closing it.

### Layer 4f — data variety · measurable, not yet measured
The plan has said from the start that "a grid with three rows recorded proves the grid, not the paging"
and then never returned to it. It is measurable: the pager renders its state inline as
`&nbsp; 1 &nbsp;to&nbsp; 45 <input class="pager-textBox">`, so current page, page size and total are
all recoverable per screen — with an entity-aware parse, which is why a first attempt with a naive
regex found nothing.

What that yields: the list of grids whose recorded data fits on one page, i.e. **every screen where
paging is present in the markup and untested by construction**. Those either need more seeded data or
an explicit note that paging is unproven there. Worth generating before writing paging assertions, so
the effort goes where the data can actually exercise it.

### Layer 5 — post-action states · 1 of many
The searched dashboard. Then pipeline stepping, query execution, the data browser with a stream
selected, the import/export wizards mid-flight. Each needs its own settle predicate — a fixed wait
photographs a half-drawn screen and then fails intermittently for months.

## Capture levels — the decision that keeps this runnable

At today's rate (377KB per target) covering 203 doors plus 544 behavioural states plus dialogs
projects to roughly **276MB of corpus and a run measured in hours**. The suite's founding property is
that "does the GWT UI still render correctly" is *a command that takes minutes and needs nothing
deployed*. Executed naively this plan destroys that.

The fix is not a smaller plan. It is that **capture is optional and purpose-driven**, on **two axes**.

### Axis 1 — depth of evidence

| level | keeps | answers | ~cost/target |
| --- | --- | --- | ---: |
| `reach` | JSON only: visited, affordances found, outcome of each click | *is anything unreachable, new, or newly broken?* | ~1KB |
| `+dom` | gzipped markup | *what IS this?* — the diagnosis question | ~100KB |
| `+pixel` | screenshot | *did it move?* | ~275KB |

DOM earns its place independently of screenshots: every structural finding this project has made — the
`.buttonPanel` 4px, `linkTab-label` vs its hidden width-reserving twin, `tickBox` not being a checkbox
— came from grepping recorded markup, not from looking at pictures.

### Axis 2 — replayable or live

Whether HTTP exchanges are captured. **This is not a deeper level, it is a different purpose:** it is
what makes a corpus, and it is only needed for targets you intend to replay.

The pairing that matters: **a coverage crawl does not need the simulation at all.** Run `reach`
against a live server with no HTTP capture and no baselines, and "did we reach all 203 doors" answers
in minutes. Pixel regression stays a curated subset replayed from the corpus. Today `record.mjs` does
both jobs in one pass, which is why they are entangled and why the projection looked fatal.

### Two coverage numbers, named separately

This splits coverage into two figures that answer different questions:

* **reach coverage** — of the 203 doors and their affordances, how many did we visit?
* **pixel coverage** — of those, how many have a baseline we diff every run?

100% reach with 30% pixel is a legitimate and probably desirable state. If the two are not named
apart, someone will quote one and mean the other.

### Target identity — deferred, with one cheap insurance policy

The right `(node, affordance)` signature scheme is not knowable in advance; it will fall out of
building. The one decision worth making now costs a few lines: **store identity as structured data in
`coverage.json` and DERIVE the filename from it**, rather than baking a string. Then when the scheme
changes, renaming is a regeneration — not a full re-record.

### Errors are self-solving once the crawl starts

Deliberately not designed up front. The read-only guard aborts mutating requests, and 527 `AlertEvent`
sites mean the application's answer to a failed request is an Alert — so **error examples appear by
themselves** the moment the crawler clicks with the guard armed. The only change needed is
photographing the Alert instead of `closeDialogs()`-ing it. Build first, let them surface, then decide
what to assert.

## What building it actually taught us · `crawl.mjs`, Annotations slice

Built and run three times. **23 nodes reached, 69 affordances classified, 0 mutations committed** — the
guard caught three (`annotation/v1/create`, `ai/v1/createChat`, `ai/v1/setDefaultAskStroomAIConfig`).
Four **alert** nodes appeared without being asked for, exactly as predicted: block a mutation and the
app answers with an Alert, so error states arrive free.

Three defect classes, each of which the crawler reported as a property of the UI until it was fixed:

* **Chrome dominated.** 96 of the first run's 104 affordances were `Main Menu`, `Expand All` and
  friends, re-clicked once per node — 92% of the run buying nothing. Covered once now.
* **`vanished` ×17 was the crawler's own selector bug.** GWT dialog buttons are `.Button` divs with no
  `title`, so `readButtons` falls back to `textContent` and GWT renders the caption **twice**:
  `Close Close`, `OK OK`, `Set As Default For All UsersSet As Default For All Users`. Looking for
  `button[title="Close Close"]` found nothing. Same shape as the `.linkTab-label` trap. Fixed by
  collapsing doubled labels and by clicking polymorphically (`button[title]`, `[aria-label]`, `.Button`
  by text).
* **Timeouts were being thrown, not recorded.** 15 `error`s that were really "the UI would not accept
  this click" — a fact about the screen, not a crash.

### The finding that mattered was mine, not the product's

The first write-up of this crawl claimed that "a route through a mutating affordance cannot be
replayed" — that blocking a save wedged the dialog so every later affordance reported `unclickable`,
34 of them. **That was wrong, and it was tested rather than left standing.**

`probe-failmode.mjs` presses the mutating control with the request either **aborted** (what the guard
does) or **fulfilled as a 500** (a real server error), and reports whether the failure path fired:

| dialog | abort | error500 |
| --- | --- | --- |
| Annotation Collections → New → OK | alert, `Close` enabled | alert, `Close` enabled |
| Ask Stroom AI → Configure → Set As Default For All Users | alert, `Close` enabled | alert, `Close` enabled |

Two things follow.

**`route.abort()` is a realistic failure.** It surfaces as a RestyGWT `FailedResponseException` and
reaches the same handler a 500 does, so the guard is not manufacturing an artificial condition. Good
news for the crawler: blocking mutations is safe *and* representative.

**Both dialogs recover correctly** — alert raised, controls re-enabled, exactly the
disable-during-flight then reset-on-failure contract. So the `unclickable` cluster was **my survey not
scoping to the topmost modal**: `readButtons(page, 'body')` offers the whole page, including controls
sitting behind an Alert's glass, which are *correctly* unclickable. The crawler reported its own
scoping bug as a property of the application, for the third time in one session.

**Fix for the crawler:** when a dialog or alert is open, survey only the topmost popup. Everything
behind the glass is not an affordance of that node.

**Swept, and this area is clean.** The crawl's own output is the worklist: an affordance with a
blocked request IS a mutating action, with its route already recorded. Three distinct mutations were
found in Annotations, and all three dialogs recover correctly:

| action | request | result |
| --- | --- | --- |
| Annotation Collections → New → OK | `annotation/v1/create` | alert, `Close` enabled |
| Ask Stroom AI → Configure → Set As Default For All Users | `ai/v1/setDefaultAskStroomAIConfig` | alert, `Close` enabled |
| Ask Stroom AI → New Conversation | `ai/v1/createChat` | alert, `Close` enabled |

Three of three. Not proof the defect class does not exist — one area of 45 — but evidence that it is
not the norm, and a reason not to have logged it on expectation.

**The method is now in place** for the real instances, if they exist. Not every dialog is required to
handle this correctly, and `probe-failmode.mjs` is parameterised (`BUTTONS`, `ACTION`, `TARGET`,
`FILL`) to check any of them. A dialog that leaves its buttons disabled with no alert has genuinely
stranded the user, and that is worth logging — but only once it has been seen, not assumed.

### Scaled to a second area · Security

Chosen as a stress test — 40 presenters, grids, password dialogs, multi-step flows. **30 nodes, 116
affordances, 14 dialogs, 8 alerts, 5 mutations caught and prevented**, including
`userAccess/v1/revoke`, the most destructive thing found so far and exactly what the guard is for.

Dialogs discovered without being told about any of them: `Add new account`, `Set Password`,
`Create User`, `Create Group`, `Filter Documents To Apply Permissions Changes On`, `Batch Edit
Permissions For Filtered Documents`, `Add new API Key`, `End this user's sessions and revoke their
tokens`, `Credentials Manager / Add`.

The classifier is doing real work rather than returning a constant: `changed` rises from 9 in
Annotations to **45** here, because Security's screens genuinely react to clicks — `Show Password`
becoming `Hide Password`, grids populating — where Annotations mostly opened dialogs.

**It capped, and said so.** `MAXNODES=30` with **8 still queued**: Security is bigger than the cap, the
run is incomplete, and the output is unambiguous about that rather than looking finished. First time
the "never silently truncate" rule has earned its place.

**The five `vanished` were a discovery, not a defect.** Measured rather than guessed at, and they were
all grid column headers: `User Id 1`, `Display Name 1`, `Expires On 1` — the trailing digit being
GWT's sort-order index. Headers are clickable because that is how you sort, so `readButtons` correctly
reports them as affordances; they are simply neither `<button title=…>` nor `.Button`, so the click
locator missed every one.

That means **the crawler is finding the sortable columns by itself** — the "sort a column and assert
the order changes" case the screen profiles derive, discovered rather than enumerated. They are now
tagged `kind: 'column-sort'` and located by header text with the sort index stripped.

**Verified.** A full Security pass (`MAXNODES=60`) completes at **38/38 nodes with no cap hit** — so 38
is the whole area, and the earlier 30-node run's "8 queued" is now closed. `vanished` goes **5 → 0**:
every column header is located and clicked.

**Now fixed, and it works.** `survey()` takes a cheap projection of every visible grid — row count plus
the first three rows' text — and `classify()` compares it, adding three outcomes: `grid-reordered`,
`grid-rows` and `grid-appeared`. On the next full Security pass **3 of the 5 column sorts report
`grid-reordered`** with the new first row quoted, where all five previously reported `nothing`.

The other two still say `nothing`, and that is the interesting part. `User Id` on Manage Accounts and
`Expires On` on Manage API Keys are grids holding **a single row** in this instance — one admin
account, one API key — and sorting one row changes nothing observable. So the answer is honest: the
click worked, the grid did not change, because it *could* not.

**Confirmed, and the numbers do not overlap.** The row counts are now stored on the node record and on
each affordance, and a `column-sort → nothing` rewrites its own detail to say how many rows it had:

| screen | column | rows | outcome |
| --- | --- | ---: | --- |
| Users | Display Name | 97 | `grid-reordered` |
| User Groups | Display Name | 100 | `grid-reordered` |
| Application Permissions | Display Name | 65 | `grid-reordered` |
| Manage Accounts | User Id | **1** | `nothing` |
| Manage API Keys | Expires On | **1** | `nothing` |

Every sort with rows to sort reordered; every sort that did not have exactly one row. There is no case
of a many-row grid failing to sort, so nothing here is a product defect.

Which is Layer 4f arriving by itself. Those two sorts are **untestable with the data this instance
holds**, and the crawler says so rather than reporting a false pass. That is the signal for what
seeding is actually for: not "make the screen non-empty" but "give the grid enough rows that its
behaviour is observable". Seed a second account and a second API key and both become real assertions.

The general rule this earned: **a projection the classifier computes must also be recorded.** The
count existed all along inside `survey()` and was discarded before anything could read it, which is why
the question needed a throwaway probe — and that probe then failed on its own import path and returned
nothing, costing a run to discover it had never reached a browser. Measure where the crawl already is.

**The limitation this replaced, kept because it explains the design.** All five column sorts come back
`nothing`. They are not failing — the clicks land — but `classify()` only notices a new dialog or a new
button, and a sort changes **neither**. It changes row ORDER, which nothing is looking at.

So `column-sort → nothing` means "the classifier is blind here", not "the sort is dead". The fix is a
cheap grid projection in `classify()` — first row's cell text, or the row-id sequence — compared before
and after. Until that exists, **no sorting assertion in the 544 derived cases can be believed**, because
the crawler cannot yet distinguish a working sort from a broken one. That is the next thing to build,
and it generalises: paging and filtering change row content too, and are equally invisible today.

## Target identity, decided by the Dashboard

The plan said target identity was the one decision that could not be made in advance and would have to
be learned by building. The Dashboard is where it was learned — 62 presenters, the largest area, and it
broke three successive identity schemes in four passes. Each failure is a different way for a crawl to
report coverage it does not have.

| pass | identity | nodes | distinct places | what it got wrong |
| --- | --- | ---: | ---: | --- |
| 1 | path (`kind / path…`) | 60, **capped**, 20 queued | 8 | two components named `Table` collided; the second was never visited |
| 2 | + content signature (caption + buttons) | 75 | 22 | — |
| 3 | + menu scoping | 67 | 25 | — |
| 4 | + structural fingerprint | 67 | **30** | — |

**Pass 1 — a route is not a place.** 52 of 60 nodes were repeats: the dashboard toolbar is the same
toolbar on every component tab, so `Save As`, `History`, `Favourites` and `Process` were re-opened and
re-explored once per component, seven times over. 87% of the budget, and it capped at 60 while holding
8 distinct affordance-sets. The reachability graph had already said this — **203 doors** — and a door
reached from seven places is one door. Duplicates are still RECORDED (`status: duplicate`,
`duplicateOf`) because "seven tabs share one toolbar" is a finding; they are simply not re-explored.

**Pass 1 also lost a screen in silence.** The seed printed ten tab labels and produced nine nodes: two
components are both called `Table`, both mapped to one id, and the second was dropped by the `seen`
set on a run that reported itself complete. Tabs are now addressed by `(label, nth)` with a `#2` suffix.
Silent coverage loss is the failure mode that looks exactly like success, and the only reason it was
caught is that the seed line prints its labels — **a count you cannot read cannot contradict you**.

**Pass 2 — a menu is a scope.** 13 affordances came back `unclickable`, which reads as the application
refusing input. `elementFromPoint` named the coverer: `div.menuItem-text`. Clicking a component tab in
design mode opens that component's menu, which sits over the toolbar; `survey()` read the buttons
underneath and offered them. Identical in shape to the 34 false `unclickable` read from behind a
modal's glass — that fix scoped to `.dialog-popup` and never asked whether a MENU is a scope too.

It is, and treating it as one is not merely a bug fix. The component menu is `Rename`, `Settings`,
`Hide`, `Duplicate`, `Duplicate To…`, `Remove`, `Maximise` — the crawler found `RenameTabPresenter` and
`SettingsPresenter` by itself, both named in the graph, and the **27 `ShowMenuEvent` classes** were
until then entirely uncrawled. `unclickable` went 13 → **0**.

**Pass 3 — a caption is not a dialog.** The pass-2 signature merged every component's `Settings` into
one node: all are captioned `Settings`, all offer OK/Cancel. The GWT source is unambiguous that they
are different — `QuerySettingsPresenter`, `TableSettingsPresenter`, `TextSettingsPresenter`,
`ListInputSettingsPresenter`, `KeyValueInputSettingsPresenter`, `TableFilterSettingsPresenter`,
`EmbeddedQuerySettingsPresenter`. So the fix for one silent coverage loss had introduced another.

The signature now includes a **structural fingerprint** of the scope: sub-tab labels plus counts of
inputs, form groups and editors. Structural and not data-valued on purpose — fingerprinting values
would stop two genuinely identical dialogs merging the moment their contents differed, which is the
same error in the opposite direction.

**The result discriminates correctly, and that is checkable rather than asserted:** all seven `Rename`
dialogs merge to one; `Settings` separates into six; and `Table #2 / Settings` merges into
`Table / Settings` because two components of the SAME type do share a dialog. Same type merges,
different type separates.

**The rule.** Identity is `kind + caption + affordances + structural fingerprint`, and the crawl reports
`distinct places` alongside `nodes` so the gap between "places visited" and "routes walked" is always
on screen. Every one of these four failures was found by a printed number disagreeing with a story —
never by reasoning about the code.

## A menu is a destination

Menu scoping was validated on the Dashboard, where a menu genuinely IS the scope, and it broke Security
the moment it ran there: **26 of 44 nodes** failed, because `clickAffordance` committed to
`clickMenuItem` whenever any menu was open and every toolbar click made after a menu step was
redirected into a menu with no such item. The menu attempt now falls through to the button locators,
and menu route-steps dismiss the menu once the leaf is taken.

**A fix validated only on the area that motivated it is a hypothesis.** That area is the least likely
of all to falsify it.

**A retraction worth keeping.** That broken run reported affordances rising 125 → 183 and it was
recorded here as expanded coverage. It was not: screens were being surveyed with a stale main menu
open and were reporting the MENU's items as their own affordances. The count went UP, so it read as
progress and went unchecked — where 52-of-60 duplicates had been investigated immediately precisely
because that number looked too good. An increase deserves the same suspicion as a decrease.

What separates real coverage from that artefact is never the count, it is **attribution**: is this
affordance recorded against the thing that owns it? The legitimate fix arrives at a similar number by
honest means, so the number alone could never have told the two apart.

**The gap underneath was real.** `Main Menu` classified as `changed` and stopped, so the main-menu
tree — 45 areas in the reachability graph — was never crawled. A menu is a destination like a dialog,
so it queues a node (marked `☰`), and a SUBMENU counts too: `after.menu && !before.menu` catches only
the first opening, and every group inside it listed its children as detail text nothing ever clicked.

Security went **38 → 61 distinct places**, 130 nodes, no cap, and reached 13 dialogs that no
button-driven crawl could see: `Import`, `Export`, `Find`, `Recent Items`, `Save Tab Session`,
`Preferences` (+ `Set As Default`, `Revert To Default`), `Change Password`, `About`, `Search Results`.
Three new mutations are now correctly attributed: `preferences/v1`,
`preferences/v1/resetToDefaultUserPreferences`, `annotation/v1/create`.

The signature dedupe pays for itself here: the menu cross-product — `Main Menu / Annotations / Tools`
is the same place as `Main Menu / Tools` — is **69 of the 130 nodes**, recorded and not re-explored.

## How to drive it: a crawler whose worklist is the graph

A hand-maintained list of dialogs rots the first time somebody adds a button. The suite already walks
the *menu* live for exactly that reason. Extend the principle down the tree:

```
queue ← 184 destinations, seeded with the reachability graph's roots
while queue not empty:
    node ← queue.pop()
    navigate; shoot screenshot + DOM + exchanges
    for each affordance (buttons, context-menu items):
        signature ← (node kind, node id, affordance label)
        if seen(signature): continue            # the 13 chrome buttons, once each
        outcome ← click and CLASSIFY
        if outcome opened a dialog/screen: queue.push(it)
        record (signature, outcome)             # including "nothing" and "disabled"
```

Cross-check the crawl against the graph: **any presenter in the graph the crawl never reached is
either a gap in the crawler or a config-gated feature.** That is the exit criterion, and it is
checkable rather than a feeling.

Four things it must get right, each already paid for once by this suite:

* **Deduplicate by signature**, or `Main Menu` is covered 184 times and a real dialog never.
* **Bound depth, detect cycles** — dialogs that open each other.
* **The read-only guard classifies rather than blocks.** A mutating affordance must be recorded as
  *reached and identified*, then skipped or rolled back; `attachReadOnlyGuard` currently fails the run.
* **Never silently cap.** A truncation that is not logged reads as "covered everything".

## Order of work

1. **Annotation seeding** — Layer 1, 18 presenters behind it, technique proven on document types.
2. **The affordance crawler** over existing destinations — produces Layer 3 for free.
2b. **Selection-gated behaviour** — the single biggest derived group (749 actions across 171 screens)
   and the cheapest to assert: select a row, assert the named buttons enable. It needs no new
   navigation, only the crawler's classification step plus a row click.
3. **The config-gated decision** — the four above, plus classifying the other 122 `isEnabled()` sites.
4. **Post-action states.**

## Two research items closed

### Mutation classification — no static list needed

The plan asked which of the 211 screen-specific actions mutate, "because it decides how much of the
crawl is safe to run". The answer is that **the question does not need answering statically**:
`attachReadOnlyGuard` already classifies at runtime, and in the same motion. With `enabled: true` it
pushes `{method, path, blocked:true}` onto a live `violations` array **and** aborts the request. So the
crawler clicks every affordance with the guard armed, and a mutating one identifies itself and is
prevented at once. No list to write, and none to keep current.

**The risk moves, and it is worth naming precisely.** Stroom READS via POST, so the guard carries a
whitelist of read-ish POST paths — and that list is incomplete by construction: its own comments record
three incidents where a legitimate read was blocked, the screen showed an error, and the error was then
misdiagnosed as a UI difference (`processorTask/v1/summary` broke every editor's Active Tasks tab;
`userAccess/v1/sessions` was read as GWT having a dialog the port lacked). It grows by triage.

So for the crawler, **a blocked request is a worklist item, not a verdict**. Each one is either "this
affordance mutates" or "the whitelist is missing a read", and only a human can tell them apart. The
crawler must record the path, not just the fact.

### The annotation form — field semantics, from the recorded DOM

No live driving needed; the create screen is already recorded. An annotation carries:

| field | how it is driven |
| --- | --- |
| Title, Subject, Comment | text — dedicated classes `annotationTitle`, `annotationSubject`, `annotationComment` |
| Status, Assigned To | single picker |
| Labels, Collections | multi picker |
| Retain | toggle |

Which closes a loop: the `ChooserPresenter` / `MultiChooserPresenter` sitting in the Annotation area's
embedded panels **are** those pickers. Seeding an annotation therefore exercises them for free, and
the seeder is writable now — same shape as `lib/fills.mjs`, deterministic values, re-runnable.

## Research still to do before building

Stated plainly, because this plan is only as good as its next measurement:

* **Whether the four gated features can be enabled here** without disturbing the rest of the corpus.
* **Whether processing/task screens say anything idle**, or need running jobs.
* **The 122 unclassified `isEnabled()` sites** — how many hide a feature rather than a field.
* **How the 414 presenters map onto the graph's nodes.** The graph names classes; the inventory names
  classes; they have not been joined, so "which of the 414 does the crawl reach" is not yet answerable.
  That join is the next tool, and it turns this plan's exit criterion into a number.
