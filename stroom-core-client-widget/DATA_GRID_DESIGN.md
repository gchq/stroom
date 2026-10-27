# Data grid — requirements and design

**Status:** done (2026-10-21). Phases 0 to 2 were done in the `datagrid` worktree, and continued in
the components session 4 worktree (`session4_grids`, 2026-10-20), where the agreed row looks and
keyboard were added (`stroom-gwt/COMPONENT_MODEL_DESIGN.md`, Data grid). Those supersede R-S7 to R-S9,
R-A2 and R-T1 below, as each says. Phase 3 switched every screen to `DataGrid`, and phase 4
removed `MyDataGrid` and its classes and applied the §8 fixes (§9). What is left to do is in
`stroom-gwt/ISSUES.md`: The data grid's follow-ups, and the grid's entries under Controls that can't
be reached with the keyboard.

**Scope:** a new GWT data grid widget, separate from `MyDataGrid`, built from plain DOM and modern CSS
rather than on GWT's `cellview` classes. Once it is at parity, call sites switch over and the full
UI regression suite is run to find visual and behavioural differences.

**Sources.** This document draws on four sources:

* the current GWT grid (`stroom.widget.datagrid.client.MyDataGrid` and the forked GWT `cellview` classes);
* every place it is used (78 `new MyDataGrid` sites, about 83 screens);
* the React port's `DataGrid` (`stroom-ui-react/src/widgets/DataGrid/`);
* the React port's research notes (`stroom-ui-react/porting/grid-widget-design.md`,
  `grid-inventory.md`, `grid-column-audit.md`, `grid-sweep-findings.md`, `gwt-bugs.md`).

File references are relative to the repository root unless they say otherwise. Line numbers are
correct at the time of writing.

---

## 1. Why replace it

`MyDataGrid` extends GWT's `DataGrid`, which is built on `AbstractCellTable`, `AbstractHasData` and
`HasDataPresenter`. All three are **forked** into
`stroom-core-client-widget/src/main/java/com/google/gwt/user/cellview/client/` and patched in place.
The forks shadow GWT 2.13.0. This architecture has several costs:

* **Header and body are separate tables.** GWT `DataGrid` puts them in a `HeaderPanel` with a
  `CustomScrollPanel`, and keeps their column widths and horizontal scroll in step by hand.
* **The whole page is always rendered.** The dashboard table allows a page of up to 10,000 rows
  (`BasicTableSettingsViewImpl.java:72-74`), and `ImportConfigConfirmPresenter` uses a page of
  100,000 (`MASSIVE_LIST_PAGE_SIZE`). Every one of those rows is in the DOM.
* **Browser events are handled one tick late.** The forked `AbstractHasData.onBrowserEvent`
  (`AbstractHasData.java:744-776`) runs `onBrowserEvent2` inside `scheduleDeferred`. By then the
  native event has finished, so `preventDefault()` in the selection, keyboard and context-menu paths
  probably has no effect.
* **Column layout depends on timers and a global hook.**
  * The column fill runs six times, at 0 to 500 ms after attach (`FocusUtil.forceFocus`,
    `MyDataGrid.java:1059-1062`).
  * Hovering over a header registers a *global* native preview handler. While it is registered,
    every mouse-move in the document is cancelled (`MyDataGrid.java:663-665`).
* **Data is copied out of the DOM.** Copy, Export CSV and Ask AI read the rendered cells' text, so
  they only ever see what has been rendered.
* **There is a lot of state to keep in sync.** `ColSettings` lives in a list parallel to GWT's column
  list, `minWidth` is stored but never read, widths are parsed back out of `"NNpx"` strings
  (`getPx`), and header refresh is turned off so callers must call `redrawHeaders()` themselves.

The React port shows what a cleaner model looks like:

* a single scroller with a sticky header;
* a CSS grid column template;
* event delegation from the grid root;
* one widget with two rendering engines: **natural** (the whole page rendered, rows sized by their
  content) and **virtual** (a fixed row height and only the visible rows rendered).

GWT has no virtual DOM. It does not need one: a grid has a regular structure, so a keyed row cache
plus targeted DOM updates gives the same benefit (§4.3).

---

## 2. Goals and non-goals

**Goals**

1. **G1.** A new widget in its own package, alongside `MyDataGrid` and not changing it, until
   switch-over.
2. **G2.** Parity with every `MyDataGrid` behaviour listed in §5, unless a row in §8 records a
   deliberate deviation.
3. **G3.** Two rendering engines: natural (the default, with variable row heights) and virtual (fixed
   row height, for large pages).
4. **G4.** Switch-over should be mechanical. Presenters, `ColumnBuilder`/`DataGridUtil`, existing
   `Cell` implementations, `RestDataProvider`, `PagerView`, `MultiSelectionModelImpl` and
   `HeadingListener` keep working without being rewritten.
5. **G5.** No dependence on the forked `cellview` classes, so that once the switch is complete they
   can be deleted (except where `MyCellTable` still needs them; see §2 non-goals).
6. **G6.** Synchronous, delegated event handling. No timers or deferred commands are needed for
   correctness.
7. **G7.** Grid logic (column model, fill, sort cycle, selection rules, virtual window maths) lives in
   plain Java classes that do not depend on the DOM, so it can be unit-tested with JUnit.

**Non-goals for the first version**

* Replacing `MyCellTable`. Its 15 headerless lists (explorer tree, menus, choosers, find results) stay
  as they are. The new grid supports `hideHeader` (R-H12), so they can move later.
* Frozen (pinned) columns, row drag-and-drop, in-cell text editing and infinite scroll. None of
  these exist today (§6).
* Changing `PagerView`, `Pager` or `RestDataProvider`. The pager and toolbar stay outside the grid,
  as they are now.

---

## 3. How the current grid is used (summary)

| | Count |
| --- | --- |
| `new MyDataGrid` sites | 78 (76 in `stroom-core-client`, 2 in `stroom-statistics-client`) |
| distinct screens (counting subclasses of abstract bases) | ~83 |
| `addResizableColumn` | 72 files |
| `addColumn` (fixed width) | 44 files |
| `addAutoResizableColumn` (fill) | 26 files |
| multi-select / single-select / no selection | 30 / ~35 / ~11 |
| server sort (`addColumnSortHandler` + `CriteriaUtil`) / client sort | 33 / ~7 |
| server-paged (`RestDataProvider`) / client list (`setRowData`) | 46 / ~30 |
| `setMultiLine(true)` | 16 (`TaskManagerList` toggles it at runtime) |
| tick-box columns / select-all header | 21 / 7 |
| in-cell editors (`TickBoxCell`, `ValueSpinnerCell`) | 21 / 3 |
| expander (tree) columns | 8 |
| double-click opens something (`isDoubleSelect`) | 21 |
| header tooltips (`withToolTip`) | 35 files, 153 calls |
| `enabledWhen` (dimmed cells) | 24 files, 121 calls |
| `setRowStyles` | 3 (dashboard, query results, traces) |
| `setHeadingListener` (header menu, persisted widths and order) | 2 (dashboard `TablePresenter`, `QueryResultTablePresenter`) |
| `setTableName` (for Ask AI and CSV) | 75 files |
| page size other than 100 | 1000 ×4, 100000 ×1 (`ImportConfigConfirmPresenter`) |
| dashboard page size | user-configurable, 1–10,000 |

Usage falls into eight categories:

* **Small client-side lists in dialogs** (~27). Up to five columns, unpaged in practice. Editing is
  done in a dialog, never in a cell.
* **Server-paged admin lists** (~40). Cells with hover icons, action menus, percentage bars, and
  refresh with a spinner.
* **Dashboard and query result tables.** The most demanding case (§3.1).
* **Meta browser.** An id-set selection with a "match all" mode, separate from the row selection
  model; a tri-state select-all header; and a tie-breaker sort.
* **Editable grids.** Tick boxes and value spinners inside cells.
* **Multi-line grids.** 16 grids, plus per-cell wrapping in the dashboard table.
* **Tree-like grids.** Eight grids with an expander column whose width follows the maximum depth.
* **Grids with persisted column widths and order.** Two.

### 3.1 The hardest consumers

1. **Dashboard `TablePresenter` and `QueryResultTablePresenter`.**
   * Pages of up to 10k rows.
   * Variable row heights from per-column wrapping (`Format.wrap` → `white-space: normal`,
     `TablePresenter.java:862-875`).
   * A server-driven group tree.
   * Conditional-formatting CSS generated at runtime.
   * A live `<input>` and button in each header (`FilterCell`).
   * A header click menu that needs hit-testing (`ColumnsManager.onShowMenu`).
   * Column resize, move and auto-fit, persisted to settings.
   * A push data model with pause.
   * Every column removed and re-added on any settings change (`updateColumns`, `:1106-1130`).
2. **Interactive cells inside selectable rows.** Spinners, tick boxes, hover icons, action menus and
   command links must not select the row (the `EventCell` / consumed-events rules,
   `DataGridSelectionEventManager.java:89-149`).
3. **Meta browser.** Id-set selection kept across pages; a tri-state header; `redrawHeaders()`.
4. **Server Tasks** (`TaskManagerList`). Pages of 1000 rows, auto-refresh, tree expanders,
   and multi-line switched on and off at runtime.
5. **ImportConfigConfirm.** A page of 100k rows with a select-all header.

---

## 4. Architecture

### 4.1 Package and naming

Package `stroom.widget.datagrid.client`, main class `DataGrid<R>` (decided, §10). During
switch-over both `DataGrid` and `MyDataGrid` implement a `DataGridView<R>` interface.

* The pure-Java model classes go in `stroom.widget.datagrid.client.model`, in
  `stroom-core-client-widget` (decided, §10).
  * They use only JRE classes that GWT emulates. Nothing from `com.google.gwt.dom` or
    `com.google.gwt.user`.
  * GWT compiles them into the client, and JUnit runs them on the JVM (G7).

### 4.2 DOM structure

Everything is in a single scroll container. Native sticky positioning replaces the separate header
and body tables.

```
div.data-grid                       role=grid, tabindex, CSS vars: --data-grid__columns, --data-grid__row-height
 └ div.data-grid__scroller           overflow:auto; the only scroller
    ├ div.data-grid__head            position:sticky; top:0; z-index
    │  ├ div.data-grid__header-row         role=row (header cells, role=columnheader)
    │  └ div.data-grid__header-row--extra  optional second header row (dashboard filter inputs)
    ├ div.data-grid__spacer          virtual mode only: height = rowCount × rowH
    └ div.data-grid__body            natural: normal flow; virtual: translateY(windowStart × rowH)
       └ div.data-grid__row          role=row, data-row=<absolute index>, aria-rowindex, aria-selected
          └ div.data-grid__cell      role=gridcell, data-col=<index>
 └ div.data-grid__empty              empty-table message (outside the rows)
 └ div.data-grid__resize-handle / data-grid__resize-line / data-grid__move-ghost / data-grid__move-line   (overlay layer)
```

* **Column template.** `--data-grid__columns` holds the template, e.g. `"22px 200px 340.5px"`.
  * Header rows and body rows both use `display: grid; grid-template-columns: var(--data-grid__columns)`.
  * Resizing a column means writing one CSS custom property. No rows are re-rendered.
  * A column span is the cell's `data-grid__cell--span` class with its `--data-grid__span: N` (so the cell's `style` holds only a custom property) (R-R9).
* **Horizontal scroll.** Header and body are in the same scroller, so they always scroll together.
  No synchronisation code is needed.
* **Scrollbars: overlay thumbs (decided, §10).** The thumbs float over the grid area as they do
  today, but are built more simply than GWT's `CustomScrollPanel`.
  * `div.data-grid__scroller` stays a real native scroller (`overflow: auto`). Its own scrollbar is hidden
    with `scrollbar-width: none` and `::-webkit-scrollbar { display: none }`.
    * GWT's trick of making the scroller oversized and clipping it is not needed.
    * Wheel, trackpad, touch, keyboard and `scrollIntoView` scrolling all stay native.
  * Two overlay tracks with thumbs (`div.data-grid__vbar`, `div.data-grid__hbar`) sit in an overlay layer above
    the scroller.
    * The vertical track starts below the sticky header.
    * Thumbs are positioned with `transform`, read once per `requestAnimationFrame` from `scroll` and
      `ResizeObserver` events.
    * As a progressive enhancement, CSS scroll-driven animations (`animation-timeline: scroll()`)
      can position the thumb with no script where the browser supports them. The script path stays
      as the fallback.
  * Dragging a thumb uses pointer events with `setPointerCapture` and writes `scrollTop` or
    `scrollLeft`. Clicking the track pages by one viewport.
  * The thumb's look (size, colours, hover and drag states) matches today's CSS, taken from theme
    variables. Thumbs are shown only when the content overflows.
  * In virtual mode, the thumb size and position come from the spacer height, so they are correct
    even though only a few rows are rendered.
* **Row height.**
  * Natural mode: rows are content-sized.
  * Virtual mode: rows are fixed at `--data-grid__row-height` with `overflow: hidden`.
  * The default row height is **derived from the current density at runtime** (measured once from a
    probe row), not hard-coded. The density settings `--row__padding--vertical` and
    `.stroom-density-compact` change it.

### 4.3 Rendering without a virtual DOM

The renderer is a small, explicit reconciler rather than a general diff:

1. **Row cache.** `Map<Object key, RowEntry>` keyed by `ProvidesKey` (falling back to the row
   object).
   * `RowEntry` holds the row element, the last row value, and the last render inputs (column
     generation and style-names hash).
2. **New data** (`setRowData(start, values)`) works out the window of rows to render:
   * natural mode renders the whole page;
   * virtual mode renders the visible rows plus a buffer.

   For each row in the window:
   * Unchanged key, equal value and the same column generation: **reuse the element as it is**.
     Only its position and the classes for its index (even/odd, selected) are updated.
   * Otherwise, re-render the cells into the existing row element. The cell HTML is built with
     `SafeHtmlBuilder` (needed for compatibility with `Cell<C>`) and set with `innerHTML` on the row.

   Rows that left the window are removed, or **recycled into a pool** in virtual mode.
3. **Updates that only change state.** Selection, keyboard row, hover and focus never re-render
   cells. They toggle a class on the row element (`data-grid__row--selected`, `data-grid__row--kbd`). The forked
   `HasSelection.hasSelectionChanged` hack (`HasDataPresenter.java:898-913`) is no longer needed.
4. **Batching.** Changes are coalesced into one `Scheduler.scheduleFinally` pass. This matches what
   `HasDataPresenter` does now: `flush()` forces the pass and is called from 67 places, so it must
   be kept. Rendering happens once per event-loop turn.
5. **Column structure changes** (add, remove, move, insert) bump a *column generation* number. That
   re-renders the header and every row in the window. Changing a width does **not** bump it.

### 4.4 Two engines

| | **Natural** (default) | **Virtual** |
| --- | --- | --- |
| rows in DOM | the whole page | visible window + buffer (5 rows each side) |
| row height | content-sized; cells may wrap | fixed `rowHeight` |
| scroll extent | the body's real height | `rowCount × rowHeight` spacer |
| locate row *i* | measured from the DOM (`data-row`) | `i × rowHeight` |
| used by | ~79 screens | dashboard table, query results, meta list, import confirm |

* **Precedence rule** (from React §6c): `effectiveVirtual = virtual && !wrapping`.
  * `wrapping` is true when `multiLine` is on **or any visible column wraps** (dashboard
    `Format.wrap`).
  * It is a live, recalculated property, not one fixed at construction.
  * The React port only considered `multiLine`. The dashboard's per-column wrap means the check has
    to be per column.
* **Scroll anchor across a switch.** Switching modes keeps the **top visible row** in place, not the
  pixel offset. The anchor is captured *before* the DOM changes and applied once the new mode has
  laid out (React `VirtualMultiLineToggleKeepsPosition`).
* **Wrapping columns switch virtual off (decided, §10).** Any wrapping column, or multi-line, makes
  the table render naturally, which is parity with GWT.
* **Later phase:** virtual mode with variable row heights (estimated heights plus a measurement
  cache), so a wrapping dashboard table with 10k rows can be virtualised. Not in the first version.

### 4.5 Events

* **One delegated listener per event type on the grid root, handled synchronously.**
  * Event types: `mousedown`, `click`, `dblclick`, `contextmenu`, `keydown`, `mousemove`,
    `focusin`, `focusout`, `input`, `change`.
  * Each listener resolves the row and column with `closest('[data-row]')` and
    `closest('[data-col]')`, then dispatches.
  * There is no global `NativePreviewHandler`. Column resize and move use **pointer events with
    `setPointerCapture`**, so no glass panel is needed. If glass is needed over iframes, it is shown
    only while dragging.
* **Cell events.** Events whose target is inside a cell go to the column's `Cell.onBrowserEvent`, but
  only if the cell lists the event in `getConsumedEvents()`. This is the same contract as now, so
  `TickBoxCell`, `ValueSpinnerCell`, `ExpanderCell`, `ActionMenuCell` and `CopyTextCell` work
  unchanged (§4.6).
* **Row selection opt-out.** A mouse-down that a cell consumes, by the existing rules, does not
  select the row. Markup can also opt out with a `data-dg-no-select` attribute.

### 4.6 Compatibility layer (G4)

The new widget implements the same contracts the presenters already use, so switch-over is
mostly changing a type:

* **`HasData<R>`** (and therefore `HasRows`, `HasCellPreviewHandlers`, `HasKeyboardSelectionPolicy`).
  `AsyncDataProvider`, `RestDataProvider`, `ListDataProvider` and `Pager` then work unchanged.
* **Column objects.** Takes GWT `Column<R,C>` and `Header<H>` objects.
  * Existing `Cell<C>` implementations render through `Cell.render(Context, value, SafeHtmlBuilder)`
    and receive events through `Cell.onBrowserEvent(Context, parent, value, event, updater)`.
  * `parent` is the `data-grid__cell` element.
  * `ColumnBuilder`, `HeadingBuilder` and `DataGridUtil` keep working.
* **`MyDataGrid` method set.** All the `MyDataGrid` methods listed in §5.12. The plan is to extract an
  interface `DataGridView<R>` that **both** widgets implement, so presenters can be switched by
  changing one type, or one factory, at a time.

---

## 5. Required behaviours

Each requirement has an ID so it can be tracked and tested. The **Source** column says where the
current behaviour is implemented: **G** = GWT `MyDataGrid` or the fork, **R** = React port only.
A requirement marked **R** goes beyond GWT and needs sign-off.

### 5.1 Layout and sizing

| ID | Requirement | Source |
| --- | --- | --- |
| R-L1 | The grid fills its parent (`100% × 100%`, `dataGridWidget`) and lays itself out again when the parent resizes, using a `ResizeObserver` (today `GlobalResizeObserver` → `onResize`). | G `MyDataGrid.java:183` |
| R-L2 | The header stays visible during vertical scroll and moves with the body during horizontal scroll. | G |
| R-L3 | If the columns are wider than the grid, it scrolls horizontally. Widths that overflow are left as they are. Columns never shrink to fit. | G |
| R-L4 | **The table width is always the sum of the column pixel widths** (`resizeTableToFitColumns`, `:1068-1106`). If the columns are narrower than the grid, the space to the right is left empty. Columns are **never** scaled to fill the grid. (The React port's "stage 2" proportional scaling, `width: 100%` + `table-layout: fixed`, is explicitly rejected.) Only percent and fill columns (R-C3, R-C4) use the grid's width, and only once, to work out their starting pixel widths. | G (confirmed) |
| R-L5 | A column width of 0 is treated as 1px (an expander column with nothing to expand). | G `:1110-1130` |
| R-L6 | Row height follows the density setting (`--row__padding--*`, `.stroom-density-compact`). Virtual-mode row height is taken from the active density, not hard-coded. | G CSS |
| R-L7 | The scroll position is kept when the grid is detached and re-attached (for example when switching tabs). | G `:129-139` |
| R-L8 | The empty and loading states keep the header visible at full column width. | G |

### 5.2 Columns

| ID | Requirement | Source |
| --- | --- | --- |
| R-C1 | **Fixed column**: `addColumn(col, name[, width])`. Default width 200. Not resizable, not movable. | G `:912-922` |
| R-C2 | **Resizable column**: `addResizableColumn(col, name/header, width)`. Resizable **and** movable. | G `:928, :992` |
| R-C3 | **Fill column**: `addAutoResizableColumn(col, name/header, [fillWeight=100], initialMinimumWidth)`. Resizable and movable. Takes up the remaining space on the first layout, and that pixel width becomes its starting width. The declared width is a floor: fill only ever grows a column, never shrinks it. Usually the last column. Several fill columns share the space by weight (Dependencies has 4, DocRefList and PropertyList have 2 each). | G `:953-990` |
| R-C3a | **Percent column** (new): a width given as a percentage of the grid's **initial visible width**. It is converted to pixels once, on the first layout, and is a plain pixel column from then on. It does not track later resizes. | New |
| R-C4 | **Initial width resolution runs once, on the first layout with a non-zero width**, so a grid built in a hidden tab waits until it is shown. Order: (1) fixed columns use their declared pixels; (2) percent columns become `(int)(outerWidth × pct / 100)`; (3) fill columns share what is left. If steps 1–2 already overflow the grid, fill columns keep their declared floor and the grid scrolls horizontally. `remaining = outerWidth − Σ(widths after steps 1–2, including fill floors)`. If `remaining > 0`, each fill column gets `declared + (int)(remaining / Σweight × weight)`, **truncated, not rounded** (rounding was a source of drift found by the React port). After that, widths belong to the user. A container resize does **not** run the fill again. (Today it runs six times on timers; one run on the first non-zero `ResizeObserver` measurement replaces that.) | G `:1059, :1132-1151` |
| R-C5 | `resizeTableToFitColumns()` is kept as a public method. Callers use it after rebuilding columns (`TablePresenter`, `QueryResultTablePresenter :731, :917`). | G |
| R-C6 | `removeColumn`, `insertColumn`, `moveColumn(from, to)`, `resizeColumn(index, width)`, `setColumnWidth(col, width, unit)`, `getColumnCount`, `getColumn(i)`. Per-column settings move with their column. They are stored **on a column model entry**, not in a parallel list. | G `:1001-1038` |
| R-C7 | Columns can be rebuilt from scratch: remove all and add all, often. This must not flicker, must keep the vertical scroll position, and must keep the horizontal position when it is still valid. | G (dashboard) |
| R-C8 | Header contents can be a `String`, which is **treated as HTML** for compatibility (for example the `"<br/>"` headers on icon columns), or a `Header<?>`, including `SafeHtmlHeader` and `CompositeCell` headers. | G |
| R-C9 | Per-column settings from `ColumnBuilder`: right or centre alignment, vertical alignment, `withStyleName`, `withConditionalStyleName`, `enabledWhen` (adds `dataGridDisabledCell`), `withFieldUpdater`, `withBrowserEventHandler`, `withSorting(field[, ignoreCase])`, `defaultSortAscending`. | G `ColumnBuilder` |
| R-C10 | Per-header settings from `HeadingBuilder`: alignment classes (`.center-align` / `.right-align`), `withToolTip` (the `title` attribute). | G `HeadingBuilder` |
| R-C11 | Size constants (`ColumnSizeConstants`: CHECKBOX 22, ICON 24, SMALL 70, MEDIUM 100, BIG 400, DATE 200, UUID 320, …) are unchanged. | G |

### 5.3 Column resize and auto-fit

| ID | Requirement | Source |
| --- | --- | --- |
| R-CR1 | Moving the pointer over a header shows a 10px resize handle on the right edge of the column under it. If the pointer is within 10px of the left edge and nearer the left than the right, and the previous column is resizable, the handle is on the previous column's right edge instead. If the hovered column is not resizable, the handle goes to the previous column if that one is resizable. | G `ResizeHandle.update :64-110` |
| R-CR2 | Dragging the handle shows a 2px vertical guide line the full height of the grid. Columns **reflow live** while dragging (decided, §10; GWT applies the width on release). Each move is one `--data-grid__columns` write per animation frame. `HeadingListener.resizeColumn` is still called **once, on release**, so persisted settings are not written on every frame. Minimum width is the column's own minimum (R-CR7). | G `ResizeHandle` / changed |
| R-CR3 | New width = **rendered** width + drag distance, written back as the declared width. Rendered and declared widths are the same, because nothing is scaled (R-L4). | G |
| R-CR4 | Double-clicking the handle sizes the column to fit its content: the largest of the header text and every rendered cell's text, whitespace collapsed, measured in an off-screen element with the grid's font (`dataGridCell-text-measurement`), plus padding. Never below 30. In virtual mode, measure rendered rows **plus** a sample taken from the data (§8, D5). | G `:699-716, getMinHeaderWidth :800-825` |
| R-CR5 | `setAllowResize(boolean)` (grid-wide) and `ColSettings.resizable` (per column). | G |
| R-CR6 | When a resize ends, call `HeadingListener.resizeColumn(colIndex, width)` so the width can be persisted (dashboard, query). Call `onMoveStart` / `onMoveEnd` around the drag. | G |
| R-CR7 | **Per-column minimum width, default 30px** (decided, §10). Resize, auto-fit and fill never take a column below its minimum. Columns with no minimum set use 30 (`MIN_COL_WIDTH`). `ColSettings.minWidth`, which is stored but never read today, becomes this setting. | changed |

### 5.4 Column reorder

| ID | Requirement | Source |
| --- | --- | --- |
| R-CM1 | Pressing on a movable header and dragging more than 10px horizontally starts a move, provided `allowMove` is true and the press was not on an `<input>` (`getHeading :886`). | G `MoveHandle :86-91` |
| R-CM2 | During the move, a translucent full-height ghost follows the pointer, and a 2px line marks the insertion point, which is worked out from column midpoints. The resize handle is hidden. Dropping a column directly after itself does nothing. | G |
| R-CM3 | The insertion point cannot be before the first movable column. **Non-movable columns later in the table are not protected today.** Fix this, or match it (§8, D6). | G |
| R-CM4 | On drop, call `HeadingListener.moveColumn(from, to)` **before** the column model changes (current order), then move the column and its settings. `onMoveStart` / `onMoveEnd` are called around the drag. | G `:1009-1027` |
| R-CM5 | `setAllowMove(boolean)` can be switched at runtime (dashboard design mode, `TablePresenter.java:1453-1458`). | G |

### 5.5 Header

| ID | Requirement | Source |
| --- | --- | --- |
| R-H1 | **Sort cycle per column:** unsorted → default direction (`isDefaultSortAscending`) → reversed → unsorted. A plain click replaces the sort list. **Ctrl+click** adds this column to or removes it from a multi-column sort. Enter on a focused sortable header behaves like a click. | G fork `AbstractCellTable :1903-2010` |
| R-H2 | A sort click updates the `ColumnSortList` and fires `ColumnSortEvent` (34 handlers). Headers then redraw themselves. `Header.onPreviewColumnSortEvent` can veto the sort. | G |
| R-H3 | Sort indicator: an ascending or descending SVG (`column-sortIcon`) containing the 1-based sort position (`column-sortOrder`), **shown even for a single sort** (kept as today, decided §10). `isSortIconStartOfLine` places it before the title. | G fork `SortIcon`, `DefaultHeaderOrFooterBuilder :166-216` |
| R-H4 | `sort(col)` pushes the column onto the sort list **without** firing an event (initial sort, 14 sites). Calling it again on the current first column flips its direction (GWT `push` semantics). `clearColumnSortList()`. | G `:940, :1174` |
| R-H4a | **Sorting stays in the data model; the grid only fires sort actions (decided, §10).** The grid owns the `ColumnSortList` (what is sorted and in which direction) and fires `ColumnSortEvent`. Ordering the rows is the job of whatever supplies the data: server sort through `CriteriaUtil` + `RestDataProvider`, and client sort through the presenter's `ListDataProvider` + `DataGridComparatorFactory`. Both use the same mechanism. The grid has **no** built-in client-side sorting. | G |
| R-H4b | **Default sort (D10).** `setDefaultSort(ColumnSortInfo...)` declares a screen's default sort on visible columns. It is (1) shown in the header from the start without firing an event, (2) used for the first request, and (3) **restored when the user's clicks leave the sort list empty** (the third click on the only sorted column). On a restore, the grid fires `ColumnSortEvent` with the default list, so what the header shows is always what the data model applies. Ctrl+click removing one of several sorted columns does not restore the default. Grids with no default go back to an empty list, and the data model then uses its natural order (D7). | changed |
| R-H4c | **Tie-breaker sorts on hidden fields** (e.g. the meta browser's Id) are not part of the grid's sort state. The presenter appends them when building the request, as `MetaListController` does today. They are never shown in a header and can never be clicked. | G |
| R-H5 | Header classes must be kept (CSS and tests depend on them): `dataGridHeader`, `dataGridFirstColumnHeader`, `dataGridLastColumnHeader`, `dataGridSortableHeader`, `dataGridSortedHeaderAscending/Descending`, `dataGridSortableHeaderOuterDiv`, `dataGridSortableHeaderNameHolder`, plus `Header.getHeaderStyleNames()`. | G |
| R-H6 | **Header click menu:** a primary-button press and release on a header that did not turn into a resize or move calls `HeadingListener.onShowMenu(event, headingSupplier)`. Requires `allowHeaderSelection` and a listener. The event target must let the listener hit-test the value-filter icon and the filter-disable button (`ColumnsManager.java:137-213`). | G `:749-756` |
| R-H7 | `setAllowHeaderSelection(boolean)` toggles `allow-header-selection` on the header (header text becomes selectable, plus the dashboard styling hooks). | G `:1188` |
| R-H8 | **Interactive header cells:** a header `Cell` can contain live `<input>` and `<button>` elements (dashboard `FilterCell`). Typing, `change` and `keyup` events reach the cell. Clicking in the header **does not move focus to the grid** (gh-4684, `allowFocus`). Keys typed in the input do not trigger grid navigation. | G fork `AbstractCellTable :2743-2753` |
| R-H9 | Headers redraw only when asked (`redrawHeaders()`, used by the meta browser's tri-state header), when a sort changes, or when columns change. Header auto-refresh stays off. | G `:104-143` |
| R-H10 | The header height follows its content (a tooltip wrapper or a second filter row makes it taller). The grid tracks the real header height with a `ResizeObserver` and never assumes a constant. | R (fixes a React bug) |
| R-H11 | A select-all tick box in a header is an ordinary `Header<TickBoxState>` with an updater, not a grid feature. Supported through R-H8 and R-H9. | G |
| R-H12 | `setHideHeader(boolean)`: no header row (so `MyCellTable` lists can move over later). | R |
| R-H13 | Long header text is cut off with an ellipsis. In multi-line mode, header text wraps. | G CSS `.multiline .dataGridHeader > div` |

### 5.6 Rows and cells

| ID | Requirement | Source |
| --- | --- | --- |
| R-R1 | Row classes: `dataGridEvenRow` / `dataGridOddRow`, `dataGridSelectedRow`, `dataGridKeyboardSelectedRow`, plus `RowStyles.getStyleNames(row, index)`. Cell classes: `dataGridCell`, `dataGridFirstColumn` / `dataGridLastColumn`, `dataGridKeyboardSelectedCell`, plus `Column.getCellStyleNames`. **Keep the legacy class names, at least alongside new `data-grid__*` ones**, so `app.css`, `dashboard.css`, conditional-formatting CSS and the regression suite keep working. | G fork `:2144-2200` |
| R-R2 | `setRowStyles(RowStyles<R>)`. Classes can be generated at runtime (`ConditionalFormattingDynamicStyles`). | G |
| R-R3 | Hover highlight in CSS (`--row__background-color--hovered`). The GWT check for "a floating element is over the row" (`:2021-2050`) is not needed with CSS `:hover`. | G |
| R-R4 | By default cells do not wrap: `nowrap`, ellipsis, `user-select: text`. | G CSS |
| R-R5 | `setMultiLine(boolean)` at runtime: adds or removes `multiline`; cells top-aligned and wrapping (header too). Turning it on in a virtual grid suspends virtualisation (§4.4). | G `:174` |
| R-R6 | Per-cell inline style from a `Cell`'s HTML (dashboard wrap, colours, bold for grouped columns) is shown exactly as rendered. A cell that sets `white-space: normal` counts as wrapping for R-R5's virtual-suspend rule (declared on the column with `setWraps(boolean)`, not detected from the HTML; §4.4). | G `TablePresenter :862-875` |
| R-R7 | `dataGridDisabledCell` (`enabledWhen`) styling, including on selected rows. | G |
| R-R8 | `redraw()`, `redrawRow(index)`, `getRowElement(index)` (returns the `data-grid__row` element, or null if not rendered), `getVisibleItem(s)`, `getVisibleItemCount`. | G |
| R-R9 | **Column span per row** (`MarkerList` Summary rows span five columns). Provided by a `RowSpans` callback that returns a column → span map. | G fork / R |
| R-R10 | Expander and tree cells (`ExpanderCell`: indent 20px per level, DOT / ARROW icons) work unchanged. An expander column width of `maxDepth×20+25`, or 0, is set by `TreeRowHandler` or the presenter, and the grid applies it immediately (R-L5). | G |

### 5.7 Selection and keyboard

| ID | Requirement | Source |
| --- | --- | --- |
| R-S1 | `addDefaultSelectionModel(allowMultiSelect)` returns a `MultiSelectionModelImpl<R>`. `setSelectionModel(model, handler)` is supported. With no selection model, rows cannot be selected and the cursor is the default arrow. With one, the cursor is a pointer. | G `:153-172` |
| R-S2 | **Mouse:** only the primary button selects. Right-click **never** changes the selection. | G `DataGridSelectionEventManager :93-162` |
| R-S3 | **Click rules** (`doSelect :164-201`): a plain click selects only that row and moves the anchor to it. Ctrl+click toggles the row and moves the anchor. Shift+click selects from the anchor to the row, and Ctrl+Shift adds that range to the existing selection; the anchor stays put. If the anchor is not on the current page, Shift+click behaves like a plain click or a Ctrl+click. Single-select mode ignores modifiers. | G |
| R-S4 | **Double-select:** a second press on the same row (by `equals`) within 500ms fires `MultiSelectEvent` with `isDoubleSelect()`. This happens even when the selection did not change. 21 screens use it to open items. | G `DoubleSelectTester` |
| R-S5 | **Cell consumption:** no row selection if the press is inside a cell whose `Cell` is an `EventCell` with `isConsumed(event)`, or whose consumed events include click, mousedown or mouseup. A press directly on the `<td>`-equivalent, rather than on a child, is never consumed. Also `data-dg-no-select`. | G `:89-149` |
| R-S6 | **Hyperlinks:** a primary press on `<u link="…">` fires `HyperlinkEvent` (through `addHyperlinkHandler`) and does not select. | G |
| R-S7 | **Keyboard row** (`setKeyboardSelectedRow(i[, stealFocus])`, `getKeyboardSelectedRow`) is separate from the selection. It is shown with `dataGridKeyboardSelectedRow` and (superseded: keyboard focus only, a ring inside the row, cell or header, `data-grid.css`) a `:focus-within` box shadow. Moving it scrolls the row into view (`nearest`, smooth) and focuses it **without** scrolling (`preventScroll`). This is `FocusUtil.focusRow`. | G fork |
| R-S8 | **Superseded** by the grid keyboard (`DataGridKeyboard`, see `COMPONENT_MODEL_DESIGN.md`, Data grid): row focus with the selection following it, cells and headers by the arrows, editing with Enter/F2. What GWT does: **Keys** (`KeyBinding` → `Action`): Up/W/K and Down/S/J move by 1. PageDown moves by 10. Home and End go to the first and last row. Enter selects only the row (`onExecute`). Space selects with the Ctrl/Shift state. Ctrl+A fires `SelectAllEvent`; the grid selects nothing itself. Left/Right, Esc and Alt+ContextMenu do nothing in the grid. Movement skips rows where `isSelectable(item)` is false. Keys from inside `<input>`, `<textarea>` or `contenteditable` are ignored, except Esc. | G `KeyBinding :65-78`, `AbstractSelectionEventManager` |
| R-S9 | **PageUp** moves up 10 in the grid keyboard (D2 applied). GWT: consumed, but **does nothing** today (`onMovePageUp` is never called, `:62-64`). Match it, or fix it to move up 10 (§8, D2). | G (bug) |
| R-S10 | Selection works through `SelectionModel` (`SelectionChangeEvent`, plus `MultiSelectEvent(SelectionType)`), unchanged, so the 51 `isDoubleSelect` consumers and the meta browser's id-set selection keep working. | G |
| R-S11 | Focus: `setFocused(boolean)` (fork addition, `TablePresenter:1379`). Focus is restored after a redraw only if the grid had it before. Clicking the header does not take focus (R-H8). The grid root is in the tab order once a selection model is set. | G fork |
| R-S12 | The keyboard row after `setRowData` matches GWT (measured in Phase 0, `Widgets/Data Grid/Behaviour` stories). It starts at 0, and is 0 on an empty page. When rows arrive it follows its row's key to the nearest row with that key. If that key has gone (e.g. the row's values changed and there is no key provider), it goes back to row 0. Its style (`dataGridKeyboardSelectedRow`, `dataGridKeyboardSelectedCell`) is shown only while the grid's rows have the focus. Clicking a row focuses it. If the focused row is rewritten or removed, the keyboard row takes the focus. A refresh never takes the focus from outside the grid. | G |

### 5.8 Context menu, copy, export, AI

| ID | Requirement | Source |
| --- | --- | --- |
| R-M1 | Right-click opens the built-in menu, unless `hasContextMenu(false)` (1 site). Menu order: (1) cell items from a `Cell` implementing `HasContextMenus`; (2) link items if the target is in an `<a href>` (not `javascript:`) or a `[link]`: Copy Link URL, Follow URL, and for dashboard links Open In New Tab / Open In This Tab; (3) a **Copy** submenu: Cell, Row, Selected Rows, Column, Column For Selected Rows, Table; (4) **Export Table** (`table.csv`); (5) **Ask Stroom AI About** (Cell, Row, Selected Rows, Column, Column For Selected Rows, Current Page). Every item except Table needs a cell. | G `:212-441`, `MyDataGridAiSupport` |
| R-M2 | The native browser menu is reliably suppressed. This works because events are handled synchronously (G6). | G (intent) |
| R-M3 | Cell text for copy, export and AI: whitespace collapsed, newlines become spaces, CSV values containing `,` or `"` are quoted. Leading columns with blank header text (tick box, expander, icon) are skipped (`getColumnOffset`). | G `:443-623` |
| R-M4 | **Text comes from the data, not the DOM.** Render the cell into a detached `SafeHtml` and take its text. This makes copy and export correct in virtual mode, where the React port only exports the rows that are rendered. The scope stays **the current page**, as in GWT. | R improvement |
| R-M5 | `setTableName(String)` / `getTableName()` names the table in AI and export descriptions. | G |
| R-M6 | The cell and row the menu acts on are the ones under the pointer. Right-clicking the header or empty space acts with row and column −1. | G |

### 5.9 Data, paging, loading

| ID | Requirement | Source |
| --- | --- | --- |
| R-D1 | Full `HasData<R>`: `setRowData(start, values)`, `setRowCount(count, exact)`, `setVisibleRange(start, length)`, `getVisibleRange`, `setPageSize`, `RangeChangeEvent`, `RowCountChangeEvent`, `LoadingStateChangeEvent` (2 handlers). Constructors take the page size (default `DEFAULT_LIST_PAGE_SIZE = 100`, `MASSIVE_LIST_PAGE_SIZE = 100000`). | G |
| R-D2 | Works with `PagerPanel` (was `PagerView`) / `Pager`: the pager calls `setDisplay(grid)`, and the "from to of", "?" for an inexact count, and typed-range behaviours are unchanged. | G |
| R-D3 | Pushed data with a range-change feedback guard (dashboard `ignoreRangeChange`). Setting rows must not fire `RangeChangeEvent` unless the range changed. | G |
| R-D4 | `flush()` applies pending changes synchronously (67 sites). `redraw()` re-renders. | G |
| R-D5 | The empty state is **blank by default**, as today (decided, §10). Screens can opt in with `setEmptyMessage(String)` or `setEmptyTableWidget(Widget)`. Errors are still shown in the pager's `MessagePanel`. | G |
| R-D6 | The loading state stays the pager's refresh spinner (`PagerPanel.createTaskMonitor`, was `PagerView`'s). The grid's own loading widget stays blank. | G |
| R-D7 | The vertical scroll position matches GWT (measured in Phase 0). It is kept when the same rows arrive again (a poll), when every row's values change, and when the columns are rebuilt. A page too short to scroll is at the top. A polling refresh never moves it. | G |
| R-D8 | `scrollToRow(index)` / reveal: scroll a row into view in either mode (needed by keyboard moves and by presenters that select an item after loading). | G / R |

### 5.10 Accessibility

| ID | Requirement | Source |
| --- | --- | --- |
| R-A1 | `role=grid`, `aria-rowcount` (the full count in virtual mode), `aria-colcount`, `role=row` with `aria-rowindex`, `role=columnheader` with `aria-sort`, `role=gridcell`, `aria-selected` when a selection model is set, `aria-colspan`. | R |
| R-A2 | Headers are reached with the grid's keys (Up from a first-row cell), not their own tab stops; Enter or Space on one does what a click does (sort, or the column menu). | changed |

### 5.11 Theming

| ID | Requirement | Source |
| --- | --- | --- |
| R-T1 | All colours come from existing theme variables (`--row__background-color--even/odd/hovered/selected/selected--focussed`, `--row__text-color--selected`, `--focus-border`, `--focus-ring__color`, `--hover__transition`, header and panel border variables), so light and dark themes both work. A selected row shows no mouse-over (`data-grid.css`). | changed |
| R-T2 | The resize handle (`#A49AED`, opacity .3), resize line, move ghost and move line keep their look. | G CSS |

### 5.12 Public API to provide (from `MyDataGrid` and inherited methods in use)

Constructors `(HasHandlers eventBus[, int pageSize])`.

| Area | Methods |
| --- | --- |
| Columns | `addColumn` (×3 overloads), `addResizableColumn` (×2), `addAutoResizableColumn` (×3), `removeColumn`, `insertColumn`, `moveColumn`, `resizeColumn`, `setColumnWidth`, `getColumnCount`, `getColumn` |
| Layout | `resizeTableToFitColumns()` and `resizeTableToFitColumns(boolean)`, `setMultiLine` |
| Header | `redrawHeaders`, `setHeadingListener`, `setAllowMove`, `setAllowResize`, `setAllowHeaderSelection`, `addColumnSortHandler`, `getColumnSortList`, `sort`, `clearColumnSortList`, `setDefaultSort` (new) |
| Selection | `addDefaultSelectionModel`, `setSelectionModel(model[, handler])`, `setKeyboardSelectedRow(i[, stealFocus])`, `getKeyboardSelectedRow`, `setFocused` |
| Rows | `setRowStyles`, `redraw`, `redrawRow`, `flush`, `getRowElement`, `getVisibleItem(s)`, `getVisibleItemCount` |
| Data | the `HasData` methods (R-D1), `addRangeChangeHandler`, `addRowCountChangeHandler`, `addLoadingStateChangeHandler` |
| Other | `addHyperlinkHandler`, `hasContextMenu`, `setTableName` / `getTableName`, `setEmptyTableWidget`, `setSkipRowHoverCheck` (no-op) |

**Not carried over:** `getTableHeadElement()`, which only widens visibility. `TablePresenter.getFilterButton`
reaches into `thead > tr > th` (`:755-765`); that needs a supported replacement, `getHeaderCellElement(col)`.

**New:** `addPercentColumn(col, name/header, percent)` (resizable and movable, R-C3a), `setVirtual(boolean)`, `setRowHeight(int)` (virtual mode), `setHideHeader(boolean)`,
`setRowSpans(...)`, `scrollToRow(int)`, `getHeaderCellElement(int)`.

---

## 6. Things that are *not* needed (checked)

* **Frozen or pinned columns, column hide and show inside the grid** (the dashboard hides columns by
  not adding them), **row drag-and-drop** (rows are reordered with Up/Down toolbar buttons),
  **infinite scroll or "show more"**, **footers**. (In-cell text editing was on this list, but the
  floor map settings now have text field and selection box cells, so the grid keyboard edits them.)
* **`EndColumn` / `addEndColumn`.** Already deleted. Do not reintroduce it.
* **`ColSpec`.** Barely used (2 sites, both in `DataGridUtil`). It can be dropped or kept as a thin
  builder.
* **The `DoubleClickEvent` class** in the grid package. Never used.

---

## 7. Performance targets

| Scenario | Target |
| --- | --- |
| Dashboard table, 10,000 rows, no wrapping (virtual) | first render < 100ms; scrolling at 60fps; DOM rows ≈ visible + 10 |
| ImportConfigConfirm, 100,000 rows (virtual) | first render < 150ms; select all < 100ms |
| A 100-row page pushed again with 5 changed rows | only the 5 rows re-render (checked with a render counter) |
| Selection change on a 1000-row natural grid | no cell re-render; class toggles only |
| Column resize drag | one CSS variable write per frame; no row re-render |

---

### 7.1 Measured (2026-10-13)

Measured with the `Widgets/Data Grid/Benchmark` workbench story, driven by a Playwright script, on
an optimised (not draft) workbench compile in headless Chromium. Both grids have the same five
text columns and generated rows. The times are the median of seven runs, in milliseconds, of the
operation plus rendering plus forced layout. Scrolling is the median time per frame while jumping
300px a frame.

| Scenario | MyDataGrid | new, natural | new, virtual |
| --- | --- | --- | --- |
| first render, 100 rows | 7 | 10 | 4 |
| first render, 1,000 rows | 53 | 20 | 4 |
| first render, 10,000 rows | 570 | 172 | 4 |
| first render, 100,000 rows (1 run) | 6,379 | 1,853 | 5 |
| refresh, unchanged, 10,000 rows | 638 | 46 | 0.5 |
| refresh, 5 rows changed, 10,000 rows | 583 | 50 | 0.8 |
| select a row, 10,000 rows | 479 | 25 | 0.9 |
| column resize, per drag frame, 10,000 rows | 94 (applied once, on release) | 57 | 1.7 |
| scroll, per frame, 10,000 rows (re-measured in Phase 0) | 17 | 26 | 17 |
| elements in the DOM, 10,000 rows | 110,058 | 110,020 | 317 |

The changes the benchmark led to:

* Rows no longer shown are removed before the others are placed. Left in place, they made every
  later row look out of place, so changing 5 rows of 10,000 moved them all.
* Natural pages of more than 200 rows set `content-visibility: auto` on their rows, so the
  browser lays out only the rows in view. This makes first render about 3.5 times faster than
  `MyDataGrid`'s and makes live column resizing affordable. Shorter pages leave it off, because
  paint containment can move a row's text by a pixel, and they stay pixel-identical to
  `MyDataGrid`.
* The overlay thumbs follow the scroll with CSS scroll-driven animations where the browser supports
  them, and with script elsewhere. They are also placed by script on `scrollend`, so they are right
  wherever animations are turned off (e.g. the test runner's screenshots).

The one place the new grid is slower is scrolling a long natural page: about 26ms a frame against
17ms. `MyDataGrid` lays out its whole table up front, so its scrolling is pure compositing, whereas
rows coming into view here are laid out as they arrive. Only long pages that cannot be virtual
(i.e. they wrap) are affected.

## 8. Deliberate deviations

**Built into the new grid from the start.** These are approved and do not change how a static
screen looks, so they do not affect the visual regression comparison:

| # | Deviation | Why |
| --- | --- | --- |
| D1 | Overlay scrollbar thumbs built on a native scroller with its scrollbar hidden (§4.2), instead of `CustomScrollPanel` | The thumbs still float over the grid as they do today, and native scrolling behaviour comes for free. |
| D5 | Auto-fit measures cells rendered from the data, not the DOM: every row of a page of up to 1,000 rows, otherwise 1,000 rows spread evenly plus the rows in view. Natural and virtual mode are measured the same way. The cells are copied, with their classes, into a hidden element inside the grid and measured in one layout, so icons and padding count, not just text | Not every row is in the DOM in virtual mode, and measuring 10,000 rows one at a time would be slow. In the workbench the result is the same as `MyDataGrid`'s (Role: 63px for both). |
| D8 | Column resize reflows live; `HeadingListener.resizeColumn` is still called once, on release | More responsive, and cheap with one CSS variable. |
| D9 | Per-column minimum width, default 30px | `ColSettings.minWidth` is stored but never read today. |

**Approved fixes, applied after switch-over, each as a separate change.** This way the switch-over
itself compares like-for-like behaviour:

| # | Fix | Why |
| --- | --- | --- |
| D2 | PageUp moves up 10 | Today it is consumed but does nothing. |
| D3 | Cmd acts as Ctrl on macOS | Only `getCtrlKey()` is checked today. |
| D4 | Keyboard multi-select: Shift+arrow and Shift/Ctrl+Space extend the selection | `KeyBinding` matches modifiers exactly, so the comment claiming support is wrong. |
| D6 | A column cannot be moved past a non-movable column anywhere in the table | Currently only the leading non-movable columns are protected. |

**Single and multi select stay controlled by the selection model, as today.** D3 and D4 only apply
when the model allows multi-select (`addDefaultSelectionModel(true)`). In single-select mode the
modifiers are ignored, as R-S3 says.

**Approved sorting fixes, applied after switch-over, each as a separate change:**

| # | Fix | Why |
| --- | --- | --- |
| D7 | `CriteriaUtil.setSortList` applies the grid's sort list even when it is empty, instead of leaving the old criteria sort in place | Today the third click shows "unsorted" while the previous sort silently stays in effect (gwt-bugs #19 area). With D10, a grid that has a default never sends an empty list. |
| D10 | Default sorts are declared on the grid (`setDefaultSort`), shown in the header, and restored when the sort list becomes empty (R-H4b). Presenters stop putting a default sort only on the request. | One source of truth: the header always matches the order the data model applies. Changes how some screens look on first open (an indicator appears), so it comes after the switch-over comparison. |
| D11 | `DataGridComparatorFactory` honours `OrderByColumn.isIgnoreCase()` | Client-sorted grids currently sort case-sensitively while server-sorted grids don't. |

---

## 9. Delivery plan

**Phase 0: measure.** Measure and record:

* the keyboard row after `setRowData` (R-S12);
* scroll position on new data and on polling refresh (R-D7);
* header and row heights at both densities.

Add screenshots and geometry probes for the hardest consumers (§3.1), so the comparison baseline
exists.

* Done (2026-10-13), in the workbench rather than the React-side suite. Every hardest consumer
  already has a workbench story: the query and dashboard tables, the meta browser, Server Tasks and
  the import dialog.
* **Behaviour:** the `Widgets/Data Grid/Behaviour` stories run the same plays against `MyDataGrid`
  and against `DataGrid` in both modes. The plays click rows, move the focus away, send the
  same, changed and fewer rows, rebuild the columns, and check the keyboard row, the focus and the
  scroll position. The rules they hold the new grid to are in R-S12 and R-D7.
  * Measuring them found five differences in the new grid, all now fixed. A click didn't focus
    the row; the keyboard row was shown without focus; it kept its index instead of following its
    row; it started at -1, not 0; and focus was lost when the focused row was rewritten.
  * It also found that the row-height probe measured the long-page placeholder height (23px, not
    23.0625px). Rows laid out later then grew slightly, and scroll anchoring moved the scroll
    position by a few pixels.
  * The benchmark harness's old-grid scroller lookup found GWT's resize detectors rather than the
    scroller. Fixed and re-measured, with a check that each grid really scrolled: the old grid's
    scroll figure in §7.1 still holds (one frame, 16.7ms, at 1,000 and 10,000 rows).
* **Geometry:** `test-runner/grid-geometry.mjs --record <file>` measures the header height, column
  widths and first ten row heights of every data grid in the `App/` stories, at the default and
  compact densities, once each story's play has finished. `--compare <file>` reports any change,
  and works for either grid, so a screen switched in Phase 3 can be compared with its recording.
  The baseline, `test-runner/grid-geometry.json`, holds 359 grids in 284 stories, all
  `MyDataGrid`. A second run matched it exactly.
* **Screenshots:** the test runner already records them (`run.mjs --screenshots`) and compares them
  (`screenshot-diff.mjs`). A run on this branch before switching is the visual baseline.
* Differences kept, as harmless:
  * When the page is emptied while the grid has the focus, the new grid keeps the focus on itself.
    GWT drops it, then gives it to row 0 when rows return.
  * Moving the keyboard row out of view scrolls it in at once. GWT scrolls smoothly; both end at
    the same position, to the pixel.

**Phase 1: model and engine.**

* Pure-Java column model, fill, sort cycle, selection rules and virtual-window maths, with JUnit
  tests.
* DOM renderer with both engines.
* `HasData`, `Cell` and `Header` compatibility.
* Workbench stories, `Widgets/Data Grid/DataGrid` (`DataGridStories` in
  `stroom-gwt/stroom-gwt-workbench`). They share their names, rows and frame sizes with the
  existing `Widgets/Data Grid/DataGrid` stories for `MyDataGrid`, so each scenario can be compared
  side by side, and their play functions check the new grid's behaviour. Run them with
  `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchTest "-PworkbenchTestFilter=Widgets/Data Grid"`.
  * Headless Chromium hides native scrollbars, so in screenshots `MyDataGrid` shows none while the
    new grid's overlay thumbs show. A pixel comparison always flags that strip.

**Phase 2: the column interactions.** Resize, auto-fit, move, the `HeadingListener` contract,
interactive header cells, the context menu, copy, export and AI.

* Done (2026-10-13). `ColumnInteractions` handles the header's pointer events: the resize handle,
  live resize, auto-fit, move and the header menu. `DataGridContextMenu` builds the right-click
  menu. The DOM-free rules are in the model: `ColumnModel.resizeTarget`, `AutoFit` and
  `TableText`, each with unit tests.
* Stories: `ResizeAndReorder`, `ResizeAndReorderDisabled`, `HeaderInput` and a new `ContextMenu`,
  whose play checks that Ask AI sends rows that are not rendered (R-M4).
* A real-mouse comparison with `MyDataGrid` gave the same widths for a drag and for auto-fit, the
  same column order after a move, and no text left selected. Dropping a moved sortable column
  does not sort it.
* Small differences from `MyDataGrid`, none visible in a static screen:
  * The resize handle is not shown when `setAllowResize(false)`. GWT showed it but ignored a
    press on it.
  * The handle is kept inside the table, so at the last column's right edge it never makes the
    grid scroll sideways.
  * The guide line and move ghost cover the grid's full height, header included.
  * A header cell gets the same parent element as in GWT (the name holder of a sortable header,
    otherwise the header cell), so `FilterCell` finds its input.

**Phase 3: the `DataGridView<R>` interface and opt-in switching.**

* Extract the interface (§5.12) and have `MyDataGrid` implement it.
* Switch screens one category at a time:
  1. small lists;
  2. server-paged lists;
  3. multi-line;
  4. editable;
  5. tree;
  6. meta browser;
  7. dashboard and query tables, with `setVirtual(true)`;
  8. ImportConfigConfirm, with `setVirtual(true)`.
* Run the regression suite after each category.
* Give each screen switched a key provider (rows keyed by their id), so that a row whose values
  change is still the same row: its unsaved cell text, keyboard row and selection survive
  (`Widgets/Data Grid/DataGridRowUpdates`).
* **Started (2026-10-20) with the dashboard table**, `TablePresenter`, the most demanding consumer
  (§3.1), switched by type alone and without `setVirtual(true)` for now. Its stories and the
  query screen's (still `MyDataGrid`) pass, and its screenshots match `MyDataGrid`'s but for
  sub-pixel header text and the agreed row colours. Found and fixed on the way:
  * `getFilterButton` uses `getHeaderCellElement(col)` (§5.12).
  * Drawing the header again (every filter change rebuilds the columns) keeps the focus, and a text
    box's caret, on a control in a header cell, e.g. the filter box after Enter (R-H8).
  * Keys on a control in a header are the control's: the grid's header keys apply only to the
    header cell itself.
  * The filter's disable button acts on its own click in `FilterCell`, rather than through
    `ColumnsManager.onShowMenu`'s hit-test of a header press, so Enter and Space work it too
    (both grids; `QueryTableColumnsManager` likewise). Enter on it is no longer taken by GWT's
    `AbstractInputCell`, which moved the focus to the text box.
  * The filter box is named "Filter <column>".
  * `.TablePresenter th span { display: flex }` also applies to the new grid's header cells, so
    the value-filter icon sits beside the name.
  * The header's whole-pixel height is measured again when its row's size changes (a density or
    text size preference change), not only when the header or widths change.
  * The stories' fixture rows had no `groupKey`, so every `TableRow` was equal and a click selected
    all of them in the new grid (`MyDataGrid` drew only the clicked row selected). The fixtures
    now give each row a key, as the server does.
  * The dashboard plays' header clicks send pointer events as well as mouse events, as a browser
    does (the new grid reads pointer events), and find rows by `tr` or `role="row"`.
* **Then the query screen's table** (`QueryResultTablePresenter`, 2026-10-21), and both tables
  with `setVirtual(true)`, each wrapping column declared with `setColumnWraps` (the wrap was an
  inline `white-space: normal` the grid can't see). Found and fixed:
  * `setEmptyText` (`MyDataGrid`'s name) shows the text as `MyDataGrid` did.
  * A header row with no columns (a query's table before its first results) has no `row` role:
    a row must hold cells.
  * The keys skip cells too narrow to see (2px or less of content), such as the group expander's
    column with no groups, in rows and in the header.
  * The keyboard scrolls only the grid sideways to a cell, never the page around it, as the
    browser's `scrollIntoView` did.
  * Both column menus have a **Filter Values** item, opening what the header's value-filter icon
    does, so the keyboard reaches it (Up to the header, Enter for the menu).
  * The query plays' link-by-keyboard story follows the agreed model: Right into the row's cells,
    a link's cell gives the link the focus, Down to the next row's link.
  * Virtual rows' height follows a density change. `GlobalResizeObserver` called its listeners
    outside any GWT event, so the render the grid scheduled (`scheduleFinally`) waited for the next
    key or click. Its handler is now wrapped in `$entry`, which every resize listener gets. A play
    can't show it (its own steps run the pending work), so the regression check is a real-input
    one, on `Widgets/Data Grid/Behaviour/RowsFollowDensityVirtual`.
* **The interface** (2026-10-21): `stroom.widget.datagrid.client.DataGridView<R>`, which `MyDataGrid` and
  `DataGrid` both implement. It holds the §5.12 methods both grids have, plus
  `addColumn(ColSpec)` (new to `DataGrid`, sized by `ColumnSizing.fromColSettings`) and
  `getHeaderCellElement` (new to `MyDataGrid`). The code several screens share takes it rather
  than `MyDataGrid`: `DataGridUtil`'s column helpers (`addExpanderColumn`, `addStatusIconColumn`,
  `addDocRefColumn`, `addFeedColumn`) and `JobNodeColumns`, so the job and node screens can
  move one at a time. `MetaListController`'s grid moves with the meta browser.
* **Small lists, first batch** (2026-10-21): 14 client-side lists, switched by type:
  * `LinkedEventPresenter`, `ScriptDependencyList`, `StatisticsFieldListPresenter`,
    `ElasticIndexFieldList`, `SolrIndexFieldList`, `DocRefList`,
    `SelectionHandlerList`, `SigningKeyList`, receive rules'
    `FieldList`, `RuleList` (conditional formatting),
    `UserSessionsList`, `WordListPresenter`, and the floor map editor's
    `FloorMapTimeList` and `FloorMapFactList`.
  * Key providers where the row's `equals` is not already its id: index fields by name, word by
    word, selection handler, signing key and formatting rule by id (a rule with no id falls back to
    the row itself), session by handle, floor map time entry by time (the list holds one fact's).
    `DocRef`, `QueryField`, the statistics `Field`, `EventId` and the fact rows already compare by
    their id.
  * `DataGridComparatorFactory` takes a `DataGridView`; the floor map lists' row elements are plain
    `Element`s.
  * Found: the new grid's sticky header is opaque (rows scroll under it) in the page's colour, but
    a list in a control's box (`.stroom-control`, e.g. a dictionary's imports), a dialog or a popup
    sits on that one's colour, where `MyDataGrid`'s transparent header showed it. Each of those now
    sets `--data-grid__head-background-color`, which the header uses; the nearest round the grid wins. (A
    dialog's or popup's colour is painted by a layer behind its content, so can't be inherited.)
  * Two plays found header cells only as `th`.
  * Differences kept: a play's click on a row left it scrolled to a different place (the Solr field
    paging story); the conditional formatting dialog opens sooner without `MyDataGrid`'s resize
    timers, so its own focus on show lands before the play's tab click rather than after.
  * Held back at first: receive rules' `RuleSetList` (no stable row id) and the floor
    map's tracking, groups and cluster lists (no story renders them).
* **Decisions** (2026-10-21, the user): fix the workbench frame now and take the screenshot
  baseline again; key the receive rules by position (`getRuleNumber`: an edited rule keeps its
  row, a moved one is a new row); write a story for a screen that has none before switching it;
  switch the server-paged lists in two or three batches.
* **Then** `RuleSetList` (keyed by rule number) and `KeyBindingPreferences` (its
  story, `KeyboardShortcutPreferences`, does exist; rows already compare by all their fields).
* **Server-paged lists, first batch**: `AiAttachmentDataPresenter` (`setPageStart` is
  `setVisibleRange`), `DuplicateManagementList`, `IndexVolumeGroupList`,
  `PathwayList` (keyed by name), `ProcessorProfileList` (id),
  `AnnotationTagList`, `CacheList` (cache name), `FsVolumeGroupList`
  (group id), `UserDependenciesList`, `CacheNodeList` (node name). Held back:
  * `NodeGroupList`, `NodeJobList`, `JobNodeList`: tick boxes and a
    value spinner, so the editable category.
  * `UserRefPopupPresenter` (the user picker): it uses `DataGridSelectionEventManager`'s
    selection-follows-focus, Up from the first row to the filter and typing into the filter, which
    `DataGridSelectionEventManager` doesn't have yet; a step of its own.
  * `AnalyticDataShardList`: no story opens its grid (needs one first, as do the floor
    map's three lists).
* In the workbench, a full-screen app screen is 48px taller than the window
  (`StroomThemeDecorator` pads every story by 24px around the screen's `100vh`), so the bottom of
  the query screen is off-screen. A play's click on a table row there scrolls it into view with
  the new grid, where `MyDataGrid` left it hidden; the annotation stories' screenshots differ by
  that scroll. Fixed 2026-10-21 (`StroomThemeDecorator.CANVAS_HEIGHT`), and the screenshot
  baseline taken again.
* The expander column's header cell measures 6px (compact) or 12px (default) at a 1px width,
  where `MyDataGrid`'s `th` measures 1px: a `div` can't be narrower than its padding. Its padding
  overflows into empty space; the next header still starts at its column. Harmless, but
  `grid-geometry.mjs` reports it.
* `grid-geometry.json` was recorded on the `datagrid` worktree's older base. Comparing found that
  rows holding a document link (83 grids) had grown 5px since: `.dataGridWidget button`
  (`standard_mod.css`) gave the row actions that became real buttons (`.rowAction`) a padding and
  border. It now leaves row actions out, and those rows match again. What still differs on screens
  using `MyDataGrid` is some column widths changed on master since (processor, node and task
  screens).
* **Recorded again** on this branch (2026-10-21), with the dashboard and query tables on the new
  grid: 333 stories with grids. A second run matched it exactly. The first attempt didn't: the
  virtual tables' row height went stale on a density change (fixed above, `$entry`).
* **After merging master's overlays work** (2026-10-21), the grid follows what its menus, dialogs
  and popups set out:
  * **BEM.** The grid's classes are one block, `data-grid` (`__scroller`, `__head`,
    `__header-row`, `__header-cell`, `__content`, `__body`, `__row`, `__cell`, `__empty`,
    `__track`, `__thumb`, `__resize-handle` and so on; modifiers such as `--virtual`), its custom
    properties `--data-grid__columns`, `--data-grid__total-width`, `--data-grid__row-height`;
    both stylesheets list the parts. They were `dg`, `dg-row`, `dg-hcell`. The old grid's classes
    (`dataGridWidget`, `dataGridEvenRow`, `multiline`, `allow-header-selection` and so on) stay
    until phase 4, and the `data-row`, `data-col`, `data-hcol` and `data-dg-no-select` attributes
    are unchanged.
  * **Forced colours.** A selected row is the system's Highlight and HighlightText, keyboard focus
    an inset outline (the box-shadow ring isn't drawn in forced colours), a dimmed cell GrayText,
    as the menu's items and the calendar's days. Before, neither the selection nor the focus
    showed. Real input check on `Widgets/Data Grid/DataGridKeyboard/SingleSelection`.
  * **`InputModality`.** Whether the focus the grid puts back after a redraw shows as keyboard
    focus follows the page's last input (`InputModality`, as the menu's ring does), not only the
    grid's own key presses.
  * The header's colour in a dialog or popup keys off master's `dialog` and `popup` blocks
    (`dialog-container` and `simplePopup-container` are gone).
* **The second paged batch and the user picker** (2026-10-21), after the merge. Found:
  * `InfoColumn` found its cell by walking up to a `td`, so in the new grid it went on to `<html>`:
    the popup was placed against the page, and the page was its auto-hide partner, so a press on
    another row's Info icon only closed the open one. It stops at a `td` or a `[data-col]` cell.
  * `PercentBarCell`'s bar `div` wasn't closed (see `ISSUES.md`, Fixed).
  * The user picker's keys play waited for the filter's first result, not its last, so Down went to
    a row of a list still being filtered (`MyDataGrid`, drawing later, hid it).
  * The picker uses `DataGridSelectionEventManager`'s `setMoveUpFromFirst` and
    `setTypedHandler` (new, as the old manager's) and `DataGrid.isInRows`; the selection
    following the focus it had asked for is every single-choice grid's.

* **Editable lists** (2026-10-21): 15 grids with tick boxes, spinners and select-all headers, switched
  by type: `NodeJobList`, `JobNodeList`, `NodeStatusList`,
  `IndexShard`, `ColumnSelection`, `NodeGroupStateList`,
  `StatisticsCustomMaskListPresenter`, `UserList`, `ConstraintList`,
  `NodeGroupList`, `ContentTemplateList`, `DataRetentionPolicyList`,
  `XPathList`, `DocRefSelection`, and `ProcessorTaskSummary` (given
  stories first, `App/Main/ProcessorTaskSummary`).
  * Key providers where a tick or refresh brings new, unequal rows: node groups and index shards
    by id, content templates and retention rules by number (their position), constraints by name,
    task summaries by their group (not their count).
  * The select-all headers (anonymous `Header`s with an updater) work as they are: the new grid
    gives header cells their events.
  * `UserList.getDataGrid()` returns a `DataGridView`; `DocRefSelection.setDataWidget`
    takes a `Widget`.
  * The plays find rows, cells and headers with `StroomDom.GRID_ROW`, `GRID_HEADER_CELL`,
    `GRID_HEADER_TEXT` and `GRID_CELL_TEXT` (new), which match either grid.
  * Plays that followed `MyDataGrid`'s keyboard follow the agreed model: a row's buttons aren't tab
    stops but are reached with Right (Jobs' `CommandLinksByKeyboard`), and each grid is one tab
    stop, an empty one too, so its headers can be reached (User Groups' `ReadingOrder`).
  * A clicked row has the focus, so its row actions' icons show, as the CSS has them on the row
    with the focus.
  * Found: removing a statistic store's last roll-up left the list empty (its story had passed only
    because the old grid's click ticked a box), the XPath list's inert Ignore Case tick box, and the
    task summary losing its selection when a count changes (the presenter compares whole rows; see
    `ISSUES.md`).
  * Held back: the floor map settings' Value Schema (`FloorMapSettingsPresenter`): its rows have no
    stable id, it uses a selection model with no handler, and it restores focus through table rows.

* **Multi-line lists** (2026-10-21): 13 grids, all `setMultiLine(true)`: `PropertyList`,
  `ProfilePeriodList`, `FeedDependencyList`, `PipelineReferenceList`,
  `ManageGlobalPropertyList`, `ActivityList`, `CredentialsList`,
  `ContentStoreContentPackList`, `DocumentUserPermissionsList`,
  `AppUserPermissionsList`, `ScheduledProcessHistoryList`,
  `AppPermissionsEdit`, `JobList`.
  * Key providers where an edit, refresh or node reply brings new rows: profile periods by uuid,
    pipeline properties by element and name, activities by id, global properties by name,
    content packs by their pack.
  * Three made their own `DataGridSelectionEventManager` (no setters); they make the new grid's.
  * **Parity fix:** the grid fires its `LoadingStateChangeEvent` once for each change of data or row
    count, when it draws, as GWT's `HasDataPresenter` does, not only when the state changes. The
    content store runs its cache lookup, upgrade check and first-row choice on every LOADED, and
    a refresh of the same page brought none. `Widgets/Data Grid/Behaviour` `LoadedOnEveryLoadOld`
    and `…New` show both grids do it.
  * Found: `DescriptionBuilder` closed spans with `</span` (see `ISSUES.md`, Fixed), which the old
    grid's tables hid.

* **Trees** (2026-10-21): the five grids with an expander column, with all their columns as before:
  `ProcessorList`, `TaskManagerList`, `MarkerList`,
  `ConfigPropertyClusterValuesList` and `DataRetentionImpactPresenter`.
  `TreeRowHandler` takes a `DataGridView`.
  * **Tree grid semantics.** A grid with an `ExpanderCell` column is an ARIA `treegrid`: rows carry
    `aria-level` and, unless they're leaves, `aria-expanded`. On a row, Right expands a collapsed
    row (else enters the cells), and Left collapses an expanded one (else goes to its parent row).
    The grid expands and collapses through the expander column's own `FieldUpdater`, so each
    presenter's tree logic is unchanged, as is the expander column's look.
    `Widgets/Data Grid/DataGrid` `TreeKeys` shows it.
  * The marker list's summary rows span five columns with `setRowSpans` (the presenter used to set a
    `td`'s colspan after drawing) and are keyed by severity.
  * Found: `TreeRowHandler` narrows the expander column once rows arrive. `MyDataGrid` filled its
    columns later, on attach, but the new grid sizes them before the first rows, so the space given
    up was left as a gap at the right (Server Tasks' Info column stopped 199px short). The handler
    now asks the grid to give spare width to the fill columns (`resizeTableToFitColumns(true)`).

* **Lists with their own selection manager** (2026-10-21): `ApiKeysList`,
  `ScheduledProcessList`, `DocumentCreatePermissionsList` and
  `NotificationList` (the analytic rule and report notifications). The three that
  made a `DataGridSelectionEventManager` make the new grid's. API keys are keyed by id and
  notifications by uuid, as their `equals` compares every field, so a tick or edit brings an
  unequal row.
  * A tick box acts on mousedown and redraws itself, so the element pressed is gone before the
    click, in either grid, and the click never reaches the tick's ancestors. The document create
    permissions stories reported the working set on a click; they now do on mousedown.
  * Found: the document create permissions grid adds its columns once the document types are
    fetched, after the grid was laid out, and the grid had resolved its widths with no columns, so
    its fill column kept its declared width (the rows stopped at 270px). `ColumnModel.resolve`
    now waits for columns, and the grid resolves when it draws as well as on a resize
    (`TestColumnModel.testResolveWaitsForColumns`).
* **The last three** (2026-10-21):
  * The meta browser (`MetaListController`, and so the stream list, the relation list and
    stepping's list): its own `DataGridSelectionEventManager` becomes the new grid's (its select-all
    handler included). The relation list narrows its expander column once rows arrive, so it
    gives the width back to the fill columns, as `TreeRowHandler` does.
  * The import confirmation (`ImportConfigConfirmPresenter`): one page of up to 100,000 items,
    now `setVirtual(true)`, so only the rows in view are drawn (`MyDataGrid` drew them all).
  * The floor map settings' Value Schema (`FloorMapSettingsPresenter`): an edit replaces a mapping
    with a new, unequal one, so rows are keyed by the mapping they started as (an identity map
    of numbers; a replacement takes the key of the mapping it replaces). Its `SingleSelectionModel`
    becomes the single-select `MultiSelectionModelImpl` with the new grid's manager, so the
    agreed keys work (Enter/F2 edits, Tab between control cells, Remove follows the row). Its
    focus restore after an edit finds the cell by `data-col`, not through table rows. The old
    mapping keeps its key too, as the grid may draw the row once more before the list change
    reaches it (dropping it gave the row a new key, and so a new element, losing the focus).
  * Its plays follow the agreed keys on a selection box: Enter, Space or Alt+Down open the list,
    while Down, Home and End are the grid's (next row, first and last cells). Home and End no
    longer open the list at its ends (`HomeAndEndOpenAtTheEnds` became
    `HomeAndEndMoveAlongTheRow`).
  * **Two grid fixes** (`Widgets/Data Grid/DataGridKeyboard` `SelectionBoxStaysPut`):
    * A cell's event forgot its row's HTML (so the next draw wrote the row again) whenever the
      cell handled it, in case the cell had rewritten itself. A selection box handles its own
      focus, so focusing one replaced it at once. The HTML is now forgotten only if the cell's
      content changed.
    * The keyboard row coming or going rebuilt the row's cells, although its class isn't in
      them. A selection box marks itself open, and its list's filter takes the focus out of the
      grid; the redraw for that replaced the open box, so it never read as expanded and its list
      was placed against a box no longer in the page. Only the class changes now.
  * `TypeFilter` and `ColumnValuesFilterPresenter` use only `MyDataGrid`'s
    `DEFAULT_LIST_PAGE_SIZE` (for a `MyCellTable`, session 5); it moves when `MyDataGrid` goes.

**Phase 4: clean-up.** Remove `MyDataGrid`, `ResizeHandle`, `MoveHandle` and
`DataGridSelectionEventManager`'s DOM coupling. Remove the forked `cellview` classes that
`MyCellTable` does not need. Then apply the §8 fixes (D2, D3, D4, D6, D7, D10, D11), one change each.

* **The removals** (2026-10-21, at the user's word), in two steps, each compared with full
  screenshots in both themes:
  * **The old grid.** `MyDataGrid`, `ResizeHandle`, `MoveHandle`, `MyDataGridAiSupport` and
    `DataGridSelectionEventManager` are gone. The page size constant the cell tables borrowed is
    `MyCellTable.DEFAULT_LIST_PAGE_SIZE`. The workbench's `Widgets/Data Grid/DataGrid` stories (the
    old grid's own, each with a `DataGrid` counterpart) went with it; the stories that set the
    two grids side by side (Behaviour's `…Old`, Row Updates' `CurrentGrid`, Row States' current
    grid, the benchmark's `old` kind) keep only the new grid's half; the cell renderer, view state
    and split pane stories use `DataGrid`, their plays its keys (a row's buttons are reached
    with Right, not Tab). `DataGridView` stays, with one implementation, as the shared helpers take
    it. None of the forked `cellview` classes could go: `MyCellTable` and the selection models
    still use them all.
  * **The old class names.** The grid no longer carries `MyDataGrid`'s classes. Its own are BEM
    throughout: rows `data-grid__row--even`, `--odd`, `--selected`, `--keyboard`; header cells
    `--sortable`, with a sortable header's title `data-grid__sort` and `data-grid__sort-name`; the
    keyboard's cell `data-grid__cell--keyboard`; the grid's `data-grid--multiline` and
    `--header-selection`. The rules `cell-table.css` gave the old names (padding, header
    text, stripes, the sortable header's title) are `data-grid.css`'s under these; the
    disabled and alert text colours (`dataGridDisabledCell`, `dataGridAlertText`, a column's own
    cell styles, unchanged) are `data-grid.css`'s; and the cell styles that keyed off the old row
    classes (percent bar, red and green text, SVG icons, a disabled spinner, the find cell, the
    dashboard's filter row, conditional formatting, pathways' error rows) key off these.
    `DataGrid.css` keeps only what other widgets share: a cell table's cell padding and sortable
    header, and the glass and handles of a drag (`DefaultStyle`). A cell's HTML no longer depends
    on its row's state, so selecting a row no longer rewrites its cells. Two old rules didn't
    come: the focus glow (`data-grid.css` always overrode it) and `margin: 0` on every `select`
    on the page (a slip in `.dataGridWidget input,select`).
* **The §8 fixes** don't remove anything, so they went first:
  * D2 (PageUp) and D4 (Shift+arrows, Space) were built into `DataGridKeyboard`.
  * **D3** (2026-10-21): on macOS, Cmd does what Ctrl does: the row keys, a press on a row and a
    click on a header (`DataGridDom.isCtrl`, `SelectionRules.isCtrl`). Elsewhere the meta key is
    still left to the browser. `TestSelectionRules.testCmdActsAsCtrlOnlyOnMacOs`, and
    `Widgets/Data Grid/DataGridKeyboard` `CmdActsAsCtrlOnMac` (which stubs the platform).
  * **D6** (2026-10-21): `ColumnModel.insertPosition` keeps a column within the run of movable
    columns it is in, not only after the leading fixed ones
    (`TestColumnModel.testInsertPositionNotPastAnyNonMovableColumn`).
  * **D11** (2026-10-21): `DataGridComparatorFactory` compares text ignoring case for a column
    that says so (`OrderByColumn.isIgnoreCase`), as the server does
    (`TestDataGridComparatorFactory`).
  * **D7 and D10** (2026-10-21), together: D7 alone would send an empty sort on a screen's first
    fetch too, dropping the default sorts presenters put only on the request.
    * `DataGrid.setDefaultSort` shows the default from the start (no event) and puts it back,
      with an event, when a click leaves nothing sorted (`SortCycle.orDefault`).
      `Widgets/Data Grid/DataGrid` `DefaultSort`.
    * `CriteriaUtil.setSortList` always sets the grid's list, empty too.
    * Of the 16 screens that call it, six had a default, and now declare it on the grid with the
      same order: Server Tasks (Age, descending), scheduled processes and the execution schedule
      manager (Name, A to Z: it was Z to A, a slip the header showed, so the user had it changed), scheduled process history (Execution
      Time, descending, for the ID it used, which has no column), processor profiles and node
      groups (Name). The others had no sort but the grid's, and the criteria classes set none.
    * A default's case sensitivity is its column's. The schedules' Name defaults ignored case, so
      their Name columns now do too (a click on Name sorts ignoring case, as the default does), and
      the first fetch's request is as before.

---

## 10. Decisions

| # | Question | Decision |
| --- | --- | --- |
| 1 | Naming and package | `DataGrid<R>` in `stroom.widget.datagrid.client`, with a `DataGridView<R>` interface implemented by both grids during switch-over (§4.1). |
| 2 | Scrollbars | Custom overlay thumbs that float over the grid as they do today. The implementation is a native scroller with its scrollbar hidden, plus overlay thumbs, with CSS scroll-driven animations as an enhancement (§4.2, D1). |
| 3 | Live resize | Live reflow; persist on release (R-CR2, D8). |
| 4 | Column minimum width | Per column, default 30px (R-CR7, D9). |
| 5 | Wrapping and virtual | Wrapping columns or multi-line switch virtual off (parity). Variable-height virtual is a later phase (§4.4). |
| 6 | Model code location | `stroom.widget.datagrid.client.model` in `stroom-core-client-widget`, with no DOM dependencies (§4.1). |
| 7 | Behaviour fixes | D2, D3, D4 and D6 approved, applied after switch-over. Single and multi select stay controlled by the selection model (§8). |
| 8 | Empty state | Blank by default; screens can opt in with `setEmptyMessage` (R-D5). |
| 9 | Where sorting happens | Sorting stays in the data model (server criteria or the presenter's `ListDataProvider`). The grid only holds the sort state and fires `ColumnSortEvent`, the same mechanism for both. No built-in client-side sorting (R-H4a). |
| 10 | Third click empties the sort | Restore the screen's default sort and show it. A grid with no default sends an empty sort (R-H4b, D7). |
| 11 | Default sort shown in the header | Yes: declared on the grid with `setDefaultSort` and shown from the start (D10). |
| 12 | Sort position badge | Keep as today: shown even when only one column is sorted (R-H3). |
| 13 | Client sort ignore-case | Honour `isIgnoreCase` (D11). |
| 14 | Hidden tie-breaker sorts | Not part of the grid's sort state; the presenter appends them to the request (R-H4c). |

---

## 11. Lists and trees on the grid (2026-10-22)

**Decision.** Stroom's lists and row trees are built on `DataGrid`, not on a separate list
widget: a cell table, a list or a tree is rows, as the grid's are, and the grid already has what
they need, tested and measured (row rendering that writes only what changed, virtual rows, paging,
row keys, the selection rules, the focus kept through redraws, the agreed row looks, tree rows). The
`session5_trees` worktree, which had begun a separate core (`RowWidget`), was stopped (the user,
2026-10-22).

**A profile, not a one-column grid.** What a list or tree is told to assistive technology, and its
keys, differ from a grid's, so the grid takes a profile (`DataGridProfile`):

| | Grid (default) | Listbox | Tree |
| --- | --- | --- | --- |
| Roles | `grid` or `treegrid`, `row`, `columnheader`, `gridcell` | `listbox` (`aria-multiselectable` when several can be chosen), `option`, cells `none` | `tree` (`aria-multiselectable` likewise), `treeitem` with `aria-level`, `aria-expanded`, `aria-setsize`, `aria-posinset`, cells `none` |
| Header | yes (can be hidden) | none | none |
| Into the cells | Right, Left, Home, End, Up into the headers | no: the row is the only stop | no |
| Right, Left | cells (on a treegrid's row: expand, collapse, parent) | nothing | expand or first child; collapse or parent |
| Tree rows from | the `ExpanderCell` column | — | a function giving each row's `Expander` (depth, open, leaf) |
| Type-ahead | yes (added to the engine, for all) | yes | yes; `*` expands the siblings |

A row's controls are reached as in a grid only where they can be: an `option` or `treeitem` may not
hold focusable controls, so a list's tick box is the row's own state (`aria-checked` on the
`option`, `mixed` for a part-ticked one), changed with Space or a click, not a control of its own.

**Focus.** The real focus moves, as it does in the grid and in today's lists: on a row, or in a
popup's filter box above the list. No list needs `aria-activedescendant` (`SelectionList` and the
document picker move the focus between their filter and their rows today). The hooks the lists use
are the grid's selection manager's: Up from the first row to the filter (`onUpFromFirst`, as now),
and a typed character to the filter where the host has one, else type-ahead.

**What moves** (inventory, 2026-10-22):

| Kind | Today | Profile |
| --- | --- | --- |
| Single-choice lists: annotation chooser, tab session chooser, the stepping filter's elements | `MyCellTable`, `BasicSelectionEventManager` | listbox (the stepping filter's three columns kept as cells) |
| Query history and favourites | `CustomCellList` | listbox |
| Tick lists: type filter (part-ticked), column values filter (paged), annotation labels | `MyCellTable`, `CheckListSelectionEventManager` (labels: no keys at all) | listbox, multi, `aria-checked` |
| Find lists: documents, recent items, find in content, annotations, AI chat history | `MyCellTable` (`FindCellTable` class), the explorer's selection manager; Left and Right change page | listbox (Left and Right keep changing page) |
| Combobox lists: `SelectionList` in `SelectionPopup`, query help, edit and remove tags | `MyCellTable` with `option` roles on its cells | listbox, last (its levels, path and paging) |
| Explorer, folder picker, document picker, tick tree | `MyCellTable` of flattened open nodes, `ExplorerCell`, no tree roles | tree (tick tree: `aria-checked`) |
| Asset tree and asset chooser | GWT `Tree` | tree |

**What doesn't:** the menus (`MenuViewImpl`, then a `MyCellTable` with menu roles, done in session 3; since 23 October 2026 their own `Menu` widget),
and the drawn trees (the pipeline tree and bar, the expression tree, pathways), which are layouts
with their own agreed keys (`DraggableTreePanel`, `TreeKeyboard`). While the menus are on
`MyCellTable`, the forked `cellview` classes stay; moving the menus onto the grid too is for later.

**Order:**

1. Split `DataGrid` (2,274 lines) into the grid and its parts (row rendering first), and add
   the profile with the grid's as the only one: no change to any grid (every story passes, and the
   screenshots are unchanged).
2. The listbox profile and type-ahead, with the small lists: the choosers, query history and
   favourites, the tick lists and the find lists.
3. The tree profile, with the explorer trees (standard WAI-ARIA tree keys) and the asset trees.
4. `SelectionList`.
5. Delete what nothing uses: `FindCellTable`, `CustomCellList`, the selection event managers, and
   `MyCellTable` once the menus have moved. Done 23 October 2026: the menus are on their own `Menu`
   widget, and these, `HoverActionMenuCell`, the `Widgets/CellList` stories and the forked
   `cellview` classes are deleted.

Each list keeps its looks in the row states agreed for lists (`list-row-states`: the grid's rows,
keyboard focus drawn inside the row) and has its stories switched with it, as the grid's were.

**Progress.**

* **Step 1** (2026-10-22): `DataGrid` (2,274 lines) is split into the grid, its rows
  (`GridRows`: drawing the rows, keeping them by key, the focus kept through a redraw, the row
  height), its tree rows (`TreeRows`) and its header (`GridHeader`), with no change to any grid:
  every story passes and every screenshot is as it was.
* **Step 2, begun** (2026-10-22): the listbox profile (`DataGridProfile.LISTBOX`, set with
  `setProfile`): the grid is a `listbox` of `option`s (each saying its place, `aria-posinset` and
  `aria-setsize`), with no header and no cells to move into; `aria-multiselectable` when its
  selection manager chooses several; type-ahead (`typeAhead`, with the shared `TypeAhead`) for a
  character typed on a row when no typed handler takes it; no stripes (`data-grid--list`), and the
  last column filling its width (`ColumnModel.toListTemplate`). Two options any grid can use came
  with it: a grid as high as its rows, up to its container's `max-height` (`setFitContent`, for a
  list in a popup, which has no height to fill), and a selection manager whose rows are choices the
  host acts on (`setChooser`: moving chooses nothing; a press, Enter or Space hands the row over,
  even one already chosen). First list moved: the annotation status and standard comment chooser
  (`ChooserPresenter`, named "Statuses" or "Standard comments"); `App/Main/AnnotationEditor`
  `StatusChooserKeys`.
* **Step 2, the small lists** (2026-10-22), each with a story of its keys, roles and names, and
  each checked against a picture of the old one:
  * The tab session chooser ("Tab sessions") and the stepping filter's elements ("Elements"), and
    query history and favourites ("Query history", "Favourite queries", which were `CustomCellList`s,
    their presenters now making the list): the row with the focus chosen; in a list, Enter opens the
    row as a double click does (a double-select `MultiSelectEvent`), e.g. confirming the dialog. The
    history dialog once also took a single click on the chosen query as choosing it, as two
    selections within the double click time: that is a double click or Enter now.
  * Tick lists: the explorer's type filter ("Document types"), a column's values filter ("Values",
    in its popup and the dashboard's Table Filter) and the annotation label and collection choosers
    ("Labels", "Collections", which had no keys at all). A tick is the option's own state
    (`setRowChecked`: `aria-checked`, `mixed` for All / None part ticked), changed with Space,
    Enter or a press (`setChooser`), Ctrl+A for all; the tick box is drawn as a picture
    (`TickBoxCell.DefaultAppearance.getPicture`, `aria-hidden`), which the grid's keyboard leaves
    alone, and in the clickable look (the values filter's were the read-only grey). The type filter
    is as wide as its longest type (`setFitWidth`) and as compact as the explorer's rows
    (`setCellPadding(false)`, its cells drawing their own padding), with its line under All / None
    a row style rather than an `<hr>` in the row.
  * The find dialogs' results: documents found, recent items ("Documents"), content found,
    annotations found and AI chats, made by one helper (`FindResultLists`): striped
    (`setStriped`), the result with the focus chosen, Enter or a double click opening it, Up on the
    first back to the filter, Left and Right changing page as they did (`setLeftRightPage`, through
    the keyboard's `Selector.onLeft` and `onRight` for a list).
  * `DataGrid.java` stays under 2,000 lines: type-ahead is `GridRows`'.
* **Step 3, the explorer trees** (2026-10-22): the tree profile (`DataGridProfile.TREE`): a `tree`
  of `treeitem`s, each with `aria-level`, `aria-expanded` and its place among its siblings
  (`aria-posinset`, `aria-setsize`, worked out by `model.TreePositions`, unit tested), its rows'
  expanders from a function and what opens or closes one (`setTreeRows`), and WAI-ARIA's tree keys:
  Right expands or goes to the first child, Left collapses or goes to the parent, `*` expands the
  siblings, typing goes to the next item starting with it, and the context menu key or Shift+F10
  gives the selection manager the row's menu (`Selector.onContextMenu`). The explorer, folder
  picker, document picker and tick tree (`AbstractExplorerTree`) are on it, keeping their choosing:
  moving doesn't choose an item, as choosing one opens its document (`DocumentPluginEventManager`),
  except in the picker; Space chooses, Enter opens as a double click does, Shift and the arrows
  extend the choice, a click on the expander opens or closes, and a right click or the menu key
  shows the item's menu (Right on a document no longer does: it is a tree key). The tick tree's
  items say whether they are ticked (`aria-checked`, its tick box a picture). A list's or tree's
  row is at least 24px high (WCAG 2.5.8; the explorer's were 23px). `Widgets/Tree/ExplorerTree`
  `Keys`.
* **Step 3, the asset trees** (2026-10-22): a visualisation's Assets tab and the 'Choose Asset'
  dialog (a floor map's asset picker) were GWT `Tree`s, their items `TreeItem`s that were both the
  data and the widget. The items (`DocumentAssetTreeItem`) are now a plain model (children, parent,
  open state; unit tested), drawn by `DocumentAssetTree`, the tree profile with the explorer's row
  looks (`explorerCell` classes): one item chosen at a time, the choice following the focus (moving
  to a file shows it in the tab's editor, or enables the dialog's OK); Enter or a double click on a
  folder opens or closes it, and on a file, in the dialog, chooses it as OK does. The trees are
  named "Assets". Their presenters keep their code, the tree mirroring the calls they made on GWT's
  (`addItem`, `getItem`, `getSelectedItem`, `setSelectedItem`, `addSelectionHandler`).
  `App/Main/VisualisationAssets` `TreeKeys` and `ChooseAssetByKeyboard`. Waiting for step 5:
  `DocumentAssetImageResource` and `components.css`'s `.visualisation-asset-tree` rules, which
  nothing uses now.
* **Step 4, `SelectionList`** (2026-10-22): its options are the grid's, as a list's, keeping every
  key agreed in session 1 (moving only moves; Enter, Alt+Up or a click chooses; Right and Left
  through its levels; Space, Shift+Up and Shift+Down, Shift+Space and Ctrl+A in a list of several
  choices; Escape closes; typing to the quick filter or the next option), now through the grid's
  `Selector` hooks rather than reaching into a cell table. In a selection box's popup it is as big
  as its options within the popup's limits (`setFitContent`); a blank option is named None
  (`setRowName`); its id is the grid's (`getAccessibleElementId`, for the box's `aria-controls`).
  Query help's list is named "Help contents". `Widgets/Selectors/SelectionListStates` shows the
  grid's row looks.
