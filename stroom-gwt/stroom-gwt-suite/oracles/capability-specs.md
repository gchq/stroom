# Capability specs — what each presenter SHOULD offer

**Generated** by `stroom-gwt-suite/tools/build-capability-specs.mjs`. Do not hand-edit.

The suite records what it finds; nothing said what it should find. Mined from the two
declarative shapes GWT uses: `SvgPresets.X[.title("…")]` for toolbar buttons and
`headingBuilder("…")` for grid columns.

`SvgPresets` declares each preset as `enabled(...)` or `disabled(...)`, which gives an oracle
for **disabled at rest** — the selection-gated behaviour the screen profiles derive but cannot
predict.

> **A spec is a CLAIM, not a fact.** Source mining produced a route from a commented-out line
> today, and a live menu registration behind a dead GIN binding. The diff below is the miner
> being tested against the presenters we can already reach.

## Totals

| | count |
| --- | ---: |
| Presenters with a mined capability spec | 423 |
| …declaring toolbar buttons | 219 |
| …declaring grid columns | 60 |
| SvgPresets constants resolved | 72 |
| …whose spec includes inherited buttons | 385 |
| …declaring a dialog PopupType | 102 |
| …naming server resources | 201 |
| Presenters with observed crawl data to diff | 136 |

## Expected vs observed

`missing` = mined but never seen (miner phantom, dead code, or a real defect).
`extra` = seen but not mined (the miner is incomplete — chrome, menu items, column headers).

* **AskStroomAiPresenter** — expected 3, saw 5, MISSING: Close, OK, Cancel, extra: Download, Delete All Messages, New Conversation, Conversation History, Configure
* **IndexFieldListPresenter** — expected 3, saw 17, MISSING: New, Edit, Delete, extra: Save, Edit Field, Remove Field, First, Backward, Forward
* **SolrIndexFieldListPresenter** — expected 3, saw 10, MISSING: New, Edit, Delete, extra: Save, Edit Field, Remove Field, First, Backward, Forward
* **BatchDocumentPermissionsPresenter** — expected 3, saw 8, MISSING: OK, Cancel, Close, extra: Edit Permissions For Selected Document, First, Backward, Filter Documents To Apply Permissions Changes On, Batch Edit Permissions For Filtered Documents, Forward
* **ScheduledProcessEditPresenter** — expected 2, saw 1, MISSING: OK, Cancel, extra: Close
* **TracesPresenter** — expected 2, saw 12, MISSING: Save, Save As, extra: First, Backward, Forward, Last, Refresh, Operation
* **AnnotationPresenter** — expected 2, saw 25, MISSING: Save As, extra: First, Backward, Forward, Last, Edit Permissions For Selected User, Copy History
* **QueryResultTablePresenter** — expected 4, saw 29, MISSING: Undo, extra: First, Backward, Forward, Last, Insert, Expand
* **SteppingFilterPresenter** — expected 6, saw 20, MISSING: Has active filter(s), extra: First, Backward, Forward, Last, Refresh, Ctrl+Enter
* **UserTaskManagerPresenter** — expected 1, saw 0, MISSING: Close
* **XMLSchemaPresenter** — expected 3, saw 30, MISSING: Alert, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, Schema is valid
* **GlobalPropertyTabPresenter** — expected 2, saw 9, MISSING: Show Warnings, extra: First, Backward, Forward, Last, Refresh, Name
* **CommonAlertPresenter** — expected 3, saw 7, extra: drag by the caption, resize by the SE handle, Escape, Ctrl+Enter
* **AiChatHistoryPresenter** — expected 2, saw 13, extra: First, Backward, Forward, Last, Refresh, Ctrl+Enter
* **AskStroomAiConfigPresenter** — expected 2, saw 53, extra: Restore From Defaults, Set As Default, Close, Enter, mouseover on "Max History Safety Cap" spinner, mouseout on "Max History Safety Cap" spinner
* **ImportConfigPresenter** — expected 2, saw 11, extra: Choose File, Close, Ctrl+Enter, drag by the caption, resize by the SE handle, File - Click for help
* **ExportConfigPresenter** — expected 2, saw 10, extra: Close, Ctrl+Enter, drag by the caption, resize by the SE handle, Items To Export - Click for help, Escape
* **ResultStorePresenter** — expected 1, saw 16, extra: First, Backward, Forward, Last, Refresh, drag by the caption
* **FindPresenter** — expected 1, saw 11, extra: First, Backward, Forward, Last, Refresh, drag by the caption
* **FindInContentPresenter** — expected 1, saw 13, extra: First, Backward, Forward, Last, Match case, Regex
* **RecentItemsPresenter** — expected 1, saw 11, extra: First, Backward, Forward, Last, Refresh, drag by the caption
* **TabSessionChooserPresenter** — no mined spec, saw 8
* **TextBoxPopup** — expected 2, saw 11, extra: Enter, OK (illegal text in "Name"), drag by the caption, resize by the SE handle, Name - Click for help, Escape
* **UserPreferencesPresenter** — expected 2, saw 185, extra: Set As Default, Revert To Default, Close, chose "Light" "Theme", chose "Comfortable" "Layout Density", ticked "Enable Transparency"
* **CurrentPasswordPresenter** — expected 2, saw 6, extra: Show Password, Ctrl+Enter, Current Password - Click for help, Escape
* **AboutPresenter** — expected 1, saw 4, extra: Escape, right-click an explorer row, right-click a grid row
* **ContentTemplateEditPresenter** — expected 0, saw 32, extra: Template Name - Click for help, Template Description - Click for help, Template Type - Click for help, Copy Pipeline Element Dependencies - Click for help, Pipeline - Click for help, Processor Priority - Click for help
* **EditExpressionPresenter** — expected 5, saw 8, extra: OK, Cancel, Ctrl+Enter
* **InfoDocumentPresenter** — expected 1, saw 1
* **ExplorerPopupPresenter** — expected 2, saw 17, extra: drag by the caption, resize by the SE handle, right-click a grid row, mouseover on "Number of slices" spinner, mouseout on "Number of slices" spinner, mousedown on "Number of slices" spinner
* **FolderPresenter** — expected 0, saw 33, extra: First, Backward, Forward, Last, Show Tasks, Edit Permissions For Selected User
* **ExpressionPresenter** — expected 0, saw 23, extra: OK, Cancel, Close, Ctrl+Enter, Escape, Terminate Search
* **BatchProcessorFilterEditPresenter** — expected 2, saw 7, extra: Close, Escape, Ctrl+Enter, right-click an explorer row, right-click a grid row
* **FolderRootPresenter** — expected 0, saw 33, extra: First, Backward, Last, Forward, Show Tasks, Edit Permissions For Selected User
* **SourceTabPresenter** — no mined spec, saw 22
* **PipelineStructurePresenter** — expected 0, saw 16, extra: Save, Edit Pipeline Element, Edit Property, First, Backward, Forward
* **NewElementPresenter** — expected 2, saw 6, extra: Enter, OK (illegal text in "Name"), Ctrl+Enter, Escape
* **AddEventLinkPresenter** — expected 2, saw 3, extra: Close
* **DurationPresenter** — expected 2, saw 2
* **RulePresenter** — expected 0, saw 15, extra: Row Match Expression - Click for help, Rule Enabled State - Click for help, Hide Matching Rows - Click for help, Formatting Type - Click for help, Style - Click for help, Make Text Bold - Click for help
* **CreateDocumentPresenter** — expected 2, saw 12, extra: Close, Enter, OK (illegal text in "Name"), Ctrl+Enter, Escape, Select the parent folder - Click for help
* **FindAnnotationPresenter** — expected 2, saw 11, extra: First, Backward, Forward, Last, Refresh, Escape
* **NewPropertyPresenter** — expected 0, saw 11, extra: Element Id - Click for help, Name - Click for help, Description - Click for help, Source - Click for help, Value - Click for help, Default Value - Click for help
* **PathwaysPresenter** — expected 2, saw 30, extra: Close, New path disabled as read only, Edit path disabled as read only, Remove path disabled as read only, View Matching Traces, Edit Pathway
* **CommentEditPresenter** — no mined spec, saw 2
* **NewPipelineReferencePresenter** — expected 0, saw 3, extra: OK, Close, Cancel
* **CustomRowStylePresenter** — expected 2, saw 10, extra: Light Background Colour - Click for help, Light Text Colour - Click for help, Dark Background Colour - Click for help, Dark Text Colour - Click for help, Escape, Enter
* **ColumnValuesFilterPresenter** — no mined spec, saw 19
* **DashboardPresenter** — expected 0, saw 10, extra: Expand, Collapse, Download, First, Backward, Forward
* **RenameColumnPresenter** — expected 2, saw 6, extra: Escape, Enter, OK (illegal text in "Name"), Ctrl+Enter
* **StepLocationPresenter** — no mined spec, saw 21
* **DocumentUserPermissionsEditPresenter** — expected 2, saw 12, extra: Ctrl+Enter, Close, Apply To Descendants, Set Document Create Permissions, Escape, Document - Click for help
* **DataPreviewTabPresenter** — no mined spec, saw 6
* **TableSettingsPresenter** — expected 0, saw 14, extra: Enable, First, Backward, Forward, Last, OK
* **SelectionHandlerPresenter** — expected 0, saw 7, extra: OK, Cancel, Ctrl+Enter
* **QuerySettingsPresenter** — expected 0, saw 13, extra: Enable, First, Backward, Forward, Last, OK
* **GitRepoPresenter** — expected 2, saw 35, extra: typed "Git repository URL", Git repository URL - Click for help, Git branch - Click for help, Git path - Click for help, Git commit - Click for help, Automatically push - Click for help
* **HttpClientConfigPresenter** — expected 2, saw 47, extra: Timeout - Click for help, Connection Timeout - Click for help, Connection Request Timeout - Click for help, Time To Live - Click for help, Help, Max Connections - Click for help
* **GitRepoCommitDialogPresenter** — expected 2, saw 5, extra: Commit message - Click for help, Enter, Close
* **ResultStoreListPresenter** — expected 3, saw 9, extra: First, Backward, Forward, Last, Refresh, Close
* **ProcessorLimitsPresenter** — no mined spec, saw 2
* **ProcessChoicePresenter** — expected 2, saw 20, extra: Escape, Enter, Ctrl+Enter, mouseover on "Priority of new filters (unless auto)" spinner, mouseout on "Priority of new filters (unless auto)" spinner, mousedown on "Priority of new filters (unless auto)" spinner
* **SelectionSummaryPresenter** — expected 3, saw 6, extra: Escape, right-click an explorer row, right-click a grid row
* **ChangeStatusPresenter** — expected 2, saw 5, extra: Escape, Ctrl+Enter, Close
* **ChangeAssignedToPresenter** — expected 2, saw 5, extra: Escape, Ctrl+Enter, Close
* **QueryDocPresenter** — expected 2, saw 39, extra: First, Backward, Forward, Last, Insert, Copy
* **DependenciesInfoPresenter** — expected 1, saw 2, extra: Escape
* **EditAccountPresenter** — expected 2, saw 28, extra: Escape, Set Password, Show Password, Ctrl+Enter, Enter, OK (illegal id in "User Id")
* **CreateUserPresenter** — expected 2, saw 8, extra: Escape, Enter, OK (illegal text in "Name"), Ctrl+Enter, Close, Name - Click for help
* **EditApiKeyPresenter** — expected 2, saw 13, extra: Escape, Owner - Click for help, API Key Name - Click for help, Hash Algorithm - Click for help, Enabled - Click for help, Comments - Click for help
* **UserTabPresenter** — expected 0, saw 27, extra: First, Backward, Forward, Last, Display Name - Click for help, Full Name - Click for help
* **CreateNewGroupPresenter** — expected 0, saw 7, extra: Escape, Enter, OK (illegal text in "Name"), Ctrl+Enter, OK, Close
* **UserRefPopupPresenter** — expected 2, saw 12, extra: First, Backward, Last, Escape, Forward, Refresh
* **VisualisationAssetsPresenter** — expected 0, saw 6, extra: Save, Revert changes, Save As, Add file, Rename, View in browser
* **VisualisationAssetsUploadFileDialogPresenter** — expected 2, saw 8, extra: Escape, Choose File, Ctrl+Enter, Close, Upload to - Click for help, File - Click for help
* **VisualisationAssetsEditAssetDialogPresenter** — expected 2, saw 4, extra: Escape, Enter
* **WelcomePresenter** — expected 0, saw 0
* **LoginPresenter** — expected 0, saw 2, extra: Show Password, link: Forgot password?
* **EmailResetPasswordPresenter** — expected 2, saw 2
* **SteppingPresenter** — expected 1, saw 21, extra: Save, Step To First, Step Backward, Step Forward, Step To Last, Terminate Stepping
* **ElementPresenter** — expected 0, saw 11, extra: Save, Step To First, Step Backward, Step Forward, Step To Last, Terminate Stepping
* **CharacterRangeSelectionPresenter** — expected 2, saw 2
* **CopyDocumentPresenter** — expected 2, saw 4, extra: Select the parent folder - Click for help, Permissions - Click for help
* **MoveDocumentPresenter** — expected 2, saw 4, extra: Select the parent folder - Click for help, Permissions - Click for help
* **RenameTabPresenter** — expected 2, saw 5, extra: Enter, Ctrl+Enter, OK (illegal text in "Name")
* **QueryHistoryPresenter** — expected 2, saw 3, extra: Ctrl+Enter
* **QueryFavouritesPresenter** — expected 5, saw 9, extra: Enter, Ctrl+Enter, Close, OK (illegal text in "Name")
* **LayoutConstraintPresenter** — no mined spec, saw 1
* **CurrentSelectionPresenter** — no mined spec, saw 1
* **BatchExecutionScheduleEditPresenter** — expected 1, saw 11, extra: Apply to Selection, Apply to Filtered, None, Schedule Name - Click for help, Enabled - Click for help, Processing Node - Click for help
* **ExecutionScheduleRunNowPresenter** — expected 1, saw 3, extra: Apply to Selection, Apply to Filtered
* **ProcessorProfileEditPresenter** — expected 2, saw 27, extra: Remove Period, First, Backward, Forward, Last, New Period
* **AnalyticRulePresenter** — expected 2, saw 76, extra: First, Backward, Forward, Last, Insert, Copy
* **AnalyticNotificationEditPresenter** — no mined spec, saw 27
* **DashboardSuperPresenter** — expected 2, saw 43, extra: Copy, Expand, Collapse, Download, First, Backward
* **DictionaryPresenter** — expected 3, saw 39, extra: Remove Import, First, Backward, Forward, Last, Edit Permissions For Selected User
* **DocumentationPresenter** — expected 3, saw 20, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, Edit
* **FeedPresenter** — expected 2, saw 63, extra: First, Backward, Forward, Last, Edit Permissions For Selected User, Refresh
* **DataUploadPresenter** — expected 2, saw 13, extra: Choose File, Enter, Ctrl+Enter, Close, OK (illegal time in "Effective Date"), Meta Data - Click for help
* **KafkaConfigPresenter** — expected 3, saw 24, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, Edit
* **OpenAIModelPresenter** — expected 2, saw 34, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, typed "Base URL (optional)"
* **ElasticClusterPresenter** — expected 2, saw 23, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, typed "Connection URLs"
* **DataGenPresenter** — expected 2, saw 47, extra: Edit Execution Schedule, Remove Execution Schedule, First, Backward, Forward, Last
* **TextConverterPresenter** — expected 2, saw 32, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, typed in the editor "Conversion"
* **XsltPresenter** — expected 2, saw 21, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, typed in the editor "XSLT"
* **ElasticIndexPresenter** — expected 2, saw 49, extra: First, Backward, Forward, Last, Edit Permissions For Selected User, typed "Index name or pattern"
* **SolrIndexPresenter** — expected 2, saw 40, extra: Edit Field, Remove Field, First, Backward, Forward, Last
* **VisualisationPresenter** — expected 2, saw 30, extra: Revert changes, Rename, View in browser, Edit Permissions For Selected User, First, Backward
* **StatisticsDataSourcePresenter** — expected 2, saw 32, extra: Edit Field, Remove Field, First, Backward, Forward, Last
* **PipelinePresenter** — expected 2, saw 72, extra: First, Backward, Forward, Last, Add New Pipeline Element, Remove Pipeline Element
* **ProcessorEditPresenter** — expected 2, saw 45, extra: Remove Reference, First, Backward, Forward, Last, mouseover on "Max Processing Tasks" spinner
* **PlanBPresenter** — expected 2, saw 62, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, chose "State" "State Type"
* **ScriptPresenter** — expected 2, saw 26, extra: Remove Dependency, First, Backward, Forward, Last, Edit Permissions For Selected User
* **ViewPresenter** — expected 2, saw 20, extra: Copy, Disable, Edit Permissions For Selected User, First, Backward, Forward
* **ReportPresenter** — expected 2, saw 74, extra: First, Backward, Forward, Last, Insert, Copy
* **S3ConfigPresenter** — expected 3, saw 31, extra: Edit Permissions For Selected User, First, Backward, Forward, Last, typed in the editor "Config"
* **IndexPresenter** — expected 2, saw 63, extra: Delete Selected Shards, First, Backward, Forward, Last, Edit Field
* **BrowseAnnotationPresenter** — no mined spec, saw 5
* **CachePresenter** — expected 0, saw 5, extra: First, Backward, Forward, Last, Refresh
* **ContentStorePresenter** — no mined spec, saw 7
* **CredentialsPresenter** — expected 0, saw 7, extra: First, Backward, Forward, Last, Refresh
* **CredentialEditPresenter** — expected 2, saw 31, extra: Name - Click for help, Credentials expire - Click for help, Type - Click for help, User Name - Click for help, Password - Click for help, Show Password
* **FsVolumeGroupPresenter** — expected 3, saw 8, extra: First, Backward, Forward, Last, Refresh
* **NavigationPresenter** — no mined spec, saw 0
* **DependenciesTabPresenter** — expected 0, saw 10, extra: First, Backward, Forward, Last, Refresh, From (Type)
* **IndexVolumeGroupPresenter** — expected 3, saw 8, extra: First, Backward, Forward, Last, Refresh
* **DatabaseTablesMonitoringPresenter** — expected 0, saw 10, extra: First, Backward, Forward, Last, Refresh, Database
* **JobPresenter** — expected 0, saw 9, extra: First, Backward, Forward, Last, Refresh, Click to show only enabled jobs
* **NodeGroupPresenter** — expected 3, saw 10, extra: First, Backward, Forward, Last, Refresh, Name
* **NodePresenter** — no mined spec, saw 14
* **ProcessorProfilePresenter** — expected 3, saw 10, extra: First, Backward, Forward, Last, Refresh, Name
* **ContentTemplateTabPresenter** — expected 8, saw 13, extra: First, Backward, Forward, Last, Refresh
* **DataRetentionPresenter** — expected 0, saw 12, extra: First, Backward, Forward, Last, Refresh
* **RuleSetPresenter** — expected 2, saw 14, extra: Select one or more rules with the same enabled state to enable/disable them., First, Backward, Forward, Last, Refresh
* **BatchDocumentPermissionsEditPresenter** — expected 2, saw 4, extra: Ctrl+Enter, type a row number in the pager
* **TaskManagerPresenter** — expected 0, saw 14, extra: First, Backward, Forward, Last, Turn Auto Refresh Off, Terminate Task

## Specs

### AboutPlugin

### AboutPresenter

**buttons** (1): `Close`

**resources** (1): `SESSION_INFO_RESOURCE`

### AbstractDuplicateManagementPresenter

### AbstractMetaListPresenter

**buttons** (2): `Deleted Stream` · `Locked Stream`

**columns** (1): `Type`

**resources** (5): `META_RESOURCE.findMetaRow` · `META_RESOURCE.updateStatus` · `DATA_RESOURCE.download` · `PROCESSOR_FILTER_RESOURCE.create` · `PROCESSOR_FILTER_RESOURCE.reprocess`

### AbstractNotificationListPresenter

**buttons** (3): `Add` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (5): `Enabled` · `Type` · `Destination` · `Limit` · `Max`

### AccountsListPresenter

**columns** (9): `User Id` · `Enabled` · `Locked` · `Active` · `Sign In Failures` · `Last Sign In` · `First Name` · `Last Name` · `Email`

**resources** (3): `ACCOUNT_RESOURCE.delete` · `USER_RESOURCE` · `ACCOUNT_RESOURCE.find`

### AccountsPlugin

**buttons** (1): `User`

### ActionCell

**buttons** (1): `Actions...`

### ActionMenuCell

**buttons** (1): `Actions...`

### ActivityEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `ACTIVITY_RESOURCE.validate` · `ACTIVITY_RESOURCE` · `ACTIVITY_RESOURCE.update`

### ActivityListPresenter

**resources** (1): `ACTIVITY_RESOURCE.list`

### AddEventLinkPresenter

**buttons** (2): `OK` · `Cancel`

### AiAttachmentDataPresenter

**buttons** (1): `Close`

### AiChatHistoryPresenter

**buttons** (2): `OK` · `Cancel`

### AlertPlugin

### AnalyticDataShardListPresenter

**columns** (1): `Path`

**resources** (1): `ANALYTIC_DATA_SHARD_RESOURCE.find`

### AnalyticDataShardsPresenter

**resources** (1): `ANALYTIC_DATA_SHARD_RESOURCE.getData`

### AnalyticDuplicateManagementPresenter

### AnalyticEmailDestinationPresenter

**resources** (2): `ANALYTIC_RULE_RESOURCE.testTemplate` · `ANALYTIC_RULE_RESOURCE.sendTestEmail`

### AnalyticNotificationListPresenter

**buttons** (3): `Add` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (5): `Enabled` · `Type` · `Destination` · `Limit` · `Max`

### AnalyticRulePresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### AnalyticsPlugin

**resources** (2): `ANALYTIC_RULE_RESOURCE.fetch` · `ANALYTIC_RULE_RESOURCE`

### AnnotationCollectionPlugin

### AnnotationCommentPlugin

### AnnotationEditPresenter

### AnnotationEditSupport

### AnnotationLabelPlugin

### AnnotationLinkPresenter

**buttons** (2): `Add Annotation Link` · `Remove Annotation Link` _(disabled at rest)_

### AnnotationManager

### AnnotationPlugin

### AnnotationPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### AnnotationResourceClient

**resources** (15): `ANNOTATION_RESOURCE.findAnnotations` · `ANNOTATION_RESOURCE.getAnnotationById` · `ANNOTATION_RESOURCE.getAnnotationEntries` · `ANNOTATION_RESOURCE.createAnnotation` · `ANNOTATION_RESOURCE.change` · `ANNOTATION_RESOURCE.batchChange` · `ANNOTATION_RESOURCE.getLinkedEvents` · `ANNOTATION_RESOURCE.deleteAnnotation` · `ANNOTATION_RESOURCE.createAnnotationTag` · `ANNOTATION_RESOURCE.updateAnnotationTag` · `ANNOTATION_RESOURCE.deleteAnnotationTag` · `ANNOTATION_RESOURCE.findAnnotationTags`

### AnnotationStatusPlugin

### AnnotationTagCreatePresenter

**buttons** (2): `OK` · `Cancel`

### AnnotationTagEditPresenter

**buttons** (3): `OK` · `Cancel` · `Close`

### AnnotationTagListPresenter

**columns** (2): `Style` · `Name`

### AnnotationTagPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

### ApiKeysListPresenter

**columns** (6): `Owner` · `Key Name` · `Key Prefix` · `State` · `Expires On` · `Hash Algorithm`

**resources** (1): `API_KEY_RESOURCE.find`

### ApiKeysPlugin

**buttons** (1): `Key`

### ApiKeysPresenter

**buttons** (3): `Add new API Key` · `Edit API Key` _(disabled at rest)_ · `Delete API Key` _(disabled at rest)_

**resources** (2): `API_KEY_RESOURCE.deleteBatch` · `API_KEY_RESOURCE.delete`

### AppPermissionsEditPresenter

**columns** (3): `Granted` · `Permission` · `Description`

**resources** (2): `APP_PERMISSION_RESOURCE.getAppUserPermissionsReport` · `APP_PERMISSION_RESOURCE.changeAppPermission`

### AppPermissionsPlugin

**buttons** (1): `Shield`

### AppUserPermissionsListPresenter

**columns** (1): `Permissions`

**resources** (1): `APP_PERMISSION_RESOURCE.fetchAppUserPermissions`

### AskStroomAiConfigPresenter

**buttons** (2): `OK` · `Cancel`

### AskStroomAiPresenter

**buttons** (3): `Close` · `OK` · `Cancel`

### AsyncSuggestOracle

**resources** (1): `SUGGESTIONS_RESOURCE.fetch`

### BasicVisSettingsPresenter

**resources** (1): `VISUALISATION_RESOURCE.fetch`

### BatchDocumentPermissionsEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `EXPLORER_RESOURCE.changeDocumentPermissions`

### BatchDocumentPermissionsPresenter

**buttons** (3): `OK` · `Cancel` · `Close`

### BatchExecutionScheduleEditPresenter

**buttons** (1): `Close`

### BatchProcessorFilterEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `PROCESSOR_FILTER_RESOURCE.bulkChange`

### CacheListPresenter

**buttons** (1): `Delete` _(disabled at rest)_

**columns** (2): `Name` · `Property Path Base`

**resources** (2): `CACHE_RESOURCE.clear` · `CACHE_RESOURCE.list`

### CacheNodeListPresenter

**buttons** (1): `Delete` _(disabled at rest)_

**resources** (2): `CACHE_RESOURCE.clear` · `CACHE_RESOURCE.info`

### CachePresenter

### ChangeAssignedToPresenter

**buttons** (2): `OK` · `Cancel`

### ChangePasswordPlugin

### ChangePasswordPresenter

**buttons** (2): `OK` · `Cancel`

### ChangeStatusPresenter

**buttons** (2): `OK` · `Cancel`

### CharacterNavigatorPresenter

### CharacterNavigatorViewImpl

**buttons** (3): `Show Beginning` _(disabled at rest)_ · `Advance Range Backwards` _(disabled at rest)_ · `Advance Range Forwards` _(disabled at rest)_

### CharacterRangeSelectionPresenter

**buttons** (2): `OK` · `Cancel`

### ColumnFilterPresenter

**buttons** (2): `OK` · `Cancel`

### ColumnFunctionEditorPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `DASHBOARD_RESOURCE.validateExpression`

### ColumnSelectionPresenter

**columns** (1): `Column Name`

### ColumnsManager

### CommonAlertPresenter

**buttons** (3): `OK` · `Cancel` · `Close`

### ConfigDefaultSetter

**resources** (1): `CONFIG_RESOURCE.setConfigValue`

### ConfigPropertyClusterValuesListPresenter

**columns** (4): `Effective Value` · `Count` · `Source` · `Node`

### ConfigPropertyClusterValuesPresenter

**buttons** (1): `Close`

### ConstraintEditPresenter

**buttons** (2): `OK` · `Cancel`

### ConstraintListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

### ContentStoreContentPackDetailsPresenter

### ContentStoreContentPackListPresenter

**columns** (3): `Content Pack` · `Status` · `Store`

### ContentTemplateEditPresenter

**resources** (1): `CONTENT_TEMPLATE_RESOURCE`

### ContentTemplateListPresenter

**columns** (8): `Enabled` · `Template No.` · `Name` · `Type` · `Copy Dependencies` · `Pipeline` · `Priority` · `Max Concurrent`

### ContentTemplatePlugin

### ContentTemplateTabPresenter

**buttons** (8): `Delete template` _(disabled at rest)_ · `Add new template above the selected one` · `Add new template below the selected one` · `Copy template` _(disabled at rest)_ · `Edit template` _(disabled at rest)_ · `Move template up` _(disabled at rest)_ · `Move template down` _(disabled at rest)_ · `Save templates` _(disabled at rest)_

**resources** (2): `CONTENT_TEMPLATE_RESOURCE` · `CONTENT_TEMPLATE_RESOURCE.update`

### CopyDocumentPresenter

**buttons** (2): `OK` · `Cancel`

### CreateDocumentPresenter

**buttons** (2): `OK` · `Cancel`

### CreateExternalUserPresenter

**resources** (2): `USER_RESOURCE.createUser` · `USER_RESOURCE.update`

### CreateMultipleUsersPresenter

**resources** (2): `USER_RESOURCE.createUsersFromCsv` · `USER_RESOURCE.update`

### CreateNewGroupPresenter

**resources** (2): `USER_RESOURCE.createGroup` · `USER_RESOURCE.update`

### CreateUserPresenter

**buttons** (2): `OK` · `Cancel`

### CredentialClient

**resources** (5): `CREDENTIALS_RESOURCE` · `CREDENTIALS_RESOURCE.getCredentialByUuid` · `CREDENTIALS_RESOURCE.getCredentialByName` · `CREDENTIALS_RESOURCE.storeCredential` · `CREDENTIALS_RESOURCE.deleteCredentials`

### CredentialEditPresenter

**buttons** (2): `OK` · `Cancel`

### CredentialsListPresenter

**buttons** (3): `Add` · `Delete` _(disabled at rest)_ · `Edit` _(disabled at rest)_

**columns** (4): `Name` · `Credential Type` · `Key Store Type` · `Expires`

### CredentialsManagerDialogPresenter

**buttons** (2): `OK` · `Cancel`

### CredentialsManagerViewImpl

### CredentialsPresenter

### CurrentActivity

**resources** (2): `ACTIVITY_RESOURCE` · `ACTIVITY_RESOURCE.setCurrentActivity`

### CurrentPasswordPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `SESSION_RESOURCE`

### CurrentUser

**resources** (1): `DOC_PERMISSION_RESOURCE.checkDocumentPermission`

### CustomRowStylePresenter

**buttons** (2): `OK` · `Cancel`

### DashboardContextImpl

### DashboardPlugin

**resources** (2): `DASHBOARD_RESOURCE.fetch` · `DASHBOARD_RESOURCE.update`

### DashboardPresenter

### DashboardSuperPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### DatabaseTablesMonitoringPresenter

**resources** (1): `DB_STATUS_RESOURCE.findSystemTableStatus`

### DataGenPlugin

**resources** (2): `DATA_GEN_RESOURCE.fetch` · `DATA_GEN_RESOURCE`

### DataGenPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### DataPresenter

**resources** (4): `META_RESOURCE.fetch` · `DATA_RESOURCE.getChildStreamTypes` · `DATA_RESOURCE.fetch` · `DATA_RESOURCE.viewInfo`

### DataRetentionImpactPresenter

**buttons** (7): `Run` · `Stop` · `Filter` · `Table` · `Nested Table` · `Expand All` · `Collapse All`

**resources** (2): `RETENTION_RULES_RESOURCE.getRetentionDeletionSummary` · `RETENTION_RULES_RESOURCE.cancelQuery`

### DataRetentionPolicyListPresenter

**columns** (4): `Rule` · `Name` · `Retention` · `Expression`

### DataRetentionPolicyPresenter

**buttons** (7): `Delete rule` _(disabled at rest)_ · `Add new rule above the selected one` · `Copy rule` _(disabled at rest)_ · `Edit rule` _(disabled at rest)_ · `Move rule up` _(disabled at rest)_ · `Move rule down` _(disabled at rest)_ · `Save rules` _(disabled at rest)_

**resources** (2): `DATA_RETENTION_RULES_RESOURCE` · `DATA_RETENTION_RULES_RESOURCE.update`

### DataRetentionPresenter

### DataRetentionRulePresenter

### DataSourceClient

**resources** (3): `DATA_SOURCE_RESOURCE.findFields` · `DATA_SOURCE_RESOURCE.fetchDocumentation` · `DATA_SOURCE_RESOURCE.fetchDefaultExtractionPipeline`

### DataTypeUiManager

**resources** (1): `META_RESOURCE`

### DataUploadPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `DATA_RESOURCE.upload`

### DateTimePopup

**buttons** (2): `OK` · `Cancel`

### DenseVectorFieldPresenter

**buttons** (2): `OK` · `Cancel`

### DependenciesInfoPresenter

**buttons** (1): `Close`

### DependenciesPresenter

**buttons** (1): `Unknown Document Type`

**resources** (2): `CONTENT_RESOURCE.fetchDependencies` · `EXPLORER_RESOURCE`

### DependenciesTabPresenter

### DictionaryListPresenter

**buttons** (2): `Add` · `Delete` _(disabled at rest)_

### DictionaryPlugin

**resources** (2): `DICTIONARY_RESOURCE.fetch` · `DICTIONARY_RESOURCE.update`

### DictionaryPresenter

**buttons** (3): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_ · `Download Dictionary words` _(disabled at rest)_

**resources** (1): `DICTIONARY_RESOURCE.download`

### DictionarySettingsPresenter

### DocPermissionRestClient

**resources** (1): `DOC_PERMISSION_RESOURCE.getDocUserPermissionsReport`

### DocSelectionBoxPresenter

**resources** (1): `EXPLORER_RESOURCE`

### DocSelectionPopup

**buttons** (2): `OK` · `Cancel`

**resources** (1): `EXPLORER_RESOURCE.getFromDocRef`

### DocTabPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### DocumentationPlugin

**resources** (2): `DOCUMENTATION_RESOURCE.fetch` · `DOCUMENTATION_RESOURCE.update`

### DocumentationPresenter

**buttons** (3): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_ · `Download` _(disabled at rest)_

**resources** (1): `DOCUMENTATION_RESOURCE.download`

### DocumentListPresenter

**resources** (1): `EXPLORER_RESOURCE.advancedFind`

### DocumentPermissionsListPresenter

**columns** (3): `Document` · `Permission` · `Inherited Permission`

**resources** (1): `EXPLORER_RESOURCE.advancedFindWithPermissions`

### DocumentPermissionsPlugin

### DocumentPluginEventManager

**resources** (12): `EXPLORER_RESOURCE.getFromDocRef` · `EXPLORER_RESOURCE.fetchDeleteConfirmation` · `EXPLORER_RESOURCE.create` · `EXPLORER_RESOURCE.copy` · `EXPLORER_RESOURCE.move` · `EXPLORER_RESOURCE.rename` · `EXPLORER_RESOURCE.delete` · `EXPLORER_FAV_RESOURCE.createUserFavourite` · `EXPLORER_FAV_RESOURCE.deleteUserFavourite` · `EXPLORER_RESOURCE.decorate` · `EXPLORER_RESOURCE.fetchExplorerPermissions` · `EXPLORER_RESOURCE.info`

### DocumentTypeCache

**resources** (1): `EXPLORER_RESOURCE`

### DocumentUserCreatePermissionsEditPresenter

**buttons** (2): `OK` · `Cancel`

### DocumentUserPermissionsEditPresenter

**buttons** (2): `OK` · `Cancel`

### DocumentUserPermissionsListPresenter

**columns** (4): `Explicit Permission` · `Effective Permission` · `Explicit Create Document Types` · `Effective Create Document Types`

**resources** (1): `DOC_PERMISSION_RESOURCE.fetchDocumentUserPermissions`

### DocumentUserPermissionsPresenter

### DropDownViewImpl

**buttons** (1): `Show Warnings`

### DuplicateManagementListPresenter

**buttons** (1): `Delete` _(disabled at rest)_

**resources** (2): `DUPLICATE_CHECK_RESOURCE.find` · `DUPLICATE_CHECK_RESOURCE.delete`

### DurationPresenter

**buttons** (2): `OK` · `Cancel`

### DynamicQueryHelpSelectionListModel

**resources** (1): `QUERY_RESOURCE.fetchQueryHelpItems`

### EditAccountPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `ACCOUNT_RESOURCE.create` · `ACCOUNT_RESOURCE.update` · `ACCOUNT_RESOURCE.fetch`

### EditApiKeyPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `API_KEY_RESOURCE.update` · `API_KEY_RESOURCE.delete` · `API_KEY_RESOURCE.create`

### EditApiKeyViewImpl

**buttons** (1): `Copy API Key to clipboard` _(disabled at rest)_

### EditExpressionPresenter

**buttons** (5): `Add Operator` · `Copy` _(disabled at rest)_ · `Disable` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Add Term`

### EditFeedDependencyPresenter

**resources** (1): `META_RESOURCE`

### ElasticClusterPlugin

**resources** (2): `ELASTIC_CLUSTER_RESOURCE.fetch` · `ELASTIC_CLUSTER_RESOURCE.update`

### ElasticClusterPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ElasticClusterSettingsPresenter

**resources** (1): `ELASTIC_CLUSTER_RESOURCE.testCluster`

### ElasticIndexPlugin

**resources** (2): `ELASTIC_INDEX_RESOURCE.fetch` · `ELASTIC_INDEX_RESOURCE.update`

### ElasticIndexPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ElasticIndexSettingsPresenter

**resources** (1): `ELASTIC_INDEX_RESOURCE.testIndex`

### ElementPresenter

**resources** (1): `STEPPING_RESOURCE.findElementDoc`

### EmailResetPasswordPresenter

**buttons** (2): `OK` · `Cancel`

### EmbeddedQueryPresenter

### EmbeddedQuerySettingsPresenter

**resources** (1): `QUERY_RESOURCE`

### ExecutionScheduleManager

**resources** (3): `EXECUTION_SCHEDULE_RESOURCE.fetchExecutionSchedule` · `EXECUTION_SCHEDULE_RESOURCE.updateExecutionSchedule` · `EXECUTION_SCHEDULE_RESOURCE`

### ExecutionScheduleRunNowPresenter

**buttons** (1): `Close`

### ExplorerClient

**resources** (2): `EXPLORER_RESOURCE.changeDocumentPermissions` · `EXPLORER_RESOURCE.advancedFind`

### ExplorerNodeEditTagsPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (4): `EXPLORER_RESOURCE` · `EXPLORER_RESOURCE.fetchExplorerNodeTags` · `EXPLORER_RESOURCE.addTags` · `EXPLORER_RESOURCE.updateNodeTags`

### ExplorerNodeEditTagsViewImpl

**buttons** (4): `Add selected tags from 'All Known Tags'` · `Remove selected tags` · `Clear entered tag(s)` _(disabled at rest)_ · `Add entered tag(s)`

### ExplorerNodeRemoveTagsPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (2): `EXPLORER_RESOURCE.fetchExplorerNodeTags` · `EXPLORER_RESOURCE.removeTags`

### ExplorerPopupPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `EXPLORER_RESOURCE.getFromDocRef`

### ExplorerTreeModel

**resources** (1): `EXPLORER_RESOURCE.fetchExplorerNodes`

### ExportConfigPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `CONTENT_RESOURCE.exportContent`

### ExpressionPresenter

### ExpressionValidator

**resources** (1): `EXPRESSION_RESOURCE.validate`

### FeedClient

**resources** (4): `FEED_RESOURCE.getDocRefForName` · `FEED_RESOURCE.fetch` · `FEED_RESOURCE.update` · `FEED_RESOURCE`

### FeedDependencyListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Remove` _(disabled at rest)_

**columns** (1): `Type`

### FeedDependencyPresenter

**buttons** (2): `OK` · `Cancel`

### FeedPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### FeedSettingsPresenter

**resources** (1): `VOLUME_GROUP_RESOURCE.find`

### FieldEditPresenter

**buttons** (2): `OK` · `Cancel`

### FieldListPresenter

**buttons** (3): `New Field` · `Edit Field` _(disabled at rest)_ · `Remove Field` _(disabled at rest)_

**columns** (3): `Name` · `Type` · `Obfuscated?`

### FindAnnotationPresenter

**buttons** (2): `OK` · `Cancel`

### FindDocResultListPresenter

**resources** (1): `EXPLORER_RESOURCE.find`

### FindInContentPresenter

**buttons** (1): `Close`

**resources** (2): `EXPLORER_RESOURCE.findInContent` · `EXPLORER_RESOURCE.fetchHighlights`

### FindPresenter

**buttons** (1): `Close`

### FolderPresenter

### FolderRootPresenter

### FormatPresenter

**buttons** (2): `OK` · `Cancel`

### FsVolumeEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `FS_VOLUME_RESOURCE.validate` · `FS_VOLUME_RESOURCE.update` · `FS_VOLUME_RESOURCE.create`

### FsVolumeGroupEditPresenter

**buttons** (6): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Rescan Volumes` · `OK` · `Cancel`

**resources** (5): `FS_VOLUME_RESOURCE` · `FS_VOLUME_RESOURCE.fetch` · `FS_VOLUME_RESOURCE.delete` · `FS_VOLUME_GROUP_RESOURCE.fetchByName` · `FS_VOLUME_GROUP_RESOURCE.update`

### FsVolumeGroupListPresenter

**columns** (3): `Group Name` · `Volume Type(s)` · `Volume Count`

**resources** (1): `FS_VOLUME_GROUP_RESOURCE`

### FsVolumeGroupPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**resources** (2): `FS_VOLUME_GROUP_RESOURCE.fetch` · `FS_VOLUME_GROUP_RESOURCE.delete`

### FsVolumeStatusListPresenter

**columns** (10): `Path` · `Type` · `Status` · `Total` · `Limit` · `Used` · `Free` · `Use%` · `Full` · `Usage Date`

**resources** (1): `FS_VOLUME_RESOURCE`

### GitRepoCommitDialogPresenter

**buttons** (2): `OK` · `Cancel`

### GitRepoPlugin

**resources** (2): `GIT_REPO_RESOURCE.fetch` · `GIT_REPO_RESOURCE.update`

### GitRepoPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### GitRepoSettingsPresenter

**resources** (4): `GIT_REPO_RESOURCE` · `GIT_REPO_RESOURCE.pushToGit` · `GIT_REPO_RESOURCE.pullFromGit` · `GIT_REPO_RESOURCE.areUpdatesAvailable`

### GlobalPropertyTabPresenter

**buttons** (2): `Edit` _(disabled at rest)_ · `Show Warnings`

### HttpClientConfigPresenter

**buttons** (2): `OK` · `Cancel`

### HttpTlsConfigPresenter

**buttons** (2): `OK` · `Cancel`

### ImportConfigConfirmPresenter

**buttons** (5): `Info` · `Alert` · `Error` · `OK` · `Cancel`

**columns** (4): `Action` · `Type` · `Source Path` · `Destination Path`

**resources** (2): `CONTENT_RESOURCE.importContent` · `CONTENT_RESOURCE`

### ImportConfigPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `CONTENT_RESOURCE.importContent`

### IncludeExcludeFilterDictionaryPresenter

**buttons** (2): `Add Dictionary` · `Remove Dictionary` _(disabled at rest)_

### IncludeExcludeFilterPresenter

### IndexFieldEditPresenter

**buttons** (2): `OK` · `Cancel`

### IndexFieldListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (1): `Case Sensitive`

**resources** (4): `INDEX_RESOURCE.addField` · `INDEX_RESOURCE.updateField` · `INDEX_RESOURCE.deleteField` · `INDEX_RESOURCE.findFields`

### IndexPlugin

**resources** (2): `INDEX_RESOURCE.fetch` · `INDEX_RESOURCE.update`

### IndexPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### IndexSettingsPresenter

**resources** (1): `INDEX_VOLUME_GROUP_RESOURCE.find`

### IndexShardPresenter

**buttons** (2): `Flush Selected Shards` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (3): `Path` · `Bytes pd` · `Index Version`

**resources** (3): `INDEX_RESOURCE.find` · `INDEX_RESOURCE.flushIndexShards` · `INDEX_RESOURCE.deleteIndexShards`

### IndexVolumeEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `INDEX_VOLUME_RESOURCE.validate` · `INDEX_VOLUME_RESOURCE.create` · `INDEX_VOLUME_RESOURCE.update`

### IndexVolumeGroupEditPresenter

**buttons** (6): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Rescan Volumes` · `OK` · `Cancel`

**resources** (5): `INDEX_VOLUME_RESOURCE.rescan` · `INDEX_VOLUME_RESOURCE.fetch` · `INDEX_VOLUME_RESOURCE.delete` · `INDEX_VOLUME_GROUP_RESOURCE.fetchByName` · `INDEX_VOLUME_GROUP_RESOURCE.update`

### IndexVolumeGroupListPresenter

**columns** (1): `Name`

**resources** (1): `INDEX_VOLUME_GROUP_RESOURCE.find`

### IndexVolumeGroupPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**resources** (2): `INDEX_VOLUME_GROUP_RESOURCE.fetch` · `INDEX_VOLUME_GROUP_RESOURCE.delete`

### IndexVolumeStatusListPresenter

**columns** (8): `Status` · `Total` · `Limit` · `Used` · `Free` · `Use%` · `Full` · `Usage Date`

**resources** (1): `INDEX_VOLUME_RESOURCE.find`

### InfoColumn

**buttons** (1): `Info`

### InfoDocumentPresenter

**buttons** (1): `Close`

### ItemNavigatorPresenter

### ItemNavigatorViewImpl

**buttons** (4): `First` _(disabled at rest)_ · `Backward` _(disabled at rest)_ · `Forward` _(disabled at rest)_ · `Last` _(disabled at rest)_

### ItemSelectionPresenter

**buttons** (2): `OK` · `Cancel`

### JobListPresenter

**buttons** (1): `Help`

**columns** (3): `Enabled` · `Job` · `Description`

**resources** (1): `JOB_RESOURCE`

### JobNodeListHelper

**buttons** (1): `Run`

**columns** (6): `Enabled` · `Type` · `Node State` · `Schedule` · `Last Executed` · `Next Scheduled`

**resources** (1): `JOB_NODE_RESOURCE`

### JobNodeListPresenter

**columns** (3): `Node` · `Max Tasks` · `Current Tasks`

**resources** (3): `JOB_NODE_RESOURCE.find` · `JOB_NODE_RESOURCE.info` · `JOB_NODE_RESOURCE.setTaskLimit`

### JobPresenter

### KafkaConfigPlugin

**resources** (2): `KAFKA_CONFIG_RESOURCE.fetch` · `KAFKA_CONFIG_RESOURCE.update`

### KafkaConfigPresenter

**buttons** (3): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_ · `Download` _(disabled at rest)_

**resources** (1): `KAFKA_CONFIG_RESOURCE.download`

### LinkedEventPresenter

**buttons** (2): `Add Event Link` · `Remove Event Link` _(disabled at rest)_

### ListInputPresenter

**resources** (1): `WORD_LIST_RESOURCE.getWords`

### LoginManager

**resources** (2): `APP_PERMISSION_RESOURCE` · `STROOM_SESSION_RESOURCE.logout`

### LoginPresenter

### ManageActivityPresenter

**buttons** (4): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Close`

**resources** (3): `ACTIVITY_RESOURCE` · `ACTIVITY_RESOURCE.fetch` · `ACTIVITY_RESOURCE.delete`

### ManageFsVolumesPlugin

### ManageGlobalPropertiesPlugin

### ManageGlobalPropertyEditPresenter

**buttons** (5): `Click to see cluster values` · `All nodes have the same effective value` · `Help` · `OK` · `Cancel`

**resources** (4): `GLOBAL_CONFIG_RESOURCE_RESOURCE.getPropertyByName` · `GLOBAL_CONFIG_RESOURCE_RESOURCE.getYamlValueByNodeAndName` · `GLOBAL_CONFIG_RESOURCE_RESOURCE.create` · `GLOBAL_CONFIG_RESOURCE_RESOURCE.update`

### ManageGlobalPropertyListPresenter

**resources** (2): `GLOBAL_CONFIG_RESOURCE_RESOURCE.list` · `GLOBAL_CONFIG_RESOURCE_RESOURCE.listByNode`

### ManageIndexVolumesPlugin

### MarkdownEditPresenter

**buttons** (1): `Documentation help`

### MarkdownTabProvider

### MarkerListPresenter

**buttons** (4): `Fatal Error` · `Error` · `Warning` · `Info`

### MetaListPresenter

**buttons** (2): `Deleted Stream` · `Locked Stream`

**columns** (1): `Type`

**resources** (5): `META_RESOURCE.findMetaRow` · `META_RESOURCE.updateStatus` · `DATA_RESOURCE.download` · `PROCESSOR_FILTER_RESOURCE.create` · `PROCESSOR_FILTER_RESOURCE.reprocess`

### MetaPresenter

**buttons** (7): `Process` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Selection summary` · `Download` _(disabled at rest)_ · `Upload` · `Filter` · `Restore` _(disabled at rest)_

**resources** (1): `META_RESOURCE.getSelectionSummary`

### MetaRelationListPresenter

**buttons** (2): `Deleted Stream` · `Locked Stream`

**columns** (1): `Type`

**resources** (5): `META_RESOURCE.findMetaRow` · `META_RESOURCE.updateStatus` · `DATA_RESOURCE.download` · `PROCESSOR_FILTER_RESOURCE.create` · `PROCESSOR_FILTER_RESOURCE.reprocess`

### MoveDocumentPresenter

**buttons** (2): `OK` · `Cancel`

### MultiRulesPresenter

### NameDocumentPresenter

**buttons** (2): `OK` · `Cancel`

### NamePresenter

**buttons** (2): `OK` · `Cancel`

**resources** (2): `STORED_QUERY_RESOURCE.create` · `STORED_QUERY_RESOURCE.update`

### NewElementPresenter

**buttons** (2): `OK` · `Cancel`

### NewFsVolumeGroupPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (2): `FS_VOLUME_GROUP_RESOURCE.fetchByName` · `FS_VOLUME_GROUP_RESOURCE.create`

### NewIndexVolumeGroupPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (2): `INDEX_VOLUME_GROUP_RESOURCE.fetchByName` · `INDEX_VOLUME_GROUP_RESOURCE.create`

### NewNodeGroupPresenter

**buttons** (2): `OK` · `Cancel`

### NewPipelineReferencePresenter

**resources** (1): `META_RESOURCE`

### NewPropertyPresenter

**resources** (2): `META_RESOURCE` · `VOLUME_GROUP_RESOURCE.find`

### NodeClient

**resources** (6): `NODE_RESOURCE.find` · `NODE_RESOURCE.ping` · `NODE_RESOURCE.info` · `NODE_RESOURCE.setPriority` · `NODE_RESOURCE.setEnabled` · `NODE_RESOURCE`

### NodeGroupClient

**resources** (8): `NODE_GROUP_RESOURCE` · `NODE_GROUP_RESOURCE.fetchByName` · `NODE_GROUP_RESOURCE.create` · `NODE_GROUP_RESOURCE.update` · `NODE_GROUP_RESOURCE.fetchById` · `NODE_GROUP_RESOURCE.delete` · `NODE_GROUP_RESOURCE.getNodeGroupState` · `NODE_GROUP_RESOURCE.updateNodeGroupState`

### NodeGroupEditPresenter

**buttons** (2): `OK` · `Cancel`

### NodeGroupListPresenter

**columns** (1): `Enabled`

### NodeGroupPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

### NodeGroupsPlugin

### NodeGroupStateListPresenter

**columns** (3): `Status` · `Name` · `Cluster Base Endpoint`

### NodeJobListPresenter

**columns** (2): `Job Name` · `Job State`

**resources** (1): `JOB_NODE_RESOURCE`

### NodeStatusListPresenter

**columns** (8): `Name` · `Cluster Base Endpoint` · `Build Version` · `Ping (ms)` · `Up Date` · `Master` · `Priority` · `Enabled`

### OpenAIModelPlugin

**resources** (2): `OPEN_AI_MODEL_RESOURCE.fetch` · `OPEN_AI_MODEL_RESOURCE.update`

### OpenAIModelPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### OpenAIModelSettingsPresenter

**resources** (2): `OPEN_AI_MODEL_RESOURCE.validateModel` · `OPEN_AI_MODEL_RESOURCE`

### Pager

**buttons** (4): `First` _(disabled at rest)_ · `Backward` _(disabled at rest)_ · `Forward` _(disabled at rest)_ · `Last` _(disabled at rest)_

### PathwayEditPresenter

**buttons** (2): `OK` · `Cancel`

### PathwayListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (1): `Root`

**resources** (4): `PATHWAYS_RESOURCE.addPathway` · `PATHWAYS_RESOURCE.updatePathway` · `PATHWAYS_RESOURCE.deletePathway` · `PATHWAYS_RESOURCE.findPathways`

### PathwaysPlugin

**resources** (2): `PATHWAYS_RESOURCE.fetch` · `PATHWAYS_RESOURCE.update`

### PathwaysPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### PathwaysSplitPresenter

### PathwayTreePresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

### PermissionChangeClient

**resources** (1): `PERMISSION_CHANGE_RESOURCE.changeDocumentPermissions`

### PipelineElementTypesFactory

**resources** (1): `PIPELINE_RESOURCE`

### PipelineModelFactory

**resources** (1): `PIPELINE_RESOURCE.fetchPipelineLayers`

### PipelinePlugin

**resources** (4): `PIPELINE_RESOURCE.fetch` · `PIPELINE_RESOURCE.update` · `STEPPING_RESOURCE.getPipelineForStepping` · `META_RESOURCE.findMetaRow`

### PipelinePresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### PipelineReferenceListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Remove` _(disabled at rest)_

**columns** (4): `Pipeline` · `Feed` · `Type` · `Inherited From`

**resources** (1): `EXPLORER_RESOURCE.fetchDocRefs`

### PipelineStructurePresenter

**resources** (3): `PIPELINE_RESOURCE.fetchPipelineJson` · `PIPELINE_RESOURCE.savePipelineJson` · `PIPELINE_RESOURCE.fetchPipelineLayers`

### PipelineStructureViewImpl

**buttons** (4): `Add New Pipeline Element` · `Remove Pipeline Element` _(disabled at rest)_ · `Edit Pipeline Element` _(disabled at rest)_ · `Restore Pipeline Element` _(disabled at rest)_

### PlanBPlugin

**resources** (2): `PLAN_B_DOC_RESOURCE.fetch` · `PLAN_B_DOC_RESOURCE.update`

### PlanBPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ProcessChoicePresenter

**buttons** (2): `OK` · `Cancel`

### ProcessorEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (3): `EXPRESSION_RESOURCE.validate` · `PROCESSOR_FILTER_RESOURCE.update` · `PROCESSOR_FILTER_RESOURCE.create`

### ProcessorListPresenter

**buttons** (2): `Filter` · `Process` _(disabled at rest)_

**columns** (1): `Run As User`

**resources** (5): `PROCESSOR_RESOURCE.setEnabled` · `PROCESSOR_FILTER_RESOURCE.setEnabled` · `PROCESSOR_FILTER_RESOURCE.setPriority` · `PROCESSOR_FILTER_RESOURCE.setMaxProcessingTasks` · `PROCESSOR_FILTER_RESOURCE.find`

### ProcessorPresenter

**buttons** (4): `Add Processor` · `Edit Processor` _(disabled at rest)_ · `Duplicate Processor` _(disabled at rest)_ · `Delete Processor` _(disabled at rest)_

**resources** (2): `PROCESSOR_FILTER_RESOURCE.fetch` · `PROCESSOR_FILTER_RESOURCE.delete`

### ProcessorProfileClient

**resources** (6): `PROCESSOR_PROFILE_RESOURCE` · `PROCESSOR_PROFILE_RESOURCE.create` · `PROCESSOR_PROFILE_RESOURCE.fetchById` · `PROCESSOR_PROFILE_RESOURCE.fetchByName` · `PROCESSOR_PROFILE_RESOURCE.update` · `PROCESSOR_PROFILE_RESOURCE.delete`

### ProcessorProfileEditPresenter

**buttons** (2): `OK` · `Cancel`

### ProcessorProfilePlugin

### ProcessorProfilePresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

### ProcessorTaskListPresenter

**columns** (2): `Status` · `Priority`

**resources** (2): `META_RESOURCE.findMetaRow` · `PROCESSOR_TASK_RESOURCE.find`

### ProcessorTaskSummaryPresenter

**columns** (1): `Priority`

**resources** (1): `PROCESSOR_TASK_RESOURCE.findSummary`

### ProfilePeriodEditPresenter

**buttons** (2): `OK` · `Cancel`

### ProfilePeriodListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Remove` _(disabled at rest)_

**columns** (5): `Days` · `Start Time` · `End Time` · `Max Node Threads` · `Max Cluster Threads`

### PropertyListPresenter

**buttons** (1): `Edit` _(disabled at rest)_

**columns** (1): `Property Name`

**resources** (1): `EXPLORER_RESOURCE.fetchDocRefs`

### QueryClient

**resources** (1): `QUERY_RESOURCE.fetch`

### QueryDocEditPresenter

**resources** (6): `QUERY_RESOURCE.validateQuery` · `EXPLORER_RESOURCE.getFromDocRef` · `ANALYTIC_RULE_RESOURCE.fetch` · `ANALYTIC_RULE_RESOURCE.update` · `REPORT_RESOURCE.fetch` · `REPORT_RESOURCE.update`

### QueryDocPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### QueryFavouritesPresenter

**buttons** (5): `Create Favourite From Current Query` · `Change Favourite Name` _(disabled at rest)_ · `Delete Favourite` _(disabled at rest)_ · `OK` · `Cancel`

**resources** (2): `STORED_QUERY_RESOURCE.find` · `STORED_QUERY_RESOURCE.delete`

### QueryHelpAceCompletionProvider

**resources** (1): `QUERY_RESOURCE.fetchCompletions`

### QueryHelpDetailProvider

**resources** (1): `QUERY_RESOURCE.fetchDetail`

### QueryHistoryPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `STORED_QUERY_RESOURCE.find`

### QueryInfo

### QueryInfoPresenter

**buttons** (2): `OK` · `Cancel`

### QueryModel

**resources** (1): `QUERY_RESOURCE.search`

### QueryPlugin

**resources** (2): `QUERY_RESOURCE.fetch` · `QUERY_RESOURCE.update`

### QueryPresenter

**buttons** (9): `Copy` _(disabled at rest)_ · `Disable` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `History` _(disabled at rest)_ · `Favourites` _(disabled at rest)_ · `Download` _(disabled at rest)_ · `Process` _(disabled at rest)_ · `Add Term` · `Add Operator`

**resources** (2): `PROCESSOR_FILTER_RESOURCE.create` · `DASHBOARD_RESOURCE.downloadQuery`

### QueryResultTablePresenter

**buttons** (4): `Download` _(disabled at rest)_ · `Undo` _(disabled at rest)_ · `Annotate` · `Ask Stroom AI`

**resources** (2): `QUERY_RESOURCE.downloadSearchResults` · `QUERY_RESOURCE.fetchDataSourceFromQueryString`

### QueryResultTableSplitPresenter

### QueryResultVisPresenter

**resources** (2): `VISUALISATION_RESOURCE.fetch` · `SCRIPT_RESOURCE.fetchLinkedScripts`

### QuerySettingsPresenter

### QueryTableColumnsManager

### QueryTableColumnValuesDataSupplier

**resources** (1): `QUERY_RESOURCE.getColumnValues`

### ReceiveDataRuleSetPlugin

### RecentItemsPresenter

**buttons** (1): `Close`

### RenameColumnPresenter

**buttons** (2): `OK` · `Cancel`

### RenameTabPresenter

**buttons** (2): `OK` · `Cancel`

### ReportNotificationListPresenter

**buttons** (3): `Add` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (5): `Enabled` · `Type` · `Destination` · `Limit` · `Max`

### ReportPlugin

**resources** (2): `REPORT_RESOURCE.fetch` · `REPORT_RESOURCE.update`

### ReportPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ResetPasswordPresenter

### ResultStoreListPresenter

**buttons** (3): `Terminate Search` · `Delete Store` _(disabled at rest)_ · `Store Settings`

**columns** (1): `User Display Name`

### ResultStoreModel

**resources** (4): `RESULT_STORE_RESOURCE.find` · `RESULT_STORE_RESOURCE.terminate` · `RESULT_STORE_RESOURCE.destroy` · `RESULT_STORE_RESOURCE.update`

### ResultStorePlugin

### ResultStorePresenter

**buttons** (1): `Close`

### ResultStoreSettingsPresenter

**buttons** (2): `OK` · `Cancel`

### RuleListPresenter

**columns** (4): `Expression` · `Style` · `Enabled` · `Hide Row`

### RulePresenter

### RuleSetListPresenter

**columns** (5): `Rule` · `Name` · `Expression` · `Action` · `State`

### RuleSetPresenter

**buttons** (2): `Save all rules` _(disabled at rest)_ · `Show Warnings`

**resources** (1): `RULES_RESOURCE`

### RuleSetSettingsPresenter

**buttons** (7): `Add new rule` · `Edit selected rule` _(disabled at rest)_ · `Copy selected rule` _(disabled at rest)_ · `Disable/enable selected rule` _(disabled at rest)_ · `Delete selected rule` _(disabled at rest)_ · `Move selected rule up` _(disabled at rest)_ · `Move selected rule down` _(disabled at rest)_

### RulesPresenter

**buttons** (7): `Add` · `Edit` _(disabled at rest)_ · `Copy` _(disabled at rest)_ · `Disable` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Up` _(disabled at rest)_ · `Down` _(disabled at rest)_

### S3ConfigPlugin

**resources** (2): `S3_CONFIG_RESOURCE.fetch` · `S3_CONFIG_RESOURCE.update`

### S3ConfigPresenter

**buttons** (3): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_ · `Download` _(disabled at rest)_

**resources** (1): `S3_CONFIG_RESOURCE.download`

### ScheduledProcessEditPresenter

**buttons** (2): `OK` · `Cancel`

**resources** (1): `EXECUTION_SCHEDULE_RESOURCE.fetchTracker`

### ScheduledProcessHistoryListPresenter

**buttons** (1): `Rerun`

**resources** (1): `EXECUTION_SCHEDULE_RESOURCE.fetchExecutionHistory`

### ScheduledProcessingPresenter

**resources** (3): `EXECUTION_SCHEDULE_RESOURCE.createExecutionSchedule` · `EXECUTION_SCHEDULE_RESOURCE.updateExecutionSchedule` · `EXECUTION_SCHEDULE_RESOURCE.deleteExecutionSchedule`

### ScheduledProcessListPresenter

**buttons** (3): `Add` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**columns** (1): `Enabled`

**resources** (2): `EXECUTION_SCHEDULE_RESOURCE` · `EXECUTION_SCHEDULE_RESOURCE.fetchExecutionSchedule`

### ScheduledTimeClient

**resources** (1): `SCHEDULED_TIME_RESOURCE.get`

### SchedulePopup

**buttons** (2): `OK` · `Cancel`

### ScriptDependencyListPresenter

**buttons** (2): `Add` · `Remove` _(disabled at rest)_

### ScriptListPresenter

**columns** (1): `Name`

### ScriptPlugin

**resources** (2): `SCRIPT_RESOURCE.fetch` · `SCRIPT_RESOURCE.update`

### ScriptPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ScriptSettingsPresenter

### SearchModel

**resources** (1): `DASHBOARD_RESOURCE.search`

### SelectionHandlerListPresenter

**columns** (2): `Enabled` · `Expression`

### SelectionHandlerPresenter

### SelectionHandlersPresenter

**buttons** (7): `Add` · `Edit` _(disabled at rest)_ · `Copy` _(disabled at rest)_ · `Disable` _(disabled at rest)_ · `Delete` _(disabled at rest)_ · `Up` _(disabled at rest)_ · `Down` _(disabled at rest)_

### SelectionSummaryPresenter

**buttons** (3): `OK` · `Cancel` · `Close`

**resources** (2): `META_RESOURCE.getReprocessSelectionSummary` · `META_RESOURCE.getSelectionSummary`

### SigningKeyListPresenter

**columns** (2): `Status` · `Issued`

**resources** (1): `SIGNING_KEY_RESOURCE`

### SigningKeyPlugin

**buttons** (1): `Key`

### SigningKeyPresenter

**buttons** (2): `Revoke the selected signing key` _(disabled at rest)_ · `Clear` _(disabled at rest)_

**resources** (2): `SIGNING_KEY_RESOURCE.revoke` · `SIGNING_KEY_RESOURCE`

### SignOutOtherSessionsPlugin

**resources** (1): `SESSION_RESOURCE`

### SolrIndexFieldEditPresenter

**buttons** (2): `OK` · `Cancel`

### SolrIndexFieldListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Delete` _(disabled at rest)_

**resources** (1): `SOLR_INDEX_RESOURCE.fetchSolrTypes`

### SolrIndexPlugin

**resources** (2): `SOLR_INDEX_RESOURCE.fetch` · `SOLR_INDEX_RESOURCE.update`

### SolrIndexPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### SolrIndexSettingsPresenter

**resources** (1): `SOLR_INDEX_RESOURCE.solrConnectionTest`

### SourcePresenter

**resources** (1): `DATA_RESOURCE.fetch`

### SplashPresenter

**buttons** (2): `Accept` · `Reject`

**resources** (1): `ACTIVITY_RESOURCE.acknowledgeSplash`

### StatisticsCustomMaskListPresenter

**buttons** (3): `New` · `Auto-generate roll-up permutations` · `Remove` _(disabled at rest)_

### StatisticsDataSourcePresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### StatisticsFieldEditPresenter

**buttons** (2): `OK` · `Cancel`

### StatisticsFieldListPresenter

**buttons** (3): `New` · `Edit` _(disabled at rest)_ · `Remove` _(disabled at rest)_

**columns** (1): `Name`

### StatisticsPlugin

**resources** (2): `STATISTIC_RESOURCE.fetch` · `STATISTIC_RESOURCE.update`

### SteppingFilterPresenter

**buttons** (6): `Has active filter(s)` · `Add XPath Filter` · `Edit XPath Filter` _(disabled at rest)_ · `Delete XPath Filter` _(disabled at rest)_ · `OK` · `Cancel`

### SteppingMetaListPresenter

**buttons** (2): `Deleted Stream` · `Locked Stream`

**columns** (1): `Type`

**resources** (5): `META_RESOURCE.findMetaRow` · `META_RESOURCE.updateStatus` · `DATA_RESOURCE.download` · `PROCESSOR_FILTER_RESOURCE.create` · `PROCESSOR_FILTER_RESOURCE.reprocess`

### SteppingPresenter

**buttons** (1): `Filter`

**resources** (2): `STEPPING_RESOURCE.step` · `STEPPING_RESOURCE.terminateStepping`

### StreamingProcessingPresenter

**resources** (1): `ANALYTIC_PROCESS_RESOURCE.getDefaultProcessingFilterExpression`

### SubStreamNavigator

**buttons** (8): `First Part` _(disabled at rest)_ · `Previous Part` _(disabled at rest)_ · `Next Part` _(disabled at rest)_ · `Last Part` _(disabled at rest)_ · `First Record` _(disabled at rest)_ · `Previous Record` _(disabled at rest)_ · `Next Record` _(disabled at rest)_ · `Last Record` _(disabled at rest)_

### TableBuilderProcessingPresenter

**resources** (1): `ANALYTIC_PROCESS_RESOURCE.getTracker`

### TableCollapseButton

**buttons** (1): `Collapse All`

### TableColumnValuesDataSupplier

**resources** (1): `DASHBOARD_RESOURCE.getColumnValues`

### TableExpandButton

**buttons** (1): `Expand All`

### TableFilterPlugin

### TableFilterPresenter

**buttons** (2): `OK` · `Cancel`

### TablePlugin

### TablePresenter

**buttons** (4): `Download` _(disabled at rest)_ · `Annotate` · `Add Column` · `Ask Stroom AI`

**resources** (1): `DASHBOARD_RESOURCE.downloadSearchResults`

### TableSettingsPresenter

### TabManager

### TabSessionManager

**resources** (3): `TAB_SESSION_RESOURCE` · `TAB_SESSION_RESOURCE.add` · `TAB_SESSION_RESOURCE.delete`

### TaskManagerListPresenter

**buttons** (4): `Delete` _(disabled at rest)_ · `Expand All` · `Collapse All` · `Show Warnings`

**resources** (2): `TASK_RESOURCE.find` · `TASK_RESOURCE.terminate`

### TaskManagerPresenter

### TextBoxPopup

**buttons** (2): `OK` · `Cancel`

### TextConverterPlugin

**resources** (3): `TEXT_CONVERTER_RESOURCE.fetch` · `TEXT_CONVERTER_RESOURCE.update` · `TEXT_CONVERTER_RESOURCE.create`

### TextConverterPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### TextPresenter

**resources** (1): `DATA_RESOURCE.fetch`

### TimePopup

**buttons** (2): `OK` · `Cancel`

### TimeZones

**resources** (1): `QUERY_RESOURCE`

### TracesDocPlugin

**resources** (2): `TRACES_DOC_RESOURCE.fetch` · `TRACES_DOC_RESOURCE.update`

### TracesListPresenter

**columns** (3): `Operation` · `Root Duration` · `Trace Duration`

**resources** (1): `TRACES_RESOURCE.findTracesWithHistogram`

### TracesListTabPresenter

**resources** (2): `TRACES_RESOURCE.findTrace` · `TRACES_RESOURCE.getSpans`

### TracesPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### UiConfigCache

**resources** (1): `CONFIG_RESOURCE`

### UserAccessListPresenter

**columns** (3): `User` · `Subject Id` · `Nodes`

**resources** (1): `USER_ACCESS_RESOURCE.find`

### UserAccessPlugin

**buttons** (1): `Shield`

### UserAccessPresenter

**buttons** (2): `End this user's sessions and revoke their tokens` _(disabled at rest)_ · `Open this user, where they can be disabled` _(disabled at rest)_

**resources** (1): `USER_ACCESS_RESOURCE.revokeAccess`

### UserAndGroupHelper

**buttons** (2): `User Group` · `User`

**resources** (1): `USER_RESOURCE`

### UserAndGroupsPresenter

**buttons** (5): `Create Group` · `Edit Group` _(disabled at rest)_ · `Delete Group` _(disabled at rest)_ · `Add` · `Remove` _(disabled at rest)_

**resources** (3): `USER_RESOURCE.addUserToGroup` · `USER_RESOURCE.removeUserFromGroup` · `USER_RESOURCE.copyGroupsAndPermissions`

### UserDependenciesListPresenter

**columns** (2): `Document Name` · `Details`

**resources** (1): `USER_RESOURCE.findDependencies`

### UserInfoPresenter

**resources** (1): `USER_RESOURCE`

### UserListPresenter

**columns** (1): `Enabled`

**resources** (2): `USER_RESOURCE` · `USER_RESOURCE.find`

### UserPermissionReportPresenter

### UserPermissionsReportPlugin

### UserPreferencesManager

**resources** (3): `PREFERENCES_RESOURCE` · `PREFERENCES_RESOURCE.update` · `PREFERENCES_RESOURCE.setDefaultUserPreferences`

### UserPreferencesPlugin

### UserPreferencesPresenter

**buttons** (2): `OK` · `Cancel`

### UserRefPopupPresenter

**buttons** (2): `OK` · `Cancel`

### UserRefSelectionBoxPresenter

### UsersAndGroupsPlugin

**buttons** (1): `User Group`

### UserSessionsListPresenter

**buttons** (1): `End this session` _(disabled at rest)_

**columns** (1): `Node`

**resources** (2): `USER_ACCESS_RESOURCE.listSessions` · `SESSION_RESOURCE.terminateSession`

### UsersPlugin

**buttons** (1): `User`

### UsersPresenter

**buttons** (2): `Create User` · `Delete User` _(disabled at rest)_

### UserTabPlugin

**buttons** (2): `User` · `User Group`

### UserTabPresenter

### UserTaskManagerPresenter

**buttons** (1): `Close`

**resources** (2): `TASK_RESOURCE.userTasks` · `TASK_RESOURCE.terminate`

### ViewPlugin

**resources** (2): `VIEW_RESOURCE.fetch` · `VIEW_RESOURCE.update`

### ViewPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### ViewSettingsPresenter

### VisPresenter

**resources** (3): `VISUALISATION_RESOURCE.fetch` · `VISUALISATION_ASSET_RESOURCE.indexAssetExists` · `SCRIPT_RESOURCE.fetchLinkedScripts`

### VisualisationAssetsAddItemDialogPresenter

**buttons** (2): `OK` · `Cancel`

### VisualisationAssetsEditAssetDialogPresenter

**buttons** (2): `OK` · `Cancel`

### VisualisationAssetsPresenter

**resources** (1): `VISUALISATION_ASSET_RESOURCE`

### VisualisationAssetsUploadFileDialogPresenter

**buttons** (2): `OK` · `Cancel`

### VisualisationPlugin

**resources** (2): `VISUALISATION_RESOURCE.fetch` · `VISUALISATION_RESOURCE.update`

### VisualisationPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_

### WelcomePresenter

**resources** (1): `SESSION_INFO_RESOURCE`

### WordListPresenter

**columns** (4): `Word` · `Type` · `Source Dictionary` · `Additional Source Dictionaries`

**resources** (1): `WORD_LIST_RESOURCE`

### XMLSchemaPlugin

**resources** (2): `XML_SCHEMA_RESOURCE.fetch` · `XML_SCHEMA_RESOURCE.update`

### XMLSchemaPresenter

**buttons** (3): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_ · `Alert`

**resources** (1): `XML_SCHEMA_RESOURCE.validate`

### XPathFilterPresenter

**buttons** (2): `OK` · `Cancel`

### XPathListPresenter

**columns** (4): `XPath` · `Condition` · `Value` · `Ignore Case`

### XsltPlugin

**resources** (3): `XSLT_RESOURCE.fetch` · `XSLT_RESOURCE.update` · `XSLT_RESOURCE.create`

### XsltPresenter

**buttons** (2): `Save` _(disabled at rest)_ · `Save As` _(disabled at rest)_
