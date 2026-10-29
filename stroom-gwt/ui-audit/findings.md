# UI audit: every finding

The verified findings behind [../UI_AUDIT.md](../UI_AUDIT.md), by dimension, most severe first.
Verdict: confirmed (as found) or adjusted (the title, fix or severity changed by the verifier).

## Composition (40)

### dashboard-query-01: Fixed child components (ExpressionTree, ExpressionEditor, QueryHelp, CurrentSelection, ResultStoreList, QueryResultTable, QueryTextPane) are put into SimplePanel/FlowPanel slots from Java, not declared in the parent template

*high, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPane.ui.xml:32`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPane.java:85`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPaneController.java:173`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavourites.ui.xml:22`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavourites.java:107`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavouritesController.java:84`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistory.ui.xml:12`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistory.java:102`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistoryController.java:74`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.ui.xml:21`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.java:75`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditorController.java:71`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ConditionalFormattingRuleEditor.ui.xml:16`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ConditionalFormattingRuleEditor.java:92`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ConditionalFormattingRuleEditorController.java:68`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditor.ui.xml:19`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditor.ui.xml:24`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditor.java:54`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditor.java:59`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditorController.java:64`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditorController.java:65`, `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexSettings.ui.xml:56`, `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexSettings.java:167`, `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexSettingsController.java:85`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFunctionEditor.ui.xml:11`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFunctionEditor.java:52`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFunctionEditorController.java:77`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.ui.xml:12`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.java:58`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEditController.java:256`, `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStores.ui.xml:9`, `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStores.java:46`, `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStoresController.java:63`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplit.ui.xml:10`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplit.ui.xml:11`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplitController.java:66`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplitController.java:67`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.ui.xml:28`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.java:89`, `stroom-core-client/src/main/java/stroom/query/client/component/AbstractQueryDataController.java:96`

**Evidence:** git grep '\.setWidget(|\.add(' over the slice, then the controllers that call the setters. Every one of these slots always receives the same child, and each child already has its own Component + Controller pair whose controller is injected (e.g. ExpressionTreeController(EventBus, ExpressionTree view, ...) calls initWidget(view)) and then handed to the parent as IsWidget: QueryPane.expressionTree <- ExpressionTreeController; QueryFavourites/QueryHistory.expression <- ExpressionTreeController; ExpressionEditor.expressionTree <- ExpressionTreeController; ConditionalFormattingRuleEditor.expression, SelectionHandlerEditor.layout and SolrIndexSettings.retentionExpressionPanel <- ExpressionEditorController; SelectionHandlerEditor.currentSelection <- CurrentSelectionController; ColumnFunctionEditor.queryHelp and QueryEdit.help <- QueryHelpController; SearchResultStores.tableContainer <- ResultStoreListController; QueryResultTableSplit.table/source <- QueryResultTableController/QueryTextPaneController; QueryDataPane.tableContainer <- QueryResultTableController. None is chosen at runtime. The attach pattern already exists in the slice (TermEditorController.attach(TermEditor), TimeRangeSelectorController.attach, VisFrameController.attach).

**Fix:** Declare the child component in the parent template and have the parent controller make the child controller and attach it, e.g. QueryPane.ui.xml: <q:ExpressionTree ui:field="expressionTree" addStyleNames="dock-max query-pane__expression-tree"/> with expressionTree = make(expressionTreeProvider).attach(view.getExpressionTree()); likewise <q:ExpressionEditor ui:field="expression"/> in ConditionalFormattingRuleEditor, SelectionHandlerEditor and SolrIndexSettings, <q:ExpressionTree> in ExpressionEditor/QueryFavourites/QueryHistory, <q:QueryHelp> in ColumnFunctionEditor and QueryEdit, <dash:CurrentSelection> in SelectionHandlerEditor, <q:ResultStoreList> in SearchResultStores, <q:QueryResultTable> + <q:QueryTextPane> in QueryResultTableSplit, <q:QueryResultTable> in QueryDataPane. Give ExpressionTreeController, ExpressionEditorController, QueryHelpController, CurrentSelectionController, ResultStoreListController, QueryResultTableController and QueryTextPaneController an attach(view) in place of the injected view, and delete the setX(IsWidget) slot setters.

**Verifier:** Checked QueryPaneController:173 (view.setExpressionTree(expressionTree) with an injected ExpressionTreeController), ExpressionEditorController (injects ExpressionTreeController, view.setExpressionTree), SelectionHandlerEditorController:64-65 (setExpressionEditor/setCurrentSelection with injected controllers), QueryResultTableSplitController:66-67 (setTable/setSource) and AbstractQueryDataController:96 (setTable). Each slot always gets the same injected child; none is chosen at runtime. ExpressionTreeController already uses make(termEditorControllerProvider).attach(termEditor), so the attach pattern exists and the fix works in UiBinder.

### dashboard-query-02: The dashboard and the query screen each have their own copies of the same components and controllers (Table/QueryResultTable, Vis/QueryResultVis, TextPane/QueryTextPane, column managers, search models), and two components are identical

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/Table.java:31`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/Table.ui.xml:6`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTable.java:31`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTable.ui.xml:5`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/Vis.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/Vis.ui.xml`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVis.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVis.ui.xml`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRules.java:30`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRules.ui.xml`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableFilterInput.java:30`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableFilterInput.ui.xml`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/VisController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVisController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnsManager.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTableColumnsManager.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/TableColumnValuesDataSupplier.java`, `stroom-core-client/src/main/java/stroom/query/client/QueryTableColumnValuesDataSupplier.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TextPane.ui.xml`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTextPane.ui.xml`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/SearchModel.java`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java`

**Evidence:** diff Vis.ui.xml QueryResultVis.ui.xml and diff of the class bodies differ only in comments and the class name; Table.java vs QueryResultTable.java differ only in javadoc/class name (templates differ by tableName="Query Results"); MultiRules.java/.ui.xml and TableFilterInput.java/.ui.xml are line-for-line the same (FlowPanel panel, clearColumns, addColumn(name, IsWidget) making a TableFilterColumn). Controllers come in pairs of similar size: TableController 1486 vs QueryResultTableController 1097 lines, VisController 828 vs QueryResultVisController 698, ColumnsManager 875 vs QueryTableColumnsManager 834, TextPaneController 747 vs QueryTextPaneController 713, SearchModel 601 vs QueryModel 527. TextPane uses a slot filled from Java while QueryTextPane declares CodeEditor and HtmlPane in its template.

**Fix:** Keep one component per thing: delete Vis (use QueryResultVis, or rename both to ResultVis) and Table (use QueryResultTable, or rename it ResultTable), and fold TableFilterInput and MultiRules into one TableFilterColumns component (setColumns). Then, as a follow-up, extract the shared controller logic (column manager, column values supplier, text pane) into one controller per part that the dashboard pane and the query screen both make, with TextPane declaring its CodeEditor and HtmlPane like QueryTextPane does.

**Verifier:** Diffs confirm the claim: Vis.java/.ui.xml and QueryResultVis differ only in package, imports, comments and class name. Table and QueryResultTable differ only in javadoc and name. MultiRules and TableFilterInput differ only in javadoc, parameter names and name. Controller line counts match the finding (1486/1097, 828/698, 875/834, 747/713).

### data-pipeline-processing-01: Fixed child components put into SimplePanel slots from Java instead of being declared in the parent template (18 parents, about 27 slots)

*high, effort large, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/job/client/component/Jobs.java:46`, `stroom-core-client/src/main/java/stroom/job/client/component/Jobs.java:53`, `stroom-core-client/src/main/java/stroom/job/client/component/JobsController.java:57`, `stroom-core-client/src/main/java/stroom/node/client/component/Nodes.java:46`, `stroom-core-client/src/main/java/stroom/node/client/component/Nodes.java:53`, `stroom-core-client/src/main/java/stroom/node/client/component/NodesController.java:58`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowser.java:47`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowser.java:52`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowser.java:57`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowserController.java:135`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.java:46`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.java:53`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.java:45`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.java:50`, `stroom-core-client/src/main/java/stroom/processor/client/component/Processors.java:50`, `stroom-core-client/src/main/java/stroom/processor/client/component/Processors.java:60`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorsController.java:128`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorEdit.java:87`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorEditController.java:124`, `stroom-core-client/src/main/java/stroom/processor/client/component/FeedDependenciesEditor.java:58`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.java:85`, `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEdit.java:95`, `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeGroupEdit.java:60`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeGroupEdit.java:60`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexFieldEditor.java:146`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructure.java:84`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructure.java:89`, `stroom-core-client/src/main/java/stroom/task/client/component/ServerTasks.java:69`, `stroom-core-client/src/main/java/stroom/data/client/component/ExpressionDialog.java:44`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewer.java:112`

**Evidence:** I grepped for `.setWidget(`, `.setContent(` and `set*(IsWidget`/`(Widget`, then for the controller calls that fill them (`view.setJobListView(jobList)`, and so on). Each slot is filled once, in the constructor, always with the same child: Jobs.jobList gets JobList and jobNodeList gets JobNodeList. Nodes gets NodeStatusList and NodeJobList. MetaBrowser gets MetaList, MetaList (relations) and DataViewer. ProcessorTasksTab and ProcessorFilterTasks get ProcessorTaskSummaryList and ProcessorTasksList. Processors gets ProcessorList and ExpressionTree. ProcessorEdit.expression gets ExpressionEditor. FeedDependenciesEditor gets FeedDependencyList. ProcessorProfileEdit gets ProfilePeriodList. NodeGroupEdit gets NodeGroupStateList. FsVolumeGroupEdit gets FsVolumeStatusList. IndexVolumeGroupEdit gets IndexVolumeStatusList. IndexFieldEditor.denseVectorOptions gets DenseVectorField. PipelineStructure gets PropertyList and PipelineReferenceList. ServerTasks.listContainer gets TaskManagerList. ExpressionDialog.expression gets ExpressionEditor. SourceViewer.setText puts DataTextPane in its ClassificationWrapper. None of these is chosen at runtime. Each child controller calls initWidget(view) on a component injected into it, so the parent cannot declare that component.

**Fix:** Declare each child component in its parent's template (e.g. `<jc:JobList ui:field="jobList"/>`, `<qc:ExpressionTree ui:field="details"/>`, `<qc:ExpressionEditor ui:field="expression"/>`, `<ic:DenseVectorField ui:field="denseVectorOptions"/>` with setVisible), and expose getters. Give each child controller attach(Component) in place of initWidget(view), as DocSelectionController.attach does, and have the parent controller call make(provider).attach(view.getX()). Delete the setXxxView/setList/setDetails setters and their SimplePanels. ExpressionTree and ExpressionEditor are already components, so no step waits on them.

**Verifier:** Checked every listed setter: Jobs.java:47/54, Nodes.java:47/54, MetaBrowser.java:48/53/58, ProcessorTasksTab.java:47/54, ProcessorFilterTasks.java:46/51, Processors.java:51/61, ProcessorEdit.java:88, FeedDependenciesEditor.java:59, ProcessorProfileEdit.java:86, NodeGroupEdit.java:96, FsVolumeGroupEdit/IndexVolumeGroupEdit.java:61, IndexFieldEditor.java:147, PipelineStructure.java:85/90, ServerTasks.java:70, ExpressionDialog.java:45 and SourceViewer.java:113. Each one is setWidget/setContent, called once from the controller constructor with a fixed child (e.g. JobsController:57-58, ProcessorsController:128-129 `view.setDetails(expressionTree)`, IndexFieldEditorController:57, which only toggles visibility at :141). Each child controller calls initWidget(view) (e.g. JobListController:68). The attach pattern the suggestion relies on already exists (DocSelectionController.attach). UI-GUIDE:47 allows a slot only for 'a part that has no component of its own to declare', and every one of these children has a component, so this breaks the plan's own rule. One factual error: ExpressionTree and ExpressionEditor are already components (query/client/component/ExpressionTree.java/.ui.xml and ExpressionEditor.java/.ui.xml, each with a controller), so they need no wait and can be declared now like the others.

### explorer-documents-shell-01: Main's two fixed children, the explorer and the document tabs, are passed in from Java, and their split pane is rebuilt in Java

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:95`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:105`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:229`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:248`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:258`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:437`, `stroom-core-client/src/main/java/stroom/main/client/component/MainController.java:96`, `stroom-core-client/src/main/java/stroom/main/client/component/MainController.java:97`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml:18`

**Evidence:** MainController always passes the same two children: view.setContentView(own(contentTabPane)) and view.setExplorerView(own(navigation)). Main keeps them as westWidget and centerWidget, and on every rebuildLayout() it creates `new SplitPane()` (setThin, the split-pane--navigation class, setSizes, setNames), a `new ResizeFlowPanel()` wrapper and a second `new SplitPane()` for the docked AI panel, then adds everything to contentPanel. setLandmark() also sets role=navigation, aria-label=Explorer and role=main on the children with setAttribute, which is markup in Java. Main.ui.xml only has an empty ResizeLayoutPanel slot (line 18). The docked AI panel and a maximised view are runtime children, which the rules allow; the explorer, the content and the split between them are not.

**Fix:** Declare the fixed layout in Main.ui.xml: `<l:SplitPane thin="true" sizes="300px, *" names="Explorer, Content" addStyleNames="split-pane--navigation"><e:Navigation ui:field="explorer" role="navigation" ariaLabel="Explorer"/><ct:ContentTabPane ui:field="content" role="main"/></l:SplitPane>`. NavigationController and ContentTabPaneController then attach to view.getExplorer() and view.getContent() (the attach pattern EntityTreeController.attach already uses). Hiding the explorer becomes SplitPane show/hide state, not a rebuild. Only the docked panel and the maximised view stay as Java-side slots.

**Verifier:** Main.java:94-108 setExplorerView/setContentView store westWidget/centerWidget; MainController.java:96-97 always passes own(contentTabPane) and own(navigation). rebuildLayout (Main.java:200-270) makes new SplitPane (setThin, split-pane--navigation, setSizes, setNames) each rebuild. Main.ui.xml has only the empty ResizeLayoutPanel contentPanel. SplitPane already has setPaneHidden/maximise/restore (SplitPane.java:242-285), so the declared-split suggestion is workable; the docked AI panel and maximised view remain runtime slots.

### floormap-pathways-stores-01: Fixed child components are put into SimplePanel slots from Java instead of being declared in the parent template

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:67`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:72`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:77`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:82`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:87`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditorController.java:304`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.java:51`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.java:56`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.java:61`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMapController.java:434`, `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSplit.java:46`, `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSplit.java:51`, `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayEdit.java:63`, `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayEdit.java:68`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesListTab.java:46`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.java:48`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.java:55`, `stroom-core-client/src/main/java/stroom/cache/client/component/Caches.java:49`, `stroom-core-client/src/main/java/stroom/cache/client/component/Caches.java:56`, `stroom-core-client/src/main/java/stroom/script/client/component/ScriptSettings.java:43`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/ImportsTab.java:46`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/ImportsTab.java:51`

**Evidence:** I searched for `.setWidget(`, `.add(` and `setContent` in the slice. Each slot below is a `g:SimplePanel` (ContentStoreTab: an AriaPanel) in the template. The controller always fills it with the same child, made with `make()` or `own()`, and that child has a component of its own:
- FloorMapEditor: canvas=FloorMapCanvas, dock=FloorMapDock, timeline=FloorMapTimeline, factList=FloorMapFactList, timeList=FloorMapTimeList.
- FloorMapMap: map=FloorMapCanvas, dock=FloorMapDock, timeline=FloorMapTimeline.
- PathwaysSplit: PathwayList, PathwayTree.
- PathwayEdit: PathwayTree, ConstraintList.
- TracesListTab: TracesList.
- ContentStoreTab: ContentStoreContentPackList, ContentStoreContentPackDetails.
- Caches: CacheList, CacheNodeList.
- ScriptSettings: ScriptDependencyList.
- ImportsTab: DocRefList, EffectiveWordsList.
None of these children is picked at runtime. TracesListTab.ui.xml already declares its other child, `<pp:TraceOverviewChart ui:field="traceOverview"/>`, in the same template, so that template mixes both ways.

**Fix:** Declare each child in its parent's template, e.g. FloorMapEditor.ui.xml `<c:FloorMapCanvas ui:field="canvas" addStyleNames="dashboard-panel overflow-hidden"/>`, `<c:FloorMapDock ui:field="dock"/>`, and Caches.ui.xml `<c:CacheList ui:field="cacheList"/><c:CacheNodeList ui:field="cacheNodeList"/>`. Then drop the setXxx(IsWidget) setters and give the parent getters.

The controller would then take the declared component rather than inject a new one. That needs one framework addition, the same for all of these: for example, a child controller made with `make(provider)` and then `attach(view.getCacheList())` (as DocSelectionController does), or a Controller constructor that takes its component from the parent. Do it once and apply it to all 22 slots.

**Verifier:** Checked every cited setter (FloorMapEditor.java:66-88, FloorMapMap.java:50-61, PathwaysSplit:45-51, PathwayEdit:62-68, TracesListTab:45, ContentStoreTab:47-56, Caches:48-56, ScriptSettings:42, ImportsTab:45-51). Each is a SimplePanel/AriaPanel slot, and its controller always fills it with the same make()d child (e.g. FloorMapEditorController.java:240-308). UI-GUIDE.md:47 allows a slot only for 'a part that has no component of its own to declare'. These children all have components. TracesListTab.ui.xml declares TraceOverviewChart inline next to the tracesList slot, as stated. The finding is right that this needs a framework change: a controller's widget is its component (COMPONENT_MODEL_DESIGN:881), and only attach-style controllers such as DocSelectionController.attach take a declared part.

### security-settings-03: Fixed child components are put into SimplePanel slots from Java instead of being declared in the parent's template

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/UserAccess.java:46`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAccess.java:53`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAccessController.java:80-81`, `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissions.java:70`, `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissions.java:77`, `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissionsController.java:58-59`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroups.java:61`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroups.java:66`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroups.java:78`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroupsController.java:130-132`, `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsTab.java:66`, `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsTabController.java:80`, `stroom-core-client/src/main/java/stroom/security/client/component/UserPermissionReport.java:63`, `stroom-core-client/src/main/java/stroom/security/client/component/UserPermissionReportController.java:85`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsManagerDialog.java:48`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsManagerDialogController.java:59`, `stroom-core-client/src/main/java/stroom/config/global/client/component/GlobalPropertyTab.java:62`, `stroom-core-client/src/main/java/stroom/config/global/client/component/GlobalPropertyTabController.java:71`, `stroom-core-client/src/main/java/stroom/activity/client/component/ManageActivity.java:61`, `stroom-core-client/src/main/java/stroom/activity/client/component/ManageActivityController.java:101`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetSettings.java:66`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetSettings.java:79`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetSettingsController.java:83-84`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionPolicy.java:63`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionPolicy.java:76`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionPolicyController.java:100-101`, `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateTab.java:59`, `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateTabController.java:120`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleEditor.java:53`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleEditorController.java:66`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionRuleEditor.java:69`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionRuleEditorController.java:59`, `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateEdit.java:93`, `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateEditController.java:71`

**Evidence:** Found with `grep -nE 'public void set[A-Za-z]*\((final )?IsWidget'` over the slice, then each call site in the controllers. Every one is filled once, in the controller's constructor, with the same injected controller every time. UserAccess gets UserAccessListController and UserSessionsListController. AppPermissions gets AppUserPermissionsListController and AppPermissionsEditController. UserAndGroups gets three UserListControllers (users, parents, children). DocumentUserPermissionsTab gets DocumentUserPermissionsListController. UserPermissionReport gets a documents list (QuickFilterPage.setContent). CredentialsManagerDialog gets CredentialsListController. GlobalPropertyTab gets ManageGlobalPropertyListController. ManageActivity gets ActivityListController. RuleSetSettings gets RuleSetListController and ExpressionTreeController. DataRetentionPolicy gets DataRetentionPolicyListController and ExpressionTreeController. ContentTemplateTab's table gets ContentTemplateListController. RuleEditor, DataRetentionRuleEditor and ContentTemplateEdit each get an ExpressionEditorController. None is chosen at runtime. Each child list already has a dumb component with a template (UserAccessList.ui.xml, UserSessionsList.ui.xml, AppUserPermissionsList.ui.xml, AppPermissionsEdit.ui.xml, UserList.ui.xml, DocumentUserPermissionsList.ui.xml, CredentialsList.ui.xml, ManageGlobalPropertyList.ui.xml, ActivityList.ui.xml, RuleSetList.ui.xml, DataRetentionPolicyList.ui.xml, ContentTemplateList.ui.xml).

**Fix:** Declare each child's component in the parent's template in place of the SimplePanel, e.g. UserAccess.ui.xml `<sec:UserAccessList ui:field="list"/><sec:UserSessionsList ui:field="sessions"/>`, and UserAndGroups.ui.xml three `<sec:UserList>`. Give the list controllers the attach form (`userAccessList = make(provider).attach(view.getList())`, as DocSelectionController and CreateUser's externalUser already do), and drop the setXxx(IsWidget) slot setters. For the expression editor and tree, declare the expression component (`<q:ExpressionTree ui:field="expression"/>`) once ExpressionTreeController/ExpressionEditorController can attach. RuleSetSettings.setRuleList(ruleList, toolbar) and DataRetentionPolicy.setRuleList then take the toolbar from the declared list component instead.

**Verifier:** Spot-checked several. UserAccessController:80-81 runs view.setList(userAccessList) and view.setSessionList(userSessionsList) with injected controllers. UserAndGroupsController:130-132 sets three UserListControllers. ContentTemplateEditController:70 calls view.setExpressionEditor(expressionEditor). RuleSetSettings:66/79, AppPermissions:70/77 and UserPermissionReport:63 (documents.setContent) follow the same pattern. Each is filled once in the constructor with a fixed child. CreateUserController:63-64 and DocSelectionController.attach show that the attach pattern exists. The effort is large because the list controllers currently initWidget their own view and would need an attach form.

### dashboard-query-20: FilterCell builds raw <input> and <button> controls in cell markup

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCell.java:259`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCell.java:269`, `stroom-app/src/main/resources/ui/css/result-table.css`

**Evidence:** FilterCell.render writes outer.elem(fromSafeConstant("input"), type=text …) and a fromSafeConstant("button") element with the toolbar-button class and the DISABLE icon. UI-GUIDE: 'No raw HTML controls … in SafeHtml or a cell's markup … in a grid Stroom's cell (TextFieldCell …)'. result-table.css styles them through '.result-table__grid input'.

**Fix:** Build the filter header from Stroom's cells: a TextFieldCell for the value and a toolbar-button cell (or a CompositeCell holding both), as the other grid control cells are. Then remove the '.result-table__grid input' rules.

**Verifier:** FilterCell.render writes raw elements: fromSafeConstant("input") type=text and fromSafeConstant("button") with the toolbar-button class. UI-GUIDE.md:35 forbids raw <input>/<button> in a cell's markup and names TextFieldCell (stroom-core-client-widget/.../cell/textfield/client/TextFieldCell.java). result-table.css:31/41 style '.result-table__grid input'.

### data-pipeline-processing-02: Navigator and progress-bar slots in DataViewer and SourceViewer are filled and cleared from Java to show and hide fixed children

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.java:158`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.java:167`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:227`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:306`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:1035`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:1177`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewer.java:127`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewer.java:145`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewerController.java:131`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewerController.java:183`

**Evidence:** `dataView.setNavigator(itemNavigator)` and `dataView.setNavigator(null)`, `dataView.setProgress(sourceProgressBar)` and `dataView.setProgress(null)`, and `view.setNavigator(characterNavigator)`. The children are always ItemNavigator, CharacterNavigator and SourceProgressBar. SourceProgressBar is a dumb widget-module component (stroom.widget.progress.client.component.SourceProgressBar) that is injected into the controller. The null cases only hide the child.

**Fix:** Declare `<ic:ItemNavigator ui:field="navigator"/>` and `<pb:SourceProgressBar ui:field="progress"/>` in DataViewer.ui.xml, and `<dc:CharacterNavigator ui:field="navigator"/>` and `<pb:SourceProgressBar .../>` in SourceViewer.ui.xml. Replace setNavigator(IsWidget) and setProgress(IsWidget) with setNavigatorVisible(boolean) and setProgressVisible(boolean). The navigator controllers attach to the declared components, and the controller gets the progress bar from the view instead of having it injected.

**Verifier:** DataViewer.java:157-173 and SourceViewer.java:126-150 setNavigator/setProgress fill the container or clear() it on null. DataViewerController:227-228, 306/313, 533, 1035/1039 and 1177/1179, and SourceViewerController:131, 183 and 423/425, always pass the same injected ItemNavigatorController/CharacterNavigatorController/SourceProgressBar or null. SourceProgressBar is a dumb component in stroom-core-client-widget (stroom.widget.progress.client.component). ItemNavigator and CharacterNavigator have .ui.xml components. Replacing these with declared children and visibility toggles works.

### data-pipeline-processing-03: SteppingScreen inserts its fixed StepLocationLink and StepControl into a FlowPanel from Java

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreen.java:88`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreen.ui.xml:24`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreenController.java:209`

**Evidence:** `right.insert(stepLocationLink, 1); right.insert(stepControl, 2);` is called once with the injected StepLocationLinkController and StepControlController. The template marks the place with a comment: `<!-- The step location link and the step controls go here (setStepControls) -->`. stepMessage is also a SimplePanel whose text is written with getElement().setInnerText, where a Label would do.

**Fix:** In SteppingScreen.ui.xml, put `<sc:StepLocationLink ui:field="stepLocationLink"/>` and `<sc:StepControl ui:field="stepControl"/>` between the progress HTMLPanel and the Terminate toolbar, and have both controllers attach to them. Remove setStepControls. Make stepMessage a `<tx:Label>`.

**Verifier:** SteppingScreen.java:88-91 `right.insert(stepLocationLink, 1); right.insert(stepControl, 2);`, called once at SteppingScreenController:209 with the injected controllers, each of which calls initWidget(view) (StepLocationLinkController:47, StepControlController:46). The template comment at SteppingScreen.ui.xml:26 marks the slot. stepMessage is a SimplePanel set with getElement().setInnerText (SteppingScreen.java:102).

### data-pipeline-processing-04: DataPreviewTab and SourceTab are empty wrapper components around a single smart part

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/DataPreviewTab.java:44`, `stroom-core-client/src/main/java/stroom/data/client/component/DataPreviewTab.ui.xml`, `stroom-core-client/src/main/java/stroom/data/client/component/DataPreviewTabController.java:51`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceTab.java:44`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceTab.ui.xml`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceTabController.java:49`

**Evidence:** Each template is only `<g:SimplePanel ui:field="container" styleName="max"/>`, and its controller puts DataViewerController or SourceViewerController into it. UI-GUIDE says: "A screen that is one smart part and nothing else ... shows that part's widget as its own, rather than wrap it in a component with nothing in it".

**Fix:** Delete DataPreviewTab and SourceTab (Java and .ui.xml). DataPreviewTabController and SourceTabController should show their DataViewer or SourceViewer as their own widget (initWidget of the part's widget, or the ContentTabController equivalent used by IndexDataController).

**Verifier:** DataPreviewTab.ui.xml and SourceTab.ui.xml are each only `<g:SimplePanel ui:field="container" styleName="max"/>`. DataPreviewTabController:47-51 does initWidget(view) then view.setContentView(dataController). UI-GUIDE:27 explicitly says such a screen shows the part's widget as its own rather than wrapping it in an empty component.

### explorer-documents-shell-02: Slots filled from Java with fixed child components that have components of their own (template + attach would do)

*medium, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/FindPanel.ui.xml:10`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindPanel.java:88`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindController.java:96`, `stroom-core-client/src/main/java/stroom/annotation/client/component/FindAnnotationController.java:62`, `stroom-core-client/src/main/java/stroom/annotation/client/component/BrowseAnnotationController.java:55`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationLinksController.java:78`, `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEvents.ui.xml:14`, `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEvents.java:71`, `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEventsController.java:104`, `stroom-core-client/src/main/java/stroom/importexport/client/component/DependenciesTab.ui.xml:9`, `stroom-core-client/src/main/java/stroom/importexport/client/component/DependenciesTab.java:61`, `stroom-core-client/src/main/java/stroom/importexport/client/component/DependenciesTabController.java:60`, `stroom-core-client/src/main/java/stroom/analytics/client/component/DuplicateManagement.ui.xml:42`, `stroom-core-client/src/main/java/stroom/analytics/client/component/DuplicateManagementController.java:47`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessing.ui.xml:8`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessing.ui.xml:9`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessingController.java:80`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessingController.java:81`, `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShards.ui.xml:9`, `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShards.ui.xml:10`, `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShardsController.java:87`, `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShardsController.java:88`

**Evidence:** I grepped for slot setters taking IsWidget and their callers. Each slot always gets the same child, and the child has its own component:
- FindPanel.bottomPanel: FindController always passes FindDocResultListController (FindDocResultList component); FindAnnotation, BrowseAnnotation and AnnotationLinks always pass FindAnnotationListController (FindAnnotationList).
- LinkedEvents.data: always the injected DataController.
- DependenciesTab.dataGrid: always DependencyListController (DependencyList). The SimplePanel is also misleadingly named dataGrid.
- DuplicateManagement.list: always DuplicateManagementListController.
- ScheduledProcessing.scheduleList and historyList: always ScheduledProcessListController and ScheduledProcessHistoryListController.
- AnalyticDataShards.shardList: always AnalyticDataShardListController; .table: always the injected tableController.
The attach pattern already exists for whole components: EntityTreeController.attach(EntityTree), EntityCheckTreeController.attach(ExplorerTree).
Not reported, as the rules allow them: AnalyticProcessing.processSettings and AnalyticNotificationEdit.destination (chosen at runtime), Core.setContent and FullScreen.setContentView (runtime screens), and AdvancedQuickFilter and ViewSettings expression editors (the guide's own slot example).

**Fix:** Declare each child in the parent's template and attach to it: `<ie:DependencyList ui:field="list" addStyleNames="dock-max"/>` in DependenciesTab, `<an:DuplicateManagementList ui:field="list" .../>`, `<an:ScheduledProcessList ui:field="scheduleList"/><an:ScheduledProcessHistoryList ui:field="historyList"/>`, `<an:AnalyticDataShardList ui:field="shardList"/>`. Give the list controllers `attach(X view)` in place of their injected view: `list = make(listProvider).attach(view.getList())`. FindPanel is shared by 5 screens with 2 list types, so either make it a container its users declare around their list (`<ex:FindPanel><ex:FindDocResultList ui:field="list"/></ex:FindPanel>`), or give FindDocResultList and FindAnnotationList each a template that includes the filter. LinkedEvents declares the data viewer component (from the data slice) in its second split pane, if that viewer gets an attach.

**Verifier:** Each listed setter is called once with the same injected list controller: DependenciesTabController:60 view.setListView(dependencyList); DuplicateManagementController:47 view.setList; ScheduledProcessingController:80-81 setScheduleList/setHistoryList; AnalyticDataShardsController:87-88 setShardList/setTable; LinkedEventsController view.setData(dataController). Each child has its own component (DependencyList.ui.xml, DuplicateManagementList.ui.xml, ScheduledProcessList.ui.xml, ScheduledProcessHistoryList.ui.xml, AnalyticDataShardList.ui.xml). The attach pattern exists (EntityTreeController.attach:70, EntityCheckTreeController.attach:56). FindPanel.setResultView (FindPanel.java:88) is shared by 4 callers with 2 list types, and the finding already treats it as a container case.

### explorer-documents-shell-03: ExecutionSchedulesController uses a bare PagerPanel as its view and builds its DataGrid, toolbar buttons and selection-count label in Java

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:86`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:90`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:149`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:159`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:165`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:205`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:222`

**Evidence:** The content tab's constructor takes `final PagerPanel view`, then:
- `dataGrid = new DataGrid<>(); dataGrid.setTableName("Execution Schedules"); view.setDataWidget(dataGrid)`
- `private final Html selectionCountLabel = new Html()`, with `selectionCountLabel.addStyleName("paging-bar paging-bar__label"); view.addToolbarWidget(selectionCountLabel)`, which borrows the paging-bar block's element
- six fixed buttons through `view.getToolbar().button(SvgImage.EDIT, ...)` and similar (lines 205-222).
Every other list in the slice (NotificationList, ScheduledProcessList, DuplicateManagementList) declares PagerPanel, ToolbarButtons and DataGrid in a template. The controller is also the only one in the slice that sets CSS classes on a widget it made.

**Fix:** Add an ExecutionSchedules component (ExecutionSchedules.java + .ui.xml): `<dg:PagerPanel ui:field="pager" toolbarName="Execution schedule actions"><b:ToolbarButton ui:field="editButton" svg="EDIT" .../> ... <tx:Html ui:field="selectionCount" styleName="execution-schedules__selection-count"/><dg:DataGrid ui:field="dataGrid" tableName="Execution Schedules"/></dg:PagerPanel>`. Give it one setOnAction(Consumer<ScheduleAction>) and a setSelectionCount(...). The controller then only adds columns and wires the actions. Style the count in its own sheet, not paging-bar's.

**Verifier:** ExecutionSchedulesController.java:86 has `private final PagerPanel view`, :149 `dataGrid = new DataGrid<>()`, :159 view.setDataWidget(dataGrid), :90 `new Html()`, :165 addStyleName("paging-bar paging-bar__label"), and six view.getToolbar().button(...) calls at lines 205-222. NotificationList.ui.xml and DocumentList.ui.xml both declare PagerPanel + ToolbarButton + DataGrid in a template, so the fix works.

### explorer-documents-shell-05: Controllers render their screen's markup as SafeHtml with CSS classes, instead of the component rendering it

*medium, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEditController.java:132`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEditController.java:487`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEditController.java:542`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEditController.java:874`, `stroom-core-client/src/main/java/stroom/entity/client/component/InfoDocumentController.java:102`, `stroom-core-client/src/main/java/stroom/entity/client/component/InfoDocumentController.java:123`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:864`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:875`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:956`

**Evidence:** I grepped for classes set from Controller, Plugin and Manager files:
- AnnotationEditController defines 11 annotation-history* className Attributes (lines 132-142) and builds the whole history with HtmlBuilder (about 700 lines, 487-1340), then calls view.setHistory(html.toSafeHtml()).
- InfoDocumentController builds info-line divs and a TableBuilder table, then calls view.setInfo(...).
- DocumentPluginEventManager, a plugin manager, builds the delete confirmation's body with delete-confirm__summary-header, __type-count and __gap, plus doc-ref-link rows, and passes the non-BEM class "docRefLinkIcon" (line 956) that no sheet styles.
The guide puts markup in the component (repetition in the component's Java, setRules adds a row per rule); controllers hold none.

**Fix:** Move the rendering into dumb components:
- AnnotationHistory widget (annotation-history block, its sheet already exists) with setEntries(List<AnnotationEntryGroup>) and callbacks for its links and actions.
- InfoDocument.setInfo(DocRef, ExplorerNode, List<DocAuditEntry>) rendering the lines itself.
- DeleteConfirmation component (delete-confirm block) with setDetail(DeleteConfirmation), its fixed headers in a template.
Controllers pass data, not SafeHtml. Drop "docRefLinkIcon" (see -24).

**Verifier:** AnnotationEditController.java:132-142 defines annotation-history* className Attributes, :487 starts an HtmlBuilder, and :542 calls view.setHistory(html.toSafeHtml()); the file is 1632 lines long. InfoDocumentController.java:102-123 builds info-line divs and a TableBuilder table, then calls view.setInfo. DocumentPluginEventManager.java:864-875 builds delete-confirm__* divs, and :956 passes "docRefLinkIcon", which no CSS file contains (grep finds only the three Java callers).

### explorer-documents-shell-07: AssignYourselfLink hand-builds a button (a div with role=button and its own key handling) instead of using Stroom's LinkButton; SettingBlock writes its icon and ARIA in Java

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/AssignYourselfLink.ui.xml:4`, `stroom-core-client/src/main/java/stroom/annotation/client/AssignYourselfLink.java:32`, `stroom-core-client/src/main/java/stroom/annotation/client/SettingBlock.java:62`, `stroom-core-client/src/main/java/stroom/annotation/client/SettingBlock.java:64`, `stroom-core-client/src/main/java/stroom/annotation/client/SettingBlock.java:91`

**Evidence:** - AssignYourselfLink.ui.xml is `<div class="assign-yourself-link" role="button" tabindex="0" ...>Assign Yourself</div>`. The Java extends Widget, adds its own Click and KeyDown (Enter/Space) handlers, and disables itself with DisabledState. That re-implements LinkButton, which UI-GUIDE names as the link-style button. It is declared in AnnotationEdit.ui.xml and ChangeAssignedToDialog.ui.xml.
- SettingBlock sets `icon.getElement().setInnerHTML(SvgImage.ELLIPSES_VERTICAL.getSvg())` and, in setTitle, sets role, tabindex, aria-label and aria-haspopup by hand on a SimplePanel to turn it into a button.

**Fix:** - Replace AssignYourselfLink with `<b:LinkButton ui:field="assignYourself" text="Assign Yourself" title="..."/>` in both templates. The tooltip and disabled reason are set by the controller; if a disabled LinkButton cannot carry a title, add that to LinkButton.
- In SettingBlock, make the icon a `<b:InlineSvgButton>` or IconBox declared in SettingBlock.ui.xml, shown or hidden by whether the setting has a title, rather than an element that becomes a button through attributes.

**Verifier:** AssignYourselfLink.ui.xml:4 is a raw <div role="button" tabindex="0">, and AssignYourselfLink.java adds its own Click and KeyDown (Enter/Space) handlers and DisabledState. UI-GUIDE.md:33 names LinkButton (stroom.widget.button.client.LinkButton exists) as Stroom's link-style button. The click's stopPropagation, which keeps the setting's chooser closed, must be kept when it moves to LinkButton. SettingBlock.java:62-64 sets the icon's innerHTML from SvgImage and aria-hidden, and setTitle toggles role/tabindex/aria-label/aria-haspopup by hand. COMPONENT_MIGRATION_ORDER.md:1401 already records 'ARIA is set in Java in SettingBlock, AssignYourselfLink'.

### floormap-pathways-stores-13: DatabaseTablesMonitoringController has no component: it makes its DataGrid in Java and puts it into an injected PagerPanel

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoringController.java:64`, `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoringController.java:71`, `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoringController.java:75`

**Evidence:** The constructor takes `final PagerPanel view` and calls `dataGrid = new DataGrid<>(1000);` then `view.setDataWidget(dataGrid);`. It is the only screen in the slice without a template; compare ConstraintList.ui.xml, which declares `<dg:PagerPanel><dg:DataGrid .../></dg:PagerPanel>`.

**Fix:** Add a DatabaseTables component: DatabaseTables.java plus DatabaseTables.ui.xml with `<dg:PagerPanel ui:field="pager"><dg:DataGrid ui:field="dataGrid" tableName="Database Tables" .../></dg:PagerPanel>`, its columns added in the component. Rename the controller DatabaseTablesController.

**Verifier:** DatabaseTablesMonitoringController.java:64 injects `final PagerPanel view`. :71 does `dataGrid = new DataGrid<>(1000)`, and :75 does `view.setDataWidget(dataGrid)`. The columns are added in the controller. There is no template.

### security-settings-01: Ask Stroom AI's controller builds each message's markup, raw <button> elements and toolbar-button classes included, instead of the component owning it

*medium, effort large, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:78`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:381`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:524-830`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:608`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:803-810`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:184`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:219`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:259`

**Evidence:** `private static final SafeHtml BUTTON = SafeHtmlUtils.fromSafeConstant("button")` (line 78) is passed to `hb.elem(..., BUTTON, ...)` at 608 (the copy button) and 808 (`button(...)`, the delete button). So raw <button> elements are made in an HtmlBuilder inside a controller. Every message's DOM (`ai-message`, `ai-message-header`, `ai-message-footer`, `ai-details-content`, `ai-attachment-status`, `ai-message-timestamp`, `ai-message-copy`, `ai-message-delete`, and <details>/<summary>) is built in the controller (appendMessageHtml, appendDetailsElement, appendAttachmentMessage, appendWorkingMessage, appendStatus, icon, button, timestamp). The component then pastes it in with setInnerHTML (AskStroomAi.java:184, 219) and patches parts by id (setPartHtml, 259). It also borrows `toolbar-button` / `toolbar-button__icon` (another block) through iconButtonClassName (790-792).

**Fix:** Chat messages are runtime rows, so rendering them per row (SafeHtmlTemplates, or one widget per message added in Java) is allowed. The defect is where the markup lives and what it uses. Move the message rendering out of AskStroomAiController into the AskStroomAi component, or into a dumb AiMessage component with Stroom ToolbarButtons for Copy and Delete and setOnCopy/setOnDelete. The controller then passes only data (addMessage, setAttachmentStatus). Drop the raw <button> and the borrowed toolbar-button/toolbar-button__icon classes.

**Verifier:** Verified: AskStroomAiController.java:78 `BUTTON = fromSafeConstant("button")` is used at 608 and 808. Lines 791, 799 and 807 add the `toolbar-button` and `toolbar-button__icon` classes. AskStroomAi.java:184 and 219 use setInnerHTML, and 259 is setPartHtml. The messages are runtime-supplied rows, though, and the rules allow children chosen at runtime and row rendering. So 'high' overstates it. The real problem is the markup sitting in the controller, plus the raw button.

### security-settings-02: Five screens wrap a single list in a component that holds nothing but an empty slot

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeys.java:44`, `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeys.ui.xml:5`, `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeysController.java:80`, `stroom-core-client/src/main/java/stroom/security/client/component/Users.java:44`, `stroom-core-client/src/main/java/stroom/security/client/component/Users.ui.xml:6`, `stroom-core-client/src/main/java/stroom/security/client/component/UsersController.java:69`, `stroom-core-client/src/main/java/stroom/security/client/component/SigningKeys.java:45`, `stroom-core-client/src/main/java/stroom/security/client/component/SigningKeys.ui.xml:7`, `stroom-core-client/src/main/java/stroom/security/client/component/SigningKeysController.java:73`, `stroom-core-client/src/main/java/stroom/credentials/client/component/Credentials.java:44`, `stroom-core-client/src/main/java/stroom/credentials/client/component/Credentials.ui.xml:4`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsController.java:44`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Accounts.java:43`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Accounts.ui.xml:5`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/AccountsController.java:50`

**Evidence:** Each template is only `<g:SimplePanel ui:field="listContainer|userList|credentialsList"/>`, at most inside one FlowPanel. Each component's only method is `setList(IsWidget)` (or `setUserList`/`setCredentialsListView`), which calls `setWidget`. Each controller always passes it the same child: ApiKeysListController, UserListController, SigningKeyListController, CredentialsListController or AccountsListController. UI-GUIDE: "A screen that is one smart part and nothing else ... shows that part's widget as its own, rather than wrap it in a component with nothing in it". Accounts.setList also takes `Widget` rather than `IsWidget`, unlike the others.

**Fix:** Delete ApiKeys, Users, SigningKeys, Credentials and Accounts (Java and .ui.xml), and have each controller show its list's widget as its own (`initWidget(apiKeysList)` etc.). If a wrapper is kept for padding, declare the list's dumb component in it (`<sec:ApiKeysList ui:field="list"/>`) and attach the list controller to it.

**Verifier:** Each template is only a SimplePanel (ApiKeys and Accounts wrap it in one FlowPanel). The only public method is setList, setUserList or setCredentialsListView, which calls setWidget. Accounts.setList takes Widget (line 43). SigningKeys has a comment but no content.

### security-settings-04: CreateUser declares two of its three forms in its template but inserts the group-name form from Java

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/CreateUser.java:66-69`, `stroom-core-client/src/main/java/stroom/security/client/component/CreateUser.ui.xml:16-19`, `stroom-core-client/src/main/java/stroom/security/client/component/CreateUserController.java:62-64`

**Evidence:** CreateUser.ui.xml declares `<sec:CreateExternalUser ui:field="externalUser"/>` and `<sec:CreateMultipleUsers ui:field="multipleUsers"/>`, and the controller attaches to them: `make(externalUserProvider).attach(view.getExternalUser())`. The third form, CreateNewGroupController (a NameEntry), always goes in through `view.setGroupForm(group)`, which runs `panel.insert(groupForm, 0)`. The template's comment says "the group's name is added by its controller".

**Fix:** Declare `<…:NameEntry ui:field="groupForm" visible="false"/>` in CreateUser.ui.xml beside the other two. Give CreateNewGroupController an attach(NameEntry) form so it attaches the same way (`group = make(groupProvider).attach(view.getGroupForm())`), then remove setGroupForm.

**Verifier:** CreateUser.java:66-69 setGroupForm inserts the form with panel.insert(groupForm, 0). CreateUser.ui.xml:16-19 declares the other two, with the comment 'the group's name is added by its controller'. CreateUserController:62 calls view.setGroupForm(group), while 63-64 use attach. CreateNewGroupController's view is NameEntry. It is also used on its own by UserAndGroupActions, so it needs both the injected form and an attach form.

### security-settings-05: User Preferences, Ask Stroom AI settings and Credential Edit copy LinkTabPanel's markup and tab bookkeeping instead of declaring LinkTabPanel or using TabbedController

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/preferences/client/component/UserPreferencesDialog.ui.xml:8-11`, `stroom-core-client/src/main/java/stroom/preferences/client/component/UserPreferencesDialogController.java:101-104`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiConfigDialog.ui.xml:8-11`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiConfigDialogController.java:79-80`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialEdit.ui.xml:7-8`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialEditController.java:56`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialEditController.java:136-150`

**Evidence:** UserPreferencesDialog.ui.xml and AskStroomAiConfigDialog.ui.xml both contain `<g:FlowPanel styleName="dock-min link-tab-panel"><tab:LinkTabBar ... addStyleNames="link-tab-panel__middle"/>` and `<tab:LayerContainerImpl>`, which is LinkTabPanel.ui.xml's markup (stroom/entity/client/component/LinkTabPanel.ui.xml) with LinkTabPanel's BEM classes. CredentialEdit.ui.xml has its own LinkTabBar and LayerContainerImpl, and CredentialEditController keeps a `Map<TabData, Layer> tabViewMap` with addTab and changeSelectedTab, which is what TabbedController already does (RuleSetEditor and UserTabs use it).

**Fix:** Make the tab part of each of these templates a declared `<e:LinkTabPanel ui:field="tabs"/>` (with the dialog's own buttons beside it). Better, make CredentialEditController (and the two settings dialogs, if their tabs fit) a TabbedController over a LinkTabPanel, so the tab map and selection code goes. This also stops two sheets' worth of components setting link-tab-panel's classes.

**Verifier:** UserPreferencesDialog.ui.xml and AskStroomAiConfigDialog.ui.xml both repeat LinkTabPanel.ui.xml's `dock-min link-tab-panel` / LinkTabBar `link-tab-panel__middle` / LayerContainerImpl markup. CredentialEditController:56 has a tabViewMap, with addTab and changeSelectedTab at 136-150. LinkTabPanel is a Component with a no-arg constructor (LinkTabPanel.java:62), so it can be declared in a template.

### security-settings-06: Fixed icons are drawn with innerHTML in Java (though stroom.svg.client.Icon exists), the length badge is a SimplePanel made in Java, and ARIA/autocomplete attributes are set by hand

*medium, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.java:76-81`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.ui.xml:17`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/AuthenticationError.java:47-48`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/AuthenticationError.ui.xml:12`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.java:68`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.java:82-84`, `stroom-core-client/src/main/java/stroom/alert/client/component/CommonAlert.java:63-65`, `stroom-core-client/src/main/java/stroom/alert/client/component/CommonAlert.java:139`

**Evidence:** Login runs `userImage.getElement().setInnerHTML(SvgImage.INFINITY_LOGO.getSvg()); userImage.addStyleName(SvgImage.INFINITY_LOGO.getClassName());` and `userName.getElement().setAttribute("autocomplete", "username")`. AuthenticationError does the same with SvgImage.ERROR. ChangePassword makes `new SimplePanel()` for the length badge, then sets its class string and `aria-hidden` by hand. CommonAlert sets `aria-controls` and `aria-expanded` on its LinkButton with setAttribute. Widget rule 5 says attributes go through typed setters, and rule 6 says markup belongs in the template. `grep 'public void setAria(Expanded|Controls)'` in the widget module finds them only on TextBox and AriaPanel, not on LinkButton, and nothing in the widget module offers `autocomplete`.

**Fix:** Declare the logo and the error image with the existing stroom.svg.client.Icon widget in Login.ui.xml and AuthenticationError.ui.xml (image="INFINITY_LOGO" / "ERROR" through setImage), and remove the setInnerHTML code. Add autocomplete setters to TextBox and PasswordBox, and ariaExpanded and ariaControls setters to LinkButton. Declare ChangePassword's badge in the template.

**Verifier:** Verified: Login.java:76-77 and AuthenticationError.java:47-48 use setInnerHTML with the SVG. Login.java:80-81 sets autocomplete through setAttribute. ChangePassword.java:68 has `new SimplePanel()`, and 82-84 set its class and aria-hidden. CommonAlert.java:65 and 139 call setAttribute for aria-controls and aria-expanded. But a template-usable icon widget already exists (stroom-core-client-widget/.../svg/client/Icon.java:34, setImage(SvgImage) at line 47), so the fix is to use it, not to add a new one.

### widgets-1-16: SelectionList makes its DataGrid in Java and puts it in its PagerPanel, which can declare it, inside an extra wrapper

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:109`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:142`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.ui.xml:16`

**Evidence:** SelectionList.java:109 'cellTable = new DataGrid<>(100); cellTable.setProfile(DataGridProfile.LISTBOX); cellTable.hasContextMenu(false)' and :142 'pagerView.setDataWidget(cellTable)'. The child is always the same grid. PagerPanel takes declared children (its doc: '<dg:PagerPanel ...><dg:DataGrid ui:field="dataGrid" .../></dg:PagerPanel>'), and DataGrid has pageSize/profile/hasContextMenu template attributes (FindResultLists' doc; 'hasContextMenu=' is used in 3 templates). The template wraps the PagerPanel in '<g:SimplePanel styleName="dock-max selection-list__options">'. The field names cellTable and pagerView are also leftovers.

**Fix:** In SelectionList.ui.xml: '<dg:PagerPanel ui:field="pager" addStyleNames="dock-max selection-list__options"><dg:DataGrid ui:field="grid" pageSize="100" profile="LISTBOX" hasContextMenu="false"/></dg:PagerPanel>'. Remove the new DataGrid and setDataWidget from Java, and rename the fields grid and pager.

**Verifier:** SelectionList.java:109-112 makes 'new DataGrid<>(100)', calls setProfile(LISTBOX) and hasContextMenu(false), and :142 calls pagerView.setDataWidget(cellTable): a fixed child. Templates already declare DataGrid with pageSize="100" profile="LISTBOX" (e.g. ColumnValuesFilter.ui.xml:20), and DataGrid has setPageSize (:1347) and setHasContextMenu (:958), so the attributes work. PagerPanel accepts declared children (its doc). The template wraps PagerPanel in a SimplePanel carrying 'dock-max selection-list__options'.

### widgets-1-18: HeadedPagerPanel copies PagerPanel (template, HasWidgets, task monitor) only to wrap it in a FormGroup

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadedPagerPanel.java:49`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadedPagerPanel.ui.xml:1`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.java:53`, `stroom-core-client/src/main/java/stroom/job/client/component/JobNodeList.ui.xml:6`, `stroom-core-client/src/main/java/stroom/node/client/component/NodeJobList.ui.xml:6`

**Evidence:** HeadedPagerPanel.java repeats PagerPanel's add/clear/iterator/remove, setDataWidget, getRefreshButton, setPagerVisible and the createTaskMonitor counter almost line for line. Its template is PagerPanel's bar plus list inside '<form:FormGroup ui:field="pagerFormGroup">', with setHeading -> pagerFormGroup.setLabel. Users: JobNodeList and NodeJobList only (setHeading from their controllers).

**Fix:** Delete HeadedPagerPanel. JobNodeList.ui.xml and NodeJobList.ui.xml declare '<form:FormGroup ui:field="heading" addStyleNames="max"><dg:PagerPanel toolbarName="...">...</dg:PagerPanel></form:FormGroup>' and their setHeading sets the FormGroup's label. If the toolbar must fill the bar there, add a PagerPanel modifier.

**Verifier:** Diffing HeadedPagerPanel.java and PagerPanel.java shows the same structure (HasWidgets, setDataWidget, toolbar, task monitor), differing in the FormGroup wrapper, the SimplePanel list container and the missing message panel. HeadedPagerPanel.ui.xml is PagerPanel's bar inside '<form:FormGroup ui:field="pagerFormGroup">'. Its only users are JobNodeList and NodeJobList. Its doc notes that a form group 'in every list's panel broke them', which is why the template-level FormGroup wrapping (not a FormGroup inside PagerPanel) suggested here is the right shape.

### widgets-1-19: Four near-identical dialog button frames (OkCancelContent, CloseContent, AcceptRejectContent, CreateOkCancelContent) and two near-identical dialog templates (Dialog, ResizableDialog)

*medium, effort large, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/OkCancelContent.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/CloseContent.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/AcceptRejectContent.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/CreateOkCancelContent.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/Dialog.ui.xml:7`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/ResizableDialog.ui.xml:9`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/Dialog.java:52`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/ResizableDialog.java:55`

**Evidence:** The four *Content templates are each 'FlowPanel max dock-container-vertical > SimplePanel content.dialog__content + FlowPanel.dialog__buttons' and differ only in which Buttons they declare. Each Java class extends Composite (not Component) and implements DialogButtons. CreateOkCancelContent's only user is PopupSupportImpl (fan-in 1). Dialog.ui.xml's 25 lines appear in ResizableDialog.ui.xml unchanged, plus 8 resize handles. Dialog.java (320 lines) and ResizableDialog.java (547) duplicate the drag/move handling (both setPopupPosition paths, the dialog__hint and the window options button). The names say 'Content' for what is the dialog's button bar and frame.

**Fix:** Make one DialogFrame component (Component, template with the content slot, which is runtime and allowed, and the buttons row) that shows the buttons for its PopupType (Create/OK/Cancel/Close/Accept/Reject), replacing the four classes. Make ResizableDialog a Dialog with resize handles (one template with the handles, hidden by the dialog--resize-* modifiers that dialog.css already reads at :211-213), keeping one copy of the move logic.

**Verifier:** OkCancelContent, CloseContent, AcceptRejectContent and CreateOkCancelContent .ui.xml are each 'FlowPanel max dock-container-vertical > SimplePanel content dialog__content + FlowPanel dialog__buttons' and differ only in their buttons (CreateOkCancel uses --start and a button group). diff Dialog.ui.xml ResizableDialog.ui.xml differs only in the comment and the 8 resize handles. dialog.css:209-215 already hides the handles by the --resize-width/--resize-height modifiers. Dialog.java is 320 lines and ResizableDialog.java 547.

### widgets-2-29: DropDownBox builds its problem feedback in Java and inserts it into its parent's DOM

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:161`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:166`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:177`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml:11`

**Evidence:** When the box is not in a FormGroup, showProblem() builds the feedback with DOM.createDiv and two DOM.createSpan calls, setting class drop-down__feedback, sr-only, ids, aria-hidden and title (161-174). It then does `parent.insertAfter(problemFeedback, pickerElement)` (177), outside the component's own root. That is markup in Java and a sibling the component does not own. ValueSpinner shows that its own feedback can be declared in its template (ValueSpinner.ui.xml: `<tx:Label ui:field="feedback" styleName="value-spinner__feedback"/>`).

**Fix:** Make the template's root a wrapper (FlowPanel drop-down-box) holding the AriaPanel button and a declared `<tx:Label ui:field="feedback" styleName="drop-down-box__feedback" visible="false"/>` plus an sr-only span. showProblem then sets their text and visibility. It still defers to FormGroup.setFeedback when in a group.

**Verifier:** DropDownBox.java:161-176 builds the feedback with DOM.createDiv and createSpan, then calls parent.insertAfter(problemFeedback, pickerElement), outside its own root. The template root is the AriaPanel button. ValueSpinner.ui.xml declares its feedback label in the template.

### workbench-11: The header filter story uses a raw <input> look-alike cell in place of Stroom's FilterCell or TextFieldCell

*medium, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/datagrid/ColumnInteractionGrids.java:235`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/datagrid/ColumnInteractionGrids.java:525`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/datagrid/ColumnInteractionGrids.java:535`

**Evidence:** `LabelledTextInputCell extends com.google.gwt.cell.client.TextInputCell` and renders `sb.appendHtmlConstant("<input type=\"text\" tabindex=\"-1\" aria-label=...")`. Its javadoc says it is there 'as the dashboard's FilterCell'. UI-GUIDE forbids raw `<input>` in a cell's markup and requires Stroom's cell (TextFieldCell). WRITING-STORIES forbids look-alikes. Other grid stories already use `new TextFieldCell(...)` (DataGridKeyboardStories:394, InteractiveCellGrids:235).

**Fix:** Build the header with Stroom's `stroom.dashboard.client.table.FilterCell` (the cell the dashboard really uses), or with TextFieldCell if FilterCell needs too much dashboard context. Then delete LabelledTextInputCell and the TextInputCell import.

**Verifier:** ColumnInteractionGrids.java:525-540: LabelledTextInputCell extends GWT's TextInputCell and appends a raw '<input type="text" ...>'. UI-GUIDE.md:35 forbids raw <input> in a cell's markup and names TextFieldCell as the cell to use. TextFieldCell exists (stroom-core-client-widget), and so does FilterCell (stroom-core-client/.../dashboard/client/table/FilterCell.java). The workbench already imports stroom.dashboard.client classes, so the fix is feasible.

### data-pipeline-processing-17: Pipeline source dialog sets its editor's classes from the controller

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructureController.java:633`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructureController.java:638`

**Evidence:** `makeDialog(jsonEditorProvider).attach(new CodeEditor()); ... jsonEditor.asWidget().getElement().addClassName("form-control-border default-min-sizes");`. The dialog content has no component or template, its classes are set from the controller, and three commented-out option lines follow (635-637).

**Fix:** Do not add a wrapper component. Give the CodeEditor its classes through its own API (addStyleName, or a CodeEditor constructor/option for a bordered, minimum-sized editor), or let the dialog's popup size handle min sizes. Delete the commented-out lines 635-637 (also covered by finding 23).

**Verifier:** PipelineStructureController:633-638 is as described: `makeDialog(jsonEditorProvider).attach(new CodeEditor())`, three commented-out option lines, and addClassName("form-control-border default-min-sizes") from Java. But this is a dialog's content made when the user asks, and the brief allows popups and dialogs to be made at runtime. The content is a single CodeEditor (already a component), so a PipelineSource component holding only that editor would be the empty wrapper UI-GUIDE:27 forbids (the same rule finding 04 cites). The real defects are the classes set on the element from Java and the commented-out lines, which finding 23 already lists.

### data-pipeline-processing-18: PipelineTree makes its two PipelineTreePanels with new and puts them into DraggableTreePanel's raw div slot

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTree.java:52`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTree.java:53`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/DraggableTreePanel.ui.xml`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/DraggableTreePanel.java:380`

**Evidence:** `private final PipelineTreePanel treePanel = new PipelineTreePanel(); private final PipelineTreePanel subTreePanel = new PipelineTreePanel();` are always the same two children, passed to setTreePanels, which replaces `<div ui:field="treeSlot">`. The only element that really is added at runtime is the dragged copy (layoutPanel.add(subTreePanel)).

**Fix:** Give DraggableTreePanel a @UiChild (`<pv:tree>`) for the main tree and declare `<pv:DraggableTreePanel><pv:tree><pv:PipelineTreePanel ui:field="treePanel"/></pv:tree></pv:DraggableTreePanel>` in PipelineTree.ui.xml. The drag copy can stay made in Java, since it exists only while dragging.

**Verifier:** PipelineTree.java:52-53 makes `new PipelineTreePanel()` twice and passes both to layoutPanel.setTreePanels (line 65). DraggableTreePanel.java:144-154 does addAndReplaceElement(treePanel, treeSlot). Only the sub tree is added while dragging (line 380). The same pattern is repeated in query/client/component/ExpressionTree.java:70-82, which a fix should cover too. UiBinder can declare a raw generic widget with a @UiChild, so the suggestion is feasible.

### data-pipeline-processing-19: PipelinePathBar builds each chevron segment's markup with Document.create*Element

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/PipelinePathBar.java:385`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/PipelinePathBar.java:435`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/PipelinePathBar.java:695`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorInfoBuilder.java:63`, `stroom-core-client/src/main/java/stroom/processor/client/component/Processors.ui.xml`

**Evidence:** Each chevron segment is about 8 elements built with createDivElement/createSpanElement, with roles, tabindex, ARIA and BEM classes set by hand (segment, button, content, label, label-text, shape, caret, ellipsis). Having one per element is fine (repetition), but the markup of each one is in Java. ProcessorInfoBuilder.get returns SafeHtml built with TableBuilder/HtmlBuilder for the Processors Info pane (`<tx:Html ui:field="info"/>`).

**Fix:** Extract a PipelinePathSegment component, or a SafeHtmlTemplates segment template, that PipelinePathBar adds once per element. Leave ProcessorInfoBuilder as is: its rows depend on the data and it also feeds the info tooltip.

**Verifier:** PipelinePathBar builds its segments with Document.create*Element (8 create calls, e.g. line 385) and sets roles and classes by hand. A segment component or a SafeHtmlTemplates template is a fair low-priority improvement. The ProcessorInfoBuilder part does not hold up. Its rows depend on the row's type (processor vs filter, reprocess, analytic vs pipeline) and on which values are present, so this is data-supplied content. The same SafeHtml is also shown as the list's info tooltip (ProcessorListController:99 `infoTooltip.show(processorInfoBuilder.get(row), ...)`), so a fixed-field template component would not replace it.

### data-pipeline-processing-20: DataViewer's 'View Source' is a hand-made div with role=button where Stroom's LinkButton exists, with a redundant --enabled modifier

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.ui.xml:17`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.java:150`, `stroom-app/src/main/resources/ui/css/data-viewer.css:68`

**Evidence:** `<div ui:field="sourceLink" class="data-viewer__source-link-label" role="button" tabindex="0">View Source</div>`. Java toggles `data-viewer__source-link-label--enabled` and also calls DisabledState.set, which adds `disabled` and aria-disabled. The sheet styles both `--enabled` and `.disabled`. CharacterNavigator and ItemNavigator use `<btn:LinkButton>` for the same kind of link.

**Fix:** Use `<btn:LinkButton ui:field="sourceLink" text="View Source"/>` with setOnClick and setEnabled. Delete the --enabled modifier and its rules, and the .disabled rule if LinkButton styles its own disabled state.

**Verifier:** DataViewer.ui.xml:22 has a `<div ui:field="sourceLink" class="data-viewer__source-link-label" role="button" ...>`. DataViewer.java:150-154 toggles --enabled and calls DisabledState.set. data-viewer.css styles both --enabled and .disabled. ItemNavigator.ui.xml:10 uses btn:LinkButton, which has setOnClick (LinkButton.java:62). The line numbers are off by about 5 (ui.xml:22, not :17).

### explorer-documents-shell-04: Fixed toolbar buttons are added from Java to a templated PagerPanel or list (LinkedEvents, AnnotationTags, AnnotationLinks)

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEvents.java:57`, `stroom-core-client/src/main/java/stroom/annotation/client/component/LinkedEvents.java:60`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTagsController.java:76`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTagsController.java:81`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationLinksController.java:85`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationLinksController.java:89`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetsTab.java:51`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEditController.java:175`, `stroom-core-client/src/main/java/stroom/aws/s3/client/component/S3ConfigEditor.java:74`, `stroom-core-client/src/main/java/stroom/documentation/client/component/DocumentationEditor.java:69`, `stroom-core-client/src/main/java/stroom/kafka/client/component/KafkaConfigEditor.java:74`, `stroom-core-client/src/main/java/stroom/xmlschema/client/component/XMLSchemaEditor.java:96`

**Evidence:** I grepped for `getToolbar().button|toggle` and `new Toolbar(`.
- LinkedEvents is a dumb component whose template already has `<dg:PagerPanel toolbarName="Linked event actions">`, yet it adds Add and Remove with pager.getToolbar().button(...) in its constructor. DocumentList.ui.xml and NotificationList.ui.xml declare theirs as `<b:ToolbarButton>` children of PagerPanel.
- AnnotationTagsController adds New, Edit and Delete to the AnnotationTagList component's toolbar; AnnotationLinksController adds Add and Remove to FindAnnotationList's. In both, controllers create widgets.
- DocumentAssetsTab makes `new Toolbar("Asset actions")` plus 5 buttons in Java.
- MarkdownEditController, a controller, makes `new Toolbar("Documentation actions")` plus an Edit toggle and a Help button.
- Four DocumentEditor subclasses add a fixed Download or validation button to the document toolbar.

**Fix:** LinkedEvents: declare Add and Remove as <b:ToolbarButton> children of its PagerPanel and delete the stale 'a template can't add to' comment. AnnotationTagList and FindAnnotationList: declare the buttons in their templates, hidden by default, with setAvailableActions/setOnAction, so the controllers make no widgets. Leave the host-shown document toolbars (DocumentAssetsTab, MarkdownEdit, DocumentEditor subclasses) out of this finding: they are outside the tab's DOM, so a template cannot declare them.

**Verifier:** Confirmed for LinkedEvents: LinkedEvents.java:57-62 adds Add/Remove with pager.getToolbar().button(...) under the comment 'On the pager's own toolbar, which a template can't add to'. That comment is stale, because DocumentList.ui.xml and NotificationList.ui.xml declare <b:ToolbarButton> children of PagerPanel. Also confirmed for AnnotationTagsController:76-84 and AnnotationLinksController:85-92, where controllers add buttons to a list component's toolbar. Refuted for DocumentAssetsTab, MarkdownEditController and the four DocumentEditor subclasses. Their Toolbar is handed to the document host and shown outside the tab (DocumentAssetsTab doc: 'Its buttons go on the document's toolbar ([#getToolbar()]), outside the tab'; MarkdownEditController.getToolbar() builds it lazily for the host). UiBinder can only declare widgets inside the template's own root tree, so these toolbars cannot simply be declared in the tab's template.

### explorer-documents-shell-06: StreamingProcessing is a component with nothing in it but a slot for ProcessorsController

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/analytics/client/component/StreamingProcessing.ui.xml:5`, `stroom-core-client/src/main/java/stroom/analytics/client/component/StreamingProcessing.java:43`, `stroom-core-client/src/main/java/stroom/analytics/client/component/StreamingProcessingController.java:59`

**Evidence:** The whole template is `<g:SimplePanel ui:field="processors" styleName="max"/>`, and the controller always calls view.setProcessors(processors) with the injected ProcessorsController. UI-GUIDE says a screen that is one smart part and nothing else shows that part's widget as its own, rather than wrap it in a component with nothing in it.

**Fix:** Delete StreamingProcessing.java and StreamingProcessing.ui.xml. StreamingProcessingController calls initWidget(processors.asWidget()), as AnnotationTagsController does with its list.

**Verifier:** StreamingProcessing.ui.xml is only `<g:SimplePanel ui:field="processors" styleName="max"/>`, and StreamingProcessingController:55/59 does initWidget(view) then view.setProcessors(processors). UI-GUIDE.md:27 says a screen that is one smart part and nothing else shows that part's widget as its own. AnnotationTagsController:86 initWidget(annotationTagList.asWidget()) is the precedent.

### explorer-documents-shell-08: The same setting blocks, and the same filter-over-tree pickers, are written out more than once

*low, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEdit.ui.xml:52`, `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeStatusDialog.ui.xml:1`, `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeAssignedToDialog.ui.xml:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityTree.ui.xml:8`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopup.ui.xml:10`, `stroom-core-client/src/main/java/stroom/importexport/client/component/ExportConfigDialog.ui.xml:13`, `stroom-core-client/src/main/java/stroom/about/client/component/About.ui.xml:4`, `stroom-core-client/src/main/java/stroom/welcome/client/component/WelcomeTab.ui.xml:8`

**Evidence:** - Settings: ChangeStatusDialog.ui.xml repeats AnnotationEdit's statusBlock (SettingBlock > FormGroup annotationStatus > annotation-setting__heading > Label), and ChangeAssignedToDialog.ui.xml repeats its assignedToBlock including AssignYourselfLink. Same identity values and same classes.
- Filter over tree: EntityTree (QuickFilter over ScrollPane.control-box over ExplorerTree), ExplorerPopup (QuickFilter over GWT ScrollPanel.stroom-control over ExplorerTree) and ExportConfigDialog (QuickFilter plus a Filter toggle over ScrollPane.control-box over ExplorerTree) are three copies, each in a quick-filter-layout panel. ExplorerPopup's copy uses GWT's ScrollPanel and the stroom-control field class for its border, where the others use ScrollPane and control-box.
- Build info: About and WelcomeTab both show Build Version, Build Date, Up Date and Node Name, in two different markups.

**Fix:** - Make an AnnotationStatusSetting and an AnnotationAssignedToSetting component (or one parameterised AnnotationSetting), declared in AnnotationEdit and in both dialogs.
- Use one filtered-tree component (EntityTree, renamed; see -11) in ExplorerPopup and ExportConfigDialog, with an optional extra-toolbar slot for Export's Filter toggle. Switch ExplorerPopup to ScrollPane and control-box.
- Make a BuildInfo widget declared by both About and WelcomeTab.

**Verifier:** ChangeStatusDialog.ui.xml repeats AnnotationEdit.ui.xml's statusBlock structure (SettingBlock > FormGroup identity=annotationStatus > annotation-setting__heading--clickable > Label annotation-setting__value), and ChangeAssignedToDialog does the same for assignedTo with AssignYourselfLink. Only the helpText differs, which a parameter can carry. EntityTree.ui.xml (QuickFilter over ScrollPane.control-box over ExplorerTree) and ExplorerPopup.ui.xml (QuickFilter over GWT ScrollPanel.stroom-control over ExplorerTree) are near copies. About.ui.xml and WelcomeTab.ui.xml both list Build Version/Build Date/Up Date/Node Name in different markup; WelcomeTab's table also has user rows, so a BuildInfo widget would cover only part of it. Low severity is right.

### floormap-pathways-stores-14: ImportsTabController builds DocRefList's fixed column and Add/Delete buttons in Java; HasToolbar toolbars are built in controllers

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditorController.java:176`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditorController.java:262`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditorController.java:347`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMapController.java:319`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMapController.java:441`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesListTabController.java:58`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesListTabController.java:84`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/ImportsTabController.java:77`

**Evidence:** - FloorMapEditorController: `new Toolbar("Floor map editor actions")`, then `editorToolbar.toggle(...)` twice (grid, dock), and createHelpToolbar() does `new Toolbar(...)`, `toolbar.addStyleName("toolbar--end")` and adds a help button.
- FloorMapMapController: `new Toolbar("Floor map actions")` and three `mapToolbar.toggle(...)`.
- TracesListTabController: `new TimeRangeSelector()`, `new FlowPanel()`, `toolbar.addStyleName("traces-toolbar")` and `toolbar.add(timeRangeSelector)`.
- ImportsTabController: `importList.initTableColumns("Document Name", true)`, `toolbar.setName("Dictionary actions")` and `toolbar.button(SvgImage.ADD...)`/`(SvgImage.DELETE...)`.
All of these are fixed. Controllers set markup (class names, names and titles), which the guide puts in templates.

**Fix:** Declare DocRefList's Add/Delete ToolbarButtons and its fixed column in DocRefList.ui.xml/DocRefList, as ConstraintList does. For the HasToolbar toolbars (FloorMapEditor, FloorMapMap, TracesListTab), consider a small component of their own (e.g. a FloorMapEditorToolbar template) if they are to be declarative. They can't go into the tab's own template.

**Verifier:** The ImportsTab part holds: ImportsTabController.java:77-81 calls initTableColumns("Document Name", true), toolbar.setName and toolbar.button(ADD/DELETE) on DocRefList's pager toolbar, while ConstraintList.ui.xml:7-9 declares its ToolbarButtons. The FloorMapEditor, FloorMapMap and TracesListTab toolbars are different. They are HasToolbar contributions (FloorMapEditorController.java:128/175/188, TracesListTabController.java:57-58 comment) that DocumentEditor places in the document tab's toolbar row, outside the tab component's DOM (COMPONENT_MODEL_DESIGN:388, :961). A UiBinder template has a single root, so the tab's template can't declare them without putting them in the tab's own layout. Nothing in the code does this either: no component declares getToolbars or getDocumentButtons widgets.

### floormap-pathways-stores-16: Fixed parts made with new in a component at runtime: the canvas hover tooltip caption and the layer style preview

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.java:1066`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.java:1068`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayerStyle.java:223`

**Evidence:** - setHoverTooltip: `hoverTooltip.clear(); final Label captionLabel = new Label(caption); captionLabel.addStyleName(HOVER_TOOLTIP_CAPTION_CLASS)`. The caption always exists; only the lines repeat.
- setPreview: `previewPanel.setWidget(new Html(FloorMapSwatchHtml.mapPreview(...)))` makes a new Html on every change.

**Fix:** In FloorMapCanvas.ui.xml, declare `<tx:Label ui:field="hoverCaption" addStyleNames="floor-map-canvas__hover-caption"/>` and a `<g:FlowPanel ui:field="hoverLines"/>`. Set the caption's text and add only the lines. In FloorMapLayerStyle.ui.xml, declare `<tx:Html ui:field="preview"/>` in place of the SimplePanel and call preview.setHTML(...).

**Verifier:** FloorMapCanvas.java:1066-1069 calls hoverTooltip.clear(), then `new Label(caption)` with addStyleName, every time. FloorMapLayerStyle.java:223 does `previewPanel.setWidget(new Html(...))` on every change. A declared caption Label plus a lines panel, and a declared Html with setHTML, would work.

### security-settings-07: DescriptionBuilder, a permission-details HTML builder, sits in the security root package and builds a fixed panel's content as SafeHtml

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/DescriptionBuilder.java:24-57`, `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsTab.ui.xml:47`, `stroom-core-client/src/main/java/stroom/security/client/component/AppPermissionsEdit.ui.xml:31`

**Evidence:** DescriptionBuilder builds lines with `classNameBuilder.addClassName("permission-details__line--inherited|--bold|--delimiter")` and the result is shown in `<tx:Html ui:field="details" styleName="max permission-details"/>`. This is HTML assembled in Java for a fixed-shape panel that is neither a grid cell nor a row. COMPONENT_MODEL_DESIGN: "Templates, not HTML in Java ... SafeHtmlTemplates stay for grid cells and rows."

**Fix:** Move DescriptionBuilder next to its users (security/client/component, or a model class in security/client/model). Consider a dumb PermissionDetails component, declared in DocumentUserPermissionsTab.ui.xml and AppPermissionsEdit.ui.xml, that takes the lines as data. DescriptionBuilder still has to serve the list cells that use it (AppUserPermissionsList, DocumentCreatePermissionsListController).

**Verifier:** DescriptionBuilder.java builds SafeHtml lines with permission-details__line--* classes. It is used not only by the two details panels but also by AppUserPermissionsList and DocumentCreatePermissionsListController, which may be list or cell rendering where SafeHtml is allowed. The detail text is a variable, data-driven list of lines. So the composition point is weaker than stated, and part of the finding is about where the class lives.

### widgets-1-17: Tab bars always add their TabSelector from Java rather than declaring it

*low, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/AbstractTabBar.java:104`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/CurveTabBar.ui.xml:4`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LinkTabBar.ui.xml:5`

**Evidence:** AbstractTabBar's constructor: 'tabSelector = new TabSelector(); tabSelector.addStyleName(SELECTOR_HIDDEN); add(tabSelector, root);', made for every bar, so it is a fixed child. Both templates' comments say the selector 'is added after it when first needed', which is not what the code does. The templates are bare HTML roots (UiBinder<Element>), so they can't declare a widget.

**Fix:** Make the bars' templates g:HTMLPanel roots that declare '<tab:TabSelector ui:field="tabSelector" addStyleNames="tab-selector--hidden"/>' after the tab list, pass the field to AbstractTabBar, and fix the template comments.

**Verifier:** AbstractTabBar constructor (:103-106): 'tabSelector = new TabSelector(); tabSelector.addStyleName(SELECTOR_HIDDEN); add(tabSelector, root);' runs unconditionally, so the selector is a fixed child. CurveTabBar.ui.xml's comment says it 'is added after it when first needed', which is wrong. The templates are UiBinder<DivElement> element roots, so they can't declare widgets today. The fix needs the bars' root changed (AbstractTabBar extends ComplexPanel with setElement), hence medium effort.

### widgets-2-28: Fixed icons set from Java where the template could declare <svg:Icon>

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:84`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml:14`, `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/ValueSpinner.java:122`, `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/ValueSpinner.java:123`, `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerSmall.java:54`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/RefreshButton.java:71`

**Evidence:** DropDownBox.ui.xml:14 declares an empty `<g:SimplePanel ui:field="ellipsesBtnPanel">`, and DropDownBox.java:84 fills it with SvgImageUtil.setSvgAsInnerHtml(..., ELLIPSES_HORIZONTAL). ValueSpinner.java:122-123 puts ARROW_DOWN and ARROW_UP into the template's empty decrease and increase divs (stepArrow, setInnerHTML at 145). SpinnerSmall.java:54 does `inner.setInnerHTML(SvgImage.SPINNER_SPINNING.getSvg())`. RefreshButton.java:71-74 appends four SVGs built with createDivElement and setInnerHTML. Each icon is the same every time, and stroom.svg.client.Icon exists so a template can declare one (`<svg:Icon image="TAB_CLOSE"/>`, Icon.java Javadoc). TickBox's tick is shared with TickBoxCell, so it is excluded.

**Fix:** Declare the icons in the templates: `<svg:Icon image="ELLIPSES_HORIZONTAL" addStyleNames="drop-down__icon"/>`, `<svg:Icon image="ARROW_DOWN" addStyleNames="value-spinner__icon"/>` inside each step div, and `<svg:Icon image="SPINNER_SPINNING"/>` in the busy spinner's inner div. If an icon must carry its own BEM class on the svg element, give Icon a setter for the svg's class. RefreshButton is a single-element ButtonBase, so its four icons may stay in Java; if they stay, build them with one SafeHtmlTemplate rather than DOM calls.

**Verifier:** DropDownBox.java:84 fills the empty ellipsesBtnPanel with SvgImageUtil. ValueSpinner.stepArrow does setInnerHTML(icon.getSvg()) into the template's empty decrease and increase divs, then sets the class on the svg. RefreshButton appends four icons it creates with createDivElement. Icon.java is documented for declaring an icon in a template (`<svg:Icon image=...>`). The fix can work: Icon is a Widget with setImage. However, Icon wraps the svg in a div with svg-image classes, so the svg-level BEM classes (value-spinner__icon) need moving or a setter, as the finding itself notes.

### widgets-2-30: XSDConstraintDisplay and XSDDisplayBox build their fixed wrappers in Java

*low, effort medium, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:57`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDNodeLabel.java:34`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplayBox.java:77`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplay.java:83`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplay.java:89`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplay.java:175`

**Evidence:** XSDConstraintDisplay is `initWidget(new SimplePanel())`. Every setNode builds a padded SimplePanel (xsd-browser__padded) holding a FlowPanel with a heading Label ("Enumeration"/"Pattern", xsd-browser__restriction-type) and one Label per value (57-84). The heading and the box are fixed; only the value rows repeat. XSDNodeLabel wraps `new Label(title)` in a Composite (34). XSDDisplayBox has a template, but its content area (`new ScrollPanel()` with xsd-browser__box-content, then box.add, 77-80) is made in Java whenever there is content. XSDDisplay creates its parse-error Label (83), the root view's xsd-browser__mid-panel (89) and the two-column scaffold (column() and columns(), 175-192) in Java. The boxes and rows are schema-driven, which is allowed, but those wrappers are the same each time.

**Fix:** Give XSDConstraintDisplay a template (a padded div with a heading Label and a values FlowPanel), adding only the value rows in Java. Declare the xsd-browser__box-content ScrollPanel in XSDDisplayBox.ui.xml, hidden when there is no content. Leave XSDDisplay's root-or-node scaffold in Java, since it is chosen at runtime.

**Verifier:** Two parts hold. XSDConstraintDisplay is initWidget(new SimplePanel()) and builds a fixed wrapper (xsd-browser__padded plus the heading) in Java. XSDDisplayBox.java:77-80 builds the content ScrollPanel in Java. The XSDDisplay part does not hold: getRootDisplay (with mid-panel) and the column()/columns() scaffold are rebuilt on every selection and chosen between the root view and the node view, and they hold schema-driven boxes. That is a runtime choice between alternatives, which the rules allow. The parse-error Label is likewise conditional on the model. XSDNodeLabel wrapping a Label in a Composite is a naming or simplification point, not template composition.

### widgets-2-31: The code editor's overview tooltip content and overview-bar summary are made in Java

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorPopup.java:31`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorPopup.java:39`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:40`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:104`

**Evidence:** IndicatorPopup (a PopupPanel; the root may stay in Java) creates its one fixed child `content = new Html()` and calls setWidget(content) (31-39). HelpPopup shows the template form for a popup (`super.setWidget(BINDER.createAndBindUi(this))` with HelpPopup.ui.xml). RightBar is a Composite over `new SimplePanel()` (40). render() writes the summary container and summary div (fixed: always present while there are indicators) as strings, together with the data-driven markers (104-125).

**Fix:** Give IndicatorPopup a template (`<tx:Html ui:field="content"/>`), as HelpPopup has, or replace it with the existing FocusTooltip. Give RightBar a template declaring code-editor__summary-container and code-editor__summary (with its severity modifier set by setStyleName), and render only the markers into a declared markers element, using SafeHtmlTemplates (see widgets-2-27).

**Verifier:** IndicatorPopup.java:31-39 does `content = new Html(); setWidget(content)`. RightBar.java:40 initialises with a new SimplePanel, and render() (lines 104-125) writes the summary container and summary divs as strings. The summary is drawn only when there are indicators (maxSeverity != null), so a template would toggle its visibility.

### workbench-08: Stories copy fragments of Stroom templates (logo, sign-in card, activity button, password badge, tab bar row) instead of showing the real component

*low, effort medium, adjusted.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/display/LogoStories.java:70`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/display/InfinityLogoStories.java:61`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/layout/RoundedPanelStories.java:46`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/ActivityChooserStories.java:112`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/PasswordInputStories.java:83`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/FieldEndStories.java:99`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/tabs/CurveTabBarStories.java:314`

**Evidence:** WRITING-STORIES' first rule is to use Stroom's real widgets and components, not look-alikes. These stories copy a Stroom template's markup into Java with setStyleName:
- LogoStories.logo() builds Navigation.ui.xml:15 (`<b:Button ui:field="logo" styleName="navigation-logo" tabIndex="-2" ariaHidden="true"/>`) with setTabIndex and setAttribute("aria-hidden").
- InfinityLogoStories.fromArgs builds Login.ui.xml:16-17 (sign-in-page__user-image-container and sign-in-page__user-image).
- RoundedPanelStories builds Login.ui.xml:13's sign-in-page / sign-in-page__form-panel / 'max form-padding form sign-in-page__form', and adds inline `style=` in its Html.
- ActivityChooserStories:112-118 builds Navigation.ui.xml:44-45 (navigation__activity and 'navigation__activity-button dashboard-panel'). Its class comment still says the code is copied from 'NavigationController (activityButton, updateActivitySummary)'; that is stale, the markup is now in Navigation.ui.xml.
- PasswordInputStories:83 and FieldEndStories:99 build ChangePassword's 'password-length password-length--short' badge (ChangePassword.java:48 LENGTH_BLOCK).
- CurveTabBarStories:314-321 builds CurveTabLayout.ui.xml:7-12's tab bar row without the sidebar toggle.
The stories' own comments say there is 'no logo widget'. So the parts are anonymous markup inside larger templates, and a story can only copy them.

**Fix:** Have each story show the real component that declares the fragment (Navigation, Login, ChangePassword, CurveTabLayout), or delete the fragment story (RoundedPanel is not a Stroom widget). Extract a small widget (e.g. PasswordLengthBadge, which ChangePassword builds in Java) only where Stroom itself reuses the part. Fix ActivityChooserStories' stale comment, which names NavigationController.

**Verifier:** The copying is real. LogoStories.logo() rebuilds Navigation.ui.xml:15's `<b:Button ui:field="logo" styleName="navigation-logo" tabIndex="-2" ariaHidden="true"/>`. RoundedPanelStories rebuilds Login.ui.xml:13-17 with inline style= in its Html. ActivityChooserStories copies Navigation.ui.xml:44-45, and its comment still points to NavigationController. ChangePassword.java:48 has LENGTH_BLOCK. This breaks WRITING-STORIES' 'no look-alikes' rule. But the Stroom side is not a composition defect: those parts are already declared in their parents' templates, which is the composed form the plan wants. Making new widgets (Logo, InfinityLogo...) only so stories can show them is not required by the plan, and they would each be used once. The defect is in the stories, so lower the severity and refocus the suggestion.

## BEM CSS and stylesheets (46)

### floormap-pathways-stores-02: stroom-floormap.css is a 1,312-line sheet holding about 15 unrelated blocks

*high, effort medium, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/stroom-floormap.css:1`, `stroom-app/src/main/resources/ui/css/stroom-floormap.css:540`, `stroom-app/src/main/resources/ui/css/stroom-floormap.css:865`, `stroom-app/src/main/resources/ui/css/stroom-floormap.css:1032`, `stroom-app/src/main/resources/ui/css/stroom-floormap.css:1270`

**Evidence:** Taking every selector's block prefix, the sheet styles:
- the canvas: stroom-floormap-canvas-container/-canvas-focus/-hover-tooltip/-gesture-readout/-scale-bar/-empty-status/-area-draw-hint/-grid/-glyph-caption/-measure-label
- the timeline: stroom-floormap-timeline-* (about 25 names)
- the timeline settings popup: stroom-floormap-timeline-settings-*
- the layers panel: floormap-layer-row/-name/-eye/-lock/-grip/-swatch/-add/-toolbar/-empty and floormap-layers-list/-footer/-discover
- the layer style dialog: floormap-layer-style-*
- the group edit dialog: floormap-group-edit-*
- the set scale dialog: floormap-set-scale-*
- the query: floormap-query-columns-hint
- the parent tabs: floormap-timeline-strip
- context rules on other blocks: `.floormap-layer-style-dialog td:last-child > .floormap-icon-picker` (:1032), `.floormap-group-edit-member .tick-box` (:1270), `.floormap-layer-style-image .drop-down`
The guide says a sheet is named for its block and holds only that block's rules.

**Fix:** Split it into one sheet per component, each named for its block: floor-map-canvas.css, floor-map-timeline.css, floor-map-timeline-settings.css, floor-map-layers.css, floor-map-layer-style.css, floor-map-group-edit.css, floor-map-set-scale.css and floor-map-query.css. Move floormap-timeline-strip to floor-map-editor.css (or a modifier of floor-map-timeline). Import each from app.css, then delete stroom-floormap.css.

**Verifier:** stroom-floormap.css is 1312 lines. Grouping its selectors by prefix gives the canvas, timeline, layers, layer style, group edit, set scale, query columns and timeline strip families, plus the sr-only copy at :540.

### floormap-pathways-stores-03: Flat hyphenated class names stand in for BEM elements in the floor map, trace overview and content store

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeline.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimelineSettings.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.ui.xml:73`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapGroupEdit.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapSetScale.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayerStyle.ui.xml:53`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayers.java:317`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapQuery.ui.xml:1`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:562`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:717`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:766`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:871`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:902`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.ui.xml:1`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreContentPackDetails.ui.xml:7`

**Evidence:** These were collected from the slice's templates and string literals. Each element is named as a block of its own joined by hyphens, never `block__element`:
- stroom-floormap-timeline-settings-row/-label/-date-box/-show-all-row/-popup/-button
- stroom-floormap-timeline-play-button/-step-button/-speed-badge/-left-controls/-date-labels-row/-out-of-range--left
- floormap-group-edit-label/-summary/-scroll/-members/-filter/-fields/-empty/-member/-member-detail--absent
- floormap-set-scale-row/-fields/-distance
- floormap-layer-style-preview-row/-image/-hint/-row-dimmed
- floormap-layers-footer-label
- floormap-query-columns-hint
- TraceOverviewChart (HtmlBuilder `Attribute.className`): timeline-container, trace-body, timeline-header, timeline-controls, timeline-bar, timeline-span, time-dim-overlay, time-range-overlay, slider-label, time-slider-container, reset-range-btn, time-range-info, timeline-buttons, service-operations, grid-lines, section-header, operation-list, operation-item, operation-content, children, duration, span-bar, span-http, span-error, span-info-panel/-header/-title/-close/-body/-row/-attr/-key/-section/-resize, expand-icon, timeline-glass
- TraceOverviewChart.ui.xml: trace-overview-root, trace-pager-bar, trace-expand-controls
- ContentStoreContentPackDetails.ui.xml: contentstore-details-heading/-markdown/-list/-icon/-url/-vertical/-name and contentstore-buttons
TestStylesheets accepts them because each is lexically a valid block name. The TraceOverviewChart names are generic enough to clash with other blocks: children, duration, section-header, grid-lines, timeline-bar.

**Fix:** Rename each to block__element and block--modifier of its component's block, with every use (Java, templates and the stories' selectors) in one commit. For example:
- floor-map-timeline-settings__row
- floor-map-timeline__play-button
- floor-map-group-edit__member--absent
- floor-map-set-scale__distance
- trace-overview-chart__span-bar and trace-overview-chart__span-bar--error
- trace-overview-chart__span-info-header (or give the span info panel its own block, trace-span-info)
- content-store-content-pack-details__heading

**Verifier:** Templates use flat hyphenated names, e.g. FloorMapTimelineSettings.ui.xml:25-43 (stroom-floormap-timeline-settings-row/-label/-date-box), FloorMapGroupEdit.ui.xml:13-39, FloorMapSetScale.ui.xml:13-23, TraceOverviewChart.ui.xml:12-17 and ContentStoreContentPackDetails.ui.xml:7-28. TraceOverviewChart.java sets generic className values at :562-1263, including children, duration, section-header, grid-lines and span-info-*.

### dashboard-query-16: FlexLayout is styled as block 'dashboard' (plus a separate 'dashboard-layout' block), and DashboardBody sets 'dashboard--design-mode', another block's modifier, on its own root

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.ui.xml:6`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.ui.xml:8`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.java:1530`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardBody.java:50`, `stroom-app/src/main/resources/ui/css/dashboard.css:21`, `stroom-app/src/main/resources/ui/css/dashboard.css:47`, `stroom-app/src/main/resources/ui/css/dashboard.css:61`, `stroom-app/src/main/resources/ui/css/flex-layout.css`

**Evidence:** FlexLayout.ui.xml roots are dashboard__outer > dashboard__scroll > dashboard-layout > dashboard__design-surface, and FlexLayout.java:1530-1537 toggles dashboard__scroll--horizontal/--vertical. No element has class 'dashboard'. FlexLayout's other parts (splitter, glass, marker, tab, item) use flex-layout__* in flex-layout.css. DashboardBody's root is 'dashboard-body', but setDesignMode does root.setStyleName("dashboard--design-mode"), and dashboard.css:61 uses it to draw the grid on FlexLayout's design surface.

**Fix:** Rename to the flex-layout block and move the rules into flex-layout.css: flex-layout (root), flex-layout__scroll(--horizontal/--vertical), flex-layout__layout, flex-layout__design-surface. Give FlexLayout setDesignMode(boolean), which toggles flex-layout--design-mode on its own root, and have DashboardBody call layout.setDesignMode. Then delete dashboard.css.

**Verifier:** FlexLayout.ui.xml uses dashboard__outer, dashboard__scroll, dashboard-layout and dashboard__design-surface. FlexLayout.java:1529-1537 toggles dashboard__scroll--horizontal/--vertical. DashboardBody.java:50 sets 'dashboard--design-mode' on its own root, and dashboard.css:61 uses it to style FlexLayout's design surface.

### dashboard-query-17: ExpressionTree's block is the leftover 'expression-tree-view', while 'expression-tree' (the sheet's name) belongs to ExpressionTreePanel and styles nothing

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionTree.ui.xml:8`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionTree.java:323`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.ui.xml:10`, `stroom-app/src/main/resources/ui/css/expression-tree.css:24`, `stroom-app/src/main/resources/ui/css/expression-item.css:96`, `stroom-app/src/main/resources/ui/css/expression-item.css:100`, `stroom-core-client/src/test/java/stroom/widget/TestStylesheets.java:83`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/DataReceiptRulesScreenStories.java:95`

**Evidence:** expression-tree.css defines only .expression-tree-view, which the ExpressionTree component sets (addStyleNames="expression-tree-view", setStyleName("expression-tree-view--read-only")). The 'View' suffix is left over from the presenter era. ExpressionTreePanel's root 'expression-tree' and its __canvas/__items are unstyled HOOKS in TestStylesheets. expression-item.css:96-100 styles items through .expression-tree-view--read-only.

**Fix:** Make the component's block expression-tree (expression-tree--read-only) and the panel's block expression-tree-panel (__canvas, __items), and update the expression-item.css context rules and the two stories' '.expression-tree-view' selectors.

**Verifier:** expression-tree.css defines only .expression-tree-view. ExpressionTree.ui.xml:8 sets addStyleNames="expression-tree-view" and ExpressionTree.java:323 toggles expression-tree-view--read-only. ExpressionTreePanel.ui.xml:10 uses 'expression-tree', which is listed in TestStylesheets HOOKS (:83). expression-item.css:96/100 use .expression-tree-view--read-only. DataReceiptRulesScreenStories and DataRetentionScreenStories use the selector.

### dashboard-query-18: conditional-formatting.css holds about 25 flat 'cf-*' blocks; the editor's swatches and examples use them; runtime classes are camelCase 'cfDynamicN'

*medium, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/conditional-formatting.css:9`, `stroom-app/src/main/resources/ui/css/conditional-formatting.css:30`, `stroom-app/src/main/resources/ui/css/conditional-formatting.css:128`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/ConditionalFormattingSwatchUtil.java:32`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/CustomRowStyleEditor.ui.xml:39`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/CustomRowStyleEditor.ui.xml:77`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/CustomRowStyleEditor.java:124`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/ConditionalFormattingDynamicStyles.java:41`, `stroom-query/stroom-query-api/src/main/java/stroom/query/api/ConditionalFormattingStyle.java:28`

**Evidence:** The sheet's selectors are .cf-colour-swatch, .cf-red … .cf-blue-grey, .cf-text, .cf-bold, .cf-italic, .cf-light-example and .cf-dark-example, each a separate block, and none is named 'conditional-formatting'. The colour class names come from ConditionalFormattingStyle's cssClassName ('cf-red'). CustomRowStyleEditor's elements are cf-light-example/cf-dark-example from this sheet, not custom-row-style-editor__*. ConditionalFormattingDynamicStyles builds class names 'cfDynamic' + n, which are not BEM. Custom properties are named --cf-swatch__…, for a block that does not exist.

**Fix:** Use one block, conditional-formatting, with modifiers: conditional-formatting--red …, conditional-formatting--text/--bold/--italic, conditional-formatting__swatch(--custom), conditional-formatting--custom-N for the dynamic ones, and custom properties --conditional-formatting__swatch-…. Give CustomRowStyleEditor its own custom-row-style-editor__light-example/__dark-example. The cssClassName strings are in stroom-query-api, so change them there (they are not persisted).

**Verifier:** conditional-formatting.css selectors are flat .cf-* classes (cf-red ... cf-colour-swatch, cf-light-example, cf-dark-example), and none is 'conditional-formatting'. ConditionalFormattingDynamicStyles:41 builds "cfDynamic" + n. CustomRowStyleEditor.ui.xml:39/77 uses cf-light-example/cf-dark-example. The cssClassName strings are in stroom-query-api's ConditionalFormattingStyle as constructor arguments, not as the serialised value.

### dashboard-query-19: Table Filter sheets use flat, non-BEM names (table-filter-outer/-panel/-label, dashboard-table-filter-cell-text-disabled …) that match no component

*medium, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/table-filter.css:19`, `stroom-app/src/main/resources/ui/css/table-filter.css:29`, `stroom-app/src/main/resources/ui/css/table-filter.css:36`, `stroom-app/src/main/resources/ui/css/column-values-filter.css:40`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableFilterColumn.ui.xml:6`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRules.ui.xml:6`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableFilterInput.ui.xml:6`, `stroom-app/src/main/resources/ui/css/table-filter-cell.css:20`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCell.java:243`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCell.java:252`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCell.java:275`

**Evidence:** table-filter.css defines three blocks (table-filter-outer, table-filter-label, table-filter-panel), and none is 'table-filter'. TableFilterColumn's root is table-filter-panel and its label table-filter-label; MultiRules/TableFilterInput use table-filter-outer. FilterCell sets dashboard-table-filter-cell, -text, -text-disabled, -buttons, -disable-button and -disable-button-disabled, putting elements and states in hyphenated names. The last is unstyled and listed in TestStylesheets HOOKS. The sheet is table-filter-cell.css and the class is FilterCell.

**Fix:** Use table-filter-columns (root) for the TableFilterInput/MultiRules pair, table-filter-column and table-filter-column__label for TableFilterColumn, and filter-cell, filter-cell__text(--disabled), filter-cell__buttons, filter-cell__disable-button(--disabled) in filter-cell.css. Drop the HOOKS entry once the modifier is styled or removed.

**Verifier:** table-filter.css defines .table-filter-label, .table-filter-outer and .table-filter-panel, three flat blocks. FilterCell.java:243-275 sets dashboard-table-filter-cell, -text, -text-disabled, -buttons, -disable-button and -disable-button-disabled. TableFilterColumn.ui.xml:6 uses table-filter-panel.

### dashboard-query-21: Fixed styles set inline on result table cells (white-space, bold) through SafeStyles

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/TableController.java:841`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableController.java:777`, `stroom-core-client/src/main/java/stroom/query/client/TableRow.java:100`

**Evidence:** Both controllers build new SafeStylesBuilder() per cell, with whiteSpace(NORMAL) when the column wraps and fontWeight(BOLD) when the row is grouped. TableRow passes cell.getStyles() to divWithClassStyleAndTitle, which renders it as an inline style attribute. These are fixed values, not runtime ones.

**Fix:** Use classes: result-table__cell--wrap and result-table__cell--grouped in result-table.css, added to the cell's class list in TableRow, and drop the SafeStyles field from TableRow.Cell.

**Verifier:** TableController:841+ and QueryResultTableController:777+ build a SafeStylesBuilder per cell with whiteSpace(NORMAL) and fontWeight(BOLD), both fixed values. TableRow.java:102 passes cell.getStyles() into the rendered markup as an inline style.

### dashboard-query-22: Results components share or borrow other blocks: result-table + stroom-dashboard-table on three roots, stroom-dashboard.css holds two unrelated blocks, VisToolbarRow borrows pager/pager-toolbar, QueryDataPane borrows query-buttons__button and result-table__grid

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/Table.ui.xml:6`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTable.ui.xml:5`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/EmbeddedQuery.ui.xml:5`, `stroom-app/src/main/resources/ui/css/stroom-dashboard.css:3`, `stroom-app/src/main/resources/ui/css/stroom-dashboard.css:23`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TextPane.ui.xml:8`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisToolbarRow.ui.xml:11`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisToolbarRow.ui.xml:12`, `stroom-app/src/main/resources/ui/css/result-table.css:22`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.ui.xml:15`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.ui.xml:28`

**Evidence:** Table, QueryResultTable and EmbeddedQuery all have root 'result-table max stroom-dashboard-table', so the classes appear twice when EmbeddedQuery holds a Table. stroom-dashboard.css styles .stroom-dashboard-table (with a bare '.cell' and td !important rules) and .stroom-dashboard-text (TextPane's root), two unrelated blocks in a sheet named for neither. VisToolbarRow's root is 'pager … vis-toolbar-row' with a 'pager-toolbar' child, the classes of PagerPanel's blocks, and result-table.css:22 '.result-table .pager' and vis-toolbar-row.css both pad it. QueryDataPane gives its own toolbar buttons query-buttons__button/--play/--stop (QueryButtons' elements) and its table slot result-table__grid (an element of the table it holds).

**Fix:** Move .stroom-dashboard-table's rules into result-table.css as result-table__grid rules and .stroom-dashboard-text's into a text-pane.css (block text-pane, TextPane's root). Remove the duplicate classes from EmbeddedQuery's slot. Give VisToolbarRow its own vis-toolbar-row__actions and padding instead of pager/pager-toolbar. Give QueryDataPane query-data-pane__button(--run/--stop) on its root block 'query-data-pane', and remove result-table__grid from its slot.

**Verifier:** Table.ui.xml:7, QueryResultTable.ui.xml:6 and EmbeddedQuery.ui.xml:5 all use 'result-table max stroom-dashboard-table'. stroom-dashboard.css defines only .stroom-dashboard-table and .stroom-dashboard-text (TextPane.ui.xml:9). VisToolbarRow.ui.xml:11-12 uses the pager and pager-toolbar classes. QueryDataPane.ui.xml:16-22 uses query-buttons__button(--play/--stop), and :28 puts result-table__grid on its slot.

### data-pipeline-processing-14: Components styled through other blocks' sheets: ProfilePeriodEditor uses credentials-details-dialog, the SteppingScreen stop button uses query-buttons, and step-location-link lives in stepping-screen.css

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/processor/client/component/ProfilePeriodEditor.ui.xml:9`, `stroom-app/src/main/resources/ui/css/credentials-details-dialog.css`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreen.ui.xml:30`, `stroom-app/src/main/resources/ui/css/query-buttons.css:33`, `stroom-app/src/main/resources/ui/css/stepping-screen.css:72`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/StepLocationLink.ui.xml`

**Evidence:** ProfilePeriodEditor's root is `styleName="credentials-details-dialog"`, and that sheet's comment says "also used by the processor profile's Edit Period". The rule is only a flex column with the form gap, which is what `.form` does. The Terminate button has `addStyleNames="query-buttons__button query-buttons__button--stop"`, which colours the svg red. stepping-screen.css holds the `.step-location-link` rules, which belong to the StepLocationLink component.

**Fix:** Give ProfilePeriodEditor `styleName="max form"` and remove the 'also used by' note from credentials-details-dialog.css. Either move the stop colour into a shared modifier (toolbar-button--stop in toolbar-button.css) used by both screens, or add stepping-screen__terminate in stepping-screen.css. Move the `.step-location-link` rules to their own step-location-link.css.

**Verifier:** ProfilePeriodEditor.ui.xml:9 has `<g:FlowPanel styleName="credentials-details-dialog">`. credentials-details-dialog.css says '(also used by the processor profile's Edit Period)', and its rule is the same flex column and gap as form.css's .form. SteppingScreen.ui.xml:30 uses query-buttons__button--stop (query-buttons.css:41). stepping-screen.css:70-85 holds .step-location-link. Small note: `styleName="form"` alone is enough; adding max also triggers layout-helpers' `.form.max:not(.form-padding)` widening.

### data-pipeline-processing-15: stroom-control (a field's border, padding and background) is used to border list panels where control-box is meant

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeGroupEdit.ui.xml:13`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeGroupEdit.ui.xml:13`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingFilterEditor.ui.xml:13`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingFilterEditor.ui.xml:38`, `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEdit.ui.xml:28`

**Evidence:** stroom-control.css describes `.stroom-control` as "A Stroom control's field (selection boxes, date-time boxes, drop downs)", with padding var(--input__padding--*). The volume group lists, the stepping element grid's ScrollPane and the XPathList use it as a panel. NodeGroupEdit does the same job with `<form:FormGroup label="Nodes">` wrapping `styleName="dock-max control-box"`. The volume group edits also have no FormGroup or label for their list.

**Fix:** Use control-box for these panels. Wrap the volume lists in a `<form:FormGroup label="Volumes" addStyleNames="dock-max">`, as NodeGroupEdit does.

**Verifier:** stroom-control.css:18-27 describes .stroom-control as a field (input padding). FsVolumeGroupEdit.ui.xml:13 and IndexVolumeGroupEdit.ui.xml:13 (`dock-max stroom-control` on a list SimplePanel with no FormGroup), and SteppingFilterEditor.ui.xml:13 and :38, use it as a panel. NodeGroupEdit.ui.xml:25-27 uses FormGroup with `dock-max control-box`, and form-controls.css:23 defines .control-box for exactly this.

### data-pipeline-processing-16: dashboard-panel (an unstyled, dashboard-named class) is used as a pane marker on non-dashboard screens, with redundant nesting

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/job/client/component/Jobs.ui.xml:5`, `stroom-core-client/src/main/java/stroom/job/client/component/Jobs.ui.xml:9`, `stroom-core-client/src/main/java/stroom/node/client/component/Nodes.ui.xml:10`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowser.ui.xml:9`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.ui.xml:7`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.ui.xml:8`, `stroom-core-client/src/main/java/stroom/processor/client/component/Processors.ui.xml:12`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructure.ui.xml:13`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingElement.ui.xml:15`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreenController.java:195`, `stroom-app/src/main/resources/ui/css/form-group.css:45`

**Evidence:** `grep '\.dashboard-panel\b' css/` finds one rule only, `.dashboard-panel > .form-group label...` in form-group.css, and it does not apply to these panes. The class is set on 27 elements across the slice. Jobs.ui.xml and Nodes.ui.xml wrap a `dashboard-panel` SimplePanel in another `max dashboard-panel form` FlowPanel. Every split-screen template wraps its SplitPane in an extra `<g:SimplePanel styleName="max">`. SteppingScreenController adds `dashboard-panel overflow-hidden` to a child's widget from Java.

**Fix:** Remove dashboard-panel from these templates and from SteppingScreenController:195, or replace it with one named, styled shared pane class (for example split-pane__pane in split-pane.css) if the panes need a look. Remove the redundant outer SimplePanels and the `form` FlowPanel in Jobs and Nodes. Set overflow-hidden in SourceViewer.ui.xml instead of from the stepping controller.

**Verifier:** The only .dashboard-panel rule is form-group.css:45 `.dashboard-panel > .form-group label...`, and none of these panes has a direct form-group child. The class is not in TestStylesheets' HOOKS. It passes only because that one selector names it. Jobs.ui.xml:5-12 shows an outer max SimplePanel and a `max dashboard-panel form` FlowPanel wrapping a `dashboard-panel` SimplePanel. SteppingScreenController:195 adds `dashboard-panel overflow-hidden` from Java. This is a repo-wide pattern (about 53 uses), not just this slice's.

### explorer-documents-shell-09: Main and FullScreen nest the same block class on two or three elements, and Main sets inline styles and a bare 'maximised' state class styled from dashboard-body.css

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml:8`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml:9`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml:16`, `stroom-core-client/src/main/java/stroom/core/client/component/FullScreen.ui.xml:6`, `stroom-core-client/src/main/java/stroom/core/client/component/FullScreen.ui.xml:7`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:216`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:221`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:250`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:356`, `stroom-app/src/main/resources/ui/css/dashboard-body.css:21`, `stroom-app/src/main/resources/ui/css/main-screen.css:27`

**Evidence:** - Main.ui.xml puts `.main-screen` (absolute, flex column, page background) on three nested elements: the SimplePanel root, the FlowPanel, and the FlowPanel `main`. FullScreen.ui.xml does the same with `.full-screen` on a SimplePanel and its FlowPanel. The outer wrappers add nothing but a second absolute box.
- Main.setBorderStyle calls `root.getElement().setPropertyString("style", style)`, an inline style from the theme's pageBorder.
- `innerContent.setSize("100%", "100%")` is also inline.
- Main adds and removes `centerWidget.getElement().addClassName("maximised")`, a bare state class whose only rule is `.maximised .dashboard-body` in dashboard-body.css.

**Fix:** - Keep one `.main-screen` element (the root) and one `.full-screen` element, and drop the redundant SimplePanel and FlowPanel layers.
- Apply the theme border through a custom property, e.g. `CssProperties.set(root, "--main-screen__border", ...)` used by `.main-screen { border: var(--main-screen__border, none) }`.
- Replace setSize with a class in main-screen.css.
- Replace `maximised` with a modifier on Main's own element (`main-screen--explorer-hidden`); dashboard-body.css may name it only as context (`.main-screen--explorer-hidden .dashboard-body`).

**Verifier:** Main.ui.xml:8, 9 and 16 all carry styleName="main-screen" on nested SimplePanel/FlowPanel/FlowPanel. FullScreen.ui.xml:6-7 has full-screen on a SimplePanel and its FlowPanel. Main.java:356 root.getElement().setPropertyString("style", style). Main.java:216/221 add and remove the bare 'maximised' class, whose only rule is dashboard-body.css:21 `.maximised .dashboard-body`. innerContent.setSize("100%","100%") is at Main.java:250. The theme border is a whole CSS declaration string, so moving it to a custom property means the theme must supply a value rather than declarations; that is still feasible.

### explorer-documents-shell-10: Navigation's elements are named as separate blocks (navigation-title-bar, navigation-logo, navigation-logo-image, navigation-toolbar, navigation-header-spinner) rather than BEM elements

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml:10`, `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml:15`, `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml:24`, `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml:36`, `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.java:96`, `stroom-app/src/main/resources/ui/css/navigation.css:66`, `stroom-app/src/main/resources/ui/css/navigation.css:78`, `stroom-app/src/main/resources/ui/css/navigation.css:88`, `stroom-app/src/main/resources/ui/css/navigation.css:94`

**Evidence:** navigation.css defines five blocks: navigation, navigation-logo, navigation-logo-image, navigation-title-bar and navigation-toolbar. Their rules are commented as Navigation's own parts ('The navigation's title bar ... (from components.css)'). navigation-header-spinner is unstyled and listed in TestStylesheets HOOKS. navigation-logo-image is passed to SvgImageUtil.toSafeHtml at Navigation.java:96. They pass the BEM regex only because each is a valid block name.

**Fix:** Rename everything that uses these classes (template, Java, navigation.css, HOOKS, stories) in one change: navigation__title-bar, navigation__logo, navigation__logo-image, navigation__toolbar, navigation__spinner. Rename the block itself too if the component is renamed (see -12).

**Verifier:** navigation.css:66/74 `.navigation-title-bar`, :78 `.navigation-logo`, :88 `.navigation-logo-image svg`, :94 `.navigation-toolbar` sit alongside the real `.navigation` / `.navigation__*` rules. Navigation.ui.xml:10/15/24/36 sets navigation-title-bar, navigation-logo, navigation-toolbar and navigation-header-spinner. Navigation.java:98 passes "navigation-logo-image". TestStylesheets.java:85 lists navigation-header-spinner in HOOKS. These are sub-parts of the navigation block named as separate blocks.

### explorer-documents-shell-28: Components styled through another component's block: DocumentAssetTree uses explorer-tree and explorer-cell__expander; FindInContent uses FindPanel's find-panel block, form-group's elements and a separate find-buttons block

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetTree.ui.xml:10`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetTree.ui.xml:11`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetTree.java:66`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.ui.xml:11`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.ui.xml:15`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.ui.xml:22`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.ui.xml:23`, `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.java:63`, `stroom-app/src/main/resources/ui/css/find-panel.css:35`, `stroom-app/src/main/resources/ui/css/find-buttons.css:19`

**Evidence:** - DocumentAssetTree.ui.xml's root is `max explorer-tree`, its grid `explorer-tree__grid` (the comment says 'as the explorer's tree'), and its Java sets `explorer-cell__expander`, all from explorer-tree.css.
- FindInContent's root, top, guidance and bottom use find-panel, find-panel--dialog, find-panel__top, find-panel__guidance and find-panel__bottom, which are FindPanel's block, and FindInContent.java sets find-panel__pattern--multiline. find-panel.css therefore holds rules (pattern height, guidance) that only FindInContent uses.
- FindInContent's labels reuse form-group__feedback and form-group__description outside any FormGroup, and its toolbar is styled by its own one-rule block find-buttons (find-buttons.css).

**Fix:** - DocumentAssetTree: either a shared tree pattern (a 'document-tree' class both declare, in its own sheet) or its own document-asset-tree block.
- FindInContent: its own find-in-content block in find-in-content.css (find-in-content__top, __pattern--multiline, __guidance, __buttons, __bottom), with find-buttons.css folded into it. Its pattern and guidance labels should go in a FormGroup (which gives feedback and description) rather than borrowing FormGroup's elements.

**Verifier:** DocumentAssetTree.ui.xml:10-11 uses `max explorer-tree` and `explorer-tree__grid`. DocumentAssetTree.java:66 uses explorer-cell__expander, :303 --explorer-cell__depth, :318-320 explorer-cell__icon-wrapper/__icon/__text, and :348 a template div.explorer-cell. FindInContent.ui.xml:11/12/21/28 uses find-panel, find-panel--dialog, find-panel__top, find-panel__guidance and find-panel__bottom. FindInContent.java:63 uses find-panel__pattern--multiline, which find-panel.css:38/44 styles only for it. FindInContent.ui.xml:22-23 uses form-group__feedback/__description outside a FormGroup, and :15 uses the one-rule find-buttons block (find-buttons.css:19).

### floormap-pathways-stores-05: pathways.css holds the trace overview, the span info panel, the traces list's row state and the Traces tab's toolbar

*medium, effort medium, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/pathways.css:283`, `stroom-app/src/main/resources/ui/css/pathways.css:288`, `stroom-app/src/main/resources/ui/css/pathways.css:512`, `stroom-app/src/main/resources/ui/css/pathways.css:525`, `stroom-app/src/main/resources/ui/css/traces-list.css:1`

**Evidence:** Blocks styled in pathways.css: trace-overview, trace-overview-root, trace-pager-bar, the timeline/time-slider/operation-* families, span-info-* (the span info panel), traces-toolbar (:512, set by TracesListTabController.java:84) and trace-row--error (:525, set by TracesList.java). Yet traces-list.css exists, styling only traces-list__operation. The sheet is named for the area, not for a block.

**Fix:** Move trace-row--error into traces-list.css as traces-list__row--error. Move traces-toolbar into traces-list-tab.css as traces-list-tab__toolbar. Rename what is left to trace-overview-chart.css, and either split the span info panel into its own block or make it trace-overview-chart__span-info-*.

**Verifier:** pathways.css has .operation-item.selected at :283, .span-info-panel at :288, .traces-toolbar at :512 and .trace-row--error at :525. TracesList.java:158 sets trace-row--error. traces-list.css has only .traces-list__operation.

### floormap-pathways-stores-06: Bare 'selected' state class on the trace overview's operation rows

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:902`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:916`, `stroom-app/src/main/resources/ui/css/pathways.css:283`

**Evidence:** `Attribute.className(isSelected(span) ? "operation-item selected" : "operation-item")` at :902 and :916. The rule is `.operation-item.selected {` at pathways.css:283. 'selected' is not the documented shared 'disabled'.

**Fix:** Use a BEM modifier, trace-overview-chart__operation--selected, in Java and CSS.

**Verifier:** TraceOverviewChart.java:902 and :916 have `isSelected(span) ? "operation-item selected" : "operation-item"`, and pathways.css:283 has `.operation-item.selected`.

### floormap-pathways-stores-08: FloorMapObjectEdit sets wide-control, which is only styled inside the credentials details dialog

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml:37`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml:49`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml:58`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml:86`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml:109`, `stroom-app/src/main/resources/ui/css/credentials-details-dialog.css:33`

**Evidence:** The only rule for this class anywhere is `.credentials-details-dialog .wide-control {` (credentials-details-dialog.css:33). FloorMapObjectEdit isn't in that dialog, so its five uses do nothing. floor-map-object-edit.css has no rule for them.

**Fix:** Remove wide-control from FloorMapObjectEdit.ui.xml. If the fields do need a width, add floor-map-object-edit__field to floor-map-object-edit.css.

**Verifier:** git grep wide-control finds one rule, `.credentials-details-dialog .wide-control` (credentials-details-dialog.css:33). FloorMapObjectEdit.ui.xml uses the class at :37, :49, :58, :86 and :109, outside that dialog.

### floormap-pathways-stores-09: gitrepo-settings-form.css styles by element and by id rather than by BEM classes

*medium, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/gitrepo-settings-form.css:20`, `stroom-app/src/main/resources/ui/css/gitrepo-settings-form.css:24`, `stroom-app/src/main/resources/ui/css/gitrepo-settings-form.css:28`, `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoSettings.ui.xml:62`

**Evidence:** The rules are `.gitrepo-settings-form button.button { width: 400px }`, `.gitrepo-settings-form input[type="text"] { width: 400px }` and `.gitrepo-settings-form #credentials-group { width: 400px }`. The last reaches the FormGroup's `identity="credentials-group"` id.

**Fix:** Add git-repo-settings__field (and __button if needed) on the template's TextBoxes, SelectionBox and Buttons. Style those in git-repo-settings.css and drop the element and id selectors.

**Verifier:** gitrepo-settings-form.css:19-29 has `button.button`, `input[type="text"]` and `#credentials-group` selectors. The FormGroup with identity="credentials-group" is at GitRepoSettings.ui.xml:70, not :62.

### floormap-pathways-stores-10: FloorMapDock copies LinkTabPanel's markup and sets link-tab-panel's classes

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapDock.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapDock.java:20`, `stroom-core-client/src/main/java/stroom/entity/client/component/LinkTabPanel.java:21`

**Evidence:** The template has `<g:FlowPanel styleName="dock-min link-tab-panel"><tab:LinkTabBar ... addStyleNames="link-tab-panel__middle"/></g:FlowPanel><tab:LayerContainerImpl .../>`. The class's javadoc says "Structurally the same as `LinkTabPanel`", and also wrongly calls it a "curve-tab bar". So the block link-tab-panel, styled in link-tab-panel.css, is set by a component that isn't LinkTabPanel. It also lacks LinkTabPanel's `tabBar.setTabPanel(layerContainer.getElement())`.

**Fix:** Declare `<ec:LinkTabPanel ui:field="tabs"/>` in the templates that hold the dock, or extract the tab bar plus layer container as a shared widget that both use, then delete FloorMapDock's copy. Failing that, give FloorMapDock its own block, floor-map-dock.

**Verifier:** FloorMapDock.ui.xml has `<g:FlowPanel styleName="dock-min link-tab-panel"><tab:LinkTabBar addStyleNames="link-tab-panel__middle"/>` and a LayerContainerImpl. Its javadoc says it is 'a curve-tab bar' and 'Structurally the same as LinkTabPanel'. LinkTabPanel.java:65 calls tabBar.setTabPanel(layerContainer.getElement()), which FloorMapDock lacks.

### security-settings-08: Ask Stroom AI uses bare state classes (empty, play, stop) and borrows Navigation's spinner class

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:60`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:305-313`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:341-343`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.ui.xml:9`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.ui.xml:38`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.ui.xml:41`, `stroom-app/src/main/resources/ui/css/ask-stroom-ai.css:92`, `stroom-app/src/main/resources/ui/css/ask-stroom-ai.css:100`, `stroom-app/src/main/resources/ui/css/ask-stroom-ai.css:178-205`

**Evidence:** `EMPTY_STYLE = "empty"` is toggled on the root, and `run.addStyleName("stop")` / `removeStyleName("play")` on the Send button. ask-stroom-ai.css styles `.ask-stroom-ai.empty ...` and `.ask-stroom-ai__model-pane .play svg` / `.stop svg`. The spinner has `addStyleNames="navigation-header-spinner"`, a class from explorer's Navigation.ui.xml:36 that no sheet styles (`grep navigation-header-spinner *.css` finds nothing; it is only in TestStylesheets' HOOKS).

**Fix:** Rename to modifiers: `ask-stroom-ai--empty`, and `ask-stroom-ai__run--play` / `ask-stroom-ai__run--stop` (or switch the button's svg and title and drop the classes). Give the spinner an `ask-stroom-ai__spinner` element class or none, and remove `navigation-header-spinner` from AskStroomAi.ui.xml.

**Verifier:** AskStroomAi.java:60 has EMPTY_STYLE="empty", and 305-313 add and remove "stop" and "play". ask-stroom-ai.css:92 and 100 style `.play svg` and `.stop svg`, and 178-205 style `.ask-stroom-ai.empty`. AskStroomAi.ui.xml:38 uses navigation-header-spinner, which no CSS file styles; it appears only in Navigation.ui.xml:36 and TestStylesheets HOOKS.

### security-settings-09: ask-stroom-ai.css holds several unrelated blocks (ai-message, ai-attachment-status, ai-details-content) whose elements aren't named BEM style

*medium, effort medium, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/ask-stroom-ai.css:118-290`, `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiController.java:609-814`

**Evidence:** Besides `.ask-stroom-ai*`, the sheet styles `.ai-message`, `.ai-message--user|assistant|error|working|data|thinking|debug-detail`, `.ai-message-header`, `.ai-message-footer`, `.ai-message-timestamp`, `.ai-message-copy`, `.ai-message-copy-label`, `.ai-message-delete`, `.ai-details-content`, `.ai-attachment-status`, `.ai-attachment-status-icon` and `.ai-attachment-status-text`. The parts of a message are hyphenated blocks rather than `ai-message__header`, `ai-message__footer` and so on. `.ai-attachment-status .icon-wrap` and `.ai-message-delete .toolbar-button__icon` style other blocks' elements.

**Fix:** Give the message its own block and sheet, `ai-message.css`, with `ai-message__header`, `__footer`, `__timestamp`, `__copy`, `__copy-label`, `__delete`, `__details`, and the attachment status as `ai-message__status` / `__status-icon` / `__status-text` (or a block `ai-attachment-status` in its own sheet). This goes with security-settings-01's AiMessage component.

**Verifier:** ask-stroom-ai.css:118-290 styles .ai-message, .ai-message-header, .ai-message-footer, .ai-message-timestamp, .ai-message-copy, .ai-message-delete, .ai-attachment-status (with `.icon-wrap` at 230) and `.ai-message-delete .toolbar-button__icon` (276, 280). These are hyphenated parts, not BEM elements.

### security-settings-10: Identity pages use bare generic classes feedback and password-policy, and change-password.css holds four unrelated blocks

*medium, effort medium, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/change-password.css:1-17`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.ui.xml:11`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.ui.xml:22`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.ui.xml:31`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.ui.xml:29`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.ui.xml:38`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/CurrentPassword.ui.xml:13`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/EmailResetPassword.ui.xml:16`

**Evidence:** change-password.css styles `.password-policy`, `.feedback`, `.password-strength*` and `.password-length*`, and no rule in it names a `change-password` block. `styleName="feedback"` is set on Labels in ChangePassword, Login, CurrentPassword and EmailResetPassword (and NameDialog in the widget module), all of them inside a FormGroup, which has its own `form-group__feedback` label. So the pages duplicate FormGroup's validation message instead of using it (`setInvalidReason` / FieldValidity).

**Fix:** Drop the `feedback` labels and show the messages through FormGroup's feedback (as FindInContent does with form-group__feedback), then remove `.feedback`. Rename `password-policy` to `change-password__policy`. Move `password-strength` and `password-length` into their own sheets (password-strength.css; password-length goes with PasswordBox's badge in password-box.css), leaving change-password.css with only the change-password block.

**Verifier:** change-password.css:1-17 styles .password-policy and .feedback, and no change-password block appears in it. styleName="feedback" is set in ChangePassword.ui.xml:22,31, Login.ui.xml:29,38, CurrentPassword.ui.xml:13, EmailResetPassword.ui.xml:16 and NameDialog.ui.xml:14. FormGroup has setFeedback (FormGroup.java:567,577).

### security-settings-11: The authentication error page is styled from sign-in-page.css and base.css, and Login's block is named sign-in-page

*medium, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/sign-in-page.css:1`, `stroom-app/src/main/resources/ui/css/sign-in-page.css:13-18`, `stroom-app/src/main/resources/ui/css/base.css:433`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.ui.xml:9`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/ResetPassword.ui.xml:5`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/SignInPageController.java:30-52`

**Evidence:** sign-in-page.css has `.sign-in-page, .authentication-error {position:absolute ...}` and `.authentication-error .authentication-error__form-panel {...}`. base.css:433 has `.authentication-error__message-container a`, although authentication-error.css exists. The Login component's root is `sign-in-page`, and ResetPassword's template is an empty `<g:FlowPanel styleName="sign-in-page"/>`. SignInPageController is the shared abstract base of LoginController, AuthenticationErrorController and ResetPasswordController, and there is no SignInPage component. It also repeats Core's `LOADING_HIDDEN = "loading--hidden"` and hide-loading code (core/client/component/Core.java:36,67).

**Fix:** Move every authentication-error rule into authentication-error.css. Rename Login to SignIn (or its block to `login`) so the block matches the component. Make the shared full-page backdrop a layout class (e.g. `sign-in-page` stays as the shared page block that Login, AuthenticationError and ResetPassword all use, documented as such, with Login's elements renamed `login__*`). Share the hide-loading helper with Core rather than copy it.

**Verifier:** sign-in-page.css:1 has `.sign-in-page, .authentication-error`, and 13-18 hold the form-panel rules for both. base.css:433 has `.authentication-error__message-container a`. Login.ui.xml:9 root is sign-in-page. SignInPageController.java:32 LOADING_HIDDEN duplicates Core.java:36.

### widgets-1-21: PagerPanel and HeadedPagerPanel have no block of their own: their parts are 'pager', 'pager-toolbar' and 'list-container', each a block in its own sheet

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml:11`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml:12`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml:13`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/PagerPanel.ui.xml:18`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadedPagerPanel.ui.xml:9`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadedPagerPanel.ui.xml:13`, `stroom-app/src/main/resources/ui/css/pager-toolbar.css:19`, `stroom-app/src/main/resources/ui/css/list-container.css:19`, `stroom-app/src/main/resources/ui/css/paging-bar.css:18`, `stroom-app/src/main/resources/ui/css/result-table.css:22`, `stroom-app/src/main/resources/ui/css/selection-popup.css:32`

**Evidence:** The templates set 'pager' (no rule of its own: only '.result-table .pager' styles it, and paging-bar.css:18 explains the Pager widget had to be named paging-bar 'as pager is the class of the container a PagerPanel puts it in'), 'pager-toolbar' (its own one-rule sheet) and 'list-container' (its own one-rule sheet, also targeted by selection-popup.css:32). The root has no class.

**Fix:** Give PagerPanel the block pager-panel in pager-panel.css: root pager-panel, pager-panel__bar (was pager), pager-panel__toolbar-widgets (was pager-toolbar), pager-panel__list (was list-container). Delete pager-toolbar.css and list-container.css, and update the result-table.css and selection-popup.css context rules.

**Verifier:** PagerPanel.ui.xml sets 'pager', 'pager-toolbar' and 'list-container', and the root has no class. pager-toolbar.css and list-container.css are each one-rule sheets. .pager is styled only as '.result-table .pager' (result-table.css:22). paging-bar.css's comment explains the name clash. selection-popup.css:32 targets '.selection-popup .list-container'.

### widgets-1-22: CurveTabLayout's template uses non-BEM classes (tab-bar-container, stroom-main-content-container) and has no block root

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.ui.xml:6`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.ui.xml:7`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.ui.xml:18`, `stroom-app/src/main/resources/ui/css/curve-tab-layout.css:38`, `stroom-app/src/main/resources/ui/css/curve-tab-layout.css:44`, `stroom-app/src/main/resources/ui/css/curve-tab-layout.css:52`

**Evidence:** curve-tab-layout.css defines .curve-tab-layout__layout (root element, with no .curve-tab-layout block), .stroom-main-content-container (comment: 'was stroom.css's'), .tab-bar-container and '.tab-bar-container > .toolbar'. tab-bar-container reads as part of the tab-bar block but is styled here.

**Fix:** Root class = the block (content-tab-pane, or curve-tab-layout if not renamed per widgets-1-01). Rename tab-bar-container to <block>__bar and stroom-main-content-container to <block>__content, and the existing __tab-bar-outer and __layer-container-outer to match.

**Verifier:** CurveTabLayout.ui.xml:6-18 uses curve-tab-layout__layout (the root has no bare block class), tab-bar-container and stroom-main-content-container. curve-tab-layout.css styles .stroom-main-content-container (:38, commented 'was stroom.css's'), .tab-bar-container (:44) and '.tab-bar-container > .toolbar' (:52). No other sheet uses those classes.

### widgets-1-23: NameDialog adds its own 'feedback' label inside its FormGroup, styled from change-password.css, rather than the form group's own feedback

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/component/NameDialog.ui.xml:14`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/component/NameDialog.java:63`, `stroom-app/src/main/resources/ui/css/change-password.css:9`

**Evidence:** NameDialog.ui.xml:14 '<tx:Label ui:field="feedback" styleName="feedback"/>' wrapped with the TextBox in an extra FlowPanel inside <form:FormGroup>. The bare, non-BEM '.feedback' rule is in change-password.css:9 (another block's sheet). FormGroup already has form-group__feedback and setInvalidReason/setFeedback (FormGroup.java:567-598), which UI-GUIDE names as where a field says why it is refused.

**Fix:** Declare '<form:FormGroup ui:field="nameGroup" ...><form:TextBox ui:field="textBox"/></form:FormGroup>' and have setInvalid/setValid call nameGroup.setInvalidReason(message)/clearFeedback(). Then move the .feedback rule out of change-password.css, or delete it if nothing else uses it.

**Verifier:** NameDialog.ui.xml:14 has '<tx:Label ui:field="feedback" styleName="feedback"/>' inside a FlowPanel inside FormGroup, and NameDialog.java:64/:70 uses FieldValidity.setInvalid/setValid with it. The only '.feedback' rule is change-password.css:9. FormGroup has setFeedback (:567), clearFeedback (:589) and setInvalidReason (:598). Note that the identity screens (ChangePassword, CurrentPassword, EmailResetPassword, Login) also use styleName="feedback", so the rule cannot simply be deleted. It belongs in that family's own block.

### widgets-1-24: Data grid header alignment and icon headers are non-BEM classes styled in data-grid.css; its sort icon uses the query's column-header block

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadingBuilder.java:78`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/HeadingBuilder.java:80`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:86`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:94`, `stroom-core-client/src/main/java/stroom/security/client/UserAndGroupHelper.java:72`, `stroom-app/src/main/resources/ui/css/data-grid.css:189`, `stroom-app/src/main/resources/ui/css/data-grid.css:193`, `stroom-app/src/main/resources/ui/css/data-grid.css:206`, `stroom-app/src/main/resources/ui/css/data-grid.css:349`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGridSortIcon.java:49`

**Evidence:** HeadingBuilder sets 'center-align' and 'right-align' on header cells (DataGridUtil too), and data-grid.css styles '.data-grid__header-cell.center-align/.right-align'. 'single-icon-column-header' and 'two-icon-column-header' (set by UserAndGroupHelper) are styled at data-grid.css:206-221. DataGridSortIcon emits 'column-header__sort' and 'column-header__sort-order'. The column-header block belongs to stroom-core-client's ColumnHeaderHtmlUtil/ColumnTitleCell (column-header.css), so the widget module's grid depends on a query block's classes.

**Fix:** Use modifiers of the grid's own element: data-grid__header-cell--align-center/--align-right and data-grid__icon-header (and --two). Have the grid's sort icon emit data-grid__sort and data-grid__sort-order (in data-grid.css), or move the shared sort icon's rules into a sort-icon block owned by SortIcon in the widget module.

**Verifier:** HeadingBuilder:78/:80 and DataGridUtil:86/:94 set bare 'center-align'/'right-align', which are styled only in data-grid.css (:189, :193, :349). UserAndGroupHelper:72/:91 set single-/two-icon-column-header, styled at data-grid.css:206-221. DataGridSortIcon emits column-header__sort/__sort-order, which are defined in column-header.css:52/:63, the query block's sheet.

### widgets-2-26: DropDownBox sets a bare 'readonly' state class, and stroom-control.css holds unrelated blocks

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:207`, `stroom-app/src/main/resources/ui/css/stroom-control.css:18`, `stroom-app/src/main/resources/ui/css/stroom-control.css:36`, `stroom-app/src/main/resources/ui/css/stroom-control.css:46`, `stroom-app/src/main/resources/ui/css/stroom-control.css:56`

**Evidence:** DropDownBox.java:207 has `setStyleName("readonly", isReadOnly() && enabled)`, the only Java that sets a bare readonly class. It is styled by stroom-control.css:36 `.stroom-control.readonly` and :41. Bare state classes should be BEM modifiers (only the shared disabled is documented), or ARIA, as DropDownBox already sets aria-disabled. stroom-control.css also holds .stroom .stroom-control-grid (a th/td layout table, lines 46-66) and .stroom .stroom-control-label, which belong to the settings-form layouts in BasicTableSettings.ui.xml and 12 other templates. It still says the rules come "from stroom.css", which was dissolved, and keeps a commented-out declaration at line 56.

**Fix:** Make DropDownBox set drop-down-box--read-only (or style [aria-disabled="true"]:not(...) in its own sheet), and make the shared one stroom-control--read-only if other controls need it. Move stroom-control-grid and stroom-control-label to their own sheet (e.g. control-grid.css with BEM names, dropping the .stroom prefix). Fix the stale comment.

**Verifier:** DropDownBox.java:207 is the only code that sets a bare readonly class (ValueBoxBase uses the readonly attribute). stroom-control.css styles .stroom-control.readonly, holds the unrelated .stroom .stroom-control-grid and .stroom-control-label rules (13 templates and Java files use them), and has the stale "from stroom.css" comment and the commented-out padding.

### widgets-2-27: Inline styles built in Java, outside CssProperties: the overview bar's markers and SafeHtmlUtil's colour and style helpers

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:188`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:206`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/SafeHtmlUtil.java:188`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/SafeHtmlUtil.java:200`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/SafeHtmlUtil.java:426`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/SafeHtmlUtil.java:441`

**Evidence:** RightBar.render builds raw HTML in a StringBuilder and sets it with setInnerHTML (206), writing `style="--code-editor__marker-top:Npx;"` per marker (188). It is a custom property, but set as a string, not through CssProperties or SafeHtmlTemplates. SafeHtmlUtil.getSafeHtml(String, boolean) writes inline `color: grey` for disabled text (188-193), and getColouredText writes `<span style="color:...">` (200-207). Its callers in stroom.util.client.DataGridUtil (103, 115, 128) colour grid cells this way, including the fixed LOW_LIGHT_COLOUR. The Template interface also offers divWithStyle (426), which nothing uses (grep: definition only), plus divWithClassStyleAndTitle and divWithClassAndStyle, used by query TableRow and ConditionalFormattingSwatchUtil.

**Fix:** Draw the markers with SafeHtmlTemplates and a CSS custom property (or CssProperties on created elements). Give the fixed greys a class, e.g. a shared text--low-light or a data-grid modifier, rather than inline colour. Keep user-supplied colours as --*__colour custom properties a class reads. Delete divWithStyle.

**Verifier:** RightBar.java:188-206 builds `style="--code-editor__marker-top:` with a StringBuilder and sets it with setInnerHTML. SafeHtmlUtil.getColouredText writes spanWithStyle with trustedColor, and getSafeHtml(String,false) passes "grey". DataGridUtil.java:103, 115 and 128 call getColouredText. divWithStyle (SafeHtmlUtil.java:427) is referenced only by its declaration.

### workbench-09: Stories style themselves with about 238 inline styles (83 files), including StoryPanels' shared row/column/note helpers and copies of control-box

*medium, effort medium, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/StoryPanels.java:39`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/StoryPanels.java:63`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/StoryPanels.java:71`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/ViewStateStories.java:137`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/ViewStateStories.java:348`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/datagrid/DataGridStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/datagrid/ColumnInteractionGrids.java:500`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dashboard/DashboardDocs.java:391`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/ScreenHarness.java:500`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/ScreenHarness.java:711`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/idp/IdpPage.java:110`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/display/LogoStories.java:84`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/layout/RoundedPanelStories.java:53`

**Evidence:** `grep -rnE 'getStyle\(\)\.set|setAttribute\("style"|style="' stroom-gwt-workbench/src/main/java` returns 238 matches in 83 files. The biggest are ViewStateStories (29), DataGridStories (12), EditorWidgets (8), ScreenHarness (8), VisualisationAssetsStories (8) and MarkdownEditorStories (7). StoryPanels.row/column/note, used about 90 times (73 row/column calls and 19 notes), set display:flex, gap, color and font-size inline. ViewStateStories:355-357 rebuilds `control-box` (a 1px var(--control__border-color) border with radius .25rem) inline, although that class exists. Each group also has its own frame and sizing helpers with the same inline width, height and position: ColumnInteractionGrids.sized, DashboardDocs.sized, ViewStateStories.gridFrame and frame, InputWidgets.maxWidth. No workbench stylesheet exists for story layout: workbench-preview.css holds only the sb-main and wbp-* preview rules.

**Fix:** Add a small BEM stylesheet for story scaffolding: `story-layout.css` with `story-row`, `story-column` and `story-note`, gaps as `--story-row__gap`, the size of `story-frame` as `--story-frame__width` and `--story-frame__height`, and `story-error`. Inject it with the preview, as workbench-preview.css is, or put it in Stroom's css only if TestStylesheets allows it. Make StoryPanels and the per-group frame helpers set those classes, and set runtime sizes with CssProperties. Use the existing `control-box`, `dock-container-vertical` and `w-100` where a story copies them. Only story args (e.g. InfinityLogo's size) remain as custom properties.

**Verifier:** grep -rnE 'getStyle\(\)\.set|setAttribute\("style"|style=' over the workbench main sources gives about 250 matches in 80 files, close to the finding's figures. StoryPanels.java:37-79 sets display, gap, flex direction, colour and font size inline. Neither WRITING-STORIES nor UI-GUIDE exempts story scaffolding from the 'no inline styles' rule.

### dashboard-query-23: 'stroom-control' (an input field's box with bare .disabled/.readonly states) is used as a framed panel where the shared control-box applies; stroom-control-label is a separate block

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.ui.xml:9`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionEditor.java:81`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ConditionalFormattingRuleEditor.ui.xml:16`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistory.ui.xml:9`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistory.ui.xml:12`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavourites.ui.xml:10`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavourites.ui.xml:21`, `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStores.ui.xml:5`, `stroom-app/src/main/resources/ui/css/search-result-stores.css:18`, `stroom-app/src/main/resources/ui/css/stroom-control.css:21`, `stroom-app/src/main/resources/ui/css/stroom-control.css:69`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/BasicTableSettings.ui.xml:15`

**Evidence:** stroom-control.css documents .stroom-control as 'A Stroom control's field … selection boxes, date-time boxes, drop downs', with input padding and bare .disabled and .readonly state classes. The slice puts it on panels: the ExpressionEditor root (toggled by setFramed), the CF rule editor's expression slot, QueryHistory and QueryFavourites' panels, and SearchResultStores' root, which search-result-stores.css overrides as '.stroom-control.search-result-stores'. data-grid.css:159-166 also styles .stroom-control. The nine Basic*Settings templates give the id label 'stroom-control-label', styled as '.stroom .stroom-control-label'.

**Fix:** Use the shared control-box for these bordered panels (as the memory note says: bordered panel without padding = control-box), with ExpressionEditor toggling expression-editor--framed. Rename stroom-control-label to a basic-settings__id element, or a shared form-label modifier.

**Verifier:** stroom-control.css documents .stroom-control as a field's box with input padding and .disabled/.readonly states. It is used as a panel frame at ExpressionEditor.ui.xml:9 and ExpressionEditor.java:81 (setStyleName toggle), QueryHistory.ui.xml:9/12 and SearchResultStores.ui.xml:5, and search-result-stores.css:18 overrides it. control-box exists in form-controls.css:23.

### dashboard-query-24: Bare state class 'maximised' styles dashboard-body; time-range-selector uses '--readonly' where the rest use '--read-only'

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/dashboard-body.css:21`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.java:216`, `stroom-app/src/main/resources/ui/css/time-range-selector.css:24`, `stroom-core-client/src/main/java/stroom/query/client/component/TimeRangeSelector.java:47`

**Evidence:** dashboard-body.css:21 '.maximised .dashboard-body', and 'maximised' is added as a bare class by Main.java:216 (another slice). grep of the sheets: time-range-selector--readonly is the only '--readonly' modifier, against nine '--read-only' modifiers.

**Fix:** Have Main set a modifier of its own block (e.g. main__center--maximised) and use it as the context in dashboard-body.css. Rename the modifier to time-range-selector--read-only.

**Verifier:** dashboard-body.css:21 '.maximised .dashboard-body', where Main.java:216 adds the bare class 'maximised'. time-range-selector.css:24 uses time-range-selector--readonly, the only '--readonly' BEM modifier; the other sheets use '--read-only' (10 occurrences).

### dashboard-query-25: Roots missing their block (QueryDataPane, ElasticIndexFieldList, SolrIndexFieldList); QueryHelp's block has no sheet and its details are a separate block

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/QueryHelp.ui.xml:9`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryHelp.ui.xml:17`, `stroom-app/src/main/resources/ui/css/quick-filter.css:96`, `stroom-app/src/main/resources/ui/css/query-help-detail.css:19`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.ui.xml:10`, `stroom-app/src/main/resources/ui/css/query-data-pane.css:23`, `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticIndexFieldList.ui.xml:13`, `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexFieldList.ui.xml:18`

**Evidence:** QueryHelp has no sheet: its block is styled only from quick-filter.css:96 ('.query-help .quick-filter__input'), and its details element is a separate block, query-help-detail (query-help-detail.css). QueryDataPane's root is 'max dock-container-vertical' while its children use query-data-pane__error-container/__error-label. ElasticIndexFieldList and SolrIndexFieldList roots are 'dock-container-vertical' only, with elements elastic-index-field-list__status-message / solr-index-field-list__sync-state.

**Fix:** Add query-data-pane, elastic-index-field-list and solr-index-field-list to the three roots. Create query-help.css with the context rule from quick-filter.css:96, and rename query-help-detail to query-help__details. Keep a separate block for the Ace doc tooltip at code-editor.css:155, or rename it there too.

**Verifier:** QueryHelp's root does have its block: QueryHelp.ui.xml:9 SplitPane addStyleNames="query-help max". The defect is that query-help has no sheet of its own: it is styled only from quick-filter.css:96, and its details element is the separate block query-help-detail (query-help-detail.css, which code-editor.css:155 also uses). QueryDataPane's root (:10 'max dock-container-vertical') lacks query-data-pane, although query-data-pane.css exists. The ElasticIndexFieldList and SolrIndexFieldList roots are 'dock-container-vertical' only, with elements named for their blocks.

### data-pipeline-processing-25: Classes with no styling or a wrong role: form-page, stroom-control-label, and form-group put on a SimplePanel

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/ProcessChoiceDialog.ui.xml:8`, `stroom-core-client/src/test/java/stroom/widget/TestStylesheets.java:84`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/NewProperty.ui.xml:14`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/NewPipelineReference.ui.xml:12`, `stroom-app/src/main/resources/ui/css/stroom-control.css`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.ui.xml:21`

**Evidence:** `form-page` has no rule in any sheet and is listed as a HOOK in TestStylesheets, but `git grep form-page` shows nothing finds an element by it; the only other user is LayoutConstraint.ui.xml. `stroom-control-label` (on 6 read-only Labels) is styled in stroom-control.css as `.stroom .stroom-control-label`, beside the field and grid rules: a different element in the stroom-control sheet. ProcessorProfileEdit puts `addStyleNames="form-group"` on a SimplePanel around TimeZoneBox instead of using a `<form:FormGroup>`.

**Fix:** Remove form-page from both templates and from HOOKS. Move the read-only value label style to a block of its own (for example form-value, or a modifier of Stroom's Label in its own sheet) and remove it from stroom-control.css. Wrap TimeZoneBox in `<form:FormGroup>`.

**Verifier:** form-page appears only in ProcessChoiceDialog.ui.xml:8, LayoutConstraint.ui.xml and TestStylesheets HOOKS (line 84). It has no CSS rule and no code finds an element by it. `.stroom .stroom-control-label` (stroom-control.css:69) is a read-only text label style kept in the field sheet beside stroom-control and stroom-control-grid, and it is used in 12 files. ProcessorProfileEdit.ui.xml:23 puts `addStyleNames="form-group"` on a SimplePanel around TimeZoneBox where a FormGroup (label optional, as used elsewhere) should be.

### explorer-documents-shell-27: 'dashboard-panel', a dashboard class, is borrowed by non-dashboard components, where it only serves as context for a form-group label rule

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShards.ui.xml:9`, `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticDataShards.ui.xml:10`, `stroom-core-client/src/main/java/stroom/analytics/client/component/DuplicateManagement.ui.xml:43`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessing.ui.xml:8`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessing.ui.xml:9`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEdit.ui.xml:7`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEdit.ui.xml:8`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreview.ui.xml:9`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreview.ui.xml:12`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreview.ui.xml:13`, `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.ui.xml:45`, `stroom-app/src/main/resources/ui/css/form-group.css:45`

**Evidence:** The only rule using the class is `.dashboard-panel > .form-group label.form-group__label:not(:empty)` in form-group.css; elsewhere 'dashboard-panel' appears only in theme variables. None of these 11 elements contains a form-group. MarkdownPreview nests it three deep (a FlowPanel.dashboard-panel holding a SimplePanel.dashboard-panel holding the IFrame). Navigation puts it on the activity button. The class is used as if it were a shared 'panel' pattern, but it is neither styled as one nor documented as shared.

**Fix:** Remove dashboard-panel from these elements (and the redundant nested wrappers in MarkdownPreview). If a panel background is meant, add a shared, documented class (or reuse control-box) in form-controls.css.

**Verifier:** The only CSS selector using the class is form-group.css:45 `.dashboard-panel > .form-group label...`; every other CSS reference is a --dashboard-panel__* variable. The listed elements (AnalyticDataShards, DuplicateManagement, ScheduledProcessing, MarkdownEdit, MarkdownPreview nested three times, Navigation's activity button) contain no direct .form-group child, so the class does nothing there. The same pattern recurs outside the slice (MetaBrowser, Jobs, FloorMapEditor, XSDBrowser, SteppingScreenController), which is worth noting for a cross-slice fix.

### explorer-documents-shell-29: Sheets holding several unrelated or extra blocks: quick-filter.css (quick-filter-layout, query-help, selection-popup), lozenge.css (lozenge-list), app-page.css (stroom-body, app-panel, #loading), iframe.css (two blocks)

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/quick-filter.css:88`, `stroom-app/src/main/resources/ui/css/lozenge.css:19`, `stroom-app/src/main/resources/ui/css/app-page.css:20`, `stroom-app/src/main/resources/ui/css/app-page.css:26`, `stroom-app/src/main/resources/ui/css/iframe.css:19`, `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEdit.java:237`, `stroom-core-client/src/main/java/stroom/annotation/client/component/Chooser.ui.xml:10`, `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityTree.ui.xml:8`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopup.ui.xml:10`, `stroom-core-client/src/main/java/stroom/importexport/client/component/ExportConfigDialog.ui.xml:13`

**Evidence:** Blocks per sheet, from extracting each sheet's class selectors:
- quick-filter.css: quick-filter, quick-filter-layout, query-help, selection-popup. quick-filter-layout ('a panel holding a quick filter above what it filters', only `gap`) is set by Chooser, EntityTree, ExplorerPopup and ExportConfigDialog in this slice.
- lozenge.css: lozenge and lozenge-list. lozenge-list is set by AnnotationEdit.java:237 around each lozenge.
- app-page.css: stroom-body, app-panel, and the loading ids with loading--hidden.
- iframe.css: iframe-frame and iframe-panel.

**Fix:** - quick-filter-layout: either a documented shared layout helper (a gap helper in layout-helpers.css) or each user's own element class.
- lozenge-list -> lozenge__list, or an annotation-edit__lozenges element in annotation-edit.css.
- app-page.css: one block, app-page, with app-page__body, app-page__loading and app-page__loading--hidden for the host page's own elements (see -14).
- iframe.css: one block per component (see -14).

**Verifier:** quick-filter.css selectors: .quick-filter, .quick-filter__*, .quick-filter--recent, .quick-filter-layout, .query-help and .selection-popup. lozenge.css: .lozenge-list and .lozenge, with lozenge-list set at AnnotationEdit.java:237. app-page.css: .stroom-body, .app-panel and #loading/#loading.loading--hidden. iframe.css: .iframe-frame and .iframe-panel. quick-filter-layout appears 8 times in .ui.xml/.java.

### explorer-documents-shell-30: TypeFilter puts its block on the inner grid and an element of that block on the outer root; its template comment points at the wrong sheet

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/TypeFilter.ui.xml:1`, `stroom-app/src/main/resources/ui/css/type-filter.css:18`, `stroom-app/src/main/resources/ui/css/type-filter.css:26`

**Evidence:** TypeFilter.ui.xml: `<g:ScrollPanel styleName="type-filter__scroll"><dg:DataGrid ... addStyleNames="type-filter"/>`. The root carries the element and the child carries the block. The comment says its sizes are in explorer-tree.css, but they are in type-filter.css (type-filter__scroll), and explorer-tree.css has no type-filter rule.

**Fix:** Put `type-filter` on the ScrollPanel root and give the grid `type-filter__list`. Update type-filter.css (`.type-filter__list.data-grid .data-grid__cell > div`, `.type-filter__all`) and fix the comment.

**Verifier:** TypeFilter.ui.xml: the root is `<g:ScrollPanel styleName="type-filter__scroll">`, and the inner DataGrid has addStyleNames="type-filter". The template comment says 'up to the popup's size (explorer-tree.css)', but the sizes are in type-filter.css (.type-filter__scroll), and grep finds no type-filter rule in explorer-tree.css.

### floormap-pathways-stores-07: stroom-floormap-visually-hidden copies the shared sr-only helper

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/stroom-floormap.css:540`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.ui.xml:73`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayers.ui.xml:49`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeline.ui.xml:92`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:281`

**Evidence:** stroom-floormap.css:540-551 holds `position:absolute; width:1px; height:1px; margin:-1px; padding:0; overflow:hidden; clip:rect(0,0,0,0); clip-path:inset(50%); white-space:nowrap; border:0`, which is the same job as `.sr-only` in layout-helpers.css:36. It is used three times in templates and named in FloorMapAria's javadoc.

**Fix:** Use class="sr-only" in the three templates, delete the rule and fix the javadoc.

**Verifier:** The rule at stroom-floormap.css:540 is the same as .sr-only in layout-helpers.css. It is used at FloorMapCanvas.ui.xml:73, FloorMapLayers.ui.xml:49 and FloorMapTimeline.ui.xml:92, and named in the FloorMapAria.java:281 javadoc. The wave-07 and wave-08 notes already deferred merging it.

### floormap-pathways-stores-11: Unstyled classes in the slice: contentstore-details-name, and dashboard-panel used as a no-op on floor map and content store panels

*low, effort small, adjusted.*

**Where:** `stroom-app/src/main/resources/ui/css/pathways.css:439`, `stroom-app/src/main/resources/ui/css/contentstore-details.css:78`, `stroom-app/src/main/resources/ui/css/contentstore-details.css:85`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreContentPackDetails.ui.xml:14`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.ui.xml:25`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.ui.xml:25`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.ui.xml:9`

**Evidence:** - `.expand-icon.expand-leaf` (pathways.css:439): `git grep expand-leaf` finds nothing that sets it in Java or templates.
- `.contentstore-details .markdown` and `.markdown h1` (contentstore-details.css:78, :85): nothing in the client sets `markdown` inside it. Its Html uses contentstore-details-markdown, which already sets height:300px. This is plausible rather than certain, because the description HTML is passed to setHTML as given.
- `contentstore-details-name` is set 8 times in ContentStoreContentPackDetails.ui.xml but no rule names it.
- `dashboard-panel`, set 5 times in FloorMapEditor.ui.xml, once in FloorMapMap.ui.xml and twice in ContentStoreTab.ui.xml: its only rule is `.dashboard-panel > .form-group label.form-group__label` (form-group.css:45), and none of those panels holds a form-group, so it does nothing there.

**Fix:** Style contentstore-details-name as content-store-content-pack-details__name or drop it. For dashboard-panel, decide app-wide: give it a real rule or make it a documented hook, since about 40 templates and the stories' selectors use it. Don't remove it only from this slice. Keep the expand-leaf and .markdown rules, which are live.

**Verifier:** Two of the four items are wrong. expand-leaf is set at TraceOverviewChart.java:1025 (`Attribute.className("expand-icon expand-leaf")`), so pathways.css:439 is live. .markdown is set too: ContentStoreContentPackDetailsController.java:101 calls MarkdownConverter.convertMarkdownToHtml, which wraps the HTML in a div whose classes come from getMarkdownContainerClasses() = "markdown " + theme (MarkdownConverter.java:164), so contentstore-details.css:78 and :85 are live. Two items stand. contentstore-details-name is set at ContentStoreContentPackDetails.ui.xml:14-25 and no rule names it. dashboard-panel's only class rule is form-group.css:45, so it does nothing in FloorMapEditor.ui.xml:28-37, FloorMapMap.ui.xml:29 and ContentStoreTab.ui.xml:10/14. The same class is set in about 40 templates app-wide, and ProcessorTaskSummaryStories.java:276/281 query it as a hook, so it is an app-wide decision rather than a slice-local fix.

### floormap-pathways-stores-12: Inline style on the floor map's image swatches

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapSwatchHtml.java:185`

**Evidence:** `" style=\"width:" + sizePx + "px;height:" + sizePx + "px;" + "object-fit:contain;\" alt=\"\"/>"`: a fixed style (object-fit) and a runtime size set inline, not as a custom property.

**Fix:** Put the img in a class such as floor-map-swatch__image { object-fit: contain; width: var(--floor-map-swatch__size); height: var(--floor-map-swatch__size) } and set --floor-map-swatch__size in the markup, keeping the width/height attributes.

**Verifier:** FloorMapSwatchHtml.java:185-186 writes `style="width:..px;height:..px;object-fit:contain;"` into an img string: a fixed style and a runtime size, both inline.

### security-settings-12: Two credential components share a block named for neither, and its sheet styles a generic .wide-control

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/credentials-details-dialog.css:19-35`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialSettings.ui.xml:9`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialSettings.ui.xml:26`, `stroom-core-client/src/main/java/stroom/credentials/client/component/ReplaceSecret.ui.xml:9`, `stroom-core-client/src/main/java/stroom/credentials/client/component/ReplaceSecret.ui.xml:23`

**Evidence:** CredentialSettings and ReplaceSecret both set `credentials-details-dialog`, which is neither component's name (there is no CredentialsDetailsDialog), and both put `addStyleNames="wide-control"` on their DateTimeBox. The sheet styles `.credentials-details-dialog .wide-control { width: 650px }`. Its comment also says the block is "also used by the processor profile's Edit Period", i.e. a third feature.

**Fix:** Name a block per component (`credential-settings`, `replace-secret`) or, if they share one, a shared form class in form-controls.css. Replace `wide-control` with an element class, `credential-settings__expiry` / `replace-secret__expiry` (or an existing width modifier on DateTimeBox).

**Verifier:** credentials-details-dialog.css:21 and 33 hold `.credentials-details-dialog` and `.credentials-details-dialog .wide-control`. The block is used by CredentialSettings.ui.xml:9, ReplaceSecret.ui.xml:9 and also processor/client/component/ProfilePeriodEditor.ui.xml:9, a third feature. That file should be added to the finding's files. wide-control is also used bare in floormap/FloorMapObjectEdit.ui.xml, outside this slice.

### security-settings-13: Block names that don't match their component

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsList.ui.xml:8`, `stroom-app/src/main/resources/ui/css/document-user-permissions.css`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetSettings.ui.xml:8-9`, `stroom-app/src/main/resources/ui/css/form-group.css:45`

**Evidence:** DocumentUserPermissionsList sets `document-user-permissions__table` (block document-user-permissions, sheet document-user-permissions.css), and no DocumentUserPermissions component exists. RuleSetSettings' panes use `dashboard-panel`, which form-group.css:45 styles (`.dashboard-panel > .form-group label...`): a dashboard name, styled from the form-group sheet, used here in receipt rules.

**Fix:** Rename the block to `document-user-permissions-list` (`document-user-permissions-list__table`, sheet document-user-permissions-list.css). Replace `dashboard-panel` with a shared layout name in layout-helpers.css (e.g. `pane`) as part of a repo-wide rename (it is used in about 20 templates outside this slice), or drop it from RuleSetSettings if the form-group rule doesn't apply there.

**Verifier:** DocumentUserPermissionsList.ui.xml:8 uses document-user-permissions__table, styled in document-user-permissions.css:21, and there is no DocumentUserPermissions component. form-group.css:45 has `.dashboard-panel > .form-group label...`. dashboard-panel appears in 24 .ui.xml files.

### widgets-1-25: The field class stroom-control is not BEM and its sheet holds two unrelated blocks (stroom-control-grid, stroom-control-label) and a bare 'readonly' state class set by DropDownBox

*low, effort medium, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/item/client/BaseSelectionBox.ui.xml:16`, `stroom-core-client-widget/src/main/java/stroom/item/client/BaseSelectionBox.ui.xml:18`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionBoxCell.java:313`, `stroom-app/src/main/resources/ui/css/stroom-control.css:36`, `stroom-app/src/main/resources/ui/css/stroom-control.css:46`, `stroom-app/src/main/resources/ui/css/stroom-control.css:69`, `stroom-app/src/main/resources/ui/css/data-grid.css:159`

**Evidence:** stroom-control.css styles .stroom-control (a field box, set by the selection box, date-time box and drop-downs), the bare state '.stroom-control.readonly', '.stroom .stroom-control-grid' (a table layout used by WelcomeTab.ui.xml:8) and '.stroom .stroom-control-label' (a wrapping label used by 15 dashboard/pipeline/config templates). These are three unrelated blocks in one sheet, none of them BEM. data-grid.css:159-165 also keys off .stroom-control and .stroom-control.disabled.

**Fix:** Name the shared field box as a form-controls.css pattern (e.g. fold it into control-box or add control-field with control-field--read-only), and replace DropDownBox.java:207's 'readonly' class with a modifier or [aria-readonly]. Move stroom-control-grid into welcome-tab.css (welcome-tab__build-info) and give stroom-control-label (12 templates) its own block and sheet.

**Verifier:** The substance holds: stroom-control.css holds the field class .stroom-control, the bare state '.stroom-control.readonly' (:36), '.stroom .stroom-control-grid' (:46-64, used by WelcomeTab.ui.xml:8) and '.stroom .stroom-control-label' (:69). data-grid.css:159-164 keys off .stroom-control and .stroom-control.disabled. Two details of the evidence are wrong. The 'readonly' class is set by DropDownBox.java:207 (setStyleName("readonly", ...)), not SelectionBoxCell:313, whose 'readonly' is an HTML attribute. And stroom-control-label is used in 12 files, not 15.

### widgets-1-26: Popups are sized with inline width and height

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupUtil.java:165`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupUtil.java:168`

**Evidence:** PopupUtil.java:165 'popupPanel.setWidth(newWidth + "px")' and :168 'popupPanel.setHeight(newHeight + "px")' for PopupType.POPUP. The dialog branch beside it (:174-177) already uses DialogWindow.setWidth/Height, i.e. CssProperties.set(container, "--dialog__width", ...).

**Fix:** Set --popup__width/--popup__height with CssProperties and have popup.css use them (as dialog.css does for --dialog__width/--dialog__height).

**Verifier:** PopupUtil.java:165 'popupPanel.setWidth(newWidth + "px")' and :168 setHeight write inline width/height for PopupType.POPUP. The dialog branch (:172-177) uses DialogWindow.setWidth/Height with custom properties, read by dialog.css (.dialog__container width: var(--dialog__width, auto)).

### widgets-2-32: base.css carries class rules for several blocks, and the XSD browser sets another feature's dashboard-panel class that styles nothing there

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/base.css:429`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowser.ui.xml:20`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowser.ui.xml:23`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowser.ui.xml:24`, `stroom-app/src/main/resources/ui/css/form-group.css:45`

**Evidence:** The UI guide says base.css holds element defaults with no classes. base.css:429-433 underlines links in .markdown, .info-page (HtmlPane), .help-popup__content (HelpPopup), .welcome-tab__body and .authentication-error__message-container, so each block's link rule lives outside its own sheet. XSDBrowser.ui.xml sets dashboard-panel on the diagram, documentation and constraints panes. The only rule naming .dashboard-panel anywhere is form-group.css:45 (`.dashboard-panel > .form-group label...`), and those panes hold no form groups, so the class does nothing in the XSD browser and names the dashboard there.

**Fix:** Move each `X a { text-decoration: underline }` into X's own sheet (info-page.css, help-popup.css, markdown.css, welcome-tab.css, authentication-error.css), or make one shared helper class (e.g. text-links in layout-helpers.css) that those elements also set. Remove dashboard-panel from XSDBrowser.ui.xml (and style the panes from xsd-browser.css if they need a border or padding).

**Verifier:** base.css:428-432 has class selectors for .markdown, .info-page, .help-popup__content, .welcome-tab__body and .authentication-error__message-container, while UI-GUIDE.md:38 says base.css holds element defaults with no classes. XSDBrowser.ui.xml:20, 23 and 24 set dashboard-panel. The only rule that styles .dashboard-panel is form-group.css:45 (`.dashboard-panel > .form-group ...`), and those panes hold no form groups.

### workbench-10: QuickFilterPanelStories sets an unstyled, non-BEM class 'hoverable-row' and styles the rows inline

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/QuickFilterPanelStories.java:154`

**Evidence:** `label.setStyleName("hoverable-row")`, followed by getStyle padding 4px 8px and cursor:pointer. `git grep hoverable-row` finds nothing else: no stylesheet styles it and no play queries it. TestStylesheets checks only Stroom's modules, so the workbench's setter escapes the check that every class set is styled.

**Fix:** Delete the class, or replace the rows with a Stroom list widget (e.g. SelectionList or MenuItemCell rows) that brings its own hover. If a plain row is wanted, use the story-layout block from workbench-09 (`story-list__row`).

**Verifier:** git grep -w hoverable-row finds only QuickFilterPanelStories.java:129 (a comment) and :154 (the setStyleName call). No stylesheet or play uses it.

## Dead code (52)

### dashboard-query-04: Unused classes and events: RecordingEventBus, MyScriptInjector, ElasticIndexFieldDataProvider, InsertQueryElementEvent, SearchStartEvent, SearchEndEvent, Views, ReopenResultStoreEvent (with DashboardPlugin.reopen)

*medium, effort small, adjusted.*

**Where:** `stroom-app-gwt/src/main/java/stroom/app/client/gin/RecordingEventBus.java:28`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/MyScriptInjector.java:61`, `stroom-core-client/src/main/java/stroom/search/elastic/client/ElasticIndexFieldDataProvider.java:26`, `stroom-core-client/src/main/java/stroom/query/client/InsertQueryElementEvent.java:25`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/SearchStartEvent.java:23`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/SearchEndEvent.java:23`, `stroom-core-client/src/main/java/stroom/query/client/Views.java:33`, `stroom-core-client/src/main/java/stroom/query/client/TokenErrorListener.java:21`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java:87`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java:488`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java:492`, `stroom-core-client/src/main/java/stroom/dashboard/client/event/ReopenResultStoreEvent.java:25`, `stroom-core-client/src/main/java/stroom/dashboard/client/DashboardPlugin.java:79`, `stroom-core-client/src/main/java/stroom/dashboard/client/DashboardPlugin.java:176`

**Evidence:** A word index of every .java/.ui.xml/.gwt.xml file in every stroom-* module (workbench and tests included) gives 0 files besides the class's own for RecordingEventBus, MyScriptInjector, ElasticIndexFieldDataProvider, InsertQueryElementEvent, SearchStartEvent and SearchEndEvent (only gwt-inventory.csv/.md and COMPONENT_MIGRATION_ORDER.md:1391 name them). Views has no user, and its only method (fetchViews) is commented out (Views.java:51-71). TokenErrorListener is used only by QueryModel's addTokenErrorListener/removeTokenErrorListener, and git grep finds no caller of either. ReopenResultStoreEvent.fire is never called (git grep ReopenResultStoreEvent: only its own file and DashboardPlugin's handler), so DashboardPlugin.reopen is dead too. wave-10.md:147 already notes that ElasticIndexFieldDataProvider is a copy of ListDataProvider.

**Fix:** Delete RecordingEventBus, MyScriptInjector, ElasticIndexFieldDataProvider, InsertQueryElementEvent, SearchStartEvent, SearchEndEvent and Views. Also delete ReopenResultStoreEvent with its handler and DashboardPlugin.reopen. Keep TokenErrorListener and QueryModel's tokenErrorListeners and addTokenErrorListener, which AbstractQueryDataController and QueryEditController use. Ask before removing.

**Verifier:** git grep -lw finds no file other than the class's own (and the handler in DashboardPlugin) for RecordingEventBus, MyScriptInjector, ElasticIndexFieldDataProvider, InsertQueryElementEvent, SearchStartEvent, SearchEndEvent and ReopenResultStoreEvent. Views is used nowhere and fetchViews is commented out. TokenErrorListener is NOT dead: QueryModel.addTokenErrorListener is called at AbstractQueryDataController.java:123 and QueryEditController.java:229 with lambdas. Only removeTokenErrorListener is unused, and dashboard-query-05 covers it.

### dashboard-query-05: Unused public methods, getters and a private helper

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/ConditionalFormattingSwatchUtil.java:83`, `stroom-core-client/src/main/java/stroom/query/client/DataSourceClient.java:84`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java:496`, `stroom-core-client/src/main/java/stroom/query/client/ResultStorePlugin.java:110`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/MessageEvent.java:33`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardContextImpl.java:582`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardContextImpl.java:586`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardContextImpl.java:590`, `stroom-app-gwt/src/main/java/stroom/app/client/App.java:98`

**Evidence:** Public method names from the slice checked against the all-modules word index (declaring file only): createCustomSwatch, fetchDataSourceDescription, setSourceType, getOrigin, getFilterList, removeTokenErrorListener, clearFiredEvents. ResultStorePlugin.getResultStoreModel is not called anywhere (PausePollingStories.java:113 calls the workbench ginjector's own getResultStoreModel). Nothing calls ComponentState.getParams/getSelectionList/getFilterList: DashboardContextImpl reads the fields directly (componentState.filterList at :277, :475). App.appendStackTraces (private) is called only from the commented-out handler at App.java:44-59. createCustomSwatch also builds inline background and colour styles with SafeStylesBuilder (:90-99).

**Fix:** Delete ConditionalFormattingSwatchUtil.createCustomSwatch, DataSourceClient.fetchDataSourceDescription, QueryModel.setSourceType, ResultStorePlugin.getResultStoreModel and MessageEvent.getOrigin (both the public method and its native helper), the three ComponentState getters, and App.appendStackTraces together with the commented-out handler that uses it.

**Verifier:** git grep -nw finds only the declarations for createCustomSwatch, fetchDataSourceDescription, setSourceType, MessageEvent.getOrigin (public plus native helper), removeTokenErrorListener and ResultStorePlugin.getResultStoreModel; PausePollingStories calls QueryScreenGinjector's own getResultStoreModel. DashboardContextImpl reads componentState.params, .selectionList and .filterList directly, so the ComponentState getters are unused. App.appendStackTraces is called only from commented-out code and from itself.

### dashboard-query-06: Large commented-out code blocks across the slice's controllers (about 500 lines)

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/QueryTextPaneController.java:293`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTextPaneController.java:619`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTableColumnsManager.java:523`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTableColumnsManager.java:582`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVisController.java:588`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableController.java:507`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardBodyController.java:221`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardBodyController.java:476`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardBodyController.java:334`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/EmbeddedQueryController.java:219`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/EmbeddedQueryController.java:435`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPaneController.java:257`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TextPaneController.java:526`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/TabMenuController.java:99`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.java:1634`, `stroom-core-client/src/main/java/stroom/query/client/ResultStorePlugin.java`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/DashboardPlugin.java:128`, `stroom-app-gwt/src/main/java/stroom/app/client/App.java:44`, `stroom-app-gwt/src/main/java/stroom/app/client/App.java:90`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:54`, `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/DashboardApp.java:52`

**Evidence:** git grep -c for comment lines holding code (calls, declarations, braces, tags) gives QueryTextPaneController 165 lines (blocks at 293-488, 490-559 and 619-711), QueryTableColumnsManager 76 (523-541, 582-682), DashboardBodyController 48 (an old UiBinder template with g:Button at 221-233, an old layout builder at 476-517), EmbeddedQueryController 36, TextPaneController 34, QueryResultVisController 34, FlexLayout 27, QueryResultTableController 23 and QueryPaneController 19. EmbeddedQueryController:436 and QueryPaneController:258 still mention the removed VisPresenter. App.java:90 and DashboardApp.java:52 are GWTP leftovers ('ginjector.getPlaceManager().revealCurrentPlace()'), and AppModule.java:54 is '// bind(CurrentUser.class)'.

**Fix:** Delete the commented-out blocks; they are in git history if needed.

**Verifier:** Sampled QueryTextPaneController:293 (a commented-out update(TableController) method), DashboardBodyController:221 (an old template with g:Button), App.java:90 and DashboardApp.java:52 ('// ginjector.getPlaceManager().revealCurrentPlace();') and AppModule:54 ('// bind(CurrentUser.class)...'). 271 comment lines in QueryTextPaneController look like code. The App and DashboardApp lines are kept on purpose as a 'remember how places were used' note, but they are still GWTP leftovers.

### data-pipeline-processing-21: Unused classes FileData and InterceptingSelectionChangeHandler

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/FileData.java`, `stroom-core-client/src/main/java/stroom/data/client/InterceptingSelectionChangeHandler.java`

**Evidence:** git grep -ow over all *.java and *.xml in the repo, stories and tests included, finds no reference to either class outside its own file.

**Fix:** Delete both files (ask before removing, per the removals rule).

**Verifier:** grep -rnw across *.java, *.xml and *.js in the whole repo, excluding build: FileData appears only at data/client/FileData.java:19, and the package-private InterceptingSelectionChangeHandler only in its own file at :25.

### explorer-documents-shell-21: Classes nothing uses: CloseExplorerTabEvent, OpenExplorerTabEvent, the explorer's FocusEvent, UrlQueryParameterChangeEvent, HelpActionEvent / HelpActionHandler, DocumentAssetImageResource, FindDocResultListHandler, SizeUtil, EntityGinjector

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/event/CloseExplorerTabEvent.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/event/OpenExplorerTabEvent.java:26`, `stroom-core-client/src/main/java/stroom/explorer/client/event/FocusEvent.java:23`, `stroom-core-client/src/main/java/stroom/main/client/event/UrlQueryParameterChangeEvent.java:27`, `stroom-core-client/src/main/java/stroom/help/client/event/HelpActionEvent.java:21`, `stroom-core-client/src/main/java/stroom/help/client/event/HelpActionHandler.java:21`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetImageResource.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/FindDocResultListHandler.java:1`, `stroom-core-client/src/main/java/stroom/util/client/SizeUtil.java:1`, `stroom-core-client/src/main/java/stroom/entity/client/gin/EntityGinjector.java:1`

**Evidence:** The identifier index over every tracked .java, .xml, .css, .js, .html and .json file in the repo, workbench and tests included, finds no file other than the class's own for CloseExplorerTabEvent, OpenExplorerTabEvent, UrlQueryParameterChangeEvent, DocumentAssetImageResource, FindDocResultListHandler and SizeUtil.
- HelpActionEvent and HelpActionHandler name only each other (and the generated gwt-inventory oracle).
- stroom.explorer.client.event.FocusEvent: its only other match is FloorMapTimeline, which imports com.google.gwt.event.dom.client.FocusEvent.
- EntityGinjector is an empty `interface EntityGinjector extends Ginjector {}`, extended only by AppGinjectorUser and DashboardAppGinjectorUser.

**Fix:** Delete all of them, and remove EntityGinjector from the two app ginjectors' extends lists. This is a removal, so per the 'ask before new removals' rule, get the user's go-ahead first.

**Verifier:** git grep -lw across the whole repo (excluding oracles/ and .md) finds no file other than the class's own for CloseExplorerTabEvent, OpenExplorerTabEvent, UrlQueryParameterChangeEvent, DocumentAssetImageResource, FindDocResultListHandler and SizeUtil. The only other hit, build-route-recipes.mjs, is a comment naming FocusExplorerFilterEvent. HelpActionEvent and HelpActionHandler name only each other. explorer.client.event.FocusEvent: grep -rlw FocusEvent under stroom/explorer finds only its own file, and nothing imports it. EntityGinjector is an empty `extends Ginjector {}`, named only in AppGinjectorUser and DashboardAppGinjectorUser extends lists.

### floormap-pathways-stores-17: Unused classes: DataUploadService, DataUploadServiceAsync and CommonSpanBuilder

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/feed/client/DataUploadService.java:21`, `stroom-core-client/src/main/java/stroom/feed/client/DataUploadServiceAsync.java:19`, `stroom-core-client/src/main/java/stroom/pathways/client/CommonSpanBuilder.java:34`

**Evidence:** `git grep -w` for each name over the whole repo finds only the declaration. The only other mentions are in stroom-gwt/component-migration/wave-17.md and the gwt-inventory oracles. DataUploadService is an empty GWT-RPC RemoteService holding five unused constants (STATUS, SUCCESS, FAILED, MESSAGE, SIZE). CommonSpanBuilder is 168 lines.

**Fix:** Delete all three files (ask first, per the removals rule).

**Verifier:** `git grep -lw` for DataUploadService, DataUploadServiceAsync and CommonSpanBuilder finds only their own files plus wave-17.md and the gwt-inventory oracles.

### security-settings-20: Unused classes, ginjector getters and public methods

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/UserPermissionsReportPlugin.java:40`, `stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:55`, `stroom-core-client/src/main/java/stroom/alert/client/gin/AlertGinjector.java:25`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsListController.java:136`, `stroom-core-client/src/main/java/stroom/preferences/client/UserPreferencesManager.java:299`, `stroom-core-client/src/main/java/stroom/config/global/client/ListDataProvider.java:128`, `stroom-core-client/src/main/java/stroom/security/client/ClassNameBuilder.java:55`, `stroom-core-client/src/main/java/stroom/security/client/ClassNameBuilder.java:66`

**Evidence:** UserPermissionsReportPlugin's only reference is the commented-out `//        bindPlugin(UserPermissionsReportPlugin.class);` in SecurityModule:55. `getAlertController` has no caller in stroom-app-gwt, stroom-dashboard-gwt, stroom-gwt or stroom-core-client (AlertPlugin is an eager singleton in AlertModule). `grep -rnw` across stroom-core-client, stroom-core-client-widget, stroom-app-gwt, stroom-dashboard-gwt, stroom-gwt and stroom-core-shared finds only the declarations of CredentialsListController.getSelectedCredentialsId, UserPreferencesManager.isUtc, ListDataProvider.getTreeRowHandler, ClassNameBuilder.buildAsList and ClassNameBuilder.buildClassAttribute.

**Fix:** Delete UserPermissionsReportPlugin and the commented binding, getAlertController, and the five unused methods (ask first, as removals are held for approval).

**Verifier:** `git grep -nwE 'getSelectedCredentialsId|getTreeRowHandler|buildAsList|buildClassAttribute'` over *.java, *.ui.xml and *.gwt.xml finds only the declarations. `.isUtc()` has no caller in the GWT modules. The only Java reference to UserPermissionsReportPlugin is the commented-out bindPlugin at SecurityModule:55 (ISSUES.md #34 already notes it as unused). getAlertController has no caller anywhere.

### widgets-2-01: Whole classes that nothing in the repo uses

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/component/client/HandlerRegistrations.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/TableUtil.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/Debug.java:21`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/treelayout/util/FixedNodeExtentProvider.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/event/IndicatorEvent.java:23`, `stroom-core-client-widget/src/main/java/stroom/editor/client/event/HasIndicatorHandlers.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/event/HasFormatHandlers.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/model/XMLStyler.java`

**Evidence:** Whole-repo word index (java, ui.xml, gwt.xml, css, js, html, tests and stories): HandlerRegistrations 0 users; TableUtil 0; FixedNodeExtentProvider 0; Debug: no import of stroom.widget.util.client.Debug and no Debug.log/enable in its own package (0); IndicatorEvent and HasIndicatorHandlers are named only by each other; HasFormatHandlers is named only by FormatEvent.fire(HasFormatHandlers), which nothing calls; XMLStyler (723 lines) is used only by stroom-core-client/src/test/java/stroom/widget/editor/testclient/model/TestXMLStyler.java, which means test only.

**Fix:** Delete HandlerRegistrations, TableUtil, Debug, FixedNodeExtentProvider, IndicatorEvent, HasIndicatorHandlers and HasFormatHandlers. Delete XMLStyler together with TestXMLStyler, since nothing in the app styles XML with it. Removals need the user's approval (memory: ask before new removals).

**Verifier:** I searched every stroom-* module for each name as a whole word, across java, xml (ui.xml and gwt.xml), css, js and html, leaving out build/. HandlerRegistrations, TableUtil and FixedNodeExtentProvider appear only in their own files. IndicatorEvent and HasIndicatorHandlers appear only in each other. HasFormatHandlers appears only in itself and in FormatEvent.fire, which nothing calls. XMLStyler appears only in itself and TestXMLStyler. Debug.java has no import and no Debug.* call anywhere.

### widgets-2-02: SingleLineCodeEditor and SingleLineCodeEditorController are used only by workbench stories

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/component/SingleLineCodeEditor.java:30`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/SingleLineCodeEditor.ui.xml`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/SingleLineCodeEditorController.java:35`, `stroom-app/src/main/resources/ui/css/code-editor.css:105`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/SingleLineEditorStories.java`

**Evidence:** grep -rl SingleLineCodeEditor over the whole repo, less build/: in code it appears only in the pair itself, in AceEditorController's doc comment, in code-editor.css (code-editor__ace--single-line, lines 105-116) and in SingleLineEditorStories. No screen in stroom-core-client, stroom-dashboard-gwt or stroom-app-gwt declares or makes one. It was split from SingleLineEditorPresenter in wave 6 (COMPONENT_MIGRATION_ORDER.md:356), and nothing has used it since.

**Fix:** Either delete the pair, its story and the code-editor__ace--single-line rules, or record in ISSUES.md which screen is meant to adopt it. It is story-only dead code as it stands.

**Verifier:** Outside the pair, SingleLineCodeEditor and SingleLineCodeEditorController appear only in code-editor.css, in AceEditorController's doc comment and in SingleLineEditorStories. No app screen uses them, so the pair is story-only.

### widgets-2-03: The first htree renderer family is used only by HTreeStories; the app uses only TreeRenderer's layer-name constants

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/TreeRenderer.java:33`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/CellRenderer.java:21`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/TextCellRenderer.java`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/ShadowBox.java`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/RoundedRectangle.java`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/ArrowConnectorRenderer.java`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:87`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTreePanel.java:134`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/tree/HTreeStories.java:144`

**Evidence:** Fan-in: TextCellRenderer and ArrowConnectorRenderer are used only by HTreeStories. ShadowBox is used only by TextCellRenderer, RoundedRectangle only by ShadowBox, and CellRenderer only by TextCellRenderer and TreeRenderer. `new TreeRenderer<>` appears only in HTreeStories.java:144. The app's two uses (ExpressionTreePanel.java:87, PipelineTreePanel.java:134) read only TreeRenderer.ARROW_LAYER; both draw with TreeRenderer2 and CellRenderer2.

**Fix:** Move the layer names (SHADOW_LAYER, ITEM_LAYER, ARROW_LAYER) to LayeredCanvas or to the surviving renderer. Delete TreeRenderer, CellRenderer, TextCellRenderer, ShadowBox, RoundedRectangle and ArrowConnectorRenderer, and point HTreeStories at the renderer the app uses (see widgets-2-14). This is story-only code.

**Verifier:** `new TreeRenderer<` appears only at HTreeStories.java:144. PipelineTreePanel.java:134 and ExpressionTreePanel.java:87 use only TreeRenderer.ARROW_LAYER. TextCellRenderer and ArrowConnectorRenderer are used only by HTreeStories, ShadowBox only by TextCellRenderer, and RoundedRectangle only by ShadowBox.

### widgets-2-04: Dead code in the code editor: an overview popup that is never shown, the handler that hides it, and unused methods

*medium, effort small, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditor.java:68`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditor.java:262`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:284`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:208`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:223`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:233`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/Editor.java:233`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/Editor.java:617`

**Evidence:** CodeEditor.java:66-68 says of INDICATOR_POPUP: "Never shown: the overview bar shows its own ... kept until removals are allowed". Its only use is .hide() at line 262. RightBar.java:32 has the popup that is actually shown. Removals have since been done (memory, 2026-10-29). RightBar.getWidth() and its width field: no caller (grep rightBar. gives only setEditor and render). CodeEditorController.getEditorId, markClean and replaceSelectedText: grep for .markClean(), .replaceSelectedText( and .getEditorId() finds callers only inside the controller and Editor. QueryEditController, QueryHelpController and ColumnFunctionEditorController use only insertSnippet, setMarkers and isClean. Editor.markClean and Editor.replaceSelectedText are called only by those dead controller methods.

**Fix:** Delete INDICATOR_POPUP, onMouseDown and its two MouseDownEvent registrations (CodeEditor.java:100 and 102). Delete RightBar.getWidth and the width field. Delete CodeEditorController.getEditorId, markClean and replaceSelectedText, and Editor.replaceSelectedText. Make Editor.markClean private, since Editor.java:251 calls it.

**Verifier:** INDICATOR_POPUP is used only by .hide() at CodeEditor.java:262 and is never shown. RightBar.getWidth has no caller; the width field is only written (render line 205). CodeEditorController's getEditorId, markClean and replaceSelectedText have no external callers (the getEditorId at DelegatingAceCompleter.java:122 is a different method, MapKey's), and Editor.replaceSelectedText is called only from the controller. However, Editor.markClean is called inside Editor at Editor.java:251, so it cannot be deleted; it can only be made private. Also, once INDICATOR_POPUP goes, onMouseDown does nothing, so both addDomHandler calls (lines 100 and 102) can go with it.

### widgets-2-05: FormatEvent is a vestigial GwtEvent used only as an empty payload for a listener list

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/event/FormatEvent.java:22`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:87`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:307`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/CodeEditorController.java:450`

**Evidence:** FormatEvent carries no data. Its TYPE, its fire(HasFormatHandlers) (no callers), FormatHandler and dispatch are all unused. CodeEditorController keeps Listeners<FormatEvent> and fires `new FormatEvent()`. All 7 callers of addFormatListener ignore the argument (e.g. WordsTabController.java:53 `editor.addFormatListener(event -> onChange.run())`, QueryEditController.java:272, SteppingElementController.java:580). With HasFormatHandlers (widgets-2-01) and IndicatorEvent gone, stroom.editor.client.event holds nothing else.

**Fix:** Replace FormatEvent with a plain callback, either `setOnFormat(Runnable)` or `addFormatListener(Runnable)` returning a registration (rule 2: outputs are callbacks). Update the 7 callers and delete FormatEvent and the stroom.editor.client.event package.

**Verifier:** FormatEvent carries no data, and nothing uses its TYPE, fire or FormatHandler. CodeEditorController fires `new FormatEvent()` (line 450). All 7 app callers of addFormatListener ignore the event (WordsTab, SteppingElement, QueryEdit, DocumentAssetsTab, VisualisationSettings, MarkdownPreview and CodeTab controllers). One test, TestDocumentAssetsTabController.java:71, mocks addFormatListener and will need updating.

### widgets-2-06: Pager: thirteen pass-through overrides, unused fields and methods, and a setTitle that hijacks UIObject.setTitle

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:56`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:67`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:69`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:171`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:220`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:290`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:299`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java:363`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/RefreshButton.java:142`

**Evidence:** Pager.java:219-293 has 13 @Override methods (firstPage, getPage, setPage, getPageCount, hasNextPage, hasNextPages, hasPage, hasPreviousPage, hasPreviousPages, lastPage, lastPageStart, nextPage, previousPage, setPageSize, setPageStart) whose only statement is the call to super. startLoading() (299) has no caller anywhere. The @UiFields lblToSeparator (67) and lblOfSeparator (69) are never read (uifields check: one mention each). lblTitle (56) is filled only by setTitle(String) (363), which overrides UIObject.setTitle, and nothing calls it on a Pager (grep across PagerPanel, HeadedPagerPanel and TraceOverviewChart). So the title label is always empty, and a template title= would set label text rather than the tooltip. setEditing (171) is public but called only from inside Pager. RefreshButton.updateRefreshState (142) is public but called only from inside RefreshButton.

**Fix:** Delete the 13 overrides, startLoading, the two separator @UiFields (keep the template labels without ui:field), and lblTitle with its setTitle override and template label. Make setEditing and updateRefreshState private.

**Verifier:** Pager.java:219-293 consists only of overrides that call super. The callers of firstPage, nextPage and the rest (ItemNavigatorController) act on HasItems, not on Pager, so nothing depends on the widened visibility. startLoading has no caller. lblToSeparator and lblOfSeparator are declared but never read. setTitle at line 363 sets lblTitle; no caller and no template title attribute uses it (only PagerPanel, HeadedPagerPanel and TraceOverviewChart declare a Pager). setEditing is called only inside Pager (the MarkdownEditController calls are on another type), and updateRefreshState only inside RefreshButton.

### widgets-2-07: XSDConstraintDisplay.getList's enumeration parameter is always true, so a pattern list is headed "Enumeration"

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:47`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:49`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:57`

**Evidence:** Line 47 is `getList(constraints.getEnumerationList(), true)` and line 49 is `getList(constraints.getPatternList(), true)`. The `enumeration` parameter (line 57) is never false, so the "Pattern" branch is dead and a pattern restriction is labelled "Enumeration". This is both an unused-parameter defect and a visible bug.

**Fix:** Pass false at line 49, or better, replace the boolean with the heading text or an enum. Add a story for a pattern restriction.

**Verifier:** XSDConstraintDisplay.java:47 and :49 both pass true, so the else branch that would head the list "Pattern" is unreachable, and a pattern list is headed "Enumeration".

### workbench-01: Eight GIN 'mirror' modules bind nothing, yet every story ginjector lists them

*medium, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:31`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/EntityScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/TaskScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/AlertScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/AlertScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/ScheduleScreenModule.java:27`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/SqlTemporalStoreScreenModule.java:24`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/AnalyticsScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/shell/ShellAppScreenModule.java:32`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/AppScreenGinjector.java:53`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/ContentScreenGinjector.java:63`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/dashboard/DashboardScreenGinjector.java:56`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/EditorsScreenGinjector.java:67`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/idp/IdpScreenGinjector.java:39`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/ProcessingScreenGinjector.java:63`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/QueryScreenGinjector.java:45`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/security/SecurityScreenGinjector.java:52`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/shell/ShellScreenGinjector.java:104`

**Evidence:** Each of these classes has an empty `configure() { }`, or one holding only section comments (ScreenViewsModule: '// AppModule: widgets', '// SecurityModule' ...; ShellAppScreenModule: '// UserPreferencesModule' ...). Their javadoc still describes the presenter/view bindings they used to copy. `git grep -w` finds them only in the `@GinModules({...})` lists of the 9 ginjectors (e.g. AppScreenGinjector.java:53-55, ShellScreenGinjector.java:104-109). There are two identical classes, both named AlertScreenModule, in app.gin.editors and app.gin.query. ScreenViewsModule is already listed in stroom-gwt/ISSUES.md:1794 ('binds nothing and is named for views').

**Fix:** Delete the nine empty modules and remove them from the `@GinModules` lists. Also delete their now-stale javadoc references (AppScreenGinjector.java:45 and ContentScreenGinjector.java:60 mention [ScreenViewsModule]). If a shared module is still wanted for future shared bindings, keep a single one named for what it binds (e.g. `SharedScreenModule`), not for views.

**Verifier:** All nine modules have an empty configure() or one holding only comments (ScreenViewsModule.java:35-47, ShellAppScreenModule.java:36-44 hold only '// AppModule ...' lines), and none has a @Provides method. app/gin/editors/AlertScreenModule.java and app/gin/query/AlertScreenModule.java are both empty. ISSUES.md:1794 already records that ScreenViewsModule binds nothing.

### workbench-04: Plays still carry selectors and click workarounds for the removed MyDataGrid (th/td), duplicated in two helpers

*medium, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/StroomDom.java:37`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/StroomDom.java:39`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/StroomDom.java:41`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/screen/StroomDom.java:43`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dashboard/DashboardPlays.java:179`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dashboard/DashboardPlays.java:207`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/editors/QueryEditorStories.java:644`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/editors/QueryEditorStories.java:1168`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dashboard/TableFilterSettingsStories.java:76`

**Evidence:** StroomDom's GRID_HEADER_CELL is "th, [data-hcol]", GRID_HEADER_TEXT is "th, th *, ..." and GRID_CELL_TEXT is "td *, [data-col] *", documented as 'in either grid: a MyDataGrid's th'. MyDataGrid no longer exists: it is not in `git ls-files '*.java'`, and BenchmarkStories:35 and DataGridRowUpdateStories:48 say 'since removed'. The same "th, [data-hcol]" literal is repeated at DashboardPlays:207, QueryEditorStories:644 and TableFilterSettingsStories:76 ('th .tick-box, [data-hcol] .tick-box'). Both DashboardPlays.clickHeader (179) and QueryEditorStories.clickHeader (1168) are copies of a workaround for MyDataGrid only: mouseMove, sleep(200), then the mouse and pointer events.

**Fix:** Cut the selectors down to DataGrid's (`[data-hcol]`, `[data-hcol] *`, `[data-col] *`). Have the inline literals use StroomDom's constants. Keep one clickHeader (in StroomDom or DashboardPlays) without the MyDataGrid move-and-wait, re-check whether the pointer-then-mouse sequence is still needed, and delete the other copy.

**Verifier:** No MyDataGrid*.java file is tracked; DataGrid.java:84 says it replaces MyDataGrid. No Stroom client code renders th or td grids any more. StroomDom.java:37-43 still holds 'th, [data-hcol]', 'th, th *, ...' and 'td *, [data-col] *'. DashboardPlays.java:179-189 does the mouseMove, sleep(200) and pointer-plus-mouse sequence, with comments naming MyDataGrid. DashboardPlays.java:207 repeats the 'th, [data-hcol]' literal.

### dashboard-query-07: Unused @UiField fields and template ui:field elements; a stepping button that is declared but never wired

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnSelection.java:51`, `stroom-statistics/stroom-statistics-client/src/main/java/stroom/statistics/impl/sql/client/component/StatisticsFieldList.java:45`, `stroom-statistics/stroom-statistics-client/src/main/java/stroom/statistics/impl/sql/client/component/StatisticsCustomMaskList.java:50`, `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticIndexFieldList.ui.xml:8`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTextPane.ui.xml:15`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTextPane.ui.xml:20`

**Evidence:** For every @UiField in the slice, grep -c -w of the field name in its own Java file returned 1 for 'pager' in ColumnSelection, StatisticsFieldList and StatisticsCustomMaskList (declared and never used). For every template ui:field, a grep in the template's Java: ElasticIndexFieldList.ui.xml ui:field="pager" has no field (0). QueryTextPane.ui.xml's FabButton ui:field="steppingButton" (visible="false") has no field, and neither QueryTextPane nor QueryTextPaneController mentions stepping, so the button and its 'stepping-button-container' wrapper are never shown.

**Fix:** Remove the three unused pager @UiField fields and the ui:field="pager" attributes. Remove the FabButton from QueryTextPane.ui.xml and replace the 'dock-max stepping-button-container' wrapper with a plain dock-max panel (or wire the button as TextPane does, if the query screen is meant to step).

**Verifier:** grep -cw pager gives 1 (the declaration only) in ColumnSelection, StatisticsFieldList and StatisticsCustomMaskList, and 0 in ElasticIndexFieldList.java while its template has ui:field="pager". QueryTextPane.ui.xml declares FabButton ui:field="steppingButton" visible="false", and its controller mentions stepping only in commented-out code (:124-128, :690+).

### dashboard-query-08: Duplicate or redundant GIN bindings and module listings

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/gin/DashboardModule.java:30`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:52`, `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/gin/EmbeddedQueryModule.java:30`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/gin/VisModule.java:30`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppGinjectorUser.java:126`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppGinjectorUser.java:127`, `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppGinjectorUser.java:58`, `stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppGinjectorUser.java:59`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/gin/TableModule.java:28`

**Evidence:** In the app, HyperlinkEventHandlerImpl is bound asEagerSingleton twice: in AppModule:52 and in DashboardModule:30, and AppGinjectorUser installs both modules. VisFunctionCache (stroom.visualisation.client) is bound in both EmbeddedQueryModule:30 and VisModule:30. VisModule and EmbeddedQueryModule are already pulled in by DashboardGinjector's @GinModules, but AppGinjectorUser:126-127 and DashboardAppGinjectorUser:58-59 list them again. TableModule:28 binds query.client.TimeZones, which does not belong to the table area.

**Fix:** Bind HyperlinkEventHandlerImpl only in AppModule, and in DashboardAppModule for the dashboard app. Bind VisFunctionCache once, in VisualisationModule. Remove VisModule and EmbeddedQueryModule from the two GinjectorUsers' lists. Bind TimeZones in query's QueryModule.

**Verifier:** AppModule:52 and DashboardModule:30 both bind HyperlinkEventHandlerImpl asEagerSingleton, and AppGinjectorUser lists both modules. DashboardAppModule does not bind it, so the dashboard app needs a binding there once DashboardModule's goes, as the finding suggests. VisModule:30 and EmbeddedQueryModule:30 both bind VisFunctionCache. Both GinjectorUsers extend DashboardGinjector (whose @GinModules lists VisModule and EmbeddedQueryModule) and list the two modules again. TableModule:28 binds TimeZones.

### data-pipeline-processing-22: Unused public methods and event fire overloads

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:1391`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:1395`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewerController.java:421`, `stroom-core-client/src/main/java/stroom/dispatch/client/RestError.java:55`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreenController.java:353`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/DraggableTreePanel.java:195`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorListController.java:261`, `stroom-core-client/src/main/java/stroom/task/client/event/OpenTaskManagerEvent.java:51`, `stroom-core-client/src/main/java/stroom/task/client/event/OpenTaskManagerEvent.java:58`, `stroom-core-client/src/main/java/stroom/node/client/event/OpenNodeEvent.java:49`

**Evidence:** A per-method git grep -ow across the repo finds no caller for setItemNoFromConsumer, setItemRangeSupplier, setNavigatorControlsVisible, getRawThrowable, SteppingScreenController.getMetaList, setOnDragCancel or getCurrentResultPageResponse. The only callers of OpenTaskManagerEvent.fire are fire(h, taskName) and fire(h, nodeName, taskName) in JobNodeActionsController:208/215. fire(h) and fire(h, node, task, user) are unused, so its userName is always null. OpenNodeEvent.fire(h, String nodeName) is unused; only fire(h, JobNode) is called, from JobNodeListController:275.

**Fix:** Delete these methods and overloads. Then drop OpenTaskManagerEvent's userName and whatever reads it in TaskManagerPlugin, and OpenNodeEvent's name-only constructor.

**Verifier:** grep -rnw repo-wide finds only the declarations of setItemNoFromConsumer (DataViewerController:1391), setItemRangeSupplier (:1395), setNavigatorControlsVisible (SourceViewerController:421), getRawThrowable (RestError:55), setOnDragCancel (DraggableTreePanel:195), getCurrentResultPageResponse (ProcessorListController:261) and getMetaList (SteppingScreenController:353). The only OpenTaskManagerEvent.fire callers are JobNodeActionsController:208/215 (the taskName and nodeName+taskName overloads), so the userName overload and fire(h) are unused. The only OpenNodeEvent.fire caller is JobNodeListController:275 (the JobNode overload).

### data-pipeline-processing-23: Commented-out code blocks

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/SourceTabController.java:88`, `stroom-core-client/src/main/java/stroom/data/client/component/DocRefCell.java:402`, `stroom-core-client/src/main/java/stroom/data/client/component/UserRefCell.java:290`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:311`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewerController.java:502`, `stroom-core-client/src/main/java/stroom/data/client/component/MetaBrowserController.java:447`, `stroom-core-client/src/main/java/stroom/data/client/component/SourceViewerController.java:157`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTabController.java:85`, `stroom-core-client/src/main/java/stroom/data/client/AbstractTabPlugin.java:157`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/PipelineModel.java:423`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/PipelineModel.java:466`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/DefaultPipelineTreeBuilder.java:33`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructureController.java:635`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingScreenController.java:275`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorsController.java:80`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorsController.java:420`, `stroom-core-client/src/main/java/stroom/node/client/NodeClient.java:110`, `stroom-core-client/src/main/java/stroom/task/client/TaskProgressUtil.java:70`, `stroom-core-client/src/main/java/stroom/pipeline/client/PipelinePlugin.java:108`

**Evidence:** `grep -rnE '^\s*//\s*[a-zA-Z_.()]+.*(;|\{|\})\s*$'` over the slice finds whole commented-out methods: SourceTabController.setSourceKey, getTextFromDocRef in DocRefCell and UserRefCell, PipelineModel.debugLinks (two versions), DefaultPipelineTreeBuilder.log, ProcessorsController.setPermissions, a NodeClient overload and a TaskProgressUtil debug block. It also finds single dead statements, plus GWT.log lines in DataViewerController, MetaBrowserController and SourceViewerController.

**Fix:** Delete the commented-out code.

**Verifier:** I spot-checked SourceTabController:88 (commented setSourceKey), DocRefCell:402 (getTextFromDocRef), PipelineModel:423 (debugLinks), DefaultPipelineTreeBuilder:33 (log block), ProcessorsController:80 (selectedProcessor field), NodeClient:110 (listAllNodes overload), TaskProgressUtil:70 (GWT::log block), PipelinePlugin:108 (highlight) and AbstractTabPlugin:157 (sourceTabController.onClose). All are commented-out code.

### explorer-documents-shell-22: Events that are never fired, or that one controller fires to itself through the bus

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/about/client/event/ShowAboutEvent.java:25`, `stroom-core-client/src/main/java/stroom/about/client/AboutPlugin.java:50`, `stroom-core-client/src/main/java/stroom/about/client/AboutPlugin.java:67`, `stroom-core-client/src/main/java/stroom/explorer/client/component/NavigationController.java:179`, `stroom-core-client/src/main/java/stroom/explorer/client/component/NavigationController.java:181`, `stroom-core-client/src/main/java/stroom/explorer/client/component/NavigationController.java:228`, `stroom-core-client/src/main/java/stroom/explorer/client/component/NavigationController.java:230`, `stroom-core-client/src/main/java/stroom/explorer/client/event/FocusExplorerFilterEvent.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/event/FocusExplorerTreeEvent.java:1`

**Evidence:** - ShowAboutEvent: AboutPlugin adds a handler (getEventBus().addHandler(ShowAboutEvent.getType(), this)) and implements onShow, but no file calls ShowAboutEvent.fire or new ShowAboutEvent. The menu item calls showAbout() directly.
- FocusExplorerFilterEvent and FocusExplorerTreeEvent: the only files naming them besides themselves are NavigationController, which fires them from its own KeyBinding commands (lines 179 and 181) and listens to them itself (lines 228 and 230), just to call view.focusQuickFilter() and explorerTree.focus().

**Fix:** Delete ShowAboutEvent, and drop AboutPlugin's handler and implements clause. Delete FocusExplorerFilterEvent and FocusExplorerTreeEvent, and have the KeyBinding commands call view.focusQuickFilter() and explorerTree.focus() directly. These are removals, so ask first.

**Verifier:** ShowAboutEvent is named only in its own file and AboutPlugin (:50 addHandler, :67 onShow); nothing fires it, and the menu item calls showAbout() directly (:62). FocusExplorerFilterEvent and FocusExplorerTreeEvent are named only by NavigationController, which fires them at :179/:181 and listens to them at :228/:230, calling view.focusQuickFilter() and explorerTree.focus().

### explorer-documents-shell-23: Unused public methods, fields and a @UiField

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/ExplorerCell.java:72`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTreeController.java:82`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTreeController.java:119`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTreeController.java:473`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTreeController.java:514`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:121`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:279`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:377`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:382`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:446`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:624`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:698`, `stroom-core-client/src/main/java/stroom/main/client/DynamicStyles.java:50`, `stroom-core-client/src/main/java/stroom/analytics/client/component/NotificationList.java:48`, `stroom-core-client/src/main/java/stroom/annotation/client/DurationLabel.java:38`

**Evidence:** Call-site search over the whole repo (`.name(`, `::name`, bare `name(`, plus template attributes for setters) finds no caller for:
- ExplorerCell.getTickBoxClassName()
- ExplorerTreeController.setOnData(...), so the onData field (82) and its call (473) are dead too, and isIncludeNullSelection()
- DataGridUtil.colouredCellExtractor, formatAsIECByteString, colourSwatchColumnBuilder, iecByteStringColumnBuilder, createTickBoxExtractor, feedRefColumnBuilder and toYesNoFunc
- DynamicStyles.remove(SafeHtml).
NotificationList's `@UiField PagerPanel pager` is named only in its declaration (the template's ui:field="pager" exists only to bind it). DurationLabel.span(...) is public but called only by its own append(...).

**Fix:** Delete the listed methods, the onData field and callback, and NotificationList's pager @UiField (keep the PagerPanel in the template without ui:field). Make DurationLabel.span private. Ask first, as these are removals.

**Verifier:** git grep -nw finds only declarations for getTickBoxClassName (ExplorerCell:72), setOnData (ExplorerTreeController:119; its onData field at :82 and call at :473 are therefore dead), isIncludeNullSelection (:514), and DataGridUtil's colouredCellExtractor, formatAsIECByteString, colourSwatchColumnBuilder, iecByteStringColumnBuilder, createTickBoxExtractor, feedRefColumnBuilder and toYesNoFunc. DynamicStyles.remove has no caller (the only external calls are DynamicStyles.put). NotificationList.java:48 `PagerPanel pager` appears nowhere else in the file. DurationLabel.span is called only by its own append (:35); external users call append and div.

### explorer-documents-shell-24: Dead and leftover CSS: #main and #content rules, the non-BEM unstyled 'docRefLinkIcon' class, commented-out declarations

*low, effort small, adjusted.*

**Where:** `stroom-app/src/main/resources/ui/css/app-page.css:102`, `stroom-app/src/main/resources/ui/css/app-page.css:107`, `stroom-app/src/main/resources/ui/css/stroom-control.css:46`, `stroom-core-client/src/main/java/stroom/welcome/client/component/WelcomeTab.ui.xml:8`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:956`, `stroom-app/src/main/resources/ui/css/full-screen.css:34`, `stroom-app/src/main/resources/ui/css/iframe.css:30`

**Evidence:** - app-page.css styles `#main` and `#content`, but no host page (stroom-core/.../servlet/app.html, ui/*.html) and no Java or JS creates elements with those ids.
- stroom-control.css:46-60 styles `.stroom .stroom-control-grid ...`, but no element anywhere gets the class `stroom` (grep of styleName, addStyleName, class= and the host pages), so the rules never match; WelcomeTab.ui.xml:8 still sets stroom-control-grid on its table.
- 'docRefLinkIcon' is passed as an icon class at DocumentPluginEventManager.java:956 (and in DocRefCell and FeedRefCell, in the data slice). It is camelCase, styled by no sheet, and missed by TestStylesheets because it is a method argument.
- full-screen.css and iframe.css keep commented-out declarations.

**Fix:** Delete the #main and #content rules in app-page.css. Drop 'docRefLinkIcon' or rename it to a styled BEM element (e.g. doc-ref-link__icon) in all three callers. Remove the commented-out declarations in full-screen.css and iframe.css. Keep `.stroom .stroom-control-grid`: the html root carries class 'stroom' from ThemeCssUtil. Whether WelcomeTab should move onto its own welcome-tab__build-info rules instead is a separate BEM point.

**Verifier:** Refuted in part: the `.stroom .stroom-control-grid` rules do match. app.html:20 is `<html lang="en" class="@ROOT_CLASS@">`, and AppServlet replaces that with ThemeCssUtil.getCurrentPreferenceClasses, whose StringJoiner starts with .add("stroom") (ThemeCssUtil.java:58-59). So the root element has class 'stroom', and WelcomeTab's stroom-control-grid table is styled. The rest is confirmed. No host page or code creates id="main" or id="content" (app.html ids are __gwt_historyFrame, loading*, logo), so app-page.css:102/107 are dead. "docRefLinkIcon" appears only as a string argument in DocumentPluginEventManager:956, DocRefCell:218 and FeedRefCell:219, and no sheet styles it. full-screen.css:34 and iframe.css:28 hold commented-out declarations.

### explorer-documents-shell-25: Commented-out code blocks

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:231`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:288`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:699`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:716`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPluginEventManager.java:1162`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPlugin.java:81`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPlugin.java:492`, `stroom-core-client/src/main/java/stroom/folder/client/FolderRootPlugin.java:65`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:211`, `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationResourceClient.java:81`, `stroom-core-client/src/main/java/stroom/hyperlink/client/HyperlinkEventHandlerImpl.java:256`, `stroom-core-client/src/main/java/stroom/explorer/client/TickBoxSelectionModel.java:140`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerTreeController.java:497`, `stroom-core-client/src/main/java/stroom/main/client/GlobalKeyHandlerImpl.java:42`, `stroom-core-client/src/main/java/stroom/main/client/component/MainController.java:152`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ExecutionSchedulesController.java:108`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEditController.java:218`, `stroom-core-client/src/main/java/stroom/importexport/client/component/ImportConfigConfirmController.java:545`

**Evidence:** I grepped for commented-out statements and declarations.
- Whole methods are commented out: DocumentPluginEventManager's onCloseTab and onCloseAllTabs handlers (231-254), a Hyperlink builder (288-297), deleteDocument (699-709), create callback (716-724) and the tab menu future (1162-1186); DocumentPlugin's copies of the same (81-99, 492-502); FolderRootPlugin's old onBind (65-97, which references the GWTP-era onBind); DataGridUtil's three MyDataGrid helpers (211-233); AnnotationResourceClient.getAnnotationByRef (81-90).
- Plus single statements in TickBoxSelectionModel, ExplorerTreeController, GlobalKeyHandlerImpl, MainController ('// refresh();'), ExecutionSchedulesController (a field), MarkdownEditController and ImportConfigConfirmController.

**Fix:** Delete the commented-out blocks; git history keeps them. Commented-out GWT.log debugging lines can go too.

**Verifier:** Spot-checked: DocumentPluginEventManager.java:231 `// public void onCloseTab(...)` block; FolderRootPlugin.java:65 `//        super.onBind();` GWTP-era block; DataGridUtil.java:211 commented-out addResizableTextColumn on MyDataGrid; AnnotationResourceClient.java:81 commented-out getAnnotationByRef; MainController.java:152 `// refresh();`; ExecutionSchedulesController.java:108 commented-out field.

### explorer-documents-shell-26: Methods used only by workbench stories or tests

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/core/client/LocationManager.java:56`, `stroom-core-client/src/main/java/stroom/ui/config/client/UiConfigCache.java:78`, `stroom-core-client/src/main/java/stroom/explorer/client/component/TypeFilterController.java:112`

**Evidence:** The only callers of:
- LocationManager.removeWindowClosingHandler() are the workbench's ScreenHarness and StoryLocationManager
- UiConfigCache.stopRefreshing() is ScreenHarness
- TypeFilterController.setOnHidden(...) is TypeFilterStories.
No production code calls any of them.

**Fix:** Keep them if they are deliberate harness hooks, but say so in their javadoc ('for the workbench's harness'). Otherwise move the clean-up behind dispose or onDispose so that production code uses the same path. Drop TypeFilterController.setOnHidden if the story can observe hiding another way.

**Verifier:** git grep -nw: removeWindowClosingHandler is called only from workbench ScreenHarness.java:665 (and named in StoryLocationManager's doc), stopRefreshing only from ScreenHarness.java:662, and TypeFilterController.setOnHidden only from TypeFilterStories.java:156. Their javadoc does not say they are harness hooks.

### floormap-pathways-stores-18: Commented-out code blocks in the trace overview, traces list and node monitoring plugin

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:352`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.java:926`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesList.java:332`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesList.java:352`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeMonitoringPlugin.java:94`

**Evidence:** - TraceOverviewChart.java:352-398 is a commented-out `onBrowserEvent` override (47 lines).
- TraceOverviewChart.java:926-997 is about 70 lines of commented sample HTML (`<div class="operation-item " ... style="left: 0%; width: 100%;">`).
- TracesList.java:332 and :352 are `//        dataGrid.sort(column);`.
- NodeMonitoringPlugin.java:94-102 holds commented-out openNode/openNodeJob methods.

**Fix:** Delete the commented blocks.

**Verifier:** Confirmed: TraceOverviewChart.java:352 starts a commented-out onBrowserEvent override, and :926-997 is commented sample HTML. TracesList.java:332 and :352 are `//        dataGrid.sort(column);`. NodeMonitoringPlugin.java:94-102 holds the commented-out openNode and openNodeJob.

### floormap-pathways-stores-19: OpenFeedEvent and CopyFeedUrlEvent carry unused overloads, builders and fields

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:53`, `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:60`, `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:93`, `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:97`, `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:101`, `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:107`, `stroom-core-client/src/main/java/stroom/feed/client/CopyFeedUrlEvent.java:62`, `stroom-core-client/src/main/java/stroom/feed/client/CopyFeedUrlEvent.java:68`

**Evidence:** `git grep -nE "OpenFeedEvent\.fire\("` finds only the 3-argument form (OpenLinkUtil.java:117, RefCellsController.java:74). The 4- and 5-argument fire overloads, `builder()`/`Builder` and `getSelectedTab()` have no callers. fullScreen is therefore always false, though FeedPlugin.java:66 reads isFullScreen(). `CopyFeedUrlEvent.builder`/`Builder` have no callers either.

**Fix:** Delete the two extra fire overloads, both builders and getSelectedTab. Drop the fullScreen and selectedTab fields, and in FeedPlugin pass false.

**Verifier:** The only OpenFeedEvent.fire calls are the 3-argument ones (OpenLinkUtil.java:117, RefCellsController.java:74). No caller uses OpenFeedEvent.builder, CopyFeedUrlEvent.builder or getSelectedTab(). FeedPlugin.java:67 reads isFullScreen(), which is always false.

### floormap-pathways-stores-20: Seven of FloorMapAria's public helpers are never called

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:71`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:78`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:186`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:202`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:216`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:267`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:285`

**Evidence:** `git grep -ohE "FloorMapAria\.[a-zA-Z]+"` finds calls only to focusFirstFocusable, group, labelInnerControl, labelledBy, labelledByInnerControl and uniqueId. These have no callers outside the class: label(UIObject), label(Element), labelledByCell (called only by labelledByCellInnerControl), layoutTable, labelledByCellInnerControl, hide and liveRegion. There are no static imports. ISSUES.md:1988 and :2053 already record some of them.

**Fix:** Delete the seven methods. The rest is generic ARIA plumbing; see floormap-pathways-stores-30.

**Verifier:** git grep finds no call in code to FloorMapAria.label, labelledByCell, layoutTable, labelledByCellInnerControl, hide or liveRegion. The only hits are in ISSUES.md and the component-migration notes, and there is no static import.

### floormap-pathways-stores-21: SqlTemporalStoreGinjector's accessor is unused, since the plugin is already an eager singleton

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/sqlstore/client/gin/SqlTemporalStoreGinjector.java:23`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppGinjectorUser.java:147`

**Evidence:** `git grep getSqlTemporalStorePlugin` finds only the declaration, which is marked `@SuppressWarnings("unused")`. SqlTemporalStoreModule calls bindPlugin, which is `bind(plugin).asEagerSingleton()` (PluginModule.java:26). No other area in the slice has a Ginjector.

**Fix:** Delete SqlTemporalStoreGinjector and remove it from AppGinjectorUser's extends list.

**Verifier:** getSqlTemporalStorePlugin is referenced only at its declaration (SqlTemporalStoreGinjector.java:26). SqlTemporalStoreModule:26 calls bindPlugin, and the interface is used only in AppGinjectorUser's extends list (:68, :147).

### floormap-pathways-stores-22: DocRefList and its controller have an unused setRefreshVisible, and options only ever given fixed values

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefList.java:64`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefList.java:116`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefListController.java:52`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefListController.java:79`

**Evidence:** `git grep setRefreshVisible` finds only the two declarations. initTableColumns/addColumn(columnName, hasOpenLink) has one caller, `importList.initTableColumns("Document Name", true)` (ImportsTabController.java:77).

**Fix:** Delete setRefreshVisible from both classes. Drop the parameters by making the column fixed in DocRefList, and the buttons fixed in its template (see floormap-pathways-stores-14).

**Verifier:** git grep -nw setRefreshVisible finds only DocRefList.java:116 and DocRefListController.java:79-80, which delegates to it. initTableColumns has the one caller, at ImportsTabController.java:77, with fixed arguments.

### floormap-pathways-stores-23: Unused @UiField pager in ConstraintList and ScriptDependencyList

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pathways/client/component/ConstraintList.java:48`, `stroom-core-client/src/main/java/stroom/script/client/component/ScriptDependencyList.java:43`

**Evidence:** A script listed every @UiField whose name appears only once in its class. `PagerPanel pager` is never used in either class. The template's `ui:field="pager"` exists only to bind it.

**Fix:** Remove the fields and the ui:field attributes on the PagerPanels.

**Verifier:** `PagerPanel pager` appears only at its declaration in ConstraintList.java:49 and ScriptDependencyList.java:44.

### floormap-pathways-stores-24: An unused field in TracesPlugin, and public methods used only by tests

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pathways/client/TracesPlugin.java:37`, `stroom-core-client/src/main/java/stroom/floormap/client/cluster/FloorMapCluster.java:180`, `stroom-core-client/src/main/java/stroom/floormap/client/cluster/FloorMapClusterOverlay.java:613`, `stroom-core-client/src/main/java/stroom/floormap/client/editor/FloorMapEditorModel.java:274`, `stroom-core-client/src/main/java/stroom/floormap/client/geometry/FloorMapMarker.java:95`, `stroom-core-client/src/main/java/stroom/floormap/client/overlay/FloorMapHighlight.java:112`, `stroom-core-client/src/main/java/stroom/floormap/client/playback/FloorMapHistogramBuckets.java:111`

**Evidence:** TracesPlugin assigns `this.securityContext = securityContext` (:51) and never reads it. The fan-in script found these methods called only from their JUnit tests (TestFloorMapClusterOverlay, TestFloorMapEditorModel, TestFloorMapMarker, TestFloorMapHighlight, TestFloorMapHistogramBuckets): maxSizeFactor, getClusteredEntityCount, getServerEntriesForSelectedFact, tailOverhang, isHighlighted and bucketIndex. They may be deliberate test seams.

**Fix:** Remove securityContext's field and constructor parameter from TracesPlugin. For the test-only methods, either make them package-private test seams with a comment saying so, or delete them and have the tests go through the methods the app calls.

**Verifier:** TracesPlugin.java:37 and :51 assign securityContext, and nothing reads it. git grep -nw outside /test/ finds only the declarations of maxSizeFactor, getClusteredEntityCount, getServerEntriesForSelectedFact, tailOverhang, isHighlighted and bucketIndex.

### security-settings-21: PromptEvent and PromptCallback are fired only by workbench stories

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/alert/client/event/PromptEvent.java:23`, `stroom-core-client/src/main/java/stroom/alert/client/event/PromptCallback.java:19`, `stroom-core-client/src/main/java/stroom/alert/client/AlertPlugin.java:56`

**Evidence:** `grep -rlw 'PromptEvent.fire'` finds only stroom-gwt/stroom-gwt-workbench/.../widgets/dialogs/AlertDialogStories.java. AlertPlugin handles it (line 56), but no app code fires it.

**Fix:** Delete PromptEvent, PromptCallback, AlertPlugin's prompt handler and the prompt stories (PromptDialogStories, AlertDialogStories' prompt), or record why they are kept.

**Verifier:** Outside its own files, PromptEvent appears only in AlertPlugin.java:23,37,56 (the handler) and in the workbench AlertDialogStories.java:155 (the only fire) and PromptDialogStories. No app code fires it.

### security-settings-22: @UiFields that the Java never uses

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAi.java:86`, `stroom-core-client/src/main/java/stroom/preferences/client/component/KeyBindingPreferences.java:52`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/FieldList.java:49`

**Evidence:** `Label emptyPrompt` (AskStroomAi), `PagerPanel pager` (KeyBindingPreferences) and `PagerPanel pager` (FieldList) each appear once in their class, at the declaration (`grep -cw` = 1). Their templates' ui:field attributes are the only other mentions.

**Fix:** Remove the @UiField declarations and the ui:field attributes from AskStroomAi.ui.xml:23, KeyBindingPreferences.ui.xml:21 and FieldList.ui.xml:6.

**Verifier:** `grep -cw` gives 1 for emptyPrompt in AskStroomAi.java, pager in KeyBindingPreferences.java and pager in FieldList.java: the declaration only. AskStroomAi.ui.xml:24 sets emptyPrompt's text and style in the template.

### security-settings-23: Large commented-out code blocks left in controllers and a plugin

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/UserPermissionReportController.java:87-255`, `stroom-core-client/src/main/java/stroom/security/client/UserPermissionsReportPlugin.java:79-124`, `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateTabController.java:63-715`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroupsController.java:110-530`, `stroom-core-client/src/main/java/stroom/config/global/client/component/ManageGlobalPropertyListController.java:62-438`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionController.java:89-223`, `stroom-core-client/src/main/java/stroom/security/client/component/ApiKeysController.java:91-99`, `stroom-core-client/src/main/java/stroom/security/client/component/UsersController.java:46-129`, `stroom-core-client/src/main/java/stroom/config/global/client/ListDataProvider.java:53-108`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleEditor.ui.xml:12`

**Evidence:** `grep -nE '^\s*//[^/].*(;|\{|\})'` (code-like // comments, not ///) counts 44 lines in UserPermissionReportController, 23 in UserPermissionsReportPlugin, 20 in ContentTemplateTabController (including `//    private DataRetentionPresenter dataRetentionController;` at 93 and `//        view.setExpressionView(expressionTree);` at 121), 19 in UserAndGroupsController, 16 in ManageGlobalPropertyListController, 13 in DataRetentionController and 11 in ApiKeysController (the QuickFilter tooltip block at 91-99). RuleEditor.ui.xml:12 keeps a commented-out SimplePanel.

**Fix:** Delete the commented-out code; anything worth keeping is in git history.

**Verifier:** The commented-out code is there. ContentTemplateTabController:93 is `//    private DataRetentionPresenter dataRetentionController;` and :121 is `//        view.setExpressionView(expressionTree);`. RuleEditor.ui.xml:12 has a commented SimplePanel. ApiKeysController:91-99 has the tooltip block, UsersController has the editButton lines (46, 84, 124-129), and ListDataProvider:53-57 has setPartialList. UserPermissionReportController has 63 // lines. The per-file counts in the finding don't match my regex exactly, but the defect is real.

### widgets-1-04: Commented-out code blocks (fade transition, old help-text logic, popup positioning, equals/hashCode, logging)

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:41`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:61`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:87`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:124`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupUtil.java:230`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupUtil.java:147`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:658`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:687`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:738`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:759`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:786`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FormGroup.java:801`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/GroupHeading.java:31`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/IconCell.java:97`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionPopup.java:210`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/event/ShowPopupEvent.java:272`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/IconMenuItem.java:155`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/ColumnBuilder.java:57`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/Dialog.java:218`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/AbstractTabBar.java:568`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/AbstractTabBar.java:611`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/AbstractTabBar.java:831`

**Evidence:** grep -nE '^\s*//.*(;|\{|\})\s*$' per file found: PopupUtil 73 lines (a whole commented-out method at 230-360), LayerContainerImpl 63 (TransitionTimer class and fadeTransition), FormGroup 41 (old help-text and add/clear logic), GroupHeading 13 (equals/hashCode/toString), then SelectionPopup, IconCell (5 each), AbstractTabBar (4: GWT.log), ShowPopupEvent (3), IconMenuItem (3, which still references the deleted Preset), ColumnBuilder (2), Dialog (2).

**Fix:** Delete the commented-out blocks. Where a comment carries a reason (e.g. Dialog.java:217's constraint note), keep the prose and drop the code.

**Verifier:** Spot-checked the cited lines: PopupUtil:230 is a commented-out centerPopup method; FormGroup:658 is old helpHTML logic; IconMenuItem:155-157 references the deleted Preset; GroupHeading:31 is a commented-out equals; LayerContainerImpl:41/61/87/124 are the transition timer, opacity and fadeTransition code.

### widgets-1-05: Unused tab events and handler interfaces: tab CloseEvent, MaximiseRequestEvent, HasMaximiseRequestHandlers

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/event/CloseEvent.java:1`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/event/MaximiseRequestEvent.java:1`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/event/HasMaximiseRequestHandlers.java:1`

**Evidence:** Fan-in loop (grep -rlw over all client modules, stories and tests): HasMaximiseRequestHandlers 0 references. MaximiseRequestEvent is referenced only by HasMaximiseRequestHandlers. stroom.widget.tab.client.event.CloseEvent has no import or use anywhere: grep 'tab.client.event.CloseEvent' returns nothing, and the other 'CloseEvent' hits are GWT's logical CloseEvent or WindowCloseEvent.

**Fix:** Delete the three files.

**Verifier:** git grep -w CloseEvent within stroom/widget/tab finds only CloseEvent.java itself. No file imports stroom.widget.tab.client.event.CloseEvent, and there are no wildcard imports of that package. HasMaximiseRequestHandlers and MaximiseRequestEvent appear only in their own files, the gwt-inventory oracles and docs. ContentTabPaneController and MainController use MaximiseEvent, a different class.

### widgets-1-06: DialogEvent is handled by PopupManager but never fired

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/event/DialogEvent.java:27`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupManager.java:34`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupManager.java:46`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupManager.java:127`

**Evidence:** grep -rn -w DialogEvent over the source (build/ excluded): only DialogEvent.java and PopupManager (implements DialogEvent.Handler, addHandler, onDialogAction). Its old firers (CreateDocumentPresenter, CreateUserPresenter) exist only in stroom-gwt-workbench/build/gwt/* copies of deleted presenters.

**Fix:** Delete DialogEvent and PopupManager's handler (the implements clause, the addHandler at :46 and onDialogAction at :127).

**Verifier:** git grep -n DialogEvent finds only DialogEvent.java and PopupManager (the implements clause at :34, addHandler at :46 and onDialogAction at :127). DialogEvent.fire is never called; the other hits are the different Show*DialogEvent classes.

### widgets-1-07: Unused menu code: GroupHeading (+ GroupHeadingAppearance, .menu__group-heading), and MenuBuilder's withSimpleMenuItem/withSimpleMenuItemIf/withInfoMenuItem/withInfoMenuItemIf

*low, effort small, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/GroupHeading.java:19`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuItemCell.java:49`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuItemCell.java:94`, `stroom-app/src/main/resources/ui/css/menu.css:71`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuBuilder.java:50`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuBuilder.java:85`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuBuilder.java:97`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuBuilder.java:105`

**Evidence:** GroupHeading is never constructed: grep -w GroupHeading finds only MenuItemCell's instanceof branch and GroupHeadingAppearance, with no stories or tests. So the 'menu__group-heading' rule at menu.css:71 styles nothing. Calls of 'withItemIf(', 'withSimpleMenuItemIf(', 'withInfoMenuItem(' and 'withInfoMenuItemIf(' outside MenuBuilder: 0 each. withIconMenuItemIf has 7 callers, withSimpleMenuItem 2 and withSeparatorIf 1.

**Fix:** Delete GroupHeading, MenuItemCell's GroupHeading branch and GroupHeadingAppearance, the .menu__group-heading rule (menu.css:71, and the mention at :29 and in Menu.java:62), and withSimpleMenuItem, withSimpleMenuItemIf, withInfoMenuItem and withInfoMenuItemIf, along with the commented-out callers. Keep withItemIf (used by withSeparatorIf) or make it private.

**Verifier:** GroupHeading is never constructed: the only references are MenuItemCell:49-50 and :94-102 (GroupHeadingAppearance), menu.css:29 and :71, and the Menu.java:62 doc. withInfoMenuItem and withInfoMenuItemIf have no callers. But withItemIf is not dead: withSeparatorIf (MenuBuilder:121) calls it, and UserAndGroupHelper:177 calls withSeparatorIf. withSimpleMenuItemIf is called by withSimpleMenuItem (:78). And withSimpleMenuItem itself is dead: its only 'callers' are commented out (ContentTemplateTabController:264, DataRetentionPolicyController:247), not 2 live ones as the evidence says.

### widgets-1-08: Unused value-spinner model classes: ConstrainedLong (no users), EditableLong (only ConstrainedLong and one story)

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/cell/valuespinner/shared/ConstrainedLong.java:19`, `stroom-core-client-widget/src/main/java/stroom/cell/valuespinner/shared/EditableLong.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/ViewStateStories.java:329`

**Evidence:** Fan-in loop: 'ConstrainedLong' 0 references outside its own file in any module, tests and stories included. grep -w EditableLong: ConstrainedLong.java and ViewStateStories.java:329 only (story-only use). EditableInteger is used by ProcessorList, JobNodeList and NodeStatusList.

**Fix:** Delete ConstrainedLong and EditableLong, and move the story to EditableInteger.

**Verifier:** git grep -w ConstrainedLong finds only its own file. EditableLong is used only by ConstrainedLong and ViewStateStories.java:21/:329 (story-only).

### widgets-1-09: DataGrid keeps MyDataGrid-compat and unused API: setSkipRowHoverCheck (no-op), setRowHeight, isVirtual, addPercentColumn (stories only); InfoColumn still looks for MyDataGrid's td

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:1098`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:800`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:793`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:386`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:395`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/InfoColumn.java:51`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/InfoColumn.java:66`

**Evidence:** Per-method grep -rlw over the client modules, stories and tests: setSkipRowHoverCheck, setRowHeight and isVirtual appear only in DataGrid.java. setSkipRowHoverCheck's doc says 'Does nothing. Kept for compatibility with MyDataGrid'. addPercentColumn appears only in DataGrid.java and DataGridStories.java. InfoColumn.isCell checks for 'td' with the comment "MyDataGrid's td", but MyDataGrid was deleted (COMPONENT_MODEL_DESIGN, Data grid).

**Fix:** Delete setSkipRowHoverCheck, setRowHeight (and the rowHeight field, if virtual mode measures it), isVirtual and both addPercentColumn overloads (moving the story to addColumn with a width). Drop the 'td' test from InfoColumn.isCell.

**Verifier:** setSkipRowHoverCheck (DataGrid:1098, an empty body with the doc 'Kept for compatibility with MyDataGrid'), setRowHeight (:800) and isVirtual (:793) have no callers outside DataGrid.java. addPercentColumn is called only at DataGridStories:911-912. rowHeight is read only by the package-private getFixedRowHeight. No CellTable or CellList is left in the repo, so InfoColumn.isCell's 'td' test at :66 (comment at :51) is a MyDataGrid leftover.

### widgets-1-10: DataGridSortIcon duplicates SortIcon's markup and constants; SortIcon.UNSORTED is unused

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGridSortIcon.java:22`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/SortIcon.java:28`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/SortIcon.java:31`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/GridHtml.java:126`

**Evidence:** DataGridSortIcon's doc says 'The markup matches [SortIcon]'s so the same CSS applies'. It redeclares SORT_ASCENDING/SORT_DESCENDING and the same icons. Its only caller is GridHtml:126. SortIcon.append is used by stroom/query/client/ColumnHeaderHtmlUtil:60,68. grep UNSORTED: only its declaration and two comments ('not SortIcon.UNSORTED').

**Fix:** Delete DataGridSortIcon and have GridHtml call SortIcon.append (or give SortIcon a render(ascending, order) returning SafeHtml). Delete SortIcon.UNSORTED.

**Verifier:** DataGridSortIcon.render builds the same markup (column-header__sort, column-header__sort-order, icon-wrap) as SortIcon.append(sb, ascending, order), and redeclares SORT_ASCENDING/SORT_DESCENDING. Its only caller is GridHtml:126. SortIcon.UNSORTED (:31) appears elsewhere only in comments, at ColumnsManager:637 and QueryTableColumnsManager:557.

### widgets-1-11: FindResultLists.create is unused: every caller declares its grid and calls wire

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/FindResultLists.java:52`

**Evidence:** grep 'FindResultLists.(create|wire)': only wire is called (FindAnnotationList:64, DocumentList:69, FindDocResultList:57, FindInContent:103, AiChatHistoryResultList:63).

**Fix:** Delete create(...) and keep wire, which matches the declare-in-template rule.

**Verifier:** Callers of FindResultLists call only wire: AiChatHistoryResultList:63, FindAnnotationList:64, DocumentList:69, FindDocResultList:57 and FindInContent:103. create (:49) has no callers, and PAGE_SIZE is then used only by create and a doc comment.

### widgets-1-12: EventBinder: a presenter-era bind/unbind helper in stroom.item.client wrapping handlers on widgets' own children (SelectionList, SelectionPopup, dashboard TabLayout)

*low, effort small, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/item/client/EventBinder.java:23`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionList.java:94`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionPopup.java:68`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/TabLayout.java:49`

**Evidence:** grep -w EventBinder: 3 users. In SelectionList:94 it registers quickFilter.addValueChangeHandler on the list's own child, a self-contained case-1 registration (COMPONENT_MODEL_DESIGN, Lifetimes) that needs no clean-up. The registerHandler/onBind/onUnbind vocabulary is the presenter lifecycle that Phase 7 replaced. It also sits in the selection-box package although TabLayout (dashboard) uses it.

**Fix:** Wire SelectionList's quick-filter handler and SelectionPopup's close handlers once in their constructors, and make TabLayout's tab-bar and settings handlers constructor-wired too, since they are its own children. Check whether FlexLayout:1703's unbind can then go. Delete EventBinder, or, if an attach-scoped registration is really needed, use COMPONENT_MODEL_DESIGN's AttachScope.

**Verifier:** EventBinder is used, so it is not dead code. SelectionList binds it in onLoad and unbinds in onUnload (:301/:306). SelectionPopup binds it on show and unbinds on hide (:193/:251). TabLayout binds it in its constructor, and FlexLayout:1703 calls tabLayout.unbind(). The rest of the finding holds: it is a presenter-style bind/onBind/registerHandler helper in the selection-box package (stroom.item.client), used by the dashboard's TabLayout, and the SelectionList/SelectionPopup registrations are on their own children, so they could be wired once in the constructors.

### widgets-2-08: xsd-browser.css styles 12 classes that no code sets

*low, effort small, confirmed.*

**Where:** `stroom-app/src/main/resources/ui/css/xsd-browser.css:186`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:264`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:270`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:312`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:324`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:342`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:366`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:384`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:402`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:408`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:420`, `stroom-app/src/main/resources/ui/css/xsd-browser.css:426`

**Evidence:** I scanned every class each slice sheet defines against all code (java, ui.xml, js, html). Unset: xsd-browser__node-type-box, __browser-title, __browser-title-selected (also not a BEM modifier), __title-attributes, __title-directives, __title-groups, __tree-01, __tree-04, __tree-07, __tree-08, __tree-10, __tree-11. XSDDisplay sets only tree-02, -03, -05, -06 and -09 (lines 472-561) and the title-/node- classes in getImage (604-645). No code builds these names from parts.

**Fix:** Delete the 12 rules. Confirm with the workbench class census (`--class-census`) on the XSD browser stories.

**Verifier:** No java, xml, js or html file in any stroom-* module contains any of the 12 class names. XSDDisplay builds class names only from whole literals (tree-02, -03, -05, -06 and -09, the title-* and node-* names), so none of the 12 is assembled from parts.

### widgets-2-09: TickBox keeps GWT CheckBox API that Stroom never uses

*low, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:89`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:207`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:215`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:228`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:281`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:311`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:322`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:334`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:344`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:358`, `stroom-core-client-widget/src/main/java/stroom/widget/tickbox/client/TickBox.java:388`

**Evidence:** TickBox (546 lines) still implements HasDirectionalSafeHtml, HasDirectionEstimator and IsEditor<LeafValueEditor<Boolean>> (line 89). grep for .asEditor(, .getDirectionEstimator(, .setDirectionEstimator(, .getTextDirection(, .setAccessKey(, .getFormValue( and .setFormValue( across all client modules and stories, less TickBox.java: 0 callers each. isChecked and setChecked are @Deprecated copies of getValue and setValue. The only .isChecked()/.setChecked( calls (FloorMapIconPicker.java:140-188) are on InputElement, not TickBox.

**Fix:** Remove the IsEditor, direction-estimator and HasDirectionalSafeHtml support (with the directionalTextHelper), the form value, setAccessKey and the deprecated isChecked and setChecked. Keep what HasValue, HasName, Focus and the templates need.

**Verifier:** TickBox.java:89-91 implements HasDirectionalSafeHtml, HasDirectionEstimator and IsEditor. Outside TickBox, nothing calls .asEditor(, .getTextDirection(, .setAccessKey(, .getFormValue( or .setFormValue(. The only get/setDirectionEstimator calls are inside GWT's own ValueBoxBase. The isChecked and setChecked callers (FloorMapIconPicker, ActivityEdit, RadioGroup, ControlsAddon) all call them on an InputElement, not a TickBox.

### widgets-2-10: Public methods with no caller outside their class: some are dead, others are used only internally and should be private

*low, effort medium, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/util/client/ElementUtil.java:215`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/HtmlBuilder.java:301`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/FutureImpl.java:52`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/KeyStroke.java:276`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/KeyStroke.java:286`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/LazyValue.java:111`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/RowActions.java:81`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/RowActions.java:87`, `stroom-core-client-widget/src/main/java/stroom/widget/layout/client/SplitPane.java:145`, `stroom-core-client-widget/src/main/java/stroom/widget/layout/client/SplitPane.java:249`, `stroom-core-client-widget/src/main/java/stroom/widget/layout/client/SplitPane.java:285`, `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/ClientDateUtil.java:192`, `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/ClientDateUtil.java:202`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/Calendar.java:176`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/CalendarUtil.java:100`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/CalendarUtil.java:148`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/CalendarUtil.java:169`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeConstants.java:77`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeModel.java:79`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeModel.java:281`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeModel.java:313`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/IntlDateTimeFormat.java:93`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/IntlDateTimeFormat.java:308`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/IntlDateTimeFormat.java:383`, `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/Spinner.java:171`, `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/Spinner.java:232`, `stroom-core-client-widget/src/main/java/stroom/widget/progress/client/Progress.java:69`, `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/event/ShowHelpEvent.java:118`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDNode.java:124`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/DataSelectionEvent.java:91`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorLines.java:157`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/ScrollbarMetrics.java:42`, `stroom-core-client-widget/src/main/java/stroom/editor/client/DelegatingAceCompleter.java:216`, `stroom-core-client-widget/src/main/java/stroom/svg/client/IconBox.java:80`, `stroom-core-client-widget/src/main/java/stroom/svg/client/IconBox.java:123`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/treelayout/AbegoTreeLayout.java:292`

**Evidence:** Index scan: each method name appears in no other file, with UiBinder attribute setters excluded after checking the templates. Examples: ElementUtil.getClientBottom, HtmlBuilder.appendEscapedLines, FutureImpl.setThrowable, KeyStroke.isAlt and isMeta, LazyValue.consumeIfInitialised, RowActions.isRowAction and isRowActionEvent, SplitPane.isVertical, isPaneHidden and getMaximised, ClientDateUtil.parseWithJavaFormat and parseWithJsFormat, Calendar.focusGrid, CalendarUtil.getDaysBetween, setToFirstDayOfMonth and resetTime, DateTimeConstants.getLanguages, DateTimeModel.getFirstDayOfMonth, getOffsetString and parseTimeZone, Spinner.getMinStep and setInitialSpeed, Progress.simpleProgress, ShowHelpEvent.Builder.withPopupPosition, XSDNode.getBase, DataSelectionEvent.isSingleSelect, IndicatorLines.getLocationAgnosticIndicator (and the locationAgnosticIndicator it fills), ScrollbarMetrics.getVerticalScrollBarWidth, DelegatingAceCompleter.MapKey.fromEditorId, fromModeName and getModeName. IntlDateTimeFormat.FormatOptions has 11 getters (getCalendar, getEra, getHour12, getWeekday, ...) and Builder setters (numberingSystem, hourCycle, dayPeriod, dateStyle, timeStyle) that are unused. AbegoTreeLayout.getLevelCount, getSizeOfLevel and getNodeExtentProvider are unused. IconBox.setWidget(Widget, SvgImage) and setDropDownWidget are public but called only by IconBox.addDropDown (the template's <svg:dropDown>).

**Fix:** Delete the methods nothing calls, e.g. ElementUtil.getClientBottom, HtmlBuilder.appendEscapedLines, FutureImpl.setThrowable, KeyStroke.isAlt and isMeta, LazyValue.consumeIfInitialised, SplitPane's isVertical, isPaneHidden and getMaximised, ClientDateUtil.parseWithJavaFormat, Calendar.focusGrid, CalendarUtil's getDaysBetween and setToFirstDayOfMonth, Spinner's getMinStep and setInitialSpeed, IndicatorLines.getLocationAgnosticIndicator, ScrollbarMetrics.getVerticalScrollBarWidth (with its field), MapKey.getModeName, AbegoTreeLayout.getNodeExtentProvider, and the unused IntlDateTimeFormat options. Make private the methods used only inside their class: RowActions' isRowAction and isRowActionEvent, CalendarUtil.resetTime, DateTimeModel's getFirstDayOfMonth, getOffsetString and parseTimeZone, ClientDateUtil.parseWithJsFormat, MapKey's fromEditorId and fromModeName, AbegoTreeLayout's getLevelCount and getSizeOfLevel, XSDNode.getBase, and IconBox's setWidget and setDropDownWidget. Keep the locationAgnosticIndicator field, which line 164 reads.

**Verifier:** No other file uses any of the listed names, but many are called inside their own class, so they are not dead. RowActions.isRowAction is called at line 89 and isRowActionEvent at line 104. CalendarUtil.resetTime is called at lines 103, 105 and 149. DateTimeModel.getFirstDayOfMonth is called at line 76, getOffsetString at 276, 292 and 303, and parseTimeZone at 282. ClientDateUtil.parseWithJsFormat is called at line 194. DelegatingAceCompleter's MapKey.fromEditorId and fromModeName are called at lines 134, 180 and 184. AbegoTreeLayout.getSizeOfLevel is called at line 632, and getLevelCount at 309. XSDNode.getBase is called at line 276. The field locationAgnosticIndicator is also read at IndicatorLines.java:164, so it must stay. The methods that really are dead include getClientBottom, appendEscapedLines, setThrowable, isAlt, isMeta, consumeIfInitialised, SplitPane's isVertical, isPaneHidden and getMaximised, parseWithJavaFormat, focusGrid, getDaysBetween, setToFirstDayOfMonth, getMinStep, setInitialSpeed, getLocationAgnosticIndicator, getVerticalScrollBarWidth, getModeName and getNodeExtentProvider.

### widgets-2-11: Commented-out code blocks

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/util/client/MySingleSelectionModel.java:195`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/Size.java:40`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/IntlDateTimeFormat.java:695`, `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/event/ShowHelpEvent.java:91`, `stroom-core-client-widget/src/main/java/stroom/editor/client/model/XmlFormatter.java:201`, `stroom-core-client-widget/src/main/java/stroom/editor/client/model/XmlFormatter.java:473`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:52`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:64`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorLines.java:53`, `stroom-core-client-widget/src/main/java/stroom/editor/client/DelegatingAceCompleter.java:94`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/Editor.java:353`, `stroom-core-client-widget/src/main/java/stroom/hyperlink/client/Hyperlink.java:107`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeModel.java:189`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/KeySequences.java:78`, `stroom-app/src/main/resources/ui/css/stroom-control.css:56`

**Evidence:** grep of commented statements, declarations and braces in the slice. Examples: MySingleSelectionModel.java:195-215 (a whole commented setSelected override and addDoubleClickHandler); Size.java:40-46 (commented set method); IntlDateTimeFormat.java:695-701 (commented JSNI getTimeZoneOffset); ShowHelpEvent.java:91-93 (commented getPopupPosition); XmlFormatter.java:201, 247 and 473-485; RightBar.java:52 and 64; Hyperlink.java:107, 117 and 124 (throwException); DateTimeModel.java:189 (refresh();); commented GWT.log debugging in IndicatorLines 53-61, DelegatingAceCompleter 94, 117 and 132, Editor 353, 354 and 429, and KeySequences 78 and 120; stroom-control.css:56 (/*padding: 0 10px 0 3px;*/).

**Fix:** Delete the commented code, and any comment that only names it.

**Verifier:** Spot checks confirmed commented-out code at each named place: MySingleSelectionModel.java:195 (a setSelected override), Size.java:40 (a set method), IntlDateTimeFormat.java:695 (a JSNI method), ShowHelpEvent.java:91, Hyperlink.java:107, DateTimeModel.java:189, RightBar.java:52 and 64, KeySequences.java:78 (a GWT.log call) and stroom-control.css:56.

### widgets-2-12: Empty or unused GWT modules and GWT stylesheet overrides in the widget module's resources

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/resources/stroom/vis/Vis.gwt.xml`, `stroom-core-client-widget/src/main/resources/stroom/websocket/WebSocket.gwt.xml`, `stroom-app-gwt/src/main/resources/stroom/app/App.gwt.xml:38`, `stroom-dashboard-gwt/src/main/resources/stroom/dashboard/DashboardApp.gwt.xml:36`, `stroom-core-client-widget/src/main/resources/stroom/editor/Editor.gwt.xml`, `stroom-core-client-widget/src/main/resources/stroom/widget/htree/HTree.gwt.xml`, `stroom-core-client-widget/src/main/resources/com/google/gwt/user/client/ui/CustomScrollPanel.css`, `stroom-core-client-widget/src/main/resources/com/google/gwt/user/client/ui/NativeHorizontalScrollbarTransparent.css`, `stroom-core-client-widget/src/main/resources/com/google/gwt/user/client/ui/NativeVerticalScrollbarTransparent.css`, `stroom-core-client-widget/src/main/resources/.gitignore`

**Evidence:** git ls-files has no java/stroom/vis or java/stroom/websocket source in any module. Vis.gwt.xml is inherited by nothing. WebSocket.gwt.xml (source paths client and shared, neither of which exists) is still inherited by App.gwt.xml:38 and DashboardApp.gwt.xml:36. Editor.gwt.xml, HTree.gwt.xml and WebSocket.gwt.xml declare <source path="shared"/> but no stroom/editor/shared, stroom/widget/htree/shared or stroom/websocket/shared directory exists. The three com/google/gwt/user/client/ui/*.css files shadow GWT's CustomScrollPanel and native-scrollbar resources (empty rules). Nothing in the repo uses CustomScrollPanel, NativeVerticalScrollbar, NativeHorizontalScrollbar or GWT's cellview DataGrid/CellTable (grep: 0). resources/.gitignore is empty.

**Fix:** Delete Vis.gwt.xml, WebSocket.gwt.xml and its two <inherits>, the three GWT CSS overrides and the empty .gitignore. Drop the shared source paths that have no directory.

**Verifier:** No gwt.xml inherits stroom.vis.Vis. App.gwt.xml:38 and DashboardApp.gwt.xml:36 still inherit stroom.websocket.WebSocket. find shows no java/stroom/vis, java/stroom/websocket, editor/shared or htree/shared source directory in any module. CustomScrollPanel.css has only empty rules. resources/.gitignore is 0 bytes.

### workbench-02: An empty file named `.java` is tracked in app/gin/dashboard

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/dashboard/.java`

**Evidence:** `git ls-files .../app/gin/dashboard/` lists `.java`, and `ls -la` shows it at 0 bytes (dated 22 Oct). It is probably left over from a broken rename.

**Fix:** Delete it with `git rm`.

**Verifier:** git ls-files lists app/gin/dashboard/.java, and ls -la shows it at 0 bytes, dated 22 October.

### workbench-03: Ginjector getters, story helpers and framework API methods that nothing calls

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/shell/ShellScreenGinjector.java:147`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/shell/ShellScreenGinjector.java:150`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/vis/VisStories.java:249`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/ai/AskStroomAiChat.java:72`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/ViewStateStories.java:279`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/menu/MenuPanelStories.java:54`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/manager/Popover.java:112`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/play/Keys.java:250`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/play/Keys.java:258`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/args/ArgType.java:94`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/args/ArgType.java:115`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/server/SourceFinder.java:63`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/preview/RunnerHooks.java:34`

**Evidence:** I counted word occurrences over all of stroom-gwt/ (main, test, test-runner JS; build/ excluded), and `git grep -w` confirmed each one. Every name below appears only at its declaration. ShellScreenGinjector: `getCopyDocumentController()` and `getMoveDocumentController()` are never called through `.getX()` or `::getX`; every other ginjector getter is. VisStories: `drawnGrid(Play,int,String)`. AskStroomAiChat: `isCreated()`. ViewStateStories: private `named(Widget,String)`. MenuPanelStories: `MENU = ".menu"` (QuickFilterHistoryStories:103 writes the literal itself). Framework: `Popover.isAnyOpen()`, `Keys.keyForName` and `Keys.keyForCode`, `ArgType.inlineRadio` and `ArgType.inlineCheck` (no story uses them, though ControlsAddon still renders the INLINE_* control types), `SourceFinder.getSourceRoots()` (server/SourceFinder.java:63), and `RunnerHooks.PLAY_STATUS_ATTRIBUTE` (its JSNI at line 53 writes 'data-play-status' literally). Nothing uses any of them, including stories and tests.

**Fix:** Delete the two ginjector getters and their imports, plus drawnGrid, isCreated, ViewStateStories.named and MenuPanelStories.MENU. In the framework, delete isAnyOpen, keyForName and keyForCode, and getSourceRoots. Either use PLAY_STATUS_ATTRIBUTE in the JSNI or delete it. inlineRadio and inlineCheck are Storybook-parity API: keep them only if that parity is a goal, otherwise delete them along with ControlType.INLINE_RADIO and INLINE_CHECK.

**Verifier:** I ran git grep -w over stroom-gwt, stroom-core-client, stroom-core-client-widget, stroom-app and stroom-dashboard-gwt. Each of getCopyDocumentController, getMoveDocumentController, drawnGrid, isCreated, isAnyOpen, keyForName, keyForCode, inlineRadio, inlineCheck, getSourceRoots and PLAY_STATUS_ATTRIBUTE appears only at its declaration. ViewStateStories.named (line 279) has no caller. MenuPanelStories.MENU (line 54) is found only at its declaration. RunnerHooks.java:53 writes 'data-play-status' literally.

### workbench-13: Template ui:fields that Java never uses

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/resources/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.ui.xml:10`, `stroom-gwt/stroom-gwt-workbench/src/main/resources/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.ui.xml:16`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.java:38`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.java:44`

**Evidence:** `@UiField ToolbarButton saveAs` and `@UiField ToolbarButton last` are bound but never read in ToolbarTemplate.java (the constructor wires only save, filter and first). The plays find 'Save As' by role (ToolbarStories:64), not by field.

**Fix:** Remove `ui:field="saveAs"` and `ui:field="last"` from the template, and the two @UiField declarations.

**Verifier:** ToolbarTemplate.java declares @UiField saveAs and last but its constructor wires only save, filter and first; nothing else reads those two fields.

## Naming (43)

### widgets-1-01: The document tabs' component CurveTabLayout sits in the widget module, named differently from its controller ContentTabPaneController in stroom.content.client.component

*high, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.java:38`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/component/CurveTabLayout.ui.xml:6`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/CurveTabLayoutPanel.java:24`, `stroom-core-client/src/main/java/stroom/content/client/component/ContentTabPaneController.java`

**Evidence:** grep -rln -w 'CurveTabLayout|CurveTabLayoutPanel': the only app user is stroom/content/client/component/ContentTabPaneController.java (plus workbench TopBarStories, AppShellStories and CurveTabBarStories). CurveTabLayout is app-specific (Hide/Show Sidebar button, the content area), but it lives in stroom.widget.tab.client.component in the widget module. UI-GUIDE says the pair is FooController + Foo side by side in the area's client.component package. Its name also describes the tab look ('Curve') rather than what the thing is (the content tab pane).

**Fix:** Rename CurveTabLayout to ContentTabPane (template ContentTabPane.ui.xml, BEM block content-tab-pane in content-tab-pane.css, replacing curve-tab-layout.css) and move it beside ContentTabPaneController in stroom-core-client stroom.content.client.component. Fold CurveTabLayoutPanel (a FlowPanel whose only job is focus forwarding) into it or into an AriaPanel-like root.

**Verifier:** git grep -w CurveTabLayout/CurveTabLayoutPanel: the only app user is stroom-core-client/.../content/client/component/ContentTabPaneController.java (plus TopBarStories, AppShellStories, CurveTabBarStories). CurveTabLayout.java's doc calls it 'The document tabs' layout: the Hide Sidebar button, the curved tab bar ... the selected tab's content', so it is app-specific. It sits in stroom.widget.tab.client.component in the widget module, not beside its controller. CurveTabLayoutPanel is a FlowPanel whose only addition is a focus-forwarding Runnable.

### dashboard-query-12: Same simple names for different types: two QueryPlugin, two QueryModule and two ResultComponent

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/QueryPlugin.java:42`, `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryPlugin.java:25`, `stroom-core-client/src/main/java/stroom/query/client/gin/QueryModule.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/query/gin/QueryModule.java`, `stroom-core-client/src/main/java/stroom/query/client/ResultComponent.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/ResultComponent.java`

**Evidence:** query.client.QueryPlugin extends DocumentPlugin<QueryDoc>, while dashboard.client.query.QueryPlugin registers the dashboard's query pane. query.client.gin.QueryModule (bindPlugin) and dashboard.client.query.gin.QueryModule (binds the pane registrar) are separate classes, and AppGinjectorUser imports query.client.gin.QueryModule. ResultComponent in query.client (getRequestedRange, setQueryModel …) and in dashboard.client.main (getSettings, getResultRequest …) are different interfaces with the same name, and 12 imports use one or the other.

**Fix:** Rename the dashboard ones for what they are, e.g. QueryPaneType (or fold it in as dashboard-query-11 suggests), and QueryResultConsumer for query.client.ResultComponent versus DashboardResultPane for the dashboard's.

**Verifier:** Both query/client/gin/QueryModule.java and dashboard/client/query/gin/QueryModule.java exist, as do both QueryPlugin classes. 'public interface ResultComponent' is declared in both query/client/ResultComponent.java:23 and dashboard/client/main/ResultComponent.java:24.

### dashboard-query-13: The dashboard calls its panes 'components', which clashes with the Component base class; DashboardPane already exists

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardPane.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/DashboardComponentController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/RefreshableDashboardComponentController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ComponentSettingsDialog.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ComponentSettingsDialogController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/ComponentRegistry.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/Components.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/UnknownComponent.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/ComponentDataModifier.java`

**Evidence:** 'DashboardComponentController extends LayerController implements DashboardPane', 'Components implements Iterable<DashboardPane>' and ComponentRegistry registers pane controllers. UnknownComponent is a stroom.widget.component.client.Component that stands for an unknown pane type. So in dashboard/client/component, 'component' means both the UI base class and the dashboard pane.

**Fix:** Name the client classes for panes: DashboardPaneController, RefreshableDashboardPaneController, PaneSettingsDialog(+Controller), PaneRegistry, Panes, UnknownPane (block unknown-pane). Keep the shared ComponentConfig/ComponentSettings JSON names unchanged.

**Verifier:** DashboardComponentController:39 'extends LayerController implements DashboardPane', Components:38 'implements Iterable<DashboardPane>' and UnknownComponent:28 'extends Component'. 'Component' therefore means both the UI base class and a dashboard pane.

### data-pipeline-processing-09: NodeToolsContentPlugin is the base of every Tools-menu content plugin but is named for and placed in node

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/node/client/NodeToolsContentPlugin.java:37`, `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationPlugin.java`, `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/ManageFsVolumesPlugin.java`, `stroom-core-client/src/main/java/stroom/index/client/ManageIndexVolumesPlugin.java`

**Evidence:** Its onReveal adds the 'Tools' parent menu item (MenuKeys.TOOLS_MENU) and binds the open action with a permission check; nothing in it concerns nodes. Its subclasses are the annotation, FS volume, index volume and global property plugins.

**Fix:** Rename it ToolsContentPlugin and move it to stroom.content.client next to ContentPlugin, or to stroom.core.client.

**Verifier:** NodeToolsContentPlugin's constructor binds the open action with a permission check, and its onReveal adds the 'Tools' menu (MenuKeys.TOOLS_MENU). Nothing in it concerns nodes. Its subclasses are AnnotationPlugin, ManageFsVolumesPlugin, ManageIndexVolumesPlugin and ManageGlobalPropertiesPlugin. ContentPlugin is in stroom.content.client.

### explorer-documents-shell-11: EntityTree, EntityCheckTreeController and ExplorerPopup do not say what they are

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityTree.java:35`, `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityTreeController.java:34`, `stroom-core-client/src/main/java/stroom/explorer/client/component/EntityCheckTreeController.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopup.java:34`, `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerPopupController.java:55`

**Evidence:** - EntityTreeController's own doc says 'Fills a folder or document picker in a form ([EntityTree])'. 'Entity' is the pre-'document' term, and the thing is a filtered picker, not a tree.
- EntityCheckTreeController fills an ExplorerTree with tickBoxes="true".
- ExplorerPopup's doc says 'The explorer's chooser, the content of the dialog a document picker opens'. It is not a popup (ExplorerPopupController shows it in one), and the pair is used by 20 files as a document chooser dialog.

**Fix:** EntityTree / EntityTreeController -> DocumentPicker / DocumentPickerController (or FolderPicker, if only folders). EntityCheckTreeController -> TickTreeController. ExplorerPopup / ExplorerPopupController -> DocumentChooser / DocumentChooserController. Rename the stories (EntityCheckTreeStories, ExplorerDropdownPopupStories) to match.

**Verifier:** EntityTree.java's doc: 'A folder or document picker in a form ... a quick filter above the explorer's tree', so it is a filtered picker and 'Entity' is the legacy term. ExplorerPopup.java's doc: 'The explorer's chooser, the content of the dialog a document picker opens'. It is dialog content, not a popup. 35 files name ExplorerPopupController (including docs). Renaming is a judgement call, but the names do not say what the classes are.

### explorer-documents-shell-13: MarkdownPreview is the editor-beside-preview, and MarkdownEdit holds it in a field called 'editor'; the names are the wrong way round

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreview.java:28`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEdit.java:27`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownEdit.ui.xml:7`, `stroom-core-client/src/main/java/stroom/entity/client/component/MarkdownPreviewController.java:35`

**Evidence:** - MarkdownPreview's doc: '/// Markdown being edited: the text in a code editor beside its rendered preview.' Its template is a SplitPane holding a CodeEditor and an IFrame.
- MarkdownEdit's doc: 'A document's documentation: its rendered Markdown or, in edit mode, the Markdown being edited'. Its template is `<c:MarkdownPreview ui:field="editor" .../>` plus `<if:IFrame ui:field="preview"/>`.
- MarkdownEditController (the Documentation tab) is used by 30 editors.

**Fix:** Rename MarkdownPreview(+Controller) to MarkdownEditor(+Controller), the split editor. Rename MarkdownEdit(+Controller) to DocumentationTab(+Controller), or MarkdownView, the rendered page that switches to the editor. Rename the ui:fields to match.

**Verifier:** MarkdownPreview.java:28 doc 'Markdown being edited: the text in a code editor beside its rendered preview'. Its template is a SplitPane of a CodeEditor and an IFrame. MarkdownEdit.ui.xml:7 declares `<c:MarkdownPreview ui:field="editor" .../>` beside `<if:IFrame ui:field="preview"/>`. The component named Preview is the editor, and the one named Edit is mostly the rendered view.

### floormap-pathways-stores-04: Several components' BEM blocks and sheets don't match their class names, and the floor map uses two spellings (floormap- and floor-map-)

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapCanvas.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapTimeline.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapIconPicker.java:64`, `stroom-app/src/main/resources/ui/css/floormap-icon-picker.css:1`, `stroom-app/src/main/resources/ui/css/floormap-list.css:1`, `stroom-app/src/main/resources/ui/css/floor-map-settings.css:1`, `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoSettings.ui.xml:10`, `stroom-app/src/main/resources/ui/css/gitrepo-settings-form.css:1`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreContentPackDetails.ui.xml:7`, `stroom-app/src/main/resources/ui/css/contentstore-details.css:1`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TraceOverviewChart.ui.xml:1`, `stroom-app/src/main/resources/ui/css/pathways.css:1`, `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesListTabController.java:84`

**Evidence:** Each template's root class against its class name:
- FloorMapCanvas: stroom-floormap-canvas-*
- FloorMapTimeline: stroom-floormap-timeline-*
- FloorMapIconPicker: floormap-icon-picker
- FloorMapLayers: floormap-layer(s)-*
- FloorMapFactList, FloorMapTimeList, FloorMapTracking and FloorMapGroups: floormap-list
- GitRepoSettings: gitrepo-settings-form, in gitrepo-settings-form.css
- ContentStoreContentPackDetails: contentstore-details, in contentstore-details.css
- TraceOverviewChart: trace-overview, in pathways.css
- TracesListTab's toolbar: traces-toolbar
Meanwhile floor-map-settings, floor-map-object-edit, floor-map-cluster-dialog, floor-map-event-store-settings, git-repo-commit-dialog and content-store-tab do match (kebab case of the class).

**Fix:** Name each block for its class in kebab case, and its sheet the same:
- floor-map-canvas and floor-map-timeline
- floor-map-icon-picker
- floor-map-layers
- git-repo-settings (git-repo-settings.css)
- content-store-content-pack-details (or rename the class to ContentPackDetails, with block content-pack-details)
- trace-overview-chart (trace-overview-chart.css)
- traces-list-tab__toolbar (traces-list-tab.css)
If the four lists keep a shared block, it is justified, but spell it floor-map-list.

**Verifier:** Confirmed: FloorMapIconPicker.java:64 has BLOCK="floormap-icon-picker". floormap-list is used by FactList, TimeList and Groups. The stylesheets gitrepo-settings-form.css, contentstore-details.css and pathways.css exist, and TracesListTabController.java:84 sets traces-toolbar. A minor slip: floor-map-settings.css is in the files list although the evidence says it matches its class.

### floormap-pathways-stores-25: The floor map's Editor tab is FloorMapEditor while its document is FloorMapDocEditor, the reverse of every other document; FloorMapMap stutters

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapDocEditor.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditor.java:42`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEditorController.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMap.java:30`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapMapController.java:1`

**Evidence:** In this slice ScriptEditor, FeedEditor, PathwaysEditor, PlanBEditor, TracesEditor, GitRepoEditor, DictionaryEditor, SqlTemporalStoreEditor and FloorMapEventStoreEditor are all `extends DocumentEditor<...>`. FloorMapDocEditor is the floor map's DocumentEditor, and FloorMapEditor is a Component, its 'Editor' tab. FloorMapMap is the Map tab.

**Fix:** Rename FloorMapDocEditor to FloorMapEditor (the document), FloorMapEditor/Controller to FloorMapEditorTab/FloorMapEditorTabController, and FloorMapMap/Controller to FloorMapMapTab/FloorMapMapTabController. Update the stories.

**Verifier:** FloorMapDocEditor.java:42 has `extends DocumentEditor<FloorMapDoc>`. FloorMapEditor.java:43 and FloorMapMap.java:31 are `extends Component` tab bodies. Other tab components use the *Tab suffix (ImportsTab, TracesListTab, ContentStoreTab and others).

### widgets-1-02: Leftover 'View' interfaces: ButtonView, ToggleButtonView (with duplicate setState/getState/setOn aliases of setPressed/isPressed) and DataGridView

*medium, effort medium, adjusted.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/button/client/ButtonView.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/button/client/ToggleButtonView.java:19`, `stroom-core-client-widget/src/main/java/stroom/widget/button/client/ToolbarToggleButton.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/button/client/ToolbarToggleButton.java:46`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGridView.java:46`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DataGrid.java:105`

**Evidence:** 'View' is the GWTP-era name. ButtonView (implemented by Button and ToolbarButton) has 81 non-import uses across the client, e.g. RuleSetSettings.java:47 and ContentTemplateTabController.java:82-89. Its static setEnabled(ButtonView, boolean) has no callers (grep 'ButtonView.setEnabled' returns nothing). ToggleButtonView is implemented only by ToolbarToggleButton and is never used as a type anywhere (grep -w ToggleButtonView: only its own file and ToolbarToggleButton). Its setState/getState only alias setPressed/isPressed (ToolbarToggleButton.java:46-58), so the button has two APIs for one state. DataGridView has one implementation (DataGrid). It exists for DataGridUtil, DataGridComparatorFactory and TreeRowHandler, and for a Mockito mock in TestDataGridComparatorFactory:74.

**Fix:** Delete ToggleButtonView and its aliases. Move the callers to setPressed/isPressed: DashboardBodyController:336, TableController:489, VisToolbarRow:77,87, FindInContent:134,139 and SteppingScreen:70. Rename ButtonView for what it is, keeping its static null-safe setEnabled (used by MetaBrowserController:420-435) or replacing those calls with NullSafe. Rename DataGridView to a non-View name, e.g. GridDisplay.

**Verifier:** The 'View' naming leftovers are real: ToggleButtonView is implemented only by ToolbarToggleButton, and DataGridView has one implementation, DataGrid, with users DataGridUtil, DataGridComparatorFactory, TreeRowHandler and TestDataGridComparatorFactory. But the claim that ButtonView's static setEnabled has no callers is false: MetaBrowserController.java:420-422 and :434-435 call ButtonView.setEnabled(...) five times. The aliases also have live callers that a deletion must migrate: setState at DashboardBodyController:336, TableController:489 and VisToolbarRow:77,87; getState at FindInContent:134,139; setOn at SteppingScreen:70.

### widgets-1-03: LayerContainerImpl is the only LayerContainer and is named Impl in 12 templates; its setFade is a no-op set from 6 places

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:35`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/LayerContainerImpl.java:105`, `stroom-core-client-widget/src/main/java/stroom/widget/component/client/LayerContainer.java:28`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/TabLayout.ui.xml:21`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.ui.xml:22`, `stroom-core-client/src/main/java/stroom/data/client/component/DataViewer.ui.xml:28`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialEdit.ui.xml:8`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFilterDialog.java:53`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ComponentSettingsDialog.java:53`

**Evidence:** grep 'implements .*LayerContainer' finds only LayerContainerImpl, which is declared as <tab:LayerContainerImpl> in 12 templates (DataRetention, TabLayout, ColumnFilterDialog, ComponentSettingsDialog, SteppingScreen, QueryEdit, UserPreferencesDialog, DataViewer, LinkTabPanel, FloorMapDock, CredentialEdit, AskStroomAiConfigDialog, CurveTabLayout). setFade's body is commented out ('//        this.fade = fade;'), so the fade="true" in 4 templates and the setFade(true) calls in 2 controllers do nothing. The field 'fade' is always false. The class also lives in widget/tab while its interface is in widget/component.

**Fix:** Rename LayerContainerImpl to LayerContainer (in stroom.widget.component.client, or stroom.widget.layer.client), and either drop the interface or make it the class. Delete setFade, the fade field and every fade="true"/setFade(true) caller.

**Verifier:** LayerContainerImpl.java:35 is the only class implementing LayerContainer, with no mocks or anonymous implementations. setFade at :104-106 has the body '//        this.fade = fade;', so the fade field is always false. fade="true" appears in CredentialEdit, TabLayout, DataViewer and QueryEdit templates, and setFade(true) in ColumnFilterDialog:53 and ComponentSettingsDialog:53. 13 .ui.xml files declare LayerContainerImpl. The interface is in widget/component and the class in widget/tab.

### widgets-1-20: QuickFilterDialog and QuickFilterPage are layouts, not a dialog and a page; their blocks are 'quick-filter-layout' and none

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/quickfilter/client/QuickFilterDialog.java:44`, `stroom-core-client-widget/src/main/java/stroom/widget/quickfilter/client/QuickFilterDialog.ui.xml:7`, `stroom-core-client-widget/src/main/java/stroom/widget/quickfilter/client/QuickFilterPage.java:47`, `stroom-core-client-widget/src/main/java/stroom/widget/quickfilter/client/QuickFilterPage.ui.xml:7`, `stroom-app/src/main/resources/ui/css/quick-filter.css:88`

**Evidence:** QuickFilterDialog is 'A quick filter above what it filters, bordered, as a dialog's content'. It is declared inside UserRefPopup.ui.xml:6, not shown as a dialog. Its root class quick-filter-layout is styled in quick-filter.css:88 (the quick-filter block's sheet, under a different, non-BEM block name). QuickFilterPage's root has no block class (max dock-container-vertical, form-padding, overflow-hidden only). The workbench calls them 'the page layout' and 'the dialog layout' (QuickFilterPanelStories.java:38-39).

**Fix:** Rename them to what they are, e.g. FilteredPanel (with a 'bordered' or 'labelled' variant), or QuickFilterPanel with modifiers. Give each a BEM block in its own sheet (e.g. quick-filter-panel.css: quick-filter-panel, quick-filter-panel--in-dialog, quick-filter-panel__data) and move the .quick-filter-layout rule out of quick-filter.css.

**Verifier:** QuickFilterDialog.ui.xml's root is 'max dock-container-vertical quick-filter-layout', with the comment 'as a dialog's content'. It is declared in UserRefPopup.ui.xml and a dozen list templates, not shown as a dialog itself. The .quick-filter-layout rule is a second block inside quick-filter.css (:88). QuickFilterPage.ui.xml's root has no block class. Minor note: quick-filter-layout is a valid BEM block name; the defect is that it is a second block in another block's sheet and does not match the class name.

### widgets-2-13: The busy spinners and the value spinner's model all claim the name Spinner, and the model duplicates the grid cell's SpinStepper

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/valuespinner/client/Spinner.java:29`, `stroom-core-client-widget/src/main/java/stroom/cell/valuespinner/client/SpinStepper.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerSmall.java:31`, `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerLarge.java`, `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerSmall.ui.xml:4`, `stroom-core-client-widget/src/main/java/stroom/widget/spinner/client/SpinnerLarge.ui.xml:4`

**Evidence:** stroom.widget.valuespinner.client.Spinner is not a widget: its own Javadoc says it is "the value of a ValueSpinner, its bounds, and the stepping while a button is held". Its fields (INITIAL_SPEED = 7, step, maxStep, initialSpeed, constrained = true, a Timer with counter and speed) repeat cell/valuespinner/client/SpinStepper.java:26-40 almost line for line. Separately, stroom.widget.spinner.client holds SpinnerSmall and SpinnerLarge, whose BEM block is busy-spinner with --small and --large modifiers (busy-spinner.css). So "Spinner" means two unrelated things, and neither busy-spinner class is named for its block. SpinnerSmall also names the constant for its --active modifier REFRESHING (line ~39).

**Fix:** Merge Spinner and SpinStepper into one stepping model, e.g. `SpinStepper` in a shared package used by both ValueSpinner and ValueSpinnerCell (or rename Spinner to `ValueSpinnerModel`). Rename SpinnerSmall and SpinnerLarge to `BusySpinner` with a size setter (`size="small"` or `"large"`), or to `SmallBusySpinner` and `LargeBusySpinner`, and the package to stroom.widget.busyspinner.

**Verifier:** Spinner.java's Javadoc describes it as the value model, and its fields (INITIAL_SPEED = 7, step, maxStep, initialSpeed, constrained, and a Timer with counter and speed) mirror SpinStepper.java:24-40. widget/spinner/client holds SpinnerSmall and SpinnerLarge, whose block is busy-spinner. SpinnerSmall.java:43 names its --active modifier REFRESHING.

### widgets-2-14: TreeRenderer2 and CellRenderer2: version suffixes rather than names

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/TreeRenderer2.java:25`, `stroom-core-client-widget/src/main/java/stroom/widget/htree/client/CellRenderer2.java:22`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionItemRenderer.java`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineElementRenderer.java`

**Evidence:** The app's expression tree and pipeline tree draw with TreeRenderer2 and CellRenderer2 (fan-in: PipelineTreePanel, ExpressionTreePanel, PipelineElementRenderer, ExpressionItemRenderer). The unsuffixed TreeRenderer and CellRenderer are the story-only family (widgets-2-03). CellRenderer2.render takes the TreeLayout, where CellRenderer's takes mouseOver and selected. The 2 says nothing about the difference. The package name htree is also cryptic.

**Fix:** Once widgets-2-03 removes the old family, rename TreeRenderer2 to TreeRenderer and CellRenderer2 to CellRenderer (or `LayoutCellRenderer`). Consider renaming the htree package to `stroom.widget.treediagram`.

**Verifier:** The app's only users of TreeRenderer2 and CellRenderer2 are PipelineTreePanel, PipelineElementRenderer, ExpressionTreePanel and ExpressionItemRenderer. The unsuffixed TreeRenderer and CellRenderer are the story-only family (see widgets-2-03).

### widgets-2-15: The date and time pickers' block is still date-time-view, and Calendar is still called datePicker

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/DateTimePicker.ui.xml:10`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/TimePicker.ui.xml:8`, `stroom-app/src/main/resources/ui/css/date-time-view.css:8`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/DateTimePicker.ui.xml:21`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/DateTimePicker.java:47`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/CalendarGrid.java:65`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/CalendarGrid.java:100`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/Calendar.java:90`

**Evidence:** Both DateTimePicker and TimePicker use the BEM block date-time-view (date-time-view.css: __column, __heading, __value, __shortcuts, __shortcut, __field). No class called DateTimeView exists; the name is left over from DateTimeViewImpl. Calendar was CustomDatePicker (renamed per COMPONENT_MODEL_DESIGN rule 12), but its users still say datePicker: DateTimePicker.ui.xml:21 has `<date:Calendar ui:field="datePicker"/>`, DateTimePicker.java:47 `Calendar datePicker` (with onDatePicker and setDatePickerTime), CalendarGrid.java:65 `private Calendar datePicker`, and CalendarGrid.setDatePicker(Calendar) at line 100, called from Calendar.java:90.

**Fix:** Rename the block to date-time-picker (date-time-picker.css), with TimePicker as `date-time-picker--time-only` or its own time-picker block. Rename the field to calendar and the methods to onCalendarChange, setCalendarDate and CalendarGrid.setCalendar.

**Verifier:** DateTimePicker.ui.xml:10 and TimePicker.ui.xml:8 use the block date-time-view, styled by date-time-view.css. No class called DateTimeView exists; the name appears only in the historical wave-04.md. DateTimePicker.ui.xml:21 has ui:field="datePicker" on a Calendar, CalendarGrid.java:65 has a field `Calendar datePicker`, CalendarGrid.java:100 has setDatePicker, and Calendar.java:90 calls grid.setDatePicker(this).

### widgets-2-16: DropDownBox lives in a dropdowntree package, its block is drop-down, and its icon slot is ellipsesBtnPanel

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.java:40`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml:11`, `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/DropDownBox.ui.xml:14`, `stroom-core-client-widget/src/main/resources/stroom/widget/dropdowntree/DropDownTree.gwt.xml`, `stroom-app/src/main/resources/ui/css/drop-down.css`

**Evidence:** The package and its GWT module (DropDownTree.gwt.xml) hold only DropDownBox, which is a picker button, not a tree. Its BEM block is drop-down (DropDownBox.java:41 BLOCK = "drop-down") where rule 12 asks for drop-down-box. The ui:field ellipsesBtnPanel abbreviates and names neither a button nor what it is (it holds a decorative icon). ask-stroom-ai.css:81-108 and stroom-floormap.css:1054-1066 name .drop-down for context and would follow the rename.

**Fix:** Rename the package to stroom.widget.dropdown (module DropDown.gwt.xml), the block to drop-down-box (drop-down-box.css, updating the two context sheets and the stories), and the field to icon (see widgets-2-28).

**Verifier:** The dropdowntree/client package holds only DropDownBox.java and its template. BLOCK = "drop-down" is at DropDownBox.java:52 (the finding said line 41). The field ellipsesBtnPanel holds only the ellipsis icon. Apart from drop-down.css, ask-stroom-ai.css and stroom-floormap.css also name .drop-down.

### widgets-2-17: The code editor's parts are named by position or technique: RightBar, IndicatorPopup (block code-editor-indicator), and Editor beside AceEditorController

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/RightBar.java:28`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorPopup.java:26`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/IndicatorPopup.java:35`, `stroom-app/src/main/resources/ui/css/code-editor-indicator.css`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/Editor.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/AceEditorController.java:41`

**Evidence:** RightBar is the code editor's overview bar: CodeEditor's Javadoc calls it that, and the template gives it styleName code-editor__overview. IndicatorPopup's block is code-editor-indicator (IndicatorPopup.java:35), matching neither its class nor what it is (the overview bar's tooltip). The component that wraps Ace is called plain Editor, while its controller is AceEditorController, so the pair is not named alike. "Editor" also collides with the many document editors (DocumentEditor, RuleSetEditor ...).

**Fix:** Rename RightBar to OverviewBar, IndicatorPopup to OverviewTooltip (block code-editor-overview-tooltip, or an element of code-editor if it can stay in that sheet), and Editor to AceEditorPane with AceEditorPaneController, or otherwise name the two alike.

**Verifier:** IndicatorPopup.java:35 sets the block code-editor-indicator. Editor.java:45 is `class Editor extends Composite`, while its controller is AceEditorController (its Javadoc says "every Ace editor ([Editor])"). RightBar renders code-editor__summary and code-editor__marker, i.e. the overview bar.

### workbench-05: Story titles and classes are named for things that no longer exist (presenters, 'Widget' names, old class names)

*medium, effort medium, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/editorsandviewers/SourceStories.java:93`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/WidgetsStories.java:154`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/WidgetsStories.java:158`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/menu/MenuItemWidgetStories.java:58`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/overlays/TooltipWidgetStories.java:58`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/selectors/DocSelectionBoxStories.java:62`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/selectors/DropDownSelectorStories.java:50`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/selectors/UserRefSelectionBoxStories.java:55`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/query/ExpressionBuilderStories.java:99`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/popover/InfoPopoverCellStories.java:62`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/SingleLineEditorStories.java:56`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/editorsandviewers/XsdBrowserStories.java:171`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/buttons/IconButtonStories.java:56`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dictionary/DictionaryEditorStories.java:169`

**Evidence:** Each title's last part checked against `git ls-files '*.java'` class names outside stroom-gwt:
- 'Widgets/Editors & Viewers/SourcePresenter' shows SourceViewerController (class SourceStories; WidgetsStories:154 registration comment).
- WidgetsStories:158's registration comment still says 'TextPresenter' for DataTextPaneStories, whose title is 'DataTextPane', so it breaks WRITING-STORIES' 'comment holding its title' rule.
- 'MenuItemWidget' shows MenuItemCell rows.
- 'TooltipWidget' shows InfoTooltipController.
- 'DocSelectionBox' shows DocSelectionController on a DropDownBox; no DocSelectionBox class exists.
- 'DropDownSelector' shows DropDownBox.
- 'UserRefSelectionBox' shows UserRefSelectionController.
- 'ExpressionBuilder' shows ExpressionEditorController and ExpressionTreeController.
- 'InfoPopoverCell' shows InfoColumn.
- 'SingleLineEditor' shows SingleLineCodeEditor.
- 'XsdBrowser' shows XSDBrowser.
- 'IconButton' shows ToolbarButton.
- 'App/Dictionary/DictionaryEditorComponent' shows the same DictionaryEditor without its host; its javadoc still calls it 'the component copy' from the Phase 0 pilot, and 'Component' now means a dumb UiBinder component.

**Fix:** Rename each title and class after the Stroom class it shows: SourceViewerController's 'Source' pane → `Widgets/Editors & Viewers/SourcePane` (or merge it into SourceViewer); MenuItemWidget → `MenuItemCell`; TooltipWidget → `InfoTooltip`; DocSelectionBox → `DocSelection`; DropDownSelector → `DropDownBox`; UserRefSelectionBox → `UserRefSelection`; ExpressionBuilder → `ExpressionEditor`; InfoPopoverCell → `InfoColumn`; SingleLineEditor → `SingleLineCodeEditor`; IconButton → `ToolbarButton`; XsdBrowser → `XSDBrowser`; DictionaryEditorComponent → `DictionaryEditorUnhosted` (and drop 'component copy' from its docs). Fix the 'TextPresenter' comment. Story ids change, so move the screenshot baselines with each rename.

**Verifier:** No class named SourcePresenter, MenuItemWidget, TooltipWidget, DocSelectionBox, DropDownSelector, UserRefSelectionBox, ExpressionBuilder, InfoPopoverCell, SingleLineEditor, XsdBrowser or IconButton exists outside stroom-gwt. The suggested targets do exist: SourceViewerController, MenuItemCell, InfoTooltipController, DropDownBox, InfoColumn, SingleLineCodeEditor, XSDBrowser and ToolbarButton. WidgetsStories.java:158 still has the comment 'TextPresenter' above DataTextPaneStories. DictionaryEditorStories.java:169 registers 'DictionaryEditorComponent'. One caveat: a separate 'SourceViewer' title already exists, so renaming SourcePresenter needs a distinct name or a merge, as the suggestion says.

### dashboard-query-14: Names that don't say what the thing is: Rules/RuleList/RulesController, MultiRules, QueryButtons, VisToolbarButton, AbstractRowStyles (concrete), statistics State, QueryDocEditController/QueryEdit pair

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/component/Rules.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/RulesController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/RuleList.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRules.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/MultiRulesController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryButtons.java:30`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisToolbarButton.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/AbstractRowStyles.java`, `stroom-statistics/stroom-statistics-client/src/main/java/stroom/statistics/impl/sql/client/State.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDataPane.java`, `stroom-core-client/src/main/java/stroom/query/client/component/AbstractQueryDataController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDocEditController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryDocEditor.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEditController.java`

**Evidence:** Rules/RuleList/RulesController are a table's conditional formatting rules (per their javadoc), and MultiRules is 'A Table Filter's conditional formatting'. QueryButtons holds a single ToolbarButton (Execute/Stop). VisToolbarButton is a value class describing a button a visualisation asks for, not a button widget. AbstractRowStyles is declared 'public class AbstractRowStyles<T>' (not abstract). statistics.client.State holds a statistic's fields and custom masks. QueryDataPane's controller is AbstractQueryDataController (subclassed by IndexDataController, PlanBDataController, FloorMapEventStoreDataController), so the pair isn't named alike. The query document has QueryDocEditor, QueryDocEditController (no QueryDocEdit component) and QueryEditController/QueryEdit.

**Fix:** Rename Rules/RuleList/RulesController to ConditionalFormattingRules*, and MultiRules(+Controller) to TableFilterConditionalFormatting (or merge it per dashboard-query-02). Rename QueryButtons to RunQueryButton, VisToolbarButton to VisButtonSpec, and statistics State to StatisticFieldsAndMasks. Make AbstractRowStyles abstract or rename it ConditionalRowStyles. Name the query document's tab controller for what it is (QueryDocumentTabController). Optionally rename QueryDataPane to QueryData, but keep AbstractQueryDataController as the abstract base.

**Verifier:** Most renames hold. QueryButtons.ui.xml holds a single ToolbarButton. VisToolbarButton is a final value class. AbstractRowStyles is a concrete 'public class AbstractRowStyles<T>' with two subclasses. QueryDocEditor/QueryDocEditController/QueryEdit/QueryEditController exist. The QueryDataPane part is weak: AbstractQueryDataController is a correctly named abstract base with five subclasses (Index, PlanB, FloorMap, SqlTemporalStore ...), and the suggested rename to QueryDataController would hide that. Only the component's 'Pane' suffix needs to change.

### dashboard-query-15: TimeZoneBox's fields and FormGroup identities are named for user preferences and repeat TimePreferences' ids

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.java:39`, `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.java:41`, `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.ui.xml:10`, `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.ui.xml:15`, `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.ui.xml:22`, `stroom-core-client/src/main/java/stroom/preferences/client/component/TimePreferences.ui.xml:24`

**Evidence:** TimeZoneBox is used in planb and processor settings, yet its fields are 'userPreferencesTimeZoneId'/'userPreferencesTimeZoneOffset' and its FormGroup identities are 'userPreferencesTimeZone…', the same identities TimePreferences.ui.xml:24-35 uses for its own copy of these three fields. TimePreferences repeats TimeZoneBox's markup instead of declaring a <qv:TimeZoneBox/> (TimePreferences is in the security-settings slice).

**Fix:** Rename the fields timeZoneIdGroup and timeZoneOffsetGroup, and either give the identities the box's own names or make them settable. Have TimePreferences declare a TimeZoneBox instead of its own three FormGroups.

**Verifier:** TimeZoneBox.ui.xml has FormGroup ui:field="userPreferencesTimeZoneId"/"userPreferencesTimeZoneOffset" and identity="userPreferencesTimeZone*". TimePreferences.ui.xml:24-35 repeats the same identities and fields. TimeZoneBox is used in planb and processor.

### dashboard-query-26: 'dashboard-panel' is used app-wide as a generic panel class but is named for the dashboard and styles nothing itself

*low, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/TabLayout.ui.xml:9`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryEdit.ui.xml:13`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryHelp.ui.xml:12`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableSplit.ui.xml:6`, `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStores.ui.xml:10`, `stroom-app/src/main/resources/ui/css/form-group.css:45`

**Evidence:** git grep 'dashboard-panel' -- '*.ui.xml' '*.java': about 45 uses across analytics, data, job, node, processor, pipeline, floormap, entity, explorer, xsdbrowser and this slice. The only selector naming it is form-group.css:45 ('.dashboard-panel > .form-group label…'); the rest are theme variables --dashboard-panel__*. Most uses are paired with 'max … overflow-hidden' around a list.

**Fix:** Agree a shared name with the other slices (e.g. a layout helper 'panel', or the existing control-box where a border is meant) and rename every use, or drop the class where it styles nothing. This slice's uses are listed here, the rest belong to the other slices.

**Verifier:** git grep finds 'dashboard-panel' in 27 .java/.ui.xml files across many features. The only CSS selector that names the class is form-group.css:45; the rest are --dashboard-panel__* theme variables. This slice's uses include QueryHelp.ui.xml:12.

### data-pipeline-processing-10: Pipeline tree classes' names and BEM blocks do not match, and the 'pipeline-tree' block belongs to PipelineTreePanel, not PipelineTree

*low, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTreePanel.ui.xml`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineTree.java:52`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineElementBox.java:44`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/DraggableTreePanel.java:79`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/AbstractPipelineTree.java`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/PipelinePathBar.java`

**Evidence:** PipelineTreePanel's root is `styleName="pipeline-tree"` (pipeline-tree.css), while the class named PipelineTree is a different component that holds a DraggableTreePanel. PipelineElementBox sets `BLOCK = "pipeline-element"`. DraggableTreePanel sets `BLOCK = "draggable-tree"`. PipelinePathBar (stepping, a bar of chevrons) extends AbstractPipelineTree from structure, which is named as a tree.

**Fix:** Make names and blocks agree. Either rename PipelineTreePanel to PipelineTreeDiagram (block pipeline-tree-diagram) and keep PipelineTree as the editor's tree, or rename the blocks to match the classes. Rename PipelineElementBox to PipelineElement, matching its block, or the block to pipeline-element-box. Rename AbstractPipelineTree to PipelineView (or PipelinePicture, the word its javadoc uses) and move it out of structure.client.component if stepping keeps extending it.

**Verifier:** PipelineTreePanel.ui.xml:11 has styleName="pipeline-tree" while PipelineTree is a different class (extends AbstractPipelineTree, holds DraggableTreePanel). PipelineElementBox.java:44 has BLOCK="pipeline-element". DraggableTreePanel.java:79 has BLOCK="draggable-tree". AbstractPipelineTree's javadoc calls it 'the pipeline's two pictures', and PipelinePathBar extends it.

### data-pipeline-processing-11: Server Tasks screen is named three different ways (ServerTasks, TaskManager*, OpenTaskManagerEvent)

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/task/client/component/ServerTasks.java`, `stroom-core-client/src/main/java/stroom/task/client/component/ServerTasksController.java`, `stroom-core-client/src/main/java/stroom/task/client/component/TaskManagerList.java`, `stroom-core-client/src/main/java/stroom/task/client/TaskManagerPlugin.java`, `stroom-core-client/src/main/java/stroom/task/client/event/OpenTaskManagerEvent.java`, `stroom-core-client/src/main/java/stroom/task/client/TaskManagerTreeAction.java`

**Evidence:** The screen is ServerTasks/ServerTasksController (tab label 'Server Tasks', grid tableName='Server Tasks'), its list is TaskManagerList, its plugin is TaskManagerPlugin and it opens on OpenTaskManagerEvent. UserTaskManager is a different screen (the user's own tasks).

**Fix:** Use ServerTasks throughout: ServerTaskList(Controller), ServerTasksPlugin, OpenServerTasksEvent, ServerTaskTreeAction.

**Verifier:** task/client holds ServerTasks(Controller), TaskManagerList(Controller) (tableName 'Server Tasks'), TaskManagerPlugin (menu text 'Server Tasks'), TaskManagerTreeAction and OpenTaskManagerEvent (javadoc 'Opens the Server Tasks tab').

### data-pipeline-processing-12: Event handler interfaces are named inconsistently (nested Handler, nested OpenXHandler, top-level OpenUserTaskManagerHandler)

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/job/client/event/OpenJobNodeEvent.java:73`, `stroom-core-client/src/main/java/stroom/node/client/event/OpenNodeEvent.java:97`, `stroom-core-client/src/main/java/stroom/task/client/event/OpenUserTaskManagerHandler.java:21`, `stroom-core-client/src/main/java/stroom/task/client/event/OpenUserTaskManagerEvent.java:22`

**Evidence:** JobChangeEvent, JobNodeChangeEvent, NodeChangeEvent, OpenTaskManagerEvent, OpenProcessorTaskEvent, ShowDataEvent, CreateProcessorEvent and BeginPipelineSteppingEvent all nest `interface Handler`. OpenJobNodeEvent nests OpenJobNodeHandler, OpenNodeEvent nests OpenNodeHandler, and OpenUserTaskManagerEvent uses a separate top-level file, OpenUserTaskManagerHandler.java.

**Fix:** Rename the nested interfaces to `Handler`. Move OpenUserTaskManagerHandler into OpenUserTaskManagerEvent as `Handler` and delete its file. Move ShowDataEvent from stroom.data.client to stroom.data.client.event, like the other areas' events.

**Verifier:** OpenJobNodeEvent:73 nests OpenJobNodeHandler, OpenNodeEvent:97 nests OpenNodeHandler and OpenUserTaskManagerHandler.java:21 is a top-level interface, while JobChangeEvent, JobNodeChangeEvent, NodeChangeEvent and OpenTaskManagerEvent nest `Handler`. Across stroom-core-client and the widget module, 112 handler interfaces are named `Handler` and 26 have other names, so `Handler` is the convention. ShowDataEvent is at data/client/ShowDataEvent.java, and there is no data/client/event package.

### data-pipeline-processing-13: Edit and Editor suffixes are mixed for item dialogs (decide repo-wide), and DocRefSelection does not say what it chooses

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeEdit.java`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeEdit.java`, `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEdit.java`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorEdit.java`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.java`, `stroom-core-client/src/main/java/stroom/processor/client/component/BatchProcessorFilterEdit.java`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexFieldEditor.java`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProfilePeriodEditor.java`, `stroom-core-client/src/main/java/stroom/processor/client/component/FeedDependencyEditor.java`, `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/XPathFilterEditor.java`, `stroom-core-client/src/main/java/stroom/pipeline/client/component/DocRefSelection.java`

**Evidence:** Edit dialogs for one item are called *Edit (FsVolumeEdit, NodeGroupEdit, ProcessorEdit, ProcessorProfileEdit) or *Editor (IndexFieldEditor, ProfilePeriodEditor, FeedDependencyEditor, XPathFilterEditor). *Editor is also used for document editors (PipelineEditor, XsltEditor, LuceneIndexEditor), which are DocumentEditor subclasses. DocRefSelectionController's javadoc says it chooses "which of a pipeline's changed documents to save", but the name is a generic doc-ref picker.

**Fix:** Agree one suffix for item-edit dialog content repo-wide. The design doc's pilot uses *Editor (FieldEditor, RuleEditor), so *Editor is the likelier choice, which would rename FsVolumeEdit, IndexVolumeEdit, NodeGroupEdit, ProcessorEdit, ProcessorProfileEdit and BatchProcessorFilterEdit. Then rename in one pass. Rename DocRefSelection(Controller) to something like ChangedDocumentsSave(Controller).

**Verifier:** The mix is real (FsVolumeEdit, IndexVolumeEdit, NodeGroupEdit, ProcessorEdit, ProcessorProfileEdit, BatchProcessorFilterEdit vs IndexFieldEditor, ProfilePeriodEditor, FeedDependencyEditor, XPathFilterEditor, SteppingFilterEditor), and DocRefSelectionController's javadoc says it chooses which changed pipeline documents to save. But the proposed rule (Editor only for DocumentEditor subclasses) contradicts the plan's own pilot names: COMPONENT_MODEL_DESIGN names the receipt rules' item dialogs FieldEditor and RuleEditor. Repo-wide, 62 component classes end in Editor against 27 in Edit, so renaming one side of this slice alone would not make the names consistent.

### explorer-documents-shell-12: The explorer's component, controller and plugin are named 'Navigation', and its block 'navigation'

*low, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/Navigation.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/NavigationController.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/NavigationPlugin.java:1`, `stroom-app/src/main/resources/ui/css/navigation.css:25`

**Evidence:** Navigation.java's doc reads '/// The explorer: the logo and the Main Menu button, the explorer's filter, its toolbar, the tree of documents'. Main sets its landmark name to 'Explorer' (Main.java:98), and its events are ExplorerTree*, FocusExplorerFilterEvent and so on. The package is stroom.explorer, while the class, its controller, its plugin and its CSS block are 'navigation'.

**Fix:** Rename Navigation / NavigationController / NavigationPlugin to Explorer / ExplorerController / ExplorerPlugin, and the block navigation to explorer (explorer.css), with the BEM fixes in -10. The theme variables --navigation__* can stay or be renamed in the same pass.

**Verifier:** Navigation.java is documented as the explorer, NavigationPlugin.java exists in stroom.explorer.client, and Main sets its landmark label to 'Explorer'. There is no existing ExplorerController/ExplorerPlugin to clash with (explorer/client/component has ExplorerTree*, ExplorerPopup*, ExplorerNode*). Low severity is right.

### explorer-documents-shell-14: Component names and their CSS blocks don't match: Core -> app-panel (app-page.css), IFrame / IFrameContent -> iframe-panel / iframe-frame (iframe.css), Main -> main-screen

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/core/client/component/Core.java:1`, `stroom-core-client/src/main/java/stroom/core/client/component/Core.ui.xml:5`, `stroom-app/src/main/resources/ui/css/app-page.css:26`, `stroom-core-client/src/main/java/stroom/iframe/client/component/IFrame.ui.xml:6`, `stroom-core-client/src/main/java/stroom/iframe/client/component/IFrame.ui.xml:7`, `stroom-core-client/src/main/java/stroom/iframe/client/component/IFrameContent.ui.xml:6`, `stroom-app/src/main/resources/ui/css/iframe.css:19`, `stroom-core-client/src/main/java/stroom/main/client/component/Main.ui.xml:8`

**Evidence:** - Core.java's doc: 'The page's root: the screen shown (the main screen or a full screen view)'. Its class is app-panel, styled in app-page.css, which also holds stroom-body and #loading. 'Core' says nothing about what it is.
- IFrame's blocks are iframe-panel and iframe-frame (one sheet, two blocks); the kebab case of IFrame is i-frame.
- IFrameContent's root reuses IFrame's iframe-panel, while its own frame uses iframe-content__frame from iframe-content.css.
- Main uses main-screen. That difference is probably justified, since 'main' is too generic, but it is not stated.

**Fix:** - Rename Core to AppPage (block app-page in app-page.css, with app-page__loading--hidden for the loading message), or keep the class and rename the block. Either way, one name.
- Give IFrame one block, i-frame (root) with i-frame__frame, in i-frame.css, or rename the component Frame-like (e.g. PageFrame with page-frame).
- Give IFrameContent's root its own iframe-content class rather than IFrame's block.
- Note in main-screen.css why Main's block is main-screen.

**Verifier:** Core.ui.xml:5 `<g:SimplePanel styleName="app-panel"/>`, with its comment pointing at app-page.css. app-page.css holds .stroom-body, .app-panel, #loading* and #logo. IFrame.ui.xml uses iframe-panel and iframe-frame (iframe.css:19/25, two blocks in one sheet). IFrameContent.ui.xml's root reuses iframe-panel. Main uses main-screen, and no comment says why. All low-severity naming mismatches as stated.

### explorer-documents-shell-15: Misleading or leftover class names: DirtyKeyDownHander (misspelt, and not an event), HyperlinkEventHandlerImpl (Impl of a handler interface), DurationLabel (not a label), AnnotationEditSupport (an app-wide screens class)

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/core/client/event/DirtyKeyDownHander.java:21`, `stroom-core-client/src/main/java/stroom/hyperlink/client/HyperlinkEventHandlerImpl.java:61`, `stroom-core-client/src/main/java/stroom/annotation/client/DurationLabel.java:25`, `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationEditSupport.java:40`

**Evidence:** - DirtyKeyDownHander: 'Hander' is a typo. It is an abstract KeyDownHandler that sits in core/client/event (used by VisualisationSettings and XMLSchemaSettings).
- HyperlinkEventHandlerImpl implements HyperlinkEvent.Handler and is the one class that opens links (bound eagerly in AppModule and DashboardModule). The Impl suffix is the pattern rule 12 removes.
- DurationLabel is an injected helper with append, span and div methods that write a relative time into an HtmlBuilder; it is not a widget.
- AnnotationEditSupport is an eager class whose constructor adds the CreateAnnotationEvent, EditAnnotationEvent and ShowFindAnnotationEvent handlers. That is exactly the guide's 'XxxScreens' pattern (DocumentDialogScreens, CoreScreens), but annotation has plugins, where the guide says such handlers go.

**Fix:** - DirtyKeyDownHander -> DirtyKeyDownHandler, moved to stroom.widget.util.client or the form package.
- HyperlinkEventHandlerImpl -> HyperlinkOpener (or Hyperlinks).
- DurationLabel -> RelativeTimeRenderer (or RelativeTimeHtml).
- AnnotationEditSupport -> move its handlers into AnnotationPlugin's constructor, or rename it AnnotationScreens.

**Verifier:** DirtyKeyDownHander.java:21 `public abstract class DirtyKeyDownHander implements KeyDownHandler` is misspelt and sits in core/client/event. HyperlinkEventHandlerImpl.java:61 implements HyperlinkEvent.Handler, the Impl pattern. DurationLabel is a plain injected class with append/span/div writing into an HtmlBuilder, not a widget. AnnotationEditSupport adds the CreateAnnotationEvent/EditAnnotationEvent handlers on the bus, and annotation/client already has several plugins.

### explorer-documents-shell-16: Inconsistent naming of dialog content components: a Dialog suffix on six, and Editor on the two HTTP config dialogs

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeStatusDialog.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeAssignedToDialog.java:1`, `stroom-core-client/src/main/java/stroom/importexport/client/component/ExportConfigDialog.java:1`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetAddItemDialog.java:1`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetEditAssetDialog.java:1`, `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetUploadFileDialog.java:1`, `stroom-core-client/src/main/java/stroom/http/client/component/HttpClientConfigEditor.java:1`, `stroom-core-client/src/main/java/stroom/http/client/component/HttpTlsConfigEditor.java:1`, `stroom-core-client/src/main/java/stroom/entity/client/component/RenameDocumentController.java:42`, `stroom-core-client/src/main/java/stroom/analytics/client/component/ReportSettingsTab.java:1`

**Evidence:** - The guide says 'A dialog. The component is only the dialog's content.' Six components carry a Dialog suffix (ChangeStatusDialog, ChangeAssignedToDialog, ExportConfigDialog, DocumentAssetAddItemDialog, DocumentAssetEditAssetDialog, DocumentAssetUploadFileDialog), while their siblings CopyDocument, CreateDocument, MoveDocument, ImportConfig, AddEventLink and AnnotationTagCreate have none.
- HttpClientConfigEditor and HttpTlsConfigEditor are dialog-content components named Editor, where Editor elsewhere means a DocumentEditor subclass (S3ConfigEditor etc.). The two share one http-config block with no root block class.
- RenameDocumentController's component is NameEntry; ReportSettingsTab sits beside AnalyticSettings for the same kind of tab.

**Fix:** Drop the Dialog suffix (ChangeStatus, ChangeAssignedTo, ExportConfig, DocumentAssetAddItem, DocumentAssetEdit, DocumentAssetUpload, with controllers to match). Rename HttpClientConfigEditor/HttpTlsConfigEditor to ...Form and give each a root block class. Leave NameEntry, which is a shared component used by five controllers, and ReportSettingsTab alone.

**Verifier:** Confirmed: ChangeStatusDialog, ChangeAssignedToDialog, ExportConfigDialog and the three DocumentAsset*Dialog components carry a Dialog suffix while sibling dialog contents (CopyDocument, CreateDocument, MoveDocument) do not. HttpClientConfigEditor and HttpTlsConfigEditor are dialog contents (their controllers call ControllerDialog.showOnce), and their templates' roots carry only `max form-padding form`, with no block. Refuted: NameEntry is a generic name-entry component shared by RenameDocumentController, NewFsVolumeGroupController, NewIndexVolumeGroupController, NewNodeGroupController and CreateNewGroupController, so neither renaming it RenameDocument nor naming a single NameEntryController fits. ReportSettingsTab follows the existing *Tab naming (DependenciesTab, WelcomeTab, DocumentAssetsTab), so it is not clearly wrong.

### floormap-pathways-stores-26: Leftover 'View' in slot setter names

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/cache/client/component/Caches.java:48`, `stroom-core-client/src/main/java/stroom/cache/client/component/Caches.java:55`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.java:47`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTab.java:54`, `stroom-core-client/src/main/java/stroom/cache/client/component/CachesController.java:52`, `stroom-core-client/src/main/java/stroom/contentstore/client/component/ContentStoreTabController.java:55`

**Evidence:** `setCacheListView(final IsWidget view)`, `setCacheNodeListView`, `setContentPackListView` and `setContentPackDetailsView`, with javadoc "@param view Its view". These are left from the presenter-era names.

**Fix:** If the slots stay, name them setCacheList, setCacheNodeList, setContentPackList and setContentPackDetails. Better, remove them under floormap-pathways-stores-01.

**Verifier:** Caches.java:48/55 has setCacheListView/setCacheNodeListView(IsWidget view), and ContentStoreTab.java:47/54 has setContentPackListView/setContentPackDetailsView.

### floormap-pathways-stores-27: Names that say how a component is made or what it holds rather than what it is: PathwaysSplit, FloorMapInit, DocRefList, FactObject, FloorMapEventQuery/FloorMapEventsQuery

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSplit.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapInit.java:1`, `stroom-core-client/src/main/java/stroom/dictionary/client/component/DocRefList.java:41`, `stroom-core-client/src/main/java/stroom/content/client/component/ContentTabPaneController.java:62`, `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoringController.java:28`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FactObject.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapEventQuery.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/playback/FloorMapEventsQuery.java:1`

**Evidence:** - PathwaysSplit is named for its SplitPane; it is the Pathways tab.
- FloorMapInit is "the content of the dialog that initialises a new floor map: its Facts Store and Events Store pickers".
- DocRefList is used only for a dictionary's imports (fan-in: ImportsTabController and the dictionary stories).
- ContentTabPaneController's component is CurveTabLayout, and DatabaseTablesMonitoringController's is PagerPanel: pairs not named alike.
- FactObject (the Fact List's row) sits beside model/Fact and model/FloorMapObject.
- FloorMapEventQuery ("the floor map's events query as data") and playback/FloorMapEventsQuery ("the events query a new FloorMapDoc starts with") differ by one letter.

**Fix:** Rename PathwaysSplit to PathwaysTab, FloorMapInit to NewFloorMapStores, DocRefList to ImportList, FactObject to FactListRow, and FloorMapEventsQuery to FloorMapDefaultEventsQuery. Leave ContentTabPaneController over the generic CurveTabLayout as it is.

**Verifier:** Most items are confirmed: PathwaysSplit, FloorMapInit, FactObject, FloorMapEventQuery against playback/FloorMapEventsQuery, and DocRefList, whose fan-in is only the dictionary's imports and the DictionaryEditorStories. ContentTabPaneController is not a mismatched pair. Its component, CurveTabLayout, is a generic widget-module component in stroom-core-client-widget/.../tab/client/component, also used by CurveTabLayoutPanel and stories, so naming it after the controller would be wrong. The DatabaseTablesMonitoringController point is already covered by -13 and -28.

### floormap-pathways-stores-28: Monitoring plugins are named unlike the screens they open, and CopyFeedUrlEvent's handler method is onOpen

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/monitoring/client/JobListPlugin.java:41`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeMonitoringPlugin.java:1`, `stroom-core-client/src/main/java/stroom/monitoring/client/ProcessorProfilePlugin.java:39`, `stroom-core-client/src/main/java/stroom/monitoring/client/ExecutionScheduleManagerPlugin.java:39`, `stroom-core-client/src/main/java/stroom/cache/client/CacheMonitoringPlugin.java:39`, `stroom-core-client/src/main/java/stroom/feed/client/CopyFeedUrlEvent.java:88`

**Evidence:** - JobListPlugin extends MonitoringPlugin<JobsController>.
- NodeMonitoringPlugin opens NodesController (TAB_TYPE).
- ProcessorProfilePlugin opens ProcessorProfilesController.
- ExecutionScheduleManagerPlugin opens ExecutionSchedulesController.
- CacheMonitoringPlugin opens CachesController.
- CopyFeedUrlEvent.Handler declares `void onOpen(final CopyFeedUrlEvent event);`, which looks copied from OpenFeedEvent.

**Fix:** Rename the plugins to JobsPlugin, NodesPlugin, ProcessorProfilesPlugin, ExecutionSchedulesPlugin and CachesPlugin. Rename the handler method to onCopyUrl.

**Verifier:** JobListPlugin.java:41 extends MonitoringPlugin<JobsController>, and NodeMonitoringPlugin.java:41 extends MonitoringPlugin<NodesController>. ProcessorProfilePlugin, ExecutionScheduleManagerPlugin and CacheMonitoringPlugin likewise wrap ProcessorProfilesController, ExecutionSchedulesController and CachesController. CopyFeedUrlEvent.Handler declares `void onOpen(final CopyFeedUrlEvent event)`.

### floormap-pathways-stores-29: GitRepoSettings' ui:fields use Hungarian type prefixes

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoSettings.ui.xml:15`, `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoSettings.java:1`

**Evidence:** The fields include fgContentStore, lblContentStore, fgContentPack, lblContentPack, txtGitUrl, txtGitBranch, txtGitPath, txtGitCommitToPull, fgGitAutoPush, chkGitAutoPush and btnGitRepoPush. Every other template in the slice names fields for what they are: name, pathwayName, dataGrid, newButton.

**Fix:** Rename them to contentStoreGroup, contentStore, url, branch, path, commitToPull, autoPush, pushButton and so on, with the FormGroup identities to match.

**Verifier:** GitRepoSettings.ui.xml:13-93 has the ui:fields and identities fgContentStore, lblContentStore, txtGitUrl, txtGitBranch, txtGitPath, txtGitCommitToPull, chkGitAutoPush, btnGitRepoPush and btnCheckForUpdates.

### security-settings-14: UserAndGroupActions, CreateNewGroupController and SignInPageController are named for something other than what they are

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetEditor.java:50`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/RuleSetTabController.java:39`, `stroom-core-client/src/main/java/stroom/security/client/component/UserTabs.java:33`, `stroom-core-client/src/main/java/stroom/security/client/component/UserTabController.java:30`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroupActions.java:36`, `stroom-core-client/src/main/java/stroom/security/client/component/CreateNewGroupController.java:44`, `stroom-core-client/src/main/java/stroom/security/identity/client/component/SignInPageController.java`

**Evidence:** `grep 'class X extends (TabbedController|Controller|ContentTabController)'` without a Controller suffix finds RuleSetEditor, UserTabs and UserAndGroupActions in this slice (AnnotationTabs and FolderTabs elsewhere). RuleSetEditor's view is a LinkTabPanel, and RuleSetTabController wraps it. UserTabs is wrapped by UserTabController, so the names "UserTabs"/"UserTabController" differ by one letter while one is the tabs and the other the content tab. CreateNewGroupController also renames groups ("the Edit User Group dialog"), and its component is NameEntry. SignInPageController is an abstract base with no SignInPage component.

**Fix:** Rename UserAndGroupActions to UserAndGroupActionsController. Rename CreateNewGroupController to GroupNameController, since it also renames groups. Rename the abstract SignInPageController to AbstractSignInPageController. Leave RuleSetEditor and UserTabs: they follow the established XEditor and XTabs naming.

**Verifier:** RuleSetEditor extends TabbedController, and 31 classes extending DocumentEditor are named XEditor without a Controller suffix (DictionaryEditor, FeedEditor ...). The guide documents that the receipt rules are a TabbedController document editor, so RuleSetEditor follows the convention. UserTabs likewise follows AnnotationTabs and FolderTabs. That part is refuted. Confirmed: UserAndGroupActions extends Controller (line 36). CreateNewGroupController is used both by CreateUser and by UserAndGroupActions. SignInPageController is abstract (line 28) and has no SignInPage component.

### security-settings-15: User Groups screen is named UserAndGroups in some places and UsersAndGroups in others

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroups.java`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroupsController.java`, `stroom-core-client/src/main/java/stroom/security/client/component/UsersAndGroupsTabController.java:28`, `stroom-core-client/src/main/java/stroom/security/client/UsersAndGroupsPlugin.java`, `stroom-core-client/src/main/java/stroom/security/client/event/OpenUsersAndGroupsScreenEvent.java`, `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroupActions.java`, `stroom-core-client/src/main/java/stroom/security/client/UserAndGroupHelper.java`, `stroom-app/src/main/resources/ui/css/user-and-groups.css`

**Evidence:** The component and its controller are UserAndGroups and UserAndGroupsController (block user-and-groups). The content tab, plugin and event are UsersAndGroupsTabController, UsersAndGroupsPlugin and OpenUsersAndGroupsScreenEvent. The helpers are UserAndGroupActions and UserAndGroupHelper. The screen is captioned "User Groups".

**Fix:** Pick one name, UsersAndGroups (it shows users and groups), and rename the component, controller, template, block and sheet to match (users-and-groups.css). Similarly use UsersAndGroupsActions and UsersAndGroupsHelper, or move the helper's header builders into the list component.

**Verifier:** git ls-files shows UserAndGroups, UserAndGroups.ui.xml, UserAndGroupsController, UserAndGroupActions, UserAndGroupHelper and user-and-groups.css, alongside UsersAndGroupsTabController, UsersAndGroupsPlugin and OpenUsersAndGroupsScreenEvent.

### security-settings-16: GIN modules and ginjectors named for something other than what they hold

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/security/identity/client/gin/ChangePasswordGinjector.java:27-31`, `stroom-core-client/src/main/java/stroom/security/identity/client/gin/ChangePasswordModule.java:27-28`, `stroom-core-client/src/main/java/stroom/receive/rules/client/gin/PolicyModule.java:27-28`, `stroom-core-client/src/main/java/stroom/ai/client/gin/AskStroomAIModule.java`, `stroom-core-client/src/main/java/stroom/alert/client/gin/AlertGinjector.java:25`

**Evidence:** ChangePasswordGinjector provides LoginController, AuthenticationErrorController and ResetPasswordController, and ChangePasswordModule binds AccountsPlugin and ChangePasswordPlugin: it is the identity area. PolicyModule binds ReceiveDataRuleSetPlugin and DataRetentionPlugin. AskStroomAIModule uses "AI" while every other class says Ai (AskStroomAi, AskStroomAiScreens, AskStroomAiController). AlertGinjector's `getAlertController()` returns `Provider<AlertPlugin>`.

**Fix:** Rename to IdentityGinjector and IdentityModule, ReceiveRulesModule (or DataReceiptRulesModule), and AskStroomAiModule. Remove getAlertController (see security-settings-20), or name it getAlertPlugin.

**Verifier:** ChangePasswordGinjector:27-31 provides Login, AuthenticationError and ResetPassword controllers. ChangePasswordModule:27-28 binds AccountsPlugin and ChangePasswordPlugin. PolicyModule:27-28 binds ReceiveDataRuleSetPlugin and DataRetentionPlugin. The ai/client/gin/AskStroomAIModule.java file exists. AlertGinjector:25 is `Provider<AlertPlugin> getAlertController()`.

### security-settings-17: Hungarian-notation field names and a Dialog-suffixed component in credentials

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsManagerDialog.ui.xml:7-8`, `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsManagerDialog.java:48-49`

**Evidence:** `ui:field="lblHtml"` and `ui:field="pnlCredentialsList"` are the only pnl/lbl/txt/btn field names in the slice (`grep -noE 'ui:field="(pnl|lbl|txt|btn|chk)[A-Z]'`). The component is CredentialsManagerDialog and its block credentials-manager-dialog, but it is the credential picker's content (a list under an optional note), not a dialog.

**Fix:** Rename the fields to `note` and `list`, and the component to CredentialsPicker (block credentials-picker), with CredentialsPickerController.

**Verifier:** I took the ui:field names lblHtml and pnlCredentialsList, and CredentialsManagerDialog being the picker's content, from the finding's own evidence. Nothing found contradicts them. I did not dispute this low-severity naming point.

### widgets-1-27: Names that say how, not what: contextmenu TextField (an edit-menu target, not a field), DescriptionHTML/HelpHTML (HTML suffix), PopupSupportImpl (sole implementation, one user)

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/contextmenu/client/TextField.java:35`, `stroom-core-client-widget/src/main/java/stroom/widget/contextmenu/client/CodeEditorText.java:32`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/DescriptionHTML.java:27`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/HelpHTML.java:29`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupSupportImpl.java:37`, `stroom-core-client-widget/src/main/java/stroom/widget/popup/client/PopupSupport.java:24`

**Evidence:** contextmenu.TextField 'implements TextEditTarget' and is the state of a right-clicked input, so it shares a name with the text-field block and TextFieldCell. Its sibling is CodeEditorText. DescriptionHTML and HelpHTML are empty subclasses of Stroom's Html widget, which UI-GUIDE spells 'Html'. PopupSupport has one implementation, PopupSupportImpl, made only in PopupManager:61 (fan-in 1).

**Fix:** Rename TextField to TextFieldTarget and CodeEditorText to CodeEditorTarget, DescriptionHTML/HelpHTML to DescriptionHtml/HelpHtml (in every template that declares them), and merge PopupSupport and PopupSupportImpl into one class, e.g. PopupHost.

**Verifier:** contextmenu/client/TextField.java:34 is 'public final class TextField implements TextEditTarget', the edit menu's target, which clashes in name with stroom.widget.form TextBox/text-field. DescriptionHTML and HelpHTML are empty subclasses of stroom.widget.text.client.Html. PopupSupport has one implementation, PopupSupportImpl, constructed only in PopupManager:61. No test or mock uses the PopupSupport interface (git grep -lw PopupSupport: only those three files).

### widgets-2-18: Generic names that clash with other Stroom or GWT types: Action, Option, Refreshable, TableRow, Hyperlink

*low, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/editor/client/Action.java:22`, `stroom-core-client-widget/src/main/java/stroom/editor/client/Option.java:19`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/KeyBinding.java`, `stroom-core-client-widget/src/main/java/stroom/data/table/client/Refreshable.java:19`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/Refreshable.java:21`, `stroom-core-client-widget/src/main/java/stroom/widget/util/client/TableRow.java`, `stroom-core-client/src/main/java/stroom/query/client/TableRow.java`, `stroom-core-client-widget/src/main/java/stroom/hyperlink/client/Hyperlink.java:31`

**Evidence:** stroom.editor.client.Action is a code editor's menu action (Format), and Option is an editor option. KeyBinding.Action in the same module is the keyboard command enum, and RefreshButton imports KeyBinding.Action. stroom.data.table.client.Refreshable has one method, refresh(); stroom.dashboard.client.main.Refreshable is a different 9-method contract (scheduleRefresh, run, getAutomate ...). stroom.widget.util.client.TableRow (an HTML table-builder row) shares its name with stroom.query.client.TableRow (a result row). stroom.hyperlink.client.Hyperlink is a parsed link value but takes the name of GWT's Hyperlink widget, which the UI guide bans and checkstyle lists (checkstyle.xml:157).

**Fix:** Rename to say what each is: EditorAction and EditorOption (or CodeEditorAction and CodeEditorOption); the widget-module Refreshable to a single-method `HasRefresh` (or reuse Runnable), and the dashboard one to `RefreshableComponent`; widget util TableRow to `HtmlTableRow` (TableBuilder and TableCell likewise to `HtmlTable*`); Hyperlink to `LinkValue` or `CellLink`.

**Verifier:** All the named files exist, and KeyBinding.java:702 declares its own `enum Action`. Hyperlink.java is a parsed link value class, and checkstyle.xml:157 lists com.google.gwt.user.client.ui.Hyperlink. stroom.data.table.client.Refreshable and the dashboard's Refreshable are different contracts with the same name. stroom.widget.util.client.TableRow and stroom.query.client.TableRow both exist.

### widgets-2-19: MySingleSelectionModel keeps its My prefix although its users have converted

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/util/client/MySingleSelectionModel.java`

**Evidence:** COMPONENT_MODEL_DESIGN rule 12: "`MySingleSelectionModel` when its users convert". There are no presenters left (wave 16). grep -rl MySingleSelectionModel finds 13 Java files still using the name.

**Fix:** Rename it to `SingleSelectionModel` (in stroom.widget.util.client; it extends GWT's, so qualify the import where needed) or `DoubleClickSelectionModel`, after what it adds (a double-select event), and update its 13 users.

**Verifier:** COMPONENT_MODEL_DESIGN.md:85 says `MySingleSelectionModel` is renamed "when its users convert". 13 Java files still use the name.

### widgets-2-20: XSD and XML classes use inconsistent acronym casing and 'Display' names that do not say what they show

*low, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplay.java:34`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDDisplayBox.java:29`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDConstraintDisplay.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XMLUtil.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/model/XMLStyler.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/model/XmlFormatter.java`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowser.ui.xml:20`, `stroom-app/src/main/resources/ui/css/theme-dark.css:171`

**Evidence:** XSDDisplay is the schema diagram (the template's ui:field is "diagram", the class xsd-browser__diagram). XSDDisplayBox is one box of that diagram, and XSDConstraintDisplay is the constraints pane. XMLStyler and XMLUtil sit beside XmlFormatter, while the workbench writes XsdBrowserStories. The theme's custom properties are --xsdbrowser-box__* and --xsdbrowser-title__* (theme-dark.css:171-174), not named for the xsd-browser block.

**Fix:** Use one casing (Xsd, Xml) and say what each is: XsdDiagram, XsdDiagramBox, XsdConstraints, XsdBrowser, XmlUtil. Rename the custom properties to --xsd-browser__box-* and --xsd-browser__title-*.

**Verifier:** The XSD classes (XSDDisplay, XSDDisplayBox, XSDConstraintDisplay, XSDModel and others) and XMLUtil use upper-case acronyms, while XmlFormatter and XsdBrowserStories do not. theme-dark.css:171-173 uses --xsdbrowser-box__* and --xsdbrowser-title__*.

### workbench-06: Story titles named as functions in lower camel case, with spaces, or without a group

*low, effort medium, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/DocPluginStories.java:84`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/CurrentActivityStories.java:64`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/annotations/DecorateCommentStories.java:79`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/core/AppPermissionsStories.java:60`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/core/DeleteConfirmationStories.java:81`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/aceeditor/FormatStories.java:49`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/formatdatetime/FormatDateTimeStories.java:50`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/screens/signin/RedirectUrlStories.java:57`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/ActivityChooserStories.java:72`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/popuppositioner/PopupContractStories.java:1`

**Evidence:** From `grep -rn 'registry.component("[^"]*[a-z]/[a-z]'`: 'App/Main/docPlugin', 'App/Main/currentActivity', 'App/Annotations/decorateComment', 'App/Core/appPermissions', 'App/Core/deleteConfirmation', 'Widgets/AceEditor/format', 'Widgets/formatDateTime' and 'Screens/SignIn/redirectUrl'. These are named after a method, not a thing. 'App/Main/Activity Chooser' and 'Widgets/PopupPositioner/Popup contract' contain spaces, which none of the other 220 titles do. 'Widgets/ActionMenuCell', 'Widgets/EntityCheckTree' and 'Widgets/formatDateTime' have no group, so each sits in a one-class package (widgets/actionmenucell, entitychecktree, formatdatetime).

**Fix:** Name each title in UpperCamel for the thing tested, under a group. For example: `App/Main/DocumentPlugin`, `App/Main/CurrentActivity`, `App/Annotations/CommentDecoration`, `App/Core/AppPermissionChecks`, `App/Core/DeleteConfirmation`, `Widgets/Editors & Viewers/AceEditorFormat`, `Widgets/Date & Time/FormatDateTime`, `App/IdP/RedirectUrl` (see workbench-07), `App/Main/ActivityChooser`, `Widgets/PopupPositioner/PopupContract`, `Widgets/Data Grid/ActionMenuCell` and `Widgets/Tree/EntityCheckTree`. Move the classes into the matching group packages.

**Verifier:** Grepping the registry.component titles gives the lower-camel leaves the finding lists (docPlugin, currentActivity, decorateComment, appPermissions, deleteConfirmation, format, formatDateTime, redirectUrl). It also gives the groupless 'Widgets/ActionMenuCell', 'Widgets/EntityCheckTree' and 'Widgets/formatDateTime'. 'Activity Chooser' and 'Popup contract' are the only last parts with spaces. Group names such as 'Data Grid' and 'Date & Time' do contain spaces, so the evidence's 'none of the other titles' claim holds only for the last parts. The defect stands.

### workbench-14: Comments and javadoc links name deleted ViewImpl classes

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/tree/ExplorerTreeStories.java:52`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/index/SolrIndexEditorStories.java:77`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/ActivityChooserStories.java:45`

**Evidence:** I checked every `[ClassName]` link in /// comments against `git ls-files '*.java'`. `[EntityTreeViewImpl]` (ExplorerTreeStories:52) is a broken link; the class is now EntityTree and EntityTreeController. SolrIndexEditorStories:77 cites 'SolrIndexFieldEditViewImpl.ui.xml'; it is now SolrIndexFieldEditor.ui.xml. ActivityChooserStories:45 says the button is built by `NavigationController (activityButton, updateActivitySummary)`, but it is declared in Navigation.ui.xml:44-45.

**Fix:** Point these at EntityTree, SolrIndexFieldEditor.ui.xml and Navigation.ui.xml.

**Verifier:** ExplorerTreeStories.java:52 links [EntityTreeViewImpl], which does not exist; EntityTree.java does. SolrIndexEditorStories.java:77 cites SolrIndexFieldEditViewImpl.ui.xml, but only SolrIndexFieldEditor.ui.xml exists. ActivityChooserStories.java:45 credits NavigationController with building the activity button, but the button is declared in Navigation.ui.xml:44-45.

### workbench-15: SidebarView keeps a View suffix for a sidebar renderer (already recorded in ISSUES.md)

*low, effort small, adjusted.*

**Where:** `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/manager/SidebarView.java:46`, `stroom-gwt/stroom-gwt-workbench-framework/src/main/java/stroom/gwt/workbench/framework/client/tree/SidebarModel.java:30`

**Evidence:** SidebarView 'Renders the sidebar tree ... and handles clicks, typing and keyboard navigation'. It is not a presenter's view, but it keeps the View suffix that the plan removed elsewhere; ISSUES.md:1790 lists it among the 'Four types keep "View" in their names'. Its model, SidebarModel, sits in a different package (client.tree).

**Fix:** Optionally rename SidebarView to `Sidebar` (or `SidebarRenderer`) and update the ISSUES.md:1790 entry. Leave SidebarModel and the other JVM-tested model classes in client.tree.

**Verifier:** The rename has merit: SidebarView is a renderer and event handler, not a presenter's view. ISSUES.md:1790 records it as one of the 'Four types [that] keep "View" in their names, as they aren't presenters' views', so the name is a known, recorded exception, not an oversight. The package split is also defensible. client.tree holds the pure-Java model classes with JVM unit tests (TestSidebarModel, TestStoryNavigation, TestStorySearch, TestStoryTreeBuilder). client.manager holds the DOM-bound UI classes. Moving SidebarModel would blur that line.

## Packages (29)

### dashboard-query-03: query and dashboard packages depend on each other: the shared results parts (table, vis, rules, filters) are split across both

*high, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVisController.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultVis.ui.xml:9`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryResultTableController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/EmbeddedQueryController.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryPane.ui.xml:30`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/ComponentSelection.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/FilterCellManager.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnValuesDataSupplier.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/table/GroupLevelButtons.java`, `stroom-core-client/src/main/java/stroom/query/client/TableRow.java`, `stroom-core-client/src/main/java/stroom/query/client/TableRowCell.java`

**Evidence:** git grep -h -o '^import stroom.dashboard.client...' -- stroom/query: 22 distinct dashboard imports, e.g. dashboard.client.component.VisFrame/VisFrameController/VisFrameLayout/RulesController/ColumnFilterEditorController/ColumnFormatController/ColumnValuesFilterController/DownloadSearchResultsController, dashboard.client.vis.VisToolbarRow/VisSelectionModel/VisToolbarButton, dashboard.client.table.FilterCell/GroupLevelButtons/TableRowStyles/ComponentSelection/FilterCellManager, dashboard.client.query.QueryInfo, dashboard.client.main.DashboardContext/UniqueUtil. The reverse: 30 distinct query.client imports in dashboard, e.g. query.client.TableRow/TableRowCell/QueryModel/ResultStoreModel/SearchStateListener, query.client.component.QueryResultTableController/QueryResultVisController/ExpressionTreeController/QueryButtons/QueryDocEditor.

**Fix:** Move the parts both screens use into one package that neither feature owns, e.g. stroom.query.client.result.{component,model,cell} (VisFrame*, VisToolbarRow, VisSelectionModel, VisToolbarButton, GroupLevelButtons, FilterCell, FilterCellManager, ColumnValuesDataSupplier, ComponentSelection, TableRow, TableRowCell, TableRowStyles, RulesController and the column filter/format editors). Then dashboard depends on query, and query does not depend on dashboard.

**Verifier:** grep of import lines: query imports 22 distinct stroom.dashboard.client types and dashboard imports 29 distinct stroom.query.client types, so the two packages depend on each other as stated.

### dashboard-query-09: Components and widgets live outside client.component, and stroom.query.client is a mixed package

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/TermEditor.java`, `stroom-core-client/src/main/java/stroom/query/client/component/TermEditorController.java`, `stroom-core-client/src/main/java/stroom/query/client/OperatorEditor.java`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionItemBox.java`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionItemRenderer.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/FlexLayout.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/TabLayout.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/flexlayout/Splitter.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisToolbarRow.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/DynamicSettingsPane.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnsManager.java`, `stroom-core-client/src/main/java/stroom/query/client/component/QueryTableColumnsManager.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/ListenerWhileShown.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/VisFrameLayout.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/component/WordItem.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/SearchStartEvent.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/event/ReopenResultStoreEvent.java`, `stroom-core-client/src/main/java/stroom/query/client/InsertQueryElementEvent.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/main/SearchModel.java`, `stroom-core-client/src/main/java/stroom/query/client/QueryModel.java`

**Evidence:** ls of the slice's packages. stroom.query.client (no sub-package) holds components and widgets (TermEditor extends Component, OperatorEditor/ExpressionTreePanel with templates, ExpressionItemBox), cells (ColumnHeaderCell, ColumnTitleCell, TableRowCell), models (QueryModel, ExpressionModel, ResultStoreModel, four *SelectionListModel), an event, REST clients, listeners and two plugins. TermEditor is in query.client while its TermEditorController is in query.client.component. The dashboard's layout components (FlexLayout, TabLayout, Splitter with .ui.xml) are in client.flexlayout, and VisToolbarRow and DynamicSettingsPane in client.vis. client.component holds non-component helpers (ColumnsManager, QueryTableColumnsManager, ListenerWhileShown, VisFrameLayout, WordItem, BasicSettingsFields). Events are spread over dashboard.client.event, dashboard.client.main, dashboard.client.vis and query.client, and models over query.client and dashboard.client.main.

**Fix:** Use one layout per feature: query.client.component (TermEditor, OperatorEditor, ExpressionTreePanel, ExpressionItemBox, ExpressionItemRenderer beside ExpressionTree/TermEditorController), query.client.model (QueryModel, ExpressionModel, ResultStoreModel, *SelectionListModel, TableRow), query.client.cell (ColumnHeaderCell, ColumnTitleCell, TableRowCell), query.client.event. For the dashboard: dashboard.client.component for FlexLayout/TabLayout/Splitter/VisToolbarRow/DynamicSettingsPane, dashboard.client.model for SearchModel/WordItem/the Mutable*Config classes, and dashboard.client.event for every dashboard event. ColumnsManager and its query twin go to a model or table package.

**Verifier:** ls stroom/query/client shows TermEditor (extends Component) and OperatorEditor/ExpressionTreePanel with templates, cells, models, events, REST clients and plugins all in one package, while TermEditorController is in query.client.component. The dashboard's flexlayout and vis packages hold the components FlexLayout, TabLayout, Splitter, VisToolbarRow and DynamicSettingsPane.

### dashboard-query-10: Generic or other features' code sits in dashboard/query packages (TimeZoneBox, HandlerRegistry, Clear*CacheEvent, pipeline's tree widgets)

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/query/client/component/TimeZoneBox.java`, `stroom-core-client/src/main/java/stroom/planb/client/component/HistogramKeySchemaSettingsForm.ui.xml:24`, `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.ui.xml:24`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/HandlerRegistry.java:24`, `stroom-core-client/src/main/java/stroom/script/client/ScriptCache.java:35`, `stroom-core-client/src/main/java/stroom/visualisation/client/VisFunctionCache.java:35`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/ClearScriptCacheEvent.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/ClearFunctionCacheEvent.java`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionTree.ui.xml:3`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:20`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionItemBox.java`

**Evidence:** git grep -w TimeZoneBox: used by planb (HistogramKeySchemaSettingsForm, MetricKeySchemaSettingsForm) and processor (ProcessorProfileEdit). It depends only on widgets and stroom.query.api, which stroom-core-client-widget already imports (QuickFilterTooltipUtil). HandlerRegistry (dashboard.client.vis) is used only by script.client.ScriptCache and visualisation.client.VisFunctionCache. ClearScriptCacheEvent is used by script.client.ScriptCache and ScriptEditor, ClearFunctionCacheEvent by visualisation.client.VisFunctionCache and importexport, and the dashboard uses neither. The query expression tree is built on pipeline.structure.client.component.TreePanel/Box/DraggableTreePanel (ExpressionTree.ui.xml xmlns:pv="urn:import:stroom.pipeline.structure.client.component").

**Fix:** Move TimeZoneBox to stroom-core-client-widget (e.g. stroom.widget.datepicker.client next to DateTimeBox). Move ClearScriptCacheEvent to stroom.script.client.event and ClearFunctionCacheEvent to stroom.visualisation.client.event. Replace HandlerRegistry with a plain list of registrations in the two caches, or move it to stroom.widget.util.client. Move TreePanel, Box, DraggableTreePanel and TreeKeyboard to a shared tree package (e.g. stroom.widget.htree.client) so that query no longer depends on pipeline.

**Verifier:** TimeZoneBox is used by planb (HistogramKeySchemaSettingsForm, MetricKeySchemaSettingsForm) and processor (ProcessorProfileEdit). Its imports are SelectionBox, MomentJs, FormGroup and ValueSpinner, all in stroom-core-client-widget, plus stroom.query.api, which the widget module already imports (QuickFilterTooltipUtil). HandlerRegistry is used only by ScriptCache and VisFunctionCache. The Clear*CacheEvent classes are used only by script, visualisation and importexport. ExpressionTreePanel and ExpressionItemBox import pipeline.structure.client.component.Box/TreePanel.

### data-pipeline-processing-05: Two near-identical processor-task screens, one of them in the data package

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.java`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTab.ui.xml`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksTabController.java:63`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.java`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.ui.xml`, `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasksController.java:50`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTaskSummaryList.java`, `stroom-core-client/src/main/java/stroom/data/client/component/ProcessorTasksList.java`

**Evidence:** Both templates are the same vertical SplitPane ('Task summary, Tasks') with two dashboard-panel SimplePanels. Both controllers inject ProcessorTaskSummaryListController and ProcessorTasksListController and build the same pipeline/feed/status expression from the selected summary row; ProcessorFilterTasksController also adds a processor-filter term. The task lists ProcessorTaskSummaryList and ProcessorTasksList live in stroom.data.client.component, not in stroom.processor.task.client.component.

**Fix:** Move ProcessorTaskSummaryList(Controller) and ProcessorTasksList(Controller) to stroom.processor.task.client.component and rename them ProcessorTaskSummaryList and ProcessorTaskList (the latter singular, like the other lists). Merge ProcessorTasksTab into ProcessorFilterTasks: one component, ProcessorTasks, with one controller whose read(docRef, doc) and setProcessorFilter(filter) set the base expression.

**Verifier:** Diffing the two templates shows only a comment, the split size (400px vs 200px) and the field names differ. Both controllers inject ProcessorTaskSummaryListController and ProcessorTasksListController and build the same PIPELINE/FEED/STATUS terms. ProcessorFilterTasksController adds PROCESSOR_FILTER_ID. ProcessorTaskSummaryList and ProcessorTasksList are in stroom.data.client.component.

### data-pipeline-processing-06: Generic tree widgets in pipeline.structure.client.component are imported by the query expression tree

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/Box.java:21`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/TreePanel.java`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/DraggableTreePanel.java`, `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/TreeKeyboard.java`, `stroom-core-client/src/main/java/stroom/query/client/component/ExpressionTree.java:20`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionTreePanel.java:20`, `stroom-core-client/src/main/java/stroom/query/client/ExpressionItemBox.java:19`

**Evidence:** `git grep 'import stroom.pipeline.structure' -- stroom-core-client/src/main/java/stroom/query` finds Box, TreePanel, DraggableTreePanel and TreeKeyboard. Box<E>, TreePanel<E> and DraggableTreePanel<E> are generic Composites. Their stroom imports are all stroom.widget.* (contextmenu, datagrid.MouseHelper, htree.treelayout, popup, util). None of them is a Component/Controller pair.

**Fix:** Move Box, TreePanel, DraggableTreePanel (with DraggableTreePanel.ui.xml) and TreeKeyboard to stroom-core-client-widget, in stroom.widget.htree.client next to its layout and renderers. Rename them for what they are, for example TreeItemBox, Tree, DraggableTree (its block is already draggable-tree) and TreeKeyboard.

**Verifier:** query/client/ExpressionItemBox.java:19, ExpressionTreePanel.java:20-21 and component/ExpressionTree.java:20-22 import Box, TreePanel, DraggableTreePanel and TreeKeyboard from pipeline.structure.client.component. Those classes' only stroom imports are stroom.widget.*, and I confirmed every one of them (ContextMenuManager, TextEditTarget, MouseHelper, PopupPosition, CssProperties, MouseUtil, MySingleSelectionModel, Rect, htree.treelayout) is in stroom-core-client-widget, so the move compiles.

### data-pipeline-processing-07: Generic grid cells, column sizes and helpers live in stroom.data.client and are used by about 20 other areas

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/data/client/ColumnSizeConstants.java`, `stroom-core-client/src/main/java/stroom/data/client/CopyTextCell.java`, `stroom-core-client/src/main/java/stroom/data/client/HasContextMenusCell.java`, `stroom-core-client/src/main/java/stroom/data/client/CopyTextUtil.java`, `stroom-core-client/src/main/java/stroom/data/client/CriteriaUtil.java`, `stroom-core-client/src/main/java/stroom/data/client/RestDataProvider.java`, `stroom-core-client/src/main/java/stroom/data/client/component/DocRefCell.java`, `stroom-core-client/src/main/java/stroom/data/client/component/FeedRefCell.java`, `stroom-core-client/src/main/java/stroom/data/client/component/UserRefCell.java`, `stroom-core-client/src/main/java/stroom/util/client/DataGridUtil.java:32`

**Evidence:** git grep fan-in for DocRefCell, UserRefCell, ColumnSizeConstants, RestDataProvider and HasContextMenusCell outside data/: security 19, analytics 9, node 8, pipeline 6, index 6, receive 5, explorer 5, plus others. ColumnSizeConstants has no stroom imports. CopyTextCell, HasContextMenusCell, CriteriaUtil and CopyTextUtil import only stroom.widget.* and stroom.util.shared/svg. DocRefCell, FeedRefCell and UserRefCell are cells but sit in a `component` package.

**Fix:** Move ColumnSizeConstants, CopyTextCell, HasContextMenusCell, CriteriaUtil and CopyTextUtil to stroom-core-client-widget (stroom.widget.datagrid.client or stroom.cell). Move DocRefCell, FeedRefCell and UserRefCell (they need core-client lookups) and RestDataProvider to a shared core-client package such as stroom.util.client.cell next to DataGridUtil. Do not leave them under data.client.component.

**Verifier:** ColumnSizeConstants has no stroom imports. CopyTextCell, HasContextMenusCell, CriteriaUtil and CopyTextUtil import only stroom.widget.*, stroom.util.shared and stroom.svg.shared.SvgImage, all of which the widget module already uses (e.g. ContextMenuManager imports SvgImage). Fan-in outside data/ for DocRefCell/UserRefCell/ColumnSizeConstants/RestDataProvider/HasContextMenusCell: security 19, analytics 9, node 8, pipeline 6, index 6, receive 5, explorer 5, and others.

### explorer-documents-shell-17: stroom.entity.client and stroom.document.client are near-duplicate packages; 'entity' is the legacy name for documents

*medium, effort large, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/entity/client/component/DocumentEditor.java:50`, `stroom-core-client/src/main/java/stroom/entity/client/component/DocumentEditorHost.java:66`, `stroom-core-client/src/main/java/stroom/entity/client/component/CopyDocument.java:33`, `stroom-core-client/src/main/java/stroom/entity/client/DocumentDialogScreens.java:41`, `stroom-core-client/src/main/java/stroom/document/client/DocumentPlugin.java:1`, `stroom-core-client/src/main/java/stroom/document/client/event/CopyDocumentEvent.java:1`, `stroom-core-client/src/main/java/stroom/entity/client/gin/EntityModule.java:23`

**Evidence:** stroom.entity.client.component holds:
- the document editor framework (DocumentEditor, DocumentEditorHost, MultiInstanceEditorHost, TabbedController, LinkTabPanel, DocumentChanges)
- the document dialogs (CopyDocument, CreateDocument, MoveDocument, InfoDocument, NameEntry)
- the Markdown documentation tab.
The events that open those dialogs (ShowCopyDocumentDialogEvent, CopyDocumentEvent and so on), DocumentPlugin and DocumentTabData are in stroom.document.client; document/asset is a third sibling. Every other feature keeps its components, events and plugin under one feature package. The framework classes (TabbedController, DocumentChanges, LinkTabPanel) are not dialogs or tabs, yet sit in client.component.

**Fix:** Merge entity into document: stroom.document.client.component for the dialogs and the documentation tab, stroom.document.client.editor (or client.framework) for DocumentEditor, DocumentEditorHost, TabbedController, LinkTabPanel and DocumentChanges, and stroom.document.client.gin for EntityModule (renamed DocumentModule). Move HasReadAndWrite and TreeRowHandler out as in -19.

**Verifier:** entity/client/component holds DocumentEditor, DocumentEditorHost, MultiInstanceEditorHost, TabbedController, LinkTabPanel and DocumentChanges, plus Copy/Create/Move/Info/NameEntry dialogs and the Markdown tab. document/client holds DocumentPlugin, DocumentTabData and the event package with ShowCopyDocumentDialogEvent and the rest. entity/client/gin holds EntityModule and EntityGinjector. These are near-duplicate feature packages.

### floormap-pathways-stores-30: Generic widgets and helpers live in feature packages: SettingsGroup in planb, FloorMapAria in floormap

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/planb/client/component/SettingsGroup.java:34`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapEventStoreSettings.ui.xml:1`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapAria.java:1`

**Evidence:** SettingsGroup is "A titled group of settings, e.g. Plan B's or the floor map's Retention". It has no Plan B code and is used by FloorMapEventStoreSettings.ui.xml as well as the planb templates (git grep -lw SettingsGroup). FloorMapAria's label/group/labelledBy/focusFirstFocusable/uniqueId are generic ARIA plumbing with nothing floor-map-specific.

**Fix:** Move SettingsGroup to stroom-core-client-widget's stroom.widget.form.client, next to FormGroup, and move settings-group.css with it unchanged. Move what is left of FloorMapAria after floormap-pathways-stores-20 into stroom.widget.util.client, or replace it with the widgets' typed ARIA setters in the templates.

**Verifier:** SettingsGroup imports only stroom.widget.form.client.FormLabel and GWT classes. Besides the planb templates, FloorMapEventStoreSettings.ui.xml uses it. FloorMapAria's remaining helpers have nothing floor-map-specific.

### floormap-pathways-stores-31: The monitoring package holds plugins and a screen belonging to other features

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/monitoring/client/JobListPlugin.java:23`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeGroupsPlugin.java:24`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeMonitoringPlugin.java:1`, `stroom-core-client/src/main/java/stroom/monitoring/client/ProcessorProfilePlugin.java:24`, `stroom-core-client/src/main/java/stroom/monitoring/client/ExecutionScheduleManagerPlugin.java:19`, `stroom-core-client/src/main/java/stroom/monitoring/client/component/DatabaseTablesMonitoringController.java:1`, `stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:38`

**Evidence:** The imports show where each plugin belongs: stroom.job.client.component.JobsController, stroom.node.client.component.NodeGroupsController/NodesController, stroom.processor.client.component.ProcessorProfilesController, stroom.analytics.client.component.ExecutionSchedulesController and stroom.node.shared.DbStatusResource. Meanwhile CacheMonitoringPlugin is in its own feature, stroom.cache.client, with CacheModule.

**Fix:** Move each plugin into its feature's client package with that feature's GIN module, as cache does:
- JobsPlugin to job/client
- NodesPlugin and NodeGroupsPlugin to node/client
- ProcessorProfilesPlugin to processor/client
- ExecutionSchedulesPlugin to analytics/client
- the database tables screen to node/client/component
MonitoringModule then keeps only what is truly cross-feature.

**Verifier:** monitoring/client holds JobListPlugin, NodeMonitoringPlugin, NodeGroupsPlugin, ProcessorProfilePlugin, ExecutionScheduleManagerPlugin and DatabaseTablesMonitoringPlugin, which import job, node, processor and analytics controllers. CacheMonitoringPlugin lives in cache/client.

### floormap-pathways-stores-32: The floor map's packages mix models, helpers and components, and an 'editor' package holds a model

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/floormap/client/editor/FloorMapEditorModel.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/editor/FloorMapDocSession.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/editor/FloorMapPendingChanges.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FactObject.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/MemberCandidate.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapImageSizeCache.java:29`, `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapFullReadQueryHelper.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapGrid.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapQueryBuilder.java:1`, `stroom-core-client/src/main/java/stroom/floormap/client/FloorMapEventQuery.java:1`, `stroom-core-client/src/main/java/stroom/pathways/client/Bezier.java:1`, `stroom-core-client/src/main/java/stroom/pathways/client/component/ConstraintValueText.java:1`

**Evidence:** - client/editor holds FloorMapEditorModel, FloorMapDocSession, FloorMapPendingChanges and FloorMapFactEndMessages, which are models and sessions with no GWT in them, while client/model exists beside it.
- client/component holds the row models FactObject and MemberCandidate, and the helpers FloorMapImageSizeCache, FloorMapFullReadQueryHelper and FloorMapEventStoreDataDefaults.
- The client root mixes SVG geometry (FloorMapGrid), query building (FloorMapQueryBuilder and FloorMapEventQuery, while playback/ has FloorMapEventsQuery), cell HTML and MapContextMenu.
- In pathways, Bezier sits at the root although only PathwayTree uses it, and ConstraintValueText (a parser) sits in component.

**Fix:** Make the layout one package per role:
- client/model: FloorMapEditorModel, FloorMapDocSession, FloorMapPendingChanges, FactObject→FactListRow and MemberCandidate
- client/query: FloorMapQueryBuilder, FloorMapEventQuery, FloorMapEventsQuery and FloorMapFullReadQueryHelper
- client/geometry: FloorMapGrid
- pathways/client/component: Bezier, beside PathwayTree (or a pathways/client/geometry)
- pathways/client/model: ConstraintValueText
Then remove client/editor.

**Verifier:** floormap/client/editor holds only FloorMapDocSession, FloorMapEditorModel, FloorMapFactEndMessages and FloorMapPendingChanges, beside floormap/client/model. FactObject and FloorMapInit are in component. Bezier is in pathways/client, and only PathwayTree uses it.

### security-settings-18: Generic helpers sit in feature packages and are used across the app

*medium, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/config/global/client/ListDataProvider.java`, `stroom-core-client/src/main/java/stroom/security/client/ClassNameBuilder.java`, `stroom-core-client/src/main/java/stroom/security/client/AbstractRestClient.java`, `stroom-core-client/src/main/java/stroom/alert/client/event/GwtStringBuilderOutputStream.java`, `stroom-core-client/src/main/java/stroom/alert/client/event/FireAlertEventFunction.java`

**Evidence:** ListDataProvider (config.global.client) is imported by about 15 features: floormap (6 classes), search elastic and solr, analytics, pipeline stepping, statistics, receive rules, and the workbench's DataGridStories and SplitPaneContent. ClassNameBuilder (security.client) is used by dashboard's AbstractRowStyles, FilterCell and ConditionalFormattingSwatchUtil and by annotation's Lozenge. AbstractRestClient (security.client) is the base of AnnotationResourceClient. GwtStringBuilderOutputStream and FireAlertEventFunction are in alert.client.event but are not events (used by DefaultErrorHandler, QueryPaneController and QueryToolbarController).

**Fix:** Move ListDataProvider to the data grid's package (stroom.data.grid.client, or the widget module once TreeRowHandler moves with it). Move ClassNameBuilder to stroom.widget.util.client in stroom-core-client-widget, where SafeHtmlUtil, which it uses, already lives. Move AbstractRestClient to stroom.dispatch.client. Move GwtStringBuilderOutputStream and FireAlertEventFunction to stroom.alert.client (or util).

**Verifier:** ListDataProvider is referenced by 16 files outside config/global/client: floormap (6), elastic, solr, analytics, stepping, receive rules, statistics, pipeline, and the workbench DataGridStories and SplitPaneContent. ClassNameBuilder is imported by annotation/Lozenge and by dashboard's AbstractRowStyles, FilterCell and ConditionalFormattingSwatchUtil. AbstractRestClient is imported by AnnotationResourceClient. GwtStringBuilderOutputStream and FireAlertEventFunction are used by DefaultErrorHandler, QueryPaneController and QueryToolbarController. SafeHtmlUtil is in the widget module's stroom/widget/util/client.

### widgets-1-13: Generic helpers live in feature-widget packages: Glass, MouseHelper and DoubleClickEvent in widget/datagrid; GlobalResizeObserver and ResizeListener in widget/tab

*medium, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/Glass.java:26`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/MouseHelper.java:23`, `stroom-core-client-widget/src/main/java/stroom/widget/datagrid/client/DoubleClickEvent.java:23`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/GlobalResizeObserver.java:24`, `stroom-core-client-widget/src/main/java/stroom/widget/tab/client/ResizeListener.java:21`

**Evidence:** grep -rlw outside widget/datagrid. Glass: AbstractPopupPanel, FlexLayout, TraceOverviewChart. MouseHelper: DraggableTreePanel, PipelineTreePanel, ExpressionTreePanel. DoubleClickEvent: TaskSpinner, MySingleSelectionModel, xsdbrowser ClickPanel/XSDNodeLabel, ResizableDialog. GlobalResizeObserver/ResizeListener: FlexLayout, Vis, PipelinePathBar, QueryResultVis, IFrameContent, CodeEditor, SingleLineCodeEditor, DataGrid and SplitPane, as well as the tab bars.

**Fix:** Move Glass, MouseHelper, DoubleClickEvent, GlobalResizeObserver and ResizeListener to stroom.widget.util.client (or a stroom.widget.event.client for DoubleClickEvent).

**Verifier:** git grep -lw shows Glass, MouseHelper, DoubleClickEvent, GlobalResizeObserver and ResizeListener are used well outside their packages: AbstractPopupPanel, FlexLayout, TraceOverviewChart, DraggableTreePanel, PipelineTreePanel, ExpressionTreePanel, TaskSpinner, MySingleSelectionModel, xsdbrowser ClickPanel/XSDNodeLabel, ResizableDialog, Vis, PipelinePathBar, QueryResultVis, IFrameContent, CodeEditor, SingleLineCodeEditor and SplitPane.

### widgets-2-21: The widget module's stroom.data.* packages are a grab bag that splits with core-client's stroom.data feature

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/data/pager/client/Pager.java`, `stroom-core-client-widget/src/main/java/stroom/data/pager/client/RefreshButton.java:36`, `stroom-core-client-widget/src/main/java/stroom/data/table/client/Refreshable.java`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/SelectAllEvent.java`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/DataSelectionEvent.java`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/AskStroomAiEvent.java:26`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/AskStroomAiTableRequest.java:31`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/ShowAskStroomAiEvent.java:25`, `stroom-core-client-widget/src/main/resources/stroom/data/Data.gwt.xml`

**Evidence:** stroom.data.client in stroom-core-client is the data viewer feature (DataPreviewTabPlugin, SourceTabPlugin ...). In the widget module, stroom.data.client.event holds AI events (AskStroomAiEvent, AskStroomAiTableRequest, ShowAskStroomAiEvent), a data-grid event (SelectAllEvent, used only by widget/datagrid/client/DataGridSelectionEventManager.java:19) and an XSD event (DataSelectionEvent, used only by XSDModel). stroom.data.pager.client holds the pager and RefreshButton, a ToolbarButton subclass used in every toolbar (Toolbar#refresh). stroom.data.table.client holds one interface. ShowAskStroomAiEvent's only users are in stroom-core-client and the workbench (AskStroomAiScreens, AskStroomAiController, MainController), none in the widget module.

**Fix:** Move Pager to stroom.widget.pager.client (or into widget.datagrid, beside PagerPanel), RefreshButton to stroom.widget.button.client and SelectAllEvent to stroom.widget.datagrid.client. Move ShowAskStroomAiEvent to stroom-core-client stroom.ai.client.event. Put AskStroomAiEvent and AskStroomAiTableRequest in widget.datagrid.client, since DataGrid makes the request. Replace DataSelectionEvent with a callback (widgets-2-24). Retire Data.gwt.xml, Table.gwt.xml and Pager.gwt.xml for the new homes.

**Verifier:** In the widget module, stroom.data.client.event holds AskStroomAiEvent, AskStroomAiTableRequest, DataSelectionEvent, HasDataSelectionHandlers, SelectAllEvent and ShowAskStroomAiEvent. SelectAllEvent is used only by DataGridSelectionEventManager, and DataSelectionEvent only by XSDModel. ShowAskStroomAiEvent's users are all in stroom-core-client and the workbench, none in the widget module.

### widgets-2-22: Split packages: stroom.task.client and stroom.hyperlink.client exist in both the widget module and stroom-core-client

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/task/client/TaskMonitorFactory.java`, `stroom-core-client/src/main/java/stroom/task/client/TaskManagerPlugin.java`, `stroom-core-client-widget/src/main/java/stroom/hyperlink/client/Hyperlink.java`, `stroom-core-client/src/main/java/stroom/hyperlink/client/HyperlinkEventHandlerImpl.java`, `stroom-core-client/src/main/resources/stroom/hyperlink/Hyperlink.gwt.xml`

**Evidence:** I listed both modules' stroom/<pkg>/client directories. stroom-core-client has stroom/task/client (TaskManagerPlugin, TaskInfoParser, component, event, gin), and the widget module has stroom/task/client (Task, TaskMonitor, TaskMonitorFactory, DefaultTaskMonitorFactory, SimpleTask, TaskStartEvent, TaskEndEvent). stroom-core-client has stroom/hyperlink/client (HyperlinkEventHandlerImpl, HyperlinkName, HyperlinkType, ShowDashboardEvent), and the widget module has Hyperlink, HyperlinkEvent, HyperlinkTargetType and OpenHyperlinkRequest. Hyperlink.gwt.xml lives in core-client's resources but compiles the widget module's half too.

**Fix:** Give the widget module's halves their own packages, e.g. stroom.widget.task.client (the task-monitor API the widgets use) and stroom.widget.link.client (the parsed link and its open request), or move the core-client halves into feature sub-packages. Either way, one package name should belong to one module.

**Verifier:** I compared the package directories of stroom-core-client and stroom-core-client-widget. The only packages split across both modules are stroom/hyperlink/client and stroom/task/client, so they are anomalies rather than a project convention.

### widgets-2-23: Classes in the wrong feature's package: IconBox, HtmlPane, CurrentPreferences, ShowHelpEvent, ScrollbarMetrics

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/svg/client/IconBox.java:44`, `stroom-core-client-widget/src/main/java/stroom/editor/client/component/HtmlPane.java:29`, `stroom-core-client-widget/src/main/java/stroom/editor/client/CurrentPreferences.java`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ChangeCurrentPreferencesEvent.java`, `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/event/ShowHelpEvent.java:17`, `stroom-core-client-widget/src/main/java/stroom/widget/help/client/HelpManager.java:19`, `stroom-core-client-widget/src/main/java/stroom/editor/client/ace/ScrollbarMetrics.java:24`

**Evidence:** IconBox is a field with an icon at its end, used by DateTimeBox, TimeBox, BaseSelectionBox and ScheduleBox, but it lives in stroom.svg.client beside Icon. HtmlPane is a scrolling pane of HTML (used by TextPaneController, DataViewerController and QueryTextPane), not an editor, but it lives in stroom.editor.client.component. CurrentPreferences holds the app theme and visualisation palette, and ChangeCurrentPreferencesEvent is consumed by Histogram, FloorMapTimeline, VisController and MarkdownPreviewController, yet both live in stroom.editor.client. ShowHelpEvent is handled only by stroom.widget.help.client.HelpManager (line 19), yet it lives in stroom.widget.tooltip.client.event. ScrollbarMetrics is a generic browser measurement in stroom.editor.client.ace.

**Fix:** Move IconBox to stroom.widget.form.client (or stroom.widget.iconbox.client), HtmlPane to stroom.widget.text.client (beside Html), CurrentPreferences and ChangeCurrentPreferencesEvent to a preferences package (e.g. stroom.widget.preferences.client, or stroom.preferences.client if the widgets can take the values by setter), ShowHelpEvent to stroom.widget.help.client, and ScrollbarMetrics to stroom.widget.util.client.

**Verifier:** IconBox sits in svg/client beside Icon. HtmlPane is in editor/client/component, and its users are TextPaneController, QueryTextPane and DataViewerController. CurrentPreferences and ChangeCurrentPreferencesEvent are in editor/client. ShowHelpEvent's handler is HelpManager in widget/help/client. ScrollbarMetrics is in editor/client/ace.

### widgets-2-24: Feature code in the widget module: the XSD browser, used only by the XML Schema editor, still talks through a bus-style DataSelectionEvent

*medium, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/component/XSDBrowserController.java:53`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDModel.java:39`, `stroom-core-client-widget/src/main/java/stroom/widget/xsdbrowser/client/XSDModel.java:190`, `stroom-core-client-widget/src/main/java/stroom/data/client/event/HasDataSelectionHandlers.java`, `stroom-core-client/src/main/java/stroom/xmlschema/client/component/XMLSchemaEditor.java`

**Evidence:** XSDBrowserController's only non-story user is stroom-core-client/.../xmlschema/client/component/XMLSchemaEditor.java. The whole stroom.widget.xsdbrowser package (17 files plus XSDBrowser.gwt.xml) serves that one document type. XSDModel implements HasDataSelectionHandlers<XSDNode> with a HandlerManager and fires DataSelectionEvent at lines 165-236, and its one listener (XSDBrowserController.java:53) could take a callback. Rule 2 says outputs are callbacks.

**Fix:** Move stroom.widget.xsdbrowser to stroom-core-client as stroom.xmlschema.client.xsdbrowser (component, model). Replace DataSelectionEvent and HasDataSelectionHandlers with `XSDModel.setOnSelect(BiConsumer<XSDNode, Boolean>)` or a Listeners<Selection> list, and delete both.

**Verifier:** XSDBrowserController's only users are XMLSchemaEditor (stroom-core-client) and XsdBrowserStories. HasDataSelectionHandlers and DataSelectionEvent are used only by XSDModel.

### workbench-07: Story packages split by area inconsistently: app/main is a catch-all beside area packages for the same areas

*medium, effort large, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/UsersScreenStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/DocumentPermissionsScreenStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/security/DocumentPermissionsTabStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/AnnotationEditorStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/annotations/DecorateCommentStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/IndexVolumesScreenStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/index/IndexEditorStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/DataScreenStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/data/MetaBrowserStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/main/ChangePasswordDialogStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/idp/ChangePasswordStories.java:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/screens/signin/RedirectUrlStories.java:44`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/dashboard/QueryResultsTableDashboardStories.java:71`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/query/QueryResultsTableStories.java:67`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/AlertScreenModule.java:26`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/AlertScreenModule.java:26`

**Evidence:** app/main holds 79 files. Grouped by the Stroom area they import, there are 15 security stories (Users, UserGroups, Accounts, ApiKeys, DocumentPermissions...), 6 pipeline, 5 processor, 5 node, 7 explorer, 3 importexport, 2 annotation and others. Yet app/security, app/data, app/index, app/annotations, app/core and app/idp also exist. So 'App/Main/DocumentPermissionsScreen' and 'App/Security/DocumentPermissionsTab' live apart, as do 'App/Main/AnnotationEditor' and 'App/Annotations/*', and 'App/Main/IndexVolumesScreen' and 'App/Index/*'. A third top-level root, 'Screens' (screens/signin), has one story, about LoginController, while the other sign-in stories are in app/idp. Two classes register the same title 'App/Query/QueryResultsTable' from different packages (app/dashboard and app/query), against WRITING-STORIES' 'One XxxStories class per title'. Two GIN modules share the simple name AlertScreenModule (see workbench-01).

**Fix:** Group the App stories by Stroom area, as the client packages are: e.g. `App/Security/*` → app.security, `App/Processing/*` → app.processing, `App/Pipeline/*`, `App/Explorer/*`, `App/Nodes/*`, `App/Annotations/*`, `App/Index/*`, `App/Data/*`. Keep `App/Main` for the shell (AppShell, TopBar, ContentTabPane, About, Welcome). Move RedirectUrlStories to `App/IdP/RedirectUrl` in app.idp and delete the `Screens` root and ScreensStories if nothing else uses it. Merge QueryResultsTableDashboardStories into QueryResultsTableStories, or give it its own title, e.g. `App/Dashboard/QueryResultsTable`.

**Verifier:** app/main holds 79 files, while app/security, app/data, app/index, app/annotations, app/core and app/idp also exist. screens/signin holds only RedirectUrlStories, which ScreensStories registers. 'App/Query/QueryResultsTable' is registered at both app/dashboard/QueryResultsTableDashboardStories.java:71 and app/query/QueryResultsTableStories.java:67, against WRITING-STORIES.md:38 ('XxxStories class per title').

### dashboard-query-11: Nine 'XxxPlugin' pane-type registrar classes spread over six small dashboard GIN modules

*low, effort small, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/TablePlugin.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/text/TextPlugin.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/vis/VisPlugin.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryPlugin.java:25`, `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/EmbeddedQueryPlugin.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/input/KeyValueInputPlugin.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/gin/DashboardGinjector.java:36`, `stroom-core-client/src/main/java/stroom/dashboard/client/text/gin/TextModule.java:27`, `stroom-core-client/src/main/java/stroom/dashboard/client/query/gin/QueryModule.java`, `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/gin/EmbeddedQueryModule.java`

**Evidence:** Each 'XxxPlugin' is a plain class (no Plugin superclass) whose constructor only calls componentRegistry.register(XController.TYPE, provider), e.g. TablePlugin. Each is bound asEagerSingleton in its own module (TextModule, VisModule, TableModule, QueryModule, InputModule, EmbeddedQueryModule), which DashboardGinjector gathers with @GinModules. The packages dashboard.client.text and dashboard.client.embeddedquery hold nothing else, and dashboard.client.query.QueryPlugin has the same simple name as the document plugin stroom.query.client.QueryPlugin.

**Fix:** Replace the nine registrars with one eager DashboardPaneTypes class (or registrations in DashboardModule) that registers each pane type's controller provider, and fold the six modules into it. Move TimeZones and VisFunctionCache to their own features' modules (see dashboard-query-08). If a class per type is kept, name it XxxPaneType, not XxxPlugin.

**Verifier:** The count is wrong. There are nine registrar 'Plugin' classes (TablePlugin, TextPlugin, VisPlugin, dashboard QueryPlugin, EmbeddedQueryPlugin, KeyValueInputPlugin, ListInputPlugin, TextInputPlugin, TableFilterPlugin), not six. Of the six modules, InputModule binds four registrars and TableModule, VisModule and EmbeddedQueryModule each bind a second class (TimeZones, VisFunctionCache), so they are not all one-binding. The point stands: each registrar only calls componentRegistry.register(TYPE, provider), and the text and embeddedquery packages hold nothing else.

### data-pipeline-processing-08: Empty TaskModule, and plugins and bindings bound from other areas' modules (ProcessorTaskPlugin in PipelineModule, ManageGlobalPropertiesPlugin in node, DelegatingAceCompleter in StreamStoreModule)

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/task/client/gin/TaskModule.java:21`, `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppGinjectorUser.java:125`, `stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:49`, `stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:53`, `stroom-core-client/src/main/java/stroom/monitoring/client/JobListPlugin.java`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeMonitoringPlugin.java`, `stroom-core-client/src/main/java/stroom/monitoring/client/NodeGroupsPlugin.java`, `stroom-core-client/src/main/java/stroom/monitoring/client/ProcessorProfilePlugin.java`, `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:38`, `stroom-core-client/src/main/java/stroom/data/client/gin/StreamStoreModule.java:34`, `stroom-core-client/src/main/java/stroom/node/client/ManageGlobalPropertiesPlugin.java:37`

**Evidence:** TaskModule.configure() is empty but is installed in AppGinjectorUser:125. TaskManagerPlugin, ManageFsVolumesPlugin and ManageIndexVolumesPlugin are bound in MonitoringModule. The Jobs, Nodes, node-group and processor-profile screens are opened from plugins in stroom.monitoring.client, while their controllers are in stroom.job, stroom.node and stroom.processor. ProcessorTaskPlugin (stroom.processor.task) is bound from PipelineModule under '// Add processor bindings.'. StreamStoreModule binds the editor's DelegatingAceCompleter eagerly. ManageGlobalPropertiesPlugin opens stroom.config.global.client's GlobalPropertyTabController but sits in stroom.node.client. The data area's module is named StreamStoreModule, not DataModule.

**Fix:** Bind TaskManagerPlugin in TaskModule (or delete TaskModule and AppGinjectorUser:125). Bind ProcessorTaskPlugin from a processor gin module. Move ManageGlobalPropertiesPlugin to stroom.config.global.client. Move the DelegatingAceCompleter binding to the editor's or core's module. Optionally rename StreamStoreModule to DataModule. Moving the Monitoring menu plugins into their feature packages is a choice for the team (menu grouping vs feature grouping), not a defect.

**Verifier:** Every fact checks out. TaskModule.configure() is empty and installed at AppGinjectorUser:125. MonitoringModule:41-53 binds JobListPlugin, NodeGroupsPlugin, NodeMonitoringPlugin, ProcessorProfilePlugin, ManageFsVolumesPlugin, ManageIndexVolumesPlugin, ManageGlobalPropertiesPlugin and TaskManagerPlugin. PipelineModule:36-38 binds ProcessorTaskPlugin under '// Add processor bindings.'. StreamStoreModule:34 binds DelegatingAceCompleter eagerly. ManageGlobalPropertiesPlugin is in node.client but opens config.global's GlobalPropertyTabController. However, stroom.monitoring.client gathers the Monitoring menu's entries, which is a defensible grouping by menu rather than an error, so only the empty TaskModule, the misplaced ProcessorTaskPlugin/ManageGlobalPropertiesPlugin bindings and the editor binding are clear defects. Lower the severity.

### data-pipeline-processing-24: DaysOfWeek, a generic dumb widget, lives in the processor feature package

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/processor/client/component/DaysOfWeek.java:35`, `stroom-core-client/src/main/java/stroom/processor/client/component/DaysOfWeek.ui.xml`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/inputs/ViewStateStories.java:35`

**Evidence:** DaysOfWeek extends Composite (not Component) and imports only stroom.util.shared.time and stroom.widget.tickbox.client.TickBox. The workbench lists it among the generic input widgets (ViewStateStories), and the design doc names it with the renamed widgets (rule 12: DaysWidget to DaysOfWeek).

**Fix:** Move DaysOfWeek and its template to stroom-core-client-widget, for example stroom.widget.datepicker.client next to TimeBox, and keep its days-of-week block and sheet.

**Verifier:** DaysOfWeek extends Composite and imports only stroom.util.shared.time.Day/Days and stroom.widget.tickbox.client.TickBox. The widget module already imports stroom.util.shared.time (DurationPicker, TimeBoxController), so it can move there. Its only users are ProfilePeriodEditor and the workbench's ViewStateStories. COMPONENT_MODEL_DESIGN rule 12 lists it among the renamed generic widgets.

### explorer-documents-shell-18: Components and events outside the feature's client/component and client/event packages; non-events in event packages

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/annotation/client/SettingBlock.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/AssignYourselfLink.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationChangeEvent.java:26`, `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagNameChangeEvent.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/CreateAnnotationEvent.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/EditAnnotationEvent.java:1`, `stroom-core-client/src/main/java/stroom/annotation/client/ShowFindAnnotationEvent.java:1`, `stroom-core-client/src/main/java/stroom/schedule/client/ScheduleBox.java:1`, `stroom-core-client/src/main/java/stroom/schedule/client/ScheduleBoxController.java:1`, `stroom-core-client/src/main/java/stroom/folder/client/FolderTabs.java:42`, `stroom-core-client/src/main/java/stroom/hyperlink/client/ShowDashboardEvent.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/event/ExplorerTaskMonitorFactory.java:1`, `stroom-core-client/src/main/java/stroom/document/client/event/ResultCallback.java:1`, `stroom-core-client/src/main/java/stroom/core/client/event/DirtyKeyDownHander.java:21`, `stroom-core-client/src/main/java/stroom/explorer/client/component/TagListModel.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/OpenItems.java:1`

**Evidence:** - annotation/client/ holds two template-backed widgets (SettingBlock, AssignYourselfLink) and five events beside its plugins, while annotation/client/component holds the rest.
- schedule/client holds ScheduleBox (a .ui.xml widget) and ScheduleBoxController, while its SchedulePicker pair is in schedule/client/component.
- FolderTabs (a TabbedController) is in folder/client.
- ShowDashboardEvent is in hyperlink/client but is handled by DashboardPlugin and fired by the hyperlink handler.
- event packages hold non-events: ExplorerTaskMonitorFactory (a TaskMonitorFactory), ResultCallback (a callback interface) and DirtyKeyDownHander (a KeyDownHandler).
- Models sit in component packages: TagListModel and OpenItems in explorer/client/component.

**Fix:** Move:
- SettingBlock and AssignYourselfLink to annotation/client/component
- the five annotation events to annotation/client/event
- ScheduleBox and ScheduleBoxController to schedule/client/component
- FolderTabs to folder/client/component
- ShowDashboardEvent to dashboard/client/event
- ExplorerTaskMonitorFactory to explorer/client
- ResultCallback to document/client
- DirtyKeyDownHander as in -15
- TagListModel and OpenItems to explorer/client/model.

**Verifier:** annotation/client holds SettingBlock, AssignYourselfLink (with .ui.xml) and five *Event classes beside the plugins. schedule/client holds ScheduleBox(.ui.xml) and ScheduleBoxController, while SchedulePicker is in schedule/client/component. folder/client/FolderTabs.java:42 extends TabbedController. ShowDashboardEvent's only handler is dashboard/client/DashboardPlugin. ExplorerTaskMonitorFactory implements TaskMonitorFactory; ResultCallback is an interface in document/client/event. TagListModel and OpenItems are models in explorer/client/component, and floormap/client/model shows the model-package convention exists.

### explorer-documents-shell-19: Feature classes sit in this slice's explorer, main, entity, core and hyperlink packages, though only one other feature uses them

*low, effort medium, adjusted.*

**Where:** `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentList.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentListController.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentPermissionsList.java:1`, `stroom-core-client/src/main/java/stroom/explorer/client/component/DocumentPermissionsListController.java:1`, `stroom-core-client/src/main/java/stroom/main/client/DynamicStyles.java:29`, `stroom-core-client/src/main/java/stroom/core/client/messages/ErrorMessageTemplates.java:1`, `stroom-core-client/src/main/java/stroom/entity/client/HasReadAndWrite.java:1`, `stroom-core-client/src/main/java/stroom/entity/client/TreeRowHandler.java:1`, `stroom-core-client/src/main/java/stroom/hyperlink/client/HyperlinkName.java:1`, `stroom-core-client/src/main/java/stroom/util/client/DurationUtil.java:1`, `stroom-core-client/src/main/java/stroom/util/client/NumberUtil.java:1`, `stroom-core-client/src/main/java/stroom/util/client/CountDownAndRun.java:1`, `stroom-core-client/src/main/java/stroom/core/client/OwnedContentTabs.java:1`, `stroom-core-client/src/main/java/stroom/widget/histogram/client/HistogramQueryHelper.java:1`

**Evidence:** Fan-in from my index (users outside the class's own file):
- DocumentListController: only BatchDocumentPermissionsController. DocumentPermissionsListController: only UserPermissionReportController. Both are security screens.
- DynamicStyles: only dashboard's ConditionalFormattingDynamicStyles.
- ErrorMessageTemplates: only QueryPaneController and QueryToolbarController.
- HasReadAndWrite: only dashboard's DynamicSettingsPane.
- TreeRowHandler: the data-grid providers (RestDataProvider, ListDataProvider, MetaList, ProcessorList, TaskManagerList).
- HyperlinkName: only dashboard's TableRow.
- DurationUtil: only TracesList. NumberUtil: only TracesList and TraceHistogramChart (pathways).
- CountDownAndRun: only ProcessorsController.
- OwnedContentTabs: only EmbeddedQueryController.
- HistogramQueryHelper: imports RestFactory and query APIs, and is used only by floormap controllers.

**Fix:** Move DocumentList(+Controller) and DocumentPermissionsList(+Controller) to stroom.security.client.component, DynamicStyles to stroom.dashboard.client.table.cf, ErrorMessageTemplates and HyperlinkName to stroom.query.client, HasReadAndWrite to stroom.dashboard.client.vis, OwnedContentTabs to stroom.dashboard.client, and HistogramQueryHelper to stroom.floormap.client. Leave the generic utilities (TreeRowHandler, DurationUtil, NumberUtil, CountDownAndRun) where they are.

**Verifier:** Fan-in checked with grep -rlw over the client modules. Confirmed: DocumentListController is used only by security BatchDocumentPermissionsController, DocumentPermissionsListController only by UserPermissionReportController, DynamicStyles only by dashboard ConditionalFormattingDynamicStyles, HasReadAndWrite only by dashboard DynamicSettingsPane, OwnedContentTabs only by dashboard EmbeddedQueryController (plus its test), and HistogramQueryHelper only by floormap. Refuted or wrong in part: TreeRowHandler has 6 users across data, processor, config and task lists, so it is a shared data-grid helper. DurationUtil, NumberUtil and CountDownAndRun are generic utilities in util/client, and having one user today does not make them feature code. HyperlinkName's user is stroom.query.client.TableRow (not dashboard's), so its target would be stroom.query.client. ErrorMessageTemplates is used by both dashboard and query, so stroom.query.client is reasonable.

### explorer-documents-shell-20: The same package name is split across modules, or near-duplicated: stroom.hyperlink.client, quickfilter, help, and stroom.widget.histogram in stroom-core-client

*low, effort medium, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/hyperlink/client/HyperlinkType.java:1`, `stroom-core-client-widget/src/main/java/stroom/hyperlink/client/HyperlinkEvent.java:1`, `stroom-core-client/src/main/java/stroom/quickfilter/client/QuickFilterContextHandlerFactory.java:1`, `stroom-core-client/src/main/java/stroom/quickfilter/client/component/AdvancedQuickFilter.java:1`, `stroom-core-client-widget/src/main/java/stroom/widget/quickfilter/client/QuickFilter.java:1`, `stroom-core-client/src/main/java/stroom/help/client/HelpPlugin.java:1`, `stroom-core-client-widget/src/main/java/stroom/widget/help/client/HelpManager.java:1`, `stroom-core-client/src/main/java/stroom/widget/histogram/client/Histogram.java:56`, `stroom-core-client/src/main/java/stroom/menubar/client/event/BeforeRevealMenubarEvent.java:1`

**Evidence:** - stroom.hyperlink.client exists in both stroom-core-client (HyperlinkEventHandlerImpl, HyperlinkName, HyperlinkType, ShowDashboardEvent) and stroom-core-client-widget (Hyperlink, HyperlinkEvent, OpenHyperlinkRequest, HyperlinkTargetType).
- stroom.quickfilter.client (core-client: QuickFilterContextHandlerFactory, AdvancedQuickFilter) sits beside stroom.widget.quickfilter.client (widget module: QuickFilter, QuickFilterContextHandler...); help/client against widget/help/client is the same pattern.
- stroom.widget.histogram.client, a widget package name, lives in stroom-core-client and mixes a generic widget (Histogram, HistogramLayout, HistogramDataModel) with floormap query code (HistogramQueryHelper).
- stroom.menubar is a whole package holding one event.

**Fix:** Give each its own package per module:
- core-client's hyperlink classes -> stroom.hyperlink.client.app (or into dashboard and core as their users dictate)
- stroom.quickfilter.client -> stroom.widget.quickfilter.client.app, or rename to stroom.query.client.quickfilter
- stroom.help.client -> stroom.core.client.help
- Histogram, HistogramLayout and HistogramDataModel -> stroom-core-client-widget (stroom.widget.histogram.client); HistogramQueryHelper -> stroom.floormap.client
- BeforeRevealMenubarEvent -> stroom.core.client.event beside MenuKeys and Plugin.

**Verifier:** stroom.hyperlink.client exists in both stroom-core-client and stroom-core-client-widget (Hyperlink, HyperlinkEvent, HyperlinkTargetType, OpenHyperlinkRequest). stroom.quickfilter.client (core-client) sits beside stroom.widget.quickfilter.client (widget), and help/client beside widget/help/client. stroom.widget.histogram.client in core-client holds Histogram/HistogramLayout/HistogramDataModel together with HistogramQueryHelper. Histogram imports only stroom.query.api, widget and datepicker classes, and stroom-core-client-widget/build.gradle already depends on stroom-query-api, so the move is feasible. stroom/menubar has only client/event/BeforeRevealMenubarEvent.java.

### floormap-pathways-stores-33: Events sit at the feature root rather than in client/event, and SupportedRetentionAge belongs to index, not feed

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/feed/client/OpenFeedEvent.java:29`, `stroom-core-client/src/main/java/stroom/feed/client/CopyFeedUrlEvent.java:27`, `stroom-core-client/src/main/java/stroom/pathways/client/ShowTracesEvent.java:1`, `stroom-core-client/src/main/java/stroom/feed/client/SupportedRetentionAge.java:1`, `stroom-core-client/src/main/java/stroom/index/client/component/IndexSettings.java:19`

**Evidence:** content/client/event holds the content events, while feed's and pathways' events are at stroom.feed.client and stroom.pathways.client. `git grep -nw SupportedRetentionAge` finds its only user in index/client/component/IndexSettings.java (:19, :55, :103).

**Fix:** Move OpenFeedEvent and CopyFeedUrlEvent to stroom.feed.client.event, and ShowTracesEvent to stroom.pathways.client.event. Move SupportedRetentionAge to stroom.index.client (or index/client/model).

**Verifier:** 16 feature client/event packages exist, while 12 events sit at feature roots, including feed/client/OpenFeedEvent, CopyFeedUrlEvent and pathways/client/ShowTracesEvent. git grep -nw SupportedRetentionAge finds its only user in index/client/component/IndexSettings.java:19, :55 and :103-104.

### security-settings-19: Classes in the wrong feature's package, and two event packages under security

*low, effort small, confirmed.*

**Where:** `stroom-core-client/src/main/java/stroom/receive/rules/client/ContentTemplatePlugin.java`, `stroom-core-client/src/main/java/stroom/receive/content/client/gin/ContentTemplateModule.java:27`, `stroom-core-client/src/main/java/stroom/security/client/ExplorerClient.java`, `stroom-core-client/src/main/java/stroom/security/client/api/event/CurrentUserChangedEvent.java`, `stroom-core-client/src/main/java/stroom/security/client/event/OpenUsersScreenEvent.java`, `stroom-core-client/src/main/java/stroom/activity/client/SplashScreen.java`, `stroom-core-client/src/main/java/stroom/config/global/client/NodeSource.java`, `stroom-core-client/src/main/java/stroom/config/global/client/component/ConfigPropertyRow.java`, `stroom-core-client/src/main/java/stroom/config/global/client/component/ClusterValuesRow.java`, `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionImpactRow.java`, `stroom-core-client/src/main/java/stroom/preferences/client/component/KeyBindingRow.java`, `stroom-core-client/src/main/java/stroom/credentials/client/CredentialListModel.java`

**Evidence:** ContentTemplatePlugin is in receive.rules.client but is bound by receive.content.client.gin.ContentTemplateModule, beside the content-template components. ExplorerClient (an explorer REST client) is in security.client. Security's events are split between security.client.api.event (CurrentUserChangedEvent, LogoutEvent, RequestLogoutEvent) and security.client.event (the Open*Screen events). The splash screen (Splash, SplashController, SplashScreen: the site's terms dialog) lives in activity.client. Row and model classes (ConfigPropertyRow, ClusterValuesRow, DataRetentionImpactRow, KeyBindingRow, the TreeActions) sit in client.component, while CredentialListModel and NodeSource sit in the client root, not in client/model.

**Fix:** Move ContentTemplatePlugin to receive.content.client and ExplorerClient to explorer.client. Merge security.client.api.event into security.client.event (keeping ClientSecurityContext in api if wanted). Move the splash classes to a splash (or core) package. Put row and model classes in each area's client.model package.

**Verifier:** receive/rules/client/ContentTemplatePlugin.java is bound by receive/content/client/gin/ContentTemplateModule.java:27. ExplorerClient is in security/client. security/client/api/event holds CurrentUserChangedEvent, LogoutEvent and RequestLogoutEvent, while security/client/event holds the Open* events. SplashScreen is in activity/client. NodeSource and CredentialListModel sit in client roots, and the row classes in client/component.

### widgets-1-14: Cell and selection packages are named for nothing they hold: stroom.cell.info (action, colour, percent, link and text cells), stroom.cell.*.shared (client-only), stroom.item (selection boxes)

*low, effort large, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/cell/info/client/ActionCell.java:41`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/PercentBarCell.java:28`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/ColourSwatchCell.java:25`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/RedGreenTextCell.java:26`, `stroom-core-client-widget/src/main/java/stroom/cell/info/client/CommandLinkCell.java:43`, `stroom-core-client-widget/src/main/java/stroom/cell/tickbox/shared/TickBoxState.java:1`, `stroom-core-client-widget/src/main/java/stroom/cell/valuespinner/shared/EditableInteger.java:1`, `stroom-core-client-widget/src/main/java/stroom/item/client/SelectionBox.java:28`, `stroom-core-client-widget/src/main/java/stroom/item/client/SuggestionBox.java:64`

**Evidence:** stroom.cell.info.client holds ActionCell, ActionMenuCell, CellIcon, ColourSwatchCell, CommandLink(Cell), IconCell, InfoColumn, PercentBarCell and RedGreenTextCell; only InfoColumn is about info. A grep for 'stroom.cell.(tickbox|valuespinner).shared' outside stroom-core-client* and stroom-gwt finds only StatisticsCustomMaskList (a client), so the 'shared' packages are client-only. stroom.item.client holds SelectionBox, BaseSelectionBox, SelectionList, SelectionPopup, SuggestionBox and their models. Other grid cells are spread over item/client (SelectionBoxCell, SelectionItemCell), widget/menu (MenuItemCell) and widget/datagrid (EventCell). RedGreenTextCell is named for its colours, not what it shows (a true/false status).

**Fix:** Rename stroom.cell.info.client to stroom.cell.client (or split it, e.g. stroom.cell.action.client and stroom.cell.icon.client). Move cell/*/shared into the matching client packages. Rename stroom.item.client to stroom.widget.selection.client. Rename RedGreenTextCell for what it shows, e.g. BooleanStatusCell (block boolean-status-cell).

**Verifier:** stroom/cell/info/client holds ActionCell, ActionMenuCell, CellIcon, ColourSwatchCell, CommandLink(Cell), IconCell, InfoColumn, PercentBarCell and RedGreenTextCell. The stroom.cell.*.shared packages are imported only by client code (stroom-core-client, stroom-statistics-client and the stroom-gwt modules), none of it server-side. stroom.item.client holds the selection box family. RedGreenTextCell is an AbstractCell<Boolean> named for its colours.

### widgets-1-15: Controllers in the widget module are not in client.component: FileChooserController (form/client), MenuController (menu/client); QuickSettings is in the button package

*low, effort small, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FileChooserController.java:44`, `stroom-core-client-widget/src/main/java/stroom/widget/form/client/FileChooser.java:39`, `stroom-core-client-widget/src/main/java/stroom/widget/menu/client/MenuController.java:43`, `stroom-core-client-widget/src/main/java/stroom/widget/button/client/QuickSettings.java:30`

**Evidence:** UI-GUIDE puts a controller 'beside it in the area's client.component package', and the slice's own NameDialog/NameDialogController do so (widget/popup/client/component). FileChooser + FileChooserController sit in widget/form/client among plain widgets (used by DataUploadController, KeyStoreSecretFormController, DocumentAssetUploadFileDialogController). MenuController extends Controller in widget/menu/client. QuickSettings (titled groups of presets, used by TimeRangePicker and SchedulePicker) is not a button but is in stroom.widget.button.client.

**Fix:** Move FileChooser and FileChooserController to stroom.widget.form.client.component, Menu and MenuController to stroom.widget.menu.client.component, and QuickSettings to its own package (e.g. stroom.widget.quicksettings.client) or to stroom.widget.form.client.

**Verifier:** widget/form/client holds FileChooser.java/.ui.xml and FileChooserController among plain widgets. widget/menu/client holds MenuController ('extends Controller', :43) beside Menu. QuickSettings is in widget/button/client. By contrast, widget/popup/client/component holds NameDialog and NameDialogController.

### widgets-2-25: Inconsistent client.component layout within the widget packages, plus a leftover customdatebox package

*low, effort medium, confirmed.*

**Where:** `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/DateTimeBoxController.java:30`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/TimeBoxController.java`, `stroom-core-client-widget/src/main/java/stroom/widget/datepicker/client/component/DateTimePickerController.java`, `stroom-core-client-widget/src/main/java/stroom/widget/progress/client/component/SourceProgressBar.java`, `stroom-core-client-widget/src/main/java/stroom/widget/tooltip/client/component/InfoTooltip.java`, `stroom-core-client-widget/src/main/java/stroom/widget/customdatebox/client/DurationPicker.java`, `stroom-core-client-widget/src/main/resources/stroom/widget/customdatebox/CustomDateBox.gwt.xml`

**Evidence:** In widget/datepicker, the pickers' controllers (DateTimePickerController, TimePickerController) are in client.component, but the boxes' controllers (DateTimeBoxController, TimeBoxController) sit in client beside their components. widget/progress/client/component holds only SourceProgressBar, a dumb widget with no controller, while its model Progress is in progress/client. widget/tooltip splits the same way (InfoTooltip and its controller in component, FocusTooltip in client). stroom.widget.customdatebox holds DurationPicker, ClientDateUtil and MomentJs; it has no date box (MyDateBox and CustomDateTimeFormat are gone), so the package and module still carry the banned Custom prefix.

**Fix:** Choose one rule for the widget module (UI-GUIDE: pairs in client.component) and apply it: move DateTimeBox, TimeBox and their controllers into datepicker.client.component (or the pickers out), and move SourceProgressBar to progress.client. Rename customdatebox to stroom.widget.duration.client (DurationPicker), and move ClientDateUtil and MomentJs to stroom.widget.datepicker.client or a stroom.widget.datetime.client package, with the GWT module renamed to match.

**Verifier:** datepicker/client holds DateTimeBox, DateTimeBoxController, TimeBox and TimeBoxController, while component/ holds DateTimePicker and TimePicker with their controllers. progress/client/component holds only SourceProgressBar, with no controller. tooltip has FocusTooltip in client and InfoTooltip in component. customdatebox/client holds DurationPicker, ClientDateUtil and MomentJs, and no date box. UI-GUIDE.md:26 puts controllers in client.component.

### workbench-12: Story UiBinder templates live in src/main/resources, not beside their Java

*low, effort small, confirmed.*

**Where:** `stroom-gwt/stroom-gwt-workbench/src/main/resources/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.ui.xml:1`, `stroom-gwt/stroom-gwt-workbench/src/main/resources/stroom/gwt/workbench/client/widgets/layout/SplitPaneTemplate.ui.xml:1`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/buttons/ToolbarTemplate.java:30`, `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/widgets/layout/SplitPaneTemplate.java:24`

**Evidence:** These are the slice's only two .ui.xml files, and both are under src/main/resources. COMPONENT_MODEL_DESIGN ('Templates live beside their Java, in every module', changed 24 October 2026) and UI-GUIDE ('its UiBinder template Foo.ui.xml beside it (in src/main/java, in every module)') say otherwise. ToolbarTemplate's javadoc and template comment still say 'as a view declares them' and 'as a view would'. SplitPaneTemplate.ui.xml:6 declares xmlns:g, which it never uses. Both classes extend GWT's Composite, not stroom.widget.component.client.Component.

**Fix:** Move both .ui.xml files into src/main/java beside ToolbarTemplate.java and SplitPaneTemplate.java. Say 'as a component's template declares them' in place of 'view'. Drop the unused xmlns:g, and extend `Component` so the stories exercise the base that real components use.

**Verifier:** git ls-files shows the workbench's only two .ui.xml files under src/main/resources. UI-GUIDE.md:16 says templates go 'beside it (in src/main/java, in every module)'. The workbench build.gradle:123 already uses src/main/java as GWT source, so moving them works. SplitPaneTemplate.ui.xml declares xmlns:g and never uses it. ToolbarTemplate's javadoc and its template comment say 'as a view declares them' and 'as a view would'. Both classes extend Composite, and Component is an abstract Composite that they could extend.

## What each slice covered

- dashboard-query: Slice dashboard-query, every file listed by git ls-files (328 .java, 85 .ui.xml, .gwt.xml): stroom-core-client stroom/dashboard (component, flexlayout, main, table, vis, input, query, text, embeddedquery, event, gin), stroom/query (client root, component, gin), stroom/search (elastic, solr), stroom/visualisation, stroom-dashboard-gwt/src/main, stroom-app-gwt/src/main and stroom-statistics-client/src/main. Also the stylesheets the slice's templates and Java set classes from (dashboard*.css, flex-layout, tab-layout, expression-tree/-item/-editor, term-editor, query-*.css, result-table, stroom-dashboard, table-filter*, conditional-formatting*, vis*.css, column-*.css, stroom-control, search-result-stores, solr/elastic-index-field-list, time-range-selector, dashboard-input, unknown-component). Method: class fan-in came from a word index of every .java/.ui.xml/.gwt.xml file in all stroom-* modules, workbench and tests included. Public-method fan-in came from the same index, then git grep to confirm each candidate. @UiField and template ui:field use was checked per file. The commented-out code count came from git grep -c. Slots came from grep for setWidget/add/setContent and IsWidget setters, traced to the controllers. Templates were compared with diff. Limits: the public-method check only flags names that appear in no other file, so a dead method whose name is shared with another class (e.g. setQuery, read) is missed. Overload and unused-parameter checks were not exhaustive, and nothing found counted as used only by stories or tests. FlexLayout.java (2000+ lines), TermEditor.java and the large controllers were sampled for composition and inline styles, not read line by line. Classes that stroom-app-gwt/AppModule binds from other slices were not audited.

- security-settings: I covered every file under stroom-core-client/src/main/java/stroom/{security, credentials, receive, preferences, config, activity, alert, ai}: 300+ files, listed and grouped by package.

- How each dimension was checked:

- - **Composition:** I grepped for every slot setter that takes an IsWidget or Widget and for every setWidget, add, insert or setContent call, then read each call site. I also looked for widgets made with new, DOM.create, setInnerHTML, getStyle and setAttribute.

- - **CSS:** I collected every class the slice's templates and Java set and mapped each to its sheet in stroom-app/src/main/resources/ui/css. I also checked every class in the slice's own sheets for a user.

- - **Dead code:** I built a word-count table over stroom-core-client, stroom-core-client-widget, stroom-app-gwt, stroom-dashboard-gwt, stroom-gwt, stroom-statistics-client and stroom-core-shared, then confirmed each candidate with grep -w. For events I checked both who fires and who handles. I also checked GIN modules and ginjector getters, compared @UiField declarations with the template ui:field attributes in both directions, and scanned for commented-out code.

- - **Naming:** I looked for controllers without a Controller suffix and for leftover Presenter, Impl, My and Custom names.

- What I read in full or in part:

- - **Templates read in full:** ApiKeys, Users, SigningKeys, Credentials, Accounts, UserAccess, AppPermissions, UserAndGroups, DocumentUserPermissionsTab, UserPermissionReport, CreateUser, DocumentUserCreatePermissionsEdit, CredentialsManagerDialog, ReplaceSecret, CredentialSettings, CredentialEdit, GlobalPropertyTab, ManageActivity, ConfigPropertyClusterValues, Splash, ActivityEdit, AskStroomAi and ChangePassword.

- - **Templates seen only through grep, not read whole:** AppPermissionsEdit, DocumentUserPermissionsList, KeyBindingPreferences, FieldList, RuleSetSettings, DataRetentionPolicy, ContentTemplateTab, ContentTemplateEdit, RuleEditor, DataRetentionRuleEditor, GlobalPropertyEdit, UserPreferencesDialog, AskStroomAiConfigDialog, Login, AuthenticationError, CurrentPassword, EmailResetPassword, ResetPassword and CommonAlert.

- - **Not read line by line:** the large controllers (AskStroomAiController, ContentTemplateTabController, DataRetentionPolicyController, UserAndGroupsController). I covered them with targeted greps only.

- Not covered:

- - The dead-method scan only flags method names that appear three times or fewer across the client modules, so a dead method with a common name such as setList or show may have been missed.

- - I did not open the templates of dumb list components (e.g. ApiKeysList, UserList) to look for unused CSS within them, beyond the class census.

- - ActivityEdit renders the activity editor's HTML, which comes from configuration and contains raw <input> and <select> controls. I treated that as data-supplied content and did not report it.

- - The section--disabled class and the shared disabled class are documented shared classes and are not reported.

- - I made no edits and ran no build. The only files I wrote are scratch files under /tmp/claude-1000/-mnt-shared-stroom--claude-worktrees-remove-gwtp/47808bca-54cf-48e5-84f5-ef1af7d1ce32/scratchpad (wc.txt, meths.txt, cand.txt, classes.txt and deadmeth.sh).

- floormap-pathways-stores: Slice: every file under stroom-core-client/src/main/java/stroom/{floormap,pathways,planb,sqlstore,gitrepo,contentstore,content,script,dictionary,feed,cache,monitoring}. That is 318 files: 66 templates, and the rest component, controller, plugin, gin, event and helper classes.

- Stylesheets checked: stroom-floormap.css, floormap-list.css, floormap-icon-picker.css, floor-map-{cluster-dialog,event-store-settings,object-edit,settings}.css, pathways.css, pathway-tree.css, traces-list.css, trace-histogram-chart.css, contentstore-details.css, content-store-tab.css, gitrepo-settings-form.css, git-repo-commit-dialog.css, imports-tab.css, shared-file-store-settings.css and settings-group.css. I also checked the shared classes the templates set (dashboard-panel, wide-control, link-tab-panel, sr-only).

- How it was checked:

- - Pairing: every component was checked for a controller of the same name, and every template for one.

- - Composition: every Java-side slot fill (setWidget/add/setContent) and every `new Widget` in a component or controller.

- - Inline styles: every getStyle/setAttribute("style") and `style=` string.

- - Class names: every class name set by templates and Java was mapped to its sheet.

- - CSS fan-in: every class in the slice's sheets was searched for with `git grep -F` across Java, ui.xml and JS files.

- - Code fan-in: a script ran `git grep -w` for every class and every non-private method in the slice, across the whole repo. It skipped .md/.csv files and build/ directories, and covered tests and workbench stories. @UiField fields never used in their own class were also listed.

- Not covered:

- - The fan-in script only finds names with no use at all. A method whose name matches an unrelated method elsewhere would not be flagged, so dead overloads with common names (beyond those listed) may remain.

- - Private fields and unused parameters were checked by hand only in the classes I read: plugins, feed, dictionary, cache, contentstore and the floor map's slot components. The large files were not checked line by line for unused parameters: FloorMapCanvas, FloorMapCanvasController, FloorMapEditorController, FloorMapMapController, FloorMapEditorModel and TraceOverviewChart.

- - The SVG drawing in FloorMapCanvas, PathwayTree and TraceHistogramChart was treated as data-driven DOM, which the rules allow. Its presentation attributes were not audited.

- data-pipeline-processing: Slice data-pipeline-processing. I listed all 346 files with git ls-files across stroom-core-client/src/main/java/stroom/{data,pipeline,processor,job,node,task,index,dispatch} and checked them as follows:

- - Every Component and Controller declaration: pairing and the kinds of class each package holds.

- - Every slot filled from Java (setWidget, setContent, insert and add).

- - Every widget made with `new` in Java.

- - Every class the slice sets in a template or in Java, mapped to its stylesheet in stroom-app/src/main/resources/ui/css.

- - Inline styles (none found).

- - Bare state classes.

- - Commented-out code.

- - GIN modules and plugin bindings.

- - Event fire/handler sites and fire overloads.

- - Fan-in for every class name (one git grep -w over all *.java and *.xml in the repo, stories and tests included).

- - Fan-in for every public, non-override method (a git grep per name). This only catches names that appear nowhere else, so common names such as refresh or read were not checked one by one.

- - Unused rules in the stylesheets of the slice's blocks (pipeline-path-bar, data-viewer, stepping-screen, source-viewer, draggable-tree, pipeline-element, pipeline-tree, user-task, stepping-button, stroom-control, credentials-details-dialog and others). After checking the class names Java builds from constants, every rule is used.

- Not done one by one:

- - I did not read every grid controller's column code in full (MetaListController, ProcessorListController, TaskManagerListController and similar); for those I relied on the greps.

- - I did not hand-check the javadoc and comments of every template, only the structure.

- - Public methods that are called only from their own file (for example MetaBrowserController.isSomeSelected and PropertyListController.getInheritedPropertySource) could be private, but are left out because they are not dead.

- Context outside the slice that I used: the monitoring/client plugins (JobListPlugin, NodeMonitoringPlugin, MonitoringModule), query/client (ExpressionTree, ExpressionItemBox, ExpressionTreePanel), util/client/DataGridUtil, AppGinjectorUser, and the widget-module DisabledState and SourceProgressBar.

- explorer-documents-shell: Slice explorer-documents-shell, all of it: 468 tracked files under stroom-core-client/src/main/java/stroom/ in explorer, annotation, document (with document/asset), entity, importexport, core, main and util, plus the packages no other slice names: about, analytics, aws/s3, datagen, documentation, folder, help, http, hyperlink, iframe, kafka, menubar, openai, quickfilter, schedule, ui/config, view, welcome, widget/histogram and xmlschema.

- Method:

- - Read UI-GUIDE.md and COMPONENT_MODEL_DESIGN.md.

- - Read every .ui.xml in the slice.

- - Read the Java of every component whose layout matters (Main, Core, FullScreen, Navigation, FindPanel, FindInContent, LinkTabPanel, TabbedController, SettingBlock, AssignYourselfLink, IFrame, IFrameContent, the Markdown pair, DocumentAssetsTab, the analytics slot components, ExecutionSchedulesController, and others).

- - Fan-in: built an identifier index over every tracked .java, .xml, .css, .js and .html file in the repo, outside build/ and node_modules, workbench stories and tests included. Used it to check fan-in for every class, every event (fired versus handled), and every public or protected method (call sites, plus template attribute use for setters).

- - Checked for @UiField fields that nothing uses.

- - Mapped every class name the slice's templates and Java set to the sheets in stroom-app/src/main/resources/ui/css that style it.

- - Searched for Java-side adding (setWidget, add, insert, setContent), widgets made with new, inline styles, markup built in Java, leftover GWTP-era names and commented-out code.

- Limits:

- - Unused methods: the check counts any call of the same name anywhere as a use, so unused methods whose names are common (setData, refresh and so on) may be missed.

- - CSS: I checked the sheets this slice's classes map to (main-screen, full-screen, app-page, navigation, find-panel, find-buttons, iframe, iframe-content, stroom-control, quick-filter, lozenge, explorer-tree, setting-block, annotation-setting, document-assets-tab, about, welcome-tab, type-filter, form-group, delete-confirm, info-*, dashboard-body). I did not go rule by rule through sheets owned by other slices.

- - I did not read the very large controllers line by line (DocumentPluginEventManager is 1995 lines, AnnotationEditController 1632, ExplorerTreeController, the analytics processing controllers), apart from the parts the greps pointed to.

- - No builds were run, as the task required.

- widgets-1: Read UI-GUIDE.md and the relevant parts of COMPONENT_MODEL_DESIGN.md (widget rules, the components/controllers/models section, toolbars, data grid and CSS sections). Listed every file in the slice: about 280 Java files and 34 templates under stroom-core-client-widget/src/main/java/stroom/{widget/datagrid, widget/popup, widget/tab, widget/menu, widget/button, widget/form, widget/quickfilter, widget/contextmenu, cell, item}. Read every .ui.xml in the slice in full. Read the Java of PagerPanel, HeadedPagerPanel, QuickSettings, LayerContainerImpl, CurveTabLayout(+Panel), TabBar, AbstractTabBar (constructor), GlobalResizeObserver, ButtonView, ToggleButtonView, ToolbarToggleButton, SimplePopup, SimplePopupLayout, Popup, DialogButtons, DialogEvent, NameDialog(+Controller), EventBinder, DecoratedItem, FindResultLists, DataGridSortIcon, SortIcon, HeadingBuilder, InfoColumn, GroupHeading, contextmenu TextField/TextEditTarget, DescriptionHTML/HelpHTML, FileUpload*, and parts of DataGrid, SelectionList, SelectionPopup, PopupUtil, FormGroup and MenuItemCell.

- Fan-in: for every class in the slice I ran grep -rlw over stroom-core-client, stroom-core-client-widget, stroom-dashboard-gwt, stroom-app-gwt, stroom-gwt, stroom-statistics and stroom-core-shared (Java, XML and mjs, build/ excluded), once for all source and once for main source only. Only the 40 classes with the lowest fan-in were reported back, and I followed those up by hand. I also checked the public methods of DataGrid and MenuBuilder for callers, including template attributes. git grep was refused by the worktree guard inside loops, so I used grep -r instead.

- CSS: I mapped the classes the slice's templates and Java set to their sheets and read data-grid.css (header and context parts), stroom-control.css, curve-tab-layout.css, quick-filter.css, paging-bar.css, pager-toolbar.css, list-container.css, change-password.css (.feedback) and column-header.css, and checked which blocks every slice sheet names. I also grepped the slice for inline styles (style=, getStyle, setWidth/setHeight), raw controls and commented-out code.

- Not covered in depth:

- - Most of DataGrid.java's internals (GridRows, GridHtml, DataGridKeyboard, ColumnInteractions) beyond class-name and public-API fan-in.

- - The public methods of the classes other than DataGrid and MenuBuilder (fan-in was checked at class level only).

- - The ValueSpinnerCell, TickBoxCell and SelectionBoxCell markup, beyond confirming that their native inputs are allowed (they are Stroom's own cells).

- - Every rule of every slice sheet against the class census. I relied on TestStylesheets for the "styled or set" check.

- widgets-2: Slice widgets-2: all 204 Java files and 22 templates under stroom-core-client-widget/src/main/java/stroom outside widgets-1 (data/client/event, data/pager, data/table, editor (root, ace, component, event, model), event, hyperlink, svg, task, widget/colour, component, customdatebox, datepicker (+component), dropdowntree, help, htree (+treelayout), layout, linecolinput, progress (+component), spinner, text, tickbox, tooltip (+component, event), util, valuespinner, xsdbrowser (+component)) and stroom-core-client-widget/src/main/resources (the 26 .gwt.xml modules in the slice, the three GWT CSS overrides, .gitignore). For CSS I read or checked these sheets, which my slice's classes map to: code-editor, code-editor-indicator, scrollbar-metrics, xsd-browser, calendar, date-time-box, date-time-view, value-spinner, duration-picker, icon-box, tick-box, colour-box, help-popup, info-tooltip, focus-tooltip, source-progress-bar, busy-spinner, drop-down, paging-bar, pager-toolbar, refresh-button, layered-canvas, split-pane, scroll-pane, info-page, stroom-control, the class rules in base.css, and form-group.css (only for dashboard-panel). Method: I built a whole-repo word index of java/xml/css/js/html sources, excluding build/, node_modules, Ace (edu/) and ui/script. With it I counted fan-in for every class, for every public or protected method (I checked template attribute setters such as maxValue, noneText and soft against the .ui.xml files), for each @UiField against its template, and for each slice sheet's class against the code. Every candidate was then confirmed with grep. Not covered in depth: the internals of KeyBinding (830 lines), IntlDateTimeFormat and the Abego treelayout port; I only checked their fan-in. I did not trace who outside the slice declares Pager or SourceProgressBar in Java rather than in a template; that is the other slices' composition. Two files were already being edited in the working tree (Templates.input removed, button.css); I skipped what those edits remove.

- workbench: Slice: stroom-gwt/stroom-gwt-workbench/src/main (about 330 Java files: the stories, the screen harness, the REST fixtures and the GIN mirror modules, plus 2 .ui.xml in resources, the gwt.xml files and the webapp assets) and stroom-gwt/stroom-gwt-workbench-framework/src/main (about 100 Java files, workbench-manager.css, workbench-preview.css, the html and gwt.xml). I read UI-GUIDE.md, the relevant parts of COMPONENT_MODEL_DESIGN.md (design principles, widget rules, 'Components, controllers and models') and the rules sections of WRITING-STORIES.md.

- Checks run:

- - Every GIN module and ginjector, with calls to each ginjector getter.

- - Fan-in of every public or static method in the slice: word counts over all of stroom-gwt (build/ excluded), confirmed with `git grep -w`.

- - Unused private members, in a per-file scan.

- - Every story title against Stroom class names and against the package and class naming rules.

- - Registration comments against titles.

- - GWT widget imports and raw `<input>`/`<button>` in stories.

- - Every addStyleName/setStyleName mapped to stroom-app/src/main/resources/ui/css sheets.

- - Inline-style counts.

- - Javadoc `[Class]` links and backticked names against existing classes.

- - CSS selectors used by plays that nothing in Stroom sets.

- Of those selectors, the vis-* and leaflet-* ones come from the external visualisation pack and the cascade-*/…-trigger ones are the stories' own hook classes, so none are reported.

- Not reported, by design:

- - The workbench framework (the Storybook look-alike) deliberately depends on nothing of Stroom's (its gwt.xml says so). It builds its UI from SafeHtml and DOM, with its own BEM 'wbm-' and 'wbp-' classes in its own sheets. So the Stroom-widget, UiBinder-composition and Stroom-stylesheet rules do not apply to it, and only naming and dead code are reported for it. Every wbm-* class in workbench-manager.css is set, some dynamically (wbm-impact--*, wbm-status--*, wbm-code-*, wbm-tag-menu__icon--*).

- - Stories that assemble their scaffolding in Java (StoryPanels rows and columns, harness frames) are allowed by WRITING-STORIES, so I reported composition only where a story copies a Stroom template's markup or cell (workbench-08 and workbench-11).

- - Stories still call Stroom Button.addClickHandler in 29 places, where UI-GUIDE prefers setOnClick. This is outside the five dimensions, so it is not reported.

- Not covered: I did not read every play's body line by line for stale assertions. I also did not check whether the workbench's test sources (src/test) have dead helpers; they were counted only as fan-in.
