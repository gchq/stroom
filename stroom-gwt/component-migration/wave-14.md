# Wave 14: Document editors

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

> **Since wave 10 (27 October 2026):** the lists are components with a controller each (`stroom.<area>.client.component`). A list's template declares its `PagerPanel` (or `HeadedPagerPanel`) with a `toolbarName`, holding its `DataGrid` (`tableName`, `multiLine`, `pageSize`) and its toolbar's `ToolbarButton`s (`svg`, `title`, `enabled="false"`); a quick-filtered list's root is a `QuickFilterPage` (`accessibleName`). The component adds its columns and gives its pager as `getTaskMonitor()`, with `getSelectionModel()`, `getSortList()` and `getToolbar()`; its buttons, info and row menus go out through callbacks (`setOnAdd`, `setOnShowInfo`, `setOnShowActionMenu`), and its controller passes in the ref cells configured (`refCells.configure(new DocRefCell.Builder<>())`, `FeedRefCell`, `UserRefCell`). Only a list inside a document watches `Contexts.READ_ONLY`, which is true where nothing provides it. A controller shown as a tab is a `ControllerLayer`, which passes the focus on. Among the new names: `MetaList` with `MetaListController`, `SteppingMetaListController` and `MetaRelationListController`; `JobNodeColumns` and `JobNodeActionsController` (were `JobNodeListHelper`); one `NotificationList` for analytic and report notifications; `ScriptDependencyList` (`ScriptListPresenter` merged in); `IncludeExcludeFilterEditor` and `IncludeExcludeFilterDictionary`; `DependencyList` (was `DependenciesPresenter`); `TabSessionChooser`; `CredentialsList`; `FindDocResultList`; `EffectiveWordsList`; `TypeFilter`. The plans below keep the old names. See `wave-10.md`.

> **Since wave 11 (27 October 2026):** the larger dialogs are components with a controller (`stroom.<area>.client.component`). One made for each use shows itself with `ControllerDialog.showOnce(opener, this)` from a method taking its result's callback (e.g. `edit(opener, caption, rule, onOk)`); one its owner keeps is owned and shown again with `ControllerDialog.show(this)`. A controller that fetches before it shows disposes itself when the fetch fails (`new DefaultErrorHandler(this, this::dispose)`), and the clients take an error handler for this. A document's Permissions tab is `DocumentUserPermissionsTab` with `DocumentUserPermissionsTabController` (`setDocRef`); `DocumentUserPermissionsTabProvider` shows it through a `ControllerTabProvider`, and `DocumentUserPermissionsPresenter` is a thin content-tab shell over it until the content-tab base. Find, Recent Items, Chat History and Choose Annotation are controllers on one `FindPanel`; the user picker is `UserRefPopupController` on a `QuickFilterDialog`; the users screens' delete and edit group actions are `UserAndGroupActions`. Among the new names: `UserPreferencesDialog`, `AskStroomAiConfigDialog`, `SteppingFilterEditor`, `PathwayEdit`, `FloorMapObjectEdit`, `DataUpload`, `QueryFavourites`, `QueryHistory`, `AdvancedQuickFilter`, `DataRetentionRuleEditor`, `ContentTemplateEdit`, `ConditionalFormattingRuleEditor`, `SelectionHandlerEditor`, `EditApiKeyMode`. `QuickFilterModule`, `ActivityModule`, `HttpModule` and `FsVolumeModule` are gone. `ColourBox` takes `ariaLabel`, and `DateTimeBox` `role` and `ariaLabel`, in a template. The plans below keep the old names. See `wave-11.md`.

> **Since wave 12 (27 October 2026):** the document tabs and composite panes are components with a controller each (`stroom.<area>.client.component`). A document shows one as a tab through `ControllerTabProvider`: `new ControllerTabProvider<>(eventBus, provider::get, Foo::read, Foo::write)` for one the tab makes, or `ControllerTabProvider.ofOwned(eventBus, controller, reader[, writer])` for one the document makes and owns (e.g. to give it callbacks before the tab is opened); the tab passes on the controller's toolbars when it is a `HasToolbar`. Hand-rolled tab panes (`SteppingPresenter`, `UserTabPresenter`, `DataRetentionPresenter`) show each controller in a `ControllerLayer`. `ProcessorTaskPresenter` and `UserAndGroupsPresenter` stay as thin `ContentTabPresenter` shells around their controllers until wave 15. Among the new names: `SteppingElement`, `UserInfoTab`, `FloorMapSettings`, `DocumentAssetsTab` (shown through `DocumentAssetsTabProvider`), `ProcessorFilterTasks`, `DuplicateManagement`, `ImportsTab` with `ImportsTabController`, `AnnotationLinksController` (on the shared `FindPanel`), `ScriptSettings`, `UserAndGroups`, `ElasticClusterSettings`, `FeedDependenciesEditor` (`edit(opener, dependencies, onOk)`), `GitRepoSettings`, `AnnotationEdit` (`setOnTitleChange`, `setOnDelete`), `PipelineStructure`, `OpenAIModelSettings`, `PlanBSettings` (also a Traces store's Settings tab, its state type locked), `ScheduledProcessing` (also a data generator's Execution tab; `setIsDocumentDirty`), `PathwaysSplit`, `TracesListTab`, `SolrIndexSettings`, `ProcessorEdit` (`show(opener, ...)`), `DataRetentionImpact`, `DataRetentionPolicy`, `Processors` with `ProcessorsController` (a pipeline's, rule's or folder's Processors tab), `UserPermissionReport`, `StreamingProcessing` and one `AnalyticProcessing` for rules and reports (`setProcessingTypes(...)`; `AbstractProcessingPresenter` and its two subclasses are gone); the notification list is shown directly (`NotificationListController`). `ClassificationWrapper` takes its content in a template (`<d:content>`), and a `QuickFilterPage` takes a `<form:HelpHTML>` child as its filter's help. The plans below keep the old names. See `wave-12.md`.

## Audited (28 October 2026)

Five reviewers read the wave's two unit commits against this plan, "Done means" and AGENTS.md (the
document framework: `TabbedController`, `DocumentEditor`, `DocumentEditorHost` and `LinkTabPanel`;
the code, settings and dashboard editors; the pipeline, feed, folder and index editors; the
annotation tabs, receipt rules and embedded query; the statistics store and what the deletion
left). Fixed, in 1 commit:

- **Default tabs.** Editors that selected a tab in their constructor now set it as their default
  tab (`setDefaultTab`), made once the editor is attached unless another tab is selected first; the
  pipeline's `showProcessorFilter` asks `hasTab` before selecting its Processors tab.
- **The dashboard's label.** `DashboardEditor.onRead` reads the dashboard's body, so its tab is
  named for the dashboard's title again (it was blank until a save).
- **Receipt rules saved by any document's save.** The rules' shell saved them on every
  `SaveDocumentEvent`, so saving another dirty document saved the rules too; it now saves only when
  the event is its own. Story `App/Main/DataReceiptRulesScreen SavingAnotherDocument` (fails
  before). Master does this too: changelog entry.
- **An annotation's tab label.** Renaming an annotation left its tab with the old title until it
  was opened again; the label follows the title (`AnnotationEditController.getAnnotationTitle`).
  The Editor story asserts the label. Master too: changelog entry.
- **Smaller fixes.** Traces opened with no document are labelled "Traces"; `CodeTabController`
  shows "" for a null text; the embedded query clears its result with `setResult(null)`; the
  read-only chip is a `SafeHtmlTemplates` template; the link tab panel's stories make their tabs'
  content lazily, as editors do; the tab story's Save wiring went; `PresenterCoverage` counts
  `DocumentEditor` subclasses as screens; the floor map event store's unused `TAB_TYPE` and the
  traces plugin's commented-out code went.
- **Docs.** ISSUES.md entries the wave had wrongly edited were put back; WRITING-STORIES.md,
  COMPONENTS.md, COMPONENT_MODEL_DESIGN.md (phase 9 done), DATA_GRID_DESIGN.md and the ginjectors'
  javadoc name the new classes.

Left, on purpose (recorded in ISSUES.md, "Left from wave 14's audit"): Save As of a visualisation
or floor map whose Assets tab was never shown; the text converter's highlighting on a type change;
no stories for the SQL temporal store and floor map event store editors; no unit tests for the
document host's rules; traces with no document still showing their document tabs; a query opened
from an embedded query closing with it; a statistic store's first view of its roll-ups making it
dirty; `HasDocumentRead`, `ChangeUiHandlers` and `HasCommonTabs` now barely used; the
`linkTabPanelViewImpl` class names; the copied `taskMonitor()` helper; the story helpers
`DocEditors.open` and `DocumentEditors.open`.

## Done (28 October 2026)

All 36 of the plan's units, and the statistics store's editor beyond it, in 3 commits: the editors
(with the annotation tabs, receipt rules, embedded query and statistics store), the old framework's
deletion (`DocPresenter` merged into `DocumentEditorHost`, `DocTabPresenter`, `LinkTabPanelPresenter`
and the tab providers gone, the link tab panel a component), and the audit's. Each ran its stories in
a copy of the worktree with the leak check and the screenshot diff. The app and the standalone
dashboard GWT compile, checkstyle is clean and the client and workbench modules' 2,179 JUnit tests
pass (`TestDocPresenter`'s 9 went with `DocPresenter`). Two bugs master has too were fixed, with
changelog entries: saving any document saved the receipt rules, and an annotation's tab kept its old
title. The full workbench run after the audit (1,723 stories, 4 workers, with the leak check) passed all 1,723; of their screenshots, 1,616 match, the 12 new are stories with no baseline yet (the wave's `DataReceiptRulesScreen.SavingAnotherDocument` and wave 13's 11), and of the 95 that differ, 29 are the annotation editor's and 2 the comment decoration's (the empty toolbar row above an annotation went, so its content is higher), 1 the pipeline editor's `ComplexStructure` (its default tab), 59 are wave 13's known ones (the query and floor map editors) and those that differ from run to run (the force visualisations), and 3 are small and not yet explained (`TopBar.Keyboard`, a tooltip; `AppShell.ExplorerItemKeys`, a renamed item's text; `DataGrid.ValueSpinnerColumn`), recorded in ISSUES.md. What was decided on the way, where it differs from the plans below:

- **The framework.** `TabbedController` has the tabs and their toolbars (`addTab`, `addOwnedTab` for
  content already made and owned, `replaceTab` for the pipeline's Structure and Stepping,
  `setDefaultTab`, `getSelectedTab`, `afterSelectTab`, `handleKeyAction`, `onContentTabVisible`,
  `onClose`, `getTabLabel`, `beforeRead`/`afterWrite`, `showReadOnlyStatus`); `DocumentEditor`
  adds Save and Save As, `addDocumentationTab` and `addPermissionsTab`, `hasAssociatedDirty`, the
  post-save callbacks and `beforeSave`; `DocumentEditorHost` (`DocPresenter` merged in) hosts one,
  and `MultiInstanceEditorHost` dashboards. `CodeTabController` is a code editor tab.
- **Key actions.** The query, report, analytic rule and dashboard editors' OK and Close keys run
  only with the Query (or Dashboard) tab shown, as the old code meant (its tab type check compared
  nulls, so they ran from any tab).
- **Selected at once.** A tab selected in a constructor was selected deferred; a default tab is now
  made once the editor is attached.
- **The receipt rules.** `RuleSetPresenter` stayed a thin content-tab shell around `RuleSetEditor`
  until wave 15.
- **The statistics store.** `StatisticsDataSourceEditor` (stroom-statistics, in no wave) was
  converted so `DocTabPresenter` had no subclasses left; its three tabs stay presenters, shown as
  tabs directly.
- **Removals.** The old framework was deleted in this wave rather than waiting: the in-flight
  worktrees are the user's experiments elsewhere (agreed 27 October 2026).
- **Tool slips.** The converter removed same-named workbench modules by simple name; it is
  package-aware now.

Move every DocTabPresenter shell onto DocumentEditor<D> plus DocumentEditorHost, with TabbedComponent tabs (Markdown and permissions tabs as reusable tabs), then delete DocPresenter, DocTabPresenter and LinkTabPanelPresenter. RuleSetPresenter's hand-rolled tabs also move onto DocumentEditor.

## CSS and template notes for the wave

- None of the 36 units in wave 14 has an inline style. I grepped every file in the units, and both templates, for getStyle(), setProperty, setAttribute("style"), style="..." and Style.Unit. So no new classes or custom properties are needed for rule 3.
- The 31 document editors render nothing of their own: their widget is the shared LinkTabPanel (unit 765). So their template, classes and ARIA all live in LinkTabPanel.ui.xml. Their only fixed text is tab labels (TabData) and document-type toolbar buttons, which stay in Java by the DictionaryEditor precedent and the Toolbars/UiBinder rule ('buttons that depend on the document type are made in Java').
- Reuse docTab-readOnlyStatus, docTab-readOnlyNote and docTab-readOnlyIcon (components.css:2203-2245) in LinkTabPanel.ui.xml and its chip constant, plus sr-only (spinner.css:126). Update the comment at components.css:2203, which names DocTabPresenter, to name LinkTabPanel.
- The LinkTabPanel template keeps the layout classes max, dock-container-vertical, dock-min and dock-max (stroom.css). The classes linkTabPanelViewImpl, linkTabPanelViewImpl-middle and toolbarContainer (components.css:727-741) are named after a ViewImpl that is being deleted. Optional rename to linkTabPanel, linkTabPanel-tabBar and linkTabPanel-toolbars. It would also need UserPreferencesViewImpl.ui.xml:7-8, FloorMapDock.ui.xml:22-23 (`stroom.floormap.client.component`), AskStroomAiConfigViewImpl.ui.xml:7-8 and the selectors in DashboardLayoutStories.java:94 and AnnotationEditorStories.java:353 changed in the same commit.
- TableViewImpl (dashboard.css:140 '.TableViewImpl .pager') is shared by EmbeddedQueryViewImpl.ui.xml:4, TableViewImpl.ui.xml:4 and QueryResultTableViewImpl.ui.xml:4. Rename it once, with wave 13's Table (e.g. dashboardTable), and have EmbeddedQuery.ui.xml use the same name. stroom-dashboard-table (stroom-dashboard.css:62-80) stays.

## `DocPresenter`

framework, presenter; complexity 3, effort L; used by 81.

- **Component:** `none (framework base, no widget of its own)`
- **Controller:** `merged into stroom.entity.client.component.DocumentEditorHost`
- **Template:** `none` → `none (no view)`
- **Goes:** stroom-core-client/src/main/java/stroom/entity/client/presenter/DocPresenter.java, merged into DocumentEditorHost (DocumentEditorHost.java:54-56 extends DocPresenter<ControllerView, D>), once its other 51 subclasses, the tabs from earlier waves and this wave's DocTabPresenter, are gone; `HasDocumentWrite.java and HasReadAndWrite.java when their last users go (ScriptDependencyListPresenter; DynamicSettingsPane)`; `ContentCallback.java (users: DocTabPresenter, LinkTabPanelPresenter, UserTabPresenter, DataRetentionPresenter, LinkTabPanelStories)`; `HasClose.java (users: TabContentProvider, AbstractTabProvider, FloorMapQueryPresenter)`; `stroom-core-client/src/test/java/stroom/entity/client/presenter/TestDocPresenter.java moves to a DocumentEditorHost test`
- **Names:** The suggested `Doc` + `DocController` clashes with the jOOQ table stroom.docstore.impl.db.jooq.tables.Doc, and it is the wrong shape. Nothing is renamed: DocumentEditor and DocumentEditorHost already replace it (Phase 9 exit criteria), so the base is merged into its last subclass, DocumentEditorHost. HasCommonTabs (users: DocumentPlugin, FolderPlugin, FolderRootPlugin, DocumentEditorHost) and HasToolbar (used by TabbedController and the tabs) stay as interfaces and move to stroom.entity.client.component, beside TabbedController. HasDocumentRead stays while ProcessorPresenter, MetaPresenter, ProcessorTaskPresenter and others implement it.

**Stays in Java:** The DOCUMENT and DIRTY context provision (DocPresenter.java:48-53), dirty listeners (lines 145-152), setReadOnly (lines 121-123) and post-save callbacks (lines 160-172) move into DocumentEditorHost; hasAssociatedDirty (lines 65-70) and getPostSaveCallback/getPostSaveAsCallback have no equivalent on DocumentEditor yet. FloorMapDocEditor, VisualisationEditor and PipelineEditor need them, so DocumentEditor must gain those hooks before this base goes.

## `LinkTabPanelPresenter`

framework, tidy; complexity 2, effort S; used by 0.

- **Component:** `stroom.entity.client.component.LinkTabPanel`
- **Controller:** `none (dumb; TabbedController and its subclasses drive it)`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/LinkTabPanelViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/LinkTabPanel.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/LinkTabPanelView.java (view interface)`; stroom-core-client/src/main/java/stroom/entity/client/view/LinkTabPanelViewImpl.java (ViewImpl; becomes LinkTabPanel with a no-arg constructor and a GWT.create Binder, not an @Inject Binder); stroom-core-client/src/main/java/stroom/entity/client/presenter/LinkTabPanelPresenter.java (dead: only the workbench LinkTabPanelStories.java:142,181 StoryLinkTabPanelPresenter extends it. The story is rewritten on a TabbedController); `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:173 bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class)`; stroom-dashboard-gwt/src/main/java/stroom/dashboard/client/gin/DashboardAppModule.java:193 bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/ScreenViewsModule.java:95 bindSharedView(LinkTabPanelView.class, LinkTabPanelViewImpl.class)
- **Names:** The suggested name keeps LinkTabPanelPresenter, but the presenter is dead and the view is the real component. The view and ViewImpl collapse into `LinkTabPanel` (free) in stroom.entity.client.component, beside TabbedController, DocumentEditor and DocumentEditorHost, its only drivers. Callers that move in the same commit: TabbedController.java, DocumentEditor.java, DictionaryEditor.java, FolderTabs.java:59, UserTabPresenter.java:47,91 (wave 15), StatisticsDataSourcePresenter.java (stroom-statistics, in no wave), DocumentEditors.java and the workbench stories. FloorMapDock (wave 7) copies its layout but is its own pair, so it is unaffected.

| Into the template | What | Detail |
| --- | --- | --- |
| LinkTabPanelViewImpl.ui.xml:6-8 + DocumentEditor.java:57,83-85 | structure | The tab row (an HTMLPanel or FlowPanel with styleName="dock-min linkTabPanelViewImpl") holds <tab:LinkTabBar ui:field="tabBar"/> and then <div ui:field="readOnlyStatus" class="docTab-readOnlyStatus" role="status"/>. The 'while > 1 remove(1)' slot in setTabBarStatus (LinkTabPanelViewImpl.java:70-78) goes |
| DocumentEditor.java:83 | css-class | docTab-readOnlyStatus (components.css:2205) is set in the template |
| DocumentEditor.java:84 | aria | role="status" is set in the template |
| TabbedController.java:56 | component-declaration | <b:Toolbar ui:field="toolbar" name="Document actions"/> goes first inside toolbarContainer, then a second <g:FlowPanel ui:field="tabToolbars"/> for the showing tab's other toolbars (in place of clearToolbar/addToolbar for every toolbar) |
| LinkTabPanelViewImpl.java:46 | structure | tabBar.setTabPanel(layerContainer.getElement()) wires ARIA tablist to tabpanel, so it stays in the constructor (it needs both elements) |

**Stays in Java:** The tab bar's tabs (LinkTabBar.addTab from TabData), the selected tab and hidden tabs are data from TabbedController; The read-only chip's content is put in and taken out (see unit 764) so that the live region announces it; Declares checked: LinkTabBar (wave 3) and LayerContainerImpl (wave 3) are already declared in the template (LinkTabPanelViewImpl.ui.xml:7,10). Toolbar (wave 1) is added as above; Template keeps its existing classes max, dock-container-vertical, dock-min and dock-max (stroom.css), and linkTabPanelViewImpl, linkTabPanelViewImpl-middle and toolbarContainer (components.css:727-741). See cssNotes on renaming.

## `DocTabPresenter`

framework, presenter; complexity 4, effort XL; used by 31.

- **Component:** `none (replaced by stroom.entity.client.component.LinkTabPanel)`
- **Controller:** `none (replaced by the existing stroom.entity.client.component.DocumentEditor / TabbedController)`
- **Template:** `none` → `none (no template; it used LinkTabPanelView)`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/DocTabPresenter.java`; `stroom-core-client/src/main/java/stroom/entity/client/presenter/DocTabProvider.java`; `stroom-core-client/src/main/java/stroom/entity/client/presenter/AbstractTabProvider.java`; `stroom-core-client/src/main/java/stroom/entity/client/presenter/TabContentProvider.java`; `stroom-core-client/src/main/java/stroom/entity/client/presenter/MarkdownTabProvider.java`; stroom-core-client/src/main/java/stroom/security/client/presenter/DocumentUserPermissionsTabProvider.java (extends AbstractTabProvider; editors use PresenterAdapter or the permissions controller, as DictionaryEditor does); The TabProvider uses in TracesSettingsPresenter.java, FloorMapQueryPresenter.java and stroom/document/asset/client/presenter/package-info.java
- **Names:** The suggested `DocTab` + `DocTabController` is not needed: DocumentEditor (Save, Save As, read-only status, dirty state) and TabbedController (tabs, toolbar merge, common tabs) already replace it, so it is deleted rather than renamed. A blocker that isn't in any wave: stroom-statistics/stroom-statistics-client/.../StatisticsDataSourcePresenter.java extends DocTabPresenter and uses LinkTabPanelView and the tab providers. It needs converting (as StatisticsDataSourceEditor, which is free) in this wave, or DocTabPresenter can't go. TestPresenterCoverage also names DocTabPresenter.

| Into the template | What | Detail |
| --- | --- | --- |
| DocTabPresenter.java:76,88-90 (same as DocumentEditor.java:57,83-85) | structure | The read-only status element moves into LinkTabPanel.ui.xml, after the tab bar in the tab row: <div ui:field="readOnlyStatus" class="docTab-readOnlyStatus" role="status"/>. It replaces new HTML(), setStyleName and setAttribute("role") and the view's setTabBarStatus(Widget) |
| DocTabPresenter.java:107 (same as TabbedController.java:56) | component-declaration | <b:Toolbar ui:field="toolbar" name="Document actions"/> is declared first in the LinkTabPanel toolbar row, and getToolbar() returns it. Its visibility is set when it has no buttons, in place of add and remove |

**Stays in Java:** DocTabPresenter.java:244-256 (= DocumentEditor.java:156-167): the read-only chip ('Read only', lock icon, sr-only reason) is put into and taken out of the status element, not shown and hidden, so that its role=status live region announces it. LinkTabPanel gets setReadOnlyStatus(boolean) with the chip as a SafeHtmlTemplates constant there. The reason title (READ_ONLY_REASON) is set only while it is shown; DocTabPresenter.java:108-112, 273-275: Save and Save As (and the 'Save (read only)' title) are DocumentEditor's buttons, made in Java because FolderTabs and the other TabbedControllers share the template without them; DocTabPresenter.java:296-302 and 315-322: the tab label '* ' + name and the tooltip type + ' - ' + name are built from data (DocumentEditorHost); Declares checked: MarkdownEdit is a tab body. Toolbar and ToolbarButton become the declared toolbar above.

## `VisualisationPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.visualisation.client.component.VisualisationEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/visualisation/client/presenter/VisualisationPresenter.java`; `stroom-core-client/src/main/java/stroom/visualisation/client/gin/VisualisationModule.java:30 bind(VisualisationPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/VisualisationScreenModule.java:48 bind(VisualisationPresenter.class)
- **Names:** `VisualisationEditor` is free.

**Stays in Java:** VisualisationPresenter.java:41-44 tab labels as TabData; Lines 83-86: afterSelectTab calls onChange when the Assets tab is shown; Lines 111-154: hasAssociatedDirty (the asset drafts) and the post-save and post-Save-As asset callbacks. DocumentEditor/DocumentEditorHost need hooks for these (see FloorMapDocEditor); Declares checked: VisualisationSettings, DocumentAsset (made up front, line 57) and MarkdownEdit are tab bodies added with addTab.

## `ElasticIndexPresenter`

document-editor, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.search.elastic.client.component.ElasticIndexEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/search/elastic/client/presenter/ElasticIndexPresenter.java`; `stroom-core-client/src/main/java/stroom/search/elastic/client/gin/ElasticIndexModule.java:33 bind(ElasticIndexPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/ElasticIndexScreenModule.java:38 bind(ElasticIndexPresenter.class)
- **Names:** `ElasticIndexEditor` is free and follows the DictionaryEditor pattern.

**Stays in Java:** ElasticIndexPresenter.java:37-40 tab labels as TabData; Declares checked: ElasticIndexSettings, ElasticIndexFieldList and MarkdownEdit are tab bodies added with addTab.

## `S3ConfigPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.aws.s3.client.component.S3ConfigEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/aws/s3/client/presenter/S3ConfigPresenter.java`; `stroom-core-client/src/main/java/stroom/aws/s3/client/gin/S3ConfigModule.java:28 bind(S3ConfigPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/S3ConfigScreenModule.java:32 bind(S3ConfigPresenter.class)
- **Names:** The suggested `S3Config` clashes with the server class stroom.aws.s3.impl.S3Config. `S3ConfigEditor` is free.

**Stays in Java:** S3ConfigPresenter.java:47-49 tab labels as TabData; Line 70: the 'Download S3 Configuration' button is a document-type button on the shared document toolbar, made in Java (design: Toolbars, UiBinder). It becomes getToolbar().button(...) as in DictionaryEditor, with its enabled state set on read (lines 71, 132); Lines 118-125: the download REST call moves to a private download() with .owner(this) in place of .taskMonitorFactory(this), as DictionaryEditor.download; Declares checked: Editor (wave 6, the PROPERTIES mode code editor) and MarkdownEdit are tab bodies added with addTab.

## `ScriptPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.script.client.component.ScriptEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/script/client/presenter/ScriptPresenter.java`; `stroom-core-client/src/main/java/stroom/script/client/gin/ScriptModule.java:36 bind(ScriptPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/ScriptScreenModule.java:35 bind(ScriptPresenter.class)
- **Names:** `ScriptEditor` is free. Its tab is wave 12's `ScriptSettings`.

**Stays in Java:** ScriptPresenter.java:42-45 tab labels as TabData; Lines 62-64: the code editor's JAVASCRIPT mode and its change wiring are set where the tab is made; Lines 76-85: after the first read it fires ClearScriptCacheEvent and ClearFunctionCacheEvent. These are app-wide events, so they stay as bus events; Declares checked: Editor (wave 6), ScriptSettings and MarkdownEdit are tab bodies added with addTab.

## `DocumentationPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.documentation.client.component.DocumentationEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/documentation/client/presenter/DocumentationPresenter.java`; `stroom-core-client/src/main/java/stroom/documentation/client/gin/DocumentationModule.java:28 bind(DocumentationPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/DocumentationScreenModule.java:32 bind(DocumentationPresenter.class)
- **Names:** The suggested `Documentation` clashes with stroom.docstore.shared.Documentation. `DocumentationEditor` is free and matches the existing workbench DocumentationEditorStories. It is the editor of DocumentationDoc, not the Documentation tab every document has (that is MarkdownEdit).

**Stays in Java:** DocumentationPresenter.java:46-47 tab labels as TabData; Line 67: the 'Download Documentation' button is a document-type button made in Java, with its enabled state set on read (lines 68, 105); Lines 77-79: selectTab inside the reader works around the markdown toolbar's read-only state. The READ_ONLY context makes this unnecessary, so it should go; Lines 91-98: the download REST call moves to download() with .owner(this); Declares checked: MarkdownEdit is a tab body added with addTab. ToolbarButton is on the TabbedController toolbar.

## `AnnotationPresenter`

document-editor, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.annotation.client.component.AnnotationTabs`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** stroom-core-client/src/main/java/stroom/annotation/client/AnnotationPresenter.java (no GIN binding; AnnotationEditSupport.java:43, AnnotationLinkPresenter.java:48,168 and LinkedEventPresenter.java:64,205 move)
- **Names:** The suggested `Annotation` clashes with stroom.annotation.shared.Annotation (the DTO it edits), stroom.editor.client.view.Annotation, a jOOQ table and a query function. An annotation is not a stored document: it has no Save As, and its Save is the description's own button. So it extends TabbedController<Annotation>, like FolderTabs, providing READ_ONLY and DOCUMENT itself, rather than DocumentEditor. `AnnotationEditor` is free but sits too close to wave 12's `AnnotationEdit` (its Annotation tab). `AnnotationTabs` is free and follows FolderTabs.

**Stays in Java:** AnnotationPresenter.java:42-47 tab labels (Annotation, Events, Link To, Link From, Documentation, Permissions) as TabData; Lines 88-92: the 'Save Annotation Description' button goes on the Documentation tab's toolbar, with its enabled state following the description's dirty state (lines 129-134); Lines 137-140: the 'Annotation actions' toolbar is the TabbedController toolbar (declared in LinkTabPanel). Its name is set with setName('Annotation actions') because it differs from the default 'Document actions'; Lines 148-151: the label '#' + id + ' ' + name is built from data; Lines 75-79: the tabs' setParent(this) back-references become callbacks on the converted AnnotationEdit, AnnotationLink and LinkedEvent controllers; Declares checked: AnnotationEdit, LinkedEvent, AnnotationLink (twice: to and from) and MarkdownEdit are tab bodies added with addTab. Toolbar and ToolbarButton are the tab toolbar.

## `ElasticClusterPresenter`

document-editor, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.search.elastic.client.component.ElasticClusterEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/search/elastic/client/presenter/ElasticClusterPresenter.java`; `stroom-core-client/src/main/java/stroom/search/elastic/client/gin/ElasticClusterModule.java:30 bind(ElasticClusterPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/ElasticClusterScreenModule.java:35 bind(ElasticClusterPresenter.class)
- **Names:** `ElasticClusterEditor` follows DictionaryEditor and is free. The suggested `ElasticCluster` is free but would be read as a model class.

**Stays in Java:** ElasticClusterPresenter.java:37-39 tab labels as TabData; Declares checked: ElasticClusterSettings and MarkdownEdit are tab bodies added with addTab.

## `GitRepoPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.gitrepo.client.component.GitRepoEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/gitrepo/client/presenter/GitRepoPresenter.java`; `stroom-core-client/src/main/java/stroom/gitrepo/client/gin/GitRepoModule.java:34 bind(GitRepoPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/GitRepoScreenModule.java:38 bind(GitRepoPresenter.class)
- **Names:** The suggested `GitRepo` clashes with stroom.content.GitRepo (stroom-core-shared) and the jOOQ table GitRepo. `GitRepoEditor` is free.

**Stays in Java:** GitRepoPresenter.java:37-39 tab labels as TabData; Declares checked: GitRepoSettings and MarkdownEdit are tab bodies added with addTab.

## `TextConverterPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.pipeline.client.component.TextConverterEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/client/presenter/TextConverterPresenter.java`; `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:105 bind(TextConverterPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/PipelineScreenModule.java:102, query/PipelineScreenModule.java:102, editors/PipelineScreenModule.java:57, content/PipelineScreenModule.java:102 bind(TextConverterPresenter.class)
- **Names:** `TextConverterEditor` is free. Its settings tab is wave 8's `TextConverterSettings`.

**Stays in Java:** TextConverterPresenter.java:43-46 tab labels as TabData; Lines 114-143: setEditorMode chooses the Ace mode from the converter type, or from the code. This is runtime logic, and it becomes a callback from the settings tab's converter type to the editor tab; Line 141: the GWT.log call should go; Declares checked: Editor (wave 6), TextConverterSettings and MarkdownEdit are tab bodies added with addTab.

## `XsltPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.pipeline.client.component.XsltEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/client/presenter/XsltPresenter.java`; `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:110 bind(XsltPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/PipelineScreenModule.java:106, query/PipelineScreenModule.java:106, editors/PipelineScreenModule.java:61, content/PipelineScreenModule.java:106 bind(XsltPresenter.class)
- **Names:** `XsltEditor` is free.

**Stays in Java:** XsltPresenter.java:39-41 tab labels as TabData (XSLT, Documentation, Permissions); Lines 55-57: XML mode and change wiring are set where the tab is made. Remove the commented-out lines 65-67; Declares checked: Editor (wave 6) and MarkdownEdit are tab bodies added with addTab.

## `XMLSchemaPresenter`

document-editor, presenter; complexity 3, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.xmlschema.client.component.XMLSchemaEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/xmlschema/client/presenter/XMLSchemaPresenter.java`; `stroom-core-client/src/main/java/stroom/xmlschema/client/gin/XMLSchemaModule.java:35 bind(XMLSchemaPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/XMLSchemaScreenModule.java:38 bind(XMLSchemaPresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/XMLSchemaScreenModule.java:38 bind(XMLSchemaPresenter.class)
- **Names:** `XMLSchemaEditor` is free and keeps the area's spelling (XMLSchemaSettings, XMLSchemaModule). `XmlSchemaEditor` is also free, after XmlSchemaDoc, but would mix the two spellings in one package.

**Stays in Java:** XMLSchemaPresenter.java:60-64 tab labels as TabData; Lines 55-57 and 217-225: the validation indicator's title ('Show Validation Result (schema is valid/invalid)') and icon (OK/ALERT) change with the result. It is a document-type button made in Java (line 88); Lines 90-100 and 140-141: the validation debounce Timer stays in the editor and must be cancelled in onDispose; Lines 193-198: the REST validate call uses .owner(this) in place of .taskMonitorFactory(this); Lines 253-268: the save override (confirm when invalid) becomes DocumentEditor's save path, which needs a before-save hook or setOnSave; Lines 201-215: afterSelectTab refreshes the diagram when the Graphical tab is shown; Declares checked: XSDBrowser (wave 8), Editor (wave 6), XMLSchemaSettings and MarkdownEdit are tab bodies added with addTab. They share the XSDModel (line 69).

## `OpenAIModelPresenter`

document-editor, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.openai.client.component.OpenAIModelEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/openai/client/presenter/OpenAIModelPresenter.java`; `stroom-core-client/src/main/java/stroom/openai/client/gin/OpenAIModelModule.java:31 bind(OpenAIModelPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/OpenAIModelScreenModule.java:35 bind(OpenAIModelPresenter.class)
- **Names:** `OpenAIModelEditor` is free.

**Stays in Java:** OpenAIModelPresenter.java:37-39 tab labels as TabData; Declares checked: OpenAIModelSettings and MarkdownEdit are tab bodies added with addTab.

## `KafkaConfigPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.kafka.client.component.KafkaConfigEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/kafka/client/presenter/KafkaConfigPresenter.java`; `stroom-core-client/src/main/java/stroom/kafka/client/gin/KafkaConfigModule.java:28 bind(KafkaConfigPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/KafkaConfigScreenModule.java:32 bind(KafkaConfigPresenter.class)
- **Names:** The suggested `KafkaConfig` clashes with the server class stroom.kafka.impl.KafkaConfig. `KafkaConfigEditor` is free.

**Stays in Java:** KafkaConfigPresenter.java:47-49 tab labels as TabData; Line 70: the 'Download Kafka Config' button is a document-type button made in Java, with its enabled state set on read (lines 71, 132); Lines 118-125: download() uses .owner(this), as DictionaryEditor; Declares checked: Editor (wave 6, PROPERTIES mode) and MarkdownEdit are tab bodies added with addTab. ToolbarButton is on the TabbedController toolbar.

## `TracesPresenter`

document-editor, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.pathways.client.component.TracesEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/TracesPresenter.java`; `stroom-core-client/src/main/java/stroom/pathways/client/gin/PathwaysModule.java:67 bind(TracesPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PathwaysScreenModule.java:67 bind(TracesPresenter.class)
- **Names:** `TracesEditor` is free. TAB_TYPE (line 38) moves with it.

**Stays in Java:** TracesPresenter.java:40-43 tab labels as TabData; Lines 95-113: refresh() is a deliberate no-op, and forceRefresh, setDataSourceRef, setFilter and setPathway pass through to the Traces tab, so they stay as the editor's inputs; Declares checked: TracesListTab (made up front, line 55), TracesSettings and MarkdownEdit are tab bodies added with addTab.

## `PathwaysPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.pathways.client.component.PathwaysEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwaysPresenter.java`; `stroom-core-client/src/main/java/stroom/pathways/client/gin/PathwaysModule.java:54 bind(PathwaysPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/PathwaysScreenModule.java:47 bind(PathwaysPresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PathwaysScreenModule.java:54 bind(PathwaysPresenter.class)
- **Names:** `PathwaysEditor` is free. The suggested `Pathways` is free but generic.

**Stays in Java:** PathwaysPresenter.java:38-41 tab labels as TabData. Pathways is first and selected (lines 53, 71); Line 50: the injected ClientSecurityContext is unused and should be dropped; Declares checked: PathwaysSplit, PathwaysSettings and MarkdownEdit are tab bodies added with addTab.

## `DataGenPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.datagen.client.component.DataGenEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/datagen/client/presenter/DataGenPresenter.java`; `stroom-core-client/src/main/java/stroom/datagen/client/gin/DataGenModule.java:38 bind(DataGenPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/DataGenScreenModule.java:38 bind(DataGenPresenter.class)
- **Names:** `DataGenEditor` is free.

**Stays in Java:** DataGenPresenter.java:41-44 tab labels as TabData; Lines 55-56: the Execution tab is made up front and given the editor (setDocumentEditPresenter(this)). This becomes a callback or a DIRTY context lookup on the converted DataGenProcessingController; Declares checked: DataGenSettings, DataGenProcessing and MarkdownEdit are tab bodies added with addTab.

## `DashboardSuperPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.dashboard.client.component.DashboardEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/main/DashboardSuperPresenter.java (no GIN binding; DashboardPlugin.java:59,65,116,178 and DashboardPresenter.java:654 move)
- **Names:** The suggested `DashboardSuper` doesn't describe it: it is the dashboard document's editor around the Dashboard tab (wave 13 DashboardPresenter -> `Dashboard`). `DashboardEditor` is free.

**Stays in Java:** DashboardSuperPresenter.java:47-49 tab labels as TabData (the Dashboard tab has type DashboardDoc.TYPE); Lines 140-142: the label is built from data (dashboard label + instance string), and so is the instance number for HasMultipleInstances (lines 144-151); Lines 84-85: listen(ContentTabSelectionChangeEvent); Lines 102-118: handleKeyAction for the Dashboard tab; Lines 121-128: it passes on the Dashboard tab's document buttons and toolbars (HasToolbar). TabbedController already merges these from the tab showing, so this goes; Lines 88-92 and 153-167: pass-through setters (parent context, link params, custom title, query on open, result store) stay as inputs on the editor; Declares checked: Dashboard and MarkdownEdit are tab bodies added with addTab. Dashboard is made up front (line 62).

## `SolrIndexPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.search.solr.client.component.SolrIndexEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/search/solr/client/presenter/SolrIndexPresenter.java`; `stroom-core-client/src/main/java/stroom/search/solr/client/gin/SolrIndexModule.java:37 bind(SolrIndexPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/SolrIndexScreenModule.java:41 bind(SolrIndexPresenter.class)
- **Names:** `SolrIndexEditor` is free.

**Stays in Java:** SolrIndexPresenter.java:37-40 tab labels as TabData. Fields is added first and selected (lines 52, 70); Declares checked: SolrIndexFieldList, SolrIndexSettings and MarkdownEdit are tab bodies added with addTab. DocumentUserPermissions is wrapped with PresenterAdapter until it is converted.

## `ViewPresenter`

document-editor, presenter; complexity 1, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.view.client.component.ViewDocEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/view/client/presenter/ViewPresenter.java`; `stroom-core-client/src/main/java/stroom/view/client/gin/ViewModule.java:32 bind(ViewPresenter.class)`; `stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/ViewScreenModule.java:34 bind(ViewPresenter.class)`
- **Names:** The suggested `View` clashes with stroom.widget.presenter.client.View, the view interface used across the client. `ViewEditor` is free but reads as an editor of views in the MVP sense, so the name is `ViewDocEditor`, after ViewDoc, which is free.

**Stays in Java:** ViewPresenter.java:37-39 tab labels as TabData; Declares checked: ViewSettings and MarkdownEdit are tab bodies added with addTab.

## `FeedPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.feed.client.component.FeedEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/feed/client/presenter/FeedPresenter.java`; `stroom-core-client/src/main/java/stroom/feed/client/gin/FeedModule.java:35 bind(FeedPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/FeedScreenModule.java:38 bind(FeedPresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/shell/ShellAppScreenModule.java:123 bind(FeedPresenter.class)
- **Names:** `FeedEditor` is free. It sits beside wave 8's FeedSettings and FeedSettingsController in stroom.feed.client.component.

**Stays in Java:** FeedPresenter.java:42-46 tab labels (Settings, Data, Active Tasks, Documentation, Permissions) as TabData; Lines 59-99: the Data and Active Tasks tabs are added only with VIEW_DATA_PERMISSION or MANAGE_PROCESSORS_PERMISSION, and the first tab shown depends on permission (lines 59, 82, 119). These are runtime decisions in Java; Lines 69-80: the Data tab's reader calls read and then refreshData on every read, so the encoding change is picked up after a save; Declares checked: FeedSettings, Meta, ProcessorTask and MarkdownEdit are tab bodies added with addTab.

## `PipelinePresenter`

document-editor, presenter; complexity 4, effort L; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.pipeline.client.component.PipelineEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/pipeline/client/presenter/PipelinePresenter.java`; `stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:113 bind(PipelinePresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/processing/PipelineScreenModule.java:107 bind(PipelinePresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PipelineScreenModule.java:107 bind(PipelinePresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PipelineScreenModule.java:107 bind(PipelinePresenter.class)
- **Names:** The suggested `Pipeline` clashes with the server class stroom.pipeline.factory.Pipeline. `PipelineEditor` is free.

**Stays in Java:** PipelinePresenter.java:60-65 tab labels as TabData. DATA, STRUCTURE, PROCESSORS and TASKS are public and are read by callers; Lines 106-121 and 161-202: the Data, Processors and Active Tasks tabs and the default tab depend on permissions; Line 222: the 'Stepping Mode' toggle is a document-type button made in Java. Its visibility follows the selected tab (lines 257-259) and its state follows the mode (line 250); Lines 243-249: stepping mode replaces the Structure tab's body (replaceTab). TabbedController needs a way to swap a tab's content; Lines 286-304 and 335-337: the PipelineModel shared by Structure and Stepping stays in the editor as the shared model. hasAssociatedDirty (the stepping editors' dirty documents) needs the DocumentEditor hook; Declares checked: Meta, PipelineStructure, Processor, ProcessorTask, Stepping and MarkdownEdit are tab bodies added with addTab. Stepping and Structure are made up front (lines 123-124).

## `RuleSetPresenter`

document-editor, presenter; complexity 4, effort M; used by 1.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.receive.rules.client.component.RuleSetEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** stroom-core-client/src/main/java/stroom/receive/rules/client/presenter/RuleSetPresenter.java (no GIN binding; ReceiveDataRuleSetPlugin.java:41,45 is the caller that moves); `Its ChangeUiHandlers implementation (RuleSetPresenter.java:72). The interface is shared by 64 files and stays`; `Its own tab, toolbar and selection code (lines 187-229, 302-312), which TabbedController replaces`
- **Names:** `RuleSet` is free, but `RuleSetEditor` matches the pilot's RuleEditor, FieldEditor and RuleSetSettings in the same package, and it is free. It extends TabbedController<ReceiveDataRules>, not DocumentEditor, because the rules have no Save As and are not opened by DocumentPlugin (it is a MonitoringPlugin content tab). It provides READ_ONLY=false and DIRTY itself, as it does today (lines 109-111), and it keeps its own Save Rules button.

**Stays in Java:** RuleSetPresenter.java:75-78 tab labels Rules/Fields/Documentation and the content tab label 'Data Receipt Rules', which gets a '* ' prefix while dirty (lines 296-298); Lines 127-130: the 'Document actions' toolbar becomes TabbedController.getToolbar(), which is declared in the LinkTabPanel template. The 'Save Rules' button (line 128) stays in Java; Lines 167-170: the 'Show Warnings' button is added only when receiptCheckMode != RECEIPT_POLICY, from UiConfigCache, so it is runtime; Lines 174-179: the warning text is built from the config value; Content-tab plumbing (CloseContentEvent, HasSave, DocumentTabData, HasSaveRegistry, lines 72, 123-126, 319-338) stays in a content-tab host until wave 15 converts the shell; Declares checked: FieldList (wave 10) and MarkdownEdit are tab bodies added with addTab. Toolbar and ToolbarButton are the TabbedController toolbar.

## `FloorMapEventStorePresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared tab panel, unit 765)`
- **Controller:** `stroom.floormap.client.component.FloorMapEventStoreEditor`
- **Template:** `none` → `none (its widget is the shared LinkTabPanel, stroom-core-client/src/main/java/stroom/entity/client/component/LinkTabPanel.ui.xml; tabs are added with addTab, as DictionaryEditor)`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapEventStorePresenter.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:100 bind(FloorMapEventStorePresenter.class)`
- **Names:** The suggested `FloorMapEventStore` + `FloorMapEventStoreController` don't fit the agreed document pattern. A document editor is one controller that extends DocumentEditor<D>, and its component is the shared LinkTabPanel (DictionaryEditor is the precedent). `FloorMapEventStoreEditor` is free in all_simple_names.json and in the wave plans. TAB_TYPE (line 39) moves with it.

**Stays in Java:** FloorMapEventStorePresenter.java:41-44 tab labels Settings/Data/Documentation/Permissions: TabData constants passed to addTab, because LinkTabBar builds its tabs from TabData and there is no template form for tabs (pilot precedent); Declares checked: FloorMapEventStoreSettings and FloorMapEventStoreData (as converted earlier) and MarkdownEdit are tab bodies, made and owned when first shown through addTab(tab, make, reader, writer). They are not declared in a template. Permissions uses PresenterAdapter.of(permissionsProvider::get); Line 71: the Documentation writer uses document.copyEventStore().description(...), not copy().

## `SqlTemporalStorePresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.sqlstore.client.component.SqlTemporalStoreEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/sqlstore/client/presenter/SqlTemporalStorePresenter.java`; stroom-core-client/src/main/java/stroom/sqlstore/client/gin/SqlTemporalStoreModule.java:33 bind(SqlTemporalStorePresenter.class) (it has no workbench binding)
- **Names:** `SqlTemporalStoreEditor` is free.

**Stays in Java:** SqlTemporalStorePresenter.java:37-40 tab labels as TabData; Declares checked: SqlTemporalStoreSettings, SqlTemporalStoreData and MarkdownEdit are tab bodies added with addTab.

## `IndexPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.index.client.component.LuceneIndexEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexPresenter.java`; `stroom-core-client/src/main/java/stroom/index/client/gin/IndexModule.java:46 bind(IndexPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/editors/IndexScreenModule.java:58 bind(IndexPresenter.class)
- **Names:** The suggested `Index` is far too generic (and close to stroom.index.impl.IndexDocument). It edits LuceneIndexDoc, so the name is `LuceneIndexEditor` (free), which sits beside ElasticIndexEditor and SolrIndexEditor.

**Stays in Java:** IndexPresenter.java:39-44 tab labels as TabData; Lines 58-62: the Shards tab exists, and is selected first, only with MANAGE_INDEX_SHARDS_PERMISSION; Declares checked: IndexShard, IndexFieldList, IndexSettings, IndexData and MarkdownEdit are tab bodies added with addTab.

## `PlanBPresenter`

document-editor, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.planb.client.component.PlanBEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/planb/client/presenter/PlanBPresenter.java`; `stroom-core-client/src/main/java/stroom/planb/client/gin/PlanBModule.java:57 bind(PlanBPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/PlanBScreenModule.java:61 bind(PlanBPresenter.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/content/PlanBScreenModule.java:61 bind(PlanBPresenter.class)
- **Names:** `PlanBEditor` is free.

**Stays in Java:** PlanBPresenter.java:40-43 tab labels as TabData. Keep the comment at lines 35-37 about the tab order; Declares checked: PlanBSettings, PlanBData and MarkdownEdit are tab bodies added with addTab.

## `FloorMapPresenter`

document-editor, presenter; complexity 3, effort M; used by 5.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.floormap.client.component.FloorMapDocEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapPresenter.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:99 bind(FloorMapPresenter.class)`
- **Names:** `FloorMapEditor` (the pattern's name) clashes with wave 13's FloorMapEditorPresenter -> `FloorMapEditor`, the document's Editor tab, so this is `FloorMapDocEditor`, which is free. The suggested `FloorMap` is free but breaks the DocumentEditor naming.

**Stays in Java:** FloorMapPresenter.java:50-56 tab labels (Map, Editor, Events Query, Settings, Assets, Documentation, Permissions) stay as TabData for addTab; Lines 99-201: the tab wiring between Map, Editor, Events Query and Settings (setOnEventData, initial view, area and status patches) stays in the editor as callbacks between the tab controllers it makes; Lines 237-238 and 247-287: listen(ContentTabSelectionChangeEvent) and the logic that pauses the timeline when its tab is hidden. This needs an after-select hook on TabbedController, which only has selectTab today; Lines 307-366: hasAssociatedDirty and the post-save and post-Save-As callbacks (editor flush, then asset save). DocumentEditor has no equivalent of DocPresenter.hasAssociatedDirty or getPostSaveCallback yet, so DocumentEditor/DocumentEditorHost must gain one; Declares checked: FloorMapMap, FloorMapEditor, FloorMapQuery, FloorMapSettings, DocumentAsset and MarkdownEdit are tab bodies made on first show by addTab, not template declarations.

## `QueryDocPresenter`

document-editor, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.query.client.component.QueryDocEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/query/client/presenter/QueryDocPresenter.java`; `stroom-core-client/src/main/java/stroom/query/client/gin/QueryModule.java:63 bind(QueryDocPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/QueryScreenModule.java:60 bind(QueryDocPresenter.class)
- **Names:** `QueryDoc` clashes with the shared DTO stroom.query.shared.QueryDoc, which every file here imports. `QueryDocEditor` is free and pairs with its tab, wave 13's `QueryDocEdit`. `QueryEditor` is free too, but it reads as the QueryEdit tab.

**Stays in Java:** QueryDocPresenter.java:42-44 tab labels Query/Documentation/Permissions as TabData; Lines 82-83: listen(ContentTabSelectionChangeEvent) is an app-wide event, so it stays as listen; Lines 87-107: handleKeyAction (OK starts and CLOSE stops when the Query tab is selected) needs a selected-tab getter on TabbedController; Lines 110-120: the save interceptor becomes DocumentEditor.setOnSave, which EmbeddedQuery (unit 783) sets. Convert this unit before 783, or with it; Declares checked: QueryDocEdit and MarkdownEdit are tab bodies added with addTab. QueryDocEdit is made up front (line 58) because the key actions reach it.

## `AnalyticRulePresenter`

document-editor, presenter; complexity 3, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.analytics.client.component.AnalyticRuleEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticRulePresenter.java`; `stroom-core-client/src/main/java/stroom/analytics/client/gin/AnalyticsModule.java:65 bind(AnalyticRulePresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/AnalyticsScreenModule.java:67 bind(AnalyticRulePresenter.class)
- **Names:** `AnalyticRuleEditor` is free. The Abstract* tab bases it uses (AbstractQueryEdit, AbstractProcessing, AbstractNotification, AbstractDuplicateManagement, AbstractSettings) are merged into their Analytic* and Report* subclasses in their own waves, so the editor makes the concrete Analytic* controllers.

**Stays in Java:** AnalyticRulePresenter.java:41-48 tab labels (Query, Settings, Notifications, Execution, Shards, Duplicate Management, Documentation, Permissions) as TabData; Lines 125-131: setRuleType hides Shards and Duplicate Management depending on the AnalyticProcessType (setTabHidden). This is runtime state; Lines 105-117: handleKeyAction (start and stop the query when the Query tab is selected); Lines 73-77: the Notifications and Execution tabs are made up front and linked through setOnProcessingTypeChange; Declares checked: the query edit, settings, notification, processing, data shards, duplicate management and MarkdownEdit controllers are tab bodies added with addTab. DocumentUserPermissions is wrapped with PresenterAdapter.

## `ReportPresenter`

document-editor, presenter; complexity 2, effort M; used by 2.

- **Component:** `stroom.entity.client.component.LinkTabPanel (shared, unit 765)`
- **Controller:** `stroom.analytics.client.component.ReportEditor`
- **Template:** `none` → `none (shared LinkTabPanel.ui.xml; tabs added with addTab)`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ReportPresenter.java`; `stroom-core-client/src/main/java/stroom/analytics/client/gin/ReportModule.java:31 bind(ReportPresenter.class)`; stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/query/ReportScreenModule.java:34 bind(ReportPresenter.class)
- **Names:** `ReportEditor` is free. The suggested `Report` is free but generic.

**Stays in Java:** ReportPresenter.java:40-45 tab labels as TabData; Lines 95-107: handleKeyAction for the Query tab; Lines 116-119: on read it tells the notifications tab the process type; Lines 64-69: the Execution and Notifications tabs are made up front; Declares checked: ReportQueryEdit, ReportSettings, ReportNotification, ReportProcessing and MarkdownEdit are tab bodies added with addTab.

## `EmbeddedQueryPresenter`

composite, presenter; complexity 5, effort XL; used by 7.

- **Component:** `stroom.dashboard.client.component.EmbeddedQuery`
- **Controller:** `stroom.dashboard.client.component.EmbeddedQueryController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/embeddedquery/EmbeddedQueryViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/EmbeddedQuery.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/EmbeddedQueryPresenter.java:913-919 EmbeddedQueryView (view interface); `stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/EmbeddedQueryViewImpl.java`; stroom-core-client/src/main/java/stroom/dashboard/client/embeddedquery/gin/EmbeddedQueryModule.java:38 bindPresenterWidget(EmbeddedQueryPresenter.class, EmbeddedQueryView.class, EmbeddedQueryViewImpl.class); stroom-gwt/stroom-gwt-workbench/src/main/java/stroom/gwt/workbench/client/app/gin/dashboard/EmbeddedQueryScreenModule.java:36-38 bindPresenterWidget(EmbeddedQueryPresenter.class, EmbeddedQueryView.class, ...)
- **Names:** `EmbeddedQuery` and `EmbeddedQueryController` are both free. They sit with wave 13's dashboard components (Table, Text, Vis, Query) in stroom.dashboard.client.component, and the controller extends wave 13's merged AbstractComponent base. Callers that move: EmbeddedQueryPlugin.java:28-29, TabManager.java:214,354,368,377, SelectionHandlersPresenter.java:310, BasicTableFilterSettingsPresenter.java:105 (TYPE). TAB_TYPE and TYPE (lines 89-96) move to the controller. It uses QueryDocPresenter (line 370, this wave's QueryDocEditor), so convert unit 190 first or together.

**Stays in Java:** EmbeddedQueryViewImpl.ui.xml:4: the result container already holds its static classes (TableViewImpl max stroom-dashboard-table) and has no inline style. It moves as <g:SimplePanel ui:field="result" .../> with the class names wave 13 chooses for Table (see cssNotes); EmbeddedQueryPresenter.java:481-488, 553: the table or vis result is a slot (EmbeddedQuery.setResult(IsWidget)). It isn't declared, because QueryResultTable and QueryResultVis are made and dropped at runtime per search and per settings (createNewTable/destroyCurrentTable lines 490-525, createNewVis/destroyCurrentVis lines 527-557); Declares checked: EmbeddedQuerySettings is a settings dialog opened through the component framework (constructor line 124), not declared. QueryResultTable and QueryResultVis go in the slot above; Existing defect to record in ISSUES.md: EmbeddedQueryViewImpl.java:37,58-62 onResize tests a 'view' field that setResultView (lines 49-56) never assigns, so the result is never resized. The new component should resize whatever is in its slot; Existing defect: destroyCurrentTable (EmbeddedQueryPresenter.java:517-525) sets currentTablePresenter to null without release(), so every table made by make() stays owned until the component is disposed.

