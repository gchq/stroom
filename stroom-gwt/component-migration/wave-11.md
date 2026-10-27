# Wave 11: Larger dialogs (REST-backed, owning lists or forms)

Part of `../COMPONENT_MIGRATION_ORDER.md`; written 24 October 2026 from a review of every unit in the wave against the code after master (with `data_grid2` and `copy_and_paste`) was merged in. Line numbers are as of then. Each unit is done when it meets "Done means, for every unit".

> **Since wave 4 (25 October 2026):** the date-time, time, schedule and time range boxes' controllers (`DateTimeBoxController`, `TimeBoxController`, `ScheduleBoxController`, `TimeRangeSelectorController`) are already attached by today's presenters, so there is no `setPopupProvider` or `setSchedulePresenterProvider` left to replace: a converted controller makes and attaches them as they do. `CustomFileUpload` is `FileChooser`, with a `FileChooserController` its owner attaches; `ClassificationWrapperView` is `ClassificationWrapper` (`stroom.data.client.component`); the schedule dialog is `SchedulePickerController`. The rows below name what was there when they were written.

> **Since wave 5 (25 October 2026):** `PagerView`/`PagerViewImpl` is `PagerPanel` and `PagerViewWithHeading` is `HeadedPagerPanel` (both components in `stroom.widget.datagrid.client`; the plans below keep the old names and line numbers); `MessagePanelImpl` is `MessagePanel` (block `message-panel`); `SvgCell` is `IconCell`; the ref cells are in `stroom.data.client.component`, configured by one `RefCellsController`. `DataGrid` takes no bus (`DataGrid()`, `DataGrid(int pageSize)`): its context menu, links and Stroom AI questions go up the page as requests (`MenuRequest`, `OpenHyperlinkRequest`, `AskStroomAiTableRequest`) to `CorePresenter`'s relays. Not there yet, for the plans below that assume it: `PagerPanel` can't take children in a template (no `@UiChild` or `HasWidgets`: the grid goes in with `setDataWidget`, buttons with `getToolbar()`, and the toolbar's name is set in Java), and it is still the list's `TaskMonitorFactory` (about 90 uses); the grid's sort, range and selection outputs are still GWT's handlers. See `wave-05.md`.

> **Since wave 6 (25 October 2026):** the code editor is `CodeEditor` (`stroom.editor.client.component`; declare it, e.g. `<ed:CodeEditor ui:field="editor"/>`, and attach its controller, `make(codeEditorControllerProvider).attach(view.getEditor())`), with `CodeEditorOptions` and `CodeEditorController` (`EditorPresenter` is a thin wrapper over them; `Editor` is only the Ace wrapper inside it); the single line editor is `SingleLineCodeEditor` with `SingleLineCodeEditorController.attach(...)`; the explorer tree is one `ExplorerTree` (no-arg; `ExplorerTickBoxTree` is `setTickBoxes(true)`; `ExtendedExplorerTree` is gone, its overrides `setOnChoose` and `setOnInitialSelect`), filled by `ExplorerTreeController.attach(tree)` (was `ExplorerTreeModel`); `DraggableTreePanel` is concrete, with its callbacks (`setIsValidTarget`, `setOnSelect`, `setOnDragStart`, `setOnDragEnd`, `setOnDragCancel`), but takes no children in a template: its two panels go in with `setTreePanels(tree, subTree)`; `DocumentAssetTree` has a no-arg constructor and `setAccessibleName`; the pickers are a `DropDownBox` with a controller attached (`DocumentAssetSelectionController` in `stroom.document.asset.client.component`, `UserRefSelectionController` in `stroom.security.client.component`; `DropDownView` is gone); the annotation choosers are one-use popups, `ChooserController` and `MultiChooserController`, `show(opener, element, caption, onChoose)`, each opening tracked by an `OpenChooser` so that a second click closes it; the menu is `MenuController`. The plans below keep the old names. See `wave-06.md`.

> **Since wave 7 (26 October 2026):** Plan B's settings widgets are components with `setOnChange`, and its type settings editors `PlanBTypeSettingsEditor`s (`stroom.planb.client.component`); the secret forms are `SecretForm`s (`stroom.credentials.client.component`; the key store's is a `KeyStoreSecretFormController`, which uploads); the floor map's Set Scale, Timeline Settings, group edit, layer appearance (wave 11's `FloorMapLayerStylePresenter` is done too), dock, Layers panel and canvas are components in `stroom.floormap.client.component`, with `FloorMapSetScaleController`, `FloorMapTimelineSettingsController`, `FloorMapGroupEditController`, `FloorMapLayerStyleController`, `FloorMapDockController` (`addTab(String, IsWidget)`), `FloorMapCanvasController`, and `FloorMapLayers` with no controller; the pipeline's tree is `PipelineTree` and the stepping screen's bar `PipelinePathBar` (attach a `PipelinePathBarController` for its menus), both on `AbstractPipelineTree`; the pathway tree is `PathwayTree` with `PathwayTreeController`; the expression tree is `ExpressionTree` with `ExpressionTreeController` (`init(dataSource, fields)`, `setOnChange`, `setOnSearch`), each `TermEditor` with a `TermEditorController`, and both `EditExpressionPresenter`s are one `ExpressionEditor` with `ExpressionEditorController` (`stroom.query.client.component`; `setFramed(false)` where the owner frames it); `ControllerDialog.show(controller)` shows a controller made once, again and again, through one host; and a `DataGrid` given one of GWT's selection models selects on a press only with a `SingleSelectionEventManager`. The plans below keep the old names. See `wave-07.md`.

> **Since wave 8 (26 October 2026):** the explorer chooser is `ExplorerPopup` with `ExplorerPopupController` (`show(consumer)` to reuse one, `showOnce(opener, ...)` for one use; `DocSelectionPopup` is gone, its `setIncludeNullSelection` public), `DocSelectionController` is in `stroom.explorer.client.component` (`DocSelectionBoxPresenter` stays as the wrapper for callers not yet converted), and the document folder tree is `EntityTree` with `EntityTreeController` (the export tick tree an `ExplorerTree` with `EntityCheckTreeController`); a `DocTabPresenter`'s tab can show a controller through `ControllerTabProvider` (`new ControllerTabProvider<>(getEventBus(), provider::get, Foo::read, Foo::write)`; `isPresenter(content)` asks whether a shown presenter is the tab's without making it); the Documentation tab is `MarkdownEditController` (`stroom.entity.client.component`, with `MarkdownPreviewController`), and `MarkdownTabProvider` takes the event bus and a `Supplier` (`new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) { onRead…; onWrite… }`, an abstract class whose `onRead`/`onWrite` are given the controller; in a `DocumentEditor` tab call `followReadOnly()`); the settings tabs (data generator, visualisation, XML schema, text converter, SQL temporal store, feed, pathways, Elastic index, index, analytic rule, report, floor map event store; the error feed and its Set Default are `ErrorFeedController`), the preferences' Theme, Editor and Date and Time tabs, the Ask Stroom AI config tabs, `DenseVectorField`, `CredentialSettings`, `TableBuilderProcessing`, `QueryToolbar`, `DataTextPane` (the data viewer's `TextPresenter`; its editor options are `getOptions()`), `QueryTextPane` (the query result's `TextPresenter`), `ColumnFilterEditor` (`edit(opener, column, onChange)`), `AnalyticEmailDestination`, `CommentEdit` (`edit(opener, text, onOk)`), `WordsTab`, `XSDBrowser`, `QueryHelp` (`linkToEditor` kept), `ContentStoreContentPackDetails` (`setOnInstalled`) and `FloorMapTimeline` are components with a `...Controller` beside each; `AriaPanel` has `ariaValueMin`, `ariaValueMax` and `ariaValueNow`; and the size modifiers `text-field--narrow`/`--wide` and `value-spinner--narrow`/`--wide` replace widths in templates. The plans below keep the old names. See `wave-08.md`.

> **Since wave 9 (26 October 2026):** the small dialogs are components with a controller made for one use, shown with `ControllerDialog.showOnce(opener, this)` from a method that takes the result's callback; take a GIN `Provider` of the controller and call it with `this` as the opener (`AppRoot` from a plugin or helper). Among them: a name is a `NameEntry` (`stroom.entity.client.component`; `RenameDocumentController.rename`, `CreateNewGroupController`, `NewNodeGroupController`, `NewFsVolumeGroupController`, `NewIndexVolumeGroupController`), an alert or confirmation a `CommonAlert` (`CommonAlertController`; `AlertPlugin` keeps the queue, and a prompt is the browser's), the filter dialogs one `ExpressionDialog` (`stroom.data.client.component`; `show(opener, caption, size, expression, dataSource, fields, (expression, e) -> ...)`), the column format `ColumnFormat`, the Edit Property dialog `NewProperty`, the Create Processors dialog `ProcessChoiceDialog`, the index fields' `IndexFieldEditor` and `SolrIndexFieldEditor`, the retention period `RetentionPeriod`, Download Options `DownloadSearchResults` (one kept by each table), and the About, Splash, Dependency Information, query info, document Info, Current Selection, Run Now, keyboard shortcut recorder, custom row style, XPath filter, profile period, feed dependency, pipeline reference, new element, favourite, event link, asset and Git Push dialogs; the step controls are `StepControl` with `StepControlController`. Every component template declares Stroom's `TextBox` or `TextArea` (`stroom.widget.form.client`), never GWT's `TextBox` or `TextArea`. The plans below keep the old names. See `wave-09.md`.

> **Since wave 10 (27 October 2026):** the lists are components with a controller each (`stroom.<area>.client.component`). A list's template declares its `PagerPanel` (or `HeadedPagerPanel`) with a `toolbarName`, holding its `DataGrid` (`tableName`, `multiLine`, `pageSize`) and its toolbar's `ToolbarButton`s (`svg`, `title`, `enabled="false"`); a quick-filtered list's root is a `QuickFilterPage` (`accessibleName`). The component adds its columns and gives its pager as `getTaskMonitor()`, with `getSelectionModel()`, `getSortList()` and `getToolbar()`; its buttons, info and row menus go out through callbacks (`setOnAdd`, `setOnShowInfo`, `setOnShowActionMenu`), and its controller passes in the ref cells configured (`refCells.configure(new DocRefCell.Builder<>())`, `FeedRefCell`, `UserRefCell`). Only a list inside a document watches `Contexts.READ_ONLY`, which is true where nothing provides it. A controller shown as a tab is a `ControllerLayer`, which passes the focus on. Among the new names: `MetaList` with `MetaListController`, `SteppingMetaListController` and `MetaRelationListController`; `JobNodeColumns` and `JobNodeActionsController` (were `JobNodeListHelper`); one `NotificationList` for analytic and report notifications; `ScriptDependencyList` (`ScriptListPresenter` merged in); `IncludeExcludeFilterEditor` and `IncludeExcludeFilterDictionary`; `DependencyList` (was `DependenciesPresenter`); `TabSessionChooser`; `CredentialsList`; `FindDocResultList`; `EffectiveWordsList`; `TypeFilter`. The plans below keep the old names. See `wave-10.md`.

Split the dialogs that call REST, own lists or forms, or make further dialogs into a component dialog plus a controller. Results go back through callbacks, the controller supplies the task monitor from context, and the identity flows hang off ChangePasswordPresenter.

## Audited (27 October 2026)

Five reviewers read the wave's 58 commits against this plan, "Done means" and AGENTS.md (the identity,
upload, store and user dialogs; the find panels, pickers, filters, tags and properties; the floor
map, permissions, node, HTTP, processor and volume dialogs; the analytics, annotation, API key,
credential, import, schedule, preference and AI dialogs; and the last eleven, from the step filters
to the permissions tab). Fixed, in 4 commits:

- **Regressions on this branch.** The credential editor's tabs had lost their tab panel (`role` and
  `aria-controls`) and the fade between them, which the shared layout view gave; `CredentialEdit`
  sets both again. The index volume group dialog opened again each time its list fetched a page
  (paging or refreshing it), as the old presenter's flags against that went; the list gives its
  first page only, once (`IndexVolumeStatusListController.init`). The Task Manager opened a second
  copy, polling as well, when its button was pressed while it was open (it isn't modal); its plugin
  keeps the one that is open. Editing a user group showed its progress on the list's pager rather
  than the screen; `UserAndGroupActions` uses the screen's task monitor again.
- **Dialogs that fetch before they show.** Set Permissions, Set Document Create Permissions, Edit
  Node Group, the index volume and volume group dialogs, User Preferences and the activity chooser
  now say why and dispose themselves when their fetch fails (`new DefaultErrorHandler(this,
  this::dispose)`), instead of staying unowned. The clients they use take an error handler
  (`DocPermissionRestClient.getDocUserPermissionsReport`, `NodeGroupClient.getNodeGroupState`,
  `UserPreferencesManager.fetch`, `CurrentActivity.getActivity`). The API key dialog disposes
  itself on a `null` UI config; Change Password disposes itself if its opener went before the
  password policy came; the sign-in pages' calls have an owner; Upload looks up its task monitor
  when the upload starts, not when it is made.
- **BEM.** The Advanced Query, credential picker, floor map cluster filter bar and annotation tag
  text rules moved out of shared stylesheets into `advanced-quick-filter.css`,
  `credentials-manager-dialog.css`, `floor-map-cluster-dialog.css` and `annotation-tag-text.css`;
  Edit Tags' clear button is `explorer-node-edit-tags__clear`; the roots of Remove Tags, the
  property editor and Add User Group have their blocks.
- **Java.** The API key dialog's mode is `EditApiKeyMode`, so the component doesn't import its
  controller; unused imports, dead `clear()` methods, `/** */` comments, stray blank lines and the
  empty workbench modules (`ExplorerScreenModule`, `ResultStoreScreenModule`) went; fields still
  named for presenters are named for what they hold (`columnValuesFilter`, `assignedToPicker`,
  `userRefPopupProvider`, `currentPasswordProvider`); swapped licence years were put back; the
  moved tests' helpers say what they do (`pressOk`).
- **Docs.** The open issues, WRITING-STORIES.md, DATA_GRID_DESIGN.md, COMPONENT_MODEL_DESIGN.md,
  COMPONENTS.md and the quick filter design record name the new classes; Batch Edit Schedules'
  `validate` is marked fixed in ISSUES.md.

Left, on purpose:

- **Slots.** Controllers that call `initWidget` (lists, the expression editors and trees, the
  current selection, the notification destinations, the profile periods) are shown in slots
  (`setList`, `setExpressionEditor`, ...), as the brief allows.
- **Wrappers.** The `SimplePanel`s around the processor profile's time zone box, the schedule
  dialogs' Run As User pickers and the import confirmation's grid stay, keeping the layout the
  screenshots match; dropping them is a look change, not tried here.
- **Toolbars in Java.** The volume group dialogs add New, Edit, Delete and Rescan to their slotted
  list's toolbar, as the list controller owns the toolbar.
- **Buttons as task monitors.** Configure Ask Stroom AI's two buttons are handed to its controller
  as task monitors (their spinners), as Send Test Email's is; there is no `setRefreshing` adapter.
- **Shared classes.** The annotation chooser's list is bordered with the shared
  `form-control-border form-control-background` (`setBordered`), rather than a modifier of its own.
- **Licence years.** A component or controller that replaces a view or presenter keeps its year;
  one made new has 2026.
- **Recorded in ISSUES.md.** Unused code the wave left (`FloorMapAria`'s label and hide helpers,
  `FileData`, `ProcessingStatusUiHandlers`); the two dialogs that wait on `UiConfigCache`, which
  never calls back on a failure; the dialogs with no story; the sign-in pages' old classes.
- **Not this wave's.** `AGENTS.md`'s "App-wide screens" example still shows `FindPresenter`; the
  suite's tools and notes keep old names, as before.

## Done (27 October 2026)

All 81 of the plan's units (the 82nd, `FloorMapLayerStylePresenter`, was done in wave 7), in 64
commits: 58 for the units, 5 for the audit and this record. Each unit's commit ran its stories in a
copy of the worktree with the leak check and the screenshot diff. The wave's targeted run, after the
audit (700 stories: the main screens and app shell, the dashboard, query, floor map, pathway,
annotation and dictionary editors, the data, security, sign-in and AI stories, the pickers and the
quick filter), passed all 700 with the leak check; of their screenshots, 696 match, the 3 new are the
document selection dialog's stories (no baseline yet), and `AppShell.OpensDocument` differed (the
focus race recorded in ISSUES.md) and matched in a run of the app shell's 57 alone. The app and the
standalone dashboard GWT compile, checkstyle is clean and the client modules' 1,934 JUnit tests
pass. One bug master has too was fixed on the way, with a regression test and a changelog entry: the
Upload dialog failed with an unexpected error, instead of saying why, when its feed couldn't be
loaded (`TestDataUploadController`). What was decided on the way, where it differs from the plans
below, is in the audit above (Left, on purpose) and:

- **Made for each use, or kept.** Most dialogs are made for each use; the schedule dialogs, Change
  Step Filters, Edit Pathway, the floor map object properties, Query Favourites and Query History,
  Find, Find In Content, Search Result Stores and Choose Annotation are kept by their owner and shown
  again.
- **Template ARIA.** The floor map object properties name their controls in the template
  (`AriaPanel` rows, `TextBox` `ariaLabel`), for which `ColourBox` gained `setAriaLabel` and
  `DateTimeBox` `setRole` and `setAriaLabel`.
- **The permissions tab.** `DocumentUserPermissionsTab` and its controller are the tab's body
  everywhere; `DocumentUserPermissionsPresenter` stays as the main content tab's thin shell until
  wave 15, and the tab provider shows the controller through a `ControllerTabProvider`.

## CSS and template notes for the wave

- New state class #loading.loading--hidden { opacity: 0; visibility: hidden; } in stroom.css beside #loading (:168). It replaces the inline opacity/visibility in AuthenticationErrorPresenter.java:63-65, LoginPresenter.java:172-174 and ResetPasswordPresenter.java:78-80, through one SignInPageController.hideLoading(). CoreViewImpl.java:55-67 (outside this range) can add and remove the same class
- form.css:68-72 (.form > .Button, .form-group > .Button { min-width: 200px }) already gives the 200px button width, so width="200px" on Buttons goes with no new class (RuleViewImpl.ui.xml:50, EditAccountViewImpl.ui.xml:63)
- max (stroom.css:793) replaces setWidth/setHeight("100%") (CopyDocument, MoveDocument). w-100 (stroom.css:843) goes on declared UserRefSelectionBoxes (BatchExecutionScheduleEdit, ScheduledProcessEdit)
- form-control-border form-control-background (bordered panel without padding) moves into templates for AiAttachmentData, AiChatHistory and FindAnnotation's result lists
- Classes named after deleted ViewImpls, renamed with their component (update every selector): preferencesViewImpl-buttons (components.css:1060), columnFunctionEditorViewImpl* (components.css:753-773, quick-filter.css:96), ColumnValuesFilterViewImpl (components.css:1876-1897, 2112, 2118), steppingFilterViewImpl (components.css:1019-1022), globalPropertyEditViewImpl-* (components.css:690-723). linkTabPanelViewImpl (components.css:727-735) and FindViewImpl* (components.css:1457-1489) are shared with other units (unit 33 for FindViewImpl), so rename them only with that whole family
- Classes in these templates and Java that no CSS file defines, to drop: conditionalFormatTypeSelection (RuleViewImpl.java:74), documentPermissions (BatchExecutionScheduleEditViewImpl.ui.xml:10), emailTextBoxOuter (EmailResetPasswordViewImpl.ui.xml:11), authErrorText (AuthenticationErrorViewImpl.ui.xml:17), globalProperties (GlobalPropertyEditViewImpl.ui.xml, nine uses)
- New classes proposed: cf-rule__example (conditional-formatting.css), batchExecutionScheduleEdit-button (components.css, beside batchExecutionScheduleEdit-row :85), stroom-floormap-object-edit__matrix-box / __fill-default / __unit (stroom-floormap.css, replacing FloorMapObjectEditViewImpl.ui.xml's ui:style)
- Shared widget gaps (not CSS) that stop fixed text and ARIA reaching templates: (1) g:TextBox has no placeholder, autocomplete or aria-label setters (Login, EmailResetPassword, FloorMapObjectEdit); (2) there is no template-usable icon widget taking an SvgImage (Login logo, AuthenticationError icon); (3) DataGrid needs a no-arg constructor (DataGrid.java:192 takes the bus) and PagerView a widget form before ColumnValuesFilter, AiAttachmentData, QueryFavourites, QueryHistory and SteppingFilterEditor can declare them; (4) PasswordBox has no 'badge' UiChild (ChangePassword)
- Classes named after a ViewImpl should be renamed to BEM names in the move, with their rules: createUserViewImpl* (components.css:1829), createMultipleUsersViewImpl* (components.css:1833), documentUserPermissionsViewImpl-users/-list and documentUserPermissions-details (components.css:1851-1875), resultStoreViewImpl (components.css:563), userTaskManagerViewImpl-layout (components.css:880), FindViewImpl, FindViewImpl--dialog, FindViewImpl-top, FindViewImpl-guidance, FindViewImpl-bottom (components.css:1457-1492, shared by FindPanel, FindInContent and stroom/ai/client/AiChatHistoryViewImpl.ui.xml, so all three templates change together).
- Template classes with no CSS rule anywhere in ui/css (drop them in the move; no screenshot change): visualisation-assets-add-dialog (unit 22), documentPermissions (units 21 and 39), createExternalUserViewImpl and its -subjectId/-displayName/-fullName (unit 14), editNodeTagsAddTagsFrmGrp and editNodeTagsAddTagsInput (unit 36), removeNodeTags and removeNodeTagsNodeTagsFrmGrp (unit 37), exportConfigViewImpl-filter (unit 28). This removes class names only, not CSS, so it does not conflict with the 'removals wait' rule.
- New shared classes: http-config__text { width: 400px } and http-config__number { width: 200px } in components.css, for units 31 and 32 (template width= attributes are inline styles). Unit 21 needs batch-document-permissions-edit__change { width: 350px }. Unit 8's ui:style block becomes floormap-object-edit__number, __fill-default and __unit in stroom-floormap.css. Unit 27 uses w-100 (stroom.css:843) plus a new content-template-edit__pipeline { margin: 0 }. No width, margin or nowrap utility classes exist to reuse.
- Reusable existing classes in this range: form-control-border form-control-background (components.css:1227,1235) for bordered list panels (units 0, 11, 30, 33, 38); default-min-sizes (components.css:1085, unit 1); sr-only (spinner.css:126) for live regions; scroll-region (components.css:1921) for a focusable scrolling region (unit 9); section--disabled (components.css:2199) is already applied by FormGroup.setDisabled, so use that instead of toggling the class by hand (unit 26).
- Toolbar buttons built in Java with toolbar.button(...) or toolbar.toggle(...) should be declared as <b:ToolbarButton svg=... title=... enabled=.../> or <b:ToolbarToggleButton/> inside the template's <b:Toolbar> (units 5, 17, 28, 36), as FindInContentViewImpl.ui.xml already does. Unit 5's button sits in PagerView's toolbar, so PagerView has to accept toolbar children in templates.
- ARIA on plain GWT TextBox/TextArea/Label/MaxScrollPanel cannot be written as UiBinder attributes. Use FormGroup's controlNames (FormGroup.java:593) for groups of inputs (replaces FloorMapAria.group/label in units 6, 7 and 8), raw elements in a g:HTMLPanel for aria-live, role or tabindex (units 0, 9, 38), or a Stroom setter. The TextBox/TextArea overrides are under stroom-core-client-widget/src/main/java/com/, which AGENTS.md says must not change without human approval, so ask before adding setAriaLabel there (unit 38's pattern).
- Declaring DataGrid in templates (units 5, 13, 30, 38) depends on the wave 5 contract: a no-arg DataGrid constructor (today it is DataGrid(HasHandlers[, pageSize])), plus tableName, accessibleName, emptyText, virtual and page-size setters. QuickFilterDialog (unit 0) and PagerViewImpl need to take a child widget in templates.
- Within-wave order: unit 0 before 13; units 14 and 15 before 16; unit 20 before 1; unit 23 with 7; unit 33 before 34, 35 and 38 (shared find-panel classes). The FindView interface stays (FindPanel implements it) until FindAnnotation (later in wave 11), AnnotationLink (wave 12) and BrowseAnnotation (wave 15) move.

## `ActivityEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.activity.client.component.ActivityEdit`
- **Controller:** `stroom.activity.client.component.ActivityEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/activity/client/ActivityEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/activity/client/component/ActivityEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/activity/client/ActivityEditPresenter.java (with its nested ActivityEditView, :397-)`; `stroom-core-client/src/main/java/stroom/activity/client/ActivityEditViewImpl.java`; `ActivityModule.java:30 bindPresenterWidget(ActivityEditPresenter, ActivityEditView, ActivityEditViewImpl)`
- **Names:** No clash.

**Stays in Java:** The whole body is HTML from config (activityEditorBody, ActivityEditPresenter.java:84), and its inputs are found and read in Java (:111,:184); Caption from config (:90).

## `ChangePasswordPresenter`

dialog, presenter; complexity 2, effort M; used by 6.

- **Component:** `stroom.security.identity.client.component.ChangePassword`
- **Controller:** `stroom.security.identity.client.component.ChangePasswordController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/ChangePasswordViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/ChangePassword.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/ChangePasswordPresenter.java (with its nested ChangePasswordView, :94-103); `stroom-core-client/src/main/java/stroom/security/identity/client/view/ChangePasswordViewImpl.java`; `ChangePasswordModule.java:63-65 bindPresenterWidget(ChangePasswordPresenter, ChangePasswordView, ChangePasswordViewImpl)`
- **Names:** No clash. HideRequestUiHandlers is shared, so it stays; this component uses a setOnSubmit(Runnable) callback instead.

| Into the template | What | Detail |
| --- | --- | --- |
| ChangePasswordViewImpl.java:75-76 | text | placeholder="Enter Password" and placeholder="Confirm Password" on the two <form:PasswordBox> (:18, :30). PasswordBox.setPlaceholder exists |
| ChangePasswordViewImpl.java:83-88 | aria | Make the strength meter a plain element in a g:HTMLPanel (replacing the FlowPanels at :9/:13): <div ui:field="strengthMeter" class="password-strength" role="meter" aria-label="Password strength" aria-valuemin="0" aria-valuemax="5" aria-valuenow="0" aria-valuetext="None"><div ui:field="strengthMeterBar" class="password-strength__bar password-strength__bar--0"></div></div>, with DivElement fields |
| ChangePasswordViewImpl.java:68,78-80 | structure | The length badge built in Java could be declared as <g:Label ui:field="lengthBadge" styleName="password-length password-length--short"/>, but only if PasswordBox (wave 2) gains a 'badge' UiChild. Until then its creation and setBadge stay |

**Stays in Java:** Unique id and aria-describedby that tie the policy message to the input (ChangePasswordViewImpl.java:91-93); Strength and length state classes and aria-valuenow/valuetext (:199,:216-217,:225,:229-233); Policy message from the server (:109), validation feedback (:122-155); Caption, PASSWORD icon and modal OK/Cancel in the controller (ChangePasswordPresenter.java:69-76).

## `DocumentAssetUploadFileDialogPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.document.asset.client.component.DocumentAssetUploadFileDialog`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetUploadFileDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/document/asset/client/view/DocumentAssetUploadFileDialogViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/document/asset/client/component/DocumentAssetUploadFileDialog.ui.xml`
- **Goes:** `DocumentAssetUploadFileDialogPresenter.DocumentAssetUploadFileDialogView (nested)`; `stroom-core-client/src/main/java/stroom/document/asset/client/view/DocumentAssetUploadFileDialogViewImpl.java`; stroom-core-client/src/main/java/stroom/document/asset/client/gin/DocumentAssetModule.java:42-44 bindPresenterWidget(DocumentAssetUploadFileDialogPresenter...)
- **Names:** No clash. The component is the dialog's content, so ...Dialog in the name is kept only to match its callers; DocumentAssetUploadFile would also do.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentAssetUploadFileDialogViewImpl.ui.xml:17 | component-declaration | <form:FileChooser ui:field="fileChooser"/> already declared (wave 4); its controller attaches |
| DocumentAssetUploadFileDialogViewImpl.ui.xml:5 | css-class | visualisation-assets-add-dialog has no CSS rule: drop it |

**Stays in Java:** upload path label (DocumentAssetUploadFileDialogPresenter.java:107): data; form submit and REST (controller).

## `ResultStoreSettingsPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.query.client.component.ResultStoreSettingsEditor`
- **Controller:** `stroom.query.client.component.ResultStoreSettingsEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/ResultStoreSettingsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/ResultStoreSettingsEditor.ui.xml`
- **Goes:** `ResultStoreSettingsPresenter.ResultStoreSettingsView (nested in ResultStoreSettingsPresenter.java)`; `stroom-core-client/src/main/java/stroom/query/client/view/ResultStoreSettingsViewImpl.java`; `stroom-core-client/src/main/java/stroom/query/client/gin/QueryModule.java:107-110 bindPresenterWidget(ResultStoreSettingsPresenter...)`
- **Names:** ResultStoreSettings clashes with stroom.query.common.v2.ResultStoreSettings, so ResultStoreSettingsEditor.

| Into the template | What | Detail |
| --- | --- | --- |
| ResultStoreSettingsViewImpl.ui.xml:18,21,35,38 | component-declaration | TickBox (wave 2) and FormGroup (wave 1) already declared; nothing else to move (no Java markup) |

**Stays in Java:** field values and validation messages (ResultStoreSettingsPresenter.java:93-131); caption (ResultStoreSettingsPresenter.java:60): passed in by the caller.

## `CreateExternalUserPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.client.component.CreateExternalUser`
- **Controller:** `stroom.security.client.component.CreateExternalUserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/CreateExternalUserViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/CreateExternalUser.ui.xml`
- **Goes:** `CreateExternalUserPresenter.CreateExternalUserView (nested in CreateExternalUserPresenter.java)`; `stroom-core-client/src/main/java/stroom/security/client/view/CreateExternalUserViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:131-134 bindPresenterWidget(CreateExternalUserPresenter...)`
- **Names:** No clash. Embedded by unit 16 (CreateUser) in this wave: convert together.

| Into the template | What | Detail |
| --- | --- | --- |
| CreateExternalUserViewImpl.ui.xml:6,11,27,45 | css-class | createExternalUserViewImpl, createExternalUserViewImpl-subjectId/-displayName/-fullName have no CSS rule: drop them, or rename to create-external-user / create-external-user__... if a rule is wanted |

**Stays in Java:** clear() (CreateExternalUserViewImpl.java:83-85): resets values; validation feedback and REST create (controller).

## `CreateMultipleUsersPresenter`

composite, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.client.component.CreateMultipleUsers`
- **Controller:** `stroom.security.client.component.CreateMultipleUsersController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/CreateMultipleUsersViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/CreateMultipleUsers.ui.xml`
- **Goes:** `CreateMultipleUsersPresenter.CreateMultipleUsersView (nested in CreateMultipleUsersPresenter.java)`; `stroom-core-client/src/main/java/stroom/security/client/view/CreateMultipleUsersViewImpl.java`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:135-138 bindPresenterWidget(CreateMultipleUsersPresenter...)
- **Names:** No clash. Embedded by unit 16.

| Into the template | What | Detail |
| --- | --- | --- |
| CreateMultipleUsersViewImpl.ui.xml:6,57 | css-class | rename createMultipleUsersViewImpl / createMultipleUsersViewImpl-userBatch to create-multiple-users / create-multiple-users__batch (components.css:1833) |

**Stays in Java:** batch text and validation (controller).

## `UserTaskManagerPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.task.client.component.UserTaskManager`
- **Controller:** `stroom.task.client.component.UserTaskManagerController`
- **Template:** `stroom-core-client/src/main/resources/stroom/task/client/view/UserTaskManagerViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/task/client/component/UserTaskManager.ui.xml`
- **Goes:** `UserTaskManagerPresenter.UserTaskManagerView (nested)`; `stroom-core-client/src/main/java/stroom/task/client/view/UserTaskManagerViewImpl.java`; stroom-core-client/src/main/java/stroom/task/client/gin/TaskModule.java:31 bindPresenter(UserTaskManagerPresenter, UserTaskManagerView, UserTaskManagerViewImpl)
- **Names:** No clash. Made by TaskManagerPlugin through a provider.

| Into the template | What | Detail |
| --- | --- | --- |
| UserTaskManagerViewImpl.ui.xml:4 | css-class | rename userTaskManagerViewImpl-layout to user-task-manager (components.css:880) |

**Stays in Java:** task rows (UserTaskManagerPresenter.java:181-199): one UserTaskPresenter (wave 3) per running task, data-driven; polling and terminate REST (controller).

## `ImportConfigPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.importexport.client.component.ImportConfig`
- **Controller:** `stroom.importexport.client.component.ImportConfigController`
- **Template:** `stroom-core-client/src/main/resources/stroom/importexport/client/view/ImportConfigViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/importexport/client/component/ImportConfig.ui.xml`
- **Goes:** `ImportConfigPresenter.ImportConfigView (nested)`; `stroom-core-client/src/main/java/stroom/importexport/client/view/ImportConfigViewImpl.java`; stroom-core-client/src/main/java/stroom/importexport/client/gin/ImportExportConfigModule.java:44-47 bindPresenterWidget(ImportConfigPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ImportConfigViewImpl.ui.xml:9 | component-declaration | <form:FileChooser ui:field="fileChooser"/> already declared (wave 4); controller attaches FileChooserController |

**Stays in Java:** submit and the confirm hand-off (ImportConfigPresenter.java:87-100).

## `FindInContentPresenter`

dialog, presenter; complexity 4, effort L; used by 3.

**Since the merge.** Renames only (DataGrid, PagerView, FindResultLists). The template is unchanged; its pattern box is still a plain g:TextArea, not the new TextField. Nothing material.

- **Component:** `stroom.explorer.client.component.FindInContent`
- **Controller:** `stroom.explorer.client.component.FindInContentController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/FindInContentViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/FindInContent.ui.xml`
- **Goes:** `FindInContentPresenter.FindInContentView (nested)`; `stroom-core-client/src/main/java/stroom/explorer/client/view/FindInContentViewImpl.java`; `stroom-core-client/src/main/java/stroom/explorer/client/presenter/FindInContentUiHandlers.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:150-153 bindPresenter(FindInContentPresenter...)`; `PagerView injection (FindInContentPresenter.java:95)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FindInContentViewImpl.java:78 | structure | visible="false" on <spinner:SpinnerLarge ui:field="spinner"> |
| FindInContentViewImpl.java:80 | aria | aria-label="Find in content" on the pattern TextArea: g:TextArea has no template attribute for it, and the TextArea override is under stroom-core-client-widget/src/main/java/com/ (needs human approval to change, per AGENTS.md); alternatives: a FormGroup with identity and an sr-only label, or keep it in Java until a Stroom setter exists |
| FindInContentViewImpl.java:83 | aria | aria-live="polite" on patternFeedback: make it <div ui:field="patternFeedback" class="form-group__feedback" aria-live="polite"/> inside an HTMLPanel guidance block (ui.xml:17) |
| FindInContentViewImpl.ui.xml:25 / FindInContentPresenter.java:105,172-180 | component-declaration | <dg:PagerViewImpl ui:field="pager"><dg:DataGrid ui:field="results" tableName="Content found"/></dg:PagerViewImpl> (wave 5) inside resultContainer; FindResultLists.create becomes a configure(grid, ...) on the declared grid |
| FindInContentViewImpl.ui.xml:27 / FindInContentPresenter.java:107-108 | component-declaration | <ed:Editor ui:field="text" readOnly="true"/> (wave 6, from EditorPresenter) inside textContainer instead of setTextView |
| FindInContentViewImpl.ui.xml:7,8,17,24 | css-class | FindViewImpl* classes renamed with unit 33 to find-panel, find-panel--dialog, find-panel__top, find-panel__guidance, find-panel__bottom |

**Stays in Java:** hint text TEXT_HINT/REGEX_HINT (FindInContentViewImpl.java:46-53, 124-128): depends on the Regex toggle; hint id and aria-describedby (FindInContentViewImpl.java:84-85): generated id; multiLine state class (FindInContentViewImpl.java:147-152): depends on the text (rename to find-panel__pattern--multiline with the CSS at components.css:1479); pattern validity (FieldValidity); REST search, highlights and the editor text (controller).

## `AuthenticationErrorPresenter`

screen, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.security.identity.client.component.AuthenticationError`
- **Controller:** `stroom.security.identity.client.component.AuthenticationErrorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/AuthenticationErrorViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/AuthenticationError.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/AuthenticationErrorPresenter.java (with its nested AuthenticationErrorView, :75-80); `stroom-core-client/src/main/java/stroom/security/identity/client/view/AuthenticationErrorViewImpl.java`; `ChangePasswordModule.java:57-59 bindPresenter(AuthenticationErrorPresenter, AuthenticationErrorView, AuthenticationErrorViewImpl)`
- **Names:** No clash. Its controller extends SignInPageController, the renamed SignInPagePresenter of unit 481 (Login).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| AuthenticationErrorPresenter.java:63 | `loading.getStyle().setOpacity(0)` | static | New state class #loading.loading--hidden { opacity: 0; visibility: hidden; } in stroom.css, beside #loading (:168). Add it with loading.addClassName("loading--hidden") in one SignInPageController.hideLoading() |
| AuthenticationErrorPresenter.java:65 | `loading.getStyle().setVisibility(Visibility.HIDDEN)` | static | Same loading--hidden class (stroom.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| AuthenticationErrorViewImpl.ui.xml:17 | css-class | authErrorText is defined in no CSS file. Drop it |

**Stays in Java:** Error icon (AuthenticationErrorViewImpl.java:47-48: SvgImage.ERROR markup and class). It is fixed, but no template-usable icon widget takes an SvgImage yet. It moves to the template once one exists (shared need with Login); Error text from the URL and the generic message HTML from config (AuthenticationErrorPresenter.java:56-61).

## `EmailResetPasswordPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.security.identity.client.component.EmailResetPassword`
- **Controller:** `stroom.security.identity.client.component.EmailResetPasswordController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/EmailResetPasswordViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/EmailResetPassword.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/EmailResetPasswordPresenter.java (with its nested EmailResetPasswordView, :113-118); `stroom-core-client/src/main/java/stroom/security/identity/client/view/EmailResetPasswordViewImpl.java`; `ChangePasswordModule.java:69-71 bindPresenterWidget(EmailResetPasswordPresenter, EmailResetPasswordView, EmailResetPasswordViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| EmailResetPasswordViewImpl.ui.xml:7 | aria | The FormGroup has identity="password" but labels the email box. Make it identity="email" so the label's 'for' names the right control |
| EmailResetPasswordViewImpl.ui.xml:11 | css-class | emailTextBoxOuter is defined in no CSS file. Drop the class (emailTextBox, ChangePassword.css:9, stays) |

**Stays in Java:** Placeholder 'Enter Your Email Address' (EmailResetPasswordViewImpl.java:50). It is fixed text, but g:TextBox has no placeholder setter. It moves to the template once a template-usable text box with placeholder/autocomplete/aria-label setters exists (shared need with Login and FloorMapObjectEdit); Validation feedback (:68-87); Caption 'Reset Your Password' in the controller (EmailResetPasswordPresenter.java:67).

## `FloorMapInitViewImpl`

composite, tidy; complexity 1, effort S; used by 1.

- **Component:** `stroom.floormap.client.view.FloorMapInitViewImpl`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapInitViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapInitViewImpl.ui.xml`
- **Goes:** `None in this wave. FloorMapModule.java:123-125 goes with FloorMapInitPresenter's own wave`
- **Names:** As a widget it keeps its name and package.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapInitViewImpl.java:67-75 / template :26,:32 | component-declaration | <dd:DropDownBox ui:field="factsStore"/> and <dd:DropDownBox ui:field="eventsStore"/> (wave 8, missing from the declares list) replace the two container SimplePanels. FloorMapInitPresenter.java:129-140 then attaches two DocSelectionBoxControllers to view.getFactsStoreBox()/getEventsStoreBox() instead of setFactsStoreView/setEventsStoreView |
| FloorMapInitViewImpl.java:56-57 | aria | FloorMapAria.group(..., "Facts Store"/"Events Store") goes: once the box is declared in the FormGroup, its identity/label names the control (add identity=... to the FormGroups if the label's 'for' needs it) |

**Stays in Java:** focus() reaching the first focusable control (FloorMapInitViewImpl.java:87-89) until DocSelectionBox exposes focus().

## `AbstractFindPresenter`

dialog, presenter; complexity 3, effort M; used by 7.

- **Component:** `stroom.explorer.client.component.FindPanel`
- **Controller:** `none (AbstractFindPresenter is merged into FindController, unit 34, which RecentItemsController, unit 35, extends)`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/FindViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/FindPanel.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/explorer/client/presenter/AbstractFindPresenter.java (merged into FindController)`; stroom-core-client/src/main/java/stroom/explorer/client/presenter/FindUiHandlers.java (callbacks setOnFilterChange / setOnFilterKeyDown on FindPanel); `stroom-core-client/src/main/java/stroom/explorer/client/view/FindViewImpl.java (becomes FindPanel)`; stroom-core-client/src/main/java/stroom/explorer/client/presenter/FindDocResultListHandler.java once FindDocResultList (wave 10) takes a setOnOpen callback; AbstractFindPresenter.FindView: stays (implemented by FindPanel) until FindAnnotation (wave 11), AnnotationLink (wave 12) and BrowseAnnotation (wave 15) move; then stroom-core-client/src/main/java/stroom/annotation/client/gin/AnnotationModule.java:88 bindSharedView(AbstractFindPresenter.FindView.class, FindViewImpl.class) goes
- **Names:** AbstractFind is not a component: the base merges. FindViewImpl, shared by Find, Recent Items and three annotation screens, becomes the dumb component FindPanel (no clash), so neither Find nor RecentItems needs a component of its own; RecentItems is already taken by the client model stroom.explorer.client.presenter.RecentItems.

| Into the template | What | Detail |
| --- | --- | --- |
| AbstractFindPresenter.java:57 | css-class | form-control-border form-control-background on the result list: addStyleNames on <ex:FindDocResultList ui:field="results"/> in FindPanel.ui.xml |
| FindViewImpl.ui.xml:9 / AbstractFindPresenter.java:58 | component-declaration | <ex:FindDocResultList ui:field="results" addStyleNames="form-control-border form-control-background"/> (wave 10) in the bottom panel instead of setResultView |
| FindViewImpl.java:55 | css-class | dialog modifier already in the template (FindViewImpl.ui.xml:5); the constructor's setDialogMode(true) goes |
| FindViewImpl.ui.xml:5-9 | css-class | rename FindViewImpl, FindViewImpl--dialog, FindViewImpl-top, FindViewImpl-bottom to find-panel, find-panel--dialog, find-panel__top, find-panel__bottom (components.css:1457-1492); FindInContentViewImpl.ui.xml and stroom/ai/client/AiChatHistoryViewImpl.ui.xml use them too and move with it |

**Stays in Java:** setDialogMode(boolean) (FindViewImpl.java:84-90): the annotation screens switch it at runtime (FindAnnotationPresenter.java:52,81); filter accessible name/description and help (setFilterGuidance, registerPopupTextProvider): per caller; filter error.

## `QuickFilterDialogViewImpl`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.widget.dropdowntree.client.view.QuickFilterDialog`
- **Controller:** `none`
- **Template:** `stroom-core-client-widget/src/main/resources/stroom/widget/dropdowntree/client/view/QuickFilterDialogViewImpl.ui.xml` → `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/QuickFilterDialog.ui.xml`
- **Goes:** `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/QuickFilterDialogView.java (view interface)`; `stroom-core-client-widget/src/main/java/stroom/widget/dropdowntree/client/view/QuickFilterDialogViewImpl.java (becomes QuickFilterDialog)`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:117 bindSharedView(QuickFilterDialogView.class, QuickFilterDialogViewImpl.class); QuickFilterUiHandlers use: replaced by setOnFilterChange(Consumer<String>) (QuickFilterUiHandlers itself stays while QuickFilterPageViewImpl, wave 10, still uses it)
- **Names:** No clash for QuickFilterDialog. It is a dumb view-turned-widget only used by UserRefPopupPresenter (unit 13), so it has no controller of its own: UserRefPopupController drives it. Make it a container (HasOneWidget / setWidget) so the list it filters is declared inside it in the caller's template rather than passed in with setDataView(View). Near-duplicate of QuickFilterPageViewImpl (wave 10); merging the two is optional, not required here.

| Into the template | What | Detail |
| --- | --- | --- |
| QuickFilterDialogViewImpl.java:49 | aria | aria-live="polite" on the status: make the root <g:HTMLPanel styleName="max dock-container-vertical quick-filter-layout"> and the status a plain element <div ui:field="status" class="sr-only" aria-live="polite"/> (DivElement field, setInnerText in announce()), as g:Label cannot carry aria attributes |
| QuickFilterDialogViewImpl.ui.xml:7 | structure | <g:SimplePanel ui:field="data" .../> slot becomes the widget's single child slot (@UiChild or HasOneWidget), so callers write <v:QuickFilterDialog><dg:PagerViewImpl>...</dg:PagerViewImpl></v:QuickFilterDialog> |
| QuickFilterDialogViewImpl.ui.xml:6 | component-declaration | <view:QuickFilter ui:field="quickFilter" addStyleNames="dock-min"/> already declared (wave 2); keep |

**Stays in Java:** announce(String) text (QuickFilterDialogViewImpl.java:99): built from the chosen user's name; setAccessibleName/registerPopupTextProvider/setFilterError pass-throughs: values come from the controller (filter syntax help depends on UiConfig).

## `DocumentUserCreatePermissionsEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.security.client.component.DocumentUserCreatePermissionsEdit`
- **Controller:** `stroom.security.client.component.DocumentUserCreatePermissionsEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/DocumentUserCreatePermissionsEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserCreatePermissionsEdit.ui.xml`
- **Goes:** `DocumentUserCreatePermissionsEditPresenter.DocumentUserCreatePermissionsEditView (nested)`; `stroom-core-client/src/main/java/stroom/security/client/view/DocumentUserCreatePermissionsEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/client/presenter/DocumentUserCreatePermissionsEditUiHandler.java`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:157-160 bindPresenterWidget(DocumentUserCreatePermissionsEditPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentUserCreatePermissionsEditViewImpl.ui.xml:14 | component-declaration | <sec:DocumentCreatePermissionsList ui:field="createPermissions" addStyleNames="max"/> (wave 10) in place of the SimplePanel slot |
| DocumentUserCreatePermissionsEditViewImpl.ui.xml:15 | component-declaration | Button (wave 1) already declared with its text |

**Stays in Java:** FormGroup label "User Group"/"User" (DocumentUserCreatePermissionsEditViewImpl.java:80-82): depends on the user ref; document and user names (data); Apply To Descendants callback: setOnApplyToDescendants(Consumer<Button>) instead of UiHandlers.

## `CurrentPasswordPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.security.identity.client.component.CurrentPassword`
- **Controller:** `stroom.security.identity.client.component.CurrentPasswordController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/CurrentPasswordViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/CurrentPassword.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/CurrentPasswordPresenter.java (with its nested CurrentPasswordView, :172-177); `stroom-core-client/src/main/java/stroom/security/identity/client/view/CurrentPasswordViewImpl.java`; `ChangePasswordModule.java:60-62 bindPresenterWidget(CurrentPasswordPresenter, CurrentPasswordView, CurrentPasswordViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| CurrentPasswordViewImpl.java:48 | text | placeholder="Enter Your Current Password" on <form:PasswordBox ui:field="password"> (:12) |

**Stays in Java:** Validation feedback (CurrentPasswordViewImpl.java:67-82); ChangePassword (same wave) is opened as a dialog with makeDialog, not declared; Caption 'Enter Your Current Password' in the controller (CurrentPasswordPresenter.java:79).

## `EditAccountPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.security.identity.client.component.EditAccount`
- **Controller:** `stroom.security.identity.client.component.EditAccountController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/EditAccountViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/EditAccount.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/EditAccountPresenter.java (with its nested EditAccountView, :312-367); `stroom-core-client/src/main/java/stroom/security/identity/client/view/EditAccountViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/EditAccountUiHandlers.java`; `ChangePasswordModule.java:76-78 bindPresenterWidget(EditAccountPresenter, EditAccountView, EditAccountViewImpl)`
- **Names:** No clash. EmailValidator, in this unit and also used by EmailResetPasswordViewImpl.java:75, moves to stroom.security.identity.client.component.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| EditAccountViewImpl.ui.xml:63 | `<b:Button ui:field="changePassword" ... width="200px">` | static | Drop the attribute. .form > .Button { min-width: 200px } (form.css:68-72) applies, as the button is a direct child of the .form panel (:7) |

**Stays in Java:** Lock and activity descriptions built from the account (EditAccountPresenter.java:241-256) and the enabled state of their buttons; Change/Set password button text (EditAccountPresenter.java:82,114); Visibility of the never-expires, enabled, locked and inactive groups (EditAccountViewImpl.java:156-195); Caption Create/Edit Account (EditAccountPresenter.java:89,121).

## `LoginPresenter`

screen, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.security.identity.client.component.Login`
- **Controller:** `stroom.security.identity.client.component.LoginController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/LoginViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/Login.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/LoginPresenter.java (with its nested LoginView, :183-)`; `stroom-core-client/src/main/java/stroom/security/identity/client/view/LoginViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/identity/client/presenter/LoginUiHandlers.java`; `ChangePasswordModule.java:54-56 bindPresenter(LoginPresenter, LoginView, LoginViewImpl)`
- **Names:** No clash. SignInPagePresenter (this unit) is the abstract base of three controllers (Login, AuthenticationError, ResetPassword), so it is kept, not merged: it becomes stroom.security.identity.client.component.SignInPageController (free), and takes the shared hideLoading().

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| LoginPresenter.java:172 | `loading.getStyle().setOpacity(0)` | static | loading--hidden state class on #loading (new, stroom.css beside :168), added by SignInPageController.hideLoading() |
| LoginPresenter.java:174 | `loading.getStyle().setVisibility(Visibility.HIDDEN)` | static | Same loading--hidden class |

| Into the template | What | Detail |
| --- | --- | --- |
| LoginViewImpl.java:80 | aria | role="main" on the page: make the root <g:HTMLPanel tag="main" ui:field="page" styleName="LoginView"> (template :6), which gives the main landmark with no attribute set in Java |
| LoginViewImpl.java:76 | text | placeholder="Enter Password" on <form:PasswordBox ui:field="password"> (:35) |

**Stays in Java:** User name placeholder (LoginViewImpl.java:74) and the autocomplete attributes (:81-82). These are fixed, but g:TextBox and PasswordBox's input have no setters. They move once a template-usable text box exists; INFINITY_LOGO icon (:71-72). It needs a template-usable icon widget, as AuthenticationError does; Password help text that depends on whether resets are allowed, and the forgot link's visibility (:140-146); Sign-in button loading/enabled state (:134-137,:164-167).

## `ResetPasswordPresenter`

screen, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.security.identity.client.component.ResetPassword`
- **Controller:** `stroom.security.identity.client.component.ResetPasswordController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/identity/client/view/ResetPasswordViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/identity/client/component/ResetPassword.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/security/identity/client/presenter/ResetPasswordPresenter.java (with its nested ResetPasswordView, :145-147); `stroom-core-client/src/main/java/stroom/security/identity/client/view/ResetPasswordViewImpl.java`; `ChangePasswordModule.java:66-68 bindPresenter(ResetPasswordPresenter, ResetPasswordView, ResetPasswordViewImpl)`
- **Names:** No clash. Its controller extends SignInPageController (unit 481).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ResetPasswordPresenter.java:78 | `loading.getStyle().setOpacity(0)` | static | loading--hidden state class (new, stroom.css beside #loading :168), through SignInPageController.hideLoading() |
| ResetPasswordPresenter.java:80 | `loading.getStyle().setVisibility(Visibility.HIDDEN)` | static | Same loading--hidden class |

**Stays in Java:** The template is only the background (<g:FlowPanel styleName="LoginView"/>, :5). ChangePassword (same wave) is opened over it with makeDialog; Invalid-link message and redirect URL (ResetPasswordPresenter.java:56-58).

## `DocumentAssetQuickUploadPresenter`

plugin-or-infrastructure, not UI; complexity 2, effort S; used by 1.

- **Component:** `none (not UI: no view of its own; it opens unit 22 as a dialog)`
- **Controller:** `stroom.document.asset.client.component.DocumentAssetQuickUploadController`
- **Template:** `none` → `none (built in Java because it has no view)`
- **Goes:** `stroom-core-client/src/main/java/stroom/document/asset/client/presenter/DocumentAssetQuickUploadPresenter.java (becomes the controller)`; AppRoot injection (DocumentAssetQuickUploadPresenter.java:83,96): owned by its caller via make() and opens the dialog with its own makeDialog
- **Names:** Not a presenter despite its name; DocumentAssetQuickUploadController (no clash). Only caller: FloorMapLayerStyleController (wave 7).

**Stays in Java:** REST fetchDraftAssets / upload (needs .owner(this)), name clash suffixing, error alerts.

## `UserRefPopupPresenter`

dialog, presenter; complexity 3, effort M; used by 7.

- **Component:** `stroom.security.client.component.UserRefPopup`
- **Controller:** `stroom.security.client.component.UserRefPopupController`
- **Template:** `none (uses the shared QuickFilterDialogViewImpl.ui.xml)` → `stroom-core-client/src/main/java/stroom/security/client/component/UserRefPopup.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/security/client/presenter/UserRefPopupPresenter.java (becomes UserRefPopupController)`; `QuickFilterDialogView and PagerView injection (UserRefPopupPresenter.java:99-100)`; `QuickFilterUiHandlers implementation (onFilterChange becomes the component's setOnFilterChange callback)`
- **Names:** No clash. Its component is a thin template that declares unit 0's QuickFilterDialog with the grid inside it.

| Into the template | What | Detail |
| --- | --- | --- |
| UserRefPopupPresenter.java:99 | component-declaration | <v:QuickFilterDialog ui:field="dialog" accessibleName="Filter users and groups"> (unit 0) as the root |
| UserRefPopupPresenter.java:100,111,124 | component-declaration | <dg:PagerViewImpl ui:field="pager"><dg:DataGrid ui:field="grid" tableName="Users" accessibleName="Users and groups"/></dg:PagerViewImpl> inside the QuickFilterDialog (DataGrid/PagerView, wave 5) |
| UserRefPopupPresenter.java:108,113,114 | aria | accessibleName="Filter users and groups", tableName="Users", accessibleName="Users and groups" as template attributes |

**Stays in Java:** columns (UserRefPopupPresenter.java:169-213): cell/heading builders; quick filter help (UserRefPopupPresenter.java:129-136): needs the help URL from UiConfig; selection event manager wiring and Enter/double-click handling (UserRefPopupPresenter.java:115-161); announce text "<name> chosen" (UserRefPopupPresenter.java:146); caption (UserRefPopupPresenter.java:247): passed in.

## `ColumnValuesFilterPresenter`

dialog, presenter; complexity 3, effort M; used by 7.

**Since the merge.** Mechanical rename: DataGrid, DataGridSelectionEventManager, and PagerView in its new package. The template did not change.

- **Component:** `stroom.dashboard.client.component.ColumnValuesFilter`
- **Controller:** `stroom.dashboard.client.component.ColumnValuesFilterController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/ColumnValuesFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnValuesFilter.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnValuesFilterPresenter.java (with its nested ColumnValuesFilterView, :307-314); `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnValuesFilterViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnValuesFilterUiHandlers.java`; `TableModule.java:94-97 bindPresenterWidget(ColumnValuesFilterPresenter, ColumnValuesFilterView, ColumnValuesFilterViewImpl)`
- **Names:** No clash. ColumnValuesDataSupplier and TableColumnValuesDataSupplier are data suppliers, not UI, so they stay in stroom.dashboard.client.table.

| Into the template | What | Detail |
| --- | --- | --- |
| ColumnValuesFilterViewImpl.java:59 | aria | setAccessibleName("Filter values") repeats accessibleName="Filter values" already in the template (:13). Delete the Java line |
| ColumnValuesFilterViewImpl.java:61 | aria | separator aria-hidden: replace <g:Label ui:field="separator" styleName="gwt-Label" text="-"/> (:9) with <g:HTML styleName="gwt-Label"><span aria-hidden="true">-</span></g:HTML>, so the field and the Java go |
| ColumnValuesFilterPresenter.java:94-98,110-111 / template :14 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="dock-max form-control-border form-control-background overflow-hidden"><dg:DataGrid ui:field="list" profile="LISTBOX" accessibleName="Values"/></dg:PagerPanel> replaces the data SimplePanel. This needs wave 5's PagerView widget and DataGrid's no-arg constructor (DataGrid.java:192 takes the bus today) |
| ColumnValuesFilterViewImpl.ui.xml:6 | css-class | ColumnValuesFilterViewImpl (components.css:1876-1897, 2112, 2118) becomes column-values-filter |

**Stays in Java:** Tick column with ColumnValueCell (wave 1), and row styles from the conditional formatting rules (ColumnValuesFilterPresenter.java:90,97-101,264-271); icon-colour__blue state class on the table's filter button (ColumnValuesFilterPresenter.java:256-258). It is state on another component's element, so it is better passed out through a callback to the table; Caption 'Filter Values' and popup position (:164-174); Name filter text (:187).

## `ExportConfigPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.importexport.client.component.ExportConfigDialog`
- **Controller:** `stroom.importexport.client.component.ExportConfigDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/importexport/client/view/ExportConfigViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/importexport/client/component/ExportConfigDialog.ui.xml`
- **Goes:** `ExportConfigPresenter.ExportConfigView (nested)`; `stroom-core-client/src/main/java/stroom/importexport/client/view/ExportConfigViewImpl.java`; `stroom-core-client/src/main/java/stroom/importexport/client/presenter/ExportConfigUiHandlers.java`; stroom-core-client/src/main/java/stroom/importexport/client/gin/ImportExportConfigModule.java:55-58 bindPresenterWidget(ExportConfigPresenter...)
- **Names:** ExportConfig clashes with stroom.importexport.impl.ExportConfig (server config), so ExportConfigDialog. Units 29 and 30 have no clash and keep ImportConfig / ImportConfigConfirm.

| Into the template | What | Detail |
| --- | --- | --- |
| ExportConfigPresenter.java:92 | component-declaration | <b:ToolbarToggleButton ui:field="filter" svg="FILTER" title="Filter Document Types"/> inside <b:Toolbar ui:field="toolbar" name="Export options"> (ui.xml:14) |
| ExportConfigViewImpl.ui.xml:16 / ExportConfigPresenter.java:94 | component-declaration | <ex:EntityCheckTree ui:field="tree"/> (wave 8) inside the MaxScrollPanel instead of setTreeView |
| ExportConfigViewImpl.ui.xml:12 | css-class | exportConfigViewImpl-filter has no CSS rule: drop it |

**Stays in Java:** filter toggle state (ExportConfigPresenter.java:105-114); TypeFilter popup (wave 10) opened at the button: a popup, not declared; quick filter context handler and filter error (controller).

## `ChangeStatusPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.ChangeStatusDialog`
- **Controller:** `stroom.annotation.client.component.ChangeStatusDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/ChangeStatusViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeStatusDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/ChangeStatusPresenter.java (with its nested ChangeStatusView, :147-)`; `stroom-core-client/src/main/java/stroom/annotation/client/ChangeStatusViewImpl.java`; `stroom-core-client/src/main/java/stroom/annotation/client/ChangeStatusUiHandlers.java`; `AnnotationModule.java:82 bindPresenterWidget(ChangeStatusPresenter, ChangeStatusView, ChangeStatusViewImpl)`
- **Names:** `ChangeStatus` is free, but it is named ChangeStatusDialog to pair with ChangeAssignedToDialog (unit 596, which had to change because of the shared ChangeAssignedTo). ChangeStatusDialog is free.

**Stays in Java:** Status text, 'None' and the annotationLabel--empty state class (ChangeStatusViewImpl.java:56-64); The ChooserPresenter (wave 6 Chooser) is a popup positioned under the block (ChangeStatusPresenter.java:58-79,136-145): the controller makes it and shows it, so it is not declared, and it is wrongly in the declares list. Its accessible name 'Statuses' (:70) stays a setter on it.

## `FindPresenter`

dialog, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.explorer.client.component.FindPanel (unit 33)`
- **Controller:** `stroom.explorer.client.component.FindController`
- **Template:** `none` → `none (built in Java because it shows unit 33's FindPanel, whose template is FindPanel.ui.xml)`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/FindPresenter.java (becomes FindController, taking in AbstractFindPresenter's body); `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:154-155 bindPresenter(FindPresenter.class)`
- **Names:** Find would be a bare, vague name and the component is shared, so only FindController (no clash). Made on ShowFindEvent by its area's plugin/screens class.

| Into the template | What | Detail |
| --- | --- | --- |
| FindPresenter.java:53 | aria | accessibleName="Find by name" and the fixed FILTER_DESCRIPTION (FindPresenter.java:38-40) could be FindPanel template attributes, but FindPanel is shared with Recent Items and the annotation screens, so they stay as setFilterGuidance calls from the controller |

**Stays in Java:** filter help (FindPresenter.java:55-61): needs UiConfig's help URL; tree filter setup, caption "Find" and size (FindPresenter.java:67-93).

## `RecentItemsPresenter`

dialog, presenter; complexity 2, effort S; used by 2.

- **Component:** `stroom.explorer.client.component.FindPanel (unit 33)`
- **Controller:** `stroom.explorer.client.component.RecentItemsController`
- **Template:** `none` → `none (built in Java because it shows unit 33's FindPanel)`
- **Goes:** stroom-core-client/src/main/java/stroom/explorer/client/presenter/RecentItemsPresenter.java (becomes RecentItemsController, extending FindController); `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:156-157 bindPresenter(RecentItemsPresenter.class)`
- **Names:** RecentItems is the client model in this unit (stroom.explorer.client.presenter.RecentItems), kept as it is (it may move to stroom.explorer.client), so the component name is not reused: RecentItemsController only.

**Stays in Java:** setFilterGuidance("Filter recent items by name", null) (RecentItemsPresenter.java:46): FindPanel is shared; recent items filter, caption "Recent Items" and size (RecentItemsPresenter.java:55-73).

## `AiAttachmentDataPresenter`

dialog, presenter; complexity 2, effort S; used by 1.

- **Component:** `stroom.ai.client.component.AiAttachmentData`
- **Controller:** `stroom.ai.client.component.AiAttachmentDataController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/ai/client/component/AiAttachmentData.ui.xml (new; today the presenter's view is a bare injected PagerView)`
- **Goes:** stroom-core-client/src/main/java/stroom/ai/client/AiAttachmentDataPresenter.java (no GIN binding: it is made by GIN from the shared PagerView binding)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AiAttachmentDataPresenter.java:63-65 | component-declaration | <dg:PagerPanel ui:field="pager" addStyleNames="form-control-border form-control-background"><dg:DataGrid ui:field="grid" tableName="Attachment Data"/></dg:PagerPanel>. This needs wave 5's PagerView widget and a no-arg DataGrid |
| AiAttachmentDataPresenter.java:64 | aria | setTableName("Attachment Data") becomes tableName="Attachment Data" (DataGrid.setTableName, DataGrid.java:956) |
| AiAttachmentDataPresenter.java:121 | css-class | "form-control-border form-control-background", added on every show, goes on the PagerView in the template once |

**Stays in Java:** Columns made from the server's header row (AiAttachmentDataPresenter.java:134-149); Caption from the attachment's description (:124-126).

## `UserAndGroupHelper`

model-or-util, smart class; complexity 3, effort M; used by 7.

**Since the merge.** selectUserIfShown(...) deleted (it used GWT's cellview DataGrid, TableRowElement and FocusUtil.focusRow, gone with GWT's cell tables); no other grid code left. 365 -> 321 lines. Menu building still via stroom.widget.menu.client.presenter.MenuBuilder/Item, unaffected by the MenuManager rename.

- **Component:** `none (not UI: the static header/icon/menu helpers stay in stroom.security.client.presenter.UserAndGroupHelper until their list callers move, then move to stroom.security.client.component)`
- **Controller:** `stroom.security.client.component.UserAndGroupActions`
- **Template:** `none` → `none (built in Java because it has no view: it builds grid header SafeHtml, cell icons and menu items, and runs delete/edit actions)`
- **Goes:** UserAndGroupHelper.onDelete (UserAndGroupHelper.java:129-165) and onEditUserOrGroup (167-194), moved to UserAndGroupActions as instance methods
- **Names:** UserAndGroupHelperController names a class that controls no component; the REST and dialog parts become a controller named for what it does, UserAndGroupActions (no clash). The pure static helpers (buildSingleIconHeader, buildUserAndGroupIconHeader, mapUserTypeToIcon, buildUserActionMenu, column names) keep the UserAndGroupHelper name.

**Stays in Java:** header SafeHtml (UserAndGroupHelper.java:75-110) with classes single-icon-column-header (data-grid.css) and two-icon-column-header: grid header cells stay SafeHtml per the design; confirm/alert text built from the user (UserAndGroupHelper.java:133-160); Edit User Group dialog (UserAndGroupHelper.java:171-193): the controller opens CreateNewGroup (wave 9) with makeDialog(createNewGroupProvider) instead of an unowned provider.get(); REST delete gets .owner(this).

## `ExplorerNodeRemoveTagsPresenter`

dialog, presenter; complexity 2, effort S; used by 4.

- **Component:** `stroom.explorer.client.component.ExplorerNodeRemoveTags`
- **Controller:** `stroom.explorer.client.component.ExplorerNodeRemoveTagsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/ExplorerNodeRemoveTagsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerNodeRemoveTags.ui.xml`
- **Goes:** `ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView (nested)`; `stroom-core-client/src/main/java/stroom/explorer/client/view/ExplorerNodeRemoveTagsViewImpl.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:145-148 bindPresenter(ExplorerNodeRemoveTagsPresenter...)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ExplorerNodeRemoveTagsViewImpl.java:53 | structure | multiSelect="true" on <sl:SelectionList ui:field="nodeTagsList"> (ui.xml:15) |
| ExplorerNodeRemoveTagsViewImpl.ui.xml:6,9 | css-class | removeNodeTags and removeNodeTagsNodeTagsFrmGrp have no CSS rule: drop; removeNodeTagsListsContainer (components.css:1684) may be renamed explorer-node-remove-tags__lists |

**Stays in Java:** tag list contents and selection (data); caption (ExplorerNodeRemoveTagsPresenter.java:115): built from the document count.

## `ResultStorePresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.query.client.component.SearchResultStores`
- **Controller:** `stroom.query.client.component.SearchResultStoresController`
- **Template:** `stroom-core-client/src/main/resources/stroom/query/client/view/ResultStoreViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/query/client/component/SearchResultStores.ui.xml`
- **Goes:** `ResultStorePresenter.ResultStoreView (ResultStorePresenter.java:128-133)`; `stroom-core-client/src/main/java/stroom/query/client/view/ResultStoreViewImpl.java`; `stroom-core-client/src/main/java/stroom/query/client/gin/QueryModule.java:102-105 bindPresenterWidget(ResultStorePresenter...)`
- **Names:** ResultStore clashes with stroom.query.common.v2.ResultStore and ResultStoreManager is also taken (stroom.query.common.v2.ResultStoreManager), so SearchResultStores (the dialog's caption is "Search Result Stores").

| Into the template | What | Detail |
| --- | --- | --- |
| ResultStoreViewImpl.java:47-49 | aria | tabindex="0" role="region" aria-label="Info": replace the MaxScrollPanel with <g:HTMLPanel styleName="max dashboard-panel"><div ui:field="info" class="max scroll-region" tabindex="0" role="region" aria-label="Info"/></g:HTMLPanel> (scroll-region, components.css:1921), the div scrolling (overflow:auto in the scroll-region rule) |
| ResultStoreViewImpl.ui.xml:8 | component-declaration | <q:ResultStoreList ui:field="list" addStyleNames="max dashboard-panel overflow-hidden"/> (wave 10) in place of tableContainer |
| ResultStoreViewImpl.ui.xml:5 | css-class | rename resultStoreViewImpl to search-result-stores (components.css:563) |

**Stays in Java:** store details table (ResultStorePresenter.java:58-103): TableBuilder rows built from the selected store; refresh on show, caption and size (ResultStorePresenter.java:117-125).

## `ExplorerNodeEditTagsPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.explorer.client.component.ExplorerNodeEditTags`
- **Controller:** `stroom.explorer.client.component.ExplorerNodeEditTagsController`
- **Template:** `stroom-core-client/src/main/resources/stroom/explorer/client/view/ExplorerNodeEditTagsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/explorer/client/component/ExplorerNodeEditTags.ui.xml`
- **Goes:** `ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsView (nested)`; `stroom-core-client/src/main/java/stroom/explorer/client/view/ExplorerNodeEditTagsViewImpl.java`; `stroom-app-gwt/src/main/java/stroom/app/client/gin/AppModule.java:141-144 bindPresenter(ExplorerNodeEditTagsPresenter...)`
- **Names:** No clash. TagListModel (in this unit, also used by unit 37) is a model: keep the name, move it to stroom.explorer.client.component beside its users.

| Into the template | What | Detail |
| --- | --- | --- |
| ExplorerNodeEditTagsViewImpl.java:102-106 | component-declaration | <b:ToolbarButton ui:field="addFromAllTagsButton" svg="ARROW_RIGHT" title="Add Selected Tags from All Known Tags" enabled="false"/> in nodeTagsButtonPanel (ui.xml:51) |
| ExplorerNodeEditTagsViewImpl.java:108-112 | component-declaration | <b:ToolbarButton ui:field="removeButton" svg="ARROW_LEFT" title="Remove Selected Tags" enabled="false"/> in nodeTagsButtonPanel |
| ExplorerNodeEditTagsViewImpl.java:115-119 | component-declaration | <b:ToolbarButton ui:field="clearInputButton" svg="CLEAR" title="Clear Entered Tags" addStyleNames="clear"/> in clearButtonPanel (ui.xml:27); the class clear is in components.css:1673 |
| ExplorerNodeEditTagsViewImpl.java:121-125 | component-declaration | <b:ToolbarButton ui:field="addFromInputButton" svg="ADD" title="Add Entered Tags" enabled="false"/> in inputButtonPanel (ui.xml:31) |
| ExplorerNodeEditTagsViewImpl.java:127,142 | structure | multiSelect="true" on both SelectionLists (ui.xml:48,63) |
| ExplorerNodeEditTagsViewImpl.java:140,156 | text | title="Enter tags manually or filter 'All Known Tags'" on the TextBox and title="All tags currently in use across all documents" on allTagsList |
| ExplorerNodeEditTagsViewImpl.ui.xml:11,26,40,57 | css-class | editNodeTagsAddTagsFrmGrp and editNodeTagsAddTagsInput have no CSS rule: drop; editNodeTags* classes that have rules (components.css:1662-1689) may be renamed to explorer-node-edit-tags__... (BEM) |

**Stays in Java:** node tags FormGroup label "Tags to add to N Documents"/"Document Tags" (ExplorerNodeEditTagsViewImpl.java:273-275): count from data; all tags label "(filtered)" suffix and clear button visibility (ExplorerNodeEditTagsViewImpl.java:309-315); button enabled states (ExplorerNodeEditTagsViewImpl.java:304-307); list contents (data).

## `AiChatHistoryPresenter`

dialog, presenter; complexity 2, effort M; used by 3.

**Since the merge.** The large commented-out old ViewImpl (MyCellTable/BasicSelectionEventManager) was removed, but other commented-out code remains.

- **Component:** `stroom.ai.client.component.AiChatHistory`
- **Controller:** `stroom.ai.client.component.AiChatHistoryController`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/AiChatHistoryViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/AiChatHistory.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/ai/client/AiChatHistoryPresenter.java (with its nested AiChatHistoryView, :202-219, and the dead commented code, :107-200); `stroom-core-client/src/main/java/stroom/ai/client/AiChatHistoryViewImpl.java`; `stroom-core-client/src/main/java/stroom/ai/client/AiChatHistoryUiHandlers.java`; `AskStroomAIModule.java:62-65 bindPresenterWidget(AiChatHistoryPresenter, AiChatHistoryView, AiChatHistoryViewImpl)`
- **Names:** No clash. Its template copies FindViewImpl's layout and classes. It could instead use the shared find component of unit 33 (AbstractFindPresenter), with a slot for its result list, rather than keep a second copy.

| Into the template | What | Detail |
| --- | --- | --- |
| AiChatHistoryViewImpl.java:52 | aria | setAccessibleName("Filter chats") repeats accessibleName in the template (:7). Delete the Java line |
| AiChatHistoryViewImpl.java:53,81-87 | css-class | setDialogMode(true) adds FindViewImpl--dialog, which the template root already has (:5). It is always a dialog, so delete setDialogMode |
| AiChatHistoryPresenter.java:62-63 / template :9 | component-declaration | <ai:AiChatHistoryResultList ui:field="results" addStyleNames="form-control-border form-control-background"/> (wave 10) goes inside the FindViewImpl-bottom SimplePanel, keeping the DOM for the screenshots. This replaces setResultView and the Java addStyleName |

**Stays in Java:** Filter error text and quick filter context handler (AiChatHistoryPresenter.java:64-66); Caption 'Chat History' and OK handling in the controller (:156-170); Enter/Down key handling of the filter (:91-97).

## `ChangeAssignedToPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.ChangeAssignedToDialog`
- **Controller:** `stroom.annotation.client.component.ChangeAssignedToDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/ChangeAssignedToViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/ChangeAssignedToDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/ChangeAssignedToPresenter.java (with its nested ChangeAssignedToView, :126-)`; `stroom-core-client/src/main/java/stroom/annotation/client/ChangeAssignedToViewImpl.java`; `stroom-core-client/src/main/java/stroom/annotation/client/ChangeAssignedToUiHandlers.java`; `AnnotationModule.java:83-85 bindPresenterWidget(ChangeAssignedToPresenter, ChangeAssignedToView, ChangeAssignedToViewImpl)`
- **Names:** `ChangeAssignedTo` clashes with the shared request stroom.annotation.shared.ChangeAssignedTo, which this controller sends. Chose ChangeAssignedToDialog, and its pair ChangeStatus (unit 597) becomes ChangeStatusDialog to match. Both names are free.

| Into the template | What | Detail |
| --- | --- | --- |
| ChangeAssignedToViewImpl.java:47,62,85 / template :12 | component-declaration | AssignYourselfLink (wave 1) is a static helper today (AssignYourselfLink.java:29, final class) wired onto a g:Label. If wave 1 makes it a widget, declare <annotation:AssignYourselfLink ui:field="assignYourself"/> in place of the Label and drop init(). SettingBlock and FormGroup are already declared (:7-8) |

**Stays in Java:** Assigned-to text, 'Nobody' and the annotationLabel--empty state class (ChangeAssignedToViewImpl.java:67-73); Assign Yourself enabled state (:61-63); UserRefPopup (same wave) is a popup that the controller makes and shows (ChangeAssignedToPresenter.java:118), not declared.

## `ConfigPropertyClusterValuesPresenter`

dialog, presenter; complexity 1, effort S; used by 3.

- **Component:** `stroom.config.global.client.component.ConfigPropertyClusterValues`
- **Controller:** `stroom.config.global.client.component.ConfigPropertyClusterValuesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/config/global/client/view/ConfigPropertyClusterValuesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/config/global/client/component/ConfigPropertyClusterValues.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/config/global/client/presenter/ConfigPropertyClusterValuesPresenter.java (with its nested ConfigPropertyClusterValuesView, :84-88); `stroom-core-client/src/main/java/stroom/config/global/client/view/ConfigPropertyClusterValuesViewImpl.java`; `stroom-core-client/src/main/java/stroom/config/global/client/presenter/ConfigPropertyClusterValuesUiHandlers.java (empty)`; MonitoringModule.java:128-131 bindPresenterWidget(ConfigPropertyClusterValuesPresenter, ConfigPropertyClusterValuesView, ConfigPropertyClusterValuesViewImpl)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ConfigPropertyClusterValuesViewImpl.java:51-54 / template :4 | component-declaration | <cfg:ConfigPropertyClusterValuesList ui:field="list"/> (wave 10) inside the SimplePanel (styleName max default-min-sizes stroom-control), replacing setList |

**Stays in Java:** Caption 'Cluster values - <property name>' and popup position (ConfigPropertyClusterValuesPresenter.java:64-73); List data from the effective-value map (:62).

## `ManageActivityPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.activity.client.component.ManageActivity`
- **Controller:** `stroom.activity.client.component.ManageActivityController`
- **Template:** `stroom-core-client/src/main/resources/stroom/activity/client/ManageActivityViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/activity/client/component/ManageActivity.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/activity/client/ManageActivityPresenter.java (with its nested ManageActivityView, :307-)`; `stroom-core-client/src/main/java/stroom/activity/client/ManageActivityViewImpl.java`; `stroom-core-client/src/main/java/stroom/activity/client/ManageActivityUiHandlers.java`; `ActivityModule.java:29 bindPresenterWidget(ManageActivityPresenter, ManageActivityView, ManageActivityViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ManageActivityViewImpl.java:57 | aria | setAccessibleName("Filter activities") repeats accessibleName in the template (:6). Delete the Java line |
| ManageActivityViewImpl.java:97-99 / template :7 | component-declaration | <act:ActivityList ui:field="list"/> (wave 10) inside the dataGrid SimplePanel, replacing setListView (ManageActivityPresenter.java:95) |

**Stays in Java:** Toolbar name 'Activity actions' and New/Edit/Delete buttons on the list's toolbar (ManageActivityPresenter.java:102-107), per the 'set of actions' rule; Quick filter tooltip from config and the filter error (ManageActivityViewImpl.java:59,73-80); Unused EventBus constructor parameter (ManageActivityViewImpl.java:54) is dropped. ActivityEdit (same wave) is opened as a dialog.

## `FindAnnotationPresenter`

dialog, presenter; complexity 2, effort S; used by 2.

- **Component:** `the shared find component of unit 33 (AbstractFindPresenter's FindViewImpl, in stroom.explorer.client.component under the name unit 33 chooses)`
- **Controller:** `stroom.annotation.client.component.FindAnnotationController`
- **Template:** `none` → `none (built in Java because it has no view of its own: it shows the shared find component, whose template is unit 33's)`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/FindAnnotationPresenter.java`; AnnotationModule.java:94-95 bindPresenter(FindAnnotationPresenter) (the shared FindView binding at AnnotationModule.java:88 goes with unit 33)
- **Names:** No clash. It only becomes a controller. Keep FindAnnotation free for no component; the controller makes unit 33's find component.

| Into the template | What | Detail |
| --- | --- | --- |
| FindAnnotationPresenter.java:35,82 | css-class | setDialogMode(false) in the constructor, then setDialogMode(true) on every show. It is only ever shown as a dialog, so the shared component is in dialog mode from the start (FindViewImpl--dialog) |
| FindAnnotationPresenter.java:38 | component-declaration | FindAnnotationList (wave 10) goes into the shared find component's result slot |

**Stays in Java:** form-control-border form-control-background on the result list (FindAnnotationPresenter.java:79). The list sits in another unit's slot, so the class is added once in the controller's constructor, not on every show; Caption 'Choose Annotation', the no-selection warning (:86-96) and Enter/Down filter keys (:115-121).

## `FloorMapInitPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapInit`
- **Controller:** `stroom.floormap.client.component.FloorMapInitController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapInitViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapInit.ui.xml`
- **Goes:** `FloorMapInitPresenter.FloorMapInitView (nested in FloorMapInitPresenter.java)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapInitViewImpl.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:123-125 bindPresenterWidget(FloorMapInitPresenter...)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapInitPresenter.java:129-133 / FloorMapInitViewImpl.ui.xml:26 | component-declaration | <dd:DropDownBox ui:field="factsStore" caption="Choose Facts Store"/> (wave 8) in place of factsStoreContainer; controller does make(docSelectionControllerProvider).attach(view.getFactsStore()) |
| FloorMapInitPresenter.java:136-140 / FloorMapInitViewImpl.ui.xml:32 | component-declaration | <dd:DropDownBox ui:field="eventsStore" caption="Choose Events Store"/> in place of eventsStoreContainer |
| FloorMapInitPresenter.java:130,137 | text | picker captions "Choose Facts Store", "Choose Events Store" as template attributes |
| FloorMapInitViewImpl.java:56-57 | aria | FloorMapAria.group(..., "Facts Store"/"Events Store") goes: with the box declared directly in its FormGroup the identity/label names it (FormGroup identity="floorMapInitFactsStore" already in template line 24); if the box's root is not labelable, use FormGroup controlNames="Facts Store" |

**Stays in Java:** included types and required permission on each picker (FloorMapInitPresenter.java:131-132, 138-139): type constants set by the controller; OK enablement and REST initialise call (controller).

## `ColumnFunctionEditorPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.dashboard.client.component.ColumnFunctionEditor`
- **Controller:** `stroom.dashboard.client.component.ColumnFunctionEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/ColumnFunctionEditorViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ColumnFunctionEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnFunctionEditorPresenter.java (with its nested ColumnFunctionEditorView, :204-209); `stroom-core-client/src/main/java/stroom/dashboard/client/table/ColumnFunctionEditorViewImpl.java`; `TableModule.java:72-75 bindPresenterWidget(ColumnFunctionEditorPresenter, ColumnFunctionEditorView, ColumnFunctionEditorViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ColumnFunctionEditorViewImpl.java:54-56 / template :9 | component-declaration | <ed:Editor ui:field="editor"/> (the wave 6 Editor) inside the editorContainer SimplePanel replaces setEditor(editorPresenter.getView()) (ColumnFunctionEditorPresenter.java:81) |
| ColumnFunctionEditorViewImpl.java:59-61 / template :8 | component-declaration | <qh:QueryHelp ui:field="queryHelp"/> (wave 8) inside queryHelpContainer replaces setQueryHelp (ColumnFunctionEditorPresenter.java:82). The controller attaches QueryHelpController and links it to the editor (:144) |
| ColumnFunctionEditorViewImpl.ui.xml:5,7,11,12 | css-class | columnFunctionEditorViewImpl and its -editorContainer, -labelPanel and -completionLabel elements (components.css:753-773, quick-filter.css:96) are named after the deleted ViewImpl. Rename them to column-function-editor, column-function-editor__editor and so on |

**Stays in Java:** Caption built from the column name (ColumnFunctionEditorPresenter.java:122); Editor options (mode STROOM_EXPRESSION, line numbers off, wrap on and so on: ColumnFunctionEditorPresenter.java:85-95), set deferred after show for the reason in the comment at :138-145; Expression text from the column (:105-109).

## `ManageGlobalPropertyEditPresenter`

dialog, presenter; complexity 4, effort M; used by 3.

- **Component:** `stroom.config.global.client.component.GlobalPropertyEdit`
- **Controller:** `stroom.config.global.client.component.GlobalPropertyEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/config/global/client/view/GlobalPropertyEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/config/global/client/component/GlobalPropertyEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/config/global/client/presenter/ManageGlobalPropertyEditPresenter.java (with its nested GlobalPropertyEditView, :545-); `stroom-core-client/src/main/java/stroom/config/global/client/view/GlobalPropertyEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/config/global/client/presenter/ManageGlobalPropertyEditUiHandlers.java`; `MonitoringModule.java:123-126 bindPresenterWidget(ManageGlobalPropertyEditPresenter, GlobalPropertyEditView, GlobalPropertyEditViewImpl)`
- **Names:** The suggested ManageGlobalPropertyEdit is free, but the view and template are already called GlobalPropertyEdit, and 'Manage' belongs to the list screen. Chose GlobalPropertyEdit / GlobalPropertyEditController. Both are free.

| Into the template | What | Detail |
| --- | --- | --- |
| GlobalPropertyEditViewImpl.java:117 | structure | copyNameButton.setSvg(SvgImage.COPY) becomes svg="COPY" on the ToolbarButton (:13) |
| GlobalPropertyEditViewImpl.java:90-93 | structure | readOnly="true" on the description, defaultValue, yamlValue and effectiveValue TextAreas (:19,:25,:47,:56) |
| GlobalPropertyEditViewImpl.java:95-97 | structure | enabled="false" on the requireRestart, requireUiRestart and readOnly CustomCheckBoxes (:76,:73,:79) |
| ManageGlobalPropertyEditPresenter.java:115-125 / template :55,:64 | component-declaration | In effectiveValueToolbar: <b:ToolbarButton ui:field="effectiveValueWarnings" svg="ALERT" title="Show Cluster Values" visible="false"/> and <b:ToolbarButton ui:field="effectiveValueInfo" svg="INFO" title="Show Cluster Values (all nodes have the same effective value)" visible="false"/>. In dataTypeToolbar: <b:ToolbarButton ui:field="dataTypeHelp" svg="HELP" title="Help on Data Types (opens in new tab)"/> |
| GlobalPropertyEditViewImpl.java:73-76 / template :46 | structure | yamlValueToolbar is unused (its TODO says so). Delete it from the template and the Java |
| GlobalPropertyEditViewImpl.ui.xml:11,20,26,37,39,48,57,63,69 | css-class | globalProperties is defined in no CSS file. Drop it. globalPropertyEditViewImpl-* (components.css:690-723) is named after the deleted ViewImpl: rename it to global-property-edit__name-actions, __text-area and so on |

**Stays in Java:** Warnings button title built from the node differences (ManageGlobalPropertyEditPresenter.java:198) and the info/warnings visibility (:183-204); Property values, source, data type text (:382-431,503); Password style and editability (GlobalPropertyEditViewImpl.java:180-210); Copy to Clipboard (copy_and_paste's Clipboard, :234). ConfigPropertyClusterValues (same wave) is opened as a dialog.

## `FloorMapClusterPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

**Since the merge.** Renames only (DataGrid, PagerView package). Nothing material.

- **Component:** `stroom.floormap.client.component.FloorMapClusterDialog`
- **Controller:** `stroom.floormap.client.component.FloorMapClusterDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapClusterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapClusterDialog.ui.xml`
- **Goes:** `FloorMapClusterPresenter.FloorMapClusterView (nested in FloorMapClusterPresenter.java)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapClusterViewImpl.java`; `stroom-core-client/src/main/java/stroom/floormap/client/presenter/FloorMapClusterUiHandlers.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:110-112 bindPresenterWidget(FloorMapClusterPresenter...)`; `PagerView injection (FloorMapClusterPresenter.java:114)`
- **Names:** FloorMapCluster clashes with the model stroom.floormap.client.cluster.FloorMapCluster (passed to show()), so FloorMapClusterDialog / FloorMapClusterDialogController.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapClusterPresenter.java:114,119,127 | component-declaration | <dg:PagerViewImpl ui:field="pager"><dg:DataGrid ui:field="grid" tableName="Cluster Members" emptyText="No members match the search and filters"/></dg:PagerViewImpl> inside <g:SimplePanel ui:field="data" styleName="dock-max overflow-hidden"> (DataGrid and PagerView, wave 5, were DataGridWidget/stroom.data.grid.client) |
| FloorMapClusterPresenter.java:120,126 | text | tableName="Cluster Members", emptyText="No members match the search and filters" as DataGrid attributes |
| FloorMapClusterPresenter.java:130-132 | component-declaration | pager toolbar name="Cluster member actions" and <b:ToolbarButton ui:field="trackButton" svg="LOCATE" title="Track this entity" enabled="false"/> in the pager's toolbar (needs PagerView to take toolbar children in templates) |
| FloorMapClusterViewImpl.java:80-88 | text | the Search help ("Matches anywhere in a member's name, id, type, area or group...") is fixed text: a help child of QuickFilter in the template (e.g. <v:QuickFilter><v:HelpHTML>...</v:HelpHTML>) once QuickFilter accepts one |

**Stays in Java:** grid columns and their cells (FloorMapClusterPresenter.java:283-328): SafeHtml/TextColumn built per row data; area/group filter options and their FormGroup visibility (FloorMapClusterViewImpl.java:118-131): data-driven; track button enabled state on selection (FloorMapClusterPresenter.java:136-137); caption "<label> in this cluster" (FloorMapClusterPresenter.java:220): built from data.

## `FloorMapLayerStylePresenter`

**Done in wave 7** (26 October 2026), with its view: `FloorMapLayerStyle` and `FloorMapLayerStyleController` in `stroom.floormap.client.component`, the controller made once by the editor and shown again for each layer with `ControllerDialog.show(this)`.

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.floormap.client.component.FloorMapLayerStyle`
- **Controller:** `stroom.floormap.client.component.FloorMapLayerStyleController`
- **Template:** `none` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapLayerStyle.ui.xml`
- **Goes:** FloorMapLayerStylePresenter.FloorMapLayerStyleView (nested in FloorMapLayerStylePresenter.java; DEFAULT_SHAPE_LABEL and GraphicMode move to the component); `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapLayerStyleViewImpl.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:165-167 bindPresenterWidget(FloorMapLayerStylePresenter...)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapLayerStyleViewImpl.java:103-128 | structure | the 6x2 Grid built in Java becomes an <g:HTMLPanel tag="table" styleName="floormap-layer-style-dialog" role="presentation"> (FloorMapAria.layoutTable, line 106) with one <tr ui:field="...Row"> per row and widgets in the second cell; keeps stroom-floormap.css:977-1004 td selectors working |
| FloorMapLayerStyleViewImpl.java:107-127 | text | row labels Graphic, Shape, Icon, Image, Colour, Preview in the first <td>s |
| FloorMapLayerStyleViewImpl.java:73-80 | component-declaration | <f:RadioGroup ui:field="modeGroup"/> (wave 2), <l:SelectionBox ui:field="shapeBox"/> (wave 2), <fm:FloorMapIconPicker ui:field="iconPicker"/> (wave 2), <colour:ColourBox ui:field="colourBox"/> (wave 2), <b:Button ui:field="uploadButton" text="Upload" title="Upload a new image into this document's assets"/> (wave 1), and <da:DocumentAssetDropDown ui:field="assetPicker"/> (wave 6) in place of assetPickerPanel/setAssetPickerView (FloorMapLayerStylePresenter.java:82) |
| FloorMapLayerStyleViewImpl.java:92-93,140-141 | text | Upload button text and title; aria-label "Upload a new image into this document's assets" duplicates the title (Button needs an ariaLabel setter, or drop it as the title already names it) |
| FloorMapLayerStyleViewImpl.java:101,168,176,183 | css-class | floormap-layer-style-preview, floormap-layer-style-image, floormap-layer-style-preview-row, floormap-layer-style-hint (stroom-floormap.css:1010-1092) on template elements |
| FloorMapLayerStyleViewImpl.java:181-183 | text | hint "Drawn at a fixed size on the map. Newly uploaded images appear after the document is saved." as <div class="floormap-layer-style-hint"> |
| FloorMapLayerStyleViewImpl.java:154-156 | text | radio items Shape/Icon/Image with their titles, if RadioGroup takes item children in templates; else they stay as addItem calls in the component's constructor |
| FloorMapLayerStyleViewImpl.java:137 | aria | FloorMapAria.group(assetPickerPanel, "Image"): role="group" aria-label="Image" on the image <div class="floormap-layer-style-image"> in the HTMLPanel |

**Stays in Java:** shape items from Shape.values() (FloorMapLayerStyleViewImpl.java:88-91): enum data; row dimming class floormap-layer-style-row-dimmed (FloorMapLayerStyleViewImpl.java:203-209): state, on the template's <tr> elements; enabled states per mode (FloorMapLayerStyleViewImpl.java:193-201); FloorMapAria.labelledByCell/labelledByCellInnerControl (FloorMapLayerStyleViewImpl.java:121-134): aria-labelledby needs ids generated at runtime; preview HTML (FloorMapLayerStyleViewImpl.java:289): built from the style; caption "Appearance - <type>" (FloorMapLayerStylePresenter.java:186-188); quick upload: controller makes DocumentAssetQuickUploadController (unit 23) with make(...) instead of quickUploadProvider.get() (FloorMapLayerStylePresenter.java:104).

## `CreateUserPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.security.client.component.CreateUser`
- **Controller:** `stroom.security.client.component.CreateUserController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/CreateUserViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/CreateUser.ui.xml`
- **Goes:** `CreateUserPresenter.CreateUserView (CreateUserPresenter.java:180-189)`; `stroom-core-client/src/main/java/stroom/security/client/view/CreateUserViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/client/presenter/CreateUserUiHandlers.java`; `stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:127-130 bindPresenterWidget(CreateUserPresenter...)`
- **Names:** No clash. CreateType enum moves with the component.

| Into the template | What | Detail |
| --- | --- | --- |
| CreateUserViewImpl.ui.xml:14 / CreateUserPresenter.java:131-147 | component-declaration | declare all three parts in place of the panel slot: <sec:CreateNewGroup ui:field="newGroup" visible="false"/> (wave 9), <sec:CreateExternalUser ui:field="externalUser"/> (unit 14), <sec:CreateMultipleUsers ui:field="multipleUsers" visible="false"/> (unit 15); the type choice shows one (setVisible) instead of swapping a view into a SimplePanel |
| CreateUserViewImpl.ui.xml:6,12,14 | css-class | rename createUserViewImpl / createUserViewImpl-selection / createUserViewImpl-panel to create-user / create-user__type / create-user__panel (components.css:1829) |

**Stays in Java:** create type items and visibility (CreateUserPresenter.java:63-79): depend on includeGroups; caption and size (CreateUserPresenter.java:67,73,105).

## `DocumentUserPermissionsEditPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.security.client.component.DocumentUserPermissionsEdit`
- **Controller:** `stroom.security.client.component.DocumentUserPermissionsEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/DocumentUserPermissionsEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsEdit.ui.xml`
- **Goes:** `DocumentUserPermissionsEditPresenter.DocumentUserPermissionsEditView (nested)`; `stroom-core-client/src/main/java/stroom/security/client/view/DocumentUserPermissionsEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/client/presenter/DocumentUserPermissionsEditUiHandler.java`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:153-156 bindPresenterWidget(DocumentUserPermissionsEditPresenter...)
- **Names:** No clash. Opens unit 18 as a dialog (makeDialog), which does not need it converted first.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentUserPermissionsEditViewImpl.java:63 | text | nonSelectString="[ none ]" on <l:SelectionBox ui:field="permission"> (ui.xml:19); drops NONE_TITLE |

**Stays in Java:** permission items DocumentPermission.LIST (DocumentUserPermissionsEditViewImpl.java:62); button visibility for folders (DocumentUserPermissionsEditViewImpl.java:79-80); FormGroup label "User Group"/"User" (DocumentUserPermissionsEditViewImpl.java:86-88).

## `BatchDocumentPermissionsEditPresenter`

dialog, presenter; complexity 3, effort M; used by 4.

- **Component:** `stroom.security.client.component.BatchDocumentPermissionsEdit`
- **Controller:** `stroom.security.client.component.BatchDocumentPermissionsEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/BatchDocumentPermissionsEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/BatchDocumentPermissionsEdit.ui.xml`
- **Goes:** `BatchDocumentPermissionsEditPresenter.BatchDocumentPermissionsEditView (nested)`; `stroom-core-client/src/main/java/stroom/security/client/view/BatchDocumentPermissionsEditViewImpl.java`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:169-172 bindPresenterWidget(BatchDocumentPermissionsEditPresenter...)
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BatchDocumentPermissionsEditViewImpl.ui.xml:10 | `<i:SelectionBox ui:field="documentPermissionChange" width="350px"/>` | static | new batch-document-permissions-edit__change { width: 350px } in components.css (no existing 350px class) |

| Into the template | What | Detail |
| --- | --- | --- |
| BatchDocumentPermissionsEditViewImpl.ui.xml:19 / BatchDocumentPermissionsEditPresenter.java:82 | component-declaration | <dd:DropDownBox ui:field="userRef"/> (wave 6) in place of the SimplePanel; controller attaches UserRefSelectionController |
| BatchDocumentPermissionsEditViewImpl.ui.xml:23 / BatchDocumentPermissionsEditPresenter.java:81 | component-declaration | <dd:DropDownBox ui:field="docRef"/> (wave 8) in place of the SimplePanel; controller attaches DocSelectionController |
| BatchDocumentPermissionsEditViewImpl.java:71 | text | nonSelectString="[ none ]" on the permission SelectionBox |
| BatchDocumentPermissionsEditViewImpl.java:118-125 | structure | the four optional groups start hidden: visible="false" on userRefLabel, docRefLabel, permissionLabel, docTypeLabel (the controls inside need no own visibility) |
| BatchDocumentPermissionsEditViewImpl.ui.xml:6 | css-class | documentPermissions has no CSS rule: drop it |

**Stays in Java:** change items and default SET_PERMISSION (BatchDocumentPermissionsEditViewImpl.java:72-73): enum list; per-change visibility in update(); document types from DocumentTypeCache (BatchDocumentPermissionsEditPresenter.java:80).

## `NodeGroupEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.node.client.component.NodeGroupEdit`
- **Controller:** `stroom.node.client.component.NodeGroupEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/node/client/view/NodeGroupEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/node/client/component/NodeGroupEdit.ui.xml`
- **Goes:** `NodeGroupEditPresenter.NodeGroupEditView (nested in NodeGroupEditPresenter.java)`; `stroom-core-client/src/main/java/stroom/node/client/view/NodeGroupEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/node/client/view/NodeGroupEditUiHandlers.java`; `stroom-core-client/src/main/java/stroom/monitoring/client/gin/MonitoringModule.java:97-100 bindPresenterWidget(NodeGroupEditPresenter...)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| NodeGroupEditViewImpl.java:55 | structure | enabled="false" on <g:TextBox ui:field="name"> (the name cannot be changed here) |
| NodeGroupEditViewImpl.ui.xml:26 / NodeGroupEditPresenter.java:73 | component-declaration | <n:NodeGroupStateList ui:field="list" addStyleNames="dock-max form-control-border form-control-background"/> (wave 10) in place of the SimplePanel slot |

**Stays in Java:** inclusion behaviour items (NodeGroupEditViewImpl.java:56-58): enum values; may stay in the component's constructor; values and caption (NodeGroupEditPresenter.java:130-138).

## `HttpTlsConfigPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.http.client.component.HttpTlsConfigEditor`
- **Controller:** `stroom.http.client.component.HttpTlsConfigEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/http/client/view/HttpTlsConfigViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/http/client/component/HttpTlsConfigEditor.ui.xml`
- **Goes:** `HttpTlsConfigPresenter.HttpTlsConfigView (nested)`; `stroom-core-client/src/main/java/stroom/http/client/view/HttpTlsConfigViewImpl.java`; `stroom-core-client/src/main/java/stroom/http/client/gin/HttpModule.java:34-36 bindPresenterWidget(HttpTlsConfigPresenter...)`
- **Names:** HttpTlsConfig clashes with the shared DTO stroom.util.shared.http.HttpTlsConfig that it edits, so HttpTlsConfigEditor.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| HttpTlsConfigViewImpl.ui.xml:13,21,51,59,67 | `width="400px" on TextBox/TextArea` | static | new http-config__text { width: 400px } in components.css, shared with unit 32 |

| Into the template | What | Detail |
| --- | --- | --- |
| HttpTlsConfigViewImpl.ui.xml:28,34 | component-declaration | SelectionBox (wave 2) for key store and trust store already declared |
| HttpTlsConfigViewImpl.ui.xml:14,22,52,60,68 | aria | title="Protocol" etc. duplicate the FormGroup labels (which name the controls by identity): drop the titles |

**Stays in Java:** credential items for the key/trust store boxes (HttpTlsConfigPresenter.java:111-112): fetched; values (read/write).

## `BatchProcessorFilterEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.processor.client.component.BatchProcessorFilterEdit`
- **Controller:** `stroom.processor.client.component.BatchProcessorFilterEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/BatchProcessorFilterEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/BatchProcessorFilterEdit.ui.xml`
- **Goes:** `BatchProcessorFilterEditPresenter.BatchProcessorFilterEditView (nested)`; `stroom-core-client/src/main/java/stroom/processor/client/view/BatchProcessorFilterEditViewImpl.java`; stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:143-146 bindPresenterWidget(BatchProcessorFilterEditPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| BatchProcessorFilterEditViewImpl.ui.xml:14 / BatchProcessorFilterEditPresenter.java:146 | component-declaration | <dd:DropDownBox ui:field="userRef"/> (wave 6) in place of the SimplePanel; controller attaches UserRefSelectionController |
| BatchProcessorFilterEditViewImpl.java:76-77 | structure | visible="false" on userRefLabel (the Run As User FormGroup) |
| BatchProcessorFilterEditViewImpl.ui.xml:6,10 | css-class | documentPermissions has no CSS rule (copied from unit 21): drop; rename field documentPermissionChange to change |

**Stays in Java:** change items and default ENABLE (BatchProcessorFilterEditViewImpl.java:50-51): enum list; Run As User visibility per change (update()).

## `FsVolumeEditPresenter`

dialog, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.data.store.impl.fs.client.component.FsVolumeEdit`
- **Controller:** `stroom.data.store.impl.fs.client.component.FsVolumeEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/store/impl/fs/client/view/FsVolumeEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/FsVolumeEditPresenter.java (with its nested FsVolumeEditView, :209-); `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/view/FsVolumeEditViewImpl.java`; `FsVolumeModule.java:31-34 bindPresenterWidget(FsVolumeEditPresenter, FsVolumeEditView, FsVolumeEditViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| FsVolumeEditViewImpl.java:86-88 / template :84-85 | component-declaration | <ed:Editor ui:field="configEditor"/> (wave 6) inside the ResizeLayoutPanel replaces setConfigView (FsVolumeEditPresenter.java:73). If the wave 6 Editor has a mode setter, mode="JSON" goes in the template too (:72) |

**Stays in Java:** Volume type and status items, path, byte limit formatted from the volume (FsVolumeEditPresenter.java:176-188); Caption from the caller (:81).

## `IndexVolumeEditPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.index.client.component.IndexVolumeEdit`
- **Controller:** `stroom.index.client.component.IndexVolumeEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/index/client/view/IndexVolumeEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/index/client/presenter/IndexVolumeEditPresenter.java (with its nested IndexVolumeEditView, :211-)`; `stroom-core-client/src/main/java/stroom/index/client/view/IndexVolumeEditViewImpl.java`; `IndexModule.java:54-56 bindPresenterWidget(IndexVolumeEditPresenter, IndexVolumeEditView, IndexVolumeEditViewImpl)`
- **Names:** No clash.

**Stays in Java:** Node names from the server (IndexVolumeEditViewImpl.java:62-65), path, state, byte limit formatted from the volume (IndexVolumeEditPresenter.java:181-192); The anonymous HasText adapter over the node SelectionBox (IndexVolumeEditViewImpl.java:68-80) becomes plain getNodeName/setNodeName setters on the component; Caption from the caller (IndexVolumeEditPresenter.java:76).

## `CopyDocumentPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.entity.client.component.CopyDocument`
- **Controller:** `stroom.entity.client.component.CopyDocumentController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/CopyDocumentViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/CopyDocument.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/CopyDocumentPresenter.java (with its nested CopyDocumentView, :138-)`; `stroom-core-client/src/main/java/stroom/entity/client/view/CopyDocumentViewImpl.java`; EntityModule.java:52-54 bindPresenter(CopyDocumentPresenter, CopyDocumentView, CopyDocumentViewImpl). DocumentDialogScreens keeps making it through a Provider
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| CopyDocumentViewImpl.java:79 | `view.asWidget().setWidth("100%")` | static | Existing class max (stroom.css:793, width and height 100%) on the declared EntityTree |
| CopyDocumentViewImpl.java:80 | `view.asWidget().setHeight("100%")` | static | Same max class |

| Into the template | What | Detail |
| --- | --- | --- |
| CopyDocumentViewImpl.java:78-82 / template :11 | component-declaration | <explorer:EntityTree ui:field="folders" addStyleNames="max"/> (wave 8) inside foldersInner, replacing setFolderView |

**Stays in Java:** Permission inheritance items and default DESTINATION (CopyDocumentViewImpl.java:50-55). SelectionBox has no template item syntax; Name value and visibility (several documents), caption (CopyDocumentPresenter.java:84,110).

## `CreateDocumentPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.entity.client.component.CreateDocument`
- **Controller:** `stroom.entity.client.component.CreateDocumentController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/CreateDocumentViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/CreateDocument.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/CreateDocumentPresenter.java (with its nested CreateDocumentView, :161-)`; `stroom-core-client/src/main/java/stroom/entity/client/view/CreateDocumentViewImpl.java`; `EntityModule.java:49-51 bindPresenter(CreateDocumentPresenter, CreateDocumentView, CreateDocumentViewImpl)`
- **Names:** No clash. DialogActionUiHandlers is shared, so it stays; this component uses setOnSubmit(Runnable) for Enter.

| Into the template | What | Detail |
| --- | --- | --- |
| CreateDocumentViewImpl.java:76-78 / template :10 | component-declaration | <explorer:EntityTree ui:field="folders"/> (wave 8) inside the foldersInner SimplePanel, replacing setFolderView |

**Stays in Java:** Permission inheritance items NONE/DESTINATION and default (CreateDocumentViewImpl.java:55-57); Name feedback message built from the doc type (CreateDocumentPresenter.java:135); Focus on attach (CreateDocumentViewImpl.java:53).

## `MoveDocumentPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.entity.client.component.MoveDocument`
- **Controller:** `stroom.entity.client.component.MoveDocumentController`
- **Template:** `stroom-core-client/src/main/resources/stroom/entity/client/view/MoveDocumentViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/entity/client/component/MoveDocument.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/entity/client/presenter/MoveDocumentPresenter.java (with its nested MoveDocumentView, :120-)`; `stroom-core-client/src/main/java/stroom/entity/client/view/MoveDocumentViewImpl.java`; `EntityModule.java:55-57 bindPresenter(MoveDocumentPresenter, MoveDocumentView, MoveDocumentViewImpl)`
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| MoveDocumentViewImpl.java:58 | `view.asWidget().setWidth("100%")` | static | Existing class max (stroom.css:793) on the declared EntityTree |
| MoveDocumentViewImpl.java:59 | `view.asWidget().setHeight("100%")` | static | Same max class |

| Into the template | What | Detail |
| --- | --- | --- |
| MoveDocumentViewImpl.java:57-61 / template :11 | component-declaration | <explorer:EntityTree ui:field="folders" addStyleNames="max"/> (wave 8) inside foldersInner |

**Stays in Java:** Permission inheritance items and default (MoveDocumentViewImpl.java:44-48); Caption from the caller (MoveDocumentPresenter.java:95).

## `FloorMapClusterViewImpl`

composite, tidy; complexity 2, effort S; used by 1.

- **Component:** `stroom.floormap.client.view.FloorMapClusterViewImpl`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapClusterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapClusterViewImpl.ui.xml`
- **Goes:** None in this wave. FloorMapModule.java:110-112 (bindPresenterWidget(FloorMapClusterPresenter, FloorMapClusterView, FloorMapClusterViewImpl)) goes when FloorMapClusterPresenter is split in its own wave
- **Names:** As a widget it keeps its name and package (rule 1), although it is still the view of the unsplit FloorMapClusterPresenter.

**Stays in Java:** Search help popup text (FloorMapClusterViewImpl.java:80-88). It is fixed, but QuickFilter only takes it as a SafeHtml provider, with no template attribute; Area/group options and their group visibility (:111-140), the filter guard (:65-70), and clearing the search text (:174).

## `DocumentUserPermissionsPresenter`

tab, presenter; complexity 3, effort M; used by 8.

- **Component:** `stroom.security.client.component.DocumentUserPermissionsTab`
- **Controller:** `stroom.security.client.component.DocumentUserPermissionsTabController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/DocumentUserPermissionsViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/DocumentUserPermissionsTab.ui.xml`
- **Goes:** `DocumentUserPermissionsPresenter.DocumentUserPermissionsView (nested)`; `stroom-core-client/src/main/java/stroom/security/client/view/DocumentUserPermissionsViewImpl.java`; stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:149-152 bindPresenterWidget(DocumentUserPermissionsPresenter...); getPermissionVisibility() exposing the box (DocumentUserPermissionsViewImpl.java:84-86): replaced by setPermissionVisibilityItems / setOnPermissionVisibility
- **Names:** DocumentUserPermissions clashes with the shared DTO stroom.security.shared.DocumentUserPermissions, which this client uses, so DocumentUserPermissionsTab (DocumentUserPermissionsTabProvider already uses that wording). Callers to move: DictionaryEditor, FolderTabs, AnnotationTagEditPresenter, CredentialEditPresenter (unit 1), DocumentPermissionsPlugin, DocumentUserPermissionsTabProvider.

| Into the template | What | Detail |
| --- | --- | --- |
| DocumentUserPermissionsViewImpl.ui.xml:38-39 | component-declaration | <sec:DocumentUserPermissionsList ui:field="list" addStyleNames="document-user-permissions-tab__list"/> (wave 10) in place of the SimplePanel slot |
| DocumentUserPermissionsPresenter.java:84 | structure | permission visibility items are a fixed list (PermissionShowLevel.ITEMS): may be added in the component's constructor rather than by the controller |
| DocumentUserPermissionsViewImpl.ui.xml:32,39,44 | css-class | rename documentUserPermissionsViewImpl-users / -list / documentUserPermissions-details to document-user-permissions-tab__users / __list / __details (components.css:1851-1875; documentUserPermissions-table there belongs to the list, wave 10) |

**Stays in Java:** details label suffix (DocumentUserPermissionsViewImpl.java:74-81): built from the user ref; details HTML (DocumentUserPermissionsViewImpl.java:70): built from data.

## `AnalyticNotificationEditPresenter`

dialog, presenter; complexity 3, effort M; used by 6.

- **Component:** `stroom.analytics.client.component.AnalyticNotificationEdit`
- **Controller:** `stroom.analytics.client.component.AnalyticNotificationEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/AnalyticNotificationEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/AnalyticNotificationEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/analytics/client/presenter/AnalyticNotificationEditPresenter.java (with its nested AnalyticNotificationEditView, :197-); `stroom-core-client/src/main/java/stroom/analytics/client/view/AnalyticNotificationEditViewImpl.java`; AnalyticsModule.java:70-72 bindPresenterWidget(AnalyticNotificationEditPresenter, AnalyticNotificationEditView, AnalyticNotificationEditViewImpl)
- **Names:** No clash. ChangeUiHandlers is shared, so it stays; this component uses setOnChange(Runnable).

| Into the template | What | Detail |
| --- | --- | --- |
| AnalyticNotificationEditViewImpl.java:60 | structure | enabled.setValue(true) becomes value="true" on <tickbox:TickBox ui:field="enabled"> (:20) |
| AnalyticNotificationEditViewImpl.java:61-63 | structure | min="1" max="1000" value="1" on <v:ValueSpinner ui:field="maxNotifications"> (:31). ValueSpinner.setMin/setMax exist (ValueSpinner.java:397,407) |
| AnalyticNotificationEditPresenter.java:179-188 / template :16 | component-declaration | Declare both destinations in place of the destinationContainer SimplePanel: <an:AnalyticStreamDestination ui:field="streamDestination" addStyleNames="form-group" visible="false"/> (already a component) and <an:AnalyticEmailDestination ui:field="emailDestination" addStyleNames="form-group" visible="false"/> (wave 8). Their controllers attach to them |

**Stays in Java:** Destination type items STREAM/EMAIL (AnalyticNotificationEditViewImpl.java:64-65). SelectionBox has no template item syntax; Which destination is visible (the type chosen); Config values read and written (AnalyticNotificationEditPresenter.java:81-85,168-172).

## `HttpClientConfigPresenter`

dialog, presenter; complexity 2, effort S; used by 5.

- **Component:** `stroom.http.client.component.HttpClientConfigEditor`
- **Controller:** `stroom.http.client.component.HttpClientConfigEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/http/client/view/HttpClientConfigViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/http/client/component/HttpClientConfigEditor.ui.xml`
- **Goes:** `HttpClientConfigPresenter.HttpClientConfigView (nested)`; `stroom-core-client/src/main/java/stroom/http/client/view/HttpClientConfigViewImpl.java`; `stroom-core-client/src/main/java/stroom/http/client/presenter/HttpClientConfigUiHandlers.java`; `stroom-core-client/src/main/java/stroom/http/client/gin/HttpModule.java:31-33 bindPresenterWidget(HttpClientConfigPresenter...)`
- **Names:** HttpClientConfig clashes with the shared DTO stroom.util.shared.http.HttpClientConfig, so HttpClientConfigEditor. Opens unit 31 as a dialog (makeDialog).

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| HttpClientConfigViewImpl.ui.xml:51,58,71 | `width="200px" on ValueSpinner` | static | new http-config__number { width: 200px } in components.css |
| HttpClientConfigViewImpl.ui.xml:78 | `width="400px" on the User Agent TextBox` | static | http-config__text (shared with unit 31) |
| HttpClientConfigViewImpl.ui.xml:87 | `width="200px" on the Set HTTP TLS Config Button` | static | http-config__number (or a separate http-config__button with the same width) |

| Into the template | What | Detail |
| --- | --- | --- |
| HttpClientConfigViewImpl.java:73-77 | structure | min="1" max="1000000" on maxConnections and maxConnectionsPerRoute, max="1000" on retries |
| HttpClientConfigViewImpl.ui.xml:79 | aria | title="User Agent" duplicates the FormGroup label: drop |

**Stays in Java:** read-only pass-through (HttpClientConfigViewImpl.java:86-100): becomes setReadOnly pushed by the controller from Contexts.READ_ONLY; values.

## `ProcessorProfileEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.processor.client.component.ProcessorProfileEdit`
- **Controller:** `stroom.processor.client.component.ProcessorProfileEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/processor/client/view/ProcessorProfileEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/processor/client/component/ProcessorProfileEdit.ui.xml`
- **Goes:** `ProcessorProfileEditPresenter.ProcessorProfileEditView (nested)`; `stroom-core-client/src/main/java/stroom/processor/client/view/ProcessorProfileEditViewImpl.java`; stroom-core-client/src/main/java/stroom/pipeline/client/gin/PipelineModule.java:155-158 bindPresenterWidget(ProcessorProfileEditPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ProcessorProfileEditViewImpl.java:51-54 / ui.xml:21 | component-declaration | <qv:TimeZoneBox ui:field="timeZone" addStyleNames="form-group"/> (wave 2) in place of the injected widget put in a SimplePanel |
| ProcessorProfileEditViewImpl.ui.xml:19 / ProcessorProfileEditPresenter.java:212 | component-declaration | <p:ProfilePeriodList ui:field="list" addStyleNames="dock-max"/> (wave 10) in place of the SimplePanel slot |

**Stays in Java:** node group model (ProcessorProfileEditPresenter.java:214-216): REST-backed list model set by the controller; name validation and caption (ProcessorProfileEditPresenter.java:102-160).

## `UserPreferencesPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.preferences.client.component.UserPreferencesDialog`
- **Controller:** `stroom.preferences.client.component.UserPreferencesDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/preferences/client/UserPreferencesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/preferences/client/component/UserPreferencesDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/preferences/client/UserPreferencesPresenter.java (with its nested UserPreferencesView, :230-237)`; `stroom-core-client/src/main/java/stroom/preferences/client/UserPreferencesViewImpl.java`; `stroom-core-client/src/main/java/stroom/preferences/client/UserPreferencesUiHandlers.java`; `UserPreferencesModule.java:47-50 bindPresenterWidget(UserPreferencesPresenter, UserPreferencesView, UserPreferencesViewImpl)`
- **Names:** `UserPreferences` clashes with the shared DTO stroom.ui.config.shared.UserPreferences, which this controller reads and writes (UserPreferencesPresenter.java:60,125,221). Chose UserPreferencesDialog / UserPreferencesDialogController. Neither name exists, and no other unit in wave 11 uses them.

| Into the template | What | Detail |
| --- | --- | --- |
| UserPreferencesViewImpl.java:54 | structure | setAsDefault.setIcon(SvgImage.OK) becomes icon="OK" on <b:Button ui:field="setAsDefault"> (template :12) |
| UserPreferencesViewImpl.java:55 | structure | revertToDefault.setIcon(SvgImage.UNDO) becomes icon="UNDO" on <b:Button ui:field="revertToDefault"> (template :13) |
| UserPreferencesViewImpl.ui.xml:11 | css-class | preferencesViewImpl-buttons (components.css:1060) is named after the deleted ViewImpl. Rename it to user-preferences-dialog__buttons in components.css when the template moves. linkTabPanelViewImpl (:7, components.css:727-735) is shared by every link-tab dialog, so it keeps its name until that family is renamed together |
| UserPreferencesViewImpl.ui.xml:8,10,12-13 | component-declaration | LinkTabBar, LayerContainerImpl and Button are already declared. Theme/Editor/KeyBindingPreferences (wave 8/10) and TimePreferencesPresenter are tab bodies: the controller makes them with make(...) and shows them in the layer container (a TabbedComponent<UserPreferences> candidate), not declared |

**Stays in Java:** Tab labels Theme, Editor, Date and Time, Keyboard Shortcuts (UserPreferencesPresenter.java:50-53): TabData added in Java, because LinkTabBar has no template syntax for tabs; setAsDefault visibility from MANAGE_PROPERTIES permission (UserPreferencesPresenter.java:83-84); Caption 'User Preferences', popup size and OK/Cancel handling stay in the controller (UserPreferencesPresenter.java:178-211); tabBar.setTabPanel(layerContainer.getElement()) wiring (UserPreferencesViewImpl.java:53) moves into the component's constructor.

## `AskStroomAiConfigPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.ai.client.component.AskStroomAiConfigDialog`
- **Controller:** `stroom.ai.client.component.AskStroomAiConfigDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/ai/client/AskStroomAiConfigViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/ai/client/component/AskStroomAiConfigDialog.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/ai/client/AskStroomAiConfigPresenter.java (with its nested AskStroomAiConfigView, :173-184)`; `stroom-core-client/src/main/java/stroom/ai/client/AskStroomAiConfigViewImpl.java`; `AskStroomAIModule.java:50-53 bindPresenterWidget(AskStroomAiConfigPresenter, AskStroomAiConfigView, AskStroomAiConfigViewImpl)`
- **Names:** `AskStroomAiConfig` clashes with the shared DTO stroom.ai.shared.AskStroomAiConfig, which this controller reads and writes (AskStroomAiConfigPresenter.java:99,123,162-163). Chose AskStroomAiConfigDialog / AskStroomAiConfigDialogController. Neither name exists.

| Into the template | What | Detail |
| --- | --- | --- |
| AskStroomAiConfigViewImpl.java:58 | structure | restoreFromDefaults.setVisible(true) is the default. Delete it |
| AskStroomAiConfigViewImpl.java:59 | structure | setDefaults.setVisible(false) becomes visible="false" on <b:Button ui:field="setDefaults"> (:15-17) |
| AskStroomAiConfigViewImpl.ui.xml:8,10,12,15 | component-declaration | LinkTabBar, LayerContainerImpl and Button are already declared. AiConfigGeneral and AiConfigTableAnalysis (wave 8) are tab bodies: the controller makes them and shows them in the layer container |

**Stays in Java:** Tab labels General and Table Analysis (AskStroomAiConfigPresenter.java:46-47), as TabData; Set As Default visibility from MANAGE_PROPERTIES (:107-111); The buttons are passed out as the TaskMonitorFactory (AskStroomAiConfigViewImpl.java:95,102). This becomes setOnRestoreFromDefaults/setOnSetDefaults Runnables, with the refreshing state set through the setRefreshing adapter.

## `FsVolumeGroupEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.data.store.impl.fs.client.component.FsVolumeGroupEdit`
- **Controller:** `stroom.data.store.impl.fs.client.component.FsVolumeGroupEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/store/impl/fs/client/view/FsVolumeGroupEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/component/FsVolumeGroupEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/presenter/FsVolumeGroupEditPresenter.java (with its nested FsVolumeGroupEditView, :287-); `stroom-core-client/src/main/java/stroom/data/store/impl/fs/client/view/FsVolumeGroupEditViewImpl.java`; `FsVolumeModule.java:36-39 bindPresenterWidget(FsVolumeGroupEditPresenter, FsVolumeGroupEditView, FsVolumeGroupEditViewImpl)`
- **Names:** No clash. Its ViewImpl and template are line for line the same as IndexVolumeGroupEdit's (unit 629) except for the FormGroup identity and the list type. They stay separate components because each declares a different list.

| Into the template | What | Detail |
| --- | --- | --- |
| FsVolumeGroupEditViewImpl.java:65-67 / template :12 | component-declaration | <fs:FsVolumeStatusList ui:field="list" addStyleNames="dock-max stroom-control"/> (wave 10) replaces the list SimplePanel and setListView (FsVolumeGroupEditPresenter.java:94) |

**Stays in Java:** Toolbar name 'Volume actions' and the New/Edit/Delete/Rescan buttons (FsVolumeGroupEditPresenter.java:82-92). They go on the list's toolbar, so per the 'set of actions' rule the component is given that toolbar and adds them; Enabled state from the selection (:171-172), name value, caption from the caller (:206).

## `SteppingFilterPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical renames: DataGridWidget to DataGrid (MASSIVE_LIST_PAGE_SIZE now on DataGrid) and DataGridWidgetSelectionEventManager to DataGridSelectionEventManager. No behaviour change.

- **Component:** `stroom.pipeline.stepping.client.component.SteppingFilterEditor`
- **Controller:** `stroom.pipeline.stepping.client.component.SteppingFilterEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pipeline/stepping/client/view/SteppingFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/component/SteppingFilterEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/pipeline/stepping/client/presenter/SteppingFilterPresenter.java (with its nested SteppingFilterView, :317-); `stroom-core-client/src/main/java/stroom/pipeline/stepping/client/view/SteppingFilterViewImpl.java`; `PipelineModule.java:134-135 bindPresenterWidget(SteppingFilterPresenter, SteppingFilterView, SteppingFilterViewImpl)`
- **Names:** `SteppingFilter` clashes with the server's stroom.pipeline.stepping.capture.SteppingFilter in stroom-pipeline, a stepping package of the same name. Chose SteppingFilterEditor, which is free. Note for wave 9: XPathFilterPresenter's planned name XPathFilter clashes with the shared DTO stroom.pipeline.shared.XPathFilter, which this unit uses (SteppingFilterPresenter.java:64,176). Use whatever name wave 9 chose.

| Into the template | What | Detail |
| --- | --- | --- |
| SteppingFilterPresenter.java:101-104,153 / template :10 | component-declaration | <dg:DataGrid ui:field="elementChooser" profile="LISTBOX" accessibleName="Elements" addStyleNames="pipelineElementChooser"/> inside the MaxScrollPanel's SimplePanel. This needs a no-arg DataGrid |
| SteppingFilterViewImpl.java:116-118 / template :28 | component-declaration | <st:XPathList ui:field="xPathList" addStyleNames="dock-max stroom-control"/> (wave 10) replaces setXPathList (SteppingFilterPresenter.java:154). The XPath filter editor (wave 9) is a dialog, so it is not declared |
| SteppingFilterViewImpl.java:59,65 | structure | setNonSelectString("") becomes nonSelectString="" on both SelectionBoxes (:17,:22) |
| SteppingFilterViewImpl.ui.xml:8 | css-class | steppingFilterViewImpl (components.css:1019-1022) becomes stepping-filter-editor |

**Stays in Java:** Severity and output-state items (SteppingFilterViewImpl.java:60-67). SelectionBox has no template item syntax; Element columns, icons and filter-on/off classes from the pipeline's element types (SteppingFilterPresenter.java:106-151); Toolbar name 'XPath actions' and Add/Edit/Delete buttons on the XPath list's toolbar (:89-98), per the 'set of actions' rule; Heading text = element name (SteppingFilterViewImpl.java:82).

## `IndexVolumeGroupEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.index.client.component.IndexVolumeGroupEdit`
- **Controller:** `stroom.index.client.component.IndexVolumeGroupEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/index/client/view/IndexVolumeGroupEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/index/client/component/IndexVolumeGroupEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/index/client/presenter/IndexVolumeGroupEditPresenter.java (with its nested IndexVolumeGroupEditView, :298-); `stroom-core-client/src/main/java/stroom/index/client/view/IndexVolumeGroupEditViewImpl.java`; `IndexModule.java:49-52 bindPresenterWidget(IndexVolumeGroupEditPresenter, IndexVolumeGroupEditView, IndexVolumeGroupEditViewImpl)`
- **Names:** No clash. Its view is the same as FsVolumeGroupEdit's (unit 573).

| Into the template | What | Detail |
| --- | --- | --- |
| IndexVolumeGroupEditViewImpl.java:65-67 / template :12 | component-declaration | <ix:IndexVolumeStatusList ui:field="list" addStyleNames="dock-max stroom-control"/> (wave 10) replaces the list SimplePanel and setListView |

**Stays in Java:** Toolbar name and New/Edit/Delete/Rescan buttons on the list's toolbar (IndexVolumeGroupEditPresenter.java:90-100), per the 'set of actions' rule; Enabled state (:183-184), name value, caption from the caller (:217).

## `AnnotationTagEditPresenter`

dialog, presenter; complexity 2, effort M; used by 4.

- **Component:** `stroom.annotation.client.component.AnnotationTagEdit`
- **Controller:** `stroom.annotation.client.component.AnnotationTagEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/AnnotationTagEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTagEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagEditPresenter.java (with its nested AnnotationTagEditView, :149-166)`; `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagEditUiHandlers.java`; `AnnotationModule.java:77-79 bindPresenterWidget(AnnotationTagEditPresenter, AnnotationTagEditView, AnnotationTagEditViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AnnotationTagEditViewImpl.java:63 | css-class | addStyleNames="conditionalFormatStyleSelection" on <l:SelectionBox ui:field="formattingStyle"> (:23) |

**Stays in Java:** Style items, default NONE and the swatch render function (AnnotationTagEditViewImpl.java:59-62); Tag text/style group visibility and label by tag type (AnnotationTagEditPresenter.java:67-72); DocumentUserPermissions (same wave) is a dialog (:135-143) made with makeDialog, so it is not declared.

## `PathwayEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.pathways.client.component.PathwayEdit`
- **Controller:** `stroom.pathways.client.component.PathwayEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/pathways/client/view/PathwayEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/pathways/client/component/PathwayEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/pathways/client/presenter/PathwayEditPresenter.java (with its nested PathwayEditView, :299-)`; `stroom-core-client/src/main/java/stroom/pathways/client/view/PathwayEditViewImpl.java`; `PathwaysModule.java:58-60 bindPresenterWidget(PathwayEditPresenter, PathwayEditView, PathwayEditViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| PathwayEditViewImpl.java:69-71 / template :13 | component-declaration | <pw:PathwayTree ui:field="tree" addStyleNames="max form-control-background form-control-border"/> (wave 7) inside the FormGroup, replacing the SimplePanel |
| PathwayEditViewImpl.java:74-76 / template :16 | component-declaration | <pw:ConstraintList ui:field="constraints" addStyleNames="max form-control-background form-control-border"/> (wave 10) |

**Stays in Java:** Name value and caption (PathwayEditPresenter.java:293); Dead commented code (PathwayEditPresenter.java:121,257-261) is deleted.

## `ReplaceSecretPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.credentials.client.component.ReplaceSecret`
- **Controller:** `stroom.credentials.client.component.ReplaceSecretController`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/ReplaceSecretViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/ReplaceSecret.ui.xml`
- **Goes:** `ReplaceSecretPresenter.ReplaceSecretView (ReplaceSecretPresenter.java:141-165)`; `stroom-core-client/src/main/java/stroom/credentials/client/view/ReplaceSecretViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:71-73 bindPresenterWidget(ReplaceSecretPresenter...)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ReplaceSecretViewImpl.ui.xml:21 | component-declaration | <datetimebox:DateTimeBox ui:field="expiryTime"/> already declared; its popup provider (ReplaceSecretPresenter.java:61 setPopupProvider) is replaced by make(dateTimeBoxControllerProvider).attach(view.getExpiryTime()) (DateTimeBox, wave 4) |
| ReplaceSecretViewImpl.ui.xml:15 | component-declaration | <chk:TickBox ui:field="credsExpire"/> already declared (wave 2); FormGroup (wave 1) already declared |

**Stays in Java:** warning text (ReplaceSecretViewImpl.java:68): built from the credential's name; secret slot <g:SimplePanel ui:field="secret"> (ReplaceSecretViewImpl.ui.xml:10): which secret component (KeyStoreSecret, KeyStoreFileSecret, AccessToken, SshKey, UsernamePassword, wave 7) is chosen at runtime by credential type through `SecretForms` (wave 7), so it cannot be declared statically; expiry enabled/disabled state (ReplaceSecretViewImpl.java:103-106); caption "Replace <type> Secret" (ReplaceSecretPresenter.java:100): built from data.

## `FloorMapObjectEditPresenter`

dialog, presenter; complexity 4, effort L; used by 3.

- **Component:** `stroom.floormap.client.component.FloorMapObjectEdit`
- **Controller:** `stroom.floormap.client.component.FloorMapObjectEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapObjectEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/component/FloorMapObjectEdit.ui.xml`
- **Goes:** `FloorMapObjectEditPresenter.FloorMapObjectEditView (nested in FloorMapObjectEditPresenter.java)`; `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapObjectEditViewImpl.java`; `stroom-core-client/src/main/java/stroom/floormap/client/gin/FloorMapModule.java:141-143 bindPresenterWidget(FloorMapObjectEditPresenter...)`; `the <ui:style> block (FloorMapObjectEditViewImpl.ui.xml:25-39)`
- **Names:** No clash. Note FloorMapObjectEditPresenter's Role enum (pathForRole) is a schema-role model, unrelated to ARIA.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapObjectEditViewImpl.ui.xml:26-30 (used at 130,131,149,150,162,163,175) | `.matrixBox { width: 80px !important; min-width: 80px !important; margin-right: 8px }` | static | new BEM class floormap-object-edit__number (width/min-width 80px, margin-right 8px) in stroom-floormap.css; no existing 80px class (components.css:1288 is commented out) |
| FloorMapObjectEditViewImpl.ui.xml:32-34 (used at 83) | `.fillDefault { margin-right: 12px }` | static | new floormap-object-edit__fill-default in stroom-floormap.css |
| FloorMapObjectEditViewImpl.ui.xml:36-38 (used at 132,151) | `.unitSuffix { white-space: nowrap }` | static | new floormap-object-edit__unit in stroom-floormap.css (no existing nowrap utility class) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapObjectEditViewImpl.java:219 | component-declaration | <da:DocumentAssetDropDown ui:field="chooseImg" addStyleNames="wide-control"/> (wave 6) in place of chooseImgContainer/setChooseImgView (FloorMapObjectEditPresenter.java:202); FloorMapAria.group("Image") then comes from FormGroup label="Image" with controlNames="Image" |
| FloorMapObjectEditViewImpl.java:221-223 | aria | controlNames="Default, Area fill colour" on the Area Fill Colour FormGroup (ui.xml:74) replaces group+label |
| FloorMapObjectEditViewImpl.java:227 | aria | group "Effective From Time": FormGroup controlNames="Effective From Time" (ui.xml:108) |
| FloorMapObjectEditViewImpl.java:229-242 | aria | controlNames="Position X in metres, Position Y in metres" (ui.xml:119), "Width in metres, Height in metres" (ui.xml:135), "Scale X, as a multiple of natural size, Scale Y, as a multiple of natural size" (ui.xml:154; commas inside a name need FormGroup to accept another separator or shorter names), "Rotation in degrees, counter-clockwise" (ui.xml:167) |
| FloorMapObjectEditViewImpl.java:244-245 | aria | aria-hidden units: make the "m" suffixes plain <span class="floormap-object-edit__unit" aria-hidden="true">m</span> in an HTMLPanel row instead of g:Label + FloorMapAria.hide |
| FloorMapObjectEditViewImpl.java:182 | structure | setAreaFieldsVisible(false): visible="false" on fillGroup, opacityGroup, verticesGroup (ui.xml:74,88,97) |
| FloorMapObjectEditViewImpl.java:183 | structure | fillDefaultCheck starts ticked: value="true" on <t:TickBox ui:field="fillDefaultCheck"> |
| FloorMapObjectEditViewImpl.java:180 | component-declaration | DateTimeBox (ui.xml:116, wave 4) keeps its declaration; setPopupProvider goes to make(dateTimeBoxControllerProvider).attach(...) |

**Stays in Java:** field values, matrix and size maths (FloorMapObjectEditViewImpl.java:300-698); size/scale group visibility (FloorMapObjectEditViewImpl.java:314-315) and area fields visibility (423-425): depend on the object; enabled state (FloorMapObjectEditViewImpl.java:703-716); caption (FloorMapObjectEditPresenter.java:316).

## `EditApiKeyPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.security.client.component.EditApiKey`
- **Controller:** `stroom.security.client.component.EditApiKeyController`
- **Template:** `stroom-core-client/src/main/resources/stroom/security/client/view/EditApiKeyViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/security/client/component/EditApiKey.ui.xml`
- **Goes:** `EditApiKeyPresenter.EditApiKeyView (nested in EditApiKeyPresenter.java)`; `stroom-core-client/src/main/java/stroom/security/client/view/EditApiKeyViewImpl.java`; `stroom-core-client/src/main/java/stroom/security/client/gin/SecurityModule.java:195-198 bindPresenterWidget(EditApiKeyPresenter...)`; `commented-out ownerLabel/ownerSelectionBox (EditApiKeyViewImpl.ui.xml:19-22)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| EditApiKeyViewImpl.ui.xml:23 / EditApiKeyViewImpl.java:167 | component-declaration | <dd:DropDownBox ui:field="owner" addStyleNames="w-100"/> (wave 6) in place of ownerPanel; controller attaches UserRefSelectionController |
| EditApiKeyViewImpl.java:101 | component-declaration | <b:ToolbarButton ui:field="copyButton" svg="COPY" title="Copy API Key to Clipboard"/> inside <b:Toolbar ui:field="apiKeyButtonPanel" name="API key actions"> (ui.xml:68) |
| EditApiKeyViewImpl.java:98-99 | structure | enabled="false" on apiKeyTextArea and prefixTextBox |
| EditApiKeyViewImpl.java:103,107 | text | "API Key copied to clipboard" as the label's text in the template with visible="false"; the timer toggles visibility instead of setting and clearing the text |

**Stays in Java:** mode-driven visibility and enabled states (EditApiKeyViewImpl.java:118-157); hash algorithm items (EditApiKeyViewImpl.java:95-97): enum values; values and reset (EditApiKeyViewImpl.java:228-235); captions per mode (EditApiKeyPresenter.java:99-107,159).

## `DataUploadPresenter`

dialog, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.data.client.component.DataUpload`
- **Controller:** `stroom.data.client.component.DataUploadController`
- **Template:** `stroom-core-client/src/main/resources/stroom/data/client/view/DataUploadViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/data/client/component/DataUpload.ui.xml`
- **Goes:** `DataUploadPresenter.DataUploadView (nested)`; `stroom-core-client/src/main/java/stroom/data/client/view/DataUploadViewImpl.java`; stroom-core-client/src/main/java/stroom/feed/client/gin/FeedModule.java:40 bindPresenterWidget(DataUploadPresenter, DataUploadView, DataUploadViewImpl)
- **Names:** No clash. Caller: MetaPresenter (provider).

| Into the template | What | Detail |
| --- | --- | --- |
| DataUploadViewImpl.ui.xml:16,21,27 | component-declaration | SelectionBox, DateTimeBox (attach DateTimeBoxController) and FileChooser (attach its controller) already declared |

**Stays in Java:** stream type items and default (DataUploadPresenter.java:164-173): fetched; meta data and submit (controller).

## `ImportConfigConfirmPresenter`

dialog, presenter; complexity 4, effort L; used by 3.

**Since the merge.** Renames only (DataGrid); template unchanged. Nothing material.

- **Component:** `stroom.importexport.client.component.ImportConfigConfirm`
- **Controller:** `stroom.importexport.client.component.ImportConfigConfirmController`
- **Template:** `stroom-core-client/src/main/resources/stroom/importexport/client/view/ImportConfigConfirmViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/importexport/client/component/ImportConfigConfirm.ui.xml`
- **Goes:** `ImportConfigConfirmPresenter.ImportConfigConfirmView (nested)`; `stroom-core-client/src/main/java/stroom/importexport/client/view/ImportConfigConfirmViewImpl.java`; stroom-core-client/src/main/java/stroom/importexport/client/gin/ImportExportConfigModule.java:48-51 bindPresenterWidget(ImportConfigConfirmPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| ImportConfigConfirmPresenter.java:112-117 / ImportConfigConfirmViewImpl.ui.xml:8 | component-declaration | <dg:DataGrid ui:field="dataGrid" tableName="Import Items" virtual="true" pageSize="MASSIVE" addStyleNames="dock-max form-control-border form-control-background"/> (wave 5) in place of the SimplePanel and setDataGrid; needs DataGrid's no-arg constructor and a page size setter |
| ImportConfigConfirmPresenter.java:118 / ui.xml:29 | component-declaration | <dd:DropDownBox ui:field="rootFolder"/> (wave 8) in place of the SimplePanel; controller attaches DocSelectionController (SYSTEM_DOC_REF, FOLDER_TYPE, VIEW, folder selection) |
| ImportConfigConfirmPresenter.java:119,121,122 | structure | value="false" on enableFilters, useImportNames, useImportFolders CustomCheckBoxes |
| ImportConfigConfirmPresenter.java:93 | component-declaration | TooltipPresenter (wave 3, Tooltip) is a popup shown over the info column, not declared; owned by the controller |

**Stays in Java:** grid columns (ImportConfigConfirmPresenter.java:299-490): built from the confirm list; enable-from date default System.currentTimeMillis() (ImportConfigConfirmPresenter.java:120); REST confirm/refresh.

## `BatchExecutionScheduleEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.BatchExecutionScheduleEdit`
- **Controller:** `stroom.analytics.client.component.BatchExecutionScheduleEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/BatchExecutionScheduleEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/BatchExecutionScheduleEdit.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/analytics/client/presenter/BatchExecutionScheduleEditPresenter.java (with its nested BatchExecutionScheduleEditView, :151-); `stroom-core-client/src/main/java/stroom/analytics/client/view/BatchExecutionScheduleEditViewImpl.java`; AnalyticsModule.java:91-93 bindPresenterWidget(BatchExecutionScheduleEditPresenter, BatchExecutionScheduleEditView, BatchExecutionScheduleEditViewImpl)
- **Names:** No clash. ProcessingStatusUiHandlers is shared, so it stays. Bug found: the ViewImpl injects and shows its own UserRefSelectionBoxPresenter (BatchExecutionScheduleEditViewImpl.java:109-116), which nothing owns, while the presenter owns a second one that is never shown (BatchExecutionScheduleEditPresenter.java:68-72), so its selection listener (:99) never fires. The DocSelectionBoxPresenter errorFeedPresenter (:64,70,97) is never shown either. Drop it, and drop DocSelectionBoxPresenter from the declares.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| BatchExecutionScheduleEditViewImpl.ui.xml:76 | `<g:SimplePanel width="45%">` | static | New class batchExecutionScheduleEdit-button { flex: 0 0 45%; } in components.css, beside .batchExecutionScheduleEdit-row (:85). The wrapper is kept so the layout matches |
| BatchExecutionScheduleEditViewImpl.ui.xml:79 | `<g:SimplePanel width="45%">` | static | Same batchExecutionScheduleEdit-button class (components.css) |

| Into the template | What | Detail |
| --- | --- | --- |
| BatchExecutionScheduleEditViewImpl.java:117 | structure | endTimeBox.setOptional(true) becomes optional="true" on <datetimebox:DateTimeBox ui:field="endTimeBox"> (:63) |
| BatchExecutionScheduleEditViewImpl.java:119-120 | structure | icon="OK" on applySelectionButton (:77) and icon="GENERATE" on applyFilteredButton (:80) |
| BatchExecutionScheduleEditViewImpl.java:283-287 / template :71 | component-declaration | <dd:DropDownBox ui:field="runAsUserBox" addStyleNames="w-100"/> (wave 6) replaces the SimplePanel and the Java addStyleName("w-100"). The controller attaches UserRefSelectionController |
| BatchExecutionScheduleEditPresenter.java:76-80 | component-declaration | ScheduleBox and DateTimeBox are already declared (:45,:54,:63). Their wave 4 controllers attach to them, instead of setPopupProvider/setSchedulePresenterProvider. SchedulePopup and DateTimePopup are opened by those controllers, not declared |
| BatchExecutionScheduleEditViewImpl.ui.xml:10 | css-class | documentPermissions is defined in no CSS file. Drop it |

**Stays in Java:** Label 'Schedule (<type>)' built from the schedule type (BatchExecutionScheduleEditViewImpl.java:208); Enabled/disabled state of each field from its tick box (:195-218); Edit summary text built from the values (:312-350); Node list from the server (:241-252).

## `ScheduledProcessEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.analytics.client.component.ScheduledProcessEdit`
- **Controller:** `stroom.analytics.client.component.ScheduledProcessEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/analytics/client/view/ScheduledProcessEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/analytics/client/component/ScheduledProcessEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ScheduledProcessEditPresenter.java`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ScheduledProcessEditView.java`; `stroom-core-client/src/main/java/stroom/analytics/client/presenter/ScheduledProcessEditUiHandlers.java`; `stroom-core-client/src/main/java/stroom/analytics/client/view/ScheduledProcessEditViewImpl.java`; `AnalyticsModule.java:79-81 bindPresenterWidget(ScheduledProcessEditPresenter, ScheduledProcessEditView, ScheduledProcessEditViewImpl)`
- **Names:** No clash. errorFeedPresenter (ScheduledProcessEditPresenter.java:73,83,97,136) is owned but never shown. Drop it, and drop DocSelectionBoxPresenter from the declares.

| Into the template | What | Detail |
| --- | --- | --- |
| ScheduledProcessEditViewImpl.java:76 | text | title="Set as the default processing node for all users" on <b:Button ui:field="setDefaultNode"> (:25-27) |
| ScheduledProcessEditViewImpl.java:74 | structure | optional="true" on <datetimebox:DateTimeBox ui:field="endTime"> (:43) |
| ScheduledProcessEditViewImpl.java:193-196 / template :47 | component-declaration | <dd:DropDownBox ui:field="runAsUser" addStyleNames="w-100"/> (wave 6) replaces the SimplePanel and the Java addStyleName("w-100"). UserRefSelectionController attaches, with context RUN_AS (ScheduledProcessEditPresenter.java:90) |
| ScheduledProcessEditPresenter.java:94-99 | component-declaration | The ScheduleBox/DateTimeBox controllers (wave 4) attach to the declared boxes (:33,:38,:43), replacing setPopupProvider/setSchedulePresenterProvider |

**Stays in Java:** Label 'Schedule (<type>)' (ScheduledProcessEditViewImpl.java:110), and start/end enabled for non-instant schedules (:99-111); Set Default visibility (permission, ScheduledProcessEditPresenter.java:94); Caption Create/Edit Schedule (:152-154), node list and values.

## `AnnotationTagCreatePresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.annotation.client.component.AnnotationTagCreate`
- **Controller:** `stroom.annotation.client.component.AnnotationTagCreateController`
- **Template:** `stroom-core-client/src/main/resources/stroom/annotation/client/AnnotationTagCreateViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/annotation/client/component/AnnotationTagCreate.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagCreatePresenter.java (with its nested AnnotationTagCreateView, :124-); `stroom-core-client/src/main/java/stroom/annotation/client/AnnotationTagCreateViewImpl.java`; `AnnotationModule.java:74-76 bindPresenterWidget(AnnotationTagCreatePresenter, AnnotationTagCreateView, AnnotationTagCreateViewImpl)`
- **Names:** No clash.

**Stays in Java:** Tag text group label and visibility by tag type (AnnotationTagCreatePresenter.java:60, AnnotationTagCreateViewImpl.java:67-70); Caption 'Create New <type>' (AnnotationTagCreatePresenter.java:69); AnnotationTagEdit (same wave) is opened as a dialog, not declared.

## `FloorMapObjectEditViewImpl`

composite, tidy; complexity 3, effort M; used by 1.

- **Component:** `stroom.floormap.client.view.FloorMapObjectEditViewImpl`
- **Controller:** `none`
- **Template:** `stroom-core-client/src/main/resources/stroom/floormap/client/view/FloorMapObjectEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/floormap/client/view/FloorMapObjectEditViewImpl.ui.xml`
- **Goes:** `None in this wave. FloorMapModule.java:141-143 goes with FloorMapObjectEditPresenter's wave`
- **Names:** As a widget it keeps its name and package.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| FloorMapObjectEditViewImpl.ui.xml:25-30 (used :130,:131,:149,:150,:162,:163,:175) | `ui:style .matrixBox { width: 80px !important; min-width: 80px !important; margin-right:…` | static | New global class stroom-floormap-object-edit__matrix-box in stroom-floormap.css, near .stroom-floormap-cluster-filters (:689). Templates use global classes, not ui:style |
| FloorMapObjectEditViewImpl.ui.xml:32-34 (used :83) | `ui:style .fillDefault { margin-right: 12px }` | static | stroom-floormap-object-edit__fill-default in stroom-floormap.css |
| FloorMapObjectEditViewImpl.ui.xml:36-38 (used :132,:151) | `ui:style .unitSuffix { white-space: nowrap }` | static | stroom-floormap-object-edit__unit in stroom-floormap.css (no nowrap utility class exists) |

| Into the template | What | Detail |
| --- | --- | --- |
| FloorMapObjectEditViewImpl.java:221,229,233,237,241 / template :82,:129,:148,:161,:174 | aria | role="group" and aria-label for the fill, position, size, scale and rotation rows: write each row as <div class="dock-container-horizontal align-center" role="group" aria-label="Position in metres"> ... </div> inside a g:HTMLPanel (FlowPanel can't take attributes). The same applies to the Image container (:219, template :71) and the effective time box (:227, template :116) |
| FloorMapObjectEditViewImpl.java:244-245 / template :132,:151 | aria | posUnit/sizeUnit hidden: <span class="stroom-floormap-object-edit__unit" aria-hidden="true">m</span> in the HTMLPanel, instead of g:Label plus FloorMapAria.hide |
| FloorMapObjectEditViewImpl.java:182 | structure | fillDefaultCheck.setValue(true) becomes value="true" (:83) |

**Stays in Java:** Per-input aria-labels on the g:TextBoxes and the ColourBox (FloorMapObjectEditViewImpl.java:223,230-231,234-235,238-239,242). They are fixed, but neither widget has an aria-label setter; they move once a template-usable text box (and ColourBox ariaLabel) exists; Group visibility by object kind (:314-315,:423-425), values and matrix maths; DateTimeBox popup provider (:180). It becomes a DateTimeBoxController attached by FloorMapObjectEditPresenter in its wave.

## `CredentialEditPresenter`

dialog, presenter; complexity 2, effort M; used by 1.

- **Component:** `stroom.credentials.client.component.CredentialEdit`
- **Controller:** `stroom.credentials.client.component.CredentialEditController`
- **Template:** `none (uses the shared LinkTabsLayoutViewImpl.ui.xml in stroom-core-client-widget/src/main/resources/stroom/widget/tab/client/view)` → `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialEdit.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/credentials/client/presenter/CredentialEditPresenter.java (becomes CredentialEditController)`; `no GIN binding (made by GIN directly); the LinkTabsLayoutView injection goes`; tabViewMap/firstTab/selectedTab tab plumbing (CredentialEditPresenter.java:65-75, 179-220): use TabbedController's addTab or a LinkTabBar+LayerContainer pair declared in the template
- **Names:** No clash. Depends on unit 20 of this wave (DocumentUserPermissionsTab) and CredentialSettings (wave 8): convert unit 20 first or keep the permissions tab as a presenter slot.

| Into the template | What | Detail |
| --- | --- | --- |
| CredentialEditPresenter.java:102 | css-class | default-min-sizes (components.css:1085) on the template root: <g:FlowPanel styleName="max dock-container-vertical default-min-sizes"> |
| CredentialEditPresenter.java:92 (LinkTabsLayoutView) | structure | <tab:LinkTabBar ui:field="tabBar" addStyleNames="dock-min"/> and <tab:LayerContainerImpl ui:field="layers" styleName="dock-max"/> (both wave 3) declared in CredentialEdit.ui.xml |
| CredentialEditPresenter.java:103,105 | text | tab names "Settings" and "Permissions" (if LinkTabBar gains template tab children; else they stay as addTab(...) calls in the controller) |
| CredentialEditPresenter.java:93-94 | component-declaration | <cred:CredentialSettings ui:field="settings"/> (wave 8) and <sec:DocumentUserPermissionsTab ui:field="permissions"/> (unit 20) as the layers |

**Stays in Java:** dialog caption "New Credentials"/"Edit Credentials" (CredentialEditPresenter.java:126-128): chosen by creation state; dialog size and OK handling with REST store (CredentialEditPresenter.java:131-160); selected tab state (changeSelectedTab).

## `CredentialsManagerDialogPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.credentials.client.component.CredentialsManagerDialog`
- **Controller:** `stroom.credentials.client.component.CredentialsManagerDialogController`
- **Template:** `stroom-core-client/src/main/resources/stroom/credentials/client/view/CredentialsManagerViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/credentials/client/component/CredentialsManagerDialog.ui.xml`
- **Goes:** `CredentialsManagerDialogPresenter.CredentialsManagerDialogView (CredentialsManagerDialogPresenter.java:88-119)`; `stroom-core-client/src/main/java/stroom/credentials/client/view/CredentialsManagerViewImpl.java`; `stroom-core-client/src/main/java/stroom/credentials/client/presenter/CredentialsManagerDialogUiHandlers.java (empty)`; stroom-core-client/src/main/java/stroom/credentials/client/gin/CredentialsModule.java:67-69 bindPresenterWidget(CredentialsManagerDialogPresenter...); the view holding the CredentialsListPresenter (CredentialsManagerViewImpl.java:64-66, 81-83, 114-116): selection goes through the controller
- **Names:** No clash; template renamed from CredentialsManagerViewImpl (not ...DialogViewImpl) to CredentialsManagerDialog.ui.xml.

| Into the template | What | Detail |
| --- | --- | --- |
| CredentialsManagerViewImpl.ui.xml:6 | component-declaration | <cred:CredentialsList ui:field="credentialsList" addStyleNames="max credentials-manager"/> (wave 10) in place of the pnlCredentialsList SimplePanel; controller attaches CredentialsListController |
| CredentialsManagerViewImpl.java:104-106 | structure | lblHtml starts hidden: visible="false" on <g:HTML ui:field="lblHtml"/> |

**Stays in Java:** label HTML (CredentialsManagerDialogPresenter.java:71-72, CredentialsManagerViewImpl.java:100-106): markdown supplied by the caller; selected credential (setCredentialName/getCredentialName); caption "Credentials" and size (CredentialsManagerDialogPresenter.java:74-77): dialog chrome set by the controller.

## `QueryFavouritesPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

**Since the merge.** Mechanical rename: DataGrid and DataGridSelectionEventManager. The template did not change.

- **Component:** `stroom.dashboard.client.component.QueryFavourites`
- **Controller:** `stroom.dashboard.client.component.QueryFavouritesController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/QueryFavouritesViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryFavourites.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryFavouritesPresenter.java (with its nested QueryFavouritesView, :231-242); `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryFavouritesViewImpl.java`; `QueryModule.java:55 bindPresenterWidget(QueryFavouritesPresenter, QueryFavouritesView, QueryFavouritesViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| QueryFavouritesPresenter.java:102-106 / template :8 | component-declaration | Inside <b:Toolbar ui:field="toolbar">: <b:ToolbarButton ui:field="create" svg="ADD" title="Create Favourite From Current Query"/>, <b:ToolbarButton ui:field="edit" svg="EDIT" title="Change Favourite Name" enabled="false"/>, <b:ToolbarButton ui:field="delete" svg="DELETE" title="Delete Favourite" enabled="false"/>. Per the 'set of actions' rule, this is one setOnAction callback |
| QueryFavouritesPresenter.java:91-93,100 / template :9 | component-declaration | <dg:DataGrid ui:field="list" profile="LISTBOX" accessibleName="Favourite queries"/> (NAME, :57) inside the list SimplePanel. This needs a no-arg DataGrid |
| QueryFavouritesPresenter.java:88 / template :12 | component-declaration | <q:ExpressionTree ui:field="expression"/> (wave 7) inside the bottom SimplePanel. NamePresenter (wave 9) is a dialog (:141,147) made with makeDialog, so it is not declared |

**Stays in Java:** QueryCell column (wave 1 cell) added in Java (QueryFavouritesPresenter.java:94-96); Edit/delete enabled state from the selection (:110-120); Caption 'Query Favourites' (:195).

## `QueryHistoryPresenter`

dialog, presenter; complexity 2, effort M; used by 3.

**Since the merge.** Mechanical rename: DataGrid and DataGridSelectionEventManager. The template did not change.

- **Component:** `stroom.dashboard.client.component.QueryHistory`
- **Controller:** `stroom.dashboard.client.component.QueryHistoryController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/QueryHistoryViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/QueryHistory.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryHistoryPresenter.java (with its nested QueryHistoryView, :179-)`; `stroom-core-client/src/main/java/stroom/dashboard/client/query/QueryHistoryViewImpl.java`; `QueryModule.java:54 bindPresenterWidget(QueryHistoryPresenter, QueryHistoryView, QueryHistoryViewImpl)`
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| QueryHistoryPresenter.java:81-83,90 / template :7 | component-declaration | <dg:DataGrid ui:field="list" profile="LISTBOX" accessibleName="Query history"/> (NAME, :55) inside the list SimplePanel. This needs a no-arg DataGrid |
| QueryHistoryPresenter.java:78 / template :8 | component-declaration | <q:ExpressionTree ui:field="expression"/> (wave 7) inside the bottom ScrollPanel |

**Stays in Java:** QueryCell column (QueryHistoryPresenter.java:84-86); Caption 'Query History' (:153), selection and double-click.

## `AdvancedQuickFilterPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.quickfilter.client.component.AdvancedQuickFilter`
- **Controller:** `stroom.quickfilter.client.component.AdvancedQuickFilterController`
- **Template:** `stroom-core-client/src/main/resources/stroom/quickfilter/client/view/AdvancedQuickFilterViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/quickfilter/client/component/AdvancedQuickFilter.ui.xml`
- **Goes:** `AdvancedQuickFilterPresenter.AdvancedQuickFilterView (AdvancedQuickFilterPresenter.java:207-219)`; `stroom-core-client/src/main/java/stroom/quickfilter/client/view/AdvancedQuickFilterViewImpl.java`; stroom-core-client/src/main/java/stroom/quickfilter/client/gin/QuickFilterModule.java:28-31 bindPresenterWidget(AdvancedQuickFilterPresenter...)
- **Names:** No clash.

| Into the template | What | Detail |
| --- | --- | --- |
| AdvancedQuickFilterViewImpl.java:44 | structure | message starts hidden: visible="false" on <g:Label ui:field="message" styleName="dock-min advanced-query__message"/> |
| AdvancedQuickFilterViewImpl.ui.xml:6 | component-declaration | <ec:EditExpression ui:field="expression" addStyleNames="dock-max advanced-query__expression"/> (wave 7) in place of the SimplePanel slot; controller makes EditExpressionController and attaches it |

**Stays in Java:** message text (AdvancedQuickFilterPresenter.java:129-131): includes the server's parse error; PopupUtil.setDialogDescription in the attach handler (AdvancedQuickFilterViewImpl.java:47-53): depends on whether the message is shown; REST parse/format calls and OK logic (controller).

## `DataRetentionRulePresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.receive.rules.client.component.DataRetentionRuleEditor`
- **Controller:** `stroom.receive.rules.client.component.DataRetentionRuleEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/receive/rules/client/view/DataRetentionRuleViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/receive/rules/client/component/DataRetentionRuleEditor.ui.xml`
- **Goes:** `DataRetentionRulePresenter.DataRetentionRuleView (DataRetentionRulePresenter.java:97-110)`; `stroom-core-client/src/main/java/stroom/receive/rules/client/view/DataRetentionRuleViewImpl.java`; `stroom-core-client/src/main/java/stroom/receive/rules/client/gin/PolicyModule.java:43-45 bindPresenterWidget(DataRetentionRulePresenter...)`; `focus Timer (DataRetentionRuleViewImpl.java:69-76): focus on show instead`
- **Names:** DataRetentionRule clashes with the shared DTO stroom.data.retention.shared.DataRetentionRule that this class reads and writes, so DataRetentionRuleEditor (beside the pilot's RuleEditor in the same package; no clash).

| Into the template | What | Detail |
| --- | --- | --- |
| DataRetentionRuleViewImpl.java:59-61 | structure | min="1" max="9999" value="1" on <valuespinner:ValueSpinner ui:field="age"> |
| DataRetentionRuleViewImpl.ui.xml:28 / DataRetentionRulePresenter.java:54 | component-declaration | <ec:EditExpression ui:field="expression" addStyleNames="max"/> (wave 7) in place of the SimplePanel slot |
| DataRetentionRuleViewImpl.java:137 | css-class | retainLabel.setStyleName("section--disabled") is a state toggle; use FormGroup.setDisabled(!enabled) as ReplaceSecret does (stays in Java, but through the FormGroup API) |

**Stays in Java:** time unit items (DataRetentionRuleViewImpl.java:62-67): enum subset; may stay in the component constructor; forever/age enabled state (DataRetentionRuleViewImpl.java:136-140); rule values (read/write).

## `ContentTemplateEditPresenter`

dialog, presenter; complexity 3, effort M; used by 3.

- **Component:** `stroom.receive.content.client.component.ContentTemplateEdit`
- **Controller:** `stroom.receive.content.client.component.ContentTemplateEditController`
- **Template:** `stroom-core-client/src/main/resources/stroom/receive/content/client/view/ContentTemplateEditViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/receive/content/client/component/ContentTemplateEdit.ui.xml`
- **Goes:** `ContentTemplateEditPresenter.ContentTemplateEditView (nested)`; `stroom-core-client/src/main/java/stroom/receive/content/client/view/ContentTemplateEditViewImpl.java`; stroom-core-client/src/main/java/stroom/receive/content/client/gin/ContentTemplateModule.java:37-39 bindPresenterWidget(ContentTemplateEditPresenter...); `focus Timer (ContentTemplateEditViewImpl.java:86-92)`
- **Names:** No clash.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| ContentTemplateEditViewImpl.java:171 | `w.getElement().getStyle().setWidth(100, Unit.PCT)` | static | w-100 (stroom.css:843) on the declared DocSelectionBox |
| ContentTemplateEditViewImpl.java:172 | `w.getElement().getStyle().setMargin(0, Unit.PX)` | static | new content-template-edit__pipeline { margin: 0 } in components.css (no margin-reset utility exists) |

| Into the template | What | Detail |
| --- | --- | --- |
| ContentTemplateEditViewImpl.ui.xml:56 / ContentTemplateEditPresenter.java:305 | component-declaration | <dd:DropDownBox ui:field="pipeline" addStyleNames="w-100 content-template-edit__pipeline"/> (wave 8) in place of the SimplePanel; controller attaches DocSelectionController with PipelineDoc.TYPE and VIEW |
| ContentTemplateEditViewImpl.ui.xml:79 / ContentTemplateEditPresenter.java:286 | component-declaration | <ec:EditExpression ui:field="expression" addStyleNames="max"/> (wave 7) in place of the SimplePanel |
| ContentTemplateEditViewImpl.java:77-83 | structure | min/max/value on the two ValueSpinners as template attributes (1/100/10 and 0/max/0), if the ProcessorFilter constants are written as literals; else they stay |

**Stays in Java:** template type items (ContentTemplateEditViewImpl.java:71-75): enum; copy dependencies enabled state (ContentTemplateEditViewImpl.java:156-166); fields fetched by REST (ContentTemplateEditPresenter.java:288-296).

## `RulePresenter`

dialog, presenter; complexity 2, effort M; used by 3.

- **Component:** `stroom.dashboard.client.component.ConditionalFormattingRuleEditor`
- **Controller:** `stroom.dashboard.client.component.ConditionalFormattingRuleEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/table/cf/RuleViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/ConditionalFormattingRuleEditor.ui.xml`
- **Goes:** `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/RulePresenter.java (with its nested RuleView, :138-167)`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/RuleViewImpl.java`; `stroom-core-client/src/main/java/stroom/dashboard/client/table/cf/RuleUiHandlers.java`; `TableModule.java:89 bindPresenterWidget(RulePresenter, RuleView, RuleViewImpl)`
- **Names:** `Rule` is free but too generic once out of the cf package: the area's component package also holds Rules/MultiRules (wave 13), and RuleEditor is taken by stroom.receive.rules.client.component.RuleEditor. Chose ConditionalFormattingRuleEditor, after the shared ConditionalFormattingRule it edits. Neither name exists.

| Inline style | Code | Kind | Replacement |
| --- | --- | --- | --- |
| RuleViewImpl.ui.xml:50 | `<b:Button ui:field="editCustomStyle" ... width="200px">` | static | Drop the attribute. The existing rule .form-group > .Button { min-width: 200px } (form.css:68-72) gives the same width, as the button is a direct child of the FormGroup |
| RuleViewImpl.ui.xml:72 | `<g:SimplePanel ui:field="example" width="200px"/>` | static | New class cf-rule__example { width: 200px; } in conditional-formatting.css, beside .cf-colour-swatch (:9) |

| Into the template | What | Detail |
| --- | --- | --- |
| RuleViewImpl.java:74 | css-class | conditionalFormatTypeSelection is defined in no CSS file. Drop it |
| RuleViewImpl.java:80 | css-class | addStyleNames="conditionalFormatStyleSelection" on <l:SelectionBox ui:field="formattingStyle"> (:43). The class is used in selection-box.css:101 and conditional-formatting.css:487 |
| RuleViewImpl.java:89-91 / template :15 | component-declaration | <dc:EditExpression ui:field="expression" addStyleNames="dock-max stroom-control"/> (wave 7) replaces the SimplePanel and setExpressionView (RulePresenter.java:54). CustomRowStyle (wave 9) is a dialog made with makeDialog (:60), so it is not declared |

**Stays in Java:** Type/style item lists, defaults and the swatch render function (RuleViewImpl.java:72-79); Style/custom-style group visibility (:164-176) and the example swatch HTML built from the values (:218-227); Rule values read and written (RulePresenter.java:72-131).

## `SelectionHandlerPresenter`

dialog, presenter; complexity 2, effort S; used by 3.

- **Component:** `stroom.dashboard.client.component.SelectionHandlerEditor`
- **Controller:** `stroom.dashboard.client.component.SelectionHandlerEditorController`
- **Template:** `stroom-core-client/src/main/resources/stroom/dashboard/client/query/SelectionHandlerViewImpl.ui.xml` → `stroom-core-client/src/main/java/stroom/dashboard/client/component/SelectionHandlerEditor.ui.xml`
- **Goes:** stroom-core-client/src/main/java/stroom/dashboard/client/query/SelectionHandlerPresenter.java (with its nested SelectionHandlerView, :101-109); `stroom-core-client/src/main/java/stroom/dashboard/client/query/SelectionHandlerViewImpl.java`; `QueryModule.java:67-70 bindPresenterWidget(SelectionHandlerPresenter, SelectionHandlerView, SelectionHandlerViewImpl)`
- **Names:** `SelectionHandler` is not in all_simple_names, but it is GWT's com.google.gwt.event.logical.shared.SelectionHandler, imported across the client (tab bars, selection boxes). The name would clash in any file using both. Chose SelectionHandlerEditor, as it edits a ComponentSelectionHandler. The name is free.

| Into the template | What | Detail |
| --- | --- | --- |
| SelectionHandlerViewImpl.java:62-64 / template :18 | component-declaration | <dc:EditExpression ui:field="expression"/> (wave 7) inside the layout SimplePanel replaces setExpressionView (SelectionHandlerPresenter.java:52) |
| SelectionHandlerViewImpl.java:77-79 / template :23 | component-declaration | <dc:CurrentSelection ui:field="currentSelection"/> (wave 9) inside the currentSelection SimplePanel replaces setCurrentSelection (:53) |

**Stays in Java:** Insert handler from CurrentSelection to EditExpression (SelectionHandlerPresenter.java:55), wired by the controller; Enabled value and expression read and written (:62-91); Unused constant ANY (SelectionHandlerViewImpl.java:35) is deleted.

