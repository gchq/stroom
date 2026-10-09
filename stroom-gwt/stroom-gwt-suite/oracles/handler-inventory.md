# Handler inventory — the behaviour denominator

**Generated** by `stroom-gwt-suite/tools/build-handler-inventory.mjs`. Do not hand-edit. BEHAVIOUR-PLAN.md § B1.

A behaviour is a handler registration in the GWT client, attributed to the class that DECLARES it. A
handler is *exercised* when the walk fired it: a click handler when an affordance with its label was
clicked on a node the coverage ledger attributes to the declaring class or a subclass; OK / Cancel when
a dialog under that class had them pressed (an OK the guard aborted is `ok-blocked`); a selection handler
when a post-action selection state was reached under it. The rest are classified by what would fire them.

## Totals

| | declared | exercised | |
| --- | ---: | ---: | ---: |
| **all classes** | 1546 | 744 | 48.1% |
| **presenters** | 805 | 471 | 58.5% |

_66 of the exercised are joined by LABEL alone (a label one or two classes use, clicked somewhere the ledger did not attribute to the class); the rest by the ledger's attribution of the node to the class, a subclass, or a presenter that embeds it._

| kind | declared | exercised | |
| --- | ---: | ---: | ---: |
| click | 473 | 252 | 53.3% |
| value | 314 | 70 | 22.3% |
| command | 212 | 137 | 64.6% |
| selection | 158 | 73 | 46.2% |
| ok | 98 | 60 | 61.2% |
| cancel | 98 | 79 | 80.6% |
| key | 82 | 20 | 24.4% |
| hide | 33 | 19 | 57.6% |
| dirty | 15 | 5 | 33.3% |
| mousedown | 14 | 4 | 28.6% |
| context | 11 | 8 | 72.7% |
| dom | 10 | 4 | 40.0% |
| close | 8 | 3 | 37.5% |
| dblclick | 6 | 0 | 0.0% |
| mouseover | 4 | 2 | 50.0% |
| mouseout | 4 | 2 | 50.0% |
| mouseup | 4 | 4 | 100.0% |
| mousemove | 2 | 2 | 100.0% |

## The unexercised, by what would fire them

| why | handlers | meaning |
| --- | ---: | --- |
| `unreached` | 250 | labelled, in a presenter, and never clicked on a node attributed to it |
| `needs-value` | 244 | a value must be typed or chosen (B3) |
| `widget` | 89 | declared in a widget class — inherited by many presenters, counted once here |
| `view` | 66 | declared in a ViewImpl (a @UiHandler) — joined by ui.xml label, none matched |
| `needs-key` | 62 | a keyboard event (B3) |
| `unlabelled` | 49 | a click handler on a receiver whose label the miner could not resolve (miner gap) |
| `ok-blocked` | 16 | the OK writes, and the guard aborted it (B4) |
| `needs-dirty` | 10 | an editor made dirty (B3) |
| `needs-dom` | 6 | a DOM event the walk has no step for |
| `needs-close` | 5 | a tab or panel closed |
| `absent` | 5 | the class is not in the running application — unreachable, not outstanding |

## Per presenter

_Presenters with any unexercised handler, most gaps first; the `why` column is the split._

| presenter | declared | exercised | unexercised by why |
| --- | ---: | ---: | --- |
| `ContentTemplateTabPresenter` | 20 | 6 | unreached 14 |
| `DataRetentionPolicyPresenter` | 19 | 11 | unreached 8 |
| `AnnotationEditPresenter` | 10 | 3 | unreached 7 |
| `AbstractNotificationListPresenter` | 9 | 3 | unreached 6 |
| `TablePresenter` | 13 | 7 | unlabelled 2, unreached 3, needs-close 1 |
| `SteppingPresenter` | 11 | 5 | unreached 5, needs-value 1 |
| `NewPropertyPresenter` | 6 | 0 | unreached 1, needs-value 5 |
| `DataRetentionImpactPresenter` | 8 | 2 | unreached 6 |
| `AskStroomAiPresenter` | 5 | 0 | unreached 5 |
| `QueryPresenter` | 19 | 14 | unreached 5 |
| `ProcessorPresenter` | 10 | 5 | unreached 4, unlabelled 1 |
| `UserAndGroupsPresenter` | 10 | 5 | unreached 5 |
| `TaskManagerListPresenter` | 10 | 5 | unreached 5 |
| `VisualisationAssetsPresenter` | 14 | 9 | unreached 5 |
| `ManageActivityPresenter` | 4 | 0 | unreached 4 |
| `AnalyticEmailDestinationPresenter` | 4 | 0 | unreached 3, needs-value 1 |
| `ScheduledProcessEditPresenter` | 5 | 1 | unreached 3, needs-value 1 |
| `ScheduledProcessListPresenter` | 5 | 1 | unreached 4 |
| `SelectionHandlersPresenter` | 10 | 6 | unreached 4 |
| `RulesPresenter` | 13 | 9 | unreached 3, needs-dirty 1 |
| `FeedSettingsPresenter` | 11 | 7 | needs-value 4 |
| `ConstraintListPresenter` | 4 | 0 | unreached 4 |
| `QueryEditPresenter` | 4 | 0 | unreached 1, needs-value 3 |
| `RuleSetPresenter` | 7 | 3 | needs-dirty 4 |
| `UserPermissionReportPresenter` | 6 | 2 | unreached 3, needs-value 1 |
| `BatchExecutionScheduleEditPresenter` | 3 | 0 | unreached 2, needs-value 1 |
| `ScheduledProcessHistoryListPresenter` | 3 | 0 | unreached 3 |
| `ManageGlobalPropertyEditPresenter` | 5 | 2 | unreached 2, ok-blocked 1 |
| `ContentStoreContentPackDetailsPresenter` | 4 | 1 | unreached 3 |
| `CredentialsListPresenter` | 5 | 2 | unreached 3 |
| `EmbeddedQueryPresenter` | 3 | 0 | unreached 2, needs-value 1 |
| `DashboardPresenter` | 9 | 6 | needs-value 2, unreached 1 |
| `QueryFavouritesPresenter` | 6 | 3 | unreached 3 |
| `DictionaryListPresenter` | 4 | 1 | unreached 3 |
| `MainPresenter` | 3 | 0 | unlabelled 1, needs-key 2 |
| `PathwayListPresenter` | 4 | 1 | unreached 3 |
| `StepLocationLinkPresenter` | 3 | 0 | unlabelled 1, unreached 2 |
| `NewPipelineReferencePresenter` | 3 | 0 | unreached 2, needs-value 1 |
| `PipelineStructurePresenter` | 9 | 6 | unreached 3 |
| `UserPreferencesPresenter` | 6 | 3 | needs-value 3 |
| `QueryResultTablePresenter` | 11 | 8 | unlabelled 2, unreached 1 |
| `ApiKeysPresenter` | 4 | 1 | unreached 3 |
| `ViewSettingsPresenter` | 3 | 0 | unreached 2, needs-value 1 |
| `AbstractEditorPresenter` | 3 | 0 | needs-value 1, needs-key 2 |
| `ActivityEditPresenter` | 2 | 0 | unreached 2 |
| `SplashPresenter` | 2 | 0 | unreached 2 |
| `AskStroomAiConfigPresenter` | 3 | 1 | unreached 1, ok-blocked 1 |
| `AnalyticNotificationEditPresenter` | 2 | 0 | needs-value 2 |
| `AnnotationLinkPresenter` | 3 | 1 | unreached 2 |
| `ChangeStatusPresenter` | 3 | 1 | unreached 1, ok-blocked 1 |
| `LinkedEventPresenter` | 3 | 1 | unreached 2 |
| `CredentialEditPresenter` | 3 | 1 | unreached 1, ok-blocked 1 |
| `QueryInfoPresenter` | 2 | 0 | unreached 2 |
| `IncludeExcludeFilterDictionaryPresenter` | 3 | 1 | unreached 2 |
| `EditExpressionPresenter` | 22 | 20 | unreached 2 |
| `VisPresenter` | 2 | 0 | unreached 2 |
| `DataPresenter` | 3 | 1 | unlabelled 1, unreached 1 |
| `MetaPresenter` | 17 | 15 | unreached 2 |
| `NavigationPresenter` | 12 | 10 | unreached 1, unlabelled 1 |
| `GitRepoSettingsPresenter` | 2 | 0 | unreached 2 |
| `ExportConfigPresenter` | 4 | 2 | unreached 1, ok-blocked 1 |
| `ImportConfigConfirmPresenter` | 2 | 0 | unreached 2 |
| `IndexShardPresenter` | 2 | 0 | unreached 2 |
| `PathwayEditPresenter` | 2 | 0 | unreached 2 |
| `PathwayTreePresenter` | 2 | 0 | unlabelled 1, unreached 1 |
| `TracesListTabPresenter` | 2 | 0 | unreached 1, needs-value 1 |
| `ElementPresenter` | 2 | 0 | needs-value 1, needs-key 1 |
| `SteppingFilterPresenter` | 7 | 5 | unreached 2 |
| `PipelineReferenceListPresenter` | 5 | 3 | unreached 2 |
| `EditFeedDependencyPresenter` | 2 | 0 | unreached 1, needs-value 1 |
| `FeedDependencyListPresenter` | 5 | 3 | unreached 2 |
| `ProcessorProfilePresenter` | 4 | 2 | unreached 2 |
| `ProfilePeriodListPresenter` | 4 | 2 | unreached 2 |
| `ExpressionTreePresenter` | 3 | 1 | unreached 2 |
| `QueryResultTableSplitPresenter` | 2 | 0 | unreached 1, needs-value 1 |
| `ResultStoreListPresenter` | 4 | 2 | unreached 2 |
| `RuleSetSettingsPresenter` | 10 | 8 | unreached 2 |
| `AppPermissionsPresenter` | 4 | 2 | needs-value 2 |
| `XMLSchemaPresenter` | 2 | 0 | unreached 1, needs-value 1 |
| `MenuPresenter` | 2 | 0 | unreached 2 |
| `AbstractProcessingPresenter` | 1 | 0 | needs-value 1 |
| `AbstractQueryEditPresenter` | 1 | 0 | needs-value 1 |
| `AnalyticStreamDestinationPresenter` | 1 | 0 | unreached 1 |
| `DuplicateManagementListPresenter` | 2 | 1 | unreached 1 |
| `ScheduledProcessingPresenter` | 1 | 0 | unreached 1 |
| `AddEventLinkPresenter` | 3 | 2 | needs-key 1 |
| `AnnotationPresenter` | 2 | 1 | needs-dirty 1 |
| `ChangeAssignedToPresenter` | 2 | 1 | ok-blocked 1 |
| `ChooserPresenter` | 1 | 0 | unreached 1 |
| `FindAnnotationListPresenter` | 1 | 0 | unreached 1 |
| `MultiChooserPresenter` | 1 | 0 | unreached 1 |
| `ContentTabPanePresenter` | 1 | 0 | needs-value 1 |
| `FullScreenPresenter` | 1 | 0 | needs-dirty 1 |
| `BasicTableFilterSettingsPresenter` | 1 | 0 | needs-value 1 |
| `MultiRulesPresenter` | 1 | 0 | needs-dirty 1 |
| `LayoutConstraintPresenter` | 1 | 0 | needs-value 1 |
| `SettingsPresenter` | 1 | 0 | unreached 1 |
| `CurrentSelectionPresenter` | 1 | 0 | unreached 1 |
| `QueryHistoryPresenter` | 3 | 2 | unreached 1 |
| `TableFilterPresenter` | 3 | 2 | unreached 1 |
| `BasicVisSettingsPresenter` | 1 | 0 | unreached 1 |
| `ProcessorTaskListPresenter` | 1 | 0 | unlabelled 1 |
| `SelectionSummaryPresenter` | 2 | 1 | ok-blocked 1 |
| `TextPresenter` | 1 | 0 | needs-value 1 |
| `FsVolumeEditPresenter` | 2 | 1 | ok-blocked 1 |
| `DataGenSettingsPresenter` | 2 | 1 | needs-value 1 |
| `DictionarySettingsPresenter` | 1 | 0 | needs-value 1 |
| `CopyDocumentPresenter` | 2 | 1 | ok-blocked 1 |
| `CreateDocumentPresenter` | 2 | 1 | ok-blocked 1 |
| `DocTabPresenter` | 4 | 3 | unreached 1 |
| `InfoDocumentPresenter` | 1 | 0 | unreached 1 |
| `MarkdownEditPresenter` | 3 | 2 | needs-value 1 |
| `NameDocumentPresenter` | 2 | 1 | ok-blocked 1 |
| `FindInContentPresenter` | 1 | 0 | unreached 1 |
| `FindPresenter` | 1 | 0 | unreached 1 |
| `RecentItemsPresenter` | 1 | 0 | unreached 1 |
| `TabSessionChooserPresenter` | 1 | 0 | unreached 1 |
| `IndexVolumeEditPresenter` | 2 | 1 | ok-blocked 1 |
| `NodeJobListPresenter` | 1 | 0 | unreached 1 |
| `NodeStatusListPresenter` | 1 | 0 | unreached 1 |
| `ConstraintEditPresenter` | 1 | 0 | unreached 1 |
| `TracesListPresenter` | 1 | 0 | unreached 1 |
| `TracesSettingsPresenter` | 1 | 0 | needs-value 1 |
| `PipelinePresenter` | 2 | 1 | needs-value 1 |
| `PipelineTreePresenter` | 2 | 1 | unreached 1 |
| `PropertyListPresenter` | 3 | 2 | unreached 1 |
| `PlanBSettingsPresenter` | 1 | 0 | needs-value 1 |
| `BatchProcessorFilterEditPresenter` | 2 | 1 | ok-blocked 1 |
| `ProcessorListPresenter` | 1 | 0 | unlabelled 1 |
| `QueryDocEditPresenter` | 3 | 2 | needs-value 1 |
| `QueryHelpPresenter` | 1 | 0 | unreached 1 |
| `ContentTemplateEditPresenter` | 1 | 0 | needs-value 1 |
| `ScriptSettingsPresenter` | 1 | 0 | needs-value 1 |
| `SolrIndexSettingsPresenter` | 2 | 1 | needs-value 1 |
| `AppPermissionsEditPresenter` | 2 | 1 | unreached 1 |
| `BatchDocumentPermissionsPresenter` | 5 | 4 | unreached 1 |
| `DocumentCreatePermissionsListPresenter` | 1 | 0 | unreached 1 |
| `DocumentUserCreatePermissionsEditPresenter` | 2 | 1 | ok-blocked 1 |
| `DocumentUserPermissionsEditPresenter` | 2 | 1 | ok-blocked 1 |
| `SigningKeyPresenter` | 3 | 2 | unreached 1 |
| `UserInfoPresenter` | 1 | 0 | needs-value 1 |
| `UserTabPresenter` | 1 | 0 | unreached 1 |
| `UsersPresenter` | 3 | 2 | unreached 1 |
| `AccountsListPresenter` | 4 | 3 | unreached 1 |
| `EditAccountPresenter` | 3 | 2 | ok-blocked 1 |
| `UserTaskManagerPresenter` | 1 | 0 | unreached 1 |
| `XMLSchemaSettingsPresenter` | 4 | 3 | needs-value 1 |
| `EditorMenuPresenter` | 1 | 0 | unreached 1 |
| `MenuItemPresenter` | 1 | 0 | unlabelled 1 |
| `CurveTabLayoutPresenter` | 1 | 0 | unreached 1 |
| `LinkTabsPresenter` | 1 | 0 | unreached 1 |
| `StatisticsCustomMaskListPresenter` | 4 | 3 | unreached 1 |

## Messages the client can show — the validation matrix (B3)

_Every `AlertEvent.fireWarn / fireError / fireInfo`, `ConfirmEvent.fire`, `ErrorEvent.fire` and `throw new ValidationException` with a literal message, as a template (`…` = a variable part), joined by prefix to the alert and confirm bodies the walk read. An unobserved message is a validation the walk has never provoked — the row of the matrix still to drive. Templates with under eight literal characters do not join._

**69 of 276 observed (25.0%).**

| kind | declared | observed |
| --- | ---: | ---: |
| error | 122 | 35 |
| confirm | 59 | 13 |
| warn | 51 | 20 |
| info | 37 | 0 |
| error-event | 4 | 0 |
| validation-exception | 3 | 1 |

### Never observed

- `AbstractMetaListPresenter` (error, line 580) — You have not selected any items
- `AbstractMetaListPresenter` (info, line 745) — Created processor filter
- `AbstractMetaListPresenter` (info, line 797) — Result Details
- `AbstractMetaListPresenter` (warn, line 804) — Result Details
- `AbstractMetaListPresenter` (error, line 812) — Result Details
- `AbstractMetaListPresenter` (confirm, line 856) — You have clicked the select all checkbox. If you continue Stroom will … all items that match the filter, not just those visible on screen. Are you sure you want to continue?
- `AbstractMetaListPresenter` (error, line 872) — You have not selected any items
- `AccountsListPresenter` (error, line 345) — Error fetching accounts: …
- `ActivityEditPresenter` (warn, line 213) — Validation Error
- `AddEventLinkPresenter` (error, line 56) — Invalid event id '…'
- `AddEventLinkPresenter` (error, line 67) — Invalid event id '…'
- `AnnotationEditPresenter` (error, line 602) — Unable to copy
- `AnnotationEditPresenter` (confirm, line 708) — Are you sure you want to delete this entry?
- `AnnotationLinkPresenter` (confirm, line 74) — Are you sure you want to remove this reference?
- `ApiKeysListPresenter` (error, line 443) — Error fetching API Keys: …
- `AskStroomAiConfigPresenter` (info, line 135) — Default config updated
- `AskStroomAiPresenter` (confirm, line 913) — Are you sure you want to delete this message?
- `AskStroomAiPresenter` (confirm, line 930) — Are you sure you want to delete all messages and attachments in this conversation?
- `BasicEmbeddedQuerySettingsPresenter` (error, line 177) — Error loading '…'
- `BasicEmbeddedQuerySettingsPresenter` (info, line 182) — Copy of '…' complete
- `BatchDocumentPermissionsEditPresenter` (error-event, line 176) — No documents are included in the current filter for this permission change.
- `BatchDocumentPermissionsEditPresenter` (info, line 208) — Successfully changed permissions.
- `BatchDocumentPermissionsEditPresenter` (error, line 216) — Failed to change permissions.
- `BatchProcessorFilterEditPresenter` (error-event, line 104) — No change selected.
- `BatchProcessorFilterEditPresenter` (error-event, line 113) — No user selected.
- `BatchProcessorFilterEditPresenter` (error-event, line 130) — No processors are included in the current filter.
- `BatchProcessorFilterEditPresenter` (info, line 163) — Successfully changed.
- `BatchProcessorFilterEditPresenter` (error, line 171) — Failed to change.
- `ConstraintEditPresenter` (validation-exception, line 345) — A constraint must have a name
- `ContentStoreContentPackDetailsPresenter` (warn, line 310) — No credentials were selected; this content pack cannot be downloaded
- `ContentStoreContentPackDetailsPresenter` (error, line 323) — This content pack requires credentials, but you don't have permission to access credentials
- `ContentStoreContentPackDetailsPresenter` (info, line 348) — Creation success
- `ContentStoreContentPackDetailsPresenter` (error, line 356) — Create failed
- `ContentStoreContentPackDetailsPresenter` (error, line 362) — Create failed
- `ContentStoreContentPackDetailsPresenter` (info, line 389) — Content pack upgrade success
- `ContentStoreContentPackListPresenter` (error, line 286) — Check for updated content failed
- `ContentTemplateTabPresenter` (confirm, line 339) — Are you sure you want to delete template …?
- `ContentTemplateTabPresenter` (confirm, line 669) — There are unsaved changes. Are you sure you want to close this tab?
- `CreateDocumentPresenter` (warn, line 114) — No parent folder has been selected
- `CreateExternalUserPresenter` (confirm, line 71) — A deleted user already exists with the same name, would you like to restore the existing user?
- `CreateMultipleUsersPresenter` (confirm, line 55) — Some deleted users already exist with the same names, would you like to restore them?
- `CreateNewGroupPresenter` (confirm, line 74) — A deleted group already exists with the same name, would you like to restore the existing group?
- `CredentialsListPresenter` (confirm, line 312) — Are you sure you want to delete the credentials '…' ?
- `CurrentPasswordPresenter` (confirm, line 129) — Your password has been changed. Do you also want to sign out of all your other sessions (on other browsers and devices)?
- `CurrentPasswordPresenter` (info, line 150) — Signed out of your other sessions.
- `DataRetentionPresenter` (confirm, line 199) — There are unsaved changes. Are you sure you want to close this tab?
- `DataUploadPresenter` (info, line 72) — Uploaded file
- `DataUploadPresenter` (warn, line 87) — Feed not set!
- `DataUploadPresenter` (warn, line 91) — Stream Type not set!
- `DocumentUserCreatePermissionsEditPresenter` (info, line 193) — Successfully changed permissions.
- `DocumentUserCreatePermissionsEditPresenter` (error, line 198) — Failed to change permissions.
- `DocumentUserPermissionsEditPresenter` (error, line 169) — There are no descendant documents in this folder.
- `DocumentUserPermissionsEditPresenter` (info, line 185) — Successfully changed permissions.
- `DocumentUserPermissionsEditPresenter` (error, line 190) — Failed to change permissions.
- `DuplicateManagementListPresenter` (confirm, line 164) — Are you sure you want to delete the selected row…s…?
- `EditAccountPresenter` (error, line 179) — Error creating account: …
- `EditApiKeyPresenter` (error, line 195) — Error updating API key: …
- `EditApiKeyPresenter` (confirm, line 207) — You will never be able to view the API Key after you close this dialog. Stroom does not store the API Key. You must copy it elsewhere first. Are you sure you want to close this dialog?
- `EditApiKeyPresenter` (confirm, line 220) — Cancelling will delete the API Key that you have just created, are you sure?
- `EditApiKeyPresenter` (error, line 233) — Error deleting API key: …
- `EditApiKeyPresenter` (error, line 251) — API Key expiry date must be less than or equal to …
- `EditApiKeyPresenter` (error, line 254) — API Key expiry date cannot be in the past …
- `EditApiKeyPresenter` (error, line 257) — API Key expiry date cannot be after …
- `EditApiKeyPresenter` (error, line 262) — An owner must be provided for the API key.
- `EditApiKeyPresenter` (error, line 293) — Error creating API key: …
- `ElasticClusterSettingsPresenter` (info, line 60) — Connection Success
- `ElasticClusterSettingsPresenter` (error, line 62) — Connection Failure
- `ElasticIndexSettingsPresenter` (info, line 97) — Connection Success
- `ElasticIndexSettingsPresenter` (error, line 99) — Connection Failure
- `EmailResetPasswordPresenter` (info, line 82) — Password Reset
- `ExplorerNodeEditTagsPresenter` (error, line 71) — No explorer nodes supplied
- `ExplorerNodeRemoveTagsPresenter` (error, line 69) — No explorer nodes supplied
- `ExplorerPopupPresenter` (error, line 115) — You must choose a valid item.
- `FeedDependencyListPresenter` (error, line 189) — You must specify a stream type to use.
- `FieldEditPresenter` (warn, line 47) — A field with this name already exists. Field names are case insensitive.
- `FsVolumeGroupEditPresenter` (error, line 230) — Group name '…' is already in use by another group.
- `GitRepoSettingsPresenter` (warn, line 183) — Git repository information not available
- `GitRepoSettingsPresenter` (info, line 202) — Push Success
- `GitRepoSettingsPresenter` (error, line 207) — Push Failure
- `GitRepoSettingsPresenter` (info, line 227) — Pull Success
- `GitRepoSettingsPresenter` (error, line 232) — Pull Failure
- `GitRepoSettingsPresenter` (warn, line 242) — Git repository information not available
- `GitRepoSettingsPresenter` (info, line 257) — Update Check Success
- `GitRepoSettingsPresenter` (warn, line 278) — Git repository information is not available
- `ImportConfigConfirmPresenter` (warn, line 220) — No items are selected for import
- `ImportConfigConfirmPresenter` (confirm, line 228) — There are warnings in the items selected. Are you sure you want to import?.
- `ImportConfigConfirmPresenter` (warn, line 496) — Import Aborted
- `ImportConfigConfirmPresenter` (info, line 516) — Import Complete
- `IndexShardPresenter` (confirm, line 470) — Are you sure you want to flush all index shards?
- `IndexShardPresenter` (confirm, line 472) — You have selected to flush all filtered index shards! Are you absolutely sure you want to do this?
- `IndexShardPresenter` (confirm, line 483) — Are you sure you want to flush the selected index shards?
- `IndexShardPresenter` (warn, line 493) — No index shards have been selected for flushing!
- `IndexShardPresenter` (confirm, line 502) — Are you sure you want to delete all index shards?
- `IndexShardPresenter` (confirm, line 505) — You have selected to delete all filtered index shards! Are you absolutely sure you want to do this?
- `IndexShardPresenter` (confirm, line 516) — Are you sure you want to delete the selected index shards?
- `IndexShardPresenter` (warn, line 524) — No index shards have been selected for deletion!
- `IndexShardPresenter` (info, line 543) — Selected index shards will be flushed. Please be patient as this may take some time.
- `IndexShardPresenter` (info, line 560) — Selected index shards will be deleted. Please be patient as this may take some time.
- `IndexVolumeGroupEditPresenter` (error, line 242) — Group name '…' is already in use by another group.
- `JobListPresenter` (error, line 204) — Help is not configured!
- `ManageGlobalPropertyEditPresenter` (error, line 533) — Help is not configured!
- `MarkdownEditPresenter` (error, line 195) — Help is not configured!
- `MetaPresenter` (confirm, line 204) — You are setting advanced filters! It is recommended you constrain your filter (e.g. by 'Created') to avoid an expensive query. Are you sure you want to apply this advanced filter?
- `NameDocumentPresenter` (warn, line 89) — You must provide a new name for …
- `NewFsVolumeGroupPresenter` (error, line 82) — Group name '…' is already in use by another group.
- `NewIndexVolumeGroupPresenter` (error, line 81) — Group name '…' is already in use by another group.
- `NewNodeGroupPresenter` (error, line 74) — Group name '…' is already in use by another group.
- `OpenAIModelSettingsPresenter` (info, line 71) — Model Validation Successful
- `PathwayEditPresenter` (validation-exception, line 267) — A pathway must have a name
- `PipelineReferenceListPresenter` (error, line 333) — You must specify a stream type to use.
- `PipelineStructurePresenter` (warn, line 126) — A pipeline cannot inherit from itself
- `PipelineStructurePresenter` (error, line 524) — You must save changes to this pipeline before you can view the source
- `PipelineStructurePresenter` (confirm, line 571) — Are you sure you want to save changes to the underlying JSON?
- `ProcessorEditPresenter` (confirm, line 216) — You are about to update an existing filter. Any streams that might now be included by this filter but are older than the current tracker position will not be processed. Are you sure you wish to do this?
- `ProcessorEditPresenter` (confirm, line 361) — You are about to process all stream types. Are you sure you wish to do this?
- `ProcessorPresenter` (error, line 390) — Unable to load filter
- `ProcessorProfileEditPresenter` (error, line 141) — Processor profile name '…' is already in use by another processor profile.
- `PropertyListPresenter` (error, line 449) — Unable to create embedded document
- `QueryFavouritesPresenter` (confirm, line 121) — Are you sure you want to delete this favourite?
- `QueryInfoPresenter` (warn, line 88) — The text entered is not valid
- `QueryPresenter` (warn, line 460) — No data source has been chosen to search
- `QueryPresenter` (info, line 543) — Created batch processor
- `QueryResultTablePresenter` (confirm, line 302) — Search still in progress. Do you want to download the current results? Note that these may be incomplete.
- `RenameColumnPresenter` (error, line 88) — Field name "…" is already in use
- `ResetPasswordPresenter` (info, line 103) — Your password has been reset. Please sign in with your new password.
- `ResultStoreListPresenter` (confirm, line 142) — Are you sure you want to terminate this search?
- `ResultStoreListPresenter` (info, line 146) — Terminated
- `ResultStoreListPresenter` (warn, line 148) — Failed to terminate
- `ResultStoreListPresenter` (confirm, line 160) — Are you sure you want to delete this result store?
- `ResultStoreListPresenter` (info, line 168) — Destroyed store
- `ResultStoreListPresenter` (warn, line 170) — Failed to destroy store
- `RuleSetPresenter` (error, line 128) — Unable to load Receive Data Rules
- `RuleSetPresenter` (confirm, line 338) — There are unsaved changes. Are you sure you want to close this tab?
- `RuleSetSettingsPresenter` (error, line 130) — You need to create one or more fields before you can add a rule.
- `RuleSetSettingsPresenter` (confirm, line 354) — …This rule contains conditions that are not compatible with obfuscated fields. Values in the effected terms will not be obfuscated on Stroom-Proxy. Do you wish to continue?
- `ScheduledProcessingPresenter` (warn, line 121) — Please ensure all settings are correct and save before adding executions
- `SolrIndexFieldEditPresenter` (warn, line 69) — An index field name must conform to the pattern '…'
- `SolrIndexFieldEditPresenter` (warn, line 75) — An index field with this name already exists
- `SolrIndexSettingsPresenter` (info, line 81) — Connection Success
- `SolrIndexSettingsPresenter` (error, line 84) — Connection Failure
- `SourcePresenter` (error, line 453) — Unexpected type …
- `SplashPresenter` (warn, line 77) — You must accept the terms to use this system
- `SteppingPresenter` (confirm, line 302) — You are setting advanced filters! It is recommended you constrain your filter (e.g. by 'Created') to avoid an expensive query. Are you sure you want to apply this advanced filter?
- `StreamingProcessingPresenter` (warn, line 40) — Please ensure all settings are correct and save before adding executions
- `TablePresenter` (confirm, line 367) — Search still in progress. Do you want to download the current results? Note that these may be incomplete.
- `TaskManagerListPresenter` (error, line 655) — Unable to find feed '…'
- `TextPresenter` (error, line 703) — No stream id
- `UserAccessListPresenter` (error, line 140) — Error fetching user access: …
- `UserPreferencesPresenter` (confirm, line 129) — Are you sure you want to set the current preferences as the defaults for ALL users? This will not change individual users' saved preferences.
- `UserSessionsListPresenter` (error, line 120) — Error fetching sessions: …
- `VisPresenter` (error, line 499) — There was an error checking if the visualisation document has an index.html asset: …
- `VisualisationAssetsPresenter` (error, line 339) — There was an error saving the document to a new document
- `VisualisationAssetsPresenter` (error, line 345) — There was an error saving the document to a new document: …
- `VisualisationAssetsPresenter` (error, line 493) — Error reverting to live version
- `VisualisationAssetsPresenter` (error, line 500) — Error reverting to live version: …
- `VisualisationAssetsPresenter` (error, line 598) — Error saving assets
- `VisualisationAssetsPresenter` (error, line 604) — Error saving assets: …
- `VisualisationAssetsPresenter` (error, line 623) — There was an error creating a new folder
- `VisualisationAssetsPresenter` (error, line 629) — There was an error creating a new folder: …
- `VisualisationAssetsPresenter` (error, line 651) — There was an error creating a new file
- `VisualisationAssetsPresenter` (error, line 657) — There was an error creating a new file: …
- `VisualisationAssetsPresenter` (error, line 682) — There was an error uploading a new file
- `VisualisationAssetsPresenter` (error, line 688) — There was an error uploading a new file: …
- `VisualisationAssetsPresenter` (error, line 775) — There was an error updating content
- `VisualisationAssetsPresenter` (error, line 781) — There was an error updating content: …
- `VisualisationAssetsPresenter` (error, line 828) — There was an error getting content for '…': …
- `VisualisationAssetsPresenter` (error, line 865) — Error downloading assets for this visualisation: …
- `XMLSchemaPresenter` (confirm, line 251) — The XML schema appears to be invalid. Are you sure you want to save?

## Unreached click handlers in presenters

_A labelled button in a presenter the walk attributes nodes to, whose label was never clicked there. Each is a walker gap, a miner mislabel, or a button the UI never shows — and only reading it says which._

- `ManageActivityPresenter` · `New` (newButton, line 100)
- `ManageActivityPresenter` · `Edit` (openButton, line 107)
- `ManageActivityPresenter` · `Delete` (deleteButton, line 114)
- `AnalyticEmailDestinationPresenter` · `Send Test Email` (getSendTestEmailBtn(), line 64)
- `AnalyticEmailDestinationPresenter` · `Test Template` (getTestSubjectTemplateBtn(), line 65)
- `AnalyticEmailDestinationPresenter` · `Test Template` (getTestBodyTemplateBtn(), line 68)
- `ScheduledProcessHistoryListPresenter` · `Replay Execution` (replayButton, line 86)
- `ScheduledProcessListPresenter` · `Edit Execution Schedule` (editButton, line 124)
- `ScheduledProcessListPresenter` · `Remove Execution Schedule` (removeButton, line 125)
- `AnnotationLinkPresenter` · `Remove Annotation Link` (remove, line 71)
- `LinkedEventPresenter` · `Remove Event Link` (removeEventButton, line 120)
- `ManageGlobalPropertyEditPresenter` · `All nodes have the same effective value` (effectiveValueInfoButton, line 124)
- `ManageGlobalPropertyEditPresenter` · `Click to see cluster values` (effectiveValueWarningsButton, line 125)
- `ContentStoreContentPackDetailsPresenter` · `Install` (btnCreateGitRepo, line 140)
- `CredentialsListPresenter` · `Edit` (btnEdit, line 216)
- `QueryFavouritesPresenter` · `Change Favourite Name` (editButton, line 109)
- `QueryFavouritesPresenter` · `Delete Favourite` (deleteButton, line 117)
- `QueryPresenter` · `Copy` (copyButton, line 226)
- `QueryPresenter` · `Delete` (deleteItemButton, line 236)
- `QueryPresenter` · `Show Messages` (errorsButton, line 265)
- `SelectionHandlersPresenter` · `Edit` (editButton, line 121)
- `SelectionHandlersPresenter` · `Copy` (copyButton, line 127)
- `SelectionHandlersPresenter` · `Delete` (deleteButton, line 161)
- `IncludeExcludeFilterDictionaryPresenter` · `Remove Dictionary` (removeButton, line 64)
- `TablePresenter` · `Download` (downloadButton, line 364)
- `RulesPresenter` · `Edit` (editButton, line 94)
- `RulesPresenter` · `Copy` (copyButton, line 100)
- `RulesPresenter` · `Delete` (deleteButton, line 135)
- `VisPresenter` · `Refresh` (getRefreshButton(), line 196)
- `MetaPresenter` · `Restore` (streamListRestore, line 292)
- `MetaPresenter` · `Restore` (streamRelationListRestore, line 299)
- `DictionaryListPresenter` · `Remove Import` (removeButton, line 67)
- `DocTabPresenter` · `Save is not available as this document is read only` (saveButton, line 88)
- `NavigationPresenter` · `Toggle Alerts` (showAlertsBtn, line 282)
- `IndexShardPresenter` · `Flush Selected Shards` (buttonFlush, line 112)
- `IndexShardPresenter` · `Delete Selected Shards` (buttonDelete, line 119)
- `NodeJobListPresenter` · `Turn Auto Refresh On` (autoRefreshButton, line 121)
- `NodeStatusListPresenter` · `Turn Auto Refresh On` (autoRefreshButton, line 173)
- `ConstraintListPresenter` · `New Constraint` (newButton, line 86)
- `ConstraintListPresenter` · `Edit Constraint` (editButton, line 93)
- `ConstraintListPresenter` · `Remove Constraint` (removeButton, line 100)
- `PathwayListPresenter` · `Edit Pathway` (editButton, line 107)
- `PathwayListPresenter` · `Remove Pathway` (removeButton, line 114)
- `PathwayTreePresenter` · `View Matching Traces` (viewTracesButton, line 99)
- `SteppingPresenter` · `Terminate Stepping` (terminateButton, line 238)
- `SteppingPresenter` · `Toggle Log Pane` (toggleLogPaneButton, line 239)
- `PipelineReferenceListPresenter` · `Remove Reference` (removeButton, line 124)
- `FeedDependencyListPresenter` · `Remove Reference` (removeButton, line 85)
- `ProcessorPresenter` · `Edit Processor` (editButton, line 158)
- `ProcessorPresenter` · `Duplicate Processor` (duplicateButton, line 167)
- `ProcessorPresenter` · `Delete Processor` (removeButton, line 176)
- `ProcessorPresenter` · `Show Tasks` (showTasksButton, line 218)
- `ProcessorProfilePresenter` · `Edit` (openButton, line 63)
- `ProfilePeriodListPresenter` · `Remove Period` (removeButton, line 82)
- `ResultStoreListPresenter` · `Terminate Search` (terminateButton, line 139)
- `ResultStoreListPresenter` · `Delete Store` (deleteButton, line 157)
- `ContentTemplateTabPresenter` · `Move template down` (moveDownButton, line 272)
- `ContentTemplateTabPresenter` · `Move template up` (moveUpButton, line 298)
- `ContentTemplateTabPresenter` · `Delete template` (deleteButton, line 326)
- `ContentTemplateTabPresenter` · `Copy template` (copyButton, line 367)
- `ContentTemplateTabPresenter` · `Edit template` (editButton, line 418)
- `ContentTemplateTabPresenter` · `Save templates` (saveButton, line 449)
- `DataRetentionImpactPresenter` · `Run` (runButton, line 251)
- `DataRetentionImpactPresenter` · `Stop` (stopButton, line 255)
- `DataRetentionImpactPresenter` · `Filter` (filterButton, line 258)
- `DataRetentionImpactPresenter` · `Table` (flatNestedToggleButton, line 261)
- `DataRetentionImpactPresenter` · `Expand All` (expandAllButton, line 266)
- `DataRetentionImpactPresenter` · `Collapse All` (collapseAllButton, line 270)
- `DataRetentionPolicyPresenter` · `Move rule down` (moveDownButton, line 276)
- `RuleSetSettingsPresenter` · `Enable selected rules` (disableButton, line 102)
- `RuleSetSettingsPresenter` · `Move selected rule up` (moveUpButton, line 104)
- `ApiKeysPresenter` · `Edit API Key` (editButton, line 98)
- `ApiKeysPresenter` · `Delete API Key` (deleteButton, line 103)
- `BatchDocumentPermissionsPresenter` · `Edit Permissions For Selected Document` (docEdit, line 124)
- `SigningKeyPresenter` · `Clear` (revokeAllButton, line 69)
- `UserAndGroupsPresenter` · `Create User Or Group` (createButton, line 139)
- `UserAndGroupsPresenter` · `No Selection` (editButton, line 144)
- `UserAndGroupsPresenter` · `No Selection` (deleteButton, line 149)
- `UserAndGroupsPresenter` · `Remove` (removeMemberOfButton, line 186)
- `UserAndGroupsPresenter` · `Remove` (removeMembersInButton, line 215)
- `UserPermissionReportPresenter` · `Edit Permissions For Selected Document` (docEdit, line 120)
- `UsersPresenter` · `No Selection` (deleteButton, line 86)
- `TaskManagerListPresenter` · `Delete` (terminateButton, line 163)
- `VisualisationAssetsPresenter` · `Revert changes` (revertButton, line 191)
- `XMLSchemaPresenter` · `Alert` (validationIndicator, line 165)
- `StatisticsCustomMaskListPresenter` · `Remove roll-up permutation` (removeButton, line 69)
