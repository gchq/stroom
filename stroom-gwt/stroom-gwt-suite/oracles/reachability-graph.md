# Reachability graph — how every GWT screen and dialog is entered

**Generated** by `stroom-gwt-suite/tools/build-reachability-graph.mjs`. Do not hand-edit.

`gwt-inventory.csv` says what exists. This says how you get to it, which is what a test
suite actually needs: a screen that cannot be reached cannot be photographed.

## Totals

| | count |
| --- | ---: |
| Presenters in Screen/Dialog + Shared | 418 |
| …across areas | 47 |
| Document editors (`extends DocumentPlugin`) | 29 |
| Screens (`extends ContentTabPlugin` and friends) | 21 |
| Classes that raise a context menu (`ShowMenuEvent`) | 27 |
| Classes that show THEMSELVES as a dialog | 108 |
| Parent→child dialog edges | 43 (from 40 parents) |

## The dialog graph

Each line is "this presenter can open these". A test plan walks this: reach the parent,
then each child is one affordance away. Depth beyond 1 is where the long tail lives.

* **AbstractComponentPresenter** → SettingsPresenter
* **AbstractNotificationListPresenter** → AnalyticNotificationEditPresenter
* **AnnotationEditPresenter** → AnnotationTag, CommentEditPresenter
* **AnnotationTagEditPresenter** → DocumentUserPermissionsPresenter
* **AskStroomAiPresenter** → DownloadChatPresenter
* **BatchDocumentPermissionsPresenter** → BatchDocumentPermissionsEditPresenter
* **ChangeStatusPresenter** → AnnotationTag
* **ContentStoreContentPackDetailsPresenter** → CredentialsManagerDialogPresenter
* **ContentTemplateTabPresenter** → ContentTemplateEditPresenter
* **DashboardPresenter** → CurrentSelectionPresenter, LayoutConstraintPresenter
* **DataDisplaySupport** → presenter
* **DataRetentionImpactPresenter** → EditExpressionPresenter
* **DataRetentionPolicyPresenter** → DataRetentionRulePresenter
* **DependenciesPresenter** → MenuPresenter
* **ExecutionScheduleManager** → ExpressionPresenter
* **FeedDependencyListPresenter** → EditFeedDependencyPresenter
* **GitRepoSettingsPresenter** → GitRepoCommitDialogPresenter
* **HyperlinkEventHandlerImpl** → IFrameContentPresenter
* **JobNodeListHelper** → MenuPresenter
* **Menu** → MenuPresenter
* **MenuPresenter** → MenuPresenter
* **MetaPresenter** → ExpressionPresenter
* **PipelinePlugin** → DocRefSelectionPresenter
* **PipelineReferenceListPresenter** → NewPipelineReferencePresenter
* **PipelineStructurePresenter** → EditorPresenter
* **ProcessorPresenter** → ExpressionPresenter
* **PropertyListPresenter** → NewPropertyPresenter
* **QueryPresenter** → ProcessorLimitsPresenter
* **QueryResultTablePresenter** → DownloadPresenter
* **QueryTableColumnsManager** → RulesPresenter
* **RuleSetSettingsPresenter** → rulePresenter
* **RulesPresenter** → RulePresenter
* **SelectionHandlersPresenter** → SelectionHandlerPresenter
* **StepLocationLinkPresenter** → StepLocationPresenter
* **SteppingPresenter** → ElementPresenter
* **TabSessionManager** → TabSession
* **TablePresenter** → DownloadPresenter
* **UserAndGroupHelper** → CreateNewGroupPresenter
* **UserPermissionReportPresenter** → BatchDocumentPermissionsEditPresenter
* **VisualisationAssetsPresenter** → VisualisationAssetsAddItemDialogPresenter, VisualisationAssetsEditAssetDialogPresenter

## Dialogs that show themselves

These raise `ShowPopupEvent.builder(this)`, so the popup site does not name a parent.
The parent is whoever holds a `Provider` for them, which IS in the source — resolved below.
An entry with no opener is genuinely unreached and needs manual research.

* **AboutPresenter** ← opened by AboutPlugin
* **ActivityEditPresenter** ← opened by ManageActivityPresenter
* **AddEventLinkPresenter** ← opened by LinkedEventPresenter
* **AiAttachmentDataPresenter** ← opened by AskStroomAiPresenter
* **AiChatHistoryPresenter** ← opened by AskStroomAiPresenter
* **AnnotationTagCreatePresenter** ← opened by AnnotationTagPresenter
* **AnnotationTagEditPresenter** ← opened by AnnotationTagCreatePresenter, AnnotationTagPresenter
* **AskStroomAiConfigPresenter** ← opened by AskStroomAiPresenter
* **AskStroomAiPresenter** ← opened by AskStroomAIGinjector, MainPresenter, MyDataGridAiSupport, QueryResultTablePresenter, TablePresenter
* **BatchDocumentPermissionsEditPresenter** ← opened by BatchDocumentPermissionsPresenter, UserPermissionReportPresenter
* **BatchDocumentPermissionsPresenter** ← opened by DocumentPermissionsPlugin
* **BatchExecutionScheduleEditPresenter** ← opened by ExecutionScheduleManager
* **BatchProcessorFilterEditPresenter** ← opened by ProcessorPresenter
* **ChangeAssignedToPresenter** ← opened by AnnotationManager
* **ChangePasswordPresenter** ← opened by ChangePasswordGinjector, CurrentPasswordPresenter, EditAccountPresenter, LoginPresenter, ResetPasswordPresenter
* **ChangeStatusPresenter** ← opened by AnnotationManager
* **CharacterRangeSelectionPresenter** ← opened by CharacterNavigatorPresenter
* **ColumnFilterPresenter** ← opened by QueryResultTablePresenter, QueryTableColumnsManager, TableFilterPresenter
* **ColumnFunctionEditorPresenter** ← opened by ColumnsManager, TablePresenter
* **ColumnValuesFilterPresenter** ← opened by ColumnsManager, QueryResultTablePresenter, QueryTableColumnsManager, TableFilterPresenter, TablePresenter
* **CommonAlertPresenter** ← opened by AlertPlugin
* **ConfigPropertyClusterValuesPresenter** ← opened by ManageGlobalPropertyEditPresenter
* **ConstraintEditPresenter** ← opened by ConstraintListPresenter
* **CopyDocumentPresenter** ← opened by DocumentPluginEventManager
* **CreateDocumentPresenter** ← opened by DocumentPlugin, DocumentPluginEventManager, ExplorerPopupPresenter, QueryDocEditPresenter
* **CreateUserPresenter** ← opened by UserAndGroupsPresenter, UsersPresenter
* **CredentialEditPresenter** ← opened by CredentialsListPresenter
* **CredentialsManagerDialogPresenter** ← opened by ContentStoreContentPackDetailsPresenter
* **CurrentPasswordPresenter** ← opened by ChangePasswordPlugin
* **CustomRowStylePresenter** ← opened by RulePresenter
* **DataUploadPresenter** ← opened by MetaPresenter
* **DateTimePopup** ← opened by BatchExecutionScheduleEditPresenter, CredentialSettingsPresenter, DateTimeBox, ScheduledProcessEditPresenter
* **DependenciesInfoPresenter** ← opened by DependenciesPresenter, ImportExportConfigGinjector
* **DocumentUserCreatePermissionsEditPresenter** ← opened by DocumentUserPermissionsEditPresenter
* **DocumentUserPermissionsEditPresenter** ← opened by DocumentUserPermissionsPresenter, UserPermissionReportPresenter
* **DurationPresenter** ← opened by AnnotationEditPresenter
* **EditAccountPresenter** ← opened by AccountsListPresenter
* **EditApiKeyPresenter** ← opened by ApiKeysPresenter
* **EmailResetPasswordPresenter** ← opened by LoginPresenter
* **ExecutionScheduleRunNowPresenter** ← opened by ExecutionScheduleManager
* **ExplorerNodeEditTagsPresenter** ← opened by DocumentPluginEventManager
* **ExplorerNodeRemoveTagsPresenter** ← opened by DocumentPluginEventManager
* **ExplorerPopupPresenter** ← opened by DocSelectionBoxPresenter
* **ExportConfigPresenter** ← opened by DocumentPluginEventManager, ExportConfigPlugin
* **FeedDependencyPresenter** ← opened by ProcessorEditPresenter
* **FieldEditPresenter** ← opened by FieldListPresenter
* **FindAnnotationPresenter** ← opened by AnnotationLinkPresenter, AnnotationManager
* **FindInContentPresenter** ← opened by AppGinjectorUser
* **FindPresenter** ← opened by DocumentPluginEventManager, NavigationPlugin
* **FormatPresenter** ← opened by ColumnsManager, QueryResultTablePresenter, QueryTableColumnsManager, TablePresenter
* **FsVolumeEditPresenter** ← opened by FsVolumeGinjector, FsVolumeGroupEditPresenter
* **FsVolumeGroupEditPresenter** ← opened by FsVolumeGroupPresenter
* **GitRepoCommitDialogPresenter** ← opened by GitRepoSettingsPresenter
* **GitRepoSettingsPresenter** ← opened by GitRepoPresenter
* **HttpClientConfigPresenter** ← opened by GitRepoSettingsPresenter, OpenAIModelSettingsPresenter
* **HttpTlsConfigPresenter** ← opened by HttpClientConfigPresenter
* **ImportConfigConfirmPresenter** ← opened by ImportConfigPresenter, ImportExportConfigGinjector
* **ImportConfigPresenter** ← opened by DocumentPluginEventManager, ImportConfigPlugin
* **IndexFieldEditPresenter** ← opened by IndexFieldListPresenter, IndexGinjector
* **IndexVolumeEditPresenter** ← opened by IndexVolumeGroupEditPresenter
* **IndexVolumeGroupEditPresenter** ← opened by IndexVolumeGroupPresenter
* **InfoDocumentPresenter** ← opened by DocumentPluginEventManager
* **ItemSelectionPresenter** ← opened by ItemNavigatorPresenter
* **ManageActivityPresenter** ← opened by CurrentActivity
* **ManageGlobalPropertyEditPresenter** ← opened by GlobalPropertyTabPresenter
* **MoveDocumentPresenter** ← opened by DocumentPluginEventManager
* **NameDocumentPresenter** ← opened by DocumentPluginEventManager
* **NamePresenter** ← opened by QueryFavouritesPresenter
* **NewElementPresenter** ← opened by PipelineStructurePresenter
* **NewFsVolumeGroupPresenter** ← opened by FsVolumeGroupPresenter
* **NewIndexVolumeGroupPresenter** ← opened by IndexVolumeGroupPresenter
* **NewNodeGroupPresenter** ← opened by NodeGroupPresenter
* **NodeGroupEditPresenter** ← opened by NodeGroupPresenter
* **PathwayEditPresenter** ← opened by PathwayListPresenter
* **ProcessChoicePresenter** ← opened by AbstractMetaListPresenter, MetaListPresenter, MetaRelationListPresenter, SteppingMetaListPresenter
* **ProcessorEditPresenter** ← opened by ProcessorPresenter
* **ProcessorProfileEditPresenter** ← opened by ProcessorProfilePresenter
* **ProfilePeriodEditPresenter** ← opened by ProfilePeriodListPresenter
* **QueryFavouritesPresenter** ← opened by QueryPresenter
* **QueryHistoryPresenter** ← opened by QueryPresenter
* **QueryInfoPresenter** ← opened by QueryInfo
* **RecentItemsPresenter** ← opened by DocumentPluginEventManager, NavigationPlugin
* **RenameColumnPresenter** ← opened by ColumnsManager, TablePresenter
* **RenameTabPresenter** ← opened by DashboardPresenter, TabManager
* **ResultStorePresenter** ← opened by ResultStorePlugin
* **ResultStoreSettingsPresenter** ← opened by ResultStoreListPresenter
* **SchedulePopup** ← opened by BatchExecutionScheduleEditPresenter, JobNodeListHelper, JobNodeListPresenter, NodeJobListPresenter, ScheduleBox, ScheduledProcessEditPresenter
* **ScheduledProcessEditPresenter** ← opened by ExecutionScheduleManager, ScheduledProcessingPresenter
* **SelectionSummaryPresenter** ← opened by AbstractMetaListPresenter, MetaListPresenter, MetaRelationListPresenter, SteppingMetaListPresenter
* **SolrIndexFieldEditPresenter** ← opened by SolrIndexFieldListPresenter, SolrIndexGinjector
* **SplashPresenter** ← opened by CurrentUser
* **StatisticsFieldEditPresenter** ← opened by StatisticsFieldListPresenter, StatisticsGinjector
* **SteppingFilterPresenter** ← opened by PipelineGinjector, SteppingPresenter
* **TableFilterPresenter** ← opened by ColumnsManager, TableFilterPlugin, TablePresenter
* **TextBoxPopup** ← opened by TabSessionManager
* **TimePopup** ← opened by ProfilePeriodEditPresenter, TimeBox
* **TooltipPresenter** ← opened by ImportConfigConfirmPresenter, IndexShardPresenter, NodeStatusListPresenter, ProcessorListPresenter, ProcessorTaskListPresenter, ProcessorTaskSummaryPresenter, TaskManagerListPresenter
* **TypeFilterPresenter** ← opened by ExportConfigPresenter, NavigationPresenter
* **UserPreferencesPresenter** ← opened by UserPreferencesPlugin
* **UserRefPopupPresenter** ← opened by AnnotationEditPresenter, ChangeAssignedToPresenter, UserAndGroupsPresenter, UserPermissionsReportPlugin, UserRefSelectionBoxPresenter
* **UserTaskManagerPresenter** ← opened by TaskGinjector
* **VisualisationAssetsAddItemDialogPresenter** ← opened by VisualisationAssetsPresenter
* **VisualisationAssetsEditAssetDialogPresenter** ← opened by VisualisationAssetsPresenter
* **VisualisationAssetsPresenter** ← opened by VisualisationPresenter
* **VisualisationAssetsUploadFileDialogPresenter** ← opened by VisualisationAssetsPresenter
* **XPathFilterPresenter** ← opened by SteppingFilterPresenter

### No opener found (2) — manual research needed

`ContentStoreContentPackDetailsPresenter` · `DenseVectorFieldPresenter`

## The crawler target list — all 418, with a route

This is the denominator. A presenter the crawl never reaches is either a crawler gap, a
config-gated feature, or an UNKNOWN below that still needs manual research.

| route | presenters |
| --- | ---: |
| embedded panel (covered via parent) | 196 |
| dialog | 113 |
| screen/dialog via plugin | 81 |
| abstract base (not a door) | 16 |
| place-based (session/navigation state) | 5 |
| config-gated (a door only when enabled) | 3 |
| not a door (by construction) | 2 |
| UNKNOWN | 2 |

**418 is not 418 doors.** It decomposes:

* **194 DOORS** — the crawler's actual target list, each with a route above.
* **196 embedded panels** — settings tabs, list panes, toolbars. Covered when
  their parent screen is, and their behaviour is the screen-profile layer's business.
* **5 place-based** — revealed by session or navigation state, not by a click.
  Reached by manipulating the session (sign out, fail auth), not by crawling.
* **16 abstract base classes** — never instantiated, not reachable by anything.
* **ActivityEditPresenter** — config-gated (a door only when enabled): stroom.ui.activity.enabled (opened from ManageActivityPresenter)
* **ManageActivityPresenter** — config-gated (a door only when enabled): stroom.ui.activity.enabled (NavigationPresenter: `activityConfig.isEnabled()` adds the activity button)
* **SplashPresenter** — config-gated (a door only when enabled): stroom.ui.splash.enabled (SplashPresenter.show: `splashConfig.isEnabled()`)
* **ContentTabPanePresenter** — not a door (by construction): the content tab pane itself — application shell, revealed by the AppModule proxy, not opened
* **UnknownComponentPresenter** — not a door (by construction): fallback for a dashboard component whose type is not registered (Components.java: `componentRegistry.getComponent(type) == null`) — needs corrupt content
* **2 genuinely unrouted** — the research backlog below.

So the crawler's denominator is **196**, of which **194 (99%) have a known route today.**

### UNKNOWN route (2) — the research backlog

Grouped by area, because a whole area with no route usually means one missing mechanism
rather than N missing screens.

* **Explorer** (1) — TabSessionChooserPresenter
* **Gwt** (1) — ContentPresenter

## Presenters by area

| area | presenters |
| --- | ---: |
| Dashboard | 62 |
| Security | 40 |
| Analytics | 33 |
| Data / streams | 29 |
| Pipeline | 20 |
| Annotation | 18 |
| Explorer | 16 |
| Data receipt | 14 |
| Processor | 13 |
| Query / search | 13 |
| Index | 12 |
| Pathways | 12 |
| State (Plan B) | 11 |
| Credentials | 10 |
| Entity | 10 |
| Search | 9 |
| AI | 8 |
| Node / cluster | 8 |
| Import / Export | 6 |
| Visualisation | 6 |
| Config / properties | 5 |
| Dictionary | 5 |
| Statistics | 5 |
| Activity | 4 |
| Preferences | 4 |
| Script | 4 |
| Tasks | 4 |
| Cache | 3 |
| Content store | 3 |
| Data generation | 3 |
| Git repository | 3 |
| Jobs / scheduler | 3 |
| Content | 2 |
| Feed | 2 |
| Folder | 2 |
| HTTP | 2 |
| AI (OpenAI) | 2 |
| View | 2 |
| XML schema | 2 |
| About | 1 |
| AWS / S3 | 1 |
| Documentation | 1 |
| Kafka | 1 |
| Main | 1 |
| Monitoring | 1 |
| Welcome | 1 |
| Gwt | 1 |
