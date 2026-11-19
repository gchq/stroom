# Wave 7: Composite families converted together

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 6 (25 October 2026):** the code editor is `CodeEditor` (`stroom.editor.client.component`; declare it, e.g. `<ed:CodeEditor ui:field="editor"/>`, and attach its controller, `make(codeEditorControllerProvider).attach(view.getEditor())`), with `CodeEditorOptions` and `CodeEditorController` (`EditorPresenter` is a thin wrapper over them; `Editor` is only the Ace wrapper inside it); the single line editor is `SingleLineCodeEditor` with `SingleLineCodeEditorController.attach(...)`; the explorer tree is one `ExplorerTree` (no-arg; `ExplorerTickBoxTree` is `setTickBoxes(true)`; `ExtendedExplorerTree` is gone, its overrides `setOnChoose` and `setOnInitialSelect`), filled by `ExplorerTreeController.attach(tree)` (was `ExplorerTreeModel`); `DraggableTreePanel` is concrete, with its callbacks (`setIsValidTarget`, `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), but takes no children in a template: its two panels go in with `setTreePanels(tree, subTree)`; `DocumentAssetTree` has a no-arg constructor and `setAccessibleName`; the pickers are a `DropDownBox` with a controller attached (`DocumentAssetSelectionController` in `stroom.document.asset.client.component`, `UserRefSelectionController` in `stroom.security.client.component`; `DropDownView` is gone); the annotation choosers are one-use popups, `ChooserController` and `MultiChooserController`, `show(opener, element, caption, onChoose)`, each opening tracked by an `OpenChooser` so that a second click closes it; the menu is `MenuController`. The plans below keep the old names. See `wave-06.md`.

Convert the regular stacks in one pass each: the 17 Plan B leaf settings widgets and their 8 type-settings presenters become dumb components (no controller); the floor map leaf panels are converted together with their ViewImpls; the five credential secret forms move behind a shared SecretForm; the pipeline and pathway trees; and the expression tree (TermEditor, then ExpressionTreePanel, then ExpressionTreePresenter, then both EditExpressionPresenters).

## Audited (26 October 2026)

Seven reviewers read the wave against this plan, "Done means" and AGENTS.md (Plan B, the secrets,
the floor map's dialogs and panels, its canvas, the pipeline and pathway trees, the expression
editor with all its owners, and the records). Fixed:

- **Two regressions on this branch.**
  - A press on a group's highlight eye selected the group too: `SingleSelectionEventManager`
    didn't skip a press a cell takes, as `DataGridSelectionEventManager` does. It now uses the
    same check (`isConsumedByCell`), takes the grid and a `SingleSelectionModel`, and moves the
    grid's keyboard row to the row pressed (story `FloorMapMap.HighlightDoesNotSelect`).
  - A term's document picker sat 1px low: its inline `margin: 0` had become a
    `termEditor-item--doc` class with no rule. `TermEditor.css` has it now.
- **A bug master has too.** View Matching Traces on a pathway's node after two forks matched the
  nearer fork's other branch: `PathwayTreeController.pathTo` kept each sequence leading on as it
  was, not as already cut down. JUnit `aNodeAfterTwoForksKeepsOnlyItsBranchAtEach`, which failed
  before, and a changelog entry.
- **Read-only.** `PipelineTree` applies read-only itself (`onReadOnlyChange`), as well as through
  `setAllowDragging`. `ExpressionEditorController` keeps its `setReadOnly`, which its document
  owners call, rather than watching `Contexts.READ_ONLY` as a reviewer asked: the context only
  ever narrows (true when nothing provides it), and an expression that is a search (the data
  browser's filter, the quick filter, a processor's filter, the retention impact's filter) has to
  stay editable inside a document the user can only view. Tried, it made every unowned editor
  read only; its javadoc says why now.
- **Rules 2 and 3.** The secret forms' text boxes, the group edit's filter and Set Scale's distance
  are `TextInput`s with their placeholders (and the filter's name) in their templates;
  `Button.setAriaLabel` is new, for the layer appearance's Upload; the Layers panel's Discover
  label is hidden from assistive technology by an `AriaPanel` in its template. The Plan B shared
  file store's path lost its `width="400px"`.
- **Rule 6.** The secret forms have `secret-form.css` (`secret-form__field`,
  `secret-form__text-area`), in place of the dialog's descendant rules in `components.css` (the
  expiry box's `wide-control` stays there until its dialogs convert); the shared file store
  settings have `shared-file-store-settings.css`; the pathway tree's rules moved from
  `pathways.css` to `pathway-tree.css`, BEM (`pathway-tree__node-name--selected` and the rest);
  the stepping bar's block is `pipeline-path-bar`, as its class is named, in
  `pipeline-path-bar.css`, with its custom properties and keyframes.
- **The canvas.** `FloorMapCanvasController` cancels its animation frame when disposed, and clears
  the canvas's callbacks, so a late image load or resize does nothing; the callbacks are
  `setOnRedraw` and `setOnSized`; the controller takes no bus; the gesture enum and
  `PendingCaption` are at the bottom of their classes, and the animation loop is a method.
- **Smaller things.** `AsyncSuggestOracle.cancel`, called when a term editor's controller goes, so
  a pending suggestion request isn't sent; `AbstractPipelineTree`'s `initSelection` and its
  subclasses' copies of the selection gone; the floor map's swatches announce the dialog they
  open; three unused `RestFactory` parameters gone; javadoc on `ExpressionTreeController`,
  `TermEditor.createSuggestBox` and `setSharedFileStoreLocked`; `ControllerDialog`'s docs cover
  `show`; the fields that hold an `ExpressionEditorController` are `expressionEditor`, and an
  `ExpressionTreeController` `expressionTree`; `AbstractPipelineTree`'s header is 2026; stale
  comments, a final local, a double blank line, the canvas's links to its deleted view, and
  `PathwayEditPresenter`'s commented-out line put back as it was.
- **Stories.** The credentials' Access Token, SSH key, their required fields, a refused key store
  upload, Replace Secret for a key store, and the focus on Replace Secret's first field; Plan B's
  temporal state and histogram read only, retention kept across a type change, and Range State in
  `OtherTypes`; Set Scale's OK, Cancel and a missing distance; the Layers panel's keyboard
  reorder; a read-only pipeline refusing Ctrl+X; a read-only pathway graph; Enter in a dashboard
  term running the query.
- **The records.** The Done section's claims that weren't so (the bug "on master too" meant master
  has it; the Layers panel's state classes; the measuring line's colours;
  `ExpressionUiHandlers`), the order's rows marked done, the later plans' names
  (`SecretForms`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDock`,
  `ExpressionEditorController`), `COMPONENTS.md`, `A11Y.md`, `ISSUES.md`, the coverage plan and the
  suite's scripts (whose expression term selector matched nothing).

Left, in ISSUES.md: the Layers panel's stale drag index after a drop (master too; the workbench
can't fire HTML drag and drop events for its story), the floor map following no read-only state
(master too), `FloorMapDock` handing out its tab bar, no story hovering on the canvas, and the
dashboard's unused `ExpressionUiHandlers`. `ExpressionEditor` keeps `EditExpressionViewImpl`'s
2016, as a file renamed (git sees it as new, as it changed so much).

All 1,649 stories passed with the leak check after the fixes (one, `Force3D` `ViewKeys`, on its second attempt); the 41 screenshots that differed are the noise named in Done, below, and the 17 new stories have baselines. The app and the standalone dashboard GWT compile.

## Done (26 October 2026)

All 52 units, in 13 commits, and wave 11's `FloorMapLayerStylePresenter` with its view, which the
layer style unit here needed. Each commit ran its stories with the leak check and the screenshot
diff; the last ran all of them (1,632 passed; the 41 screenshots that differed are the force graphs' (39), the data grid's spinner column and, once, the query editor's `AddToAnnotation`, unchanged in 4 runs alone; all recorded as noise in ISSUES.md), and the app and the standalone dashboard GWT
compile. One bug fixed on the way (master has it too), with a changelog entry and a story: a press on
a row in the floor map's Groups, Tracking, cluster and fact time lists selected nothing, so a
group could never be edited (`FloorMapMap.EditGroup`; see ISSUES.md). What was decided on the
way, where it differs from the plans below:

- **Plan B.**
  - The 17 leaves are components with their own `setOnChange`, `onReadOnlyChange` and templates
    beside their Java; `AbstractSettingsWidget`, `HttpStoreSettingsView` and the view interfaces
    are gone.
  - The Histogram and Metric key schemas declare their `TimeZoneBox` inside the
    `<g:SimplePanel addStyleNames="form-group">` it had: on the box itself, `form-group` made the
    pages 7px taller.
  - The 8 type settings editors are `PlanBTypeSettingsEditor`s (`read`, `write`, `setOnChange`,
    their common settings' getters), with templates, in `stroom.planb.client.component`.
    `PlanBSettingsPresenter` (wave 12's) makes them with `new` and keeps the common settings
    across a change of type; its read-only watch is `showReadOnly`, as `setReadOnly` is
    `DocPresenter`'s.
- **The secrets.** A sealed `SecretForm` (`getSecret`, `validate`, `focus`, `submit`), with the
  username and password, access token, SSH key and key store file forms as components and the key
  store's as `KeyStoreSecretForm` with a `KeyStoreSecretFormController`, which uploads in `submit`
  (its task monitor is the one it is given, else its own). `SecretForms` keeps one of each;
  `CredentialSettingsPresenter` and `ReplaceSecretPresenter` show `validate`'s message.
- **The floor map.**
  - `ControllerDialog.show(controller)` is new: it shows a controller made once (Timeline
    Settings, the layer appearance) again and again through one host, which the controller owns
    and which isn't disposed when hidden, so a second press of the settings button closes it, as
    the popup manager does.
  - Set Scale and the group edit are one-use (`showOnce`), with their parsing and the members'
    roster merge as static methods with JUnit tests.
  - `FloorMapDockController.addTab` takes any `IsWidget` (a `ControllerLayer` for one that isn't
    a `Layer`). `FloorMapLayers` is one component with no controller, its rows' state classes
    (`floormap-layer-row--dragging`, `--drop-above`, `--drop-below`) and its lock's, eye's and
    name's (`--locked`, `--off`, `--dim`), and its grips and swatches `LinkButton`s with their own
    classes in place of GWT's `Button`.
  - The canvas is `FloorMapCanvas` with `FloorMapCanvasController`, its gesture code unchanged:
    the component offers `add...Handler`, `getSurfaceElement`, `focus` and `setCrosshair` in
    place of `getFocusPanel()`, its readout, tooltip and scale bar are placed by custom
    properties, and the grid's colours and the measuring readout's halo are classes (the
    measuring line, its ticks and its text keep their literal blue).
    `FloorMapImageSizeCache` moved with it.
  - The new classes stay in `stroom-floormap.css`, as the notes below say; its older names
    (`floormap-group-edit-fields`, `floormap-layer-row`, `stroom-floormap-*`) aren't BEM yet.
  - `SingleSelectionEventManager` (beside `DataGrid`) makes a press, Space or Enter select a row
    of a grid given one of GWT's selection models, the selection following the focus; the four
    floor map grids use it. `DataGrid`'s one-argument `setSelectionModel` still adds nothing.
  - `FloorMapAria.labelledByInnerControl` names a composite's inner control from an id; the
    `Grid` helpers (`labelledByCell`, `labelledByCellInnerControl`, `layoutTable`) have no callers
    now, recorded in ISSUES.md for the removals.
- **The pipeline trees.** `PipelineTree` and `PipelinePathBar` share an abstract
  `AbstractPipelineTree` (the selection that refuses disabled elements, the model, the tree
  builder, the disabled elements and `getNames`), rather than the shared model object the plan
  suggests: both draw from the same state and their owners use them alike, so one base is
  simpler. `PipelineTree` has no controller (its owner moves an element and alerts, through
  `setOnMove`); `PipelinePathBarController` attaches to the bar (`setOnShowMenu`), as a
  `MenuRequest` can't carry the item to focus. `DraggableTreePanel.setTreePanels` hides its drag
  copy from assistive technology itself, for both trees. `ScreenHarness.showWidget` shows a
  component with no controller as `show` shows a screen.
- **The pathway tree** keeps its buttons as they were: New, Edit and Delete have no command, and
  the read-only state shows on them only once a node is chosen (ISSUES.md). Its path pruning is a
  static method with a JUnit test.
- **The expression tree.**
  - `TermEditor` keeps its reading and writing of a term, and is given a `TermEditor.Pickers`
    (its `TermEditorController`, which attaches the document and user pickers and the date boxes'
    controllers, and fetches the suggestions with an owner): the plan's controller doing the
    reading would have needed the editor's every box.
  - `AsyncSuggestOracle` fetches through a callback, so it makes no REST calls itself.
  - `ExpressionTreePanel`'s canvas goes into a slot in its template, as it is made only where
    the browser has one; its items are placed by `--expression-item__left` and `__top`.
  - `ExpressionTreeController` attaches a `TermEditorController` to each of the tree's two term
    editors; `init` takes no `RestFactory`, and `setOnChange` and `setOnSearch` replace
    `stroom.query.client.ExpressionUiHandlers`, which is gone.
  - The two `EditExpressionPresenter`s are one `ExpressionEditor`, framed as a control
    (`stroom-control`) unless its owner frames it (`setFramed(false)`: the dashboard's rule and
    selection handler, whose slots do; their `dock-max` did nothing in a `SimplePanel`). Its focus
    is Add Term (the dashboard's was Add Operator). The tree goes into a slot, as its controller
    is made with it. The old view's `onResize` cast a `FlowPanel` to `RequiresResize`, so nothing
    called it, and it is gone. `editExpressionViewImpl-expressionTree` is
    `expression-editor__tree`, in `expression-editor.css`.
  - The term editor's classes (`termEditor-*`, `TermEditor.css`) and the tree's
    (`expression-tree-view`) aren't BEM yet.

## CSS and template notes for the wave

- Reuse max (stroom.css:793, width and height 100%) for FloorMapLayersViewImpl.java:40 and PipelineTreeViewImpl.java:95-96.
- Plan B leaves and composites keep form, form-group, durationSetting and the state class section--disabled (components.css:2199); none of the 25 Plan B units has an inline style.
- Two visually hidden classes do the same job: sr-only (spinner.css:126, used by PipelinePathBar's live region) and stroom-floormap-visually-hidden (stroom-floormap.css:547, used by the floor map canvas and layers). Keep each where it is this wave; one shared class in a general file is worth a later sweep.
- New floor map classes all go in stroom-floormap.css: stroom-floormap-canvas-focus--crosshair; floormap-layer-row--dragging, --drop-above, --drop-below (box-shadow with var(--blue-500), which is the #2196f3 used today); floormap-layer-lock--locked; floormap-layer-eye--off and --dim; floormap-layer-name--off and --dim; stroom-floormap-measure-label; stroom-floormap-grid__line--minor, __line--major, __background (using the existing --floormap-grid__* tokens at :26-29).
- Custom properties follow the existing --block__part naming (--expression-tree__bracket, --floormap-grid__major-stroke): --expression-item__left/__top (expression-item.css:28), --pipeline-path__pulse-delay (pipeline-path.css:360, :497), --floormap-scale-bar__width, --floormap-gesture-readout__left/__top, --floormap-hover-tooltip__left/__top.
- Rename editExpressionViewImpl-expressionTree (components.css:288 and :743) to expression-editor__tree with the merged ExpressionEditor.
- UiBinder can't set role or aria-* on a widget. This wave uses raw elements in an HTMLPanel (ui:field Element, HTMLPanel.add(widget, element) where boxes go inside) for ExpressionTreePanel's tree, PipelinePathBar's toolbar and live region, and the floor map live regions and SVG container. For named controls, an ariaLabel setter like TickBox.setAriaLabel is missing on SelectionBox, BaseSelectionBox, DateTimeBox, SuggestBox and Button; until it exists AccessibleName.set stays in Java (TermEditor, FloorMapSetScale, FloorMapTimelineSettings).
- Placeholders: PasswordBox has placeholder and secretName for templates; g:TextBox and g:TextArea have no placeholder setter and live in the protected com/google/gwt directory, so their placeholders stay in Java (SshKey, KeyStoreFile, FloorMapGroupEdit, FloorMapSetScale).

## `AbstractPlanBSettingsPresenter`

composite, presenter; complexity 1, effort S; used by 9.

- **Component:** `stroom.planb.client.component.PlanBTypeSettingsEditor`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is an abstract base with no markup of its own)`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/AbstractPlanBSettingsPresenter.java`; `Its watch(Contexts.READ_ONLY, ...) (:39): PlanBSettings' controller calls setReadOnly on the editor it shows`
- **Names:** Suggested AbstractPlanBSettings clashes with the shared DTO stroom.planb.shared.AbstractPlanBSettings (its read/write type). With eight subclasses it can't merge into one, so it stays a thin abstract base: PlanBTypeSettingsEditor extends Component with read(AbstractPlanBSettings), write() and setOnChange. Free name.

**Stays in Java:** The carry-over of common values across a type change (instanceof checks in PlanBSettingsPresenter) is left alone until PlanBSettings converts in wave 12.

## `PipelinePathPresenter`

widget, presenter; complexity 5, effort L; used by 4.

- **Component:** `stroom.pipeline.stepping.client.component.PipelinePathBar`
- **Controller:** `stroom.pipeline.stepping.client.component.PipelinePathBarController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/PipelinePathBar.ui.xml`
- **Goes:** `PipelinePathView interface (stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/PipelinePathPresenter.java:62-69)`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/PipelinePathUiHandlers.java`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java (becomes PipelinePathBar)`; `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:122`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PipelineScreenModule.java:120-122`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/PipelineScreenModule.java:120-122`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PipelineScreenModule.java:120-122`
- **Names:** Suggested PipelinePath clashes with stroom.pipeline.stepping.client.view.PipelinePath, the path model in this same unit (kept as a helper with PipelinePathCollapse and SeverityChanges). Named PipelinePathBar, as the unit note and the agreed stepping chevron bar design call it. It no longer extends PipelineTreePresenter (presenter :35): the selection, disabled-element and severity state it shares with PipelineTree becomes a small shared model both are given, and the controller shows the branch menu (ShowMenuEvent, :46-54).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java:508 | `shape.getStyle().setProperty("animationDelay", -Math.round(pulseElapsed) + "ms")` | dynamic | Custom property --pipeline-path__pulse-delay set on the segment; .pipeline-path__segment--pulse .pipeline-path__shape (pipeline-path.css:360) adds animation-delay: var(--pipeline-path__pulse-delay, 0ms) |
| stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java:531 | `badge.getStyle().setProperty("animationDelay", -Math.round(pulseElapsed) + "ms")` | dynamic | Same --pipeline-path__pulse-delay on the badge; .pipeline-path__branch-severity--pulse (pipeline-path.css:497) adds animation-delay: var(--pipeline-path__pulse-delay, 0ms) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java:162-165 | aria | <div ui:field="bar" class="pipeline-path" role="toolbar" aria-label="Pipeline"/> in an HTMLPanel root; the click, key and context menu handlers (:167-169) go on the root widget, as the events bubble |
| stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java:179-182 | aria | <div ui:field="liveRegion" class="sr-only" role="status" aria-live="polite"/> |
| stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/PipelinePathViewImpl.java:184-186 | structure | Root <g:HTMLPanel> holding the bar and the live region |

**Stays in Java:** Segments, carets, badges and ellipsis built per step (:408-535, :665, :718-719): classes, role="button", aria-label from each element's description, aria-haspopup/aria-expanded, severity classes; Live region messages (:279-281); Resize observer and layout.

## `FloorMapCanvasViewImpl`

widget, tidy; complexity 5, effort XL; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapCanvas`
- **Controller:** `stroom.floormap.client.component.FloorMapCanvasController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapCanvasViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java (renamed FloorMapCanvas; no longer implements FloorMapCanvasView)
- **Names:** The view half of unit 146: renamed and moved to the component package rather than staying FloorMapCanvasViewImpl, since its presenter is split. FloorMapGrid and FloorMapImageSizeCache stay as helpers.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:883 | `scaleBarLine.setWidth(bar[1] + "px")` | dynamic | --floormap-scale-bar__width on the line; .stroom-floormap-scale-bar__line (stroom-floormap.css:823) gets width: var(--floormap-scale-bar__width) |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:916-917 | `gestureReadout...setLeft/setTop(px)` | dynamic | --floormap-gesture-readout__left / __top; .stroom-floormap-gesture-readout (stroom-floormap.css:607) gets left/top: var(..., 0) |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:971-972 | `hoverTooltip...setLeft/setTop(px)` | dynamic | --floormap-hover-tooltip__left / __top; .stroom-floormap-hover-tooltip (stroom-floormap.css:641) gets left/top: var(..., 0) |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:838 | `new Attribute("style", "stroke: var(--page__background-color)") on the measure label` | static | New class stroom-floormap-measure-label { stroke: var(--page__background-color) } in stroom-floormap.css, as the SVG text's class attribute |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGrid.java:211 | `new Attribute("style", "stroke: " + MINOR_STROKE) (also :220)` | static | New class stroom-floormap-grid__line--minor { stroke: var(--floormap-grid__minor-stroke) } in stroom-floormap.css; stroke-opacity and stroke-width stay attributes (data) |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGrid.java:233 | `new Attribute("style", "stroke: " + MAJOR_STROKE) (also :241)` | static | New class stroom-floormap-grid__line--major { stroke: var(--floormap-grid__major-stroke) }; a CSS rule resolves var() as the style form does, which was the reason for it (comment :73-83) |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGrid.java:260 | `new Attribute("style", "fill: " + BG_FILL)` | static | New class stroom-floormap-grid__background { fill: var(--floormap-grid__background-color) } |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:387, :418 | aria | svgContainer becomes <div ui:field="svgContainer" class="max" role="img" aria-label="Floor map"/> (filled with setInnerSafeHtml), the container (template line 25) an HTMLPanel |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapCanvasViewImpl.java:420 | aria | <div ui:field="statusRegion" class="stroom-floormap-visually-hidden" role="status" aria-live="polite" aria-atomic="true"/> in place of FloorMapAria.liveRegion on a Label |

**Stays in Java:** role="group" on the FocusPanel (:404): UiBinder can't set an attribute on a widget, and the FocusPanel is needed for focus, blur and key handlers; Summary aria-label kept off the focused element (:414, :434, :449) and aria-describedby id (:465); Empty status, readout, scale label and tooltip texts and lines (:484-505, :882, :899, :939-945); Visible and fault state classes; The drawn SVG, including aria-hidden on it (:771).

## `FloorMapLayersViewImpl`

composite, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapLayers`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayers.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayersViewImpl.java (merged into FloorMapLayers with its presenter, unit 158)
- **Names:** View half of unit 158.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayersViewImpl.java:40 | `scroll.setSize("100%", "100%")` | static | Existing class max (stroom.css:793) on the ScrollPanel |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayersViewImpl.java:50-53 | structure | Root <g:HTMLPanel styleName="max"> holding <g:ScrollPanel styleName="max"> (with the list, footer and empty labels of unit 158) and the status region |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayersViewImpl.java:46-48 | aria | <div ui:field="statusRegion" class="stroom-floormap-visually-hidden" role="status" aria-live="polite" aria-atomic="true"/> |

**Stays in Java:** announce() message text (:67-85).

## `FloorMapCanvasPresenter`

widget, presenter; complexity 5, effort XL; used by 6.

- **Component:** `stroom.floormap.client.component.FloorMapCanvas`
- **Controller:** `stroom.floormap.client.component.FloorMapCanvasController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapCanvasViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.ui.xml`
- **Goes:** `FloorMapCanvasView interface (stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapCanvasPresenter.java:3455)`; `FloorMapCanvasPresenter.java (becomes FloorMapCanvasController)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:126-128`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:94-96`
- **Names:** No clash for FloorMapCanvas or FloorMapCanvasController. The unit note suggests one widget; a pair is chosen so the 3,712 lines of gesture state move unchanged (the wave's risk note: rename callbacks, don't restructure). The controller has no REST or bus, so folding it into the widget stays open. The component is the ViewImpl of unit 723.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapCanvasPresenter.java:2772 | `getFocusPanel().getElement().getStyle().setProperty("cursor", "crosshair") (also :2795;…` | static | State class stroom-floormap-canvas-focus--crosshair { cursor: crosshair } in stroom-floormap.css beside .stroom-floormap-canvas-focus (:534), toggled by FloorMapCanvas.setCrosshair(boolean) |

**Stays in Java:** All gesture state and input handling (mouse, wheel, key, double-click handlers :625-1292), becoming callbacks the component offers rather than getFocusPanel()/getMouseMoveHandlers() reach-ins.

## `FloorMapGroupEditPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapGroupEdit`
- **Controller:** `stroom.floormap.client.component.FloorMapGroupEditController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapGroupEdit.ui.xml`
- **Goes:** `FloorMapGroupEditView interface (stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapGroupEditPresenter.java:186-207)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java (becomes the component, unit 730)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:173-175`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:136-138`
- **Names:** No clash. MemberCandidate (presenter :144) is the component's input row type and moves beside it.

**Stays in Java:** Dialog caption "New Group" / "Group — <name>" (presenter :80-82), set by the controller when it shows the dialog; "Give the group a name." warning on OK (presenter :90-92): controller; candidates() roster merge (presenter :107-137).

## `FloorMapGroupEditViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapGroupEdit`
- **Controller:** `stroom.floormap.client.component.FloorMapGroupEditController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapGroupEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java (becomes FloorMapGroupEdit)`
- **Names:** View half of unit 154.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:84-85 | structure | Root <g:HTMLPanel styleName="floormap-group-edit"> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:87-94 | structure | <table class="floormap-group-edit-fields" role="presentation"> with cells "Name" and "Colour" and declared <g:TextBox ui:field="nameBox"/> and <colour:ColourBox ui:field="colourBox"/>, replacing the Grid and FloorMapAria.layoutTable |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:101-102 | text | <g:Label ui:field="membersLabel" styleName="floormap-group-edit-label" text="Members"/> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:105 | css-class | <g:TextBox ui:field="filterBox" styleName="floormap-group-edit-filter"/> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:113-115 | structure | <g:ScrollPanel styleName="floormap-group-edit-scroll"><g:FlowPanel ui:field="memberList" styleName="floormap-group-edit-members"/></g:ScrollPanel> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:169-171 | text | <g:Label ui:field="empty" styleName="floormap-group-edit-empty" text="No entities have been seen on this map yet — run the map's queries first." visible="false"/>, shown when there are no candidates |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:123 | css-class | <g:Label ui:field="summary" styleName="floormap-group-edit-summary"/> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupEditViewImpl.java:67-68 | component-declaration | ColourBox (its declares list) in the template rather than new ColourBox() |

**Stays in Java:** Filter placeholder and aria-label (:106, :110): g:TextBox has neither setter; aria-labelledby links with generated ids (:98-99, :118-120); Member rows: TickBox per candidate with label and id title, detail labels and the absent state (:177-211); Summary text from the counts (:250-252).

## `GeneralSettingsWidget`

widget, tidy; complexity 2, effort S; used by 10.

- **Component:** `stroom.planb.client.view.GeneralSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/GeneralSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/GeneralSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/GeneralSettingsView.java`; stroom-core-client/src/main/java/stroom/planb/client/view/HttpStoreSettingsView.java (its synchronise merge and overwrite setters fold into the widget's own); `Injected Binder constructor (:52-53)`
- **Names:** Keeps its name and package.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/resources/stroom/planb/client/view/GeneralSettingsWidget.ui.xml:6 | css-class | Drop max from the root (keep form): every user puts it in a SettingsGroup, and TraceSettingsViewImpl.java:75 removes it by hand today; check the screenshots |

**Stays in Java:** Default max store size and IEC formatting (:79, :84-95); setMaxStoreSizeHelpText and setWriteOptionsVisible as setters a template can call (used as attributes by TraceSettingsEditor); Values.

## `SnapshotSettingsWidget`

widget, tidy; complexity 1, effort S; used by 7.

- **Component:** `stroom.planb.client.view.SnapshotSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/SnapshotSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/SnapshotSettingsWidget.ui.xml`
- **Goes:** `Injected Binder constructor (:45-46); it has no view interface`
- **Names:** Keeps its name.

**Stays in Java:** Values.

## `UsernamePasswordSecretPresenter`

composite, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.credentials.client.component.UsernamePasswordSecretForm`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/UsernamePasswordSecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/UsernamePasswordSecretForm.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/credentials/client/presenter/UsernamePasswordSecretPresenter.java (with its UsernamePasswordSecretView, :68-77); stroom-core-client/src/main/java/stroom/credentials/client/view/UsernamePasswordSecretViewImpl.java (ViewWithUiHandlers<DirtyUiHandlers>, handlers never used); `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:87-89`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/CredentialsScreenModule.java:74-76`; SecretPresenter permits entry (SecretPresenter.java:30); SecretPresenter becomes the SecretForm interface, SecretPresenters becomes SecretForms
- **Names:** Suggested UsernamePasswordSecret clashes with the shared DTO stroom.credentials.shared.UsernamePasswordSecret, which this form builds (UsernamePasswordSecretPresenter.java:47). Named UsernamePasswordSecretForm, implementing the wave's shared SecretForm (getSecret, validate, focus); SecretForm and SecretForms are free names. No controller: validate() returns the message and the owner (ReplaceSecret / CredentialSettings) shows the alert that AlertEvent.fireError at :53 shows today.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/credentials/client/view/UsernamePasswordSecretViewImpl.java:48 | text | placeholder="Enter Password" on <form:PasswordBox ui:field="password"> (template line 17) |

**Stays in Java:** Username and password values (getters and setters, ViewImpl :56-74); Validation messages, returned by validate() for the owner's alert (presenter :59-66).

## `AccessTokenSecretPresenter`

composite, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.credentials.client.component.AccessTokenSecretForm`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/AccessTokenSecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/AccessTokenSecretForm.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/credentials/client/presenter/AccessTokenSecretPresenter.java (with AccessTokenSecretView, :70-75)`; `stroom-core-client/src/main/java/stroom/credentials/client/view/AccessTokenSecretViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:75-77`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/CredentialsScreenModule.java:65-67`
- **Names:** Suggested AccessTokenSecret clashes with the shared DTO stroom.credentials.shared.AccessTokenSecret (built at presenter :47); named AccessTokenSecretForm, implements SecretForm. No controller: the owner shows the validation alert (presenter :53).

**Stays in Java:** Access token value; Validation message "Access token must not be empty" returned to the owner (presenter :61).

## `SshKeySecretPresenter`

composite, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.credentials.client.component.SshKeySecretForm`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/SshKeySecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/SshKeySecretForm.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/credentials/client/presenter/SshKeySecretPresenter.java (with SshKeySecretView, :70-87)`; `stroom-core-client/src/main/java/stroom/credentials/client/view/SshKeySecretViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:78-80`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/CredentialsScreenModule.java:68-70`
- **Names:** Suggested SshKeySecret clashes with the shared DTO stroom.credentials.shared.SshKeySecret (presenter :47); named SshKeySecretForm, implements SecretForm. No controller (alert at :57 goes to the owner).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/credentials/client/view/SshKeySecretViewImpl.java:53 | text | placeholder="Enter Pass Phrase" on <form:PasswordBox ui:field="passPhrase"> (template line 14) |
| stroom-core-client/src/main/java/stroom/credentials/client/view/SshKeySecretViewImpl.java:54 | aria | secretName="pass phrase" on the same PasswordBox (names its show/hide control) |

**Stays in Java:** Multi-line example placeholders on the two g:TextAreas (ViewImpl :55-60, :62-67): GWT's TextArea has no placeholder setter and com/google/gwt/user/client/ui/TextArea.java is in the protected directory; verifyHosts.setValue(true) default (ViewImpl :61), a value; Field values and the validation message (presenter :65).

## `FloorMapDockPresenter`

composite, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapDock`
- **Controller:** `stroom.floormap.client.component.FloorMapDockController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapDockViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapDock.ui.xml`
- **Goes:** `FloorMapDockView interface (FloorMapDockPresenter.java, bottom)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapDockViewImpl.java (becomes FloorMapDock, unit 725)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:157-159`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:112-114`
- **Names:** No clash. The note says TabbedComponent could replace it: that class is now stroom.entity.client.component.TabbedController and is document-shaped (read(DocRef, D), toolbar row), so it is not a drop-in; a small pair stays. The controller keeps the tab-to-content map and relays the tab bar's ShowMenu event (presenter :60), an app-wide request.

**Stays in Java:** Tab labels and contents passed to addTab (data); Selected tab.

## `FloorMapLayersPresenter`

list, presenter; complexity 3, effort M; used by 4.

**GWT button.** It still uses GWT's own `Button` (`com.google.gwt.user.client.ui.Button`), which nothing may use (agreed 24 October 2026): the drag grip and the colour swatches (`FloorMapLayersPresenter.java`, which strip GWT's `gwt-Button` class). Replace it in this wave with Stroom widgets (an unstyled `LinkButton`, or a component of their own) keeping their classes, and check the look against the screenshots.

- **Component:** `stroom.floormap.client.component.FloorMapLayers`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayers.ui.xml`
- **Goes:** `FloorMapLayersView interface (stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:662-673)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayersViewImpl.java (merged, unit 735)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:161-163`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:115-117`
- **Names:** No clash. No REST or bus: one dumb component (presenter and ViewImpl merged), its owners set the callbacks it already has (setChangeHandler, setStyleEditor, setDiscoverHandler become setOnChange, setOnEditStyle, setOnDiscover).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:487 | `row.getElement().getStyle().setOpacity(0.4) (cleared :511)` | static | State class floormap-layer-row--dragging { opacity: 0.4 } in stroom-floormap.css after .floormap-layer-row (:840) |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:495 | `setProperty("boxShadow", below ? "inset 0 -2px 0 0 #2196f3" : "inset 0 2px 0 0 #2196f3")…` | static | State classes floormap-layer-row--drop-above { box-shadow: inset 0 2px 0 0 var(--blue-500) } and floormap-layer-row--drop-below { box-shadow: inset 0 -2px 0 0 var(--blue-500) } in stroom-floormap.css (--blue-500 is #2196f3, material_design_colors.css:137); DROP_INDICATOR (:98) goes |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:620 | `lock.getElement().getStyle().setOpacity(locked ? 0.95 : 0.7)` | static | floormap-layer-lock { opacity: 0.7 } plus state floormap-layer-lock--locked { opacity: 0.95 } in stroom-floormap.css (extend the rule at :881-883) |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:632 | `eye.getElement().getStyle().setOpacity(0.7 / 0.7 / 1.0) (:632, :637, :642)` | static | State classes floormap-layer-eye--off and floormap-layer-eye--dim { opacity: 0.7 } in stroom-floormap.css; full visibility has neither |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:658 | `name.getElement().getStyle().setOpacity(0.65 / 0.75 / 1.0)` | static | State classes floormap-layer-name--off { opacity: 0.65 } and floormap-layer-name--dim { opacity: 0.75 } in stroom-floormap.css after .floormap-layer-name (:863) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:87 | structure | <g:FlowPanel ui:field="list" styleName="floormap-layers-list"/> (class from :115) inside the scroll panel of unit 735 |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:288-306 | structure | The Discover footer after the list: <g:FlowPanel ui:field="discoverFooter" styleName="floormap-layers-footer"> with <b:Toolbar name="Discover types" addStyleNames="floormap-layer-toolbar"><b:ToolbarButton ui:field="discover" svg="REFRESH" title="Discover types from the facts store" addStyleNames="floormap-layers-discover"/></b:Toolbar> and a label span class="floormap-layers-footer-label" aria-hidden="true" "Discover types"; shown with setVisible in editor mode |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:280-283 | text | Two <g:Label styleName="floormap-layer-empty"> with "No layers yet. Types found in the data appear here, or use Discover types." and "No layers configured. They are added on the Editor tab.", one made visible when the list is empty |
| stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapLayersPresenter.java:291 | component-declaration | Toolbar and ToolbarButton (its declares list) for the footer; the per-row toolbars stay built |

**Stays in Java:** Layer rows and provisional rows (buildRow :436-600, buildProvisionalRow :312-330): one per layer, with names and titles built from the type; Lock and eye icons and titles that follow state (applyLockState :610-622, applyEyeState :624-645); floormap-layer-row--provisional (:316) and the drag/drop handlers; announce() text.

## `PipelineTreePresenter`

widget, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.pipeline.structure.client.component.PipelineTree`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTree.ui.xml`
- **Goes:** `PipelineTreeView interface (stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/PipelineTreePresenter.java:209)`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreeViewImpl.java`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/PipelineTreeUiHandlers.java`; `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:121`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PipelineScreenModule.java:117-119`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/PipelineScreenModule.java:117-119`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PipelineScreenModule.java:117-119`
- **Names:** PipelineTree is free. Dumb, no controller: its one smart act, the alert on a failed move (presenter :88), becomes a setOnMove callback whose owner (PipelineStructure) moves the element and alerts. PipelinePathBar (unit 610) no longer subclasses it.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreeViewImpl.java:95-96 | `layoutPanel.setWidth("100%"); layoutPanel.setHeight("100%")` | static | Existing class max (stroom.css:793) on the declared DraggableTreePanel |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreeViewImpl.java:56-94 | component-declaration | <pv:DraggableTreePanel ui:field="layoutPanel" addStyleNames="max" putBackOnOwnParent="true"> holding <pv:PipelineTreePanel ui:field="treePanel"/> and <pv:PipelineTreePanel ui:field="subTreePanel"/>, with wave 6's callbacks instead of the anonymous subclass |
| stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineTreeViewImpl.java:60-61 | aria | aria-hidden and inert on the drag copy go into DraggableTreePanel itself (shared with ExpressionTree) |

**Stays in Java:** TreeKeyboard helper and its "element"/"elements" words (:98); Selection model with disabled elements, the pipeline model, severities.

## `PathwayTreePresenter`

widget, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.pathways.client.component.PathwayTree`
- **Controller:** `stroom.pathways.client.component.PathwayTreeController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/PathwayTreeViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayTree.ui.xml`
- **Goes:** `PathwayTreeView interface (stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayTreePresenter.java:380-388)`; `stroom-core-client/src/main/java/stroom/pathways/client/view/PathwayTreeViewImpl.java`; `stroom-core-client/src/main/java/stroom/pathways/client/gin/PathwaysModule.java:64-66`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PathwaysScreenModule.java:64-66`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/PathwaysScreenModule.java:57-59`
- **Names:** PathwayTree and PathwayTreeController are free. The drawing and selection are the component; ShowTracesEvent (presenter :140-144) and the read-only context (:162) go to the controller.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/pathways/client/view/PathwayTreeViewImpl.java:46-50 | component-declaration | <b:Toolbar ui:field="toolbar" name="Pathway actions"/> inside toolbarWidgets (template line 6) instead of made lazily |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayTreePresenter.java:80-86 | component-declaration | <b:ToolbarButton ui:field="newButton" svg="ADD" title="New Path"/>, "editButton" EDIT "Edit Selected Path" enabled="false", "removeButton" DELETE "Delete Selected Path" enabled="false", "viewTracesButton" EYE "View Matching Traces" |
| stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayTreePresenter.java:89-91 | structure | <g:HTML ui:field="tree" styleName="max"/> inside listContainer (template line 8), replacing setDataWidget |

**Stays in Java:** Tree HTML and SVG built from the pathway (read, :160-260), with its pathway-* classes; Selected node state class (:102-105); Button titles with " (read only)" and enabled states (enableButtons); Unused ui:field="pagerContainer" (template line 5) can lose its field.

## `TraceGeneralSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.TraceGeneralSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TraceGeneralSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/TraceGeneralSettingsWidget.ui.xml`
- **Goes:** `Injected Binder constructor (:41-42); it has no view interface`
- **Names:** Keeps its name.

**Stays in Java:** Default max spans (:61-70); Values.

## `FloorMapDockViewImpl`

composite, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapDock`
- **Controller:** `stroom.floormap.client.component.FloorMapDockController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapDockViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapDock.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapDockViewImpl.java (becomes FloorMapDock)`
- **Names:** View half of unit 148.

**Stays in Java:** Nothing beyond the tab bar and layer container getters.

## `SharedFileStoreSettingsWidget`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.planb.client.view.SharedFileStoreSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/SharedFileStoreSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/SharedFileStoreSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/SharedFileStoreSettingsView.java`; `Injected Binder constructor (:54-55)`
- **Names:** Keeps its name; setSharedFileStoreLocked (:88) becomes a plain setter its owner calls.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/SharedFileStoreSettingsWidget.java:58-59 | structure | min="1" max="999" on <v:ValueSpinner ui:field="shardCount"> (template line 16) |

**Stays in Java:** Default shard count (:60); Locked state and enabling (:88-98); Values.

## `RetentionSettingsWidget`

widget, tidy; complexity 2, effort S; used by 9.

- **Component:** `stroom.planb.client.view.RetentionSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/RetentionSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/RetentionSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/RetentionSettingsView.java`; `Injected Binder constructor (:60-61)`
- **Names:** Keeps its name.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/RetentionSettingsWidget.java:66-67 | structure | min="1" max="9999" on <valuespinner:ValueSpinner ui:field="retentionAge"> (template line 33) |
| stroom-core-client/src/main/java/stroom/planb/client/view/RetentionSettingsWidget.java:76-77 | structure | min="1" max="9999" on <valuespinner:ValueSpinner ui:field="retentionCheckInterval"> (template line 22) |

**Stays in Java:** Time unit items and defaults (:68-83); section--disabled state class (:155, :157) and enabling; setCheckIntervalVisible / setUseStateTimeVisible as setters (owners set them as template attributes); Values.

## `KeyStoreFileSecretPresenter`

composite, presenter; complexity 1, effort S; used by 5.

- **Component:** `stroom.credentials.client.component.KeyStoreFileSecretForm`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/KeyStoreFileSecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/KeyStoreFileSecretForm.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/credentials/client/presenter/KeyStoreFileSecretPresenter.java (with KeyStoreFileSecretView, :89-108); `stroom-core-client/src/main/java/stroom/credentials/client/view/KeyStoreFileSecretViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:84-86`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/CredentialsScreenModule.java:80-82`
- **Names:** Suggested KeyStoreFileSecret clashes with the shared DTO stroom.credentials.shared.KeyStoreFileSecret (presenter :56); named KeyStoreFileSecretForm, implements SecretForm, with setType/setPath on the form so callers stop reaching into the view. No controller (alert at :68 goes to the owner).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/credentials/client/view/KeyStoreFileSecretViewImpl.java:54 | text | placeholder="Enter Password" on <form:PasswordBox ui:field="password"> (template line 23) |

**Stays in Java:** Key store type items from KeyStoreType.values() and the PKCS12 default (ViewImpl :50-51); Path placeholder "/etc/stroom/certs/client.p12" (ViewImpl :53): g:TextBox has no placeholder setter (protected com/ directory); Values and validation messages (presenter :74-82).

## `KeyStoreSecretPresenter`

composite, presenter; complexity 2, effort S; used by 5.

- **Component:** `stroom.credentials.client.component.KeyStoreSecretForm`
- **Controller:** `stroom.credentials.client.component.KeyStoreSecretFormController`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/KeyStoreSecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/KeyStoreSecretForm.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/credentials/client/presenter/KeyStoreSecretPresenter.java (with KeyStoreSecretView, :100-111, incl. getFileUpload()); `stroom-core-client/src/main/java/stroom/credentials/client/view/KeyStoreSecretViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:81-83`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/CredentialsScreenModule.java:71-73`
- **Names:** Suggested KeyStoreSecret clashes with the shared DTO stroom.credentials.shared.KeyStoreSecret (presenter :71); named KeyStoreSecretForm, implements SecretForm. The only secret form with I/O, so it alone keeps a controller: KeyStoreSecretFormController sets the upload action URL, task monitor and success/failure (presenter :49-58) and runs submit on OK; the form exposes submit(onDone).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/credentials/client/view/KeyStoreSecretViewImpl.java:55 | text | placeholder="Enter Password" on <form:PasswordBox ui:field="password"> (template line 20) |

**Stays in Java:** Type items and PKCS12 default (ViewImpl :52-53); Upload action URL ImportUtil.getImportFileURL() (presenter :49), the uploaded resourceKey and the upload alerts: controller; Validation messages (presenter :89-98).

## `FloorMapSetScalePresenter`

dialog, presenter; complexity 1, effort S; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapSetScale`
- **Controller:** `stroom.floormap.client.component.FloorMapSetScaleController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapSetScale.ui.xml`
- **Goes:** `FloorMapSetScaleView interface (stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapSetScalePresenter.java:132-147)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSetScaleViewImpl.java (becomes the component, unit 739)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:177-179`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:121-123`
- **Names:** No clash.

**Stays in Java:** Caption "Set Scale" (presenter :86), set by the controller that shows the dialog; Distance parsing, calibration and the two warnings (presenter :89-111, :116-128): controller; Current reading text built from the map (presenter :80).

## `CondenseSettingsWidget`

widget, tidy; complexity 2, effort S; used by 4.

- **Component:** `stroom.planb.client.view.CondenseSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/CondenseSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/CondenseSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/CondenseSettingsView.java`; `Injected Binder constructor (CondenseSettingsWidget.java:47-48), for GWT.create and a no-arg constructor`; stroom-core-client/src/main/java/stroom/planb/client/view/AbstractSettingsWidget.java once all 17 leaves extend Component (setReadOnly/onReadOnlyChange, setOnChange)
- **Names:** A widget: keeps its name and package. AbstractSettingsWidget is not renamed: it goes, merged into the thin Component base, in this sweep of all 17 leaves.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/CondenseSettingsWidget.java:51-52 | structure | min="1" max="9999" on <valuespinner:ValueSpinner ui:field="condenseAge"> (template line 22) |

**Stays in Java:** Time unit items and defaults (:53-59); section--disabled state class (:98, components.css:2199) and enabling from its own tick box; Values read and written (:70-90).

## `StateValueSchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 4.

- **Component:** `stroom.planb.client.view.StateValueSchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/StateValueSchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/StateValueSchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/StateValueSchemaSettingsView.java`; `Injected Binder constructor (:42-43)`
- **Names:** Keeps its name.

**Stays in Java:** Enum items (:45-46); Hash length shown by value type; Values.

## `HistogramValueSchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.HistogramValueSchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/HistogramValueSchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/HistogramValueSchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/HistogramValueSchemaSettingsView.java`; `Injected Binder constructor (:39-40)`
- **Names:** Keeps its name.

**Stays in Java:** MaxValueSize items (:42); Values.

## `MetricValueSchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.MetricValueSchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/MetricValueSchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/MetricValueSchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/MetricValueSchemaSettingsView.java`; `Injected Binder constructor (:50-51)`
- **Names:** Keeps its name.

**Stays in Java:** MaxValueSize items (:53); Values.

## `PublishingSettingsWidget`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.planb.client.view.PublishingSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/PublishingSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/PublishingSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/PublishingSettingsView.java`; `Injected Binder constructor (:54-55)`
- **Names:** Keeps its name.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/PublishingSettingsWidget.java:63-64 | structure | min="1" max="9999" on <v:ValueSpinner ui:field="maxWaitForData"> (template line 14) |

**Stays in Java:** Granularity and time unit items and defaults (:58-72); Values.

## `RangeKeySchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.RangeKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/RangeKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/RangeKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/RangeKeySchemaSettingsView.java`; `Injected Binder constructor (:37-38)`
- **Names:** Keeps its name.

**Stays in Java:** RangeType items (:40); Values.

## `SessionKeySchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.SessionKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/SessionKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/SessionKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/SessionKeySchemaSettingsView.java`; `Injected Binder constructor (:46-47)`
- **Names:** Keeps its name.

**Stays in Java:** Enum items (:49-51); Hash length shown by key type; Values.

## `StateKeySchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.StateKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/StateKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/StateKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/StateKeySchemaSettingsView.java`; `Injected Binder constructor (:42-43)`
- **Names:** Keeps its name.

**Stays in Java:** Enum items (:45-46); Hash length shown by key type; Values.

## `TemporalRangeKeySchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.TemporalRangeKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TemporalRangeKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalRangeKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalRangeKeySchemaSettingsView.java`; `Injected Binder constructor (:42-43)`
- **Names:** Keeps its name.

**Stays in Java:** Enum items (:45-46); Values.

## `TemporalStateKeySchemaSettingsWidget`

widget, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.planb.client.view.TemporalStateKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TemporalStateKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalStateKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalStateKeySchemaSettingsView.java`; `Injected Binder constructor (:46-47)`
- **Names:** Keeps its name.

**Stays in Java:** Enum items (:49-51); Hash length shown by key type; Values.

## `FloorMapLayerStyleViewImpl`

composite, tidy; complexity 3, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapLayerStyle`
- **Controller:** `stroom.floormap.client.component.FloorMapLayerStyleController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayerStyle.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java (becomes FloorMapLayerStyle)`; FloorMapLayerStyleView and its GIN binding (FloorMapModule.java:165-167, editors/FloorMapScreenModule.java:118-120) go when FloorMapLayerStylePresenter converts to the controller
- **Names:** View half only: FloorMapLayerStyle and FloorMapLayerStyleController are free; the controller comes from FloorMapLayerStylePresenter.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java:103-123 | structure | <table class="floormap-layer-style-dialog" role="presentation"> with rows Graphic, Shape, Icon, Image, Colour, Preview (text cells) and their controls, replacing the Grid |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java:74-79 | component-declaration | <form:RadioGroup ui:field="modeGroup"/>, <i:SelectionBox ui:field="shapeBox"/>, <fv:FloorMapIconPicker ui:field="iconPicker"/>, <colour:ColourBox ui:field="colourBox"/>, <b:Button ui:field="uploadButton"/> rather than new ...() |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java:92-93 | text | text="Upload" title="Upload a new image into this document's assets" on the Button |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java:166-171 | structure | <g:FlowPanel styleName="floormap-layer-style-image"> holding the asset picker slot and the upload button |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java:174-185 | text | <g:FlowPanel styleName="floormap-layer-style-preview-row"> with <g:SimplePanel ui:field="previewPanel" styleName="floormap-layer-style-preview"/> (:101) and <g:Label styleName="floormap-layer-style-hint" text="Drawn at a fixed size on the map. Newly uploaded images appear after the document is saved."/> |

**Stays in Java:** Shape items from the Shape enum (:87-90); Radio items with their texts and titles (:158-160): RadioGroup takes items through addItem, not template children; aria-label on the upload Button (:140-141): Button has no ariaLabel setter (its title says the same); Row labelling with generated ids (:128-138); the asset picker slot (a presenter's view until it splits); Dimmed rows and enablement by mode (:193-210); preview HTML from the style (:289).

## `FloorMapSetScaleViewImpl`

dialog, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapSetScale`
- **Controller:** `stroom.floormap.client.component.FloorMapSetScaleController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapSetScale.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSetScaleViewImpl.java (becomes FloorMapSetScale)`
- **Names:** View half of unit 163.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSetScaleViewImpl.java:59-60 | structure | Root <g:HTMLPanel styleName="floormap-set-scale"> |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSetScaleViewImpl.java:78-85 | structure | <table class="floormap-set-scale-fields" role="presentation"> with "The line you measured is currently" (:81) / <g:Label ui:field="currentReading"/> and "It is really" (:83) / the distance row |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSetScaleViewImpl.java:69-76 | css-class | <g:FlowPanel styleName="floormap-set-scale-row"><g:TextBox ui:field="distanceBox" styleName="stroom-control floormap-set-scale-distance"/><i:SelectionBox ui:field="unitListBox"/></g:FlowPanel> |

**Stays in Java:** Metric unit items and their labels (:62-67); Placeholder "e.g. 2.4" (:71): g:TextBox has no setter; aria-labelledby with a generated id (:90) and the unit box's name "Unit of the distance you typed" (:93) until SelectionBox has an ariaLabel setter; Reading and distance values.

## `StateSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.StateSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/StateSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/StateSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/StateSettingsPresenter.java (with StateSettingsView)`; `stroom-core-client/src/main/java/stroom/planb/client/view/StateSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:65-67`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:69-71`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:69-71`
- **Names:** Suggested StateSettings clashes with the shared DTO stroom.planb.shared.StateSettings, which this class reads and writes (presenter read/write). Named StateSettingsEditor; extends PlanBTypeSettingsEditor (unit 684). No controller: PlanBSettings' controller pushes read-only and sets setOnChange.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/StateSettingsViewImpl.java:67-71 | component-declaration | Leaves nested in their SettingsGroups instead of injected and added: <sg:SettingsGroup label="General"><v:GeneralSettingsWidget ui:field="general"/></sg:SettingsGroup>, likewise SnapshotSettingsWidget, RetentionSettingsWidget, StateKeySchemaSettingsWidget, StateValueSchemaSettingsWidget (SettingsGroup is a HasWidgets) |

**Stays in Java:** read/write mapping to and from StateSettings.

## `TemporalStateSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.TemporalStateSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TemporalStateSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/TemporalStateSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/TemporalStateSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalStateSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:68-70`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:72-74`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:72-74`
- **Names:** Suggested TemporalStateSettings clashes with the shared DTO stroom.planb.shared.TemporalStateSettings; named TemporalStateSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/TemporalStateSettingsViewImpl.java:74-79 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, CondenseSettingsWidget, RetentionSettingsWidget, TemporalStateKeySchemaSettingsWidget and StateValueSchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `RangeStateSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.RangeStateSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/RangeStateSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/RangeStateSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/RangeStateSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/RangeStateSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:71-73`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:75-77`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:75-77`
- **Names:** Suggested RangeStateSettings clashes with the shared DTO stroom.planb.shared.RangeStateSettings; named RangeStateSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/RangeStateSettingsViewImpl.java:67-71 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, RetentionSettingsWidget, RangeKeySchemaSettingsWidget and StateValueSchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `TemporalRangeStateSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.TemporalRangeStateSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TemporalRangeStateSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/TemporalRangeStateSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/TemporalRangeStateSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/TemporalRangeStateSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:74-76`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:78-80`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:78-80`
- **Names:** Suggested TemporalRangeStateSettings clashes with the shared DTO stroom.planb.shared.TemporalRangeStateSettings; named TemporalRangeStateSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/TemporalRangeStateSettingsViewImpl.java:74-79 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, CondenseSettingsWidget, RetentionSettingsWidget, TemporalRangeKeySchemaSettingsWidget and StateValueSchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `SessionSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.SessionSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/SessionSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/SessionSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/SessionSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/SessionSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:77-79`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:81-83`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:81-83`
- **Names:** Suggested SessionSettings clashes with the shared DTO stroom.planb.shared.SessionSettings; named SessionSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/SessionSettingsViewImpl.java:67-71 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, CondenseSettingsWidget, RetentionSettingsWidget and SessionKeySchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `TraceSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.TraceSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/TraceSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/TraceSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/TraceSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/TraceSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:86-88`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:90-92`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:90-92`
- **Names:** Suggested TraceSettings clashes with the shared DTO stroom.planb.shared.TraceSettings; named TraceSettingsEditor (TracesSettingsPresenter in pathways is a different class).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/TraceSettingsViewImpl.java:72-78 | structure | Storage group: <sg:SettingsGroup label="Storage"><g:FlowPanel styleName="form"><v:GeneralSettingsWidget ui:field="general" .../><v:TraceGeneralSettingsWidget ui:field="traceGeneral"/></g:FlowPanel></sg:SettingsGroup>; removeStyleName("max") (:75) goes with max leaving GeneralSettingsWidget's root (unit 659) |
| stroom-core-client/src/main/java/stroom/planb/client/view/TraceSettingsViewImpl.java:82-85 | text | maxStoreSizeHelpText="The size limit for each file in the store. A trace store has one file per time bucket and shard, so the whole store can be much bigger than this." writeOptionsVisible="false" on the GeneralSettingsWidget |
| stroom-core-client/src/main/java/stroom/planb/client/view/TraceSettingsViewImpl.java:87-93 | component-declaration | <v:RetentionSettingsWidget ui:field="retention" useStateTimeVisible="false" checkIntervalVisible="true"/> in the Data Retention group |
| stroom-core-client/src/main/java/stroom/planb/client/view/TraceSettingsViewImpl.java:67-68 | component-declaration | SharedFileStoreSettingsWidget and PublishingSettingsWidget declared in their SettingsGroups |

**Stays in Java:** read/write mapping, including maxQueryTimeRange and compactionFrequency, which have no editor and must round-trip so a save keeps them.

## `HistogramKeySchemaSettingsWidget`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.planb.client.view.HistogramKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/HistogramKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/HistogramKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/HistogramKeySchemaSettingsView.java`; `Injected constructor with Binder and TimeZoneBox (:56-57)`
- **Names:** Keeps its name. Line for line the same as MetricKeySchemaSettingsWidget but for the shared DTO (HistogramKeySchema vs MetricKeySchema); they could share a base, but separate classes keep the DTO types simple.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/HistogramKeySchemaSettingsWidget.java:53, :63 | component-declaration | <qv:TimeZoneBox ui:field="timeZone" addStyleNames="form-group"/> replacing the SimplePanel (template line 22) and the injected TimeZoneBox; template-usable after wave 2 |

**Stays in Java:** Items from KeyType/HashLength/TemporalResolution ORDERED_LIST (:60-62); Hash length shown by key type; Values.

## `MetricKeySchemaSettingsWidget`

widget, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.planb.client.view.MetricKeySchemaSettingsWidget`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/MetricKeySchemaSettingsWidget.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/view/MetricKeySchemaSettingsWidget.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/MetricKeySchemaSettingsView.java`; `Injected constructor with Binder and TimeZoneBox (:56-57)`
- **Names:** Keeps its name; see unit 660 on the duplicate.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/MetricKeySchemaSettingsWidget.java:53, :63 | component-declaration | <qv:TimeZoneBox ui:field="timeZone" addStyleNames="form-group"/> replacing the SimplePanel (template line 22) |

**Stays in Java:** Enum items (:60-62); Values.

## `HistogramSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.HistogramSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/HistogramSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/HistogramSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/HistogramSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/HistogramSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:80-82`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:84-86`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:84-86`
- **Names:** Suggested HistogramSettings clashes with the shared DTO stroom.planb.shared.HistogramSettings; named HistogramSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/HistogramSettingsViewImpl.java:68-72 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, RetentionSettingsWidget, HistogramKeySchemaSettingsWidget and HistogramValueSchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `MetricSettingsPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.planb.client.component.MetricSettingsEditor`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/MetricSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/MetricSettingsEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/MetricSettingsPresenter.java (with its view interface)`; `stroom-core-client/src/main/java/stroom/planb/client/view/MetricSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:83-85`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:87-89`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:87-89`
- **Names:** Suggested MetricSettings clashes with the shared DTO stroom.planb.shared.MetricSettings; named MetricSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/planb/client/view/MetricSettingsViewImpl.java:68-72 | component-declaration | GeneralSettingsWidget, SnapshotSettingsWidget, RetentionSettingsWidget, MetricKeySchemaSettingsWidget and MetricValueSchemaSettingsWidget declared inside their SettingsGroups |

**Stays in Java:** read/write mapping.

## `FloorMapTimelineSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.floormap.client.component.FloorMapTimelineSettings`
- **Controller:** `stroom.floormap.client.component.FloorMapTimelineSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapTimelineSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimelineSettings.ui.xml`
- **Goes:** FloorMapTimelineSettingsView interface (stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapTimelineSettingsPresenter.java:132-153); `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimelineSettingsViewImpl.java (becomes the component, unit 742)`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:133-135`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FloorMapScreenModule.java:100-102`
- **Names:** No clash. The "raw Button" in the note is stroom.widget.button.client.Button (Stroom's text button, template line 43), so it stays a text button; it is not a lone ToolbarButton.

**Stays in Java:** Popup caption "Timeline Settings" and anchored position (presenter :50-59): controller; addStart/EndTimeChangeHandler pass-throughs become setOnStartChange/setOnEndChange callbacks.

## `TermEditor`

widget, smart widget; complexity 4, effort L; used by 2.

- **Component:** `stroom.query.client.TermEditor`
- **Controller:** `stroom.query.client.component.TermEditorController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/query/client/TermEditor.ui.xml`
- **Goes:** HasControllerNode and its ControllerNode (stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:63, :87, :187-190): a dumb widget owns nothing; `Provider<DocSelectionBoxPresenter>/Provider<UserRefSelectionBoxPresenter>/UiConfigCache constructor (:103-106) for a no-arg constructor`; `setUiHandlers(ExpressionUiHandlers) (:670-672), replaced by setOnChange/setOnSearch`; `AsyncSuggestOracle's REST use moves to TermEditorController (AsyncSuggestOracle.java)`
- **Names:** A widget, so it keeps its name and package (rule 1); its new controller TermEditorController (no clash) goes in stroom.query.client.component, owned by ExpressionTreeController, and attaches DocSelectionController and UserRefSelectionController to the two DropDownBoxes declared here. UserDocRefUtil stays a helper.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:109 | `docRefPresenter.getWidget().getElement().getStyle().setMargin(0, Unit.PX)` | static | New class termEditor-item--doc { margin: 0 } in TermEditor.css, placed after .termEditor-item.docRef (:88-92) and written .termEditor-item.termEditor-item--doc so it beats that rule's margin-top: 1px; only on the doc box (the user box keeps its 1px); check drop-down.css for a more specific margin |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:156-174 | structure | <g:FlowPanel styleName="termEditor-outer" visible="false"><g:FlowPanel ui:field="inner" styleName="termEditor-inner"> field, condition, value, valueFrom, date, dateFrom, and-label, valueTo, dateTo, docRef, userRef </g:FlowPanel> fieldTypeLabel </g:FlowPanel> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:601-604 | css-class | <i:BaseSelectionBox ui:field="fieldListBox" addStyleNames="termEditor-item dropdown field"/> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:615-617 | css-class | <i:SelectionBox ui:field="conditionListBox" addStyleNames="termEditor-item dropdown condition"/> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:630-632 | css-class | Three SuggestBoxes with addStyleNames="termEditor-item wide textBox" (value) and "termEditor-item narrow textBox" (valueFrom, valueTo), made by a @UiFactory as SuggestBox takes its oracle in its constructor |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:638-642 | component-declaration | <dt:DateTimeBox ui:field="date\|dateFrom\|dateTo" expressionsAllowed="true" addStyleNames="termEditor-item narrow"/> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:131-132 | text | <g:Label ui:field="andLabel" styleName="termEditor-label" wordWrap="false" text=" and " visible="false"/> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:653-656 | css-class | <g:Label ui:field="fieldTypeLabel" styleName="termEditor-label fieldType" visible="false"/> |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:119-130 | component-declaration | <dd:DropDownBox ui:field="docRef" addStyleNames="termEditor-item docRef termEditor-item--doc" visible="false"/> and <dd:DropDownBox ui:field="userRef" addStyleNames="termEditor-item docRef" visible="false"/>, replacing the DocSelectionBoxPresenter and UserRefSelectionBoxPresenter widgets |
| stroom-core-client/src/main/java/stroom/query/client/TermEditor.java:606, :618, :624, :640 | aria | Names Field, Condition, Value/From/To, Date/From/To as ariaLabel="..." attributes once BaseSelectionBox, SelectionBox, SuggestBox and DateTimeBox have an ariaLabel setter (TickBox's is the model); until then AccessibleName.set stays |

**Stays in Java:** Which boxes show for a field's type and condition (setVisible), and condition-wide (:339); Field type label text and title from the field (:349-354); Values written into the boxes (:439, :523-531); Condition items and the condition filter; Field filter tooltip built from the configured help URL (:607-610); Row element for the item box's looks (getRow).

## `FloorMapTimelineSettingsViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapTimelineSettings`
- **Controller:** `stroom.floormap.client.component.FloorMapTimelineSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapTimelineSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimelineSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimelineSettingsViewImpl.java (becomes FloorMapTimelineSettings)`
- **Names:** View half of unit 167.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimelineSettingsViewImpl.java:76-79 | aria | ariaLabel="Loop Playback" on <tickbox:TickBox ui:field="loopCheckBox"> (template line 28); TickBox.setAriaLabel names its input, replacing FloorMapAria.labelInnerControl and its Console.error fallback |

**Stays in Java:** DateTimeBox popup provider (:63-64): injected, so the controller passes it in; Group names on the two DateTimeBoxes (:70-71) until DateTimeBox has an ariaLabel setter; Show All enabled state and the date and loop values.

## `ExpressionTreePanel`

widget, smart widget; complexity 4, effort M; used by 1.

- **Component:** `stroom.query.client.ExpressionTreePanel`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.ui.xml`
- **Goes:** Provider and UiConfigCache constructor arguments (stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:70-72) for a no-arg constructor; `HasControllerNode / getControllerNode (:54, :114-117)`; `setUiHandlers(ExpressionUiHandlers) (:300), replaced by setOnChange/setOnSearch`
- **Names:** A widget: keeps its name and package. No controller of its own: once TermEditor is dumb it only lays out item boxes, and ExpressionTreeController (unit 173) owns the term editors' controllers. ExpressionItemBox and ExpressionItemRenderer stay as its helpers. DraggableTreePanel in its declares list belongs to ExpressionTree (unit 173), not to this class.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionItemRenderer.java:71-73 | `style.setLeft(x, Unit.PX); style.setTop(y, Unit.PX)` | dynamic | Custom properties --expression-item__left and --expression-item__top set on the box; .expression-item in expression-item.css:28-32 becomes left: var(--expression-item__left, -100000px); top: var(--expression-item__top, -100000px) |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:77-78 | structure | Root <g:HTMLPanel styleName="expression-tree"> |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:79-83 | aria | <div ui:field="items" class="expression-tree__items" role="tree" aria-label="Expression"/>; the renderer adds boxes with HTMLPanel.add(box, items), as UiBinder can't put role on a FlowPanel |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:85, :103 | component-declaration | <htree:LayeredCanvas ui:field="canvas" styleName="expression-tree__canvas"/>, made by a @UiFactory with LayeredCanvas.createIfSupported() |

**Stays in Java:** new OperatorEditor() and the TermEditor (:74-75): moved into whichever box is being edited, so not in the layout; Per-item boxes, their classes and ARIA (ExpressionItemBox.java:73, :83, :95, :105, :125-134, :209, :220): built per item from data, like grid rows; Faded and editing state.

## `ExpressionTreePresenter`

widget, presenter; complexity 4, effort L; used by 11.

- **Component:** `stroom.query.client.component.ExpressionTree`
- **Controller:** `stroom.query.client.component.ExpressionTreeController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionTree.ui.xml`
- **Goes:** `ExpressionTreeView interface (stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePresenter.java:368)`; `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreeViewImpl.java (becomes the component)`; stroom-core-client/src/main/java/stroom/query/client/ExpressionUiHandlers.java (TermEditor, ExpressionTreePanel and dashboard QueryPresenter.java:182 move to callbacks); `stroom-core-client/src/main/java/stroom/query/client/gin/QueryModule.java:97-100`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:124`
- **Names:** ExpressionTree and ExpressionTreeController are free. Not ExpressionTreeBox (the note's alternative) as nothing else needs the distinction.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionTreeViewImpl.java:95-138 | component-declaration | <pv:DraggableTreePanel ui:field="layoutPanel" addStyleNames="expression-tree-view" allowDragging="true"> holding <q:ExpressionTreePanel ui:field="treePanel"/> and <q:ExpressionTreePanel ui:field="subTreePanel"/>, using the callbacks wave 6 gives DraggableTreePanel instead of the anonymous subclass |
| stroom-core-client/src/main/java/stroom/query/client/ExpressionTreeViewImpl.java:92-93 | aria | aria-hidden="true" and inert on the drag copy: DraggableTreePanel sets these on its own copy panel (shared with PipelineTree, unit 691), so neither tree does it |

**Stays in Java:** Read-only look state class expression-tree-view--read-only (ViewImpl :367); TreeKeyboard (ViewImpl :140-199): a helper, not markup; its "item"/"items" and "in it" words are arguments; Selection model, tree model and context menu items (presenter): controller.

## `EditExpressionPresenter`

composite, presenter; complexity 3, effort M; used by 12.

**Since the merge.** copy_and_paste changed its context menu. The right-clicked term field's edit items now come first (ContextMenuManager.withEditItems). A read-only expression now shows a menu too (the field's Copy) instead of returning early. The menu restores the field's focus on hide (TextEditTarget.restore). The read-only menu is new behaviour that the controller must keep.

- **Component:** `stroom.query.client.component.ExpressionEditor`
- **Controller:** `stroom.query.client.component.ExpressionEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/EditExpressionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/EditExpressionPresenter.java (with EditExpressionView, :275-283)`; `stroom-core-client/src/main/java/stroom/data/client/view/EditExpressionViewImpl.java`; `stroom-core-client/src/main/java/stroom/receive/rules/client/gin/PolicyModule.java:41`; `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:137-140`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:125`
- **Names:** The suggested EditExpression is the same simple name as unit 444's in this wave, and the two classes are near copies (toolbar, five actions, context menu). Both merge into one ExpressionEditor in stroom.query.client.component, beside the ExpressionTree it wraps, with this one's setReadOnly and setConditionFilter and unit 444's insertValue. ExpressionEditor is free. init(RestFactory, ...) becomes setters, and the controller makes its REST calls with an owner.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/data/client/presenter/EditExpressionPresenter.java:87-95 | component-declaration | Inside <b:Toolbar name="Expression actions">: <b:ToolbarButton ui:field="addTerm" svg="ADD" title="Add Term"/>, "addOperator" OPERATOR "Add Operator", "copy" COPY "Duplicate Selected Expression Item" enabled="false", "disableItem" DISABLE "Disable Selected Expression Item" enabled="false", "deleteItem" DELETE "Delete Selected Expression Item" enabled="false" |
| stroom-core-client/src/main/java/stroom/data/client/view/EditExpressionViewImpl.java:37 | component-declaration | <qc:ExpressionTree ui:field="expressionTree"/> in the ScrollPanel, replacing the SimplePanel slot |
| stroom-core-client/src/main/resources/stroom/data/client/view/EditExpressionViewImpl.ui.xml:5 | css-class | Root "max dock-container-vertical"; this caller's extra stroom-control (and the dashboard's dock-max) is added where it is declared; rename editExpressionViewImpl-expressionTree to expression-editor__tree (components.css:288, :743) |

**Stays in Java:** Enabled states and the Enable/Disable title that follows the selection (presenter :145-150); Context menu items (IconMenuItem texts, :195-225) and the edit items: built by the controller; Read-only switch of the selection model (:126-136).

## `EditExpressionPresenter`

composite, presenter; complexity 2, effort M; used by 4.

**Since the merge.** copy_and_paste changed its add and context menu. The term field's edit items come first (ContextMenuManager.withEditItems), and the menu restores the field's focus on hide (TextEditTarget.restore through ShowMenuEvent.Builder.onHide). The same change was made to the data EditExpressionPresenter and to QueryPresenter.

- **Component:** `stroom.query.client.component.ExpressionEditor`
- **Controller:** `stroom.query.client.component.ExpressionEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/cf/EditExpressionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/EditExpressionPresenter.java (with EditExpressionView, :258-266)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/EditExpressionViewImpl.java`; stroom-core-client/src/main/java/stroom/dashboard/client/table/ExpressionUiHandlers.java (onAddFunction/onAddField: nothing implements or uses it); `stroom-core-client/src/main/java/stroom/dashboard/client/table/gin/TableModule.java:84-87`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/QueryResultTableScreenModule.java:68-70`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/TableScreenModule.java:89-91`
- **Names:** Merged into unit 310's ExpressionEditor (same simple name suggested for both, and the same code); callers cf/RulePresenter and query/SelectionHandlerPresenter move to it in the same commit. Its template differs only in the root's dock-max where 310 has stroom-control.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/EditExpressionPresenter.java:81-90 | component-declaration | Same five ToolbarButtons as unit 310, declared in the shared template |
| stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/EditExpressionViewImpl.java:38 | component-declaration | <qc:ExpressionTree ui:field="expressionTree"/> replacing the SimplePanel |

**Stays in Java:** Enable/Disable title (:119, :126) and enabled states; insertValue (data); Context menu items: controller.

