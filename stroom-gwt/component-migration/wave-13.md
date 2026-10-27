# Wave 13: Query and dashboard stack

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 4 (25 October 2026):** the date-time, time, schedule and time range boxes' controllers (`DateTimeBoxController`, `TimeBoxController`, `ScheduleBoxController`, `TimeRangeSelectorController`) are already attached by today's presenters, so there is no `setPopupProvider` or `setSchedulePresenterProvider` left to replace: a converted controller makes and attaches them as they do. `CustomFileUpload` is `FileChooser`, with a `FileChooserController` its owner attaches; `ClassificationWrapperView` is `ClassificationWrapper` (`stroom.data.client.component`); the schedule dialog is `SchedulePickerController`. The rows below name what was there when they were written.

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`. For the visualisations: `VisPane` and `SelectionUiHandlers` are gone; `VisFrame` is in `stroom.dashboard.client.component` (blocks `vis-frame` and `vis-frame__frame`, placed by `--vis-frame__container-*` and `--vis-frame__frame-*`), and `VisFrameController`, attached by its presenter, binds it.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

> **Since wave 10 (27 October 2026):** the lists are components with a controller each (`stroom.<area>.client.component`). A list's template declares its `PagerPanel` (or `HeadedPagerPanel`) with a `toolbarName`, holding its `DataGrid` (`tableName`, `multiLine`, `pageSize`) and its toolbar's `ToolbarButton`s (`svg`, `title`, `enabled="false"`); a quick-filtered list's root is a `QuickFilterPage` (`accessibleName`). The component adds its columns and gives its pager as `getTaskMonitor()`, with `getSelectionModel()`, `getSortList()` and `getToolbar()`; its buttons, info and row menus go out through callbacks (`setOnAdd`, `setOnShowInfo`, `setOnShowActionMenu`), and its controller passes in the ref cells configured (`refCells.configure(new DocRefCell.Builder<>())`, `FeedRefCell`, `UserRefCell`). Only a list inside a document watches `Contexts.READ_ONLY`, which is true where nothing provides it. A controller shown as a tab is a `ControllerLayer`, which passes the focus on. Among the new names: `MetaList` with `MetaListController`, `SteppingMetaListController` and `MetaRelationListController`; `JobNodeColumns` and `JobNodeActionsController` (were `JobNodeListHelper`); one `NotificationList` for analytic and report notifications; `ScriptDependencyList` (`ScriptListPresenter` merged in); `IncludeExcludeFilterEditor` and `IncludeExcludeFilterDictionary`; `DependencyList` (was `DependenciesPresenter`); `TabSessionChooser`; `CredentialsList`; `FindDocResultList`; `EffectiveWordsList`; `TypeFilter`. The plans below keep the old names. See `wave-10.md`.

> **Since wave 11 (27 October 2026):** the larger dialogs are components with a controller (`stroom.<area>.client.component`). One made for each use shows itself with `ControllerDialog.showOnce(opener, this)` from a method taking its result's callback (e.g. `edit(opener, caption, rule, onOk)`); one its owner keeps is owned and shown again with `ControllerDialog.show(this)`. A controller that fetches before it shows disposes itself when the fetch fails (`new DefaultErrorHandler(this, this::dispose)`), and the clients take an error handler for this. A document's Permissions tab is `DocumentUserPermissionsTab` with `DocumentUserPermissionsTabController` (`setDocRef`); `DocumentUserPermissionsTabProvider` shows it through a `ControllerTabProvider`, and `DocumentUserPermissionsPresenter` is a thin content-tab shell over it until the content-tab base. Find, Recent Items, Chat History and Choose Annotation are controllers on one `FindPanel`; the user picker is `UserRefPopupController` on a `QuickFilterDialog`; the users screens' delete and edit group actions are `UserAndGroupActions`. Among the new names: `UserPreferencesDialog`, `AskStroomAiConfigDialog`, `SteppingFilterEditor`, `PathwayEdit`, `FloorMapObjectEdit`, `DataUpload`, `QueryFavourites`, `QueryHistory`, `AdvancedQuickFilter`, `DataRetentionRuleEditor`, `ContentTemplateEdit`, `ConditionalFormattingRuleEditor`, `SelectionHandlerEditor`, `EditApiKeyMode`. `QuickFilterModule`, `ActivityModule`, `HttpModule` and `FsVolumeModule` are gone. `ColourBox` takes `ariaLabel`, and `DateTimeBox` `role` and `ariaLabel`, in a template. The plans below keep the old names. See `wave-11.md`.

> **Since wave 12 (27 October 2026):** the document tabs and composite panes are components with a controller each (`stroom.<area>.client.component`). A document shows one as a tab through `ControllerTabProvider`: `new ControllerTabProvider<>(eventBus, provider::get, Foo::read, Foo::write)` for one the tab makes, or `ControllerTabProvider.ofOwned(eventBus, controller, reader[, writer])` for one the document makes and owns (e.g. to give it callbacks before the tab is opened); the tab passes on the controller's toolbars when it is a `HasToolbar`. Hand-rolled tab panes (`SteppingPresenter`, `UserTabPresenter`, `DataRetentionPresenter`) show each controller in a `ControllerLayer`. `ProcessorTaskPresenter` and `UserAndGroupsPresenter` stay as thin `ContentTabPresenter` shells around their controllers until wave 15. Among the new names: `SteppingElement`, `UserInfoTab`, `FloorMapSettings`, `DocumentAssetsTab` (shown through `DocumentAssetsTabProvider`), `ProcessorFilterTasks`, `DuplicateManagement`, `ImportsTab` with `ImportsTabController`, `AnnotationLinksController` (on the shared `FindPanel`), `ScriptSettings`, `UserAndGroups`, `ElasticClusterSettings`, `FeedDependenciesEditor` (`edit(opener, dependencies, onOk)`), `GitRepoSettings`, `AnnotationEdit` (`setOnTitleChange`, `setOnDelete`), `PipelineStructure`, `OpenAIModelSettings`, `PlanBSettings` (also a Traces store's Settings tab, its state type locked), `ScheduledProcessing` (also a data generator's Execution tab; `setIsDocumentDirty`), `PathwaysSplit`, `TracesListTab`, `SolrIndexSettings`, `ProcessorEdit` (`show(opener, ...)`), `DataRetentionImpact`, `DataRetentionPolicy`, `Processors` with `ProcessorsController` (a pipeline's, rule's or folder's Processors tab), `UserPermissionReport`, `StreamingProcessing` and one `AnalyticProcessing` for rules and reports (`setProcessingTypes(...)`; `AbstractProcessingPresenter` and its two subclasses are gone); the notification list is shown directly (`NotificationListController`). `ClassificationWrapper` takes its content in a template (`<d:content>`), and a `QuickFilterPage` takes a `<form:HelpHTML>` child as its filter's help. The plans below keep the old names. See `wave-12.md`.

Rename the dashboard Component interface to DashboardPane, give panes and their settings a component-model base, then convert bottom-up through this stack: settings tabs, TextInput, KeyValueInput and ListInput panes, Vis, table rules and filters, ColumnsManager, TablePresenter, QueryResultTable, QueryResultTableSplit, the query-data tabs, QueryEdit, QueryDocEdit, then DashboardPresenter and EmbeddedQuery. The model pushes data through setData and setSearching, not ResultComponent.

## Audited (27 October 2026)

Five reviewers read the wave's 12 unit commits against this plan, "Done means" and AGENTS.md (the
settings dialog, its tabs and the input panes; the query stack: the vis pane, results tables, data
tabs and query editor; the dashboard core: the table, query pane, layout, tab menu and body; the
data viewer, source viewer, data browser and stepping screen; the floor map and what was left).
Fixed, in 1 commit:

- **The floor map editor's dirty state.** Its 15 `markChanged()` calls went to the tab host, which
  recomputes dirty from the written document; the editor's staged changes aren't in it, so adding
  an object never marked the document changed. `FloorMapEditorController` has `setOnChange` now,
  which `FloorMapPresenter` sets to its own `onChange`. Story
  `App/Editors/FloorMapEditor AddObjectTellsDocument` (fails with `markChanged()`).
- **The data tabs' queries.** Closing a document while its Data tab polled never stopped the query
  (nor did the presenter); `AbstractQueryDataController` resets its query model on dispose and on
  the window closing. Stories `DataTab`, `DataTabRun` and `DataTabEnterAndReset` on the index; the
  query box has the label "Query".
- **Late replies and timers.** `.owner(this)` on 38 REST calls in 12 controllers; the UI config,
  client property, query document, linked events and annotation menu callbacks guarded; the query
  pane's deferred start and the dashboard body's prompt check `isDisposed()`; the query editor's
  debounce timer and the data viewer's fetch timer cancelled on dispose; the floor map query's
  search state listener stops once disposed; the results visualisation releases its frame on
  dispose.
- **Dead code.** The embedded dashboard's `dashboard__content--embedded` and `setEmbedded` (the
  `top: 0` it replaced set nothing); the workbench's emptied input, text, vis and floor map screen
  modules and their ginjector entries; the floor map map controller's unused task monitor.
- **Java, CSS and docs.** Licence years kept from the replaced classes and 2026 headers added;
  same-package imports; `///` javadoc in the query editor; `ColAndPosition` at the bottom of its
  class; getters named for what they return (`getQueryResultTable`, `getTable`); the new
  stylesheets imported before `scroll-pane.css`, as the old classes were; stale class names in
  comments, stories, WRITING-STORIES.md, DATA_GRID_DESIGN.md, the query filter syntax spec, the
  stepping design notes, COMPONENT_MODEL_DESIGN.md and COMPONENTS.md (no `DockLayoutPanel` left);
  ISSUES.md's misleading `identity` values and the results visualisation's `onUnbind` marked fixed.

Left, on purpose:

- **Slots.** Controllers that call `initWidget` (the expression tree, the results tables, the data
  browser's three panes, the navigators and progress bars set and cleared at runtime, the floor map
  editor's five and map's three children, the query help and editor) are shown in slots, as before.
- **Wrappers.** The `w-100` `SimplePanel`s around declared pickers and the bordered panels around
  the column, rule and selection handler lists stay: dropping them is a look change.
- **The old bases.** `AbstractComponentPresenter` and `AbstractRefreshableComponentPresenter` stay
  for `EmbeddedQueryPresenter`, wave 14's.
- **Recorded in ISSUES.md.** `.dashboard-settings .layer-container`, which matches nothing now (a
  decision: drop it, or keep the look it was for); fields and constructor `@param` lines still named
  for presenters; the Data tab's destroy on close, with no story of its own; whether the table
  filter dialog's focus works now that its tabs are `ControllerLayer`s; handlers wired with
  `@UiHandler` in older components, to move to Java.

## Done (27 October 2026)

All 69 of the plan's units, in 14 commits: 12 for the units (the vis pane, results tables, data
tabs, analytic shards and query editor shared one, their callers overlapping; the source viewer
and data browser another, sharing their GIN modules), the `@UiHandler` decision, 1 for the audit,
and this record. Each unit's commit ran its stories in a copy of the worktree with the leak check
and the screenshot diff. The wave's targeted run, after the audit (511 stories: the dashboard, query,
index, data, feed, stepping and floor map stories, every editor and the app shell), passed all 511
with the leak check; of their screenshots, 477 match, the 9 new have no baseline yet (the wave's 6 new stories and the document selection dialog's
three), and of
the 25 that differ, 14 are the query editor's (its results pane, 17px tall in the stories, shows
overflow: the old table spilled over the tab bar, the split pane now clips it so the "Table" tab
shows; at 1000px the tab, pager and grid show as expected), 10 the floor map editor's (the Fact and
Time lists now show, below) and `DocumentationEditor.TypingEnablesSave` (as in wave 12). The app and
the standalone dashboard GWT compile, checkstyle is clean and the client and workbench modules'
2,180 JUnit tests pass. Two bugs master has too were fixed on the way, with changelog entries:
renaming a Key/Value Input in its settings left its tab with the old name and didn't mark the
dashboard changed (story `DashboardLayout.KeyValueInputSettingsRename`); and the floor map editor's
Fact and Time lists never showed, their dock having no height in the split pane (story
`FloorMapEditor.FactAndTimeLists`, which fails with the old layout). The full workbench run after
the wave (1,722 stories, 4 workers, with the leak check) passed all 1,722; of their screenshots,
1,646 match, the 11 new are stories with no baseline yet (the 9 above, the user profile's
`ManageUsers` and the processor task summary's `ProcessorFilterTasks`), and of the 65 that differ,
25 are the query editor's, floor map editor's and documentation editor's explained above, and 40
are the ones that differ from run to run (the force visualisations and the data grid's polled
column; see ISSUES.md). What was decided on the way,
where it differs from the plans below, is in the audit above (Left, on purpose) and:

- **Bases.** `DashboardPane` (was the dashboard's `Component`) is what a dashboard holds;
  `DashboardComponentController` and `RefreshableDashboardComponentController` (cancels its refresh
  timer on dispose) are the panes' base; `LayerController`, `SettingsTabController` and
  `BasicSettingsTabController` with `BasicSettingsFields` the settings tabs'.
- **Names.** `ColumnsManager` moved beside the table keeping its name; the query pane's block is
  `query-pane` (the plan's `dashboard-query` broke the naming rule); the table filter's settings are
  `TableFilterInputSettingsController`; the source viewer's story keeps its registered title.
- **Widgetless controllers.** `TabMenuController` (was `TabManager`) takes a `TabActions` callback
  in place of the dashboard; `AnnotationMenuController` (was `AnnotationManager`) is owned by the
  tables, and `TableRowCell` takes a function for its menu items.
- **Layout.** `FlexLayout`, `TabLayout` and `Splitter` have templates and no event bus; a placed
  pane's geometry is `.flex-layout__item` with custom properties. The floor map editor and map are a
  flex column with a fixed-height `floormap-timeline-strip`, not a `DockLayoutPanel`.
- **Models.** The floor map query's static helpers are `FloorMapEventQuery`, a model class beside
  it.
- **Dirty state from tabs.** A tab whose staged state isn't in the written document tells the
  document directly (`setOnChange`), not with `markChanged()`.
- **Test tools.** `PresenterCoverage` sees only `ShowPopupEvent` dialogs, so its report shrinks as
  presenters go; its evidence file is empty and its test no longer needs entries.
- **JSNI.** The workbench GWT compile caught a JSNI reference to a moved class that javac doesn't
  check; moves should grep for `@stroom.` references.

## CSS and template notes for the wave

- settingsPresenter (components.css:1280) is added by every SettingsPresenter subclass (QuerySettings, KeyValueInputSettings, ListInputSettings, TableFilterSettings, TextInputSettings, VisSettings, EmbeddedQuerySettings, plus TableSettings and TextSettings outside this range). Put it once on the root of unit 34's Settings.ui.xml, renamed to the BEM dashboard-settings, and delete the seven addStyleName lines.
- The settings dialogs that only add tabs (units 38, 45, 47, 50, 52, 62 and 68) get a controller only, over the shared Settings component, with no component or template of their own. This also avoids the VisSettings clash with the server's stroom.dashboard.impl.vis.VisSettings.
- Rename classes named after deleted classes: QueryViewImpl -> dashboard-query, QueryViewImpl--buttons -> dashboard-query__buttons (dashboard.css:141), queryViewImpl-expressionTree -> dashboard-query__expression-tree (components.css:341), and KeyValueInputView/ListInputView/TextInputView -> one dashboard-input (dashboard.css:449-451).
- New .visibility-hidden { visibility: hidden; } in stroom.css by .overflow-hidden (stroom.css:859). It hides an element but keeps its space, as QueryPane's errors button needs; no shared class does this yet.
- New .w-200 { width: 200px; } in stroom.css by .w-100 (stroom.css:843): the forms' standard control width (form.css:66-71). It replaces width="200px" in BasicEmbeddedQuerySettings and in eleven other templates (HttpClientConfigViewImpl, ElasticIndexSettingsViewImpl, ElasticClusterSettingsViewImpl).
- A DocSelectionBox slot given width 100% and margin 0 in Java (BasicQuerySettings, BasicListInputSettings, BasicVisSettings, BasicEmbeddedQuerySettings) becomes <dd:DropDownBox addStyleNames="w-100"/>. .drop-down sets no margin, so margin 0 needs no class; check this against the screenshots.
- setBorderStyle(BorderStyle.NONE) on a PagerView-backed list is a no-op: PagerViewImpl.ui.xml's root has no border. Delete it in SelectionHandlers, and also in RulesPresenter.java:106, RuleSetSettings.java:68, DataRetentionPolicyPresenter.java:131 and ContentTemplateTabPresenter.java:147.
- flex-layout.css: new .flex-layout__item (position: absolute; left/top/width/height from the custom properties --flex-layout__left, --flex-layout__top, --flex-layout__width, --flex-layout__height) for TabLayout and Splitter roots. Also new glass modifiers flex-layout__glass--move, --col-resize and --row-resize in place of getStyle().setCursor.
- stroom-floormap.css: new floormap-timeline-strip (height: 110px; flex: none), shared by FloorMapEditor and FloorMapMap. It replaces both DockLayoutPanels, whose fixed 110px north/south slot writes inline position styles.
- New shared dumb row TableFilterColumn (stroom.dashboard.client.component, with TableFilterColumn.ui.xml) holds the table-filter-panel and table-filter-label markup (components.css:2096-2110) that MultiRules and TableFilterInput each build in Java per column.
- Name clashes settled in this range: input/TableFilterPresenter -> TableFilterInput, since unit 25 (table/TableFilterPresenter) is TableFilter in the same package. QueryPresenter -> QueryPane: Query is stroom.query.api.Query among others. SteppingPresenter -> SteppingScreen: Stepping and SteppingController exist on the server. Unit 16's shared query data component must not be QueryData, which is stroom.processor.shared.QueryData; QueryDataPane is suggested.
- The FloorMap*ViewImpl units (63, 65 and 66) are the view halves of units 3, 5 and 6 in this wave. They become those components' templates (FloorMapEditor, FloorMapMap, FloorMapQuery) rather than staying ViewImpls, and unit 64's view is deleted in favour of the shared query data component.
- w-100 (stroom.css:843) replaces every setWidth(100, PCT) on a document picker (BasicTextSettings, BasicTableSettings) once the picker is declared as a DropDownBox / DocSelectionBox with addStyleNames="w-100", as AnalyticStreamDestination.ui.xml does
- max (stroom.css:793) replaces setWidth/setHeight 100% and setSize("100%","100%") (DataPresenter.java:189-196, FloorMapEditorViewImpl.java:152)
- dashboard-panel overflow-hidden (dashboard.css:167, stroom.css overflow-hidden) is added to a child's widget by view setters in MetaViewImpl:51/57/63, QueryEditViewImpl:59, AnalyticDataShardsViewImpl:57 and FloorMapEditorViewImpl:111-133; it moves to addStyleNames on the declared child in each template
- default-min-sizes (components.css:1085) and settingsPresenter (components.css:1280) go on the ComponentSettingsDialog root once, replacing SettingsPresenter.java:48 and the nine subclasses' addStyleName("settingsPresenter")
- New .toolbar--end { margin-left: auto; } in toolbar.css: pushes a toolbar to the end of a toolbar row (FloorMapEditorPresenter.java:342); no Stroom class does it today
- New .stroom-border-none { border-style: none; } in stroom.css beside .stroom-border-*: RulesPresenter.java:106 and 7 other setBorderStyle calls in the client
- New .value-spinner--narrow { width: 200px; } in value-spinner.css: BasicTableSettingsViewImpl.ui.xml:39,49 and about 19 other <ValueSpinner width="200px"> template uses
- New .floormap-timeline-strip { flex: 0 0 110px; } in stroom-floormap.css: replaces the DockLayoutPanel 110px timeline in both FloorMapEditor and FloorMapMap
- New .unknown-component { padding: 5px; } and .dashboard__content--embedded { top: 0; } in dashboard.css
- Template attributes replace Java setters across Text, Source and Data: visible="false" on SpinnerLarge, icon="STEPPING" on FabButton, fade="true" on LayerContainerImpl, svg/title on declared ToolbarButtons
- Not declarable yet: DataGrid (its constructor takes the bus, DataGrid.java:192), PagerViewImpl (injects its Binder), FlexLayout (takes the EventBus); the template moves for QueryResultTable, Table and DashboardBody depend on the 'before the waves' bus and no-arg constructor items
- Tabs added to a LinkTabBar (TabDataImpl) stay in Java everywhere: no widget in the repo uses @UiChild, so there is no declarative tab
- Legacy class names (TablePresenter, TableViewImpl, DashboardViewImpl, dataViewImpl-*, sourceViewImpl-*) are kept as they are so the screenshots match; renaming them is a separate change

## `BasicSettingsTabPresenter`

framework, presenter; complexity 2, effort M; used by 9.

- **Component:** `none (abstract controller bases; each settings tab has its own component)`
- **Controller:** `stroom.dashboard.client.component.BasicSettingsTabController (from BasicSettingsTabPresenter) and stroom.dashboard.client.component.SettingsTabController (from AbstractSettingsTabPresenter)`
- **Template:** `none` → `none (no view of its own)`
- **Goes:** `stroom/dashboard/client/main/AbstractSettingsTabPresenter.java`; `stroom/dashboard/client/main/BasicSettingsTabPresenter.java`; stroom/dashboard/client/main/BasicSettingsView.java (becomes the plain interface stroom.dashboard.client.component.BasicSettingsFields: setId, getName, setName, focus; not a GWTP View)
- **Names:** Thirteen subclasses (ten Basic... tabs plus Rules, MultiRules, SelectionHandlers), so neither base merges. BasicSettingsTab would be an empty component; the controllers BasicSettingsTabController and SettingsTabController are free, and BasicSettingsFields is free.

**Stays in Java:** id and name read/write (BasicSettingsTabPresenter.java read/write/isDirty): data.

## `Splitter`

widget, tidy; complexity 2, effort S; used by 3.

- **Component:** `stroom.dashboard.client.flexlayout.Splitter`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/Splitter.ui.xml`
- **Names:** A widget, so it keeps its name and package (stroom.dashboard.client.flexlayout.Splitter is the only Splitter in all_simple_names.json).

| Into the template | What | Detail |
| --- | --- | --- |
| Splitter.java:54-69 | structure | Splitter.ui.xml, an element template (UiBinder<DivElement, Splitter>, since Splitter extends Widget): <div class="flex-layout__splitter flex-layout__item" role="separator" tabindex="0"><div class="flex-layout__dragger"/></div> |
| Splitter.java:55 | css-class | flex-layout__splitter (and the new flex-layout__item for its position) |
| Splitter.java:57 | aria | role="separator" |
| Splitter.java:66 | css-class | flex-layout__dragger on the child div |

**Stays in Java:** Splitter.java:61-63 aria-orientation vertical/horizontal, from the split's dimension (data, set once); Splitter.java:86-96 aria-label "Resize <panel> and <panel>" or "Resize the dashboard's width/height" (built from the panels' selected tabs, refreshed on focus); Splitter.java:158-161 aria-valuenow/min/max/valuetext (runtime values).

## `TableRowCell`

cell, tidy; complexity 1, effort S; used by 2.

**Since the merge.** HasContextMenus moved to stroom.widget.datagrid.client; DataGrid's new DataGridContextMenu now puts the cell's items first, ahead of Copy, Export Table and Ask Stroom AI. Still holds AnnotationManager; the planned Function<List<TableRow>, List<Item>> supplier fits unchanged.

- **Component:** `stroom.query.client.presenter.TableRowCell`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because it is a grid cell rendering SafeHtml)`
- **Names:** Keeps its name. Its AnnotationManager field becomes a Function<List<TableRow>, List<Item>> menu supplier so it no longer depends on AnnotationMenuController (unit 179).

**Stays in Java:** TableRowCell.java:44-48 renders the row value: data; context menu items: data from the supplier.

## `UnknownComponentPresenter`

tab, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.UnknownComponent`
- **Controller:** `stroom.dashboard.client.component.UnknownComponentController`
- **Template:** `none (HTMLViewImpl.java:30 is a bare new HTML())` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/UnknownComponent.ui.xml (new)`
- **Goes:** `stroom/dashboard/client/unknown/HTMLView.java`; `stroom/dashboard/client/unknown/HTMLViewImpl.java`; `stroom/dashboard/client/gin/DashboardModule.java:62 bindSharedView(HTMLView, HTMLViewImpl)`
- **Names:** No clash ('Component' here is the dashboard's sense; the widget base is stroom.widget.component.client.Component). Extends DashboardComponentController (unit 526).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/unknown/UnknownComponentPresenter.java:39,54 | `setHTML("<div style=\"padding:5px\">...")` | static | New .unknown-component { padding: 5px; } in dashboard.css (no Stroom class pads exactly 5px; form-padding uses the control gap vars and would change the look) |

| Into the template | What | Detail |
| --- | --- | --- |
| UnknownComponentPresenter.java:39 | text | <g:Label ui:field="message" styleName="unknown-component" text="Unknown component"/>; also stops building HTML from the config type (setText, not setHTML) |

**Stays in Java:** 'Unknown component type: ' + componentConfig.getType() (UnknownComponentPresenter.java:54): text from data.

## `TabLayout`

widget, tidy; complexity 3, effort M; used by 7.

- **Component:** `stroom.dashboard.client.flexlayout.TabLayout`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/TabLayout.ui.xml`
- **Goes:** TabLayout.java:74-79 EventBus and TabManager constructor arguments (show-menu out through a callback FlexLayout sets; TabLayout.java:58 eventBus::fireEvent relay goes)
- **Names:** A widget, so it keeps its name and package. FlexLayout makes it in Java, one per tab layout config, so it keeps a constructor with its config. Its template is its own UiBinder, made with GWT.create.

| Into the template | What | Detail |
| --- | --- | --- |
| TabLayout.java:86-109 | structure | TabLayout.ui.xml: <g:FlowPanel styleName="tabLayout flex-layout__item"><g:FlowPanel styleName="tabLayout-contentOuter dashboard-panel"><g:FlowPanel styleName="tabLayout-contentInner"><g:FlowPanel ui:field="barOuter" styleName="tabLayout-barOuter"><g:FlowPanel styleName="tabLayout-tabContainer"><tab:LinkTabBar ui:field="tabBar" addStyleNames="dock-max tabLayout-tabBar"/></g:FlowPanel> ...toolbar... </g:FlowPanel><tab:LayerContainerImpl ui:field="layerContainer" fade="true" styleName="tabLayout-content"/></g:FlowPanel></g:FlowPanel></g:FlowPanel> (LinkTabBar, LayerContainerImpl: wave 3) |
| TabLayout.java:111-117 | component-declaration | <b:Toolbar name="Pane actions" addStyleNames="dock-min tabLayout-buttons icon-colour__grey"><b:ToolbarButton ui:field="settings" svg="ELLIPSES_VERTICAL" title="Tab Actions" opensMenu="true" addStyleNames="tabLayout-settingsButton"/></b:Toolbar> inside barOuter after the tab container (Toolbar, ToolbarButton: wave 1, as MainToolbar.ui.xml:8-11) |
| TabLayout.java:119-121 | structure | layerContainerImpl.setFade(true) and setStyleName("tabLayout-content") become template attributes |

**Stays in Java:** TabLayout.java:124 tabBar.setTabPanel(layerContainer.getElement()) (wiring between two template fields; done once in the constructor); tabs added per component (TabLayout.addTab, data).

## `BasicKeyValueInputSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicKeyValueInputSettings`
- **Controller:** `stroom.dashboard.client.component.BasicKeyValueInputSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/BasicKeyValueInputSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicKeyValueInputSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicKeyValueInputSettingsViewImpl.java`; `BasicKeyValueInputSettingsPresenter.BasicKeyValueInputSettingsView (inner interface, BasicKeyValueInputSettingsPresenter.java:81)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicKeyValueInputSettingsPresenter.java (becomes the controller)`; InputModule.java:58-60 bindPresenterWidget(BasicKeyValueInputSettingsPresenter.class, BasicKeyValueInputSettingsView.class, BasicKeyValueInputSettingsViewImpl.class)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| BasicKeyValueInputSettingsViewImpl.ui.xml (whole) | component-declaration | FormGroup (wave 1) already declared with label and helpText; the template moves as it is |

**Stays in Java:** BasicKeyValueInputSettingsViewImpl.java:55 id.setText(id) (data); BasicKeyValueInputSettingsViewImpl.java:65 name.setText(name) (data).

## `BasicTextInputSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicTextInputSettings`
- **Controller:** `stroom.dashboard.client.component.BasicTextInputSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/BasicTextInputSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicTextInputSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicTextInputSettingsViewImpl.java`; `BasicTextInputSettingsPresenter.BasicTextInputSettingsView (inner interface, BasicTextInputSettingsPresenter.java:85)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicTextInputSettingsPresenter.java (becomes the controller)`; InputModule.java:70-72 bindPresenterWidget(BasicTextInputSettingsPresenter.class, BasicTextInputSettingsView.class, BasicTextInputSettingsViewImpl.class)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| BasicTextInputSettingsViewImpl.ui.xml (whole) | component-declaration | FormGroup (wave 1) already declared with label and helpText; the template moves as it is |

**Stays in Java:** BasicTextInputSettingsViewImpl.java:57 id.setText(id) (data); BasicTextInputSettingsViewImpl.java:67 name.setText(name) (data).

## `QueryResultTabsViewImpl`

composite, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.query.client.component.QueryEdit (merged: its tab bar and layer container are declared in QueryEdit.ui.xml)`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryResultTabsViewImpl.ui.xml` → `none (merged into stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.ui.xml)`
- **Goes:** `stroom/query/client/view/QueryResultTabsViewImpl.java`; `stroom/query/client/view/QueryResultTabsView.java`; `stroom/query/client/gin/QueryModule.java:81-83 bindSharedView(QueryResultTabsView, QueryResultTabsViewImpl)`; `the commented-out field at stroom/dashboard/client/embeddedquery/EmbeddedQueryPresenter.java:102`
- **Names:** Its only user is QueryEditPresenter (:85,110); three elements do not earn a class, so it merges into QueryEdit rather than being kept as a widget named ...ViewImpl. If kept, it would be the dumb widget stroom.query.client.view.QueryResultTabs (free).

| Into the template | What | Detail |
| --- | --- | --- |
| QueryResultTabsViewImpl.ui.xml:4-9 | structure | <g:FlowPanel styleName="QueryResultTabsViewImpl max dock-container-vertical"><g:SimplePanel styleName="QueryResultTabsViewImpl-outer"><tab:LinkTabBar ui:field="resultTabs" addStyleNames="dock-min"/></g:SimplePanel><tab:LayerContainerImpl ui:field="resultLayers" styleName="dock-max" fade="true"/></g:FlowPanel> in QueryEdit's results pane |
| QueryResultTabsViewImpl.java:41 | structure | layerContainer.setFade(true) becomes fade="true" |

**Stays in Java:** tabBar.setTabPanel(layerContainer.getElement()) (QueryResultTabsViewImpl.java:40): takes an Element, done in QueryEdit's constructor.

## `FloorMapEditorViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapEditor (merged into unit 3's component)`
- **Controller:** `stroom.floormap.client.component.FloorMapEditorController (unit 3)`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapEditorViewImpl.java`; `FloorMapEditorPresenter.FloorMapEditorView (inner interface, FloorMapEditorPresenter.java:2174)`; `FloorMapModule.java:113-116 bindPresenterWidget(FloorMapEditorPresenter.class, FloorMapEditorView.class, FloorMapEditorViewImpl.class)`
- **Names:** This is the view half of unit 3 (FloorMapEditorPresenter -> FloorMapEditor + FloorMapEditorController, this wave). The plan to keep it as FloorMapEditorViewImpl would break rule 1, so it becomes unit 3's FloorMapEditor component. Both names are free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapEditorViewImpl.java:152 | `outerSplitPane.setSize("100%", "100%")` | static | max (stroom.css:793) on the root SplitPane |
| FloorMapEditorViewImpl.java:144-145 | `new DockLayoutPanel(Unit.PX); addNorth(timelinePanel, 110)` | static | DockLayoutPanel writes inline position styles: use <g:FlowPanel styleName="max dock-container-vertical"> with the timeline panel classed floormap-timeline-strip (new in stroom-floormap.css: height: 110px; flex: none), and the bottom SplitPane classed dock-max |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapEditorViewImpl.java:108-158 | structure | FloorMapEditor.ui.xml: <l:SplitPane ui:field="outerSplitPane" vertical="true" thin="true" sizes="*, 33%" names="Map and Controls, Timeline and Lists" addStyleNames="max stroom-border-top"> holding <l:SplitPane ui:field="topSplitPane" thin="true" sizes="*, 200px" names="Map, Controls"> (canvasPanel, dockPanel) and the bottom FlowPanel (timelinePanel, then <l:SplitPane thin="true" sizes="50%, *" names="Fact List, Time List"> with factListPanel and timeListPanel); SplitPane is wave 2 |
| FloorMapEditorViewImpl.java:111, 114, 130, 133 | css-class | dashboard-panel overflow-hidden on the canvas, dock, fact list and time list SimplePanels |
| FloorMapEditorViewImpl.java:126 | css-class | dashboard-panel overflow-hidden stroom-border-bottom floormap-timeline-strip on the timeline panel |
| FloorMapEditorViewImpl.java:154 | css-class | stroom-border-top on the outer SplitPane |
| FloorMapEditorViewImpl.java:119, 139, 156 | aria | SplitPane names ("Map, Controls", "Fact List, Time List", "Map and Controls, Timeline and Lists") as template attributes |

**Stays in Java:** FloorMapEditorViewImpl.java:162-185 the five slots (canvas, dock, timeline, fact list, time list) hold unit 3's child presenters, which have not been split yet (a slot per child until they convert); FloorMapEditorViewImpl.java:188-191 setDockVisible -> topSplitPane.setPaneHidden (state).

## `FloorMapMapViewImpl`

composite, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapMap (merged into unit 5's component)`
- **Controller:** `stroom.floormap.client.component.FloorMapMapController (unit 5)`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapMapViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapMapViewImpl.java`; `FloorMapMapPresenter.FloorMapMapView (inner interface, FloorMapMapPresenter.java:1784)`; `FloorMapModule.java:117-119 bindPresenterWidget(FloorMapMapPresenter.class, FloorMapMapView.class, FloorMapMapViewImpl.class)`
- **Names:** The view half of unit 5 (FloorMapMapPresenter -> FloorMapMap, this wave). It becomes that component rather than staying a ViewImpl. The names are free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapMapViewImpl.ui.xml:21-24 | `<g:DockLayoutPanel unit='PX'> <g:south size="110">` | static | DockLayoutPanel writes inline position and size styles: use <g:FlowPanel styleName="max stroom-border-top dock-container-vertical"> with the SplitPane (dock-max) first and the timeline SimplePanel classed floormap-timeline-strip (the new class shared with FloorMapEditor, stroom-floormap.css) last |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapMapViewImpl.ui.xml:21-37 | structure | the template moves beside FloorMapMap.java, with the DockLayoutPanel replaced as in styles; the comment at 22-23 that cites FloorMapEditorViewImpl's TIMELINE_HEIGHT now cites the shared floormap-timeline-strip class |
| FloorMapMapViewImpl.ui.xml:29 | component-declaration | SplitPane (wave 2) already declared with sizes and names; kept |

**Stays in Java:** FloorMapMapViewImpl.java:56-68 map, dock and timeline slots (unit 5's child presenters, not yet split); FloorMapMapViewImpl.java:71-74 setDockVisible (state).

## `SettingsPresenter`

dialog, presenter; complexity 2, effort M; used by 11.

- **Component:** `stroom.dashboard.client.component.ComponentSettingsDialog`
- **Controller:** `stroom.dashboard.client.component.ComponentSettingsDialogController`
- **Template:** `none (uses the shared LinkTabsLayoutView, stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view/LinkTabsLayoutViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ComponentSettingsDialog.ui.xml (new)`
- **Goes:** `stroom/dashboard/client/main/SettingsPresenter.java (moved and renamed)`
- **Names:** Settings is free but ambiguous beside the shared DTOs stroom.dashboard.shared.ComponentSettings and stroom.query.api.TableSettings that its subclasses import; ComponentSettings itself is taken. ComponentSettingsDialog / ComponentSettingsDialogController are free. Nine subclasses become controller-only (TextSettingsController, TableSettingsController, QuerySettingsController, ...). The shared LinkTabsLayoutView binding (DashboardModule.java:61) stays for its other users.

| Into the template | What | Detail |
| --- | --- | --- |
| SettingsPresenter.java:48 and the nine subclasses' addStyleName("settingsPresenter") (e.g. TextSettingsPresenter.java:32, TableSettingsPresenter.java:38) | css-class | <g:FlowPanel styleName="max dock-container-vertical default-min-sizes settingsPresenter"> root (components.css:1085, :1280) |
| LinkTabsLayoutViewImpl.ui.xml:5-6 | structure | <tab:LinkTabBar ui:field="tabBar" addStyleNames="dock-min"/> and <tab:LayerContainerImpl ui:field="layers" styleName="dock-max"/> |

**Stays in Java:** addTab(text, layer) (SettingsPresenter.java:71-85) and tab hiding: tabs are added in Java by each subclass; read/write/validate/isDirty over the tabs: data.

## `FlexLayout`

widget, tidy; complexity 5, effort XL; used by 6.

- **Component:** `stroom.dashboard.client.flexlayout.FlexLayout`
- **Controller:** `none`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.ui.xml`
- **Goes:** FlexLayout.java:110 @Inject EventBus constructor argument (replace with a no-arg constructor and setOnShowTabMenu / setOnShowMenu callbacks; TabLayout's show-menu relay at TabLayout.java:58 too); `FlexLayout.java:154 setTabManager(TabManager) (replace with callbacks set by the dashboard's controller, which owns TabMenuController)`
- **Names:** A widget, so it keeps its name and package. FlexLayoutChangeHandler, FocusMemory, LayoutSizes, PlacementKeys and PositionAndSize are its helpers and stay beside it.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FlexLayout.java:969 (callers 946, 953, 960, 963) | `glass.getElement().getStyle().setCursor(cursor)` | static | modifier classes on the glass from a fixed set: flex-layout__glass--move, flex-layout__glass--col-resize, flex-layout__glass--row-resize (default = none), new in flex-layout.css next to .flex-layout__glass (flex-layout.css:112); setGlassCursor becomes setGlassMode, which swaps the class |
| FlexLayout.java:1814 | `element.getStyle().setPosition(Position.ABSOLUTE)` | static | position: absolute in a new .flex-layout__item class (flex-layout.css), given to every TabLayout and Splitter root; .flex-layout__splitter (flex-layout.css:31-32) already has it |
| FlexLayout.java:1815-1818 | `setLeft/setTop/setWidth/setHeight(positionAndSize..., Unit.PX)` | dynamic | custom properties --flex-layout__left, --flex-layout__top, --flex-layout__width, --flex-layout__height set with style.setProperty(name, value + "px"); .flex-layout__item { left: var(--flex-layout__left); top: var(--flex-layout__top); width: var(--flex-layout__width); height: var(--flex-layout__height); } in flex-layout.css |

| Into the template | What | Detail |
| --- | --- | --- |
| FlexLayout.java:120-143 | structure | FlexLayout.ui.xml: <g:SimplePanel styleName="dashboard-outerPanel"><g:SimplePanel ui:field="scrollPanel" styleName="dashboard-scrollPanel"><g:SimplePanel ui:field="layout" styleName="dashboard-layout"><g:FlowPanel ui:field="designSurface" styleName="dashboard-designSurface"/></g:SimplePanel></g:SimplePanel></g:SimplePanel> (dashboard.css:114-152); the anonymous SimplePanel's resize observer (123-135) moves to FlexLayout.onLoad/onUnload on the layout field, and the scroll handler (140) is added to the scrollPanel field |

**Stays in Java:** FlexLayout.java:113-118 the shared static Glass overlays (flex-layout__glass, flex-layout__marker), which live on the document body, not in the widget; FlexLayout.java:292, 435 flexLayout-selected (state); FlexLayout.java:943-964, 1435, 1444, 1536 flex-layout__marker--splitter/--down/--across/--tab (state); FlexLayout.java:1510-1517 dashboard-scrollPanel--horizontal-scroll/--vertical-scroll (state); FlexLayout.java:476, 879 glass title PlacementKeys.HELP set only while placing, then cleared (state); FlexLayout.java:1763, 1797 one TabLayout per tab layout config and one Splitter per split (repetition from the dashboard's layout data); FlexLayout.java:1815-1818 the four custom property values (layout geometry).

## `AbstractComponentPresenter`

framework, presenter; complexity 3, effort L; used by 10.

- **Component:** `none (an abstract controller base; each pane has its own component)`
- **Controller:** `stroom.dashboard.client.component.DashboardComponentController and stroom.dashboard.client.component.RefreshableDashboardComponentController`
- **Template:** `none` → `none (no view of its own)`
- **Goes:** `stroom/dashboard/client/main/AbstractComponentPresenter.java`; `stroom/dashboard/client/main/AbstractRefreshableComponentPresenter.java`
- **Names:** AbstractComponent would read as a base of stroom.widget.component.client.Component and sits next to the dashboard's own stroom.dashboard.client.main.Component interface; DashboardComponentController (free) says it is a controller of a dashboard component. Ten subclasses, so it is not merged; AbstractRefreshableComponentPresenter (three subclasses: Table, Query, EmbeddedQuery) becomes RefreshableDashboardComponentController (free).

**Stays in Java:** settings dialog shown with caption 'Settings' and PopupSize 800x700 (AbstractComponentPresenter.java:95-127): the controller shows ComponentSettingsDialog with makeDialog instead of own(provider.get()); design mode, tab config, component config: state and data.

## `TabManager`

framework, smart class; complexity 3, effort M; used by 3.

- **Component:** `none (no widget of its own: it fills the shared menu through ShowMenuEvent / MenuManager)`
- **Controller:** `stroom.dashboard.client.component.TabMenuController`
- **Template:** `none` → `none (built in Java because it only builds menu items, which are data for the shared Menu widget, and fires the rename dialog)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/main/TabManager.java (moved and renamed; no GIN binding: DashboardPresenter.java:170 makes it with new TabManager(components, () -> makeDialog(renameTabPresenterProvider), this))
- **Names:** TabManager + TabManagerController (the suggested pair) gives a component with nothing to show. TabMenuController says what it does (the tab's context menu) and is free in all_simple_names.json and in this wave. It is not a dumb component. It also shares nothing with stroom.document.client.DocumentTabManager. FlexLayout and TabLayout take a setOnShowTabMenu(callback) instead of the class itself, so the controller is given callbacks (duplicateTabTo, showSettings, rename) rather than the DashboardPresenter.

**Stays in Java:** TabManager.java:97-105 ShowMenuEvent name "Actions for tab '<component name>'" (built from data); TabManager.java:156 AlertEvent.fireError "You cannot remove or hide all tabs" (controller message); TabManager.java:234-385 menu item text (Rename, Settings, Hide Tab, Show Hidden Tab, Duplicate, Duplicate to Another Dashboard..., Duplicate All Tabs in Pane, Remove, Remove All Tabs in Pane, Maximise, Restore Pane Layout, Show Table/Show Visualisation, Edit Query, Run Query): menu items are data handed to the shared Menu widget, not markup; hidden-tab item text is the component's name (data); TabManager.java:84-88 popup position from the target's Rect (runtime).

## `KeyValueInputSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34; no component of its own)`
- **Controller:** `stroom.dashboard.client.component.KeyValueInputSettingsController`
- **Template:** `none` → `none (built in Java because it only adds one tab to the shared Settings component, unit 34's Settings.ui.xml)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/input/KeyValueInputSettingsPresenter.java (becomes KeyValueInputSettingsController; no GIN binding); `KeyValueInputSettingsPresenter.java:32 addStyleName("settingsPresenter")`
- **Names:** KeyValueInputSettingsController is free; no KeyValueInputSettings component is made.

| Into the template | What | Detail |
| --- | --- | --- |
| KeyValueInputSettingsPresenter.java:32 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings (components.css:1280) |

**Stays in Java:** addTab("Basic", ...) tab label (TabbedComponent convention).

## `TextInputSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34)`
- **Controller:** `stroom.dashboard.client.component.TextInputSettingsController`
- **Template:** `none` → `none (built in Java because it only adds one tab to the shared Settings component)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/input/TextInputSettingsPresenter.java (becomes TextInputSettingsController; no GIN binding); `TextInputSettingsPresenter.java:32 addStyleName("settingsPresenter")`
- **Names:** TextInputSettingsController is free; no TextInputSettings component is made.

| Into the template | What | Detail |
| --- | --- | --- |
| TextInputSettingsPresenter.java:32 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings |

**Stays in Java:** addTab("Basic", ...) tab label.

## `DataPresenter`

composite, presenter; complexity 5, effort XL; used by 8.

- **Component:** `stroom.data.client.component.DataViewer`
- **Controller:** `stroom.data.client.component.DataViewerController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/DataViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.ui.xml`
- **Goes:** `DataView interface (DataPresenter.java:1304)`; `stroom/data/client/view/DataViewImpl.java`; `stroom/data/client/gin/StreamStoreModule.java:109-112 bindPresenterWidget(DataPresenter, DataView, DataViewImpl)`
- **Names:** Data clashes with stroom.pipeline.xml.converter.ds3.Data and stroom.query.language.functions.Data; DataViewer / DataViewerController are free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/data/client/presenter/DataPresenter.java:189-196 | `markerListPresenter.getWidget().getElement().getStyle().setWidth(100, PCT);…` | static | max (stroom.css:793): on the MarkerList template root (wave 10) or, as it is a layer of layerContainer, addStyleName("max") once on the MarkerList widget |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/data/client/view/DataViewImpl.java:65 | structure | visible="false" on <spinner:SpinnerLarge> |
| stroom/data/client/view/DataViewImpl.java:66 | structure | fade="true" on <tab:LayerContainerImpl> |
| stroom/data/client/view/DataViewImpl.java:67-68 | text | text="View Source" on the source link |
| stroom/data/client/view/DataViewImpl.java:70-71 | aria | role="button" tabindex="0": declare the link as an HTMLPanel <span ui:field="sourceLink" class="dataViewImpl-sourceLinkLabel clickableLabel" role="button" tabindex="0">View Source</span> (g:Label takes no role attribute), keeping the Enter/Space handler in Java |
| DataPresenter.java:213-214 / ui.xml:15,21 | component-declaration | <d:ItemNavigator ui:field="navigator"/> (wave 4) and <pr:Progress ui:field="progress" addStyleNames="dock-min dataViewImpl-progressBarPanel"/> (wave 3) instead of the two slots |
| DataPresenter.java:179 | component-declaration | <d:ClassificationWrapper> (wave 4) as the template root around the data layout instead of super(eventBus, classificationWrapperView) + setContent(dataView) |

**Stays in Java:** tabs Info, Error, Data Preview, Meta, Context (DataPresenter.java:106-110, 204-208) and their hidden state: LinkTabBar tabs are added in Java; source link visible/enabled and its 'enabled' class (DataViewImpl.java:92, DataPresenter.java:1018-1022): state; Html (wave 3), Text (wave 8 data) and MarkerList (wave 10): layers swapped in layerContainer, so made in Java; tabBar.setTabPanel(layerContainer.getElement()) (DataViewImpl.java:64): Element argument; pager unit text Record/Page/Part (DataPresenter.java:102-104): chosen at runtime.

## `QueryResultVisPresenter`

composite, presenter; complexity 4, effort L; used by 6.

- **Component:** `stroom.query.client.component.QueryResultVis`
- **Controller:** `stroom.query.client.component.QueryResultVisController`
- **Template:** `none (QueryResultVisViewImpl.java:50-89 builds it in Java)` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVis.ui.xml (new)`
- **Goes:** `QueryResultVisView interface (QueryResultVisPresenter.java:688)`; `stroom/query/client/view/QueryResultVisViewImpl.java`; `stroom/query/client/gin/QueryModule.java:92-95 bindPresenterWidget(QueryResultVisPresenter, QueryResultVisView, QueryResultVisViewImpl)`
- **Names:** No clash. The component keeps the onResize positioning (it is layout of its own frame) and its attach/detach hooks become Composite onLoad/onUnload instead of an anonymous FlowPanel (QueryResultVisViewImpl.java:64-86).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/query/client/presenter/QueryResultVisPresenter.java:166-167 | `// getStyle().setOpacity(opacity) (commented out)` | static | Delete the dead commented-out setOpacity block (:160-175); nothing to replace |
| stroom/query/client/view/QueryResultVisViewImpl.java:125-151 (into VisFrame.java:112,124) | `visFrame.setContainerPositionAndSize / setInnerPositionAndSize` | dynamic | Owned by VisFrame (wave 5): --vis-frame-left/top/width/height and --vis-inner-left/top/width/height custom properties used by .VisFrame-container and .VisFrame-frame (dashboard.css:478,487); this unit just passes the rectangle |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/query/client/view/QueryResultVisViewImpl.java:56,87-89 | structure | <g:FlowPanel styleName="queryVis-outerLayout dock-container-vertical"> with <v:VisToolbarRow ui:field="toolbarRow"/> (wave 3) then the body |
| stroom/query/client/view/QueryResultVisViewImpl.java:51-62 | structure | <g:FlowPanel styleName="dock-max visBody"><g:SimplePanel ui:field="visContainer" styleName="queryVis-innerLayout"/><dg:MessagePanelImpl ui:field="messagePanel"/></g:FlowPanel> (MessagePanelImpl, wave 5, stroom.widget.datagrid.client) |

**Stays in Java:** VisFrame itself (QueryResultVisPresenter.java:122-136): laid over RootPanel and positioned to visContainer, so not in the template; its title 'Visualisation' (:124) stays with it; visFrame.setClassName(getClassName(theme)) (:144): theme state; vis buttons (setVisButtons) and messages: data; The record's PagerView and RefreshButton declarations are wrong: the RefreshButton is inside VisToolbarRow and there is no PagerView.

## `KeyValueInputPresenter`

tab, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.dashboard.client.component.KeyValueInput`
- **Controller:** `stroom.dashboard.client.component.KeyValueInputController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/KeyValueInputViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/KeyValueInput.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/KeyValueInputViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/KeyValueInputUiHandlers.java`; `KeyValueInputPresenter.KeyValueInputView (inner interface, KeyValueInputPresenter.java:114)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/KeyValueInputPresenter.java (becomes the controller)`; `InputModule.java:57 bindPresenterWidget(KeyValueInputPresenter.class, KeyValueInputView.class, KeyValueInputViewImpl.class)`
- **Names:** No clash. KeyValueInputPlugin (InputModule.java:56) stays, and makes the controller.

| Into the template | What | Detail |
| --- | --- | --- |
| KeyValueInputViewImpl.ui.xml:5 | css-class | KeyValueInputView (named after the deleted view) -> dashboard-input, shared with ListInput and TextInput (one rule at dashboard.css:449-451 becomes .dashboard-input) |
| KeyValueInputViewImpl.ui.xml:6 | component-declaration | TextBox params kept; its change goes out through setOnChange(Consumer<String>) in place of KeyValueInputUiHandlers |

**Stays in Java:** KeyValueInputViewImpl.java:59-60 / KeyValueInputPresenter.java:76,93 AccessibleName.set(params, component name) (the component's name, data); params text (value).

## `TextInputPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.TextInput`
- **Controller:** `stroom.dashboard.client.component.TextInputController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/TextInputViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/TextInput.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/TextInputViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/TextInputUiHandlers.java`; `TextInputPresenter.TextInputView (inner interface, TextInputPresenter.java:125)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/TextInputPresenter.java (becomes the controller)`; `InputModule.java:69 bindPresenterWidget(TextInputPresenter.class, TextInputView.class, TextInputViewImpl.class)`
- **Names:** No clash. TextInputPlugin stays.

| Into the template | What | Detail |
| --- | --- | --- |
| TextInputViewImpl.ui.xml:5 | css-class | TextInputView -> dashboard-input (shared rule, dashboard.css:449-451) |
| TextInputViewImpl.ui.xml:6 | component-declaration | TextBox value kept; its change goes out through setOnChange(Consumer<String>) in place of TextInputUiHandlers |

**Stays in Java:** TextInputViewImpl.java:59-60 / TextInputPresenter.java:89 AccessibleName.set(value, component name) (data).

## `AnnotationManager`

plugin-or-infrastructure, smart class; complexity 3, effort M; used by 3.

- **Component:** `none (no widget of its own)`
- **Controller:** `stroom.query.client.component.AnnotationMenuController`
- **Template:** `none` → `none (no widget: it builds the Annotate menu and opens dialogs)`
- **Goes:** `stroom/query/client/presenter/AnnotationManager.java (moved and renamed)`
- **Names:** AnnotationManager + AnnotationManagerController would leave an empty 'component'; it is one controller. AnnotationMenuController names what it does and is free. Owned by QueryResultTableController and TableController (make(provider)); its dialogs use the owner's makeDialog (AnnotationManager.java:428,434 use appRoot.makeDialog) and its REST calls get .owner(this).

**Stays in Java:** menu items Edit/Create/Add to/Change status/assigned (AnnotationManager.java:285-323): menu data built per selection; CreateAnnotationEvent / EditAnnotationEvent / ShowFindAnnotationEvent (:347-421): app-wide requests.

## `BasicQuerySettingsPresenter`

tab, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicQuerySettings`
- **Controller:** `stroom.dashboard.client.component.BasicQuerySettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/BasicQuerySettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicQuerySettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/BasicQuerySettingsViewImpl.java`; `BasicQuerySettingsPresenter.BasicQuerySettingsView (inner interface, BasicQuerySettingsPresenter.java:143)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/BasicQuerySettingsPresenter.java (becomes the controller)`; QueryModule.java:60-61 bindPresenterWidget(BasicQuerySettingsPresenter.class, BasicQuerySettingsView.class, BasicQuerySettingsViewImpl.class)
- **Names:** No clash: BasicQuerySettings and BasicQuerySettingsController are free in the repo and in this wave. It extends the base unit 32 (BasicSettingsTab) makes. It does not use BasicSettingsView.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BasicQuerySettingsViewImpl.java:83 | `w.getElement().getStyle().setWidth(100, Unit.PCT)` | static | w-100 (stroom.css:843) on the declared <dd:DropDownBox ui:field="dataSource" addStyleNames="w-100"/> |
| BasicQuerySettingsViewImpl.java:84 | `w.getElement().getStyle().setMargin(0, Unit.PX)` | static | none needed: .drop-down (drop-down.css) sets no margin, so the declared DropDownBox has none. Check it against the screenshots. |

| Into the template | What | Detail |
| --- | --- | --- |
| BasicQuerySettingsViewImpl.ui.xml:17 + BasicQuerySettingsViewImpl.java:81-86 setDataSourceSelectionView | component-declaration | <dd:DropDownBox ui:field="dataSource" addStyleNames="w-100"/> in place of the SimplePanel slot; the controller does make(docSelectionControllerProvider).attach(view.getDataSource()) instead of owning a DocSelectionBoxPresenter (DocSelectionBox, wave 8) |
| BasicQuerySettingsViewImpl.ui.xml:20,23 | component-declaration | <v:TickBox ui:field="queryOnOpen"/> and "autoRefresh" already declared (wave 2): keep them, and use setOnChange instead of ValueChangeEvent |
| BasicQuerySettingsViewImpl.ui.xml:7-29 | component-declaration | FormGroup (wave 1) already declared with label, helpText and screenReaderText: keep them as they are |

**Stays in Java:** BasicQuerySettingsViewImpl.java:67 id.setText(id) (component id, data); BasicQuerySettingsViewImpl.java:77 name.setText(name) (data); BasicQuerySettingsViewImpl.java:115 refreshInterval.setText(...) (data).

## `BasicListInputSettingsPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicListInputSettings`
- **Controller:** `stroom.dashboard.client.component.BasicListInputSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/BasicListInputSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicListInputSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicListInputSettingsViewImpl.java`; `BasicListInputSettingsPresenter.BasicListInputSettingsView (inner interface, BasicListInputSettingsPresenter.java:116)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicListInputSettingsPresenter.java (becomes the controller)`; InputModule.java:64-66 bindPresenterWidget(BasicListInputSettingsPresenter.class, BasicListInputSettingsView.class, BasicListInputSettingsViewImpl.class)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| BasicListInputSettingsViewImpl.ui.xml:31 + BasicListInputSettingsViewImpl.java:119-120 setDictionaryView | component-declaration | <dd:DropDownBox ui:field="dictionary" addStyleNames="w-100"/> in place of the SimplePanel slot; the controller attaches a DocSelectionController (DocSelectionBox, wave 8) instead of owning a DocSelectionBoxPresenter |
| BasicListInputSettingsViewImpl.ui.xml:26,34 | component-declaration | TickBox useDictionary and allowTextEntry already declared (wave 2); use setOnChange |
| BasicListInputSettingsViewImpl.ui.xml:26 | aria | identity="basicListInputSettingsExtractValues" on useDictionary -> basicListInputSettingsUseDictionary (fixed text that names the wrong field) |

**Stays in Java:** BasicListInputSettingsViewImpl.java:73 id.setText(id) (data); BasicListInputSettingsViewImpl.java:83 name.setText(name) (data).

## `LinkedEventPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.annotation.client.component.LinkedEvents`
- **Controller:** `stroom.annotation.client.component.LinkedEventsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/LinkedEventViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEvents.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/LinkedEventViewImpl.java`; `LinkedEventPresenter.LinkedEventView (inner interface, LinkedEventPresenter.java:214)`; `stroom-core-client/src/main/java/stroom/annotation/client/LinkedEventPresenter.java (becomes the controller)`; `AnnotationModule.java:80 bindPresenterWidget(LinkedEventPresenter.class, LinkedEventView.class, LinkedEventViewImpl.class)`
- **Names:** LinkedEvent (the suggested name) is free, but it reads as one event. The pane is a list of an annotation's linked events beside the selected one's data, so it becomes LinkedEvents / LinkedEventsController, which are free. Do not confuse it with the shared DTO stroom.annotation.shared.LinkEvents, which this class uses at LinkedEventPresenter.java:107. The DataPresenter it embeds is unit 18's Data, which clashes with the server's Data classes; that is unit 18's to settle.

| Into the template | What | Detail |
| --- | --- | --- |
| LinkedEventViewImpl.ui.xml:5-6 | structure | SplitPane (wave 2, vertical, thin, sizes="400px, *", names="Linked events, Data", max) kept as the root |
| LinkedEventViewImpl.ui.xml:7 + LinkedEventPresenter.java:77-80, 97 | component-declaration | <dg:PagerPanel ui:field="eventList" addStyleNames="max"> holding <dg:DataGrid ui:field="eventGrid" tableName="Linked Events"/> (PagerView, DataGrid: wave 5, stroom.widget.datagrid.client) in place of the SimplePanel slot and the injected PagerView; this needs the template-ready PagerView and the bus-free DataGrid from wave 5 |
| LinkedEventPresenter.java:91-95 | aria | toolbar name "Linked event actions" and <b:ToolbarButton svg="ADD" title="Add Event Link" enabled="false"/>, <b:ToolbarButton svg="DELETE" title="Remove Event Link" enabled="false"/> declared in the PagerView's toolbar, if wave 5's PagerView takes toolbar children; else the component adds them, with setOnAction |
| LinkedEventViewImpl.ui.xml:8 + LinkedEventPresenter.java:98 | component-declaration | <dc:Data ui:field="data" addStyleNames="max"/> (unit 18, this wave) in place of the SimplePanel slot, with its controller attached, if unit 18 lands first; else a setDataView(IsWidget) slot |
| declares | component-declaration | Checked: AddEventLink (wave 9) is a dialog opened with makeDialog. It is not declared. ButtonView is the pager toolbar's buttons (above). |

**Stays in Java:** LinkedEventPresenter.java:152 dataGrid.setTableName("Annotation '<name>' Events") (built from data); LinkedEventPresenter.java:100-103 Id column (columns are data-grid config built in Java); enabled state of Add and Remove.

## `BasicEmbeddedQuerySettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicEmbeddedQuerySettings`
- **Controller:** `stroom.dashboard.client.component.BasicEmbeddedQuerySettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/embeddedquery/BasicEmbeddedQuerySettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicEmbeddedQuerySettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/BasicEmbeddedQuerySettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/BasicEmbeddedQuerySettingsUiHandlers.java`; `BasicEmbeddedQuerySettingsPresenter.BasicEmbeddedQuerySettingsView (inner interface, BasicEmbeddedQuerySettingsPresenter.java:246)`; `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/BasicEmbeddedQuerySettingsPresenter.java (becomes the controller)`; EmbeddedQueryModule.java:39-41 bindPresenterWidget(BasicEmbeddedQuerySettingsPresenter.class, BasicEmbeddedQuerySettingsView.class, BasicEmbeddedQuerySettingsViewImpl.class)
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BasicEmbeddedQuerySettingsViewImpl.java:102 | `w.getElement().getStyle().setWidth(100, Unit.PCT)` | static | w-100 (stroom.css:843) on the declared <dd:DropDownBox ui:field="queryRef" addStyleNames="w-100"/> |
| BasicEmbeddedQuerySettingsViewImpl.java:103 | `w.getElement().getStyle().setMargin(0, Unit.PX)` | static | none needed: .drop-down sets no margin (drop-down.css) |
| BasicEmbeddedQuerySettingsViewImpl.ui.xml:46 | `<valuespinner:ValueSpinner ui:field="pageSize" width="200px"/>` | static | new utility .w-200 { width: 200px; } in stroom.css next to .w-100 (stroom.css:843). 200px is the forms' standard control width (form.css:66-71), and no class sets it. The same width="200px" is in HttpClientConfigViewImpl.ui.xml:51,58,71,87, ElasticIndexSettingsViewImpl.ui.xml:23,60,67,79 and ElasticClusterSettingsViewImpl.ui.xml:19,21. |

| Into the template | What | Detail |
| --- | --- | --- |
| BasicEmbeddedQuerySettingsViewImpl.ui.xml:25 + BasicEmbeddedQuerySettingsViewImpl.java:100-105 setQuerySelectionView | component-declaration | <dd:DropDownBox ui:field="queryRef" addStyleNames="w-100"/> in place of the SimplePanel slot; the controller attaches a DocSelectionController (DocSelectionBox, wave 8) |
| BasicEmbeddedQuerySettingsViewImpl.ui.xml:27 | component-declaration | Button copyQuery already declared (wave 1); setOnClick in place of BasicEmbeddedQuerySettingsUiHandlers. DocSelectionPopup (wave 8) is a popup opened by the controller and is not declared. |
| BasicEmbeddedQuerySettingsViewImpl.ui.xml:9,13,18,22,30,35,38,43 | aria | identity values copied from other forms (basicQuerySettingsId, basicQuerySettingsName, basicQuerySettingsLocal, basicQuerySettingsQueryRef, basicQuerySettingsQueryOnOpen, basicQuerySettingsAutoRefresh, basicQuerySettingsRefreshInterval, basicTableSettingsPageSize) -> basicEmbeddedQuerySettings*: fixed text that repeats the ids of BasicQuerySettings and BasicTableSettings |
| BasicEmbeddedQuerySettingsViewImpl.ui.xml:18,29,34,46 | component-declaration | TickBox x3 and ValueSpinner (wave 2) already declared; kept, with setOnChange |

**Stays in Java:** BasicEmbeddedQuerySettingsViewImpl.java:86, 96, 151 id, name and refresh interval text (data); BasicEmbeddedQuerySettingsViewImpl.java:120 queryRefFormGroup section--disabled (state class); BasicEmbeddedQuerySettingsViewImpl.java:121 copyQuery.setEnabled(!reference) (state).

## `BasicTextSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicTextSettings`
- **Controller:** `stroom.dashboard.client.component.BasicTextSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/text/BasicTextSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicTextSettings.ui.xml`
- **Goes:** `stroom/dashboard/client/text/BasicTextSettingsPresenter.java (BasicTextSettingsView interface at :237)`; `stroom/dashboard/client/text/BasicTextSettingsViewImpl.java`; `stroom/dashboard/client/text/BasicTextSettingsUiHandlers.java (onTableChange becomes setOnTableChange(Runnable))`; stroom/dashboard/client/text/gin/TextModule.java:34-35 bindPresenterWidget(BasicTextSettingsPresenter, BasicTextSettingsView, BasicTextSettingsViewImpl)
- **Names:** No clash in all_simple_names.json or in this wave. Its base BasicSettingsTabPresenter (unit 528) becomes the abstract BasicSettingsTabController, so the controller extends that; the component implements the small BasicSettingsFields interface (setId/getName/setName) that replaces BasicSettingsView.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/text/BasicTextSettingsViewImpl.java:245 | `w.getElement().getStyle().setWidth(100, Unit.PCT)` | static | w-100 (stroom.css:843) on the declared picker in the template: <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/> (as AnalyticStreamDestination.ui.xml:12) |
| stroom/dashboard/client/text/BasicTextSettingsViewImpl.java:246 | `w.getElement().getStyle().setMargin(0, Unit.PX)` | static | Nothing: a DropDownBox declared in a FormGroup has no margin (the pilot's AnalyticStreamDestination.ui.xml sets none); it only cancelled DocSelectionBoxPresenter's view margin. Check with the screenshot diff |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/dashboard/client/text/BasicTextSettingsViewImpl.ui.xml:58 | component-declaration | Replace <g:SimplePanel ui:field="pipeline"/> with <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/> (wave 8 DocSelectionBox when it lands); the controller does make(docSelectionControllerProvider).attach(view.getPipelineBox()) instead of BasicTextSettingsPresenter.java:54-62 (setIncludedTypes(PipelineDoc.TYPE) and setRequiredPermissions(USE) go on the controller) |
| stroom/dashboard/client/text/BasicTextSettingsViewImpl.ui.xml:8-65 | component-declaration | FormGroup, SelectionBox x8, TickBox x2 are already declared; keep them |

**Stays in Java:** SelectionBox items (Any, blank and the table/column lists) set from the dashboard context: BasicTextSettingsViewImpl.java:47-48 constants and setTableList/setColumns; id label text (setId) and name text: data; table change callback (was BasicTextSettingsUiHandlers.onTableChange) wired by the controller.

## `BasicTableSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicTableSettings`
- **Controller:** `stroom.dashboard.client.component.BasicTableSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/BasicTableSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicTableSettings.ui.xml`
- **Goes:** `BasicTableSettingsView interface (BasicTableSettingsPresenter.java:306)`; `stroom/dashboard/client/table/BasicTableSettingsViewImpl.java`; `stroom/dashboard/client/table/BasicTableSettingsUihandlers.java`; stroom/dashboard/client/table/gin/TableModule.java:68-71 bindPresenterWidget(BasicTableSettingsPresenter, BasicTableSettingsView, BasicTableSettingsViewImpl)
- **Names:** No clash (stroom.query.api.TableSettings is a different name). Extends BasicSettingsTabController (unit 528).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/table/BasicTableSettingsViewImpl.java:152 | `widget.getElement().getStyle().setWidth(100, Unit.PCT)` | static | w-100 (stroom.css:843) on the declared <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/> |
| stroom/dashboard/client/table/BasicTableSettingsViewImpl.ui.xml:39,49 | `<valuespinner:ValueSpinner ... width="200px"/>` | static | New .value-spinner--narrow { width: 200px; } in value-spinner.css (21 ValueSpinner width= uses in templates share it) |

| Into the template | What | Detail |
| --- | --- | --- |
| BasicTableSettingsViewImpl.ui.xml:29 | component-declaration | <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/> (wave 8 DocSelectionBox) with DocSelectionController attached |
| BasicTableSettingsViewImpl.ui.xml:9-50 | component-declaration | FormGroup, SelectionBox, TickBox and ValueSpinner are already declared; keep |

**Stays in Java:** query list, values, pipeline enabled per Extract Values / Use Default: state; max results, page size: data.

## `TableFilterPresenter`

dialog, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.dashboard.client.component.ColumnFilterDialog`
- **Controller:** `stroom.dashboard.client.component.ColumnFilterDialogController`
- **Template:** `none (uses the shared LinkTabsLayoutView, stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view/LinkTabsLayoutViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFilterDialog.ui.xml (new)`
- **Goes:** `stroom/dashboard/client/table/TableFilterPresenter.java (moved and renamed)`
- **Names:** TableFilter clashes inside this wave with unit 49 (stroom.dashboard.client.input.TableFilterPresenter, the Table Filter pane, also proposed as TableFilter). This one is the per-column filter dialog with Filter and Include Exclude tabs, so ColumnFilterDialog (free; ColumnFilter is the wave 8 tab inside it). Not GIN-bound; the shared LinkTabsLayoutView binding (DashboardModule.java:61) stays for its other users.

| Into the template | What | Detail |
| --- | --- | --- |
| TableFilterPresenter.java:59 | css-class | <g:FlowPanel styleName="max dock-container-vertical default-min-sizes"> root |
| LinkTabsLayoutViewImpl.ui.xml:4-6 | structure | <tab:LinkTabBar ui:field="tabBar" addStyleNames="dock-min"/> and <tab:LayerContainerImpl ui:field="layers" styleName="dock-max"/> |

**Stays in Java:** tabs 'Filter' and 'Include Exclude' (TableFilterPresenter.java:61-62): added in Java; ColumnFilter (wave 8) and IncludeExcludeFilter (wave 10) are layers shown in the LayerContainer; caption "Filter '" + column name + "'" (:83): from data, set by the controller; PopupSize 1085x500 (:80): passed to the popup.

## `ListInputSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34)`
- **Controller:** `stroom.dashboard.client.component.ListInputSettingsController`
- **Template:** `none` → `none (built in Java because it only adds one tab to the shared Settings component)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/input/ListInputSettingsPresenter.java (becomes ListInputSettingsController; no GIN binding); `ListInputSettingsPresenter.java:32 addStyleName("settingsPresenter")`
- **Names:** ListInputSettingsController is free; no ListInputSettings component is made.

| Into the template | What | Detail |
| --- | --- | --- |
| ListInputSettingsPresenter.java:32 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings |

**Stays in Java:** addTab("Basic", ...) tab label.

## `FloorMapQueryViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapQuery (merged into unit 6's component)`
- **Controller:** `stroom.floormap.client.component.FloorMapQueryController (unit 6)`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapQueryViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapQuery.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapQueryViewImpl.java`; `FloorMapQueryPresenter.FloorMapQueryView (inner interface, FloorMapQueryPresenter.java:627)`; `FloorMapModule.java:137-139 bindPresenterWidget(FloorMapQueryPresenter.class, FloorMapQueryView.class, FloorMapQueryViewImpl.class)`
- **Names:** The view half of unit 6 (FloorMapQueryPresenter -> FloorMapQuery, this wave). It becomes that component. The names are free.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapQueryViewImpl.java:87-93 | aria | accessible names "Entity ID Column", "Location Column", "Location Ref Column" and "Type Column" are fixed text: set them as a template attribute on each SelectionBox (e.g. accessibleName="Entity ID Column") once SelectionBox (wave 2) labels its inner input; FloorMapAria.labelInnerControl and its Console.error go |
| FloorMapQueryViewImpl.ui.xml:26,30,34,38 | css-class | margin-right-10 is defined in no CSS file (no effect): drop it. flex-grow-1 is Bootstrap's (bootstrap.css:7249, to be retired): use dock-max (stroom.css:828, flex-grow: 1) |
| FloorMapQueryViewImpl.ui.xml:28-40 | component-declaration | SelectionBox and FormGroup (waves 2, 1) already declared; kept |

**Stays in Java:** FloorMapQueryViewImpl.java:76-81 role-to-box map (pairs fields with an enum); FloorMapQueryViewImpl.java:105-117 setColumnMappingsVisible / setColumnsHintVisible (state); FloorMapQueryViewImpl.java:119-148 available columns and selected values (data); FloorMapQueryViewImpl.java:100 query edit slot (unit 6's QueryEdit child, unit 14, this wave).

## `ListInputPresenter`

tab, presenter; complexity 2, effort M; used by 4.

- **Component:** `stroom.dashboard.client.component.ListInput`
- **Controller:** `stroom.dashboard.client.component.ListInputController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/ListInputViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ListInput.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/ListInputViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/ListInputUiHandlers.java`; `ListInputPresenter.ListInputView (inner interface, ListInputPresenter.java:174)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/ListInputPresenter.java (becomes the controller)`; `InputModule.java:63 bindPresenterWidget(ListInputPresenter.class, ListInputView.class, ListInputViewImpl.class)`
- **Names:** No clash. ListInputPlugin (InputModule.java:62) stays.

| Into the template | What | Detail |
| --- | --- | --- |
| ListInputViewImpl.ui.xml:6 | css-class | ListInputView -> dashboard-input (shared rule, dashboard.css:449-451) |
| ListInputViewImpl.ui.xml:7 + ListInputViewImpl.java:126-135 @UiHandler | component-declaration | SelectionBox valueSelectionBox already declared (wave 2); its change goes out through setOnValueChange(Consumer<WordItem>) in place of ListInputUiHandlers |

**Stays in Java:** ListInputViewImpl.java:56-83 setRenderFunction building item SafeHtml with listInputItem-container/-word/-source/-source-text classes and the dictionary icon (per-item rendering from data; SafeHtml stays for cells and rows); ListInputViewImpl.java:122-123 AccessibleName.set(valueSelectionBox, component name) (data); ListInputViewImpl.java:94-114 values, selection and allowTextEntry (data/state).

## `TextSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `none (shows stroom.dashboard.client.component.ComponentSettingsDialog, the converted SettingsPresenter)`
- **Controller:** `stroom.dashboard.client.component.TextSettingsController`
- **Template:** `none (uses the shared LinkTabsLayoutView)` → `none (it only adds tabs to ComponentSettingsDialog; it has no view of its own)`
- **Goes:** `stroom/dashboard/client/text/TextSettingsPresenter.java`
- **Names:** Controller only, so TextSettingsController; no clash. Like the other SettingsPresenter subclasses in this wave (QuerySettings, VisSettings, TableSettings...) it extends ComponentSettingsDialogController. No GIN binding exists for it (made through a Provider by TextPresenter).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/dashboard/client/text/TextSettingsPresenter.java:32 | css-class | settingsPresenter: all nine SettingsPresenter subclasses add it, so it goes on the root of ComponentSettingsDialog.ui.xml (with default-min-sizes) and this line goes |

**Stays in Java:** addTab("Basic", basicTextSettings) at TextSettingsPresenter.java:34: tabs are added to a LinkTabBar in Java (no declarative tabs exist).

## `SourcePresenter`

composite, presenter; complexity 4, effort L; used by 7.

- **Component:** `stroom.data.client.component.SourceViewer`
- **Controller:** `stroom.data.client.component.SourceViewerController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/SourceViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewer.ui.xml`
- **Goes:** `SourceView interface (SourcePresenter.java:888)`; `stroom/data/client/view/SourceViewImpl.java`; `stroom/data/client/gin/StreamStoreModule.java:137-140 bindPresenterWidget(SourcePresenter, SourceView, SourceViewImpl)`
- **Names:** Source clashes with stroom.data.store.api.Source and stroom.pipeline.xsltfunctions.Source; SourceViewer / SourceViewerController are free.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/data/client/view/SourceViewImpl.java:69 | text | icon="STEPPING" on <btn:FabButton ui:field="steppingButton"> |
| stroom/data/client/view/SourceViewImpl.java:70 | structure | visible="false" on <spinner:SpinnerLarge> |
| SourcePresenter.java:122,409-411 / ui.xml:30 | component-declaration | <d:CharacterNavigator ui:field="navigator"/> (wave 4) in navigatorContainer; setNavigatorView(null) becomes setVisible(false) |
| SourcePresenter.java:174 / ui.xml:34 | component-declaration | <pr:Progress ui:field="progress" addStyleNames="dock-min sourceViewImpl-progressBarPanel"/> (wave 3) |
| SourcePresenter.java:120-121 / ui.xml:36 | component-declaration | <d:ClassificationWrapper ui:field="classification"> wrapping <d:Text ui:field="text"/> (wave 8 stroom.data.client.component.Text) inside the stepping-button-container; setContent stays in Java if wave 4 does not make the wrapper a HasOneWidget |

**Stays in Java:** feed, id, part or record number, type labels and which of Part/Record shows (SourceViewImpl.java:84-106): data; metaListContainer slot (setMetaListContainerView, set by SteppingPresenter.java:236): stays a setter until SteppingPresenter (unit 54) declares it; stepping button visibility and enabled: state.

## `TextPresenter`

composite, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.dashboard.client.component.TextPane`
- **Controller:** `stroom.dashboard.client.component.TextPaneController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/text/TextViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/TextPane.ui.xml`
- **Goes:** `stroom/dashboard/client/text/TextPresenter.java (TextView interface at :746)`; `stroom/dashboard/client/text/TextViewImpl.java`; `stroom/dashboard/client/text/TextUiHandlers.java (beginStepping becomes setOnStep(Runnable))`; `stroom/dashboard/client/text/TextSettingsUihandlers.java (unused: no references)`; `stroom/dashboard/client/text/gin/TextModule.java:33 bindPresenterWidget(TextPresenter, TextView, TextViewImpl)`
- **Names:** Text/TextController clash: wave 8 converts stroom.query.client.presenter.TextPresenter and stroom.data.client.presenter.TextPresenter to Text + TextController (query and data component packages). This dashboard pane becomes TextPane/TextPaneController. The wave record wrongly lists the query and data TextViewImpl.ui.xml templates for this unit; only dashboard/client/text/TextViewImpl.ui.xml is its. Its base AbstractComponentPresenter becomes DashboardComponentController (unit 526).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/dashboard/client/text/TextViewImpl.java:52 | structure | spinner.setVisible(false) becomes visible="false" on <spinner:SpinnerLarge> |
| stroom/dashboard/client/text/TextViewImpl.java:53 | text | steppingButton.setIcon(SvgImage.STEPPING) becomes icon="STEPPING" on <button:FabButton> |
| stroom/dashboard/client/text/TextViewImpl.ui.xml:14 and TextViewImpl.java:41-48 | component-declaration | <l:ClassificationLabel ui:field="classification"/> declared plainly, not @UiField(provided=true) injected; ClassificationLabelController (wave 4) attaches to it |
| stroom/dashboard/client/text/TextViewImpl.ui.xml:9, TextPresenter.java:156,161,191 | component-declaration | Replace <g:SimplePanel ui:field="content"/> with both <ed:Editor ui:field="raw" styleName="max"/> (wave 6) and <ed:Html ui:field="html" styleName="max"/> (wave 3) declared, the controller toggling setVisible instead of setContent; their controllers attach (TextPresenter.java:204-215 make() calls) |

**Stays in Java:** setSteppingEnabled title switch 'Enter Stepping Mode' / 'Select a row to step through its source' (TextViewImpl.java:79-81): state; the initial title is already in the template; setSteppingVisible, setClassification text, which of raw/html shows: runtime state; editor text and highlights (TextPresenter.java:156-172): data.

## `BasicVisSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicVisSettings`
- **Controller:** `stroom.dashboard.client.component.BasicVisSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/vis/BasicVisSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicVisSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/vis/BasicVisSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/vis/BasicVisSettingsUiHandlers.java`; `BasicVisSettingsPresenter.BasicVisSettingsView (inner interface, BasicVisSettingsPresenter.java:275)`; `stroom-core-client/src/main/java/stroom/dashboard/client/vis/BasicVisSettingsPresenter.java (becomes the controller)`; `VisModule.java:39-40 bindPresenterWidget(BasicVisSettingsPresenter.class, BasicVisSettingsView.class, BasicVisSettingsViewImpl.class)`; BasicVisSettingsPresenter.java:267 setSettingsPresenter(SettingsPresenter) (the reach into its parent goes: VisSettingsController gives it addTab/removeTab callbacks)
- **Names:** No clash: BasicVisSettings and BasicVisSettingsController are free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BasicVisSettingsViewImpl.java:105 | `widget.setWidth("100%")` | static | w-100 (stroom.css:843) on the declared <dd:DropDownBox ui:field="visualisation" addStyleNames="w-100"/> |

| Into the template | What | Detail |
| --- | --- | --- |
| BasicVisSettingsViewImpl.ui.xml:21 + BasicVisSettingsViewImpl.java:103-107 setVisualisationView | component-declaration | <dd:DropDownBox ui:field="visualisation" addStyleNames="w-100"/> in place of the SimplePanel slot; the controller attaches a DocSelectionController (DocSelectionBox, wave 8) |
| BasicVisSettingsViewImpl.ui.xml:17 + BasicVisSettingsViewImpl.java:113 @UiHandler("table") | component-declaration | SelectionBox table already declared (wave 2); setOnTableChange(Runnable) in place of BasicVisSettingsUiHandlers |
| declares | component-declaration | Checked: DynamicSettingsPane (wave 4) is made per settings tab the visualisation defines (BasicVisSettingsPresenter.java:165) and added as a tab of the parent Settings. It is data-driven, so it is not declared in this template. |

**Stays in Java:** BasicVisSettingsViewImpl.java:68, 78 id and name text (data); BasicVisSettingsPresenter.java:152-178 dynamic tabs named from the visualisation's JSON (data).

## `FloorMapEditorPresenter`

tab, presenter; complexity 5, effort XL; used by 8.

- **Component:** `stroom.floormap.client.component.FloorMapEditor`
- **Controller:** `stroom.floormap.client.component.FloorMapEditorController`
- **Template:** `none (FloorMapEditorViewImpl.java:108-155 builds it in Java)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.ui.xml (new: FloorMapEditorViewImpl, unit 63 of this wave, builds its layout in Java and merges in here)`
- **Goes:** `FloorMapEditorView interface (FloorMapEditorPresenter.java:2174)`; `stroom/floormap/client/view/FloorMapEditorViewImpl.java (unit 63, folded in)`; stroom/floormap/client/gin/FloorMapModule.java:114-116 bindPresenterWidget(FloorMapEditorPresenter, FloorMapEditorView, FloorMapEditorViewImpl)
- **Names:** No clash. Units 63-66 (FloorMap*ViewImpl 'stays') should not survive separately: rule 1 makes each ViewImpl the component of its presenter unit (FloorMapEditor, FloorMapEventStoreData via QueryDataPane, FloorMapMap, FloorMapQuery).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/floormap/client/presenter/FloorMapEditorPresenter.java:342 | `toolbar.getElement().getStyle().setProperty("marginLeft", "auto")` | static | New .toolbar--end { margin-left: auto; } in toolbar.css (no existing Stroom class pushes a toolbar to the row end); the help toolbar gets addStyleNames="toolbar--end" |
| stroom/floormap/client/view/FloorMapEditorViewImpl.java:152 | `outerSplitPane.setSize("100%", "100%")` | static | max (stroom.css:793) on the root SplitPane |
| stroom/floormap/client/view/FloorMapEditorViewImpl.java:144-146 | `new DockLayoutPanel(Unit.PX); addNorth(timelinePanel, TIMELINE_HEIGHT=110)` | static | A dock-container-vertical FlowPanel (stroom.css) with the timeline in a new .floormap-timeline-strip { flex: 0 0 110px; } (stroom-floormap.css, shared with FloorMapMap) and the bottom SplitPane dock-max; no DockLayoutPanel inline positions |

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/floormap/client/view/FloorMapEditorViewImpl.java:108-155 | structure | <l:SplitPane vertical="true" thin="true" sizes="*, 33%" names="Map and Controls, Timeline and Lists" addStyleNames="max stroom-border-top"> containing <l:SplitPane ui:field="topSplitPane" thin="true" sizes="*, 200px" names="Map, Controls"> and the timeline strip plus <l:SplitPane thin="true" sizes="50%, *" names="Fact List, Time List"> |
| stroom/floormap/client/view/FloorMapEditorViewImpl.java:111,114,126,130,133 | css-class | dashboard-panel overflow-hidden (and stroom-border-bottom on the timeline) on the declared children |
| FloorMapEditorPresenter.java:294-298 | component-declaration | <fm:FloorMapCanvas ui:field="canvas"/> (wave 7), <fm:FloorMapDock ui:field="dock"/> (wave 7), <fm:FloorMapTimeline ui:field="timeline"/> (wave 8), <fm:FloorMapFactList ui:field="factList"/> and <fm:FloorMapTimeList ui:field="timeList"/> (wave 10) declared; their controllers attach instead of the five setXxxView slots |

**Stays in Java:** editorToolbar 'Floor map editor actions' toggles (FloorMapEditorPresenter.java:170, 252-259) and the help toolbar (:339-344): they go on the document's toolbar row (HasToolbar), not in this component; floorMapDockPresenter.addTab("Layers", ...) at :279: dock tabs are added in Java; FloorMapObjectEdit, FloorMapLayerStyle, FloorMapSetScale: dialogs, made with makeDialog, not declared; context-menu items (:1411-1570): menu data; setDockVisible via topSplitPane.setPaneHidden: state.

## `FloorMapMapPresenter`

tab, presenter; complexity 5, effort XL; used by 8.

- **Component:** `stroom.floormap.client.component.FloorMapMap`
- **Controller:** `stroom.floormap.client.component.FloorMapMapController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapMapViewImpl.ui.xml (unit 65's ViewImpl, folded in)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.ui.xml`
- **Goes:** `FloorMapMapView interface (FloorMapMapPresenter.java:1784)`; `stroom/floormap/client/view/FloorMapMapViewImpl.java (unit 65)`; `stroom/floormap/client/gin/FloorMapModule.java:117-119 bindPresenterWidget(FloorMapMapPresenter, FloorMapMapView, FloorMapMapViewImpl)`
- **Names:** No clash. FloorMapFullReadQueryHelper is a model helper and moves beside the controller; the static query parsing it calls on FloorMapQueryPresenter (FloorMapMapPresenter.java:894) moves to a model class first, as the record says.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/floormap/client/view/FloorMapMapViewImpl.ui.xml:21-26 | `<g:DockLayoutPanel unit='PX'> <g:south size="110">` | static | dock-container-vertical FlowPanel with the timeline in .floormap-timeline-strip { flex: 0 0 110px; } (new, stroom-floormap.css, shared with FloorMapEditor); DockLayoutPanel writes inline positions |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapMapPresenter.java:429-431 | component-declaration | <fm:FloorMapCanvas ui:field="map"/>, <fm:FloorMapDock ui:field="dock"/> and <fm:FloorMapTimeline ui:field="timeline"/> in place of the mapPanel, dockPanel and timelinePanel SimplePanels (template lines 24-32) |

**Stays in Java:** mapToolbar 'Floor map actions' with grid, clustering and dock toggles (FloorMapMapPresenter.java:317, 436-452): on the document toolbar row (HasToolbar); dock tabs Tracking, Layers, Groups (:406-408): added in Java; FloorMapCluster: a dialog; error and warning text built from FloorMapEventRole display names (:1181-1184): text from data.

## `DashboardPresenter`

tab, presenter; complexity 5, effort XL; used by 5.

- **Component:** `stroom.dashboard.client.component.DashboardBody`
- **Controller:** `stroom.dashboard.client.component.DashboardBodyController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/main/DashboardViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardBody.ui.xml`
- **Goes:** `DashboardView interface (DashboardPresenter.java:1096)`; `stroom/dashboard/client/main/DashboardViewImpl.java`; stroom/dashboard/client/gin/DashboardModule.java:48-51 bindPresenterWidget(DashboardPresenter, DashboardPresenter.DashboardView, DashboardViewImpl)
- **Names:** Dashboard clashes with stroom.query.language.functions.Dashboard; DashboardBody / DashboardBodyController are free and say it is the dashboard's layout body inside the document editor (DashboardSuper, wave 14).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/main/DashboardViewImpl.java:53-55 | `content.getElement().getStyle().clearTop(); if (embedded) setTop(0, Unit.PX)` | static | State class: new .dashboard__content--embedded { top: 0; } in dashboard.css, toggled with setStyleName(..., embedded) on the content panel |

| Into the template | What | Detail |
| --- | --- | --- |
| DashboardViewImpl.ui.xml:4 | css-class | Root styleName lists DashboardViewImpl twice; keep one ("DashboardViewImpl max dock-container-vertical") |
| DashboardPresenter.java:175 / ui.xml:5 | component-declaration | <fl:FlexLayout ui:field="layout" addStyleNames="dock-max DashboardViewImpl-content"/> (unit 56, this wave) once FlexLayout has a no-arg constructor (FlexLayout.java:110 takes the EventBus); until then the content setter stays |

**Stays in Java:** dashboard__designMode at DashboardViewImpl.java:62-64: state class; editToolbar 'Dashboard actions' with Design Mode, Add Component, Set Layout Constraints, View Current Selection, Maximise and Restore (DashboardPresenter.java:177-194): on the document toolbar row (getDocumentButtons), visibility state; QueryToolbar from getToolbars() (:240-242): document toolbar row; LayoutConstraint, CurrentSelection, RenameTab, DocSelectionPopup: dialogs and popups; captions (:269, :286, :645) set by the controller.

## `VisSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34)`
- **Controller:** `stroom.dashboard.client.component.VisSettingsController`
- **Template:** `none` → `none (built in Java because it only adds the Basic tab, plus the visualisation's own tabs, to the shared Settings component)`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisSettingsPresenter.java (becomes VisSettingsController; no GIN binding)`; `VisSettingsPresenter.java:32 addStyleName("settingsPresenter")`
- **Names:** VisSettings would clash with the server's stroom.dashboard.impl.vis.VisSettings. No VisSettings component is made: it uses the shared Settings. VisSettingsController is free.

| Into the template | What | Detail |
| --- | --- | --- |
| VisSettingsPresenter.java:32 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings |

**Stays in Java:** VisSettingsPresenter.java:35 addTab("Basic", ...) tab label; VisSettingsPresenter.java:34 basicSettingsPresenter.setSettingsPresenter(this) becomes add/remove tab callbacks given to BasicVisSettingsController.

## `VisPresenter`

composite, presenter; complexity 5, effort XL; used by 3.

- **Component:** `stroom.dashboard.client.component.Vis`
- **Controller:** `stroom.dashboard.client.component.VisController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/Vis.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisViewImpl.java`; `VisPresenter.VisView (inner interface, VisPresenter.java:819)`; `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisPresenter.java (becomes the controller)`; `VisModule.java:38 bindPresenterWidget(VisPresenter.class, VisView.class, VisViewImpl.class)`
- **Names:** Vis and VisController are free. It pairs with unit 11's QueryResultVis. It is not VisPane: stroom.dashboard.client.vis.VisPane already exists (an interface).

| Into the template | What | Detail |
| --- | --- | --- |
| VisViewImpl.java:47-85 | structure | Vis.ui.xml: <g:FlowPanel styleName="dashboardVis-outerLayout dock-container-vertical"><v:VisToolbarRow ui:field="toolbarRow"/><g:FlowPanel styleName="dock-max visBody"><g:SimplePanel ui:field="visContainer" styleName="dashboardVis-innerLayout"/><dg:MessagePanelImpl ui:field="messagePanel"/></g:FlowPanel></g:FlowPanel>; the anonymous FlowPanel's onAttach/onDetach (61-82: show or hide the frame, resize observer) move to Vis.onLoad/onUnload |
| VisViewImpl.java:50,53 | component-declaration | VisToolbarRow (wave 3, holds the RefreshButton) and MessagePanelImpl (wave 5, stroom.widget.datagrid.client) declared, not constructed |
| declares | component-declaration | Checked: PagerView is not used, so drop it from declares. RefreshButton comes in through VisToolbarRow. VisFrame (wave 5) is not declared: it is added to RootPanel (VisPresenter.java:166) and laid over visContainer by Vis.onResize, so its controller makes it. |

**Stays in Java:** VisPresenter.java:155-173 VisFrame made and bound in Java, added to RootPanel (an overlay, not in the widget tree); VisPresenter.java:176, 250-252, 572 visFrame class "vis <theme class>" (from the theme preference); VisPresenter.java:680 visFrame.setTitle(component name) (data); VisPresenter.java:422-655 showMessage text: "No visualisation", "Unable to parse settings", "Loading visualisation entity...", "Loading script...", "Injecting script...", errors (state of loading); VisViewImpl.java:107-149 onResize frame geometry (sets VisFrame's position; VisFrame's own inline styles are wave 5's); VisViewImpl.java:155-163 getParentByClass("dashboard-scrollPanel") lookup (reads a class; sets nothing).

## `ViewSettingsPresenter`

tab, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.view.client.component.ViewSettings`
- **Controller:** `stroom.view.client.component.ViewSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/view/client/view/ViewSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/view/client/component/ViewSettings.ui.xml`
- **Goes:** `ViewSettingsView interface (ViewSettingsPresenter.java:129)`; `stroom/view/client/view/ViewSettingsViewImpl.java`; `stroom/view/client/gin/ViewModule.java:34 bindSharedView(ViewSettingsView, ViewSettingsViewImpl) and :35 bind(ViewSettingsPresenter)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ViewSettingsPresenter.java:65 / ui.xml:8 | component-declaration | <dd:DropDownBox ui:field="dataSource" addStyleNames="w-100"/> (wave 8 DocSelectionBox) with a DocSelectionController attached |
| ViewSettingsPresenter.java:66,73 / ui.xml:12 | component-declaration | <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100"/> with DocSelectionController attached (setIncludedTypes(PipelineDoc.TYPE) on the controller) |
| ViewSettingsPresenter.java:67 / ui.xml:17 | component-declaration | <d:EditExpression ui:field="expression" styleName="max default-min-sizes"/> (wave 7, stroom.data.client.component.EditExpression, the one this imports) |

**Stays in Java:** selected data source, pipeline and expression: data.

## `RulesPresenter`

tab, presenter; complexity 3, effort M; used by 7.

- **Component:** `stroom.dashboard.client.component.Rules`
- **Controller:** `stroom.dashboard.client.component.RulesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/cf/RulesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/Rules.ui.xml`
- **Goes:** `RulesView interface (RulesPresenter.java:435)`; `stroom/dashboard/client/table/cf/RulesViewImpl.java`; `stroom/dashboard/client/table/gin/TableModule.java:88 bindPresenterWidget(RulesPresenter, RulesView, RulesViewImpl)`
- **Names:** No clash; sits beside Rule and RuleList (waves 10-11) in the same package. Extends SettingsTabController (from AbstractSettingsTabPresenter, unit 528). Follows the pilot: one setOnAction(Consumer<RuleAction>)-style enum for the seven actions on the list's toolbar.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/table/cf/RulesPresenter.java:106 | `listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NON…` | static | New .stroom-border-none { border-style: none; } in stroom.css beside .stroom-border-* (stroom.css:770-786); 8 setBorderStyle calls in the client can share it. Declared: <cf:RuleList ui:field="list" addStyleNames="stroom-border-none"/> |

| Into the template | What | Detail |
| --- | --- | --- |
| RulesPresenter.java:83 / RulesViewImpl.ui.xml:5 | component-declaration | <cf:RuleList ui:field="list" addStyleNames="max stroom-border-none"/> (wave 10) inside the form-control-border form-control-background panel, replacing setTableView |

**Stays in Java:** toolbar name and seven buttons (RulesPresenter.java:85-104): added to RuleList's toolbar in Java, as the pilot does; Disable/Enable title switch (:404-406) and enabled states (:409-415): state; Rule edit dialog captions 'Add New Rule' / 'Edit Rule' (:228,255).

## `MetaPresenter`

screen, presenter; complexity 4, effort L; used by 6.

- **Component:** `stroom.data.client.component.MetaBrowser`
- **Controller:** `stroom.data.client.component.MetaBrowserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/MetaViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowser.ui.xml`
- **Goes:** `MetaView interface (MetaPresenter.java:581)`; `stroom/data/client/view/MetaViewImpl.java`; `stroom/data/client/gin/StreamStoreModule.java:93-96 bindPresenterWidget(MetaPresenter, MetaView, MetaViewImpl)`
- **Names:** Meta clashes with the shared DTO stroom.meta.shared.Meta, which this very controller uses, and with stroom.meta.impl.db.jooq.tables.Meta and stroom.pipeline.xsltfunctions.Meta. MetaBrowser matches the agreed 'meta browser lineage design' naming; free.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/data/client/view/MetaViewImpl.java:51,57,63 | css-class | dashboard-panel overflow-hidden on each declared pane |
| MetaPresenter.java:122 / ui.xml:8 | component-declaration | <d:MetaList ui:field="streamList" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) |
| MetaPresenter.java:123 / ui.xml:11 | component-declaration | <d:MetaRelationList ui:field="relationList" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) |
| MetaPresenter.java:124 / ui.xml:13 | component-declaration | <d:DataViewer ui:field="data" addStyleNames="max dashboard-panel overflow-hidden"/> (unit 313, this wave) |

**Stays in Java:** list toolbar names 'Stream actions' / 'Related stream actions' and the Process/Delete/Restore/Info/Download/Upload/Filter buttons (MetaPresenter.java:129-186): added to the lists' own toolbars, permission-dependent; default-min-sizes on the filter dialog content (:266): on another's widget; better on the Expression component's own root if all its users want it; Expression filter and DataUpload: dialogs; caption 'Filter Streams' (:271): set by the controller.

## `SelectionHandlersPresenter`

tab, presenter; complexity 3, effort M; used by 5.

- **Component:** `stroom.dashboard.client.component.SelectionHandlers`
- **Controller:** `stroom.dashboard.client.component.SelectionHandlersController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/SelectionHandlersViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlers.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/SelectionHandlersViewImpl.java`; `SelectionHandlersPresenter.SelectionHandlersView (inner interface, SelectionHandlersPresenter.java:457-460)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/SelectionHandlersPresenter.java (becomes the controller)`; `QueryModule.java:63-66 bindPresenterWidget(SelectionHandlersPresenter.class, SelectionHandlersView.class, SelectionHandlersViewImpl.class)`
- **Names:** No clash: SelectionHandlers and SelectionHandlersController are free in the repo and in this wave.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| SelectionHandlersPresenter.java:129 | `listPresenter...getStyle().setBorderStyle(BorderStyle.NONE)` | static | none needed: the list's PagerView root (PagerViewImpl.ui.xml: max dock-container-vertical) and PagerView.java set no border, so this is a no-op. Delete it and check the screenshots. The same no-op is at RulesPresenter.java:106, RuleSetSettings.java:68, DataRetentionPolicyPresenter.java:131 and ContentTemplateTabPresenter.java:147. |

| Into the template | What | Detail |
| --- | --- | --- |
| SelectionHandlersViewImpl.ui.xml:5 + SelectionHandlersViewImpl.java:46-48 setTableView + SelectionHandlersPresenter.java:102 | component-declaration | <dash:SelectionHandlerList ui:field="list" addStyleNames="max form-control-border form-control-background"/> (SelectionHandlerList, wave 10) in place of the SimplePanel slot; it has no setTableView |
| SelectionHandlersViewImpl.ui.xml:4 | structure | the outer SimplePanel class max is kept as the root |
| SelectionHandlersPresenter.java:108-128 | aria | toolbar name "Selection handler actions" and the buttons go on the list's toolbar (another part's): following the design's action enum (setOnAction(Consumer<SelectionHandlerAction>), setAvailableActions(Set)), the component is given the list's toolbar and adds New, Edit, Duplicate, Disable, Delete, Move Up and Move Down with their icons and titles in its own Java, not the controller's |

**Stays in Java:** SelectionHandlersPresenter.java:431-435 disable button title "Disable/Enable Selected Selection Handler" (follows the selection); SelectionHandlersPresenter.java:438-444 setEnabled on each action (state, via setAvailableActions).

## `SteppingPresenter`

screen, presenter; complexity 5, effort XL; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.SteppingScreen`
- **Controller:** `stroom.pipeline.stepping.client.component.SteppingScreenController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/stepping/client/view/SteppingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreen.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/SteppingViewImpl.java`; `SteppingPresenter.SteppingView (inner interface, SteppingPresenter.java:1241-1248)`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/SteppingPresenter.java (becomes the controller)`; `PipelineModule.java:133 bindPresenterWidget(SteppingPresenter.class, SteppingView.class, SteppingViewImpl.class)`
- **Names:** Stepping clashes with stroom.query.language.functions.Stepping. SteppingController clashes with the server's stroom.pipeline.stepping.capture.SteppingController, which shares the stroom.pipeline.stepping root package and would be confusing. SteppingScreen / SteppingScreenController are free, and match the CSS block the screen already uses (stepping-screen, stepping-screen.css).

| Into the template | What | Detail |
| --- | --- | --- |
| SteppingPresenter.java:188-191 | component-declaration | <b:Toolbar name="Stepping"><b:ToolbarButton ui:field="terminate" svg="STOP" title="Terminate Stepping" enabled="false" addStyleNames="QueryButtons-button stop"/></b:Toolbar>, last child of the right panel |
| SteppingPresenter.java:206-208 | component-declaration | <b:Toolbar name="Log pane"><b:ToolbarToggleButton ui:field="toggleLogPane" svg="EXCLAMATION" title="Log Pane"/></b:Toolbar> (on at start: setOn in Java, or a template attribute if ToolbarToggleButton gets one) |
| SteppingPresenter.java:212-226 | structure | <g:FlowPanel styleName="stepping-screen__progress dock-container-horizontal dock-min"> holding the Log pane toolbar, <g:SimplePanel ui:field="stepMessage" styleName="stepping-screen__progress-message"/> and <div ui:field="stepAnnouncement" class="sr-only" role="status" aria-live="polite"/> (sr-only: spinner.css:126); first child of the right panel |
| SteppingViewImpl.ui.xml:8 + SteppingPresenter.java:228-231 addWidgetRight | component-declaration | in the right panel (dock-min stepping-screen__controls), in this order: progress panel, <st:StepLocationLink ui:field="stepLocationLink"/> (wave 4), <st:StepControl ui:field="stepControl"/> (wave 9), Stepping toolbar; their controllers attach. addWidgetRight goes. |
| SteppingViewImpl.ui.xml:7 + SteppingPresenter.java:193 | component-declaration | <pp:PipelinePath ui:field="pipelinePath"/> (wave 7) in the stepping-screen__pipeline slot in place of setTreeView; the PipelinePath controller attaches |
| SteppingViewImpl.ui.xml:10 | component-declaration | LayerContainerImpl (wave 3) already declared; kept |
| declares | component-declaration | Checked: Element (wave 12) is made once per pipeline element and shown in the layer container. Expression (wave 9) and SteppingFilter (wave 11) are dialogs. SteppingMetaList (wave 10) sits in Source's meta list slot (SteppingPresenter.java:236). None of these is declared. |

**Stays in Java:** SteppingPresenter.java:195-196 sourcePresenter.getWidget().addStyleName("dashboard-panel overflow-hidden"): Source (unit 17) is shown as a layer, not declared, so move the classes to the root of Source.ui.xml if every use wants them, else keep one call (line 196 repeats line 195; delete it); SteppingPresenter.java:233-234 "Stream actions" toolbar name and Filter Streams button go on SteppingMetaList's toolbar (another part's): the component adds them with an action callback; SteppingPresenter.java:362 presenter.getWidget().getElement().addClassName("default-min-sizes") on the Expression dialog: belongs in Expression.ui.xml (wave 9) if every use is a resizable dialog, else stays in the dialog's opener; SteppingPresenter.java:667-672 log pane title "Log Pane (<n> <type>s)" (built from data); SteppingPresenter.java:1001-1002 step message text and title (data); SteppingPresenter.java:1008-1009 announcement text (data); enabled/visible/on state of terminate and log pane buttons.

## `MultiRulesPresenter`

tab, presenter; complexity 2, effort M; used by 4.

- **Component:** `stroom.dashboard.client.component.MultiRules`
- **Controller:** `stroom.dashboard.client.component.MultiRulesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/MultiRulesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRules.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/MultiRulesViewImpl.java`; `MultiRulesPresenter.MultiRulesView (inner interface, MultiRulesPresenter.java:162)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/MultiRulesPresenter.java (becomes the controller)`; `InputModule.java:80-82 bindPresenterWidget(MultiRulesPresenter.class, MultiRulesView.class, MultiRulesViewImpl.class)`
- **Names:** No clash. Its per-column row is a new small dumb component, stroom.dashboard.client.component.TableFilterColumn (free), shared with unit 49 TableFilterInput.

| Into the template | What | Detail |
| --- | --- | --- |
| MultiRulesPresenter.java:82-83, 97-101 | structure | each column's row (FlowPanel table-filter-panel holding a Label table-filter-label with no wrapping, then the rules editor) becomes a new component, TableFilterColumn.ui.xml: <g:FlowPanel styleName="table-filter-panel"><g:Label ui:field="label" styleName="table-filter-label" wordWrap="false"/></g:FlowPanel>. setName(String) sets the label and setContent(IsWidget) adds the editor as the panel's second child, so the flex rules in components.css:2103-2116 still apply. MultiRules.setColumns adds one row per column (repetition stays in the component's Java) |
| MultiRulesViewImpl.ui.xml:5 | css-class | table-filter-outer kept on the FlowPanel (components.css:2086) |
| declares | component-declaration | Rules (unit 28, this wave) is made once per column and goes in a TableFilterColumn's content. It cannot be declared in the template: the rows come from data. Make it with make(), not makeDialog() (MultiRulesPresenter.java:91 uses makeDialog). |

**Stays in Java:** MultiRulesPresenter.java:82 column.getName() as the row label (data); MultiRulesPresenter.java:79-104 one row per selected column (repetition from data).

## `TableSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `none (shows stroom.dashboard.client.component.ComponentSettingsDialog)`
- **Controller:** `stroom.dashboard.client.component.TableSettingsController`
- **Template:** `none (shared LinkTabsLayoutView)` → `none (it only adds tabs to ComponentSettingsDialog)`
- **Goes:** `stroom/dashboard/client/table/TableSettingsPresenter.java`
- **Names:** A component named TableSettings would clash with the shared DTO stroom.query.api.TableSettings (used throughout the table code); as a controller only, TableSettingsController is free and no TableSettings class is made. Extends ComponentSettingsDialogController.

| Into the template | What | Detail |
| --- | --- | --- |
| TableSettingsPresenter.java:38 | css-class | settingsPresenter moves to the ComponentSettingsDialog.ui.xml root (all nine subclasses add it) |

**Stays in Java:** tabs Basic, Conditional Formatting, Selection Filter (TableSettingsPresenter.java:42-44): added in Java.

## `QuerySettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (the shared tabbed settings component from unit 34; no component of its own)`
- **Controller:** `stroom.dashboard.client.component.QuerySettingsController`
- **Template:** `none` → `none (built in Java because it only adds two tabs to the shared Settings component, whose template is unit 34's Settings.ui.xml)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/query/QuerySettingsPresenter.java (becomes QuerySettingsController extends SettingsController; no GIN binding); `QuerySettingsPresenter.java:34 getView().asWidget().addStyleName("settingsPresenter")`
- **Names:** QuerySettingsController is free. No QuerySettings class is made: the component is the shared Settings (unit 34). This also avoids a second, empty template.

| Into the template | What | Detail |
| --- | --- | --- |
| QuerySettingsPresenter.java:34 | css-class | settingsPresenter goes on the root of unit 34's Settings.ui.xml, renamed dashboard-settings (rule at components.css:1280), since every SettingsPresenter subclass adds it |

**Stays in Java:** QuerySettingsPresenter.java:39-40 addTab("Basic", ...), addTab("Selection Query", ...) tab labels (TabbedComponent convention: the labels are given in Java, as DictionaryEditor.java:86-113 does); QuerySettingsPresenter.java:36-37 data source consumer wiring (controller).

## `EmbeddedQuerySettingsPresenter`

dialog, presenter; complexity 2, effort M; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34)`
- **Controller:** `stroom.dashboard.client.component.EmbeddedQuerySettingsController`
- **Template:** `none` → `none (built in Java because it only adds four tabs and the data source lookup to the shared Settings component)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/EmbeddedQuerySettingsPresenter.java (becomes EmbeddedQuerySettingsController; no GIN binding); `EmbeddedQuerySettingsPresenter.java:57 addStyleName("settingsPresenter")`
- **Names:** EmbeddedQuerySettingsController is free; no EmbeddedQuerySettings component is made.

| Into the template | What | Detail |
| --- | --- | --- |
| EmbeddedQuerySettingsPresenter.java:57 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings |

**Stays in Java:** EmbeddedQuerySettingsPresenter.java:98-101 addTab labels "Basic", "Conditional Formatting", "Selection Query", "Selection Filter"; EmbeddedQuerySettingsPresenter.java:59-94 data source lookup over REST and its cache (controller; use .owner(this) in place of taskMonitorFactory).

## `QueryPresenter`

tab, presenter; complexity 5, effort XL; used by 8.

**Since the merge.** copy_and_paste changed the expression context menu. It now puts the right-clicked term field's edit items first (ContextMenuManager.withEditItems(event.getEditTarget(), ...)) and passes the TextEditTarget to showMenu, which restores the field's focus on hide through ShowMenuEvent.Builder.onHide. The controller must keep this when it takes over the menu. It is now 1028 lines.

- **Component:** `stroom.dashboard.client.component.QueryPane`
- **Controller:** `stroom.dashboard.client.component.QueryPaneController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/QueryViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPane.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryViewImpl.java`; `QueryPresenter.QueryView (inner interface, QueryPresenter.java:916-933)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryPresenter.java (becomes QueryPaneController)`; `QueryModule.java:58 bindPresenterWidget(QueryPresenter.class, QueryView.class, QueryViewImpl.class)`; `QueryPresenter.java:171-180 anonymous QueryUiHandlers given to QueryButtons (replace with QueryButtons.setOnStart)`
- **Names:** Query clashes with stroom.query.api.Query (a shared DTO compiled into the client, used for search requests), stroom.storedquery.impl.db.jooq.tables.Query and stroom.gwt.workbench.framework.client.play.Query. QueryPane and QueryPaneController are free in the repo and in this wave. The dashboard calls its parts panes ('dashboard-panel'). Unit 26 Table, unit 2 Text and unit 60 Vis have no clash, so they keep their short names.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| QueryPresenter.java:903-905 | `errorsButton...getStyle().setVisibility(show ? VISIBLE : HIDDEN)` | static | state class toggled by setErrorsVisible: new utility .visibility-hidden { visibility: hidden; } in stroom.css next to .overflow-hidden (stroom.css:859), as no Stroom class hides an element while it keeps its space (refresh-button.css:18 and data-grid.css:368 are scoped to their own blocks) |

| Into the template | What | Detail |
| --- | --- | --- |
| QueryViewImpl.ui.xml:6 | css-class | root class QueryViewImpl -> dashboard-query (rename the class named after the deleted ViewImpl; no rule uses .QueryViewImpl alone) |
| QueryViewImpl.ui.xml:7 | css-class | QueryViewImpl--buttons -> dashboard-query__buttons (rename the rule at dashboard.css:141) |
| QueryViewImpl.ui.xml:12-13 | css-class | queryViewImpl-expressionTree -> dashboard-query__expression-tree (rename the rule at components.css:341) |
| QueryViewImpl.ui.xml:12-14 + QueryViewImpl.java:62-64 setExpressionView + QueryPresenter.java:170 | component-declaration | <q:ExpressionTree ui:field="expressionTree" addStyleNames="dock-max dashboard-query__expression-tree"/> (ExpressionTree, wave 7) in place of the FlowPanel slot; the controller attaches ExpressionTreeController to it |
| QueryPresenter.java:194-212 | component-declaration | inside <b:Toolbar ui:field="expressionToolbar" name="Expression actions">: <b:ToolbarButton ui:field="addTerm" svg="ADD" title="Add Term"/>, addOperator svg="OPERATOR" title="Add Operator", copy svg="COPY" title="Duplicate Selected Expression Item" enabled="false", disableItem svg="DISABLE" title="Disable Selected Expression Item" enabled="false", deleteItem svg="DELETE" title="Delete Selected Expression Item" enabled="false", history svg="HISTORY" title="Show Query History", favourites svg="FAVOURITES" title="Show Favourite Queries", downloadQuery svg="DOWNLOAD" title="Download Query" enabled="false" (ToolbarButton, wave 1) |
| QueryPresenter.java:214-217 | component-declaration | <b:ToolbarButton ui:field="process" svg="PROCESS" title="Process Query with Pipeline" visible="false"/>; the controller shows it with setProcessVisible(hasAppPermission(MANAGE_PROCESSORS_PERMISSION)) |
| QueryPresenter.java:220-221 | component-declaration | inside <b:Toolbar ui:field="messagesToolbar" name="Query messages">: <b:ToolbarButton ui:field="errors" svg="ERROR" title="Show Errors" addStyleNames="visibility-hidden"/> |
| QueryViewImpl.ui.xml:10 | component-declaration | <q:QueryButtons ui:field="queryButtons"/> already declared (wave 3); use setOnStart(Runnable) in place of setUiHandlers(QueryUiHandlers) |
| declares list | component-declaration | Checked: QueryHistory, QueryFavourites, ProcessorLimits, QueryInfo and DocSelectionPopup are dialogs opened with makeDialog. They are not declared in the template. Only ExpressionTree, Toolbar/ToolbarButton and QueryButtons are. |

**Stays in Java:** QueryPresenter.java:345-354 errors button icon and title (Show Errors/Show Warnings/Show Messages, chosen from the worst severity); QueryPresenter.java:365-394 setEnabled on the buttons and disable title "Enable/Disable Selected Expression Item", download title "Download Query (choose a data source first)" (state); QueryPresenter.java:903-905 toggling the visibility-hidden state class; QueryPresenter.java:471, 492 dialog captions "Choose Pipeline To Process Results With", "Process Search Results" (controller shows the dialogs); QueryPresenter.java:805-878 context menu items and "Actions for expression item '<text>'" (menu data); QueryPresenter.java:444, 528 AlertEvent messages (controller).

## `BasicTableFilterSettingsPresenter`

tab, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.BasicTableFilterInputSettings`
- **Controller:** `stroom.dashboard.client.component.BasicTableFilterInputSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/input/BasicTableFilterSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicTableFilterInputSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicTableFilterSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicTableFilterSettingsUiHandlers.java`; `BasicTableFilterSettingsPresenter.BasicTableFilterSettingsView (inner interface, BasicTableFilterSettingsPresenter.java:160)`; `stroom-core-client/src/main/java/stroom/dashboard/client/input/BasicTableFilterSettingsPresenter.java (becomes the controller)`; InputModule.java:77-79 bindPresenterWidget(BasicTableFilterSettingsPresenter.class, BasicTableFilterSettingsView.class, BasicTableFilterSettingsViewImpl.class)
- **Names:** BasicTableFilterSettings is itself free. Its pane, unit 49 (input/TableFilterPresenter), cannot be TableFilter: unit 25 (table/TableFilterPresenter, the column filter dialog) is already TableFilter in the same package (stroom.dashboard.client.component). The pane becomes TableFilterInput, beside KeyValueInput, ListInput and TextInput, so its settings tab is renamed BasicTableFilterInputSettings to keep the family together. Both names are free.

| Into the template | What | Detail |
| --- | --- | --- |
| BasicTableFilterSettingsViewImpl.ui.xml:22 + BasicTableFilterSettingsViewImpl.java:104-105 setColumnList | component-declaration | <dash:ColumnSelection ui:field="columns" addStyleNames="max form-control-border form-control-background"/> (ColumnSelection, wave 10) in place of the SimplePanel slot; the controller attaches its controller |
| BasicTableFilterSettingsViewImpl.ui.xml:17 + BasicTableFilterSettingsViewImpl.java:112-116 @UiHandler("table") | component-declaration | SelectionBox table already declared (wave 2); its change goes out through setOnTableChange(Runnable) in place of BasicTableFilterSettingsUiHandlers.onTableChange |

**Stays in Java:** BasicTableFilterSettingsViewImpl.java:69 id.setText(id) (data); BasicTableFilterSettingsViewImpl.java:79 name.setText(name) (data); BasicTableFilterSettingsPresenter.java:153 setMultiRulesPresenter link to MultiRules (controller to controller, set by TableFilterInputSettingsController).

## `TableFilterSettingsPresenter`

dialog, presenter; complexity 1, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.Settings (shared, unit 34)`
- **Controller:** `stroom.dashboard.client.component.TableFilterInputSettingsController`
- **Template:** `none` → `none (built in Java because it only adds two tabs to the shared Settings component)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/input/TableFilterSettingsPresenter.java (becomes TableFilterInputSettingsController; no GIN binding); `TableFilterSettingsPresenter.java:38 addStyleName("settingsPresenter")`
- **Names:** Named TableFilterInputSettingsController to follow TableFilterInput (unit 49), whose plain name clashes with unit 25. The name is free.

| Into the template | What | Detail |
| --- | --- | --- |
| TableFilterSettingsPresenter.java:38 | css-class | settingsPresenter -> on the root of Settings.ui.xml (unit 34) as dashboard-settings |

**Stays in Java:** addTab("Basic"), addTab("Conditional Formatting") tab labels; TableFilterSettingsPresenter.java:35 basic -> MultiRules link (controller).

## `TableFilterPresenter`

tab, presenter; complexity 3, effort M; used by 3.

No plan yet (a second unit of the same name, or new with the merge): plan it as it is converted.

## `ColumnsManager`

composite, smart class; complexity 4, effort L; used by 1.

**Since the merge.** Heading and HeadingListener moved to stroom.widget.datagrid.client. SortIcon is now Stroom's own stroom.widget.datagrid.client.SortIcon, not GWT's cellview SortIcon, which was deleted with the cell tables. Nothing structural changed.

- **Component:** `none (no widget; FilterCell stays a grid cell)`
- **Controller:** `stroom.dashboard.client.component.TableColumnsController`
- **Template:** `none` → `none (built in Java because it is the column header menu and FilterCell renders SafeHtml in a grid header)`
- **Goes:** `stroom/dashboard/client/table/ColumnsManager.java (moved and renamed)`
- **Names:** ColumnsManager + ColumnsManagerController would leave an empty component: it is one controller. TableColumnsController (free) pairs with QueryResultColumnsController (unit 180's QueryTableColumnsManager); the two are near copies and could share a base later. FilterCell and FilterCellManager keep their names and package (cells). Its dialogs come from its owner's makeDialog (TablePresenter.java:273-280) rather than being passed in as presenters.

**Stays in Java:** FilterCell.java:238-268 classes dashboard-table-filter-cell*, toolbar-button, aria-label 'Filter ' + column name, title Enable/Disable Filter: grid cell HTML with state and data; column menu items (ColumnsManager.java:592-860): menu data built per column.

## `TablePresenter`

screen, presenter; complexity 5, effort XL; used by 14.

**Since the merge.** Mechanical rename: DataGrid, with MessagePanel and PagerView now in stroom.widget.datagrid.client. It is now 1762 lines across its files.

- **Component:** `stroom.dashboard.client.component.Table`
- **Controller:** `stroom.dashboard.client.component.TableController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/TableViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/Table.ui.xml`
- **Goes:** `TableView interface (TablePresenter.java:1447)`; `stroom/dashboard/client/table/TableViewImpl.java`; `stroom/dashboard/client/table/gin/TableModule.java:67 bindPresenterWidget(TablePresenter, TableView, TableViewImpl)`
- **Names:** Table and TableController are free in all_simple_names.json and in the wave. Extends RefreshableDashboardComponentController (unit 526). TablePlugin, TableRowStyles, AbstractRowStyles, ComponentSelection, HasComponentSelection and JsonUtil are non-UI and stay in stroom.dashboard.client.table (TableRowStyles is shared with QueryResultTable).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/dashboard/client/table/TablePresenter.java:863 | `dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX)` | dynamic | Not an element style: DataGrid's column model owns the width (DataGrid.java:449); drop the Unit argument when DataGrid offers setColumnWidth(column, px) |

| Into the template | What | Detail |
| --- | --- | --- |
| TableViewImpl.ui.xml:4 / TablePresenter.java:243-245 | component-declaration | <dg:PagerPanel ui:field="pager"/> (wave 5) inside the TableViewImpl max stroom-dashboard-table root, once PagerViewImpl has a no-arg constructor |
| TablePresenter.java:236-239 | component-declaration | <dg:DataGrid ui:field="grid" virtual="true" addStyleNames="TablePresenter"/> once DataGrid drops the bus from its constructor; until then only the class and virtual setting move |
| TablePresenter.java:253 | aria | 'Table actions' toolbar name, as a toolbarName attribute on PagerView if wave 5 adds one |

**Stays in Java:** Add Column, Download, Column Filters, Annotate, Ask AI buttons (TablePresenter.java:247-271) on PagerView's toolbar, permission-dependent; GroupLevelButtons on the pager toolbar; showValueFilters at :472/:474: state class; AbstractRowStyles.java:57-62 conditional formatting classes: from rule data; SelectionPopup for columns, Download/Format/RenameColumn/ColumnFunctionEditor/ColumnValuesFilter/ColumnFilterDialog: popups and dialogs; caption 'Download Options' (:548).

## `ColumnHeaderCell`

cell, dumb; complexity 2, effort S; used by 1.

**Since the merge.** ColumnHeaderHtmlUtil now uses Stroom's SortIcon in stroom.widget.datagrid.client in place of GWT's deleted cellview SortIcon. ColumnHeader still extends GWT's cellview Header, which survives. No longer in flight.

- **Component:** `stroom.query.client.presenter.ColumnHeaderCell (with ColumnHeader, ColumnTitleCell, ColumnHeaderHtmlUtil; a cell keeps its name and package)`
- **Controller:** `none`
- **Template:** `none` → `none (built in Java because grid header cells render SafeHtml; the design keeps SafeHtml for grid cells)`
- **Names:** Keeps its name; exists today only once (stroom/query/client/presenter). The record's note that data_grid2 is changing ColumnHeaderHtmlUtil is history: it has landed.

**Stays in Java:** ColumnTitleCell.java:38-44 classes svgIcon column-valueFilterIcon, icon-colour__blue (state), column-valueFilter: cell HTML; ColumnHeaderHtmlUtil.java:48-56 column-top, title from column info, column-label: cell HTML with data; FilterCell embedded via ColumnHeaderCell.create (:34-55).

## `QueryResultTablePresenter`

list, presenter; complexity 5, effort XL; used by 13.

**Since the merge.** Mechanical renames: DataGrid, and PagerView, MessagePanel, Heading, HeadingListener and SortIcon now from stroom.widget.datagrid.client (SortIcon replacing GWT's deleted cellview one). Header menus still go through ShowMenuEvent/HideMenuEvent, which survive. No longer in flight.

- **Component:** `stroom.query.client.component.QueryResultTable`
- **Controller:** `stroom.query.client.component.QueryResultTableController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryResultTableViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTable.ui.xml`
- **Goes:** `QueryResultTableView interface (QueryResultTablePresenter.java:983)`; `stroom/query/client/view/QueryResultTableViewImpl.java`; stroom/query/client/gin/QueryModule.java:88-91 bindPresenterWidget(QueryResultTablePresenter, QueryResultTableView, QueryResultTableViewImpl)
- **Names:** No clash. QueryTableColumnsManager becomes stroom.query.client.component.QueryResultColumnsController (free); QueryTableColumnValuesDataSupplier stays a model helper. DataGridWidget in the record is now stroom.widget.datagrid.client.DataGrid, PagerView is stroom.widget.datagrid.client.PagerView; the 'leave until data_grid2' note is history.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| stroom/query/client/presenter/QueryResultTablePresenter.java:804 | `dataGrid.setColumnWidth(expanderColumn, expanderColumnWidth, Unit.PX)` | dynamic | Not an element style: DataGrid keeps the width in its column model (DataGrid.java:449 ignores the unit) and owns --column-width; drop the Unit argument when DataGrid offers setColumnWidth(column, px) |

| Into the template | What | Detail |
| --- | --- | --- |
| QueryResultTableViewImpl.ui.xml:4 and QueryResultTablePresenter.java:199-201 | component-declaration | <dg:PagerPanel ui:field="pager"/> (wave 5) declared inside the TableViewImpl max stroom-dashboard-table root once PagerViewImpl has a no-arg constructor (today it injects its Binder, PagerViewImpl.java:55) |
| QueryResultTablePresenter.java:193-197 | component-declaration | <dg:DataGrid ui:field="grid" tableName="Query Results" virtual="true" addStyleNames="TablePresenter"/> once DataGrid no longer takes the bus in its constructor (DataGrid.java:192; the 'DOM-bubbled requests' item before the waves); until then the grid is made in Java and only these three settings move |
| QueryResultTablePresenter.java:235 | aria | Toolbar name 'Table actions': a toolbarName attribute on the declared PagerView if wave 5 adds one; else stays |

**Stays in Java:** Download, Column Filters, Reset, Annotate, Ask AI buttons (QueryResultTablePresenter.java:236-255): added to PagerView's toolbar in Java, with permission-dependent visibility; GroupLevelButtons built on the pager toolbar (:230-231); showValueFilters toggled at :407/:409: state class; row styles (TableRowStyles), columns, header cells, expander column: data; Download/Format/ColumnFilter/ColumnValuesFilter/Rules: dialogs; the dialog caption 'Download Options' (:858): set by the controller when showing it.

## `FloorMapEventStoreDataPresenter`

tab, presenter; complexity 2, effort S; used by 4.

- **Component:** `none (shows the shared stroom.query.client.component.QueryDataPane)`
- **Controller:** `stroom.floormap.client.component.FloorMapEventStoreDataController`
- **Template:** `none (FloorMapEventStoreDataViewImpl extends QueryDataViewImpl and its Binder)` → `none (uses QueryDataPane.ui.xml; FloorMapEventStoreDataViewImpl only re-used QueryDataViewImpl's template)`
- **Goes:** `FloorMapEventStoreDataView interface (FloorMapEventStoreDataPresenter.java:52)`; `stroom/floormap/client/view/FloorMapEventStoreDataViewImpl.java (unit 64, empty subclass)`; stroom/floormap/client/gin/FloorMapModule.java:104-106 bindPresenterWidget(FloorMapEventStoreDataPresenter, FloorMapEventStoreDataView, FloorMapEventStoreDataViewImpl)
- **Names:** Controller only (extends AbstractQueryDataController<FloorMapEventStoreDoc>); no clash. FloorMapEventStoreDataDefaults stays as a model helper beside it.

**Stays in Java:** default query and preferred columns: data from FloorMapEventStoreDataDefaults.

## `AbstractQueryDataPresenter`

tab, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.query.client.component.QueryDataPane`
- **Controller:** `stroom.query.client.component.AbstractQueryDataController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryDataViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.ui.xml`
- **Goes:** `stroom/query/client/presenter/QueryDataView.java`; `stroom/query/client/view/QueryDataViewImpl.java`; stroom/query/client/presenter/QueryDataUiHandlers.java (onRun/onStop/onReset/onCreateDashboard become one setOnAction(Consumer<QueryDataAction>)); the empty subclass views and their view interfaces: FloorMapEventStoreDataViewImpl, SqlTemporalStoreDataViewImpl, IndexDataViewImpl, PlanBDataViewImpl (and their bindings, see units 150, 432 and wave 13 units 55, 59)
- **Names:** Four subclasses (IndexData, SqlTemporalStoreData, FloorMapEventStoreData, PlanBData), so the abstract controller stays abstract rather than merging. The component is shared and concrete: 'AbstractQueryData' is wrong for a dumb component and 'QueryData' clashes with the shared DTO stroom.processor.shared.QueryData, so QueryDataPane (free).

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/query/client/view/QueryDataViewImpl.java:44-51 | structure | Declare the four buttons in the existing <b:Toolbar name="Query actions">: <b:ToolbarButton ui:field="reset" svg="UNDO" title="Reset to Default" addStyleNames="QueryButtons-button undo"/>, run (PLAY, 'Run', play), stop (STOP, 'Stop', stop), createDashboard (DOCUMENT_DASHBOARD, 'Create Dashboard', create-dashboard) |
| stroom/query/client/view/QueryDataViewImpl.java:71 / ui.xml:16 | component-declaration | <q:QueryResultTable ui:field="table" addStyleNames="TablePresenter"/> (unit 180) replaces tableContainer and setTable(View) |

**Stays in Java:** error text and aria-invalid (QueryDataViewImpl.java:76-92): state; query text and selection range (:60,95-98): data; Enter in the query box runs (:124-131): behaviour in the component, reported through setOnAction.

## `QueryResultTableSplitPresenter`

composite, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.query.client.component.QueryResultTableSplit`
- **Controller:** `stroom.query.client.component.QueryResultTableSplitController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryResultTableSplitViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplit.ui.xml`
- **Goes:** `QueryResultTableSplitView interface (QueryResultTableSplitPresenter.java:206)`; `stroom/query/client/view/QueryResultTableSplitViewImpl.java`; stroom/query/client/gin/QueryModule.java:84-87 bindPresenterWidget(QueryResultTableSplitPresenter, QueryResultTableSplitView, QueryResultTableSplitViewImpl)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| QueryResultTableSplitPresenter.java:116-127 | structure | Inside the existing max dashboard-panel overflow-hidden root: <l:SplitPane ui:field="split" thin="true" sizes="*, 50%" minSizes="0px, 100px" names="Table, Source" addStyleNames="max"> built once in the template instead of lazily in showSplit |
| QueryResultTableSplitPresenter.java:62,125-126 | component-declaration | <q:QueryResultTable ui:field="table" styleName="max"/> (unit 180) and <q:Text ui:field="text"/> (wave 8, stroom.query.client.component.Text) as the two panes; showSplit(show) becomes split.setPaneHidden(text, !show) (SplitPane.java:239) |

**Stays in Java:** whether the source pane shows (selection with stream and event ids): runtime state; source location parsed from the selected row: data.

## `SqlTemporalStoreDataPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `none (shows the shared stroom.query.client.component.QueryDataPane)`
- **Controller:** `stroom.sqlstore.client.component.SqlTemporalStoreDataController`
- **Template:** `none` → `none (uses QueryDataPane.ui.xml; SqlTemporalStoreDataViewImpl only re-used QueryDataViewImpl's template)`
- **Goes:** `SqlTemporalStoreDataView interface (SqlTemporalStoreDataPresenter.java:24)`; `stroom/sqlstore/client/view/SqlTemporalStoreDataViewImpl.java`; stroom/sqlstore/client/gin/SqlTemporalStoreModule.java:38-41 bindPresenterWidget(SqlTemporalStoreDataPresenter, SqlTemporalStoreDataView, SqlTemporalStoreDataViewImpl)
- **Names:** Controller only (extends AbstractQueryDataController<SqlTemporalStoreDoc>); no clash.

**Stays in Java:** default query and the three preferred columns: data.

## `AnalyticDataShardsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.AnalyticDataShards`
- **Controller:** `stroom.analytics.client.component.AnalyticDataShardsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticDataShardsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShards.ui.xml`
- **Goes:** `AnalyticDataShardsView interface (AnalyticDataShardsPresenter.java:139)`; `stroom/analytics/client/view/AnalyticDataShardsViewImpl.java`; stroom/analytics/client/gin/AnalyticsModule.java:76-78 bindPresenterWidget(AnalyticDataShardsPresenter, AnalyticDataShardsView, AnalyticDataShardsViewImpl)
- **Names:** No clash; beside AnalyticStreamDestination in stroom.analytics.client.component.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/analytics/client/view/AnalyticDataShardsViewImpl.java:57 | css-class | dashboard-panel overflow-hidden on the declared table |
| AnalyticDataShardsPresenter.java:77 / ui.xml:8 | component-declaration | <a:AnalyticDataShardList ui:field="shardList" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) |
| AnalyticDataShardsPresenter.java:78 / ui.xml:9 | component-declaration | <q:QueryResultTable ui:field="table" addStyleNames="max dashboard-panel overflow-hidden"/> (unit 180) |

**Stays in Java:** QueryToolbar returned by getToolbars() (AnalyticDataShardsPresenter.java:89-91): on the document toolbar row, not declared (the record's declares list is wrong on this); selected shard and results: data.

## `QueryEditPresenter`

tab, presenter; complexity 4, effort L; used by 7.

- **Component:** `stroom.query.client.component.QueryEdit`
- **Controller:** `stroom.query.client.component.QueryEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/QueryEditViewImpl.ui.xml (plus QueryResultTabsViewImpl.ui.xml, unit 184, merged)` → `stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.ui.xml`
- **Goes:** `QueryEditView interface (QueryEditPresenter.java:535)`; `stroom/query/client/view/QueryEditViewImpl.java`; `stroom/query/client/gin/QueryModule.java:73-76 bindPresenterWidget(QueryEditPresenter, QueryEditView, QueryEditViewImpl)`
- **Names:** No clash. Its users that only show its view (QueryDocEditPresenter, AbstractQueryEditPresenter subclasses, FloorMapQueryPresenter) become controllers that show or declare QueryEdit.

| Into the template | What | Detail |
| --- | --- | --- |
| stroom/query/client/view/QueryEditViewImpl.java:59 | css-class | dashboard-panel overflow-hidden on the declared editor |
| QueryEditPresenter.java:141 / QueryEditViewImpl.ui.xml:9 | component-declaration | <q:QueryHelp ui:field="help" styleName="max"/> (wave 8) replaces queryHelpContainer |
| QueryEditPresenter.java:142 / QueryEditViewImpl.ui.xml:10 | component-declaration | <ed:Editor ui:field="editor" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 6) replaces queryEditorContainer |
| QueryEditPresenter.java:143 / QueryEditViewImpl.ui.xml:12 | component-declaration | resultContainer becomes the merged tabs (unit 184); QueryResultTableSplit (unit 183) and QueryResultVis (unit 181) are layers of resultLayers |

**Stays in Java:** TabDataImpl("Table") and ("Visualisation") added at QueryEditPresenter.java:77-78, 244-245: LinkTabBar tabs are added in Java (no declared tabs exist); QueryToolbar (wave 8) returned from getToolbars() for the document's toolbar row, so not declared here (the record's declares list is wrong on this); query text, query variables, time range: data.

## `IndexDataPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `the shared query data component that unit 16 makes from QueryDataViewImpl (suggested stroom.query.client.component.QueryDataPane); no component of its own`
- **Controller:** `stroom.index.client.component.IndexDataController`
- **Template:** `none` → `none (built in Java because IndexDataViewImpl adds nothing to QueryDataViewImpl; the shared template is unit 16's, from stroom-core-client/src/main/resources/stroom/query/client/view/QueryDataViewImpl.ui.xml)`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/view/IndexDataViewImpl.java`; `IndexDataPresenter.IndexDataView (inner interface, IndexDataPresenter.java:48)`; stroom-core-client/src/main/java/stroom/index/client/presenter/IndexDataPresenter.java (becomes IndexDataController extends unit 16's controller base); `IndexModule.java:63-66 bindPresenterWidget(IndexDataPresenter.class, IndexDataView.class, IndexDataViewImpl.class)`
- **Names:** IndexDataController is free. No IndexData component: the view is the shared one. Unit 16 cannot name its component QueryData: stroom.processor.shared.QueryData is a shared DTO in the client. QueryDataPane and QueryDataPaneController are free.

| Into the template | What | Detail |
| --- | --- | --- |
| declares | component-declaration | QueryResultTable (unit 10) sits in the shared component's tableContainer, attached by the base controller (unit 16); nothing to declare here |

**Stays in Java:** IndexDataPresenter.java getDefaultQuery and preferred columns: StroomQL text built from the index's fields (data); IndexDataPresenter.java onRead REST call for fields (controller).

## `PlanBDataPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `the shared query data component that unit 16 makes from QueryDataViewImpl (suggested stroom.query.client.component.QueryDataPane); no component of its own`
- **Controller:** `stroom.planb.client.component.PlanBDataController`
- **Template:** `none` → `none (built in Java because PlanBDataViewImpl adds nothing to QueryDataViewImpl; the shared template is unit 16's)`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/view/PlanBDataViewImpl.java`; `PlanBDataPresenter.PlanBDataView (inner interface, PlanBDataPresenter.java:22)`; stroom-core-client/src/main/java/stroom/planb/client/presenter/PlanBDataPresenter.java (becomes PlanBDataController extends unit 16's controller base); `PlanBModule.java:61-63 bindPresenterWidget(PlanBDataPresenter.class, PlanBDataView.class, PlanBDataViewImpl.class)`
- **Names:** PlanBDataController is free. No PlanBData component (see unit 625 for why the shared one is not QueryData).

**Stays in Java:** default query and preferred columns per state type (StroomQL built from data).

## `FloorMapEventStoreDataViewImpl`

composite, tidy; complexity 1, effort S; used by 1.

- **Component:** `the shared query data component of unit 16 (suggested stroom.query.client.component.QueryDataPane)`
- **Controller:** `stroom.floormap.client.component.FloorMapEventStoreDataController (unit 4)`
- **Template:** `none` → `none (built in Java because it adds nothing to QueryDataViewImpl; the shared template is unit 16's)`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapEventStoreDataViewImpl.java`; `FloorMapEventStoreDataPresenter.FloorMapEventStoreDataView (inner interface, FloorMapEventStoreDataPresenter.java:52)`; FloorMapModule.java:104-106 bindPresenterWidget(FloorMapEventStoreDataPresenter.class, FloorMapEventStoreDataView.class, FloorMapEventStoreDataViewImpl.class)
- **Names:** The view half of unit 4. It is deleted, not kept: like IndexData and PlanBData, its controller uses the shared query data component. FloorMapEventStoreDataController is free.

## `FloorMapQueryPresenter`

tab, presenter; complexity 3, effort M; used by 5.

- **Component:** `stroom.floormap.client.component.FloorMapQuery`
- **Controller:** `stroom.floormap.client.component.FloorMapQueryController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapQueryViewImpl.ui.xml (unit 66's ViewImpl, folded in)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapQuery.ui.xml`
- **Goes:** `FloorMapQueryView interface (FloorMapQueryPresenter.java:627)`; `stroom/floormap/client/view/FloorMapQueryViewImpl.java (unit 66; its role-to-box pairing moves into FloorMapQuery.java)`; stroom/floormap/client/gin/FloorMapModule.java:137-139 bindPresenterWidget(FloorMapQueryPresenter, FloorMapQueryView, FloorMapQueryViewImpl)
- **Names:** No clash. Static buildQueryVariables (:616) and the row parsing (:381-487) move to a model class (e.g. FloorMapEventQuery) before the split, since FloorMapMap calls them.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapQueryPresenter.java:86 and FloorMapQueryViewImpl.ui.xml:43 | component-declaration | <q:QueryEdit ui:field="queryEdit" styleName="dock-max overflow-hidden"/> (unit 188, this wave) replaces <g:SimplePanel ui:field="queryEditContainer"/> and view.setQueryEditView |
| FloorMapQueryViewImpl.ui.xml:24-41 | component-declaration | FormGroup and SelectionBox x4 already declared, with their text; keep |

**Stays in Java:** column lists, selected columns, setColumnsHintVisible, setColumnMappingsVisible (FloorMapQueryPresenter.java:189-215, 548-549): runtime state; error messages built from role display names (:464-487): text from data.

## `AbstractQueryEditPresenter`

tab, presenter; complexity 2, effort S; used by 2.

- **Component:** `none (shows stroom.query.client.component.QueryEdit)`
- **Controller:** `stroom.analytics.client.component.AnalyticQueryEditController and stroom.analytics.client.component.ReportQueryEditController`
- **Template:** `none` → `none (uses queryEditPresenter.getView(), AbstractQueryEditPresenter.java:41)`
- **Goes:** `stroom/analytics/client/presenter/AbstractQueryEditPresenter.java (merged into the two)`; `stroom/analytics/client/presenter/AnalyticQueryEditPresenter.java`; `stroom/analytics/client/presenter/ReportQueryEditPresenter.java`
- **Names:** The abstract base has two 25-line subclasses differing only in doc type, so it merges into AnalyticQueryEditController and ReportQueryEditController (both free) rather than becoming AbstractQueryEdit; neither has a component. Not GIN-bound. Callers AnalyticRulePresenter and ReportPresenter move in the same commit.

**Stays in Java:** time range, query and table preferences read/write: data.

## `QueryDocEditPresenter`

tab, presenter; complexity 4, effort L; used by 1.

- **Component:** `none (shows stroom.query.client.component.QueryEdit as its widget)`
- **Controller:** `stroom.query.client.component.QueryDocEditController`
- **Template:** `none` → `none (it uses queryEditPresenter.getView() as its own, QueryDocEditPresenter.java:82)`
- **Goes:** `stroom/query/client/presenter/QueryDocEditPresenter.java (moved and renamed)`
- **Names:** Controller only; a QueryDocEdit component would be empty. No clash. Not GIN-bound (made by QueryDocPresenter).

**Stays in Java:** toolbar 'Query actions' with Create Analytic Rule and Create Report (QueryDocEditPresenter.java:88-90): contributed to the document's toolbar row (HasToolbar), not in a template.

