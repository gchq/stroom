# Wave 5: Data grid, cells and selection list (after data_grid2 and copy_and_paste land)

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

Make DataGridWidget usable in templates and free of the bus: requests bubble through the DOM, and sort, range and selection outputs become callbacks. Cells take onOpen/onMenu callbacks instead of an EventBus or HasHandlers. SelectionList and SelectionPopup move from init(model) to setters.

## Audited (25 October 2026)

Six reviewers read the wave against this plan, "Done means" and AGENTS.md. Fixed:

- **Two regressions.** A dashboard table's Add Column list added a column twice after a second
  press on its button had closed it (on master the next choice was added twice; here, once the
  list's close was one callback, every later choice was): the list was shown before its handlers
  were added, and showing an open list closes it. A second press now only closes it (story
  `TableAddColumnAfterClosingTheList`; on master too, so a changelog entry). And every selection
  list showed the grid's own context menu (Copy, Export Table, Ask Stroom AI About) on a
  right-click: its grid had a private bus nothing heard, and now asks the page; the list turns the
  menu off (story `NoMenuOnTheOptions`).
- **A bug recorded as this branch's only.** The standalone dashboard's menus (splitters', dialogs')
  never opened on master either (`MenuRequest` came with `d3ddaab46f`, which master has): a
  changelog entry, and ISSUES.md says so.
- **A fix only half done.** `setHideHeader` after the first render hid the header; showing it again
  didn't draw and snap it again. It marks the header to be drawn again now (story
  `HeaderHiddenAfterRender`).
- **Claims that weren't so.** The counts (16 units, in 9 commits; 127 `DataGrid` constructor calls
  and 5 `FindResultLists.create` calls, not "132 callers"; about 90 uses of the pager as a task
  monitor, not "83 REST calls"); "a template can't name a `<nav>`" (an `HTMLPanel` can be one, but
  can't carry an `aria-label`, so the `AriaPanel` stays); the menu's link items "follow a link as a
  click does" (only where something handles the grid's links: elsewhere a click does nothing and the
  menu asks the app); `HeadedPagerPanel` as "the plans' fallback" (it is a copy of the old panel, not
  the fallback's wrapper around `PagerPanel`, and the reason given wasn't shown).
- **Rule 6.** `MessagePanel` and `VisFrame`, used only by themselves, have their own BEM
  stylesheets: `message-panel.css` (`message-panel`, `message-panel__message`, were
  `dashboardVis-message*`) and `vis-frame.css` (`vis-frame`, `vis-frame__frame`, were
  `VisFrame-*`).
- **Smaller things.** A grid's hyperlink handler is counted once however often it is removed; the
  download link is hidden with `hidden`, not an inline style; `SelectionList`'s event manager is at
  the bottom of the class; javadoc on `VisFrame`'s methods and `SelectionPopup`; misplaced
  comments, an unused import, an indent; stale names left by the renames in the docs, the design
  history they had rewritten (`PagerPanel.addButton`, which never was), the CSS comments and the
  suite's `build-handler-inventory.mjs`; the half-renamed snippets in the wave 11 and 15 plans; and
  a "Since wave 5" note at the top of the wave 6, 10, 11, 12, 13 and 15 plans. Stories:
  `DocRefCell` `NoOpen` checks the menu, `FeedRefCell` `CopyLinkFromTheMenu` the link, and
  `DataGrid` `PagerMessage` shows a message.

Decided, and left:

- **Shared layout classes.** `PagerPanel` and `HeadedPagerPanel` keep `pager`, `pager-toolbar` and
  `listContainer` (components.css), which unconverted views and the vis toolbar row use too; they
  move when those do. `icon-cell--button` stays on the row action's button (with `icon-cell` on
  its icon), as `svgCell-button` was.
- **`HeadedPagerPanel`** stays a panel of its own until its two lists convert (wave 10), when a
  thin `FormGroup` around `PagerPanel` can be tried with their screenshots.
- **`SelectionPopup`'s position** is still GWT's `PopupPanel.setPopupPosition` (inline left and
  top), as for every popup, until the popup and `PopupManager` work. `setModel` is still once only
  and `destroy()` is still its popup's to call.
- **`ColumnInteractions.div`** stays, for the auto-fit's measuring copies made at runtime.
- **The task monitor.** The vis presenters' `TASK_MONITOR` also reaches their settings dialog
  before it is shown, so its first fetch spins the visualisation's refresh button rather than the
  tab's or the app's.
- **Untested.** No story covers `VisFrame`'s `setOnSelection` and `setOnHyperlink` (the vis
  stories hear the frame's messages directly), nor the standalone dashboard's relay.

## Done (25 October 2026)

All 16 units, in 9 commits (and one recording them): the cells (`CopyTextCell`, `SvgCell`, `ActionMenuCell`, and the checks
of `CommandLinkCell`, `HasContextMenusCell`, `SelectionBoxCell` and `MyCellTable`, which needed no
change), the three ref cells, `DataGrid` (three commits), `PagerView` with `PagerViewWithHeading`,
`SelectionList`, `SelectionPopup` and `VisFrame`. Each commit ran its stories with the leak check
and the screenshot diff; the grid's and the pager's ran all of them. (Corrected by the audit,
above.) What was decided on the way,
where it differs from the plans below:

- **`SvgCell` is `IconCell`**, named for what it is, with its stylesheet `icon-cell.css` (BEM:
  `icon-cell`, `icon-cell--button`, `icon-cell--disabled`; `svgCell-face`, unused, keeps its name).
- **`ActionMenuCell`** asks for its menu with `MenuRequest.fireFromOpener`, which can now say that
  Left on a top item closes it (`allowCloseOnMoveLeft`), as the row menus need.
- **One `RefCellsController`** for the three ref cells, which the plans allowed: its
  `configure(...)` overloads set `DocRefCell`'s `onOpen`, `FeedRefCell`'s `onOpen` and `onCopyUrl`,
  and `UserRefCell`'s `canOpen` (a predicate: anyone who manages users, else only themself) and
  `onOpen`. A cell with no `onOpen` offers no way to open. Each list presenter injects and owns
  one; the `DataGridUtil` helpers take it in place of the bus (and the security context). The
  cells' stories set the callbacks themselves; the controller's rules are JVM tested
  (`TestRefCellsController`), as the cells need a browser.
- **The menu relay is `CorePresenter`'s**, not `MainPresenter`'s, as the standalone dashboard shows
  only the core presenter: its splitters' and dialogs' menus never opened (see ISSUES.md).
- **`DataGrid`**, in three commits, each with its stories' screenshots unchanged: its inline styles
  became custom properties and state classes (the rules select through the grid's own parts with
  child combinators, as custom properties inherit into a grid in a cell); its parts come from
  `DataGrid.ui.xml`; and it has no bus. It has `DataGrid()` and also `DataGrid(int pageSize)`
  for Java callers, as `setPageSize` goes through `setVisibleRange`, which a new grid's callers
  didn't. All 127 calls of the old constructors (and 5 of `FindResultLists.create`) moved, so they
  and `getGlobalEventBus()` are gone. Its context menu is a `MenuRequest` (which now takes an
  `onHide`); its link items fire the grid's `HyperlinkEvent` when something handles it (a
  dashboard's or query's table), as a click does, and otherwise an `OpenHyperlinkRequest`; asking Stroom AI is an
  `AskStroomAiTableRequest`. `DomRequests` sends and receives all three, and `CorePresenter`,
  `ScreenHarness` and `StoryPopups.withPageRequests()` (was `withMenuRequests`) relay them. Going
  through the grid fixed a bug: a dashboard opened from a table's menu got no parent dashboard
  (stories `LinkMenuThroughTheGrid` and `LinkMenuToTheApp`; a changelog entry).
- **`PagerView` is `PagerPanel`**, a component with its template beside it, which declares its
  toolbar (hidden until `getToolbar()`) and its `MessagePanel`; `addInfoLabel`/`getInfoLabel(index)`
  became `addToolbarWidget(Widget)` (its one user keeps its label). It is still a `View` (the list
  presenters are `MyPresenterWidget<PagerPanel>`; GIN makes it unbound) and still a
  `TaskMonitorFactory`: about 90 calls pass it, and moving them to `Contexts.TASK_MONITOR` is left
  for when the list presenters become controllers. `MessagePanel` is one concrete component, with
  `white-space: nowrap` on its message (`message-panel__message` since the audit) in place of GWT's
  `Label` with no word wrap.
- **`PagerViewWithHeading` is `HeadedPagerPanel`**, a copy of the old panel (not a heading on
  `PagerPanel`, nor the plans' fallback, a `FormGroup` around one): its layout differs (the toolbar
  fills the bar, the list is in a `SimplePanel`, no message).
- **`SelectionList`** is built from its template; its breadcrumb is an `AriaPanel` with role
  `navigation` (the same landmark; an `HTMLPanel` can be a `<nav>` but can't carry its
  `aria-label`), and the grid stays in
  Java, given to its `PagerPanel` with `setDataWidget`. Its quick filter is declared, with the
  filter's own `addKeyDownHandler` for Down and Escape (after the filter's keys, as the subclass's
  override was), so `QuickFilter` needed no callback. `init(model)` is `setModel(model)` (five
  callers), and `setOnClose(Runnable)` replaced `CloseEvent`.
- **`SelectionPopup`** is a plain class that owns its `PopupPanel` (it was a `Composite` with no
  widget), its content from its template. `setModel`, `setPopupTextProvider` (on `SelectionList`
  too; `QuickFilter`'s many callers keep `registerPopupTextProvider`) and `setOnClose(Runnable)`,
  which a close handler added in its constructor calls however it closes (one bound with the popup
  missed `hide()`, which unbinds first). The table's Add Column popup, which is kept, now replaces
  its close callback each time it is shown rather than adding another handler.
- **`VisFrame`** is in `stroom.dashboard.client.component` with its template, and
  `VisFrameController` attaches to it (the presenters keep the frame, which their views place and
  their data goes to): it binds the frame's messages until it is disposed and fires `HyperlinkEvent`,
  its task monitor the context's `TASK_MONITOR`, which both vis presenters provide from their
  refresh buttons. Selections and links are `setOnSelection` and `setOnHyperlink`; `VisPane`,
  `SelectionUiHandlers` and `MessageSupport`'s bus are gone. Its placing is the `--vis-frame__*`
  custom properties; the tooltip's showing and placing are `vis-tooltip--shown` and
  `--vis-tooltip__left`/`--vis-tooltip__top`.
- **Left:** the wave's summary says the grid's sort, range and selection outputs become callbacks;
  no unit's plan lists them, and they are still GWT's handlers (`ColumnSortEvent`, range changes,
  the selection model). They change with the list controllers (wave 10). `PagerPanel` is still a
  `TaskMonitorFactory` (above).
- `setHideHeader` called after the first render now hides the header: `render` only set its
  display when the header's structure changed. The root's `data-grid--no-header` hides it now (and
  since the audit the header is drawn again when shown).

## CSS and template notes for the wave

- data-grid.css (stroom-app/src/main/resources/ui/css/data-grid.css) already uses custom properties set with DataGridDom.setCssVariable (--data-grid__columns, --total-width, --row-height, --v-travel, --h-travel). Every DataGrid inline style becomes one more property set with that helper: --data-grid__handle-left, --resize-line-left, --move-ghost-left, --move-ghost-width, --move-line-left, --head-height, --content-height, --body-offset, --v-track-top, --v-thumb-length, --h-thumb-length, --v-thumb-position, --h-thumb-position and --span (with a class data-grid__cell--span).
- There is no generic 'hidden' class in the Stroom CSS. bootstrap.css:10216 .invisible must not be used (Bootstrap is being retired). Hiding is done with state modifiers on the block: the existing data-grid--no-header (no rule yet), data-grid--resizing and data-grid--dragging, and new ones: data-grid--empty, data-grid__track--hidden, data-grid__resize-handle--shown, vis-tooltip--shown, and dialog--resize-width / --resize-height.
- dialog.css: --dialog__width and --dialog__height on .dialog__container replace the inline sizes from ResizableDialog.java:389/413 and DialogWindow.java:350/353. PopupUtil.java:165/168 (outside wave 5) sets the same sizes inline and should use them too.
- The dialog button templates reuse max and dock-container-vertical (stroom.css:793, :815) and dialog__content / dialog__buttons / dialog__button-group (dialog.css). AcceptReject's classes are in components.css:610-646. The resizableAcceptRejectContent-* classes have no CSS and can be dropped.
- (Done in wave 2.) selection-box.css: a rule .selection-box--select-only .selection-box__input { opacity: 0 } replaces the inline opacity in SelectionBoxCell's SafeHtml (:315, :326), and later BaseSelectionBox.java:114-117's getStyle().setOpacity.
- dashboard.css: the existing VisFrame-container / VisFrame-frame (:478, :487) and dashboardVis-messageOuter / -messageInner (:424, :432) classes are reused by VisFrame.ui.xml and MessagePanel.ui.xml. The positions become --vis-frame__container-* and --vis-frame__frame-* properties. They are named apart because custom properties inherit into the nested iframe.
- Templates for widgets in stroom-core-client-widget move beside their Java (src/main/java/...), not under src/main/resources, as decided after DropDownBox.ui.xml was moved: Dialog.ui.xml, DialogBody.ui.xml, PagerPanel.ui.xml, MessagePanel.ui.xml, DataGrid.ui.xml, SelectionList.ui.xml, SelectionPopup.ui.xml and HelpPopup.ui.xml.
- Several wave 5 units sit on the root element of a GWT PopupPanel (Dialog, SimplePopup, HelpPopup, SelectionPopup). That element can't come from a template, so the block class and role on it stay in Java. PopupPanel's own inline left/top (setPopupPosition) is GWT's and is left for the popup and PopupManager re-keying work.

## `DocRefCell`

cell, smart widget; complexity 3, effort M; used by 7.

**Since the merge.** Mechanical: ClipboardUtil.copy became Clipboard.writeText, and EventCell and HasContextMenus moved to stroom.widget.datagrid.client. Its menu items are now shown by DataGrid's own DataGridContextMenu. The cell's structure did not change.

- **Component:** `stroom.data.client.component.DocRefCell`
- **Controller:** `stroom.data.client.component.DocRefCellController`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Goes:** `Builder.eventBus(...) (DocRefCell.java:329-331) and the cell's EventBus field and fireEvent (:68, :90, :157)`; OpenDocumentEvent.fire(this, ...) in the cell (DocRefCell.java:144, :177): an onOpen(Consumer<DocRef>) callback in the builder, set by DocRefCellController, which fires OpenDocumentEvent on the bus; ShowMenuEvent fired through the bus for the copy menu: MenuRequest.fire(parent, ...) from the cell, or the menu items given by the controller
- **Names:** DocRefCell has no clash, and DocRefCellController is free. It moves from stroom.data.client.presenter to stroom.data.client.component beside its controller, as smart parts are split. The 'component' is a Cell, not a Composite: it can't be declared in a template, so the controller doesn't attach to a widget. Instead it is injected (GIN, unbound) into the list controller and configures the builder: docRefCellController.configure(builder). Makers that move in the same commit: ContentTemplateListPresenter.java:153, PipelineReferenceListPresenter.java:143,168,206, PropertyListPresenter.java:151, DocumentPermissionsListPresenter.java:101, UserDependenciesListPresenter.java:196 and DataGridUtil.java:617. Convert together with FeedRefCell and UserRefCell (units 299, 300).

**Stays in Java:** the cell's HTML (DocRefCell.java:200-230 and its Template): per-row SafeHtml; the copy menu's items and text (DocRefCell.java:275-315): built from the row's DocRef.

## `SvgCell`

cell, dumb; complexity 1, effort S; used by 6.

**Name (rule 12, agreed after wave 2).** It draws an icon, so it becomes `IconCell`, with its block (`svg-cell` and `SvgCell.css` become `icon-cell` and `icon-cell.css`, with `ActionCell`'s and `SvgStringCell`'s uses). `SvgIconCell` is unused, and `SvgStringCell` probably is: delete them once removals are allowed, rather than renaming them.

**Since the merge.** InfoColumn: a comment now says DataGrid instead of DataGridWidget. Nothing material.

- **Component:** `stroom.cell.info.client.SvgCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Names:** SvgCell, CellIcon and InfoColumn have no clash and keep their names and package. Already dumb. InfoColumn was changed by data_grid2 (now landed); check that it still matches before closing the unit.

**Stays in Java:** The icon markup and title (SvgCell.java:80-108): per-row SafeHtml from CellIcon's image, name and enabled flag.

## `ActionMenuCell`

cell, tidy; complexity 2, effort S; used by 4.

- **Component:** `stroom.cell.info.client.ActionMenuCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Goes:** the HasHandlers constructor parameter (ActionMenuCell.java:57, :67-93): it shows its menu with MenuRequest.fire(parent, name, items, position), which bubbles to the page's relay, instead of ShowMenuEvent on a HasHandlers (ActionMenuCell.java:136). Old constructors stay until callers move
- **Names:** ActionMenuCell has no clash. It keeps its name and package.

**Stays in Java:** The rendered button's markup and title (ActionMenuCell.java:163-185): per-row SafeHtml, named from the row.

## `MyCellTable`

widget, dumb; complexity 1, effort S; used by 2.

- **Component:** `stroom.data.table.client.Refreshable`
- **Controller:** `none`
- **Template:** `none` → `none (MyCellTable was deleted by the data_grid2 merge; the only class left, Refreshable, is an interface)`
- **Goes:** `MyCellTable.java (already deleted on master)`
- **Names:** MyCellTable is gone, so the unit is just Refreshable, and there is nothing to convert. Refreshable is also the name of stroom.dashboard.client.main.Refreshable and stroom.util.authentication.Refreshable. Those are existing clashes, not new ones. Keep it where it is (23 users), or close the unit as done.

**Stays in Java:** Refreshable as it is.

## `CommandLinkCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.cell.info.client.CommandLinkCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Names:** CommandLinkCell and CommandLink have no clash. Both keep their names and package. Already dumb (no-arg cell), so there is nothing to change but a check.

**Stays in Java:** commandLinkContainer and the link markup (CommandLinkCell.java:83-100): per-row SafeHtml from the CommandLink's text and tooltip.

## `HasContextMenusCell`

cell, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.data.client.presenter.HasContextMenusCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Names:** No clash. A dumb cell keeps its name and package (rule 1 for widgets). Its package, stroom.data.client.presenter, empties as the data presenters move to stroom.data.client.component. It could move there with the ref cells (units 298-300) so that the cells sit together, but that isn't required.

**Stays in Java:** the rendered value (HasContextMenusCell.java:36-44): data.

## `CopyTextCell`

cell, tidy; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical: ClipboardUtil.copy became Clipboard.writeText (stroom.widget.util.client), and EventCell moved to stroom.widget.datagrid.client. The cell still takes an EventBus and fires through it.

- **Component:** `stroom.data.client.presenter.CopyTextCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Goes:** the EventBus constructor parameter and implements HasHandlers / fireEvent (CopyTextCell.java:36-43, :69-72): since copy_and_paste the cell copies with Clipboard.writeText (:84) and never fires through the bus, so the parameter is dead. Its only maker is DataGridUtil.java:467
- **Names:** No clash. Keeps its name and package (see unit 296 about the package). After the copy_and_paste merge the 'indirect bus via CopyTextUtil' no longer applies: CopyTextCell doesn't call CopyTextUtil.onClick, so the cell is dumb once the bus parameter goes.

**Stays in Java:** CopyTextUtil.render markup (CopyTextCell.java:91): per-row SafeHtml.

## `UserRefCell`

cell, smart widget; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical: ClipboardUtil.copy became Clipboard.writeText, and EventCell moved to stroom.widget.datagrid.client.

- **Component:** `stroom.data.client.component.UserRefCell`
- **Controller:** `stroom.data.client.component.UserRefCellController`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Goes:** Builder.eventBus(...) and Builder.securityContext(...) (UserRefCell.java:226-228, :256-258), and the EventBus and ClientSecurityContext fields (:62-63, :78, :118); OpenUserEvent.fire (UserRefCell.java:138): a canOpen(boolean) flag and an onOpen(Consumer<UserRef>) callback, both set by UserRefCellController from ClientSecurityContext and the bus
- **Names:** UserRefCell has no clash, and UserRefCellController is free. It moves to stroom.data.client.component with units 298 and 299. Builder callers: none use 'new UserRefCell.Builder' directly by that text (search DataGridUtil and the list presenters when doing it).

**Stays in Java:** cell HTML (UserRefCell.java:165-200): per-row SafeHtml from the user's display name.

## `DataGridWidget`

widget, tidy; complexity 5, effort L; used by 114.

- **Component:** `stroom.widget.datagrid.client.DataGrid`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.ui.xml`
- **Goes:** DataGrid(HasHandlers globalEventBus[, pageSize]) constructors and DataGrid.getGlobalEventBus() (DataGrid.java:105, 192-201, 1159), replaced by DataGrid() and setPageSize(int); old constructors stay until the callers move (rule 5); DataGridContextMenu.getEventBus() (DataGridContextMenu.java:402) and the bus fires at :121 (ShowMenuEvent), :212 (HyperlinkEvent), :317 (AskStroomAiEvent): they become MenuRequest.fire(...) and two new DOM-bubbled requests, OpenHyperlinkRequest and AskStroomAiTableRequest, each relayed to the bus once for the page; `FindResultLists.create(eventBus, ...) bus parameter`; the private div(...) builders in DataGrid.java:259, ColumnInteractions.java:119 and OverlayScrollbars.java:94 (the elements come from the template); `no GIN binding (not bound)`
- **Names:** The unit is still called DataGridWidget in wave05.json, but the class is now DataGrid (renamed by data_grid2). It keeps that name and stroom.widget.datagrid.client. DataGrid clashes only with itself in all_simple_names.json. DataGridView is a different existing class in the same package, so leave it alone. The two new request classes, OpenHyperlinkRequest and AskStroomAiTableRequest, are free names. AskStroomAiRequest is NOT free: it is a shared DTO in stroom-core-shared (stroom.ai.shared.AskStroomAiRequest), so the DOM request needs another name. No controller: once the bus goes, the grid is dumb.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:283 | `handle.getStyle().setLeft(left, Unit.PX)` | dynamic | --data-grid__handle-left set on the handle with DataGridDom.setCssVariable, used by .data-grid__resize-handle { left: var(--data-grid__handle-left, 0) } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:325 | `resizeLine.getStyle().setLeft(edge - scrollLeft - HALF_LINE_WIDTH, PX)` | dynamic | --data-grid__resize-line-left, used by .data-grid__resize-line (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:420 | `moveGhost.getStyle().setLeft(clientX - pressOffset - rootLeft, PX)` | dynamic | --data-grid__move-ghost-left, used by .data-grid__move-ghost (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:421 | `moveGhost.getStyle().setWidth(model.getWidth(column), PX)` | dynamic | --data-grid__move-ghost-width, used by .data-grid__move-ghost { width: var(--data-grid__move-ghost-width) } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:431 | `moveLine.getStyle().setLeft(left, PX)` | dynamic | --data-grid__move-line-left, used by .data-grid__move-line (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:537,541 (show/hide; callers at :103-106 and through the class) | `element.getStyle().clearDisplay() / setProperty("display","none")` | static | State classes in data-grid.css. The lines and ghost are hidden by default and shown under the root modifiers that already exist: data-grid--resizing (ColumnInteractions.java:57) for the resize line, and data-grid--dragging without --resizing (:56) for the move ghost and move line. Check that the timing matches show() at :422 and :432. The handle gets a new state modifier, data-grid__resize-handle--shown. There is no generic hidden class in the Stroom CSS, and Bootstrap's .invisible must not be used |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:1468 | `empty.getStyle().setProperty("display", isEmpty && emptyMessage != null ? "" : "none")` | static | New root state modifier data-grid--empty, set with setStyleName("data-grid--empty", isEmpty && emptyMessage != null). Rule: .data-grid__empty { display: none } .data-grid--empty .data-grid__empty { display: block } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridHeader.java:56 | `head.getStyle().setProperty("display", hidden ? "none" : "")` | static | Reuse the existing modifier data-grid--no-header (DataGrid.java:831, listed in the data-grid.css header comment but with no rule yet). Add .data-grid--no-header .data-grid__head { display: none } to data-grid.css |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridHeader.java:113,117 | `head.getStyle().clearHeight() / setHeight(height, PX)` | dynamic | --data-grid__head-height on the head (removed to measure again), used by .data-grid__head { height: var(--data-grid__head-height, auto) } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridRows.java:202,207 | `content.getStyle().setHeight(window.getContentHeight(), PX) / clearHeight()` | dynamic | --data-grid__content-height, used by .data-grid--virtual .data-grid__content { height: var(--data-grid__content-height) }. The data-grid--virtual modifier already exists (DataGrid.java:1459) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridRows.java:203,208 | `body.getStyle().setProperty("transform", "translateY(" + offset + "px)") / clearProperty` | dynamic | --data-grid__body-offset, used by .data-grid--virtual .data-grid__body { transform: translateY(var(--data-grid__body-offset, 0px)) } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/OverlayScrollbars.java:130 | `vTrack.getStyle().setTop(headerHeight, PX)` | dynamic | --data-grid__v-track-top on the root, used by .data-grid__track--vertical { top: var(--data-grid__v-track-top, 0) } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/OverlayScrollbars.java:131,136 | `vTrack/hTrack.getStyle().setProperty("display", visible ? "" : "none")` | static | New state modifier data-grid__track--hidden { display: none } (data-grid.css) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/OverlayScrollbars.java:134,139 | `vThumb.getStyle().setHeight(len, PX); hThumb.getStyle().setWidth(len, PX)` | dynamic | --data-grid__v-thumb-length and --data-grid__h-thumb-length on the root, used by .data-grid__track--vertical .data-grid__thumb (height) and .data-grid__track--horizontal .data-grid__thumb (width) (data-grid.css:420, :424) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/OverlayScrollbars.java:148,149 | `v/hThumb.getStyle().setProperty("transform", "translateY/X(" + pos + "px)")` | dynamic | --data-grid__v-thumb-position and --data-grid__h-thumb-position, used by transform on the same thumb rules. The scroll-timeline animation (data-grid.css:443-467, var --data-grid__v/h-travel) still overrides them while it runs |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridHtml.java:201 | `style="grid-column: span N" in a row cell's SafeHtml` | dynamic | style="--data-grid__span: N" plus a class data-grid__cell--span { grid-column: span var(--data-grid__span) } (data-grid.css). The style attribute then holds only a custom property |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:211-231 | structure | An element template (UiBinder<DivElement, DataGrid>): <div class="data-grid data-grid--header-selection"> holding <div ui:field="scroller" class="data-grid__scroller" role="grid" tabindex="0"> with <div ui:field="head" class="data-grid__head"><div ui:field="headerRow" class="data-grid__header-row" role="row"/></div> and <div ui:field="content" class="data-grid__content"><div ui:field="body" class="data-grid__body" role="rowgroup"/><div ui:field="empty" class="data-grid__empty"/></div>, then the overlay parts below |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:213,214,219,220,222,223,224,226,227,228 | css-class | data-grid, data-grid--header-selection (the default of allowHeaderSelection), data-grid__scroller, __head, __header-row, __content, __body, __empty |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:219,223,227 | aria | role="grid" on the scroller (the default; :1436 still changes it to the profile's role or treegrid), role="row" on the header row, role="rowgroup" on the body; tabindex="0" (:220) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnInteractions.java:102-109 | structure | <div ui:field="resizeHandle" class="data-grid__resize-handle"/> inside the head; <div class="data-grid__resize-line"/>, <div class="data-grid__move-ghost"/> and <div class="data-grid__move-line"/> in the root. ColumnInteractions is given these elements instead of making them |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/OverlayScrollbars.java:79-86 | structure | <div class="data-grid__track data-grid__track--vertical"><div class="data-grid__thumb"/></div> and the same with --horizontal, in the root, passed to OverlayScrollbars |

**Stays in Java:** Row and cell HTML (GridHtml, GridRows.java:240-363, :423-445, TreeRows.java:118-129): built from data with SafeHtml, which the design keeps for grid rows and cells; Changing ARIA: aria-label (DataGrid.java:903), the scroller's role by profile or tree (:1436), aria-rowcount, aria-colcount and aria-multiselectable (:1472-1479), the header row's role while there are no columns (GridHeader.java:79), and per-row aria-rowindex, -posinset, -setsize, -selected, -label, -description, -checked, -level and -expanded; State modifiers set with setStyleName: --multiline, --list, --fit, --fit-width, --striped, --no-cell-padding, --no-header, --header-selection, --selectable, --virtual, --contain, --dragging, --resizing, --scroll-timeline, __thumb--active, __cell--keyboard, __empty--text (DataGrid.java:978); The empty message text (DataGrid.java:969), which comes from the caller; The CSS custom properties --data-grid__columns, --total-width, --row-height, --v-travel and --h-travel (DataGrid.java:1540-1545, GridRows.java:193,213, OverlayScrollbars.java:143-144): already custom properties, so they meet rule 3; ExpanderCell (the declares list) can't go in a template: it is a cell drawn inside rows' SafeHtml, not a widget; Context menu items and their text (DataGridContextMenu): a menu built from data and the row clicked.

## `FeedRefCell`

cell, smart widget; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical: ClipboardUtil.copy became Clipboard.writeText, and EventCell and HasContextMenus moved to stroom.widget.datagrid.client.

- **Component:** `stroom.data.client.component.FeedRefCell`
- **Controller:** `stroom.data.client.component.FeedRefCellController`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml)`
- **Goes:** `Builder.eventBus(...) (FeedRefCell.java:320-322), the EventBus field and fireEvent (:66, :86, :154)`; OpenFeedEvent.fire (FeedRefCell.java:146, :174) and CopyFeedUrlEvent.fire (:287): onOpen(Consumer<String>) and onCopyUrl(Consumer<String>) callbacks set by FeedRefCellController, which fires the bus events
- **Names:** FeedRefCell has no clash, and FeedRefCellController is free. It moves to stroom.data.client.component with unit 298. Its one maker is DataGridUtil.java:558. If the three controllers end up nearly identical, merging them into one RefCellsController (also free) is fine; the units should be done together.

**Stays in Java:** cell HTML and the Template's class/title strings (FeedRefCell.java:206, :301-305): per-row SafeHtml; menu items built from the feed name (FeedRefCell.java:270-290).

## `PagerView`

composite, tidy; complexity 2, effort M; used by 86.

**Since the merge.** Moved from stroom.data.grid.client to stroom.widget.datagrid.client (its suggested PagerPanel name and package should follow); about 90 files reference PagerView.

- **Component:** `stroom.widget.datagrid.client.PagerPanel`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/datagrid/client/PagerViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml`
- **Goes:** PagerView.java (the view interface) and PagerViewImpl.java (becomes PagerPanel, a Composite with a no-arg constructor and a Binder made with GWT.create); GIN: AppModule.java:171 bindSharedView(PagerView.class, PagerViewImpl.class); DashboardAppModule.java:190 the same; workbench ScreenViewsModule.java:93 the same (PagerPanel is made just in time by GIN, unbound); new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class)) at SelectionList.java:139, RecentItemsDialogStories.java:159, DataGridStories.java:690, :706, :997, SplitPaneContent.java:81, SelectorWidgets.java:103; MessagePanel.java (interface) and MessagePanelImpl.java, which become one concrete class MessagePanel (users: PagerViewImpl, VisViewImpl.java:48, QueryResultVisViewImpl.java:51); `implements TaskMonitorFactory (PagerViewImpl.java:91-109): the RefreshButton's setRefreshing through the Contexts.TASK_MONITOR adapter`; `addInfoLabel/getInfoLabel(index) (PagerViewImpl.java:118-134): an addToolbarWidget(Widget) that returns nothing positional`
- **Names:** The interface PagerView and its ViewImpl become one component. 'PagerView' would keep a View-suffixed name for a component that is no longer a view, so it is renamed PagerPanel (free in all_simple_names.json; Pager is an existing widget in stroom.data.pager.client, so 'Pager' can't be used). It stays in stroom.widget.datagrid.client (moved there by the merge; the 'becomes' text's stroom.data.grid.client is out of date). About 89 files mention PagerView; callers change their constructor parameter type in the same commit. MessagePanel keeps the interface's name once the Impl is folded in (no other class has it).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerViewImpl.java:75-76 | component-declaration | <b:Toolbar ui:field="toolbar" name="List actions" visible="false"/> inside pager-toolbar, instead of new Toolbar("List actions") made on first use. getToolbar() makes it visible (a list with no buttons still has no toolbar shown) |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerViewImpl.java:75 | aria | name="List actions" (the toolbar's accessible name) in the template |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerViewImpl.java:45,64 | component-declaration | <dg:MessagePanel ui:field="messagePanel"/> declared in listContainer. setDataWidget inserts the grid before it (listContainer.insert(widget, 0)) |
| stroom-core-client-widget/src/main/resources/stroom/widget/datagrid/client/PagerViewImpl.ui.xml | component-declaration | <p:Pager ui:field="pager" addStyleNames="dock-min"/> (already declared; it holds the RefreshButton). Pager, RefreshButton and Toolbar are in the declares list |
| stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/MessagePanelImpl.java:35,63-64 | structure | MessagePanel's own template (MessagePanel.ui.xml beside it): <g:SimplePanel styleName="dashboardVis-messageOuter" visible="false"><g:HTML ui:field="message" styleName="dashboardVis-messageInner"/></g:SimplePanel> (classes in dashboard.css:424, :432), instead of a Label made on first use |

**Stays in Java:** pagerContainer.setVisible (PagerViewImpl.java:88) and the message panel's setVisible (MessagePanelImpl.java:36, :77): state; the message text (MessagePanelImpl.java:44-70): from errors; listContainer.add(widget) of the caller's grid (PagerViewImpl.java:63): the grid is the caller's, until list controllers declare the grid inside the panel.

## `PagerViewWithHeading`

composite, tidy; complexity 1, effort S; used by 4.

**Since the merge.** Moved from stroom.data.grid.client to stroom.widget.datagrid.client. Nothing else.

- **Component:** `stroom.widget.datagrid.client.PagerPanel`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/datagrid/client/PagerViewWithHeadingImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml`
- **Goes:** `PagerViewWithHeading.java and PagerViewWithHeadingImpl.java`; `stroom-core-client-widget/src/main/resources/stroom/widget/datagrid/client/PagerViewWithHeadingImpl.ui.xml`; GIN: AppModule.java:172 bindSharedView(PagerViewWithHeading.class, PagerViewWithHeadingImpl.class); workbench ScreenViewsModule.java:94 the same; `implements TaskMonitorFactory (PagerViewWithHeadingImpl.java:93-111)`
- **Names:** Merged into PagerPanel (unit 95) as an optional heading (setHeading(String)), as the migration notes propose, rather than kept as a second panel. Only JobNodeListPresenter and NodeJobListPresenter use it. Their screenshots must match: today the heading is a FormGroup wrapping the whole list (FormGroup is in the declares list). If the merged template can't reproduce that without changing every other list, keep a thin HeadedPagerPanel (a free name) declaring <form:FormGroup><dg:PagerPanel/></form:FormGroup> instead.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/resources/stroom/widget/datagrid/client/PagerViewWithHeadingImpl.ui.xml | component-declaration | <form:FormGroup ui:field="pagerFormGroup" addStyleNames="max"> around the panel. The toolbar <b:Toolbar ui:field="toolbar" name="List actions" addStyleNames="dock-max"/> and the Pager are already declared |

**Stays in Java:** setHeading's label text (PagerViewWithHeadingImpl.java:69-71): from the caller; pagerContainer.setVisible (PagerViewWithHeadingImpl.java:85): state.

## `SelectionList`

widget, tidy; complexity 4, effort M; used by 6.

- **Component:** `stroom.item.client.SelectionList`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.ui.xml`
- **Goes:** `new DataGrid<>(new SimpleEventBus(), 100) (SelectionList.java:108): the private bus goes once DataGrid has a no-arg constructor`; `new PagerViewImpl(GWT.create(PagerViewImpl.Binder.class)) (SelectionList.java:139)`; the anonymous QuickFilter subclass (SelectionList.java:142-151): QuickFilter needs a key callback (e.g. setOnKeyDown(Consumer<KeyDownEvent>)) so that it can be declared; `the layout built in Java (SelectionList.java:154-171)`; `init(model) two-phase construction and destroy(): setModel(...), with clean-up on detach or through the owner`; `CloseEvent/addCloseHandler: setOnClose(Runnable) beside it until the callers (SelectionPopup, BaseSelectionBox) move`
- **Names:** SelectionList has no clash. It keeps its name and package (stroom.item.client). It uses PagerPanel (the new name for PagerView, unit 95, same wave) rather than PagerViewImpl, so units 95 and 52 must go in the same commit, or 95 first.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:165-170 | structure | <g:FlowPanel styleName="selection-list dock-container-vertical"> holding the filter, the nav and <g:SimplePanel styleName="dock-max selection-list__options"> around the pager panel |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:142-154 | component-declaration | <qf:QuickFilter ui:field="quickFilter" addStyleNames="dock-min selection-list__filter"/> (QuickFilter, from the declares list) |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:157-159 | structure | The breadcrumb as an element in an HTMLPanel or a FlowPanel with tag nav: <nav ui:field="links" class="dock-min selection-list__path" aria-label="Breadcrumb"/> |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:159 | aria | aria-label="Breadcrumb" |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:108,139-140 | component-declaration | <dg:PagerPanel ui:field="pager"><dg:DataGrid ui:field="cellTable" pageSize="100"/></dg:PagerPanel>, or the grid declared and given to the pager with setDataWidget. This needs DataGrid's no-arg constructor (unit 0) |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:154,158,162,166 | css-class | dock-min selection-list__filter, dock-min selection-list__path, dock-max selection-list__options, selection-list dock-container-vertical |

**Stays in Java:** The breadcrumb's LinkButton, Label separator and current-level label (SelectionList.java:694-713), with path-link, path-separator and path-current classes, aria-current="page" and aria-hidden: one set per level of the model's path, so made from data. LinkButton is in the declares list but can only be made per level; quickFilter.setVisible and links.setVisible from the model (SelectionList.java:364-365, :400-401); The quick filter's accessible name, "Filter " + name (SelectionList.java:201-208): built from data; The grid's profile, row name, selection model and column (SelectionList.java:109-136): behaviour, not markup.

## `VisFrame`

widget, smart widget; complexity 4, effort L; used by 4.

- **Component:** `stroom.dashboard.client.component.VisFrame`
- **Controller:** `stroom.dashboard.client.component.VisFrameController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/VisFrame.ui.xml`
- **Goes:** `VisPane.java (interface with one implementation, VisFrame; nothing else references it)`; SelectionUiHandlers.java (VisPane extends HasUiHandlers<SelectionUiHandlers>): VisFrame gets setOnSelection(Consumer<JavaScriptObject>). VisSelectionModel, which implements it, becomes the controller's callback; VisFrame(EventBus) constructor (VisFrame.java:60): VisFrame() with no-arg construction. MessageSupport no longer takes the bus (MessageSupport.java:58, :67-69, :268-269). Hyperlinks go out through setOnHyperlink(Consumer<Hyperlink>), and VisFrameController fires HyperlinkEvent (MessageSupport.java:133); `bind()/unbind() called by the owner (VisFrame.java:142-148): the controller binds on attach and unbinds in onDispose`; `setTaskMonitorFactory (VisFrame.java:389): the controller watches Contexts.TASK_MONITOR`
- **Names:** VisFrame has no clash, and VisFrameController is free. The pair goes to stroom.dashboard.client.component, as the review proposed. That package is new. It must not be confused with stroom.dashboard.client.main.Component, which is planned to be renamed DashboardPane before the dashboard waves. VisFrame is also used by QueryResultVisPresenter (stroom.query.client.presenter), but its area is the dashboard. MessageSupport, PostMessage, MessageEvent, VisTooltip and VisTooltipPlacement keep their names and move with it, or stay in stroom.dashboard.client.vis as helpers. Callers: VisPresenter.java:157 and QueryResultVisPresenter.java:122 (new VisFrame(eventBus)) make the controller with make(provider). MessagePanelImpl, which the record lists as embedded, is used by VisViewImpl and QueryResultVisViewImpl, not by VisFrame.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisFrame.java:112,136-139 | `container style.setLeft/Top/Width/Height(..., Unit.PX) (setContainerPositionAndSize)` | dynamic | --vis-frame__container-left, -top, -width and -height on the container, used by .VisFrame-container { left: var(--vis-frame__container-left); top; width; height } (dashboard.css:478). They have their own names because custom properties inherit into the frame |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisFrame.java:124,136-139 | `frame style.setLeft/Top/Width/Height(..., Unit.PX) (setInnerPositionAndSize)` | dynamic | --vis-frame__frame-left, -top, -width and -height on the iframe, used by .VisFrame-frame (dashboard.css:487) |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisTooltip.java:73,107,117 | `tooltip.getStyle().setDisplay(BLOCK / NONE)` | static | A state modifier vis-tooltip--shown: .vis-tooltip { display: none } .vis-tooltip--shown { display: block } (vis-tooltip.css:30) |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisTooltip.java:90-91 | `element.getStyle().setLeft/Top(placement, Unit.PX)` | dynamic | --vis-tooltip__left and --vis-tooltip__top, used by .vis-tooltip { left: var(--vis-tooltip__left); top: var(--vis-tooltip__top) } (vis-tooltip.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisFrame.java:62-69 | structure | <g:SimplePanel styleName="VisFrame-container"><g:Frame ui:field="frame" styleName="VisFrame-frame" url="ui/vis.html"/></g:SimplePanel> |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisFrame.java:63,68 | css-class | VisFrame-frame, VisFrame-container (existing, dashboard.css:478-492) |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisTooltip.java:114-120 | structure | Optional: VisTooltip.ui.xml as an element template (UiBinder<DivElement, ...>): <div class="popup vis-tooltip" aria-hidden="true"><div ui:field="content" class="vis-tooltip__content"/></div>, instead of createDivElement and setClassName. It stays a single element on the page's body |
| stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisTooltip.java:116 | aria | aria-hidden="true" |

**Stays in Java:** frame.setTitle(title) (VisFrame.java:104): the dashboard component's name, from data; setUrl (VisFrame.java:75-78): assets URL from data; the JSON messages, scripts and class names posted to the frame (VisFrame.java:180-390); PostMessage's static singleton and frame id counter, and the JSNI postMessage bridge: no markup; the tooltip's sanitised HTML (VisTooltip.nativeSetSanitisedContent): from the visualisation.

## `SelectionPopup`

widget, tidy; complexity 2, effort S; used by 3.

- **Component:** `stroom.item.client.SelectionPopup`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionPopup.ui.xml`
- **Goes:** `new SelectionList<>() and new SimplePopupLayout() built in Java (SelectionPopup.java:92-100)`; `init(model) two-phase API, replaced by setModel`; `addCloseHandler(CloseHandler<PopupPanel>), replaced by setOnClose(Runnable) once BaseSelectionBox and SelectionBoxCell move`; `registerPopupTextProvider, renamed setPopupTextProvider`
- **Names:** SelectionPopup has no clash. It keeps its name and package. Today it extends Composite but never calls initWidget. Either make it a plain class that owns its PopupPanel, or have the template make the popup's content only.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionPopup.java:173,183 | `popupPanel.setPopupPosition(-1000,-1000) / setPopupPosition(left, top)` | dynamic | GWT PopupPanel writes its own left and top. This goes when the popup is placed through PopupUtil or PopupManager (apiIssues). If kept, use --selection-popup__left and --selection-popup__top on .selection-popup (selection-popup.css), with PopupPanel's position overridden |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionPopup.java:92-100 | component-declaration | Template for the popup's content: <v:SimplePopupLayout><i:SelectionList ui:field="selectionList" fitContent="true"/></v:SimplePopupLayout>, added to the PopupPanel with popupPanel.add(binder.createAndBindUi(this)) |

**Stays in Java:** popupPanel.setStyleName("selection-popup") (SelectionPopup.java:103): the PopupPanel's own root, which no template makes; setKeyboardSelectionPolicy(ENABLED) (SelectionPopup.java:93): behaviour; the anonymous PopupPanel's onPreviewNativeEvent for Escape and Tab (SelectionPopup.java:69-90).

## `SelectionBoxCell`

cell, dumb; complexity 2, effort S; used by 1.

**Since the merge.** Adds showChoice: after a choice in the popup it writes the chosen label straight into the row's combobox value and its selection-box__value element, so the cell shows it before the grid redraws. Still a dumb cell.

- **Component:** `stroom.item.client.SelectionBoxCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because a grid cell renders SafeHtml through its SafeHtmlTemplates, which the design keeps for cells and rows)`
- **Names:** SelectionBoxCell and SelectionItemCell have no clash. Both keep their names and stroom.item.client.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/item/client/SelectionBoxCell.java:315,326 | `<input ... style="opacity: 0" ...> in Template.box/readOnlyBox` | static | Done in wave 2: .selection-box--select-only .selection-box__input { opacity: 0 } in selection-box.css. The cell's root already carries selection-box--select-only. BaseSelectionBox.java:114-117 sets the same opacity inline, and can use this rule when it is tidied (wave 5 changes its popup and base) |

**Stays in Java:** The SafeHtmlTemplates markup (SelectionBoxCell.java:311-329): fixed classes, role="combobox", aria-haspopup and the readonly variant, already declared in the cell's template strings as the design allows; aria-expanded and aria-controls on the combobox while the list is open (SelectionBoxCell.java:181-182, :193-194): state; The arrow SVG with class icon-box__image (SelectionBoxCell.java:112-113); SelectionItemCell's explorerCell and selectionItemCell-* markup (SelectionItemCell.java:37-71): per-row data.

