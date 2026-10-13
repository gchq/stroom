# Route recipes — the clicks that reach each presenter

**Generated** by `stroom-gwt-suite/tools/build-route-recipes.mjs`. Do not hand-edit.

`reachability-graph.md` gives the topology; this gives the words to click. Mined from GWT's own
declarative menu registration, so it re-derives instead of rotting.

## Totals

| | count |
| --- | ---: |
| Menu recipes | 50 |
| Document-type recipes | 27 |
| …naming a presenter we can check off | 72 |
| Dialog captions mapped to a presenter | 176 |
| Parent→child show edges (`shower` opens `presenter`) | 334 |
| …of those with no literal caption, so only the ROUTE can name them | 158 |
| Presenters with NO recipe (see below) | 356 of 418 |

## Menu recipes

* `Main Menu > Administration > Content Templates` → **ContentTemplateTabPresenter** _(ContentTemplatePlugin)_
* `Main Menu > Administration > Data Receipt Rules` → **RuleSetPresenter** _(ReceiveDataRuleSetPlugin)_
* `Main Menu > Administration > Data Retention` → **DataRetentionPresenter** _(DataRetentionPlugin)_
* `Main Menu > Administration > Data Volumes` → **FsVolumeGroupPresenter** _(ManageFsVolumesPlugin)_
* `Main Menu > Administration > Index Volumes` → **IndexVolumeGroupPresenter** _(ManageIndexVolumesPlugin)_
* `Main Menu > Administration > Properties` → **GlobalPropertyTabPresenter** _(ManageGlobalPropertiesPlugin)_
* `Main Menu > Annotations > Annotation Collections` → **AnnotationTagPresenter** _(AnnotationCollectionPlugin)_
* `Main Menu > Annotations > Annotation Comments` → **AnnotationTagPresenter** _(AnnotationCommentPlugin)_
* `Main Menu > Annotations > Annotation Labels` → **AnnotationTagPresenter** _(AnnotationLabelPlugin)_
* `Main Menu > Annotations > Annotation Statuses` → **AnnotationTagPresenter** _(AnnotationStatusPlugin)_
* `Main Menu > Annotations > Browse Annotations` → **BrowseAnnotationPresenter** _(AnnotationBrowsePlugin)_
* `Main Menu > Annotations > Create New Annotation` → **(unresolved)** _(AnnotationCreatePlugin)_
* `Main Menu > Help > About` → **AboutPresenter** _(AboutPlugin)_
* `Main Menu > Help > API Specification` → **(unresolved)** _(HelpPlugin)_
* `Main Menu > Help > Help` → **(unresolved)** _(HelpPlugin)_
* `Main Menu > Monitoring > Caches` → **CachePresenter** _(CacheMonitoringPlugin)_
* `Main Menu > Monitoring > Database Tables` → **DatabaseTablesMonitoringPresenter** _(DatabaseTablesMonitoringPlugin)_
* `Main Menu > Monitoring > Execution Schedule Manager` → **(unresolved)** _(ExecutionScheduleManagerPlugin)_
* `Main Menu > Monitoring > Jobs` → **JobPresenter** _(JobListPlugin)_
* `Main Menu > Monitoring > Node Groups` → **NodeGroupPresenter** _(NodeGroupsPlugin)_
* `Main Menu > Monitoring > Nodes` → **NodePresenter** _(NodeMonitoringPlugin)_
* `Main Menu > Monitoring > Processor Profiles` → **ProcessorProfilePresenter** _(ProcessorProfilePlugin)_
* `Main Menu > Monitoring > Search Results` → **ResultStorePresenter** _(ResultStorePlugin)_
* `Main Menu > Monitoring > Server Tasks` → **TaskManagerPresenter** _(TaskManagerPlugin)_
* `Main Menu > Navigation > Add Current Item to Favourites` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Delete Tab Session` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Find` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Find in Content` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Locate Current Item` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Open Tab Session` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Recent Items` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Navigation > Save Tab Session` → **NavigationPresenter** _(NavigationPlugin)_
* `Main Menu > Security > Application Permissions` → **AppPermissionsPresenter** _(AppPermissionsPlugin)_
* `Main Menu > Security > Credentials Manager` → **CredentialsPresenter** _(CredentialsPlugin)_
* `Main Menu > Security > Document Permissions` → **BatchDocumentPermissionsPresenter** _(DocumentPermissionsPlugin)_
* `Main Menu > Security > Manage Accounts` → **AccountsPresenter** _(AccountsPlugin)_
* `Main Menu > Security > Manage API Keys` → **ApiKeysPresenter** _(ApiKeysPlugin)_
* `Main Menu > Security > Signing Keys` → **SigningKeyPresenter** _(SigningKeyPlugin)_
* `Main Menu > Security > User Access` → **UserAccessPresenter** _(UserAccessPlugin)_
* `Main Menu > Security > User Groups` → **UserAndGroupsPresenter** _(UsersAndGroupsPlugin)_
* `Main Menu > Security > User Permissions Report` → **UserRefPopupPresenter** _(UserPermissionsReportPlugin)_
* `Main Menu > Security > Users` → **UsersPresenter** _(UsersPlugin)_
* `Main Menu > Tools > Content Store` → **ContentStorePresenter** _(ContentStorePlugin)_
* `Main Menu > Tools > Dependencies` → **DependenciesTabPresenter** _(DependenciesPlugin)_
* `Main Menu > Tools > Export` → **ExportConfigPresenter** _(ExportConfigPlugin)_
* `Main Menu > Tools > Import` → **ImportConfigPresenter** _(ImportConfigPlugin)_
* `Main Menu > User > Change Password` → **CurrentPasswordPresenter** _(ChangePasswordPlugin)_
* `Main Menu > User > Preferences` → **UserPreferencesPresenter** _(UserPreferencesPlugin)_
* `Main Menu > User > Sign Out` → **(unresolved)** _(LogoutPlugin)_
* `Main Menu > User > User Profile` → **UserTabPresenter** _(UserPlugin)_

## Document-type recipes

* `Explorer > New > … > AnalyticRule` → **AnalyticRulePresenter** _(AnalyticsPlugin)_
* `Explorer > New > … > Dashboard` → **DashboardSuperPresenter** _(DashboardPlugin)_
* `Explorer > New > … > DataGen` → **DataGenPresenter** _(DataGenPlugin)_
* `Explorer > New > … > Dictionary` → **DictionaryPresenter** _(DictionaryPlugin)_
* `Explorer > New > … > Documentation` → **DocumentationPresenter** _(DocumentationPlugin)_
* `Explorer > New > … > ElasticCluster` → **ElasticClusterPresenter** _(ElasticClusterPlugin)_
* `Explorer > New > … > ElasticIndex` → **ElasticIndexPresenter** _(ElasticIndexPlugin)_
* `Explorer > New > … > Feed` → **FeedPresenter** _(FeedPlugin)_
* `Explorer > New > … > GitRepo` → **GitRepoPresenter** _(GitRepoPlugin)_
* `Explorer > New > … > KafkaConfig` → **KafkaConfigPresenter** _(KafkaConfigPlugin)_
* `Explorer > New > … > LuceneIndex` → **IndexPresenter** _(IndexPlugin)_
* `Explorer > New > … > OpenAIModel` → **OpenAIModelPresenter** _(OpenAIModelPlugin)_
* `Explorer > New > … > Pathways` → **PathwaysPresenter** _(PathwaysPlugin)_
* `Explorer > New > … > Pipeline` → **PipelinePresenter** _(PipelinePlugin)_
* `Explorer > New > … > PlanB` → **PlanBPresenter** _(PlanBPlugin)_
* `Explorer > New > … > Query` → **QueryDocPresenter** _(QueryPlugin)_
* `Explorer > New > … > Report` → **ReportPresenter** _(ReportPlugin)_
* `Explorer > New > … > S3Config` → **S3ConfigPresenter** _(S3ConfigPlugin)_
* `Explorer > New > … > Script` → **ScriptPresenter** _(ScriptPlugin)_
* `Explorer > New > … > SolrIndex` → **SolrIndexPresenter** _(SolrIndexPlugin)_
* `Explorer > New > … > StatisticStore` → **StatisticsDataSourcePresenter** _(StatisticsPlugin)_
* `Explorer > New > … > TextConverter` → **TextConverterPresenter** _(TextConverterPlugin)_
* `Explorer > New > … > Traces` → **TracesPresenter** _(TracesDocPlugin)_
* `Explorer > New > … > View` → **ViewPresenter** _(ViewPlugin)_
* `Explorer > New > … > Visualisation` → **VisualisationPresenter** _(VisualisationPlugin)_
* `Explorer > New > … > XmlSchema` → **XMLSchemaPresenter** _(XMLSchemaPlugin)_
* `Explorer > New > … > Xslt` → **XsltPresenter** _(XsltPlugin)_

## No recipe yet

Named rather than omitted. Most are dialogs reached FROM one of the above — the parent edges are in
`reachability-graph.md` — plus the embedded panels, which have no route of their own by definition.

* ActivityEditPresenter
* ActivityListPresenter
* ManageActivityPresenter
* SplashPresenter
* AiAttachmentDataPresenter
* AiChatHistoryPresenter
* AiChatHistoryResultListPresenter
* AiConfigGeneralPresenter
* AiConfigTableAnalysisPresenter
* AskStroomAiConfigPresenter
* AskStroomAiPresenter
* DownloadChatPresenter
* AbstractDuplicateManagementPresenter
* AbstractNotificationListPresenter
* AbstractNotificationPresenter
* AbstractProcessingPresenter
* AbstractQueryEditPresenter
* AbstractSettingsPresenter
* AnalyticDataShardListPresenter
* AnalyticDataShardsPresenter
* AnalyticDuplicateManagementPresenter
* AnalyticEmailDestinationPresenter
* AnalyticNotificationEditPresenter
* AnalyticNotificationListPresenter
* AnalyticNotificationPresenter
* AnalyticProcessingPresenter
* AnalyticQueryEditPresenter
* AnalyticSettingsPresenter
* AnalyticStreamDestinationPresenter
* BatchExecutionScheduleEditPresenter
* DuplicateManagementListPresenter
* ExecutionScheduleRunNowPresenter
* ReportNotificationListPresenter
* ReportNotificationPresenter
* ReportProcessingPresenter
* ReportQueryEditPresenter
* ReportSettingsPresenter
* ScheduledProcessEditPresenter
* ScheduledProcessHistoryListPresenter
* ScheduledProcessListPresenter
* ScheduledProcessingPresenter
* StreamingProcessingPresenter
* TableBuilderProcessingPresenter
* AddEventLinkPresenter
* AnnotationEditPresenter
* AnnotationLinkPresenter
* AnnotationPresenter
* AnnotationTagCreatePresenter
* AnnotationTagEditPresenter
* AnnotationTagListPresenter
* ChangeAssignedToPresenter
* ChangeStatusPresenter
* ChooserPresenter
* CommentEditPresenter
* DurationPresenter
* FindAnnotationListPresenter
* FindAnnotationPresenter
* LinkedEventPresenter
* MultiChooserPresenter
* CacheListPresenter
* CacheNodeListPresenter
* ConfigPropertyClusterValuesListPresenter
* ConfigPropertyClusterValuesPresenter
* ManageGlobalPropertyEditPresenter
* ManageGlobalPropertyListPresenter
* ContentTabPanePresenter
* ContentTabPresenter
* ContentStoreContentPackDetailsPresenter
* ContentStoreContentPackListPresenter
* AccessTokenSecretPresenter
* CredentialEditPresenter
* CredentialSettingsPresenter
* CredentialsListPresenter
* CredentialsManagerDialogPresenter
* KeyStoreSecretPresenter
* SecretPresenter
* SshKeySecretPresenter
* UsernamePasswordSecretPresenter
* BasicEmbeddedQuerySettingsPresenter
* EmbeddedQueryPresenter
* EmbeddedQuerySettingsPresenter
* BasicKeyValueInputSettingsPresenter
* BasicListInputSettingsPresenter
* BasicTableFilterSettingsPresenter
* BasicTextInputSettingsPresenter
* ColumnSelectionPresenter
* KeyValueInputPresenter
* KeyValueInputSettingsPresenter
* ListInputPresenter
* ListInputSettingsPresenter
* MultiRulesPresenter
* TableFilterPresenter
* TableFilterSettingsPresenter
* TextInputPresenter
* TextInputSettingsPresenter
* AbstractComponentPresenter
* AbstractRefreshableComponentPresenter
* AbstractSettingsTabPresenter
* BasicSettingsTabPresenter
* DashboardPresenter
* LayoutConstraintPresenter
* RenameTabPresenter
* SettingsPresenter
* BasicQuerySettingsPresenter
* CurrentSelectionPresenter
* NamePresenter
* ProcessorLimitsPresenter
* QueryFavouritesPresenter
* QueryHistoryPresenter
* QueryInfoPresenter
* QueryPresenter
* QuerySettingsPresenter
* SelectionHandlerListPresenter
* SelectionHandlerPresenter
* SelectionHandlersPresenter
* BasicTableSettingsPresenter
* ColumnFilterPresenter
* ColumnFunctionEditorPresenter
* ColumnValuesFilterPresenter
* DownloadPresenter
* FormatPresenter
* IncludeExcludeFilterDictionaryPresenter
* IncludeExcludeFilterPresenter
* RenameColumnPresenter
* TableFilterPresenter
* TablePresenter
* TableSettingsPresenter
* CustomRowStylePresenter
* EditExpressionPresenter
* RuleListPresenter
* RulePresenter
* RulesPresenter
* BasicTextSettingsPresenter
* TextPresenter
* TextSettingsPresenter
* UnknownComponentPresenter
* BasicVisSettingsPresenter
* VisPresenter
* VisSettingsPresenter
* AbstractMetaListPresenter
* CharacterNavigatorPresenter
* CharacterRangeSelectionPresenter
* DataPresenter
* DataPreviewTabPresenter
* DataUploadPresenter
* EditExpressionPresenter
* ExpressionPresenter
* ItemNavigatorPresenter
* ItemSelectionPresenter
* MarkerListPresenter
* MetaListPresenter
* MetaPresenter
* MetaRelationListPresenter
* ProcessChoicePresenter
* ProcessorTaskListPresenter
* ProcessorTaskPresenter
* ProcessorTaskSummaryPresenter
* SelectionSummaryPresenter
* SourcePresenter
* SourceTabPresenter
* SteppingMetaListPresenter
* TextPresenter
* FsVolumeEditPresenter
* FsVolumeGroupEditPresenter
* FsVolumeGroupListPresenter
* FsVolumeStatusListPresenter
* NewFsVolumeGroupPresenter
* DataGenProcessingPresenter
* DataGenSettingsPresenter
* DictionaryListPresenter
* DictionarySettingsPresenter
* DocRefListPresenter
* WordListPresenter
* CopyDocumentPresenter
* CreateDocumentPresenter
* DocPresenter
* DocTabPresenter
* InfoDocumentPresenter
* LinkTabPanelPresenter
* MarkdownEditPresenter
* MarkdownPreviewPresenter
* MoveDocumentPresenter
* NameDocumentPresenter
* AbstractFindPresenter
* DocSelectionBoxPresenter
* DocumentListPresenter
* DocumentPermissionsListPresenter
* EntityCheckTreePresenter
* EntityTreePresenter
* ExplorerNodeEditTagsPresenter
* ExplorerNodeRemoveTagsPresenter
* ExplorerPopupPresenter
* FindDocResultListPresenter
* FindInContentPresenter
* FindPresenter
* RecentItemsPresenter
* TabSessionChooserPresenter
* TypeFilterPresenter
* FeedSettingsPresenter
* FolderPresenter
* FolderRootPresenter
* GitRepoCommitDialogPresenter
* GitRepoSettingsPresenter
* HttpClientConfigPresenter
* HttpTlsConfigPresenter
* DependenciesInfoPresenter
* DependenciesPresenter
* ImportConfigConfirmPresenter
* DenseVectorFieldPresenter
* IndexFieldEditPresenter
* IndexFieldListPresenter
* IndexSettingsPresenter
* IndexShardPresenter
* IndexVolumeEditPresenter
* IndexVolumeGroupEditPresenter
* IndexVolumeGroupListPresenter
* IndexVolumeStatusListPresenter
* NewIndexVolumeGroupPresenter
* JobListPresenter
* JobNodeListPresenter
* MainPresenter
* NewNodeGroupPresenter
* NodeGroupEditPresenter
* NodeGroupListPresenter
* NodeGroupStateListPresenter
* NodeJobListPresenter
* NodeStatusListPresenter
* OpenAIModelSettingsPresenter
* ConstraintEditPresenter
* ConstraintListPresenter
* PathwayEditPresenter
* PathwayListPresenter
* PathwayTreePresenter
* PathwaysSettingsPresenter
* PathwaysSplitPresenter
* TracesListPresenter
* TracesListTabPresenter
* TracesSettingsPresenter
* DocRefSelectionPresenter
* TextConverterSettingsPresenter
* ElementPresenter
* StepControlPresenter
* StepLocationLinkPresenter
* StepLocationPresenter
* SteppingFilterPresenter
* SteppingPresenter
* XPathFilterPresenter
* XPathListPresenter
* NewElementPresenter
* NewPipelineReferencePresenter
* NewPropertyPresenter
* PipelineReferenceListPresenter
* PipelineStructurePresenter
* PipelineTreePresenter
* PropertyListPresenter
* AbstractPlanBSettingsPresenter
* HistogramSettingsPresenter
* MetricSettingsPresenter
* PlanBSettingsPresenter
* RangeStateSettingsPresenter
* SessionSettingsPresenter
* StateSettingsPresenter
* TemporalRangeStateSettingsPresenter
* TemporalStateSettingsPresenter
* TraceSettingsPresenter
* EditorPreferencesPresenter
* ThemePreferencesPresenter
* TimePreferencesPresenter
* BatchProcessorFilterEditPresenter
* EditFeedDependencyPresenter
* FeedDependencyListPresenter
* FeedDependencyPresenter
* ProcessorEditPresenter
* ProcessorListPresenter
* ProcessorPresenter
* ProcessorProfileEditPresenter
* ProcessorProfileListPresenter
* ProfilePeriodEditPresenter
* ProfilePeriodListPresenter
* ProcessorTaskPresenter
* ExpressionTreePresenter
* QueryDocEditPresenter
* QueryEditPresenter
* QueryHelpPresenter
* QueryResultTablePresenter
* QueryResultTableSplitPresenter
* QueryResultVisPresenter
* QueryToolbarPresenter
* ResultStoreListPresenter
* ResultStoreSettingsPresenter
* TextPresenter
* ContentTemplateEditPresenter
* ContentTemplateListPresenter
* DataRetentionImpactPresenter
* DataRetentionPolicyListPresenter
* DataRetentionPolicyPresenter
* DataRetentionRulePresenter
* FieldEditPresenter
* FieldListPresenter
* RulePresenter
* RuleSetListPresenter
* RuleSetSettingsPresenter
* ScriptDependencyListPresenter
* ScriptListPresenter
* ScriptSettingsPresenter
* ElasticClusterSettingsPresenter
* ElasticIndexFieldListPresenter
* ElasticIndexSettingsPresenter
* SolrIndexFieldEditPresenter
* SolrIndexFieldListPresenter
* SolrIndexSettingsPresenter
* ApiKeysListPresenter
* AppPermissionsEditPresenter
* AppUserPermissionsListPresenter
* BatchDocumentPermissionsEditPresenter
* CreateExternalUserPresenter
* CreateMultipleUsersPresenter
* CreateNewGroupPresenter
* CreateUserPresenter
* DocumentCreatePermissionsListPresenter
* DocumentUserCreatePermissionsEditPresenter
* DocumentUserPermissionsEditPresenter
* DocumentUserPermissionsListPresenter
* DocumentUserPermissionsPresenter
* EditApiKeyPresenter
* SigningKeyListPresenter
* UserAccessListPresenter
* UserDependenciesListPresenter
* UserInfoPresenter
* UserListPresenter
* UserPermissionReportPresenter
* UserRefSelectionBoxPresenter
* UserSessionsListPresenter
* AccountsListPresenter
* AuthenticationErrorPresenter
* ChangePasswordPresenter
* EditAccountPresenter
* EmailResetPasswordPresenter
* LoginPresenter
* ResetPasswordPresenter
* TaskManagerListPresenter
* UserTaskManagerPresenter
* UserTaskPresenter
* ViewSettingsPresenter
* VisualisationAssetsAddItemDialogPresenter
* VisualisationAssetsEditAssetDialogPresenter
* VisualisationAssetsPresenter
* VisualisationAssetsUploadFileDialogPresenter
* VisualisationSettingsPresenter
* WelcomePresenter
* XMLSchemaSettingsPresenter
* ContentPresenter
* StatisticsCustomMaskListPresenter
* StatisticsDataSourceSettingsPresenter
* StatisticsFieldEditPresenter
* StatisticsFieldListPresenter