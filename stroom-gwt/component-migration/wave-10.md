# Wave 10: Lists (after data_grid2 lands)

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 4 (25 October 2026):** the date-time, time, schedule and time range boxes' controllers (`DateTimeBoxController`, `TimeBoxController`, `ScheduleBoxController`, `TimeRangeSelectorController`) are already attached by today's presenters, so there is no `setPopupProvider` or `setSchedulePresenterProvider` left to replace: a converted controller makes and attaches them as they do. `CustomFileUpload` is `FileChooser`, with a `FileChooserController` its owner attaches; `ClassificationWrapperView` is `ClassificationWrapper` (`stroom.data.client.component`); the schedule dialog is `SchedulePickerController`. The rows below name what was there when they were written.

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

Convert every list presenter built on PagerView/DataGridWidget into a list component plus a controller that owns its REST data: RestDataProvider with an owner, and FilteredList where the list has a quick filter. Selection goes out through callbacks, and back-references to parents are removed. The floor map grid lists use the shared ToolbarGrid.

## Audited (27 October 2026)

Six reviewers read the wave's 77 commits against this plan, "Done means" and AGENTS.md (the floor map
lists, the type filter and the document lists; the processor, preferences, user and annotation lists;
the dictionary, task, job, property, store, node, cache and security lists; the retention, content,
dependency, rule, AI, account, shard and schedule lists; the dashboard, volume, tag, XPath, shard,
script, trace, activity and filter lists; and the last thirteen, from the profile periods to the
stream lists). Fixed, in 13 commits:

- **A regression on this branch.** The table filter dialog focuses its chosen tab's layer if it is a
  `Focus`, as a presenter was; a `ControllerLayer` wasn't, so the Include Exclude tab (and any
  controller shown as a tab) lost its focus when chosen. `ControllerLayer` is a `Focus` that focuses
  its content if that takes the focus (`TestControllerLayer`).
- **Owned work and replies.** The cache lists' delayed refresh and the node list's delayed redraw are
  cancelled when they go (`onDispose`); the processor list's saves and the annotation client's finds
  (`findAnnotations`, `findAnnotationTags`, which now take an owner) drop replies to a list that has
  gone; the document permission list adds its columns once both its document and the document types
  are known, whichever comes first.
- **BEM and no inline styles.** The type filter's rules are in `type-filter.css` (they were in
  `explorer-tree.css`); the dependency status is `dependency-list__status` (`--ok`, `--missing`);
  a property's value or source that differs between the nodes is red through
  `manage-global-property-list__multiple`, not an inline colour; a pipeline property's or reference's
  state is `pipeline-property--added`, `--removed`, `--inherited` or `--default`, in
  `pipeline-property.css` (were `pipelineStructureViewImpl-property-*` in `components.css`); the inline
  `border: none` five owners put on their lists went, as the lists' roots have no border.
- **Inputs and outputs.** The pathway list gives its choice through `setOnSelectionChange`, not its
  selection model; the retention list is told which rule is the default (`setDefaultRule`), so it no
  longer imports the presenter's constant; the retention and template lists' enabled callbacks are
  `setOnEnabledChange`; the result store settings dialog is made for each use (`makeDialog`); the tab
  session chooser focuses its list when shown.
- **The plan's helpers.** The Elastic field list uses the shared `ListDataProvider`, as the Solr one
  does; the document selection and processor lists add their document columns with
  `DataGridUtil.addDocRefColumn`; the column selection fills its grid with `setRowData(rows)`;
  `DataGrid.setColumnWidth(column, width)` takes pixels (the stream list's expander uses it);
  `PagerPanel.remove` stops paging the list it removes; `HeadedPagerPanel` is no longer a `View`.
- **Names.** The owners' fields and parameters are named for the lists they hold (`dataRetentionPolicyList`,
  not `listPresenter`), in 50 files; class names that went are gone from comments, stories,
  DATA_GRID_DESIGN.md, COMPONENTS.md, WRITING-STORIES.md, COVERAGE-PLAN.md and the open entries of
  ISSUES.md.
- **Smaller.** Commented-out code (the user list's old columns and logging, the template list's
  columns, the cluster values list's demo data, the batch permissions' edit, the stream list's old
  expression) and unused focus methods went; `///` javadoc for the user list's controller and its
  `Mode`, the annotation list's setters, `FactObject`, the cluster value rows, the processor info and
  status helpers and five constructors; the task list's stray block, a garbled template comment and
  javadoc, same-package imports and double blank lines went; the find screens no longer implement
  `FindDocResultListHandler`, which the lists' callbacks replaced.
- **Tests.** Stories for the dialog that chooses which changed documents to save
  (`App/Data/DocRefSelectionDialog`: Default, OkGivesTheDocumentsTicked, CancelGivesNothing).

Checked and not changed: the `"<br/>"` headings a reviewer thought would show as text are HTML
(`DataGrid.addColumn(column, String, width)` treats its text as HTML), so they show nothing, as before;
the document permission list has stories (`UserTabScreen.DocumentPermissionsTab`,
`DocumentPermissionsScreen`).

Left, on purpose (recorded below or in ISSUES.md):

- **Removals wait.** Code and rules this wave left unused stay until removals are allowed:
  `AskStroomAiClient.listChats`, `ScheduledProcessHistoryList.clearRows`,
  `DataGridUtil.colouredCellExtractor`, `ElasticIndexFieldDataProvider`, `FindDocResultListHandler`,
  and `components.css`'s `.solrIndexFieldListViewImpl-synchState` (copied to
  `solr-index-field-list.css`) and node ping bar rules (`.nodePingBar-*`).
- **Wrappers kept.** The `SimplePanel` wrappers around the key binding, keyboard shortcut, application
  permission, Elastic and Solr field lists and the include/exclude filter keep the DOM, and so the
  screenshots, as they were; the include/exclude dictionary list is a `DocRefList`, which takes no
  toolbar children, so its buttons are added in Java.
- **No `ToolbarGrid`.** The four floor map lists share the `floormap-list` block (`floormap-list__toolbar`
  keeps the 26px strip), not a new `ToolbarGrid` widget, as nothing else would use it.
- **Separate callbacks.** Lists with Add, Edit and Remove have a callback each (`setOnAdd`,
  `setOnEdit`, `setOnRemove`) rather than an action enum, across the wave.
- **Nested types stay nested.** `UserListController.Mode` and the keyboard shortcuts' row are at the
  bottom of their classes, as AGENTS.md allows.
- **The task monitor is the pager's.** The application permissions and user lists' requests spin the
  pager's refresh button, as every other list's do, rather than a monitor of their own.
- **Copyright years.** A component is new (2026); its controller continues the presenter's code and
  keeps its year.
- **Helpers with no owner.** `NodeClient`, `NodeGroupClient`, `CredentialClient`, `ResultStoreModel`,
  `UiConfigCache` and `DocumentTypeCache` take no owner, so their replies still reach a list that has
  gone (ISSUES.md, "Replies helpers give to lists that have gone").
- **The constraint list's read-only argument** stays with the bug it keeps (ISSUES.md), and the
  retention and template lists' action menus still have no opener (ISSUES.md).
- **History.** Two commits don't build on their own: `4608101725` holds a file of the next commit, and
  `406bbb67cf` the rename of `PropertyListPresenter` to `PropertyListController` that `058f8fc228`
  completes. They are left as they are rather than rewriting the branch.

## CSS and template notes for the wave

- List frame: every list here declares the wave-5 pager frame <dg:PagerView> (as a no-arg widget) holding <dg:DataGrid> (renamed from DataGridWidget, package stroom.widget.datagrid.client); tableName, multiLine, pageSize, profile, accessibleName and striped become template attributes; the toolbar's name and its ToolbarButtons (svg, title, enabled="false") are declared inside it as PipelineStructureViewImpl.ui.xml already does with <v:Toolbar>/<v:ToolbarButton>. Only the " (read only)" style suffixes and enabled states stay in Java.
- Filtered lists (AccountsList, IndexFieldList, PathwayList, TracesList) declare the shared QuickFilterPage/FilteredList frame (wave 10 entry 0) as their root with accessibleName in the template.
- Reuse form-control-background form-control-border (components.css:1227,1235) for the bordered lists inside forms: FeedDependencyList, ProfilePeriodList.
- Reuse default-min-sizes (components.css:1085): ProcessorProfileList, FsVolumeGroupList, FsVolumeStatusList, AnnotationTagList, IndexVolumeGroupList.
- Reuse max, dock-container-vertical, dock-min, dock-max, overflow-hidden, w-100, h-100 (stroom.css:793-860) for layout; keyBindingPreferences-list (components.css:1065) and messageContainer (components.css:1449) stay.
- New: type-filter__scroll in explorer-tree.css (min-width 50px, max-width 600px, max-height 600px) for TypeFilter.
- New: toolbar-grid, toolbar-grid__toolbar (flex: 0 0 26px) and toolbar-grid__grid (flex: 1 1 auto; min-height: 0) in data-grid.css for the shared ToolbarGrid that replaces the four floor map DockLayoutPanel views; check the 26px toolbar against the screenshots.
- New: traces-list__operation in pathways.css (display flex, align-items center, gap 4px) for the inline style in TracesList's operation cell.
- FindResultLists.create builds the grid in Java (profile LISTBOX, accessible name, striped); AiChatHistoryResultList and FindAnnotationList need a FindResultLists.configure(grid, ...) that wires selection on a grid declared in the template.
- No dynamic (runtime-valued) inline styles in units 46-90; column widths go through the DataGrid API, not element styles.
- Paths in 'where' are file names under stroom-core-client/src/main/java/stroom/<area>/client/presenter\|view unless given in full; CSS files are under stroom-app/src/main/resources/ui/css/.
- Assumed list shell (wave 5): every PagerView list in this range declares <dg:PagerView ui:field="pager"> (xmlns:dg="urn:import:stroom.widget.datagrid.client") with its DataGrid as the content child and the toolbar's ToolbarButton/ToolbarToggleButton (stroom.widget.button.client; Toolbar already takes button children in templates, see Toolbar.java:40-45 and PipelineStructureViewImpl.ui.xml:11) as toolbar children plus a toolbarName attribute. This needs wave 5 to give PagerView a no-arg constructor, a content child and a toolbar child slot, and DataGrid a no-arg constructor (bus moved to DOM-bubbled requests); DataGrid already has setTableName, setMultiLine, setAccessibleName, setProfile and setPageSize for template attributes.
- Grid columns, cells (TickBoxCell, ActionCell, ActionMenuCell, CommandLinkCell, SvgCell, ExpanderCell, DocRefCell, FindResultCell) and key providers cannot be template-declared; they stay in each component's Java, with clicks going out as callbacks. TooltipPresenter, SchedulePopup and the edit dialogs listed in 'declares' are popups/dialogs opened with makeDialog, not declared.
- Quick-filter lists (units 18, 20, 22, 24, 26, 27, and 57, 70, 86, 87 in the other half) declare <v:QuickFilterPage accessibleName="..."> wrapping their PagerView and DataGrid; this is the before-the-waves FilteredList in all but name.
- Toolbar-above-grid shell used by the four FloorMap lists (units 5-8, whose ViewImpls are units 81-84): root max dock-container-vertical, toolbar dock-min, grid max dock-max; one new class .floormap-list__toolbar { flex: 0 0 26px; overflow: hidden; } in stroom-floormap.css keeps the 26px strip DockLayoutPanel gave it.
- New classes proposed in this range: .floormap-list__toolbar (stroom-floormap.css), .marker-list__summary (data-grid.css), .dependency-status / --ok / --missing (components.css), .tab-session-chooser__item (components.css); the ping bar gets --node-ping-bar__width used by .nodePingBar-bar (components.css:1384). Reused: w-100, max, dock-*, overflow-hidden (stroom.css), default-min-sizes, form-control-border form-control-background, documentUserPermissions-table, elasticFieldListView-statusMessage, solrIndexFieldListViewImpl-synchState (components.css), form-padding (form.css).
- Dead class: credentials-list (CredentialsListPresenter.java:171) has no CSS rule; drop it.
- ElasticIndexFieldDataProvider and SolrIndexFieldDataProvider are byte-for-byte the same as stroom.config.global.client.presenter.ListDataProvider; units 15 and 29 can delete them and use that one.

## `FloorMapFactListViewImpl`

composite, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.widget.datagrid.client.ToolbarGrid`
- **Controller:** `none`
- **Template:** `none (DockLayoutPanel built in Java)` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ToolbarGrid.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapFactListViewImpl.java`; `FloorMapFactListView interface (FloorMapFactListPresenter.java:470-)`; stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:145-147 bindPresenterWidget(FloorMapFactListPresenter, ...View, ...ViewImpl)
- **Names:** The four floor map views (units 81-84) are the same toolbar-above-grid shell, so they become one shared dumb widget, ToolbarGrid (named in 'Before the waves'; free in all_simple_names.json), in the widget module beside DataGrid. FloorMapFactList, FloorMapGroups, FloorMapTimeList and FloorMapTracking (wave 10 entries 5-8) declare <dg:ToolbarGrid> and add their buttons to its getToolbar().

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapFactListViewImpl.java:48 | `gridContainer.setSize("100%", "100%")` | static | toolbar-grid__grid in data-grid.css: flex: 1 1 auto; min-height: 0 (the grid fills what the toolbar leaves) |
| FloorMapFactListViewImpl.java:50-53 | `new DockLayoutPanel(Unit.PX); root.setSize("100%","100%"); addNorth(toolbar, 26)` | static | root class "max dock-container-vertical toolbar-grid" (max, dock-container-vertical in stroom.css); toolbar-grid__toolbar in data-grid.css: flex: 0 0 26px |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapFactListViewImpl.java:44-53 | structure | <g:FlowPanel styleName="max dock-container-vertical toolbar-grid"><v:Toolbar ui:field="toolbar" addStyleNames="toolbar-grid__toolbar"/><g:SimplePanel ui:field="grid" styleName="toolbar-grid__grid"/></g:FlowPanel> |

**Stays in Java:** setGrid(DataGrid) and getToolbar(): which grid and which buttons is the floor map list's business.

## `FloorMapGroupsViewImpl`

composite, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.widget.datagrid.client.ToolbarGrid (merged into unit 81)`
- **Controller:** `none`
- **Template:** `none (DockLayoutPanel built in Java)` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ToolbarGrid.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupsViewImpl.java`; `FloorMapGroupsView interface (FloorMapGroupsPresenter.java:541-)`; stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:169-171 bindPresenterWidget(FloorMapGroupsPresenter, ...View, ...ViewImpl)
- **Names:** Identical to FloorMapFactListViewImpl (diff shows only names and comments); replaced by the shared ToolbarGrid.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapGroupsViewImpl.java:46 | `gridContainer.setSize("100%", "100%")` | static | toolbar-grid__grid (data-grid.css) |
| FloorMapGroupsViewImpl.java:48-51 | `new DockLayoutPanel(Unit.PX); root.setSize("100%","100%"); addNorth(toolbar, 26)` | static | max dock-container-vertical toolbar-grid (stroom.css) and toolbar-grid__toolbar (data-grid.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapGroupsViewImpl.java:42-51 | structure | the ToolbarGrid template (see unit 81) |

**Stays in Java:** none.

## `FloorMapTimeListViewImpl`

composite, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.widget.datagrid.client.ToolbarGrid (merged into unit 81)`
- **Controller:** `none`
- **Template:** `none (DockLayoutPanel built in Java)` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ToolbarGrid.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimeListViewImpl.java`; `FloorMapTimeListView interface (FloorMapTimeListPresenter.java:361-)`; stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:149-151 bindPresenterWidget(FloorMapTimeListPresenter, ...View, ...ViewImpl)
- **Names:** Identical shell; replaced by the shared ToolbarGrid.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapTimeListViewImpl.java:46 | `gridContainer.setSize("100%", "100%")` | static | toolbar-grid__grid (data-grid.css) |
| FloorMapTimeListViewImpl.java:48-51 | `new DockLayoutPanel(Unit.PX); root.setSize("100%","100%"); addNorth(toolbar, 26)` | static | max dock-container-vertical toolbar-grid (stroom.css) and toolbar-grid__toolbar (data-grid.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTimeListViewImpl.java:42-51 | structure | the ToolbarGrid template (see unit 81) |

**Stays in Java:** none.

## `FloorMapTrackingViewImpl`

composite, dumb; complexity 1, effort S; used by 1.

- **Component:** `stroom.widget.datagrid.client.ToolbarGrid (merged into unit 81)`
- **Controller:** `none`
- **Template:** `none (DockLayoutPanel built in Java)` → `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ToolbarGrid.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTrackingViewImpl.java`; `FloorMapTrackingView interface (FloorMapTrackingPresenter.java:563-)`; stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:153-155 bindPresenterWidget(FloorMapTrackingPresenter, ...View, ...ViewImpl)
- **Names:** Identical shell; replaced by the shared ToolbarGrid.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapTrackingViewImpl.java:46 | `gridContainer.setSize("100%", "100%")` | static | toolbar-grid__grid (data-grid.css) |
| FloorMapTrackingViewImpl.java:48-51 | `new DockLayoutPanel(Unit.PX); root.setSize("100%","100%"); addNorth(toolbar, 26)` | static | max dock-container-vertical toolbar-grid (stroom.css) and toolbar-grid__toolbar (data-grid.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTrackingViewImpl.java:42-51 | structure | the ToolbarGrid template (see unit 81) |

**Stays in Java:** none.

## `TypeFilterPresenter`

widget, presenter; complexity 2, effort S; used by 4.

**Since the merge.** Renames only (DataGrid, DataGridSelectionEventManager). Nothing material.

- **Component:** `stroom.explorer.client.component.TypeFilter`
- **Controller:** `stroom.explorer.client.component.TypeFilterController`
- **Template:** `none (TypeFilterViewImpl builds a ScrollPanel in Java)` → `stroom-core-client/src/main/java/stroom/explorer/client/component/TypeFilter.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/TypeFilterViewImpl.java`; `TypeFilterView interface (TypeFilterPresenter.java:280-283)`; stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:132 bindPresenterWidget(TypeFilterPresenter, TypeFilterView, TypeFilterViewImpl); `stroom-core-client/src/main/java/stroom/explorer/client/presenter/TypeFilterPresenter.java (split into the two classes)`
- **Names:** TypeFilter and TypeFilterController are free in all_simple_names.json and in this wave. Listeners<TypeFilterPresenter> becomes Listeners on the controller (two callers, ExportConfigPresenter and NavigationPresenter, both listen), passing the included types rather than itself.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| TypeFilterViewImpl.java:30 | `scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px")` | static | New class type-filter__scroll in explorer-tree.css (beside .type-filter): min-width: 50px |
| TypeFilterViewImpl.java:31 | `setProperty("maxWidth", 600 + "px")` | static | type-filter__scroll (explorer-tree.css): max-width: 600px |
| TypeFilterViewImpl.java:32 | `setProperty("maxHeight", 600 + "px")` | static | type-filter__scroll (explorer-tree.css): max-height: 600px |

| Into the template | What | Detail |
| --- | --- | --- |
| TypeFilterViewImpl.java:29 | structure | <g:ScrollPanel styleName="type-filter__scroll"> as the root |
| TypeFilterPresenter.java:81 | component-declaration | <dg:DataGrid ui:field="list" profile="LISTBOX" fitContent="true" fitWidth="true" cellPadding="false"/> inside the ScrollPanel (DataGrid is wave 5, was DataGridWidget; needs its no-arg constructor and DEFAULT_LIST_PAGE_SIZE as default) |
| TypeFilterPresenter.java:82,84,85,89 | structure | profile, fitContent, fitWidth and cellPadding become attributes of the declared DataGrid |
| TypeFilterPresenter.java:83 | aria | accessibleName="Document types" on the DataGrid |
| TypeFilterPresenter.java:90 | css-class | addStyleNames="type-filter" on the DataGrid (explorer-tree.css:22) |

**Stays in Java:** TypeFilterPresenter.java:86 setRowChecked(getState): tick state changes with the selection; TypeFilterPresenter.java:91-93 setRowStyles type-filter__all: a per-row class chosen from the row's data; TypeFilterPresenter.java:94 tick-box column with DocumentTypeCell: a Cell, not a widget, so it is added in Java; TypeFilterPresenter.java:95-104 selection event manager and select-all wiring; TypeFilterPresenter.java:122 caption "Filter Types" and the popup position: set by the controller when it shows the popup; the selected-type set and toggle logic (controller, or the component as its own tick state).

## `FloorMapFactListPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Renames only (DataGrid). Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapFactList`
- **Controller:** `stroom.floormap.client.component.FloorMapFactListController`
- **Template:** `none (layout built in Java in FloorMapFactListViewImpl, wave-10 unit 81)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapFactList.ui.xml`
- **Goes:** `FloorMapFactListPresenter.java; its inner FloorMapFactListView interface (:470-482)`; stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapFactListViewImpl.java (listed as unit 81; it must go in the same commit since the template replaces it); `FloorMapModule.java:145-147 bindPresenterWidget(FloorMapFactListPresenter, FloorMapFactListView, FloorMapFactListViewImpl)`; `stroom-gwt-workbench .../gin/editors/FloorMapScreenModule.java:103-105 (same binding)`
- **Names:** No clash. Units 81-84 (the four identical FloorMap*ViewImpl) should be merged into units 5-8, not kept as ViewImpls as their record suggests.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapFactListViewImpl.java:48 | `gridContainer.setSize("100%", "100%")` | static | max (stroom.css:793) + dock-max (stroom.css:828) on the declared DataGrid |
| FloorMapFactListViewImpl.java:50-52 | `new DockLayoutPanel(Unit.PX); root.setSize("100%","100%"); addNorth(toolbar, 26)` | static | root <g:FlowPanel styleName="max dock-container-vertical"> (stroom.css:793,815); toolbar addStyleNames="dock-min floormap-list__toolbar" with new .floormap-list__toolbar { flex: 0 0 26px; overflow: hidden; } in stroom-floormap.css, shared by units 5-8 (a .toolbar is 24px + 2px margins, so check the 26px strip with the screenshot diff) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapFactListPresenter.java:120-121,129 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Facts"/> in the template instead of new DataGrid + view.setGridView |
| FloorMapFactListPresenter.java:134-147 | component-declaration | <b:Toolbar name="Fact list actions"> with ToolbarButton add (ADD, "Add New Object"), end (REMOVE, END_TITLE text, enabled="false"), delete (DELETE, "Erase History (delete every version)", enabled="false"), ToolbarToggleButton showAll (HISTORY, "Show All (ignore time filter)"), help (HELP_OUTLINE, "Fact List help") |
| FloorMapFactListPresenter.java:144 FloorMapEditorHelp.addHelpButton | structure | declare the help button in the template; FloorMapEditorHelp gets a wire(button, heading, content) helper instead of building it |

**Stays in Java:** dataGrid.getElement().setId(gridElementId) (:128): id minted per instance for aria-describedby; multi selection model (:124), columns; enabled states of end/delete from the selection (:154, :333); data provider, selection consumers, help content: controller.

## `FloorMapGroupsPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Renames only (DataGrid). Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapGroups`
- **Controller:** `stroom.floormap.client.component.FloorMapGroupsController`
- **Template:** `none (layout built in Java in FloorMapGroupsViewImpl, unit 82)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapGroups.ui.xml`
- **Goes:** `FloorMapGroupsPresenter.java; inner FloorMapGroupsView (:541-)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapGroupsViewImpl.java (unit 82, same commit)`; `FloorMapModule.java:169-171`; `FloorMapScreenModule.java:133-135`
- **Names:** FloorMapGroups: no clash (FloorMapGroup, the shared model, is a different name). FloorMapGroupEditPresenter in its declares list is a dialog it opens: since wave 7 a one-use `FloorMapGroupEditController` it shows (`show`, with `ControllerDialog.showOnce`), not declared.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapGroupsViewImpl.java:48,50-52 | `setSize("100%","100%"), DockLayoutPanel(Unit.PX), addNorth(toolbar, 26)` | static | same as unit 5: max dock-container-vertical root, dock-min floormap-list__toolbar (new, stroom-floormap.css), max dock-max grid |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapGroupsPresenter.java:145-150 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Groups"/> |
| FloorMapGroupsPresenter.java:154-160 | component-declaration | <b:Toolbar name="Group actions"> with ToolbarButton newGroup (ADD, "New Group"), edit (EDIT, "Edit Group (name, colour and members)", enabled="false"), delete (DELETE, "Delete Group", enabled="false") |

**Stays in Java:** key provider and selection model (:148-149); highlight SvgCell column and swatch name cell (:174-200, :238-243): data-driven cells; edit/delete enabled from selection (:165-166); group edit dialog, random colour, highlight state: controller.

## `FloorMapTimeListPresenter`

list, presenter; complexity 2, effort M; used by 3.

**Since the merge.** Renames only (DataGrid). Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapTimeList`
- **Controller:** `stroom.floormap.client.component.FloorMapTimeListController`
- **Template:** `none (FloorMapTimeListViewImpl, unit 83)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeList.ui.xml`
- **Goes:** `FloorMapTimeListPresenter.java; inner FloorMapTimeListView (:361-)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTimeListViewImpl.java (unit 83, same commit)`; `FloorMapModule.java:149-151`; `FloorMapScreenModule.java:106-108`
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapTimeListViewImpl.java:48,50-52 | `setSize("100%","100%"), DockLayoutPanel(Unit.PX), addNorth(toolbar, 26)` | static | as unit 5 (max dock-container-vertical, floormap-list__toolbar new in stroom-floormap.css, max dock-max) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTimeListPresenter.java:85-90 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Fact Times"/> |
| FloorMapTimeListPresenter.java:94-104 | component-declaration | <b:Toolbar name="Time list actions"> with edit (EDIT, "Edit", enabled="false"), add (ADD, "Add"), delete (DELETE, "Delete", enabled="false"), help (HELP_OUTLINE, "Time List help") wired by FloorMapEditorHelp |

**Stays in Java:** key provider and selection model (:88-89), columns; edit/delete enabled states (:115-116, :179-242); preference-change redraw (listen), selection consumer, scroll-into-view: controller.

## `FloorMapTrackingPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Renames only (DataGrid). ShowMenuEvent is unchanged in stroom.widget.menu.client.presenter. Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapTracking`
- **Controller:** `stroom.floormap.client.component.FloorMapTrackingController`
- **Template:** `none (FloorMapTrackingViewImpl, unit 84)` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTracking.ui.xml`
- **Goes:** `FloorMapTrackingPresenter.java; inner FloorMapTrackingView (:563-)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapTrackingViewImpl.java (unit 84, same commit)`; `FloorMapModule.java:153-155`; `FloorMapScreenModule.java:130-132`
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapTrackingViewImpl.java:48,50-52 | `setSize("100%","100%"), DockLayoutPanel(Unit.PX), addNorth(toolbar, 26)` | static | as unit 5 (max dock-container-vertical, floormap-list__toolbar new, max dock-max) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapTrackingPresenter.java:159-160,166 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Tracking"/> |
| FloorMapTrackingPresenter.java:171-183 | component-declaration | <b:Toolbar name="Tracking actions"> with stopTracking (CLEAR, "Stop Tracking", enabled="false"), addToGroup (ADD, "Add to Group", enabled="false"), ToolbarToggleButton showFacts (LOCATE) |

**Stays in Java:** dataGrid.getElement().setId(gridElementId) (:165): per-instance id for aria-describedby; showFacts title and pressed state (:180-181, :533-534): change with state; enabled states (:188-189, :224); cell-preview re-select handling, add-to-group menu, group callbacks: controller.

## `DocumentCreatePermissionsListPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical renames: DataGrid and DataGridSelectionEventManager. No longer in flight.

- **Component:** `stroom.security.client.component.DocumentCreatePermissionsList`
- **Controller:** `stroom.security.client.component.DocumentCreatePermissionsListController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/DocumentCreatePermissionsListViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/DocumentCreatePermissionsList.ui.xml`
- **Goes:** `DocumentCreatePermissionsListPresenter.java; inner DocumentCreatePermissionsListView (:329-334)`; stroom-core-client/src/main/java/stroom/security/client/view/DocumentCreatePermissionsListViewImpl.java (it needlessly extends ViewWithUiHandlers<QuickFilterUiHandlers>); `SecurityModule.java:161-164`; `stroom-gwt-workbench .../gin/SecurityScreenModule.java:114-116`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentCreatePermissionsListPresenter.java:75-81 and DocumentCreatePermissionsListViewImpl.java:47-50 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Document Create Permissions" addStyleNames="max form-control-border form-control-background"/> in place of the SimplePanel slot (template :13) |
| DocumentCreatePermissionsListViewImpl.ui.xml:8-20 | component-declaration | SplitPane and FormGroups with their labels and help text already in the template; keep |

**Stays in Java:** details SafeHtml (:93-96): built from data; columns from DocumentTypeCache (data-driven, one per type) and tick-box state; REST and permission logic: controller.

## `FindDocResultListPresenter`

list, presenter; complexity 2, effort S; used by 3.

**Since the merge.** Renames only; built with FindResultLists.create. Nothing material.

- **Component:** `stroom.explorer.client.component.FindDocResultList`
- **Controller:** `stroom.explorer.client.component.FindDocResultListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/explorer/client/component/FindDocResultList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/FindDocResultListPresenter.java (no binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FindDocResultListPresenter.java:82-87,150 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="resultList" tableName="Documents found" pageSize="100"/>, configured by FindResultLists as in unit 42 |

**Stays in Java:** FindResultCell column and selection; empty-filter short cut, page start, REST find, filter error: controller.

## `TabSessionChooserPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

**Since the merge.** Renames only (DataGrid, DataGridSelectionEventManager). Nothing material.

- **Component:** `stroom.explorer.client.component.TabSessionChooser`
- **Controller:** `stroom.explorer.client.component.TabSessionChooserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/TabSessionChooserViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/TabSessionChooser.ui.xml`
- **Goes:** `TabSessionChooserPresenter.java; inner TabSessionChooserView (:119-122)`; `stroom-core-client/src/main/java/stroom/explorer/client/view/TabSessionChooserViewImpl.java`; `AppModule.java:159 bind(TabSessionChooserPresenter.class) and :174 bindSharedView(TabSessionChooserView, TabSessionChooserViewImpl)`; `stroom-gwt-workbench .../gin/content/ContentAppScreenModule.java:49-51`
- **Names:** No clash. Generic in T; the component is TabSessionChooser<T> (UiBinder owner may be generic). It is dialog content: its controller shows it and closes on Enter/double click instead of firing HidePopupRequestEvent itself (:68).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| TabSessionChooserPresenter.java:77 | `<div style="padding: 5px; min-width: 200px"> in the cell` | static | new .tab-session-chooser__item { padding: 5px; min-width: 200px; } in components.css (no existing class has exactly this; default-min-sizes also sets min-height) |

| Into the template | What | Detail |
| --- | --- | --- |
| TabSessionChooserPresenter.java:58-63 and TabSessionChooserViewImpl.java:42-46 | component-declaration | <dg:DataGrid ui:field="list" profile="LISTBOX" accessibleName="Tab sessions" pageSize="100000" addStyleNames="w-100"/> inside the bordered FlowPanel (template :6), replacing setSelectionList(Widget) |
| TabSessionChooserViewImpl.java:44 | css-class | w-100 (stroom.css:843) in the template instead of addClassName |
| TabSessionChooserViewImpl.ui.xml:5-6 | css-class | keeps max default-min-sizes dock-container-vertical overflow-hidden and max form-control-border form-control-background |

**Stays in Java:** display value per row from setDisplayValueFunction (:79): data; selection model, setSelectionList rows (:110-113); double-click/Enter confirm: setOnConfirm callback to the controller.

## `DocRefSelectionPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

**Since the merge.** Mechanical rename only (DataGridWidget to DataGrid). TickBoxCell (its selected column) was missed from embeds before. No longer in flight.

- **Component:** `stroom.pipeline.client.component.DocRefSelection`
- **Controller:** `stroom.pipeline.client.component.DocRefSelectionController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/client/view/DocRefSelectionViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/client/component/DocRefSelection.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/client/view/DocRefSelectionViewImpl.java`; `DocRefSelectionView interface (DocRefSelectionPresenter.java:133-138)`; stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:126 bindPresenterWidget(DocRefSelectionPresenter, DocRefSelectionView, DocRefSelectionViewImpl); `setMessageText (DocRefSelectionPresenter.java:~103), its text moves to the template`
- **Names:** No clash. PipelinePlugin is not a component, so it makes the dialog with appRoot.makeDialog(provider); the controller shows it (OK/Cancel) and returns the selected DocRefs through a callback.

| Into the template | What | Detail |
| --- | --- | --- |
| DocRefSelectionViewImpl.java:51-57 and PipelinePlugin.java:117-118 | text | <g:Label styleName="messageContainer" text="The following documents have changed. Please select the ones you want to save:"/> in place of the SimplePanel and the HTML built in Java (only caller, fixed text) |
| DocRefSelectionPresenter.java:54-62 and DocRefSelectionViewImpl.ui.xml:22 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Document References" contextMenu="false" addStyleNames="listContainer dock-max"/> in place of listContainer (hasContextMenu(false) at :58) |

**Stays in Java:** DocRefSelectionPresenter.java:57 doc-ref column (uses the bus until the wave-5 DOM-bubbled HyperlinkEvent lands); tick column and match-all selection state; PipelinePlugin.java:121-122 popup height computed from the number of documents (controller).

## `MarkerListPresenter`

list, presenter; complexity 2, effort M; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and WrapperView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.data.client.component.MarkerList`
- **Controller:** `stroom.data.client.component.MarkerListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/data/client/component/MarkerList.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/data/client/presenter/MarkerListPresenter.java (no view interface or binding; its WrapperView goes: the template's root is the DataGrid)
- **Names:** No clash (Marker is the shared model).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| MarkerListPresenter.java:185 | `<div style="font-weight:500;"> summary row` | static | new .marker-list__summary { font-weight: 500; } in data-grid.css (or reuse stroom.css's b { font-weight: 500 } by wrapping the text in <b>; check the screenshot either way) |

| Into the template | What | Detail |
| --- | --- | --- |
| MarkerListPresenter.java:69-71,86 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Markers" multiLine="true"/> as the whole template, replacing WrapperView.setWidget |
| MarkerListPresenter.java:122 | text | expander column heading "<br/>" (HTML used as an empty heading): use an empty heading with an aria label instead |

**Stays in Java:** key provider and row spans (:74-85); expander, severity, element, stream, line, col, message columns: built from data; summary text with item counts (:160-180).

## `DocumentListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Renames only (DataGrid; PagerView and FindResultLists in stroom.widget.datagrid.client). The list is built with FindResultLists.create, which the first record did not name. Nothing material.

- **Component:** `stroom.explorer.client.component.DocumentList`
- **Controller:** `stroom.explorer.client.component.DocumentListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/DocumentListPresenter.java (no binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentListPresenter.java:82-87,136 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="resultList" tableName="Documents" pageSize="100"/>; FindResultLists.create (FindResultLists.java:51) becomes a configure(grid, cell, selection, onOpen, onFocus) applied to the declared grid |

**Stays in Java:** FindResultCell column and selection model; page start sync, first-row selection on filter change, REST advancedFind, filter error: controller.

## `DocumentPermissionsListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.explorer.client.component.DocumentPermissionsList`
- **Controller:** `stroom.explorer.client.component.DocumentPermissionsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentPermissionsList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/DocumentPermissionsListPresenter.java (no binding)`
- **Names:** No clash with unit 22 DocumentUserPermissionsList or 21 DocumentCreatePermissionsList.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentPermissionsListPresenter.java:75-78 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Document Permissions"/> |

**Stays in Java:** DocRefCell and permission columns (addColumns); REST: controller.

## `ProcessorListPresenter`

list, presenter; complexity 4, effort L; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.processor.client.component.ProcessorList`
- **Controller:** `stroom.processor.client.component.ProcessorListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProcessorListPresenter.java`; `the injected PagerView (no binding of its own)`
- **Names:** No clash. ProcessorInfoBuilder and RestSaveQueue move to stroom.processor.client.component with the controller (ProcessorPresenter imports ProcessorInfoBuilder from there); ProcessorStatusUtil is package-private and moves with the columns into the same package. RestSaveQueue's eventBus/HasHandlers goes: the controller reports failures with AlertEvent.fire...(this).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorListPresenter.java:118-121 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Processors"/> |

**Stays in Java:** columns with ExpanderCell, SvgCell, TickBoxCell, ValueSpinnerCell (Cells, added in Java); ProcessorListPresenter.java:115 TooltipPresenter: the wave-3 Tooltip popup, made and owned by the controller, not declared; ProcessorListPresenter.java:126-160 REST save queues and taskMonitorFactory(getView()) at 138,153: controller, task monitor from Contexts.TASK_MONITOR; tree expansion and row data.

## `ProcessorProfileListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.processor.client.component.ProcessorProfileList`
- **Controller:** `stroom.processor.client.component.ProcessorProfileListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProcessorProfileListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorProfileListPresenter.java:53-58 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Processor Profiles"/> |
| ProcessorProfileListPresenter.java:59 | css-class | addStyleNames="default-min-sizes" (components.css:1085) on the root |

**Stays in Java:** ProcessorProfileClient REST paging (controller); columns.

## `QuickFilterPageViewImpl`

composite, presenter; complexity 1, effort S; used by 14.

- **Component:** `stroom.widget.dropdowntree.client.view.QuickFilterPage`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/dropdowntree/client/view/QuickFilterPageViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/QuickFilterPage.ui.xml`
- **Goes:** stroom-core-client-widget/.../dropdowntree/client/view/QuickFilterPageView.java and QuickFilterPageViewImpl.java: only once UserPermissionReportPresenter (wave 12) and BatchDocumentPermissionsPresenter (wave 15) have moved; until then QuickFilterPageViewImpl is a thin wrapper over QuickFilterPage (rule 5); `SecurityModule.java:118 bindSharedView(QuickFilterPageView, QuickFilterPageViewImpl) (with the wrapper, same timing)`; `ScreenViewsModule.java:104 bindSharedView(QuickFilterPageView, QuickFilterPageViewImpl) (same timing)`; QuickFilterUiHandlers.java stays: still used by QuickFilterDialogView (wave 11), ColumnValuesFilterPresenter, FloorMapClusterUiHandlers, UserRefPopupPresenter and the wave-10 lists 57/70/86/87 until they move; it goes with its last caller
- **Names:** QuickFilterPage: no clash in all_simple_names.json or in this wave. No controller: it is a dumb layout (form group, quick filter, content slot) that each list's controller fills, so the suggested QuickFilterPageController is dropped. It stays in the widget module's package (rule 1, widget keeps package). If the before-the-waves FilteredList (QuickFilter + toolbar + DataGrid + pager in one) is built, it is QuickFilterPage with a PagerView and DataGrid declared inside; FilteredList is also free in all_simple_names.json.

| Into the template | What | Detail |
| --- | --- | --- |
| QuickFilterPageViewImpl.java:50 | text | formGroup.setLabel(null): drop; the template's FormGroup simply has no label attribute |
| QuickFilterPageViewImpl.java:74-76 (setDataView(View)) | structure | content slot becomes a HasOneWidget child: callers declare their list inside <v:QuickFilterPage>...</v:QuickFilterPage> instead of setDataView(pagerView) |
| QuickFilterPageViewImpl.java:92-95 (setAccessibleName) and the 6 callers in this wave | aria | accessibleName as a template attribute set by each user, e.g. <v:QuickFilterPage accessibleName="Filter users"> |
| QuickFilterPageViewImpl.ui.xml:6-10 | css-class | keeps max dock-container-vertical, dock-min form-padding, dock-max overflow-hidden (stroom.css, form.css) |
| QuickFilterPageViewImpl.ui.xml:7-8 | component-declaration | <form:FormGroup ui:field="formGroup"> and <view:QuickFilter ui:field="quickFilter"/> already declared; keep |

**Stays in Java:** setOnFilterChange(Consumer<String>) replaces QuickFilterUiHandlers.onFilterChange (QuickFilterPageViewImpl.java:103-106); setQuickFilterText with its change check (:79-85): value from the controller; setFilterError (:88-90): server text; registerPopupTextProvider (:64-66) and setQuickFilterContextHandler (:98-100): the tooltip and context come from UiConfig/REST, set by each controller; setLabel/setHelpText (:54-61) for the wave-12 UserPermissionReport caller that sets "Documents" (fixed text there should move into that caller's template use); focus() (:69-71).

## `KeyBindingPreferencesPresenter`

tab, presenter; complexity 3, effort M; used by 5.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.preferences.client.component.KeyBindingPreferences`
- **Controller:** `stroom.preferences.client.component.KeyBindingPreferencesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/KeyBindingPreferencesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/KeyBindingPreferences.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/KeyBindingPreferencesViewImpl.java`; `stroom-core-client/src/main/java/stroom/preferences/client/KeyBindingPreferencesUiHandlers.java`; `KeyBindingPreferencesView interface (KeyBindingPreferencesPresenter.java:314-)`; stroom-core-client/src/main/java/stroom/preferences/client/gin/UserPreferencesModule.java:63-66 bindPresenterWidget(KeyBindingPreferencesPresenter, ...View, ...ViewImpl); `the injected PagerView`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| KeyBindingPreferencesViewImpl.java:49 | aria | <view:QuickFilter ui:field="quickFilter" accessibleName="Filter keyboard shortcuts"/> |
| KeyBindingPreferencesViewImpl.ui.xml:16 and KeyBindingPreferencesPresenter.java:80-83,94 | component-declaration | replace <g:SimplePanel ui:field="list"> with <dg:PagerPanel ui:field="pager" addStyleNames="dock-max keyBindingPreferences-list"> holding <dg:DataGrid tableName="Keyboard Shortcuts"/> |
| KeyBindingPreferencesPresenter.java:86 | aria | toolbar name="Keyboard shortcut actions" |
| KeyBindingPreferencesPresenter.java:87-93 | component-declaration | <v:ToolbarButton> ADD "Add Shortcut", EDIT "Change Shortcut", DELETE "Remove Shortcut", UNDO "Reset Action to Default Shortcuts", HISTORY "Reset All Shortcuts to Defaults" |

**Stays in Java:** KeyBindingPreferencesPresenter.java:122-136 status column text (Built in / Default / Changed, turned off) built from data; button enabled states (296); KeyShortcutRecorder (wave 9) opened by the controller with makeDialog, not declared; TickBox and FormGroup already declared in the template.

## `KeyboardShortcutsPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

**Since the merge.** Mechanical rename only (DataGridWidget to DataGrid). No longer in flight.

- **Component:** `stroom.preferences.client.component.KeyboardShortcuts`
- **Controller:** `stroom.preferences.client.component.KeyboardShortcutsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/KeyboardShortcutsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/KeyboardShortcuts.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/KeyboardShortcutsViewImpl.java`; `KeyboardShortcutsView interface (KeyboardShortcutsPresenter.java:161-)`; stroom-core-client/src/main/java/stroom/preferences/client/gin/UserPreferencesModule.java:71-74 bindPresenterWidget(KeyboardShortcutsPresenter, ...View, ...ViewImpl)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| KeyboardShortcutsViewImpl.java:47 | aria | <view:QuickFilter ui:field="quickFilter" accessibleName="Filter keyboard shortcuts"/> |
| KeyboardShortcutsViewImpl.ui.xml:9 and KeyboardShortcutsPresenter.java:64-65,79 | component-declaration | replace <g:SimplePanel ui:field="list"> with <dg:DataGrid ui:field="dataGrid" tableName="Keyboard Shortcuts" addStyleNames="dock-max keyBindingPreferences-list"/> |

**Stays in Java:** KeyboardShortcutsPresenter.java:92 dialog caption "Keyboard Shortcuts" and size: set by the controller when it shows the dialog; rows (getRows, static, JUnit-testable) and filtering; column definitions (66-77).

## `AppPermissionsEditPresenter`

composite, presenter; complexity 3, effort M; used by 4.

**Since the merge.** Mechanical renames: DataGrid, DataGridSelectionEventManager (was DataGridWidgetSelectionEventManager), PagerView package. No longer in flight.

- **Component:** `stroom.security.client.component.AppPermissionsEdit`
- **Controller:** `stroom.security.client.component.AppPermissionsEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/AppPermissionsEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissionsEdit.ui.xml`
- **Goes:** `AppPermissionsEditPresenter.java; inner AppPermissionsEditView (:326-333)`; `stroom-core-client/src/main/java/stroom/security/client/view/AppPermissionsEditViewImpl.java`; `SecurityModule.java:144-147 bindPresenterWidget`; `stroom-gwt-workbench .../gin/SecurityScreenModule.java:102-104`
- **Names:** No clash. The ValueChange inner class (:337-) moves to the controller (it is the controller's output).

| Into the template | What | Detail |
| --- | --- | --- |
| AppPermissionsEditPresenter.java:83-90 and AppPermissionsEditViewImpl.java:64-67 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="max"> containing <dg:DataGrid ui:field="dataGrid" tableName="Application Permissions" multiLine="true"/> in place of the SimplePanel 'permissions' (template :18-19); setMultiLine (:185) moves too |
| AppPermissionsEditViewImpl.java:74-78 | text | default labels label="Application Permissions:" and label="Application Permission Details:" on the two FormGroups in the template |
| AppPermissionsEditViewImpl.ui.xml:9-25 | component-declaration | SplitPane, FormGroup and HelpHTML already declared; keep |

**Stays in Java:** FormGroup labels with " for user \"name\":" suffix (AppPermissionsEditViewImpl.java:79-83): built from data; details HTML (:69-72): built from data (DescriptionBuilder); refresh button wiring (:98): setOnRefresh; tick-box column and its REST update, selection-driven details: controller.

## `UserListPresenter`

list, presenter; complexity 4, effort L; used by 4.

**Since the merge.** DataGridWidget -> DataGrid; DataGridView and PagerView now from stroom.widget.datagrid.client; commented-out HoverActionMenuCell column and selectUserIfShown block removed (545 -> 513 lines). ActionMenuCell still used for the action column.

- **Component:** `stroom.security.client.component.UserList`
- **Controller:** `stroom.security.client.component.UserListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/UserList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserListPresenter.java (no own view or binding)`; `implements QuickFilterUiHandlers (:67)`
- **Names:** No clash in all_simple_names.json (only UserListPresenter exists).

| Into the template | What | Detail |
| --- | --- | --- |
| UserListPresenter.java:102-103,127-128 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter users and groups"> |
| UserListPresenter.java:109-112 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Users"/> |

**Stays in Java:** getToolbar() (:453-455) for UsersPresenter:81 and UserAndGroupsPresenter:124; quick filter tooltip (:116-125) and context (:129-130); columns incl. ActionMenuCell; REST, filter error (:414): controller.

## `ElasticIndexFieldListPresenter`

tab, presenter; complexity 2, effort S; used by 3.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.search.elastic.client.component.ElasticIndexFieldList`
- **Controller:** `stroom.search.elastic.client.component.ElasticIndexFieldListController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticIndexFieldListViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticIndexFieldList.ui.xml`
- **Goes:** `ElasticIndexFieldListPresenter.java; inner ElasticIndexFieldListView (:142-147)`; `stroom-core-client/src/main/java/stroom/search/elastic/client/view/ElasticIndexFieldListViewImpl.java`; `ElasticIndexModule.java:37-39 bindPresenterWidget`; `stroom-gwt-workbench .../gin/editors/ElasticIndexScreenModule.java:42-44`; ElasticIndexFieldDataProvider.java: identical to SolrIndexFieldDataProvider and stroom.config.global.client.presenter.ListDataProvider; replace with the latter
- **Names:** No clash. It is a document tab (DocPresenter today), so the controller reads and writes the ElasticIndexDoc as a DocumentEditor part.

| Into the template | What | Detail |
| --- | --- | --- |
| ElasticIndexFieldListPresenter.java:55-62 and ElasticIndexFieldListViewImpl.java:51-53 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="dock-max"> containing <dg:DataGrid ui:field="dataGrid" tableName="Elastic Index Fields"/> in place of the SimplePanel slot (template :5) |
| ElasticIndexFieldListViewImpl.ui.xml:4-7 | css-class | keeps dock-container-vertical, dock-min, elasticFieldListView-statusMessage (components.css:31) |
| ElasticIndexFieldListPresenter.java:126-128 | text | the fixed "Field list updated at: " prefix can be a label in the template beside a value span |

**Stays in Java:** setTableName("Elastic Index '" + name + "' Fields") (:120): built from data; status time (:126-131): data; set as text, not fromTrustedString HTML (ElasticIndexFieldListViewImpl.java:57); columns (:66-100), key provider.

## `ProcessorTaskListPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical rename only: DataGrid, with OrderByColumn and PagerView in stroom.widget.datagrid.client.

- **Component:** `stroom.data.client.component.ProcessorTasksList`
- **Controller:** `stroom.data.client.component.ProcessorTasksListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ProcessorTaskListPresenter.java`; `StreamStoreModule.java:160 bind(ProcessorTaskListPresenter.class)`; `workbench StreamStoreScreenModule.java: processing/:115, query/:115, content/:115, editors/:116`
- **Names:** ProcessorTaskList clashes with the shared DTO stroom.processor.shared.ProcessorTaskList (stroom-core-shared/.../processor/shared/ProcessorTaskList.java), so ProcessorTasksList (free). Note wave 12/15's ProcessorTask would clash with stroom.processor.shared.ProcessorTask, which this unit imports.

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorTaskListPresenter.java:93-95 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Processor Tasks"/> |

**Stays in Java:** OpenLinkUtil click handler on the tooltip popup (:102): controller; columns, info column tooltip (TooltipPresenter popup, not declared); criteria, REST, HasDocumentRead: controller.

## `FindAnnotationListPresenter`

list, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.FindAnnotationList`
- **Controller:** `stroom.annotation.client.component.FindAnnotationListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/FindAnnotationList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/FindAnnotationListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FindAnnotationListPresenter.java:123-129 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="resultList" profile="LISTBOX" striped="true" accessibleName="Annotations found"/> (as FindResultLists.create sets it) |

**Stays in Java:** FindAnnotationCell column (a Cell; its DurationLabel comes from the controller); selection wiring through FindResultLists; REST paging and filter errors (controller).

## `EffectiveWordsList`

list, smart class; complexity 2, effort S; used by 2.

**Since the merge.** Mechanical rename only: DataGrid, with OrderByColumn and PagerView in stroom.widget.datagrid.client.

- **Component:** `stroom.dictionary.client.component.EffectiveWordsList`
- **Controller:** `stroom.dictionary.client.component.EffectiveWordsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dictionary/client/component/EffectiveWordsList.ui.xml`
- **Goes:** Nothing deleted: today's EffectiveWordsList (a Controller doing REST) splits; its REST, refresh and setDocRef move to EffectiveWordsListController and the class keeps only the dumb grid. Callers ImportsTab.java:56,76 and DictionaryEditor.java:75 inject the controller instead
- **Names:** EffectiveWordsList is in all_simple_names.json only as itself (stroom/dictionary/client/component/EffectiveWordsList.java), the class being split, so no clash; EffectiveWordsListController is free.

| Into the template | What | Detail |
| --- | --- | --- |
| EffectiveWordsList.java:72-80 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Words"/>; initWidget(pagerView.asWidget()) becomes initWidget(binder.createAndBindUi(this)) |
| EffectiveWordsList.java:75 | text | tableName="Words" (the default) in the template |

**Stays in Java:** dataGrid.setTableName("Dictionary '" + name + "' Words") at :89: built from data; key provider (:74), selection model, columns (addColumns); refresh button wiring (:79): component exposes setOnRefresh, controller handles it; WORD_LIST_RESOURCE REST fetch and sort state: controller.

## `DocRefListPresenter`

list, presenter; complexity 1, effort S; used by 2.

**Since the merge.** Import and type rename only: DataGridWidget is DataGrid and PagerView moved to stroom.widget.datagrid.client. Nothing material.

- **Component:** `stroom.dictionary.client.component.DocRefList`
- **Controller:** `stroom.dictionary.client.component.DocRefListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dictionary/client/presenter/DocRefListPresenter.java (no view interface or GIN binding)`
- **Names:** No clash. Controller is thin (fires OpenDocumentEvent); keep the pair per 'Always a pair'.

| Into the template | What | Detail |
| --- | --- | --- |
| DocRefListPresenter.java:47-50 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Document References"/> |
| DocRefListPresenter.java:106-108 setTableName | text | callers that pass a fixed name should set tableName where they declare DocRefList in their own template |

**Stays in Java:** initTableColumns(columnName, hasOpenLink) (:58-75): column heading supplied by the owner; CommandLink column; its click goes out through setOnOpen(Consumer<DocRef>) and the controller fires OpenDocumentEvent (:83); setData/selection (:88-96).

## `ProcessorTaskSummaryPresenter`

list, presenter; complexity 3, effort M; used by 2.

**Since the merge.** Mechanical rename only: DataGrid, with OrderByColumn and PagerView in stroom.widget.datagrid.client.

- **Component:** `stroom.data.client.component.ProcessorTaskSummaryList`
- **Controller:** `stroom.data.client.component.ProcessorTaskSummaryListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTaskSummaryList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/ProcessorTaskSummaryPresenter.java (no binding)`
- **Names:** ProcessorTaskSummary clashes with the shared DTO stroom.processor.shared.ProcessorTaskSummary, which this unit imports (:31) and lists; renamed ProcessorTaskSummaryList (free).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorTaskSummaryPresenter.java:82-90 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Processor Task Summary"/> |

**Stays in Java:** key provider (:84-87), selection, columns; criteria and REST: controller.

## `DuplicateManagementListPresenter`

list, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.analytics.client.component.DuplicateManagementList`
- **Controller:** `stroom.analytics.client.component.DuplicateManagementListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/DuplicateManagementList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/DuplicateManagementListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DuplicateManagementListPresenter.java:73-76 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Duplicate Management"/> |
| DuplicateManagementListPresenter.java:77 | aria | toolbar name="Duplicate check actions" |
| DuplicateManagementListPresenter.java:78-80 | component-declaration | <v:ToolbarButton ui:field="deleteButton" svg="DELETE" title="Delete Selected Duplicate Check Rows" enabled="false"/> |

**Stays in Java:** columns built from the data's column names; REST paging and delete (controller); delete button enabled state.

## `JobListPresenter`

list, presenter; complexity 3, effort M; used by 2.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.job.client.component.JobList`
- **Controller:** `stroom.job.client.component.JobListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/job/client/component/JobList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/job/client/presenter/JobListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| JobListPresenter.java:69-73 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Jobs" multiLine="true"/> |

**Stays in Java:** JobListPresenter.java:86-110 REST data provider using the view as task monitor (controller, Contexts.TASK_MONITOR); columns built with UiConfigCache help links.

## `ConfigPropertyClusterValuesListPresenter`

list, presenter; complexity 2, effort S; used by 2.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.config.global.client.component.ConfigPropertyClusterValuesList`
- **Controller:** `none`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/config/global/client/component/ConfigPropertyClusterValuesList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/config/global/client/presenter/ConfigPropertyClusterValuesListPresenter.java`; stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:116 bind(ConfigPropertyClusterValuesListPresenter.class); `dev-only makeDemoData (ConfigPropertyClusterValuesListPresenter.java:69-)`
- **Names:** No clash. No REST, bus or dialogs (ListDataProvider over values its parent gives): dumb, wired by ConfigPropertyClusterValuesPresenter.

| Into the template | What | Detail |
| --- | --- | --- |
| ConfigPropertyClusterValuesListPresenter.java:55-58 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid pageSize="1000" tableName="Cluster Property Values" multiLine="true"/> |

**Stays in Java:** ExpanderCell tree column and TreeRowHandler (63-66); rows built from the effective value to node map.

## `ManageGlobalPropertyListPresenter`

list, presenter; complexity 4, effort M; used by 2.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.config.global.client.component.ManageGlobalPropertyList`
- **Controller:** `stroom.config.global.client.component.ManageGlobalPropertyListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/config/global/client/component/ManageGlobalPropertyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/config/global/client/presenter/ManageGlobalPropertyListPresenter.java`; `stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:114 bind(ManageGlobalPropertyListPresenter.class)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ManageGlobalPropertyListPresenter.java:119-125 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Properties" multiLine="true"/> |

**Stays in Java:** ManageGlobalPropertyListPresenter.java:413 getToolbar(): GlobalPropertyTabPresenter adds its buttons until it converts; REST per node, name filter timer, filter errors (controller); columns.

## `ContentStoreContentPackListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved. The JSNI console() helper is still there to remove.

- **Component:** `stroom.contentstore.client.component.ContentStoreContentPackList`
- **Controller:** `stroom.contentstore.client.component.ContentStoreContentPackListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreContentPackList.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/contentstore/client/presenter/ContentStoreContentPackListPresenter.java (no view interface or GIN binding)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ContentStoreContentPackListPresenter.java:81-90 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Content Packs" multiLine="true"/> |

**Stays in Java:** key provider (:83) and single selection model (:93); columns (:211 on; icon via passthrough servlet, status from data); REST provider, first-row selection on load, the status cache and the back-reference to ContentStorePresenter (should become a setOnSelect callback to its owner rather than holding the parent): controller.

## `ResultStoreListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.query.client.component.ResultStoreList`
- **Controller:** `stroom.query.client.component.ResultStoreListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/query/client/component/ResultStoreList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/query/client/presenter/ResultStoreListPresenter.java (no view interface or binding)`
- **Names:** No clash. ResultStoreSettingsPresenter (owned at :68) is a dialog: makeDialog from the controller, not declared.

| Into the template | What | Detail |
| --- | --- | --- |
| ResultStoreListPresenter.java:72-77 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Result Stores"/> |
| ResultStoreListPresenter.java:79-87 | component-declaration | toolbar name="Result store actions" with terminate (STOP, "Terminate Search", enabled="false"), delete (DELETE, "Delete Selected Result Store", enabled="false"), settings (SETTINGS, "Edit Result Store Settings", enabled="false") |

**Stays in Java:** key provider, selection model, columns (:90-136); enabled states from selection (:151-160); ResultStoreModel fetch, terminate/delete/settings actions: controller.

## `NodeStatusListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.node.client.component.NodeStatusList`
- **Controller:** `stroom.node.client.component.NodeStatusListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeStatusList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NodeStatusListPresenter.java (no view interface or binding)`
- **Names:** No clash. TooltipPresenter (owned at :108) is a popup, shown not declared.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| NodeStatusListPresenter.java:439 | `Attribute.style("width:" + barWidthPct + "%")` | dynamic | Attribute.style("--node-ping-bar__width: " + pct + "%") on the bar div, and .nodePingBar-bar { width: var(--node-ping-bar__width); } in components.css:1384 |

| Into the template | What | Detail |
| --- | --- | --- |
| NodeStatusListPresenter.java:111-113 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Nodes"/> |
| NodeStatusListPresenter.java:121-123 | component-declaration | toolbar name="Node actions" with <b:ToolbarToggleButton ui:field="autoRefresh" svg="AUTO_REFRESH" title="Auto Refresh"/> |

**Stays in Java:** ping bar cell HTML (:400-460): text, title and severity classes from data; columns, enabled tick-box, info column tooltip; REST fetch, auto refresh, node enable: controller.

## `NodeGroupListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.node.client.component.NodeGroupList`
- **Controller:** `stroom.node.client.component.NodeGroupListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NodeGroupListPresenter.java (no view interface or binding)`
- **Names:** No clash (NodeGroup is the shared model).

| Into the template | What | Detail |
| --- | --- | --- |
| NodeGroupListPresenter.java:61-66 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="default-min-sizes"> containing <dg:DataGrid ui:field="dataGrid" tableName="Node Groups"/> |
| NodeGroupListPresenter.java:67 | css-class | default-min-sizes (components.css:1085) on the PagerView in the template |

**Stays in Java:** key provider and selection model (:63-65), name and enabled columns (:78-128); REST find/update and sort handler: controller.

## `NodeGroupStateListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.node.client.component.NodeGroupStateList`
- **Controller:** `stroom.node.client.component.NodeGroupStateListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupStateList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/node/client/presenter/NodeGroupStateListPresenter.java (no view interface or binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| NodeGroupStateListPresenter.java:63-65 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Node Group State"/> |

**Stays in Java:** columns with TickBoxCell (initTableColumns); node status fetch and group updates: controller.

## `CacheListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.cache.client.component.CacheList`
- **Controller:** `stroom.cache.client.component.CacheListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/cache/client/component/CacheList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/cache/client/presenter/CacheListPresenter.java (no view interface or binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| CacheListPresenter.java:80-85 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Caches"/> |

**Stays in Java:** key provider, selection model, columns incl. the ActionCell icon column "Clear and rebuild cache" (:88-123): cells are Java; its click goes out by setOnClear(Consumer<CacheIdentity>); REST clear and node fan-out fetch (:92-136): controller.

## `CacheNodeListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.cache.client.component.CacheNodeList`
- **Controller:** `stroom.cache.client.component.CacheNodeListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/cache/client/component/CacheNodeList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/cache/client/presenter/CacheNodeListPresenter.java (no view interface or binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| CacheNodeListPresenter.java:99-103 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Cache Nodes"/> |

**Stays in Java:** key provider, data-driven columns (per-cache statistics keys) and ActionCell column; REST per node and delayed update: controller.

## `ApiKeysListPresenter`

list, presenter; complexity 4, effort L; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.security.client.component.ApiKeysList`
- **Controller:** `stroom.security.client.component.ApiKeysListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeysList.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/client/presenter/ApiKeysListPresenter.java (no own view or binding; uses the shared QuickFilterPageView and PagerView bindings); `implements QuickFilterUiHandlers (:68): replaced by QuickFilterPage.setOnFilterChange`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ApiKeysListPresenter.java:105-106,136-137 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter API keys"> wrapping the list (unit 0) |
| ApiKeysListPresenter.java:112-120 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="API Keys"/> |
| ApiKeysListPresenter.java:86,141-181 | structure | commented-out toolbar code: delete (the owner ApiKeysPresenter adds its buttons through getToolbar()) |

**Stays in Java:** getToolbar() (:564-566): the owner (ApiKeysPresenter:78-83) adds its buttons, per 'a set of actions on another part's toolbar'; popup tooltip "API Keys Quick Filter" with field definitions and help URL (:129-133): from UiConfig; owner restriction from permissions, filter error, REST: controller; key provider and selection (:115-116).

## `AppUserPermissionsListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical renames: DataGrid, DataGridSelectionEventManager, PagerView package. No longer in flight.

- **Component:** `stroom.security.client.component.AppUserPermissionsList`
- **Controller:** `stroom.security.client.component.AppUserPermissionsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/AppUserPermissionsList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/AppUserPermissionsListPresenter.java (no own view or binding)`; `implements QuickFilterUiHandlers (:70)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AppUserPermissionsListPresenter.java:94-95,123-124 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter users"> |
| AppUserPermissionsListPresenter.java:101-110,217 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="User Permissions" multiLine="true"/> |

**Stays in Java:** getToolbar() (:344-346) for the owner's buttons; quick-filter tooltip from UiConfig (:114-121), context handler, filter error; selection manager, columns incl. CommandLink and ActionMenuCell; REST: controller.

## `DocumentUserPermissionsListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.security.client.component.DocumentUserPermissionsList`
- **Controller:** `stroom.security.client.component.DocumentUserPermissionsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/DocumentUserPermissionsListPresenter.java (no own view or binding)`; `implements QuickFilterUiHandlers (:77)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentUserPermissionsListPresenter.java:101-102,127-128 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter users"> |
| DocumentUserPermissionsListPresenter.java:108-112 | component-declaration | inside it <dg:PagerPanel ui:field="pager" addStyleNames="documentUserPermissions-table"> containing <dg:DataGrid ui:field="dataGrid" tableName="Document User Permissions" multiLine="true"/> |
| DocumentUserPermissionsListPresenter.java:114 | css-class | documentUserPermissions-table (components.css:1859) moves to the PagerView in the template |

**Stays in Java:** getToolbar() (:359-361) for DocumentUserPermissionsPresenter's buttons; columns built from DocumentTypeCache (:106), quick filter tooltip and context; REST and filter error: controller.

## `SigningKeyListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.security.client.component.SigningKeyList`
- **Controller:** `stroom.security.client.component.SigningKeyListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/SigningKeyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/SigningKeyListPresenter.java (no view interface or binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| SigningKeyListPresenter.java:66-71 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Signing Keys"/> |

**Stays in Java:** getToolbar() (:83-85) for the owner's buttons; key provider, selection, columns; REST: controller.

## `UserAccessListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.security.client.component.UserAccessList`
- **Controller:** `stroom.security.client.component.UserAccessListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/UserAccessList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserAccessListPresenter.java (no own view or binding)`; `implements QuickFilterUiHandlers (:56)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| UserAccessListPresenter.java:75-76,87-88 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter users"> |
| UserAccessListPresenter.java:80-85 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="User Access"/> |

**Stays in Java:** getToolbar() (:208-210); key provider, selection, columns; setQuickFilterText (:214) and REST: controller.

## `UserSessionsListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.security.client.component.UserSessionsList`
- **Controller:** `stroom.security.client.component.UserSessionsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/UserSessionsList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserSessionsListPresenter.java (no view interface or binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| UserSessionsListPresenter.java:75-80 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Sessions"/> |
| UserSessionsListPresenter.java:82-84 | component-declaration | toolbar name="Session actions" with <b:ToolbarButton ui:field="endSession" svg="DELETE" title="End Selected Session" enabled="false"/> |

**Stays in Java:** endSession enabled from selection (:92); key provider, selection, columns; REST end session and list: controller.

## `UserDependenciesListPresenter`

tab, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.security.client.component.UserDependenciesList`
- **Controller:** `stroom.security.client.component.UserDependenciesListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/security/client/component/UserDependenciesList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserDependenciesListPresenter.java (no own view or binding)`; `implements QuickFilterUiHandlers (:54)`
- **Names:** No clash. DocRefCell is a cell (in a Java column), not template-declared.

| Into the template | What | Detail |
| --- | --- | --- |
| UserDependenciesListPresenter.java:75-76,98-99 | component-declaration | <v:QuickFilterPage ui:field="page" accessibleName="Filter dependencies"> |
| UserDependenciesListPresenter.java:80-83 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="User Dependencies"/> |

**Stays in Java:** quick filter tooltip from UiConfig (:87-96) and context handler (:100-101); DocRefCell columns; REST, filter error: controller.

## `TaskManagerListPresenter`

list, presenter; complexity 4, effort L; used by 1.

- **Component:** `stroom.task.client.component.TaskManagerList`
- **Controller:** `stroom.task.client.component.TaskManagerListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/task/client/component/TaskManagerList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/task/client/presenter/TaskManagerListPresenter.java (no view interface or binding)`
- **Names:** TaskManagerList is free; note wave 15's TaskManagerPresenter -> TaskManager would clash with stroom.task.api.TaskManager, not this one.

| Into the template | What | Detail |
| --- | --- | --- |
| TaskManagerListPresenter.java:162-164 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Server Tasks" pageSize="1000"/> (DataGrid.setPageSize, DataGrid.java:1238) |
| TaskManagerListPresenter.java:166-185 | component-declaration | toolbar name="Task actions" with ToolbarToggleButton autoRefresh (AUTO_REFRESH, "Auto Refresh"), ToolbarButton terminate (DELETE, "Terminate Selected Tasks"), expandAll (EXPAND_ALL, "Expand All Tasks", enabled="false"), collapseAll (COLLAPSE_ALL, "Collapse All Tasks", enabled="false"), ToolbarToggleButton wrap (TEXT_WRAP, "Wrap Text"), warnings (ALERT, "Show Task Warnings", visible="false") |

**Stays in Java:** setMultiLine(wrap) (:225): changes with the toggle; warnings visible and expand/collapse enabled (:249-254): state; tree expander and tick-box columns, info tooltip; name filter timer, REST, terminate: controller.

## `RuleSetListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight; still a good first list component.

- **Component:** `stroom.receive.rules.client.component.RuleSetList`
- **Controller:** `stroom.receive.rules.client.component.RuleSetListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/receive/rules/client/presenter/RuleSetListPresenter.java (no binding)`
- **Names:** No clash; sits with the pilot's RuleEditor and RuleSetSettings. Its controller is thin (it holds no REST); keep the pair.

| Into the template | What | Detail |
| --- | --- | --- |
| RuleSetListPresenter.java:48-53 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Receive Rules"/> |
| RuleSetListPresenter.java:150-158 | structure | commented-out <span title> HTML: delete |

**Stays in Java:** getToolbar() (:140-142) for the rule-set's action buttons; key provider, selection, columns.

## `DataRetentionPolicyListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical renames: DataGrid, and PagerView and HeadingBuilder now from stroom.widget.datagrid.client. No longer in flight.

- **Component:** `stroom.receive.rules.client.component.DataRetentionPolicyList`
- **Controller:** `stroom.receive.rules.client.component.DataRetentionPolicyListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionPolicyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/receive/rules/client/presenter/DataRetentionPolicyListPresenter.java (no binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DataRetentionPolicyListPresenter.java:69-74 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Data Retention Rules"/> |

**Stays in Java:** getToolbar() (:262-264) for the owner's buttons; key provider, selection, tick-box and ActionCell columns; their clicks out through callbacks.

## `ContentTemplateListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). Its action menu is still a ShowMenuEvent, which survives the menu rename. No longer in flight.

- **Component:** `stroom.receive.content.client.component.ContentTemplateList`
- **Controller:** `stroom.receive.content.client.component.ContentTemplateListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/receive/content/client/presenter/ContentTemplateListPresenter.java (no binding)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ContentTemplateListPresenter.java:66-71 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Content Templates"/> |

**Stays in Java:** getToolbar() (:338-340) for the owner's buttons; key provider, selection, columns incl. ActionCell.

## `DependenciesPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.importexport.client.component.DependencyList`
- **Controller:** `stroom.importexport.client.component.DependencyListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/importexport/client/component/DependencyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/importexport/client/presenter/DependenciesPresenter.java (no binding)`
- **Names:** Suggested 'Dependencies' is free but vague beside the other ...List units; DependencyList (free) says it is the dependencies grid.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| DependenciesPresenter.java:365-371 | `<span style="color:var(--text-color--green);font-weight:bold"> / --text-color--red` | static | new .dependency-status { font-weight: bold; } with .dependency-status--ok { color: var(--text-color--green); } and .dependency-status--missing { color: var(--text-color--red); } in components.css. (redGreenCell__green/__red in RedGreenTextCell.css give the colours but also recolour on selected rows, which would change the screenshots.) |

| Into the template | What | Detail |
| --- | --- | --- |
| DependenciesPresenter.java:96-102 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Dependencies" pageSize="100"/> |

**Stays in Java:** status value OK/Missing per row (:362-374): data, choosing the modifier class; key provider (:98-100), doc type icons, CommandLink and ActionCell columns; REST, criteria, filter error consumer: controller.

## `RuleListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved.

- **Component:** `stroom.dashboard.client.component.RuleList`
- **Controller:** `none`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/RuleList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/RuleListPresenter.java`
- **Names:** No clash; matches Rule (wave 11) and Rules/MultiRules (wave 13) in the same package. No REST, bus or dialogs, so a dumb component with no controller (the Rules controller wires setOnChange and the selection).

| Into the template | What | Detail |
| --- | --- | --- |
| RuleListPresenter.java:54-59 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Conditional Formatting Rules"/> |

**Stays in Java:** RuleListPresenter.java:56 key provider, columns and swatch cells; RuleListPresenter.java:173 getToolbar(): Rules adds its buttons to the list's toolbar until Rules (wave 13) declares them; rows and enabled state.

## `AiChatHistoryResultListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.ai.client.component.AiChatHistoryResultList`
- **Controller:** `stroom.ai.client.component.AiChatHistoryResultListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/ai/client/component/AiChatHistoryResultList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/AiChatHistoryResultListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AiChatHistoryResultListPresenter.java:113-119 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="resultList" profile="LISTBOX" striped="true" accessibleName="Chats"/> (what FindResultLists.create sets at FindResultLists.java:58-60); FindResultLists gains a configure(grid, ...) for the selection wiring |

**Stays in Java:** AiChatCell column (a Cell); FindResultLists selection wiring (open on Enter, Up from first); AiChatHistoryResultListPresenter.java:72-110 REST paging through AskStroomAiClient and filter errors (controller).

## `AccountsListPresenter`

list, presenter; complexity 3, effort M; used by 1.

- **Component:** `stroom.security.identity.client.component.AccountsList`
- **Controller:** `stroom.security.identity.client.component.AccountsListController`
- **Template:** `none (QuickFilterPageView plus injected PagerView)` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/AccountsList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/AccountsListPresenter.java`; `QuickFilterUiHandlers implementation (AccountsListPresenter.java:64,133)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AccountsListPresenter.java:91,132 | component-declaration | root <qf:QuickFilterPage ui:field="page" accessibleName="Filter accounts"> (wave 10 entry 0, the shared FilteredList frame) |
| AccountsListPresenter.java:96-104 | component-declaration | inside it <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" pageSize="1000" tableName="Accounts"/> |
| AccountsListPresenter.java:107 | aria | toolbar name="Account actions" |
| AccountsListPresenter.java:108-110 | component-declaration | <v:ToolbarButton> ADD "New Account", EDIT "Edit Selected Account", DELETE "Delete Selected Account" |

**Stays in Java:** AccountsListPresenter.java:123-130 quick filter help text built from field definitions and UiConfig (controller); AccountsListPresenter.java:134 quick filter context handler; button states, CommandLinkCell columns, REST paging, EditAccount dialog via makeDialog.

## `AnalyticDataShardListPresenter`

list, presenter; complexity 3, effort M; used by 1.

- **Component:** `stroom.analytics.client.component.AnalyticDataShardList`
- **Controller:** `stroom.analytics.client.component.AnalyticDataShardListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShardList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticDataShardListPresenter.java`; `commented-out buttons and old data provider (AnalyticDataShardListPresenter.java:96-98,103-120)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticDataShardListPresenter.java:89-94 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Analytic Data Shards"/> |

**Stays in Java:** REST paging (122-) and task monitor (194,203) in the controller; columns.

## `ScheduledProcessHistoryListPresenter`

list, presenter; complexity 2, effort M; used by 1.

- **Component:** `stroom.analytics.client.component.ScheduledProcessHistoryList`
- **Controller:** `stroom.analytics.client.component.ScheduledProcessHistoryListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessHistoryList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ScheduledProcessHistoryListPresenter.java`; `back-reference setScheduledProcessingPresenter (ScheduledProcessHistoryListPresenter.java:65,213), replaced by a callback its owner sets`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduledProcessHistoryListPresenter.java:78-88 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Scheduled Process History" multiLine="true"/> |
| ScheduledProcessHistoryListPresenter.java:90 | aria | toolbar name="History actions" |
| ScheduledProcessHistoryListPresenter.java:91 | component-declaration | <v:ToolbarButton ui:field="replayButton" svg="RERUN" title="Replay Selected Execution"/> |

**Stays in Java:** REST paging (170-) and replay (controller); replay enabled state, columns.

## `ScheduledProcessListPresenter`

list, presenter; complexity 3, effort M; used by 1.

- **Component:** `stroom.analytics.client.component.ScheduledProcessList`
- **Controller:** `stroom.analytics.client.component.ScheduledProcessListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ScheduledProcessListPresenter.java`; `back-reference setScheduledProcessingPresenter (ScheduledProcessListPresenter.java:83,380), replaced by a callback`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduledProcessListPresenter.java:102-111 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Scheduled Processes"/> |
| ScheduledProcessListPresenter.java:113 | aria | toolbar name="Schedule actions" |
| ScheduledProcessListPresenter.java:114-121 | component-declaration | <v:ToolbarButton> ADD "New Execution Schedule", EDIT "Edit Selected Execution Schedule" enabled="false", DELETE "Delete Selected Execution Schedule" enabled="false" |

**Stays in Java:** ScheduledProcessListPresenter.java:302 enabled states; REST paging (334-) and edit dialog via makeDialog (controller); columns.

## `SelectionHandlerListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved.

- **Component:** `stroom.dashboard.client.component.SelectionHandlerList`
- **Controller:** `none`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/SelectionHandlerListPresenter.java`; `unused setComponents/components field (SelectionHandlerListPresenter.java:43,102-104) if nothing reads it`
- **Names:** No clash. No REST, bus or dialogs: a dumb component; SelectionHandlersPresenter wires it.

| Into the template | What | Detail |
| --- | --- | --- |
| SelectionHandlerListPresenter.java:50-55 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Selection Handlers"/> |

**Stays in Java:** SelectionHandlerListPresenter.java:99 getToolbar(): the parent adds its buttons until SelectionHandlersPresenter converts; key provider, columns, rows.

## `ColumnSelectionPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only: DataGrid, and OrderByColumn and PagerView in stroom.widget.datagrid.client. On re-check it also uses TickBoxCell for its tick column, which the first record missed. That is not a change from the merge.

- **Component:** `stroom.dashboard.client.component.ColumnSelection`
- **Controller:** `none`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnSelection.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/input/ColumnSelectionPresenter.java`; `the RestDataProvider over local data (ColumnSelectionPresenter.java:156-170), replaced by setRowData`; HasValueChangeHandlers/HandlerManager (54,60,129,138,174), replaced by setOnChange(Consumer<Set<ColumnRef>>) (one caller, BasicTableFilterSettingsPresenter)
- **Names:** No clash. Its RestDataProvider does no REST (it pages the in-memory column set), so it is dumb and needs no controller.

| Into the template | What | Detail |
| --- | --- | --- |
| ColumnSelectionPresenter.java:67-69 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Column Selection"/> |

**Stays in Java:** tick-box columns and select-all header state; sorting of the rows.

## `FsVolumeGroupListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved.

- **Component:** `stroom.data.store.impl.fs.client.component.FsVolumeGroupList`
- **Controller:** `stroom.data.store.impl.fs.client.component.FsVolumeGroupListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeGroupList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/FsVolumeGroupListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FsVolumeGroupListPresenter.java:59-64 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="FS Volume Groups"/> |
| FsVolumeGroupListPresenter.java:65 | css-class | addStyleNames="default-min-sizes" (components.css:1085) |

**Stays in Java:** REST paging (controller); columns.

## `FsVolumeStatusListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved.

- **Component:** `stroom.data.store.impl.fs.client.component.FsVolumeStatusList`
- **Controller:** `stroom.data.store.impl.fs.client.component.FsVolumeStatusListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeStatusList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/FsVolumeStatusListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FsVolumeStatusListPresenter.java:74-79 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="FS Volumes"/> |
| FsVolumeStatusListPresenter.java:82 | css-class | addStyleNames="default-min-sizes" (components.css:1085) |

**Stays in Java:** REST paging and DateTimeFormatter use (controller pushes the formatter or formatted values); columns.

## `AnnotationTagListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.annotation.client.component.AnnotationTagList`
- **Controller:** `stroom.annotation.client.component.AnnotationTagListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTagList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationTagListPresenter.java:61-64 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Annotation Tags"/> |
| AnnotationTagListPresenter.java:65 | css-class | addStyleNames="default-min-sizes" (components.css:1085) |

**Stays in Java:** AnnotationTagListPresenter.java:117,131 column heading and tooltip from AnnotationTagType (data); AnnotationTagListPresenter.java:125 annotationTag.getStyle() is the tag's colour swatch data, not an inline style; REST through AnnotationResourceClient (controller).

## `XPathListPresenter`

list, presenter; complexity 1, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView from stroom.widget.datagrid.client).

- **Component:** `stroom.pipeline.stepping.client.component.XPathList`
- **Controller:** `none`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/XPathList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/XPathListPresenter.java`
- **Names:** No clash. No REST, bus or dialogs: dumb, wired by SteppingFilterPresenter.

| Into the template | What | Detail |
| --- | --- | --- |
| XPathListPresenter.java:50-53 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="XPath Matches"/> |

**Stays in Java:** XPathListPresenter.java:64 getToolbar(): SteppingFilterPresenter adds its buttons until it converts; columns including the read-only TickBoxCell column; ListDataProvider rows.

## `IndexShardPresenter`

list, presenter; complexity 4, effort L; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.index.client.component.IndexShardList`
- **Controller:** `stroom.index.client.component.IndexShardListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexShardList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexShardPresenter.java`
- **Names:** IndexShard clashes with the shared DTO stroom.index.shared.IndexShard (this class's row type) and the jOOQ table, so IndexShardList and IndexShardListController.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexShardPresenter.java:105-109 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Index Shards"/> |
| IndexShardPresenter.java:118 | aria | toolbar name="Shard actions" |
| IndexShardPresenter.java:119-122 | component-declaration | <v:ToolbarButton> SHARD_FLUSH "Flush Selected Index Shards" enabled="false", DELETE "Delete Selected Index Shards" enabled="false"; declared always, the controller hides them without MANAGE_INDEX_SHARDS (117) via setShardActionsVisible(boolean) |

**Stays in Java:** IndexShardPresenter.java:117 permission check (controller); IndexShardPresenter.java:132-149 enabled states and reason suffixes; IndexShardPresenter.java:432 table name with the index's name; TickBoxCell columns, Tooltip popup (wave 3) made by the controller, REST and NodeClient.

## `IndexVolumeGroupListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.index.client.component.IndexVolumeGroupList`
- **Controller:** `stroom.index.client.component.IndexVolumeGroupListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeGroupList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexVolumeGroupListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexVolumeGroupListPresenter.java:57-60 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Index Volume Groups"/> |
| IndexVolumeGroupListPresenter.java:61 | css-class | addStyleNames="default-min-sizes" (components.css:1085) |

**Stays in Java:** REST paging (controller); columns.

## `IndexVolumeStatusListPresenter`

list, presenter; complexity 2, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.index.client.component.IndexVolumeStatusList`
- **Controller:** `stroom.index.client.component.IndexVolumeStatusListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeStatusList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexVolumeStatusListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexVolumeStatusListPresenter.java:70-75 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Index Volumes"/> |

**Stays in Java:** REST paging and DateTimeFormatter (controller); columns.

## `ScriptListPresenter`

list, presenter; complexity 1, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.script.client.component.ScriptDependencyList (merged into unit 76)`
- **Controller:** `none`
- **Template:** `none` → `none (its grid is declared in ScriptDependencyList.ui.xml)`
- **Goes:** `stroom-core-client/src/main/java/stroom/script/client/presenter/ScriptListPresenter.java`
- **Names:** ScriptList is free, but ScriptListPresenter's only user is ScriptDependencyListPresenter, so it merges into ScriptDependencyList rather than becoming its own component.

| Into the template | What | Detail |
| --- | --- | --- |
| ScriptListPresenter.java:43-46 | component-declaration | DataGrid tableName="Script Dependencies" declared in ScriptDependencyList.ui.xml |

**Stays in Java:** ScriptListPresenter.java:53-62 the Name column, now in ScriptDependencyList; ScriptListPresenter.java:74 setTableName (data) used by the controller.

## `TracesListPresenter`

list, presenter; complexity 4, effort M; used by 1.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid and PagerView is imported from stroom.widget.datagrid.client. No behaviour change; no longer in flight.

- **Component:** `stroom.pathways.client.component.TracesList`
- **Controller:** `stroom.pathways.client.component.TracesListController`
- **Template:** `none (QuickFilterPageView plus a FlowPanel built in Java)` → `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/TracesListPresenter.java`; `SimpleView wrapper and QuickFilterUiHandlers implementation (TracesListPresenter.java:70,141-142)`
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| TracesListPresenter.java:229 | `"<div style=\"display:flex;align-items:center;gap:4px;\">" in the operation cell's…` | static | New class traces-list__operation in pathways.css (display: flex; align-items: center; gap: 4px); the cell writes <div class="traces-list__operation"> |

| Into the template | What | Detail |
| --- | --- | --- |
| TracesListPresenter.java:107 | component-declaration | root <qf:QuickFilterPage ui:field="page" accessibleName="Filter traces"> (wave 10 entry 0) |
| TracesListPresenter.java:132-141 | structure | <g:FlowPanel styleName="dock-container-vertical max"> as the page's data part, in place of the FlowPanel and addStyleName calls |
| TracesListPresenter.java:111,135 | component-declaration | <p:TraceHistogramChart ui:field="histogram" addStyleNames="dock-min"/> (wave 2; its DateTimeFormatter becomes a setter the controller calls) |
| TracesListPresenter.java:113-117,137-139 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="dock-max overflow-hidden"> holding <dg:DataGrid tableName="Traces"/> |

**Stays in Java:** TracesListPresenter.java:120 errorRowStyles: per-row classes from data; TracesListPresenter.java:122-129 quick filter help text from field definitions and UiConfig (controller); TracesListPresenter.java:230-244 status icon titles chosen per trace, and the "No root span found" placeholder name (data); TracesListPresenter.java:442 quick filter context per data source.

## `ActivityListPresenter`

list, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.activity.client.component.ActivityList`
- **Controller:** `stroom.activity.client.component.ActivityListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/activity/client/component/ActivityList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/activity/client/ActivityListPresenter.java`
- **Names:** No clash. Its package-private getSelectionModel/setCriteria/getCriteria (ActivityListPresenter.java:144-154) become public, as ManageActivityPresenter stays in stroom.activity.client.

| Into the template | What | Detail |
| --- | --- | --- |
| ActivityListPresenter.java:67-73 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Activities" multiLine="true"/> |

**Stays in Java:** ActivityListPresenter.java:134 getToolbar(): ManageActivityPresenter adds its buttons until it converts; activity details column (SafeHtml from data); REST paging and filter errors (controller).

## `IncludeExcludeFilterDictionaryPresenter`

composite, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.dashboard.client.component.IncludeExcludeFilterDictionary`
- **Controller:** `stroom.dashboard.client.component.IncludeExcludeFilterDictionaryController`
- **Template:** `none (WrapperView holding DocRefListPresenter's view)` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/IncludeExcludeFilterDictionary.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/IncludeExcludeFilterDictionaryPresenter.java`; `WrapperView use (IncludeExcludeFilterDictionaryPresenter.java:49,58)`
- **Names:** No clash. Depends on DocRefList (wave 10 entry 4, stroom.dictionary.client.component.DocRefList) converting first in this wave.

| Into the template | What | Detail |
| --- | --- | --- |
| IncludeExcludeFilterDictionaryPresenter.java:58 | component-declaration | <dc:DocRefList ui:field="list" nameHeading="Dictionary Name" refreshVisible="false"/> as the root (replaces WrapperView; nameHeading from initTableColumns at :68, refreshVisible from :67) |
| IncludeExcludeFilterDictionaryPresenter.java:60 | aria | the list's toolbar name="Dictionary actions" |
| IncludeExcludeFilterDictionaryPresenter.java:61 | component-declaration | <v:ToolbarButton ui:field="addButton" svg="ADD" title="Add Dictionary"/> |
| IncludeExcludeFilterDictionaryPresenter.java:63-65 | component-declaration | <v:ToolbarButton ui:field="removeButton" svg="DELETE" title="Remove Dictionary" enabled="false"/> |

**Stays in Java:** IncludeExcludeFilterDictionaryPresenter.java:77-80 DocSelectionPopup (wave 8) made with makeDialog by the controller, caption "Choose Dictionary" set there; a popup, not declared; IncludeExcludeFilterDictionaryPresenter.java:89-93 confirm message chosen by selection count; remove button enabled state (112).

## `ScriptDependencyListPresenter`

composite, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.script.client.component.ScriptDependencyList`
- **Controller:** `stroom.script.client.component.ScriptDependencyListController`
- **Template:** `none (WrapperView holding ScriptListPresenter's PagerView)` → `stroom-core-client/src/main/java/stroom/script/client/component/ScriptDependencyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/script/client/presenter/ScriptDependencyListPresenter.java`; `WrapperView use (ScriptDependencyListPresenter.java:51,65)`; `stroom-core-client/src/main/java/stroom/script/client/presenter/ScriptListPresenter.java (merged in, unit 77)`
- **Names:** No clash. Absorbs ScriptList (unit 77), whose only user it is.

| Into the template | What | Detail |
| --- | --- | --- |
| ScriptDependencyListPresenter.java:65 and ScriptListPresenter.java:43-46 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Script Dependencies"/> |
| ScriptDependencyListPresenter.java:67 | aria | toolbar name="Script actions" |
| ScriptDependencyListPresenter.java:68-69 | component-declaration | <v:ToolbarButton ui:field="addButton" svg="ADD" title="Add Script Dependency"/> |
| ScriptDependencyListPresenter.java:70-73 | component-declaration | <v:ToolbarButton ui:field="removeButton" svg="REMOVE" title="Remove Selected Script Dependency" enabled="false"/> |

**Stays in Java:** ScriptDependencyListPresenter.java:122 table name with the script's name; ScriptDependencyListPresenter.java:151-158 enabled states and " (script is read only)" suffix; DocSelectionPopup (wave 8, owned at :58) made by the controller, a popup not declared; confirm messages by count, read/write of ScriptDoc (controller).

## `IncludeExcludeFilterPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.IncludeExcludeFilterEditor`
- **Controller:** `stroom.dashboard.client.component.IncludeExcludeFilterEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/IncludeExcludeFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/IncludeExcludeFilterEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/IncludeExcludeFilterViewImpl.java`; `IncludeExcludeFilterView interface (IncludeExcludeFilterPresenter.java:90-103)`; stroom-core-client/src/main/java/stroom/dashboard/client/table/gin/TableModule.java:77-79 bindPresenterWidget(IncludeExcludeFilterPresenter, ...View, ...ViewImpl)
- **Names:** IncludeExcludeFilter clashes with the shared DTO stroom.query.api.IncludeExcludeFilter, which this class reads and returns (setFilter/getFilter), so the component is IncludeExcludeFilterEditor and the controller IncludeExcludeFilterEditorController.

| Into the template | What | Detail |
| --- | --- | --- |
| IncludeExcludeFilterViewImpl.ui.xml:17 and IncludeExcludeFilterPresenter.java:46 | component-declaration | replace <g:SimplePanel ui:field="includeDictionaryPanel"> with <c:IncludeExcludeFilterDictionary ui:field="includeDictionaries" addStyleNames="max stroom-control"/> (unit 53); the controller attaches an IncludeExcludeFilterDictionaryController to it |
| IncludeExcludeFilterViewImpl.ui.xml:30 and IncludeExcludeFilterPresenter.java:47 | component-declaration | <c:IncludeExcludeFilterDictionary ui:field="excludeDictionaries" addStyleNames="max stroom-control"/> |
| IncludeExcludeFilterViewImpl.ui.xml:14 | css-class | tidy addStyleNames=" default-min-sizes dock-max" (leading space) |

**Stays in Java:** IncludeExcludeFilterViewImpl.java:74,84 include/exclude text set from the filter; IncludeExcludeFilterPresenter.java:49-89 building the IncludeExcludeFilter DTO (controller); FormGroup and SplitPane already declared.

## `ProfilePeriodListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.processor.client.component.ProfilePeriodList`
- **Controller:** `stroom.processor.client.component.ProfilePeriodListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProfilePeriodList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProfilePeriodListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ProfilePeriodListPresenter.java:64 | css-class | addStyleNames="form-control-background form-control-border" (components.css) on the pager frame |
| ProfilePeriodListPresenter.java:65-71 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Profile Periods" multiLine="true"/> |
| ProfilePeriodListPresenter.java:76 | aria | toolbar name="Profile period actions" |
| ProfilePeriodListPresenter.java:75 | component-declaration | <v:ToolbarButton ui:field="addButton" svg="ADD" title="New Profile Period"/> (the Button in Declares is this ToolbarButton) |
| ProfilePeriodListPresenter.java:77-78 | component-declaration | <v:ToolbarButton ui:field="editButton" svg="EDIT" title="Edit Selected Profile Period" enabled="false"/> |
| ProfilePeriodListPresenter.java:79-80 | component-declaration | <v:ToolbarButton ui:field="removeButton" svg="REMOVE" title="Remove Selected Profile Period" enabled="false"/> |

**Stays in Java:** ProfilePeriodListPresenter.java:223-235 enabled states and read-only title suffix; ProfilePeriodListPresenter.java:190 ProfilePeriodEdit (wave 9) opened by the controller with makeDialog, not declared.

## `PropertyListPresenter`

list, presenter; complexity 4, effort L; used by 4.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.pipeline.structure.client.component.PropertyList`
- **Controller:** `stroom.pipeline.structure.client.component.PropertyListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PropertyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/PropertyListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| PropertyListPresenter.java:104-110 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Pipeline Properties" multiLine="true"/> |
| PropertyListPresenter.java:116 | aria | toolbar name="Property actions" |
| PropertyListPresenter.java:117-119 | component-declaration | <v:ToolbarButton ui:field="editButton" svg="EDIT" title="Edit Selected Property" enabled="false"/> |

**Stays in Java:** PropertyListPresenter.java:300 setTableName from the caller (data); PropertyListPresenter.java:552-560 enabled state and " (read only)" title; PropertyListPresenter.java:455 caption "Edit Property": the controller opens NewProperty (wave 9) with makeDialog; REST and DocumentPluginRegistry (controller).

## `SolrIndexFieldListPresenter`

tab, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.search.solr.client.component.SolrIndexFieldList`
- **Controller:** `stroom.search.solr.client.component.SolrIndexFieldListController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/solr/client/view/SolrIndexFieldListViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexFieldList.ui.xml`
- **Goes:** `SolrIndexFieldListPresenter.java; inner SolrIndexFieldListView (:369-374)`; `stroom-core-client/src/main/java/stroom/search/solr/client/view/SolrIndexFieldListViewImpl.java`; `SolrIndexModule.java:41-43`; `stroom-gwt-workbench .../gin/editors/SolrIndexScreenModule.java:45-47`; `SolrIndexFieldDataProvider.java (identical to ListDataProvider in stroom.config.global.client.presenter; use that)`
- **Names:** No clash. SolrIndexFieldEditPresenter (owned at :78, shown with show(...) at :209) is a dialog: makeDialog, not declared.

| Into the template | What | Detail |
| --- | --- | --- |
| SolrIndexFieldListPresenter.java:85-92 and SolrIndexFieldListViewImpl.java:51-53 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="dock-max"> containing <dg:DataGrid ui:field="dataGrid" tableName="Solr Index Fields"/> in place of the SimplePanel slot (template :5) |
| SolrIndexFieldListPresenter.java:93-100 | component-declaration | toolbar name="Field actions" with new (ADD, "New Index Field"), edit (EDIT, "Edit Selected Index Field", enabled="false"), remove (REMOVE, "Remove Selected Index Fields", enabled="false") |
| SolrIndexFieldListPresenter.java:325-327 | text | the fixed "Last synchronised:" label can be a template label shown when there is a time, beside a value span |
| SolrIndexFieldListViewImpl.ui.xml:4-7 | css-class | keeps dock-container-vertical, dock-min, solrIndexFieldListViewImpl-synchState (components.css:916) |

**Stays in Java:** button titles with " (index is read only)" (:144-150): change with read-only (should come from watch(READ_ONLY) in the controller); setTableName("Solr Index '" + name + "' Fields") (:316); sync messages list (:328-331): data; render as text rows, not HTML string with </br> via fromTrustedString (SolrIndexFieldListViewImpl.java:57); columns, enabled states, onWrite deleted-fields logic: controller.

## `JobNodeListHelper`

list, smart class; complexity 3, effort M; used by 2.

- **Component:** `stroom.node.client.component.JobNodeColumns`
- **Controller:** `stroom.node.client.component.JobNodeActionsController`
- **Template:** `none` → `none (built in Java because it is not a visual component: it adds shared columns to its owner's DataGrid and performs job-node actions)`
- **Goes:** stroom-core-client/src/main/java/stroom/node/client/JobNodeListHelper.java (split into the two; callers NodeJobListPresenter.java:95 and JobNodeListPresenter.java:107 (unit 75) move in the same commit)
- **Names:** The suggested JobNodeListHelper + JobNodeListHelperController keeps a 'Helper' in a component package and suggests a widget it does not have. Split by concern: JobNodeColumns (dumb: the enabled tick-box, type, node state, schedule, last/next executed and action columns for a DataGrid<JobNodeAndInfo>, with callbacks out) and JobNodeActionsController (a Controller made with make() by NodeJobListController and JobNodeListController: REST executeJobNow/setEnabled/batch schedule, SchedulePopup via makeDialog, the action menu). Both names are free in all_simple_names.json and in this wave; JobNodeList/JobNodeListController stay with unit 75.

| Into the template | What | Detail |
| --- | --- | --- |
| JobNodeListHelper.java:343-349 addJobFilterButton | component-declaration | <b:ToolbarToggleButton ui:field="showEnabled" svg="FILTER" title="Show Only Enabled Jobs"/> declared in the NodeJobList (unit 11) and JobNodeList (unit 75) templates; the helper no longer builds it |

**Stays in Java:** column builders (:412-520): cells (TickBoxCell, CommandLinkCell, ActionCell) are Java; text built from data: getCurrentTaskCountAsStr, getLastExecutedTimeAsStr, getNextScheduledTimeAsStr, buildJobTypeStr (:258-340); REST, schedule popup and action menu (:122-250, :370-410, :524-): JobNodeActionsController.

## `FieldListPresenter`

tab, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.receive.rules.client.component.FieldList`
- **Controller:** `stroom.receive.rules.client.component.FieldListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/FieldList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/receive/rules/client/presenter/FieldListPresenter.java (a DocPresenter tab; no binding)`
- **Names:** FieldList is free in all_simple_names.json; it sits beside FieldEditor in the same package, which it opens as a dialog (FieldEditorController, not declared).

| Into the template | What | Detail |
| --- | --- | --- |
| FieldListPresenter.java:73-76 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Fields"/> |
| FieldListPresenter.java:80-85 | component-declaration | toolbar name="Field actions" with new (ADD, "New Field"), edit (EDIT, "Edit Selected Field", enabled="false"), remove (DELETE, "Delete Selected Fields", enabled="false") |
| FieldListPresenter.java:86-89,155-165 | structure | commented-out up/down buttons: delete |

**Stays in Java:** enabled states from read-only and selection (:146-163); read-only from watch(Contexts.READ_ONLY); obfuscated fields from UiConfig (:94-97), comparators, columns; document read/write: controller.

## `FeedDependencyListPresenter`

list, presenter; complexity 2, effort S; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.processor.client.component.FeedDependencyList`
- **Controller:** `stroom.processor.client.component.FeedDependencyListController`
- **Template:** `none (built in Java on the injected PagerView, whose template is PagerViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/processor/client/component/FeedDependencyList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/processor/client/presenter/FeedDependencyListPresenter.java`; `the injected PagerView (no binding of its own; no view interface)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FeedDependencyListPresenter.java:67 | css-class | addStyleNames="form-control-background form-control-border" (components.css:1227,1235) on the declared pager frame |
| FeedDependencyListPresenter.java:68-72 | component-declaration | <dg:PagerPanel ui:field="pager"> (wave 5 frame, as a no-arg widget) holding <dg:DataGrid ui:field="dataGrid" tableName="Feed Dependencies" multiLine="true"/> |
| FeedDependencyListPresenter.java:77 | aria | the pager's toolbar name="Feed dependency actions" |
| FeedDependencyListPresenter.java:76 | component-declaration | <v:ToolbarButton ui:field="addButton" svg="ADD" title="New Feed Dependency"/> |
| FeedDependencyListPresenter.java:78-79 | component-declaration | <v:ToolbarButton ui:field="editButton" svg="EDIT" title="Edit Selected Feed Dependency" enabled="false"/> |
| FeedDependencyListPresenter.java:80-81 | component-declaration | <v:ToolbarButton ui:field="removeButton" svg="REMOVE" title="Remove Selected Feed Dependency" enabled="false"/> |

**Stays in Java:** FeedDependencyListPresenter.java:243-256 enabled states and the " (read only)" suffix appended to the template titles; FeedDependencyListPresenter.java:213 dialog caption New/Edit: the controller opens EditFeedDependency (wave 9) with makeDialog, not declared; grid columns (cells built in Java).

## `JobNodeListPresenter`

list, presenter; complexity 4, effort L; used by 1.

**Since the merge.** Renames only (DataGrid; PagerViewWithHeading in stroom.widget.datagrid.client). Nothing material.

- **Component:** `stroom.job.client.component.JobNodeList`
- **Controller:** `stroom.job.client.component.JobNodeListController`
- **Template:** `none (built in Java on the injected PagerViewWithHeading)` → `stroom-core-client/src/main/java/stroom/job/client/component/JobNodeList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/job/client/presenter/JobNodeListPresenter.java`; JobNodeListHelper.addJobFilterButton (stroom-core-client/src/main/java/stroom/node/client/JobNodeListHelper.java:343-349) once the toggle is declared here (NodeJobList, wave 10 entry 11, uses it too)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| JobNodeListPresenter.java:90,100-104 | component-declaration | <dg:PagerViewWithHeading ui:field="pager"> (wave 5; or PagerView with its heading if they merge) holding <dg:DataGrid tableName="Job Nodes"/> |
| JobNodeListPresenter.java:116 | aria | toolbar name="Job node actions" |
| JobNodeListPresenter.java:117 via JobNodeListHelper.java:345-346 | component-declaration | <v:ToolbarToggleButton ui:field="showEnabled" svg="FILTER" title="Show Only Enabled Jobs"/> |
| JobNodeListPresenter.java:120-122 | component-declaration | <v:ToolbarToggleButton ui:field="autoRefresh" svg="AUTO_REFRESH" title="Auto Refresh"/> |

**Stays in Java:** JobNodeListPresenter.java:378-388 heading built from the job name and filter state; toggle states, NodeChangeEvent listen (128-), redraw timer, REST; SchedulePickerController (wave 4) opened by the controller, not declared; CommandLinkCell columns.

## `PipelineReferenceListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight; the Add/Edit/Remove buttons still suit an action enum.

- **Component:** `stroom.pipeline.structure.client.component.PipelineReferenceList`
- **Controller:** `stroom.pipeline.structure.client.component.PipelineReferenceListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineReferenceList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/PipelineReferenceListPresenter.java`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| PipelineReferenceListPresenter.java:106-110 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Pipeline References" multiLine="true"/> |
| PipelineReferenceListPresenter.java:117 | aria | toolbar name="Reference actions" |
| PipelineReferenceListPresenter.java:116-121 | component-declaration | <v:ToolbarButton> ADD "New Pipeline Reference", EDIT "Edit Selected Pipeline Reference" enabled="false", REMOVE "Remove Selected Pipeline Reference" enabled="false" |

**Stays in Java:** PipelineReferenceListPresenter.java:262 setTableName from the caller; PipelineReferenceListPresenter.java:511-530 enabled states, Remove/Restore title by row state, read-only suffix; PipelineReferenceListPresenter.java:368 dialog caption: NewPipelineReference (wave 9) opened with makeDialog; REST and element types (controller).

## `ConstraintListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.pathways.client.component.ConstraintList`
- **Controller:** `stroom.pathways.client.component.ConstraintListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/pathways/client/component/ConstraintList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/ConstraintListPresenter.java`; `commented-out code (ConstraintListPresenter.java:74,79-81)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ConstraintListPresenter.java:85-90 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Constraints"/> |
| ConstraintListPresenter.java:94 | aria | toolbar name="Constraint actions" |
| ConstraintListPresenter.java:95-99 | component-declaration | <v:ToolbarButton> ADD "New Constraint", EDIT "Edit Selected Constraint" enabled="false", DELETE "Delete Selected Constraints" enabled="false" |

**Stays in Java:** ConstraintListPresenter.java:141-157 enabled states and " (read only)" suffix; ConstraintEdit (wave 9, owned at :77) opened by the controller with makeDialog, not declared; TickBoxCell columns.

## `AbstractNotificationListPresenter`

list, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.NotificationList`
- **Controller:** `stroom.analytics.client.component.NotificationListController`
- **Template:** `none (built in Java on the injected PagerView)` → `stroom-core-client/src/main/java/stroom/analytics/client/component/NotificationList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AbstractNotificationListPresenter.java`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticNotificationListPresenter.java`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ReportNotificationListPresenter.java`
- **Names:** The Abstract base and its two 42-line subclasses merge into one NotificationList and one NotificationListController (no Abstract... name). The subclasses differ only in onWrite (AnalyticNotificationListPresenter.java:39-41, ReportNotificationListPresenter.java:39-41), which becomes the writer lambda of their tabs in AnalyticNotificationPresenter and ReportNotificationPresenter; the controller takes the DocRef, process type and List<NotificationConfig> and returns the list. NotificationList is free.

| Into the template | What | Detail |
| --- | --- | --- |
| AbstractNotificationListPresenter.java:77-88 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid ui:field="dataGrid" tableName="Notifications"/> |
| AbstractNotificationListPresenter.java:92 | aria | toolbar name="Notification actions" |
| AbstractNotificationListPresenter.java:93-97 | component-declaration | <v:ToolbarButton> ADD "New Notification", EDIT "Edit Selected Notification" enabled="false", DELETE "Remove Selected Notification" enabled="false" |

**Stays in Java:** AbstractNotificationListPresenter.java:125,147 dialog captions Add/Edit Notification (controller opens AnalyticNotificationEdit with makeDialog); AbstractNotificationListPresenter.java:313 enabled states and read-only tick columns rebuilt; key provider and columns.

## `NodeJobListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerViewWithHeading package). Nothing material.

- **Component:** `stroom.node.client.component.NodeJobList`
- **Controller:** `stroom.node.client.component.NodeJobListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeJobList.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/node/client/presenter/NodeJobListPresenter.java (no view interface or binding; PagerViewWithHeading's shared binding stays for unit 75)
- **Names:** No clash; distinct from unit 75 JobNodeList.

| Into the template | What | Detail |
| --- | --- | --- |
| NodeJobListPresenter.java:88-92 | component-declaration | <dg:PagerViewWithHeading ui:field="pager"> (or the merged PagerView with heading from wave 5) containing <dg:DataGrid ui:field="dataGrid" tableName="Node Jobs" accessibleName="Node jobs"/> |
| NodeJobListPresenter.java:104-110 | component-declaration | toolbar name="Node job actions" with <b:ToolbarToggleButton ui:field="showEnabled" svg="FILTER" title="Show Only Enabled Jobs"/> (from JobNodeListHelper:345) and <b:ToolbarToggleButton ui:field="autoRefresh" svg="AUTO_REFRESH" title="Auto Refresh"/> |

**Stays in Java:** accessible name "Jobs on " + nodeName (:224-226): built from data; heading "Enabled/All jobs on node '...'" (:320-328): built from data; autoRefresh state (:111); columns via JobNodeColumns; REST, NodeChangeEvent/JobChangeEvent listeners: controller.

## `IndexFieldListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.index.client.component.IndexFieldList`
- **Controller:** `stroom.index.client.component.IndexFieldListController`
- **Template:** `none (QuickFilterPageView plus injected PagerView)` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexFieldList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexFieldListPresenter.java`; `QuickFilterUiHandlers implementation (IndexFieldListPresenter.java:59,94)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| IndexFieldListPresenter.java:90,93 | component-declaration | root <qf:QuickFilterPage ui:field="page" accessibleName="Filter fields"> (wave 10 entry 0) |
| IndexFieldListPresenter.java:97-102 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Index Fields"/> |
| IndexFieldListPresenter.java:106 | aria | toolbar name="Field actions" |
| IndexFieldListPresenter.java:107-111 | component-declaration | <v:ToolbarButton> ADD "New Index Field", EDIT "Edit Selected Index Field" enabled="false", DELETE "Delete Selected Index Fields" enabled="false" |

**Stays in Java:** IndexFieldListPresenter.java:317 table name with the index's name (data); IndexFieldListPresenter.java:155-169 enabled states and " (index is read only)" suffix; IndexFieldListPresenter.java:320 quick filter context per index; IndexFieldEdit (wave 9) opened by the controller with makeDialog, not declared.

## `PathwayListPresenter`

list, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.pathways.client.component.PathwayList`
- **Controller:** `stroom.pathways.client.component.PathwayListController`
- **Template:** `none (QuickFilterPageView plus injected PagerView)` → `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayListPresenter.java`; `QuickFilterUiHandlers implementation (PathwayListPresenter.java:60,98)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| PathwayListPresenter.java:93,97 | component-declaration | root <qf:QuickFilterPage ui:field="page" accessibleName="Filter pathways"> (wave 10 entry 0) |
| PathwayListPresenter.java:100-105 | component-declaration | <dg:PagerPanel ui:field="pager"> holding <dg:DataGrid tableName="Pathways"/> |
| PathwayListPresenter.java:109 | aria | toolbar name="Pathway actions" |
| PathwayListPresenter.java:110-114 | component-declaration | <v:ToolbarButton> ADD "New Pathway", EDIT "Edit Selected Pathway" enabled="false", DELETE "Delete Selected Pathways" enabled="false" |

**Stays in Java:** PathwayListPresenter.java:160-175 enabled states and read-only suffix; PathwayEdit owned at :92, opened by the controller; document read/write (controller).

## `AbstractMetaListPresenter`

list, presenter; complexity 5, effort L; used by 4.

**Since the merge.** Mechanical rename: DataGrid, DataGridSelectionEventManager and PagerView. The merge also added three imports it does not use (DataSelectionEvent, DataSelectionHandler, HasDataSelectionHandlers from stroom.data.client.event). Remove them when it is split, or sooner if checkstyle flags them.

- **Component:** `stroom.data.client.component.MetaList`
- **Controller:** `stroom.data.client.component.MetaListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/data/client/component/MetaList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/AbstractMetaListPresenter.java: merged, not renamed`
- **Names:** AbstractMetaList is not used. The base merges: its grid and columns (selected tick box, info, created, type, feed, pipeline, attribute columns) go into the one dumb MetaList component, and its REST, selection, process/reprocess/delete dialogs into MetaListController, which SteppingMetaListController and MetaRelationListController extend (units 33, 34). The three subclasses repeat the same addColumns list, so the component takes a column set (with or without the tick column, with or without the lineage expander) instead of overriding.

| Into the template | What | Detail |
| --- | --- | --- |
| AbstractMetaListPresenter.java:141-147 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Streams"/> |

**Stays in Java:** getToolbar() (:982-984) for MetaPresenter's buttons; setTableName (:200-202) used by unit 34 (or set in the declaring template); selection model and DataGridSelectionEventManager (:143-145), Selection<Long> and Listeners; columns: data-driven; REST, criteria, ProcessChoice/SelectionSummary/DocSelectionPopup dialogs via makeDialog (:598, :672, :867; dialogs, not declared): controller.

## `CredentialsListPresenter`

list, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical rename only: DataGridWidget is DataGrid, and PagerView moved to stroom.widget.datagrid.client.

- **Component:** `stroom.credentials.client.component.CredentialsList`
- **Controller:** `stroom.credentials.client.component.CredentialsListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsList.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/credentials/client/presenter/CredentialsListPresenter.java (split into the two; no view interface or GIN binding exists, it is made JIT)
- **Names:** CredentialsList and CredentialsListController: no clash in all_simple_names.json or this wave.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| CredentialsListPresenter.java:152 | `dataGrid.setWidth("100%")` | static | w-100 (stroom.css:843) as addStyleNames on the declared DataGrid, or drop it if the PagerView list container already fills (check with the screenshot diff) |

| Into the template | What | Detail |
| --- | --- | --- |
| CredentialsListPresenter.java:148-151 | component-declaration | <dg:PagerPanel ui:field="pager"> containing <dg:DataGrid ui:field="dataGrid" tableName="Credentials" multiLine="true"/> instead of new DataGrid + view.setDataWidget |
| CredentialsListPresenter.java:155-166 | component-declaration | toolbar name="Credential actions" with <b:ToolbarButton ui:field="add" svg="ADD" title="New Credential"/>, delete (DELETE, "Delete Selected Credential", enabled="false"), edit (EDIT, "Edit Selected Credential", enabled="false"), replaceSecret (PASSWORD, "Replace Selected Credential's Secret", enabled="false") declared as the PagerView's toolbar children |
| CredentialsListPresenter.java:171 | css-class | credentials-list on the root: it has no CSS rule (components.css has only credentials-details-dialog/credentials-manager-dialog); drop it rather than move it |

**Stays in Java:** setKeyProvider/selection model and the name column (cells and columns are built in the component's Java); button enabled states from the selected row's permissions (:359-365); REST data provider, permission check (:174-177), edit/replace-secret dialogs via makeDialog (CredentialEditPresenter, ReplaceSecretPresenter are dialogs, not declared), first-row default selection (:189-199): controller.

## `MetaListPresenter`

list, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.data.client.component.MetaList`
- **Controller:** `stroom.data.client.component.MetaListController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/data/client/component/MetaList.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/MetaListPresenter.java`; `StreamStoreModule.java:157 bind(MetaListPresenter.class)`; `stroom-gwt-workbench StreamStoreScreenModule.java bind(MetaListPresenter.class): processing/:113, query/:113, content/:113, editors/:114`
- **Names:** MetaList, MetaListController free. Takes the merged AbstractMetaListPresenter (unit 31): the same pair.

| Into the template | What | Detail |
| --- | --- | --- |
| MetaListPresenter.java:55-111 addColumns | structure | the column list is shared with units 33 and 34; it lives once in MetaList's Java with the tick column on (allowSelectAll=true, :53) |

**Stays in Java:** columns (data-driven; Java); all behaviour in MetaListController.

## `SteppingMetaListPresenter`

list, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.data.client.component.MetaList`
- **Controller:** `stroom.data.client.component.SteppingMetaListController`
- **Template:** `none` → `none (uses MetaList.ui.xml; no template of its own)`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/SteppingMetaListPresenter.java`; `StreamStoreModule.java:158 bind(SteppingMetaListPresenter.class)`; `workbench StreamStoreScreenModule.java: processing/:114, query/:114, content/:114, editors/:115`
- **Names:** SteppingMetaList is not needed as a component: it is MetaList without the tick column (its addColumns :59-111 is MetaList's minus addSelectedColumn). SteppingMetaListController (free) extends MetaListController and calls view.setSelectable(false).

**Stays in Java:** getResultPage/getSelectionModel/getSelected (:113-124): on the controller.

## `MetaRelationListPresenter`

list, presenter; complexity 2, effort M; used by 1.

- **Component:** `stroom.data.client.component.MetaList`
- **Controller:** `stroom.data.client.component.MetaRelationListController`
- **Template:** `none` → `none (uses MetaList.ui.xml; the declaring template sets tableName="Related Streams")`
- **Goes:** `stroom-core-client/src/main/java/stroom/data/client/presenter/MetaRelationListPresenter.java (no binding)`
- **Names:** MetaRelationListController (free) extends MetaListController and turns on MetaList's lineage expander column. The agreed meta browser lineage design replaces this table with a processing tree later, so keep it thin.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| MetaRelationListPresenter.java:122 | `dataGrid.setColumnWidth(expanderColumn, ExpanderCell.getColumnWidth(maxDepth), Unit.PX)` | dynamic | the grid's own --data-grid__columns (DataGrid.java:82), which DataGrid.setColumnWidth already writes; add a setColumnWidth(column, width) overload without the ignored Style.Unit (DataGrid.java:449) and call that |

| Into the template | What | Detail |
| --- | --- | --- |
| MetaRelationListPresenter.java:79 | text | setTableName("Related Streams"): tableName attribute where the relation list is declared |
| MetaRelationListPresenter.java:161 | text | expander heading "<br/>": empty heading instead |

**Stays in Java:** expander depth and tree building (:118-157, :213-232): data; setSelectedStream (:82-104): controller.

