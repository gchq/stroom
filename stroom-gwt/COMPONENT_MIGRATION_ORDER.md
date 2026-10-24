# Component migration order

Proposed 24 October 2026, for agreement. The bottom-up order in which the GWT client moves to dumb components and controllers (see `COMPONENT_MODEL_DESIGN.md`, "Components, controllers and models"): every widget first, then composites, then dialogs and lists, then tabs, document editors, screens and the shell.

## How it was measured

- **Metrics, by script.** Every client class in `stroom-core-client-widget`, `stroom-core-client`, `stroom-dashboard-gwt` and `stroom-app-gwt` (2,000 classes, 310,463 lines; Ace's own code excluded): its lines, the signals that make it smart (REST, the event bus, GIN injection, owned children, static state), the client classes it uses and how many use it (fan-in), and whether another worktree is changing it (`data_grid2`: 254 client files; `copy_and_paste`: 55).
- **Review, by 14 agents**, one per partition (four for the widget module). Each grouped its classes into units (a presenter with its view interface, `ViewImpl`, `UiHandlers` and template is one unit) and classified each: kind, state, why it is smart, what stops it being a consistent dumb component, what it embeds, complexity (1 to 5) and effort (S to XL, against the pilot: `FieldEditor` S, `RuleSetSettings` M, the document picker M). Every one of the 2,000 classes is in exactly one unit.
- **Order.** A unit's level is how deep it sits: 0 if it embeds nothing to convert, else one more than the deepest unit it embeds. A planner grouped the units into 15 waves; a critic read the source of the early waves and found units placed before something they embed, misclassifications and in-flight files not flagged. Those corrections were applied (below), and the order was then checked by script: no unit that is split comes before a unit it embeds. Opening a dialog doesn't count (a controller can open an unconverted presenter dialog with `makeDialog`), nor does a dumb widget's outward tidy (adding callbacks and `setReadOnly` doesn't need what it contains converted).

## What there is

| | Units | Lines |
| --- | --- | --- |
| To convert | 608 | 220,343 |
| Presenters to split (presenter, view, template) | 441 | 156,159 |
| Widgets already dumb | 40 | 9,955 |
| Widgets dumb but needing a tidy (callbacks, read-only, template use) | 98 | 39,950 |
| Smart widgets (a box and a controller, as the document picker) | 18 | 9,575 |
| One-class smart parts to split | 11 | 4,704 |
| Already split (the pilot and earlier) | 10 | 2,815 |
| Not UI (events, models, REST, GIN modules, plugins' wiring) | 181 | 87,305 |

## Waves

A unit's wave is the table it is in; the prose above each table is the planner's, and may still name a unit the corrections moved (e.g. the data and stepping tabs, now in wave 13), or `ComponentDialog` (now `ControllerDialog`).

| Wave | Units | Lines | Effort | In flight | Waits for |
| --- | --- | --- | --- | --- | --- |
| 1. Conventions and simple leaves | 30 | 6,375 | S 30 | 0 |  |
| 2. High fan-in input and layout widgets | 20 | 9,327 | S 18, M 2 | 0 |  |
| 3. Containers, tab bars, dialog chrome and dumb view/presenter collapses | 15 | 4,032 | S 14, L 1 | 0 |  |
| 4. Smart widgets not in flight: split into box plus controller | 17 | 5,522 | S 12, M 5 | 0 |  |
| 5. Data grid, cells and selection list (after data_grid2 and copy_and_paste land) | 22 | 14,329 | S 12, M 8, L 2 | 15 | data_grid2, copy_and_paste |
| 6. Editor, menu, tree engine, explorer tree and the remaining pickers (after merges) | 15 | 10,341 | S 6, M 6, L 3 | 9 | data_grid2, copy_and_paste |
| 7. Composite families converted together | 52 | 20,723 | S 41, M 6, L 3, XL 2 | 3 |  |
| 8. Composite forms and panes built on the new pickers and editors | 41 | 12,854 | S 25, M 14, L 2 | 2 |  |
| 9. Near-dumb small dialogs | 42 | 10,099 | S 41, M 1 | 0 |  |
| 10. Lists (after data_grid2 lands) | 91 | 28,059 | S 44, M 39, L 8 | 84 | data_grid2, copy_and_paste |
| 11. Larger dialogs (REST-backed, owning lists or forms) | 82 | 28,710 | S 34, M 45, L 3 | 13 |  |
| 12. Tabs and composite panes (excluding query and dashboard) | 32 | 15,786 | S 13, M 11, L 5, XL 3 | 4 |  |
| 13. Query and dashboard stack | 69 | 34,711 | S 27, M 24, L 8, XL 10 | 10 |  |
| 14. Document editors | 34 | 6,715 | S 16, M 14, L 2, XL 2 | 0 |  |
| 15. Content-tab screens and the shell | 40 | 12,295 | S 20, M 15, L 3, XL 2 | 8 |  |

## Before the waves

- Write the leaf-widget contract into COMPONENT_MODEL_DESIGN.md before wave 1. It covers: a public no-arg constructor so the widget works in templates; outputs as setOnX(Runnable/Consumer), or a Listeners<T> list where several may listen; setReadOnly(boolean) as the single read-only setter, with setEnabled kept only where it means something different; focus() not setFocus(boolean); and the widget's UiBinder Binder made with GWT.create, not injected. Also decide whether HasValue/ValueChangeEvent stays alongside the callbacks for one release, so callers can move gradually.
- Add the setOnX callback beside the old GWT event (ClickHandler, ValueChangeEvent, SelectionEvent) and then move callers. Never remove an event and move all its callers in one change, because CustomCheckBox has 67 callers and SelectionBox 108.
- Replace task counting inside widgets (implementations of TaskMonitorFactory in RefreshButton, PagerView, PagerViewWithHeading, Button and the spinners) with setRefreshing(boolean) and a small adapter. The adapter turns Contexts.TASK_MONITOR into setRefreshing calls on the widget.
- Add DOM-bubbled requests on the MenuRequest pattern for HyperlinkEvent, AskStroomAiEvent and the copy alerts, each with a relay to the bus. Cells and DataGridWidget then no longer need the event bus in their constructors.
- Add an owner-based constructor to RestDataProvider (51 callers) and to the *ResourceClient wrappers (AnnotationResourceClient, CredentialClient, NodeClient), so late replies are dropped once the owner is disposed. Do this once, before the list wave.
- Build one shared FilteredList component (QuickFilter, toolbar, DataGrid and pager) to replace QuickFilterPageView and PagerView in the roughly 14 lists that filter, plus a ToolbarGrid shell for the toolbar-above-grid floor map views.
- Make UserRefSelectionController and DocumentAssetSelectionController follow the DocSelectionController pilot, each attached to a DropDownBox in the template, so the presenter-in-a-view pattern goes away in wave 6.
- Re-key ShowPopupEvent and the HidePopup*Event classes, and the PopupManager/PopupSupport mapping, on a Widget or HasControllerNode instead of MyPresenterWidget. This is the critical path to deleting ControllerDialog, PresenterAdapter, MyPresenterWidget, View and ViewImpl.
- Merge Dialog and ResizableDialog, and the seven OkCancelContent-style button-bar classes, into one dialog chrome with no-arg construction and an onHideRequest callback, before wave 9 starts showing component dialogs.
- Build the component equivalents of the old frameworks before the units that depend on them: a dashboard pane base, after renaming stroom.dashboard.client.main.Component to DashboardPane; a settings-tab base to replace BasicSettingsTabPresenter and SettingsPresenter; a content-tab screen base to replace ContentTabPresenter and OpenContentTabEvent's presenter payload; and DocumentEditor / DocumentEditorHost recipes for DocTabPresenter shells. Make DocumentUserPermissionsTabProvider (29 callers) and MarkdownTabProvider (29 callers) work as TabbedComponent tabs.
- Find and replace the StaticEventBus callers (DateTimeBox, TimeRangeSelector and the HelpManager path) as each owner converts.
- Keep stroom-gwt/ISSUES.md current with the order, each wave's status and the in-flight dependencies, and update COMPONENT_MODEL_DESIGN.md's migration progress at the end of each wave.
- A document-tab base, so that a tab converted in waves 7 to 13 can be hosted both by `DocTabPresenter` (until wave 14) and by `DocumentEditor` (the critic: many tabs in those waves extend `DocPresenter`).
- The parts of the critic's review applied: `DynamicSettingsPane` after `DateTimeBox`; `StreamingProcessingPresenter` with `ProcessorPresenter`; the include/exclude filters after `DocRefListPresenter`; `MetaPresenter` with the data tabs it embeds (it is `FeedPresenter`'s and `PipelinePresenter`'s Data tab); `TabLayout`, `FlexLayout` and `Splitter` with the dashboard (they take `TabManager`); `PagerView`, `PagerViewWithHeading`, `WrapperView` and the dialog chrome after `data_grid2` (which moves or edits them); the floor map timeline's view and presenter together; `QueryDocPresenter` with its owner; `TableRowCell` with `AnnotationManager`; `DataGridWidget` and `TraceOverviewWidget` reclassified as smart widgets (they take the app's bus), `LinkTabPanelPresenter` as a presenter, and `DocumentAssetQuickUploadPresenter` as a dialog. `DocSelectionBoxPresenter`, a wrapper over `DocSelectionController` since the pilot, is retired in wave 8 once its users attach the controller themselves. `SelectionBox` keeps wave 2 for its outward API only; its popup and base class change in wave 5.

## 1. Conventions and simple leaves

Set the leaf contract (no-arg constructor, setOnX, setReadOnly, focus(), Binder made with GWT.create) on the smallest leaf widgets and cells that are not in flight, so every later wave has a model to copy. Buttons gain setOnClick, SvgIconBox's setReadonly is renamed setReadOnly, and RefreshButton/Spinner* get setRefreshing in place of their own task counting.

**Why now.** Each of these units embeds nothing that needs converting, and most are S effort. Button, ToolbarButton and Toolbar together have about 180 callers, so fixing their API first unblocks almost every template above them. The read-only and callback conventions have to be settled before any composite reads Contexts.READ_ONLY.

**Risks.**

- Button and ToolbarButton have high fan-in. Keep ClickHandler/ButtonView working while setOnClick is added, and move callers in later waves.
- AbstractSettingsWidget is the base for 17 Plan B leaves. Change it to the thin Component base without breaking them; they convert in wave 7.
- RefreshButton registers a global REFRESH key binding statically. Keep that behaviour, or move it to an explicit app-level registration.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ToolbarButton` | widget | dumb | 0 | 2 | S | 294 | 57 |  | Already the model for a dumb button: no-arg constructor, svg/title template attributes, setOnClick; only remove the ButtonView legacy when callers are off it. |
| `Button` | widget | tidy | 0 | 2 | S | 349 | 43 |  | Add setOnClick and keep ClickHandler until callers move; ButtonView can only go once the presenters' view interfaces that expose ButtonView are gone, so drop… |
| `TickBoxCell` | cell | dumb | 0 | 2 | S | 388 | 19 |  | Render plus ARIA checkbox; output via ValueUpdater/selection model as GWT cells do. Already dumb. |
| `AbstractSettingsWidget` | widget | tidy | 0 | 1 | S | 36 | 17 |  | Base of 17 PlanB settings widgets; retire it when they move onto Component (setReadOnly/onReadOnlyChange + setOnChange), in one sweep with the leaf widgets. |
| `RefreshButton` | widget | tidy | 0 | 2 | S | 190 | 13 |  | Dumb apart from a static shortcut registration and acting as a task monitor. Keep the static command, as it is app-wide and idempotent; whether task… |
| `LinkButton` | widget | tidy | 0 | 1 | S | 77 | 11 |  | Tiny; add setOnClick for consistency with ToolbarButton. |
| `ExpanderCell` | cell | dumb | 0 | 1 | S | 153 | 11 |  | A plain GWT AbstractCell with a lazy static SafeHtml template. Nothing to split; the global_state signal is only the static template. |
| `SpinnerLarge` | widget | dumb | 0 | 1 | S | 120 | 8 |  | No-arg, has a UiBinder template and is used in Dialog/ResizableDialog templates. Leaf; nothing to change. |
| `ActionCell` | cell | dumb | 0 | 1 | S | 128 | 7 |  | Output is a BiConsumer action passed to the constructor; already dumb. |
| `SvgIconBox` | widget | tidy | 0 | 1 | S | 114 | 4 |  | Tiny wrapper that puts an icon button beside a field; rename setReadonly and add a callback; only 4 callers. |
| `FabButton` | widget | tidy | 0 | 1 | S | 204 | 4 |  | Add setOnClick and align the focus API with ToolbarButton. |
| `LayeredCanvas` | widget | tidy | 0 | 1 | S | 117 | 4 |  | Stack of canvases; dumb. Only a constructor/factory tidy if it ever needs to go in a template. |
| `SpinnerSmall` | widget | dumb | 0 | 1 | S | 105 | 3 |  | No-arg leaf widget (pager spinner). Nothing to change. |
| `SettingBlock` | widget | dumb | 0 | 1 | S | 164 | 3 |  | Has a no-arg constructor and setReadOnly, and templates already use it. It could switch to setOnClick but works in templates as it is. |
| `ValueSpinnerCell` | cell | dumb | 0 | 3 | S | 860 | 2 |  | An editable cell (730 lines) with a timer-driven Spinner. cell/valuespinner/client/Spinner duplicates widget/valuespinner/client/Spinner, so merge them when… |
| `FindResultCell` | cell | dumb | 0 | 1 | S | 75 | 2 |  | SafeHtml cell; nothing to do. |
| `QueryCell` | cell | tidy | 0 | 1 | S | 72 | 2 |  | GWT cell for stored queries; keep it as a cell. |
| `AssignYourselfLink` | widget | tidy | 0 | 1 | S | 84 | 2 |  | Make it a real dumb widget so the two templates that use it can declare it. |
| `TextFieldCell` | cell | dumb | 0 | 1 | S | 112 | 1 |  | Editable text cell with aria-label and setReadOnly; already dumb. |
| `PercentBarCell` | cell | dumb | 0 | 1 | S | 352 | 1 |  | Small render-only cells grouped together; nothing to do. |
| `FindInContentResultCell` | cell | dumb | 0 | 1 | S | 116 | 1 |  | SafeHtml cell; nothing to do. |
| `AiChatCell` | cell | tidy | 0 | 1 | S | 57 | 1 |  | Simple renderer; fine to keep with the formatter given by its owner. |
| `Toolbar` | widget | dumb | 1 | 2 | S | 537 | 79 |  | Container with factory methods button/toggle/refresh(onClick); dumb already. Highest fan-in leaf here, so any API change ripples widely. |
| `FormGroup` | widget | dumb | 1 | 3 | S | 1128 | 54 |  | The template layout container used by nearly every form (fan-in 54). It does heavy ARIA wiring of whatever control it holds (JSNI querySelectorAll), so any… |
| `QuickSettings` | widget | dumb | 1 | 1 | S | 89 | 2 |  | Already dumb; nothing to do beyond following any LinkButton API change. |
| `Spinner` | widget | dumb | 1 | 1 | S | 90 | 2 |  | Dumb widget with a display-delay Timer; no change needed. |
| `DocumentTypeCell` | cell | dumb | 1 | 1 | S | 104 | 1 |  | Cell plus its DocumentTypeSelectionModel interface; nothing to do. |
| `ColumnValueCell` | cell | dumb | 1 | 1 | S | 69 | 1 |  | Plain GWT cell rendering a tick box and value. |
| `FindAnnotationCell` | cell | tidy | 1 | 1 | S | 106 | 1 |  | A pure render cell. The only issue is its formatter dependency. |
| `SettingsGroup` | widget | dumb | 2 | 1 | S | 85 | 9 |  | Already a no-arg Composite used in templates; nothing to do. |

## 2. High fan-in input and layout widgets

Give the shared value widgets and layout widgets setOnChange callbacks and one read-only setter, alongside their existing HasValue/ValueChangeEvent API, and make them usable in templates (TimeZoneWidget loses its injected Binder; DropDownBox keeps one disabled state).

**Why now.** Between them these widgets have more than 350 callers (SelectionBox 108, CustomCheckBox 67, ValueSpinner 34, QuickFilter 21). Every form, settings pane and dialog later in the plan is built from them.

**Risks.**

- SelectionBox's popup (SelectionPopup/SelectionList) is in flight in data_grid2. Change only SelectionBox's outward API here; its two-step init(model) is fixed in wave 5.
- Editor needs an explicit destroy() for its Ace instance. The component's dispose must call it, or Ace instances leak.
- Settings panes still call onReadOnly while this changes. Agree how setReadOnly and setEnabled differ before changing CustomCheckBox.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `CustomCheckBox` | widget | tidy | 0 | 2 | M | 547 | 67 |  | No bus despite the 'bus' signal (it is GWT addHandler for its own events). High fan-in (67) means changing the output API touches many callers; add… |
| `PasswordBox` | widget | tidy | 0 | 2 | S | 230 | 8 |  | Already template-usable with a show/hide toggle and a badge slot. The tidy is about API consistency only, so remove getInput() once callers stop using it. |
| `Editor` | widget | tidy | 0 | 3 | S | 768 | 4 |  | The Ace wrapper Composite (650 lines) has a no-arg constructor and no bus or injection, so it is effectively the dumb code-editor widget. destroy() must stay… |
| `ColourBox` | widget | tidy | 0 | 1 | S | 95 | 3 |  | A native colour input with a no-arg constructor; it only needs setReadOnly and a callback or listener list in place of ValueChangeEvent. Its 'bus' signal is… |
| `XSDDisplay` | widget | dumb | 0 | 3 | S | 1222 | 1 |  | Hand-drawn schema diagram widgets with no outside dependencies; large but self-contained, so leave unless the browser component needs a different selection API. |
| `LineColInput` | widget | tidy | 0 | 1 | S | 133 | 1 |  | Dumb and has a no-arg constructor. Users reach into getTextBox() for events, so add setOnChange and hide the TextBox. |
| `RadioGroup` | widget | tidy | 0 | 2 | S | 183 | 1 |  | A dumb, generic ARIA radio group that draws its own DOM. Add setReadOnly and decide between a callback and HasValue for every leaf box at once. |
| `HistogramWidget` | widget | tidy | 0 | 2 | S | 828 | 1 |  | Already a no-arg Composite with ARIA and keyboard; rename the callback. Used by FloorMapTimelineViewImpl. |
| `FloorMapIconPicker` | widget | tidy | 0 | 2 | S | 230 | 1 |  | Leaf icon-grid picker, already dumb with a public no-arg constructor; only the API names need tidying. Used by FloorMapLayerStyleViewImpl. |
| `TraceHistogramWidget` | widget | tidy | 0 | 2 | S | 169 | 1 |  | A leaf histogram rendered as HTML. Pass the formatter through a setter, or format in the controller. |
| `SplitPane` | widget | dumb | 1 | 3 | S | 934 | 7 |  | No-arg constructor, template-usable, setOnResized callback, keyboard resizing; already dumb. Its use of MenuRequest is the pattern other widgets should copy… |
| `DaysWidget` | widget | tidy | 1 | 1 | S | 164 | 1 |  | Already template-usable; add setOnChange and drop setEnabled. |
| `ValueSpinner` | widget | tidy | 2 | 3 | S | 753 | 34 |  | Leaf control with 34 users; add setOnChange(Consumer<Long>) beside HasValue first and migrate callers gradually, as UiBinder @UiHandler(ValueChangeEvent)… |
| `QuickFilter` | widget | tidy | 2 | 3 | S | 900 | 21 |  | Template-usable (no-arg constructor) and already dumb; tidy its output to a callback and add setReadOnly before converting the 16+ list presenters that use it… |
| `DropDownBox` | widget | dumb | 2 | 2 | S | 231 | 4 |  | Already the worked example of a dumb box (Component, UiBinder DropDownBox.ui.xml, setOnOpen, setText, setProblem, setReadOnly); DocSelectionController… |
| `Pager` | widget | dumb | 2 | 2 | S | 377 | 3 |  | A dumb pager with its own template (Pager.ui.xml) and a no-arg constructor. RangeChangeEvent goes to its display, which is the GWT cell-view contract, so… |
| `SelectionBox` | widget | tidy | 6 | 3 | M | 523 | 108 |  | Already a no-arg, template-usable leaf (combobox ARIA done); the work is moving its change output from ValueChangeEvent to a callback/Listeners across ~108… |
| `DurationPicker` | widget | tidy | 7 | 2 | S | 150 | 7 |  | Convert after ValueSpinner and SelectionBox get callback APIs; it just relays their changes. |
| `CustomDatePicker` | widget | tidy | 7 | 2 | S | 688 | 3 |  | The ARIA-grid calendar from the date-time box design; it is dumb, has a no-arg constructor and is used in the DateTimeViewImpl template. Only callback… |
| `TimeZoneWidget` | widget | tidy | 7 | 2 | S | 202 | 3 |  | A dumb form part used by date-time settings panels: give it a no-arg constructor (GWT.create Binder) and setOnChange. |

## 3. Containers, tab bars, dialog chrome and dumb view/presenter collapses

Move the tab-bar family to setOnSelect/setOnClose/setOnMenu, merge Dialog and ResizableDialog and the button bars into no-arg chrome, and collapse the presenter/view pairs that hold no logic into single components. PagerView and PagerViewWithHeading become components; their callers move later.

**Why now.** These are leaf-level containers and chrome. The tab bars block CurveTabLayoutPresenter and the 16 views built on LinkTabsLayoutView. The dialog chrome blocks every component dialog in waves 9 and 11. PagerView (86 callers) and WrapperView (11 callers) must exist as components before the lists and tool screens can move.

**Risks.**

- AbstractTabBar is 1,349 lines with four GwtEvent outputs. Its owners forward these to the bus, so keep both routes until CurveTabLayoutPresenter converts (wave 15).
- PagerView and WrapperView are widely used. Ship the component next to the old view and keep the GIN binding until wave 10 or 15 moves the callers.
- TooltipPresenter is used as a singleton by NodeStatusList and ProcessorTask*. It must keep working as an app-wide helper made through AppRoot.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TooltipPresenter` | dialog | presenter | 0 | 1 | S | 141 | 9 |  | Tiny popup; becomes a component with its widget inline and show(...) via ShowPopupEvent from the owner. Nine callers inject a Provider, so they will switch to… |
| `ProgressPresenter` | widget | presenter | 0 | 2 | S | 483 | 7 |  | Nothing smart here: fold the presenter's bound/uncertainty maths and the ViewImpl into one dumb ProgressBar component with a no-arg constructor and… |
| `IFramePresenter` | widget | tidy | 0 | 2 | S | 333 | 6 |  | Already dumb in behaviour. Collapse it into one IFrame widget with setters and setOnTitleChange. IFrameLoadUiHandlers is shared with IFrameContentPresenter. |
| `LinkTabBar` | widget | tidy | 0 | 1 | S | 200 | 4 |  | Used by settings dialogs' LinkTabsLayoutView; follows AbstractTabBar. |
| `HtmlPresenter` | widget | presenter | 0 | 1 | S | 87 | 4 |  | Collapse it into one dumb HtmlPanel component with a no-arg constructor and setHtml. No controller is needed. Its users are TextPresenter and DataPresenter. |
| `LayerContainerImpl` | widget | dumb | 1 | 2 | S | 179 | 10 |  | Already used by the component model via ComponentLayer; leave as is. |
| `UserTaskPresenter` | composite | tidy | 1 | 1 | S | 181 | 3 |  | A dumb task row. Fold it into one component with setTaskProgress and setOnTerminate. |
| `AbstractTabBar` | widget | tidy | 1 | 5 | L | 1349 | 2 |  | 1025-line base class with keyboard (ARIA tablist), overflow, drag and resize logic; not smart, but swapping its four event channels for callbacks touches both… |
| `VisToolbarRow` | widget | tidy | 1 | 2 | S | 231 | 2 |  | Already dumb and no-arg; tidy the callback. VisToolbarButton is its data (JUnit-tested). |
| `QueryButtons` | widget | tidy | 2 | 1 | S | 117 | 5 |  | A leaf with a no-arg constructor and a template, so it is a quick early win. Several dashboard and analytics views use it, so update all callers together. |
| `TextBoxPopup` | dialog | presenter | 2 | 1 | S | 171 | 3 |  | A tiny name-entry dialog (used by TabSessionManager); no REST, so it becomes one component dialog shown with ComponentDialog.showOnce and a show(caption,… |
| `GroupLevelButtons` | widget | tidy | 2 | 1 | S | 95 | 3 |  | Dumb helper; fine as is unless the table toolbar moves to a template. |
| `MainToolbar` | composite | tidy | 2 | 1 | S | 78 | 2 |  | Already a template-usable Composite; replace MainUiHandlers with two callbacks. |
| `CurveTabBar` | widget | tidy | 2 | 2 | S | 250 | 1 |  | Main content tab bar; follows AbstractTabBar's API change. CurveTabSelector has its own UiBinder template. |
| `OperatorEditor` | widget | tidy | 7 | 1 | S | 137 | 2 |  | Tiny leaf already built with new(): swap the UiHandlers for setOnChange(Runnable) and keep Operator mutation in the tree controller. Convert it with… |

## 4. Smart widgets not in flight: split into box plus controller

Split the widgets that open their own presenter dialogs, or reach services or the bus themselves, into a dumb box and a controller or callback, following the DropDownBox and DocSelectionController pilot. The date, time and schedule popups become component dialogs. The navigators invert to callbacks. ClassificationLabel takes its colours from outside, and VisFrame and CustomFileUpload lose their bus and transport coupling.

**Why now.** The input widgets from wave 2 are now dumb. DateTimeBox (19 callers) and ScheduleBox are what stop the floor map, credential and scheduling views from being used in templates: those views inject a Provider<DateTimePopup> only to pass it on. Removing the StaticEventBus fallbacks here closes a hidden smart dependency.

**Risks.**

- DateTimeBox's popup provider is injected in several views (CredentialSettings, ReplaceSecret, FloorMapObjectEdit, FloorMapTimelineSettings). Keep setPopupProvider until each of them converts.
- VisFrame holds static state (the PostMessage singleton, frame id counters). Keep it a widget with a dispose step, and test with both of its users (VisPresenter and QueryResultVisPresenter).
- CustomFileUpload's upload transport is swapped by workbench stories. Keep the transport hook test-only when moving the upload into a controller.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ClassificationLabel` | widget | smart widget | 0 | 1 | S | 110 | 3 |  | Make it dumb with setLabelColours(...) pushed by its owner; a quick first conversion. |
| `CustomFileUpload` | widget | smart widget | 1 | 3 | M | 503 | 9 |  | Split into a dumb file-chooser box (template, getFilename, setOnChange) and an upload controller or service that owns the transport, task monitor and reply… |
| `ClassificationWrapperView` | composite | tidy | 1 | 1 | S | 111 | 6 |  | Becomes a small Component with a content slot and setClassification once ClassificationLabel is dumb; used by DataPresenter and SourcePresenter. |
| `TimePopup` | dialog | presenter | 3 | 2 | S | 231 | 4 |  | Becomes a dumb TimePanel (TimeViewImpl's template, no-arg) plus a TimeDialogController that shows it. It must be split before TimeBox can lose its… |
| `ItemSelectionPresenter` | dialog | presenter | 3 | 1 | S | 207 | 4 |  | Tiny go-to-item dialog; a good early Phase 8 dialog. |
| `StepLocationPresenter` | composite | presenter | 3 | 1 | S | 130 | 3 |  | A dumb form that can become a component directly. |
| `ItemNavigatorPresenter` | widget | presenter | 4 | 2 | S | 352 | 5 |  | The view is already most of a dumb pager widget; split into an ItemNavigator component (callbacks for first/prev/next/last/refresh/label) and a small… |
| `StepLocationLinkPresenter` | widget | presenter | 4 | 2 | S | 181 | 3 |  | A link that opens the location dialog. Once StepLocation is a component, this is a dumb link plus a small controller. |
| `TimeBox` | widget | smart widget | 4 | 2 | S | 200 | 2 |  | A text box plus a dialog opener. Replace the provider with setOnOpenPicker(Runnable) and let a controller, or a no-GIN TimePanel dialog, open the picker, or… |
| `SchedulePopup` | dialog | presenter | 7 | 3 | M | 513 | 8 |  | The schedule dialog (fan-in 8), used by ScheduleBox and others. ScheduledTimeClient is its REST client. |
| `CharacterRangeSelectionPresenter` | dialog | presenter | 7 | 2 | S | 428 | 4 |  | 300-line ViewImpl with range validation; result is already a Consumer<DataRange>. |
| `DateTimePopup` | dialog | presenter | 8 | 2 | S | 476 | 10 |  | DateTimeViewImpl (393 lines) is the real dumb content and becomes a DateTimePanel component; the popup becomes a small dialog controller. Several screens… |
| `CharacterNavigatorPresenter` | widget | presenter | 8 | 2 | S | 435 | 4 |  | Sibling of ItemNavigatorPresenter; do both together. Drop the unused ProgressPresenter. |
| `ScheduleBox` | widget | smart widget | 8 | 3 | M | 261 | 4 |  | Same shape as the DocSelectionBox split: a dumb box that asks to open and shows validity, plus a ScheduleController that owns SchedulePopup and the validate… |
| `DateTimeBox` | widget | smart widget | 9 | 3 | M | 325 | 19 |  | The design calls this the single date box used everywhere (fan-in 19). Removing its StaticEventBus fallback depends on DateTimePopup being split into a panel… |
| `TimeRangeSelector` | widget | smart widget | 10 | 2 | S | 371 | 3 |  | Make TimeRangePopup a dumb component shown with ComponentDialog/popup from the selector (as DateTimeBox does). This removes StaticEventBus. Used in templates… |
| `DynamicSettingsPane` | widget | tidy | 10 | 3 | M | 688 | 1 |  | Dumb but dynamic: keep the Java-built controls; add setReadOnly and setOnChange. VisInputChoices/VisFieldOption are its pure helpers (JUnit-tested). |

## 5. Data grid, cells and selection list (after data_grid2 and copy_and_paste land)

Make DataGridWidget usable in templates and free of the bus: requests bubble through the DOM, and sort, range and selection outputs become callbacks. Cells take onOpen/onMenu callbacks instead of an EventBus or HasHandlers. SelectionList and SelectionPopup move from init(model) to setters.

**Why now.** DataGridWidget has 114 callers and sits under every list. Nearly all of these files are being changed in data_grid2 or copy_and_paste, so this wave starts as soon as those land, before any list.

**Risks.**

- Blocked until data_grid2 and copy_and_paste are merged. Starting earlier means conflicts in nearly every file in the wave.
- The ref cells and TableRowCell open menus and documents. The menu-opener and onOpen callbacks need a working route to the bus through the owning list, or context menus stop working.
- Changing DataGridWidget's constructor touches 114 callers. Keep a deprecated constructor that takes the bus until wave 10.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WrapperView` | composite | tidy | 0 | 1 | S | 69 | 11 | yes | A trivial SimplePanel-like view. Once presenters become controllers it can be replaced by a plain SimplePanel, or dropped by calling initWidget(child), at… |
| `DocRefCell` | cell | smart widget | 0 | 3 | M | 429 | 7 | yes | FeedRefCell and UserRefCell copy its shape, so convert the three together (onOpen callback, menu provider). In flight in copy_and_paste and data_grid2. |
| `SvgCell` | cell | dumb | 0 | 1 | S | 290 | 6 | yes | Icon cell, its value type and an info column; InfoColumn is in flight in worktree-data_grid2. Nothing to split. |
| `AbstractPopupPanel` | widget | tidy | 0 | 2 | S | 363 | 5 | yes | Base popup panel (glass, drag) and the undecorated popup; AbstractPopupPanel is being changed in worktree-data_grid2, so wait for it. |
| `HelpPopup` | dialog | tidy | 0 | 2 | S | 180 | 4 |  | Self-contained popup panel, no bus; mostly an API consistency tidy (setContent/setOnClose). |
| `ActionMenuCell` | cell | tidy | 0 | 2 | S | 186 | 4 |  | Showing the menu by bus event ties it to a presenter source; either keep (menus are app-wide requests, allowed) or swap HasHandlers for a menu-showing… |
| `MyCellTable` | widget | dumb | 0 | 1 | S | 107 | 2 | yes | Legacy CellTable subclass due to be replaced by the DataGrid in worktree-data_grid2; Refreshable is a widely used interface (fan-in 23) that should survive. |
| `CommandLinkCell` | cell | dumb | 0 | 1 | S | 168 | 1 | yes | No-arg cell running the CommandLink's own command; in flight in worktree-data_grid2. |
| `HasContextMenusCell` | cell | dumb | 0 | 1 | S | 52 | 1 | yes | Tiny wrapper cell; in flight in data_grid2. |
| `CopyTextCell` | cell | tidy | 0 | 2 | S | 93 | 1 | yes | In flight in copy_and_paste and data_grid2; do not touch until both land. |
| `UserRefCell` | cell | smart widget | 0 | 3 | M | 313 | 1 | yes | Same pattern as DocRefCell; in flight in copy_and_paste and data_grid2. |
| `DataGridWidget` | widget | smart widget | 1 | 5 | L | 6266 | 114 | yes | The new div grid (fan-in 114) is being changed in worktree-data_grid2 and worktree-copy_and_paste, so wait for those before touching it; the only smart part… |
| `FeedRefCell` | cell | smart widget | 1 | 3 | M | 371 | 1 | yes | Near copy of DocRefCell; convert with it. In flight in copy_and_paste and data_grid2. |
| `Dialog` | widget | tidy | 2 | 3 | M | 830 | 1 |  | Fixed-size dialog chrome with drag, keyboard window menu (DialogWindow) and focus trap; shares DialogWindow with ResizableDialog. Only PopupSupportImpl makes… |
| `OkCancelContent` | widget | tidy | 2 | 2 | M | 793 | 1 |  | Dialog button bars made only by PopupSupportImpl. Collapse into one DialogButtonBar taking the action set and a callback; that removes most of the 793 lines. |
| `PagerView` | composite | tidy | 3 | 2 | M | 308 | 86 | yes | A dumb list frame (pager, toolbar, list container, message panel) bound by GIN for every list presenter (fan-in 86). Turn it into one concrete PagerPanel… |
| `PagerViewWithHeading` | composite | tidy | 3 | 1 | S | 173 | 4 | yes | Merge into the PagerView component as an optional heading. Only JobNodeListPresenter and NodeJobListPresenter use it. |
| `ResizableDialog` | widget | tidy | 3 | 3 | M | 591 | 1 |  | Dialog chrome built only by PopupSupportImpl. Merge with Dialog (a resizable flag) before or while tidying, rather than converting both separately. |
| `SelectionList` | widget | tidy | 4 | 4 | M | 786 | 6 | yes | No REST/bus of its own; data comes from the SelectionListModel (async onRangeChange). Being changed by worktree-data_grid2 (DataGridWidget rename to… |
| `VisFrame` | widget | smart widget | 4 | 4 | L | 1322 | 4 |  | Split into a dumb frame component (iframe, messages in/out through callbacks) and a controller that turns hyperlink/selection messages into bus events. Used… |
| `SelectionPopup` | widget | tidy | 5 | 2 | S | 254 | 3 |  | Internal helper of BaseSelectionBox, SelectionBoxCell and similar drop-downs; tidy together with SelectionBox so the selection/close outputs become callbacks… |
| `SelectionBoxCell` | cell | dumb | 6 | 2 | S | 385 | 1 | yes | A grid cell that opens a SelectionPopup; no bus/REST. Being edited in worktree-data_grid2; leave it until that merges. |

## 6. Editor, menu, tree engine, explorer tree and the remaining pickers (after merges)

Turn the Ace EditorPresenter into a dumb CodeEditor with a settings model in place of its 13 option getters. Split MenuPresenter into a component. Configure DraggableTreePanel and PipelineTreePanel with callbacks instead of subclassing. Split AbstractExplorerTree into a tree widget and an ExplorerTreeModel controller. Give FlexLayout and TabLayout no-arg construction. Finish the UserRef and DocumentAsset box splits so DropDownViewImpl can go.

**Why now.** Everything at level 1 and above that embeds an editor (31 callers), the explorer tree, a user picker (11 callers) or the tree engine waits on these units. Wave 5 has made the grid and selection list dumb, which the choosers and the explorer popup need.

**Risks.**

- The EditorPresenter API change is the hardest in the plan: callers change editor options directly through 13 getXxxOption() getters. Introduce the settings model and move callers in the same review.
- DraggableTreePanel is shared by the pipeline tree and the query expression tree. Test both, including the agreed keyboard designs.
- Making MenuPresenter a component depends on popup events keyed on widgets (shared work). It is also in flight in copy_and_paste.
- FlexLayout is 2,498 lines and in flight. Limit the change to construction and outputs; do not restructure it in this wave.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ExplorerTreeModel` | model-or-util | smart class | 0 | 4 | M | 449 | 4 |  | The data half of the explorer tree; becomes the controller-side model once AbstractExplorerTree is dumb. |
| `DraggableTreePanel` | widget | tidy | 0 | 4 | M | 1229 | 3 | yes | Generic drag/keyboard tree engine shared with ExpressionTreeViewImpl/ExpressionTreePanel/ExpressionItemBox in stroom.query.client; change its API with both… |
| `EditorPresenter` | widget | smart widget | 1 | 4 | L | 1868 | 31 | yes | The code editor used by 31 screens. EditorPresenter, EditorViewImpl and EditorMenuPresenter are being changed in worktree-copy_and_paste, so wait for that to… |
| `MenuPresenter` | dialog | presenter | 1 | 4 | M | 1342 | 3 | yes | Popup menu with recursive sub-menus, keyboard model and timers; converting to a Menu component means each sub-menu component is made and owned by its parent.… |
| `PipelineTreePanel` | widget | tidy | 1 | 3 | M | 1117 | 2 | yes | Pipeline-specific rendering (boxes, ports, connectors, severities); dumb already apart from depending on PipelineModel. PipelineTreePanel is in flight in… |
| `SingleLineEditorPresenter` | widget | presenter | 1 | 2 | S | 154 | 1 |  | Split it with EditorPresenter: a dumb single-line editor component plus the shared editor controller logic (theme, key bindings, completer). Split… |
| `DocumentAssetTree` | widget | tidy | 2 | 3 | S | 593 | 6 | yes | Dumb tree on the data grid. Give it a no-arg constructor, setAccessibleName and a setOnSelect callback. In flight in data_grid2. |
| `AbstractExplorerTree` | widget | smart widget | 2 | 4 | L | 1197 | 3 | yes | Leaf of the explorer stack (ExplorerTree, ExplorerTickBoxTree, ExplorerCell, TickBoxSelectionModel): split into a dumb tree widget fed rows by a… |
| `ChooserPresenter` | widget | smart widget | 3 | 2 | M | 313 | 5 | yes | A quick-filter and single-select grid that the annotation editor and ChangeStatus show in popups. It becomes a dumb Chooser<T> with a tiny controller for the… |
| `DocumentAssetChooserPresenter` | dialog | presenter | 3 | 2 | S | 150 | 4 |  | Do it after DocumentAssetTree. |
| `DropDownViewImpl` | widget | presenter | 3 | 1 | S | 155 | 2 |  | Delete once UserRefSelectionBoxPresenter and DocumentAssetDropDownPresenter follow the DocSelectionBox split; no work in this unit itself. |
| `MultiChooserPresenter` | widget | smart widget | 3 | 2 | S | 181 | 2 | yes | The multi-select twin of ChooserPresenter and shares its view, so split them together. Being changed in worktree-data_grid2. |
| `TraceOverviewWidget` | widget | smart widget | 3 | 5 | L | 1379 | 1 | yes | A 1379-line trace waterfall renderer, being changed in worktree-data_grid2. Replace the HasHandlers bus hook with a callback and TaskMonitorFactory with… |
| `DocumentAssetDropDownPresenter` | widget | smart widget | 4 | 2 | S | 99 | 3 |  | Same split as DocSelectionBox: a DropDownBox in the template plus an asset selection controller. |
| `UserRefSelectionBoxPresenter` | widget | smart widget | 5 | 2 | M | 115 | 11 |  | Same split as DocSelectionBox: a dumb DropDownBox in templates plus a UserRefSelectionController that owns the popup. Fan-in 11, so do it early. It already… |

## 7. Composite families converted together

Convert the regular stacks in one pass each: the 17 Plan B leaf settings widgets and their 8 type-settings presenters become dumb components (no controller); the floor map leaf panels are converted together with their ViewImpls; the five credential secret forms move behind a shared SecretForm; the pipeline and pathway trees; and the expression tree (TermEditor, then ExpressionTreePanel, then ExpressionTreePresenter, then both EditExpressionPresenters).

**Why now.** These are the first composites. Each family embeds only widgets that are dumb by now: the wave 2 inputs, the wave 4 date and time boxes, and the wave 6 tree engine and pickers. Each family is regular enough that converting it in one sweep is cheaper than doing it piece by piece.

**Risks.**

- This is a large wave. Review it as five sub-batches: Plan B, floor map, secrets, pipeline/pathway trees, and the expression tree.
- FloorMapCanvasViewImpl (3,104 lines) and FloorMapCanvasPresenter (3,712 lines) are XL. Rename the callbacks and remove the view interface without restructuring the drawing code.
- Plan B's carry-over of common values across a state-type change currently works through instanceof checks on views. Move that into plain data held by PlanBSettingsPresenter (wave 12) and do not break it in the meantime.
- EditExpressionPresenter has 12 callers that use init(RestFactory, ...). Replace init with setters and a REST owner, and keep the read-only and change behaviour.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `AbstractPlanBSettingsPresenter` | composite | presenter | 0 | 1 | S | 68 | 9 |  | Becomes a small interface/base for the dumb type-settings components (read/write/setOnChange/setReadOnly); goes when its 8 subclasses do. |
| `PipelinePathPresenter` | widget | presenter | 0 | 5 | L | 1758 | 4 |  | The stepping chevron bar, which is mostly a widget. Make it a dumb PipelinePathBar with setOnSelect and setOnMenu, and keep the menu in its controller. |
| `FloorMapCanvasViewImpl` | widget | tidy | 0 | 5 | XL | 3104 | 1 |  | View half only (FloorMapCanvasPresenter is in another partition). 2574 lines of hand-drawn map rendering with JSNI and ARIA; FloorMapGrid (also used by the… |
| `FloorMapLayersViewImpl` | composite | dumb | 0 | 1 | S | 86 | 1 |  | View half only. |
| `FloorMapCanvasPresenter` | widget | presenter | 1 | 5 | XL | 3712 | 6 |  | Behaviourally dumb (callbacks only), so it should become a single FloorMapCanvas widget with interaction helpers extracted, not a component+controller pair;… |
| `FloorMapGroupEditPresenter` | dialog | presenter | 1 | 2 | S | 208 | 4 |  | Simple OK/Cancel dialog; FieldEditor-sized split. |
| `FloorMapGroupEditViewImpl` | composite | tidy | 1 | 2 | S | 254 | 1 |  | View half only. Builds member check-box rows dynamically; a template plus a list would suit it. |
| `GeneralSettingsWidget` | widget | tidy | 2 | 2 | S | 241 | 10 |  | HttpStoreSettingsView (synchronise merge/overwrite) is satisfied through it by the composite views; fold both interfaces into the component's setters. |
| `SnapshotSettingsWidget` | widget | tidy | 2 | 1 | S | 94 | 7 |  | No view interface; mechanical. |
| `UsernamePasswordSecretPresenter` | composite | presenter | 2 | 1 | S | 162 | 4 |  | Nearly dumb already: merge into one UsernamePasswordSecretForm component with getSecret()/validate() returning a message; the owner shows the alert. |
| `AccessTokenSecretPresenter` | composite | presenter | 2 | 1 | S | 144 | 4 |  | Same shape as the other secret forms; do all five together with a shared SecretForm interface (getSecret, validate, focus). |
| `SshKeySecretPresenter` | composite | presenter | 2 | 1 | S | 211 | 4 |  | Dumb form apart from the alert; merge into one component. |
| `FloorMapDockPresenter` | composite | presenter | 2 | 1 | S | 122 | 4 |  | Duplicates TabbedComponent; likely replaced by it rather than converted. |
| `FloorMapLayersPresenter` | list | presenter | 2 | 3 | M | 674 | 4 |  | No REST or bus: becomes a dumb FloorMapLayers component directly; style editing is delegated by callback already. |
| `PipelineTreePresenter` | widget | presenter | 2 | 3 | M | 519 | 4 |  | Nearly dumb: only the AlertEvent on a failed move is smart, which an owner callback can take. Make it a dumb PipelineTree component (no controller) for… |
| `PathwayTreePresenter` | widget | presenter | 2 | 3 | M | 457 | 4 |  | Draws a pathway tree as HTML/SVG with its own selection model. The drawing becomes a dumb widget; firing ShowTracesEvent moves to its owner or controller. |
| `TraceGeneralSettingsWidget` | widget | tidy | 2 | 1 | S | 86 | 1 |  | No view interface; mechanical. |
| `FloorMapDockViewImpl` | composite | tidy | 2 | 1 | S | 65 | 1 |  | View half only. A LinkTabPanel lookalike; could become a TabbedComponent. |
| `SharedFileStoreSettingsWidget` | widget | tidy | 3 | 2 | S | 154 | 1 |  | Make setSharedFileStoreLocked a plain setter its owner calls. |
| `RetentionSettingsWidget` | widget | tidy | 7 | 2 | S | 242 | 9 |  | Check-interval panel shown by a flag; keep as a setter. Used by all 8 type-settings composites. |
| `KeyStoreFileSecretPresenter` | composite | presenter | 7 | 1 | S | 212 | 5 |  | Dumb form; give it setType/setPath on the component so callers stop reaching into the view. |
| `KeyStoreSecretPresenter` | composite | presenter | 7 | 2 | S | 208 | 5 |  | Only secret form with real I/O: component owns the upload widget and exposes submit(onDone); the controller (or settings controller) sets the action URL and… |
| `FloorMapSetScalePresenter` | dialog | presenter | 7 | 1 | S | 148 | 4 |  | Small OK/Cancel dialog; FieldEditor-sized. |
| `CondenseSettingsWidget` | widget | tidy | 7 | 2 | S | 161 | 4 |  | Enables age/unit from its own tick box; keep that local state in the component. |
| `StateValueSchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 131 | 4 |  | Shows/hides hash length by value type; used by 4 state-type composites. |
| `HistogramValueSchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 109 | 1 |  | Trivial form; mechanical. |
| `MetricValueSchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 160 | 1 |  | Trivial form; mechanical. |
| `PublishingSettingsWidget` | widget | tidy | 7 | 2 | S | 176 | 1 |  | Trace-only; mechanical. |
| `RangeKeySchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 108 | 1 |  | Trivial form; mechanical. |
| `SessionKeySchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 144 | 1 |  | Trivial form; mechanical. |
| `StateKeySchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 131 | 1 |  | Shows/hides hash length by key type; mechanical. |
| `TemporalRangeKeySchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 122 | 1 |  | Trivial form; mechanical. |
| `TemporalStateKeySchemaSettingsWidget` | widget | tidy | 7 | 1 | S | 144 | 1 |  | Trivial form; mechanical. |
| `FloorMapLayerStyleViewImpl` | composite | tidy | 7 | 3 | S | 291 | 1 |  | View half only; do FloorMapIconPicker first. |
| `FloorMapSetScaleViewImpl` | dialog | tidy | 7 | 1 | S | 139 | 1 |  | View half only; a small dialog body. |
| `StateSettingsPresenter` | composite | presenter | 8 | 2 | S | 265 | 3 |  | Only maps settings to and from the view; once its leaves are dumb it collapses into one dumb component with read/write, setOnChange and setReadOnly, read-only… |
| `TemporalStateSettingsPresenter` | composite | presenter | 8 | 2 | S | 284 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `RangeStateSettingsPresenter` | composite | presenter | 8 | 2 | S | 260 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `TemporalRangeStateSettingsPresenter` | composite | presenter | 8 | 2 | S | 284 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `SessionSettingsPresenter` | composite | presenter | 8 | 2 | S | 260 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `TraceSettingsPresenter` | composite | presenter | 8 | 2 | S | 299 | 3 |  | Also round-trips maxQueryTimeRange and compactionFrequency, which have no editor; keep that in read/write so a save does not clear them. |
| `HistogramKeySchemaSettingsWidget` | widget | tidy | 8 | 2 | S | 166 | 1 |  | Declare TimeZoneWidget in the template once it is template-usable; near-duplicate of MetricKeySchemaSettingsWidget. |
| `MetricKeySchemaSettingsWidget` | widget | tidy | 8 | 2 | S | 166 | 1 |  | Near-duplicate of HistogramKeySchemaSettingsWidget; could share one component. |
| `HistogramSettingsPresenter` | composite | presenter | 9 | 2 | S | 261 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `MetricSettingsPresenter` | composite | presenter | 9 | 2 | S | 261 | 3 |  | Same shape as StateSettingsPresenter; collapse into one dumb component. |
| `FloorMapTimelineSettingsPresenter` | dialog | presenter | 10 | 1 | S | 154 | 3 |  | Small popup; uses a raw Button, which should become a toolbar or Stroom button. |
| `TermEditor` | widget | smart widget | 10 | 4 | L | 860 | 2 |  | Needs a dumb TermBox that uses DropDownBox for doc and user refs and takes a suggestion source from outside. A TermEditorController (owned by the expression… |
| `FloorMapTimelineSettingsViewImpl` | composite | tidy | 10 | 2 | S | 155 | 1 |  | View half only. |
| `ExpressionTreePanel` | widget | smart widget | 11 | 4 | M | 737 | 1 | yes | Becomes dumb once TermEditor is a dumb box: it then only lays out boxes and editors. ExpressionTreePanel is being changed in worktree-data_grid2, so… |
| `ExpressionTreePresenter` | widget | presenter | 12 | 4 | L | 864 | 11 |  | A widely used expression editor (fan-in 11: dashboards, analytics, processor filters, quick filter). Split into an ExpressionTreeBox component (tree, drag,… |
| `EditExpressionPresenter` | composite | presenter | 13 | 3 | M | 344 | 12 | yes | Fan-in 12 (receipt rules, retention, content templates, view settings, meta filter); convert right after ExpressionTreePresenter. In flight in copy_and_paste. |
| `EditExpressionPresenter` | composite | presenter | 13 | 2 | M | 358 | 4 | yes | Expression tree + toolbar wrapper; in flight in copy_and_paste. Needs ExpressionTreePresenter converted first; RestFactory threaded through init should become… |

## 8. Composite forms and panes built on the new pickers and editors

Convert the settings forms, editor panes and explorer pickers that embed DocSelectionBox, UserRef, EditorPresenter, Markdown or the explorer tree. Replace embedded DocSelectionBoxPresenter with a DropDownBox in the template plus DocSelectionController. Views stop exposing their widgets, and onReadOnly becomes setReadOnly.

**Why now.** Their children (editor, explorer tree, pickers, secret forms and Plan B leaves) became components in waves 6 and 7. MarkdownEditPresenter (34 callers) and DocSelectionPopup (11 callers) unblock many tabs and dialogs.

**Risks.**

- The explorer popup is the foundation of DocSelectionController. Any regression here breaks every document picker, so run the picker stories with the leak check.
- Settings panes are tabs on documents that have not converted yet. They must still work when hosted by DocPresenter or DocTabPresenter (presenters can host components).
- QueryHelpPresenter is handed the EditorPresenter. Replace that with an onInsert(text) callback the owner wires up.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TextPresenter` | composite | presenter | 2 | 2 | S | 307 | 5 |  | Thin wrapper over EditorPresenter with FAB buttons; depends on the code editor becoming a component first. |
| `ColumnFilterPresenter` | composite | presenter | 2 | 1 | S | 150 | 5 |  | Thin wrapper around an Ace editor for a column filter expression; becomes a tiny component once EditorPresenter is a component. |
| `XSDBrowserPresenter` | tab | presenter | 2 | 2 | S | 204 | 3 |  | Essentially dumb already (no REST, no bus use); collapse presenter, UiHandlers and ViewImpl into one component with read(model). Used by XMLSchemaPresenter. |
| `TextPresenter` | composite | presenter | 2 | 4 | L | 880 | 3 |  | The query's text pane. Its template is shared with the dashboard TextViewImpl (stroom/dashboard/client/text). Split a dumb text pane from a controller that… |
| `SqlTemporalStoreSettingsPresenter` | tab | presenter | 2 | 2 | S | 198 | 3 |  | Small settings tab; good early candidate. |
| `AnalyticEmailDestinationPresenter` | composite | presenter | 2 | 3 | M | 434 | 3 |  | Two code editors for the templates. Split into a templated component (with the code editor widgets) and a controller that makes the test REST calls. |
| `CommentEditPresenter` | composite | presenter | 2 | 1 | S | 111 | 3 |  | A thin wrapper over the Ace editor for a comment. It goes away once EditorPresenter is a component. |
| `XMLSchemaSettingsPresenter` | tab | presenter | 2 | 1 | S | 171 | 3 |  | Trivial form; mechanical. |
| `MarkdownPreviewPresenter` | composite | presenter | 2 | 2 | S | 206 | 3 |  | Markdown source and preview side by side; MarkdownEditPresenter embeds it. |
| `WordsTab` | tab | smart class | 2 | 1 | S | 64 | 1 |  | Only a wrapper; once the Ace EditorPresenter has a dumb code-editor component this becomes that component with setOnChange, no controller needed. |
| `FloorMapTimelineViewImpl` | widget | tidy | 2 | 3 | M | 607 | 1 |  | View half only. A 607-line scrubber/timeline control with a histogram; once the view interface goes it is essentially a leaf widget. |
| `MarkdownEditPresenter` | tab | presenter | 3 | 3 | M | 302 | 34 |  | The Documentation tab of every document (fan-in 34), so high value. Split it after EditorPresenter and IFramePresenter. |
| `EntityTreePresenter` | composite | presenter | 3 | 2 | S | 257 | 6 |  | Filter + explorer tree pair; same shape as ExplorerPopupPresenter without the popup, consider sharing a component. |
| `ExplorerPopupPresenter` | dialog | presenter | 3 | 3 | M | 355 | 3 |  | The document chooser dialog behind every DocSelectionBox; split after AbstractExplorerTree so the dialog component can hold a dumb tree. |
| `EntityCheckTreePresenter` | composite | presenter | 3 | 2 | S | 172 | 3 |  | Mostly a wrapper; becomes trivial once the tick-box tree is dumb. |
| `AiConfigTableAnalysisPresenter` | tab | presenter | 3 | 1 | S | 273 | 3 |  | Spinner form with no smarts; quick win. |
| `ExplorerPopupViewImpl` | dialog | presenter | 3 | 2 | S | 216 | 2 |  | Template (ExplorerPopupViewImpl.ui.xml) is already a clean QuickFilter + tree slot + sr-only status; it can become the popup component's template almost as is… |
| `DocSelectionPopup` | dialog | presenter | 4 | 1 | S | 38 | 11 |  | Exists only to give ExplorerPopupPresenter a public injectable constructor; disappears when that is split. |
| `DocSelectionBoxPresenter` | widget | presenter | 5 | 2 | M | 173 | 35 |  | A thin wrapper over DocSelectionController since the pilot; its users move to a DropDownBox in their templates plus the controller as they convert, and it is… |
| `QueryHelpPresenter` | composite | presenter | 5 | 3 | M | 362 | 4 | yes | Being changed in worktree-copy_and_paste. Split it into a dumb help pane (list, detail, buttons, setOnInsert) and a controller holding the REST-backed model… |
| `DataGenSettingsPresenter` | tab | presenter | 6 | 2 | S | 162 | 3 |  | Use the DropDownBox attach pattern for the destination feed. |
| `VisualisationSettingsPresenter` | tab | presenter | 6 | 2 | S | 194 | 3 |  | Settings tab; dumb once DocSelectionBox/Editor are components. |
| `FloorMapEventStoreSettingsPresenter` | tab | presenter | 7 | 1 | S | 91 | 3 |  | Small settings form; good early floormap conversion. |
| `ElasticIndexSettingsPresenter` | tab | presenter | 7 | 3 | M | 464 | 3 |  | Uses the DropDownBox and DocSelectionController pattern from the pilot four times, so put the pickers in the template. |
| `FeedSettingsPresenter` | tab | presenter | 7 | 3 | M | 402 | 3 |  | A settings form whose presenter reaches into the view's widgets. Move to read/write methods on a dumb component and a small controller for the option lists. |
| `EditorPreferencesPresenter` | tab | presenter | 7 | 2 | S | 305 | 3 | yes | Near-dumb; pass theme lists in from the owner and it becomes a pure component; inflight in copy_and_paste. |
| `ThemePreferencesPresenter` | tab | presenter | 7 | 2 | S | 356 | 3 |  | Move the live theme preview event to the owner. |
| `TimePreferencesPresenter` | tab | tidy | 7 | 2 | S | 338 | 3 |  | Nothing smart; merge view into a component. |
| `AiConfigGeneralPresenter` | tab | presenter | 7 | 2 | S | 335 | 3 |  | Settings form; after the DocSelectionBox split put DropDownBox + DocSelectionController in the template and it becomes dumb. |
| `ReportSettingsPresenter` | tab | presenter | 7 | 2 | M | 317 | 3 |  | Convert together with AbstractSettingsPresenter. Both doc pickers become a DropDownBox plus a DocSelectionController. |
| `IndexSettingsPresenter` | tab | presenter | 7 | 3 | M | 465 | 3 |  | Like the pilot's RuleSetSettings. Its pipeline picker follows the DocSelectionController pattern. |
| `DenseVectorFieldPresenter` | composite | presenter | 7 | 2 | S | 287 | 3 |  | A settings sub-form. Put DropDownBoxes in its template and attach DocSelectionControllers. |
| `TextConverterSettingsPresenter` | tab | presenter | 7 | 1 | S | 128 | 3 |  | Trivial; one component with setConverterType/setOnChange. |
| `AbstractSettingsPresenter` | tab | presenter | 7 | 2 | M | 381 | 2 |  | Same shape as the AnalyticStreamDestination pilot: the error feed picker becomes a DropDownBox in the template plus a DocSelectionController.… |
| `PathwaysSettingsPresenter` | tab | presenter | 8 | 2 | S | 344 | 3 |  | A simple settings form. Replace DocSelectionBoxPresenter with DropDownBox + DocSelectionController. |
| `FloorMapEventStoreSettingsViewImpl` | composite | tidy | 8 | 2 | S | 210 | 1 |  | View half only. The Plan B settings widgets have to be template-usable first. |
| `TableBuilderProcessingPresenter` | composite | presenter | 10 | 3 | M | 446 | 5 |  | The form in the template is already dumb; the node list and the info refresh are controller work. A good early candidate in analytics. |
| `CredentialSettingsPresenter` | tab | presenter | 10 | 3 | M | 418 | 3 |  | Split after the five secret forms; the type switch swaps a dumb secret component in a slot. DateTimeBox popup provider injection is a cross-cutting tidy… |
| `QueryToolbarPresenter` | composite | presenter | 11 | 2 | S | 335 | 6 |  | Nearly dumb. Once its error alert moves to the owner, it collapses into a single QueryToolbar component with a template. |
| `FloorMapTimelinePresenter` | composite | presenter | 11 | 4 | L | 875 | 4 |  | Playback state is the controller; the histogram/scrub bar should become a dumb timeline widget with icons and text in its template. |
| `ContentStoreContentPackDetailsPresenter` | composite | smart class | 14 | 3 | M | 521 | 1 |  | Classic one-class smart widget: move layout into ContentPackDetails.ui.xml with setContentPack(...) and setOnAction, and the REST/credentials dialog into a… |

## 9. Near-dumb small dialogs

Collapse each small dialog (presenter, view interface and ViewImpl) into one component dialog. Each gets a show or edit(..., callback) method and is shown with ComponentDialog.showOnce. Validation returns a message, and the opener raises the alert. Callers stop reading getView() getters.

**Why now.** The dialog chrome (wave 3) and the forms and pickers they embed (waves 7-8) are ready. Most of these are S effort quick wins in the style of the FieldEditor pilot, and they remove the shared NameDocumentView (7 callers) and the back-references into parent presenters (TablePresenter, DashboardPresenter).

**Risks.**

- CommonAlertPresenter queues alerts and runs their callbacks on hide. Keep the queue order and the callback timing exactly.
- One shared name dialog replaces NameDocumentView for five presenters. Each owner's REST create call moves into its owner or controller, not into the dialog.
- Callers that read getView() getters inside HidePopupRequestEvent handlers must change in the same commit as their dialog.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `CommonAlertPresenter` | dialog | presenter | 0 | 2 | S | 328 | 4 |  | The body is already dumb (message, icon, show/hide detail). The queue of alert events belongs in AlertPlugin or a controller. Shown at the app root. |
| `PromptPresenter` | dialog | presenter | 0 | 1 | S | 81 | 4 |  | Not really UI: it wraps the browser's Window.prompt. Fold it into AlertPlugin or replace it with a proper prompt dialog component. |
| `CurrentSelectionPresenter` | composite | presenter | 0 | 1 | S | 114 | 4 |  | Nearly dumb already. Becomes a component with setHtml and setOnInsert. |
| `AboutPresenter` | dialog | presenter | 0 | 1 | S | 189 | 3 |  | Ideal first dialog split: AboutDialog component with setBuildInfo(...)/setAboutHtml and an AboutController doing the REST and UiConfigCache call; REST is… |
| `DependenciesInfoPresenter` | dialog | presenter | 0 | 1 | S | 119 | 3 |  | Tiny info dialog. |
| `QueryInfoPresenter` | dialog | presenter | 0 | 2 | S | 266 | 3 |  | Dialog that asks for query info and checks it against a regex. The QueryInfo helper already follows the AppRoot pattern. |
| `InfoDocumentPresenter` | dialog | presenter | 0 | 1 | S | 219 | 3 |  | A read-only info table; a trivial component. |
| `SplashPresenter` | dialog | presenter | 0 | 1 | S | 168 | 3 |  | The start-up splash/acknowledgement dialog. |
| `SelectionSummaryPresenter` | dialog | presenter | 1 | 2 | S | 241 | 4 |  | Confirm-with-summary dialog used by the meta lists for delete/restore/reprocess. |
| `ExecutionScheduleRunNowPresenter` | dialog | presenter | 1 | 1 | S | 174 | 3 |  | Small confirm-style dialog. Converts easily to a ComponentDialog with a result callback. |
| `KeyShortcutRecorderPresenter` | dialog | presenter | 2 | 2 | S | 344 | 4 |  | Dumb apart from the popup; easy dialog component with a result callback. |
| `RenameColumnPresenter` | dialog | presenter | 2 | 1 | S | 186 | 4 |  | Single text box dialog; good early candidate, replace the TablePresenter reference with a result callback. |
| `RenameTabPresenter` | dialog | presenter | 2 | 1 | S | 169 | 4 |  | Replace the DashboardPresenter reference with the result callback it already takes. |
| `DocumentAssetAddItemDialogPresenter` | dialog | presenter | 2 | 1 | S | 228 | 3 |  | Effectively dumb already. Becomes a ComponentDialog with an edit(callback) method. |
| `DocumentAssetEditAssetDialogPresenter` | dialog | presenter | 2 | 1 | S | 231 | 3 |  | Rename dialog that is effectively dumb already. |
| `GitRepoCommitDialogPresenter` | dialog | presenter | 2 | 1 | S | 178 | 3 |  | Effectively dumb; convert to a component dialog with edit(onOk) as FieldEditor does. |
| `CustomRowStylePresenter` | dialog | presenter | 2 | 1 | S | 267 | 3 |  | Colour fields for a custom row style; no REST, simple component + showOnce. |
| `DownloadChatPresenter` | dialog | presenter | 2 | 1 | S | 99 | 3 |  | Trivial options dialog; quick win. |
| `LayoutConstraintPresenter` | dialog | presenter | 2 | 1 | S | 170 | 3 |  | Two tick boxes. Becomes a dumb component with setOnChange(Consumer<LayoutConstraints>). |
| `NamePresenter` | dialog | presenter | 2 | 1 | S | 213 | 3 |  | Name dialog for favourites. The REST call moves to its owner or a controller. |
| `AddEventLinkPresenter` | dialog | presenter | 2 | 1 | S | 169 | 3 |  | A tiny one-field dialog that already returns its result by callback. An easy first split (component, plus a controller using makeDialog). |
| `StepControlPresenter` | composite | presenter | 2 | 2 | S | 392 | 3 |  | Already effectively dumb (the first, back, forward, last and refresh toolbar). Merge presenter and view into one component with setOnAction(StepAction). |
| `NewElementPresenter` | dialog | presenter | 2 | 1 | S | 170 | 3 |  | Small name/description dialog; good early Phase 8 candidate (component + edit(..., callback)). |
| `NameDocumentPresenter` | dialog | presenter | 2 | 1 | S | 259 | 2 |  | Other presenters reuse NameDocumentView as a generic name-entry view; one dumb name-entry component would serve all of them. |
| `CreateNewGroupPresenter` | dialog | presenter | 3 | 2 | S | 124 | 4 |  | Shown by CreateUserPresenter, UsersPresenter, UserAndGroupsPresenter and UserAndGroupHelper. Needs a dumb name-entry component to replace NameDocumentView… |
| `ProcessorLimitsPresenter` | dialog | presenter | 3 | 1 | S | 145 | 3 |  | Already dumb in substance. Merge the presenter and view into one component. |
| `NewNodeGroupPresenter` | dialog | presenter | 3 | 2 | S | 117 | 1 |  | A name-entry dialog. It could share a generic dumb 'name' dialog component. |
| `NewFsVolumeGroupPresenter` | dialog | presenter | 3 | 1 | S | 134 | 1 |  | Name dialog, the same shape as NamePresenter; both could share one dumb name dialog component. |
| `NewIndexVolumeGroupPresenter` | dialog | presenter | 3 | 2 | S | 133 | 1 |  | A name dialog that could reuse a generic name-entry component. |
| `ProfilePeriodEditPresenter` | dialog | presenter | 5 | 2 | S | 286 | 3 |  | No REST; straightforward dialog. |
| `FormatPresenter` | dialog | presenter | 7 | 2 | S | 582 | 6 |  | Column format dialog; no REST, only the TimeZones list; straightforward component conversion. |
| `DownloadPresenter` | dialog | presenter | 7 | 1 | S | 184 | 4 |  | Pure form (format, sample, percent); essentially dumb already, just needs collapsing into a component with a show(callback). |
| `SolrIndexFieldEditPresenter` | dialog | presenter | 7 | 2 | S | 506 | 3 |  | Nearly dumb, with 15 tick boxes. Same pattern as FieldEditor in the pilot. |
| `EditFeedDependencyPresenter` | dialog | presenter | 7 | 2 | S | 214 | 3 |  | Good early candidate: put DropDownBox in its template and attach DocSelectionController. |
| `XPathFilterPresenter` | dialog | presenter | 7 | 1 | S | 293 | 3 |  | A small dumb form dialog, so an easy split. |
| `NewPipelineReferencePresenter` | dialog | presenter | 7 | 2 | S | 266 | 3 |  | Use the pilot's attach pattern: two <DropDownBox> in the template with DocSelectionControllers attached (as AnalyticStreamDestination). |
| `NewPropertyPresenter` | dialog | presenter | 7 | 3 | M | 640 | 3 |  | Value editor varies by property type; keep it a slot or put all editors in the template with setVisible. Opened by PropertyListPresenter with makeDialog. |
| `ConstraintEditPresenter` | dialog | presenter | 7 | 3 | S | 582 | 3 |  | Dialog that edits a pathway constraint. Its large switch converting values to and from text belongs in a controller or model helper; there is a lot of… |
| `DurationPresenter` | dialog | presenter | 8 | 1 | S | 144 | 3 |  | The retention duration popup. It is essentially dumb already, so an easy split. |
| `IndexFieldEditPresenter` | dialog | presenter | 8 | 2 | S | 344 | 3 |  | The field dialog. DenseVectorField must be split first. |
| `ProcessChoicePresenter` | dialog | presenter | 10 | 1 | S | 291 | 6 |  | Simple OK/Cancel form; replace the UiHandler with a Consumer. |
| `ExpressionPresenter` | dialog | presenter | 14 | 1 | S | 140 | 9 |  | Meta filter dialog content; almost nothing left once EditExpressionPresenter is split. |

## 10. Lists (after data_grid2 lands)

Convert every list presenter built on PagerView/DataGridWidget into a list component plus a controller that owns its REST data: RestDataProvider with an owner, and FilteredList where the list has a quick filter. Selection goes out through callbacks, and back-references to parents are removed. The floor map grid lists use the shared ToolbarGrid.

**Why now.** The grid and cells (wave 5) and the RestDataProvider owner change (shared work) are in place, and data_grid2 has landed, since it touches nearly every unit here. Lists sit under every screen, document tab and larger dialog. A converted list can still open its unconverted edit dialog with makeDialog, so dialog ownership does not block this wave.

**Risks.**

- This is the largest wave (89 units). Review it by area: explorer/find, pipeline/index fields, processing/meta, nodes/jobs/cache/volumes, analytics/dashboard lists, security/users, floor map.
- It cannot start until data_grid2 has merged. Check its final API before writing FilteredList.
- Lists that call back into a parent (ScheduledProcess*, ContentStoreContentPackList, MarkerList) need the parent's handling replaced with callbacks the parent wires, even though the parent converts later.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `FloorMapFactListViewImpl` | composite | dumb | 0 | 1 | S | 70 | 1 |  | View half only. The same toolbar+grid shell as the Groups, TimeList and Tracking views; one shared component would replace all four. |
| `FloorMapGroupsViewImpl` | composite | dumb | 0 | 1 | S | 68 | 1 |  | View half only; a duplicate toolbar+grid shell. |
| `FloorMapTimeListViewImpl` | composite | dumb | 0 | 1 | S | 68 | 1 |  | View half only; a duplicate toolbar+grid shell. |
| `FloorMapTrackingViewImpl` | composite | dumb | 0 | 1 | S | 68 | 1 |  | View half only; a duplicate toolbar+grid shell. |
| `TypeFilterPresenter` | widget | presenter | 2 | 2 | S | 328 | 4 | yes | Effectively dumb (no REST); make it a component popup with setTypes/getSelected and a Listeners list; inflight in data_grid2. |
| `FloorMapFactListPresenter` | list | presenter | 2 | 3 | M | 483 | 3 | yes | No REST/bus: really a dumb list in presenter clothing; in flight in worktree-data_grid2. |
| `FloorMapGroupsPresenter` | list | presenter | 2 | 3 | M | 553 | 3 | yes | In flight in worktree-data_grid2; split after FloorMapGroupEdit. |
| `FloorMapTimeListPresenter` | list | presenter | 2 | 2 | M | 368 | 3 | yes | Preference listening should move to the controller, which pushes a formatter into the dumb list. |
| `FloorMapTrackingPresenter` | list | presenter | 2 | 3 | M | 576 | 3 | yes | No REST: close to a dumb list; in flight in worktree-data_grid2. |
| `DocumentCreatePermissionsListPresenter` | list | presenter | 2 | 3 | M | 400 | 3 | yes | No REST. It is nearly a dumb tick list once the document types are passed in. In flight in data_grid2. |
| `FindDocResultListPresenter` | list | presenter | 2 | 2 | S | 192 | 3 | yes | Result list in Find/Recent Items; inflight in data_grid2. |
| `TabSessionChooserPresenter` | dialog | presenter | 2 | 2 | S | 175 | 3 | yes | Nearly dumb already: a list chooser; convert into a component with setOnChoose; inflight in data_grid2. |
| `DocRefSelectionPresenter` | dialog | presenter | 2 | 2 | S | 206 | 3 | yes | Injected once into the singleton PipelinePlugin and reused, never owned or disposed - make it per use with appRoot.makeDialog. In flight in data_grid2. |
| `MarkerListPresenter` | list | presenter | 2 | 2 | M | 322 | 1 | yes | Replace setDataPresenter with setOnSelect-style callbacks; in flight in data_grid2. |
| `DocumentListPresenter` | list | presenter | 2 | 2 | S | 197 | 1 | yes | Plain REST list used by BatchDocumentPermissionsPresenter; inflight in data_grid2. |
| `DocumentPermissionsListPresenter` | list | presenter | 2 | 3 | M | 307 | 1 | yes | REST list used by UserPermissionReportPresenter; inflight in data_grid2. |
| `ProcessorListPresenter` | list | presenter | 2 | 4 | L | 930 | 1 | yes | Tree grid with inline edits saved via REST; inflight in data_grid2, so convert after it lands. |
| `ProcessorProfileListPresenter` | list | presenter | 2 | 2 | S | 121 | 1 | yes | Simple REST list; inflight in data_grid2. |
| `QuickFilterPageViewImpl` | composite | presenter | 3 | 1 | S | 196 | 14 |  | A generic 'quick filter above a list' layout; good candidate for a single dumb FilteredList component (template + setOnFilterChange) that each list controller… |
| `KeyBindingPreferencesPresenter` | tab | presenter | 3 | 3 | M | 502 | 5 | yes | No REST; inflight in data_grid2. |
| `KeyboardShortcutsPresenter` | dialog | presenter | 3 | 2 | S | 271 | 3 | yes | Read-only static list; inflight in data_grid2. |
| `AppPermissionsEditPresenter` | composite | presenter | 4 | 3 | M | 450 | 4 | yes | Permission tick grid with a description pane, in flight in data_grid2. Used as a tab in UserTabPresenter and inside AppPermissionsPresenter. |
| `UserListPresenter` | list | presenter | 4 | 4 | L | 545 | 4 | yes | UserAndGroupsPresenter uses it three times and UsersPresenter once. In flight in data_grid2. |
| `ElasticIndexFieldListPresenter` | tab | presenter | 4 | 2 | S | 267 | 3 | yes | A read-only field list with no REST: close to dumb already. Being changed in worktree-data_grid2. |
| `ProcessorTaskListPresenter` | list | presenter | 4 | 3 | M | 348 | 3 | yes | In flight in data_grid2. |
| `FindAnnotationListPresenter` | list | presenter | 4 | 2 | S | 187 | 3 | yes | Shared by the three Find screens. Being changed in worktree-data_grid2. |
| `EffectiveWordsList` | list | smart class | 4 | 2 | S | 249 | 2 |  | Pre-split pilot class (old smart Component, now Controller); split into a dumb WordList (setWords, setOnOpen) and a controller doing setDocRef/REST. |
| `DocRefListPresenter` | list | presenter | 4 | 1 | S | 113 | 2 | yes | Almost dumb: becomes a DocRefList widget with setOnOpen(Consumer<DocRef>); wait for worktree-data_grid2. |
| `ProcessorTaskSummaryPresenter` | list | presenter | 4 | 3 | M | 243 | 2 | yes | In flight in data_grid2. |
| `DuplicateManagementListPresenter` | list | presenter | 4 | 2 | M | 188 | 2 | yes | In flight in worktree-data_grid2. |
| `JobListPresenter` | list | presenter | 4 | 3 | M | 271 | 2 | yes | The job list, with enable tick boxes. Being changed in worktree-data_grid2. |
| `ConfigPropertyClusterValuesListPresenter` | list | presenter | 4 | 2 | S | 161 | 2 | yes | A tree-like list of per-node values; being changed in worktree-data_grid2. |
| `ManageGlobalPropertyListPresenter` | list | presenter | 4 | 4 | M | 539 | 2 | yes | Being changed in worktree-data_grid2. Merges data from every node into its rows. |
| `ContentStoreContentPackListPresenter` | list | presenter | 4 | 3 | M | 360 | 1 | yes | In flight in worktree-data_grid2; remove the native console() debug helper while splitting. |
| `ResultStoreListPresenter` | list | presenter | 4 | 3 | M | 221 | 1 | yes | A typical REST list. Being changed in worktree-data_grid2 (DataGrid). |
| `NodeStatusListPresenter` | list | presenter | 4 | 3 | M | 591 | 1 | yes | Being changed in worktree-data_grid2. The inline-edit columns (priority, enabled) make the controller split larger than a plain list. |
| `NodeGroupListPresenter` | list | presenter | 4 | 2 | S | 156 | 1 | yes | A simple REST list. Being changed in worktree-data_grid2. |
| `NodeGroupStateListPresenter` | list | presenter | 4 | 2 | S | 191 | 1 | yes | A list of nodes with membership ticks. Being changed in worktree-data_grid2. |
| `CacheListPresenter` | list | presenter | 4 | 2 | S | 198 | 1 | yes | A simple REST list. Being changed in worktree-data_grid2. |
| `CacheNodeListPresenter` | list | presenter | 4 | 3 | M | 367 | 1 | yes | Its columns change per cache. Being changed in worktree-data_grid2. |
| `ApiKeysListPresenter` | list | presenter | 4 | 4 | L | 576 | 1 | yes | Large data-grid list, in flight in data_grid2. ApiKeysListViewImpl.ui.xml is an empty template with no class behind it. |
| `AppUserPermissionsListPresenter` | list | presenter | 4 | 3 | M | 361 | 1 | yes | In flight in data_grid2. |
| `DocumentUserPermissionsListPresenter` | list | presenter | 4 | 3 | M | 378 | 1 | yes | In flight in data_grid2. |
| `SigningKeyListPresenter` | list | presenter | 4 | 2 | S | 154 | 1 | yes | Simple list, in flight in data_grid2. |
| `UserAccessListPresenter` | list | presenter | 4 | 2 | S | 226 | 1 | yes | In flight in data_grid2. |
| `UserSessionsListPresenter` | list | presenter | 4 | 2 | S | 181 | 1 | yes | In flight in data_grid2. |
| `UserDependenciesListPresenter` | tab | presenter | 4 | 2 | S | 224 | 1 | yes | Dependencies tab of UserTabPresenter, in flight in data_grid2. |
| `TaskManagerListPresenter` | list | presenter | 4 | 4 | L | 692 | 1 | yes | 692-line tree grid, in flight in data_grid2. |
| `RuleSetListPresenter` | list | presenter | 4 | 2 | S | 160 | 1 | yes | Already nearly dumb: a candidate for the first list component. In flight in data_grid2. |
| `DataRetentionPolicyListPresenter` | list | presenter | 4 | 2 | S | 273 | 1 | yes | Almost dumb list; in flight in data_grid2. |
| `ContentTemplateListPresenter` | list | presenter | 4 | 2 | S | 349 | 1 | yes | Nearly dumb list; in flight in data_grid2. |
| `DependenciesPresenter` | list | presenter | 4 | 3 | M | 401 | 1 | yes | Row actions fire app-wide events (acceptable from the controller side). In flight in data_grid2. |
| `RuleListPresenter` | list | presenter | 4 | 2 | S | 188 | 1 | yes | Plain list of conditional-formatting rules, no REST; in flight in data_grid2. Becomes a dumb list component. |
| `AiChatHistoryResultListPresenter` | list | presenter | 4 | 2 | S | 162 | 1 | yes | Result list of chats; in flight in data_grid2. Split into a dumb list + controller doing the REST. |
| `AccountsListPresenter` | list | presenter | 4 | 3 | M | 386 | 1 | yes | Accounts grid with toolbar; in flight in data_grid2. Split list component from controller. |
| `AnalyticDataShardListPresenter` | list | presenter | 4 | 3 | M | 371 | 1 | yes | In flight in worktree-data_grid2. Merging the results from several nodes is controller logic. |
| `ScheduledProcessHistoryListPresenter` | list | presenter | 4 | 2 | M | 216 | 1 | yes | In flight in worktree-data_grid2. Replace the parent reference with a callback. |
| `ScheduledProcessListPresenter` | list | presenter | 4 | 3 | M | 383 | 1 | yes | In flight in worktree-data_grid2. |
| `SelectionHandlerListPresenter` | list | presenter | 4 | 2 | S | 106 | 1 | yes | In flight in worktree-data_grid2. A local list with no REST. |
| `ColumnSelectionPresenter` | list | presenter | 4 | 2 | S | 187 | 1 | yes | In flight in worktree-data_grid2. A list of columns to tick; could become a dumb list widget with setOnChange. |
| `FsVolumeGroupListPresenter` | list | presenter | 4 | 2 | S | 142 | 1 | yes | In flight in worktree-data_grid2. |
| `FsVolumeStatusListPresenter` | list | presenter | 4 | 3 | M | 306 | 1 | yes | In flight in worktree-data_grid2. |
| `AnnotationTagListPresenter` | list | presenter | 4 | 2 | S | 140 | 1 | yes | A plain REST-backed list. Wait for worktree-data_grid2. |
| `XPathListPresenter` | list | presenter | 4 | 1 | S | 123 | 1 | yes | A client-side list. Being changed in worktree-data_grid2. |
| `IndexShardPresenter` | list | presenter | 4 | 4 | L | 567 | 1 | yes | A 567-line shard list with flush and delete actions across nodes. Being changed in worktree-data_grid2. |
| `IndexVolumeGroupListPresenter` | list | presenter | 4 | 2 | S | 105 | 1 | yes | A plain list. Being changed in worktree-data_grid2. |
| `IndexVolumeStatusListPresenter` | list | presenter | 4 | 2 | M | 275 | 1 | yes | The volume status list. Being changed in worktree-data_grid2. |
| `ScriptListPresenter` | list | presenter | 4 | 1 | S | 77 | 1 | yes | A client-side list of DocRefs. Being changed in worktree-data_grid2. |
| `TracesListPresenter` | list | presenter | 4 | 4 | M | 459 | 1 | yes | Being changed in worktree-data_grid2. A list with a histogram and filter-error reporting. |
| `ActivityListPresenter` | list | presenter | 4 | 2 | S | 158 | 1 | yes | Being changed in worktree-data_grid2. |
| `IncludeExcludeFilterDictionaryPresenter` | composite | presenter | 5 | 2 | S | 127 | 1 | yes | Dictionary list with add/remove; in flight in data_grid2. Depends on DocRefListPresenter and DocSelectionPopup conversion. |
| `ScriptDependencyListPresenter` | composite | presenter | 5 | 2 | S | 165 | 1 | yes | The add and remove dependency list. Being changed in worktree-data_grid2. |
| `IncludeExcludeFilterPresenter` | composite | presenter | 6 | 2 | S | 194 | 3 |  | Two text areas + two dictionary lists; convert after IncludeExcludeFilterDictionaryPresenter. |
| `ProfilePeriodListPresenter` | list | presenter | 6 | 2 | S | 237 | 1 | yes | Local list editor; inflight in data_grid2. |
| `PropertyListPresenter` | list | presenter | 8 | 4 | L | 632 | 4 | yes | In flight in data_grid2 - wait. Also bound in MonitoringModule; the edit flow and inherited-value display are the tricky parts. |
| `SolrIndexFieldListPresenter` | tab | presenter | 8 | 3 | M | 495 | 3 | yes | Fields tab, in flight in data_grid2. |
| `JobNodeListHelper` | list | smart class | 8 | 3 | M | 536 | 2 | yes | Shared by NodeJobListPresenter and the job's node list. Being changed in worktree-data_grid2. Split column building (dumb) from the job-node actions… |
| `FieldListPresenter` | tab | presenter | 8 | 3 | M | 349 | 1 | yes | Fields tab of the receipt rules; in flight in data_grid2. |
| `FeedDependencyListPresenter` | list | presenter | 8 | 2 | S | 258 | 1 | yes | Local list editor, no REST; inflight in data_grid2. |
| `JobNodeListPresenter` | list | presenter | 8 | 4 | L | 389 | 1 | yes | A 389-line job-node list with schedule editing and auto refresh. Being changed in worktree-data_grid2. |
| `PipelineReferenceListPresenter` | list | presenter | 8 | 3 | M | 537 | 1 | yes | In flight in data_grid2 - wait. Add/Edit/Remove buttons suit a RuleAction-style action enum. |
| `ConstraintListPresenter` | list | presenter | 8 | 3 | M | 369 | 1 | yes | Being changed in worktree-data_grid2 (the DataGrid move); wait for that before splitting. |
| `AbstractNotificationListPresenter` | list | presenter | 9 | 3 | M | 453 | 3 | yes | In flight in worktree-data_grid2 (grid rework), so wait for it. Edits the document's own notification list locally rather than loading it by REST. |
| `NodeJobListPresenter` | list | presenter | 9 | 3 | M | 330 | 1 | yes | Comes after JobNodeListHelper. Being changed in worktree-data_grid2. |
| `IndexFieldListPresenter` | list | presenter | 9 | 3 | M | 367 | 1 | yes | The field list tab, with add, edit and delete. Being changed in worktree-data_grid2. |
| `PathwayListPresenter` | list | presenter | 10 | 3 | M | 349 | 1 | yes | Being changed in worktree-data_grid2. A list with a quick filter plus edit and delete flows. |
| `AbstractMetaListPresenter` | list | presenter | 11 | 5 | L | 986 | 4 | yes | 986-line base of the stream lists; in flight in data_grid2. Split the grid columns (dumb) from the delete/restore/reprocess/download actions (controller). |
| `CredentialsListPresenter` | list | presenter | 12 | 3 | M | 443 | 3 | yes | In flight in worktree-data_grid2 (DataGridWidget rename), so wait. Good candidate for the RuleAction pattern: setOnAction(Consumer<CredentialAction>) +… |
| `MetaListPresenter` | list | presenter | 12 | 2 | S | 112 | 2 | yes | Columns only; follows the base. In flight in data_grid2. |
| `SteppingMetaListPresenter` | list | presenter | 12 | 2 | S | 125 | 2 | yes | Stepping stream chooser; follows the base. In flight in data_grid2. |
| `MetaRelationListPresenter` | list | presenter | 12 | 2 | M | 234 | 1 | yes | Tree of related streams; the agreed meta browser lineage design replaces it with a processing tree, so convert minimally or skip. In flight in data_grid2. |

## 11. Larger dialogs (REST-backed, owning lists or forms)

Split the dialogs that call REST, own lists or forms, or make further dialogs into a component dialog plus a controller. Results go back through callbacks, the controller supplies the task monitor from context, and the identity flows hang off ChangePasswordPresenter.

**Why now.** Their embedded lists (wave 10), forms and pickers (waves 7-8) and small child dialogs (wave 9) are components now. DocumentUserPermissionsPresenter (8 callers) is the permissions tab that most document editors need in wave 14.

**Risks.**

- Review by area: identity, explorer and import/export, volumes/nodes/processing, security/permissions, annotations, query/dashboard dialogs, floor map.
- The identity screens run before the main app has started (login, reset password). Test them outside the workbench shell as well.
- The UiHandlers that carry a TaskMonitorFactory (permissions edits) must take the task monitor from context. Watch that the spinners still show.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ActivityEditPresenter` | dialog | presenter | 0 | 3 | M | 453 | 3 |  | The edit form is admin-supplied HTML whose inputs are read back from the DOM. Keep the HTML host as a dumb widget and move validation and REST into a… |
| `ChangePasswordPresenter` | dialog | presenter | 2 | 2 | M | 342 | 6 |  | Shared by login, reset, edit-account and current-password flows (fan_in 6); convert early as a dialog component + controller. |
| `DocumentAssetUploadFileDialogPresenter` | dialog | presenter | 2 | 2 | S | 265 | 4 |  | Shared by DocumentAssetPresenter and DocumentAssetQuickUploadPresenter. |
| `ResultStoreSettingsPresenter` | dialog | presenter | 2 | 2 | S | 319 | 3 |  | A simple form dialog: a ComponentDialog with a callback, and the REST save moves to the opener's controller. |
| `CreateExternalUserPresenter` | composite | presenter | 2 | 2 | S | 273 | 3 |  | Body of the Create User dialog. The form is dumb; the REST create moves to the CreateUser controller. |
| `CreateMultipleUsersPresenter` | composite | presenter | 2 | 2 | S | 223 | 3 |  | Form with a single TextArea. Becomes a trivial component once the REST call moves out. |
| `UserTaskManagerPresenter` | dialog | presenter | 2 | 3 | M | 305 | 3 |  | The timer must stop on dispose. Do it after UserTaskPresenter. |
| `ImportConfigPresenter` | dialog | presenter | 2 | 2 | S | 182 | 3 |  | Upload dialog; then fires an app-wide event to open the confirm dialog. |
| `FindInContentPresenter` | dialog | presenter | 2 | 4 | L | 514 | 3 | yes | Search box + results + editor preview with debounce timers; inflight in data_grid2. Wait for EditorPresenter to be a component. |
| `AuthenticationErrorPresenter` | screen | presenter | 2 | 1 | S | 158 | 3 |  | Sign-in error page; trivial. |
| `EmailResetPasswordPresenter` | dialog | presenter | 2 | 1 | S | 223 | 3 |  | Single-field dialog with one REST call. |
| `FloorMapInitViewImpl` | composite | tidy | 2 | 1 | S | 96 | 1 |  | View half only. Its slots will hold DropDownBox once the doc selection boxes are split. |
| `AbstractFindPresenter` | dialog | presenter | 3 | 3 | M | 301 | 7 |  | Base of Find and Recent Items; split once into a FindDialog component (QuickFilter + list slot) and a base controller, then both subclasses follow. |
| `QuickFilterDialogViewImpl` | dialog | presenter | 3 | 1 | S | 178 | 3 |  | Near-duplicate of the page view with an sr-only status label; consider merging both into one component with a 'dialog' style option when split. |
| `DocumentUserCreatePermissionsEditPresenter` | dialog | presenter | 3 | 3 | M | 394 | 3 |  | Do it after DocumentCreatePermissionsListPresenter. |
| `CurrentPasswordPresenter` | dialog | presenter | 3 | 2 | S | 277 | 3 |  | Current password prompt; after ChangePassword. |
| `EditAccountPresenter` | dialog | presenter | 3 | 3 | M | 675 | 3 |  | Account form dialog with validation and nested REST; pilot-like split (component + controller). |
| `LoginPresenter` | screen | presenter | 3 | 2 | M | 449 | 3 |  | Sign-in page; runs in the separate ChangePasswordGinjector app. SignInPagePresenter is the shared page base for Login, ResetPassword and AuthenticationError. |
| `ResetPasswordPresenter` | screen | presenter | 3 | 1 | S | 199 | 3 |  | Mostly a controller that shows ChangePassword; the view is nearly empty. |
| `DocumentAssetQuickUploadPresenter` | dialog | presenter | 3 | 2 | S | 229 | 1 |  | Already shaped like a controller (it has no widget). It only needs renaming to ...Controller and to use its owner instead of AppRoot. |
| `UserRefPopupPresenter` | dialog | presenter | 4 | 3 | M | 356 | 7 | yes | The user picker behind UserRefSelectionBoxPresenter (fan-in 7). Its grid is in flight in data_grid2; once DataGrid settles, split it into a dumb picker list… |
| `ColumnValuesFilterPresenter` | dialog | presenter | 4 | 3 | M | 677 | 7 | yes | Column value pick-list popup; in flight in data_grid2 (DataGrid rename), so wait. The abstract data supplier already decouples REST and is the natural… |
| `ExportConfigPresenter` | dialog | presenter | 4 | 3 | M | 354 | 3 |  | App-wide dialog; depends on the explorer check tree. |
| `ChangeStatusPresenter` | dialog | presenter | 4 | 2 | S | 253 | 3 |  | The bulk change-status dialog. ChooserPresenter must be split first. |
| `FindPresenter` | dialog | presenter | 4 | 2 | S | 96 | 2 |  | Thin subclass; converts with AbstractFindPresenter. |
| `RecentItemsPresenter` | dialog | presenter | 4 | 2 | S | 131 | 2 |  | Thin subclass plus RecentItems store; converts with AbstractFindPresenter. |
| `AiAttachmentDataPresenter` | dialog | presenter | 4 | 2 | S | 150 | 1 | yes | Attachment data preview grid dialog; in flight in data_grid2. |
| `UserAndGroupHelper` | model-or-util | smart class | 5 | 3 | M | 365 | 7 | yes | Shared by 7 user/permission lists. Split it into a pure menu-model builder plus actions that live in the controllers, and give its dialogs an owner. In flight… |
| `ExplorerNodeRemoveTagsPresenter` | dialog | presenter | 5 | 2 | S | 283 | 4 |  | Small sibling of the edit tags dialog; do them together. |
| `ResultStorePresenter` | dialog | presenter | 5 | 2 | S | 203 | 3 |  | A small dialog screen. It follows ResultStoreListPresenter. |
| `ExplorerNodeEditTagsPresenter` | dialog | presenter | 5 | 3 | M | 729 | 3 |  | Large view holds tag list behaviour (TagListModel); keep that in the component, move REST to the controller. |
| `AiChatHistoryPresenter` | dialog | presenter | 5 | 2 | M | 501 | 3 | yes | Chat history picker dialog; in flight in data_grid2. Remove the commented-out code during conversion. |
| `ChangeAssignedToPresenter` | dialog | presenter | 5 | 2 | S | 266 | 3 |  | The bulk 'change assigned to' dialog. It shares SettingBlock and AssignYourselfLink with the annotation editor, so do it after those are tidied. |
| `ConfigPropertyClusterValuesPresenter` | dialog | presenter | 5 | 1 | S | 171 | 3 |  | A dialog wrapped round the list. |
| `ManageActivityPresenter` | dialog | presenter | 5 | 3 | M | 474 | 3 |  | The activity chooser dialog. ManageActivityViewImpl injects the EventBus (for the quick filter context); that has to go. |
| `FindAnnotationPresenter` | dialog | presenter | 5 | 2 | S | 125 | 2 |  | The Find dialog. Split it with the shared Find view, which is in another partition. |
| `FloorMapInitPresenter` | dialog | presenter | 6 | 3 | M | 364 | 4 |  | Store pickers should become template DocSelectionBoxes with attached DocSelectionControllers (the stream-destination pattern). |
| `ColumnFunctionEditorPresenter` | dialog | presenter | 6 | 3 | M | 276 | 4 |  | Expression editor dialog with query help side panel; QueryHelpPresenter is big and smart, so the dialog needs a controller for the REST validate step and must… |
| `ManageGlobalPropertyEditPresenter` | dialog | presenter | 6 | 4 | M | 850 | 3 | yes | An edit dialog with several REST flows. GlobalPropertyEditViewImpl is being changed in worktree-copy_and_paste. |
| `FloorMapClusterPresenter` | dialog | presenter | 7 | 3 | M | 454 | 4 | yes | In flight in worktree-data_grid2; no REST, so the split is mostly moving grid + filters into a component. |
| `FloorMapLayerStylePresenter` | dialog | presenter | 7 | 3 | M | 262 | 4 |  | Depends on the document-asset picker split; the asset drop-down is a smart widget like DocSelectionBox. |
| `CreateUserPresenter` | dialog | presenter | 7 | 3 | M | 307 | 4 |  | Do it after its three form bodies. It becomes a dialog component whose type SelectionBox swaps between child components. |
| `DocumentUserPermissionsEditPresenter` | dialog | presenter | 7 | 3 | M | 384 | 4 |  | Shown from DocumentUserPermissionsPresenter and UserPermissionReportPresenter. |
| `BatchDocumentPermissionsEditPresenter` | dialog | presenter | 7 | 3 | M | 451 | 4 |  | Once both selection boxes are dumb DropDownBoxes in its template, this becomes a template-only dialog plus a controller. |
| `NodeGroupEditPresenter` | dialog | presenter | 7 | 3 | M | 359 | 3 |  | A dialog with an embedded list. Rename the view's setEnabled to something like setGroupEnabled when it becomes a component. |
| `HttpTlsConfigPresenter` | dialog | presenter | 7 | 2 | S | 386 | 3 |  | Already takes read-only from context; a straightforward dialog split. |
| `BatchProcessorFilterEditPresenter` | dialog | presenter | 7 | 3 | M | 304 | 3 |  | Waits for the user picker to be split like DocSelectionBox. |
| `FsVolumeEditPresenter` | dialog | presenter | 7 | 2 | M | 318 | 3 |  | The form is dumb; the config code editor and the save become controller work. |
| `IndexVolumeEditPresenter` | dialog | presenter | 7 | 2 | S | 323 | 3 |  | A small form dialog. Its node SelectionBox gets filled by REST in the controller. |
| `CopyDocumentPresenter` | dialog | presenter | 7 | 2 | S | 253 | 3 |  | A small dialog; the entity tree picker has to be a component first. |
| `CreateDocumentPresenter` | dialog | presenter | 7 | 2 | S | 309 | 3 |  | Same shape as Copy and Move; do the three together. |
| `MoveDocumentPresenter` | dialog | presenter | 7 | 2 | S | 210 | 3 |  | Same shape as Copy and Create. |
| `FloorMapClusterViewImpl` | composite | tidy | 7 | 2 | S | 200 | 1 |  | View half only; its presenter is in another partition and this becomes the dumb component when it is split. |
| `DocumentUserPermissionsPresenter` | tab | presenter | 8 | 3 | M | 418 | 8 |  | The Permissions tab on every document, added through DocumentUserPermissionsTabProvider (fan-in 29). Convert it with the DocumentEditor tab work. |
| `AnalyticNotificationEditPresenter` | dialog | presenter | 8 | 3 | M | 378 | 6 |  | Convert after AnalyticEmailDestinationPresenter. Already hosts the pilot's stream destination controller. |
| `HttpClientConfigPresenter` | dialog | presenter | 8 | 2 | S | 447 | 5 |  | Fan-in 5 (HTTP-using document settings); convert after HttpTlsConfigPresenter. |
| `ProcessorProfileEditPresenter` | dialog | presenter | 8 | 3 | M | 305 | 3 |  | Do after ProfilePeriodListPresenter. |
| `UserPreferencesPresenter` | dialog | presenter | 8 | 3 | M | 361 | 3 |  | Tabbed dialog; a TabbedComponent candidate once its four tabs are components. |
| `AskStroomAiConfigPresenter` | dialog | presenter | 8 | 3 | M | 296 | 3 |  | Tabbed config dialog; make the tabs components and use TabbedComponent; a controller does default load/save. |
| `FsVolumeGroupEditPresenter` | dialog | presenter | 8 | 3 | M | 367 | 3 |  | Convert after FsVolumeStatusListPresenter and FsVolumeEditPresenter. |
| `SteppingFilterPresenter` | dialog | presenter | 8 | 3 | M | 474 | 3 | yes | The element filter dialog with its XPath filter list. Being changed in worktree-data_grid2. |
| `IndexVolumeGroupEditPresenter` | dialog | presenter | 8 | 3 | M | 378 | 3 |  | The group editor dialog, with a nested volume list and the volume edit dialog. |
| `AnnotationTagEditPresenter` | dialog | presenter | 9 | 2 | M | 316 | 4 |  | A small dialog that also opens the permissions dialog. Its style SelectionBox renders swatches. |
| `PathwayEditPresenter` | dialog | presenter | 9 | 3 | M | 390 | 3 |  | Composes two child presenters; split it after PathwayTreePresenter and ConstraintListPresenter. |
| `ReplaceSecretPresenter` | dialog | presenter | 10 | 2 | S | 286 | 3 |  | Shares the expiry + secret-slot layout with CredentialSettings; consider one shared expiry/secret component. |
| `FloorMapObjectEditPresenter` | dialog | presenter | 10 | 4 | L | 756 | 3 |  | Geometry/validation logic belongs in a model or the controller; the form itself is dumb. |
| `EditApiKeyPresenter` | dialog | presenter | 10 | 3 | M | 635 | 3 | yes | Waits for the UserRef box split. EditApiKeyViewImpl is in flight in copy_and_paste. |
| `DataUploadPresenter` | dialog | presenter | 10 | 2 | M | 291 | 3 |  | Form submit plus stream type fetch; ImportConfigPresenter has the same upload shape, so do them together. |
| `ImportConfigConfirmPresenter` | dialog | presenter | 10 | 4 | L | 720 | 3 | yes | 578 lines with a grid of import states; in flight in data_grid2. |
| `BatchExecutionScheduleEditPresenter` | dialog | presenter | 10 | 3 | M | 543 | 3 |  | 355-line view with an enable box per field. The view must stop injecting a presenter. The user picker box (another partition) must be split first. |
| `ScheduledProcessEditPresenter` | dialog | presenter | 10 | 3 | M | 585 | 3 |  | Uses the same pickers as BatchExecutionScheduleEdit. Convert both together, after the user and doc pickers are split. |
| `AnnotationTagCreatePresenter` | dialog | presenter | 10 | 2 | S | 221 | 3 |  | A name and description dialog that goes on to the edit dialog. |
| `FloorMapObjectEditViewImpl` | composite | tidy | 10 | 3 | M | 725 | 1 |  | View half only. A 725-line form; the DateTimeBox popup provider should stop being injected (DateTimeBox should make its own popup). |
| `CredentialEditPresenter` | dialog | presenter | 11 | 2 | M | 233 | 1 |  | Replace the hand-made tab map with TabbedComponent; becomes CredentialEditDialog component plus controller handling OK (validate, store, close). |
| `CredentialsManagerDialogPresenter` | dialog | presenter | 13 | 2 | S | 267 | 3 |  | Injected unowned into ContentStoreContentPackDetailsPresenter; give it a show(label, initial, Consumer<String> onOk) so callers stop building its popup. |
| `QueryFavouritesPresenter` | dialog | presenter | 13 | 3 | M | 312 | 3 | yes | In flight in worktree-data_grid2. Replace the QueryPresenter reference with a result callback. |
| `QueryHistoryPresenter` | dialog | presenter | 13 | 2 | M | 246 | 3 | yes | In flight in worktree-data_grid2. |
| `AdvancedQuickFilterPresenter` | dialog | presenter | 14 | 2 | S | 308 | 3 |  | View is already tiny (message + expression slot); split is mechanical once EditExpressionPresenter can sit in a slot. |
| `DataRetentionRulePresenter` | dialog | presenter | 14 | 2 | S | 255 | 3 |  | Same shape as the pilot's RuleEditor; copy that split. |
| `ContentTemplateEditPresenter` | dialog | presenter | 14 | 3 | M | 372 | 3 |  | Use the DropDownBox + DocSelectionController attach pattern for the pipeline picker. |
| `RulePresenter` | dialog | presenter | 14 | 2 | M | 431 | 3 |  | Rule edit dialog; analogous to the receive rules RuleEditor pilot. Convert after EditExpressionPresenter and CustomRowStylePresenter. |
| `SelectionHandlerPresenter` | dialog | presenter | 14 | 2 | S | 195 | 3 |  | Dialog for editing one selection handler. EditExpressionPresenter (another partition) converts first. |

## 12. Tabs and composite panes (excluding query and dashboard)

Convert document tabs and large composite panes into components with controllers, removing the dependency on DocPresenter: data, source and stepping; pipeline structure; annotations; processing and notifications; Plan B settings; user and permissions tabs; retention and processor tabs. Hand-rolled tabs become TabbedComponent.

**Why now.** Everything these tabs embed (lists, forms, the editor, trees and dialogs) is converted. The document editors in wave 14 need their tab bodies as components first.

**Risks.**

- Several XL units are carved into sub-components before the split: AnnotationEditPresenter (2,202 lines, in flight in copy_and_paste), DataPresenter (1,622), SteppingPresenter (1,326), PipelineStructurePresenter (1,001) and DocumentAssetPresenter (1,156).
- These tabs are still hosted by DocTabPresenter until wave 14. Dirty state and read-only must come through Contexts in both hosts.
- AbstractNotification, AbstractProcessing and AbstractDuplicateManagement are thin base and subclass pairs. Merge them into one component used by both documents rather than carrying the inheritance over.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `UserInfoPresenter` | tab | presenter | 2 | 2 | S | 223 | 3 |  | Small form. Fix the double-inverted read-only flag while splitting, because a dumb setReadOnly must mean read-only. |
| `ElementPresenter` | tab | presenter | 2 | 4 | L | 905 | 3 |  | The per-element code, input, output and log editors with their indicators. The editors must be components first. |
| `FloorMapSettingsViewImpl` | tab | tidy | 2 | 2 | S | 185 | 1 |  | View half only. Its slots receive doc selection boxes, a value format widget and a schema grid. |
| `DocumentAssetPresenter` | tab | presenter | 3 | 5 | XL | 1156 | 9 |  | Assets tab used by 9 document types: over 1000 lines with draft/live state. Do it last in this area, after the tree and the dialogs. |
| `ProcessorTaskPresenter` | tab | presenter | 5 | 1 | S | 191 | 3 |  | Used both as a screen and as a feed/pipeline tab. A thin composite: convert it after its lists. |
| `AbstractDuplicateManagementPresenter` | tab | presenter | 5 | 2 | S | 293 | 3 |  | Three tick boxes and a text box above the duplicate list; the view is already dumb. Remove the abstract base (it has only one subclass) when converting. |
| `ImportsTab` | tab | smart class | 5 | 2 | S | 197 | 1 |  | Has a template already; split into ImportsTab component (slots for the two lists, setOnAction) and ImportsTabController, mirroring the receipt-rules pilot. |
| `AnnotationLinkPresenter` | tab | presenter | 5 | 2 | S | 175 | 1 |  | The linked-to and linked-from tab (two instances). It shares the Find view with BrowseAnnotation and FindAnnotation, so split all three together. |
| `ScriptSettingsPresenter` | tab | presenter | 6 | 1 | S | 113 | 3 |  | A thin wrapper; fold it into the dependency list component. |
| `FloorMapSettingsPresenter` | tab | presenter | 7 | 4 | L | 672 | 3 | yes | In flight in worktree-data_grid2; widgets built in the presenter should move into the component template. |
| `UserAndGroupsPresenter` | tab | presenter | 8 | 4 | L | 715 | 4 |  | Both a screen and the User Groups tab of UserTabPresenter. The membership logic belongs in its controller. |
| `ElasticClusterSettingsPresenter` | tab | presenter | 9 | 3 | M | 318 | 3 |  | A typical settings tab, like RuleSetSettings. HttpClientConfigPresenter should be converted first. |
| `FeedDependencyPresenter` | composite | presenter | 9 | 2 | S | 213 | 3 |  | Dialog wrapping the dependency list and delay settings; no REST. |
| `GitRepoSettingsPresenter` | tab | presenter | 9 | 3 | M | 801 | 3 |  | Several REST flows (pull/push/update); split view into component, keep flows in controller. |
| `AnnotationEditPresenter` | tab | presenter | 9 | 5 | XL | 2202 | 3 | yes | A 1728-line presenter and a 428-line view hold the timeline, six setting choosers and the comment editor. Split the timeline and each setting's chooser flow… |
| `PipelineStructurePresenter` | tab | presenter | 9 | 5 | XL | 1001 | 3 |  | Core of the pipeline editor; convert last in this area, after the tree, both lists and the three dialogs. Inherited/added element state and the model's edit… |
| `OpenAIModelSettingsPresenter` | tab | presenter | 9 | 3 | M | 403 | 3 |  | A settings form that loads the credential list and opens an HTTP client config dialog. |
| `AbstractNotificationPresenter` | tab | presenter | 10 | 1 | S | 211 | 4 |  | Pass-through tab around the notification list. Once the list is a component, add the list as the tab and remove this. |
| `PlanBSettingsPresenter` | tab | presenter | 10 | 3 | M | 441 | 4 |  | Do after the 8 type-settings parts are dumb; the instanceof carry-over of common values across a type change should become plain data in the controller. Two… |
| `ScheduledProcessingPresenter` | composite | presenter | 11 | 3 | M | 270 | 8 |  | Fan-in 8: other documents' processing tabs use it. Convert after its two lists and its edit dialog. |
| `PathwaysSplitPresenter` | tab | presenter | 11 | 2 | S | 134 | 3 |  | A thin split layout; trivial once both children are components. |
| `TracesListTabPresenter` | tab | presenter | 11 | 3 | M | 269 | 3 | yes | Being changed in worktree-data_grid2. Coordinates list selection, the time range and loading a trace. |
| `TracesSettingsPresenter` | tab | presenter | 11 | 1 | S | 134 | 3 |  | A thin wrapper round PlanBSettingsPresenter; convert it after that. |
| `DataGenProcessingPresenter` | tab | presenter | 12 | 1 | S | 129 | 3 |  | Thin host over ScheduledProcessingPresenter. |
| `SolrIndexSettingsPresenter` | tab | presenter | 14 | 3 | M | 481 | 3 |  | Settings tab. As in the pilot, its template can hold a DropDownBox with a DocSelectionController. |
| `ProcessorEditPresenter` | dialog | presenter | 14 | 4 | L | 716 | 3 |  | Big form dialog; do after its embedded pickers and the feed dependency dialog. |
| `DataRetentionImpactPresenter` | tab | presenter | 14 | 3 | M | 1026 | 1 | yes | Row and tree-action classes are pure model (600 of the lines). In flight in data_grid2. |
| `ProcessorPresenter` | tab | presenter | 15 | 4 | L | 592 | 5 |  | Processors tab of pipelines/feeds; top of the processor stack. |
| `UserPermissionReportPresenter` | tab | presenter | 15 | 3 | M | 383 | 4 |  | Both the Document Permissions tab of UserTabPresenter and the report screen. |
| `DataRetentionPolicyPresenter` | tab | presenter | 15 | 4 | M | 691 | 4 |  | Same pattern as RuleSetSettings; reuse its action-enum approach. |
| `StreamingProcessingPresenter` | composite | presenter | 16 | 2 | S | 147 | 5 |  | Thin wrapper around ProcessorPresenter (another partition) plus one REST call. Its owner could take it over. |
| `AbstractProcessingPresenter` | tab | presenter | 17 | 3 | M | 399 | 4 |  | Switches its body between three smart presenters by process type. Convert after the Scheduled, TableBuilder and Streaming processing parts. |

## 13. Query and dashboard stack

Rename the dashboard Component interface to DashboardPane, give panes and their settings a component-model base, then convert bottom-up through this stack: settings tabs, TextInput, KeyValueInput and ListInput panes, Vis, table rules and filters, ColumnsManager, TablePresenter, QueryResultTable, QueryResultTableSplit, the query-data tabs, QueryEdit, QueryDocEdit, then DashboardPresenter and EmbeddedQuery. The model pushes data through setData and setSearching, not ResultComponent.

**Why now.** This is the deepest stack (levels 2-27) and depends on almost everything below it: the expression tree, the lists, the column dialogs and the editor. It is also the most entangled with in-flight work, so it comes last before the document editors.

**Risks.**

- TablePresenter (1,762 lines), QueryResultTablePresenter (2,078), ColumnsManager (1,223), QueryPresenter and the floor map map and editor presenters (about 2,200 lines each) are XL and in flight in data_grid2 and copy_and_paste. Each needs sub-components carved out first.
- The name clash between DashboardPane and Component must be resolved before any pane extends the new Component.
- MultiRulesPresenter uses makeDialog for children it embeds in a panel. Change these to make(...) and own them while converting.
- The floor map map and editor presenters call static helpers on FloorMapQueryPresenter. Move those helpers to a plain utility class first.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `BasicSettingsTabPresenter` | framework | presenter | 0 | 2 | M | 133 | 9 |  | Base for every dashboard settings tab. Define a component base first (read/write ComponentConfig, setDashboardContext); the settings tabs then follow. |
| `Splitter` | widget | tidy | 0 | 2 | S | 229 | 3 |  | FlexLayout's internal splitter. It is fine as an internal helper; tidy it only alongside FlexLayout. |
| `TableRowCell` | cell | tidy | 0 | 1 | S | 56 | 2 | yes | Replace AnnotationManager with a Function<List<TableRow>, List<Item>> menu supplier. It is being changed in worktree-data_grid2. |
| `UnknownComponentPresenter` | tab | presenter | 0 | 1 | S | 126 | 1 |  | Placeholder dashboard component; follows the dashboard component base class. |
| `TabLayout` | widget | tidy | 2 | 3 | M | 186 | 7 |  | An inner part of FlexLayout. Tidy it with FlexLayout, sending the menu out by callback. |
| `BasicKeyValueInputSettingsPresenter` | tab | presenter | 2 | 1 | S | 159 | 3 |  | Dumb settings form. Easy once BasicSettingsTabPresenter has a component base. |
| `BasicTextInputSettingsPresenter` | tab | presenter | 2 | 1 | S | 178 | 3 |  | Dumb settings form. |
| `QueryResultTabsViewImpl` | composite | tidy | 2 | 1 | S | 90 | 1 |  | Make it a small dumb tabs component, or replace it with TabbedComponent. |
| `FloorMapEditorViewImpl` | composite | tidy | 2 | 2 | S | 196 | 1 |  | View half only. Pure layout of nested SplitPanes holding canvas, dock, timeline, fact and time lists. |
| `FloorMapMapViewImpl` | composite | tidy | 2 | 1 | S | 87 | 1 |  | View half only; pure layout. |
| `SettingsPresenter` | dialog | presenter | 3 | 2 | M | 149 | 11 |  | Generic tabbed settings dialog for panes (fan-in 11), much like a TabbedComponent. Convert it with BasicSettingsTabPresenter. |
| `FlexLayout` | widget | tidy | 3 | 5 | XL | 2498 | 6 | yes | The 1963-line dashboard layout engine. Replacing the bus with a setOnShowMenu callback makes it a dumb widget, but its size and drag logic make it XL to… |
| `AbstractComponentPresenter` | framework | presenter | 4 | 3 | L | 314 | 10 |  | Base of every dashboard pane (fan-in 10). Needs a component-model version (e.g. a DashboardPane base extending Component) before any pane can convert. |
| `TabManager` | framework | smart class | 4 | 3 | M | 386 | 3 |  | Context-menu logic for the dashboard layout's tabs. Becomes a helper given callbacks instead of the presenter. |
| `KeyValueInputSettingsPresenter` | dialog | presenter | 4 | 1 | S | 36 | 1 |  | Only adds a tab. |
| `TextInputSettingsPresenter` | dialog | presenter | 4 | 1 | S | 36 | 1 |  | Only adds a tab. |
| `DataPresenter` | composite | presenter | 5 | 5 | XL | 1622 | 8 |  | 1474 lines; the data viewer embedded in MetaPresenter, preview tabs and dialogs. Convert last in the data area, after all its children. |
| `QueryResultVisPresenter` | composite | presenter | 5 | 4 | L | 882 | 6 | yes | Overlaps with the vis modernisation and the dashboard VisPresenter. QueryResultVisViewImpl is being changed in worktree-data_grid2. Do the vis host component… |
| `KeyValueInputPresenter` | tab | presenter | 5 | 2 | S | 245 | 4 |  | Simple dashboard pane; a good first pane once the pane base exists. |
| `TextInputPresenter` | tab | presenter | 5 | 1 | S | 233 | 3 |  | The simplest dashboard pane (one text box). Use it as the pilot for the pane base. |
| `AnnotationManager` | plugin-or-infrastructure | smart class | 6 | 3 | M | 442 | 3 |  | Already a controller with no widget of its own. Converting it mostly means routing dialogs through the owner's makeDialog and giving its REST calls an owner.… |
| `BasicQuerySettingsPresenter` | tab | presenter | 6 | 2 | M | 284 | 3 |  | The data source picker becomes a DropDownBox plus a DocSelectionController. Convert after the BasicSettingsTabPresenter base. |
| `BasicListInputSettingsPresenter` | tab | presenter | 6 | 2 | S | 276 | 3 |  | The dictionary picker becomes a DropDownBox plus a DocSelectionController. |
| `LinkedEventPresenter` | tab | presenter | 6 | 3 | M | 288 | 3 | yes | A list of linked events above a DataPresenter in a SplitPane. Its grid is being changed in worktree-data_grid2. |
| `BasicEmbeddedQuerySettingsPresenter` | tab | presenter | 6 | 3 | M | 479 | 3 |  | A dashboard component's settings tab. Depends on the BasicSettingsTabPresenter base and on the doc selection split. |
| `BasicTextSettingsPresenter` | tab | presenter | 7 | 3 | M | 595 | 3 |  | Depends on the shared BasicSettingsTabPresenter base (other partition) being split first; pipeline picker should become a template DocSelectionBox +… |
| `BasicTableSettingsPresenter` | tab | presenter | 7 | 3 | M | 604 | 3 |  | Table component settings tab; once DocSelectionBox is split use DropDownBox + DocSelectionController in the template. Depends on the shared… |
| `TableFilterPresenter` | dialog | presenter | 7 | 2 | S | 134 | 2 |  | Tabbed filter dialog; convert to a TabbedComponent after its two tabs. |
| `ListInputSettingsPresenter` | dialog | presenter | 7 | 1 | S | 36 | 1 |  | Only adds a tab. |
| `FloorMapQueryViewImpl` | composite | tidy | 7 | 2 | S | 176 | 1 |  | View half only. |
| `ListInputPresenter` | tab | presenter | 8 | 2 | M | 419 | 4 |  | Loading the dictionary is controller work; the view is one SelectionBox. |
| `TextSettingsPresenter` | dialog | presenter | 8 | 1 | S | 36 | 1 |  | Thin subclass of the dashboard SettingsPresenter; converts when that base does. |
| `SourcePresenter` | composite | presenter | 9 | 4 | L | 1057 | 7 |  | 900 lines of fetch/paging/state for the source view; split after its navigator, text and classification parts. |
| `TextPresenter` | composite | presenter | 9 | 4 | L | 904 | 3 |  | A dashboard component on AbstractComponentPresenter, so it moves with the dashboard component framework; the view is small but the presenter has the… |
| `BasicVisSettingsPresenter` | tab | presenter | 11 | 3 | M | 433 | 3 |  | Reaches up into its parent settings presenter to add tabs; give it a callback, or let the VisSettings controller manage the dynamic tabs. |
| `FloorMapEditorPresenter` | tab | presenter | 12 | 5 | XL | 2206 | 8 |  | Convert last in floormap, after every child; the canvas/canvas-presenter static coupling (MapContextMenu, editor references in the canvas) needs untangling… |
| `FloorMapMapPresenter` | tab | presenter | 12 | 5 | XL | 2196 | 8 |  | Heaviest controller in the partition; move query parsing statics out of FloorMapQueryPresenter into a model class before splitting either. |
| `DashboardPresenter` | tab | presenter | 12 | 5 | XL | 1176 | 5 |  | 1105 lines; the dashboard shell. Convert it last, after the panes, their settings and TabManager. |
| `VisSettingsPresenter` | dialog | presenter | 12 | 1 | S | 37 | 1 |  | Thin; converts with the dashboard SettingsPresenter base. |
| `VisPresenter` | composite | presenter | 13 | 5 | XL | 1010 | 3 | yes | VisViewImpl is in flight in data_grid2. Shares VisFrame/VisToolbarRow with QueryResultVisPresenter - convert the frame first, then both hosts. |
| `ViewSettingsPresenter` | tab | presenter | 14 | 2 | M | 209 | 3 |  | Two attached pickers plus the expression editor in a slot. |
| `RulesPresenter` | tab | presenter | 15 | 3 | M | 496 | 7 |  | Conditional formatting rules tab; mirror of the receive rules Rules tab pilot (component + controller). |
| `MetaPresenter` | screen | presenter | 15 | 4 | L | 668 | 6 |  | The Data browser used on feed/pipeline/folder tabs; top of the data area, convert last. |
| `SelectionHandlersPresenter` | tab | presenter | 15 | 3 | M | 522 | 5 |  | Tab that edits a list (add, edit, copy, move). Similar to the receipt rules Rules tab pilot. |
| `SteppingPresenter` | screen | presenter | 15 | 5 | XL | 1326 | 3 |  | A 1249-line stepping screen that runs the step state machine. Split it last in this area, after its parts. |
| `MultiRulesPresenter` | tab | presenter | 16 | 2 | M | 219 | 4 |  | Conditional formatting rule editors, one per column. RulesPresenter (table partition) converts first. Make the embedded children with make, not makeDialog. |
| `TableSettingsPresenter` | dialog | presenter | 16 | 1 | S | 46 | 1 |  | 46-line tab container; follows the dashboard SettingsPresenter conversion. |
| `QuerySettingsPresenter` | dialog | presenter | 16 | 1 | S | 47 | 1 |  | Only adds two tabs; replace it with a settings component factory. |
| `EmbeddedQuerySettingsPresenter` | dialog | presenter | 16 | 2 | M | 104 | 1 |  | A tabbed settings dialog; it follows the dashboard SettingsPresenter base. |
| `QueryPresenter` | tab | presenter | 17 | 5 | XL | 1019 | 8 | yes | 925 lines, in flight in worktree-copy_and_paste. Convert after its dialogs and the dashboard pane base. |
| `BasicTableFilterSettingsPresenter` | tab | presenter | 17 | 2 | M | 317 | 3 |  | Convert after ColumnSelectionPresenter. |
| `TableFilterSettingsPresenter` | dialog | presenter | 18 | 1 | S | 42 | 1 |  | Only adds two tabs. |
| `TableFilterPresenter` | tab | presenter | 19 | 3 | M | 262 | 3 |  | Builds one filter for each column of a linked table. ColumnValuesFilterPresenter (table partition) converts first. |
| `ColumnsManager` | composite | smart class | 20 | 4 | L | 1223 | 1 | yes | 870-line column header menu/controller for the dashboard table; already a controller in spirit but entangled with TablePresenter; in flight in data_grid2.… |
| `TablePresenter` | screen | presenter | 21 | 5 | XL | 1762 | 14 | yes | 1472-line dashboard table component; in flight in data_grid2. Do it last in this area together with ColumnsManager and the dashboard component framework… |
| `ColumnHeaderCell` | cell | dumb | 21 | 2 | S | 283 | 1 | yes | Rendering-only cells. ColumnHeaderHtmlUtil is being changed in worktree-data_grid2; leave these alone until the DataGrid work lands. |
| `QueryResultTablePresenter` | list | presenter | 22 | 5 | XL | 2078 | 13 | yes | The largest unit in the partition and heavily reused (fan-in 13). It and QueryTableColumnsManager are being changed in worktree-data_grid2, so leave them… |
| `FloorMapEventStoreDataPresenter` | tab | presenter | 23 | 2 | S | 194 | 4 |  | Thin subclass; converts with the shared AbstractQueryDataPresenter base (other partition). |
| `AbstractQueryDataPresenter` | tab | presenter | 23 | 3 | M | 449 | 4 |  | A shared 'Data' tab base for three document types (IndexDataPresenter, SqlTemporalStoreDataPresenter, FloorMapEventStoreDataPresenter). Convert it to a… |
| `QueryResultTableSplitPresenter` | composite | presenter | 23 | 3 | M | 265 | 3 |  | A thin composite: convert it once its two children are components, or turn it into a template with a SplitPane. |
| `SqlTemporalStoreDataPresenter` | tab | presenter | 23 | 2 | S | 76 | 3 |  | Follows whatever happens to AbstractQueryDataPresenter (other partition). |
| `AnalyticDataShardsPresenter` | tab | presenter | 23 | 3 | M | 209 | 3 |  | Connects the shard selection to a results table. QueryResultTablePresenter (another partition) should convert first. |
| `QueryEditPresenter` | tab | presenter | 24 | 4 | L | 614 | 7 |  | The StroomQL editor body, also used by analytics/embedded query (fan-in 7). Convert it near the top, after its children, as a component plus a QueryController… |
| `IndexDataPresenter` | tab | presenter | 24 | 3 | M | 178 | 3 |  | Convert it together with its AbstractQueryDataPresenter siblings, which are in other partitions. |
| `PlanBDataPresenter` | tab | presenter | 24 | 2 | S | 127 | 3 |  | Only supplies the default query and preferred columns per state type (pure data); converts when AbstractQueryDataPresenter (outside this partition) does. |
| `FloorMapEventStoreDataViewImpl` | composite | tidy | 24 | 1 | S | 31 | 1 |  | View half only; follows whatever QueryDataViewImpl becomes. |
| `FloorMapQueryPresenter` | tab | presenter | 25 | 3 | M | 649 | 5 |  | Extract the static query/row parsing to a model class first. |
| `AbstractQueryEditPresenter` | tab | presenter | 25 | 2 | S | 170 | 2 |  | Thin adapter around QueryEditPresenter (another partition). Convert once QueryEditPresenter is a component. |
| `QueryDocEditPresenter` | tab | presenter | 25 | 4 | L | 497 | 1 |  | The document tab around QueryEditPresenter. Becomes a DocumentEditor part once QueryEditPresenter is a component. |

## 14. Document editors

Move every DocTabPresenter shell onto DocumentEditor<D> plus DocumentEditorHost, with TabbedComponent tabs (Markdown and permissions tabs as reusable tabs), then delete DocPresenter, DocTabPresenter and LinkTabPanelPresenter. RuleSetPresenter's hand-rolled tabs also move onto DocumentEditor.

**Why now.** Their tab bodies converted in waves 8, 12 and 13. The recipe is mechanical and shared, so S3ConfigPresenter, the smallest, goes first as the template. The old document framework is deleted only once no editor uses it.

**Risks.**

- The document plugins' createEditor() and OpenDocumentEvent carry presenters. They must accept DocumentEditorHost before the old bases are deleted.
- Deleting DocPresenter, DocTabPresenter and LinkTabPanelPresenter must wait for every in-flight worktree to land and for a full screenshot comparison (removals rule).
- PipelinePresenter and the dashboard and query document editors have permission-dependent tabs and multi-instance behaviour (HasMultipleInstances). Cover both with stories.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `DocPresenter` | framework | presenter | 0 | 3 | L | 370 | 81 |  | Base class and Has* interfaces for document tab presenters. It can only go once its 81 subclasses are components; keep it as an adapter during the migration. |
| `LinkTabPanelPresenter` | framework | presenter | 2 | 2 | S | 250 | 0 |  | Nothing uses LinkTabPanelPresenter except workbench stories, so it is probably dead. LinkTabPanelView/ViewImpl is the tab-bar layout every DocTabPresenter… |
| `DocTabPresenter` | framework | presenter | 4 | 4 | XL | 709 | 31 |  | Legacy document shell (fan-in 31) and its tab providers (DocTabProvider fan-in 25, MarkdownTabProvider 29, AbstractTabProvider 11). DocumentEditor +… |
| `VisualisationPresenter` | document-editor | presenter | 7 | 2 | M | 155 | 2 |  | Move to DocumentEditor<VisualisationDoc> with DocumentEditorHost once its tabs are ready. |
| `ElasticIndexPresenter` | document-editor | presenter | 8 | 1 | S | 87 | 2 |  | Becomes DocumentEditor<ElasticIndexDoc>. |
| `S3ConfigPresenter` | document-editor | presenter | 9 | 2 | S | 149 | 2 |  | Small document: config editor, documentation and permissions tabs. A good early DocumentEditor conversion. |
| `ScriptPresenter` | document-editor | presenter | 9 | 2 | M | 127 | 2 |  | A DocumentEditor conversion once the editor and settings tabs are components. |
| `DocumentationPresenter` | document-editor | presenter | 9 | 2 | S | 122 | 2 |  | A small DocumentEditor conversion. |
| `AnnotationPresenter` | document-editor | presenter | 10 | 3 | M | 177 | 4 |  | A five-tab DocTabPresenter that becomes a DocumentEditor once its tabs are components. An annotation isn't a stored doc, so its Save differs from other editors. |
| `ElasticClusterPresenter` | document-editor | presenter | 10 | 1 | S | 84 | 2 |  | Becomes DocumentEditor<ElasticClusterDoc> after its settings tab is converted. |
| `GitRepoPresenter` | document-editor | presenter | 10 | 2 | S | 83 | 2 |  | Becomes a DocumentEditor with tabs. |
| `TextConverterPresenter` | document-editor | presenter | 10 | 2 | M | 159 | 2 |  | The editor's mode depends on the settings tab's converter type; wire that as a callback between tabs. |
| `XsltPresenter` | document-editor | presenter | 10 | 2 | S | 113 | 2 |  | Simple editor document; mechanical DocumentEditor conversion once EditorPresenter can be a tab. |
| `XMLSchemaPresenter` | document-editor | presenter | 10 | 3 | M | 269 | 2 |  | Graphical and text tabs share the schema data; keep the debounce timer in the controller and cancel it on dispose. |
| `OpenAIModelPresenter` | document-editor | presenter | 10 | 1 | S | 84 | 2 |  | Becomes a DocumentEditor<OpenAIModelDoc>. |
| `KafkaConfigPresenter` | document-editor | presenter | 10 | 2 | S | 149 | 2 |  | A small DocTabPresenter with an Ace editor tab and a download button; a good early DocumentEditor conversion once EditorPresenter is a component. |
| `TracesPresenter` | document-editor | presenter | 12 | 2 | M | 114 | 3 |  | Becomes a DocumentEditor<TracesDoc>. |
| `PathwaysPresenter` | document-editor | presenter | 12 | 2 | M | 88 | 2 |  | Becomes a DocumentEditor<PathwaysDoc> once its tabs are components; follow the Dictionary editor. |
| `DataGenPresenter` | document-editor | presenter | 13 | 2 | S | 93 | 2 |  | Phase 9 DocumentEditor conversion; mechanical. |
| `DashboardSuperPresenter` | document-editor | presenter | 13 | 2 | M | 178 | 2 |  | Becomes a DocumentEditor<DashboardDoc>. Meanwhile it can keep DashboardPresenter as a presenter tab. |
| `SolrIndexPresenter` | document-editor | presenter | 15 | 2 | S | 87 | 2 |  | Becomes DocumentEditor<SolrIndexDoc> once its tabs are done. |
| `ViewPresenter` | document-editor | presenter | 15 | 1 | S | 84 | 2 |  | Phase 9 mechanical. |
| `FeedPresenter` | document-editor | presenter | 16 | 2 | S | 136 | 2 |  | Embeds MetaPresenter (the data browser), which is large and elsewhere. Presenter tabs can stay as presenters inside a DocumentEditor. |
| `PipelinePresenter` | document-editor | presenter | 16 | 4 | L | 357 | 2 |  | Shares one PipelineModel between the Structure and Stepping tabs; keep that as the shared model. Convert to DocumentEditor after its tabs. |
| `RuleSetPresenter` | document-editor | presenter | 16 | 4 | M | 352 | 1 |  | The pilot's host; becomes a DocumentEditor with TabbedComponent tabs. |
| `FloorMapEventStorePresenter` | document-editor | presenter | 24 | 2 | S | 92 | 2 |  | Becomes a DocumentEditor like the Dictionary pilot once its two tabs are convertible. |
| `SqlTemporalStorePresenter` | document-editor | presenter | 24 | 2 | S | 87 | 2 |  | Becomes a DocumentEditor with tabs. |
| `IndexPresenter` | document-editor | presenter | 25 | 2 | M | 101 | 2 |  | A straight DocumentEditor conversion once its tabs are done. |
| `PlanBPresenter` | document-editor | presenter | 25 | 2 | S | 90 | 2 |  | Straight DocumentEditor conversion (Phase 9) once its tabs can be added as components or presenter tabs. |
| `FloorMapPresenter` | document-editor | presenter | 26 | 3 | M | 367 | 5 |  | Can move to DocumentEditor early with presenters as tabs (they are allowed), before the big tabs are split. |
| `QueryDocPresenter` | document-editor | presenter | 26 | 2 | M | 136 | 3 |  | Converts to DocumentEditor<QueryDoc> with DocumentEditorHost in QueryPlugin, last in the query stack. |
| `AnalyticRulePresenter` | document-editor | presenter | 26 | 3 | M | 147 | 2 |  | Becomes a DocumentEditor<AnalyticRuleDoc>. Presenters can still be tabs, so it can convert before all of its tabs do. |
| `ReportPresenter` | document-editor | presenter | 26 | 2 | M | 131 | 2 |  | As AnalyticRulePresenter, for reports. |
| `EmbeddedQueryPresenter` | composite | presenter | 27 | 5 | XL | 988 | 7 |  | A 920-line dashboard component that runs a query and switches between table and vis. It is tied to the dashboard component framework, so convert it with the… |

## 15. Content-tab screens and the shell

Convert the tool screens onto a content-tab component base: Users, Accounts, API keys, Credentials, Nodes, Jobs, Cache, Volumes, Meta, Retention, Content Store and so on. Then convert the shell: ContentTabPane, CurveTabLayout, Navigation, Main, Core, FullScreen and TabSessionManager. Replace explorer bus events with callbacks and context where possible, and finally delete ContentTabPresenter, WrapperView's GIN binding and the popup bridges.

**Why now.** Screens only own lists, dialogs and tabs, all converted by now. The shell owns the screens and the tab pane, so it goes last. NavigationPresenter can only drop its explorer bus events once nothing else listens for them.

**Risks.**

- AskStroomAiPresenter (1,543 lines, in flight) and ExecutionScheduleManager (786 lines) need sub-components carved out first.
- OpenContentTabEvent, DockEvent and the tab events carry presenters. Re-key them on components before ContentTabPresenter is deleted.
- The shell's startup order (Core, then Main, then Navigation and the tab session restore) is fragile. Test login, tab restore, maximise and full screen end to end with the workbench leak check.
- Deleting MyPresenterWidget, View and ViewImpl (452 users), PresenterAdapter and ControllerDialog is the final step. It waits for the removals rule and a full screenshot comparison.

| Unit | Kind | State | Level | C | Effort | Lines | Used by | In flight | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ContentTabPresenter` | framework | presenter | 0 | 2 | M | 43 | 33 |  | Needs a component equivalent, such as a Component base that implements TabData, before its 33 subclasses can move. |
| `CorePresenter` | shell | presenter | 0 | 2 | M | 257 | 7 |  | The app root shell (spinner and startup). Convert it late, with the main shell. |
| `WelcomePresenter` | screen | presenter | 0 | 2 | S | 229 | 6 |  | Simple screen, a good early candidate for converting a whole screen. |
| `IFrameContentPresenter` | screen | presenter | 0 | 2 | S | 237 | 4 |  | A content tab that shows a URL. It could reuse the tidied IFrame widget. |
| `NavigationPresenter` | shell | presenter | 3 | 5 | XL | 670 | 3 |  | The explorer pane of the shell; convert last in this partition, after AbstractExplorerTree and TypeFilterPresenter. |
| `TabSessionManager` | plugin-or-infrastructure | smart class | 3 | 3 | M | 272 | 2 |  | App-wide service that shows dialogs; stays a controller, just re-point at the new chooser component. |
| `CurveTabLayoutPresenter` | shell | presenter | 3 | 3 | M | 307 | 1 |  | The main content tab pane's base; the dumb part (tab bar + layer container + sidebar toggle) becomes one component, and the bus forwarding moves to… |
| `ContentTabPanePresenter` | shell | presenter | 4 | 3 | M | 263 | 3 |  | The app shell's content area. Convert it late, once tab bodies can be components. |
| `DatabaseTablesMonitoringPresenter` | screen | presenter | 4 | 2 | S | 157 | 1 | yes | Read-only REST grid content tab; in flight in data_grid2. |
| `FullScreenPresenter` | shell | presenter | 5 | 2 | S | 130 | 6 |  | A small shell that shows a single document full screen. |
| `ProcessorTaskPresenter` | tab | presenter | 5 | 1 | S | 151 | 5 |  | Thin split pane over two lists. |
| `CachePresenter` | screen | presenter | 5 | 1 | S | 154 | 3 |  | A thin screen. |
| `SigningKeyPresenter` | screen | presenter | 5 | 2 | S | 282 | 3 |  | Small screen. |
| `UserAccessPresenter` | screen | presenter | 5 | 2 | S | 318 | 3 |  | A split pane holding two lists. |
| `TaskManagerPresenter` | screen | presenter | 5 | 2 | S | 303 | 3 |  | A thin screen around the list. |
| `DependenciesTabPresenter` | screen | presenter | 5 | 2 | S | 268 | 3 |  | Thin screen over the list. |
| `AccountsPresenter` | screen | presenter | 5 | 1 | S | 187 | 3 |  | Thin content tab around the list; delete the unused UiHandlers. |
| `MainPresenter` | shell | presenter | 5 | 4 | L | 878 | 3 |  | App shell; convert last. MainViewImpl firing DockResizeEvent should become a callback. |
| `BrowseAnnotationPresenter` | screen | presenter | 5 | 2 | S | 104 | 1 |  | The annotation browse tab, the same shape as FindAnnotationPresenter. |
| `DataPreviewTabPresenter` | screen | presenter | 6 | 1 | S | 136 | 4 |  | Trivial tab shell; becomes a content tab over DataPresenter's controller. |
| `AppPermissionsPresenter` | screen | presenter | 7 | 2 | S | 227 | 11 |  | A thin screen once its two children are components. |
| `GlobalPropertyTabPresenter` | screen | presenter | 7 | 3 | M | 306 | 3 |  | The top-level Properties screen; split it after its list and edit dialog. |
| `UsersPresenter` | screen | presenter | 8 | 2 | S | 221 | 4 |  | Do it after UserListPresenter. |
| `NodeGroupPresenter` | screen | presenter | 8 | 2 | S | 151 | 1 | yes | The top of the node-group stack. Being changed in worktree-data_grid2. |
| `AskStroomAiPresenter` | screen | presenter | 9 | 5 | XL | 1543 | 8 | yes | 1213-line dockable chat screen; in flight in copy_and_paste. Last in the AI area: split a chat view component from a conversation controller. |
| `JobPresenter` | screen | presenter | 9 | 2 | S | 178 | 5 |  | The Jobs screen, straightforward once its two lists are done. |
| `IndexVolumeGroupPresenter` | screen | presenter | 9 | 3 | M | 190 | 2 | yes | The volume groups tool screen. Being changed in worktree-data_grid2. |
| `ProcessorProfilePresenter` | screen | presenter | 9 | 3 | M | 154 | 1 | yes | Top-level tab; inflight in data_grid2. |
| `FsVolumeGroupPresenter` | screen | presenter | 9 | 2 | M | 173 | 1 | yes | In flight in worktree-data_grid2. The top screen of the volumes area. |
| `NodePresenter` | screen | presenter | 10 | 2 | S | 179 | 4 |  | A thin screen. Convert it after the two lists. |
| `SourceTabPresenter` | screen | presenter | 10 | 1 | S | 152 | 4 |  | Trivial tab shell over SourcePresenter. |
| `AnnotationTagPresenter` | screen | presenter | 11 | 3 | M | 179 | 5 | yes | The tool screen for statuses, labels and collections; each AnnotationXxxPlugin opens one per tag type. Being changed in worktree-data_grid2. |
| `ApiKeysPresenter` | screen | presenter | 11 | 3 | M | 381 | 4 |  | Both a screen and the API Keys tab of UserTabPresenter. Do it after ApiKeysListPresenter and EditApiKeyPresenter. |
| `CredentialsPresenter` | screen | presenter | 13 | 1 | S | 172 | 3 |  | Trivial shell around the list; the view can go entirely (show the list's widget directly). |
| `ContentStorePresenter` | screen | presenter | 15 | 2 | M | 228 | 5 |  | Children hold a back-reference to the parent to fire alerts and request redraws; replace with setOnChanged callbacks when splitting. |
| `BatchDocumentPermissionsPresenter` | screen | presenter | 15 | 3 | M | 322 | 5 |  | Same shape as UserPermissionReportPresenter; convert the two together. |
| `ContentTemplateTabPresenter` | screen | presenter | 15 | 4 | L | 862 | 3 |  | 767 lines; a near copy of DataRetentionPolicyPresenter, so share the approach. |
| `ExecutionScheduleManager` | screen | presenter | 15 | 4 | L | 786 | 1 | yes | Top-level screen, in flight in worktree-data_grid2. Convert it last in this area, after its three dialogs. |
| `UserTabPresenter` | screen | presenter | 16 | 3 | M | 256 | 4 |  | Top of the security tree. Becomes a TabbedComponent once its tabs are components. |
| `DataRetentionPresenter` | screen | presenter | 16 | 3 | M | 289 | 4 |  | Becomes a TabbedComponent screen. |

## Deferred: delete rather than convert

- ItemCell: no callers (fan-in 0). Delete it rather than convert it, after the in-flight worktrees land (removals wait for other work).
- CustomCellList: no callers, and in flight in data_grid2. Delete it after data_grid2 lands.
- SvgIconCell: no callers. Delete it after the in-flight work completes.
- FindCellTable: no callers, and in flight in data_grid2. Delete it after data_grid2 lands.
- FindResultWithPermissionsCell: no callers. Delete it after the in-flight work completes.
- JobTypeCell: no callers. Delete it after the in-flight work completes.
- HoverActionMenuCell: no direct callers, and in flight in both data_grid2 and copy_and_paste. Delete it, or fold it into ActionMenuCell, after both land.
- LinkTabsPresenter: no callers. The settings presenters re-implement its tab switching. Delete it once the LinkTabsLayoutView users are on TabbedComponent (wave 13).

## What the reviewers saw across the code

- p01: Widget outputs are mostly GWT events (ValueChangeEvent via HasValue/HasValueChangeHandlers, ColumnSortEvent, RangeChangeEvent, addClickHandler) rather than the setOnX callbacks the component model asks for: CustomCheckBox (fan-in 67), QuickFilter (21), SvgIconBox, DataGridWidget. Adding a callback beside the event first, then migrating callers, avoids big-bang changes.
- p01: setEnabled and setReadOnly both exist on several leaves (CustomCheckBox, DropDownBox, QuickFilter has only setEnabled; SvgIconBox has misspelt setReadonly); a single read-only convention needs settling before composites read Contexts.READ_ONLY.
- p01: The only bus use in these leaf widgets is for app-wide requests (DataGridWidget's context menu: HyperlinkEvent, AskStroomAiEvent; it takes the bus in its constructor so it has no no-arg constructor). SplitPane already shows the fix: MenuRequest bubbles a DOM custom event that a relay turns into a ShowMenuEvent. A HyperlinkRequest/AskAiRequest on the same lines would make DataGridWidget dumb and template-usable.
- p01: GWTP-style view interfaces + UiHandlers live in the widget module for presenters elsewhere (DropDownView, ExplorerPopupView, QuickFilterPageView (fan-in 14), QuickFilterDialogView); they disappear only when those presenters are split, so they come after QuickFilter/DropDownBox in the order. QuickFilterPageView is a strong candidate for one shared 'filtered list' component reused by ~14 list controllers.
- p01: Handler interfaces with several methods (QuickFilterContextHandler) are used instead of separate callbacks; workable but inconsistent with setOnX.
- p01: Menu items model and DataGrid models are pure Java and browser-free: easy to unit test, nothing to convert; menu model sits in a 'presenter' package though it is a model.
- p01: Static global state: StaticEventBus (static bus back door used by 5 classes), DebugPane (unused), static event TYPEs; StaticEventBus callers are hidden smart dependencies to hunt down in other partitions.
- p01: Heavy in-flight work: the whole datagrid package (worktree-data_grid2, worktree-copy_and_paste) and parts of the menu (Menu, MenuViewImpl, MenuItemCell, IconMenuItem, MenuItem) are being changed in other worktrees, so these must be sequenced after those land.
- p01: Suggested bottom-up order for this partition: TextFieldCell, SplitPane, DropDownBox (already done) -> SvgIconBox, CustomCheckBox, QuickFilter tidy -> DataGridWidget bus removal (after data_grid2) -> MenuPresenter split -> the view-only units go as their presenters in other partitions are split.
- p02: Leaf widgets in this partition are almost all already dumb (no REST, no app bus, no GIN), but their output API is inconsistent: ToolbarButton/ToolbarToggleButton/Toolbar use setOnClick/setOnToggle(Runnable/Consumer), while Button, FabButton and LinkButton only offer GWT ClickHandler, and ValueSpinner/Spinner/DurationPicker use HasValue + ValueChangeEvent. The tidy is mostly to add setOnX callbacks beside the old handlers and then move callers.
- p02: Many 'bus' signals here are local HandlerManagers or addHandler on the widget itself (Spinner, XSDModel, the selection models, AbstractTabBar), not the app EventBus. The real app-bus users are only TooltipPresenter (ShowPopupEvent), CurveTabLayoutPresenter/LinkTabsPresenter (re-firing tab bar ShowMenuEvent/RequestCloseTabEvent onto the bus) and HelpManager/ShowHelpEvent (StaticEventBus).
- p02: The tab bar family (AbstractTabBar 1025 lines -> DraggableTabBar -> CurveTabBar, LinkTabBar) talks to its owner through four GwtEvent channels (Selection, RequestCloseTab, ShowMenu, ShowTabMenu) that presenters forward to the bus. Moving these to callbacks is the key bottom-up step before CurveTabLayoutPresenter (main content pane) and the LinkTabsLayoutView-based settings dialogs (16 views) can become components.
- p02: ButtonView/ToggleButtonView (fan-in 60), TabBar and LinkTabsLayoutView (fan-in 16) are interfaces kept only for GWTP view interfaces; they can only be removed late, after the presenters exposing them are converted.
- p02: TaskMonitorFactory is implemented by leaf widgets (Button, AbstractTab) to show spinners - task plumbing inside dumb widgets; the component model gets the task monitor from context instead.
- p02: Setters naming varies: focus() vs setFocus(boolean) (FabButton), setEnabled everywhere but setReadOnly only on ValueSpinner/DurationPicker; buttons have no read-only concept.
- p02: Several unused classes found (ItemCell, LinkTabsPresenter, CustomDateTimeFormat, ExpanderEvent, ResourceCache, Debug, BasicSelectionEventManager at fan-in 0): removal candidates once in-flight work completes.
- p02: In-flight files: the selection models/managers and TypeAhead (worktree-data_grid2), SafeHtmlUtil and ContextMenuEvent (worktree-copy_and_paste). Leave these until those worktrees land.
- p03: Leaf widgets here are mostly already dumb (no REST, no GIN, no-arg); the gap is output API style: BaseSelectionBox/SelectionBox (fan-in 108) still emit GWT ValueChangeEvent, SelectionList/SelectionPopup emit CloseEvent, rather than setOnX callbacks or Listeners<T>. Add callback APIs alongside the old handlers first, then move callers.
- p03: Two-phase construction (init(model) that throws if called twice) in SelectionList/SelectionPopup and constructor-injected UiHandlers in the dialog chrome (Dialog, ResizableDialog, SimplePopup, the seven *Content bars) stop them being template-usable; the fix is a no-arg constructor plus setters/callbacks.
- p03: UiHandlers-style single-method interfaces (HideRequestUiHandlers fan-in 22, DialogActionUiHandlers 21) are used as callbacks; they map directly onto Consumer/Runnable callbacks.
- p03: Cells that open menus (ActionMenuCell, HoverActionMenuCell, DialogWindow via MenuRequest) take a HasHandlers and fire ShowMenuEvent; menus are an app-wide request so this is tolerable, but a menu-opener function would let cells live outside presenters.
- p03: The popup system is keyed on MyPresenterWidget: every ShowPopupEvent/HidePopup*Event (fan-in 155/94) carries a presenter and PopupManager maps presenter to PopupSupport; ControllerDialog and PresenterAdapter exist only to bridge this. Re-keying the popup events on a widget/HasControllerNode is the critical path to retiring MyPresenterWidget, View (fan-in 452) and ViewImpl.
- p03: Duplication in popup chrome: Dialog vs ResizableDialog (830 vs 591 lines, shared DialogWindow) and seven near-identical button-bar Content classes with templates; consolidate before or while converting.
- p03: Many files in this partition are in flight in worktree-data_grid2 (SelectionList, SelectionBoxCell, CommandLinkCell, HoverActionMenuCell, InfoColumn, MyCellTable, CustomCellList, AbstractPopupPanel), plus worktree-copy_and_paste on HoverActionMenuCell; schedule these after that merge.
- p03: Probably-dead code seen (fan-in 0): HandlerRegistrations, CustomCellList, HoverActionMenuCell(direct), TextCellRenderer, ArrowConnectorRenderer, MyTreeLayout, FixedNodeExtentProvider, SvgStringCell, ToolTipCell; per the removals rule, deleting waits for other in-flight work.
- p03: The htree package and treelayout (about 3,600 lines) are pure drawing/layout code with no UI framework coupling; they count as not-ui and need no conversion.
- p03: Suggested bottom-up order within this partition: SpinnerLarge/SpinnerSmall/cells (already done) -> TextBoxPopup (S warm-up) -> SelectionPopup + SelectionBox callback API -> dialog button bars + Dialog/ResizableDialog merge -> PopupSupportImpl -> popup events re-keyed -> framework retirement (PresenterAdapter, ControllerDialog, MyPresenterWidget); SelectionList and data-grid cells after worktree-data_grid2 lands.
- p04: Most leaf widgets here (ColourBox, RadioGroup, PasswordBox, DateTimeBox, TimeBox, CustomDatePicker, Editor, LineColInput) are already no-arg and bus-free, but report changes with GWT ValueChangeEvent/HasValue instead of setOnChange callbacks. Decide once whether HasValue stays as the leaf-widget contract before tidying them one by one.
- p04: Read-only is inconsistent across leaf widgets: some have only setEnabled (ColourBox, RadioGroup, CustomDatePicker), some have both setEnabled and setReadOnly (PasswordBox, DateTimeBox, TimeBox, LineColInput), and FormGroup has setDisabled. A shared convention is needed before controllers push READ_ONLY.
- p04: Several 'presenters' in the widget module hold no smart behaviour at all (ProgressPresenter, HtmlPresenter, WrapperView, PagerView/PagerViewWithHeading). They are GWTP View/ViewImpl pairs with an @Inject Binder and can collapse straight into single no-arg dumb components without controllers. The cost is in their many callers (PagerView fan-in 86, WrapperView 11).
- p04: Picker boxes open their own presenter dialogs: DateTimeBox and TimeBox take a Provider<XxxPopup>, and DateTimeBox falls back to StaticEventBus. The dialog content (DateTimeViewImpl, TimeViewImpl) must become a dumb panel first, then the box gets setOnOpenPicker or opens a bus-free component dialog. Order: CustomDatePicker, then DateTime/Time panels, then the popups, then DateTimeBox and TimeBox.
- p04: View classes implement TaskMonitorFactory (PagerViewImpl, PagerViewWithHeadingImpl, RefreshButton count tasks themselves). This conflicts with 'context, not arguments' (Contexts.TASK_MONITOR). The widget should expose setRefreshing and let the controller adapt the task monitor.
- p04: Option/Action getters on EditorPresenter let callers mutate editor state directly (13 getXxxOption()). A dumb CodeEditor needs a settings model or setters instead, and this is the hardest API change in the partition.
- p04: In-flight conflicts: the editor unit (EditorPresenter, EditorViewImpl, EditorMenuPresenter) is in worktree-copy_and_paste, and the grid DefaultResources/DefaultStyle are in worktree-data_grid2. Schedule these units after those land.
- p04: Apparent dead code: XMLStyler and DoubleClickEvent have fan-in 0, and cell/valuespinner/Spinner duplicates widget/valuespinner/Spinner. Removal waits for other in-flight work, per the project's removal rule.
- p04: In stroom-core-client-widget, templates live under src/main/resources in the same package (PagerViewImpl.ui.xml, Pager.ui.xml, DateTimeViewImpl.ui.xml, TimeViewImpl.ui.xml, EditorViewImpl.ui.xml, CustomFileUpload.ui.xml, ProgressViewImpl.ui.xml).
- p05: Floormap presenters (28 files here) have their ViewImpls in stroom/floormap/client/view in another partition; each floormap unit's split must be done with its ViewImpl, and the canvas/grid/icon-picker helper widgets (FloorMapGrid, FloorMapIconPicker) live there too.
- p05: Many floormap presenters are behaviourally dumb (no REST, no bus, only setXConsumer/setXHandler callbacks: Canvas, FactList, Layers, Tracking, GroupEdit, SetScale, TimelineSettings, Dock). They need a single dumb component, not a component+controller pair, but their callback names (setXConsumer, setXHandler, setXListener) are inconsistent with the setOnX convention.
- p05: Lists build DataGridWidget, columns and Toolbar buttons in Java over the shared PagerView (CredentialsList, ContentPackList, DocRefList, EffectiveWordsList, FloorMap FactList/TimeList/Groups/Tracking/Cluster/Settings). Most are in flight in worktree-data_grid2, so they should wait.
- p05: Dialogs show themselves with ShowPopupEvent.builder(this) inside the presenter and validate by firing AlertEvent (credentials secret forms, floormap edit dialogs, About, AdvancedQuickFilter). The split moves the popup and validation alert to the controller, leaving validate() returning a message.
- p05: The five credential secret forms share a sealed SecretPresenter interface that exposes getView() and onOk(Consumer<Boolean>). Convert them together into dumb forms behind a shared SecretForm interface, then CredentialSettings and ReplaceSecret.
- p05: DocSelectionBoxPresenter is still made directly by BasicTextSettings, FloorMapInit and FloorMapSettings. These should follow the stream-destination pilot: a template DocSelectionBox plus an attached DocSelectionController.
- p05: Child-to-parent back-references stand in for callbacks. ContentStore children hold ContentStorePresenter to fire alerts and redraws, and CredentialsManagerViewImpl holds CredentialsListPresenter. Floormap Map and Editor call static helpers on FloorMapQueryPresenter.
- p05: The views of DateTimeBox users expose the box so the presenter can inject a Provider<DateTimePopup> (CredentialSettings, ReplaceSecret). That stops DateTimeBox being usable as a dumb template widget.
- p05: Tab hosting is done by hand (CredentialEditPresenter's TabData->Layer map, FloorMapDockPresenter) where TabbedComponent now exists.
- p05: Some code is dead: SvgIconCell (fan-in 0), TextSettingsUihandlers (fan-in 0) and CredentialsManagerDialogUiHandlers (never set). Delete them once removals are allowed rather than convert them.
- p05: The dictionary pilot classes (EffectiveWordsList, ImportsTab, WordsTab) are pre-split smart Controllers that build their own widgets. Under the new component/controller rule they still need splitting, but cheaply.
- p06: Many leaf widgets report changes through a *UiHandlers object (ExpressionUiHandlers, QueryUiHandlers, ChangeUiHandlers) instead of a setOnChange callback: OperatorEditor, TermEditor, QueryButtons, TimeZoneWidget. A mechanical tidy pass could do them together.
- p06: Widgets implement model or search interfaces (QueryButtons and QueryToolbarPresenter implement SearchStateListener; table and vis presenters implement ResultComponent), so QueryModel pushes state straight into the UI. A dumb component should take setSearching/setData from a controller instead.
- p06: Several widgets get services through their constructor or init(): TermEditor and ExpressionTreePanel take Provider<DocSelectionBoxPresenter>, Provider<UserRefSelectionBoxPresenter>, UiConfigCache and RestFactory. ExpressionTreeViewImpl is @Inject-ed only to pass them on. The DropDownBox + DocSelectionController pilot fixes this, but a user-ref controller equivalent is needed too.
- p06: The read-only API is inconsistent. Settings views have onReadOnly(boolean) driven by watch(Contexts.READ_ONLY). TimeRangeSelector and TimeZoneWidget have both setEnabled and setReadOnly. ExpressionTreeView has setReadOnlyLook. NodeGroupEditView's setEnabled means a data value, not the widget state.
- p06: View interfaces exist only to hold SplitPane slots (CacheView, NodeView, ProcessorTaskView, ResultStoreView, QueryResultTableSplitView, QueryEditView). These screens are thin and convert in S effort once their child lists are components.
- p06: Most lists (Cache*, Node*, NodeGroup*, ResultStoreList, ElasticIndexFieldList, JobNodeListHelper, QueryResultTable) use the shared PagerView/DataGridWidget with RestDataProvider. Nearly all are inflight in worktree-data_grid2, so schedule them after that merges.
- p06: Document editors (QueryDocPresenter, ElasticClusterPresenter, ElasticIndexPresenter, FeedPresenter) all follow the same DocTabPresenter + LinkTabPanelView + DocTabProvider + Markdown/permissions tab pattern. One recipe for moving to DocumentEditor plus DocumentEditorHost covers all of them.
- p06: TimeZoneWidget and QueryDataViewImpl take an @Inject Binder constructor, which blocks template use. Switch them to GWT.create(Binder) with a no-arg constructor.
- p06: Small GWTP-style dialogs built with new/StaticEventBus (TimeRangeDialog in TimeRangeSelector) should become ComponentDialog.showOnce from the widget's owner, or a popup the widget owns.
- p06: The query area has a deep stack. Bottom-up order: OperatorEditor/QueryButtons/TimeZoneWidget/QueryResultTabsView, then TermEditor and TimeRangeSelector, then ExpressionTreePanel, ExpressionTreePresenter and QueryToolbar, then QueryHelp/Text/Vis/ResultTable, then QueryResultTableSplit and QueryEdit, then QueryDocEdit and QueryDoc.
- p07: Two widget-sized selection boxes, UserRefSelectionBoxPresenter (fan-in 11) and DocumentAssetDropDownPresenter, are MyPresenterWidget<DropDownView> wrappers that own a picker dialog. Both are the same shape as the old DocSelectionBoxPresenter and should get the same split: a DropDownBox in the template plus a ...SelectionController. Several dialogs are waiting on that split: BatchDocumentPermissionsEdit and EditApiKey (UserRef box), and SolrIndexSettings (DocSelection box).
- p07: Most of the security area has the same three layers: a *ListPresenter (data grid + RestDataProvider + QuickFilter), a screen presenter that owns the list and makes edit dialogs, and UserTabPresenter on top using several of the screens as tabs. Bottom-up order: lists, then dialogs, then screens/tabs, then UserTabPresenter.
- p07: Nearly every list in this partition (11 of them, plus DocumentAssetTree and UserAndGroupHelper) is in flight in worktree-data_grid2. In practice the security, task and Solr lists can't move until the DataGrid work lands.
- p07: Children are still handed over as views: getView().setXView(child.getView()). BatchDocumentPermissionsPresenter and UserPermissionReportPresenter also reach into a child's view toolbar to add buttons (getView().getToolbar().button(...)). A component API needs to expose the toolbar or the actions explicitly.
- p07: UiHandlers interfaces that carry a TaskMonitorFactory (DocumentUser*PermissionsEditUiHandler, SolrIndexSettingsUiHandlers) show the view starting work. When split, those become plain callbacks and the controller supplies the task monitor from context.
- p07: Several dialog bodies are nearly dumb already and only fire ShowPopupEvent or AlertEvent for validation: DocumentAssetAddItem/EditAsset dialogs, SolrIndexFieldEditPresenter, CreateExternalUser/CreateMultipleUsers. These are cheap first conversions (S, like the FieldEditor pilot), but callers read getView() getters directly and need changing at the same time.
- p07: Some MVP pairs have nothing smart in them at all (UserTaskPresenter, IFramePresenter). They only need collapsing into a component or widget with setters and setOnX callbacks.
- p07: The view-model APIs mix enable and read-only: UserRefSelectionBox has both setEnabled and setReadOnly, DocumentAssetDropDown toggles a 'disabled' CSS class, and UserInfo inverts the flag twice (the presenter passes hasPermissionToEdit to setReadOnly, and the view enables the tick box when it is true).
- p07: Framework blockers in this partition: ContentTabPresenter (base of 33 tab presenters), OpenContentTabEvent (carries a MyPresenterWidget) and DocumentUserPermissionsTabProvider (fan-in 29). Each needs a component equivalent before the screens and document tabs that use it can move.
- p07: Plain not-UI code: floormap geometry, task helpers, and the security REST clients/description builders. The *RestClient classes are already shaped like controller services and can be reused by the new controllers.
- p08: Navigator views (ItemNavigatorViewImpl, CharacterNavigatorViewImpl) hold the real logic and call a HasItems/HasCharacterData display directly; the presenter only opens a dialog. These are near-dumb widgets: invert to callbacks and they become reusable components.
- p08: Many ViewImpls take an unused EventBus in their @Inject constructor (ItemNavigator, CharacterNavigator, ItemSelection, DataPreviewTab); and use a static or injected Binder, so they are not template-usable.
- p08: Ref cells (DocRefCell, FeedRefCell, UserRefCell) take the EventBus through their builder and fire Open*Event themselves; one onOpen callback pattern would fix all three. All are in flight in copy_and_paste and data_grid2.
- p08: RestDataProvider (fan-in 51) needs EventBus in its constructor; changing it to take an owner is a mechanical, cross-cutting change best done once before the lists.
- p08: Repeated 'rules tab' shape: list presenter (PagerView, nearly dumb) + ExpressionTreePresenter + edit dialog + REST load/save + toolbar buttons built in Java, in DataRetentionPolicyPresenter, ContentTemplateTabPresenter and RuleSetSettings (already split). Reuse the pilot's action-enum approach for the other two.
- p08: EditExpressionPresenter (fan-in 12) is the shared expression editor used by rules, retention, content templates, view settings and the meta filter; its init(RestFactory, ...) passes REST through. Convert directly after ExpressionTreePresenter.
- p08: DocSelectionBoxPresenter is still embedded in ContentTemplateEditPresenter, ImportConfigConfirmPresenter, DataGenSettingsPresenter and ViewSettingsPresenter: use the DropDownBox + DocSelectionController attach pattern there.
- p08: Results returned through ad hoc interfaces (ProcessChoiceUiHandler) or by writing to a passed-in display (ItemSelectionPresenter) or a parent back-reference (MarkerListPresenter.setDataPresenter) instead of a Consumer callback.
- p08: Read-only via watch(Contexts.READ_ONLY, view::onReadOnly) in the HTTP dialogs: should push setReadOnly to a Component instead.
- p08: Hand-rolled tabs (LinkTabBar + LayerContainer, ShowMenu relayed to the bus) in DataPresenter, DataRetentionPresenter and RuleSetPresenter; these become TabbedComponent/DocumentEditor.
- p08: Many list presenters in this partition (meta lists, processor tasks, retention, content templates, dependencies, import confirm, marker list, field list) are in flight in worktree-data_grid2; schedule them after it merges.
- p08: Data area order bottom-up: ClassificationLabel -> ClassificationWrapperView; ItemSelection/CharacterRangeSelection dialogs -> Item/CharacterNavigator; TextPresenter (after EditorPresenter); MarkerListPresenter -> SourcePresenter -> DataPresenter -> preview/source tabs; ProcessChoice/SelectionSummary/DataUpload/Expression dialogs -> AbstractMetaListPresenter family -> MetaPresenter last.
- p09: The explorer tree stack is the critical leaf here: AbstractExplorerTree (and ExplorerTree/ExplorerTickBoxTree) takes RestFactory + TaskMonitorFactory and builds its own REST-backed ExplorerTreeModel, so ExplorerPopupPresenter, EntityTreePresenter, EntityCheckTreePresenter, NavigationPresenter and (via ExplorerPopupPresenter) every DocSelectionBox depend on it; split tree widget vs ExplorerTreeModel controller first, after data_grid2 lands.
- p09: Many 'presenters' in this partition are already dumb in substance (TypeFilterPresenter, TabSessionChooserPresenter, TimePreferencesPresenter, EditorPreferencesPresenter, KeyShortcutRecorderPresenter, KeyboardShortcutsPresenter, GitRepoCommitDialogPresenter): their only smartness is ShowPopupEvent/HidePopupRequestEvent or one injected service, so they are cheap S conversions to component dialogs with a result callback.
- p09: Grid-hosting presenters build the DataGridWidget inside the presenter and hand it to a PagerView or a holder view (setSelectionList/setWidget); the view interface is just a slot. Nearly all of them are inflight in worktree-data_grid2 (ProcessorListPresenter, FeedDependencyListPresenter, ProfilePeriodListPresenter, ProcessorProfileListPresenter, FindDocResultListPresenter, DocumentListPresenter, DocumentPermissionsListPresenter, KeyBindingPreferencesPresenter, KeyboardShortcutsPresenter, TypeFilterPresenter, TabSessionChooserPresenter, FindInContentPresenter), so the list layer should wait for that merge.
- p09: Child-to-owner outputs are inconsistent: ChangeUiHandlers/XxxUiHandlers on views, setOnChange(Runnable) on the preferences presenters, Listeners<TypeFilterPresenter> passing itself, SelectionChangeEvent registrations (TabSessionChooser), GWT handler events on AbstractExplorerTree (addContextMenuHandler, addItemKeyHandler) and FindDocResultListHandler; standardise on setOnX / Listeners<T>.
- p09: Pickers embedded as presenters block their dialogs: EditFeedDependencyPresenter (DocSelectionBoxPresenter), BatchProcessorFilterEditPresenter and ProcessorEditPresenter (UserRefSelectionBoxPresenter). EditFeedDependencyPresenter is a good first adopter of the DropDownBox + DocSelectionController template pattern; UserRefSelectionBox needs the same split first.
- p09: Widget-level setReadOnly vs setEnabled duplication recurs (DaysWidget, DocSelectionBoxPresenter); views still implement onReadOnly themselves (GitRepoSettingsViewImpl) fed by watch(Contexts.READ_ONLY) in the presenter.
- p09: Document editors (GitRepoPresenter, SqlTemporalStorePresenter) are thin DocTabPresenter shells with providers for settings/markdown/permissions tabs; they convert mechanically to DocumentEditor once their settings tabs are components. SqlTemporalStoreDataPresenter and UnknownComponentPresenter depend on base classes outside this partition (AbstractQueryDataPresenter, AbstractComponentPresenter).
- p09: Dead code spotted (fan-in 0): FindCellTable, FindResultWithPermissionsCell, ExplorerTreeUiHandlers; TabSessionChooserPresenter injects an unused UiConfigCache. Per the removals rule, delete only after in-flight work completes.
- p09: Several explorer bus events exist only to link NavigationPresenter with the shell (FocusExplorerFilterEvent, FocusExplorerTreeEvent, ExplorerTreeSelectEvent, HighlightExplorerNodeEvent, ExplorerStart/EndTaskEvent via ExplorerTaskMonitorFactory); NavigationPresenter (complexity 5, XL) should go last and can replace these with callbacks/context.
- p09: Large non-UI units inflate this partition's line count: floormap.cluster (pure algorithm), stroom.dispatch.client (RestFactory, fan-in 251), stroom.util.client (DataGridUtil fan-in 66, inflight), DateTimeFormatter (fan-in 52), explorer events; none need conversion.
- p10: Almost every dialog in this partition (Download, Format, RenameColumn, CustomRowStyle, DownloadChat, AiConfigTableAnalysis, EmailResetPassword, AuthenticationError) is a MyPresenterWidget + View interface + ViewImpl whose view is already dumb (UiBinder template of FormGroup/SelectionBox/CustomCheckBox/ValueSpinner); the conversion is mostly collapsing the three classes into one component with a show(callback) and ComponentDialog.showOnce. These are the bottom-up quick wins.
- p10: DocSelectionBoxPresenter is embedded by BasicTableSettings, AiConfigGeneral, AskStroomAi and VisualisationSettings; once each uses DropDownBox + DocSelectionController in its template, those settings forms lose their only smart child.
- p10: EditorPresenter (Ace) is embedded by ColumnFilter, ColumnFunctionEditor and VisualisationSettings, and ExpressionTreePresenter by EditExpression; these leaf presenters in other partitions block several units here.
- p10: Child dialogs often return results through a back reference to the owner (TablePresenter passed into RenameColumn/ColumnFunctionEditor; ColumnsManager mutating TablePresenter); replace with result callbacks per the component model.
- p10: Data-grid lists (RuleList, AccountsList, AiChatHistoryResultList, AiAttachmentData, DatabaseTablesMonitoring, ColumnValuesFilter, TablePresenter) are all MyPresenterWidget<PagerView> over DataGridWidget with RestDataProvider, and nearly all are in flight in worktree-data_grid2, so they should wait for that merge.
- p10: The identity area all hangs off ChangePasswordPresenter (fan_in 6, used by Login, ResetPassword, CurrentPassword, EditAccount): convert it first, then the rest of the identity flows are small S/M splits.
- p10: Settings/tab containers (TableSettingsPresenter, TableFilterPresenter, AskStroomAiConfigPresenter, VisualisationPresenter) use LinkTabsLayoutView/LayerContainer/TabBar exposed from the view; they map onto TabbedComponent once their tabs are components.
- p10: GWTP UiHandlers interfaces are everywhere; some are unused (AccountsUiHandlers, ExpressionUiHandlers both fan_in 0) and can be deleted. ChangeUiHandlers (fan_in 61) and DirtyUiHandlers (fan_in 10) are the cross-cutting ones that setOnChange/markChanged replace.
- p10: Some views do smart work: MainViewImpl injects EventBus and fires DockResizeEvent; AskStroomAiView implements TaskMonitorFactory; DockEvent and OpenDocumentEvent carry MyPresenterWidget instances, so these events need reworking when their presenters become components.
- p10: Largest/most entangled units (TablePresenter 1472 lines with ColumnsManager 870, AskStroomAiPresenter 1213, DocumentPluginEventManager 1992, MainPresenter) are also in flight in other worktrees or are app-wide infrastructure; they belong at the end of the bottom-up order.
- p11: Nearly every unit in p11 is a GWTP-style presenter with a nested view interface and a ViewImpl. The ViewImpls are usually dumb UiBinder templates already, so most conversions just merge the ViewImpl into a Component and move the presenter's logic into a controller or the owner.
- p11: HasUiHandlers/ViewWithUiHandlers is used throughout (ChangeUiHandlers, ProcessingStatusUiHandlers, SettingsUiHandlers, DialogActionUiHandlers, and per-pane XxxUiHandlers). Several presenters also take a separate onChange Runnable or Consumer, so the outputs are mixed. Replacing the UiHandlers with setOnX callbacks makes the shared UiHandlers interfaces deletable.
- p11: The DocSelectionBoxPresenter split is the main prerequisite in analytics and dashboard settings. Seven units embed DocSelectionBoxPresenter (AbstractSettings, ReportSettings, BatchExecutionScheduleEdit, ScheduledProcessEdit, BasicQuerySettings, BasicListInputSettings). Two embed UserRefSelectionBoxPresenter. All should follow the AnalyticStreamDestination pilot (a DropDownBox in the template plus an attached DocSelectionController).
- p11: BatchExecutionScheduleEditViewImpl @Injects UserRefSelectionBoxPresenter itself: a presenter inside a view, which the component model forbids.
- p11: Child-to-parent back-references stand in for callbacks: ScheduledProcessList/HistoryListPresenter -> ScheduledProcessingPresenter, QueryFavourites/QueryHistoryPresenter -> QueryPresenter, RenameTabPresenter and TabManager -> DashboardPresenter. Each should take a result callback instead.
- p11: Change events are sent three ways: LayoutConstraintViewImpl (private SimpleEventBus) and ColumnSelectionPresenter (HandlerManager) use HasValueChangeHandlers, while other units use UiHandlers or Runnables. These should become setOnChange or a Listeners<T> list.
- p11: Thin abstract base + Analytic/Report subclass pairs (Settings, Notification, NotificationList, Processing, QueryEdit, DuplicateManagement) differ only in document type. AbstractDuplicateManagementPresenter has only one subclass. These can become a single component used by both documents.
- p11: Dashboard panes (Query, TextInput, KeyValueInput, ListInput, TableFilter) all extend AbstractComponentPresenter. Their settings go through SettingsPresenter, a thin XxxSettingsPresenter subclass and BasicSettingsTabPresenter tabs. That pane and settings framework needs a component-model base before any pane converts. TextInputPresenter is the natural pilot.
- p11: Name clash: stroom.dashboard.client.main.Component (the dashboard pane interface, fan-in 24) and stroom.widget.component.client.Component (the new base class) will collide once panes extend the new Component. A rename (e.g. DashboardPane) is worth doing first.
- p11: Many list presenters use MyPresenterWidget<PagerView> and build the DataGridWidget in Java: AnalyticDataShardList, DuplicateManagementList, ScheduledProcessList/HistoryList, ExecutionScheduleManager, NotificationList, SelectionHandlerList, ColumnSelection, FsVolumeGroupList/StatusList, QueryFavourites/History. All but SelectionHandlerList and ColumnSelection are in flight in worktree-data_grid2 and should wait for it. QueryPresenter is in flight in worktree-copy_and_paste.
- p11: MultiRulesPresenter makes embedded per-column RulesPresenters with makeDialog but adds them to a panel. That is an ownership misuse to fix during conversion.
- p11: Document editors (AnalyticRulePresenter, ReportPresenter, DashboardSuperPresenter, S3ConfigPresenter) are DocTabPresenter shells over LinkTabPanelView. They can become DocumentEditor<D> before their tabs, since presenters can still be tabs. S3ConfigPresenter is the smallest.
- p11: Suggested bottom-up order for p11. (1) Near-dumb leaf dialogs and composites: ProcessorLimits, LayoutConstraint, CurrentSelection, RenameTab, ExecutionScheduleRunNow, Name/NewFsVolumeGroup (one shared name dialog), the BasicTextInput and BasicKeyValueInput settings tabs. (2) Picker-based settings panes after the DocSelectionBox and UserRef splits: Analytic and Report settings, BasicQuery and BasicList settings, ScheduledProcessEdit, BatchExecutionScheduleEdit. (3) Analytics composites: TableBuilderProcessing, StreamingProcessing, AnalyticEmailDestination, then AnalyticNotificationEdit. (4) Once data_grid2 lands: the lists, then ScheduledProcessing and AbstractProcessing. (5) The dashboard pane and settings framework, then the panes from TextInput up to QueryPresenter. (6) DashboardPresenter and ExecutionScheduleManager last.
- p11: Not UI (no conversion): the alert events, SearchModel/IndexLoader, DashboardContextImpl, the floormap overlay models, the registries and the GIN modules. SearchStartEvent and SearchEndEvent have fan-in 0 and look dead.
- p12: Only five units in this partition are widget-sized: SettingBlock (already dumb), AssignYourselfLink (a static Label decorator; make it a real widget), and FlexLayout, TabLayout and Splitter. Almost everything else is a presenter with a view, so the bottom-up order here mostly depends on outside widgets: DataGridWidget, PagerView, SelectionBox, ValueSpinner, EditorPresenter, DocSelectionBoxPresenter and the shared FindView.
- p12: Many dialogs are already dumb in substance and can be split early at low cost: AddEventLink, Duration, XPathFilter, StepLocation and StepControl (S). They only show themselves with ShowPopupEvent and have a view interface or UiHandlers.
- p12: Annotation presenters reach REST through an injected AnnotationResourceClient wrapper, not RestFactory. The REST-owner rule (drop late callbacks) has to reach that wrapper, as it would any similar *ResourceClient.
- p12: Repeated API inconsistencies: results go out through GWT SelectionChangeEvent or MultiSelectEvent (ChooserPresenter, MultiChooserPresenter), handler interfaces (FindDocResultListHandler, FlexLayoutChangeHandler, HasClickHandlers), HasDocumentRead/Write and ChangeUiHandlers. Each should become a setOnX callback.
- p12: DocSelectionBoxPresenter appears in IndexSettingsPresenter and twice in DenseVectorFieldPresenter. Both should move to the pilot's DropDownBox-in-template plus DocSelectionController pattern.
- p12: Shared base classes tie units together: AbstractFindPresenter/FindView (AnnotationLink, BrowseAnnotation, FindAnnotation), AbstractQueryDataPresenter (IndexData), PipelineTreePresenter (PipelinePath), DocTabPresenter/LinkTabPanelView (every document editor) and ContentTabPresenter/WrapperView (the tool screens). Convert each family together.
- p12: worktree-data_grid2 is changing nearly every list here (annotation tag and find lists, choosers, XPathList, SteppingFilter, all the index lists, the job lists, the script lists) and FlexLayout. Hold those units until it lands.
- p12: The biggest pieces are AnnotationEditPresenter (2202 lines, also in flight in worktree-copy_and_paste), FlexLayout (2498), PipelinePathPresenter (1758), SteppingPresenter (1326) and ElementPresenter (905). Each needs sub-components carved out before the split.
- p12: Dead code found: JobTypeCell, SteppingPipelineTreeBuilder, NameTokens and KeyboardInterceptor have no users. Under the removals-wait rule they stay until other work completes.
- p12: ARIA is set in Java in SettingBlock, AssignYourselfLink, Splitter and PipelinePathViewImpl (11 places); ElementViewImpl and CoreViewImpl are built in Java with no template.
- p13: PlanB settings are a deep, regular stack: 17 leaf 'Widget' classes (AbstractSettingsWidget base, not Composite, @Inject Binder constructor, HasUiHandlers<ChangeUiHandlers>, onReadOnly) -> 8 type-settings presenter+ViewImpl pairs that only map data -> PlanBSettingsPresenter that swaps them by state type. Convert all leaves in one mechanical sweep onto Component (no-arg, setOnChange, setReadOnly), then collapse each type-settings presenter into a single dumb component without a controller.
- p13: Per-widget view interfaces (XxxSettingsView, HttpStoreSettingsView) exist only so PlanBSettingsPresenter can instanceof-check child views to carry common values across a state-type change; this cross-reaching should become plain data held by the controller.
- p13: Settings/document tabs consistently use onReadOnly(boolean) on the view plus watch(Contexts.READ_ONLY) in the presenter (TextConverterSettings, XMLSchemaSettings, PlanB) - a direct rename to Component.setReadOnly with the push moved into the controller.
- p13: Settings views expose raw widget getters (getConverterType(), getNamespaceURI(), getName() as HasText) and the presenter attaches handlers - the opposite of setters + setOnChange; NewElement, TextConverterSettings and XMLSchemaSettings all need this flip.
- p13: DocSelectionBoxPresenter is owned and its View passed into a view setter in NewPipelineReferencePresenter (x2), NewPropertyPresenter, PipelineStructurePresenter and BasicVisSettingsPresenter - all candidates for the pilot's <DropDownBox> + DocSelectionController.attach pattern.
- p13: Several dialogs return results through getters read in a HidePopupRequestEvent.Handler supplied by the opener (NewElement, NewPipelineReference, NewProperty) rather than a result callback; Phase 8's edit(..., callback) shape fixes this uniformly.
- p13: Generic tree engine (DraggableTreePanel, TreeKeyboard, TreePanel, Box) lives in pipeline.structure.client.view but is shared with the query expression tree; it is configured by abstract subclassing, so making it dumb is an API change across both trees and is in flight in copy_and_paste and data_grid2.
- p13: List presenters (PropertyListPresenter, PipelineReferenceListPresenter, DocRefSelectionPresenter) build DataGridWidget columns and toolbar buttons in Java on the shared PagerView and are all in flight in data_grid2 - schedule after that worktree lands.
- p13: Vis frame layer mixes callback styles (HasUiHandlers<SelectionUiHandlers>, setToolbarHandler, setColoursHandler, setClickHandler-like) and holds static state (PostMessage singleton, VisTooltip statics, frame id counters); VisFrame takes EventBus in its constructor - the one true smart-widget in this partition, shared by dashboard VisPresenter and QueryResultVisPresenter.
- p13: Document editors here (PlanB, Pipeline, TextConverter, Xslt, XMLSchema) are all DocTabPresenter + AbstractTabProvider/DocTabProvider/MarkdownTabProvider with LinkTabPanelView and pass isReadOnly() into tabs by hand; they convert mechanically to DocumentEditor + TabbedComponent once their tab bodies are done.
- p13: Singleton plugins hold dialog presenters injected once (PipelinePlugin -> DocRefSelectionPresenter), so those dialogs are never owned/disposed; switch to appRoot.makeDialog per use.
- p13: Large non-UI model code (floor map editor/playback ~3.4k lines, PipelineModel ~0.9k) is already pure and JUnit-tested - no conversion work, but REST-calling factories/helpers (PipelineModelFactory, PipelineElementTypesFactory, HistogramQueryHelper) should take a REST owner once callers are controllers.
- p14: The floormap partition holds only the ViewImpls (22 views); their presenters are in another partition. Most are already close to dumb (layout, ARIA, setters) but use @Inject Binder constructors, GWTP View slots (setXView(View)) and DirtyUiHandlers/ViewWithUiHandlers. Some callbacks are named setXHandler(Runnable) rather than setOnX.
- p14: Four floormap views (FactList, Groups, TimeList, Tracking) are the same toolbar-above-grid DockLayoutPanel shell; one shared dumb component would replace all of them.
- p14: DateTimeBox popups come from an injected Provider<DateTimePopup> in each view (FloorMapObjectEditViewImpl, FloorMapTimelineSettingsViewImpl). DateTimeBox should make its own popup, so the views get no-arg constructors.
- p14: Document editors (Pathways, Traces, OpenAIModel, KafkaConfig) are thin DocTabPresenter subclasses that wire DocTabProvider/MarkdownTabProvider/DocumentUserPermissionsTabProvider. Each converts to DocumentEditor<D> + DocumentEditorHost only after its tabs (especially MarkdownEditPresenter, fan-in 34, and EditorPresenter) are components.
- p14: DocPresenter (fan-in 81), DocTabPresenter (31), DocTabProvider (25), MarkdownTabProvider (29) and LinkTabPanelView (40) are the legacy document framework. The new DocumentEditor/TabbedController/DocumentEditorHost trio sits beside them as the target, so the legacy classes are deleted last, not converted.
- p14: Several dialogs embed a smart picker presenter: EntityTreePresenter in Copy/Create/Move; DocSelectionBoxPresenter in PathwaysSettings and BasicEmbeddedQuerySettings; SchedulePopup in ScheduleBox. Those pickers come first in the bottom-up order, and ScheduleBox needs the same box + controller split as DocSelectionBox.
- p14: Most list presenters here (ConstraintList, PathwayList, TracesList, ManageGlobalPropertyList, ConfigPropertyClusterValuesList, ActivityList, plus TreeRowHandler and TraceOverviewWidget) are in flight in worktree-data_grid2, so they should wait for the DataGrid work to land.
- p14: Widgets that render HTML strings (TraceOverviewWidget, TraceHistogramWidget, PathwayTreePresenter, FloorMapCanvasViewImpl) are mostly dumb. Their constructors take services (DateTimeFormatter, HasHandlers, DefaultResources) or they reach the bus through a HasHandlers for copy alerts; swapping these for setters or callbacks makes them template-usable.
- p14: Dialogs show themselves through ShowPopupEvent and are opened by app-wide events through eager opener classes (DocumentDialogScreens) or services (CurrentActivity uses AppRoot.makeDialog). They match the documented pattern and only need their presenter/view split.
- p14: View impls that inject the EventBus (ManageActivityViewImpl for its quick filter context) break dumbness and must be fixed while splitting.
