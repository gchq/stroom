# Wave 6: Editor, menu, tree engine, explorer tree and the remaining pickers (after merges)

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`.

Turn the Ace EditorPresenter into a dumb CodeEditor with a settings model in place of its 13 option getters. Split MenuPresenter into a component. Configure DraggableTreePanel and PipelineTreePanel with callbacks instead of subclassing. Split AbstractExplorerTree into a tree widget and an ExplorerTreeModel controller. Give FlexLayout and TabLayout no-arg construction. Finish the UserRef and DocumentAsset box splits so DropDownViewImpl can go.

## Audited (25 October 2026)

Six reviewers read the wave against this plan, "Done means" and AGENTS.md (the trees, the pickers,
the choosers and menu, the trace overview, the code editor, and the records). Fixed:

- **A regression.** A second click on an annotation's setting (Status, Labels, Collections, Choose
  Comment) opened a second chooser over the first, rather than closing it, as each opening is a
  one-use chooser the popup manager hadn't seen. With two label choosers open, a tick in the older
  could take back the newer's. Each caller keeps an `OpenChooser`, which closes the chooser opened
  from the element clicked (story `SecondClickClosesTheChooser`, which failed before; this branch
  only, so no changelog entry). `StatusSettingBlock` now checks that choosing the current status
  leaves the chooser open and another closes it.
- **The editor could not be declared.** `CodeEditorController` was injected with its own
  `CodeEditor`, so the later waves' templates couldn't declare one; it attaches now
  (`attach(CodeEditor)`), and `EditorPresenter` is given both. `SingleLineCodeEditorController`
  attaches too, watches `Contexts.READ_ONLY` and has `setOnChange`, in place of `setReadOnly` and
  GWT's value change handler.
- **Rule 6.** The explorer's classes are BEM, with every use (the cells that borrow them, the
  stories and the suite's scripts): `explorer-tree` (was `explorer-tree-panel`),
  `explorer-tree__grid` (was `explorerTree`), `explorer-cell` with `__expander`, `__tick-box`,
  `__icon`, `__icon-wrapper`, `__alert-icon`, `__text`, `--filter-match` and `--filter-no-match`
  (were `explorerCell-*`). The editor's stylesheet is `code-editor.css`, named for its block, and the
  indicator popup's block has its own `code-editor-indicator.css`.
- **Rule 2.** The trees' grid settings (profile, cell padding, fit width, no context menu) are in
  their templates, with a new `DataGrid.setHasContextMenu` setter for the last; the chooser's cell
  uses `SafeHtmlTemplates`.
- **The task monitor.** The explorer tree controller's REST call takes its owners' monitor; the
  three presenters that passed themselves did nothing more, and only Navigation's spinner is set.
- **Smaller things.** A sub menu whose items arrive after its menu has closed isn't shown; the drag
  copy's classes come off after a drag; `setTicked`, `getTickedSet` and `setShowAlerts` are safe on
  a tree without tick boxes or before it is attached (and the controller applies `showAlerts` on
  attach); the Span Info's region has the scroll region's focus ring; an unused `Chooser.clearFilter`,
  `CodeEditor.setMode` (which bypassed the mode the options keep), `EditorPresenter.getController`
  and `CodeEditorController.getAceEditorController` are gone; `EditorPresenter` keeps its 2017;
  javadoc, stale comments (the floor map views, `pathways.css`, the editor's never-shown popup) and
  unused imports. Stories: the asset picker's OK with the mouse, a double click, Cancel and a
  disabled picker.
- **The records.** The claims below that weren't so (the floor map's layer style makes its box; the
  editor's users; `TextPresenter.getContextMenu`; the baselines taken again; which screenshots
  differed), what is left (below), a "Since wave 6" note in the wave 7, 8, 11, 12 and 15 plans, the
  order's rows, `A11Y.md`'s scrolling regions, the stale names in `COMPONENTS.md`,
  `COMPONENT_MODEL_DESIGN.md` and the suite's `dirty.mjs`, and the GWT inventory, regenerated.

All 1,622 stories passed with the leak check after the fixes (the 39 screenshots that differed are the noise named in Done, below), and the app and the
standalone dashboard GWT compile.

## Done (25 October 2026)

All 16 units, in 11 commits. `FlexLayout` and `TabLayout`, named in the paragraph above, are not
units of this wave: they are wave 8's, with the dashboard. Each commit ran its stories with the
leak check and the screenshot diff. The `DropDownView` commit ran all 1,617, and so did the last,
the editor's (1,618 passed; the 39 screenshots that differed are the force graphs, the word
cloud's and line chart's keyboard stories, the data grid's spinner column, AppShell `OpensDocument`
and the ScheduleBox dialog's 1px shift, all recorded as noise in ISSUES.md), and the app and the
standalone dashboard GWT compile. What was decided on the way, where it differs from the plans
below:

- **The trees.**
  - `DraggableTreePanel` is concrete and configured with callbacks (`setIsValidTarget`,
    `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), and so is its
    `TreeKeyboard`.
  - `PipelineTreePanel` and `PipelineElementBox` have templates; the boxes are placed by
    `--pipeline-element__*`.
  - `AbstractExplorerTree` and `ExplorerTickBoxTree` are one `ExplorerTree` (`setTickBoxes`,
    `setOnChoose`, `setOnInitialSelect`), fed by an `ExplorerTreeController` (was
    `ExplorerTreeModel`); `OpenItems` moved with them to `stroom.explorer.client.component`.
  - `DocumentAssetTree` has a template, `setAccessibleName` and `setOnSelect`; its
    `HasSelectionHandlers` went in the same commit, as both its users moved (rule 5 allows it).
- **The pickers.**
  - `DocumentAssetChooser` is shown once by `DocumentAssetChooserController.choose(...)`, with the
    new `ControllerDialog.setActionEnabled` (OK only while a file is chosen) and `pressAction`
    (Enter or a double click on a file).
  - The floor map's object edit dialog declares a `DropDownBox` in its template; its layer style
    dialog, whose view is built in Java (its own unit is wave 11's), makes one. Both attach a
    `DocumentAssetSelectionController`. Their forms stories now show the box, as the dialogs do
    (4 baselines taken again).
  - `UserRefSelectionController` has the user picker's logic, and `UserRefSelectionBoxPresenter`
    stays a thin wrapper for its 11 callers.
  - `DropDownBox` is a `View`, so both wrappers are `MyPresenterWidget<DropDownBox>`;
    `DropDownView`, `DropDownViewImpl`, `DropDownUiHandlers` and their 3 bindings are deleted.
- **The choosers are one-use popups.** `Chooser<T>` (`chooser.css`) is shown by a
  `ChooserController` or `MultiChooserController` made each time one opens, with
  `show(opener, element, caption, onChoose)`. A controller can't be shown again once its popup is
  hidden (disposal is final), and the schedule and time range pickers already work this way. Each
  caller keeps an `OpenChooser`, so that a second click on a setting closes its chooser, as the
  popup manager did for a chooser shown again (fixed in the audit, above). Its generic template
  can't use `@UiHandler`, so the filter's handler is added in Java.
- **The menu is `MenuController`**, beside `Menu`, as this plan says (the order's notes said
  `MenuPopup` and `MenuPopupController`; corrected there). It merges `MenuPresenter` and
  `MenuViewImpl`; `Menu` has a template and no inline styles (`--menu__width`,
  `menu__viewport--fixed-width`). Sub menus and `MenuManager`'s menus are shown with
  `ControllerDialog.showOnce`, and closed with the new `ControllerDialog.hide`.
- **`TraceOverviewWidget`** has a no-arg constructor and a template for its frame. Its drawing is
  placed by custom properties, its copy menu is a `MenuRequest` (`CopyTextUtil.onClick(e)`), and
  its Span Info, which could be scrolled only with a mouse, is a named region that takes the focus
  (a new story, `SpanInfo`, found it; on master too, so a changelog entry).
- **The editor.**
  - `EditorViewImpl` and `AbstractEditorViewImpl` are `CodeEditor`, a component whose template
    declares the `Editor`, the error panel and the `RightBar`.
  - `CodeEditorOptions` holds the options and the mode.
  - `CodeEditorController` has the text, Format and the menu (was `EditorMenuPresenter`), and
    attaches to the editor (`attach(CodeEditor)`), so a template can declare it.
  - `AceEditorController` (was `AbstractEditorPresenter`) has the user's preferences, the global
    keys and the completer, and destroys the editor when disposed.
  - `SingleLineCodeEditor` with `SingleLineCodeEditorController` (which attaches to it, watches
    `Contexts.READ_ONLY` and has `setOnChange`) replaces `SingleLineEditorPresenter`.
  - `EditorPresenter` is a thin `MyPresenterWidget<CodeEditor>` for its users (26 files in
    `stroom-core-client`, and 7 stories; the plan's 31 was its fan-in), with the same API, its
    option getters included. Nothing called its `getContextMenu()` but `TextPresenter`'s, which
    nothing called, so both are gone.
  - `EditorView`, `BaseEditorView`, `SingleLineEditorView` and their bindings are deleted.
  - `FormatEvent`'s constructor is public, for the controller's listeners.
  - The overview bar's markers are placed by `--code-editor__marker-top`.
  - Its stylesheet is `code-editor.css` (was `editor.css`), and the indicator popup's block is in
    `code-editor-indicator.css` (moved in the audit).

Found on the way and recorded in ISSUES.md:
- the editor's own `IndicatorPopup`, never shown;
- the trace overview's fixed text, which stays in its drawn HTML;
- the unused `chooserViewImpl-*` CSS;
- the top bar's and the editor snippet's flaky stories.

Left, and why:

- **Callers of the editor's option getters.** `EditorPresenter` keeps its 12 `get...Option()`
  getters and `getFormatAction()` for its users, which move to `CodeEditorOptions` as their screens
  convert (waves 8, 11 and 12), as rule 5 allows; the risk the order named is met by the model
  being there.
- **The trace overview's stylesheet.** `TraceOverviewWidget` keeps its name (a `TraceOverview`
  DTO exists) and its rules in `pathways.css`, which the traces screens share, with the class names
  they had, until wave 12's `TracesListTabPresenter` gives the pathways blocks their own files.
  `explorer-tree.css` still holds the type filter's two rules too, and the asset tree's grid has an
  unused `documentAssetTree` class (removals wait).
- **Small things left as they were.** The drag copy's `aria-hidden` and `inert` are still set by
  the two tree views (wave 7's); the fixed "None" text is in three controllers (no `emptyText` on
  `DropDownBox`); a chooser is placed before its values arrive, as the label chooser always was;
  `ControllerDialog`'s map of hosts keeps a builder never fired until its opener is disposed.
- **Copyright years.** A class renamed, even when git sees a new file because it changed so much
  (`CodeEditor`, `MenuController`, `ChooserController` and the rest), keeps its year; a class that
  is new keeps 2026 (`OpenChooser`, `DocumentAssetSelectionController`, `UserRefSelectionController`).

## CSS and template notes for the wave

- Reuse: max (stroom-app/src/main/resources/ui/css/stroom.css:793) for every setWidth("100%")/setHeight("100%") pair (AbstractExplorerTree.java:162-163, DocumentAssetTree.java:114-115). Also form-control-border form-control-background (already in DocumentAssetChooserViewImpl.ui.xml:22) for the bordered asset tree, sr-only for the live regions (AbstractExplorerTree, TreeKeyboard), and w-100 from stroom.css:843 (not Bootstrap's) for the Chooser's grid.
- New explorer-tree-panel (position: relative) in explorer-tree.css. ExplorerTree (unit 369) and DocumentAssetTree (unit 266) share it, used with max: both wrap their DataGrid in the same relative, full-size FlowPanel today.
- New --explorer-cell__depth custom property, with .explorerCell-expander (explorer-tree.css:41) given padding-left: calc(4px + var(--explorer-cell__depth, 0) * 17px). ExplorerCell.java:99-101 and DocumentAssetTree.ItemCell (DocumentAssetTree.java:307-308) use the same 4 + depth * 17 formula, so both write only the custom property.
- Custom property names follow the existing --block__name style (e.g. --menu__border-radius, --pipeline-element__border-color): --menu__width, --code-editor__marker-top, --draggable-tree__drag-left/top, --pipeline-element__left/top/width, and pathways.css's --grid-lines__*, --time-slider__start/end, --timeline-span__*, --span-bar__*, --operation__depth, --span-info__width.
- Wave 5 dependency. Chooser, ExplorerTree and DocumentAssetTree can declare <dg:DataGrid> only if DataGrid has a template constructor; today it has only DataGrid(HasHandlers) and DataGrid(HasHandlers, int), DataGrid.java:192,200. They also need attribute setters for page size, profile, cellPadding, fitWidth and fitContent, and a setter in place of hasContextMenu(boolean).
- ARIA on widget containers. UiBinder sets a widget tag's attributes only through setters, and no template in the repo puts role= on a g: widget. Containers that hold widgets and need a role or label (PipelineTreePanel's items panel role=tree aria-label="Pipeline structure" and faded group role=none) need either an HTMLPanel with element fields and add(widget, element), or a small shared FlowPanel with setRole and setAriaLabel. AriaPanel is a free name for it (e.g. stroom.widget.util.client).
- Thin wrappers without view interfaces. Make DropDownBox (wave 2) and the new CodeEditor implement stroom.widget.presenter.client.View, which is only IsWidget. The presenters kept for their many callers (DocSelectionBoxPresenter, UserRefSelectionBoxPresenter, EditorPresenter) can then be MyPresenterWidget<DropDownBox> or MyPresenterWidget<CodeEditor>, made by GIN with no binding. DropDownView, DropDownViewImpl, DropDownUiHandlers, EditorView and BaseEditorView can then all go in this wave.
- Selection controllers attach to DropDownBox, following the pilot's DocSelectionController: UserRefSelectionController (stroom.security.client.component) and DocumentAssetSelectionController (stroom.document.asset.client.component). The fixed 'None' text appears in three places (UserRefSelectionBoxPresenter.java:92, DocumentAssetDropDownPresenter.java:50, DocSelectionController.NONE_DISPLAY_VALUE). It could become one emptyText attribute on DropDownBox, written in the declaring templates.
- New classes in this wave: chooser__item (components.css), timeline-span and timeline-header__end (pathways.css), draggable-tree__dragged and draggable-tree__dragged--moving (draggable-tree.css), menu__viewport--fixed-width and a .menu rule (menu.css). Static moves into existing rules: .menu__viewport (menu.css:41), .timeline-bar (pathways.css:144), .pipeline-element (pipeline-element.css:29), .code-editor__marker (editor.css:288).

## `ExplorerTreeModel`

model-or-util, smart class; complexity 4, effort M; used by 4.

- **Component:** `none (a controller only; its view is stroom.explorer.client.component.ExplorerTree, unit 369)`
- **Controller:** `stroom.explorer.client.component.ExplorerTreeController`
- **Template:** `none` → `none (built in Java because it has no view: it fetches and holds the explorer's nodes)`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/ExplorerTreeModel.java (merged into ExplorerTreeController: REST fetch with owner(this) in place of the TaskMonitorFactory, the name filter Timer (:434-449) as an onDispose clean-up, and onDataChanged (:377) as a callback); Direct users of ExplorerTreeModel: EntityTreePresenter, ExplorerPopupPresenter, EntityCheckTreePresenter, ExplorerFixture, ExplorerTreeStories. They call the controller instead, in the same commit
- **Names:** The suggested ExplorerTreeModel + ExplorerTreeModelController doesn't fit the layers. A model is shared data with no controller of its own, and ExplorerTreeModel makes REST calls and runs timers, which is controller work. Its simple name also clashes with stroom.explorer.impl.ExplorerTreeModel, the server side (stroom-explorer-impl), in all_simple_names.json. It merges into ExplorerTreeController (unit 369, free). If the merged controller grows too large (449 + the smart half of AbstractExplorerTree), the fetching can stay as a plain helper owned by it under a new name, e.g. ExplorerNodeFetcher (free), but not as a second controller.

**Stays in Java:** All of it: filters, included types, tags, permissions, open state and fetching are behaviour, not markup.

## `DraggableTreePanel`

widget, tidy; complexity 4, effort M; used by 3.

**Since the merge.** copy_and_paste added a contextmenu path: it now sinks ONCONTEXTMENU, leaves right-clicks on a TextField to it, and fires ContextMenuEvent with the field's TextEditTarget so the owner's menu starts with the edit items (used by expression term values). MouseHelper moved to stroom.widget.datagrid.client. The ContextMenuEvent-with-edit-target contract must survive the conversion in both ExpressionTreeViewImpl and PipelineTreeViewImpl. No longer in flight.

- **Component:** `stroom.pipeline.structure.client.view.DraggableTreePanel`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.ui.xml`
- **Goes:** The constructor arguments (DraggableTreePanel.java:108): a no-arg constructor and setTreePanels(TreePanel<E> tree, TreePanel<E> subTree), so PipelineTreeViewImpl and ExpressionTreeViewImpl (wave 7) can declare it; TreeKeyboard's abstract hooks (getName and the rest, subclassed anonymously in PipelineTreeViewImpl.java:98 and ExpressionTreeViewImpl) become callbacks set on it, as the migration order asks ('callbacks instead of subclassing')
- **Names:** A dumb widget, so it keeps its name and package. TreePanel and Box are abstract bases with two implementations each (pipeline and query expression), so they stay; they are the engine's extension points, not a base with one user. No clash in all_simple_names.json.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.java:297-301 | `subTreePanel style: setPosition(ABSOLUTE), setZIndex(2), setOpacity(0), setLeft(0, PX),…` | static | new class draggable-tree__dragged in stroom-app/src/main/resources/ui/css/draggable-tree.css: position: absolute; z-index: 2; opacity: 0; left: var(--draggable-tree__drag-left, 0); top: var(--draggable-tree__drag-top, 0). Added when the drag starts, removed when it ends |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.java:272 | `subTreePanel.getElement().getStyle().setOpacity(0.5)` | static | new modifier draggable-tree__dragged--moving (opacity: 0.5) in draggable-tree.css, added once the dragged copy moves |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.java:270-271 | `subTreePanel style setLeft(x - offsetX, PX); setTop(y - offsetY, PX)` | dynamic | --draggable-tree__drag-left and --draggable-tree__drag-top set on the dragged copy (setProperty of the custom properties), used by draggable-tree__dragged |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.java:390-391 | `subTreePanel style setLeft/setTop while edge scrolling` | dynamic | the same --draggable-tree__drag-left and --draggable-tree__drag-top |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/DraggableTreePanel.java:111-114 | structure | <g:FlowPanel ui:field="layoutPanel" styleName="draggable-tree"> as the template's root, in place of new FlowPanel and setStyleName(BLOCK); the tree panel is added by setTreePanels |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/TreeKeyboard.java:85-89 | aria | <div ui:field="status" class="sr-only draggable-tree__status" role="status" aria-live="polite"/> in DraggableTreePanel's template (needs an HTMLPanel root or an element field), exposed as getStatusElement() for TreeKeyboard, in place of createDivElement, setClassName, setAttribute and appendChild to root |

**Stays in Java:** DraggableTreePanel.java:319-345 draggable-tree--pannable and draggable-tree--panning: state classes; TreeKeyboard.java:381,384 tabindex 0 and -1: the roving tab stop; TreeKeyboard.java:468-469 the live region's text: built from what was moved.

## `MenuManager`

plugin-or-infrastructure, smart class; complexity 2, effort S; used by 0.

**Since the merge.** Renamed in the merge from the old menu singleton (formerly MenuListPresenter-style manager) to MenuManager; it now names menus for screen readers (setName/setLabelledBy via MenuOpener) and sets aria-expanded on the opener.

No plan yet (a second unit of the same name, or new with the merge): plan it as it is converted.

## `EditorPresenter`

widget, smart widget; complexity 4, effort L; used by 31.

**Since the merge.** copy_and_paste landed: EditorViewImpl now handles the DOM contextmenu event (Shift+right-click or the browser preference leaves it to the browser), finds a CodeEditorText edit target and fires ContextMenuEvent with it; EditorMenuPresenter.show takes the edit target and builds an 'Editor' menu of edit items plus Format and an 'Editor Options' sub menu (restoring the selection on hide); EditorPresenter passes the target through. More context-menu logic to split between component and controller.

- **Component:** `stroom.editor.client.component.CodeEditor`
- **Controller:** `stroom.editor.client.component.CodeEditorController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/editor/client/view/EditorViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditor.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/EditorView.java`; `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/BaseEditorView.java`; `stroom-core-client-widget/src/main/java/stroom/editor/client/view/EditorViewImpl.java (becomes CodeEditor)`; stroom-core-client-widget/src/main/java/stroom/editor/client/view/AbstractEditorViewImpl.java (merged: its option wiring into CodeEditorOptions, its Editor and resize panel into CodeEditor and SingleLineCodeEditor); `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/AbstractEditorPresenter.java (merged into AceEditorController)`; stroom-core-client-widget/src/main/java/stroom/editor/client/view/EditorMenuPresenter.java (its menu building moves into CodeEditorController); stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:168 bind(EditorMenuPresenter.class).in(Singleton.class) and import :20; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:97-100 bindPresenterWidget(EditorPresenter, EditorView, EditorViewImpl) and imports :68,72; stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:129-132 bindPresenterWidget(EditorPresenter, EditorView, EditorViewImpl) and imports :64,66; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:116 bindPresenterWidget(EditorPresenter, EditorView, EditorViewImpl) and imports :25,29; Users of EditorView and EditorMenuPresenter outside the area move in the same commit: ColumnFunctionEditorPresenter.java:206 and ColumnFunctionEditorViewImpl.java:54, ColumnFilterPresenter.java:93 and ColumnFilterViewImpl.java:48 (setEditor(EditorView) becomes setEditor(CodeEditor)), and TextPresenter.java:165 (getContextMenu() returns EditorMenuPresenter)
- **Names:** The suggested 'Editor' clashes with stroom.editor.client.view.Editor, the Ace wrapper (wave 2), which this component declares, so the component is CodeEditor (free; the migration order already calls it 'a dumb CodeEditor', line 265). CodeEditorText (stroom.widget.contextmenu.client) is a different class and doesn't clash. The suggested package stroom.editor.client.presenter becomes stroom.editor.client.component, per rule 1. The controller is CodeEditorController. AbstractEditorPresenter has two subclasses, so its preference and completer half becomes AceEditorController. That name is free, and the controller attaches to an Editor, as DocSelectionController does to a DropDownBox. CodeEditorController and SingleLineCodeEditorController each own one. The 13 Option getters become a model, CodeEditorOptions (free). Action and Option stay as its parts. EditorPresenter has 31 callers, so it stays as a thin wrapper over CodeEditorController (rule 5). It becomes MyPresenterWidget<CodeEditor>, with CodeEditor implementing the View marker, so that its users' getView() keeps working and no view binding is needed.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/RightBar.java:187-189 | `sb.append("\" style=\"top:"); sb.append(top); sb.append("px;\">")` | dynamic | style="--code-editor__marker-top: Npx" on each marker, and top: var(--code-editor__marker-top) in the existing .code-editor__marker rule (stroom-app/src/main/resources/ui/css/editor.css:288) |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/RightBar.java:255-284 | `indicatorPopup.setPopupPositionAndShow(... setPopupPosition(x, y))` | dynamic | Stays as it is: GWT's PopupPanel places itself (left and top) from the pointer's position; it is popup positioning, not this unit's markup. Note it in ISSUES.md if the rule is read strictly |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/AbstractEditorViewImpl.java:58-59, EditorViewImpl.java:99-100 | component-declaration | <e:Editor ui:field="editor" styleName="code-editor__ace"/> inside the content panel, in place of new Editor(), setClassName and content.setWidget(editor) |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/EditorViewImpl.java:84-85,96, AbstractEditorViewImpl.java:87-106 | structure | content becomes a plain <g:FlowPanel ui:field="content" styleName="code-editor__text dock-max"> (template line 6), not @UiField(provided = true) of an anonymous SimplePanel, so it can hold both the Editor and the error panel. Its resize observer moves to CodeEditor's attach handler |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/EditorViewImpl.java:293-295,309-318 | structure | <g:ScrollPanel ui:field="errorPanel" styleName="code-editor__load-error-container max" visible="false"><g:HTML ui:field="errorText" styleName="code-editor__load-error"/></g:ScrollPanel> in the content panel, in place of the ScrollPanel, HTMLPanel and classes built on each setErrorText. setText and setErrorText (154, 159, 318) switch which one is visible rather than swapping the content panel's widget; the DOM handlers at 312-314 are added once |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/IndicatorPopup.java:30-36 | aria | The popup's content and its role are fixed. Either IndicatorPopup.ui.xml beside it (<g:HTML ui:field="content"/>), or keep the class and role in its constructor, since PopupPanel's own element can't be the root of a template. setStyleName("code-editor-indicator") and role="tooltip" don't change |
| stroom-core-client-widget/src/main/resources/stroom/editor/client/view/EditorViewImpl.ui.xml:7 | component-declaration | <e:RightBar ui:field="rightBar" styleName="code-editor__overview dock-min"/> is already declared. rightBar.setEditor(editor) (EditorViewImpl.java:106) stays in the component's constructor |

**Stays in Java:** AbstractEditorViewImpl.java:64-84,186-200 option names ('Syntax Highlighting', 'Line Numbers' and the rest) and EditorViewImpl.java:102 'Format': menu item text, which moves with the options into CodeEditorOptions; EditorMenuPresenter.java:36-38 menu name 'Editor' or 'Editor Options' and :73 'Editor Options' item: built for the menu the controller shows, chosen by where it opened; EditorViewImpl.java:296-306 the error's title and lines: data; EditorViewImpl.java:270 'code-editor__highlight': an Ace marker class handed to Ace; RightBar.java:98-215 the overview's summary and markers (innerHTML from the indicators): data; severity modifier classes are chosen per line; EditorViewImpl.java:425-429 setScrollMargin: an Ace setting, not CSS.

## `MenuPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

**Since the merge.** The view no longer uses MyCellTable: MenuViewImpl (480 lines removed) now wraps the new Menu widget, which does the ARIA menu roles, keys, focus and type-ahead; MenuItemCell moved with that widget. What is left to split is the popup, sub-menu ownership and timer.

- **Component:** `stroom.widget.menu.client.presenter.Menu`
- **Controller:** `stroom.widget.menu.client.presenter.MenuController`
- **Template:** `none` → `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.ui.xml`
- **Goes:** `MenuPresenter.java (its nested MenuView interface, MenuPresenter.java:351-388; the sub-menu logic becomes MenuController)`; `MenuUiHandlers.java`; MenuViewImpl.java (its Menu.Handler implementation and sub-menu Timer, MenuViewImpl.java:30-302, move into MenuController, which is given the Menu); `MenuItemCellUiHandler.java (unused, no references)`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:166 bindPresenterWidget(MenuPresenter, MenuView, MenuViewImpl) and imports :78-80; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:98 bindPresenterWidget(MenuPresenter, MenuView, MenuViewImpl) and imports :62-64; MenuManager.java:35,44,47,61: Provider<MenuPresenter> and appRoot.makeDialog become Provider<MenuController> and ControllerDialog.showOnce(appRoot, controller); MenuPresenter.java:113,134 do the same for sub menus (ControllerDialog.showOnce(this, sub))
- **Names:** The suggested Menu + MenuController: Menu already exists (stroom.widget.menu.client.presenter.Menu, the menu widget from master) and it is the dumb half, so it is the component as it is, not a new class. MenuController is free in all_simple_names.json and in this wave. It sits beside Menu in stroom.widget.menu.client.presenter rather than in a new stroom.widget.menu.client.component package, because rule 1 keeps a widget in its own package and puts the pair side by side; moving the whole menu package (about 25 classes) isn't needed. The 'declares MyCellTable' in the record is out of date: master deleted GWT's cell tables and Menu draws its own rows with MenuItemCell, so nothing is declared.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:95 | `viewport.getStyle().setProperty("overflow", "auto")` | static | add overflow: auto to the existing .menu__viewport rule (stroom-app/src/main/resources/ui/css/menu.css:41) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:96 | `viewport.getStyle().setProperty("minWidth", 50 + "px")` | static | min-width: 50px in .menu__viewport (menu.css:41) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:97 | `viewport.getStyle().setProperty("maxWidth", 400 + "px")` | static | max-width: 400px in .menu__viewport (menu.css:41) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:98 | `viewport.getStyle().setProperty("maxHeight", 500 + "px")` | static | max-height: 500px in .menu__viewport (menu.css:41) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:104 | `menuElement.getStyle().setProperty("minWidth", 50 + "px")` | static | new rule .menu { min-width: 50px; } in menu.css (there is no .menu rule today, only its elements and modifiers) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:262-265 | `getElement().getStyle().setProperty("width"/"minWidth"/"maxWidth", px)` | dynamic | setFixedWidth sets --menu__width on the viewport and adds the new modifier menu__viewport--fixed-width (menu.css): width, min-width and max-width: var(--menu__width). It keeps menu--fixed-width on the inner menu (Menu.java:266) for the text ellipsis (menu.css:142) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:92-94,99 | structure | The viewport div and its class: <div class="menu__viewport"> as the root of Menu.ui.xml (UiBinder<DivElement, Menu>, setElement(binder.createAndBindUi(this))) |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/Menu.java:101-105 | aria | <div ui:field="menuElement" class="menu" role="menu"/> inside the viewport, in place of createDivElement, setClassName, setAttribute(ROLE, "menu") and appendChild |
| stroom-core-client-widget/src/main/java/stroom/widget/menu/client/presenter/MenuViewImpl.java:34 | component-declaration | new Menu() goes: MenuController is given the Menu in its @Inject constructor and calls initWidget(menu), so ControllerDialog shows it |

**Stays in Java:** Menu.java:142 rows (menu__row with data-index), one per item: built from the items; Menu.java:187-192 menu__row--active and Menu.java:454-456 menu--keyboard: state classes; Menu.java:469-510 each row's tabindex, role (menuitem, menuitemcheckbox, separator, none), aria-checked, aria-disabled, aria-keyshortcuts, aria-haspopup and aria-expanded: they depend on the item; Menu.java:241 aria-label and :253 aria-labelledby: the name comes from the item or button that opened the menu (MenuPresenter.java:117, MenuManager.java:99); Menu.java:266 menu--fixed-width: set only when a width is asked for; MenuItemCell.java:144-185 and its SafeHtmlTemplates (195-202, 250-257, 293-300): a cell's markup stays as SafeHtmlTemplates (the design keeps them for grid cells and rows); MenuPresenter.java:48-52 HORIZONTAL_PADDING, VERTICAL_PADDING and the menu__text lookup: used to position the sub-menu popup, not styles.

## `PipelineTreePanel`

widget, tidy; complexity 3, effort M; used by 2.

- **Component:** `stroom.pipeline.structure.client.view.PipelineTreePanel`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreePanel.ui.xml`
- **Goes:** setPipelineModel making new panels on each call (PipelineTreePanel.java:120-131,167-180 clear): the item panels are declared once and emptied
- **Names:** A dumb widget, so it keeps its name and package. PipelineElementBox, PipelineElementBoxFactory and the renderers keep theirs. PipelineElementBox can have its own template (PipelineElementBox.ui.xml beside it) for its fixed background and label. No clash in all_simple_names.json.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineElementRenderer.java:86-89 | `style.setLeft(bounds.getX(), PX); style.setTop(bounds.getY(), PX);…` | dynamic | --pipeline-element__left, --pipeline-element__top and --pipeline-element__width on the box, used by the existing .pipeline-element rule (stroom-app/src/main/resources/ui/css/pipeline-element.css:29): left: var(--pipeline-element__left, -100000px); top: var(--pipeline-element__top, -100000px); width: var(--pipeline-element__width, auto). The fallbacks are today's left and top |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineElementRenderer.java:172 | `pipelineElementBox.getElement().getStyle().clearWidth()` | dynamic | remove --pipeline-element__width before measuring (the rule falls back to auto) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreePanel.java:68-71 | structure | <g:FlowPanel ui:field="panel" styleName="pipeline-tree"> as the template's root |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreePanel.java:122-126 | aria | The items panel, class pipeline-tree__items, role="tree", aria-label="Pipeline structure", declared once. UiBinder sets a widget's attributes only through setters, so this needs a panel with setRole and setAriaLabel (see cssNotes) or an HTMLPanel element with add(widget, element) |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreePanel.java:127-131 | aria | The faded group, class pipeline-tree__faded, role="none", inside the items panel, declared once |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineElementBox.java:84-88 | structure | PipelineElementBox.ui.xml: <div class="pipeline-element"> with <div ui:field="label" class="pipeline-element__label"/>, in place of new FlowPanel, setStyleName(BLOCK), createDivElement and setClassName(LABEL_CLASS) |

**Stays in Java:** PipelineTreePanel.java:134-160 the LayeredCanvas and its class pipeline-tree__canvas: made only where canvas is supported (LayeredCanvas.createIfSupported; its constructor is private), so it can't be declared; PipelineElementBox.java:138,156-169,222,257,315,330-341 aria-selected, role treeitem with aria-level, posinset, setsize and expanded, tabindex, the built aria-label, aria-disabled, data-flow attributes, label text and modifier classes (target, refused, detached): data and state; PipelineElementBox.java:91-130,270-295 type icon, refused icon and embedded icon with their titles: made only when the element has them; PipelineConnectorRenderer: canvas drawing.

## `SingleLineEditorPresenter`

widget, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.editor.client.component.SingleLineCodeEditor`
- **Controller:** `stroom.editor.client.component.SingleLineCodeEditorController`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/editor/client/view/SingleLineEditorViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/editor/client/component/SingleLineCodeEditor.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/SingleLineEditorPresenter.java`; `stroom-core-client-widget/src/main/java/stroom/editor/client/presenter/SingleLineEditorView.java`; `stroom-core-client-widget/src/main/java/stroom/editor/client/view/SingleLineEditorViewImpl.java (becomes SingleLineCodeEditor)`; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:101-104 bindPresenterWidget(SingleLineEditorPresenter, SingleLineEditorView, SingleLineEditorViewImpl) and imports :70-71,73; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:117-118 bindPresenterWidget(SingleLineEditorPresenter, ...) and imports :27-28,30; `Its only caller, SingleLineEditorStories, moves in the same commit`
- **Names:** The suggested SingleLineEditor is free, but 'Editor' is already stroom.editor.client.view.Editor, the Ace wrapper from wave 2, which this component declares. So it is named SingleLineCodeEditor, to pair with CodeEditor (unit 110). SingleLineCodeEditor and SingleLineCodeEditorController are both free in all_simple_names.json and in this wave. Its smart half today is AbstractEditorPresenter (theme, key bindings, live auto-completion preference, global keys, completion providers, destroy on dispose), which it shares with EditorPresenter. That base merges into AceEditorController (unit 110), which this controller owns and attaches to the component's Editor. Do this unit with or right after 110.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/AbstractEditorViewImpl.java:58-59, SingleLineEditorViewImpl.java:49,79-81 | component-declaration | <e:Editor ui:field="editor" styleName="code-editor__ace code-editor__ace--single-line" maxLines="1"/> inside the content panel, in place of new Editor(), setClassName with getAdditionalClassNames(), content.setWidget(editor) and editor.setMaxLines(1) (SingleLineEditorViewImpl.java:52) |
| stroom-core-client-widget/src/main/java/stroom/editor/client/view/SingleLineEditorViewImpl.java:40-46, AbstractEditorViewImpl.java:87-106 | structure | The content panel is a plain <g:SimplePanel ui:field="content" styleName="code-editor__text dock-max"> (template line 15) holding the Editor, rather than @UiField(provided = true) of an anonymous SimplePanel. Its resize observer moves to the component's attach handler |
| stroom-core-client-widget/src/main/resources/stroom/editor/client/view/SingleLineEditorViewImpl.ui.xml:4 | structure | The e: namespace, unused today, is the one the Editor declaration uses |

**Stays in Java:** SingleLineEditorViewImpl.java:53-59 setFirstLineNumber(1) and the options turned off (line wrap, active line, line numbers, indent guides, invisibles, hex): editor settings applied through the options model (CodeEditorOptions, unit 110), not markup; SingleLineEditorViewImpl.java:73-76 setText: value.

## `DocumentAssetTree`

widget, tidy; complexity 3, effort S; used by 6.

**Since the merge.** Renames only: DataGridWidget is DataGrid and DataGridWidgetSelectionEventManager is DataGridSelectionEventManager. Nothing material.

- **Component:** `stroom.document.asset.client.presenter.assets.DocumentAssetTree`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.ui.xml`
- **Goes:** The constructor argument (DocumentAssetTree.java:88): a no-arg constructor plus setAccessibleName, so templates can declare it. Callers: DocumentAssetChooserPresenter.java:46 and DocumentAssetPresenter.java:102; setOpenHandler (DocumentAssetTree.java:128) and addSelectionHandler (:199) gain a setOnSelect(Consumer<DocumentAssetTreeItem>) callback; HasSelectionHandlers stays until DocumentAssetPresenter moves (rule 5)
- **Names:** A dumb widget, so it keeps its name and package. No clash: DocumentAssetTree is in all_simple_names.json only as itself.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:113 | `panel.getElement().getStyle().setPosition(Style.Position.RELATIVE)` | static | new class explorer-tree-panel (position: relative) in stroom-app/src/main/resources/ui/css/explorer-tree.css, shared with ExplorerTree (unit 369) |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:114-115 | `panel.setWidth("100%"); panel.setHeight("100%")` | static | max (stroom-app/src/main/resources/ui/css/stroom.css:793), with explorer-tree-panel |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:307-308,324,338 | `SafeStylesUtils.fromTrustedString("padding-left:" + (EXPANDER_PADDING + depth * INDENT)…` | dynamic | style="--explorer-cell__depth: N" on the expander, and padding-left: calc(4px + var(--explorer-cell__depth, 0) * 17px) on .explorerCell-expander (explorer-tree.css:41). Shared with ExplorerCell, which uses the same 4 + depth * 17 |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:112-117 | structure | <g:FlowPanel styleName="max explorer-tree-panel"> as the template's root, in place of new FlowPanel, the inline styles and panel.add(grid) |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:93-98 | component-declaration | <dg:DataGrid ui:field="grid" profile="TREE" cellPadding="false" fitWidth="true" .../> in place of new DataGrid<>(this, MASSIVE_LIST_PAGE_SIZE) and its setters. This needs the wave 5 DataGrid to have a template constructor and a page size attribute; hasContextMenu(false) needs a setter to be an attribute |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:99-100 | css-class | addStyleNames="explorerTree documentAssetTree" on the DataGrid declaration |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/assets/DocumentAssetTree.java:101 | aria | The tree's name goes on the declaring template (accessibleName="Assets" on <a:DocumentAssetTree>), through setAccessibleName |

**Stays in Java:** DocumentAssetTree.java:102-110 the column and its ItemCell, 111 setTreeRows, setSelectionModel: grid configuration with code (cells, callbacks); DocumentAssetTree.java:305-334 ItemCell's SafeHtmlTemplates and its classes (explorerCell-*, the arrow's class): a cell's markup, per row; DocumentAssetTree.java:285 EXPANDER_CLASS_NAME: used to find the expander in events.

## `AbstractExplorerTree`

widget, smart widget; complexity 4, effort L; used by 3.

**Since the merge.** DataGridWidget is DataGrid and its selection event manager renamed. TickBoxSelectionModel no longer implements HasSelection (deleted with GWT's cell tables) and lost its stateChanges/hasSelectionChanged redraw hook, so it is now a plain AbstractSelectionModel; slightly simpler to move.

- **Component:** `stroom.explorer.client.component.ExplorerTree`
- **Controller:** `stroom.explorer.client.component.ExplorerTreeController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTree.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java (merged into ExplorerTree and ExplorerTreeController); stroom-core-client/src/main/java/stroom/explorer/client/presenter/ExplorerTickBoxTree.java (merged: ExplorerTree gets setTickBoxes(true), and its tick toggling, :59-98, moves in); stroom-core-client/src/main/java/stroom/explorer/client/presenter/ExplorerTree.java (moves to stroom.explorer.client.component and takes the merged code); `The anonymous ExplorerTreeModel subclass at AbstractExplorerTree.java:121-140 (onDataChanged override) becomes a setOnData callback`; No GIN bindings: the trees are made with new by EntityTreePresenter, NavigationPresenter, ExplorerPopupPresenter, EntityCheckTreePresenter, ExplorerFixture and ExplorerTreeStories, which move in the same commit
- **Names:** The suggested AbstractExplorerTree + AbstractExplorerTreeController keeps an Abstract base, which rule 1 says to merge. AbstractExplorerTree has two subclasses: ExplorerTree (only a selection model, 43 lines) and ExplorerTickBoxTree (tick toggling, 98 lines). All three merge into one dumb ExplorerTree with a tick-box mode. That name is the unit's own class, and it moves to stroom.explorer.client.component, where its pair goes. The smart half (RestFactory and TaskMonitorFactory at AbstractExplorerTree.java:96-99, the model, menus) goes to ExplorerTreeController, which is free. It holds the merged ExplorerTreeModel (unit 370), so do 369 and 370 together. ExplorerCell and TickBoxSelectionModel keep their names and packages: one is a cell, the other a model.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:161 | `flowPanel.getElement().getStyle().setPosition(Style.Position.RELATIVE)` | static | new class explorer-tree-panel (position: relative) in stroom-app/src/main/resources/ui/css/explorer-tree.css, shared with DocumentAssetTree |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:162-163 | `flowPanel.setWidth("100%"); flowPanel.setHeight("100%")` | static | max (stroom-app/src/main/resources/ui/css/stroom.css:793) |
| stroom-core-client/src/main/java/stroom/explorer/client/view/ExplorerCell.java:99-101,113-116,219-220 | `SafeStylesUtils.fromTrustedString("padding-left:" + (4 + depth * 17) + "px;") in <div…` | dynamic | style="--explorer-cell__depth: N" (SafeStylesUtils.fromTrustedNameAndValue), and padding-left: calc(4px + var(--explorer-cell__depth, 0) * 17px) on .explorerCell-expander (explorer-tree.css:41) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:160-164,171 | structure | <g:HTMLPanel styleName="max explorer-tree-panel"> as the root, in place of new FlowPanel, its styles, add(tree) and initWidget |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:108-113 | component-declaration | <dg:DataGrid ui:field="tree" profile="TREE" cellPadding="false" fitWidth="true" addStyleNames="explorerTree"/> in place of new DataGrid<>(this, MASSIVE_LIST_PAGE_SIZE) and its setters. This needs the wave 5 DataGrid's template constructor; hasContextMenu(false) needs a setter |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:114 | css-class | explorerTree on the DataGrid declaration |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:166-170 | aria | <div ui:field="liveRegion" class="sr-only" role="status" aria-live="polite"/> in the HTMLPanel, in place of createDivElement, setClassName, setAttribute and appendChild |
| stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractExplorerTree.java:215-217 | aria | The tree's name goes on each declaring template, e.g. accessibleName="Documents" on <c:ExplorerTree>, through setAccessibleName |

**Stays in Java:** AbstractExplorerTree.java:115-120,143-157 the column with ExplorerCell, setTreeRows, setRowDescription, setRowChecked and the selection event manager: grid configuration with code; ExplorerCell.java:69-170 the cell's classes, tick box picture (TickBoxCell.DefaultAppearance, the TickBoxCell 'declares' entry, drawn in the cell rather than declared), alert icon, 'Descendants have issues' and filter match classes: per-row cell markup in SafeHtmlTemplates; AbstractExplorerTree.java:571 expander class lookup in events; The live region's text (why a key did nothing): built from the item.

## `ChooserPresenter`

widget, smart widget; complexity 2, effort M; used by 5.

- **Component:** `stroom.annotation.client.component.Chooser`
- **Controller:** `stroom.annotation.client.component.ChooserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/ChooserViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/Chooser.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/ChooserPresenter.java (and its ChooserView interface, :200-207)`; `stroom-core-client/src/main/java/stroom/annotation/client/ChooserViewImpl.java (becomes Chooser)`; `stroom-core-client/src/main/java/stroom/annotation/client/ChooserUiHandlers.java (becomes setOnFilterChange(Consumer<String>))`; stroom-core-client/src/main/java/stroom/annotation/client/gin/AnnotationModule.java:90-91 bindSharedView(ChooserView, ChooserViewImpl), bind(ChooserPresenter) and imports :44-46; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/AnnotationScreenModule.java:93-95 and imports :37-39; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/AnnotationScreenModule.java:93-95 and imports :37-39; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/IndexScreenModule.java:85-86 bindSharedView(ChooserView, ChooserViewImpl) and imports :25-26; `Callers AnnotationEditPresenter and ChangeStatusPresenter, and TestPresenterCoverage, move in the same commit`
- **Names:** Chooser and ChooserController are free in all_simple_names.json and in this wave. Chooser is the one component for both choosers (MultiChooser, unit 599, shares its template today through ChooserView). It holds the QuickFilter and the DataGrid, and each controller adds its columns to the declared grid. ChooserPresenter<T> is generic. The component can be Chooser<T> (setValues, setDisplayValueFunction, setOnChoose) or stay untyped with the grid typed by its controller; the generic dumb component is simpler for both.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/annotation/client/ChooserPresenter.java:94,98,101 | `builder.appendHtmlConstant("<div style=\"padding: 5px; min-width: 200px\" ...>")` | static | new class chooser__item (padding: 5px; min-width: 200px) in stroom-app/src/main/resources/ui/css/components.css, beside .chooserViewImpl-bottom (:1009); the cell writes <div class="chooser__item" title=...> |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/annotation/client/ChooserPresenter.java:69-72,84, ChooserViewImpl.java:59-63 | component-declaration | <dg:DataGrid ui:field="list" profile="LISTBOX" fitContent="true" addStyleNames="w-100"/> inside the bottom panel (template line 9), in place of new DataGrid<>(this, MASSIVE_LIST_PAGE_SIZE), its setters, view.setBottomWidget(list) and addClassName("w-100") (w-100 is stroom.css:843, not Bootstrap's). Needs the wave 5 DataGrid template constructor and page size attribute |
| stroom-core-client/src/main/java/stroom/annotation/client/ChooserViewImpl.java:51 | aria | accessibleName="Filter choices" on the QuickFilter in the template (line 7). Today the template says "Filter options" and Java overrides it with "Filter choices"; keep the one used now |
| stroom-core-client/src/main/resources/stroom/annotation/client/ChooserViewImpl.ui.xml:6 | structure | ui:field="top" is unused. Drop the field (keep the panel and its class) |

**Stays in Java:** ChooserPresenter.java:87-110 the text column and its cell (display value and tooltip from data); the column is added by the controller or by Chooser<T>; ChooserPresenter.java:113-120 the quick filter's help tooltip ('Choose Item Quick Filter' with the help URL from UiConfigCache): needs config, so the controller sets it; ChooserPresenter.java:126-128 setAccessibleName for the list ('Statuses' and so on): set by each caller, since one Chooser serves several lists; ChooserPresenter.java:172-186 onFilterChange and the data supplier: controller.

## `DocumentAssetChooserPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.document.asset.client.component.DocumentAssetChooser`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetChooserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetChooserViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetChooser.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetChooserPresenter.java (and its DocumentAssetChooserView interface, :107-112); `stroom-core-client/src/main/java/stroom/document/asset/client/view/DocumentAssetChooserViewImpl.java`; stroom-core-client/src/main/java/stroom/document/asset/client/gin/DocumentAssetModule.java:51-53 bindPresenterWidget(DocumentAssetChooserPresenter, DocumentAssetChooserView, DocumentAssetChooserViewImpl) and imports :22-23,31; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:145-147 the same binding and imports :19-21; stroom-core-client/src/main/java/stroom/document/asset/client/gin/DocumentAssetGinjector.java:30 Provider<DocumentAssetChooserPresenter> getter and import :19 (to Provider<DocumentAssetChooserController> only if something still uses it)
- **Names:** DocumentAssetChooser and DocumentAssetChooserController are free in all_simple_names.json and in this wave. The opener is DocumentAssetSelectionController (unit 271), which shows it with ControllerDialog.showOnce; that replaces makeDialog at DocumentAssetDropDownPresenter.java:64.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetChooserViewImpl.ui.xml:22, DocumentAssetChooserViewImpl.java:31-33, DocumentAssetChooserPresenter.java:46,49 | component-declaration | <t:DocumentAssetTree ui:field="tree" accessibleName="Assets" addStyleNames="max form-control-border form-control-background"/> in place of the SimplePanel slot, setTree, and new DocumentAssetTree("Assets"). The tree must come first (unit 266, no-arg constructor) |

**Stays in Java:** DocumentAssetChooserPresenter.java:68-71 OK_CANCEL_DIALOG, PopupSize.resizable(400, 450), caption 'Choose Asset', modal: the controller sets the dialog's chrome through the popup builder, as the design says (the component is the content only); DocumentAssetChooserPresenter.java:55,58,74,92 Enable and DisablePopupEvent for OK: state, done by the controller; DocumentAssetChooserPresenter.java:85-95 REST fetch: controller (owner(this) in place of taskMonitorFactory(this) alone).

## `DropDownViewImpl`

widget, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.widget.dropdowntree.client.DropDownBox`
- **Controller:** `none`
- **Template:** `none` → `none (DropDownBox.ui.xml is already beside DropDownBox.java, from wave 2; this unit adds no component)`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/DropDownView.java`; `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/DropDownViewImpl.java`; `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/DropDownUiHandlers.java`; stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:162 bindSharedView(DropDownView, DropDownViewImpl) and imports :74-75; stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:121 bindSharedView(DropDownView, DropDownViewImpl) and imports :83-84; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:120 bindSharedView(DropDownView, DropDownViewImpl); Stories move to DropDownBox: ViewStateStories.java:546, DropDownSelectorStories.java:55,70,98-108 (its DropDownUiHandlers lambda becomes box.setOnOpen), SelectorWidgets.java:90,108; the comment in drop-down.css:18 names DropDownViewImpl
- **Names:** The suggested DropDown + DropDownController isn't needed. DropDownViewImpl only passes calls through to DropDownBox (DropDownViewImpl.java:34-74), and DropDownBox is already the dumb component (wave 2, with setOnOpen). DropDown and DropDownController would be new names that hold nothing. The unit is a deletion. Its three users are UserRefSelectionBoxPresenter and DocumentAssetDropDownPresenter (this wave), and DocSelectionBoxPresenter (wave 8, but it is already a thin wrapper over DocSelectionController.attach(view.getBox())). Make DropDownBox implement stroom.widget.presenter.client.View, an IsWidget marker. The wrappers that stay can then be MyPresenterWidget<DropDownBox>: DocSelectionBoxPresenter.java:40,49,57 changes in one line to controller.attach(view). DropDownView can then go in this wave rather than waiting for wave 8.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/DropDownViewImpl.java:30 | component-declaration | new DropDownBox() goes: the screens that use a picker declare <d:DropDownBox ui:field="..."/> in their templates, and a selection controller attaches to it, as for DocSelectionController |

**Stays in Java:** Nothing: every method of DropDownViewImpl (setText, setProblem, setEnabled, setReadOnly, focus) is already on DropDownBox.

## `MultiChooserPresenter`

widget, smart widget; complexity 2, effort S; used by 2.

**Since the merge.** Mechanical rename: DataGridWidget and DataGridWidgetSelectionEventManager are now DataGrid and DataGridSelectionEventManager. Nothing else changed.

- **Component:** `stroom.annotation.client.component.Chooser`
- **Controller:** `stroom.annotation.client.component.MultiChooserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/ChooserViewImpl.ui.xml (shared through ChooserView)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/Chooser.ui.xml (shared with unit 598)`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/MultiChooserPresenter.java`; `stroom-core-client/src/main/java/stroom/annotation/client/gin/AnnotationModule.java:92 bind(MultiChooserPresenter) and import :57`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/AnnotationScreenModule.java:96 and import :50`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/AnnotationScreenModule.java:96 and import :50`; `Caller AnnotationEditPresenter moves in the same commit`
- **Names:** The suggested MultiChooser as a second component would duplicate Chooser's template, since both use the same view today. One Chooser component serves both. MultiChooserController is free and adds the tick column and the toggling. MultiChooser isn't used as a name. Split it in the same commit as unit 598.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/annotation/client/MultiChooserPresenter.java:69-71,79 | component-declaration | Uses the DataGrid declared in Chooser.ui.xml (profile LISTBOX, fitContent) in place of new DataGrid, its setters and setBottomWidget |

**Stays in Java:** MultiChooserPresenter.java:72 'multiChooser' class: the shared Chooser chooses it at runtime, e.g. Chooser.setMultiSelect(true) adds it, since the same template serves both. components.css:2048 .multiChooser .tick-box depends on it; MultiChooserPresenter.java:73-78 setRowChecked, the selection event manager and setChooser: grid behaviour; MultiChooserPresenter.java:82-99 the tick column (TickBoxCell.DefaultAppearance picture, the TickBoxCell 'declares' entry, drawn in a cell rather than declared) and the text column: cells; MultiChooserPresenter.java:102-109 quick filter help tooltip: needs UiConfigCache, so the controller sets it.

## `TraceOverviewWidget`

widget, tidy; complexity 5, effort L; used by 1.

**Since the merge.** The constructor no longer takes DefaultResources (only HasHandlers now), and Glass moved to stroom.widget.datagrid.client and is built with a CSS class name (new Glass("resizeGlass")) rather than a CssResource style. That removes one constructor obstacle; the HasHandlers hook and TaskMonitorFactory remain. No longer in flight.

- **Component:** `stroom.pathways.client.presenter.TraceOverviewWidget`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.ui.xml`
- **Goes:** The HasHandlers constructor argument (TraceOverviewWidget.java:161-162): a no-arg constructor, so TracesListTabPresenter's template can declare it. CopyTextUtil.onClick at :202 is given the widget itself, or the owner sets a copy callback; TaskMonitorFactory on the widget (:76, :1195) stays only for its own pager spinner; it is no longer given the owner's monitor (context comes from its owner)
- **Names:** A dumb widget, so it keeps its name and package. A TraceOverview exists (stroom.pathways.shared.TraceOverview, a shared DTO), so dropping 'Widget' from its name would clash. No other clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:615-616 | `Attribute.style("left: " + pct + "%;") on timeline markers` | dynamic | --timeline-marker__left on each span, used by .timeline-header span (stroom-app/src/main/resources/ui/css/pathways.css:132): left: var(--timeline-marker__left) |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:623 | `Attribute.style("left: 100%;") on the last marker` | static | new class timeline-header__end (left: 100%) in pathways.css |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:655 | `Attribute.style("height: " + OVERVIEW_STRIP_PX + "px;" + gridStyle) on timeline-bar` | static | height: 40px into the existing .timeline-bar (pathways.css:144; it has min-height: 40px); the grid part is dynamic, below |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:639,821-822,825-874 | `computeGridStyle(...) returns background-size and background-position for timeline-bar…` | dynamic | computeGridStyle returns --grid-lines__big-size, --grid-lines__small-size, --grid-lines__big-offset and --grid-lines__small-offset. .timeline-bar (pathways.css:144) and .grid-lines (pathways.css:285) use background-size: var(--grid-lines__big-size) 100%, var(--grid-lines__small-size) 100%; background-position: var(--grid-lines__big-offset) 0, var(--grid-lines__small-offset) 0 |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:721-725 | `Attribute.style("left..; width..; background-color: rgb(255, 140, 66); top..; height..;…` | static | new rule .timeline-span in pathways.css: position: absolute; background-color: rgb(255, 140, 66); opacity: 0.9; border-radius: 1px; min-width: 1px; plus the dynamic part below |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:722-724 | `left/width (%) and top/height (px) of each overview span` | dynamic | --timeline-span__left, --timeline-span__width, --timeline-span__top and --timeline-span__height, used by .timeline-span |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:737,741,745,751,757 | `left/width of leftDimOverlay, rightDimOverlay, timeRangeOverlay, startSlider, endSlider` | dynamic | --time-slider__start and --time-slider__end set once on .time-slider-container (pathways.css:158). #leftDimOverlay width: var(--time-slider__start); #rightDimOverlay left: var(--time-slider__end), width: calc(100% - var(--time-slider__end)); .time-range-overlay (:226) left: start, width: calc(end - start); #startSlider and #endSlider (:174-187) left: start and end |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:1025 | `Attribute.style("padding-left: " + (depth * 30) + "px;")` | dynamic | --operation__depth on the row, and padding-left: calc(var(--operation__depth, 0) * 30px) in .operation-content (pathways.css:484) |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:1077 | `Attribute.style("left: " + leftPct + "%; width: " + widthPct + "%;") on span-bar` | dynamic | --span-bar__left and --span-bar__width, used by .span-bar (pathways.css:556) |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:1080 | `Attribute.style("left: " + (leftPct + widthPct) + "%;") on duration` | dynamic | --span-bar__end, used by .duration (pathways.css:574): left: var(--span-bar__end) |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:1258 | `Attribute.style("width: " + spanInfoWidth + "px;")` | dynamic | --span-info__width, used by .span-info-panel (pathways.css:372): width: var(--span-info__width) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:164-165,178-179,186-187 | structure | <g:FlowPanel styleName="trace-overview-root"> as the root, holding the pager bar and the content, in place of new FlowPanel, addStyleName, the adds and initWidget |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:132,167-168 | structure | <g:FlowPanel ui:field="pagerBar" styleName="trace-pager-bar" visible="false"> |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:171-172 | component-declaration | <b:Toolbar ui:field="expandControls" name="Group levels" addStyleNames="trace-expand-controls"/> (Toolbar has a @UiConstructor taking name). GroupLevelButtons (:173-176) stays in Java, adding its buttons to the declared toolbar: it isn't a widget |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:130 | component-declaration | <p:Pager ui:field="pager"/> in the pager bar, in place of new Pager() |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/TraceOverviewWidget.java:78,185 | structure | <g:HTML ui:field="panel" styleName="trace-overview"/> in place of new HTML() and addStyleName |

**Stays in Java:** TraceOverviewWidget.java:573 and the HtmlBuilder sections (timeline header, controls, waterfall, span info): rebuilt from the spans on every refresh; TraceOverviewWidget.java:880 'Service & Operation' and :887-892 the operation list's region role and aria-label 'Service and operation': fixed text and ARIA, but inside the HTML rebuilt on each refresh. They move to the template only if the list's frame is split from its rows; note it in ISSUES.md; TraceOverviewWidget.java:196 Glass('resizeGlass'): an overlay shown while resizing, not layout; TraceOverviewWidget.java:168 pagerBar visibility: state (shown once there is more than a page).

## `DocumentAssetDropDownPresenter`

widget, smart widget; complexity 2, effort S; used by 3.

- **Component:** `stroom.widget.dropdowntree.client.DropDownBox`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetSelectionController`
- **Template:** `none` → `none (built in Java because the picker is a DropDownBox declared in its users' templates)`
- **Goes:** stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetDropDownPresenter.java (2 callers, FloorMapLayerStylePresenter and FloorMapObjectEditPresenter, plus VisualisationAssetsStories; they move in the same commit, so no wrapper is needed); stroom-core-client/src/main/java/stroom/document/asset/client/gin/DocumentAssetGinjector.java:32 Provider<DocumentAssetDropDownPresenter> getter and import :20; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/EditorsScreenGinjector.java:145 getDocumentAssetDropDownPresenter() and import :22
- **Names:** The suggested DocumentAssetDropDown + DocumentAssetDropDownController would make a component that is only a DropDownBox. As with DocSelectionController and UserRefSelectionController, the controller attaches to a declared DropDownBox, so it is named DocumentAssetSelectionController (free in all_simple_names.json and in this wave).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetDropDownPresenter.java:31,35 | component-declaration | The DropDownView and setUiHandlers(this) become <d:DropDownBox ui:field="assetBox"/> in the FloorMap layer style and object edit templates, with make(provider).attach(view.getAssetBox()), and box.setOnOpen(this::showPopup) in the controller |
| stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetDropDownPresenter.java:85-89 | css-class | Adding and removing the 'disabled' class on the view's element by hand goes. box.setEnabled(enabled) (DropDownBox.java:142) already sets the disabled class and aria-disabled |

**Stays in Java:** DocumentAssetDropDownPresenter.java:50 'None' and :52 the asset path: the shown value (see the emptyText note on unit 233); DocumentAssetDropDownPresenter.java:83-84 enabled: state.

## `UserRefSelectionBoxPresenter`

widget, smart widget; complexity 2, effort M; used by 11.

- **Component:** `stroom.widget.dropdowntree.client.DropDownBox`
- **Controller:** `stroom.security.client.component.UserRefSelectionController`
- **Template:** `none` → `none (built in Java because the picker is a DropDownBox declared in each user's template; this unit has no screen of its own)`
- **Goes:** stroom-core-client/src/main/java/stroom/security/client/presenter/UserRefSelectionBoxPresenter.java: its logic (UserRefSelectionBoxPresenter.java:37-114) moves into UserRefSelectionController.attach(DropDownBox). With 11 callers (BatchExecutionScheduleEditPresenter, ScheduledProcessEditPresenter, BatchProcessorFilterEditPresenter, ProcessorEditPresenter, ExpressionTreePanel, TermEditor, ExpressionTreeViewImpl, EditApiKeyViewImpl/Presenter, BatchDocumentPermissionsEditPresenter, and their tests) it stays as a thin wrapper, MyPresenterWidget<DropDownBox>, over the controller until they move (rule 5), as DocSelectionBoxPresenter does; `No GIN binding of its own (it uses the shared DropDownView binding that unit 6 deletes)`
- **Names:** The suggested UserRefSelectionBox + UserRefSelectionController would make a component that is only a DropDownBox. Follow the pilot instead: the dumb part is DropDownBox (wave 2), and the smart part is a controller that attaches to it, named like DocSelectionController. UserRefSelectionController is free in all_simple_names.json and in this wave. It goes in stroom.security.client.component. It owns UserRefPopupPresenter (wave 11) with own(...) and opens it; opening a dialog doesn't need that dialog converted first.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/security/client/presenter/UserRefSelectionBoxPresenter.java:45,49 | component-declaration | The DropDownView handed in, with setUiHandlers(this), becomes <d:DropDownBox ui:field="ownerBox"/> declared in each caller's template as it moves (e.g. EditApiKeyViewImpl.ui.xml, BatchExecutionScheduleEditViewImpl.ui.xml). The caller's controller does make(userRefSelectionControllerProvider).attach(view.getOwnerBox()), and the controller sets box.setOnOpen(this::showPopup) |

**Stays in Java:** UserRefSelectionBoxPresenter.java:92 'None' and :94 selection.toDisplayString(): the shown value depends on the selection. 'None' could instead become an emptyText attribute on DropDownBox, set in the template (shared with DocumentAssetDropDownPresenter.java:50 and DocSelectionController.NONE_DISPLAY_VALUE); UserRefSelectionBoxPresenter.java:102-110 setReadOnly and setEnabled: state, passed on to DropDownBox.

