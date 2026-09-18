# Quick filter: recent-filter drop-down and Advanced Query — design

**Status:** reviewed 2026-09-18; decisions in §8 taken. **A1–A3 implemented 2026-09-18** on
`gh-5740_quick_filter` (uncommitted): shared `QuickFilterContext` / `QuickFilterHistoryKey`,
`quick_filter_history` table + DAO + service + `QuickFilterHistoryResource`, widget `▼` + menu via
`QuickFilterContextHandler` / `QuickFilterContextHandlerFactory`, wired on Dependencies and
Properties. **A4 rolled out 2026-09-18** to every surface in the table in §4 (the ones marked
*history only* use `QuickFilterContext.historyOnly`). **B built 2026-09-18**: `QuickFilterPrinter`
(+ `QuickFilterPrintException`) beside the parser with 85 round-trip tests, and
`parseQuickFilter` / `formatQuickFilter` on `ExpressionResource`. C not started.

**Builds on:** `docs/query-filter-surface-syntax-spec.md` (recoverable from commit `739c927737`;
untracked in this checkout) and the gh-5720 work on `gh-5740_quick_filter`. The decisions
recorded there are taken as given here and not re-argued:

| Decision (spec §) | Consequence for this design |
|---|---|
| Text is king; the tree is a transient view (§9) | History stores the **text**. Advanced Query is a view over the text, not a second source of truth. |
| Never overwrite the text unless the user edits the tree; warn before reformatting (§10.1–10.2) | Cancel in the dialog is a no-op on the box. OK replaces the text only after an edit. |
| Refuse to open advanced mode when the text cannot be represented (§10.3) | A parse error on open is shown, not silently degraded. |
| The tree editor offers only the field's `ConditionSet` (§10.4) | The dialog additionally intersects with what the quick-filter grammar can *print* — see §5.3. |
| Parse and convert server-side, as endpoints (§11) | `parse` and `format` are new endpoints on the existing `ExpressionResource`; the client holds no grammar. |

And on repo direction: the UI may move from GWT to React, so the client part is kept thin and
the server part is what a future client would call unchanged.

---

## 1. What is being asked for

1. A small drop-down arrow on the right of every quick filter box.
2. Clicking it shows a panel listing that user's **recently used filters for this quick filter**,
   most recent first, de-duplicated.
3. The list is **stored on the server** and fetched from an endpoint.
4. The list is **scoped to the context** — each screen's quick filter shows only its own history.
5. At the bottom of the panel, an **Advanced Query** link that opens an expression-tree editor for
   the same filter; confirming it writes the equivalent text back into the box.

---

## 2. Current state — what the research found

### 2.1 The widget

`QuickFilter` (`stroom-core-client-widget/.../dropdowntree/client/view/QuickFilter.java`) is a
`FlowPanel` of `[TextBox][clear ×][help ?]` (`:84-91`). It debounces value changes by 400 ms and
fires `ValueChangeEvent<String>` (`:189`); Enter fires immediately, Escape clears (`:209`). The
help `?` opens an auto-hide `PopupPanel` positioned below the box (`:101`). The branch added
`setFilterError(String)` (`:136`), which reddens the box and prepends the message to the help
popup — the widget already knows whether its current text was accepted by the server.

It is wrapped by `QuickFilterPageViewImpl` / `QuickFilterDialogViewImpl` (form-group + data slot)
and used directly from 16 `ui.xml` files. There are ~80 client files touching it; every surface
that registers a tooltip does so via `QuickFilterTooltipUtil.createTooltip(title, …,
FIELD_DEFINITIONS, …)` — roughly 25 call sites, all shaped like
`DependenciesTabPresenter.java:60`.

The widget module has **no access to `RestFactory`** (that is `stroom-core-client`), so the widget
cannot fetch history itself; it needs hooks set by the presenter.

### 2.2 Existing drop-down and popup building blocks

- `BaseSelectionBox` (`stroom-core-client-widget/.../item/client/BaseSelectionBox.java:85`) is
  the app's combo box: a `SvgIconBox` with `SvgImage.DROP_DOWN`, click → `SelectionPopup`
  (`:146`) with a filter box, list and keyboard navigation, auto-hide partnered with the text box.
- `ShowMenuEvent` + `IconMenuItem` / `Separator` / `InfoMenuItem` (`stroom.widget.menu.client`)
  is the lightest popup and is already used from inside the widget module (`MyDataGrid.java:437`).
  It gives a positioned auto-hide list with keyboard navigation for free.
- `SvgImage.DROP_DOWN`, `ARROW_DOWN`, `HISTORY` all exist.

### 2.3 Server-side persistence precedents

Two per-user lists already exist, one of them literally "recent queries":

| | `explorer_favourite` | `query` (StoredQuery) |
|---|---|---|
| Module | `stroom-explorer-impl(-db)` | `stroom-dashboard/stroom-storedquery-impl(-db)` |
| Scope | `(user_uuid, explorer_node_id)` UNIQUE | `owner_uuid`, `dashboard_uuid`, `component_id`, `favourite` |
| Written | explicit user action | `DashboardServiceImpl.storeSearchHistory` (`:531`) on every user-initiated search; **no dedup** |
| Bounded | no | `StoredQueryHistoryCleanExecutor` — daily job, keeps N items and D days per user, cluster-locked (`StoredQueryDaoImpl.clean:204`); config `stroom.storedQuery.itemsRetention=100`, `daysRetention=365` |
| User deleted | — | `UserServiceImpl:416` calls each service's `delete(userRef)` |

`UserPreferences` (`preferences` table, one JSON blob per user, `stroom-config-global-impl-db`) was
considered as a home and rejected: it is a versioned blob edited by the preferences dialog, so
two tabs recording filters would clobber each other and every read of prefs would carry the
history.

`RecentItems` (`explorer.client.presenter`) is client-memory only and is not a precedent.

### 2.4 Parsing and the expression editor

- **Text → tree** exists server-side only: `stroom.query.language.filter.QuickFilter.parse(text,
  defaultFields, qualifiedFields)` (`:55`) wraps `SimpleStringExpressionParser.create` (`:76`).
  The grammar is `and`/`or`/`not`, brackets, quoted strings, `qualifier:` prefix, `!` negation
  and the 15 `SUPPORTED_CONDITIONS` sigils (`:57-74`, taken from `Condition.getOperator()`), plus
  `~` (chars-anywhere → rewritten to `MATCHES_REGEX`, `:326`) and `\` (escape, `:331`).
- **Tree → text does not exist.** No printer anywhere in the repo. This is the one genuinely new
  piece of server logic.
- `ExpressionResource` (`stroom-core-shared/.../query/shared/ExpressionResource.java:42`) already
  has `POST /expression/v1/validate` taking `ValidateExpressionRequest{expressionItem, fields,
  dateTimeSettings}` — i.e. the client already sends the field list with the request, which is the
  pattern to copy so the server needs no registry of contexts.
- The reusable tree editor is `stroom.data.client.presenter.EditExpressionPresenter`
  (`init(restFactory, dataSource, FieldSelectionListModel)` `:136`, `read(op)` `:160`,
  `write()` `:164`). `ProcessorEditPresenter.java:120-124, 204-214` is the canonical "open it in
  an OK/Cancel popup" caller, with `SimpleFieldSelectionListModel.addItems(List<QueryField>)`.
  `TermEditor` already restricts the condition list box to the field's `ConditionSet` (`:277`).
- `QueryField`, `ConditionSet`, `ExpressionOperator` are GWT-visible (`Query.gwt.xml` source
  path `api`), so the client can build the field list for the dialog from the same shared
  constants the server parses with.

### 2.5 How a surface is identified today

There is no identity. Each surface has 1–3 parallel constants in a shared class:
`FIELD_DEFINITIONS` (tooltip), and on the branch `QUERY_FIELDS` / `DEFAULT_QUERY_FIELDS` (parse),
in `FindTraceCriteria`, `DependencyCriteria`, `GlobalConfigResource`, `FindTaskProgressCriteria`,
`ExplorerTreeFilter`, `UserFields`, `FindUserDependenciesCriteria`, `FindApiKeyCriteria`,
`AccountFields`, `AnnotationTagFields`, `DocumentPermissionFields`, `CredentialFields`, …
The tooltip *title* string ("Dependencies Quick Filter Syntax") is the nearest thing to a name and
is not stable enough to key storage on.

---

## 3. Design overview

```
┌─ client (GWT) ───────────────────────────────────────────────────────────────┐
│ QuickFilter widget  [text………………][×][▼][?]                                    │
│   ▼ → menu: recent filters… / ─── / Advanced Query…                          │
│   hooks: QuickFilterContextHandler { showRecentFilters, recordUse }          │
│   commit/verdict state machine: QuickFilterHistoryTracker (unit tested)      │
│                                                                              │
│ QuickFilterContextHandlerFactory (stroom-core-client) — one line/presenter:  │
│   view.setQuickFilterContextHandler(factory.create(CONTEXT, this, this))     │
│     showRecentFilters → POST /quickFilterHistory/v1/fetch, then ShowMenuEvent│
│     recordUse         → POST /quickFilterHistory/v1/record (fire and forget) │
│     (C) "Advanced Query…" → AdvancedQuickFilterPresenter                     │
│                     open: POST /expression/v1/parseQuickFilter               │
│                     OK:   POST /expression/v1/formatQuickFilter              │
└──────────────────────────────────────────────────────────────────────────────┘
┌─ server ─────────────────────────────────────────────────────────────────────┐
│ QuickFilterHistoryResourceImpl → QuickFilterHistoryService → Dao → table     │
│ ExpressionResourceImpl.parseQuickFilter  → QuickFilter.parse (exists)        │
│ ExpressionResourceImpl.formatQuickFilter → QuickFilterPrinter (NEW)          │
└──────────────────────────────────────────────────────────────────────────────┘
```

Three independently shippable pieces: **A** history (server + widget), **B** printer +
parse/format endpoints (pure server, fully unit-testable), **C** the Advanced Query dialog
(needs B). A and B have no dependency on each other.

---

## 4. Context identity

**Decided.** A quick filter's history is keyed by `(user, context, dataSource)`:

- `context` is a short stable string naming the **field set** the filter is parsed against.
- `dataSource` is the `DocRef` the surface queries, where it has one; empty otherwise.

A context is therefore "these fields over this data source". Two screens that parse against the
same fields over the same (or no) data source share history — the explorer tree filter in the
navigation pane and in the entity-picker popup both use `ExplorerTreeFilter`, so a filter that
works in one works in the other and appears in both. Two Traces tabs open on different Plan B
documents do **not** share, because the operations and trace ids in one are meaningless in the
other.

Introduced as a GWT-shared value object, `QuickFilterContext`, carrying the things every surface
already hands around separately plus the key:

```java
public final class QuickFilterContext {
    String key;                              // "dependencies" — storage key, stable forever
    DocRef dataSource;                       // nullable — set per instance for doc-scoped surfaces
    List<FilterFieldDefinition> fieldDefs;   // tooltip
    List<QueryField> defaultFields;          // parse + Advanced Query
    List<QueryField> qualifiedFields;        // parse + Advanced Query

    QuickFilterContext withDataSource(DocRef dataSource);
}
```

declared next to the existing constants (`DependencyCriteria.QUICK_FILTER_CONTEXT = …`). The
tooltip util, the server `QuickFilter.parse` callers and the new binder all take the one object.
This is the consolidation `QuickFilterFields` started on the branch, one step further, and it
removes the "forgot to pass the same fields to parse and to the dialog" failure mode. It is
introduced surface-by-surface as each is wired (§7, step A4).

Surfaces whose criteria already carry a data source and so call `withDataSource(...)` at bind
time: **traces** (`FindTraceCriteria.dataSourceRef`) and **pathways**
(`FindPathwayCriteria.dataSourceRef`). Everything else binds the static constant. The document a
permissions screen is *about* (`DocumentPermissionFields.DOCUMENT` term) is a filter term, not a
data source, so those screens share one context.

Keys must be **stable across releases** (they are persisted). Keys as built (A4):

| Key | Declared on | Screens |
|---|---|---|
| `explorer` | `ExplorerTreeFilter` | nav tree, `EntityTreePresenter`, `ExplorerPopupPresenter`/`DocSelectionPopup`, export tree, **and the Find and Recent Items dialogs** — their text is parsed by `NodeInclusionChecker` against the same fields |
| `dependencies` | `DependencyCriteria` | Dependencies tab |
| `userDependencies` | `FindUserDependenciesCriteria` (fields derived via `QuickFilterFields`) | user dependencies list |
| `globalProperties` | `GlobalConfigResource` | Properties tab |
| `tasks` | `FindTaskProgressCriteria` | task manager |
| `users` | `UserFields` | user list, user-ref popup, app permissions, document user permissions |
| `documentPermissions` | `DocumentPermissionFields` (no `FilterFieldDefinition`s; tooltip is hand-built) | batch permissions, permission report |
| `apiKeys` | `FindApiKeyCriteria` | API keys |
| `accounts` | `AccountFields` | accounts |
| `activities` | `ActivityResource` — *history only*, fields served by `listFieldDefinitions()` | choose activity |
| `annotations` | `FindAnnotationRequest` — *history only* | find annotation |
| `aiChatHistory` | `FindAiChatHistoryCriteria` — *history only* | AI chat history |
| `traces` | `FindTraceCriteria` (derived fields) — **per Plan B document** | traces list; bound in `setDataSourceRef` |
| `indexFields` | `IndexResource` — *history only*, **per index document** | index fields; bound in `onRead`. (`FindFieldCriteria` lives in `stroom-query-api`, which cannot see `QuickFilterContext`.) |

Not wired, deliberately:

- the dashboard **column value filter** (`ColumnValuesFilterViewImpl`) and the field pickers in
  `TermEditor` / `QueryHelpPresenter` — excluded by the spec;
- the generic annotation `ChooserPresenter` and the `SelectionList` picker (credentials, etc.) —
  reusable widgets fed by arbitrary data suppliers, so there is no single context to key on;
- `pathways` — no quick filter box on the pathways screen at present;
- the annotation tag list — the criteria has a filter but the screen exposes no box.

The widget must therefore work with no context set — the arrow is simply not shown.

---

## 5. Recent filters (piece A)

### 5.1 Storage

New table, hosted with `preferences` in `stroom-config-global-impl-db` (Flyway history
`config_schema_history`; migration `V07_14_00_001__quick_filter_history.sql` — check the prefix
against whatever the branch is targeting when this lands). Rationale for the module: it is
per-user UI state, the same category as preferences, and it avoids a new module for one table.
Alternative if that feels wrong: a `stroom-quickfilter` api/impl/impl-db trio.

```sql
CREATE TABLE IF NOT EXISTS `quick_filter_history` (
    `id`            int          NOT NULL AUTO_INCREMENT,
    `user_uuid`     varchar(255) NOT NULL,
    `context`          varchar(64)  NOT NULL,
    `data_source_uuid` varchar(36)  NOT NULL DEFAULT '',
    `filter_text`      varchar(400) NOT NULL,
    `last_used_ms`     bigint       NOT NULL,
    `use_count`        int          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `quick_filter_history_user_ctx_ds_text_idx`
        (`user_uuid`, `context`, `data_source_uuid`, `filter_text`),
    KEY `quick_filter_history_user_ctx_ds_used_idx`
        (`user_uuid`, `context`, `data_source_uuid`, `last_used_ms`)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

- The unique key *is* the de-duplication. Column widths are chosen so the composite index fits
  InnoDB's 3072-byte limit under utf8mb4 (255+64+36+400 chars × 4 = 3020 bytes).
- `data_source_uuid` is `NOT NULL DEFAULT ''` rather than nullable because MySQL does not
  enforce uniqueness across NULLs; a surface with no data source stores the empty string.
- **Decided: case-insensitive de-dup.** The table collation `utf8mb4_0900_ai_ci` makes the unique
  key case- and accent-insensitive, so `Name:Foo` and `name:foo` are one row, matching the parser,
  which ignores case on qualifiers and on most conditions. The *stored* spelling is whichever was
  recorded first; `recordUse` does not rewrite it.
- Filters longer than 400 chars are not recorded (silently; they are not "quick").
- `use_count` is cheap and lets a later "most used" ordering happen without a migration.

### 5.2 Service and DAO

```java
public interface QuickFilterHistoryService {
    List<String> fetch(QuickFilterHistoryKey key);                 // current user, most recent first, ≤ limit
    void recordUse(QuickFilterHistoryKey key, String filterText);  // upsert; bump last_used_ms; trim to limit
    void clear(QuickFilterHistoryKey key);                         // current user
    int delete(UserRef userRef);                                   // all contexts — user deletion hook
}

// GWT-shared; what the client derives from a QuickFilterContext
public final class QuickFilterHistoryKey {
    String context;          // QuickFilterContext.key
    String dataSourceUuid;   // NullSafe.get(context.dataSource, DocRef::getUuid), or ""
}
```

- `recordUse` is `INSERT … ON DUPLICATE KEY UPDATE last_used_ms = ?, use_count = use_count + 1`
  followed by `DELETE … WHERE user_uuid=? AND context=? AND data_source_uuid=? AND id NOT IN
  (top N by last_used_ms)`.
  Trimming at write time keeps the table bounded at `users × contexts × N` with no job; the
  StoredQuery daily-clean job is not needed. Blank / whitespace-only text is ignored.
- `delete(UserRef)` is wired into `UserServiceImpl` beside `userPreferencesService.delete`
  (`:416`) so history dies with the user.
- **Decided:** limit from config `stroom.ui.quickFilter.historySize`, default **20**. Lives in `UiConfig`
  (it is already sent to the client via `UiConfigCache`, so the client can size the menu to it).
- Owning user is `SecurityContext.getUserRef()`; there is no admin view of other users' history.

### 5.3 REST

`QuickFilterHistoryResource` in `stroom-core-shared` (`RestResource, DirectRestService`),
`BASE_PATH = "/quickFilterHistory" + ResourcePaths.V1`:

| | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/fetch` | `QuickFilterHistoryKey` | `List<String>` |
| `POST` | `/record` | `RecordQuickFilterUseRequest { key, filterText }` | `void` |
| `POST` | `/clear` | `QuickFilterHistoryKey` | `void` |

`POST` with a body throughout rather than path/query parameters, because the key now has two parts
and the codebase's `DirectRestService` interfaces already favour request objects for anything
beyond a single id. All `@AutoLogged(OperationType.UNLOGGED)` — this is UI state, and the search
the filter drives is what gets audited. `context` is validated against `[a-zA-Z0-9_-]{1,64}` and
`dataSourceUuid` against empty-or-UUID; anything else is a 400. The server does not need to know
the set of valid contexts.

### 5.4 When a use is recorded

**Decided.** The debounce timer must **not** record — it would store `a`, `ab`, `abc`. A use is
recorded when the user *commits* a filter:

1. **Enter** in the text box (already a hard commit at `QuickFilter.onKeyDown:209`).
2. **Blur** of the text box when the text is non-blank and differs from the last recorded value.
3. Choosing an item from the drop-down (bumps it to the top).
4. OK from Advanced Query.

And only if the widget's `filterError == null` — the server told it the last text was accepted,
so rejected syntax never enters history. Because the debounced request may still be in flight at
blur time, the binder records on the *next* result callback when `filterError` is null, not
synchronously on blur; the presenter already routes `resultPage.getFilterError()` to
`setFilterError`, which is the hook.

Not recorded: filters set programmatically by the screen (`setText(text, false)`), and the
empty string.

### 5.5 Widget changes

`QuickFilter`:

- **Decided:** add `InlineSvgButton dropDownButton` (`SvgImage.DROP_DOWN`, title "Recent
  filters"), placed **between the clear and help buttons**: `[text][×][▼][?]`. `×` and `▼` both
  act on the text (like a combo box); `?` stays outermost as it is today on every screen. Hidden
  until a `QuickFilterContextHandler` is set, so the excluded surfaces (§4) are untouched.
- New hook interface, set by the presenter via the factory. **As built** the widget delegates
  the whole "show the list" step rather than just the fetch, because the widget module has no
  event bus to fire a menu from either:

  ```java
  public interface QuickFilterContextHandler {
      void showRecentFilters(QuickFilter quickFilter, Consumer<String> onSelect);
      void recordUse(String filterText);
  }
  ```

  The Advanced Query entry (C) will be a third method, `openAdvancedQuery(String, Consumer<String>)`.
- The commit/verdict bookkeeping lives in `QuickFilterHistoryTracker`, a plain class with no GWT
  types so it is unit tested (`TestQuickFilterHistoryTracker`). The widget feeds it `commit`,
  `verdict`, `chosen` and `cleared`.
- Click `▼` → `showRecentFilters` → factory fetches → `ShowMenuEvent.builder().items(…)`
  positioned under the box, auto-hide partner = the box (as `BaseSelectionBox.showPopup:146`):
  - one `IconMenuItem` per filter (text as label, ellipsised by CSS at ~60 ch; full text in
    `title`), click → `setText(text, true)` and `recordUse`;
  - `InfoMenuItem("No recent filters")` when empty;
  - `Separator`;
  - `IconMenuItem("Advanced Query…")` → `openAdvancedQuery(getText(), text -> setText(text, true))`.
  - **Alt+↓** on the text box opens the same menu (combo-box convention; the menu's own keyboard
    navigation then applies).
- Fetch happens on click, not on load — 16 screens × one GET at open would be wasteful and the
  list is stale the moment you use it anyway.
- `QuickFilterPageView` / `QuickFilterDialogView` gain a pass-through `setContextHandler(…)`.

Per-item delete and "clear history" are deliberately left out of the first cut; `DELETE` exists on
the resource so a "Clear recent filters" item can be added to the menu without server work.

### 5.6 Client factory

`stroom.quickfilter.client.QuickFilterContextHandlerFactory` in `stroom-core-client`
(`@Inject RestFactory`; C adds `Provider<AdvancedQuickFilterPresenter>`):

```java
view.setQuickFilterContextHandler(factory.create(CONTEXT, this, this));  // HasHandlers, TaskMonitorFactory
```

Sixteen call sites, one line each, next to the existing `registerPopupTextProvider` call. Nothing
about the criteria or the result handling changes; the text still flows to the server exactly as
it does today.

---

## 6. Advanced Query (pieces B and C)

### 6.1 The printer (B) — new server logic

`stroom.query.language.filter.QuickFilterPrinter` beside the parser. Contract, in the spec's
terms: `parse(print(tree)) ≡ tree` for every tree the dialog can produce, and
`print(parse(text))` is a canonical form of `text` for every string in the conformance corpus.

Rules:

| Tree | Text |
|---|---|
| `AND(a, b)` | `a b` (children separated by a space; nested `OR` bracketed) |
| `OR(a, b)` | `a or b` (bracketed when it is a child of `AND` or `NOT`) |
| `NOT(term)` | `!` folded into the term: `field:!^abc` |
| `NOT(operator)` | `not (…)` |
| term, field is a default field, condition is the field's default | bare value: `abc` |
| `OR` whose children are exactly one term per default field, each with **that field's** default condition (`defaultCondition(field)` — they can differ per field) and the same value | collapses back to the bare term `abc` — this is what the parser produced from it (`addTerms:389`) |
| term on a qualified field | `qualifier:value` — the `QueryField` name *is* the qualifier (`QuickFilterFields` javadoc) |
| condition ≠ default | `Condition.getOperator()` sigil before the value: `field:^abc`, `field:>=5` |
| value containing whitespace, `(`, `)`, or starting with a sigil / `!` / `~` / `\` | double-quoted, inner `"` escaped |
| disabled item (`enabled == false`) | no textual form — `format` fails with "disabled terms cannot be expressed as filter text" |
| `NOT_EQUALS` / `NOT_EQUALS_CASE_SENSITIVE` | `!=` / `!==` — spec §5.1 |
| `BETWEEN`, `IN`, `IN_DICTIONARY`, `IS_NULL`, doc-ref / user-ref conditions | not spellable — `format` fails naming the term |

`~` (chars-anywhere) is the one lossy corner: the parser rewrites `~abc` into
`MATCHES_REGEX a.*?b.*?c`, so the printer emits `/a.*?b.*?c`. Correct, round-trips, ugly.
Spec §5.2 already decided `~` becomes a real `CHARS_ANYWHERE` condition; when that lands the
printer emits `~abc` again. Not a blocker.

**Parser quirks the printer prints around** (found while building it; each is pinned by a test in
`TestQuickFilterPrinter` so that a parser fix shows up as a printer test change):

| Parser behaviour | Printer response |
|---|---|
| `!=` reads as `NOT(EQUALS)`, not `NOT_EQUALS` (spec §5.1 not yet in the parser) | `NOT_EQUALS` prints as `!=` and comes back as the equivalent negation; a *negated* `NOT_EQUALS` prints as `not !=abc` since `!!=abc` would read as `CONTAINS "!=abc"` |
| a lone `!` directly before a quoted value is not negation (`fieldValue.length() > 1` check) | `not "a b"` / `not name:"a b"` instead of `!"a b"` |
| the longest sigil wins, so `=` + value `=abc` reads as `==abc` | the value is quoted: `="=abc"` |
| keywords are only recognised after whitespace, `^` or `)` — never directly after `(` — and not after a `=` | a bracketed group opening with `not` is written `( not …)`; a value ending in `=` is quoted |
| `//` and `/*` open comments anywhere, and only survive in a value because the comment token is glued back on | any composed text containing them is quoted: `"http://example.com"`, `/"/tmp/.*"` |

Output is deterministic (children in tree order, single spaces, no trailing space) so the history
de-dup in §5.1 sees one spelling.

### 6.2 Endpoints (B)

On the existing `ExpressionResource` (`stroom-core-shared`), impl in
`ExpressionResourceImpl` (`stroom-query-impl`), both `UNLOGGED` like `validate`:

```
POST /expression/v1/parseQuickFilter
  ParseQuickFilterRequest  { text, defaultFields: List<QueryField>, qualifiedFields: List<QueryField> }
  ParseQuickFilterResult   { expression: ExpressionOperator | null, error: TokenError | null }

POST /expression/v1/formatQuickFilter
  FormatQuickFilterRequest { expression: ExpressionOperator, defaultFields, qualifiedFields }
  FormatQuickFilterResult  { text: String | null, error: String | null }
```

`parse` delegates to the existing `QuickFilter.parse` and converts `TokenException` with
`TokenErrorUtil` (both on the branch). The field lists ride on **both** requests exactly as
`ValidateExpressionRequest` does, so the server has no context registry to maintain. `format`
needs them as much as `parse` does: without knowing which fields are defaults and what each
field's default condition is, the printer cannot decide between `abc` and `name:+abc`, nor
collapse the default-field `OR` group (§6.1).

### 6.3 The dialog (C)

`AdvancedQuickFilterPresenter` in `stroom-core-client`, a thin OK/Cancel popup around
`EditExpressionPresenter`, modelled line-for-line on `ProcessorEditPresenter:120-124, 204-214`:

1. **Open:** `parseQuickFilter(currentText, ctx.defaultFields, ctx.qualifiedFields)`.
   - `expression` → `editExpressionPresenter.read(expression)`.
   - blank text → `read(AND())` (empty tree).
   - `error` → **decided:** open with an empty tree and the message at the top of the dialog
     ("The current filter could not be converted: …"). OK from here *does* overwrite, because the
     user has built something new; Cancel leaves the box untouched. This satisfies spec §10.3's
     explicit-message rule without a dead end.
2. **Fields offered:** `SimpleFieldSelectionListModel` over `ctx.qualifiedFields`, each
   `QueryField` copied with its `ConditionSet` **intersected with the printable set** (the 15
   parser sigils + `NOT_EQUALS` pair). Without this, `TermEditor` would happily offer `IN` on a
   `SQL_TEXT` field (the spec deliberately keeps `IN` in that set) and OK would then fail. Doing
   the intersection on the client is a few lines over GWT-visible types; the server `format` is
   still the backstop.
3. **OK:** `write()` → if unchanged from the tree parsed on open, hide with no change (spec §10.1
   — cancel-by-OK leaves comments and spacing intact). Otherwise `formatQuickFilter` →
   `onOk(text)` → widget `setText(text, true)` → normal change event → `recordUse` via §5.4.
   If the text is already non-blank and differs from what will be written, a one-line warning
   above the tree: "OK will replace the current filter text" (spec §10.2).
4. **Cancel:** nothing.

The `dataSource` argument to `EditExpressionPresenter.init` is `null` — these surfaces are not
datasources; `TermEditor` only uses it for doc-ref/dictionary pickers, which the intersection
removes.

For `FindUserCriteria` / `AdvancedDocumentFindRequest` (the two surfaces that AND the screen's
own expression with the user's text — `QuickFilter.and:75`) the dialog edits **only the user's
part**. The screen's own terms are not shown; they are not the user's to edit and they have no
text form.

---

## 7. Sequencing

| Step | Scope | Depends on |
|---|---|---|
| A1 | `QuickFilterContext` + `QuickFilterHistoryKey` (shared) for two surfaces: Dependencies, Global Properties | — |
| A2 | Table, DAO, service, resource, `UiConfig.quickFilterHistorySize`, user-delete hook, DAO test | — |
| A3 | Widget `▼` + menu + `QuickFilterContextHandler` + `QuickFilterHistoryTracker`; factory; wire the two surfaces | A1, A2 |
| A4 | Roll the factory wiring out to the remaining surfaces (table in §4), incl. traces/index-fields `withDataSource`. Each surface must also route `ResultPage.filterError` to the widget, or nothing is ever recorded (§5.4). **Done.** Audit of A4 found and fixed a second such gap: `ExplorerServiceImpl.find()` computed the filter error and dropped it, and `FindDocResultListPresenter` had no consumer, so the Find and Recent Items dialogs would never have recorded anything. Caveat remaining: the traces *server* never sets `TracesResultPage.filterError` (`TraceArchiveReader` builds its predicate without an error consumer), so on that screen a rejected filter is indistinguishable from an empty one and *will* be recorded — a server-side follow-up | A3 |
| B1 | `QuickFilterPrinter` + round-trip tests against the existing conformance corpus | — |
| B2 | `parseQuickFilter` / `formatQuickFilter` endpoints + request/result DTOs | B1 |
| C1 | `AdvancedQuickFilterPresenter`, condition-set intersection, "Advanced Query…" menu item live | A3, B2 |

**Decided: A, then B, then C**, in the order of the table. A1–A3 is a complete, demonstrable
feature on two screens; A4 rolls it out; B1–B2 add the printer and endpoints with round-trip
tests; C1 is the last mile.

Test obligations, matching how the branch already works: a DAO test for upsert/trim/ordering
(`DbTestModule`), round-trip tests for the printer over generated trees and over
`TestQuickFilterSurfaceConformance`'s sigil corpus, and a resource test for context validation.

---

## 8. Decisions taken (2026-09-18)

| Question | Decision |
|---|---|
| Context identity | `QuickFilterContext` value object (§4), keyed by **field set + data source** |
| Sharing across screens | Screens with the same field set *and* the same data source share history; traces and pathways are per Plan B document (§4) |
| Table home | `stroom-config-global-impl-db`, beside `preferences` (§5.1) |
| De-duplication | Case-insensitive, via the table's default collation (§5.1) |
| When to record | Enter, menu pick, dialog OK, and blur — all gated on the server having accepted the text (§5.4) |
| Widget layout | `[text][×][▼][?]` (§5.5) |
| History size | 20 per context, config `stroom.ui.quickFilter.historySize` (§5.2) |
| Parse error on opening Advanced Query | Open with an empty tree and a warning; Cancel is a no-op (§6.3) |
| Build order | A (history) → B (printer + endpoints) → C (dialog) (§7) |
