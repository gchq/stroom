# Wave 12: Tabs and composite panes (excluding query and dashboard)

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 4 (25 October 2026):** the date-time, time, schedule and time range boxes' controllers (`DateTimeBoxController`, `TimeBoxController`, `ScheduleBoxController`, `TimeRangeSelectorController`) are already attached by today's presenters, so there is no `setPopupProvider` or `setSchedulePresenterProvider` left to replace: a converted controller makes and attaches them as they do. `CustomFileUpload` is `FileChooser`, with a `FileChooserController` its owner attaches; `ClassificationWrapperView` is `ClassificationWrapper` (`stroom.data.client.component`); the schedule dialog is `SchedulePickerController`. The rows below name what was there when they were written.

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`.

> **Since wave 6 (25 October 2026):** the code editor is `CodeEditor` (`stroom.editor.client.component`; declare it, e.g. `<ed:CodeEditor ui:field="editor"/>`, and attach its controller, `make(codeEditorControllerProvider).attach(view.getEditor())`), with `CodeEditorOptions` and `CodeEditorController` (`EditorPresenter` is a thin wrapper over them; `Editor` is only the Ace wrapper inside it); the single line editor is `SingleLineCodeEditor` with `SingleLineCodeEditorController.attach(...)`; the explorer tree is one `ExplorerTree` (no-arg; `ExplorerTickBoxTree` is `setTickBoxes(true)`; `ExtendedExplorerTree` is gone, its overrides `setOnChoose` and `setOnInitialSelect`), filled by `ExplorerTreeController.attach(tree)` (was `ExplorerTreeModel`); `DraggableTreePanel` is concrete, with its callbacks (`setIsValidTarget`, `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), but takes no children in a template: its two panels go in with `setTreePanels(tree, subTree)`; `DocumentAssetTree` has a no-arg constructor and `setAccessibleName`; the pickers are a `DropDownBox` with a controller attached (`DocumentAssetSelectionController` in `stroom.document.asset.client.component`, `UserRefSelectionController` in `stroom.security.client.component`; `DropDownView` is gone); the annotation choosers are one-use popups, `ChooserController` and `MultiChooserController`, `show(opener, element, caption, onChoose)`, each opening tracked by an `OpenChooser` so that a second click closes it; the menu is `MenuController`. The plans below keep the old names. See `wave-06.md`.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

> **Since wave 10 (27 October 2026):** the lists are components with a controller each (`stroom.<area>.client.component`). A list's template declares its `PagerPanel` (or `HeadedPagerPanel`) with a `toolbarName`, holding its `DataGrid` (`tableName`, `multiLine`, `pageSize`) and its toolbar's `ToolbarButton`s (`svg`, `title`, `enabled="false"`); a quick-filtered list's root is a `QuickFilterPage` (`accessibleName`). The component adds its columns and gives its pager as `getTaskMonitor()`, with `getSelectionModel()`, `getSortList()` and `getToolbar()`; its buttons, info and row menus go out through callbacks (`setOnAdd`, `setOnShowInfo`, `setOnShowActionMenu`), and its controller passes in the ref cells configured (`refCells.configure(new DocRefCell.Builder<>())`, `FeedRefCell`, `UserRefCell`). Only a list inside a document watches `Contexts.READ_ONLY`, which is true where nothing provides it. A controller shown as a tab is a `ControllerLayer`, which passes the focus on. Among the new names: `MetaList` with `MetaListController`, `SteppingMetaListController` and `MetaRelationListController`; `JobNodeColumns` and `JobNodeActionsController` (were `JobNodeListHelper`); one `NotificationList` for analytic and report notifications; `ScriptDependencyList` (`ScriptListPresenter` merged in); `IncludeExcludeFilterEditor` and `IncludeExcludeFilterDictionary`; `DependencyList` (was `DependenciesPresenter`); `TabSessionChooser`; `CredentialsList`; `FindDocResultList`; `EffectiveWordsList`; `TypeFilter`. The plans below keep the old names. See `wave-10.md`.

Convert document tabs and large composite panes into components with controllers, removing the dependency on DocPresenter: data, source and stepping; pipeline structure; annotations; processing and notifications; Plan B settings; user and permissions tabs; retention and processor tabs. Hand-rolled tabs become TabbedComponent.

## CSS and template notes for the wave

- New shared modifier text-field--wide { width: 400px; } in text-field.css for the width="400px" text boxes and areas (23 uses in 10 templates; in this wave: ElasticClusterSettings, UserInfoTab, SolrIndexSettings, OpenAIModelSettings). width="200px" on a TextBox/TextArea is the .text-field default (text-field.css:34-35) and can simply go.
- Button width="200px" directly in a .form or .form-group: drop it, form.css already gives '.form > .Button, .form-group > .Button' min-width: 200px (ElasticClusterSettings, SolrIndexSettings, OpenAIModelSettings). Buttons outside a form (AnnotationEdit's .annotationButtons) need a rule of their own in their block's file.
- New shared modifier value-spinner--narrow { width: 80px; } in value-spinner.css for ValueSpinner width="80px" (4 templates; OpenAIModelSettings here).
- width="100%" on a field: existing w-100 (stroom.css:843).
- Button icons set in Java (setIcon(SvgImage.X)) move to the template as icon="X": SvgImage is an enum, so UiBinder parses it (ElasticClusterSettings, SolrIndexSettings, OpenAIModelSettings, AnnotationEdit).
- Blocker for text in templates: TextBox/TextArea placeholders and aria-labels (AnnotationEdit's "Title"/"Subject") have no setter, and Stroom's TextBox/TextBoxBase/ValueBoxBase copies are in the protected stroom-core-client-widget/src/main/java/com/ directory (human approval needed to add setPlaceholder/setAriaLabel there; or a Stroom subclass outside com/, or the new stroom.widget.contextmenu.client.TextField support, used in templates).
- getStyle().setBorderStyle(BorderStyle.NONE) on a list's PagerView (DataRetentionPolicyPresenter.java:131, RuleSetSettings.java:68, RulesPresenter.java:106, SelectionHandlersPresenter.java:129, ContentTemplateTabPresenter.java:147): PagerViewImpl.ui.xml's root has no border class, so it looks like a no-op to delete; confirm by screenshot, else one pager modifier class shared by all five.
- Faded, inert panes waiting for a selection (UserAndGroups): .user-and-groups__pane[inert] { opacity: var(--disabled-opacity); } in components.css, reusing the theme's --disabled-opacity (0.4, theme-root.css:22), so Java only toggles inert.
- CSS classes named after deleted ViewImpls should take the component's BEM name when their template moves: .DuplicateManagementViewImpl and .ScheduledProcessingViewImpl (components.css:1799-1802), .DictionarySettingViewImpl-words (components.css:1824), .pipelineStructureViewImpl-* (components.css:198-252), visualisationAssets-* (components.css:2246-2256).
- 'default-min-sizes' added in Java to dialogs' content (UserPermissionReportPresenter.java:164, ProcessorPresenter.java:253 on ExpressionPresenter; PipelineStructurePresenter.java:588 with form-control-border on the source editor) belongs on the dialog content component's own template root once those are components, not on the opener.
- Several 'declares' entries in wave12.json are dialogs or popups the controller opens with makeDialog (HttpClientConfig, DocSelectionPopup, the annotation choosers, CommentEdit, Create*/Batch*/DocumentUserPermissionsEdit, Expression, ProcessorEdit, NewElement, ScheduledProcessEdit, DataRetentionRule, GitRepoCommitDialog, DocumentAsset*Dialog). Under 'Done means' rule 4 and the migration doc (opening a dialog doesn't count) they are not declared in templates. 'DataGridWidget' in the declares lists is now stroom.widget.datagrid.client.DataGrid and 'PagerView' is in stroom.widget.datagrid.client (wave 5 may rename it PagerPanel).
- Order inside wave 12: 521 ScheduledProcessing before 360 DataGenProcessing and 505 AnalyticProcessing; 405 Processors before 522 StreamingProcessing; 685 PlanBSettings before 760 TracesSettings; 164 and 740 are one conversion.

## `UserInfoPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.client.component.UserInfoTab`
- **Controller:** `stroom.security.client.component.UserInfoTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/UserInfoViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/UserInfoTab.ui.xml`
- **Goes:** `UserInfoPresenter.UserInfoView`; `stroom-core-client/src/main/java/stroom/security/client/view/UserInfoViewImpl.java`; `SecurityModule.java:178-181 bindPresenterWidget(UserInfoPresenter, UserInfoView, UserInfoViewImpl)`
- **Names:** UserInfo clashes with stroom.util.shared.UserInfo (a shared DTO used on the client), so UserInfoTab + UserInfoTabController (it is the Info tab of the user screen); both free.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| UserInfoViewImpl.ui.xml:24 | `<g:TextBox ui:field="displayName" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| UserInfoViewImpl.ui.xml:38 | `<g:TextBox ui:field="fullName" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| UserInfoViewImpl.ui.xml:57 | `<g:TextBox ui:field="subjectId" width="400px"/>` | static | text-field--wide (new, text-field.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| UserInfoViewImpl.java:60-62 | structure | displayName/subjectId/fullName setReadOnly(true) -> readOnly="true" on the three <g:TextBox> (always read only, not state) |
| UserInfoViewImpl.ui.xml:64 | component-declaration | <tickbox:TickBox ui:field="isEnabledTickBox" label="Enabled"/> and the FormGroups are already declared (waves 1-2) |

**Stays in Java:** UserInfoViewImpl.java:72-82 the visibility of three groups and the subject id group's label/help/screen reader text switching between user ("Unique Identifier") and group ("Group Name") (from the UserRef); UserInfoViewImpl.java:85-88 values from the UserRef; UserInfoViewImpl.java:96 setReadOnly: fix the inverted flag while splitting (setEnabled(isReadOnly) with UserInfoPresenter passing hasPermissionToEdit()); the dumb setReadOnly(true) must mean read only, and the controller passes !hasPermissionToEdit().

## `ElementPresenter`

tab, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.pipeline.stepping.client.component.SteppingElement`
- **Controller:** `stroom.pipeline.stepping.client.component.SteppingElementController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingElement.ui.xml`
- **Goes:** `ElementPresenter.ElementView (ElementPresenter.java:676-)`; `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/ElementViewImpl.java`; `PipelineModule.java:138 bindPresenterWidget(ElementPresenter, ElementView, ElementViewImpl) and imports PipelineModule.java:33-34`
- **Names:** Element clashes with stroom.pipeline.factory.Element and, worse, with com.google.gwt.dom.client.Element used throughout client code. It is one pipeline element's panes on the stepping screen, so SteppingElement + SteppingElementController (free).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ElementViewImpl.java:78 | `widget.setSize("100%", "100%")` | static | existing max (stroom.css:793) on the template root |

| Into the template | What | Detail |
| --- | --- | --- |
| ElementViewImpl.java:40-76,82-88 | structure | the layout built in asWidget/thinSplitPane goes into the template: <l:SplitPane ui:field="layout" vertical="true" thin="true" sizes="50%, *, 15%" names="Code, Output, Log" addStyleNames="max"> with the code pane, an inner <l:SplitPane ui:field="inputAndOutput" thin="true" sizes="50%, *" names="Input, Output"> and the log pane; panes not used by an element are hidden with setPaneHidden (code, input) as the log is today |
| ElementViewImpl.java:46-47,55,66 | css-class | dashboard-panel overflow-hidden on the code, input, output and log panes, in the template |
| ElementPresenter.java:124 (code), 190 (log) | component-declaration | <ed:CodeEditor ui:field="code"/> and <ed:CodeEditor ui:field="log"/> (wave 6's dumb editor component) in the code and log panes |
| ElementPresenter.java:96-97,155,159 | component-declaration | <d:ClassificationWrapper ui:field="input"> and <d:ClassificationWrapper ui:field="output"> (wave 4, stroom.data.client.presenter) each wrapping a <ed:CodeEditor/> in the input and output panes |

**Stays in Java:** Which panes show (from the element's roles: has code, mutator, validator), log visibility, editor texts, modes and indicators (ElementPresenter.java:123-207,321,445); LOG_PANE_SIZE as part of the template's sizes; SplitPane split positions the user drags.

## `FloorMapSettingsViewImpl`

tab, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.component.FloorMapSettings`
- **Controller:** `stroom.floormap.client.component.FloorMapSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapSettings.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSettingsViewImpl.java (becomes the FloorMapSettings component)`; `FloorMapSettingsPresenter.FloorMapSettingsView`; `FloorMapModule.java:120-122 bindPresenterWidget line (shared with unit 164)`
- **Names:** Not a widget that keeps its name: it is the view half of FloorMapSettingsPresenter (unit 164), so under rule 1 it becomes the FloorMapSettings component in stroom.floormap.client.component, converted together with 164. The 'stays in stroom.floormap.client.view' suggestion is overridden.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapSettingsViewImpl.ui.xml:92 | `height="250px" on schemaGridContainer` | static | stroom-floormap-settings__schema-grid (new, stroom-floormap.css), as in unit 164 |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapSettingsViewImpl.java:63-67 | aria | the five FloorMapAria.group calls go once the slots are replaced by declared controls (see unit 164); FormGroup identity= then labels each control |
| FloorMapSettingsViewImpl.ui.xml:34,48,65,91,92 | component-declaration | slots replaced by DocSelectionBox x2, SelectionBox, Toolbar with ToolbarButtons and DataGrid (unit 164) |

**Stays in Java:** FloorMapSettingsViewImpl.java:130-133 the query fields' enabled state (read-only); Query values.

## `DocumentAssetPresenter`

tab, presenter; complexity 5, effort XL; used by 9.

- **Component:** `stroom.document.asset.client.component.DocumentAssetsTab`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetsTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetsTab.ui.xml`
- **Goes:** `DocumentAssetPresenter.DocumentAssetView`; `stroom-core-client/src/main/java/stroom/document/asset/client/view/DocumentAssetViewImpl.java`; `DocumentAssetModule.java:40-41 bindPresenterWidget(DocumentAssetPresenter, DocumentAssetView, DocumentAssetViewImpl)`
- **Names:** DocumentAsset clashes with the shared DTO stroom.document.asset.shared.DocumentAsset, and DocumentAssets with stroom.document.asset.shared.DocumentAssets. It is the Assets tab of nine document types, so DocumentAssetsTab + DocumentAssetsTabController (free). The generic <D> moves to the controller.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentAssetViewImpl.java:57-65 + DocumentAssetPresenter.java:102 | component-declaration | <a:DocumentAssetTree ui:field="tree" accessibleName="Assets"/> (wave 6, once it has a no-arg constructor and setAccessibleName) as the split pane's first child, instead of new DocumentAssetTree("Assets") and splitPane.add(tree) |
| DocumentAssetViewImpl.java:60-62 | structure | <g:FlowPanel styleName="max visualisationAssets-editorArea"> as the split pane's second child, instead of new FlowPanel built in setTreeAndEditor |
| DocumentAssetViewImpl.java:37,59 | text | <g:Label ui:field="editorPlaceholder" styleName="visualisationAssets-placeholder">Select a file to view or edit it</g:Label> inside the editor area |
| DocumentAssetPresenter.java:193 (make(editorPresenterProvider)) + DocumentAssetViewImpl.java:58,63 | component-declaration | <ed:CodeEditor ui:field="editor"/> (wave 6's dumb editor component, named CodeEditor in its notes; EditorPresenter becomes a wrapper) inside the editor area; the controller attaches the EditorController |
| DocumentAssetViewImpl.ui.xml:5 | css-class | visualisation-assets / visualisationAssets-* (components.css:2246-2256) are not BEM; optional rename to document-assets__* while moving |

**Stays in Java:** DocumentAssetPresenter.java:108-135,199 the 'Asset actions' toolbar and its five buttons: they go on the owning document's toolbar (getDocumentButtons), outside this component's root, so the component builds them in Java (or takes the document toolbar and adds them, as the pilot does) with one setOnAction(Consumer<AssetAction>); DocumentAssetPresenter.java:222-245 the Add menu items (shown in a popup menu); Editor text and mode, tree content, dirty/draft state, visibility of placeholder vs editor (DocumentAssetViewImpl.java:69-72); The add/edit/upload dialogs are opened by the controller, so not declared.

## `ProcessorTaskPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.processor.task.client.component.ProcessorFilterTasks`
- **Controller:** `stroom.processor.task.client.component.ProcessorFilterTasksController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/task/client/view/ProcessorTaskViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/task/client/component/ProcessorFilterTasks.ui.xml`
- **Goes:** `ProcessorTaskPresenter.ProcessorTaskView`; `stroom-core-client/src/main/java/stroom/processor/task/client/view/ProcessorTaskViewImpl.java`; PipelineModule.java:165-166 bindPresenterWidget(ProcessorTaskPresenter, ProcessorTaskView, ProcessorTaskViewImpl) and imports PipelineModule.java:94-96
- **Names:** ProcessorTask clashes with the shared DTO stroom.processor.shared.ProcessorTask (and the jOOQ table), and the other stroom.data.client.presenter.ProcessorTaskPresenter (wave 15) needs a name too. This one is the 'Tasks: Filter <id>' content tab for one processor filter, so ProcessorFilterTasks + ProcessorFilterTasksController (both free).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorTaskViewImpl.ui.xml:7 + ProcessorTaskPresenter.java (setTaskSummaryView) | component-declaration | <d:ProcessorTaskSummary ui:field="summary" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10, stroom.data.client.component) in place of the streamTaskSummary slot |
| ProcessorTaskViewImpl.ui.xml:8 + ProcessorTaskPresenter.java (setTaskListView) | component-declaration | <d:ProcessorTaskList ui:field="tasks" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) in place of the streamTaskList slot |

**Stays in Java:** Tab label "Tasks: Filter <id>" (getLabel, built from the filter id), icon and type: tab metadata in the controller; Expressions built from the selected summary row and the filter (controller).

## `AbstractDuplicateManagementPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.analytics.client.component.DuplicateManagement`
- **Controller:** `stroom.analytics.client.component.DuplicateManagementController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/DuplicateManagementViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/DuplicateManagement.ui.xml`
- **Goes:** `AbstractDuplicateManagementPresenter.java (merged into DuplicateManagementController: it has one subclass)`; `AnalyticDuplicateManagementPresenter.java (its onWrite moves into DuplicateManagementController, typed for AnalyticRuleDoc)`; `AbstractDuplicateManagementPresenter.DuplicateManagementView`; `stroom-core-client/src/main/java/stroom/analytics/client/view/DuplicateManagementViewImpl.java`; `AnalyticsModule.java:104-106 bindSharedView(DuplicateManagementView, DuplicateManagementViewImpl)`
- **Names:** The Abstract... base is merged, not renamed: AbstractDuplicateManagementPresenter + AnalyticDuplicateManagementPresenter become one DuplicateManagement + DuplicateManagementController (both free; distinct from wave 10's DuplicateManagementList). AnalyticRulePresenter moves with it.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| DuplicateManagementViewImpl.ui.xml:32 | `<g:TextBox ui:field="columns" width="100%"/>` | static | existing w-100 (stroom.css:843) |

| Into the template | What | Detail |
| --- | --- | --- |
| DuplicateManagementViewImpl.ui.xml:41-42 + AbstractDuplicateManagementPresenter (view.setListView) | component-declaration | <a:DuplicateManagementList ui:field="list" addStyleNames="dock-max dashboard-panel overflow-hidden duplicate-management__list"/> (wave 10) instead of the SimplePanel slot |
| DuplicateManagementViewImpl.java:54 | structure | columns.setEnabled(false) at construction -> enabled="false" on the TextBox (its later enabled state follows chooseColumns) |
| DuplicateManagementViewImpl.ui.xml:42 + components.css:1799-1802 | css-class | rename .DuplicateManagementViewImpl (named after the deleted view) to BEM duplicate-management__list in components.css |

**Stays in Java:** Tick box and columns values from the document, and the columns field's enabled state following Choose Columns; Read-only state.

## `ImportsTab`

tab, smart class; complexity 2, effort S; used by 1.

- **Component:** `stroom.dictionary.client.component.ImportsTab`
- **Controller:** `stroom.dictionary.client.component.ImportsTabController`
- **Template:** `stroom-core-client/src/main/java/stroom/dictionary/client/component/ImportsTab.ui.xml` → `stroom-core-client/src/main/java/stroom/dictionary/client/component/ImportsTab.ui.xml (stays; its Binder moves from the controller to the dumb ImportsTab)`
- **Goes:** `No view interface, UiHandlers or GIN line exists (pilot class, made just in time).`; The two SimplePanel slots importsPanel/wordsPanel (ImportsTab.ui.xml:13,19, ImportsTab.java:68-71,99-100) once DocRefList and EffectiveWordsList are declared
- **Names:** ImportsTab is already this class's own name (no clash in all_simple_names.json apart from itself); the current Controller becomes ImportsTabController and the dumb Component takes the name ImportsTab. No clash with other wave-12 units.

| Into the template | What | Detail |
| --- | --- | --- |
| ImportsTab.ui.xml:13 + ImportsTab.java:81,99 | component-declaration | <d:DocRefList ui:field="importList" addStyleNames="dock-max stroom-control"/> (wave 10 DocRefList, stroom.dictionary.client.component) replacing the importsPanel SimplePanel; the controller attaches DocRefListController to it |
| ImportsTab.ui.xml:19 + ImportsTab.java:80,100 | component-declaration | <d:EffectiveWordsList ui:field="effectiveWords" addStyleNames="dock-max stroom-control"/> (wave 10 component) replacing wordsPanel |
| ImportsTab.java:84 | text | column heading "Document Name" (initTableColumns) as an attribute of the declared DocRefList, e.g. columnName="Document Name", if wave 10 gives DocRefList that setter |
| ImportsTab.java:86-88 | aria | toolbar name "Dictionary actions" and the Add/Remove buttons (SvgImage.ADD/DELETE, titles ADD_TITLE/REMOVE_TITLE): if DocRefList's template accepts toolbar children, declare <b:ToolbarButton ui:field="add" svg="ADD" title="Add Imported Dictionary"/> etc.; otherwise ImportsTab is given the list's toolbar and adds them (the pilot's action-enum pattern, setOnAction(Consumer<ImportsAction>)) |
| ImportsTab.ui.xml:6,18 | css-class | DictionarySettingViewImpl / DictionarySettingViewImpl-words (components.css:1824) are named after a deleted view: rename to BEM dictionary-imports / dictionary-imports__words when the template moves to the component |

**Stays in Java:** ImportsTab.java:120 table name "Dictionary '<name>' Imports" (built from the document name); ImportsTab.java:179-188 button enabled state and the "(dictionary is read only)" title suffix (read-only state); ImportsTab.java:145-147 DocSelectionPopup caption/types/permission: a popup the controller opens with makeDialog, so it is not declared in the template (its "declares" entry is wrong); ImportsTab.java:160-162 confirm text (chosen by selection count).

## `AnnotationLinkPresenter`

tab, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.annotation.client.component.AnnotationLinks`
- **Controller:** `stroom.annotation.client.component.AnnotationLinksController`
- **Template:** `none (uses the shared FindViewImpl template, stroom-core-client/src/main/resources/stroom/explorer/client/view/FindViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationLinks.ui.xml`
- **Goes:** AnnotationLinkPresenter.java (no GIN binding of its own; the shared AnnotationModule.java:88 bindSharedView(FindView, FindViewImpl) goes when BrowseAnnotation and FindAnnotation move too); `FindUiHandlers implementation on it (view.setUiHandlers(this), AnnotationLinkPresenter.java:68)`
- **Names:** AnnotationLink clashes with the jOOQ table stroom.annotation.impl.db.jooq.tables.AnnotationLink, so AnnotationLinks + AnnotationLinksController (free; it lists the links to or from an annotation, two instances).

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationLinkPresenter.java:56,66-67 | component-declaration | <e:Find ui:field="find" dialogMode="false"> (wave 11's component from FindViewImpl, stroom.explorer.client.component) holding <a:FindAnnotationList ui:field="list"/> (wave 10) as its result widget, instead of the injected FindView and setResultView |
| AnnotationLinkPresenter.java:71-78 | aria | toolbar name "Link actions" and Add/Remove Annotation Link (ADD, DELETE): one LinkAction enum with setOnAction/setAvailableActions on the list's toolbar, or ToolbarButton children if the list takes them |

**Stays in Java:** Visibility of Add/Remove (only on the 'to' instance) and enabled state (read-only, selection), AnnotationLinkPresenter.java:156-161; Source/destination id from the document, quick filter and REST link/unlink (controller); Button is not used ("declares" entry wrong).

## `ScriptSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.script.client.component.ScriptSettings`
- **Controller:** `stroom.script.client.component.ScriptSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/script/client/view/ScriptSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/script/client/component/ScriptSettings.ui.xml`
- **Goes:** `ScriptSettingsPresenter.ScriptSettingsView`; `stroom-core-client/src/main/java/stroom/script/client/view/ScriptSettingsViewImpl.java`; `ScriptModule.java:37 bindPresenterWidget(ScriptSettingsPresenter, ScriptSettingsView, ScriptSettingsViewImpl)`
- **Names:** ScriptSettings / ScriptSettingsController: free. The review suggests folding it into the dependency list; keeping the pair is simpler because the form frame (padding, FormGroup with help) is this template's, and the controller is a few lines (read/write pass-through).

| Into the template | What | Detail |
| --- | --- | --- |
| ScriptSettingsViewImpl.ui.xml:10 + ScriptSettingsPresenter (view.setDependencyList) | component-declaration | <s:ScriptDependencyList ui:field="dependencies" addStyleNames="dock-max stroom-control"/> (wave 10) instead of the SimplePanel slot; FormGroup already declared |

**Stays in Java:** read/write of the dependencies and the onChange callback (controller).

## `FloorMapSettingsPresenter`

tab, presenter; complexity 4, effort L; used by 3.

**Since the merge.** Renames only (DataGrid, DataGridSelectionEventManager). TextFieldCell (still a TextInputCell) is unchanged here; the new TextField widget is not used. Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapSettings`
- **Controller:** `stroom.floormap.client.component.FloorMapSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapSettings.ui.xml`
- **Goes:** `FloorMapSettingsPresenter.FloorMapSettingsView (FloorMapSettingsPresenter.java:630-672)`; stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapSettingsViewImpl.java (becomes the FloorMapSettings component: unit 740 is the same conversion); `FloorMapModule.java:120-122 bindPresenterWidget(FloorMapSettingsPresenter, FloorMapSettingsView, FloorMapSettingsViewImpl) and its imports`; `DirtyUiHandlers use on this view (view.setUiHandlers, FloorMapSettingsPresenter.java:136): replaced by setOnChange(Runnable)`
- **Names:** FloorMapSettings and FloorMapSettingsController are free in all_simple_names.json and in wave 12. Unit 740 (FloorMapSettingsViewImpl) is not a separate widget: it is this unit's view and becomes this same FloorMapSettings component, so the two are converted together.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapSettingsViewImpl.ui.xml:92 | `<g:SimplePanel ui:field="schemaGridContainer" height="250px"/>` | static | new class stroom-floormap-settings__schema-grid { height: 250px; } in stroom-floormap.css (beside .stroom-floormap-settings-section, stroom-floormap.css:463), on the declared DataGrid |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapSettingsPresenter.java:138-148 + FloorMapSettingsViewImpl.ui.xml:34,48 | component-declaration | <dd:DropDownBox ui:field="eventsStoreBox"/> and <dd:DropDownBox ui:field="factsStoreBox"/> (wave 8, stroom.explorer.client.component) in place of the two SimplePanel slots; the controller attaches two DocSelectionBoxControllers (make(provider).attach(view.getEventsStoreBox())) |
| FloorMapSettingsPresenter.java:139,145 | text | captions "Choose Events Store" / "Choose Facts Store" as caption= attributes on the declared boxes if DocSelectionBox takes one, else on the controllers |
| FloorMapSettingsPresenter.java:152-156 + ViewImpl.ui.xml:65 | component-declaration | <i:SelectionBox ui:field="valueFormat"/> (wave 2) instead of new SelectionBox in Java and the valueFormatContainer slot; its items (ValueFormat.values()) are added in the component's constructor |
| FloorMapSettingsPresenter.java:159-160,170 + ViewImpl.ui.xml:92 | component-declaration | <dg:DataGrid ui:field="schemaGrid" tableName="Value Schema" addStyleNames="stroom-floormap-settings__schema-grid"/> (stroom.widget.datagrid.client.DataGrid, once wave 5 gives it a no-arg constructor) instead of new DataGrid<>(this) and schemaGridContainer |
| FloorMapSettingsPresenter.java:172-175 + ViewImpl.ui.xml:91 | component-declaration | <b:Toolbar name="Field mapping actions"><b:ToolbarButton ui:field="addMapping" svg="ADD" title="Add Field Mapping"/><b:ToolbarButton ui:field="removeMapping" svg="DELETE" title="Remove Field Mapping" enabled="false"/></b:Toolbar> instead of new Toolbar and schemaToolbarContainer |
| FloorMapSettingsViewImpl.java:63-67 | aria | FloorMapAria.group(...) role=group/aria-label on the five slots goes: with the boxes, selection box and grid declared, each FormGroup gets identity= pointing at its real control (FormGroup label association), and the toolbar is named by its name= attribute |
| FloorMapSettingsPresenter.java:254,269,286,303 (addColumn headings) | text | No: column headings are grid configuration in Java (cells are data); listed only so they are not mistaken for template text |

**Stays in Java:** FloorMapSettingsPresenter.java:233-320 schema columns, SelectionBoxCell/TextFieldCell and their per-row accessible names (schemaCellLabel, built from row data); FloorMapSettingsPresenter.java:356-436 focus restore after redraw and the getActiveElement JSNI (controller or component behaviour, not markup); FloorMapSettingsPresenter.java:505-560 values read from the document (store refs, queries with defaults, value format, mappings), enabled state from read-only; FloorMapSettingsPresenter.java:533 add/remove button enabled state.

## `UserAndGroupsPresenter`

tab, presenter; complexity 4, effort L; used by 4.

- **Component:** `stroom.security.client.component.UserAndGroups`
- **Controller:** `stroom.security.client.component.UserAndGroupsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/UserAndGroupsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/UserAndGroups.ui.xml`
- **Goes:** `UserAndGroupsPresenter.UserAndGroupsView`; `stroom-core-client/src/main/java/stroom/security/client/view/UserAndGroupsViewImpl.java`; `SecurityModule.java:123-126 bindPresenterWidget(UserAndGroupsPresenter, UserAndGroupsView, UserAndGroupsViewImpl)`
- **Names:** UserAndGroups / UserAndGroupsController: free in all_simple_names.json and wave 12.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| UserAndGroupsViewImpl.java:92-94 | `pane.getElement().getStyle().setOpacity(enabled ? 1 : 0.4)` | static | two fixed values chosen by state, so a state rule rather than a custom property: new class user-and-groups__pane on the three panes in the template, with .user-and-groups__pane[inert] { opacity: var(--disabled-opacity); } (--disabled-opacity is 0.4, theme-root.css:22) in components.css; Java keeps only the inert attribute (UserAndGroupsViewImpl.java:95-99) |

| Into the template | What | Detail |
| --- | --- | --- |
| UserAndGroupsViewImpl.ui.xml:15-16 + UserAndGroupsPresenter.java:86,123 | component-declaration | <s:UserList ui:field="userList" addStyleNames="max user-and-groups__pane"/> (wave 10) in place of the userList slot |
| UserAndGroupsViewImpl.ui.xml:22-25 + UserAndGroupsPresenter.java:103,108,124 | component-declaration | <s:UserList ui:field="parentsList" .../> and <s:UserList ui:field="childrenList" .../> in place of the parents/children slots |
| UserAndGroupsPresenter.java:88 | text | label="Users and Groups:" (UsersAndGroupsPlugin.SCREEN_NAME + ":") on the top UserList, if wave 10 gives UserList a label attribute |
| UserAndGroupsPresenter.java:89-96 | text | the top list's fixed help text ("Lists all users and user groups...") as a HelpHTML child or helpText= on the declared UserList |
| UserAndGroupsPresenter.java:90,100,105,110 (setMode) | structure | mode="USERS_AND_GROUPS" / mode="GROUPS_ONLY" on the three declared lists if UserList's mode becomes a component setter (else the controllers keep it) |
| UserAndGroupsPresenter.java:125-155 | aria | toolbar names ("User and group actions", "Member of actions", "Members actions") and the seven buttons with their icons and default titles: declared as ToolbarButtons in each UserList if its template takes toolbar children, else added by the component with one setOnAction(Consumer<UserAndGroupsAction>) and setAvailableActions |

**Stays in Java:** UserAndGroupsPresenter.java:233,268,285,323-324 list labels built from the selected user/group ("User \"x\" is a member of:", "Members of ...", "No Selection") and their help text; UserAndGroupsPresenter.java:135,309-334 button titles built from the selection or config ("Edit <group>", "Create User Or Group"); UserAndGroupsViewImpl.java:95-99 inert on a pane waiting for a selection, and setPaneHidden/visibility (UserAndGroupsViewImpl.java:61-64,103-107); CreateNewGroup, CreateUser and UserRefPopup are dialogs opened by the controller, so not declared (their "declares" entries are wrong).

## `ElasticClusterSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.search.elastic.client.component.ElasticClusterSettings`
- **Controller:** `stroom.search.elastic.client.component.ElasticClusterSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/elastic/client/view/ElasticClusterSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/elastic/client/component/ElasticClusterSettings.ui.xml`
- **Goes:** `ElasticClusterSettingsPresenter.ElasticClusterSettingsView`; `stroom-core-client/src/main/java/stroom/search/elastic/client/view/ElasticClusterSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/search/elastic/client/presenter/ElasticClusterSettingsUiHandlers.java`; ElasticClusterModule.java:31-33 bindPresenterWidget(ElasticClusterSettingsPresenter, ElasticClusterSettingsView, ElasticClusterSettingsViewImpl)
- **Names:** ElasticClusterSettings / ElasticClusterSettingsController: no clash in all_simple_names.json or wave 12.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ElasticClusterSettingsViewImpl.ui.xml:11 | `<g:TextArea ui:field="connectionUrls" width="400px"/>` | static | new modifier text-field--wide { width: 400px; } in text-field.css (shared, see cssNotes) |
| ElasticClusterSettingsViewImpl.ui.xml:19 | `<b:Button ui:field="setHttpClientConfig" ... width="200px">` | static | drop it: form.css '.form > .Button' already gives a form's button min-width: 200px (check the screenshot) |
| ElasticClusterSettingsViewImpl.ui.xml:21 | `<b:Button ui:field="testConnection" ... width="200px">` | static | drop it (form.css '.form > .Button' min-width 200px) |

| Into the template | What | Detail |
| --- | --- | --- |
| ElasticClusterSettingsViewImpl.java:57 | structure | testConnection.setIcon(SvgImage.OK) -> icon="OK" on <b:Button ui:field="testConnection"> (SvgImage is an enum, so UiBinder parses it) |
| ElasticClusterSettingsViewImpl.ui.xml:17 | component-declaration | <l:SelectionBox ui:field="apiKey"/> already declared (wave 2); Button and FormGroup already declared. HttpClientConfigPresenter is a dialog the controller opens (makeDialog), so it is not declared (its "declares" entry is wrong) |

**Stays in Java:** ElasticClusterSettingsPresenter.java CredentialListModel set on the api key box (data source, controller); ElasticClusterSettingsViewImpl.java:75 connection URLs text (document data); read-only state of the fields (onReadOnly); test connection alerts (text built from the server's result).

## `FeedDependencyPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.processor.client.component.FeedDependenciesEditor`
- **Controller:** `stroom.processor.client.component.FeedDependenciesEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/FeedDependencyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/FeedDependenciesEditor.ui.xml`
- **Goes:** `FeedDependencyPresenter.FeedDependencyView`; `stroom-core-client/src/main/java/stroom/processor/client/view/FeedDependencyViewImpl.java`; `PipelineModule.java:147-150 bindPresenterWidget(FeedDependencyPresenter, FeedDependencyView, FeedDependencyViewImpl)`
- **Names:** FeedDependency clashes with the shared DTO stroom.processor.shared.FeedDependency, and FeedDependencies with stroom.processor.shared.FeedDependencies (what the dialog edits). So FeedDependenciesEditor + FeedDependenciesEditorController (free), named like the pilot's FieldEditor; the dialog shows itself with edit(owner, value, updated -> ...).

| Into the template | What | Detail |
| --- | --- | --- |
| FeedDependencyViewImpl.ui.xml:12 + FeedDependencyPresenter (view.setFeedDependencyList) | component-declaration | <p:FeedDependencyList ui:field="feedDependencies" addStyleNames="max"/> (wave 10) instead of the SimplePanel slot |
| FeedDependencyViewImpl.ui.xml:17-34 | component-declaration | TickBox (wave 2) and DurationPicker (wave 2) are already declared; nothing more to move |

**Stays in Java:** FeedDependencyViewImpl.java:69-110 the delay values, their defaults (30 minutes, 1 day) when none is set, and the pickers' enabled state following their tick boxes; FeedDependencyPresenter.java dialog caption "Set Feed Dependencies", size and OK/Cancel (the controller shows the dialog).

## `GitRepoSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.gitrepo.client.component.GitRepoSettings`
- **Controller:** `stroom.gitrepo.client.component.GitRepoSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/gitrepo/client/view/GitRepoSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/gitrepo/client/component/GitRepoSettings.ui.xml`
- **Goes:** `GitRepoSettingsPresenter.GitRepoSettingsView`; `stroom-core-client/src/main/java/stroom/gitrepo/client/view/GitRepoSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/gitrepo/client/presenter/GitRepoSettingsUiHandlers.java`; `GitRepoModule.java:35 bindPresenterWidget(GitRepoSettingsPresenter, GitRepoSettingsView, GitRepoSettingsViewImpl)`
- **Names:** GitRepoSettings / GitRepoSettingsController: free in all_simple_names.json and wave 12.

| Into the template | What | Detail |
| --- | --- | --- |
| GitRepoSettingsViewImpl.ui.xml:16,23 | text | remove the 'Lorem ipsum' placeholder text in the two Labels (always replaced at runtime by setContentStoreName/setContentPackName) |
| GitRepoSettingsViewImpl.ui.xml:74-77 | structure | remove the commented-out credentials Button |
| GitRepoSettingsViewImpl.ui.xml:8 + components.css:2005-2016 | css-class | the form's button and field widths already come from .gitrepo-settings-form rules (no inline styles); keep the class |
| GitRepoSettingsViewImpl.ui.xml:73 | component-declaration | SelectionBox, TickBox, Button, FormGroup are already declared; HttpClientConfig and GitRepoCommitDialog are dialogs the controller opens, so not declared |

**Stays in Java:** GitRepoSettingsViewImpl.java:101-146 values from the document; GitRepoSettingsViewImpl.java:183-247 content-pack vs plain repository visibility/enabled state, and the push controls' titles switching between AUTO_PUSH_TITLE/PUSH_TITLE and the reason pushing is unavailable (state). The default titles in the template stay as they are; Read-only state (GitRepoSettingsViewImpl.java:169-174); Push/pull/check-for-updates REST flows and alerts (controller).

## `AnnotationEditPresenter`

tab, presenter; complexity 5, effort XL; used by 3.

**Since the merge.** Copying the history now uses Clipboard.writeText: the hidden aria-hidden TextArea built in Java and the JSNI execCommand copy are gone (so 'JSNI' is no longer a smart reason). Slightly less to convert.

- **Component:** `stroom.annotation.client.component.AnnotationEdit`
- **Controller:** `stroom.annotation.client.component.AnnotationEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/AnnotationEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationEdit.ui.xml`
- **Goes:** `AnnotationEditPresenter.AnnotationEditView (AnnotationEditPresenter.java:1660-1708)`; `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationEditUiHandlers.java`; `AnnotationModule.java:73 bindPresenterWidget(AnnotationEditPresenter, AnnotationEditView, AnnotationEditViewImpl)`
- **Names:** AnnotationEdit / AnnotationEditController: free. copy_and_paste has merged, so the clipboard code it touched (Clipboard.writeText) is current.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| AnnotationEditViewImpl.ui.xml:32-33 | `<b:Button ui:field="commentButton" ... width="200px"/>` | static | new rule .annotationButtons > .Button { width: 200px; } in stroom-annotation.css (beside .annotationButtons, stroom-annotation.css:176), as .annotationHistoryCopyButton already does (stroom-annotation.css:322-327) |
| AnnotationEditViewImpl.ui.xml:34-35 | `<b:Button ui:field="create" ... width="200px"/>` | static | .annotationButtons > .Button (new, stroom-annotation.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationEditPresenter.java:550-551,615-620 + AnnotationEditViewImpl.ui.xml:25 | structure | the history frame in the template instead of the history SimplePanel and Java-built panels: <g:FlowPanel styleName="max dock-container-vertical annotationHistoryContainer"><g:HTML ui:field="historyHtml" styleName="dock-max annotationHistoryOuter"/><b:Button ui:field="copyHistory" icon="COPY" text="Copy History" addStyleNames="dock-min allow-focus annotationHistoryCopyButton"/></g:FlowPanel>; its key/mouse handlers are added once, not on every update |
| AnnotationEditPresenter.java:605-608 | text | Copy History button text and icon (above) |
| AnnotationEditViewImpl.java:107-108 | structure | create.setIcon(ADD), delete.setIcon(DELETE) -> icon="ADD"/icon="DELETE" on the Buttons |
| AnnotationEditViewImpl.java:101-102 | text | placeholders "Title"/"Subject" on the two TextBoxes: needs a placeholder setter on the text box (see cssNotes: Stroom's TextBox copy is in the protected com/ directory) |
| AnnotationEditViewImpl.java:105-106 | aria | AccessibleName.set(..., "Title"/"Subject") -> an accessibleName/ariaLabel attribute on the TextBoxes, same blocker as the placeholders |

**Stays in Java:** History SafeHtml (entries, groups, aria-labels "Event n"/"Annotation n", entry action buttons with their names: built from data, AnnotationEditPresenter.java:876-910,1385-1398); AnnotationEditViewImpl.java:117 "#<id>", 126-211 title/subject/status/assigned/labels/collections/retention values and the annotationLabel--empty state class with "None"/"Nobody"; AnnotationEditViewImpl.java:232 create button text (changes with the comment state); The setting choosers (ChooserPresenter, MultiChooserPresenter, UserRefPopupPresenter, DurationPresenter) and CommentEdit are popups opened from the setting blocks, so they are made by the controller, not declared; their accessible names ("Statuses", "Labels", "Collections", "Standard comments", AnnotationEditPresenter.java:225-252) stay with the controller. SettingBlock and AssignYourselfLink are already in the template.

## `PipelineStructurePresenter`

tab, presenter; complexity 5, effort XL; used by 3.

- **Component:** `stroom.pipeline.structure.client.component.PipelineStructure`
- **Controller:** `stroom.pipeline.structure.client.component.PipelineStructureController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/structure/client/view/PipelineStructureViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/structure/client/component/PipelineStructure.ui.xml`
- **Goes:** `PipelineStructurePresenter.PipelineStructureView`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/view/PipelineStructureViewImpl.java`; `stroom-core-client/src/main/java/stroom/pipeline/structure/client/presenter/PipelineStructureUiHandlers.java`; `PipelineModule.java:123-124 bindPresenterWidget(PipelineStructurePresenter, PipelineStructureView, PipelineStructureViewImpl)`
- **Names:** PipelineStructure / PipelineStructureController: free in all_simple_names.json and wave 12.

| Into the template | What | Detail |
| --- | --- | --- |
| PipelineStructureViewImpl.ui.xml:21 + PipelineStructurePresenter.java:125 | component-declaration | <dd:DropDownBox ui:field="inherit"/> (wave 8) instead of the inherit SimplePanel; the controller attaches a DocSelectionBoxController |
| PipelineStructureViewImpl.ui.xml:27-28 + PipelineStructurePresenter.java:126 | component-declaration | <ps:PipelineTree ui:field="tree"/> (wave 7) inside the treeContainer MaxScrollPanel instead of setTreeView |
| PipelineStructureViewImpl.ui.xml:33-34 + PipelineStructurePresenter.java:127 | component-declaration | <ps:PropertyList ui:field="properties" addStyleNames="dashboard-panel"/> (wave 10) instead of the properties slot |
| PipelineStructureViewImpl.ui.xml:35-36 + PipelineStructurePresenter.java:128 | component-declaration | <ps:PipelineReferenceList ui:field="pipelineReferences" addStyleNames="dashboard-panel"/> (wave 10) instead of the slot |
| PipelineStructureViewImpl.ui.xml:9-25 | css-class | the toolbar and Inherit From label are already in the template; the pipelineStructureViewImpl-* classes (components.css:198-252) are named after the deleted view: optional BEM rename to pipeline-structure__* |

**Stays in Java:** Toolbar buttons' enabled state (PipelineStructurePresenter.java:660-663) and the Add/Restore menus (data-driven menu items, lines 370-417); PipelineStructurePresenter.java:588 'form-control-border default-min-sizes' on the View Source editor: that editor is a popup dialog (makeDialog), so the class belongs to the dialog's content, not this template; it stays in Java until the source dialog gets a component of its own; NewElement and the source editor are dialogs, opened by the controller, not declared.

## `OpenAIModelSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.openai.client.component.OpenAIModelSettings`
- **Controller:** `stroom.openai.client.component.OpenAIModelSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/openai/client/view/OpenAIModelSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/openai/client/component/OpenAIModelSettings.ui.xml`
- **Goes:** `OpenAIModelSettingsPresenter.OpenAIModelSettingsView`; `stroom-core-client/src/main/java/stroom/openai/client/view/OpenAIModelSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/openai/client/presenter/OpenAIModelSettingsUiHandlers.java`; `OpenAIModelModule.java:32-34 bindPresenterWidget(OpenAIModelSettingsPresenter, OpenAIModelSettingsView, OpenAIModelSettingsViewImpl)`
- **Names:** OpenAIModelSettings / OpenAIModelSettingsController: free in all_simple_names.json and wave 12.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| OpenAIModelSettingsViewImpl.ui.xml:13 | `<g:TextBox ui:field="baseUrl" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| OpenAIModelSettingsViewImpl.ui.xml:26 | `<g:TextBox ui:field="modelId" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| OpenAIModelSettingsViewImpl.ui.xml:40 | `<g:TextBox ui:field="reasoningEffort" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| OpenAIModelSettingsViewImpl.ui.xml:33 | `<v:ValueSpinner ui:field="maxContextWindowTokens" width="80px"/>` | static | new modifier value-spinner--narrow { width: 80px; } in value-spinner.css (beside .value-spinner, value-spinner.css:29; 4 templates use width="80px") |
| OpenAIModelSettingsViewImpl.ui.xml:47 | `<v:ValueSpinner ui:field="embeddingModelDimensions" width="80px"/>` | static | value-spinner--narrow (new, value-spinner.css) |
| OpenAIModelSettingsViewImpl.ui.xml:50 | `<b:Button ui:field="setHttpClientConfig" ... width="200px">` | static | drop it: form.css '.form > .Button' min-width 200px |
| OpenAIModelSettingsViewImpl.ui.xml:52 | `<b:Button ui:field="testModel" ... width="200px">` | static | drop it: form.css '.form > .Button' min-width 200px |

| Into the template | What | Detail |
| --- | --- | --- |
| OpenAIModelSettingsViewImpl.java:63 | structure | testModel.setIcon(SvgImage.OK) -> icon="OK" on the Button |
| OpenAIModelSettingsViewImpl.java:65-68 | structure | min="0" max="2147483647" on the two ValueSpinners, if UiBinder parses long attributes (setMin/setMax take long and no template sets them yet); else they stay in the component's constructor |
| OpenAIModelSettingsViewImpl.ui.xml:20 | component-declaration | SelectionBox, ValueSpinner, Button and FormGroup already declared; HttpClientConfig is a dialog the controller opens, not declared |

**Stays in Java:** Field values from the document, credential list model, read-only state; Test model REST call and its alerts (controller).

## `AbstractNotificationPresenter`

tab, presenter; complexity 1, effort S; used by 4.

- **Component:** `none: removed. The documents add the wave-10 notification list component (NotificationList, stroom.analytics.client.component) as their Notifications tab directly`
- **Controller:** `none: AnalyticNotificationPresenter/ReportNotificationPresenter's onWrite (languageVersion STROOM_QL_VERSION_0_1) moves into the wave-10 AnalyticNotificationList/ReportNotificationList controllers or the documents' write`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticNotificationViewImpl.ui.xml` → `none (built in Java because the tab is only a frame: AnalyticNotificationViewImpl.ui.xml's 'max dock-container-vertical' root with a 'dock-max' slot adds nothing the list's own root template cannot have)`
- **Goes:** `AbstractNotificationPresenter.java`; `AnalyticNotificationPresenter.java`; `ReportNotificationPresenter.java`; `AbstractNotificationPresenter.AnalyticNotificationView`; `stroom-core-client/src/main/java/stroom/analytics/client/view/AnalyticNotificationViewImpl.java and its template`; `AnalyticsModule.java:101-103 bindSharedView(AnalyticNotificationView, AnalyticNotificationViewImpl)`
- **Names:** If a pair is kept instead, AbstractNotification merges into one Notifications tab, but Notifications clashes with stroom.gwt.workbench.framework.client.manager.Notifications; use AnalyticNotifications + AnalyticNotificationsController (free). Recommended: remove the wrapper, as the review's note says.

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticNotificationViewImpl.ui.xml:4-5 | css-class | 'max dock-container-vertical' / 'dock-max' move onto the notification list component's own root in its template |

**Stays in Java:** setAnalyticProcessType passes from AnalyticProcessing's setOnProcessingTypeChange straight to the list controller (AnalyticRulePresenter/ReportPresenter wiring).

## `PlanBSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.planb.client.component.PlanBSettings`
- **Controller:** `stroom.planb.client.component.PlanBSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/planb/client/view/PlanBSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/planb/client/component/PlanBSettings.ui.xml`
- **Goes:** `PlanBSettingsPresenter.PlanBSettingsView`; `stroom-core-client/src/main/java/stroom/planb/client/view/PlanBSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/planb/client/presenter/StateTypeChangeUiHandlers.java`; `PlanBModule.java:58-60 bindPresenterWidget(PlanBSettingsPresenter, PlanBSettingsView, PlanBSettingsViewImpl)`
- **Names:** PlanBSettings / PlanBSettingsController: free (the shared settings DTOs are AbstractPlanBSettings and the *Settings classes, not PlanBSettings). Used by the PlanB document and unit 760 (TracesSettings): convert 685 first.

| Into the template | What | Detail |
| --- | --- | --- |
| PlanBSettingsViewImpl.java:52-59 | structure | the seven StateType items and the TEMPORAL_STATE default: fixed, added in the component's constructor (SelectionBox takes no item children) |
| PlanBSettingsViewImpl.ui.xml:13 + PlanBSettingsPresenter.changeStateType (make(...Provider) per type) | component-declaration | declare the type settings parts (wave 7, stroom.planb.client.component) in the settings area instead of the SimplePanel slot: <p:StateSettings ui:field="state"/>, TemporalStateSettings, RangeStateSettings, TemporalRangeStateSettings, SessionSettings, HistogramSettings, MetricSettings, TraceSettings; the component shows the one for the state type (setVisible) and the controller attaches each controller when first shown; the common values (max store size, synchronise merge, overwrite, condense, retention) carried across a type change become plain fields in the controller rather than instanceof reads of views |

**Stays in Java:** Selected state type, the State Type group's visibility (locked for Traces), read-only state; Values read from and written to the document's settings.

## `ScheduledProcessingPresenter`

composite, presenter; complexity 3, effort M; used by 8.

- **Component:** `stroom.analytics.client.component.ScheduledProcessing`
- **Controller:** `stroom.analytics.client.component.ScheduledProcessingController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/ScheduledProcessingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessing.ui.xml`
- **Goes:** `ScheduledProcessingPresenter.ScheduledProcessingView`; `stroom-core-client/src/main/java/stroom/analytics/client/view/ScheduledProcessingViewImpl.java`; `AnalyticsModule.java:88-90 bindPresenterWidget(ScheduledProcessingPresenter, ScheduledProcessingView, ScheduledProcessingViewImpl)`
- **Names:** ScheduledProcessing / ScheduledProcessingController: free. Fan-in 8 (DataGen and other documents' processing tabs): convert it before units 360 and 505 in this wave.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduledProcessingViewImpl.ui.xml:6 | component-declaration | <a:ScheduledProcessList ui:field="scheduleList" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) instead of the executionScheduleList slot |
| ScheduledProcessingViewImpl.ui.xml:7 | component-declaration | <a:ScheduledProcessHistoryList ui:field="historyList" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) instead of the executionHistoryList slot |
| ScheduledProcessingViewImpl.ui.xml:5 + components.css:1799 | css-class | rename .ScheduledProcessingViewImpl (named after the deleted view, shares a rule with DuplicateManagementViewImpl) to BEM scheduled-processing |

**Stays in Java:** New/replayed schedule values ("Daily", cron, bounds) built in the controller; Read-only (now from context), the dirty check before adding (documentEditPresenter becomes the document's dirty context) and the REST calls; ScheduledProcessEdit is the edit dialog, opened by the controller, so not declared.

## `PathwaysSplitPresenter`

tab, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.pathways.client.component.PathwaysSplit`
- **Controller:** `stroom.pathways.client.component.PathwaysSplitController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/PathwaysSplitViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwaysSplit.ui.xml`
- **Goes:** `PathwaysSplitPresenter.PathwaysSplitView`; `stroom-core-client/src/main/java/stroom/pathways/client/view/PathwaysSplitViewImpl.java`; PathwaysModule.java:61-63 bindPresenterWidget(PathwaysSplitPresenter, PathwaysSplitView, PathwaysSplitViewImpl) and import PathwaysModule.java:33
- **Names:** PathwaysSplit / PathwaysSplitController: free.

| Into the template | What | Detail |
| --- | --- | --- |
| PathwaysSplitViewImpl.ui.xml:7 + PathwaysSplitPresenter (view.setTable) | component-declaration | <pw:PathwayList ui:field="pathwayList" addStyleNames="max"/> (wave 10) instead of the dataGrid slot |
| PathwaysSplitViewImpl.ui.xml:8 + PathwaysSplitPresenter (view.setTree) | component-declaration | <pw:PathwayTree ui:field="pathwayTree" addStyleNames="max"/> (wave 7) instead of the tree slot |

**Stays in Java:** Selected pathway passed to the tree, read/write of the document (controller).

## `TracesListTabPresenter`

tab, presenter; complexity 3, effort M; used by 3.

**Since the merge.** No longer injects DefaultResources; makes TraceOverviewWidget with only the HasHandlers. Nothing else material.

- **Component:** `stroom.pathways.client.component.TracesListTab`
- **Controller:** `stroom.pathways.client.component.TracesListTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/TracesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesListTab.ui.xml`
- **Goes:** `TracesListTabPresenter.TracesView`; `stroom-core-client/src/main/java/stroom/pathways/client/view/TracesViewImpl.java`; `PathwaysModule.java:71-73 bindPresenterWidget(TracesListTabPresenter, TracesView, TracesViewImpl) and import PathwaysModule.java:35`
- **Names:** TracesListTab / TracesListTabController: free (the view was named TracesViewImpl; the template is renamed after the component).

| Into the template | What | Detail |
| --- | --- | --- |
| TracesViewImpl.ui.xml:8 + TracesListTabPresenter (view.setTopWidget) | component-declaration | <pw:TracesList ui:field="tracesList" addStyleNames="max"/> (wave 10) instead of topWidget |
| TracesListTabPresenter.java:72 + TracesViewImpl.ui.xml:9 | component-declaration | <pp:TraceOverviewWidget ui:field="traceOverview" addStyleNames="max"/> (wave 6, stroom.pathways.client.presenter, once it has a no-arg constructor) instead of new TraceOverviewWidget(this) and bottomWidget |

**Stays in Java:** TracesListTabPresenter.java:59-60,74 the time range selector in a 'traces-toolbar' FlowPanel: it goes on the document's toolbar row (HasToolbar.getToolbars), outside this component's root, so it is built in Java (TimeRangeSelector has a no-arg constructor, wave 4) or given its own small template; it is not inline style; Time range values, selection-driven trace loading and paging (controller).

## `TracesSettingsPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.pathways.client.component.TracesSettings`
- **Controller:** `stroom.pathways.client.component.TracesSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/TracesSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/TracesSettings.ui.xml`
- **Goes:** `TracesSettingsPresenter.TracesSettingsView`; `stroom-core-client/src/main/java/stroom/pathways/client/view/TracesSettingsViewImpl.java`; `PathwaysModule.java:68-70 bindPresenterWidget(TracesSettingsPresenter, TracesSettingsView, TracesSettingsViewImpl)`
- **Names:** TracesSettings / TracesSettingsController: free. Wraps unit 685 (PlanBSettings): convert 685 first. Alternatively the Traces document adds PlanBSettings directly with the type locked, and this pair goes.

| Into the template | What | Detail |
| --- | --- | --- |
| TracesSettingsViewImpl.ui.xml:6 + TracesSettingsPresenter (view.setSettingsView) | component-declaration | <pb:PlanBSettings ui:field="settings" stateTypeLocked="true"/> (unit 685) instead of the SimplePanel slot; stateTypeLocked is fixed for Traces, so a template attribute if PlanBSettings gets that setter |
| TracesSettingsViewImpl.ui.xml:4 | structure | remove the unused xmlns:sg namespace |

**Stays in Java:** StateType.TRACE read/write, shared file store lock (from the document).

## `DataGenProcessingPresenter`

tab, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.datagen.client.component.DataGenProcessing`
- **Controller:** `stroom.datagen.client.component.DataGenProcessingController`
- **Template:** `stroom-core-client/src/main/resources/stroom/datagen/client/view/DataGenProcessingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/datagen/client/component/DataGenProcessing.ui.xml`
- **Goes:** `DataGenProcessingPresenter.DataGenProcessingView`; `stroom-core-client/src/main/java/stroom/datagen/client/view/DataGenProcessingViewImpl.java`; `DataGenModule.java:43-45 bindPresenterWidget(DataGenProcessingPresenter, DataGenProcessingView, DataGenProcessingViewImpl)`
- **Names:** DataGenProcessing / DataGenProcessingController: free. Alternative: drop the pair and add the ScheduledProcessing component (unit 521) to the DataGen document as its Execution tab directly, since this tab writes nothing (onWrite returns the doc).

| Into the template | What | Detail |
| --- | --- | --- |
| DataGenProcessingViewImpl.ui.xml:6 + DataGenProcessingPresenter (view.setProcessSettings) | component-declaration | <a:ScheduledProcessing ui:field="processSettings" addStyleNames="dock-max"/> (unit 521, same wave: convert 521 first) instead of the SimplePanel slot |

**Stays in Java:** read(docRef) passed to the ScheduledProcessingController; setDocumentEditPresenter becomes the document's dirty context.

## `SolrIndexSettingsPresenter`

tab, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.search.solr.client.component.SolrIndexSettings`
- **Controller:** `stroom.search.solr.client.component.SolrIndexSettingsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/search/solr/client/view/SolrIndexSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/search/solr/client/component/SolrIndexSettings.ui.xml`
- **Goes:** `SolrIndexSettingsPresenter.SolrIndexSettingsView`; `stroom-core-client/src/main/java/stroom/search/solr/client/view/SolrIndexSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/search/solr/client/presenter/SolrIndexSettingsUiHandlers.java`; `SolrIndexModule.java:38-40 bindPresenterWidget(SolrIndexSettingsPresenter, SolrIndexSettingsView, SolrIndexSettingsViewImpl)`
- **Names:** SolrIndexSettings / SolrIndexSettingsController: free in all_simple_names.json and wave 12.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| SolrIndexSettingsViewImpl.ui.xml:12 | `<g:TextBox ui:field="collection" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| SolrIndexSettingsViewImpl.ui.xml:22 | `<g:TextArea ui:field="solrUrls" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| SolrIndexSettingsViewImpl.ui.xml:31 | `<g:TextArea ui:field="zkHosts" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| SolrIndexSettingsViewImpl.ui.xml:36 | `<g:TextArea ui:field="zkPath" width="400px"/>` | static | text-field--wide (new, text-field.css) |
| SolrIndexSettingsViewImpl.ui.xml:41 | `<g:TextBox ui:field="timeField" width="200px"/>` | static | drop it: .text-field is already width: 200px (text-field.css:34-35) |
| SolrIndexSettingsViewImpl.ui.xml:47 | `<b:Button ui:field="testConnection" ... width="200px">` | static | drop it: form.css '.form > .Button' min-width 200px |

| Into the template | What | Detail |
| --- | --- | --- |
| SolrIndexSettingsViewImpl.java:74 | structure | testConnection.setIcon(SvgImage.OK) -> icon="OK" on the Button |
| SolrIndexSettingsViewImpl.java:76-77 | structure | instanceType items (SINGLE_NOOE, SOLR_CLOUD): fixed, added in the component's constructor (a SelectionBox has no item children in UiBinder) |
| SolrIndexSettingsViewImpl.ui.xml:45 + SolrIndexSettingsPresenter (view.setDefaultExtractionPipelineView) | component-declaration | <dd:DropDownBox ui:field="defaultExtractionPipeline"/> (wave 8) instead of the SimplePanel slot; the controller attaches a DocSelectionBoxController (PipelineDoc.TYPE, VIEW), as the receive-rules pilot does |
| SolrIndexSettingsViewImpl.ui.xml:52-53 + SolrIndexSettingsPresenter (view.setRetentionExpressionView) | component-declaration | <q:EditExpression ui:field="retentionExpression" addStyleNames="dock-max"/> (wave 7 EditExpression; note wave 7 lists it in both stroom.data.client.component and stroom.dashboard.client.component: use the one wave 7 settles on) instead of retentionExpressionPanel |

**Stays in Java:** Field values from the document and the read-only state (onReadOnly); Test-connection loading state (setTestingConnection) and the result alerts (text from the server).

## `ProcessorEditPresenter`

dialog, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.processor.client.component.ProcessorEdit`
- **Controller:** `stroom.processor.client.component.ProcessorEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/ProcessorEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorEdit.ui.xml`
- **Goes:** `ProcessorEditPresenter.ProcessorEditView`; `stroom-core-client/src/main/java/stroom/processor/client/view/ProcessorEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/processor/client/presenter/ProcessorEditUiHandlers.java`; `PipelineModule.java:142 bindPresenterWidget(ProcessorEditPresenter, ProcessorEditView, ProcessorEditViewImpl)`
- **Names:** ProcessorEdit / ProcessorEditController: free in all_simple_names.json and wave 12 (ProcessorFilterEditor would describe it better and is also free; either is fine).

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorEditViewImpl.ui.xml:15 + ProcessorEditPresenter.java (view.setExpressionView) | component-declaration | <q:EditExpression ui:field="expression" addStyleNames="max default-min-sizes"/> (wave 7) instead of the SimplePanel slot |
| ProcessorEditViewImpl.ui.xml:58 + ProcessorEditPresenter.java (view.setRunAsUserView) | component-declaration | <dd:DropDownBox ui:field="runAsUser"/> (wave 6, stroom.security.client.component) instead of the SimplePanel slot; the controller attaches UserRefSelectionController with FindUserContext.RUN_AS |
| ProcessorEditViewImpl.java:78-79 | structure | maxProcessingTasks min/max: min="0" max="..." on the ValueSpinner only if UiBinder parses long attributes (setMin/setMax take long; no template sets them today) and the ProcessorFilter constants are duplicated knowingly; else stays in the component's constructor |
| ProcessorEditViewImpl.ui.xml:20-62 | component-declaration | DateTimeBox (wave 4), ValueSpinner, SelectionBox, TickBox, DurationPicker, Button are already declared |

**Stays in Java:** ProcessorEditViewImpl.java:81 default max task creation delay (30 s) and the enabled state from its tick box; Field values from the filter; the profile list model (ProcessorProfileListModel, controller); Dialog caption ("Add Filter"/"Edit Filter" chosen by existingFilter), size and OK handling (controller); FeedDependenciesEditor (unit 402) is a dialog the controller opens, not declared.

## `DataRetentionImpactPresenter`

tab, presenter; complexity 3, effort M; used by 1.

**Since the merge.** Mechanical rename only (DataGrid, PagerView package). No longer in flight.

- **Component:** `stroom.receive.rules.client.component.DataRetentionImpact`
- **Controller:** `stroom.receive.rules.client.component.DataRetentionImpactController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionImpact.ui.xml`
- **Goes:** Nothing bound in GIN (it takes the shared PagerView binding); the injected PagerView goes from its constructor (DataRetentionImpactPresenter.java:110); DataRetentionImpactRow.java and DataRetentionImpactTreeAction.java stay as model classes; they may move to stroom.receive.rules.client.component or a model package beside it, but are not deleted
- **Names:** DataRetentionImpact / DataRetentionImpactController: free in all_simple_names.json and wave 12.

| Into the template | What | Detail |
| --- | --- | --- |
| DataRetentionImpactPresenter.java:110,117 | component-declaration | new template: root <dg:PagerPanel ui:field="pager"> (wave 5, stroom.widget.datagrid.client; wave 5 proposes renaming it PagerPanel) holding <dg:DataGrid ui:field="dataGrid" tableName="Data Retention Impact"/> (once DataGrid has its no-arg constructor) instead of new DataGrid<>(this) and view.setDataWidget |
| DataRetentionImpactPresenter.java:122-132 | aria | toolbar name "Retention impact actions" and Run Query/Stop Query/Set Query Filter/Nested Results (toggle)/Expand All/Collapse All with icons and titles: ToolbarButton children of the pager's toolbar in the template if the wave-5 pager takes them, else one ImpactAction enum with setOnAction/setAvailableActions |

**Stays in Java:** Grid columns and ExpanderCell rendering (data); Button enabled/toggle state (updateButtonStates, DataRetentionImpactPresenter.java:236-248); Query, cancel and sort logic and the rows (controller and model); the filter dialog (since wave 7 an `ExpressionEditorController`, shown with `ControllerDialog.showOnce`) is opened by the controller, so not declared.

## `ProcessorPresenter`

tab, presenter; complexity 4, effort L; used by 5.

- **Component:** `stroom.processor.client.component.Processors`
- **Controller:** `stroom.processor.client.component.ProcessorsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/ProcessorViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/Processors.ui.xml`
- **Goes:** `ProcessorPresenter.ProcessorView (ProcessorPresenter.java:515-522)`; `stroom-core-client/src/main/java/stroom/processor/client/view/ProcessorViewImpl.java`; `PipelineModule.java:141 bindPresenterWidget(ProcessorPresenter, ProcessorView, ProcessorViewImpl)`
- **Names:** Processor clashes with stroom.processor.shared.Processor, stroom.pipeline.factory.Processor and the jOOQ table. It is the Processors tab of pipelines and analytic rules, so Processors + ProcessorsController (free). Its users (6 imports, including StreamingProcessingPresenter, unit 522) move in the same commit.

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorViewImpl.ui.xml:8-9 + ProcessorPresenter.java:125 | component-declaration | <p:ProcessorList ui:field="processorList" addStyleNames="max dashboard-panel"/> (wave 10) instead of the SimplePanel slot |
| ProcessorViewImpl.ui.xml:16 + ProcessorPresenter.java:126 | component-declaration | <q:ExpressionTree ui:field="details"/> (wave 7) inside the MaxScrollPanel instead of the details slot |
| ProcessorViewImpl.java:58 + ProcessorViewImpl.ui.xml:13 | structure | <g:HTML ui:field="info"/> in the template instead of a SimplePanel given new HTML(info) each time; Java calls info.setHTML(safeHtml) |
| ProcessorPresenter.java:167-216 | aria | toolbar name "Processor actions" and New/Edit/Duplicate/Delete/Filter/Batch Edit/Show Tasks buttons with icons and titles: one ProcessorAction enum with setOnAction/setAvailableActions (which ones apply depends on allowCreate/allowUpdate), added to the list's toolbar by the component |
| ProcessorPresenter.java:253 | css-class | default-min-sizes on the Expression filter dialog belongs in the Expression component's template (wave 9), not here |

**Stays in Java:** Info HTML built from the selected row (ProcessorInfoBuilder); Which actions are available (allowCreate/allowUpdate/admin) and enabled (selection); Edit interceptor and default expression (StreamingProcessing sets them); ProcessorEdit, Expression and BatchProcessorFilterEdit are dialogs, opened by the controller, not declared; Button is not used here ("declares" entries wrong).

## `UserPermissionReportPresenter`

tab, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.security.client.component.UserPermissionReport`
- **Controller:** `stroom.security.client.component.UserPermissionReportController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/UserPermissionReportViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/UserPermissionReport.ui.xml`
- **Goes:** `UserPermissionReportPresenter.UserPermissionReportView`; `stroom-core-client/src/main/java/stroom/security/client/view/UserPermissionReportViewImpl.java`; `SecurityModule.java:173-176 bindPresenterWidget(UserPermissionReportPresenter, UserPermissionReportView, UserPermissionReportViewImpl)`; QuickFilterUiHandlers implementation on the presenter (quickFilterPageView.setUiHandlers(this), UserPermissionReportPresenter.java:92) once QuickFilterPage has callbacks
- **Names:** UserPermissionReport / UserPermissionReportController: free in all_simple_names.json and wave 12.

| Into the template | What | Detail |
| --- | --- | --- |
| UserPermissionReportViewImpl.ui.xml:36 + UserPermissionReportPresenter.java:90-91 | component-declaration | <q:QuickFilterPage ui:field="documents" addStyleNames="dock-max overflow-hidden"> (wave 10, the former QuickFilterPageViewImpl) holding <s:DocumentPermissionsList ui:field="permissionsList"/> (wave 10, stroom.explorer.client.component) as its data widget, in place of the permissionsList slot and the injected QuickFilterPageView |
| UserPermissionReportPresenter.java:97 | text | label="Documents" on the declared QuickFilterPage |
| UserPermissionReportPresenter.java:98-106 | text | the fixed help text ("Lists documents, optionally filtered based on the value of the Permission Visibility dropdown.") as a <form:HelpHTML> child or helpText= of QuickFilterPage, depending on what wave 10 gives it |
| UserPermissionReportPresenter.java:112 | structure | permissionVisibility items (PermissionShowLevel.ITEMS) and default SHOW_EXPLICIT: added in the component's constructor, not the controller |
| UserPermissionReportPresenter.java:114-127 | aria | toolbar name "Document permission actions" and the Edit/Filter/Batch Edit buttons (EDIT, FILTER, GENERATE with their titles): declared as ToolbarButtons if DocumentPermissionsList's template accepts toolbar children, else added by the component to the list's toolbar with setOnAction(Consumer<ReportAction>) |
| UserPermissionReportPresenter.java:164 | css-class | default-min-sizes on the ExpressionPresenter filter dialog: belongs in the Expression component's own template root (wave 9) or the dialog's, not here |

**Stays in Java:** Button enabled state (selection, result count); The filter expression, criteria and the dialogs (Expression, DocumentUserPermissionsEdit, BatchDocumentPermissionsEdit are opened with makeDialog, so not declared: their "declares" entries are wrong); Filter error text on the quick filter (from the server).

## `DataRetentionPolicyPresenter`

tab, presenter; complexity 4, effort M; used by 4.

- **Component:** `stroom.receive.rules.client.component.DataRetentionPolicy`
- **Controller:** `stroom.receive.rules.client.component.DataRetentionPolicyController`
- **Template:** `stroom-core-client/src/main/resources/stroom/receive/rules/client/view/DataRetentionPolicyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionPolicy.ui.xml`
- **Goes:** `DataRetentionPolicyPresenter.DataRetentionPolicyView (DataRetentionPolicyPresenter.java:624-629)`; `stroom-core-client/src/main/java/stroom/receive/rules/client/view/DataRetentionPolicyViewImpl.java`; PolicyModule.java:46-48 bindPresenterWidget(DataRetentionPolicyPresenter, DataRetentionPolicyView, DataRetentionPolicyViewImpl) and imports PolicyModule.java:26,31
- **Names:** DataRetentionPolicy / DataRetentionPolicyController: free (the shared DTO is DataRetentionRules, not DataRetentionPolicy). Sits beside the pilot's RuleSetSettings in the same package.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| DataRetentionPolicyPresenter.java:127-131 | `listPresenter.getView().asWidget().getElement().getStyle().setBorderStyle(BorderStyle.NON…` | static | drop it: the list's PagerView root (PagerViewImpl.ui.xml: 'max dock-container-vertical') has no border rule, so this is a no-op; confirm with the screenshots. If a border does show, a modifier on the wave-5 pager component (e.g. pager-view--borderless) rather than a style. The same line is in RuleSetSettings.java:68, RulesPresenter.java:106, SelectionHandlersPresenter.java:129, ContentTemplateTabPresenter.java:147 (see cssNotes) |

| Into the template | What | Detail |
| --- | --- | --- |
| DataRetentionPolicyViewImpl.ui.xml:7 + DataRetentionPolicyPresenter.java:105 | component-declaration | <r:DataRetentionPolicyList ui:field="ruleList" addStyleNames="max"/> (wave 10) instead of the table slot |
| DataRetentionPolicyViewImpl.ui.xml:8 + DataRetentionPolicyPresenter.java:106 | component-declaration | <q:ExpressionTree ui:field="expression" addStyleNames="max"/> (wave 7, stroom.query.client.component) instead of the expression slot; 'no selection' (setSelectionModel(null), line 109) as a template attribute if ExpressionTree gives one |
| DataRetentionPolicyPresenter.java:111-125 | aria | toolbar name "Retention rule actions" and the Save/Add Above/Edit/Duplicate/Delete/Move Up/Move Down buttons (icons, titles): one RetentionRuleAction enum with setOnAction/setAvailableActions, the component adding them to the list's toolbar, as RuleSetSettings does |

**Stays in Java:** Button enabled state (DataRetentionPolicyPresenter.java:593 etc.), dirty state, the rules and the selected rule's expression; The list's action menu items (buildActionMenuItems); DataRetentionRulePresenter is the edit dialog the controller opens, so not declared; Button is not used (the "declares" entries are wrong).

## `StreamingProcessingPresenter`

composite, presenter; complexity 2, effort S; used by 5.

- **Component:** `stroom.analytics.client.component.StreamingProcessing`
- **Controller:** `stroom.analytics.client.component.StreamingProcessingController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/StreamingProcessingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/StreamingProcessing.ui.xml`
- **Goes:** `StreamingProcessingPresenter.StreamingProcessingView`; `stroom-core-client/src/main/java/stroom/analytics/client/view/StreamingProcessingViewImpl.java`; `AnalyticsModule.java:85-87 bindPresenterWidget(StreamingProcessingPresenter, StreamingProcessingView, StreamingProcessingViewImpl)`
- **Names:** StreamingProcessing / StreamingProcessingController: free. It wraps unit 405 (Processors), so convert 405 first in this wave.

| Into the template | What | Detail |
| --- | --- | --- |
| StreamingProcessingViewImpl.ui.xml:4 + StreamingProcessingPresenter (getView().setProcessorsView) | component-declaration | <p:Processors ui:field="processors" addStyleNames="max"/> (unit 405) as the template's root content instead of the SimplePanel slot; the controller attaches ProcessorsController |

**Stays in Java:** The default filter expression fetched by REST, read-only (context) and the edit interceptor's dirty check (controller).

## `AbstractProcessingPresenter`

tab, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.analytics.client.component.AnalyticProcessing`
- **Controller:** `stroom.analytics.client.component.AnalyticProcessingController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticProcessingViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticProcessing.ui.xml`
- **Goes:** `AbstractProcessingPresenter.java (merged into AnalyticProcessingController)`; AnalyticProcessingPresenter.java and ReportProcessingPresenter.java (their only differences, the offered types and the document type written, become a constructor argument or two thin subclasses AnalyticProcessingController/ReportProcessingController); `AbstractProcessingPresenter.AnalyticProcessingView`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticProcessingUiHandlers.java`; `stroom-core-client/src/main/java/stroom/analytics/client/view/AnalyticProcessingViewImpl.java`; `AnalyticsModule.java:98-100 bindSharedView(AnalyticProcessingView, AnalyticProcessingViewImpl)`
- **Names:** The Abstract... base merges into its two subclasses: one AnalyticProcessing component and AnalyticProcessingController (with ReportProcessingController only if the Report differences need a class). AnalyticProcessing, ReportProcessing and their controllers are free.

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticProcessingViewImpl.ui.xml:22 + AbstractProcessingPresenter.setProcessType (getView().setProcessSettings) | component-declaration | declare all three bodies in the template, inside the dock-max area, instead of the processSettings slot: <a:ScheduledProcessing ui:field="scheduled"/> (unit 521), <a:StreamingProcessing ui:field="streaming"/> (unit 522), <a:TableBuilderProcessing ui:field="tableBuilder"/> (wave 8); the component shows one (setVisible) and the controller attaches each part's controller |
| AnalyticProcessingPresenter.java (addProcessingType x3) / ReportProcessingPresenter.java (x1) | structure | the processing type items depend on the document type, so they stay a setter (setProcessingTypes(List)) called by the controller |

**Stays in Java:** Selected processing type, which body is visible, read-only state; uiConfigCache read and the per-type read/write (controller); setOnProcessingTypeChange callback to the document.

